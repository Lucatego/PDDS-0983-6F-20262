import { useMemo, useState } from 'react';
import { useRegisterAveria } from '@/api/hooks';
import { DataTable, SelectField, type Column } from '@/components/data/DataTable';
import { FileImportDialog } from '@/components/data/FileImportDialog';
import { Drawer } from '@/components/ui/Dialog';
import { Icon } from '@/components/ui/Icon';
import { Badge, PageHeader, StatCard, type Tone } from '@/components/ui/primitives';
import { FALLA_TYPES, VEHICLE_TYPES } from '@/domain/constants';
import { fmtDayTime, fmtMinutes } from '@/domain/time';
import type { FallaTipo } from '@/domain/types';
import { useGoToVehicle } from '@/lib/useGoToOnMap';
import { useSimStore } from '@/store/simStore';
import { useUiStore } from '@/store/uiStore';
import { useIncidentRows, type IncidentRow } from './useIncidentRows';

const FALLA_TONE: Record<FallaTipo, Tone> = { 1: 'warning', 2: 'serious', 3: 'critical' };

function NewAveriaDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const snap = useSimStore((s) => s.snapshot);
  const eligible = (snap?.vehicles ?? []).filter((v) => v.state === 'toClient' || v.state === 'returning');
  const [vehicleId, setVehicleId] = useState('');
  const [tipo, setTipo] = useState<FallaTipo>(1);
  const averia = useRegisterAveria();
  const toast = useUiStore((s) => s.toast);
  const chosen = eligible.find((v) => v.id === vehicleId) ? vehicleId : eligible[0]?.id ?? '';

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title="Registrar avería"
      description="Solo sobre unidades en ruta. El pedido que lleve vuelve a la cola de asignación y el planificador lo reprograma."
      footer={
        <>
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancelar
          </button>
          <button
            type="button"
            className="btn btn-danger"
            disabled={!chosen || averia.isPending}
            onClick={() =>
              averia.mutate(
                { vehicleId: chosen, tipo },
                {
                  onSuccess: () => {
                    toast(`Avería registrada en ${chosen}`, 'warning', FALLA_TYPES[tipo - 1].label);
                    onClose();
                  },
                },
              )
            }
          >
            Registrar avería
          </button>
        </>
      }
    >
      <div className="grid gap-4">
        <label className="field">
          <span className="field-label">Unidad en ruta</span>
          <select className="select" value={chosen} onChange={(e) => setVehicleId(e.target.value)} disabled={!eligible.length}>
            {!eligible.length && <option>Ninguna unidad en ruta ahora</option>}
            {eligible.map((v) => (
              <option key={v.id} value={v.id}>
                {v.id} · {VEHICLE_TYPES[v.type].label} · {v.state === 'toClient' ? 'hacia el cliente' : 'retornando'}
              </option>
            ))}
          </select>
        </label>
        <fieldset>
          <legend className="field-label mb-2">Tipo de avería</legend>
          <div className="grid gap-2">
            {FALLA_TYPES.map((f) => (
              <label key={f.tipo} className={`flex cursor-pointer items-center gap-3 rounded-xl border p-3 ${tipo === f.tipo ? 'border-accent bg-accent-soft' : 'border-line hover:bg-surface-2'}`}>
                <input type="radio" name="tipo" className="accent-[var(--accent)]" checked={tipo === f.tipo} onChange={() => setTipo(f.tipo)} />
                <span className="flex-1">
                  <span className="block text-[13px] font-medium text-ink">{f.label}</span>
                  <span className="block text-xs text-ink-3">
                    Inmovilizada entre {f.minMin} y {f.maxMin} min
                  </span>
                </span>
                <Badge tone={FALLA_TONE[f.tipo]}>{f.short}</Badge>
              </label>
            ))}
          </div>
        </fieldset>
      </div>
    </Drawer>
  );
}

