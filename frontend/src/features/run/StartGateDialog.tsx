// Configuración de la corrida: escenario, fecha/hora de inicio, flota, capacidad de almacenes y turnos.
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { useConfigureRun } from '@/api/hooks';
import { Dialog } from '@/components/ui/Dialog';
import { Icon, type IconName } from '@/components/ui/Icon';
import { SCENARIO_DESC, SCENARIO_LABEL, VEHICLE_TYPES } from '@/domain/constants';
import { parseHHMM, todayISO } from '@/domain/time';
import type { RunConfig, Scenario, VehicleTypeKey } from '@/domain/types';
import { useUiStore } from '@/store/uiStore';

const SCENARIO_ICON: Record<Scenario, IconName> = { diaria: 'calendar', '5d': 'clock', colapso: 'alert' };

const clampInt = (v: string, min: number, max: number, def: number) => {
  const n = Number.parseInt(v, 10);
  return Number.isFinite(n) ? Math.min(max, Math.max(min, n)) : def;
};

export function StartGateDialog() {
  const gate = useUiStore((s) => s.gate);
  const close = useUiStore((s) => s.closeGate);
  const configure = useConfigureRun();
  const navigate = useNavigate();

  const [scenario, setScenario] = useState<Scenario>('5d');
  const [date, setDate] = useState(todayISO());
  const [time, setTime] = useState('07:00');
  const [fleet, setFleet] = useState({ auto: '6', moto: '10', bici: '8' });
  const [caps, setCaps] = useState({ noroeste: '1000', este: '1000' });
  const [shifts, setShifts] = useState(['07:00', '15:00', '23:00']);

  useEffect(() => {
    if (gate.open && gate.preset) setScenario(gate.preset);
  }, [gate.open, gate.preset]);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const cfg: RunConfig = {
      scenario,
      startDate: date,
      startTime: time,
      fleet: {
        auto: clampInt(fleet.auto, 0, 30, 6),
        moto: clampInt(fleet.moto, 0, 40, 10),
        bici: clampInt(fleet.bici, 0, 40, 8),
      },
      capacities: { noroeste: clampInt(caps.noroeste, 100, 5000, 1000), este: clampInt(caps.este, 100, 5000, 1000) },
      shiftStarts: [parseHHMM(shifts[0], 420), parseHHMM(shifts[1], 900), parseHHMM(shifts[2], 1380)],
    };
    configure.mutate(cfg, {
      onSuccess: () => {
        close();
        useUiStore.getState().toast(`${SCENARIO_LABEL[scenario]} configurada`, 'good', scenario === 'diaria' ? 'Registra el primer pedido para arrancar.' : 'La simulación está corriendo.');
        navigate(scenario === 'diaria' ? '/pedidos?nuevo=1' : '/monitor');
      },
    });
  };

  const totalFleet = (['auto', 'moto', 'bici'] as VehicleTypeKey[]).reduce((a, k) => a + clampInt(fleet[k], 0, 40, 0), 0);

  return (
    <Dialog
      open={gate.open}
      onClose={close}
      size="lg"
      title="Configurar ejecución"
      description="Elige el escenario y los parámetros de la operación. Los tres escenarios usan el mismo planificador."
      footer={
        <>
          <span className="mr-auto text-xs text-ink-3">
            Flota: <b className="text-ink-2">{totalFleet}</b> unidades
          </span>
          <button type="button" className="btn btn-secondary" onClick={close}>
            Cancelar
          </button>
          <button type="submit" form="gate-form" className="btn btn-primary" disabled={configure.isPending || totalFleet === 0}>
            <Icon name="play" size={13} />
            {scenario === 'diaria' ? 'Continuar al registro de pedidos' : 'Iniciar simulación'}
          </button>
        </>
      }
    >
      <form id="gate-form" onSubmit={submit} className="flex flex-col gap-6">
        <fieldset>
          <legend className="field-label mb-2">Escenario</legend>
          <div className="grid gap-2 sm:grid-cols-3">
            {(['diaria', '5d', 'colapso'] as Scenario[]).map((sc) => (
              <label
                key={sc}
                className={`flex cursor-pointer flex-col gap-2 rounded-xl border p-3 transition-colors ${
                  scenario === sc ? 'border-accent bg-accent-soft ring-1 ring-accent' : 'border-line hover:bg-surface-2'
                }`}
              >
                <input type="radio" name="scenario" value={sc} checked={scenario === sc} onChange={() => setScenario(sc)} className="sr-only" />
                <span className="flex items-center gap-2 text-[13px] font-semibold text-ink">
                  <Icon name={SCENARIO_ICON[sc]} size={15} className={scenario === sc ? 'text-accent-ink' : 'text-ink-3'} />
                  {SCENARIO_LABEL[sc]}
                </span>
                <span className="text-xs leading-relaxed text-ink-3">{SCENARIO_DESC[sc]}</span>
              </label>
            ))}
          </div>
        </fieldset>

        {scenario !== 'diaria' && (
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="field">
              <span className="field-label">Fecha de inicio</span>
              <input className="input" type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
            </label>
            <label className="field">
              <span className="field-label">Hora de inicio</span>
              <input className="input" type="time" value={time} onChange={(e) => setTime(e.target.value)} required />
            </label>
          </div>
        )}

        <fieldset>
          <legend className="field-label mb-2">Composición de la flota</legend>
          <div className="grid grid-cols-3 gap-3">
            {(['auto', 'moto', 'bici'] as VehicleTypeKey[]).map((k) => {
              const t = VEHICLE_TYPES[k];
              return (
                <label key={k} className="field">
                  <span className="flex items-center gap-1.5 text-xs text-ink-2">
                    <span className="size-2 rounded-sm" style={{ background: `var(${t.colorVar})` }} />
                    {t.plural}
                    <span className="text-ink-3">· {t.capacity} paq.</span>
                  </span>
                  <input className="input num" type="number" min={0} max={40} value={fleet[k]} onChange={(e) => setFleet({ ...fleet, [k]: e.target.value })} />
                </label>
              );
            })}
          </div>
        </fieldset>

        <div className="grid gap-6 sm:grid-cols-2">
          <fieldset>
            <legend className="field-label mb-2">Capacidad de almacenes intermedios</legend>
            <div className="grid grid-cols-2 gap-3">
              <label className="field">
                <span className="text-xs text-ink-2">Nor-Oeste</span>
                <input className="input num" type="number" min={100} max={5000} step={50} value={caps.noroeste} onChange={(e) => setCaps({ ...caps, noroeste: e.target.value })} />
              </label>
              <label className="field">
                <span className="text-xs text-ink-2">Este</span>
                <input className="input num" type="number" min={100} max={5000} step={50} value={caps.este} onChange={(e) => setCaps({ ...caps, este: e.target.value })} />
              </label>
            </div>
            <p className="field-hint mt-1.5">El Almacén Central tiene stock ilimitado. Recarga diaria a las 23:59:59.</p>
          </fieldset>
          <fieldset>
            <legend className="field-label mb-2">Inicio de turnos (8 h cada uno)</legend>
            <div className="grid grid-cols-3 gap-3">
              {shifts.map((s, i) => (
                <label key={i} className="field">
                  <span className="text-xs text-ink-2">Turno {i + 1}</span>
                  <input
                    className="input num"
                    type="time"
                    value={s}
                    onChange={(e) => setShifts(shifts.map((x, j) => (j === i ? e.target.value : x)))}
                  />
                </label>
              ))}
            </div>
          </fieldset>
        </div>
      </form>
    </Dialog>
  );
}
