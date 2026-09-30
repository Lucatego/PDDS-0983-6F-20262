// Tabla de datos con paginación y estado vacío. Las columnas definen cómo se pinta cada celda.
import { useEffect, useState, type ReactNode } from 'react';
import { Icon, type IconName } from '@/components/ui/Icon';
import { EmptyState } from '@/components/ui/primitives';

export interface Column<T> {
  key: string;
  header: string;
  cell: (row: T) => ReactNode;
  className?: string;
  align?: 'left' | 'right';
}

export function DataTable<T>({
  columns,
  rows,
  rowKey,
  onRowClick,
  rowTitle,
  pageSize = 15,
  empty,
}: {
  columns: Column<T>[];
  rows: T[];
  rowKey: (r: T) => string | number;
  onRowClick?: (r: T) => void;
  rowTitle?: (r: T) => string | undefined;
  pageSize?: number;
  empty: { icon: IconName; title: string; text?: string };
}) {
  const [page, setPage] = useState(0);
  const pages = Math.max(1, Math.ceil(rows.length / pageSize));
  useEffect(() => {
    if (page >= pages) setPage(pages - 1);
  }, [page, pages]);
  const slice = rows.slice(page * pageSize, page * pageSize + pageSize);

  if (!rows.length)
    return (
      <EmptyState icon={empty.icon} title={empty.title}>
        {empty.text}
      </EmptyState>
    );

  return (
    <>
      <div className="overflow-x-auto">
        <table className="data-table">
          <thead>
            <tr>
              {columns.map((c) => (
                <th key={c.key} className={c.align === 'right' ? 'text-right' : ''}>
                  {c.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {slice.map((r) => {
              const clickable = !!onRowClick && rowTitle?.(r) !== undefined;
              return (
                <tr
                  key={rowKey(r)}
                  data-clickable={clickable}
                  title={clickable ? rowTitle?.(r) : undefined}
                  onClick={clickable ? () => onRowClick!(r) : undefined}
                  tabIndex={clickable ? 0 : undefined}
                  onKeyDown={clickable ? (e) => e.key === 'Enter' && onRowClick!(r) : undefined}
                >
                  {columns.map((c) => (
                    <td key={c.key} className={`${c.className ?? ''} ${c.align === 'right' ? 'text-right' : ''}`}>
                      {c.cell(r)}
                    </td>
                  ))}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <div className="flex items-center justify-between gap-3 border-t border-line px-4 py-2.5 text-xs text-ink-3">
        <span>
          {page * pageSize + 1}–{Math.min(rows.length, (page + 1) * pageSize)} de <b className="text-ink-2">{rows.length}</b>
        </span>
        <div className="flex items-center gap-1">
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => setPage(page - 1)} disabled={page === 0} aria-label="Página anterior">
            <Icon name="chevronLeft" size={14} />
          </button>
          <span className="px-1">
            Página {page + 1} de {pages}
          </span>
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => setPage(page + 1)} disabled={page >= pages - 1} aria-label="Página siguiente">
            <Icon name="chevronRight" size={14} />
          </button>
        </div>
      </div>
    </>
  );
}

export function SearchField({ value, onChange, placeholder, label }: { value: string; onChange: (v: string) => void; placeholder: string; label: string }) {
  return (
    <label className="field min-w-[200px] flex-1 sm:max-w-xs">
      <span className="field-label">{label}</span>
      <span className="relative">
        <Icon name="search" className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-ink-3" />
        <input className="input pl-9" value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} />
      </span>
    </label>
  );
}

export function SelectField({
  value,
  onChange,
  label,
  options,
}: {
  value: string;
  onChange: (v: string) => void;
  label: string;
  options: { value: string; label: string }[];
}) {
  return (
    <label className="field min-w-[160px]">
      <span className="field-label">{label}</span>
      <select className="select" value={value} onChange={(e) => onChange(e.target.value)}>
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </label>
  );
}
