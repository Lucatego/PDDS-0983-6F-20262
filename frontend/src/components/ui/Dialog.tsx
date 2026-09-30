// Diálogo modal y panel lateral (drawer) accesibles: foco inicial, Escape para cerrar,
// clic fuera para cerrar y bloqueo del scroll de fondo.
import { useEffect, useId, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { Icon } from './Icon';

interface BaseProps {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: ReactNode;
  children?: ReactNode;
  footer?: ReactNode;
  /** No cerrar con Escape ni clic fuera (p. ej. configuración obligatoria). */
  dismissable?: boolean;
}

function useModalBehavior(open: boolean, onClose: () => void, dismissable: boolean, panel: React.RefObject<HTMLDivElement | null>) {
  useEffect(() => {
    if (!open) return;
    const prevFocus = document.activeElement as HTMLElement | null;
    const first = panel.current?.querySelector<HTMLElement>('[data-autofocus], input, select, textarea, button:not([data-close])');
    (first ?? panel.current)?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && dismissable) {
        e.stopPropagation();
        onClose();
      }
      if (e.key === 'Tab' && panel.current) {
        const items = panel.current.querySelectorAll<HTMLElement>('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])');
        const list = Array.from(items).filter((el) => !el.hasAttribute('disabled'));
        if (!list.length) return;
        const firstEl = list[0];
        const lastEl = list[list.length - 1];
        if (e.shiftKey && document.activeElement === firstEl) {
          e.preventDefault();
          lastEl.focus();
        } else if (!e.shiftKey && document.activeElement === lastEl) {
          e.preventDefault();
          firstEl.focus();
        }
      }
    };
    document.addEventListener('keydown', onKey);
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = overflow;
      prevFocus?.focus?.();
    };
  }, [open, onClose, dismissable, panel]);
}

export function Dialog({ open, onClose, title, description, children, footer, dismissable = true, size = 'md' }: BaseProps & { size?: 'sm' | 'md' | 'lg' }) {
  const panel = useRef<HTMLDivElement>(null);
  const titleId = useId();
  useModalBehavior(open, onClose, dismissable, panel);
  if (!open) return null;
  const width = size === 'sm' ? 'max-w-md' : size === 'lg' ? 'max-w-3xl' : 'max-w-xl';
  return createPortal(
    <div
      className="animate-fade-in fixed inset-0 z-50 flex items-end justify-center bg-[#0b111c]/50 p-0 backdrop-blur-[2px] sm:items-center sm:p-6"
      onMouseDown={(e) => dismissable && e.target === e.currentTarget && onClose()}
    >
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className={`animate-pop-in flex max-h-[92dvh] w-full ${width} flex-col overflow-hidden rounded-t-2xl border border-line bg-surface shadow-lg outline-none sm:rounded-2xl`}
      >
        <div className="flex items-start justify-between gap-4 px-6 pt-5 pb-4">
          <div className="min-w-0">
            <h2 id={titleId} className="text-base font-semibold text-ink">
              {title}
            </h2>
            {description && <div className="mt-1 text-[13px] text-ink-3">{description}</div>}
          </div>
          {dismissable && (
            <button type="button" data-close className="icon-btn -mt-1 -mr-2" onClick={onClose} aria-label="Cerrar">
              <Icon name="x" />
            </button>
          )}
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto px-6 pb-5">{children}</div>
        {footer && <div className="flex flex-wrap items-center justify-end gap-2 border-t border-line bg-surface-2 px-6 py-3">{footer}</div>}
      </div>
    </div>,
    document.body,
  );
}

export function Drawer({ open, onClose, title, description, children, footer, dismissable = true }: BaseProps) {
  const panel = useRef<HTMLDivElement>(null);
  const titleId = useId();
  useModalBehavior(open, onClose, dismissable, panel);
  if (!open) return null;
  return createPortal(
    <div
      className="animate-fade-in fixed inset-0 z-50 flex justify-end bg-[#0b111c]/40 backdrop-blur-[1px]"
      onMouseDown={(e) => dismissable && e.target === e.currentTarget && onClose()}
    >
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className="animate-slide-in-right flex h-full w-full max-w-lg flex-col border-l border-line bg-surface shadow-lg outline-none"
      >
        <div className="flex items-start justify-between gap-4 border-b border-line px-6 py-4">
          <div className="min-w-0">
            <h2 id={titleId} className="text-base font-semibold text-ink">
              {title}
            </h2>
            {description && <div className="mt-1 text-[13px] text-ink-3">{description}</div>}
          </div>
          <button type="button" data-close className="icon-btn -mr-2" onClick={onClose} aria-label="Cerrar">
            <Icon name="x" />
          </button>
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto px-6 py-5">{children}</div>
        {footer && <div className="flex items-center justify-end gap-2 border-t border-line bg-surface-2 px-6 py-3">{footer}</div>}
      </div>
    </div>,
    document.body,
  );
}