export function AveriasPage() {
  const snap = useSimStore((s) => s.snapshot);
  const rows = useIncidentRows('falla');
  const goToVehicle = useGoToVehicle();
  const [tipo, setTipo] = useState('');
  const [estado, setEstado] = useState('');
  const [newOpen, setNewOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const filtered = useMemo(() => rows.filter((r) => (!tipo || String(r.tipo) === tipo) && (!estado || (estado === 'activa') === r.activa)), [rows, tipo, estado]);
  if (!snap) return null;

  const active = rows.filter((r) => r.activa);
  const columns: Column<IncidentRow<'falla'>>[] = [
    { key: 'veh', header: 'Unidad', cell: (r) => <span className="code">{r.vehicleId}</span> },
    { key: 'tipo', header: 'Tipo', cell: (r) => <Badge tone={FALLA_TONE[r.tipo]}>{FALLA_TYPES[r.tipo - 1].label}</Badge> },
    { key: 'estado', header: 'Estado', cell: (r) => (r.activa ? <Badge tone="critical">En reparación</Badge> : <Badge tone="good">Reparada</Badge>) },
    { key: 'desde', header: 'Desde', cell: (r) => <span className="num text-xs">{fmtDayTime(r.since)}</span> },
    { key: 'hasta', header: 'Hasta', cell: (r) => <span className="num text-xs">{fmtDayTime(r.until)}</span> },
    { key: 'dur', header: 'Duración', cell: (r) => fmtMinutes(r.until - r.since) },
    { key: 'pos', header: 'Lugar', cell: (r) => <span className="num font-mono text-xs">({Math.round(r.pos.x)}, {Math.round(r.pos.y)})</span> },
    { key: 'orig', header: 'Origen', cell: (r) => <span className="text-xs capitalize">{r.origin}</span> },
  ];

  return (
    <>
      <PageHeader
        crumbs={['Gestión', 'Averías']}
        title="Averías"
        description="Registro manual en cualquier escenario; en 5D y colapso también por archivo programado."
        actions={
          snap.configured && (
            <>
              {snap.scenario !== 'diaria' && (
                <button type="button" className="btn btn-secondary" onClick={() => setImportOpen(true)}>
                  <Icon name="upload" size={15} />
                  Importar averías
                </button>
              )}
              <button type="button" className="btn btn-primary" onClick={() => setNewOpen(true)}>
                <Icon name="plus" size={15} />
                Registrar avería
              </button>
            </>
          )
        }
      />
      <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatCard label="En reparación" value={active.length} icon="wrench" tone={active.length ? 'critical' : 'neutral'} />
        {FALLA_TYPES.map((f) => (
          <StatCard key={f.tipo} label={f.label} value={rows.filter((r) => r.tipo === f.tipo).length} icon="alert" tone={FALLA_TONE[f.tipo]} sub="en la corrida" />
        ))}
      </div>
      <div className="card overflow-hidden">
        <div className="flex flex-wrap items-end gap-3 border-b border-line px-4 py-3">
          <SelectField label="Tipo" value={tipo} onChange={setTipo} options={[{ value: '', label: 'Todos' }, ...FALLA_TYPES.map((f) => ({ value: String(f.tipo), label: f.label }))]} />
          <SelectField label="Estado" value={estado} onChange={setEstado} options={[{ value: '', label: 'Todos' }, { value: 'activa', label: 'En reparación' }, { value: 'fin', label: 'Reparadas' }]} />
          {snap.files.averias !== null && <span className="ml-auto self-center text-xs text-ink-3">Archivo cargado: {snap.files.averias} averías programadas</span>}
        </div>
        <DataTable
          columns={columns}
          rows={filtered}
          rowKey={(r) => r.id}
          onRowClick={(r) => goToVehicle(r.vehicleId)}
          rowTitle={(r) => (r.activa ? 'Ver la unidad en el mapa' : undefined)}
          empty={{ icon: 'wrench', title: 'Sin averías registradas', text: 'Las averías aparecen aquí cuando se registran o se activan desde el archivo.' }}
        />
      </div>
      <NewAveriaDrawer open={newOpen} onClose={() => setNewOpen(false)} />
      <FileImportDialog kind="averias" open={importOpen} onClose={() => setImportOpen(false)} />
    </>
  );
}
