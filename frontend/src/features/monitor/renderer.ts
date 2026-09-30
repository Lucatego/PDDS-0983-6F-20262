// Dibujo del mapa en Canvas 2D: retícula, bloqueos, rutas, estelas, almacenes, pedidos y unidades.
// Port de Prototipo/js/ui/render.js con una estética más sobria: marcadores geométricos en vez
// de emojis, etiquetas solo cuando hay zoom o selección, y colores del tema activo.
import { GRID_H, GRID_W, VEHICLE_TYPES } from '@/domain/constants';
import { riskLevel, slackPct, warehouseLevel, type Thresholds } from '@/domain/risk';
import type { FallaIncident, Point, SimSnapshot, Vehicle, VehicleTypeKey } from '@/domain/types';
import { isOnMap } from '@/domain/labels';
import type { Selection } from '@/store/uiStore';
import { alpha, type Palette } from '@/lib/palette';
import type { Camera } from './camera';

export interface RenderOptions {
  thresholds: Thresholds;
  typeFilter: Record<VehicleTypeKey, boolean>;
  showRoutes: boolean;
  showTrails: boolean;
  selected: Selection | null;
  hover: Point | null;
}

const FONT = "'Inter Variable', Inter, system-ui, sans-serif";
const MONO = "'IBM Plex Mono', ui-monospace, monospace";

function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  ctx.beginPath();
  ctx.roundRect(x, y, w, h, r);
}

function vehicleColor(pal: Palette, v: Vehicle): string {
  return pal[VEHICLE_TYPES[v.type].colorVar.slice(2) as keyof Palette];
}

function drawGrid(ctx: CanvasRenderingContext2D, cam: Camera, pal: Palette) {
  const tl = cam.toScreen({ x: 0, y: GRID_H });
  const br = cam.toScreen({ x: GRID_W, y: 0 });
  ctx.fillStyle = pal.land;
  ctx.fillRect(tl.x, tl.y, br.x - tl.x, br.y - tl.y);

  const u = cam.unit;
  // calles locales cada 1 km (se atenúan si están muy juntas)
  ctx.strokeStyle = pal['road-local'];
  ctx.lineWidth = u > 14 ? 1.2 : 0.8;
  ctx.beginPath();
  for (let x = 0; x <= GRID_W; x++) {
    const sx = Math.round(tl.x + x * u) + 0.5;
    ctx.moveTo(sx, tl.y);
    ctx.lineTo(sx, br.y);
  }
  for (let y = 0; y <= GRID_H; y++) {
    const sy = Math.round(br.y - y * u) + 0.5;
    ctx.moveTo(tl.x, sy);
    ctx.lineTo(br.x, sy);
  }
  ctx.stroke();

  // avenidas cada 10 km
  ctx.strokeStyle = pal['road-arterial'];
  ctx.lineWidth = Math.min(4, 1.6 + u * 0.08);
  ctx.beginPath();
  for (let x = 0; x <= GRID_W; x += 10) {
    const sx = tl.x + x * u;
    ctx.moveTo(sx, tl.y);
    ctx.lineTo(sx, br.y);
  }
  for (let y = 0; y <= GRID_H; y += 10) {
    const sy = br.y - y * u;
    ctx.moveTo(tl.x, sy);
    ctx.lineTo(br.x, sy);
  }
  ctx.stroke();

  // ejes en km, fijos al borde visible
  ctx.font = `500 10px ${MONO}`;
  ctx.fillStyle = pal['map-label'];
  ctx.textAlign = 'center';
  ctx.textBaseline = 'top';
  const step = u * 10 < 34 ? 20 : 10;
  for (let x = 0; x <= GRID_W; x += step) {
    const p = cam.toScreen({ x, y: 0 });
    if (p.x > 8 && p.x < cam.W - 8) ctx.fillText(String(x), p.x, Math.min(p.y + 5, cam.H - 14));
  }
  ctx.textAlign = 'right';
  ctx.textBaseline = 'middle';
  for (let y = 0; y <= GRID_H; y += step) {
    const p = cam.toScreen({ x: 0, y });
    if (p.y > 8 && p.y < cam.H - 8) ctx.fillText(String(y), Math.max(p.x - 6, 18), p.y);
  }
}

