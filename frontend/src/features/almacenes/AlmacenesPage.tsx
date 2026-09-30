import { useNavigate } from 'react-router';
import { Icon } from '@/components/ui/Icon';
import { Badge, Meter, PageHeader } from '@/components/ui/primitives';
import { isOnMap } from '@/domain/labels';
import { warehouseLevel } from '@/domain/risk';
import { useSimStore } from '@/store/simStore';
import { useUiStore } from '@/store/uiStore';

const LEVEL_TEXT = { good: 'Normal', warning: 'Bajo', critical: 'Crítico' } as const;

export function AlmacenesPage() {
  const snap = useSimStore((s) => s.snapshot);
  const thresholds = useUiStore((s) => s.thresholds);
  const select = useUiStore((s) => s.select);
  const focusOn = useUiStore((s) => s.focusOn);
  const navigate = useNavigate();
  if (!snap) return null;

  return (
    <>
      <PageHeader
        crumbs={['Gestión', 'Almacenes']}
        title="Almacenes e inventario"
        description="El almacén central tiene stock ilimitado. Los intermedios se recargan a su capacidad todos los días a las 23:59:59."
      />
      <div className="grid gap-4 lg:grid-cols-3">
        {snap.warehouses.map((w) => {
          const level = warehouseLevel(w, thresholds);
          const outgoing = snap.orders.filter((o) => o.warehouseId === w.id && o.status === 'assigned');
          const arriving = snap.vehicles.filter((v) => v.state === 'returning' && v.returnTarget === w.id).length;
          const based = snap.vehicles.filter((v) => v.home === w.id && !isOnMap(v)).length;
          const pct = w.infinite ? 100 : (w.stock / w.capacity) * 100;
          return (
            <section key={w.id} className="card flex flex-col">
              <div className="card-header">
                <div className="flex items-center gap-3">
                  <span className={`flex size-10 items-center justify-center rounded-xl ${w.infinite ? 'bg-accent text-on-accent' : 'bg-accent-soft text-accent-ink'}`}>
                    <Icon name="warehouse" size={18} />
                  </span>
                  <div>
                    <h2 className="card-title">{w.name}</h2>
                    <p className="card-subtitle">
                      {w.infinite ? 'Central' : 'Intermedio'} · nodo ({w.pos.x}, {w.pos.y})
                    </p>
                  </div>
                </div>
                {!w.infinite && <Badge tone={level}>{LEVEL_TEXT[level]}</Badge>}
              </div>
              <div className="flex flex-1 flex-col gap-4 p-4">
                <div>
                  <div className="mb-2 flex items-baseline justify-between">
                    <span className="text-xs text-ink-3">Stock disponible</span>
                    <span className="text-2xl font-semibold tracking-tight text-ink">
                      {w.infinite ? '∞' : w.stock}
                      {!w.infinite && <span className="text-sm font-medium text-ink-3"> / {w.capacity}</span>}
                    </span>
                  </div>
                  {w.infinite ? <div className="h-1.5 rounded-full bg-accent-soft" /> : <Meter pct={pct} tone={level} label={`Stock de ${w.name}`} />}
                  {!w.infinite && <div className="mt-1.5 text-right text-xs text-ink-3">{Math.round(pct)}% de ocupación</div>}
                </div>
                <dl className="grid grid-cols-3 gap-2 text-center">
                  {[
                    ['Despachos hoy', w.dispatchedToday],
                    ['Pedidos saliendo', outgoing.length],
                    ['Unidades llegando', arriving],
                  ].map(([k, v]) => (
                    <div key={k} className="rounded-lg bg-surface-2 px-2 py-2.5">
                      <dd className="text-lg font-semibold text-ink">{v}</dd>
                      <dt className="text-[11px] text-ink-3">{k}</dt>
                    </div>
                  ))}
                </dl>
                <div className="text-xs text-ink-3">{based === 1 ? '1 unidad disponible' : `${based} unidades disponibles`} con base en este almacén.</div>
                <button
                  type="button"
                  className="btn btn-secondary mt-auto"
                  onClick={() => {
                    select({ type: 'warehouse', id: w.id });
                    focusOn(w.pos, 2);
                    navigate('/monitor');
                  }}
                >
                  <Icon name="mapPin" size={15} />
                  Ver en el mapa
                </button>
              </div>
            </section>
          );
        })}
      </div>
    </>
  );
}
