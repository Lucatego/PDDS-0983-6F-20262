import { describe, expect, it } from 'vitest';
import { normVehId, parseAverias, parseBloqueos, parseDDHHMM, parseLote, parseMantenimiento, parseVentas, validateOrderInput } from './fileFormats';
import { computeRoute, edgeKey, pathLength } from './grid';
import { riskLevel, slackPct } from './risk';
import { buildShifts, currentShift, fmtElapsedSim, fmtTime, inMeal } from './time';

describe('archivos del curso', () => {
  it('convierte ##d##h##m a minutos desde el día 1', () => {
    expect(parseDDHHMM('01d00h00m')).toBe(0);
    expect(parseDDHHMM('02d06h30m')).toBe(1440 + 390);
    expect(parseDDHHMM('x')).toBeNull();
  });

  it('lee ventas, ordena por llegada y descarta líneas mal formadas', () => {
    const recs = parseVentas('11d13h31m:45,43,c9167,12,36\nbasura\n01d08h00m:1,2,c1,3,4\n');
    expect(recs).toHaveLength(2);
    expect(recs[0]).toMatchObject({ clientId: 'c1', qty: 3, hourLimit: 4, pos: { x: 1, y: 2 } });
  });

  it('lee bloqueos como polígono abierto', () => {
    const [b] = parseBloqueos('01d06h00m-01d15h00m:31,21,34,21,34,25');
    expect(b.startMin).toBe(360);
    expect(b.endMin).toBe(900);
    expect(b.nodes).toEqual([
      { x: 31, y: 21 },
      { x: 34, y: 21 },
      { x: 34, y: 25 },
    ]);
  });

  it('normaliza códigos de unidad y lee averías y mantenimiento', () => {
    expect(normVehId('tb3')).toBe('TB03');
    expect(parseAverias('01d09h30m:ta1,2')[0]).toMatchObject({ vehicleId: 'TA01', tipo: 2, atMin: 570 });
    expect(parseMantenimiento('plan\n20260908:TB03\n20261301:TA01')).toEqual([{ y: 2026, mo: 9, d: 8, dateStr: '2026-09-08', vehicleId: 'TB03' }]);
  });

  it('valida el alta de pedidos y marca en riesgo los plazos inalcanzables', () => {
    expect(validateOrderInput({ clientId: 'c1', qty: 30, hourLimit: 36, x: 1, y: 1 }).ok).toBe(false);
    expect(validateOrderInput({ clientId: 'c1', qty: 5, hourLimit: 36, x: 71, y: 1 }).ok).toBe(false);
    expect(validateOrderInput({ clientId: 'c1', qty: 5, hourLimit: 7, x: 1, y: 1 }).ok).toBe(false);
    expect(validateOrderInput({ clientId: 'c1', qty: 5, hourLimit: 4, x: 30, y: 15 })).toEqual({ ok: true, enRiesgo: false });
    expect(validateOrderInput({ clientId: 'c1', qty: 5, hourLimit: 4, x: 70, y: 50 }).ok).toBe(true);
  });

  it('valida la carga por lote línea a línea', () => {
    const l = parseLote('c1,10,36,20,20\nc2,5,8\n\nc3,50,36,1,1');
    expect(l.map((x) => !!x.input)).toEqual([true, false, false]);
    expect(l[1].error).toMatch(/formato/);
  });
});

describe('retícula', () => {
  it('encuentra el camino más corto y rodea un tramo bloqueado', () => {
    const libre = computeRoute({ x: 0, y: 0 }, { x: 3, y: 0 }, new Set());
    expect(pathLength(libre)).toBe(3);
    const bloqueado = new Set([edgeKey({ x: 1, y: 0 }, { x: 2, y: 0 })]);
    const desvio = computeRoute({ x: 0, y: 0 }, { x: 3, y: 0 }, bloqueado);
    expect(pathLength(desvio)).toBe(5);
    expect(edgeKey({ x: 2, y: 0 }, { x: 1, y: 0 })).toBe(edgeKey({ x: 1, y: 0 }, { x: 2, y: 0 }));
  });
});

describe('tiempo y semáforo', () => {
  it('formatea horas y tiempo transcurrido', () => {
    expect(fmtTime(1440 + 75)).toBe('01:15');
    expect(fmtElapsedSim(1440 + 61.5)).toBe('01d 01:01:30');
  });

  it('ubica el turno y el refrigerio', () => {
    const shifts = buildShifts([420, 900, 1380]);
    expect(currentShift(8 * 60, shifts).index).toBe(1);
    expect(currentShift(2 * 60, shifts).index).toBe(3);
    expect(inMeal(11 * 60 + 30, currentShift(11 * 60 + 30, shifts))).toBe(true);
  });

  it('clasifica el plazo restante', () => {
    const t = { green: 70, amber: 35 };
    expect(riskLevel(80, t)).toBe('good');
    expect(riskLevel(50, t)).toBe('warning');
    expect(riskLevel(10, t)).toBe('critical');
    expect(slackPct({ createdAt: 0, deadline: 100 }, 25)).toBe(75);
  });
});
