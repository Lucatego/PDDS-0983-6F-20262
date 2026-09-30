// Modelo del dominio PaqRap que comparten el motor local, la pasarela al servidor y la interfaz.
// Las formas de SimSnapshot y LogEvent son también el contrato JSON que debe emitir el backend.

export type Scenario = 'diaria' | '5d' | 'colapso';
export type VehicleTypeKey = 'auto' | 'moto' | 'bici';
export type WarehouseId = 'central' | 'noroeste' | 'este';
export type RiskLevel = 'good' | 'warning' | 'critical';
export type LogKind = 'good' | 'warning' | 'critical' | 'accent';
export type FileKind = 'ventas' | 'bloqueos' | 'averias' | 'mantenimiento';
export type FallaTipo = 1 | 2 | 3;

export interface Point {
  x: number;
  y: number;
}

/** idle = disponible en almacén · break = refrigerio · toClient/atClient/returning = en operación */
export type VehicleState = 'idle' | 'break' | 'toClient' | 'atClient' | 'returning' | 'broken' | 'maintenance';

export interface Vehicle {
  id: string; // TTNN: TA01, TM03, TB08…
  type: VehicleTypeKey;
  capacity: number;
  speed: number; // km/h
  costPerKm: number; // S/
  home: WarehouseId;
  state: VehicleState;
  pos: Point;
  path: Point[] | null;
  pathIdx: number;
  orderId: number | null;
  timer: number; // minutos restantes de entrega en el cliente
  heading: number; // radianes, en coordenadas del mundo
  trail: Point[];
  returnTarget: WarehouseId | null;
}

export type OrderStatus = 'pending' | 'assigned';

export interface Order {
  id: number;
  clientId: string;
  pos: Point;
  qty: number;
  priority: number; // horas de plazo: 4, 8, 12, 18 o 36
  createdAt: number; // minuto de simulación
  deadline: number; // minuto de simulación
  status: OrderStatus;
  reprogramado: boolean;
  enRiesgo: boolean;
  vehicleId: string | null;
  warehouseId: WarehouseId | null;
}

export type ClosedOrderStatus = 'entregado' | 'no cumplido';

export interface ClosedOrder extends Order {
  estadoFinal: ClosedOrderStatus;
  closedAt: number;
}

export interface BloqueoIncident {
  kind: 'bloqueo';
  id: number;
  nodes: Point[];
  edges: string[];
  since: number;
  until: number;
  origin: 'archivo' | 'manual' | 'aleatorio';
}

export interface FallaIncident {
  kind: 'falla';
  id: number;
  vehicleId: string;
  pos: Point;
  since: number;
  until: number;
  tipo: FallaTipo;
  origin: 'archivo' | 'manual' | 'aleatorio';
}

export interface MantenimientoIncident {
  kind: 'mantenimiento';
  id: number;
  vehicleId: string;
  pos: Point;
  since: number;
  until: number;
  horas: number;
  origin: 'archivo' | 'manual';
}

export type Incident = BloqueoIncident | FallaIncident | MantenimientoIncident;

export interface Warehouse {
  id: WarehouseId;
  name: string;
  shortName: string;
  pos: Point;
  infinite: boolean;
  capacity: number;
  stock: number;
  dispatchedToday: number;
}

export interface BucketStats {
  delivered: number;
  onTime: number;
}

export interface Stats {
  deliveredTotal: number;
  deliveredToday: number;
  onTime: number;
  late: number;
  cost: number; // S/ acumulado
  distanceKm: number;
  byPriority: Record<number, BucketStats>;
  bySector: Record<string, BucketStats>;
}

export interface Flash {
  pos: Point;
  onTime: boolean;
  born: number;
}

export interface FleetConfig {
  auto: number;
  moto: number;
  bici: number;
}

export interface RunConfig {
  scenario: Scenario;
  startDate: string; // aaaa-mm-dd
  startTime: string; // HH:MM
  fleet: FleetConfig;
  capacities: { noroeste: number; este: number };
  shiftStarts: [number, number, number]; // minutos desde medianoche
}

export interface SimSnapshot {
  scenario: Scenario;
  configured: boolean;
  running: boolean;
  waitingFirstOrder: boolean;
  collapsed: boolean;
  finished: boolean;
  simMin: number;
  runStartSimMin: number;
  cycleDay: number;
  epochDate: string; // fecha calendario del minuto 0, aaaa-mm-dd
  runElapsedMs: number; // tiempo real de ejecución acumulado
  shiftStarts: number[];
  fleet: FleetConfig;
  vehicles: Vehicle[];
  orders: Order[];
  orderHistory: ClosedOrder[];
  incidents: Incident[];
  incidentHistory: Incident[]; // incidencias ya terminadas, la más reciente primero
  warehouses: Warehouse[];
  stats: Stats;
  flashes: Flash[];
  files: Record<FileKind, number | null>; // registros cargados por archivo (null = sin archivo)
}

export interface LogEvent {
  id: number;
  simMin: number;
  text: string; // admite **negrita**
  kind: LogKind;
}

export interface OrderInput {
  clientId: string;
  qty: number;
  hourLimit: number;
  x: number;
  y: number;
}

export type OrderResult = { ok: true; id: number; enRiesgo: boolean } | { ok: false; motivo: string };

export interface FileLoadSummary {
  kind: FileKind;
  count: number;
  immediate: number;
  message: string;
}

export interface Catalogos {
  vehicleTypes: VehicleTypeInfo[];
  fallaTypes: FallaTypeInfo[];
  modalidades: { horas: number; label: string }[];
}

export interface VehicleTypeInfo {
  key: VehicleTypeKey;
  label: string;
  plural: string;
  prefix: string;
  capacity: number;
  speed: number;
  cost: number;
  colorVar: string;
  emoji: string;
}

export interface FallaTypeInfo {
  tipo: FallaTipo;
  label: string;
  short: string;
  minMin: number;
  maxMin: number;
  colorVar: string;
}
