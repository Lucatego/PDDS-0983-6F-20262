// Integración con los archivos reales del curso que usa el equipo de algoritmos (si están en el repo).
import { describe, expect, it } from 'vitest';
import { parseBloqueos, parseMantenimiento, parseVentas } from '@/domain/fileFormats';
import { SimulationEngine } from './SimulationEngine';

const files = import.meta.glob('../../../Prototipo/DP1-G6F-Prototipo/algoritmos/alns/data/**/{ventas.202601,bloqueo.2601,mant.preventivo.09.10}.txt', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>;

const find = (name: string) => Object.entries(files).find(([k]) => k.includes(name))?.[1];
const ventas = find('ventas.202601');
const bloqueos = find('bloqueo.2601');
const mant = find('mant.preventivo');

describe.runIf(ventas && bloqueos && mant)('archivos reales del curso', () => {
  it('lee todas las líneas de ventas, bloqueos y mantenimiento', () => {
    expect(parseVentas(ventas!)).toHaveLength(ventas!.trim().split(/\r?\n/).length);
    expect(parseBloqueos(bloqueos!)).toHaveLength(bloqueos!.trim().split(/\r?\n/).length);
    expect(parseMantenimiento(mant!).length).toBeGreaterThan(0);
  });

  it('simula un día completo con los tres archivos cargados', () => {
    const e = new SimulationEngine(() => {});
    e.configure({
      scenario: 'colapso',
      startDate: '2026-09-01',
      startTime: '00:00',
      fleet: { auto: 6, moto: 10, bici: 8 },
      capacities: { noroeste: 1000, este: 1000 },
      shiftStarts: [420, 900, 1380],
    });
    e.loadFile('ventas', ventas!);
    e.loadFile('bloqueos', bloqueos!);
    e.loadFile('mantenimiento', mant!);
    for (let i = 0; i < 1440 / 2.5; i++) e.tick(0.25);
    const s = e.snapshot();
    expect(s.simMin).toBeGreaterThan(1300);
    expect(s.stats.deliveredTotal + s.orders.length).toBeGreaterThan(0);
    expect(s.incidentHistory.some((i) => i.kind === 'bloqueo' && i.origin === 'archivo') || s.incidents.some((i) => i.kind === 'bloqueo')).toBe(true);
  });
});
