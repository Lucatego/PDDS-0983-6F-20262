// Dashboard de indicadores: KPIs, evolución temporal, cumplimiento por prioridad, flota, pedidos y zonas.
import { useMemo } from 'react';
import { ChartCard, LegendItem } from '@/components/charts/ChartCard';
import { EChart } from '@/components/charts/EChart';
import { baseOption, categoryAxis, valueAxis } from '@/components/charts/chartTheme';
import { EmptyState, PageHeader, StatCard, TEXT_TONE } from '@/components/ui/primitives';
import { GRID_H, GRID_W, PRIORITY_BUCKETS, SCENARIO_LABEL, VEHICLE_TYPES, VEHICLE_TYPE_KEYS } from '@/domain/constants';
import { fmtInt, fmtMoney, orderEstado } from '@/domain/labels';
import { RISK_LABEL, riskLevel, slackPct } from '@/domain/risk';
import { fmtDayTime } from '@/domain/time';
import type { VehicleState } from '@/domain/types';
import { usePalette, type Palette } from '@/lib/palette';
import { useSimStore } from '@/store/simStore';
import { useUiStore } from '@/store/uiStore';

const FLEET_STATES: { key: VehicleState[]; label: string; color: (p: Palette) => string }[] = [
  { key: ['toClient'], label: 'En ruta', color: (p) => p['veh-auto'] },
  { key: ['atClient'], label: 'Entregando', color: (p) => p['veh-bici'] },
  { key: ['returning'], label: 'Retornando', color: (p) => p['ink-3'] },
  { key: ['broken'], label: 'Averiada', color: (p) => p.critical },
  { key: ['maintenance'], label: 'Mantenimiento', color: (p) => p.warning },
  { key: ['idle', 'break'], label: 'Disponible', color: (p) => p['surface-3'] },
];

// rampa secuencial de un solo tono (azul) para el mapa de calor por zona
const SEQ = ['#cde2fb', '#9ec5f4', '#6da7ec', '#3987e5', '#256abf', '#184f95'];
// en oscuro la magnitud crece hacia el tono más claro (más contraste con la superficie)
const SEQ_DARK = ['#104281', '#184f95', '#1c5cab', '#2a78d6', '#5598e7', '#86b6ef'];

function isDarkSurface(hex: string): boolean {
  const n = Number.parseInt(hex.slice(1, 7), 16);
  return ((n >> 16) & 255) * 0.299 + ((n >> 8) & 255) * 0.587 + (n & 255) * 0.114 < 128;
}

