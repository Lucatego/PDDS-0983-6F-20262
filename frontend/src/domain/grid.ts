// Geometría de la retícula y caminos más cortos (BFS): cada arista mide 1 km, es de doble sentido
// y las aristas bloqueadas no se pueden recorrer.
import { GRID_H, GRID_W } from './constants';
import type { Point, Vehicle } from './types';

export const dist = (a: Point, b: Point) => Math.hypot(a.x - b.x, a.y - b.y);
export const roundNode = (p: Point): Point => ({ x: Math.round(p.x), y: Math.round(p.y) });
const key = (p: Point) => `${p.x},${p.y}`;

export function edgeKey(a: Point, b: Point): string {
  const swap = a.x > b.x || (a.x === b.x && a.y > b.y);
  const [p, q] = swap ? [b, a] : [a, b];
  return `${p.x},${p.y}-${q.x},${q.y}`;
}

export function neighborsOf(n: Point): Point[] {
  const out: Point[] = [];
  if (n.x > 0) out.push({ x: n.x - 1, y: n.y });
  if (n.x < GRID_W) out.push({ x: n.x + 1, y: n.y });
  if (n.y > 0) out.push({ x: n.x, y: n.y - 1 });
  if (n.y < GRID_H) out.push({ x: n.x, y: n.y + 1 });
  return out;
}

export function inGrid(p: Point): boolean {
  return p.x >= 0 && p.x <= GRID_W && p.y >= 0 && p.y <= GRID_H;
}

export function computeRoute(fromPos: Point, toPos: Point, blocked: ReadonlySet<string>): Point[] {
  const from = roundNode(fromPos);
  const to = roundNode(toPos);
  const startKey = key(from);
  const goalKey = key(to);
  if (startKey === goalKey) return [from];
  const cameFrom = new Map<string, Point>();
  const visited = new Set([startKey]);
  const queue: Point[] = [from];
  for (let qi = 0; qi < queue.length; qi++) {
    const cur = queue[qi];
    if (key(cur) === goalKey) break;
    for (const nb of neighborsOf(cur)) {
      const nk = key(nb);
      if (visited.has(nk) || blocked.has(edgeKey(cur, nb))) continue;
      visited.add(nk);
      cameFrom.set(nk, cur);
      queue.push(nb);
    }
  }
  if (!visited.has(goalKey)) return [from, to]; // destino aislado: no debería ocurrir con bloqueos de polígono abierto
  const path: Point[] = [to];
  let cur = goalKey;
  while (cur !== startKey) {
    const prev = cameFrom.get(cur)!;
    path.unshift(prev);
    cur = key(prev);
  }
  return path;
}

export function pathLength(path: Point[]): number {
  let d = 0;
  for (let i = 1; i < path.length; i++) d += dist(path[i - 1], path[i]);
  return d;
}

export function pathUsesEdges(path: Point[], fromIdx: number, edges: string[]): boolean {
  for (let i = Math.max(0, fromIdx); i < path.length - 1; i++) {
    if (edges.includes(edgeKey(path[i], path[i + 1]))) return true;
  }
  return false;
}

export function remainingDistance(v: Pick<Vehicle, 'path' | 'pathIdx' | 'pos'>): number {
  if (!v.path) return 0;
  let remain = 0;
  if (v.pathIdx < v.path.length - 1) remain += dist(v.pos, v.path[v.pathIdx + 1]);
  for (let i = v.pathIdx + 1; i < v.path.length - 1; i++) remain += dist(v.path[i], v.path[i + 1]);
  return remain;
}

/** Sectores de 10 × 10 km para la interacción por zonas. */
export function sectorOf(p: Point): string {
  const sx = Math.min(60, Math.floor(p.x / 10) * 10);
  const sy = Math.min(40, Math.floor(p.y / 10) * 10);
  return `Sector (${sx}–${sx + 10}, ${sy}–${sy + 10})`;
}
