import { useEffect, useState } from 'react';
import { CYCLE_DAYS_5D } from '@/domain/constants';
import { buildShifts, currentShift, fmtDuration, fmtElapsedSim, fmtTime, fmtTimeS, inMeal, simDate } from '@/domain/time';
import { useSimStore } from '@/store/simStore';
import { Icon } from '@/components/ui/Icon';

function useRealClock() {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const t = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(t);
  }, []);
  return now;
}

/** Reloj de la simulación: fecha y hora simuladas, día del ciclo, turno y tiempos transcurridos. */
export function SimClock() {
  const snap = useSimStore((s) => s.snapshot);
  const real = useRealClock();
  if (!snap) return null;

  const date = simDate(snap.epochDate, snap.simMin).toLocaleDateString('es-PE', { day: '2-digit', month: 'short', year: 'numeric' });
  const shifts = buildShifts(snap.shiftStarts);
  const shift = currentShift(snap.simMin, shifts);
  const meal = inMeal(snap.simMin, shift);
  const dayText =
    snap.scenario === '5d' ? `Día ${Math.min(snap.cycleDay - Math.floor(snap.runStartSimMin / 1440), CYCLE_DAYS_5D)} de ${CYCLE_DAYS_5D}` : `Día ${snap.cycleDay}`;

  return (
    <div className="flex min-w-0 items-center gap-3">
      <div className="flex min-w-0 items-center gap-2.5 rounded-lg border border-line bg-surface-2 py-1 pr-3 pl-1.5" title="Fecha y hora de la simulación">
        <span className={`flex size-7 items-center justify-center rounded-md ${snap.running ? 'bg-good-soft text-good-ink' : 'bg-surface-3 text-ink-3'}`}>
          <Icon name="clock" size={15} />
        </span>
        <div className="leading-tight">
          <div className="num font-mono text-[15px] font-semibold text-ink">{fmtTimeS(snap.simMin)}</div>
          <div className="hidden text-[10.5px] whitespace-nowrap text-ink-3 sm:block">
            {date} · {dayText}
          </div>
        </div>
      </div>

      <dl className="hidden items-center gap-4 text-[11.5px] xl:flex">
        <div className="leading-tight">
          <dt className="text-ink-3">Turno</dt>
          <dd className="flex items-center gap-1.5 font-medium whitespace-nowrap text-ink">
            T{shift.index} · {fmtTime(shift.start)}–{fmtTime(shift.start + shift.dur)}
            {meal && <span className="badge badge-warning py-0 text-[10.5px]">Refrigerio</span>}
          </dd>
        </div>
        <div className="leading-tight" title="Tiempo simulado transcurrido desde el inicio de la corrida">
          <dt className="text-ink-3">Simulado</dt>
          <dd className="num font-mono font-medium whitespace-nowrap text-ink">{fmtElapsedSim(snap.simMin - snap.runStartSimMin)}</dd>
        </div>
        <div className="leading-tight" title="Tiempo real de ejecución">
          <dt className="text-ink-3">Ejecución</dt>
          <dd className="num font-mono font-medium whitespace-nowrap text-ink">{fmtDuration(snap.runElapsedMs)}</dd>
        </div>
        <div className="hidden leading-tight 2xl:block" title="Hora real de este equipo">
          <dt className="text-ink-3">Hora real</dt>
          <dd className="num font-mono font-medium whitespace-nowrap text-ink-2">{real.toLocaleTimeString('es-PE', { hour12: false })}</dd>
        </div>
      </dl>
    </div>
  );
}