export function IndicadoresPage() {
  const snap = useSimStore((s) => s.snapshot);
  const series = useSimStore((s) => s.series);
  const thresholds = useUiStore((s) => s.thresholds);
  const p = usePalette();

  const slaOption = useMemo(() => {
    const pts = series.filter((s) => s.sla !== null);
    return {
      ...baseOption(p),
      xAxis: categoryAxis(p, pts.map((s) => fmtDayTime(s.simMin)), { boundaryGap: false, axisLabel: { color: p['ink-3'], fontSize: 11, hideOverlap: true, showMaxLabel: false } }),
      yAxis: valueAxis(p, { min: 0, max: 100, axisLabel: { color: p['ink-3'], fontSize: 11, formatter: '{value}%' } }),
      series: [
        {
          name: 'Cumplimiento',
          type: 'line',
          data: pts.map((s) => Math.round((s.sla ?? 0) * 10) / 10),
          showSymbol: false,
          symbolSize: 8,
          lineStyle: { width: 2, color: p['veh-auto'] },
          itemStyle: { color: p['veh-auto'] },
          areaStyle: { color: p['veh-auto'], opacity: 0.08 },
          markLine: {
            symbol: 'none',
            silent: true,
            label: { formatter: `Meta verde ${thresholds.green}%`, color: p['ink-3'], fontSize: 10, position: 'insideEndTop' },
            lineStyle: { color: p.good, type: 'solid', width: 1 },
            data: [{ yAxis: thresholds.green }],
          },
        },
      ],
      tooltip: { ...baseOption(p).tooltip, valueFormatter: (v: number) => `${v}%` },
    };
  }, [series, p, thresholds.green]);

  const activosOption = useMemo(
    () => ({
      ...baseOption(p),
      xAxis: categoryAxis(p, series.map((s) => fmtDayTime(s.simMin)), { boundaryGap: false, axisLabel: { color: p['ink-3'], fontSize: 11, hideOverlap: true, showMaxLabel: false } }),
      yAxis: valueAxis(p, { minInterval: 1 }),
      series: [
        {
          name: 'Pedidos activos',
          type: 'line',
          data: series.map((s) => s.activos),
          showSymbol: false,
          lineStyle: { width: 2, color: p['veh-auto'] },
          itemStyle: { color: p['veh-auto'] },
        },
      ],
    }),
    [series, p],
  );

  const costoOption = useMemo(
    () => ({
      ...baseOption(p),
      xAxis: categoryAxis(p, series.map((s) => fmtDayTime(s.simMin)), { boundaryGap: false, axisLabel: { color: p['ink-3'], fontSize: 11, hideOverlap: true, showMaxLabel: false } }),
      yAxis: valueAxis(p, { axisLabel: { color: p['ink-3'], fontSize: 11, formatter: (v: number) => `S/ ${fmtInt(v)}` } }),
      series: [
        {
          name: 'Costo acumulado',
          type: 'line',
          data: series.map((s) => Math.round(s.costo)),
          showSymbol: false,
          lineStyle: { width: 2, color: p['veh-auto'] },
          itemStyle: { color: p['veh-auto'] },
          areaStyle: { color: p['veh-auto'], opacity: 0.08 },
        },
      ],
      tooltip: { ...baseOption(p).tooltip, valueFormatter: (v: number) => fmtMoney(v) },
    }),
    [series, p],
  );

  const prio = useMemo(() => {
    const rows = PRIORITY_BUCKETS.map((h) => {
      const b = snap?.stats.byPriority[h] ?? { delivered: 0, onTime: 0 };
      const pct = b.delivered ? Math.round((b.onTime / b.delivered) * 100) : null;
      return { h, ...b, pct };
    });
    const option = {
      ...baseOption(p),
      tooltip: { ...baseOption(p).tooltip, trigger: 'item', formatter: (i: { dataIndex: number }) => {
        const r = rows[i.dataIndex];
        return r.pct === null ? `${r.h} h: sin entregas` : `${r.h} h: ${r.pct}% a tiempo (${r.onTime}/${r.delivered})`;
      } },
      xAxis: categoryAxis(p, rows.map((r) => (r.h === 36 ? 'Regular 36 h' : `${r.h} h`))),
      yAxis: valueAxis(p, { min: 0, max: 100, axisLabel: { color: p['ink-3'], fontSize: 11, formatter: '{value}%' } }),
      series: [
        {
          type: 'bar',
          barMaxWidth: 18,
          // la etiqueta va en cada dato (texto fijo) para que no dependa de un formatter que ECharts fusiona entre renders
          data: rows.map((r) => ({
            value: r.pct ?? 0,
            itemStyle: { color: r.pct === null ? p['surface-3'] : p[riskLevel(r.pct, thresholds)], borderRadius: [4, 4, 0, 0] },
            label: { show: true, position: 'top', color: p['ink-2'], fontSize: 11, formatter: r.pct === null ? 'sin datos' : `${r.pct}%` },
          })),
        },
      ],
    };
    return { rows, option };
  }, [snap, p, thresholds]);

  const fleet = useMemo(() => {
    const counts = VEHICLE_TYPE_KEYS.map((k) => FLEET_STATES.map((st) => snap?.vehicles.filter((v) => v.type === k && st.key.includes(v.state)).length ?? 0));
    const option = {
      ...baseOption(p),
      tooltip: { ...baseOption(p).tooltip, axisPointer: { type: 'shadow', shadowStyle: { color: p['accent-soft'] } } },
      grid: { left: 8, right: 16, top: 8, bottom: 4, containLabel: true },
      xAxis: valueAxis(p, { minInterval: 1 }),
      yAxis: categoryAxis(p, VEHICLE_TYPE_KEYS.map((k) => VEHICLE_TYPES[k].plural), { inverse: true }),
      series: FLEET_STATES.map((st, i) => ({
        name: st.label,
        type: 'bar',
        stack: 'flota',
        barMaxWidth: 22,
        data: counts.map((c) => c[i]),
        itemStyle: { color: st.color(p), borderColor: p.surface, borderWidth: 1 },
      })),
    };
    return { counts, option };
  }, [snap, p]);

  const estados = useMemo(() => {
    const all = [...(snap?.orders ?? []), ...(snap?.orderHistory ?? [])];
    const labels = ['registrado', 'reprogramado', 'en ruta', 'entregado', 'no cumplido'] as const;
    const counts = labels.map((l) => all.filter((o) => orderEstado(o) === l).length);
    const option = {
      ...baseOption(p),
      tooltip: { ...baseOption(p).tooltip, axisPointer: { type: 'shadow', shadowStyle: { color: p['accent-soft'] } } },
      grid: { left: 8, right: 36, top: 8, bottom: 4, containLabel: true },
      xAxis: valueAxis(p, { minInterval: 1 }),
      yAxis: categoryAxis(p, labels.map((l) => l[0].toUpperCase() + l.slice(1)), { inverse: true }),
      series: [
        {
          name: 'Pedidos',
          type: 'bar',
          barMaxWidth: 16,
          data: counts,
          itemStyle: { color: p['veh-auto'], borderRadius: [0, 4, 4, 0] },
          label: { show: true, position: 'right', color: p['ink-2'], fontSize: 11 },
        },
      ],
    };
    return { labels, counts, option };
  }, [snap, p]);

  const zones = useMemo(() => {
    const cols = GRID_W / 10;
    const rows = GRID_H / 10;
    const grid = Array.from({ length: rows }, () => Array.from({ length: cols }, () => ({ activos: 0, riesgo: 0 })));
    for (const o of snap?.orders ?? []) {
      const c = Math.min(cols - 1, Math.floor(o.pos.x / 10));
      const r = Math.min(rows - 1, Math.floor(o.pos.y / 10));
      grid[r][c].activos++;
      if (riskLevel(slackPct(o, snap!.simMin), thresholds) !== 'good') grid[r][c].riesgo++;
    }
    const max = Math.max(1, ...grid.flat().map((g) => g.activos));
    return { grid, max, cols, rows };
  }, [snap, thresholds]);

  if (!snap) return null;
  const delivered = snap.stats.onTime + snap.stats.late;
  const sla = delivered ? Math.round((snap.stats.onTime / delivered) * 100) : null;
  const slaTone = sla === null ? 'neutral' : riskLevel(sla, thresholds);
  const faults = snap.incidents.filter((i) => i.kind === 'falla').length;
  const blocks = snap.incidents.filter((i) => i.kind === 'bloqueo').length;
  const empty = !snap.configured || series.length < 2;

  return (
    <>
      <PageHeader
        crumbs={['Operación', 'Indicadores']}
        title="Indicadores de desempeño"
        description={
          snap.configured
            ? `${SCENARIO_LABEL[snap.scenario]} · datos desde ${fmtDayTime(snap.runStartSimMin)} hasta ${fmtDayTime(snap.simMin)} (tiempo simulado).`
            : 'Configura una ejecución para empezar a registrar indicadores.'
        }
      />

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-5">
        <StatCard label="Pedidos activos" value={fmtInt(snap.orders.length)} icon="package" sub={`${snap.orders.filter((o) => o.status === 'pending').length} por asignar`} />
        <StatCard
          label="Cumplimiento de plazos"
          value={<span className={TEXT_TONE[slaTone]}>{sla === null ? '—' : `${sla}%`}</span>}
          icon="gauge"
          tone={slaTone}
          sub={sla === null ? 'Sin entregas aún' : `${RISK_LABEL[riskLevel(sla, thresholds)]} · meta ${thresholds.green}%`}
        />
        <StatCard label="Entregados" value={fmtInt(snap.stats.deliveredTotal)} icon="check" tone="good" sub={`${snap.stats.deliveredToday} hoy · ${snap.stats.late} con retraso`} />
        <StatCard label="Costo logístico" value={fmtMoney(snap.stats.cost)} icon="coins" sub={`${fmtInt(snap.stats.distanceKm)} km recorridos`} />
        <StatCard label="Incidencias activas" value={faults + blocks} icon="alert" tone={faults + blocks ? 'warning' : 'neutral'} sub={`${blocks} bloqueos · ${faults} averías`} />
      </div>

      {empty ? (
        <div className="card mt-4">
          <EmptyState icon="chart" title="Aún no hay suficientes datos">
            Las series se construyen con una muestra cada 30 minutos simulados. Inicia o reanuda la ejecución para verlas.
          </EmptyState>
        </div>
      ) : (
        <div className="mt-4 grid gap-4 lg:grid-cols-3">
          <ChartCard
            className="lg:col-span-2"
            title="Cumplimiento de plazos en el tiempo"
            subtitle="Porcentaje acumulado de entregas a tiempo"
            table={{ columns: ['Momento', 'Cumplimiento'], rows: series.filter((s) => s.sla !== null).map((s) => [fmtDayTime(s.simMin), `${(s.sla ?? 0).toFixed(1)}%`]) }}
          >
            <EChart option={slaOption} height={260} ariaLabel="Evolución del porcentaje de entregas a tiempo" />
          </ChartCard>
          <ChartCard
            title="Cumplimiento por prioridad"
            subtitle="Entregas a tiempo por plazo comprometido"
            table={{ columns: ['Plazo', 'A tiempo', 'Entregados', '%'], rows: prio.rows.map((r) => [`${r.h} h`, r.onTime, r.delivered, r.pct === null ? '—' : `${r.pct}%`]) }}
          >
            <EChart option={prio.option} height={260} ariaLabel="Porcentaje de entregas a tiempo por prioridad" />
          </ChartCard>

          <ChartCard
            title="Pedidos activos"
            subtitle="Pedidos registrados aún sin entregar"
            table={{ columns: ['Momento', 'Activos'], rows: series.map((s) => [fmtDayTime(s.simMin), s.activos]) }}
          >
            <EChart option={activosOption} height={220} ariaLabel="Evolución de los pedidos activos" />
          </ChartCard>
          <ChartCard
            title="Estado de la flota"
            subtitle="Unidades por tipo y estado, ahora"
            legend={FLEET_STATES.map((s) => (
              <LegendItem key={s.label} color={s.color(p)} label={s.label} />
            ))}
            table={{
              columns: ['Tipo', ...FLEET_STATES.map((s) => s.label)],
              rows: VEHICLE_TYPE_KEYS.map((k, i) => [VEHICLE_TYPES[k].plural, ...fleet.counts[i]]),
            }}
          >
            <EChart option={fleet.option} height={180} ariaLabel="Unidades de la flota por tipo y estado" />
          </ChartCard>
          <ChartCard
            title="Pedidos por estado"
            subtitle="Activos e historial de la corrida"
            table={{ columns: ['Estado', 'Pedidos'], rows: estados.labels.map((l, i) => [l, estados.counts[i]]) }}
          >
            <EChart option={estados.option} height={200} ariaLabel="Cantidad de pedidos por estado" />
          </ChartCard>

          <ChartCard
            className="lg:col-span-2"
            title="Costo logístico acumulado"
            subtitle="Suma de km recorridos por la tarifa de cada tipo de unidad"
            table={{ columns: ['Momento', 'Costo'], rows: series.map((s) => [fmtDayTime(s.simMin), fmtMoney(s.costo)]) }}
          >
            <EChart option={costoOption} height={220} ariaLabel="Evolución del costo logístico acumulado" />
          </ChartCard>

          <section className="card p-4">
            <h3 className="card-title">Pedidos activos por zona</h3>
            <p className="card-subtitle mt-0.5">Sectores de 10 × 10 km; el número en rojo son pedidos en riesgo o críticos</p>
            <div className="mt-3 grid gap-[2px]" style={{ gridTemplateColumns: `repeat(${zones.cols}, minmax(0,1fr))` }}>
              {zones.grid
                .slice()
                .reverse()
                .flatMap((row, ri) =>
                  row.map((cell, ci) => {
                    const level = cell.activos ? Math.min(SEQ.length - 1, Math.floor((cell.activos / zones.max) * (SEQ.length - 1))) : -1;
                    const ramp = isDarkSurface(p.surface) ? SEQ_DARK : SEQ;
                    const bg = level < 0 ? p['surface-2'] : ramp[level];
                    const dark = isDarkSurface(p.surface) ? level < 3 : level >= 3;
                    const y0 = (zones.rows - 1 - ri) * 10;
                    return (
                      <div
                        key={`${ri}-${ci}`}
                        className="flex aspect-square flex-col items-center justify-center rounded-md text-[11px] leading-tight"
                        style={{ background: bg, color: dark ? '#fff' : level < 0 ? p['ink-3'] : '#0d366b' }}
                        title={`Sector (${ci * 10}–${ci * 10 + 10}, ${y0}–${y0 + 10}): ${cell.activos} activos, ${cell.riesgo} en riesgo`}
                      >
                        <span className="font-semibold">{cell.activos || ''}</span>
                        {cell.riesgo > 0 && <span className={`text-[9.5px] font-semibold ${dark ? 'text-[#fecaca]' : 'text-critical-ink'}`}>{cell.riesgo}!</span>}
                      </div>
                    );
                  }),
                )}
            </div>
            <div className="mt-2 flex items-center gap-1 text-[10.5px] text-ink-3">
              Menos
              {(isDarkSurface(p.surface) ? SEQ_DARK : SEQ).map((c) => (
                <i key={c} className="h-2 w-4 rounded-sm" style={{ background: c }} />
              ))}
              Más
            </div>
          </section>
        </div>
      )}
    </>
  );
}
