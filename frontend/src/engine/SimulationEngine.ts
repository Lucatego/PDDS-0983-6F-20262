// Motor de simulación que corre en el navegador (modo "local"). Es el port a TypeScript de
// Prototipo/js/sim/*: pedidos, asignación, bloqueos, averías, mantenimiento, turnos y recarga.
// No depende del DOM: recibe el paso de tiempo real y emite eventos por callback, de modo que
// el mismo contrato (SimSnapshot + LogEvent) lo puede cumplir el backend.
import {
  CYCLE_DAYS_5D,
  DEFAULT_SHIFT_STARTS,
  DELIVERY_MIN,
  FALLA_TYPES,
  GRID_H,
  GRID_W,
  PRIORITY_BUCKETS,
  SCENARIO_LABEL,
  SIM_BASE_MIN_PER_SEC,
  SIM_MIN_PER_SEC,
  SPEED_FACTORS,
  type SpeedFactor,
  VEHICLE_TYPES,
  VEHICLE_TYPE_KEYS,
  initialWarehouses,
} from '@/domain/constants';
import {
  parseAverias,
  parseBloqueos,
  parseMantenimiento,
  parseVentas,
  validateOrderInput,
  type AveriaRecord,
  type BloqueoRecord,
  type MantenimientoRecord,
  type VentaRecord,
} from '@/domain/fileFormats';
import { computeRoute, dist, edgeKey, inGrid, pathLength, pathUsesEdges, sectorOf } from '@/domain/grid';
import { buildShifts, currentShift, dayOf, fmtTime, inMeal, mod1440, parseHHMM, todayISO, type Shift } from '@/domain/time';
import type {
  ClosedOrder,
  FallaTipo,
  FileKind,
  FileLoadSummary,
  FleetConfig,
  Flash,
  Incident,
  LogEvent,
  LogKind,
  Order,
  OrderInput,
  OrderResult,
  Point,
  RunConfig,
  Scenario,
  SimSnapshot,
  Stats,
  Vehicle,
  Warehouse,
  WarehouseId,
} from '@/domain/types';

type Rand = () => number;

interface Scheduled<T> {
  rec: T;
  done: boolean;
  warned?: boolean;
}

const PRIORITY_OPTIONS = [36, 36, 36, 18, 12, 8, 4]; // generación aleatoria sesgada a la entrega regular
const HISTORY_LIMIT = 500;

function emptyStats(): Stats {
  const byPriority: Stats['byPriority'] = {};
  PRIORITY_BUCKETS.forEach((p) => (byPriority[p] = { delivered: 0, onTime: 0 }));
  return { deliveredTotal: 0, deliveredToday: 0, onTime: 0, late: 0, cost: 0, distanceKm: 0, byPriority, bySector: {} };
}

export class SimulationEngine {
  private scenario: Scenario = '5d';
  private speedFactor: SpeedFactor = 1;
  private configured = false;
  private running = false;
  private waitingFirstOrder = false;
  private collapsed = false;
  private finished = false;

  private simMin = 7 * 60;
  private runStartSimMin = 7 * 60;
  private cycleDay = 1;
  private lastDayNum = 1;
  private lastRestockDay = -1;
  private epochDate = todayISO();
  private fleet: FleetConfig = { auto: 6, moto: 10, bici: 8 };
  private shiftStarts: number[] = [...DEFAULT_SHIFT_STARTS];
  private shifts: Shift[] = buildShifts(DEFAULT_SHIFT_STARTS);

  private warehouses: Warehouse[] = initialWarehouses();
  private vehicles: Vehicle[] = [];
  private orders: Order[] = [];
  private orderHistory: ClosedOrder[] = [];
  private incidents: Incident[] = [];
  private incidentHistory: Incident[] = [];
  private blocked = new Set<string>();
  private stats: Stats = emptyStats();
  private flashes: Flash[] = [];

  private ventas: VentaRecord[] | null = null;
  private ventasPtr = 0;
  private bloqueos: Scheduled<BloqueoRecord>[] | null = null;
  private averias: Scheduled<AveriaRecord>[] | null = null;
  private mantenimientos: Scheduled<MantenimientoRecord>[] | null = null;

  private orderSeq = 1000;
  private incidentSeq = 1;
  private logSeq = 1;
  private nextOrderIn = 3;
  private nextIncidentIn: number;

  private runElapsedMs = 0;
  private runStartedAt: number | null = null;

  constructor(
    private readonly emit: (e: LogEvent) => void,
    private readonly rand: Rand = Math.random,
    private readonly now: () => number = () => Date.now(),
  ) {
    this.nextIncidentIn = 60 + this.rand() * 60;
    this.buildFleet();
    this.seedInitialBlockages();
    this.log('Sistema listo. Pulsa **Iniciar** para elegir el escenario, la flota y la fecha de inicio.', 'accent');
  }

