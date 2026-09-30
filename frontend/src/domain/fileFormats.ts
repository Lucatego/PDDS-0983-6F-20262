// Lectores de los archivos de entrada del curso y validación del alta de pedidos.
//   ventas:        ##d##h##m:posX,posY,cIdCliente,qq,hl        11d13h31m:45,43,c9167,12,36
//   bloqueos:      ##d##h##m-##d##h##m:x1,y1,x2,y2,...        01d06h00m-01d15h00m:31,21,34,21
//   averías:       ##d##h##m:TTNN,tipo                        01d09h30m:TA01,2
//   mantenimiento: aaaammdd:TTNN                              20260908:TB03
import { GRID_H, GRID_W, MODALIDADES, VEHICLE_TYPES } from './constants';
import { dist } from './grid';
import { fmtTime } from './time';
import type { FallaTipo, FileKind, OrderInput, Point } from './types';

export interface VentaRecord {
  arriveMin: number;
  pos: Point;
  clientId: string;
  qty: number;
  hourLimit: number;
}
export interface BloqueoRecord {
  startMin: number;
  endMin: number;
  nodes: Point[];
}
export interface AveriaRecord {
  atMin: number;
  vehicleId: string;
  tipo: FallaTipo;
}
export interface MantenimientoRecord {
  y: number;
  mo: number;
  d: number;
  dateStr: string;
  vehicleId: string;
}

export type ParsedFile =
  | { kind: 'ventas'; records: VentaRecord[] }
  | { kind: 'bloqueos'; records: BloqueoRecord[] }
  | { kind: 'averias'; records: AveriaRecord[] }
  | { kind: 'mantenimiento'; records: MantenimientoRecord[] };

const lines = (text: string) =>
  text
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter(Boolean);

/** ##d##h##m -> minutos desde las 00:00 del día 1. */
export function parseDDHHMM(s: string): number | null {
  const m = /^(\d+)d(\d+)h(\d+)m$/.exec(s);
  if (!m) return null;
  return (Number(m[1]) - 1) * 1440 + Number(m[2]) * 60 + Number(m[3]);
}

/** "tb3" -> "TB03" */
export function normVehId(s: string): string {
  const up = String(s).trim().toUpperCase();
  const m = /^([A-Z]{2})(\d{1,3})$/.exec(up);
  return m ? m[1] + m[2].padStart(2, '0') : up;
}

export function parseVentas(text: string): VentaRecord[] {
  const out: VentaRecord[] = [];
  for (const line of lines(text)) {
    const m = /^(\d+d\d+h\d+m):(-?\d+),(-?\d+),(\w+),(\d+),(\d+)$/.exec(line);
    const arriveMin = m ? parseDDHHMM(m[1]) : null;
    if (!m || arriveMin === null) continue;
    out.push({ arriveMin, pos: { x: +m[2], y: +m[3] }, clientId: m[4], qty: +m[5], hourLimit: +m[6] });
  }
  return out.sort((a, b) => a.arriveMin - b.arriveMin);
}

export function parseNodeList(csv: string): Point[] | null {
  const coords = csv.split(',').map((n) => Number.parseInt(n.trim(), 10));
  if (coords.length < 4 || coords.length % 2 !== 0 || coords.some(Number.isNaN)) return null;
  const nodes: Point[] = [];
  for (let i = 0; i + 1 < coords.length; i += 2) nodes.push({ x: coords[i], y: coords[i + 1] });
  return nodes;
}

export function parseBloqueos(text: string): BloqueoRecord[] {
  const out: BloqueoRecord[] = [];
  for (const line of lines(text)) {
    const m = /^(\d+d\d+h\d+m)-(\d+d\d+h\d+m):(.+)$/.exec(line);
    if (!m) continue;
    const startMin = parseDDHHMM(m[1]);
    const endMin = parseDDHHMM(m[2]);
    const nodes = parseNodeList(m[3]);
    if (startMin === null || endMin === null || !nodes) continue;
    out.push({ startMin, endMin, nodes });
  }
  return out;
}

export function parseAverias(text: string): AveriaRecord[] {
  const out: AveriaRecord[] = [];
  for (const line of lines(text)) {
    const m = /^(\d+d\d+h\d+m):([A-Za-z]{1,3}\d{1,3}),([123])$/.exec(line);
    const atMin = m ? parseDDHHMM(m[1]) : null;
    if (!m || atMin === null) continue;
    out.push({ atMin, vehicleId: normVehId(m[2]), tipo: Number(m[3]) as FallaTipo });
  }
  return out.sort((a, b) => a.atMin - b.atMin);
}

export function parseMantenimiento(text: string): MantenimientoRecord[] {
  const out: MantenimientoRecord[] = [];
  for (const line of lines(text)) {
    if (/^plan$/i.test(line)) continue;
    const m = /^(\d{4})(\d{2})(\d{2}):([A-Za-z]{2})(\d{1,3})$/.exec(line);
    if (!m) continue;
    const y = +m[1];
    const mo = +m[2];
    const d = +m[3];
    if (mo < 1 || mo > 12 || d < 1 || d > 31) continue;
    out.push({ y, mo, d, dateStr: `${m[1]}-${m[2]}-${m[3]}`, vehicleId: normVehId(m[4] + m[5]) });
  }
  return out.sort((a, b) => a.y - b.y || a.mo - b.mo || a.d - b.d);
}

