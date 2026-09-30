import { useState } from 'react';
import { useRegisterMantenimiento } from '@/api/hooks';
import { DataTable, type Column } from '@/components/data/DataTable';
import { FileImportDialog } from '@/components/data/FileImportDialog';
import { Drawer } from '@/components/ui/Dialog';
import { Icon } from '@/components/ui/Icon';
import { Badge, PageHeader } from '@/components/ui/primitives';
import { VEHICLE_TYPES } from '@/domain/constants';
import { fmtDayTime } from '@/domain/time';
import { useSimStore } from '@/store/simStore';
import { useUiStore } from '@/store/uiStore';
import { useIncidentRows, type IncidentRow } from './useIncidentRows';

function NewMantenimientoDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const snap = useSimStore((s) => s.snapshot);
  const eligible = (snap?.vehicles ?? []).filter((v) => v.state === 'idle' || v.state === 'break');
  const [vehicleId, setVehicleId] = useState('');
  const [horas, setHoras] = useState('2');
  const reg = useRegisterMantenimiento();
  const toast = useUiStore((s) => s.toast);
  const chosen = eligible.find((v) => v.id === vehicleId) ? vehicleId : eligible[0]?.id ?? '';
  const h = Math.min(24, Math.max(1, Number.parseInt(horas, 10) || 1));

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title="Programar mantenimiento"
      description="La unidad debe estar disponible en almacén. Queda fuera de servicio durante las horas indicadas."
      footer={
        <>
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Cancelar
          </button>
          <button
            type="button"
            className="btn btn-primary"
            disabled={!chosen || reg.isPending}
            onClick={() =>
              reg.mutate(
                { vehicleId: chosen, horas: h },
                {
                  onSuccess: () => {
                    toast(`Mantenimiento programado para ${chosen}`, 'good', `${h} h fuera de servicio.`);
                    onClose();
                  },
                },
              )
            }
          >
            Programar
          </button>
        </>
      }
    >
      <div className="grid gap-4">
        <label className="field">
          <span className="field-label">Unidad disponible</span>
          <select className="select" value={chosen} onChange={(e) => setVehicleId(e.target.value)} disabled={!eligible.length}>
            {!eligible.length && <option>Ninguna unidad disponible ahora</option>}
            {eligible.map((v) => (
              <option key={v.id} value={v.id}>
                {v.id} · {VEHICLE_TYPES[v.type].label} · base {snap?.warehouses.find((w) => w.id === v.home)?.shortName}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span className="field-label">Duración (horas)</span>
          <input className="input num" type="number" min={1} max={24} value={horas} onChange={(e) => setHoras(e.target.value)} />
          <span className="field-hint">Entre 1 y 24 horas.</span>
        </label>
      </div>
    </Drawer>
  );
}

export function MantenimientoPage() {
  const snap = useSimStore((s) => s.snapshot);
  const rows = useIncidentRows('mantenimiento');
  const [newOpen, setNewOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  if (!snap) return null;
  const diaria = snap.scenario === 'diaria';

  const columns: Column<IncidentRow<'mantenimiento'>>[] = [
    { key: 'veh', header: 'Unidad', cell: (r) => <span className="code">{r.vehicleId}</span> },
    { key: 'estado', header: 'Estado', cell: (r) => (r.activa ? <Badge tone="warning">En mantenimiento</Badge> : <Badge tone="good">Finalizado</Badge>) },
    { key: 'dur', header: 'Duración', cell: (r) => `${r.horas} h` },
    { key: 'desde', header: 'Desde', cell: (r) => <span className="num text-xs">{fmtDayTime(r.since)}</span> },
    { key: 'hasta', header: 'Hasta', cell: (r) => <span className="num text-xs">{fmtDayTime(r.until)}</span> },
    { key: 'orig', header: 'Origen', cell: (r) => (r.origin === 'archivo' ? 'Plan preventivo' : 'Manual') },
  ];

  return (
    <>
      <PageHeader
        crumbs={['Gestión', 'Mantenimiento']}
        title="Mantenimiento preventivo"
        description={
          diaria
            ? 'Programa mantenimientos para unidades disponibles.'
            : 'En 5D y colapso el plan llega por archivo (aaaammdd:TTNN): la unidad queda fuera de servicio de 00:00 a 23:59 ese día y, si estaba en ruta, su pedido vuelve a la cola.'
        }
        actions={
          snap.configured &&
          (diaria ? (
            <button type="button" className="btn btn-primary" onClick={() => setNewOpen(true)}>
              <Icon name="plus" size={15} />
              Programar mantenimiento
            </button>
          ) : (
            <button type="button" className="btn btn-primary" onClick={() => setImportOpen(true)}>
              <Icon name="upload" size={15} />
              Importar plan
            </button>
          ))
        }
      />
      {snap.files.mantenimiento !== null && (
        <div className="mb-4 flex items-center gap-3 rounded-xl border border-line bg-surface px-4 py-3 text-[13px] text-ink-2">
          <Icon name="calendar" className="text-ink-3" />
          Plan cargado: <b className="text-ink">{snap.files.mantenimiento}</b> mantenimientos programados.
        </div>
      )}
      <div className="card overflow-hidden">
        <DataTable
          columns={columns}
          rows={rows}
          rowKey={(r) => r.id}
          empty={{ icon: 'calendar', title: 'Sin mantenimientos', text: diaria ? 'Programa uno con el botón de arriba.' : 'Importa el plan de mantenimiento preventivo.' }}
        />
      </div>
      <NewMantenimientoDrawer open={newOpen} onClose={() => setNewOpen(false)} />
      <FileImportDialog kind="mantenimiento" open={importOpen} onClose={() => setImportOpen(false)} />
    </>
  );
}