function drawZone(ctx: CanvasRenderingContext2D, cam: Camera, pal: Palette, sel: Selection | null) {
  if (!sel || sel.type !== 'zone') return;
  const x0 = Math.min(60, Math.floor(sel.center.x / 10) * 10);
  const y0 = Math.min(40, Math.floor(sel.center.y / 10) * 10);
  const a = cam.toScreen({ x: x0, y: y0 + 10 });
  const b = cam.toScreen({ x: x0 + 10, y: y0 });
  ctx.fillStyle = alpha(pal.accent, 0.08);
  ctx.fillRect(a.x, a.y, b.x - a.x, b.y - a.y);
  ctx.strokeStyle = pal.accent;
  ctx.lineWidth = 1.5;
  ctx.setLineDash([5, 4]);
  ctx.strokeRect(a.x, a.y, b.x - a.x, b.y - a.y);
  ctx.setLineDash([]);
}

function strokePath(ctx: CanvasRenderingContext2D, pts: Point[]) {
  ctx.beginPath();
  pts.forEach((p, i) => (i ? ctx.lineTo(p.x, p.y) : ctx.moveTo(p.x, p.y)));
  ctx.stroke();
}

function drawBlockages(ctx: CanvasRenderingContext2D, cam: Camera, pal: Palette, snap: SimSnapshot, sel: Selection | null) {
  for (const inc of snap.incidents) {
    if (inc.kind !== 'bloqueo') continue;
    const pts = inc.nodes.map((n) => cam.toScreen(n));
    const isSel = sel?.type === 'bloqueo' && sel.id === inc.id;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    if (isSel) {
      ctx.strokeStyle = alpha(pal.critical, 0.25);
      ctx.lineWidth = 14;
      strokePath(ctx, pts);
    }
    ctx.strokeStyle = pal.critical;
    ctx.lineWidth = 5;
    strokePath(ctx, pts);
    ctx.strokeStyle = pal.surface;
    ctx.lineWidth = 1.5;
    ctx.setLineDash([3, 4]);
    strokePath(ctx, pts);
    ctx.setLineDash([]);
    ctx.fillStyle = pal.critical;
    for (const p of [pts[0], pts[pts.length - 1]]) {
      ctx.beginPath();
      ctx.arc(p.x, p.y, 3.5, 0, Math.PI * 2);
      ctx.fill();
    }
    if (cam.scale >= 1.8 || isSel) {
      const mid = pts[Math.floor(pts.length / 2)];
      ctx.font = `600 10px ${FONT}`;
      const w = ctx.measureText('Bloqueo').width + 12;
      ctx.fillStyle = pal.critical;
      roundRect(ctx, mid.x - w / 2, mid.y - 24, w, 17, 5);
      ctx.fill();
      ctx.fillStyle = '#fff';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText('Bloqueo', mid.x, mid.y - 15.5);
    }
  }
}

function drawRoutes(ctx: CanvasRenderingContext2D, cam: Camera, pal: Palette, snap: SimSnapshot, opt: RenderOptions) {
  for (const v of snap.vehicles) {
    if (!v.path || v.pathIdx >= v.path.length - 1 || !opt.typeFilter[v.type]) continue;
    if (v.state !== 'toClient' && v.state !== 'returning') continue;
    const isSel = opt.selected?.type === 'vehicle' && opt.selected.id === v.id;
    const pts = [cam.toScreen(v.pos), ...v.path.slice(v.pathIdx + 1).map((p) => cam.toScreen(p))];
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.strokeStyle = pal.surface;
    ctx.lineWidth = isSel ? 6 : 4;
    ctx.globalAlpha = 0.9;
    strokePath(ctx, pts);
    ctx.globalAlpha = v.state === 'returning' && !isSel ? 0.55 : 1;
    ctx.strokeStyle = vehicleColor(pal, v);
    ctx.lineWidth = isSel ? 3 : 2;
    ctx.setLineDash(v.state === 'returning' ? [2, 5] : [7, 5]);
    strokePath(ctx, pts);
    ctx.setLineDash([]);
    ctx.globalAlpha = 1;
    // destino
    const end = pts[pts.length - 1];
    ctx.fillStyle = pal.surface;
    ctx.strokeStyle = vehicleColor(pal, v);
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.arc(end.x, end.y, 3.5, 0, Math.PI * 2);
    ctx.fill();
    ctx.stroke();
  }
}

