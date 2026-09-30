import { useMemo, useState } from 'react';
import { DataTable, SearchField, SelectField, type Column } from '@/components/data/DataTable';
import { Badge, Meter, PageHeader, StatCard } from '@/components/ui/primitives';
import { VEHICLE_TYPES, VEHICLE_TYPE_KEYS } from '@/domain/constants';
import { VEHICLE_STATE, etaText, isOnMap } from '@/domain/labels';
import type { Vehicle, VehicleState } from '@/domain/types';
import { useGoToVehicle } from '@/lib/useGoToOnMap';
import { useSimStore } from '@/store/simStore';

export function FlotaPage() {
  const snap = useSimStore((s) => s.snapshot);
  const goToVehicle = useGoToVehicle();
  const [q, setQ] = useState('');
  const [tipo, setTipo] = useState('');
  const [estado, setEstado] = useState('');

  const rows = useMemo(() => {
    const query = q.trim().toUpperCase();
    return (snap?.vehicles ?? []).filter((v) => (!query || v.id.includes(query)) && (!tipo || v.type === tipo) && (!estado || v.state === estado));
  }, [snap, q, tipo, estado]);

  if (!snap) return null;

  const columns: Column<Vehicle>[] = [
    {
      key: 'id',
      header: 'Unidad',
      cell: (v) => (
        <span className="flex items-center gap-2">
          <span className="size-2.5 rounded-full" style={{ background: `var(${VEHICLE_TYPES[v.type].colorVar})` }} />
          <span className="code">{v.id}</span>
        </span>
      ),
    },
    { key: 'tipo', header: 'Tipo', cell: (v) => VEHICLE_TYPES[v.type].label },
    { key: 'estado', header: 'Estado', cell: (v) => <Badge tone={VEHICLE_STATE[v.state].tone}>{VEHICLE_STATE[v.state].label}</Badge> },
    {
      key: 'carga',
      header: 'Carga',
      cell: (v) => {
        const o = v.orderId ? snap.orders.find((x) => x.id === v.orderId) : null;
        const qty = o?.qty ?? 0;
        return (
          <div className="flex w-28 items-center gap-2">
            <Meter pct={(qty / v.capacity) * 100} tone={qty ? 'accent' : 'neutral'} label={`Carga de ${v.id}`} />
            <span className="num w-10 shrink-0 text-right font-mono text-xs">
              {qty}/{v.capacity}
            </span>
          </div>
        );
      },
    },
    { key: 'pedido', header: 'Pedido', cell: (v) => (v.orderId ? <span className="code">#{v.orderId}</span> : <span className="text-ink-3">—</span>) },
    { key: 'eta', header: 'ETA', cell: (v) => <span className="text-xs">{isOnMap(v) ? etaText(v, snap.simMin) : '—'}</span> },
    { key: 'base', header: 'Base', cell: (v) => snap.warehouses.find((w) => w.id === v.home)?.shortName },
    { key: 'pos', header: 'Posición', cell: (v) => <span className="num font-mono text-xs">({Math.round(v.pos.x)}, {Math.round(v.pos.y)})</span> },
    {
      key: 'hist',
      header: 'Últimas entregas',
      cell: (v) =>
        snap.orderHistory
          .filter((o) => o.vehicleId === v.id)
          .slice(0, 3)
          .map((o) => `#${o.id}`)
          .join(', ') || <span className="text-ink-3">—</span>,
    },
  ];

  return (
    <>
      <PageHeader crumbs={['Gestión', 'Flota']} title="Flota" description="Unidades configuradas para la corrida, su estado actual, carga y ruta." />
      <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-3">
        {VEHICLE_TYPE_KEYS.map((k) => {
          const t = VEHICLE_TYPES[k];
          const list = snap.vehicles.filter((v) => v.type === k);
          const busy = list.filter(isOnMap).length;
          return (
            <StatCard
              key={k}
              label={t.plural}
              icon="truck"
              value={
                <span>
                  {busy}
                  <span className="text-base font-medium text-ink-3"> / {list.length} en operación</span>
                </span>
              }
              sub={`${t.capacity} paq. · ${t.speed} km/h · S/ ${t.cost.toFixed(2)} por km`}
            />
          );
        })}
      </div>
      <div className="card overflow-hidden">
        <div className="flex flex-wrap items-end gap-3 border-b border-line px-4 py-3">
          <SearchField label="Buscar" value={q} onChange={setQ} placeholder="Código de unidad, p. ej. TA03" />
          <SelectField label="Tipo" value={tipo} onChange={setTipo} options={[{ value: '', label: 'Todos' }, ...VEHICLE_TYPE_KEYS.map((k) => ({ value: k, label: VEHICLE_TYPES[k].plural }))]} />
          <SelectField
            label="Estado"
            value={estado}
            onChange={setEstado}
            options={[{ value: '', label: 'Todos' }, ...(Object.keys(VEHICLE_STATE) as VehicleState[]).map((s) => ({ value: s, label: VEHICLE_STATE[s].label }))]}
          />
        </div>
        <DataTable
          columns={columns}
          rows={rows}
          rowKey={(v) => v.id}
          pageSize={20}
          onRowClick={(v) => goToVehicle(v.id)}
          rowTitle={(v) => (isOnMap(v) ? 'Ver en el mapa' : undefined)}
          empty={{ icon: 'truck', title: 'Ninguna unidad coincide con los filtros' }}
        />
      </div>
    </>
  );
}
