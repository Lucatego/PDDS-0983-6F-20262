// Pasarela al backend: comandos por REST y estado en tiempo real por WebSocket/STOMP.
// Tópicos y endpoints documentados en README.md (sección "Contrato con el backend").
import { Client } from '@stomp/stompjs';
import type { Catalogos, FileLoadSummary, LogEvent, OrderResult, SimSnapshot } from '@/domain/types';
import type { SimulationGateway } from './gateway';
import { createRestClient } from './rest';

export const TOPIC_ESTADO = '/topic/simulacion/estado';
export const TOPIC_EVENTOS = '/topic/simulacion/eventos';

export function createStompGateway(opts: { apiUrl: string; wsUrl: string }): SimulationGateway {
  const api = createRestClient(opts.apiUrl);

  return {
    mode: 'server',
    connect(l) {
      const client = new Client({
        brokerURL: opts.wsUrl,
        reconnectDelay: 3000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
      });
      client.onConnect = () => {
        l.onStatus('online');
        client.subscribe(TOPIC_ESTADO, (m) => l.onSnapshot(JSON.parse(m.body) as SimSnapshot));
        client.subscribe(TOPIC_EVENTOS, (m) => l.onLog(JSON.parse(m.body) as LogEvent));
        // estado inicial, por si la simulación está detenida y no llegan difusiones
        api
          .get<SimSnapshot>('/simulacion/estado')
          .then(l.onSnapshot)
          .catch(() => undefined);
      };
      client.onWebSocketClose = () => l.onStatus('offline');
      client.onStompError = () => l.onStatus('offline');
      l.onStatus('connecting');
      client.activate();
      return () => {
        void client.deactivate();
      };
    },
    getCatalogos: () => api.get<Catalogos>('/catalogos'),
    configure: (cfg) => api.post('/simulacion/configuracion', cfg),
    start: () => api.post('/simulacion/iniciar'),
    stop: () => api.post('/simulacion/detener'),
    reset: () => api.post('/simulacion/reiniciar'),
    setSpeed: (factor) => api.post('/simulacion/velocidad', { factor }),
    registerOrder: (input) => api.post<OrderResult>('/pedidos', input),
    registerOrderBatch: (inputs) => api.post<OrderResult[]>('/pedidos/lote', inputs),
    loadFile: (kind, text) => api.postText<FileLoadSummary>(`/archivos/${kind}`, text),
    registerAveria: (vehicleId, tipo) => api.post('/averias', { vehicleId, tipo }),
    registerMantenimiento: (vehicleId, horas) => api.post('/mantenimientos', { vehicleId, horas }),
    registerBloqueo: (nodes, horas) => api.post('/bloqueos', { nodos: nodes, horas }),
  };
}
