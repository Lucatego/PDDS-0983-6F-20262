import { gateway } from '@/api/instance';
import { Icon } from '@/components/ui/Icon';
import { Badge, PageHeader } from '@/components/ui/primitives';
import { FALLA_TYPES, SIM_MIN_PER_SEC, VEHICLE_TYPES, VEHICLE_TYPE_KEYS } from '@/domain/constants';
import { DEFAULT_THRESHOLDS, riskLevel } from '@/domain/risk';
import { useSimStore } from '@/store/simStore';
import { useUiStore, type Theme } from '@/store/uiStore';

function Card({ title, description, children }: { title: string; description: string; children: React.ReactNode }) {
  return (
    <section className="card">
      <div className="border-b border-line px-5 py-4">
        <h2 className="card-title">{title}</h2>
        <p className="card-subtitle mt-0.5">{description}</p>
      </div>
      <div className="px-5 py-5">{children}</div>
    </section>
  );
}

export function ConfiguracionPage() {
  const thresholds = useUiStore((s) => s.thresholds);
  const setThresholds = useUiStore((s) => s.setThresholds);
  const theme = useUiStore((s) => s.theme);
  const setTheme = useUiStore((s) => s.setTheme);
  const connection = useSimStore((s) => s.connection);

  const setGreen = (g: number) => setThresholds({ green: g, amber: Math.min(thresholds.amber, g - 5) });
  const setAmber = (a: number) => setThresholds({ green: Math.max(thresholds.green, a + 5), amber: a });

  return (
    <>
      <PageHeader crumbs={['Sistema', 'Configuración']} title="Configuración" description="Preferencias de visualización y parámetros de referencia del caso PaqRap." />
      <div className="grid gap-4 lg:grid-cols-2">
        <Card title="Semáforo" description="Umbrales que colorean pedidos, almacenes, flota e indicadores. Se guardan en este navegador.">
          <div className="grid gap-5">
            <label className="field">
              <span className="flex items-center justify-between">
                <span className="field-label">Verde a partir de</span>
                <b className="num font-mono text-sm text-good-ink">{thresholds.green}%</b>
              </span>
              <input type="range" min={30} max={95} value={thresholds.green} onChange={(e) => setGreen(Number(e.target.value))} className="accent-[var(--good)]" />
            </label>
            <label className="field">
              <span className="flex items-center justify-between">
                <span className="field-label">Ámbar a partir de</span>
                <b className="num font-mono text-sm text-warning-ink">{thresholds.amber}%</b>
              </span>
              <input type="range" min={5} max={65} value={thresholds.amber} onChange={(e) => setAmber(Number(e.target.value))} className="accent-[var(--warning)]" />
            </label>
            <div>
              <div className="flex h-2.5 overflow-hidden rounded-full">
                <div className="bg-critical" style={{ width: `${thresholds.amber}%` }} />
                <div className="bg-warning" style={{ width: `${thresholds.green - thresholds.amber}%` }} />
                <div className="bg-good flex-1" />
              </div>
              <div className="mt-2 flex justify-between text-xs text-ink-3">
                <span>
                  <Badge tone="critical">Crítico</Badge> &lt; {thresholds.amber}%
                </span>
                <span>
                  <Badge tone="warning">En riesgo</Badge>
                </span>
                <span>
                  ≥ {thresholds.green}% <Badge tone="good">En plazo</Badge>
                </span>
              </div>
            </div>
            <p className="text-xs text-ink-3">
              Para pedidos, el valor es el porcentaje del plazo que queda; para almacenes, la ocupación; para la flota, las unidades disponibles. Ejemplo: 50% se
              muestra como <b>{riskLevel(50, thresholds) === 'good' ? 'verde' : riskLevel(50, thresholds) === 'warning' ? 'ámbar' : 'rojo'}</b>.
            </p>
            <button type="button" className="btn btn-secondary self-start" onClick={() => setThresholds(DEFAULT_THRESHOLDS)}>
              Restablecer ({DEFAULT_THRESHOLDS.green}% / {DEFAULT_THRESHOLDS.amber}%)
            </button>
          </div>
        </Card>

        <Card title="Apariencia" description="Tema de la interfaz. «Sistema» sigue la preferencia del equipo.">
          <div className="grid grid-cols-3 gap-2">
            {(
              [
                ['system', 'Sistema', 'monitor'],
                ['light', 'Claro', 'sun'],
                ['dark', 'Oscuro', 'moon'],
              ] as [Theme, string, 'monitor' | 'sun' | 'moon'][]
            ).map(([t, label, icon]) => (
              <button
                key={t}
                type="button"
                aria-pressed={theme === t}
                onClick={() => setTheme(t)}
                className={`flex flex-col items-center gap-2 rounded-xl border px-3 py-4 text-[13px] font-medium ${theme === t ? 'border-accent bg-accent-soft text-accent-ink' : 'border-line text-ink-2 hover:bg-surface-2'}`}
              >
                <Icon name={icon} size={18} />
                {label}
              </button>
            ))}
          </div>
          <div className="mt-5 rounded-xl border border-line px-4 py-3 text-[13px]">
            <div className="flex items-center justify-between">
              <span className="text-ink-3">Fuente de datos</span>
              <span className="font-medium text-ink">{gateway.mode === 'local' ? 'Motor local (navegador)' : 'Servidor PaqRap'}</span>
            </div>
            {gateway.mode === 'server' && (
              <div className="mt-1.5 flex items-center justify-between">
                <span className="text-ink-3">Conexión en tiempo real</span>
                <Badge tone={connection === 'online' ? 'good' : connection === 'connecting' ? 'warning' : 'critical'}>
                  {connection === 'online' ? 'Conectado' : connection === 'connecting' ? 'Conectando' : 'Sin conexión'}
                </Badge>
              </div>
            )}
            <p className="mt-2 text-xs text-ink-3">
              Se cambia con la variable <code className="font-mono">VITE_DATA_SOURCE</code> (local | server) al construir la aplicación.
            </p>
          </div>
        </Card>

        <Card title="Flota (parámetros del caso)" description="Capacidad, velocidad y costo por tipo de unidad.">
          <table className="data-table">
            <thead>
              <tr>
                <th>Tipo</th>
                <th className="text-right">Capacidad</th>
                <th className="text-right">Velocidad</th>
                <th className="text-right">Costo</th>
              </tr>
            </thead>
            <tbody>
              {VEHICLE_TYPE_KEYS.map((k) => {
                const t = VEHICLE_TYPES[k];
                return (
                  <tr key={k}>
                    <td>
                      <span className="flex items-center gap-2 font-medium text-ink">
                        <span className="size-2.5 rounded-full" style={{ background: `var(${t.colorVar})` }} />
                        {t.label} <span className="code text-ink-3">{t.prefix}NN</span>
                      </span>
                    </td>
                    <td className="num text-right">{t.capacity} paq.</td>
                    <td className="num text-right">{t.speed} km/h</td>
                    <td className="num text-right">S/ {t.cost.toFixed(2)}/km</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </Card>

        <Card title="Operación (parámetros del caso)" description="Reglas fijas que aplica el planificador.">
          <dl className="grid gap-2 text-[13px]">
            {[
              ['Retícula', '70 × 50 km, nodos cada 1 km, calles de doble sentido'],
              ['Plazos', 'Regular 36 h · priorizada 4, 8, 12 o 18 h'],
              ['Entrega al cliente', '1 hora'],
              ['Turnos', '3 turnos de 8 h con refrigerio de 1 h'],
              ['Almacenes intermedios', 'Recarga a capacidad a las 23:59:59'],
              ['Averías', FALLA_TYPES.map((f) => `${f.short} ${f.minMin}–${f.maxMin} min`).join(' · ')],
              ['Velocidad de simulación', `${SIM_MIN_PER_SEC} min simulados por segundo (modo local)`],
            ].map(([k, v]) => (
              <div key={k} className="flex justify-between gap-4 border-b border-line pb-2 last:border-0">
                <dt className="text-ink-3">{k}</dt>
                <dd className="text-right font-medium text-ink">{v}</dd>
              </div>
            ))}
          </dl>
        </Card>
      </div>
    </>
  );
}
