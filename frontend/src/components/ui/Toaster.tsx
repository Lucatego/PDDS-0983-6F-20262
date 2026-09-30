import { useUiStore, type ToastKind } from '@/store/uiStore';
import { Icon, type IconName } from './Icon';

const STYLE: Record<ToastKind, { icon: IconName; color: string }> = {
  good: { icon: 'check', color: 'text-good-ink bg-good-soft' },
  warning: { icon: 'alert', color: 'text-warning-ink bg-warning-soft' },
  critical: { icon: 'alert', color: 'text-critical-ink bg-critical-soft' },
  accent: { icon: 'info', color: 'text-accent-ink bg-accent-soft' },
};

export function Toaster() {
  const toasts = useUiStore((s) => s.toasts);
  const dismiss = useUiStore((s) => s.dismissToast);
  return (
    <div className="pointer-events-none fixed top-16 right-4 z-[60] flex w-[min(380px,calc(100vw-2rem))] flex-col gap-2" role="status" aria-live="polite">
      {toasts.map((t) => (
        <div key={t.id} className="animate-slide-in-right pointer-events-auto flex gap-3 rounded-xl border border-line bg-surface p-3 pr-2 shadow-lg">
          <span className={`flex size-8 shrink-0 items-center justify-center rounded-lg ${STYLE[t.kind].color}`}>
            <Icon name={STYLE[t.kind].icon} />
          </span>
          <div className="min-w-0 flex-1 pt-0.5">
            <div className="text-[13px] font-semibold text-ink">{t.title}</div>
            {t.detail && <div className="mt-0.5 text-xs leading-relaxed text-ink-2">{t.detail}</div>}
          </div>
          <button type="button" className="icon-btn size-7 shrink-0" onClick={() => dismiss(t.id)} aria-label="Cerrar aviso">
            <Icon name="x" size={14} />
          </button>
        </div>
      ))}
    </div>
  );
}
