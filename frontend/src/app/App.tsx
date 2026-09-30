import { lazy, Suspense } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router';
import { AppShell } from '@/components/layout/AppShell';
import { MonitorPage } from '@/features/monitor/MonitorPage';
import { PedidosPage } from '@/features/pedidos/PedidosPage';
import { FlotaPage } from '@/features/flota/FlotaPage';
import { AlmacenesPage } from '@/features/almacenes/AlmacenesPage';
import { AveriasPage } from '@/features/incidencias/AveriasPage';
import { MantenimientoPage } from '@/features/incidencias/MantenimientoPage';
import { BloqueosPage } from '@/features/incidencias/BloqueosPage';
import { EventosPage } from '@/features/eventos/EventosPage';
import { ConfiguracionPage } from '@/features/configuracion/ConfiguracionPage';

// ECharts pesa ~1 MB: el dashboard se carga solo cuando se abre
const IndicadoresPage = lazy(() => import('@/features/indicadores/IndicadoresPage').then((m) => ({ default: m.IndicadoresPage })));

function Loading() {
  return <div className="py-20 text-center text-sm text-ink-3">Cargando…</div>;
}

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppShell />}>
          <Route index element={<Navigate to="/monitor" replace />} />
          <Route path="monitor" element={<MonitorPage />} />
          <Route
            path="indicadores"
            element={
              <Suspense fallback={<Loading />}>
                <IndicadoresPage />
              </Suspense>
            }
          />
          <Route path="pedidos" element={<PedidosPage />} />
          <Route path="flota" element={<FlotaPage />} />
          <Route path="almacenes" element={<AlmacenesPage />} />
          <Route path="averias" element={<AveriasPage />} />
          <Route path="mantenimiento" element={<MantenimientoPage />} />
          <Route path="bloqueos" element={<BloqueosPage />} />
          <Route path="eventos" element={<EventosPage />} />
          <Route path="configuracion" element={<ConfiguracionPage />} />
          <Route path="*" element={<Navigate to="/monitor" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
