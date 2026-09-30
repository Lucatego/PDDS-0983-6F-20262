import { useNavigate } from 'react-router';
import { useRunControl } from '@/api/hooks';
import { Dialog } from '@/components/ui/Dialog';
import { SCENARIO_LABEL } from '@/domain/constants';
import { useUiStore } from '@/store/uiStore';

export function ResetDialog() {
  const state = useUiStore((s) => s.resetConfirm);
  const close = useUiStore((s) => s.closeResetConfirm);
  const openGate = useUiStore((s) => s.openGate);
  const select = useUiStore((s) => s.select);
  const run = useRunControl();
  const navigate = useNavigate();

  const confirm = () => {
    const target = state.target;
    run.mutate('reset', {
      onSuccess: () => {
        close();
        select(null);
        navigate('/monitor');
        openGate(target);
      },
    });
  };

  return (
    <Dialog
      open={state.open}
      onClose={close}
      size="sm"
      title={state.target ? `¿Cambiar a ${SCENARIO_LABEL[state.target]}?` : '¿Reiniciar la ejecución?'}
      description="Se detiene la ejecución actual y se descartan los pedidos, rutas e incidencias en curso. Después podrás configurar una nueva corrida."
      footer={
        <>
          <button type="button" className="btn btn-secondary" onClick={close}>
            Cancelar
          </button>
          <button type="button" className="btn btn-danger" onClick={confirm} disabled={run.isPending}>
            Reiniciar
          </button>
        </>
      }
    />
  );
}
