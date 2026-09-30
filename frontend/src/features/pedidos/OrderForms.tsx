// Alta individual (panel lateral con confirmación) y carga por lote de pedidos.
import { useMemo, useState } from 'react';
import { useCatalogos, useRegisterOrder, useRegisterOrderBatch } from '@/api/hooks';
import { Dialog, Drawer } from '@/components/ui/Dialog';
import { Icon } from '@/components/ui/Icon';
import { GRID_H, GRID_W, MODALIDADES } from '@/domain/constants';
import { parseLote, validateOrderInput } from '@/domain/fileFormats';
import type { OrderInput } from '@/domain/types';
import { useUiStore } from '@/store/uiStore';

const EMPTY = { clientId: '', qty: '', hourLimit: '36', x: '', y: '' };

export function NewOrderDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const catalogos = useCatalogos();
  const modalidades = catalogos.data?.modalidades ?? MODALIDADES;
  const [form, setForm] = useState(EMPTY);
  const [step, setStep] = useState<'form' | 'confirm'>('form');
  const [touched, setTouched] = useState(false);
  const register = useRegisterOrder();
  const toast = useUiStore((s) => s.toast);

  const input: OrderInput = {
    clientId: form.clientId.trim(),
    qty: Number(form.qty),
    hourLimit: Number(form.hourLimit),
    x: form.x === '' ? Number.NaN : Number(form.x),
    y: form.y === '' ? Number.NaN : Number(form.y),
  };
  const validation = validateOrderInput(input);
  const errors = {
    clientId: !input.clientId ? 'Indica el código del cliente.' : null,
    qty: !Number.isInteger(input.qty) || input.qty < 1 || input.qty > 24 ? 'Entre 1 y 24 paquetes.' : null,
    x: !Number.isInteger(input.x) || input.x < 0 || input.x > GRID_W ? `Entre 0 y ${GRID_W}.` : null,
    y: !Number.isInteger(input.y) || input.y < 0 || input.y > GRID_H ? `Entre 0 y ${GRID_H}.` : null,
  };

  const close = () => {
    setForm(EMPTY);
    setStep('form');
    setTouched(false);
    register.reset();
    onClose();
  };

  const submit = () => {
    register.mutate(input, {
      onSuccess: (res) => {
        if (!res.ok) return;
        toast(`Pedido #${res.id} registrado`, res.enRiesgo ? 'warning' : 'good', res.enRiesgo ? 'Marcado en riesgo: el plazo elegido podría no alcanzarse desde el almacén central.' : `${input.clientId} · ${input.qty} paq. · ${input.hourLimit} h`);
        setForm({ ...EMPTY, hourLimit: form.hourLimit });
        setStep('form');
        setTouched(false);
      },
    });
  };

  const serverError = register.data && !register.data.ok ? register.data.motivo : register.error instanceof Error ? register.error.message : null;
  const field = (k: keyof typeof errors) => (touched && errors[k] ? { 'aria-invalid': true as const } : {});

  return (
    <Drawer
      open={open}
      onClose={close}
      title={step === 'form' ? 'Nuevo pedido' : 'Confirmar pedido'}
      description={step === 'form' ? 'Los datos se validan igual que en el planificador. El plazo se cuenta desde el registro.' : 'Revisa los datos antes de registrarlo.'}
      footer={
        step === 'form' ? (
          <>
            <button type="button" className="btn btn-secondary" onClick={close}>
              Cancelar
            </button>
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => {
                setTouched(true);
                if (validation.ok) setStep('confirm');
              }}
            >
              Continuar
            </button>
          </>
        ) : (
          <>
            <button type="button" className="btn btn-secondary" onClick={() => setStep('form')}>
              Volver
            </button>
            <button type="button" className="btn btn-primary" onClick={submit} disabled={register.isPending}>
              <Icon name="check" size={14} />
              Registrar pedido
            </button>
          </>
        )
      }
    >
      {step === 'form' ? (
        <form
          className="grid gap-4"
          onSubmit={(e) => {
            e.preventDefault();
            setTouched(true);
            if (validation.ok) setStep('confirm');
          }}
        >
          <label className="field">
            <span className="field-label">Cliente</span>
            <input className="input" data-autofocus placeholder="c1001" value={form.clientId} onChange={(e) => setForm({ ...form, clientId: e.target.value })} {...field('clientId')} />
            {touched && errors.clientId && <span className="text-xs text-critical-ink">{errors.clientId}</span>}
          </label>
          <div className="grid grid-cols-2 gap-3">
            <label className="field">
              <span className="field-label">Cantidad de paquetes</span>
              <input className="input num" type="number" min={1} max={24} placeholder="1 – 24" value={form.qty} onChange={(e) => setForm({ ...form, qty: e.target.value })} {...field('qty')} />
              {touched && errors.qty && <span className="text-xs text-critical-ink">{errors.qty}</span>}
            </label>
            <label className="field">
              <span className="field-label">Modalidad</span>
              <select className="select" value={form.hourLimit} onChange={(e) => setForm({ ...form, hourLimit: e.target.value })}>
                {modalidades.map((m) => (
                  <option key={m.horas} value={m.horas}>
                    {m.label}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <fieldset className="grid gap-1.5">
            <legend className="field-label mb-1.5">Destino en la retícula (km)</legend>
            <div className="grid grid-cols-2 gap-3">
              <label className="field">
                <span className="text-xs text-ink-3">X (0 – {GRID_W})</span>
                <input className="input num" type="number" min={0} max={GRID_W} value={form.x} onChange={(e) => setForm({ ...form, x: e.target.value })} {...field('x')} />
                {touched && errors.x && <span className="text-xs text-critical-ink">{errors.x}</span>}
              </label>
              <label className="field">
                <span className="text-xs text-ink-3">Y (0 – {GRID_H})</span>
                <input className="input num" type="number" min={0} max={GRID_H} value={form.y} onChange={(e) => setForm({ ...form, y: e.target.value })} {...field('y')} />
                {touched && errors.y && <span className="text-xs text-critical-ink">{errors.y}</span>}
              </label>
            </div>
          </fieldset>
          {validation.ok && validation.enRiesgo && (
            <div className="flex gap-2 rounded-lg bg-warning-soft px-3 py-2.5 text-[13px] text-warning-ink">
              <Icon name="alert" className="mt-0.5 shrink-0" />
              Con este destino y plazo el pedido quedará marcado en riesgo: un auto tardaría más que el plazo desde el almacén central.
            </div>
          )}
          <button type="submit" hidden />
        </form>
      ) : (
        <dl className="divide-y divide-line rounded-xl border border-line">
          {[
            ['Cliente', input.clientId],
            ['Cantidad', `${input.qty} paquete(s)`],
            ['Modalidad', modalidades.find((m) => m.horas === input.hourLimit)?.label ?? `${input.hourLimit} h`],
            ['Destino', `(${input.x}, ${input.y}) km`],
          ].map(([k, v]) => (
            <div key={k} className="flex justify-between px-4 py-3 text-[13px]">
              <dt className="text-ink-3">{k}</dt>
              <dd className="font-medium text-ink">{v}</dd>
            </div>
          ))}
        </dl>
      )}
      {serverError && (
        <div className="mt-4 flex gap-2 rounded-lg bg-critical-soft px-3 py-2.5 text-[13px] text-critical-ink" role="alert">
          <Icon name="alert" className="mt-0.5 shrink-0" />
          No se pudo registrar: {serverError}.
        </div>
      )}
    </Drawer>
  );
}

export function BatchOrdersDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [text, setText] = useState('');
  const batch = useRegisterOrderBatch();
  const toast = useUiStore((s) => s.toast);
  const lines = useMemo(() => parseLote(text), [text]);
  const valid = lines.filter((l) => l.input);
  const invalid = lines.filter((l) => !l.input);

  const close = () => {
    setText('');
    batch.reset();
    onClose();
  };

  const submit = () => {
    batch.mutate(
      valid.map((l) => l.input!),
      {
        onSuccess: (res) => {
          const ok = res.filter((r) => r.ok).length;
          toast('Carga por lote aplicada', ok === res.length ? 'good' : 'warning', `${ok} pedidos registrados${invalid.length ? `, ${invalid.length} líneas descartadas` : ''}.`);
          close();
        },
      },
    );
  };

  return (
    <Dialog
      open={open}
      onClose={close}
      size="lg"
      title="Carga de pedidos por lote"
      description={
        <>
          Una línea por pedido: <code className="font-mono text-ink-2">cliente,cantidad,modalidad,x,y</code> (modalidad en horas: 36, 18, 12, 8 o 4).
        </>
      }
      footer={
        <>
          <span className="mr-auto text-xs text-ink-3">
            <b className="text-good-ink">{valid.length}</b> válidas · <b className={invalid.length ? 'text-critical-ink' : ''}>{invalid.length}</b> con errores
          </span>
          <button type="button" className="btn btn-secondary" onClick={close}>
            Cancelar
          </button>
          <button type="button" className="btn btn-primary" onClick={submit} disabled={!valid.length || batch.isPending}>
            Registrar {valid.length || ''} pedidos
          </button>
        </>
      }
    >
      <textarea className="textarea min-h-40" rows={8} value={text} onChange={(e) => setText(e.target.value)} placeholder={'c2001,10,36,20,20\nc2002,5,8,40,30'} aria-label="Pedidos, uno por línea" />
      {lines.length > 0 && (
        <div className="mt-3 max-h-56 overflow-auto rounded-xl border border-line">
          <table className="data-table">
            <thead>
              <tr>
                <th>Línea</th>
                <th>Contenido</th>
                <th>Resultado</th>
              </tr>
            </thead>
            <tbody>
              {lines.map((l) => (
                <tr key={l.line}>
                  <td className="num">{l.line}</td>
                  <td className="font-mono text-xs">{l.raw}</td>
                  <td>{l.input ? <span className="badge badge-good">Válida</span> : <span className="badge badge-critical">{l.error}</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Dialog>
  );
}
