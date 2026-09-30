import { useMemo } from 'react';
import type { Incident } from '@/domain/types';
import { useSimStore } from '@/store/simStore';

export type IncidentRow<K extends Incident['kind']> = Extract<Incident, { kind: K }> & { activa: boolean };

/** Incidencias activas e historial de un tipo, la más reciente primero. */
export function useIncidentRows<K extends Incident['kind']>(kind: K): IncidentRow<K>[] {
  const snap = useSimStore((s) => s.snapshot);
  return useMemo(() => {
    if (!snap) return [];
    const act = snap.incidents.filter((i) => i.kind === kind).map((i) => ({ ...i, activa: true }));
    const hist = snap.incidentHistory.filter((i) => i.kind === kind).map((i) => ({ ...i, activa: false }));
    return [...act.sort((a, b) => b.since - a.since), ...hist] as IncidentRow<K>[];
  }, [snap, kind]);
}