  /* ============================== API pública ============================== */

  configure(cfg: RunConfig): void {
    if (this.running) this.stop('Ejecución detenida para aplicar una nueva configuración.');
    this.scenario = cfg.scenario;
    this.speedFactor = 1;
    this.fleet = { ...cfg.fleet };
    this.shiftStarts = [...cfg.shiftStarts];
    this.shifts = buildShifts(this.shiftStarts);
    this.warehouses = initialWarehouses(Math.max(100, cfg.capacities.noroeste), Math.max(100, cfg.capacities.este));
    this.configured = true;
    this.collapsed = false;
    this.finished = false;
    this.runElapsedMs = 0;

    if (cfg.scenario === 'diaria') {
      this.epochDate = todayISO();
      this.resetRun(7 * 60);
      this.waitingFirstOrder = true;
      this.log('Escenario **Operación día a día** configurado. Registra el primer pedido para que comience la operación.', 'accent');
      return;
    }
    this.epochDate = cfg.startDate;
    this.waitingFirstOrder = false;
    this.resetRun(parseHHMM(cfg.startTime, 7 * 60));
    this.log(`Ejecución configurada: inicio ${cfg.startDate} ${cfg.startTime}, escenario **${SCENARIO_LABEL[cfg.scenario]}**.`, 'accent');
    this.start();
  }

  start(): void {
    if (!this.configured) throw new Error('Configura la ejecución antes de iniciarla.');
    if (this.waitingFirstOrder) throw new Error('La operación día a día empieza con el primer pedido registrado.');
    if (this.collapsed || this.finished) throw new Error('La ejecución terminó. Reinicia para correr otra.');
    if (this.running) return;
    this.running = true;
    this.runStartedAt = this.now();
    this.log('Ejecución **iniciada**.', 'good');
  }

  stop(reason = 'Ejecución **detenida** por el usuario.', kind: LogKind = 'warning'): void {
    if (!this.running) return;
    this.running = false;
    if (this.runStartedAt !== null) this.runElapsedMs += this.now() - this.runStartedAt;
    this.runStartedAt = null;
    this.log(reason, kind);
  }

  /** Vuelve al estado sin configurar (equivale a "Reiniciar" del prototipo). */
  reset(): void {
    if (this.running) this.stop('Ejecución detenida para reiniciar.');
    this.speedFactor = 1;
    this.configured = false;
    this.waitingFirstOrder = false;
    this.collapsed = false;
    this.finished = false;
    this.runElapsedMs = 0;
    this.ventas = null;
    this.bloqueos = null;
    this.averias = null;
    this.mantenimientos = null;
    this.resetRun(7 * 60);
    this.log('Simulación **reiniciada**. Elige de nuevo el escenario, la flota y la fecha de inicio.', 'warning');
  }

  tick(dtRealSec: number): void {
    if (!this.running) return;
    this.step(Math.min(0.25, Math.max(0, dtRealSec)) * this.simMinPerSec());
  }

  /** Minutos simulados por segundo real: 5D y Colapso = base × multiplicador; Día a día no cambia. */
  private simMinPerSec(): number {
    return this.scenario === 'diaria' ? SIM_MIN_PER_SEC : SIM_BASE_MIN_PER_SEC * this.speedFactor;
  }

  /** Cambia el multiplicador de velocidad en caliente. Solo Simulación 5D y Colapso. */
  setSpeed(factor: number): void {
    if (!this.configured) throw new Error('No hay simulación configurada.');
    if (this.scenario === 'diaria') throw new Error('Día a día corre en tiempo real y no admite cambio de velocidad.');
    if (!(SPEED_FACTORS as readonly number[]).includes(factor)) throw new Error('Velocidad no válida: use ×1, ×2, ×5 o ×10.');
    this.speedFactor = factor as SpeedFactor;
  }

  registerOrder(input: OrderInput): OrderResult {
    const v = validateOrderInput(input);
    if (!v.ok) return v;
    const id = this.orderSeq++;
    this.orders.push({
      id,
      clientId: input.clientId.trim(),
      pos: { x: input.x, y: input.y },
      qty: input.qty,
      priority: input.hourLimit,
      createdAt: this.simMin,
      deadline: this.simMin + input.hourLimit * 60,
      status: 'pending',
      reprogramado: false,
      enRiesgo: v.enRiesgo,
      vehicleId: null,
      warehouseId: null,
    });
    this.log(
      `Pedido **#${id}** registrado (${input.clientId} · ${input.qty} paq. · ${input.hourLimit} h)` +
        (v.enRiesgo ? ' — marcado **en riesgo**: el plazo podría no ser alcanzable.' : '.'),
      v.enRiesgo ? 'warning' : 'accent',
    );
    if (this.scenario === 'diaria' && this.waitingFirstOrder) {
      this.waitingFirstOrder = false;
      this.start();
      this.log('Primer pedido registrado: la operación día a día comienza a correr.', 'good');
    }
    return { ok: true, id, enRiesgo: v.enRiesgo };
  }