export function parseFile(kind: FileKind, text: string): ParsedFile {
  switch (kind) {
    case 'ventas':
      return { kind, records: parseVentas(text) };
    case 'bloqueos':
      return { kind, records: parseBloqueos(text) };
    case 'averias':
      return { kind, records: parseAverias(text) };
    case 'mantenimiento':
      return { kind, records: parseMantenimiento(text) };
  }
}

const dayLabel = (min: number) => `D${Math.floor(min / 1440) + 1} ${fmtTime(min)}`;
const FALLA_SHORT: Record<FallaTipo, string> = { 1: 'leve', 2: 'moderada', 3: 'grave' };

/** Filas de vista previa para el diálogo de confirmación de carga. */
export function previewRows(parsed: ParsedFile, max = 8): string[] {
  switch (parsed.kind) {
    case 'ventas':
      return parsed.records
        .slice(0, max)
        .map((r) => `${dayLabel(r.arriveMin)} · ${r.clientId} · ${r.qty} paq. · (${r.pos.x},${r.pos.y}) · ${r.hourLimit} h`);
    case 'bloqueos':
      return parsed.records
        .slice(0, max)
        .map((r) => `${dayLabel(r.startMin)} → ${dayLabel(r.endMin)} · ${r.nodes.length} nodos desde (${r.nodes[0].x},${r.nodes[0].y})`);
    case 'averias':
      return parsed.records.slice(0, max).map((r) => `${dayLabel(r.atMin)} · ${r.vehicleId} · avería ${FALLA_SHORT[r.tipo]}`);
    case 'mantenimiento':
      return parsed.records.slice(0, max).map((r) => `${r.dateStr} · ${r.vehicleId} · día completo (00:00–23:59)`);
  }
}

export const FILE_KIND_INFO: Record<FileKind, { title: string; format: string; example: string; button: string }> = {
  ventas: {
    title: 'Archivo de ventas',
    format: '##d##h##m:posX,posY,cIdCliente,qq,hl',
    example: '11d13h31m:45,43,c9167,12,36',
    button: 'Importar ventas',
  },
  bloqueos: {
    title: 'Archivo de bloqueos',
    format: '##d##h##m-##d##h##m:x1,y1,x2,y2,...',
    example: '01d06h00m-01d15h00m:31,21,34,21',
    button: 'Importar bloqueos',
  },
  averias: {
    title: 'Archivo de averías',
    format: '##d##h##m:TTNN,tipo',
    example: '01d09h30m:TA01,2',
    button: 'Importar averías',
  },
  mantenimiento: {
    title: 'Plan de mantenimiento preventivo',
    format: 'aaaammdd:TTNN',
    example: '20260908:TB03',
    button: 'Importar plan',
  },
};

const VALID_HOURS = MODALIDADES.map((m) => m.horas);
const CENTRAL: Point = { x: 27, y: 14 };

export type OrderValidation = { ok: true; enRiesgo: boolean } | { ok: false; motivo: string };

/** Mismas reglas del alta manual del prototipo (LE010: un plazo inalcanzable se marca en riesgo, no se rechaza). */
export function validateOrderInput(o: OrderInput): OrderValidation {
  if (!o.clientId.trim()) return { ok: false, motivo: 'cliente vacío' };
  if (!Number.isInteger(o.qty) || o.qty < 1 || o.qty > 24) return { ok: false, motivo: `cantidad fuera de rango (${o.qty}); debe estar entre 1 y 24` };
  if (!Number.isInteger(o.x) || !Number.isInteger(o.y) || o.x < 0 || o.x > GRID_W || o.y < 0 || o.y > GRID_H)
    return { ok: false, motivo: `destino fuera de la retícula (0–${GRID_W}, 0–${GRID_H})` };
  if (!VALID_HOURS.includes(o.hourLimit)) return { ok: false, motivo: 'modalidad no reconocida' };
  const horasTraslado = dist({ x: o.x, y: o.y }, CENTRAL) / VEHICLE_TYPES.auto.speed;
  return { ok: true, enRiesgo: o.hourLimit < 36 && horasTraslado >= o.hourLimit };
}

export interface LoteLine {
  line: number;
  raw: string;
  input: OrderInput | null;
  error: string | null;
}

/** Carga masiva: una línea por pedido, "cliente,cantidad,modalidad,x,y". */
export function parseLote(text: string): LoteLine[] {
  return text
    .split(/\r?\n/)
    .map((raw, i) => ({ raw: raw.trim(), line: i + 1 }))
    .filter((l) => l.raw)
    .map(({ raw, line }) => {
      const parts = raw.split(',').map((s) => s.trim());
      if (parts.length !== 5) return { line, raw, input: null, error: 'formato inválido (se esperan 5 campos)' };
      const [clientId, qty, hl, x, y] = parts;
      const input: OrderInput = { clientId, qty: Number(qty), hourLimit: Number(hl), x: Number(x), y: Number(y) };
      const v = validateOrderInput(input);
      return { line, raw, input: v.ok ? input : null, error: v.ok ? null : v.motivo };
    });
}
