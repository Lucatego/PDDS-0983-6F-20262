// Panel lateral del monitor: resumen operativo cuando no hay selección, detalle cuando la hay.
import { useState } from 'react';
import { Link } from 'react-router';
import { useRegisterAveria } from '@/api/hooks';
import { Icon } from '@/components/ui/Icon';
import { Badge, Meter, RichText, TEXT_TONE } from '@/components/ui/primitives';
import { FALLA_TYPES, VEHICLE_TYPES, VEHICLE_TYPE_KEYS } from '@/domain/constants';
import { VEHICLE_STATE, etaText, fmtMoney, isOnMap, modalidadLabel } from '@/domain/labels';
import { RISK_LABEL, riskLevel, slackPct, warehouseLevel } from '@/domain/risk';
import { fmtDayTime, fmtMinutes, fmtTime } from '@/domain/time';
import type { FallaTipo, SimSnapshot } from '@/domain/types';
import { fmtLogTime } from '@/engine/SimulationEngine';
import { useSimStore } from '@/store/simStore';
import { useUiStore, type Selection } from '@/store/uiStore';

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-4 py-1.5 text-[13px]">
      <dt className="text-ink-3">{label}</dt>
      <dd className="text-right font-medium text-ink">{children}</dd>
    </div>
  );
}

function Section({ title, children, action }: { title: string; children: React.ReactNode; action?: React.ReactNode }) {
  return (
    <section className="border-b border-line px-4 py-4 last:border-b-0">
      <div className="mb-2.5 flex items-center justify-between">
        <h3 className="text-[11px] font-semibold tracking-wider text-ink-3 uppercase">{title}</h3>
        {action}
      </div>
      {children}
    </section>
  );
}

function Summary({ snap }: { snap: SimSnapshot }) {
  const thresholds = useUiStore((s) => s.thresholds);
  const select = useUiStore((s) => s.select);
  const focusOn = useUiStore((s) => s.focusOn);
  const logs = useSimStore((s) => s.logs);
  const delivered = snap.stats.onTime + snap.stats.late;
  const sla = delivered ? Math.round((snap.stats.onTime / delivered) * 100) : null;
  const slaTone = sla === null ? 'neutral' : riskLevel(sla, thresholds);
  const pending = snap.orders.filter((o) => o.status === 'pending').length;
  const inTransit = snap.orders.filter((o) => o.status === 'assigned').reduce((a, o) => a + o.qty, 0);
  const faults = snap.incidents.filter((i) => i.kind === 'falla').length;
  const blocks = snap.incidents.filter((i) => i.kind === 'bloqueo').length;

  const kpis = [
    { label: 'Pedidos activos', value: snap.orders.length, sub: `${pending} por asignar` },
    { label: 'Cumplimiento', value: sla === null ? '—' : `${sla}%`, sub: delivered ? `${snap.stats.onTime}/${delivered} a tiempo` : 'sin entregas', tone: slaTone },
    { label: 'En tránsito', value: inTransit, sub: 'paquetes' },
    { label: 'Incidencias', value: faults + blocks, sub: `${blocks} bloqueos · ${faults} averías`, tone: faults + blocks ? 'warning' : 'neutral' },
  ] as const;

  return (
    <>
      <Section title="Resumen operativo">
        <div className="grid grid-cols-2 gap-2">
          {kpis.map((k) => (
            <div key={k.label} className="rounded-lg border border-line bg-surface-2 px-3 py-2.5">
              <div className="text-[11.5px] text-ink-3">{k.label}</div>
              <div className={`mt-0.5 text-xl font-semibold tracking-tight ${'tone' in k ? TEXT_TONE[k.tone] : 'text-ink'}`}>{k.value}</div>
              <div className="truncate text-[11px] text-ink-3">{k.sub}</div>
            </div>
          ))}
        </div>
        <div className="mt-3 flex items-center justify-between rounded-lg bg-surface-2 px-3 py-2 text-xs">
          <span className="text-ink-3">Costo logístico acumulado</span>
          <span className="num font-mono font-semibold text-ink">{fmtMoney(snap.stats.cost)}</span>
        </div>
      </Section>

      <Section title="Flota" action={<Link to="/flota" className="text-xs font-medium text-accent-ink hover:underline">Ver flota</Link>}>
        <ul className="grid gap-3">
          {VEHICLE_TYPE_KEYS.map((k) => {
            const t = VEHICLE_TYPES[k];
            const list = snap.vehicles.filter((v) => v.type === k);
            const busy = list.filter(isOnMap).length;
            const avail = list.length - busy;
            const pct = list.length ? (avail / list.length) * 100 : 100;
            const tone = riskLevel(pct, thresholds);
            return (
              <li key={k}>
                <div className="mb-1.5 flex items-center justify-between text-[13px]">
                  <span className="flex items-center gap-2 font-medium text-ink">
                    <span className="size-2.5 rounded-full" style={{ background: `var(${t.colorVar})` }} />
                    {t.plural}
                  </span>
                  <span className="text-xs text-ink-3">
                    <b className="num font-mono text-ink">{avail}</b> disponibles de {list.length}
                  </span>
                </div>
                <Meter pct={pct} tone={tone} label={`${t.plural} disponibles`} />
              </li>
            );
          })}
        </ul>
      </Section>

      <Section title="Almacenes" action={<Link to="/almacenes" className="text-xs font-medium text-accent-ink hover:underline">Ver almacenes</Link>}>
        <ul className="grid gap-2">
          {snap.warehouses.map((w) => {
            const level = warehouseLevel(w, thresholds);
            return (
              <li key={w.id}>
                <button
                  type="button"
                  className="w-full rounded-lg border border-line px-3 py-2 text-left hover:bg-surface-2"
                  onClick={() => {
                    select({ type: 'warehouse', id: w.id });
                    focusOn(w.pos, 2);
                  }}
                >
                  <div className="mb-1.5 flex items-center justify-between text-[13px]">
                    <span className="font-medium text-ink">{w.shortName}</span>
                    <span className="num font-mono text-xs text-ink-2">{w.infinite ? 'Ilimitado' : `${w.stock} / ${w.capacity}`}</span>
                  </div>
                  {w.infinite ? (
                    <div className="text-[11px] text-ink-3">Almacén central · {w.dispatchedToday} despachos hoy</div>
                  ) : (
                    <Meter pct={(w.stock / w.capacity) * 100} tone={level} label={`Stock de ${w.name}`} />
                  )}
                </button>
              </li>
            );
          })}
        </ul>
      </Section>

      <Section title="Últimos eventos" action={<Link to="/eventos" className="text-xs font-medium text-accent-ink hover:underline">Ver todos</Link>}>
        <ol className="grid gap-2.5">
          {logs
            .slice(-6)
            .reverse()
            .map((e) => (
              <li key={e.id} className="flex gap-2.5 text-xs leading-relaxed">
                <span className={`mt-1.5 size-1.5 shrink-0 rounded-full ${{ good: 'bg-good', warning: 'bg-warning', critical: 'bg-critical', accent: 'bg-accent' }[e.kind]}`} />
                <span className="min-w-0 text-ink-2">
                  <span className="mr-1.5 font-mono text-[10.5px] text-ink-3">{fmtLogTime(e)}</span>
                  <RichText text={e.text} />
                </span>
              </li>
            ))}
        </ol>
      </Section>
    </>
  );
}

