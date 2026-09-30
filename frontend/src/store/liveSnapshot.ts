// Última instantánea recibida, sin pasar por React. El canvas del mapa la lee en cada frame
// (60 fps) sin provocar renders; el DOM recibe una copia limitada a 4 Hz por el store.
import type { SimSnapshot } from '@/domain/types';

let current: SimSnapshot | null = null;
let version = 0;

export function setLiveSnapshot(s: SimSnapshot): void {
  current = s;
  version++;
}

export function getLiveSnapshot(): SimSnapshot | null {
  return current;
}

export function getLiveVersion(): number {
  return version;
}
