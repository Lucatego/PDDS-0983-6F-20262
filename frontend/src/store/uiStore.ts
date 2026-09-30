// Estado de la interfaz: tema, semáforo, filtros del mapa, selección, diálogos y avisos.
// Tema y umbrales del semáforo se recuerdan en este navegador.
import { create } from 'zustand';
import { createJSONStorage, persist, type StateStorage } from 'zustand/middleware';
import { DEFAULT_THRESHOLDS, type Thresholds } from '@/domain/risk';
import type { Point, Scenario, VehicleTypeKey, WarehouseId } from '@/domain/types';

export type Theme = 'system' | 'light' | 'dark';

export type Selection =
  | { type: 'vehicle'; id: string }
  | { type: 'warehouse'; id: WarehouseId }
  | { type: 'bloqueo'; id: number }
  | { type: 'zone'; name: string; center: Point };

export type ToastKind = 'good' | 'warning' | 'critical' | 'accent';

export interface Toast {
  id: number;
  title: string;
  detail?: string;
  kind: ToastKind;
}

interface FocusRequest {
  pos: Point;
  scale: number;
  nonce: number;
}

interface UiState {
  theme: Theme;
  thresholds: Thresholds;
  sidebarCollapsed: boolean;
  mobileNavOpen: boolean;
  typeFilter: Record<VehicleTypeKey, boolean>;
  showRoutes: boolean;
  showTrails: boolean;
  selected: Selection | null;
  focus: FocusRequest | null;
  gate: { open: boolean; preset: Scenario | null };
  resetConfirm: { open: boolean; target: Scenario | null };
  toasts: Toast[];

  setTheme(t: Theme): void;
  setThresholds(t: Thresholds): void;
  toggleSidebar(): void;
  setMobileNav(open: boolean): void;
  toggleType(k: VehicleTypeKey): void;
  toggleRoutes(): void;
  toggleTrails(): void;
  select(s: Selection | null): void;
  focusOn(pos: Point, scale?: number): void;
  openGate(preset?: Scenario | null): void;
  closeGate(): void;
  openResetConfirm(target?: Scenario | null): void;
  closeResetConfirm(): void;
  toast(title: string, kind?: ToastKind, detail?: string): void;
  dismissToast(id: number): void;
}

// localStorage puede no existir o lanzar (ventana privada, datos bloqueados): se ignora sin romper la app
const safeStorage: StateStorage = {
  getItem: (k) => {
    try {
      return localStorage.getItem(k);
    } catch {
      return null;
    }
  },
  setItem: (k, v) => {
    try {
      localStorage.setItem(k, v);
    } catch {
      /* sin persistencia */
    }
  },
  removeItem: (k) => {
    try {
      localStorage.removeItem(k);
    } catch {
      /* sin persistencia */
    }
  },
};

let toastSeq = 1;

export function applyTheme(t: Theme): void {
  const root = document.documentElement;
  if (t === 'system') root.removeAttribute('data-theme');
  else root.setAttribute('data-theme', t);
}

export const useUiStore = create<UiState>()(
  persist(
    (set, get) => ({
      theme: 'system',
      thresholds: DEFAULT_THRESHOLDS,
      sidebarCollapsed: false,
      mobileNavOpen: false,
      typeFilter: { auto: true, moto: true, bici: true },
      showRoutes: true,
      showTrails: true,
      selected: null,
      focus: null,
      gate: { open: false, preset: null },
      resetConfirm: { open: false, target: null },
      toasts: [],

      setTheme: (theme) => {
        applyTheme(theme);
        set({ theme });
      },
      setThresholds: (thresholds) => set({ thresholds }),
      toggleSidebar: () => set((s) => ({ sidebarCollapsed: !s.sidebarCollapsed })),
      setMobileNav: (mobileNavOpen) => set({ mobileNavOpen }),
      toggleType: (k) => set((s) => ({ typeFilter: { ...s.typeFilter, [k]: !s.typeFilter[k] } })),
      toggleRoutes: () => set((s) => ({ showRoutes: !s.showRoutes })),
      toggleTrails: () => set((s) => ({ showTrails: !s.showTrails })),
      select: (selected) => set({ selected }),
      focusOn: (pos, scale = 2.5) => set({ focus: { pos, scale, nonce: (get().focus?.nonce ?? 0) + 1 } }),
      openGate: (preset = null) => set({ gate: { open: true, preset } }),
      closeGate: () => set({ gate: { open: false, preset: null } }),
      openResetConfirm: (target = null) => set({ resetConfirm: { open: true, target } }),
      closeResetConfirm: () => set({ resetConfirm: { open: false, target: null } }),
      toast: (title, kind = 'accent', detail) => {
        const id = toastSeq++;
        set((s) => ({ toasts: [...s.toasts.slice(-3), { id, title, detail, kind }] }));
        setTimeout(() => get().dismissToast(id), kind === 'critical' ? 7000 : 4200);
      },
      dismissToast: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
    }),
    {
      name: 'paqrap-ui',
      storage: createJSONStorage(() => safeStorage),
      partialize: (s) => ({
        theme: s.theme,
        thresholds: s.thresholds,
        sidebarCollapsed: s.sidebarCollapsed,
        showRoutes: s.showRoutes,
        showTrails: s.showTrails,
      }),
    },
  ),
);
