// Importación de archivos del curso: se elige el archivo, se lee y valida en el navegador,
// se muestra una vista previa y solo al confirmar se envía a la simulación.
import { useRef, useState } from 'react';
import { useLoadFile } from '@/api/hooks';
import { Dialog } from '@/components/ui/Dialog';
import { Icon } from '@/components/ui/Icon';
import { FILE_KIND_INFO, parseFile, previewRows } from '@/domain/fileFormats';
import type { FileKind } from '@/domain/types';
import { useUiStore } from '@/store/uiStore';

export function FileImportDialog({ kind, open, onClose }: { kind: FileKind; open: boolean; onClose: () => void }) {
  const info = FILE_KIND_INFO[kind];
  const [file, setFile] = useState<{ name: string; text: string; count: number; preview: string[] } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [drag, setDrag] = useState(false);
  const input = useRef<HTMLInputElement>(null);
  const load = useLoadFile();
  const toast = useUiStore((s) => s.toast);

  const reset = () => {
    setFile(null);
    setError(null);
    load.reset();
  };
  const close = () => {
    reset();
    onClose();
  };

  const read = (f: File) => {
    setError(null);
    const reader = new FileReader();
    reader.onload = () => {
      const text = String(reader.result ?? '');
      const parsed = parseFile(kind, text);
      if (!parsed.records.length) {
        setFile(null);
        setError(`No se reconoció ningún registro con el formato ${info.format}.`);
        return;
      }
      setFile({ name: f.name, text, count: parsed.records.length, preview: previewRows(parsed) });
    };
    reader.onerror = () => setError('No se pudo leer el archivo.');
    reader.readAsText(f);
  };

  const confirm = () => {
    if (!file) return;
    load.mutate(
      { kind, text: file.text },
      {
        onSuccess: (res) => {
          toast(`${info.title} cargado`, 'good', res.message);
          close();
        },
      },
    );
  };

  return (
    <Dialog
      open={open}
      onClose={close}
      title={`Importar ${info.title.toLowerCase()}`}
      description="Se valida cada línea antes de aplicar el archivo. Solo se cargan los registros con el formato correcto."
      footer={
        <>
          <button type="button" className="btn btn-secondary" onClick={close}>
            Cancelar
          </button>
          <button type="button" className="btn btn-primary" onClick={confirm} disabled={!file || load.isPending}>
            <Icon name="check" size={14} />
            {file ? `Cargar ${file.count} registros` : 'Cargar'}
          </button>
        </>
      }
    >
      <div className="mb-4 rounded-lg border border-line bg-surface-2 px-3 py-2.5">
        <div className="text-[11px] font-semibold tracking-wider text-ink-3 uppercase">Formato por línea</div>
        <code className="mt-1 block font-mono text-[12.5px] text-ink">{info.format}</code>
        <div className="mt-1 text-xs text-ink-3">
          Ejemplo: <code className="font-mono text-ink-2">{info.example}</code>
        </div>
      </div>

      {!file ? (
        <label
          onDragOver={(e) => {
            e.preventDefault();
            setDrag(true);
          }}
          onDragLeave={() => setDrag(false)}
          onDrop={(e) => {
            e.preventDefault();
            setDrag(false);
            const f = e.dataTransfer.files[0];
            if (f) read(f);
          }}
          className={`flex cursor-pointer flex-col items-center gap-2 rounded-xl border-2 border-dashed px-6 py-10 text-center transition-colors ${
            drag ? 'border-accent bg-accent-soft' : 'border-line-strong hover:bg-surface-2'
          }`}
        >
          <span className="flex size-11 items-center justify-center rounded-xl bg-accent-soft text-accent-ink">
            <Icon name="upload" size={20} />
          </span>
          <span className="text-sm font-medium text-ink">Arrastra el archivo aquí o haz clic para elegirlo</span>
          <span className="text-xs text-ink-3">Texto plano (.txt), una línea por registro</span>
          <input
            ref={input}
            type="file"
            accept=".txt,text/plain"
            className="sr-only"
            onChange={(e) => {
              const f = e.target.files?.[0];
              if (f) read(f);
              e.target.value = '';
            }}
          />
        </label>
      ) : (
        <div className="rounded-xl border border-line">
          <div className="flex items-center gap-3 border-b border-line px-4 py-3">
            <span className="flex size-9 items-center justify-center rounded-lg bg-good-soft text-good-ink">
              <Icon name="file" />
            </span>
            <div className="min-w-0 flex-1">
              <div className="truncate text-[13px] font-medium text-ink">{file.name}</div>
              <div className="text-xs text-ink-3">{file.count} registros válidos</div>
            </div>
            <button type="button" className="btn btn-ghost btn-sm" onClick={reset}>
              Cambiar
            </button>
          </div>
          <div className="px-4 py-3">
            <div className="mb-1.5 text-[11px] font-semibold tracking-wider text-ink-3 uppercase">Vista previa</div>
            <ul className="grid gap-1 font-mono text-xs text-ink-2">
              {file.preview.map((r, i) => (
                <li key={i}>{r}</li>
              ))}
              {file.count > file.preview.length && <li className="text-ink-3">… y {file.count - file.preview.length} registros más</li>}
            </ul>
          </div>
        </div>
      )}
      {(error || load.error) && (
        <div className="mt-3 flex items-start gap-2 rounded-lg bg-critical-soft px-3 py-2.5 text-[13px] text-critical-ink" role="alert">
          <Icon name="alert" className="mt-0.5 shrink-0" />
          {error ?? (load.error instanceof Error ? load.error.message : 'No se pudo cargar el archivo.')}
        </div>
      )}
    </Dialog>
  );
}
