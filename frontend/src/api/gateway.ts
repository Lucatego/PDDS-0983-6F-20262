// Contrato entre la interfaz y la fuente de datos de la simulación. La interfaz solo conoce esta
// interfaz: hoy la cumple el motor local (LocalGateway) y mañana el backend Spring Boot (StompGateway).
import type {
  Catalogos,
  FallaTipo,
  FileKind,
  FileLoadSummary,
  LogEvent,
  OrderInput,
  OrderResult,
  Point,
  RunConfig,
  SimSnapshot,
} from '@/domain/types';
import type { SpeedFactor } from '@/domain/constants';

export type ConnectionStatus = 'connecting' | 'online' | 'offline';
export type DataSourceMode = 'local' | 'server';

export interface GatewayListeners {
  onSnapshot(s: SimSnapshot): void;
  onLog(e: LogEvent): void;
  onStatus(s: ConnectionStatus): void;
}

export interface SimulationGateway {
  readonly mode: DataSourceMode;
  /** Se suscribe a las instantáneas y eventos; devuelve la función para desuscribirse. */
  connect(listeners: GatewayListeners): () => void;
  getCatalogos(): Promise<Catalogos>;
  configure(cfg: RunConfig): Promise<void>;
  start(): Promise<void>;
  stop(): Promise<void>;
  reset(): Promise<void>;
  /** Cambia la velocidad de la simulación en caliente (solo Simulación 5D y Colapso). */
  setSpeed(factor: SpeedFactor): Promise<void>;
  registerOrder(input: OrderInput): Promise<OrderResult>;
  registerOrderBatch(inputs: OrderInput[]): Promise<OrderResult[]>;
  loadFile(kind: FileKind, text: string): Promise<FileLoadSummary>;
  registerAveria(vehicleId: string, tipo: FallaTipo): Promise<void>;
  registerMantenimiento(vehicleId: string, horas: number): Promise<void>;
  registerBloqueo(nodes: Point[], horas: number): Promise<void>;
}