  registerOrderBatch(inputs: OrderInput[]): OrderResult[] {
    const res = inputs.map((i) => this.registerOrder(i));
    const ok = res.filter((r) => r.ok).length;
    this.log(`Carga masiva de pedidos: **${ok}** aceptados, ${res.length - ok} rechazados.`, 'accent');
    return res;
  }

  loadFile(kind: FileKind, text: string): FileLoadSummary {
    switch (kind) {
      case 'ventas': {
        const recs = parseVentas(text);
        if (!recs.length) throw new Error('No se reconoció ningún registro de ventas válido.');
        this.ventas = recs;
        this.ventasPtr = 0;
        const immediate = this.releaseVentas();
        const message = `Archivo de ventas cargado: ${recs.length} registros (${immediate} ya vencidos se registraron de inmediato, ${recs.length - immediate} en cola).`;
        this.log(message, 'accent');
        return { kind, count: recs.length, immediate, message };
      }
      case 'bloqueos': {
        const recs = parseBloqueos(text);
        if (!recs.length) throw new Error('No se reconoció ningún registro de bloqueos válido.');
        this.bloqueos = recs.map((rec) => ({ rec, done: false }));
        const before = this.count('bloqueo');
        this.activateScheduledBlockages();
        const immediate = this.count('bloqueo') - before;
        const message = `Archivo de bloqueos cargado: ${recs.length} registros (${immediate} vigentes desde ya).`;
        this.log(message, 'accent');
        return { kind, count: recs.length, immediate, message };
      }
      case 'averias': {
        const recs = parseAverias(text);
        if (!recs.length) throw new Error('No se reconoció ningún registro de averías válido.');
        this.averias = recs.map((rec) => ({ rec, done: false }));
        const before = this.count('falla');
        this.activateScheduledAverias();
        const immediate = this.count('falla') - before;
        const message = `Archivo de averías cargado: ${recs.length} registros (${immediate} aplicadas de inmediato).`;
        this.log(message, 'accent');
        return { kind, count: recs.length, immediate, message };
      }
      case 'mantenimiento': {
        const recs = parseMantenimiento(text);
        if (!recs.length) throw new Error('No se reconoció ningún registro de mantenimiento válido.');
        this.mantenimientos = recs.map((rec) => ({ rec, done: false, warned: false }));
        const before = this.count('mantenimiento');
        this.activateScheduledMantenimiento();
        const immediate = this.count('mantenimiento') - before;
        const message = `Plan de mantenimiento cargado: ${recs.length} registros (${immediate} aplicados de inmediato).`;
        this.log(message, 'accent');
        return { kind, count: recs.length, immediate, message };
      }
    }
  }

  registerAveria(vehicleId: string, tipo: FallaTipo): void {
    const v = this.vehicles.find((x) => x.id === vehicleId);
    if (!v) throw new Error(`No existe la unidad ${vehicleId}.`);
    if (v.state !== 'toClient' && v.state !== 'returning') throw new Error(`${vehicleId} no está en ruta: solo se registran averías sobre unidades en ruta.`);
    this.applyAveria(v, tipo, 'manual');
  }

  registerMantenimiento(vehicleId: string, horas: number): void {
    const v = this.vehicles.find((x) => x.id === vehicleId);
    if (!v) throw new Error(`No existe la unidad ${vehicleId}.`);
    if (v.state !== 'idle' && v.state !== 'break') throw new Error(`${vehicleId} no está disponible en almacén.`);
    const h = Math.max(1, Math.round(horas || 1));
    this.applyMantenimiento(v, h, 'manual', this.simMin + h * 60);
  }

  registerBloqueo(nodes: Point[], horas: number): void {
    if (nodes.length < 2) throw new Error('Un bloqueo necesita al menos dos nodos.');
    if (!nodes.every(inGrid)) throw new Error(`Hay nodos fuera de la retícula (0–${GRID_W}, 0–${GRID_H}).`);
    for (let i = 1; i < nodes.length; i++) {
      const d = Math.abs(nodes[i].x - nodes[i - 1].x) + Math.abs(nodes[i].y - nodes[i - 1].y);
      const straight = nodes[i].x === nodes[i - 1].x || nodes[i].y === nodes[i - 1].y;
      if (!straight || d === 0) throw new Error('Cada tramo del bloqueo debe ser horizontal o vertical.');
    }
    const h = Math.max(1, horas || 1);
    this.addBlockageChain(expandChain(nodes), this.simMin + h * 60, 'manual');
    this.log(`**Bloqueo** registrado manualmente (${nodes.length} nodos, ${h} h).`, 'warning');
  }

