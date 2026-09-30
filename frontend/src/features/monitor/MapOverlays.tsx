import { useMemo, useState } from 'react';
import { Icon } from '@/components/ui/Icon';
import { VEHICLE_TYPES, VEHICLE_TYPE_KEYS } from '@/domain/constants';
import { normVehId } from '@/domain/fileFormats';
import { VEHICLE_STATE, isOnMap } from '@/domain/labels';
import { useSimStore } from '@/store/simStore';
import { useUiStore } from '@/store/uiStore';
import type { Camera } from './camera';

/** Buscador de unidades y almacenes con resultados navegables. */
export function MapSearch() {
  const snap = useSimStore((s) => s.snapshot);
  const select = useUiStore((s) => s.select);
  const focusOn = useUiStore((s) => s.focusOn);
  const [q, setQ] = useState('');
  const [open, setOpen] = useState(false);

  const results = useMemo(() => {
    if (!snap || !q.trim()) return [];
    const query = q.trim().toLowerCase();
    const code = normVehId(q);
    const veh = snap.vehicles
      .filter((v) => v.id.toLowerCase().includes(query) || v.id === code)
      .slice(0, 6)
      .map((v) => ({ key: v.id, label: v.id, sub: `${VEHICLE_TYPES[v.type].label} · ${VEHICLE_STATE[v.state].label}`, onMap: isOnMap(v), kind: 'vehicle' as const, pos: v.pos }));
    const wh = snap.warehouses
      .filter((w) => w.name.toLowerCase().includes(query) || w.id.startsWith(query))
      .map((w) => ({ key: w.id, label: w.name, sub: `Nodo (${w.pos.x}, ${w.pos.y})`, onMap: true, kind: 'warehouse' as const, pos: w.pos }));
    return [...wh, ...veh];
  }, [snap, q]);

  const pick = (r: (typeof results)[number]) => {
    if (r.kind === 'vehicle') select({ type: 'vehicle', id: r.key });
    else select({ type: 'warehouse', id: r.key as 'central' | 'noroeste' | 'este' });
    focusOn(r.pos, r.kind === 'vehicle' ? 3 : 2);
    setOpen(false);
    setQ('');
  };

  return (
    <div className="relative w-[min(300px,calc(100vw-2rem))]">
      <div className="flex h-10 items-center gap-2 rounded-xl border border-line bg-surface/95 px-3 shadow-md backdrop-blur">
        <Icon name="search" className="shrink-0 text-ink-3" />
        <input
          value={q}
          onChange={(e) => {
            setQ(e.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => setTimeout(() => setOpen(false), 150)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && results[0]) pick(results[0]);
            if (e.key === 'Escape') setOpen(false);
          }}
          placeholder="Buscar unidad (TA03) o almacén"
          aria-label="Buscar unidad o almacén"
          className="min-w-0 flex-1 bg-transparent text-[13px] text-ink outline-none placeholder:text-ink-3"
        />
        {q && (
          <button type="button" className="text-ink-3 hover:text-ink" onClick={() => setQ('')} aria-label="Limpiar búsqueda">
            <Icon name="x" size={14} />
          </button>
        )}
      </div>
      {open && q.trim() && (
        <div className="animate-pop-in absolute top-12 left-0 z-10 w-full overflow-hidden rounded-xl border border-line bg-surface shadow-lg" role="listbox">
          {results.length === 0 ? (
            <div className="px-3 py-3 text-xs text-ink-3">Sin coincidencias.</div>
          ) : (
            results.map((r) => (
              <button
                key={r.key}
                type="button"
                role="option"
                aria-selected={false}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => pick(r)}
                className="flex w-full items-center gap-3 px-3 py-2 text-left hover:bg-surface-2"
              >
                <Icon name={r.kind === 'vehicle' ? 'truck' : 'warehouse'} className="text-ink-3" />
                <span className="min-w-0 flex-1">
                  <span className="block text-[13px] font-medium text-ink">{r.label}</span>
                  <span className="block text-xs text-ink-3">{r.sub}</span>
                </span>
                {!r.onMap && <span className="badge badge-neutral">en almacén</span>}
              </button>
            ))
          )}
        </div>
      )}
    </div>
  );
}

