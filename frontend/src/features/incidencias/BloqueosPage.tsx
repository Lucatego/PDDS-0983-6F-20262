import { useState } from 'react';
import { useNavigate } from 'react-router';
import { useRegisterBloqueo } from '@/api/hooks';
import { DataTable, type Column } from '@/components/data/DataTable';
import { FileImportDialog } from '@/components/data/FileImportDialog';
import { Drawer } from '@/components/ui/Dialog';
import { Icon } from '@/components/ui/Icon';
import { Badge, PageHeader } from '@/components/ui/primitives';
import { GRID_H, GRID_W } from '@/domain/constants';
import { parseNodeList } from '@/domain/fileFormats';
import { fmtDayTime, fmtMinutes } from '@/domain/time';
import { useSimStore } from '@/store/simStore';
import { useUiStore } from '@/store/uiStore';
import { useIncidentRows, type IncidentRow } from './useIncidentRows';

function NewBloqueoDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [nodos, setNodos] = useState('');
  const [horas, setHoras] = useState('4');
  const reg = useRegisterBloqueo();
  const toast = useUiStore((s) => s.toast);
  const nodes = parseNodeList(nodos);
  const h = Math.min(72, Math.max(1, Number.parseInt(horas, 10) || 1));
  const close = () => {
    setNodos('');
    reg.reset();
    onClose();
  };

  return (
    <Drawer
      open={open}
      onClose={close}
      title="Registrar bloqueo"
      description="Polígono abierto de nodos con tramos horizontales o verticales. Las rutas que lo crucen se recalculan de inmediato."
      footer={
        <>
          <button type="button" className="btn btn-secondary" onClick={close}>
            Cancelar
          </button>
          <button
            type="button"
            className="btn btn-primary"
            disabled={!nodes || reg.isPending}
            onClick={() =>
              nodes &&
              reg.mutate(
                { nodes, horas: h },
                {
                  onSuccess: () => {
                    toast('Bloqueo registrado', 'warning', `${nodes.length} nodos durante ${h} h.`);
                    close();
                  },
                },
              )
            }
          >
            Registrar bloqueo
          </button>
        </>
      }
    >
      <div className="grid gap-4">
        <label className="field">
          <span className="field-label">Nodos (x1,y1,x2,y2,…)</span>
          <input className="input font-mono" data-autofocus placeholder="31,21,34,21" value={nodos} onChange={(e) => setNodos(e.target.value)} aria-invalid={nodos !== '' && !nodes} />
          <span className="field-hint">
            Coordenadas en km dentro de la retícula (0–{GRID_W}, 0–{GRID_H}). Mínimo dos nodos.
          </span>
        </label>
        <label className="field">
          <span className="field-label">Duración (horas)</span>
          <input className="input num" type="number" min={1} max={72} value={horas} onChange={(e) => setHoras(e.target.value)} />
        </label>
        {nodes && (
          <div className="rounded-lg bg-surface-2 px-3 py-2.5 text-xs text-ink-2">
            Tramo de ({nodes[0].x},{nodes[0].y}) a ({nodes[nodes.length - 1].x},{nodes[nodes.length - 1].y}) con {nodes.length} vértices.
          </div>
        )}
        {reg.error && (
          <div className="flex gap-2 rounded-lg bg-critical-soft px-3 py-2.5 text-[13px] text-critical-ink" role="alert">
            <Icon name="alert" className="mt-0.5 shrink-0" />
            {reg.error instanceof Error ? reg.error.message : 'No se pudo registrar el bloqueo.'}
          </div>
        )}
      </div>
    </Drawer>
  );
}

export function BloqueosPage() {
  const snap = useSimStore((s) => s.snapshot);
  const rows = useIncidentRows('bloqueo');
  const select = useUiStore((s) => s.select);
  const focusOn = useUiStore((s) => s.focusOn);
  const navigate = useNavigate();
  const [newOpen, setNewOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  if (!snap) return null;

  const columns: Column<IncidentRow<'bloqueo'>>[] = [
    {
      key: 'tramo',
      header: 'Tramo',
      cell: (r) => (
        <span className="num font-mono text-xs text-ink">
          ({r.nodes[0].x},{r.nodes[0].y}) → ({r.nodes[r.nodes.length - 1].x},{r.nodes[r.nodes.length - 1].y})
        </span>
      ),
    },
    { key: 'km', header: 'Longitud', align: 'right', cell: (r) => `${r.nodes.length - 1} km` },
    { key: 'estado', header: 'Estado', cell: (r) => (r.activa ? <Badge tone="critical">Activo</Badge> : <Badge tone="good">Despejado</Badge>) },
    { key: 'desde', header: 'Desde', cell: (r) => <span className="num text-xs">{fmtDayTime(r.since)}</span> },
    { key: 'hasta', header: 'Hasta', cell: (r) => <span className="num text-xs">{fmtDayTime(r.until)}</span> },
    { key: 'resta', header: 'Se despeja en', cell: (r) => (r.activa ? fmtMinutes(r.until - snap.simMin) : '—') },
    { key: 'orig', header: 'Origen', cell: (r) => <span className="text-xs capitalize">{r.origin}</span> },
  ];

  return (
    <>
      <PageHeader
        crumbs={['Gestión', 'Bloqueos']}
        title="Bloqueos viales"
        description="Calles cerradas temporalmente. Los tramos bloqueados no se pueden recorrer y las rutas afectadas se recalculan."
        actions={
          snap.configured && (
            <>
              <button type="button" className="btn btn-secondary" onClick={() => setImportOpen(true)}>
                <Icon name="upload" size={15} />
                Importar bloqueos
              </button>
              <button type="button" className="btn btn-primary" onClick={() => setNewOpen(true)}>
                <Icon name="plus" size={15} />
                Registrar bloqueo
              </button>
            </>
          )
        }
      />
      <div className="card overflow-hidden">
        <DataTable
          columns={columns}
          rows={rows}
          rowKey={(r) => r.id}
          onRowClick={(r) => {
            select({ type: 'bloqueo', id: r.id });
            focusOn(r.nodes[Math.floor(r.nodes.length / 2)], 2.5);
            navigate('/monitor');
          }}
          rowTitle={(r) => (r.activa ? 'Ver en el mapa' : undefined)}
          empty={{ icon: 'barrier', title: 'Sin bloqueos', text: 'Registra uno manualmente o importa el archivo de bloqueos.' }}
        />
      </div>
      <NewBloqueoDrawer open={newOpen} onClose={() => setNewOpen(false)} />
      <FileImportDialog kind="bloqueos" open={importOpen} onClose={() => setImportOpen(false)} />
    </>
  );
}
