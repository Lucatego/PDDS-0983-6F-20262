// Comandos (mutaciones) y consultas de TanStack Query sobre la pasarela activa.
// Los errores se muestran como aviso; cada formulario puede además leer mutation.error.
import { useMutation, useQuery } from '@tanstack/react-query';
import type { FallaTipo, FileKind, OrderInput, Point, RunConfig } from '@/domain/types';
import { useUiStore } from '@/store/uiStore';
import { gateway } from './instance';

const errorToast = (e: unknown) =>
  useUiStore.getState().toast('No se pudo completar la acción', 'critical', e instanceof Error ? e.message : String(e));

export function useCatalogos() {
  return useQuery({ queryKey: ['catalogos'], queryFn: () => gateway.getCatalogos(), staleTime: Infinity });
}

export function useConfigureRun() {
  return useMutation({ mutationFn: (cfg: RunConfig) => gateway.configure(cfg), onError: errorToast });
}

export function useRunControl() {
  return useMutation({
    mutationFn: (action: 'start' | 'stop' | 'reset') =>
      action === 'start' ? gateway.start() : action === 'stop' ? gateway.stop() : gateway.reset(),
    onError: errorToast,
  });
}

export function useRegisterOrder() {
  return useMutation({ mutationFn: (input: OrderInput) => gateway.registerOrder(input) });
}

export function useRegisterOrderBatch() {
  return useMutation({ mutationFn: (inputs: OrderInput[]) => gateway.registerOrderBatch(inputs), onError: errorToast });
}

export function useLoadFile() {
  return useMutation({ mutationFn: (v: { kind: FileKind; text: string }) => gateway.loadFile(v.kind, v.text) });
}

export function useRegisterAveria() {
  return useMutation({
    mutationFn: (v: { vehicleId: string; tipo: FallaTipo }) => gateway.registerAveria(v.vehicleId, v.tipo),
    onError: errorToast,
  });
}

export function useRegisterMantenimiento() {
  return useMutation({
    mutationFn: (v: { vehicleId: string; horas: number }) => gateway.registerMantenimiento(v.vehicleId, v.horas),
    onError: errorToast,
  });
}

export function useRegisterBloqueo() {
  return useMutation({ mutationFn: (v: { nodes: Point[]; horas: number }) => gateway.registerBloqueo(v.nodes, v.horas) });
}
