// Lee los tokens de color del tema activo para usarlos en canvas y ECharts, que no entienden var().
import { useEffect, useState } from 'react';
import { useUiStore } from '@/store/uiStore';

const TOKENS = [
  'surface',
  'surface-2',
  'surface-3',
  'line',
  'line-strong',
  'ink',
  'ink-2',
  'ink-3',
  'accent',
  'accent-soft',
  'good',
  'warning',
  'serious',
  'critical',
  'critical-soft',
  'veh-auto',
  'veh-moto',
  'veh-bici',
  'land',
  'road-local',
  'road-arterial',
  'map-label',
  'chart-grid',
  'chart-axis',
] as const;

export type Palette = Record<(typeof TOKENS)[number], string>;

export function readPalette(): Palette {
  const cs = getComputedStyle(document.documentElement);
  const out = {} as Palette;
  for (const t of TOKENS) out[t] = cs.getPropertyValue(`--${t}`).trim();
  return out;
}

/** Paleta reactiva: se recalcula al cambiar el tema del usuario o del sistema. */
export function usePalette(): Palette {
  const theme = useUiStore((s) => s.theme);
  const [systemDark, setSystemDark] = useState(() => window.matchMedia('(prefers-color-scheme: dark)').matches);
  const [palette, setPalette] = useState(readPalette);
  useEffect(() => {
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const on = () => setSystemDark(mq.matches);
    mq.addEventListener('change', on);
    return () => mq.removeEventListener('change', on);
  }, []);
  useEffect(() => {
    setPalette(readPalette());
  }, [theme, systemDark]);
  return palette;
}

/** #rrggbb + alfa (0–1) -> #rrggbbaa */
export function alpha(hex: string, a: number): string {
  const h = hex.length === 9 ? hex.slice(0, 7) : hex;
  return h + Math.round(Math.max(0, Math.min(1, a)) * 255).toString(16).padStart(2, '0');
}
