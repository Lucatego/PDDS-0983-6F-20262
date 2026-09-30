// Opciones comunes de ECharts alineadas con la guía de visualización: rejilla y ejes en líneas
// finas y sólidas, un solo eje Y, marcas delgadas con extremos redondeados y tooltip siempre activo.
import type { Palette } from '@/lib/palette';

export function baseOption(p: Palette) {
  return {
    animationDuration: 300,
    textStyle: { fontFamily: "'Inter Variable', Inter, system-ui, sans-serif", color: p['ink-2'] },
    grid: { left: 8, right: 16, top: 16, bottom: 4, containLabel: true },
    tooltip: {
      trigger: 'axis',
      backgroundColor: p.surface,
      borderColor: p.line,
      borderWidth: 1,
      padding: [8, 10],
      textStyle: { color: p.ink, fontSize: 12 },
      extraCssText: 'box-shadow: 0 8px 24px rgba(16,24,40,.12); border-radius: 10px;',
      axisPointer: { type: 'line', lineStyle: { color: p['chart-axis'], width: 1 } },
    },
  };
}

export function valueAxis(p: Palette, extra: Record<string, unknown> = {}) {
  return {
    type: 'value',
    splitLine: { lineStyle: { color: p['chart-grid'], width: 1 } },
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: p['ink-3'], fontSize: 11 },
    ...extra,
  };
}

export function categoryAxis(p: Palette, data: (string | number)[], extra: Record<string, unknown> = {}) {
  return {
    type: 'category',
    data,
    axisLine: { lineStyle: { color: p['chart-axis'], width: 1 } },
    axisTick: { show: false },
    axisLabel: { color: p['ink-3'], fontSize: 11 },
    ...extra,
  };
}
