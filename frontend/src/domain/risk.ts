// Semáforo configurable (verde / ámbar / rojo) para pedidos, almacenes e indicadores.
import type { Order, RiskLevel, Warehouse } from './types';

export interface Thresholds {
  green: number; // % mínimo para verde
  amber: number; // % mínimo para ámbar
}

export const DEFAULT_THRESHOLDS: Thresholds = { green: 70, amber: 35 };

export function riskLevel(pct: number, t: Thresholds): RiskLevel {
  if (pct >= t.green) return 'good';
  if (pct >= t.amber) return 'warning';
  return 'critical';
}

/** Porcentaje del plazo que aún queda (100 = recién registrado, 0 = vencido). */
export function slackPct(o: Pick<Order, 'createdAt' | 'deadline'>, simMin: number): number {
  const total = o.deadline - o.createdAt;
  return total > 0 ? Math.max(0, (o.deadline - simMin) / total) * 100 : 0;
}

export function warehouseLevel(w: Warehouse, t: Thresholds): RiskLevel {
  return w.infinite ? 'good' : riskLevel((w.stock / Math.max(1, w.capacity)) * 100, t);
}

export const RISK_LABEL: Record<RiskLevel, string> = {
  good: 'En plazo',
  warning: 'En riesgo',
  critical: 'Crítico',
};