  snapshot(): SimSnapshot {
    const elapsed = this.runElapsedMs + (this.running && this.runStartedAt !== null ? this.now() - this.runStartedAt : 0);
    return {
      scenario: this.scenario,
      configured: this.configured,
      running: this.running,
      waitingFirstOrder: this.waitingFirstOrder,
      collapsed: this.collapsed,
      finished: this.finished,
      simMin: this.simMin,
      runStartSimMin: this.runStartSimMin,
      cycleDay: this.cycleDay,
      epochDate: this.epochDate,
      runElapsedMs: elapsed,
      speedFactor: this.scenario === 'diaria' ? 1 : this.speedFactor,
      simMinPerSec: this.simMinPerSec(),
      shiftStarts: [...this.shiftStarts],
      fleet: { ...this.fleet },
      vehicles: this.vehicles.map((v) => ({ ...v, pos: { ...v.pos }, trail: v.trail.slice() })),
      orders: this.orders.map((o) => ({ ...o })),
      orderHistory: this.orderHistory.slice(),
      incidents: this.incidents.slice(),
      incidentHistory: this.incidentHistory.slice(),
      warehouses: this.warehouses.map((w) => ({ ...w })),
      stats: {
        ...this.stats,
        byPriority: Object.fromEntries(Object.entries(this.stats.byPriority).map(([k, b]) => [k, { ...b }])),
        bySector: Object.fromEntries(Object.entries(this.stats.bySector).map(([k, b]) => [k, { ...b }])),
      },
      flashes: this.flashes.slice(),
      files: {
        ventas: this.ventas ? this.ventas.length : null,
        bloqueos: this.bloqueos ? this.bloqueos.length : null,
        averias: this.averias ? this.averias.length : null,
        mantenimiento: this.mantenimientos ? this.mantenimientos.length : null,
      },
    };
  }

  /* ============================== Paso de simulación ============================== */

  private step(dtMin: number): void {
    this.simMin += dtMin;
    this.cycleDay = dayOf(this.simMin);
    if (this.cycleDay !== this.lastDayNum) {
      this.stats.deliveredToday = 0;
      this.warehouses.forEach((w) => (w.dispatchedToday = 0));
      this.lastDayNum = this.cycleDay;
    }
    if (this.scenario === '5d' && this.simMin - this.runStartSimMin >= CYCLE_DAYS_5D * 1440) {
      this.finished = true;
      this.stop(`**Simulación 5D completada**: ${CYCLE_DAYS_5D} días simulados. ${this.orders.length} pedido(s) quedaron en curso.`, 'good');
      return;
    }
    this.maybeSpawnOrder(dtMin);
    this.tryAssign();
    this.maybeTriggerIncident(dtMin);
    this.updateIncidents();
    this.applyMealBreaks();
    this.handleRestock();
    for (const v of this.vehicles) {
      this.advanceVehicle(v, dtMin);
      if (!this.running) break; // colapso detectado dentro del avance
    }
    this.flashes = this.flashes.filter((f) => this.simMin - f.born < 22);
  }

  private resetRun(startMin: number): void {
    this.simMin = startMin;
    this.runStartSimMin = startMin;
    this.cycleDay = dayOf(startMin);
    this.lastDayNum = this.cycleDay;
    this.lastRestockDay = -1;
    this.orderSeq = 1000;
    this.orders = [];
    this.flashes = [];
    this.incidents = [];
    this.incidentHistory = [];
    this.blocked = new Set();
    this.orderHistory = [];
    this.stats = emptyStats();
    this.warehouses.forEach((w) => {
      if (!w.infinite) w.stock = Math.round(w.capacity * (w.id === 'noroeste' ? 0.76 : 0.84));
      w.dispatchedToday = 0;
    });
    this.ventasPtr = 0;
    this.bloqueos?.forEach((s) => (s.done = false));
    this.averias?.forEach((s) => (s.done = false));
    this.mantenimientos?.forEach((s) => {
      s.done = false;
      s.warned = false;
    });
    this.buildFleet();
    this.seedInitialBlockages();
    this.activateScheduledBlockages();
    this.activateScheduledAverias();
    this.activateScheduledMantenimiento();
  }

  private buildFleet(): void {
    const central = this.warehouses.find((w) => w.id === 'central')!;
    this.vehicles = [];
    for (const key of VEHICLE_TYPE_KEYS) {
      const t = VEHICLE_TYPES[key];
      for (let i = 0; i < this.fleet[key]; i++) {
        this.vehicles.push({
          id: t.prefix + String(i + 1).padStart(2, '0'),
          type: key,
          capacity: t.capacity,
          speed: t.speed,
          costPerKm: t.cost,
          home: 'central',
          state: 'idle',
          pos: { ...central.pos },
          path: null,
          pathIdx: 0,
          orderId: null,
          timer: 0,
          heading: 0,
          trail: [],
          returnTarget: null,
        });
      }
    }
  }

