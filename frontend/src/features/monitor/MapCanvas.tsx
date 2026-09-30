// Lienzo del mapa: bucle de dibujo propio (requestAnimationFrame) que lee la instantánea viva
// sin pasar por React, más la interacción: rueda, arrastre, pellizco, doble clic y clic para seleccionar.
import { useEffect, useRef } from 'react';
import { readPalette, type Palette } from '@/lib/palette';
import { getLiveSnapshot } from '@/store/liveSnapshot';
import { useUiStore } from '@/store/uiStore';
import type { Camera } from './camera';
import { drawScene, hitTest, isInteractiveAt } from './renderer';

export function MapCanvas({ camera, onScale }: { camera: Camera; onScale: (unitPx: number) => void }) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);

  // pedidos de centrado desde búsquedas, tablas y tarjetas
  const focus = useUiStore((s) => s.focus);
  useEffect(() => {
    if (focus) camera.centerOn(focus.pos, focus.scale);
  }, [focus, camera]);

  useEffect(() => {
    const canvas = canvasRef.current!;
    const wrap = wrapRef.current!;
    const ctx = canvas.getContext('2d')!;
    let dpr = window.devicePixelRatio || 1;
    let palette: Palette = readPalette();
    let raf = 0;
    let lastUnit = -1;

    const resize = () => {
      const r = wrap.getBoundingClientRect();
      dpr = window.devicePixelRatio || 1;
      canvas.width = Math.max(1, Math.round(r.width * dpr));
      canvas.height = Math.max(1, Math.round(r.height * dpr));
      canvas.style.width = `${r.width}px`;
      canvas.style.height = `${r.height}px`;
      camera.resize(r.width, r.height);
    };
    const ro = new ResizeObserver(resize);
    ro.observe(wrap);
    resize();

    // el tema cambia por atributo en <html> o por el sistema: se vuelve a leer la paleta
    const refreshPalette = () => (palette = readPalette());
    const mo = new MutationObserver(refreshPalette);
    mo.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    mq.addEventListener('change', refreshPalette);

    const frame = () => {
      const ui = useUiStore.getState();
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      drawScene(ctx, camera, palette, getLiveSnapshot(), {
        thresholds: ui.thresholds,
        typeFilter: ui.typeFilter,
        showRoutes: ui.showRoutes,
        showTrails: ui.showTrails,
        selected: ui.selected,
        hover: null,
      });
      if (camera.unit !== lastUnit) {
        lastUnit = camera.unit;
        onScale(camera.unit);
      }
      raf = requestAnimationFrame(frame);
    };
    raf = requestAnimationFrame(frame);

    /* ---- interacción ---- */
    const pointers = new Map<number, { x: number; y: number }>();
    let moved = false;
    let pinchDist = 0;
    const local = (e: { clientX: number; clientY: number }) => {
      const r = canvas.getBoundingClientRect();
      return { x: e.clientX - r.left, y: e.clientY - r.top };
    };

    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      const p = local(e);
      camera.zoomAt(p.x, p.y, Math.exp(-e.deltaY * 0.0015));
    };
    const onDown = (e: PointerEvent) => {
      canvas.setPointerCapture(e.pointerId);
      pointers.set(e.pointerId, local(e));
      moved = false;
      if (pointers.size === 2) {
        const [a, b] = [...pointers.values()];
        pinchDist = Math.hypot(a.x - b.x, a.y - b.y);
      }
    };
    const onMove = (e: PointerEvent) => {
      const p = local(e);
      const prev = pointers.get(e.pointerId);
      if (!prev) {
        const snap = getLiveSnapshot();
        canvas.style.cursor = snap && isInteractiveAt(camera, snap, p.x, p.y, useUiStore.getState().typeFilter) ? 'pointer' : 'grab';
        return;
      }
      if (pointers.size === 2) {
        pointers.set(e.pointerId, p);
        const [a, b] = [...pointers.values()];
        const d = Math.hypot(a.x - b.x, a.y - b.y);
        if (pinchDist > 0) camera.zoomAt((a.x + b.x) / 2, (a.y + b.y) / 2, d / pinchDist);
        pinchDist = d;
        moved = true;
        return;
      }
      const dx = p.x - prev.x;
      const dy = p.y - prev.y;
      if (Math.abs(dx) + Math.abs(dy) > 0) {
        if (Math.hypot(dx, dy) > 2) moved = true;
        camera.panBy(dx, dy);
        canvas.style.cursor = 'grabbing';
      }
      pointers.set(e.pointerId, p);
    };
    const onUp = (e: PointerEvent) => {
      const p = local(e);
      const wasSingle = pointers.size === 1;
      pointers.delete(e.pointerId);
      if (pointers.size < 2) pinchDist = 0;
      canvas.style.cursor = 'grab';
      if (wasSingle && !moved) {
        const snap = getLiveSnapshot();
        const ui = useUiStore.getState();
        ui.select(snap ? hitTest(camera, snap, p.x, p.y, ui.typeFilter) : null);
      }
    };
    const onDbl = (e: MouseEvent) => {
      const p = local(e);
      camera.zoomAt(p.x, p.y, 1.8);
    };
    const onKey = (e: KeyboardEvent) => {
      const step = 40;
      if (e.key === '+' || e.key === '=') camera.zoomAt(camera.W / 2, camera.H / 2, 1.3);
      else if (e.key === '-') camera.zoomAt(camera.W / 2, camera.H / 2, 1 / 1.3);
      else if (e.key === 'ArrowLeft') camera.panBy(step, 0);
      else if (e.key === 'ArrowRight') camera.panBy(-step, 0);
      else if (e.key === 'ArrowUp') camera.panBy(0, step);
      else if (e.key === 'ArrowDown') camera.panBy(0, -step);
      else if (e.key === '0') camera.reset();
      else if (e.key === 'Escape') useUiStore.getState().select(null);
      else return;
      e.preventDefault();
    };

    canvas.addEventListener('wheel', onWheel, { passive: false });
    canvas.addEventListener('pointerdown', onDown);
    canvas.addEventListener('pointermove', onMove);
    canvas.addEventListener('pointerup', onUp);
    canvas.addEventListener('pointercancel', onUp);
    canvas.addEventListener('dblclick', onDbl);
    canvas.addEventListener('keydown', onKey);

    return () => {
      cancelAnimationFrame(raf);
      ro.disconnect();
      mo.disconnect();
      mq.removeEventListener('change', refreshPalette);
      canvas.removeEventListener('wheel', onWheel);
      canvas.removeEventListener('pointerdown', onDown);
      canvas.removeEventListener('pointermove', onMove);
      canvas.removeEventListener('pointerup', onUp);
      canvas.removeEventListener('pointercancel', onUp);
      canvas.removeEventListener('dblclick', onDbl);
      canvas.removeEventListener('keydown', onKey);
    };
  }, [camera, onScale]);

  return (
    <div ref={wrapRef} className="absolute inset-0">
      <canvas
        ref={canvasRef}
        tabIndex={0}
        role="application"
        aria-label="Mapa de la retícula urbana de 70 por 50 kilómetros. Arrastra para desplazar, rueda o teclas más y menos para acercar, flechas para mover, cero para restablecer, clic para ver el detalle."
        className="block touch-none outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-inset"
        style={{ cursor: 'grab' }}
      />
    </div>
  );
}