function VehicleDetail({ snap, id }: { snap: SimSnapshot; id: string }) {
  const v = snap.vehicles.find((x) => x.id === id);
  const [tipo, setTipo] = useState<FallaTipo>(1);
  const averia = useRegisterAveria();
  if (!v) return <div className="p-4 text-sm text-ink-3">La unidad ya no está en la flota.</div>;
  const t = VEHICLE_TYPES[v.type];
  const order = v.orderId ? snap.orders.find((o) => o.id === v.orderId) : null;
  const fault = snap.incidents.find((i) => i.kind === 'falla' && i.vehicleId === v.id);
  const maint = snap.incidents.find((i) => i.kind === 'mantenimiento' && i.vehicleId === v.id);
  const st = VEHICLE_STATE[v.state];
  const history = snap.orderHistory.filter((o) => o.vehicleId === v.id).slice(0, 4);

  return (
    <>
      <Section title="Unidad">
        <div className="mb-3 flex items-center gap-3">
          <span className="flex size-10 items-center justify-center rounded-xl text-white" style={{ background: `var(${t.colorVar})` }}>
            <Icon name="truck" size={18} />
          </span>
          <div>
            <div className="text-base font-semibold text-ink">{v.id}</div>
            <div className="text-xs text-ink-3">
              {t.label} · {t.capacity} paq. · {t.speed} km/h · S/ {t.cost.toFixed(2)}/km
            </div>
          </div>
        </div>
        <Badge tone={st.tone}>{st.label}</Badge>
        <dl className="mt-3 divide-y divide-line">
          <Row label="Posición">
            ({Math.round(v.pos.x)}, {Math.round(v.pos.y)})
          </Row>
          <Row label="Base">{snap.warehouses.find((w) => w.id === v.home)?.name}</Row>
          {(v.state === 'toClient' || v.state === 'returning' || v.state === 'atClient') && <Row label="ETA">{etaText(v, snap.simMin)}</Row>}
          {v.state === 'returning' && <Row label="Regresa a">{snap.warehouses.find((w) => w.id === v.returnTarget)?.name ?? '—'}</Row>}
          {fault && fault.kind === 'falla' && (
            <>
              <Row label="Avería">{FALLA_TYPES[fault.tipo - 1].label}</Row>
              <Row label="Reparada a las">{fmtDayTime(fault.until)}</Row>
            </>
          )}
          {maint && <Row label="Disponible desde">{fmtDayTime(maint.until)}</Row>}
        </dl>
      </Section>
      {order && (
        <Section title="Pedido en curso">
          <dl className="divide-y divide-line">
            <Row label="Pedido">#{order.id}</Row>
            <Row label="Cliente">
              {order.clientId} · ({order.pos.x}, {order.pos.y})
            </Row>
            <Row label="Carga">
              {order.qty} / {v.capacity} paq.
            </Row>
            <Row label="Modalidad">{modalidadLabel(order.priority)}</Row>
            <Row label="Hora límite">{fmtDayTime(order.deadline)}</Row>
            <Row label="Plazo restante">
              <span className={TEXT_TONE[riskLevel(slackPct(order, snap.simMin), useUiStore.getState().thresholds)]}>{fmtMinutes(order.deadline - snap.simMin)}</span>
            </Row>
          </dl>
        </Section>
      )}
      {(v.state === 'toClient' || v.state === 'returning') && (
        <Section title="Registrar avería">
          <div className="flex gap-2">
            <select className="select" value={tipo} onChange={(e) => setTipo(Number(e.target.value) as FallaTipo)} aria-label="Tipo de avería">
              {FALLA_TYPES.map((f) => (
                <option key={f.tipo} value={f.tipo}>
                  {f.label}
                </option>
              ))}
            </select>
            <button type="button" className="btn btn-danger h-9 shrink-0" disabled={averia.isPending} onClick={() => averia.mutate({ vehicleId: v.id, tipo })}>
              Registrar
            </button>
          </div>
          <p className="field-hint mt-2">El pedido que lleva vuelve a la cola de asignación.</p>
        </Section>
      )}
      {history.length > 0 && (
        <Section title="Últimas entregas">
          <ul className="grid gap-1.5 text-xs">
            {history.map((o) => (
              <li key={o.id} className="flex items-center justify-between">
                <span className="font-mono text-ink">#{o.id}</span>
                <span className="text-ink-3">{fmtDayTime(o.closedAt)}</span>
                <Badge tone={o.estadoFinal === 'entregado' ? 'good' : 'critical'}>{o.estadoFinal}</Badge>
              </li>
            ))}
          </ul>
        </Section>
      )}
    </>
  );
}

