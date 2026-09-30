import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router';
import { DataTable, SearchField, SelectField, type Column } from '@/components/data/DataTable';
import { FileImportDialog } from '@/components/data/FileImportDialog';
import { Icon } from '@/components/ui/Icon';
import { Badge, PageHeader } from '@/components/ui/primitives';
import { MODALIDADES } from '@/domain/constants';
import { ORDER_ESTADO, modalidadLabel, orderEstado, type OrderEstado } from '@/domain/labels';
import { RISK_LABEL, riskLevel, slackPct } from '@/domain/risk';
import { fmtDayTime, fmtMinutes } from '@/domain/time';
import type { ClosedOrder, Order } from '@/domain/types';
import { useGoToVehicle } from '@/lib/useGoToOnMap';
import { useSimStore } from '@/store/simStore';
import { useUiStore } from '@/store/uiStore';
import { BatchOrdersDialog, NewOrderDrawer } from './OrderForms';

type Row = (Order | ClosedOrder) & { estado: OrderEstado };

const TABS: { key: OrderEstado | ''; label: string }[] = [
  { key: '', label: 'Todos' },
  { key: 'registrado', label: 'Registrados' },
  { key: 'reprogramado', label: 'Reprogramados' },
  { key: 'en ruta', label: 'En ruta' },
  { key: 'entregado', label: 'Entregados' },
  { key: 'no cumplido', label: 'No cumplidos' },
];

