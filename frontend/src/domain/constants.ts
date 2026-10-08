// Parámetros del caso PaqRap (Situación Auténtica) y de la simulación.
import type { FallaTypeInfo, Scenario, VehicleTypeInfo, VehicleTypeKey, Warehouse } from './types';

/** Minutos simulados por segundo real del modo local en Día a día (demostración; el backend usa tiempo real). */
export const SIM_MIN_PER_SEC = 10;
/** Velocidad base (×1) de Simulación 5D y Colapso: 5 días (7200 min) duran ≈ 40 min reales. */
export const SIM_BASE_MIN_PER_SEC = 3;
/** Multiplicadores de velocidad disponibles en el monitor para 5D y Colapso. */
export const SPEED_FACTORS = [1, 2, 5, 10] as const;
export type SpeedFactor = (typeof SPEED_FACTORS)[number];
/** Retícula urbana: 70 km (X) × 50 km (Y), nodos cada 1 km. */
export const GRID_W = 70;
export const GRID_H = 50;
export const CYCLE_DAYS_5D = 5;
export const SHIFT_DUR = 8 * 60;
export const DELIVERY_MIN = 60; // tiempo de entrega al destinatario
export const DEFAULT_SHIFT_STARTS: [number, number, number] = [7 * 60, 15 * 60, 23 * 60];

export const VEHICLE_TYPES: Record<VehicleTypeKey, VehicleTypeInfo> = {
  auto: { key: 'auto', label: 'Auto', plural: 'Autos', prefix: 'TA', capacity: 24, speed: 40, cost: 8, colorVar: '--veh-auto', emoji: '🚗' },
  moto: { key: 'moto', label: 'Moto', plural: 'Motos', prefix: 'TM', capacity: 8, speed: 25, cost: 6, colorVar: '--veh-moto', emoji: '🏍️' },
  bici: { key: 'bici', label: 'Bicicleta', plural: 'Bicicletas', prefix: 'TB', capacity: 4, speed: 12, cost: 3, colorVar: '--veh-bici', emoji: '🚲' },
};
export const VEHICLE_TYPE_KEYS: VehicleTypeKey[] = ['auto', 'moto', 'bici'];

/** Posiciones fijas: central (27,14), Nor-Oeste (12,38), Este (57,27). */
export function initialWarehouses(capNoroeste = 1000, capEste = 1000): Warehouse[] {
  return [
    { id: 'central', name: 'Almacén Central', shortName: 'Central', pos: { x: 27, y: 14 }, infinite: true, capacity: 0, stock: 0, dispatchedToday: 0 },
    { id: 'noroeste', name: 'Almacén Nor-Oeste', shortName: 'Nor-Oeste', pos: { x: 12, y: 38 }, infinite: false, capacity: capNoroeste, stock: Math.round(capNoroeste * 0.76), dispatchedToday: 0 },
    { id: 'este', name: 'Almacén Este', shortName: 'Este', pos: { x: 57, y: 27 }, infinite: false, capacity: capEste, stock: Math.round(capEste * 0.84), dispatchedToday: 0 },
  ];
}

export const PRIORITY_BUCKETS = [4, 8, 12, 18, 36] as const;

export const MODALIDADES = [
  { horas: 36, label: 'Regular (36 h)' },
  { horas: 18, label: 'Priorizada (18 h)' },
  { horas: 12, label: 'Priorizada (12 h)' },
  { horas: 8, label: 'Priorizada (8 h)' },
  { horas: 4, label: 'Priorizada (4 h)' },
];

export const FALLA_TYPES: FallaTypeInfo[] = [
  { tipo: 1, label: 'Tipo 1 · leve', short: 'Leve', minMin: 20, maxMin: 40, colorVar: '--warning' },
  { tipo: 2, label: 'Tipo 2 · moderada', short: 'Moderada', minMin: 45, maxMin: 80, colorVar: '--serious' },
  { tipo: 3, label: 'Tipo 3 · grave', short: 'Grave', minMin: 90, maxMin: 150, colorVar: '--critical' },
];

export const SCENARIO_LABEL: Record<Scenario, string> = {
  diaria: 'Operación día a día',
  '5d': 'Simulación 5 días',
  colapso: 'Hasta el colapso',
};

export const SCENARIO_SHORT: Record<Scenario, string> = {
  diaria: 'Día a día',
  '5d': 'Simulación 5D',
  colapso: 'Colapso',
};

export const SCENARIO_DESC: Record<Scenario, string> = {
  diaria:
    'Operación en vivo con la fecha de hoy. El reloj no avanza hasta que registres el primer pedido en Pedidos.',
  '5d': 'Recibe pedidos, bloqueos, averías y mantenimientos por archivo. Corre 5 días simulados y se detiene.',
  colapso:
    'Corre sin límite de días hasta que un pedido no se entrega dentro de su plazo; en ese momento se detiene y reporta el colapso.',
};