function drawTrails(ctx: CanvasRenderingContext2D, cam: Camera, pal: Palette, snap: SimSnapshot, opt: RenderOptions) {
  ctx.lineCap = 'round';
  for (const v of snap.vehicles) {
    if (v.trail.length < 2 || !opt.typeFilter[v.type]) continue;
    const col = vehicleColor(pal, v);
    for (let i = 1; i < v.trail.length; i++) {
      const a = cam.toScreen(v.trail[i - 1]);
      const b = cam.toScreen(v.trail[i]);
      ctx.globalAlpha = 0.05 + 0.3 * (i / v.trail.length);
      ctx.strokeStyle = col;
      ctx.lineWidth = 3;
      ctx.beginPath();
      ctx.moveTo(a.x, a.y);
      ctx.lineTo(b.x, b.y);
      ctx.stroke();
    }
  }
  ctx.globalAlpha = 1;
}

function drawWarehouses(ctx: CanvasRenderingContext2D, cam: Camera, pal: Palette, snap: SimSnapshot, opt: RenderOptions) {
  for (const w of snap.warehouses) {
    const p = cam.toScreen(w.pos);
    const level = warehouseLevel(w, opt.thresholds);
    const levelColor = pal[level];
    const s = w.infinite ? 22 : 19;
    const isSel = opt.selected?.type === 'warehouse' && opt.selected.id === w.id;

    // halo
    ctx.fillStyle = alpha(pal.accent, 0.1);
    ctx.beginPath();
    ctx.arc(p.x, p.y, s * 1.15, 0, Math.PI * 2);
    ctx.fill();
    if (isSel) {
      ctx.strokeStyle = pal.accent;
      ctx.lineWidth = 2;
      ctx.setLineDash([4, 3]);
      ctx.beginPath();
      ctx.arc(p.x, p.y, s * 1.35, 0, Math.PI * 2);
      ctx.stroke();
      ctx.setLineDash([]);
    }
    // cuerpo
    ctx.save();
    ctx.shadowColor = 'rgba(16,24,40,.25)';
    ctx.shadowBlur = 8;
    ctx.shadowOffsetY = 2;
    ctx.fillStyle = w.infinite ? pal.accent : pal.surface;
    roundRect(ctx, p.x - s / 2, p.y - s / 2, s, s, 6);
    ctx.fill();
    ctx.restore();
    ctx.strokeStyle = w.infinite ? pal.surface : pal.accent;
    ctx.lineWidth = 2;
    roundRect(ctx, p.x - s / 2, p.y - s / 2, s, s, 6);
    ctx.stroke();
    // techo y puerta (glifo de almacén)
    const g = s * 0.26;
    ctx.strokeStyle = w.infinite ? '#fff' : pal.accent;
    ctx.lineWidth = 1.6;
    ctx.beginPath();
    ctx.moveTo(p.x - g * 1.3, p.y - g * 0.2);
    ctx.lineTo(p.x, p.y - g * 1.2);
    ctx.lineTo(p.x + g * 1.3, p.y - g * 0.2);
    ctx.moveTo(p.x - g * 0.9, p.y - g * 0.4);
    ctx.lineTo(p.x - g * 0.9, p.y + g * 1.1);
    ctx.lineTo(p.x + g * 0.9, p.y + g * 1.1);
    ctx.lineTo(p.x + g * 0.9, p.y - g * 0.4);
    ctx.stroke();

    // rótulo: nombre + stock con semáforo
    const stock = w.infinite ? '∞' : `${w.stock}/${w.capacity}`;
    ctx.font = `600 11px ${FONT}`;
    const nameW = ctx.measureText(w.shortName).width;
    ctx.font = `600 10px ${MONO}`;
    const stockW = ctx.measureText(stock).width;
    const totalW = nameW + stockW + 30;
    const lx = p.x - totalW / 2;
    const ly = p.y - s / 2 - 26;
    ctx.fillStyle = pal.surface;
    ctx.strokeStyle = pal.line;
    ctx.lineWidth = 1;
    roundRect(ctx, lx, ly, totalW, 20, 6);
    ctx.fill();
    ctx.stroke();
    ctx.fillStyle = levelColor;
    ctx.beginPath();
    ctx.arc(lx + 10, ly + 10, 3.5, 0, Math.PI * 2);
    ctx.fill();
    ctx.textAlign = 'left';
    ctx.textBaseline = 'middle';
    ctx.font = `600 11px ${FONT}`;
    ctx.fillStyle = pal.ink;
    ctx.fillText(w.shortName, lx + 18, ly + 10.5);
    ctx.font = `600 10px ${MONO}`;
    ctx.fillStyle = pal['ink-2'];
    ctx.fillText(stock, lx + 24 + nameW, ly + 10.5);
  }
}

