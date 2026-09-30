import { useEffect } from 'react';
import { Outlet, useLocation } from 'react-router';
import { StartGateDialog } from '@/features/run/StartGateDialog';
import { ResetDialog } from '@/features/run/ResetDialog';
import { Toaster } from '@/components/ui/Toaster';
import { useSimStore } from '@/store/simStore';
import { useUiStore } from '@/store/uiStore';
import { SimulationBridge } from '@/app/SimulationBridge';
import { ErrorBoundary } from './ErrorBoundary';
import { Sidebar } from './Sidebar';
import { ScenarioSwitcher, Topbar } from './Topbar';

export function AppShell() {
  const collapsed = useUiStore((s) => s.sidebarCollapsed);
  const mobileOpen = useUiStore((s) => s.mobileNavOpen);
  const setMobileNav = useUiStore((s) => s.setMobileNav);
  const markSeen = useSimStore((s) => s.markLogsSeen);
  const location = useLocation();

  useEffect(() => {
    setMobileNav(false);
    if (location.pathname.startsWith('/eventos')) markSeen();
  }, [location.pathname, setMobileNav, markSeen]);

  const fullBleed = location.pathname.startsWith('/monitor');

  return (
    <div className="flex h-dvh overflow-hidden bg-bg">
      <SimulationBridge />
      <aside className={`hidden shrink-0 transition-[width] duration-200 lg:block ${collapsed ? 'w-[68px]' : 'w-[248px]'}`}>
        <Sidebar collapsed={collapsed} />
      </aside>

      {mobileOpen && (
        <div className="animate-fade-in fixed inset-0 z-40 bg-[#0b111c]/50 lg:hidden" onMouseDown={(e) => e.target === e.currentTarget && setMobileNav(false)}>
          <div className="h-full w-[272px] shadow-lg">
            <Sidebar collapsed={false} onNavigate={() => setMobileNav(false)} />
          </div>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <Topbar />
        <div className="flex items-center justify-center border-b border-line bg-surface px-3 py-2 md:hidden">
          <ScenarioSwitcher />
        </div>
        <main id="contenido" className={`min-h-0 flex-1 ${fullBleed ? 'overflow-hidden' : 'overflow-y-auto'}`}>
          <ErrorBoundary resetKey={location.pathname}>
            {fullBleed ? (
              <Outlet />
            ) : (
              <div className="mx-auto w-full max-w-[1480px] px-4 py-6 sm:px-6 lg:px-8">
                <Outlet />
              </div>
            )}
          </ErrorBoundary>
        </main>
      </div>

      <StartGateDialog />
      <ResetDialog />
      <Toaster />
    </div>
  );
}
