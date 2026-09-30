// Piezas pequeñas reutilizadas por todas las pantallas.
import type { ReactNode } from 'react';
import { Link } from 'react-router';
import type { RiskLevel } from '@/domain/types';
import { Icon, type IconName } from './Icon';

export type Tone = 'neutral' | 'accent' | 'good' | 'warning' | 'serious' | 'critical';

const DOT: Record<Tone, string> = {
  neutral: 'bg-ink-3',
  accent: 'bg-accent',
  good: 'bg-good',
  warning: 'bg-warning',
  serious: 'bg-serious',
  critical: 'bg-critical',
};

export const ICON_TONE: Record<Tone, string> = {
  neutral: 'bg-surface-3 text-ink-2',
  accent: 'bg-accent-soft text-accent-ink',
  good: 'bg-good-soft text-good-ink',
  warning: 'bg-warning-soft text-warning-ink',
  serious: 'bg-serious-soft text-serious-ink',
  critical: 'bg-critical-soft text-critical-ink',
};

export const TEXT_TONE: Record<Tone, string> = {
  neutral: 'text-ink',
  accent: 'text-accent-ink',
  good: 'text-good-ink',
  warning: 'text-warning-ink',
  serious: 'text-serious-ink',
  critical: 'text-critical-ink',
};

export function Badge({ tone = 'neutral', dot = true, children }: { tone?: Tone; dot?: boolean; children: ReactNode }) {
  return (
    <span className={`badge badge-${tone}`}>
      {dot && <span className={`badge-dot ${DOT[tone]}`} />}
      {children}
    </span>
  );
}

export const riskTone = (r: RiskLevel): Tone => r;

/** Encabezado de página estilo ERP: migas de pan, título, descripción y acciones. */
export function PageHeader({
  crumbs,
  title,
  description,
  actions,
}: {
  crumbs: string[];
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4 pb-5">
      <div className="min-w-0">
        <nav aria-label="Ruta de navegación" className="mb-1.5 flex items-center gap-1 text-xs text-ink-3">
          <Link to="/monitor" className="hover:text-ink-2">
            Centro de operaciones
          </Link>
          {crumbs.map((c) => (
            <span key={c} className="flex items-center gap-1">
              <Icon name="chevronRight" size={12} />
              {c}
            </span>
          ))}
        </nav>
        <h1 className="text-xl font-semibold tracking-tight text-ink">{title}</h1>
        {description && <p className="mt-1 max-w-3xl text-[13px] text-ink-3">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

export function StatCard({
  label,
  value,
  sub,
  icon,
  tone,
  trend,
}: {
  label: string;
  value: ReactNode;
  sub?: ReactNode;
  icon: IconName;
  tone?: Tone;
  trend?: ReactNode;
}) {
  const iconColor = ICON_TONE[tone ?? 'neutral'];
  return (
    <div className="card flex flex-col gap-3 p-4">
      <div className="flex items-center justify-between gap-2">
        <span className="text-[12.5px] font-medium text-ink-2">{label}</span>
        <span className={`flex size-8 items-center justify-center rounded-lg ${iconColor}`}>
          <Icon name={icon} />
        </span>
      </div>
      <div className="flex items-baseline gap-2">
        <span className="text-[26px] leading-none font-semibold tracking-tight text-ink">{value}</span>
        {trend}
      </div>
      {sub && <div className="text-xs text-ink-3">{sub}</div>}
    </div>
  );
}

export function EmptyState({ icon, title, children }: { icon: IconName; title: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 px-6 py-12 text-center">
      <span className="flex size-11 items-center justify-center rounded-xl bg-surface-3 text-ink-3">
        <Icon name={icon} size={20} />
      </span>
      <div className="text-sm font-medium text-ink">{title}</div>
      {children && <div className="max-w-sm text-xs text-ink-3">{children}</div>}
    </div>
  );
}

/** Barra de progreso fina (medidor) con texto accesible. */
export function Meter({ pct, tone, label }: { pct: number; tone: Tone; label: string }) {
  const p = Math.max(0, Math.min(100, pct));
  return (
    <div className="h-1.5 w-full overflow-hidden rounded-full bg-surface-3" role="meter" aria-valuenow={Math.round(p)} aria-valuemin={0} aria-valuemax={100} aria-label={label}>
      <div className={`h-full rounded-full ${DOT[tone]} transition-[width] duration-500`} style={{ width: `${p}%` }} />
    </div>
  );
}

/** Texto de bitácora con **negrita**. */
export function RichText({ text }: { text: string }) {
  const parts = text.split('**');
  return (
    <>
      {parts.map((p, i) =>
        i % 2 ? (
          <strong key={i} className="font-semibold text-ink">
            {p}
          </strong>
        ) : (
          <span key={i}>{p}</span>
        ),
      )}
    </>
  );
}

export function Toolbar({ children }: { children: ReactNode }) {
  return <div className="flex flex-wrap items-end gap-3 border-b border-line px-4 py-3">{children}</div>;
}