function drawOrders(ctx: CanvasRenderingContext2D, cam: Camera, pal: Palette, snap: SimSnapshot, opt: RenderOptions) {
  const r0 = Math.min(6, 2.6 + cam.scale * 0.5);
  for (const o of snap.orders) {
    const p = cam.toScreen(o.pos);
    const col = pal[riskLevel(slackPct(o, snap.simMin), opt.thresholds)];
    const r = r0 + Math.min(o.qty, 24) * 0.09;
    ctx.save();
    ctx.translate(p.x, p.y);
    ctx.rotate(Math.PI / 4);
    ctx.fillStyle = o.status === 'pending' ? pal.surface : alpha(col, 0.22);
    ctx.strokeStyle = col;
    ctx.lineWidth = 1.8;
    if (o.status === 'pending') ctx.setLineDash([2.5, 2]);
    ctx.fillRect(-r, -r, r * 2, r * 2);
    ctx.strokeRect(-r, -r, r * 2, r * 2);
    ctx.restore();
    if (o.priority < 36) {
      ctx.fillStyle = pal.critical;
      ctx.beginPath();
      ctx.arc(p.x + r + 2, p.y - r - 2, 2.2, 0, Math.PI * 2);
      ctx.fill();
    }
  }
}

function drawFlashes(ctx: CanvasRenderingContext2D, cam: Camera, pal: Palette, snap: SimSnapshot) {
  for (const f of snap.flashes) {
    const age = (snap.simMin - f.born) / 22;
    if (age < 0 || age > 1) continue;
    const p = cam.toScreen(f.pos);
    ctx.globalAlpha = 1 - age;
    ctx.strokeStyle = f.onTime ? pal.good : pal.critical;
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.arc(p.x, p.y, 5 + age * 18, 0, Math.PI * 2);
    ctx.stroke();
  }
  ctx.globalAlpha = 1;
}

function drawVehicles(ctx: CanvasRenderingContext2D, cam: Camera, pal: Palette, snap: SimSnapshot, opt: RenderOptions) {
  const showLabels = cam.scale >= 2.2;
  const faults = new Map<string, FallaIncident>();
  snap.incidents.forEach((i) => i.kind === 'falla' && faults.set(i.vehicleId, i));

  for (const v of snap.vehicles) {
    if (!isOnMap(v) || !opt.typeFilter[v.type]) continue;
    const p = cam.toScreen(v.pos);
    const col = vehicleColor(pal, v);
    const isSel = opt.selected?.type === 'vehicle' && opt.selected.id === v.id;
    const R = 7.5;

    if (isSel) {
      ctx.fillStyle = alpha(col, 0.18);
      ctx.beginPath();
      ctx.arc(p.x, p.y, 17, 0, Math.PI * 2);
      ctx.fill();
    }
    if (v.state === 'broken' || v.state === 'maintenance') {
      ctx.strokeStyle = v.state === 'broken' ? pal[(['warning', 'serious', 'critical'] as const)[(faults.get(v.id)?.tipo ?? 3) - 1]] : pal['ink-3'];
      ctx.lineWidth = 2;
      ctx.setLineDash([3, 3]);
      ctx.beginPath();
      ctx.arc(p.x, p.y, 13, 0, Math.PI * 2);
      ctx.stroke();
      ctx.setLineDash([]);
    }
    if (v.state === 'atClient') {
      ctx.strokeStyle = alpha(col, 0.5);
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.arc(p.x, p.y, 12, 0, Math.PI * 2);
      ctx.stroke();
    }

    // cuerpo del marcador
    ctx.save();
    ctx.shadowColor = 'rgba(16,24,40,.3)';
    ctx.shadowBlur = 4;
    ctx.shadowOffsetY = 1;
    ctx.fillStyle = v.state === 'maintenance' ? pal['ink-3'] : col;
    ctx.beginPath();
    ctx.arc(p.x, p.y, R, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
    ctx.strokeStyle = pal.surface;
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.arc(p.x, p.y, R, 0, Math.PI * 2);
    ctx.stroke();

    // flecha de dirección o glifo de estado
    ctx.fillStyle = '#fff';
    if ((v.state === 'toClient' || v.state === 'returning') && v.path) {
      const ang = -v.heading; // mundo (Y arriba) -> pantalla (Y abajo)
      ctx.save();
      ctx.translate(p.x, p.y);
      ctx.rotate(ang);
      ctx.beginPath();
      ctx.moveTo(4, 0);
      ctx.lineTo(-2.5, -3.2);
      ctx.lineTo(-1, 0);
      ctx.lineTo(-2.5, 3.2);
      ctx.closePath();
      ctx.fill();
      ctx.restore();
    } else if (v.state === 'broken') {
      ctx.font = `700 10px ${FONT}`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText('!', p.x, p.y + 0.5);
    } else {
      ctx.beginPath();
      ctx.arc(p.x, p.y, 2.2, 0, Math.PI * 2);
      ctx.fill();
    }

    if (showLabels || isSel) {
      const order = v.orderId ? snap.orders.find((o) => o.id === v.orderId) : null;
      const text = order ? `${v.id} · #${order.id} · ${order.qty}/${v.capacity}` : v.id;
      ctx.font = `600 10px ${MONO}`;
      const w = ctx.measureText(text).width + 10;
      const lx = p.x + 11;
      const ly = p.y - 8;
      ctx.fillStyle = pal.surface;
      ctx.strokeStyle = isSel ? col : pal.line;
      ctx.lineWidth = 1;
      roundRect(ctx, lx, ly, w, 16, 5);
      ctx.fill();
      ctx.stroke();
      ctx.fillStyle = pal.ink;
      ctx.textAlign = 'left';
      ctx.textBaseline = 'middle';
      ctx.fillText(text, lx + 5, ly + 8.5);
    }
  }
}

export function drawScene(ctx: CanvasRenderingContext2D, cam: Camera, pal: Palette, snap: SimSnapshot | null, opt: RenderOptions): void {
  ctx.clearRect(0, 0, cam.W, cam.H);
  drawGrid(ctx, cam, pal);
  if (!snap) return;
  drawZone(ctx, cam, pal, opt.selected);
  drawBlockages(ctx, cam, pal, snap, opt.selected);
  if (opt.showRoutes) drawRoutes(ctx, cam, pal, snap, opt);
  if (opt.showTrails) drawTrails(ctx, cam, pal, snap, opt);
  drawOrders(ctx, cam, pal, snap, opt);
  drawWarehouses(ctx, cam, pal, snap, opt);
  drawFlashes(ctx, cam, pal, snap);
  drawVehicles(ctx, cam, pal, snap, opt);
}

/* ------------------------------ selección por clic ------------------------------ */

function distToSegment(p: Point, a: Point, b: Point): number {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const len2 = dx * dx + dy * dy;
  const t = len2 ? Math.max(0, Math.min(1, ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2)) : 0;
  return Math.hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy));
}