/** Filtros por tipo de vehículo y capas visibles. */
export function LayerControls() {
  const snap = useSimStore((s) => s.snapshot);
  const typeFilter = useUiStore((s) => s.typeFilter);
  const toggleType = useUiStore((s) => s.toggleType);
  const showRoutes = useUiStore((s) => s.showRoutes);
  const showTrails = useUiStore((s) => s.showTrails);
  const toggleRoutes = useUiStore((s) => s.toggleRoutes);
  const toggleTrails = useUiStore((s) => s.toggleTrails);

  const chip = (active: boolean) =>
    `inline-flex h-8 items-center gap-1.5 rounded-lg border px-2.5 text-xs font-medium transition-colors ${
      active ? 'border-line bg-surface text-ink shadow-sm' : 'border-transparent bg-transparent text-ink-3 line-through decoration-ink-3/60'
    }`;

  return (
    <div className="flex flex-wrap items-center gap-1 rounded-xl border border-line bg-surface-2/95 p-1 shadow-md backdrop-blur">
      {VEHICLE_TYPE_KEYS.map((k) => {
        const t = VEHICLE_TYPES[k];
        const list = snap?.vehicles.filter((v) => v.type === k) ?? [];
        const busy = list.filter(isOnMap).length;
        return (
          <button key={k} type="button" className={chip(typeFilter[k])} aria-pressed={typeFilter[k]} onClick={() => toggleType(k)} title={`Mostrar u ocultar ${t.plural.toLowerCase()}`}>
            <span className="size-2.5 rounded-full" style={{ background: `var(${t.colorVar})` }} />
            {t.plural}
            <span className="num font-mono text-[11px] text-ink-3">
              {busy}/{list.length}
            </span>
          </button>
        );
      })}
      <span className="mx-1 h-5 w-px bg-line" />
      <button type="button" className={chip(showRoutes)} aria-pressed={showRoutes} onClick={toggleRoutes}>
        <Icon name="route" size={14} />
        Rutas
      </button>
      <button type="button" className={chip(showTrails)} aria-pressed={showTrails} onClick={toggleTrails}>
        <Icon name="layers" size={14} />
        Estelas
      </button>
    </div>
  );
}

export function ZoomControls({ camera, unitPx }: { camera: Camera; unitPx: number }) {
  const btn = 'flex size-9 items-center justify-center text-ink-2 hover:bg-surface-3 hover:text-ink';
  const barKm = unitPx * 5 > 140 ? 1 : unitPx * 10 > 140 ? 5 : 10;
  return (
    <div className="flex items-end gap-2">
      <div className="flex flex-col items-start gap-1 rounded-lg border border-line bg-surface/95 px-2 py-1.5 shadow-sm backdrop-blur" aria-label={`Escala: ${barKm} km`}>
        <span className="h-1 rounded-full bg-ink-2" style={{ width: Math.max(12, unitPx * barKm) }} />
        <span className="font-mono text-[10px] text-ink-3">{barKm} km</span>
      </div>
      <div className="flex flex-col overflow-hidden rounded-xl border border-line bg-surface/95 shadow-md backdrop-blur">
        <button type="button" className={btn} onClick={() => camera.zoomAt(camera.W / 2, camera.H / 2, 1.4)} aria-label="Acercar" title="Acercar (+)">
          <Icon name="plus" />
        </button>
        <span className="h-px bg-line" />
        <button type="button" className={btn} onClick={() => camera.zoomAt(camera.W / 2, camera.H / 2, 1 / 1.4)} aria-label="Alejar" title="Alejar (−)">
          <Icon name="zoomOut" />
        </button>
        <span className="h-px bg-line" />
        <button type="button" className={btn} onClick={() => camera.reset()} aria-label="Ver toda la retícula" title="Ver toda la retícula (0)">
          <Icon name="expand" />
        </button>
      </div>
    </div>
  );
}

export function MapLegend() {
  const [open, setOpen] = useState(true);
  return (
    <div className="rounded-xl border border-line bg-surface/95 text-xs shadow-md backdrop-blur">
      <button type="button" className="flex w-full items-center justify-between gap-6 px-3 py-2 font-medium text-ink" onClick={() => setOpen(!open)} aria-expanded={open}>
        Leyenda
        <Icon name="chevronDown" size={14} className={`text-ink-3 transition-transform ${open ? '' : '-rotate-90'}`} />
      </button>
      {open && (
        <ul className="grid gap-1.5 border-t border-line px-3 py-2.5 text-ink-2">
          <li className="flex items-center gap-2">
            <span className="flex gap-0.5">
              <i className="size-2.5 rotate-45 border-2 border-good" />
              <i className="size-2.5 rotate-45 border-2 border-warning" />
              <i className="size-2.5 rotate-45 border-2 border-critical" />
            </span>
            Pedido según plazo restante
          </li>
          <li className="flex items-center gap-2">
            <i className="size-2.5 rotate-45 border-2 border-dashed border-ink-3" />
            Pedido por asignar (borde punteado)
          </li>
          <li className="flex items-center gap-2">
            <i className="h-0 w-5 border-t-2 border-dashed border-veh-auto" />
            Ruta asignada
          </li>
          <li className="flex items-center gap-2">
            <i className="h-1.5 w-5 rounded-full bg-critical" />
            Calle bloqueada
          </li>
          <li className="flex items-center gap-2">
            <i className="size-3 rounded border-2 border-accent bg-surface" />
            Almacén (punto = nivel de stock)
          </li>
          <li className="flex items-center gap-2">
            <i className="size-2 rounded-full bg-critical" />
            Pedido priorizado (menos de 36 h)
          </li>
        </ul>
      )}
    </div>
  );
}