export function PedidosPage() {
  const snap = useSimStore((s) => s.snapshot);
  const thresholds = useUiStore((s) => s.thresholds);
  const goToVehicle = useGoToVehicle();
  const [params, setParams] = useSearchParams();
  const [tab, setTab] = useState<OrderEstado | ''>('');
  const [q, setQ] = useState('');
  const [modalidad, setModalidad] = useState('');
  const [newOpen, setNewOpen] = useState(false);
  const [batchOpen, setBatchOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);

  useEffect(() => {
    if (params.get('nuevo') === '1') {
      setNewOpen(true);
      params.delete('nuevo');
      setParams(params, { replace: true });
    }
  }, [params, setParams]);

  const all: Row[] = useMemo(() => {
    if (!snap) return [];
    return [...snap.orders, ...snap.orderHistory].map((o) => ({ ...o, estado: orderEstado(o) })).sort((a, b) => b.id - a.id);
  }, [snap]);

  const counts = useMemo(() => {
    const c: Record<string, number> = { '': all.length };
    all.forEach((r) => (c[r.estado] = (c[r.estado] ?? 0) + 1));
    return c;
  }, [all]);

  const rows = useMemo(() => {
    const query = q.trim().toLowerCase();
    return all.filter(
      (r) =>
        (!tab || r.estado === tab) &&
        (!modalidad || String(r.priority) === modalidad) &&
        (!query || r.clientId.toLowerCase().includes(query) || String(r.id).includes(query)),
    );
  }, [all, tab, q, modalidad]);

  if (!snap) return null;
  const diaria = snap.scenario === 'diaria';
  const canManual = snap.configured && diaria;

  const columns: Column<Row>[] = [
    { key: 'id', header: 'Pedido', cell: (r) => <span className="code">#{r.id}</span> },
    { key: 'cliente', header: 'Cliente', cell: (r) => <span className="font-medium text-ink">{r.clientId}</span> },
    { key: 'qty', header: 'Cant.', align: 'right', cell: (r) => <span className="num">{r.qty}</span> },
    { key: 'mod', header: 'Modalidad', cell: (r) => modalidadLabel(r.priority) },
    { key: 'dest', header: 'Destino', cell: (r) => <span className="num font-mono text-xs">({r.pos.x}, {r.pos.y})</span> },
    { key: 'reg', header: 'Registrado', cell: (r) => <span className="num text-xs">{fmtDayTime(r.createdAt)}</span> },
    { key: 'lim', header: 'Hora límite', cell: (r) => <span className="num text-xs">{fmtDayTime(r.deadline)}</span> },
    {
      key: 'plazo',
      header: 'Plazo',
      cell: (r) => {
        if ('estadoFinal' in r) return <span className="text-xs text-ink-3">cerrado {fmtDayTime(r.closedAt)}</span>;
        const lvl = riskLevel(slackPct(r, snap.simMin), thresholds);
        return (
          <Badge tone={lvl}>
            {RISK_LABEL[lvl]} · {fmtMinutes(r.deadline - snap.simMin)}
          </Badge>
        );
      },
    },
    { key: 'estado', header: 'Estado', cell: (r) => <Badge tone={ORDER_ESTADO[r.estado]}>{r.estado[0].toUpperCase() + r.estado.slice(1)}</Badge> },
    { key: 'veh', header: 'Unidad', cell: (r) => (r.vehicleId ? <span className="code">{r.vehicleId}</span> : <span className="text-ink-3">—</span>) },
  ];

  return (
    <>
      <PageHeader
        crumbs={['Gestión', 'Pedidos']}
        title="Pedidos"
        description={
          !snap.configured
            ? 'Configura una ejecución para registrar pedidos.'
            : diaria
              ? snap.waitingFirstOrder
                ? 'La operación está en espera del primer pedido: regístralo para que el reloj empiece a correr.'
                : 'Registro manual y por lote, con las mismas validaciones que aplica el planificador.'
              : 'En este escenario los pedidos llegan por el archivo de ventas y se activan a su hora programada.'
        }
        actions={
          canManual ? (
            <>
              <button type="button" className="btn btn-secondary" onClick={() => setBatchOpen(true)}>
                <Icon name="file" size={15} />
                Carga por lote
              </button>
              <button type="button" className="btn btn-primary" onClick={() => setNewOpen(true)}>
                <Icon name="plus" size={15} />
                Nuevo pedido
              </button>
            </>
          ) : snap.configured ? (
            <button type="button" className="btn btn-primary" onClick={() => setImportOpen(true)}>
              <Icon name="upload" size={15} />
              Importar ventas
            </button>
          ) : null
        }
      />

      {snap.configured && !diaria && (
        <div className="mb-4 flex items-center gap-3 rounded-xl border border-line bg-surface px-4 py-3 text-[13px]">
          <Icon name="file" className="text-ink-3" />
          {snap.files.ventas !== null ? (
            <span className="text-ink-2">
              Archivo de ventas cargado: <b className="text-ink">{snap.files.ventas}</b> registros. Los pedidos entran a su hora programada.
            </span>
          ) : (
            <span className="text-ink-2">Sin archivo de ventas: la simulación genera pedidos aleatorios de prueba.</span>
          )}
        </div>
      )}

      <div className="card overflow-hidden">
        <div className="flex gap-1 overflow-x-auto overflow-y-hidden border-b border-line px-3 pt-2" role="tablist" aria-label="Estado del pedido">
          {TABS.map((t) => (
            <button
              key={t.key}
              type="button"
              role="tab"
              aria-selected={tab === t.key}
              onClick={() => setTab(t.key)}
              className={`-mb-px flex items-center gap-2 border-b-2 px-3 pt-1.5 pb-2.5 text-[13px] font-medium whitespace-nowrap transition-colors ${
                tab === t.key ? 'border-accent text-ink' : 'border-transparent text-ink-3 hover:text-ink-2'
              }`}
            >
              {t.label}
              <span className={`rounded-full px-1.5 text-[11px] ${tab === t.key ? 'bg-accent-soft text-accent-ink' : 'bg-surface-3 text-ink-3'}`}>{counts[t.key] ?? 0}</span>
            </button>
          ))}
        </div>
        <div className="flex flex-wrap items-end gap-3 border-b border-line px-4 py-3">
          <SearchField label="Buscar" value={q} onChange={setQ} placeholder="Cliente o número de pedido" />
          <SelectField
            label="Modalidad"
            value={modalidad}
            onChange={setModalidad}
            options={[{ value: '', label: 'Todas' }, ...MODALIDADES.map((m) => ({ value: String(m.horas), label: m.label }))]}
          />
          {(q || modalidad || tab) && (
            <button
              type="button"
              className="btn btn-ghost h-9"
              onClick={() => {
                setQ('');
                setModalidad('');
                setTab('');
              }}
            >
              Limpiar filtros
            </button>
          )}
        </div>
        <DataTable
          columns={columns}
          rows={rows}
          rowKey={(r) => r.id}
          onRowClick={(r) => r.vehicleId && goToVehicle(r.vehicleId)}
          rowTitle={(r) => (r.vehicleId && !('estadoFinal' in r) ? 'Ver la unidad en el mapa' : undefined)}
          empty={{
            icon: 'package',
            title: all.length ? 'Ningún pedido coincide con los filtros' : 'Todavía no hay pedidos',
            text: all.length ? 'Prueba con otro estado o modalidad.' : canManual ? 'Registra el primero con «Nuevo pedido».' : undefined,
          }}
        />
      </div>

      <NewOrderDrawer open={newOpen} onClose={() => setNewOpen(false)} />
      <BatchOrdersDialog open={batchOpen} onClose={() => setBatchOpen(false)} />
      <FileImportDialog kind="ventas" open={importOpen} onClose={() => setImportOpen(false)} />
    </>
  );
}
