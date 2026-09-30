// Selecciona la fuente de datos según VITE_DATA_SOURCE (local | server). Instancia única por pestaña.
import type { SimulationGateway } from './gateway';
import { createLocalGateway } from './localGateway';
import { createStompGateway } from './stompGateway';

function defaultWsUrl(): string {
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
  return `${proto}://${window.location.host}/ws`;
}

export const gateway: SimulationGateway =
  import.meta.env.VITE_DATA_SOURCE === 'server'
    ? createStompGateway({
        apiUrl: import.meta.env.VITE_API_URL ?? '/api',
        wsUrl: import.meta.env.VITE_WS_URL ?? defaultWsUrl(),
      })
    : createLocalGateway();
