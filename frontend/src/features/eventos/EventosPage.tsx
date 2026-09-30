import { useMemo, useState } from 'react';
import { SearchField } from '@/components/data/DataTable';
import { EmptyState, PageHeader, RichText } from '@/components/ui/primitives';
import type { LogKind } from '@/domain/types';
import { fmtLogTime } from '@/engine/SimulationEngine';
import { useSimStore } from '@/store/simStore';

const KINDS: { key: LogKind | ''; label: string; dot: string }[] = [
  { key: '', label: 'Todos', dot: 'bg-ink-3' },
  { key: 'critical', label: 'Críticos', dot: 'bg-critical' },
  { key: 'warning', label: 'Alertas', dot: 'bg-warning' },
  { key: 'good', label: 'Resueltos', dot: 'bg-good' },
  { key: 'accent', label: 'Operación', dot: 'bg-accent' },
];
const DOT: Record<LogKind, string> = { critical: 'bg-critical', warning: 'bg-warning', good: 'bg-good', accent: 'bg-accent' };
const KIND_TEXT: Record<LogKind, string> = { critical: 'Crítico', warning: 'Alerta', good: 'Resuelto', accent: 'Operación' };

export function EventosPage() {
  const logs = useSimStore((s) => s.logs);
  const [kind, setKind] = useState<LogKind | ''>('');
  const [q, setQ] = useState('');
  const rows = useMemo(() => {
    const query = q.trim().toLowerCase();
    return logs.filter((e) => (!kind || e.kind === kind) && (!query || e.text.toLowerCase().includes(query))).reverse();
  }, [logs, kind, q]);

  return (
    <>
      <PageHeader crumbs={['Sistema', 'Registro de eventos']} title="Registro de eventos" description="Bitácora de la ejecución: asignaciones, entregas, incidencias, replanificaciones y cambios de estado." />
      <div className="card overflow-hidden">
        <div className="flex flex-wrap items-end gap-3 border-b border-line px-4 py-3">
          <SearchField label="Buscar" value={q} onChange={setQ} placeholder="Pedido, unidad, almacén…" />
          <div className="field">
            <span className="field-label">Tipo</span>
            <div className="segmented" role="group" aria-label="Tipo de evento">
              {KINDS.map((k) => (
                <button key={k.key} type="button" aria-pressed={kind === k.key} onClick={() => setKind(k.key)} className="flex items-center gap-1.5">
                  <span className={`size-1.5 rounded-full ${k.dot}`} />
                  {k.label}
                </button>
              ))}
            </div>
          </div>
          <span className="ml-auto self-center text-xs text-ink-3">{rows.length} eventos</span>
        </div>
        {rows.length === 0 ? (
          <EmptyState icon="events" title="Sin eventos que mostrar" />
        ) : (
          <ol className="max-h-[calc(100dvh-280px)] divide-y divide-line overflow-y-auto">
            {rows.slice(0, 400).map((e) => (
              <li key={e.id} className="flex items-start gap-3 px-4 py-2.5 text-[13px] hover:bg-surface-2">
                <span className="num w-24 shrink-0 pt-px font-mono text-[11.5px] text-ink-3">{fmtLogTime(e)}</span>
                <span className="flex w-24 shrink-0 items-center gap-1.5 pt-0.5 text-[11.5px] text-ink-3">
                  <span className={`size-1.5 rounded-full ${DOT[e.kind]}`} />
                  {KIND_TEXT[e.kind]}
                </span>
                <span className="min-w-0 flex-1 text-ink-2">
                  <RichText text={e.text} />
                </span>
              </li>
            ))}
          </ol>
        )}
      </div>
    </>
  );
}
