import { useCallback, useEffect, useState } from 'react';
import { Icon } from '@/components/ui/Icon';
import { SCENARIO_LABEL } from '@/domain/constants';
import { useSimStore } from '@/store/simStore';
import { useUiStore } from '@/store/uiStore';
import { Camera } from './camera';
import { MapCanvas } from './MapCanvas';
import { LayerControls, MapLegend, MapSearch, ZoomControls } from './MapOverlays';
import { SidePanel } from './SidePanel';

export function MonitorPage() {
  const [camera] = useState(() => new Camera());
  const [unitPx, setUnitPx] = useState(10);
  const onScale = useCallback((u: number) => setUnitPx(u), []);
  const snap = useSimStore((s) => s.snapshot);
  const selected = useUiStore((s) => s.selected);
  const openGate = useUiStore((s) => s.openGate);
  const [panelOpen, setPanelOpen] = useState(true);
  useEffect(() => {
    if (selected) setPanelOpen(true); // una selección siempre se muestra
  }, [selected]);

  return (
    <div className="flex h-full">
      <section className="relative min-w-0 flex-1 overflow-hidden" aria-label="Mapa de operaciones">
        <MapCanvas camera={camera} onScale={onScale} />

        <div className="pointer-events-none absolute inset-x-3 top-3 flex flex-wrap items-start gap-2 sm:inset-x-4 sm:top-4">
          <div className="pointer-events-auto">
            <MapSearch />
          </div>
          <div className="pointer-events-auto hidden sm:block">
            <LayerControls />
          </div>
          <button
            type="button"
            className="pointer-events-auto ml-auto hidden h-10 items-center gap-2 rounded-xl border border-line bg-surface/95 px-3 text-xs font-medium text-ink-2 shadow-md backdrop-blur hover:text-ink xl:flex"
            onClick={() => setPanelOpen(!panelOpen)}
            aria-pressed={panelOpen}
          >
            <Icon name="sidebar" size={15} />
            {panelOpen ? 'Ocultar panel' : 'Mostrar panel'}
          </button>
        </div>

        <div className="pointer-events-none absolute bottom-3 left-3 sm:bottom-4 sm:left-4">
          <div className="pointer-events-auto hidden sm:block">
            <MapLegend />
          </div>
        </div>
        <div className="pointer-events-none absolute right-3 bottom-3 sm:right-4 sm:bottom-4">
          <div className="pointer-events-auto">
            <ZoomControls camera={camera} unitPx={unitPx} />
          </div>
        </div>

        {snap && !snap.configured && (
          <div className="absolute inset-0 flex items-center justify-center bg-bg/40 p-6 backdrop-blur-[1px]">
            <div className="animate-pop-in card max-w-md p-6 text-center shadow-lg">
              <span className="mx-auto mb-3 flex size-11 items-center justify-center rounded-xl bg-accent-soft text-accent-ink">
                <Icon name="play" size={18} />
              </span>
              <h2 className="text-base font-semibold text-ink">No hay una ejecución en curso</h2>
              <p className="mt-1.5 text-[13px] text-ink-3">
                Configura el escenario ({Object.values(SCENARIO_LABEL).join(', ').toLowerCase()}), la flota y la fecha de inicio para ver la operación en el mapa.
              </p>
              <button type="button" className="btn btn-primary btn-lg mt-4" onClick={() => openGate()}>
                Configurar ejecución
              </button>
            </div>
          </div>
        )}
      </section>

      {/* panel lateral: fijo en pantallas anchas; hoja inferior al seleccionar algo en pantallas estrechas */}
      {panelOpen && (
        <aside className="hidden w-[340px] shrink-0 border-l border-line bg-surface xl:block" aria-label="Panel de estado">
          <SidePanel />
        </aside>
      )}
      {selected && (
        <aside className="animate-pop-in fixed inset-x-0 bottom-0 z-30 max-h-[60dvh] overflow-hidden rounded-t-2xl border-t border-line bg-surface shadow-lg xl:hidden" aria-label="Detalle">
          <SidePanel />
        </aside>
      )}
    </div>
  );
}