export function hitTest(cam: Camera, snap: SimSnapshot, sx: number, sy: number, typeFilter: Record<VehicleTypeKey, boolean>): Selection | null {
  const s = { x: sx, y: sy };
  let best: { d: number; sel: Selection } | null = null;
  for (const v of snap.vehicles) {
    if (!isOnMap(v) || !typeFilter[v.type]) continue;
    const d = Math.hypot(cam.toScreen(v.pos).x - sx, cam.toScreen(v.pos).y - sy);
    if (d < 14 && (!best || d < best.d)) best = { d, sel: { type: 'vehicle', id: v.id } };
  }
  if (best) return best.sel;
  for (const w of snap.warehouses) {
    const p = cam.toScreen(w.pos);
    if (Math.hypot(p.x - sx, p.y - sy) < 16) return { type: 'warehouse', id: w.id };
  }
  for (const inc of snap.incidents) {
    if (inc.kind !== 'bloqueo') continue;
    for (let i = 0; i < inc.nodes.length - 1; i++) {
      if (distToSegment(s, cam.toScreen(inc.nodes[i]), cam.toScreen(inc.nodes[i + 1])) < 8) return { type: 'bloqueo', id: inc.id };
    }
  }
  const world = cam.toWorld(sx, sy);
  if (world.x >= 0 && world.x <= GRID_W && world.y >= 0 && world.y <= GRID_H) {
    const cx = Math.min(60, Math.floor(world.x / 10) * 10) + 5;
    const cy = Math.min(40, Math.floor(world.y / 10) * 10) + 5;
    return { type: 'zone', name: `Sector (${cx - 5}–${cx + 5}, ${cy - 5}–${cy + 5})`, center: { x: cx, y: cy } };
  }
  return null;
}

/** Cursor de mano cuando hay algo seleccionable bajo el puntero. */
export function isInteractiveAt(cam: Camera, snap: SimSnapshot, sx: number, sy: number, typeFilter: Record<VehicleTypeKey, boolean>): boolean {
  const sel = hitTest(cam, snap, sx, sy, typeFilter);
  return !!sel && sel.type !== 'zone';
}