  /* ------------------------------ pedidos ------------------------------ */

  private pushOrder(rec: VentaRecord): void {
    const v = validateOrderInput({ clientId: rec.clientId, qty: rec.qty, hourLimit: rec.hourLimit, x: rec.pos.x, y: rec.pos.y });
    this.orders.push({
      id: this.orderSeq++,
      clientId: rec.clientId,
      pos: rec.pos,
      qty: rec.qty,
      priority: rec.hourLimit,
      createdAt: this.simMin,
      deadline: this.simMin + rec.hourLimit * 60,
      status: 'pending',
      reprogramado: false,
      enRiesgo: v.ok ? v.enRiesgo : false,
      vehicleId: null,
      warehouseId: null,
    });
  }

  private releaseVentas(): number {
    let n = 0;
    while (this.ventas && this.ventasPtr < this.ventas.length && this.ventas[this.ventasPtr].arriveMin <= this.simMin) {
      this.pushOrder(this.ventas[this.ventasPtr++]);
      n++;
    }
    return n;
  }

  private maybeSpawnOrder(dtMin: number): void {
    if (this.scenario === 'diaria') return; // en día a día los pedidos llegan solo por registro
    if (this.ventas) {
      this.releaseVentas();
      return;
    }
    this.nextOrderIn -= dtMin;
    if (this.nextOrderIn > 0) return;
    const tod = mod1440(this.simMin);
    const active = tod >= 7 * 60 && tod < 23 * 60;
    this.nextOrderIn = active ? 3 + this.rand() * 6 : 14 + this.rand() * 22;
    this.pushOrder({
      arriveMin: this.simMin,
      clientId: 'c' + (1000 + Math.floor(this.rand() * 9000)),
      pos: { x: Math.floor(this.rand() * (GRID_W + 1)), y: Math.floor(this.rand() * (GRID_H + 1)) },
      qty: 1 + Math.floor(this.rand() * 22),
      hourLimit: PRIORITY_OPTIONS[Math.floor(this.rand() * PRIORITY_OPTIONS.length)],
    });
  }

  private pickReturnWarehouse(from: Point): Warehouse {
    // las unidades no regresan a un almacén sin stock; el central (ilimitado) siempre califica
    return this.warehouses.filter((w) => w.infinite || w.stock > 0).sort((a, b) => dist(a.pos, from) - dist(b.pos, from))[0];
  }

  private tryAssign(): void {
    for (const o of this.orders) {
      if (o.status !== 'pending') continue;
      const candidates = this.warehouses
        .filter((w) => w.infinite || w.stock >= o.qty)
        .sort((a, b) => dist(a.pos, o.pos) - dist(b.pos, o.pos));
      for (const w of candidates) {
        const v = this.vehicles
          .filter((x) => x.state === 'idle' && x.home === w.id && x.capacity >= o.qty)
          .sort((a, b) => a.capacity - b.capacity)[0];
        if (!v) continue;
        v.state = 'toClient';
        v.orderId = o.id;
        v.path = computeRoute(v.pos, o.pos, this.blocked);
        v.pathIdx = 0;
        if (!w.infinite) w.stock -= o.qty;
        w.dispatchedToday++;
        o.status = 'assigned';
        o.vehicleId = v.id;
        o.warehouseId = w.id;
        this.log(`Pedido **#${o.id}** (${o.clientId} · ${o.qty} paq.) asignado a **${v.id}** desde ${w.name}.`, 'accent');
        break;
      }
    }
  }

  /* ------------------------------ bloqueos ------------------------------ */

  private randomBlockageChain(): Point[] {
    let cur = { x: 5 + Math.floor(this.rand() * (GRID_W - 10)), y: 5 + Math.floor(this.rand() * (GRID_H - 10)) };
    const nodes = [cur];
    const steps = 3 + Math.floor(this.rand() * 4);
    let dir: 'h' | 'v' = this.rand() < 0.5 ? 'h' : 'v';
    for (let i = 0; i < steps; i++) {
      const sign = this.rand() < 0.5 ? 1 : -1;
      const next = { x: cur.x + (dir === 'h' ? sign : 0), y: cur.y + (dir === 'v' ? sign : 0) };
      if (!inGrid(next)) break;
      nodes.push(next);
      cur = next;
      if (this.rand() < 0.3) dir = dir === 'h' ? 'v' : 'h';
    }
    return nodes;
  }

