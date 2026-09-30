// Conecta la pasarela con los stores: la instantánea "viva" se guarda en cada mensaje y el
// store de React se actualiza a 4 Hz, que es lo que necesita el DOM (reloj, KPIs, tablas).
import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router';
import { gateway } from '@/api/instance';
import type { LogEvent } from '@/domain/types';
import { getLiveSnapshot, getLiveVersion, setLiveSnapshot } from '@/store/liveSnapshot';
import { useSimStore } from '@/store/simStore';
import { useUiStore } from '@/store/uiStore';

const DOM_REFRESH_MS = 250;

export function SimulationBridge() {
  const location = useLocation();
  const onEventsPage = useRef(false);
  onEventsPage.current = location.pathname.startsWith('/eventos');

  useEffect(() => {
    let pending: LogEvent[] = [];
    let lastVersion = -1;
    const { setSnapshot, pushLogs, setConnection } = useSimStore.getState();

    const unsubscribe = gateway.connect({
      onSnapshot: setLiveSnapshot,
      onLog: (e) => {
        pending.push(e);
        if (e.kind === 'critical' && /colapso/i.test(e.text)) {
          useUiStore.getState().toast('Colapso logístico', 'critical', e.text.replace(/\*\*/g, ''));
        } else if (/Simulación 5D completada/.test(e.text)) {
          useUiStore.getState().toast('Simulación 5D completada', 'good', e.text.replace(/\*\*/g, ''));
        }
      },
      onStatus: setConnection,
    });

    const timer = window.setInterval(() => {
      const v = getLiveVersion();
      const snap = getLiveSnapshot();
      if (snap && v !== lastVersion) {
        lastVersion = v;
        setSnapshot(snap);
      }
      if (pending.length) {
        pushLogs(pending, onEventsPage.current);
        pending = [];
      }
    }, DOM_REFRESH_MS);

    return () => {
      window.clearInterval(timer);
      unsubscribe();
      if (pending.length) pushLogs(pending, onEventsPage.current); // no perder eventos al remontar
    };
  }, []);

  return null;
}
