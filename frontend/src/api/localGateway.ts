// Pasarela local: el motor corre en el navegador, avanzado por un temporizador de ~30 Hz.
// Útil para demostraciones y para desarrollar la interfaz sin backend.
import { FALLA_TYPES, MODALIDADES, VEHICLE_TYPE_KEYS, VEHICLE_TYPES } from '@/domain/constants';
import type { LogEvent } from '@/domain/types';
import { SimulationEngine } from '@/engine/SimulationEngine';
import type { GatewayListeners, SimulationGateway } from './gateway';

export function createLocalGateway(): SimulationGateway {
  let listeners: GatewayListeners | null = null;
  const pendingLogs: LogEvent[] = [];
  const engine = new SimulationEngine((e) => {
    if (listeners) listeners.onLog(e);
    else pendingLogs.push(e);
  });

  // El reloj avanza por tiempo real transcurrido (no por frames): si el navegador frena los
  // temporizadores de una pestaña en segundo plano, el atraso se recupera en pasos de 0,25 s.
  const TICK_MS = 33;
  const MAX_CATCH_UP_SEC = 5;
  let timer = 0;
  let last = 0;
  const tick = () => {
    const now = performance.now();
    let dt = Math.min(MAX_CATCH_UP_SEC, (now - last) / 1000);
    last = now;
    while (dt > 0) {
      engine.tick(Math.min(0.25, dt));
      dt -= 0.25;
    }
    listeners?.onSnapshot(engine.snapshot());
  };

  // Las acciones son síncronas en el motor; se envuelven en promesas para cumplir el mismo
  // contrato asíncrono que el servidor y publicar de inmediato la nueva instantánea.
  const run = async <T>(fn: () => T): Promise<T> => {
    const r = fn();
    listeners?.onSnapshot(engine.snapshot());
    return r;
  };

  return {
    mode: 'local',
    connect(l) {
      listeners = l;
      l.onStatus('online');
      pendingLogs.splice(0).forEach((e) => l.onLog(e));
      l.onSnapshot(engine.snapshot());
      last = performance.now();
      timer = window.setInterval(tick, TICK_MS);
      return () => {
        window.clearInterval(timer);
        if (listeners === l) listeners = null;
      };
    },
    getCatalogos: async () => ({
      vehicleTypes: VEHICLE_TYPE_KEYS.map((k) => VEHICLE_TYPES[k]),
      fallaTypes: FALLA_TYPES,
      modalidades: MODALIDADES,
    }),
    configure: (cfg) => run(() => engine.configure(cfg)),
    start: () => run(() => engine.start()),
    stop: () => run(() => engine.stop()),
    reset: () => run(() => engine.reset()),
    registerOrder: (input) => run(() => engine.registerOrder(input)),
    registerOrderBatch: (inputs) => run(() => engine.registerOrderBatch(inputs)),
    loadFile: (kind, text) => run(() => engine.loadFile(kind, text)),
    registerAveria: (id, tipo) => run(() => engine.registerAveria(id, tipo)),
    registerMantenimiento: (id, horas) => run(() => engine.registerMantenimiento(id, horas)),
    registerBloqueo: (nodes, horas) => run(() => engine.registerBloqueo(nodes, horas)),
  };
}
