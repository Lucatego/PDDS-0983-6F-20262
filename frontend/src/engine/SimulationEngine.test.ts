import { describe, expect, it } from 'vitest';
import type { LogEvent, RunConfig } from '@/domain/types';
import { SimulationEngine, expandChain } from './SimulationEngine';

// generador determinista para que las pruebas sean reproducibles
function seeded(seed = 42) {
  let s = seed;
  return () => {
    s = (s * 1664525 + 1013904223) % 4294967296;
    return s / 4294967296;
  };
}

const cfg = (over: Partial<RunConfig> = {}): RunConfig => ({
  scenario: '5d',
  startDate: '2026-09-01',
  startTime: '07:00',
  fleet: { auto: 2, moto: 2, bici: 2 },
  capacities: { noroeste: 1000, este: 1000 },
  shiftStarts: [420, 900, 1380],
  ...over,
});

function run(engine: SimulationEngine, simMinutes: number) {
  // pasos de 0,25 s reales; los minutos simulados por paso dependen del escenario y la velocidad
  const stepMin = 0.25 * (engine.snapshot().simMinPerSec ?? 10);
  for (let i = 0; i < simMinutes / stepMin; i++) engine.tick(0.25);
}

describe('SimulationEngine', () => {
  it('arranca al configurar 5D y construye la flota con códigos TTNN', () => {
    const e = new SimulationEngine(() => {}, seeded());
    e.configure(cfg());
    const s = e.snapshot();
    expect(s.running).toBe(true);
    expect(s.vehicles.map((v) => v.id)).toEqual(['TA01', 'TA02', 'TM01', 'TM02', 'TB01', 'TB02']);
    expect(s.simMin).toBe(420);
  });

  it('en día a día espera el primer pedido y luego lo entrega', () => {
    const logs: LogEvent[] = [];
    const e = new SimulationEngine((l) => logs.push(l), seeded());
    e.configure(cfg({ scenario: 'diaria' }));
    expect(e.snapshot().waitingFirstOrder).toBe(true);
    expect(() => e.start()).toThrow();
    const r = e.registerOrder({ clientId: 'c1', qty: 3, hourLimit: 36, x: 30, y: 14 });
    expect(r.ok).toBe(true);
    expect(e.snapshot().running).toBe(true);
    run(e, 240);
    const s = e.snapshot();
    expect(s.stats.deliveredTotal).toBe(1);
    expect(s.orderHistory[0].estadoFinal).toBe('entregado');
    expect(s.stats.cost).toBeGreaterThan(0);
  });

  it('rechaza pedidos inválidos sin registrarlos', () => {
    const e = new SimulationEngine(() => {}, seeded());
    e.configure(cfg({ scenario: 'diaria' }));
    expect(e.registerOrder({ clientId: 'c1', qty: 99, hourLimit: 36, x: 1, y: 1 })).toMatchObject({ ok: false });
    expect(e.snapshot().orders).toHaveLength(0);
  });

  it('una avería devuelve el pedido a la cola como reprogramado', () => {
    const e = new SimulationEngine(() => {}, seeded());
    e.configure(cfg({ scenario: 'diaria' }));
    e.registerOrder({ clientId: 'c1', qty: 20, hourLimit: 36, x: 60, y: 45 });
    run(e, 5);
    const v = e.snapshot().vehicles.find((x) => x.state === 'toClient')!;
    e.registerAveria(v.id, 3);
    const s = e.snapshot();
    expect(s.vehicles.find((x) => x.id === v.id)!.state).toBe('broken');
    expect(s.orders[0]).toMatchObject({ status: 'pending', reprogramado: true });
  });

  it('carga ventas por archivo y las libera a su hora', () => {
    const e = new SimulationEngine(() => {}, seeded());
    e.configure(cfg());
    const res = e.loadFile('ventas', '01d06h00m:10,10,c1,2,36\n01d08h00m:20,20,c2,2,36');
    expect(res).toMatchObject({ count: 2, immediate: 1 });
    run(e, 70);
    expect(e.snapshot().orders.length + e.snapshot().orderHistory.length).toBe(2);
  });

  it('el bloqueo por archivo bloquea cada kilómetro del tramo', () => {
    expect(expandChain([{ x: 31, y: 21 }, { x: 34, y: 21 }])).toHaveLength(4);
    const e = new SimulationEngine(() => {}, seeded());
    e.configure(cfg());
    e.loadFile('bloqueos', '01d06h00m-01d15h00m:31,21,34,21');
    const b = e.snapshot().incidents.find((i) => i.kind === 'bloqueo' && i.origin === 'archivo');
    expect(b && b.kind === 'bloqueo' ? b.edges : []).toHaveLength(3);
  });

  it('5D se detiene al completar los cinco días', () => {
    const e = new SimulationEngine(() => {}, seeded());
    e.configure(cfg({ fleet: { auto: 6, moto: 10, bici: 8 } }));
    run(e, 5 * 1440 + 10);
    const s = e.snapshot();
    expect(s.finished).toBe(true);
    expect(s.running).toBe(false);
  });
  it('en 5D la velocidad base es 3 min simulados por segundo (5 días ≈ 40 min reales)', () => {
    const e = new SimulationEngine(() => {}, seeded());
    e.configure(cfg());
    const s = e.snapshot();
    expect(s.speedFactor).toBe(1);
    expect(s.simMinPerSec).toBe(3);
    const t0 = e.snapshot().simMin;
    for (let i = 0; i < 40; i++) e.tick(0.25); // 10 s reales
    expect(e.snapshot().simMin - t0).toBeCloseTo(30, 5);
  });

  it('setSpeed cambia la velocidad en caliente en 5D y Colapso', () => {
    for (const scenario of ['5d', 'colapso'] as const) {
      const e = new SimulationEngine(() => {}, seeded());
      e.configure(cfg({ scenario }));
      for (const f of [1, 2, 5, 10]) {
        e.setSpeed(f);
        expect(e.snapshot().speedFactor).toBe(f);
        expect(e.snapshot().simMinPerSec).toBe(3 * f);
      }
    }
  });

  it('setSpeed rechaza factores inválidos, Día a día y el estado sin configurar', () => {
    const e = new SimulationEngine(() => {}, seeded());
    expect(() => e.setSpeed(2)).toThrow(/configurada/);
    e.configure(cfg());
    expect(() => e.setSpeed(3)).toThrow(/no válida/);
    e.configure(cfg({ scenario: 'diaria' }));
    expect(() => e.setSpeed(2)).toThrow(/tiempo real/);
  });

  it('configurar o reiniciar vuelve la velocidad a ×1', () => {
    const e = new SimulationEngine(() => {}, seeded());
    e.configure(cfg());
    e.setSpeed(10);
    e.configure(cfg());
    expect(e.snapshot().speedFactor).toBe(1);
    e.setSpeed(5);
    e.reset();
    e.configure(cfg());
    expect(e.snapshot().speedFactor).toBe(1);
  });
});
