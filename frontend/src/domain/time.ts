// Tiempo simulado: minutos desde las 00:00 del día 1. Turnos de 8 h con refrigerio entre la 4.ª y 5.ª hora.
import { SHIFT_DUR } from './constants';

const pad = (n: number) => String(n).padStart(2, '0');

export const mod1440 = (m: number) => ((m % 1440) + 1440) % 1440;

export function dayOf(simMin: number): number {
  return Math.floor(simMin / 1440) + 1;
}

export function fmtTime(m: number): string {
  const mm = mod1440(m);
  return `${pad(Math.floor(mm / 60))}:${pad(Math.floor(mm % 60))}`;
}

export function fmtTimeS(m: number): string {
  const mm = mod1440(m);
  return `${pad(Math.floor(mm / 60))}:${pad(Math.floor(mm % 60))}:${pad(Math.floor((mm % 1) * 60))}`;
}

/** "D2 · 14:30" */
export function fmtDayTime(m: number): string {
  return `D${dayOf(m)} · ${fmtTime(m)}`;
}

/** dd d hh:mm:ss de tiempo simulado transcurrido. */
export function fmtElapsedSim(totalMin: number): string {
  const t = Math.max(0, totalMin);
  const days = Math.floor(t / 1440);
  return `${pad(days)}d ${pad(Math.floor((t % 1440) / 60))}:${pad(Math.floor(t % 60))}:${pad(Math.floor((t % 1) * 60))}`;
}

export function fmtDuration(ms: number): string {
  const s = Math.max(0, Math.floor(ms / 1000));
  return `${pad(Math.floor(s / 3600))}:${pad(Math.floor(s / 60) % 60)}:${pad(s % 60)}`;
}

export function fmtMinutes(min: number): string {
  const m = Math.max(0, Math.round(min));
  if (m < 60) return `${m} min`;
  return `${Math.floor(m / 60)} h ${pad(m % 60)} min`;
}

export function parseHHMM(s: string, fallback: number): number {
  const m = /^(\d{1,2}):(\d{2})$/.exec(s.trim());
  if (!m) return fallback;
  const h = Number(m[1]);
  const mi = Number(m[2]);
  return h < 24 && mi < 60 ? h * 60 + mi : fallback;
}

export function toHHMM(min: number): string {
  return fmtTime(min);
}

/** Fecha calendario de un minuto de simulación. epochDate = aaaa-mm-dd del día 1. */
export function simDate(epochDate: string, simMin: number): Date {
  const [y, mo, d] = epochDate.split('-').map(Number);
  const base = new Date(y, (mo || 1) - 1, d || 1);
  return new Date(base.getTime() + simMin * 60000);
}

export function todayISO(): string {
  const d = new Date();
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export interface Shift {
  index: number;
  start: number;
  dur: number;
  label: string;
}

export function buildShifts(starts: number[]): Shift[] {
  return starts.map((start, i) => {
    const st = mod1440(start);
    return { index: i + 1, start: st, dur: SHIFT_DUR, label: `Turno ${i + 1} · ${fmtTime(st)}–${fmtTime(st + SHIFT_DUR)}` };
  });
}

function minutesIntoShift(tod: number, s: Shift): number {
  return mod1440(tod - s.start);
}

export function currentShift(simMin: number, shifts: Shift[]): Shift {
  const tod = mod1440(simMin);
  return shifts.find((s) => minutesIntoShift(tod, s) < s.dur) ?? shifts[0];
}

export function inMeal(simMin: number, shift: Shift): boolean {
  const m = minutesIntoShift(mod1440(simMin), shift);
  return m >= 4 * 60 && m < 5 * 60;
}
