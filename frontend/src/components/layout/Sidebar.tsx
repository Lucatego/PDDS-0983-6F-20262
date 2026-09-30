import { NavLink } from 'react-router';
import { gateway } from '@/api/instance';
import { Icon, type IconName } from '@/components/ui/Icon';
import { useSimStore } from '@/store/simStore';
import { useUiStore } from '@/store/uiStore';

interface NavItem {
  to: string;
  label: string;
  icon: IconName;
  badge?: (s: ReturnType<typeof useBadges>) => number;
}

const SECTIONS: { title: string; items: NavItem[] }[] = [
  {
    title: 'Operación',
    items: [
      { to: '/monitor', label: 'Monitor en vivo', icon: 'monitor' },
      { to: '/indicadores', label: 'Indicadores', icon: 'dashboard' },
    ],
  },
  {
    title: 'Gestión',
    items: [
      { to: '/pedidos', label: 'Pedidos', icon: 'package', badge: (b) => b.pendientes },
      { to: '/flota', label: 'Flota', icon: 'truck' },
      { to: '/almacenes', label: 'Almacenes', icon: 'warehouse' },
      { to: '/averias', label: 'Averías', icon: 'wrench', badge: (b) => b.averias },
      { to: '/mantenimiento', label: 'Mantenimiento', icon: 'calendar' },
      { to: '/bloqueos', label: 'Bloqueos', icon: 'barrier', badge: (b) => b.bloqueos },
    ],
  },
  {
    title: 'Sistema',
    items: [
      { to: '/eventos', label: 'Registro de eventos', icon: 'events', badge: (b) => b.eventos },
      { to: '/configuracion', label: 'Configuración', icon: 'settings' },
    ],
  },
];

function useBadges() {
  const snap = useSimStore((s) => s.snapshot);
  const eventos = useSimStore((s) => s.unseenLogs);
  return {
    pendientes: snap?.orders.filter((o) => o.status === 'pending').length ?? 0,
    averias: snap?.incidents.filter((i) => i.kind === 'falla').length ?? 0,
    bloqueos: snap?.incidents.filter((i) => i.kind === 'bloqueo').length ?? 0,
    eventos,
  };
}

export function Sidebar({ collapsed, onNavigate }: { collapsed: boolean; onNavigate?: () => void }) {
  const badges = useBadges();
  const connection = useSimStore((s) => s.connection);
  const toggle = useUiStore((s) => s.toggleSidebar);
  const local = gateway.mode === 'local';
  const statusText = local ? 'Motor local' : connection === 'online' ? 'Servidor conectado' : connection === 'connecting' ? 'Conectando…' : 'Sin conexión';
  const statusColor = local || connection === 'online' ? 'bg-good' : connection === 'connecting' ? 'bg-warning' : 'bg-critical';

  return (
    <div className="flex h-full flex-col bg-nav text-nav-ink">
      <div className={`flex h-14 shrink-0 items-center gap-2.5 border-b border-nav-line ${collapsed ? 'justify-center px-2' : 'px-4'}`}>
        <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-[#3a6ae6] to-[#2446a8] text-white shadow-md">
          <Icon name="package" size={17} />
        </span>
        {!collapsed && (
          <div className="min-w-0 leading-tight">
            <div className="text-[14px] font-semibold tracking-tight text-white">PaqRap</div>
            <div className="text-[11px] text-nav-ink-2">Centro de Operaciones</div>
          </div>
        )}
      </div>

      <nav className="flex-1 overflow-y-auto px-2.5 py-3" aria-label="Módulos">
        {SECTIONS.map((sec) => (
          <div key={sec.title} className="mb-4">
            {!collapsed ? (
              <div className="px-2.5 pb-1.5 text-[10.5px] font-semibold tracking-[0.08em] text-nav-ink-2 uppercase">{sec.title}</div>
            ) : (
              <div className="mx-auto mb-2 h-px w-6 bg-nav-line" />
            )}
            <ul className="flex flex-col gap-0.5">
              {sec.items.map((it) => {
                const count = it.badge?.(badges) ?? 0;
                return (
                  <li key={it.to}>
                    <NavLink
                      to={it.to}
                      onClick={onNavigate}
                      title={collapsed ? it.label : undefined}
                      className={({ isActive }) =>
                        `group relative flex h-9 items-center gap-3 rounded-lg text-[13px] font-medium transition-colors ${
                          collapsed ? 'justify-center' : 'px-2.5'
                        } ${isActive ? 'bg-nav-active text-white' : 'text-nav-ink hover:bg-nav-active/60 hover:text-white'}`
                      }
                    >
                      {({ isActive }) => (
                        <>
                          {isActive && <span className="absolute top-2 bottom-2 -left-2.5 w-[3px] rounded-r bg-[#5b8def]" />}
                          <Icon name={it.icon} size={17} className={isActive ? 'text-[#8fb2f6]' : 'text-nav-ink-2 group-hover:text-nav-ink'} />
                          {!collapsed && <span className="flex-1 truncate">{it.label}</span>}
                          {count > 0 &&
                            (collapsed ? (
                              <span className="absolute top-1.5 right-1.5 size-2 rounded-full bg-[#ef4444]" />
                            ) : (
                              <span
                                className={`min-w-5 rounded-full px-1.5 text-center text-[10.5px] leading-5 font-semibold ${
                                  it.to === '/averias' || it.to === '/bloqueos' ? 'bg-[#ef44442e] text-[#fca5a5]' : 'bg-white/10 text-white'
                                }`}
                              >
                                {count > 99 ? '99+' : count}
                              </span>
                            ))}
                        </>
                      )}
                    </NavLink>
                  </li>
                );
              })}
            </ul>
          </div>
        ))}
      </nav>

      <div className="shrink-0 border-t border-nav-line p-2.5">
        <div className={`mb-2 flex items-center gap-2 rounded-lg bg-nav-2 py-2 text-[11.5px] ${collapsed ? 'justify-center px-0' : 'px-2.5'}`} title={statusText}>
          <span className={`size-2 shrink-0 rounded-full ${statusColor} ${connection === 'connecting' && !local ? 'animate-pulse-soft' : ''}`} />
          {!collapsed && (
            <span className="truncate">
              {statusText}
              <span className="text-nav-ink-2"> · {local ? 'sin backend' : 'STOMP'}</span>
            </span>
          )}
        </div>
        <div className={`flex items-center gap-2.5 ${collapsed ? 'flex-col' : ''}`}>
          <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-[#2446a8] text-[11px] font-semibold text-white">GZ</span>
          {!collapsed && (
            <div className="min-w-0 flex-1 leading-tight">
              <div className="truncate text-[12.5px] font-medium text-white">Gandy Zinanyuca</div>
              <div className="text-[11px] text-nav-ink-2">Jefa de operaciones</div>
            </div>
          )}
          <button
            type="button"
            onClick={toggle}
            className="hidden size-7 items-center justify-center rounded-md text-nav-ink-2 hover:bg-nav-active hover:text-white lg:flex"
            aria-label={collapsed ? 'Expandir menú' : 'Contraer menú'}
            title={collapsed ? 'Expandir menú' : 'Contraer menú'}
          >
            <Icon name={collapsed ? 'chevronRight' : 'chevronLeft'} size={15} />
          </button>
        </div>
      </div>
    </div>
  );
}
