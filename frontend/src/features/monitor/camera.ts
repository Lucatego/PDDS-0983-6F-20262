// Cámara del mapa (zoom y desplazamiento). Solo navegación: nunca afecta a la simulación.
// El mundo va de (0,0) abajo-izquierda a (70,50) arriba-derecha; la pantalla tiene Y hacia abajo.
import { GRID_H, GRID_W } from '@/domain/constants';
import type { Point } from '@/domain/types';

const PAD = 28; // margen interior en píxeles para que los bordes de la retícula no queden pegados
export const MIN_SCALE = 1;
export const MAX_SCALE = 8;

export class Camera {
  W = 0;
  H = 0;
  scale = 1;
  cx = GRID_W / 2; // centro de la vista, en km
  cy = GRID_H / 2;

  resize(w: number, h: number): void {
    this.W = w;
    this.H = h;
    this.clamp();
  }

  /** píxeles por km con zoom 1: la retícula entera cabe en el contenedor */
  get baseUnit(): number {
    return Math.max(1, Math.min((this.W - PAD * 2) / GRID_W, (this.H - PAD * 2) / GRID_H));
  }

  get unit(): number {
    return this.baseUnit * this.scale;
  }

  toScreen(p: Point): Point {
    return { x: this.W / 2 + (p.x - this.cx) * this.unit, y: this.H / 2 - (p.y - this.cy) * this.unit };
  }

  toWorld(sx: number, sy: number): Point {
    return { x: this.cx + (sx - this.W / 2) / this.unit, y: this.cy - (sy - this.H / 2) / this.unit };
  }

  clamp(): void {
    this.scale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, this.scale));
    const halfW = this.W / 2 / this.unit;
    const halfH = this.H / 2 / this.unit;
    // si la vista es más grande que la retícula, se centra; si no, no deja salir de los bordes
    this.cx = halfW * 2 >= GRID_W ? GRID_W / 2 : Math.min(GRID_W - halfW + 1, Math.max(halfW - 1, this.cx));
    this.cy = halfH * 2 >= GRID_H ? GRID_H / 2 : Math.min(GRID_H - halfH + 1, Math.max(halfH - 1, this.cy));
  }

  zoomAt(sx: number, sy: number, factor: number): void {
    const before = this.toWorld(sx, sy);
    this.scale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, this.scale * factor));
    this.cx = before.x - (sx - this.W / 2) / this.unit;
    this.cy = before.y + (sy - this.H / 2) / this.unit;
    this.clamp();
  }

  panBy(dx: number, dy: number): void {
    this.cx -= dx / this.unit;
    this.cy += dy / this.unit;
    this.clamp();
  }

  centerOn(p: Point, scale?: number): void {
    if (scale !== undefined) this.scale = Math.max(this.scale, scale);
    this.cx = p.x;
    this.cy = p.y;
    this.clamp();
  }

  reset(): void {
    this.scale = 1;
    this.cx = GRID_W / 2;
    this.cy = GRID_H / 2;
    this.clamp();
  }
}
