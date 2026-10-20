import { useEffect, useRef, useState, type ReactNode } from 'react';
import { ApiError } from './api';
export function ErrorNotice({ error }: { error: unknown }) {
 if (!error) return null;
 return <div className="notice danger" role="alert"><strong>{error instanceof ApiError && error.status === 403 ? 'Acceso restringido' : 'No se pudo completar la operación'}</strong><span>{error instanceof ApiError ? error.message : 'Inténtalo nuevamente. Si el problema continúa, contacta al administrador.'}</span>{error instanceof ApiError && error.retryAfter && <small>Vuelve a intentarlo en {error.retryAfter} segundos.</small>}</div>;
}
export function Empty({ title, children }: { title: string; children?: ReactNode }) { return <div className="empty"><span className="empty-mark" aria-hidden="true">↗</span><h3>{title}</h3><p>{children}</p></div>; }
export function Heading({ eyebrow, title, children, action }: { eyebrow: string; title: string; children?: ReactNode; action?: ReactNode }) { return <div className="page-heading"><div><span className="eyebrow">{eyebrow}</span><h1>{title}</h1><p>{children}</p></div>{action}</div>; }
export function Busy() { return <div className="loading" role="status"><span className="spinner"/> Cargando información…</div>; }
export function Badge({ children, muted = false }: { children: ReactNode; muted?: boolean }) { return <span className={`badge ${muted ? 'muted' : ''}`}>{children}</span>; }
export function Pagination({ page, next, busy, change }: { page: number; next: boolean; busy: boolean; change(page: number): void }) { return <nav className="pagination" aria-label="Paginación"><span>Página {page + 1}</span><div><button className="button secondary" disabled={page === 0 || busy} onClick={() => change(page - 1)}>Anterior</button><button className="button secondary" disabled={!next || busy} onClick={() => change(page + 1)}>Siguiente</button></div></nav>; }
export function useAction() {
 const [busy, setBusy] = useState(false); const [error, setError] = useState<unknown>(null); const running = useRef(false);
 async function run(action: () => Promise<void>) { if (running.current) return; running.current = true; setBusy(true); setError(null); try { await action(); } catch (failure) { setError(failure); } finally { running.current = false; setBusy(false); } }
 return { busy, error, setError, run };
}
export function Confirm({ title, children, busy, cancel, confirm }: { title: string; children: ReactNode; busy: boolean; cancel(): void; confirm(): void }) {
 const ref = useRef<HTMLDivElement>(null);
 useEffect(() => { const previous = document.activeElement as HTMLElement | null; ref.current?.querySelector<HTMLButtonElement>('button')?.focus(); return () => previous?.focus(); }, []);
 return <div className="modal-backdrop"><div ref={ref} className="modal" role="dialog" aria-modal="true" aria-labelledby="confirm-title" onKeyDown={e => {
  if (e.key === 'Escape' && !busy) cancel();
  if (e.key === 'Tab') { const items = ref.current?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)'); if (items?.length) { const first = items[0], last = items[items.length - 1]; if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); } else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); } } }
 }}><span className="eyebrow">Confirmación de seguridad</span><h2 id="confirm-title">{title}</h2><p>{children}</p><div className="actions"><button className="button secondary" disabled={busy} onClick={cancel}>Cancelar</button><button className="button" disabled={busy} onClick={confirm}>{busy ? 'Aplicando…' : 'Confirmar cambio'}</button></div></div></div>;
}
export function decimal(value: string) { if (!/^-?\d+(\.\d+)?$/.test(value)) return value; const [whole, fraction] = value.split('.'); return `${BigInt(whole).toLocaleString('es-PE')}${fraction === undefined ? '' : `.${fraction}`}`; }
export function instant(value: string) { const date = new Date(value); return Number.isFinite(date.getTime()) ? date.toLocaleString('es-PE') : 'Fecha no disponible'; }