  private seedInitialBlockages(): void {
    if (this.bloqueos) return;
    for (let i = 0; i < 2; i++) this.addBlockageChain(this.randomBlockageChain(), this.simMin + 200 + this.rand() * 400, 'aleatorio');
  }

  private addBlockageChain(nodes: Point[], until: number, origin: 'archivo' | 'manual' | 'aleatorio'): void {
    if (nodes.length < 2) return;
    const edges: string[] = [];
    for (let i = 0; i < nodes.length - 1; i++) {
      const k = edgeKey(nodes[i], nodes[i + 1]);
      edges.push(k);
      this.blocked.add(k);
    }
    this.incidents.push({ kind: 'bloqueo', id: this.incidentSeq++, nodes, edges, since: this.simMin, until, origin });
    for (const v of this.vehicles) {
      if ((v.state === 'toClient' || v.state === 'returning') && v.path && pathUsesEdges(v.path, v.pathIdx, edges)) {
        v.path = computeRoute(v.pos, v.path[v.path.length - 1], this.blocked);
        v.pathIdx = 0;
        this.log(`Ruta de **${v.id}** recalculada: tramo bloqueado por delante.`, 'warning');
      }
    }
  }

  private activateScheduledBlockages(): void {
    this.bloqueos?.forEach((s) => {
      if (s.done || this.simMin < s.rec.startMin) return;
      s.done = true;
      if (this.simMin < s.rec.endMin) this.addBlockageChain(expandChain(s.rec.nodes), s.rec.endMin, 'archivo');
    });
  }

  /* ------------------------------ averías y mantenimiento ------------------------------ */

  private applyAveria(v: Vehicle, tipo: FallaTipo, origin: 'archivo' | 'manual' | 'aleatorio'): void {
    const ft = FALLA_TYPES.find((f) => f.tipo === tipo) ?? FALLA_TYPES[0];
    const order = this.orders.find((o) => o.id === v.orderId);
    const until = this.simMin + ft.minMin + this.rand() * (ft.maxMin - ft.minMin);
    this.incidents.push({ kind: 'falla', id: this.incidentSeq++, vehicleId: v.id, pos: { ...v.pos }, since: this.simMin, until, tipo, origin });
    v.state = 'broken';
    if (order) {
      order.status = 'pending';
      order.vehicleId = null;
      order.reprogramado = true;
    }
    v.orderId = null;
    const tag = origin === 'manual' ? ' (registrada manualmente)' : origin === 'archivo' ? ' (programada por archivo)' : '';
    this.log(`**Avería ${ft.short.toLowerCase()}**${tag} en ${v.id}` + (order ? `: el pedido #${order.id} vuelve a asignación.` : '.'), 'critical');
  }

  private applyMantenimiento(v: Vehicle, horas: number, origin: 'archivo' | 'manual', until: number): void {
    this.incidents.push({ kind: 'mantenimiento', id: this.incidentSeq++, vehicleId: v.id, pos: { ...v.pos }, since: this.simMin, until, horas, origin });
    v.state = 'maintenance';
    this.log(`**Mantenimiento**${origin === 'archivo' ? ' preventivo (plan)' : ''} para ${v.id} durante ${horas} h.`, 'accent');
  }

  private activateScheduledAverias(): void {
    this.averias?.forEach((s) => {
      if (s.done || s.rec.atMin > this.simMin) return;
      const v = this.vehicles.find((x) => x.id === s.rec.vehicleId);
      if (v && (v.state === 'toClient' || v.state === 'returning')) {
        this.applyAveria(v, s.rec.tipo, 'archivo');
        s.done = true;
      } else if (s.rec.atMin <= this.simMin - 120) {
        s.done = true; // la unidad no estuvo en ruta en las 2 h siguientes a la hora programada
        this.log(`Avería programada de ${s.rec.vehicleId} descartada: la unidad no estaba en ruta a esa hora.`, 'warning');
      }
    });
  }

  private activateScheduledMantenimiento(): void {
    if (!this.mantenimientos) return;
    const [ey, em, ed] = this.epochDate.split('-').map(Number);
    const epoch = new Date(ey, em - 1, ed).getTime();
    for (const s of this.mantenimientos) {
      if (s.done) continue;
      const dayIndex = Math.round((new Date(s.rec.y, s.rec.mo - 1, s.rec.d).getTime() - epoch) / 86400000);
      if (dayIndex < 0) {
        s.done = true;
        continue;
      }
      const startMin = dayIndex * 1440;
      const endMin = startMin + 1440;
      if (this.simMin < startMin) continue;
      if (this.simMin >= endMin) {
        if (!s.warned) this.log(`Mantenimiento de ${s.rec.vehicleId} (${s.rec.dateStr}) no se aplicó: la unidad no estuvo disponible ese día.`, 'warning');
        s.done = true;
        continue;
      }
      const v = this.vehicles.find((x) => x.id === s.rec.vehicleId);
      if (!v) continue;
      // la unidad sale de ruta de inmediato, aunque lleve un pedido, y el pedido vuelve a la cola
      const order = v.orderId ? this.orders.find((o) => o.id === v.orderId) : null;
      if (order) {
        order.status = 'pending';
        order.vehicleId = null;
        order.reprogramado = true;
      }
      v.orderId = null;
      v.path = null;
      v.pathIdx = 0;
      v.trail = [];
      this.applyMantenimiento(v, Math.max(1, Math.round((endMin - this.simMin) / 60)), 'archivo', endMin);
      s.done = true;
    }
  }

