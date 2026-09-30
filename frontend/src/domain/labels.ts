// Etiquetas y tonos de estado para la interfaz (texto + color, nunca solo color).
import type { Tone } from '@/components/ui/primitives';
import { remainingDistance } from './grid';
import { fmtTime } from './time';
import type { ClosedOrder, Order, Vehicle, VehicleState } from './types';

export const VEHICLE_STATE: Record<VehicleState, { label: string; tone: Tone }> = {
  idle: { label: 'Disponible', tone: 'good' },
  break: { label: 'Refrigerio', tone: 'neutral' },
  toClient: { label: 'En ruta al cliente', tone: 'accent' },
  atClient: { label: 'Entregando', tone: 'accent' },
  returning: { label: 'Retornando', tone: 'neutral' },
  broken: { label: 'Averiada', tone: 'critical' },
  maintenance: { label: 'Mantenimiento', tone: 'warning' },
};

export const isOnMap = (v: Vehicle) => v.state !== 'idle' && v.state !== 'break';
export const isBusy = isOnMap;

export type OrderEstado = 'registrado' | 'reprogramado' | 'en ruta' | 'entregado' | 'no cumplido';

export const ORDER_ESTADO: Record<OrderEstado, Tone> = {
  registrado: 'neutral',
  reprogramado: 'warning',
  'en ruta': 'accent',
  entregado: 'good',
  'no cumplido': 'critical',
};

export function orderEstado(o: Order | ClosedOrder): OrderEstado {
  if ('estadoFinal' in o) return o.estadoFinal;
  if (o.status === 'assigned') return 'en ruta';
  return o.reprogramado ? 'reprogramado' : 'registrado';
}

export function modalidadLabel(h: number): string {
  return h >= 36 ? 'Regular · 36 h' : `Priorizada · ${h} h`;
}

export function etaText(v: Vehicle, simMin: number): string {
  if (v.state === 'atClient') return `Entregando · ${Math.max(0, Math.round(v.timer))} min`;
  if (!v.path) return '—';
  const mins = (remainingDistance(v) / v.speed) * 60;
  return `${Math.round(mins)} min · llega ${fmtTime(simMin + mins)}`;
}

export const fmtMoney = (n: number) =>
  new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN', maximumFractionDigits: 0 }).format(n);

export const fmtInt = (n: number) => new Intl.NumberFormat('es-PE').format(Math.round(n));
