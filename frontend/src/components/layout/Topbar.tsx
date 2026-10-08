import { Link, useNavigate } from 'react-router';
import { useRunControl, useSetSpeed } from '@/api/hooks';
import { Icon } from '@/components/ui/Icon';
import { SCENARIO_SHORT, SPEED_FACTORS } from '@/domain/constants';
import type { Scenario } from '@/domain/types';
import { useSimStore } from '@/store/simStore';
import { useUiStore } from '@/store/uiStore';
import { SimClock } from './SimClock';

const SCENARIOS: Scenario[] = ['diaria', '5d', 'colapso'];

function ScenarioSwitcher() {
  const snap = useSimStore((s) => s.snapshot);
  const openGate = useUiStore((s) => s.openGate);
  const openReset = useUiStore((s) => s.openResetConfirm);
  const configured = snap?.configured ?? false;
  return (
    <div className="segmented" role="group" aria-label="Escenario">
      {SCENARIOS.map((sc) => {
        const active = configured && snap?.scenario === sc;
        return (
          <button
            key={sc}
            type="button"
            aria-pressed={active}
            onClick={() => {
              if (!configured) openGate(sc);
              else if (!active) openReset(sc);
            }}
          >
            {SCENARIO_SHORT[sc]}
          </button>
        );
      })}
    </div>
  );
}

/** Multiplicador de velocidad (×1, ×2, ×5, ×10) en caliente; solo Simulación 5D y Colapso en curso. */
function SpeedControl() {
  const snap = useSimStore((s) => s.snapshot);
  const setSpeed = useSetSpeed();
  if (!snap || !snap.configured || snap.scenario === 'diaria' || snap.collapsed || snap.finished) return null;
  const current = snap.speedFactor ?? 1;
  return (
    <div className="segmented" role="group" aria-label="Velocidad de la simulación" title="Velocidad de la simulación">
      {SPEED_FACTORS.map((f) => (
        <button
          key={f}
          type="button"
          aria-pressed={current === f}
          disabled={setSpeed.isPending}
          onClick={() => current !== f && setSpeed.mutate(f)}
          aria-label={`Velocidad ×${f}`}
        >
          ×{f}
        </button>
      ))}
    </div>
  );
}

function RunControls() {
  const snap = useSimStore((s) => s.snapshot);
  const openGate = useUiStore((s) => s.openGate);
  const openReset = useUiStore((s) => s.openResetConfirm);
  const toast = useUiStore((s) => s.toast);
  const navigate = useNavigate();
  const run = useRunControl();
  if (!snap) return null;

  const ended = snap.collapsed || snap.finished;
  const onPrimary = () => {
    if (!snap.configured) return openGate();
    if (ended) return openReset(snap.scenario);
    if (snap.waitingFirstOrder) {
      toast('Registra el primer pedido', 'warning', 'La operación día a día comienza cuando entra el primer pedido.');
      return navigate('/pedidos?nuevo=1');
    }
    run.mutate(snap.running ? 'stop' : 'start');
  };

  const label = !snap.configured ? 'Iniciar' : ended ? 'Nueva corrida' : snap.waitingFirstOrder ? 'Registrar pedido' : snap.running ? 'Pausar' : 'Reanudar';
  const icon = snap.running ? 'pause' : ended ? 'reset' : 'play';

  return (
    <div className="flex items-center gap-2">
      <button type="button" className={`btn ${snap.running ? 'btn-secondary' : 'btn-primary'}`} onClick={onPrimary} disabled={run.isPending} aria-label={label} title={label}>
        <Icon name={icon} size={14} />
        <span className="hidden sm:inline">{label}</span>
      </button>
      {snap.configured && (
        <button type="button" className="btn btn-ghost" onClick={() => openReset(null)} title="Detener y volver a configurar la ejecución">
          <Icon name="reset" size={15} />
          <span className="hidden 2xl:inline">Reiniciar</span>
        </button>
      )}
    </div>
  );
}

function RunStatus() {
  const snap = useSimStore((s) => s.snapshot);
  if (!snap) return null;
  const [text, cls] = !snap.configured
    ? ['Sin configurar', 'badge-neutral']
    : snap.collapsed
      ? ['Colapso', 'badge-critical']
      : snap.finished
        ? ['Completada', 'badge-good']
        : snap.waitingFirstOrder
          ? ['Esperando pedido', 'badge-warning']
          : snap.running
            ? ['En ejecución', 'badge-good']
            : ['En pausa', 'badge-warning'];
  return (
    <span className={`badge ${cls} hidden md:inline-flex`}>
      <span className={`badge-dot ${snap.running ? 'animate-pulse-soft bg-good' : 'bg-current'}`} />
      {text}
    </span>
  );
}

export function Topbar() {
  const theme = useUiStore((s) => s.theme);
  const setTheme = useUiStore((s) => s.setTheme);
  const setMobileNav = useUiStore((s) => s.setMobileNav);
  const unseen = useSimStore((s) => s.unseenLogs);
  const isDark = theme === 'dark' || (theme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);

  return (
    <header className="z-20 flex h-14 shrink-0 items-center gap-3 border-b border-line bg-surface px-3 sm:px-4">
      <button type="button" className="icon-btn lg:hidden" onClick={() => setMobileNav(true)} aria-label="Abrir menú">
        <Icon name="menu" size={18} />
      </button>
      <div className="hidden md:block">
        <ScenarioSwitcher />
      </div>
      <RunStatus />
      <div className="mx-auto flex min-w-0 justify-center">
        <SimClock />
      </div>
      <SpeedControl />
      <RunControls />
      <div className="h-6 w-px bg-line" />
      <button
        type="button"
        className="icon-btn"
        onClick={() => setTheme(isDark ? 'light' : 'dark')}
        aria-label={isDark ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
        title={isDark ? 'Modo claro' : 'Modo oscuro'}
      >
        <Icon name={isDark ? 'sun' : 'moon'} size={17} />
      </button>
      <Link to="/eventos" className="icon-btn relative" aria-label={`Registro de eventos${unseen ? `, ${unseen} nuevos` : ''}`} title="Registro de eventos">
        <Icon name="bell" size={17} />
        {unseen > 0 && (
          <span className="absolute top-1 right-1 min-w-4 rounded-full bg-critical px-1 text-center text-[9.5px] leading-4 font-semibold text-white">
            {unseen > 99 ? '99+' : unseen}
          </span>
        )}
      </Link>
    </header>
  );
}

export { ScenarioSwitcher };
