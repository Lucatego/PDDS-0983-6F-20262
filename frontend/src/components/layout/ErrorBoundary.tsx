import { Component, type ReactNode } from 'react';
import { Icon } from '@/components/ui/Icon';

/** Si una pantalla falla, se muestra un aviso en su lugar y el resto de la aplicación sigue funcionando. */
export class ErrorBoundary extends Component<{ children: ReactNode; resetKey: string }, { error: Error | null }> {
  state = { error: null as Error | null };

  static getDerivedStateFromError(error: Error) {
    return { error };
  }

  componentDidUpdate(prev: { resetKey: string }) {
    if (prev.resetKey !== this.props.resetKey && this.state.error) this.setState({ error: null });
  }

  render() {
    if (!this.state.error) return this.props.children;
    return (
      <div className="mx-auto max-w-lg px-6 py-20 text-center">
        <span className="mx-auto mb-3 flex size-11 items-center justify-center rounded-xl bg-critical-soft text-critical-ink">
          <Icon name="alert" size={20} />
        </span>
        <h2 className="text-base font-semibold text-ink">Esta pantalla no se pudo mostrar</h2>
        <p className="mt-1.5 text-[13px] text-ink-3">La simulación sigue corriendo. Puedes volver a intentarlo o ir a otro módulo.</p>
        <pre className="mt-4 overflow-auto rounded-lg bg-surface-2 p-3 text-left font-mono text-xs text-ink-2">{this.state.error.message}</pre>
        <button type="button" className="btn btn-primary mt-4" onClick={() => this.setState({ error: null })}>
          Reintentar
        </button>
      </div>
    );
  }
}
