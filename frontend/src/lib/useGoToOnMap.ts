import { useCallback } from 'react';
import { useNavigate } from 'react-router';
import { getLiveSnapshot } from '@/store/liveSnapshot';
import { useUiStore } from '@/store/uiStore';
import { isOnMap } from '@/domain/labels';

/** Abre el monitor centrado en una unidad (desde las tablas de Pedidos, Flota o Averías). */
export function useGoToVehicle() {
  const navigate = useNavigate();
  return useCallback(
    (id: string) => {
      const v = getLiveSnapshot()?.vehicles.find((x) => x.id === id);
      const ui = useUiStore.getState();
      if (!v || !isOnMap(v)) {
        ui.toast(`${id} está en almacén`, 'accent', 'Las unidades disponibles no se dibujan en el mapa.');
        return;
      }
      ui.select({ type: 'vehicle', id });
      ui.focusOn(v.pos, 3);
      navigate('/monitor');
    },
    [navigate],
  );
}