  private maybeTriggerIncident(dtMin: number): void {
    this.activateScheduledBlockages();
    this.activateScheduledAverias();
    this.activateScheduledMantenimiento();

    this.nextIncidentIn -= dtMin;
    if (this.nextIncidentIn > 0) return;
    this.nextIncidentIn = 70 + this.rand() * 110;

    // si un tipo de incidencia viene por archivo, ya no se genera al azar
    const canBlock = !this.bloqueos;
    const canFail = !this.averias;
    if (!canBlock && !canFail) return;
    if (canBlock && (!canFail || this.rand() < 0.5)) {
      const chain = this.randomBlockageChain();
      if (chain.length < 2) return;
      this.addBlockageChain(chain, this.simMin + 60 + this.rand() * 120, 'aleatorio');
      const a = chain[0];
      const b = chain[chain.length - 1];
      this.log(`**Bloqueo vial** activo entre (${a.x},${a.y}) y (${b.x},${b.y}).`, 'warning');
    } else if (canFail) {
      const moving = this.vehicles.filter((v) => v.state === 'toClient' && v.orderId !== null);
      if (!moving.length) return;
      const v = moving[Math.floor(this.rand() * moving.length)];
      this.applyAveria(v, (1 + Math.floor(this.rand() * 3)) as FallaTipo, 'aleatorio');
    }
  }

  private updateIncidents(): void {
    this.incidents = this.incidents.filter((inc) => {
      if (this.simMin < inc.until) return true;
      this.incidentHistory.unshift(inc);
      if (this.incidentHistory.length > HISTORY_LIMIT) this.incidentHistory.length = HISTORY_LIMIT;
      if (inc.kind === 'falla') {
        const v = this.vehicles.find((x) => x.id === inc.vehicleId);
        if (v && v.state === 'broken') {
          this.sendBack(v);
          this.log(`${v.id} reparada: regresa a ${this.warehouseName(v.returnTarget)}.`, 'good');
        }
      } else if (inc.kind === 'bloqueo') {
        inc.edges.forEach((k) => this.blocked.delete(k));
        const a = inc.nodes[0];
        const b = inc.nodes[inc.nodes.length - 1];
        this.log(`Bloqueo entre (${a.x},${a.y}) y (${b.x},${b.y}) despejado.`, 'good');
      } else {
        const v = this.vehicles.find((x) => x.id === inc.vehicleId);
        if (v && v.state === 'maintenance') {
          v.state = 'idle';
          this.log(`Mantenimiento de ${v.id} finalizado: unidad disponible.`, 'good');
        }
      }
      return false;
    });
  }

  /* ------------------------------ turnos y recarga ------------------------------ */

  private applyMealBreaks(): void {
    const meal = inMeal(this.simMin, currentShift(this.simMin, this.shifts));
    for (const v of this.vehicles) {
      if (v.state === 'idle' && meal && this.rand() < 0.003) v.state = 'break';
      if (v.state === 'break' && !meal) v.state = 'idle';
    }
  }

  private handleRestock(): void {
    if (mod1440(this.simMin) >= 23 * 60 + 59 && this.lastRestockDay !== this.cycleDay) {
      this.warehouses.forEach((w) => {
        if (!w.infinite) w.stock = w.capacity;
      });
      this.lastRestockDay = this.cycleDay;
      this.log('Recarga de almacenes intermedios completada (23:59:59).', 'accent');
    }
  }

  /* ------------------------------ movimiento ------------------------------ */

  private sendBack(v: Vehicle): void {
    const wh = this.pickReturnWarehouse(v.pos);
    v.state = 'returning';
    v.path = computeRoute(v.pos, wh.pos, this.blocked);
    v.pathIdx = 0;
    v.returnTarget = wh.id;
    v.trail = [];
  }

