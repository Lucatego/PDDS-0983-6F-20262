// Estado de la simulación visible para React: instantánea (a 4 Hz), bitácora de eventos,
// estado de la conexión y series temporales para el dashboard de indicadores.
import { create } from 'zustand';
import type { ConnectionStatus } from '@/api/gateway';
import type { LogEvent, SimSnapshot } from '@/domain/types';

export interface SeriesPoint {
  simMin: number;
  activos: number;
  sla: number | null;
  costo: number;
  enTransito: number;
}

const LOG_LIMIT = 800;
const SERIES_STEP_MIN = 30; // una muestra cada 30 min simulados
const SERIES_LIMIT = 2000;

interface SimState {
  snapshot: SimSnapshot | null;
  logs: LogEvent[];
  unseenLogs: number;
  connection: ConnectionStatus;
  series: SeriesPoint[];
  setSnapshot(s: SimSnapshot): void;
  pushLogs(e: LogEvent[], seen: boolean): void;
  markLogsSeen(): void;
  setConnection(c: ConnectionStatus): void;
}

export const useSimStore = create<SimState>()((set) => ({
  snapshot: null,
  logs: [],
  unseenLogs: 0,
  connection: 'connecting',
  series: [],
  setSnapshot: (s) =>
    set((st) => {
      let series = st.series;
      // nueva corrida: la serie se reinicia
      if (!s.configured || (series.length && s.simMin < series[series.length - 1].simMin)) series = [];
      const last = series[series.length - 1];
      if (s.configured && (!last || s.simMin - last.simMin >= SERIES_STEP_MIN)) {
        const delivered = s.stats.onTime + s.stats.late;
        series = [
          ...series.slice(-SERIES_LIMIT + 1),
          {
            simMin: s.simMin,
            activos: s.orders.length,
            sla: delivered ? (s.stats.onTime / delivered) * 100 : null,
            costo: s.stats.cost,
            enTransito: s.orders.filter((o) => o.status === 'assigned').reduce((a, o) => a + o.qty, 0),
          },
        ];
      }
      return { snapshot: s, series };
    }),
  pushLogs: (e, seen) =>
    set((st) => ({
      logs: [...st.logs, ...e].slice(-LOG_LIMIT),
      unseenLogs: seen ? 0 : st.unseenLogs + e.length,
    })),
  markLogsSeen: () => set({ unseenLogs: 0 }),
  setConnection: (connection) => set({ connection }),
}));