function WarehouseDetail({ snap, id }: { snap: SimSnapshot; id: string }) {
  const thresholds = useUiStore((s) => s.thresholds);
  const w = snap.warehouses.find((x) => x.id === id)!;
  const outgoing = snap.orders.filter((o) => o.warehouseId === w.id && o.status === 'assigned');
  const arriving = snap.vehicles.filter((v) => v.state === 'returning' && v.returnTarget === w.id);
  const based = snap.vehicles.filter((v) => v.home === w.id && !isOnMap(v));
  const level = warehouseLevel(w, thresholds);
  return (
    <>
      <Section title={w.infinite ? 'Almacén central' : 'Almacén intermedio'}>
        <div className="mb-3 text-base font-semibold text-ink">{w.name}</div>
        <dl className="divide-y divide-line">
          <Row label="Nodo">
            ({w.pos.x}, {w.pos.y})
          </Row>
          <Row label="Stock">{w.infinite ? 'Ilimitado' : `${w.stock} / ${w.capacity}`}</Row>
          {!w.infinite && (
            <Row label="Nivel">
              <Badge tone={level}>
                {Math.round((w.stock / w.capacity) * 100)}% · {level === 'good' ? 'Normal' : level === 'warning' ? 'Bajo' : 'Crítico'}
              </Badge>
            </Row>
          )}
          <Row label="Despachos hoy">{w.dispatchedToday}</Row>
          <Row label="Unidades disponibles aquí">{based.length}</Row>
        </dl>
      </Section>
      <Section title={`Pedidos que salen (${outgoing.length})`}>
        {outgoing.length ? (
          <ul className="grid gap-1 font-mono text-xs text-ink-2">
            {outgoing.slice(0, 8).map((o) => (
              <li key={o.id}>
                #{o.id} · {o.clientId} · {o.qty} paq. · {o.vehicleId}
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-xs text-ink-3">Ninguno en este momento.</p>
        )}
      </Section>
      <Section title={`Unidades que llegan (${arriving.length})`}>
        {arriving.length ? (
          <ul className="grid gap-1 font-mono text-xs text-ink-2">
            {arriving.slice(0, 8).map((v) => (
              <li key={v.id}>
                {v.id} · {etaText(v, snap.simMin)}
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-xs text-ink-3">Ninguna en este momento.</p>
        )}
      </Section>
    </>
  );
}

function BloqueoDetail({ snap, id }: { snap: SimSnapshot; id: number }) {
  const inc = snap.incidents.find((i) => i.id === id && i.kind === 'bloqueo');
  if (!inc || inc.kind !== 'bloqueo') return <div className="p-4 text-sm text-ink-3">El bloqueo ya fue despejado.</div>;
  const a = inc.nodes[0];
  const b = inc.nodes[inc.nodes.length - 1];
  return (
    <Section title="Bloqueo vial">
      <div className="mb-3 text-base font-semibold text-ink">
        ({a.x},{a.y}) → ({b.x},{b.y})
      </div>
      <dl className="divide-y divide-line">
        <Row label="Tramos afectados">{inc.nodes.length - 1} km</Row>
        <Row label="Desde">{fmtDayTime(inc.since)}</Row>
        <Row label="Hasta">{fmtDayTime(inc.until)}</Row>
        <Row label="Se despeja en">{fmtMinutes(inc.until - snap.simMin)}</Row>
        <Row label="Origen">{inc.origin === 'archivo' ? 'Archivo de bloqueos' : inc.origin === 'manual' ? 'Registro manual' : 'Generado'}</Row>
      </dl>
    </Section>
  );
}

function ZoneDetail({ snap, name }: { snap: SimSnapshot; name: string }) {
  const thresholds = useUiStore((s) => s.thresholds);
  const inZone = snap.orders.filter((o) => {
    const sx = Math.min(60, Math.floor(o.pos.x / 10) * 10);
    const sy = Math.min(40, Math.floor(o.pos.y / 10) * 10);
    return name === `Sector (${sx}–${sx + 10}, ${sy}–${sy + 10})`;
  });
  const d = snap.stats.bySector[name] ?? { delivered: 0, onTime: 0 };
  const pct = d.delivered ? Math.round((d.onTime / d.delivered) * 100) : null;
  const atRisk = inZone.filter((o) => riskLevel(slackPct(o, snap.simMin), thresholds) !== 'good').length;
  return (
    <Section title="Zona de la retícula">
      <div className="mb-3 text-base font-semibold text-ink">{name}</div>
      <dl className="divide-y divide-line">
        <Row label="Pedidos activos">{inZone.length}</Row>
        <Row label="En riesgo o críticos">{atRisk}</Row>
        <Row label="Entregados">{d.delivered}</Row>
        <Row label="A tiempo">
          {pct === null ? '—' : <Badge tone={riskLevel(pct, thresholds)}>{pct}% · {RISK_LABEL[riskLevel(pct, thresholds)]}</Badge>}
        </Row>
      </dl>
      {inZone.length > 0 && (
        <ul className="mt-3 grid gap-1 font-mono text-xs text-ink-2">
          {inZone.slice(0, 8).map((o) => (
            <li key={o.id}>
              #{o.id} · {o.clientId} · límite {fmtTime(o.deadline)}
            </li>
          ))}
        </ul>
      )}
    </Section>
  );
}

const SEL_TITLE: Record<Selection['type'], string> = { vehicle: 'Detalle de unidad', warehouse: 'Detalle de almacén', bloqueo: 'Detalle de bloqueo', zone: 'Detalle de zona' };

export function SidePanel() {
  const snap = useSimStore((s) => s.snapshot);
  const selected = useUiStore((s) => s.selected);
  const select = useUiStore((s) => s.select);
  if (!snap) return null;
  return (
    <div className="flex h-full flex-col">
      <div className="flex h-12 shrink-0 items-center justify-between border-b border-line px-4">
        <h2 className="text-[13px] font-semibold text-ink">{selected ? SEL_TITLE[selected.type] : 'Estado de la operación'}</h2>
        {selected && (
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => select(null)}>
            <Icon name="chevronLeft" size={14} />
            Resumen
          </button>
        )}
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto">
        {!selected && <Summary snap={snap} />}
        {selected?.type === 'vehicle' && <VehicleDetail snap={snap} id={selected.id} />}
        {selected?.type === 'warehouse' && <WarehouseDetail snap={snap} id={selected.id} />}
        {selected?.type === 'bloqueo' && <BloqueoDetail snap={snap} id={selected.id} />}
        {selected?.type === 'zone' && <ZoneDetail snap={snap} name={selected.name} />}
      </div>
    </div>
  );
}