  private deliver(v: Vehicle): void {
    const idx = this.orders.findIndex((o) => o.id === v.orderId);
    if (idx >= 0) {
      const order = this.orders[idx];
      const onTime = this.simMin <= order.deadline;
      this.stats.deliveredTotal++;
      this.stats.deliveredToday++;
      if (onTime) this.stats.onTime++;
      else this.stats.late++;
      const bucket = this.stats.byPriority[order.priority];
      if (bucket) {
        bucket.delivered++;
        if (onTime) bucket.onTime++;
      }
      const sk = sectorOf(order.pos);
      const sec = (this.stats.bySector[sk] ??= { delivered: 0, onTime: 0 });
      sec.delivered++;
      if (onTime) sec.onTime++;
      this.flashes.push({ pos: { ...v.pos }, onTime, born: this.simMin });
      this.log(
        `Pedido **#${order.id}** entregado ${onTime ? 'a tiempo' : 'con retraso'} por ${v.id} a ${order.clientId}.`,
        onTime ? 'good' : 'critical',
      );
      this.orderHistory.unshift({ ...order, estadoFinal: onTime ? 'entregado' : 'no cumplido', closedAt: this.simMin });
      if (this.orderHistory.length > HISTORY_LIMIT) this.orderHistory.length = HISTORY_LIMIT;
      this.orders.splice(idx, 1);

      if (!onTime && this.scenario === 'colapso' && !this.collapsed) {
        this.collapsed = true;
        this.stop(
          `**Colapso logístico**: el pedido #${order.id} (nodo ${order.pos.x},${order.pos.y}) no se entregó dentro de su plazo. ` +
            `${this.orders.length} pedido(s) quedaron sin atender. Ejecución detenida.`,
          'critical',
        );
      }
    }
    v.orderId = null;
    this.sendBack(v);
  }

  private advanceVehicle(v: Vehicle, dtMin: number): void {
    if (v.state === 'idle' || v.state === 'break' || v.state === 'broken' || v.state === 'maintenance') return;
    if (v.state === 'atClient') {
      v.timer -= dtMin;
      if (v.timer <= 0) this.deliver(v);
      return;
    }
    if (!v.path) return;
    let remaining = v.speed * (dtMin / 60); // km disponibles en este paso
    while (remaining > 0 && v.pathIdx < v.path.length - 1) {
      const target = v.path[v.pathIdx + 1];
      const d = dist(v.pos, target);
      if (d <= remaining) {
        v.pos = { ...target };
        v.pathIdx++;
        remaining -= d;
        if (v.pathIdx === v.path.length - 1) {
          const km = pathLength(v.path);
          this.stats.distanceKm += km;
          this.stats.cost += km * v.costPerKm;
          if (v.state === 'toClient') {
            v.state = 'atClient';
            v.timer = DELIVERY_MIN;
          } else {
            v.state = 'idle';
            v.path = null;
            v.home = v.returnTarget ?? v.home;
            v.returnTarget = null;
          }
          v.trail = [];
          break;
        }
      } else {
        v.heading = Math.atan2(target.y - v.pos.y, target.x - v.pos.x);
        v.pos = { x: v.pos.x + Math.cos(v.heading) * remaining, y: v.pos.y + Math.sin(v.heading) * remaining };
        remaining = 0;
      }
      v.trail.push({ ...v.pos });
      if (v.trail.length > 18) v.trail.shift();
    }
    // un vehículo sin ruta útil (destino = origen) llega de inmediato
    if (v.path && v.path.length === 1) {
      if (v.state === 'toClient') {
        v.state = 'atClient';
        v.timer = DELIVERY_MIN;
      } else if (v.state === 'returning') {
        v.state = 'idle';
        v.path = null;
        v.home = v.returnTarget ?? v.home;
        v.returnTarget = null;
      }
    }
  }

  /* ------------------------------ utilidades ------------------------------ */

  private count(kind: Incident['kind']): number {
    return this.incidents.filter((i) => i.kind === kind).length;
  }

  private warehouseName(id: WarehouseId | null): string {
    return this.warehouses.find((w) => w.id === id)?.name ?? 'almacén';
  }

  private log(text: string, kind: LogKind): void {
    this.emit({ id: this.logSeq++, simMin: this.simMin, text, kind });
  }
}

/** Expande un polígono abierto (x1,y1,x2,y2,…) a nodos consecutivos de 1 km. */
export function expandChain(nodes: Point[]): Point[] {
  const out: Point[] = [nodes[0]];
  for (let i = 1; i < nodes.length; i++) {
    let cur = out[out.length - 1];
    const to = nodes[i];
    while (cur.x !== to.x || cur.y !== to.y) {
      cur = { x: cur.x + Math.sign(to.x - cur.x), y: cur.x === to.x ? cur.y + Math.sign(to.y - cur.y) : cur.y };
      out.push(cur);
    }
  }
  return out;
}

export const fmtLogTime = (e: LogEvent) => `D${dayOf(e.simMin)} · ${fmtTime(e.simMin)}`;
