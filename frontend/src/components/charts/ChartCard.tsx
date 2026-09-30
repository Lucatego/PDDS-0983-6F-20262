// Tarjeta de gráfico con alternancia Gráfico / Tabla: la tabla es el equivalente accesible
// y permite leer cada valor sin depender del color ni del tooltip.
import { useState, type ReactNode } from 'react';
import { Icon } from '@/components/ui/Icon';

export interface TableView {
  columns: string[];
  rows: (string | number)[][];
}

export function ChartCard({
  title,
  subtitle,
  children,
  table,
  legend,
  className = '',
}: {
  title: string;
  subtitle?: ReactNode;
  children: ReactNode;
  table: TableView;
  legend?: ReactNode;
  className?: string;
}) {
  const [view, setView] = useState<'chart' | 'table'>('chart');
  return (
    <section className={`card flex flex-col ${className}`}>
      <div className="flex items-start justify-between gap-3 px-4 pt-4">
        <div className="min-w-0">
          <h3 className="card-title">{title}</h3>
          {subtitle && <p className="card-subtitle mt-0.5">{subtitle}</p>}
        </div>
        <div className="segmented shrink-0" role="group" aria-label={`Vista de ${title}`}>
          <button type="button" aria-pressed={view === 'chart'} onClick={() => setView('chart')} title="Ver gráfico">
            <Icon name="chart" size={14} />
            <span className="sr-only">Gráfico</span>
          </button>
          <button type="button" aria-pressed={view === 'table'} onClick={() => setView('table')} title="Ver tabla">
            <Icon name="table" size={14} />
            <span className="sr-only">Tabla</span>
          </button>
        </div>
      </div>
      {legend && view === 'chart' && <div className="flex flex-wrap gap-x-4 gap-y-1 px-4 pt-2 text-xs text-ink-2">{legend}</div>}
      <div className="min-h-0 flex-1 px-2 pt-1 pb-3">
        {view === 'chart' ? (
          children
        ) : (
          <div className="max-h-[260px] overflow-auto px-2">
            <table className="data-table">
              <thead>
                <tr>
                  {table.columns.map((c) => (
                    <th key={c}>{c}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {table.rows.map((r, i) => (
                  <tr key={i}>
                    {r.map((c, j) => (
                      <td key={j} className={j ? 'num font-mono text-xs' : ''}>
                        {c}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  );
}

export function LegendItem({ color, label, shape = 'square' }: { color: string; label: string; shape?: 'square' | 'line' }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      {shape === 'line' ? <i className="h-0.5 w-3.5 rounded-full" style={{ background: color }} /> : <i className="size-2.5 rounded-sm" style={{ background: color }} />}
      {label}
    </span>
  );
}
