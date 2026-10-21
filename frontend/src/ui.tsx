import { useEffect, useRef, useState, type ReactNode, type InputHTMLAttributes } from 'react';
import { ApiError } from './api';
export function ErrorNotice({ error }: { error: unknown }) {
 if (!error) return null;
 return <div className="notice danger" role="alert"><strong>{error instanceof ApiError && error.status === 403 ? 'Acceso restringido' : 'No se pudo completar la operación'}</strong><span>{error instanceof ApiError ? error.message : 'Inténtalo nuevamente. Si el problema continúa, contacta al administrador.'}</span>{error instanceof ApiError && error.retryAfter && <small>Vuelve a intentarlo en {error.retryAfter} segundos.</small>}</div>;
}
export function Empty({ title, children }: { title: string; children?: ReactNode }) { return <div className="empty"><span className="empty-mark" aria-hidden="true">↗</span><h3>{title}</h3><p>{children}</p></div>; }
export function Heading({ eyebrow, title, children, action }: { eyebrow: string; title: string; children?: ReactNode; action?: ReactNode }) { return <div className="page-heading"><div><span className="eyebrow">{eyebrow}</span><h1>{title}</h1><p>{children}</p></div>{action}</div>; }
export function Busy() { return <div className="loading" role="status"><span className="spinner"/> Cargando información…</div>; }
export function Badge({ children, muted = false, neutral = false }: { children: ReactNode; muted?: boolean; neutral?: boolean }) { return <span className={`badge ${muted ? 'muted' : ''} ${neutral ? 'neutral' : ''}`}>{children}</span>; }
export function Pagination({ page, next, busy, change }: { page: number; next: boolean; busy: boolean; change(page: number): void }) { return <nav className="pagination" aria-label="Paginación"><span>Página {page + 1}</span><div><button className="button secondary" disabled={page === 0 || busy} onClick={() => change(page - 1)}>Anterior</button><button className="button secondary" disabled={!next || busy} onClick={() => change(page + 1)}>Siguiente</button></div></nav>; }
export function useAction() {
 const [busy, setBusy] = useState(false); const [error, setError] = useState<unknown>(null); const running = useRef(false);
 async function run(action: () => Promise<void>) { if (running.current) return; running.current = true; setBusy(true); setError(null); try { await action(); } catch (failure) { setError(failure); } finally { running.current = false; setBusy(false); } }
 return { busy, error, setError, run };
}
export function Confirm({ title, children, busy, cancel, confirm }: { title: string; children: ReactNode; busy: boolean; cancel(): void; confirm(): void }) {
 const ref = useRef<HTMLDivElement>(null);
 useEffect(() => {
  const previous = document.activeElement as HTMLElement | null;
  const changed: Array<[HTMLElement, boolean]> = [];
  let node = ref.current?.parentElement;
  while (node && node !== document.body) {
   for (const sibling of node.parentElement?.children ?? []) {
    if (sibling !== node && sibling instanceof HTMLElement) { changed.push([sibling, sibling.inert]); sibling.inert = true; }
   }
   node = node.parentElement;
  }
  const overflow = document.body.style.overflow; document.body.style.overflow = 'hidden';
  ref.current?.querySelector<HTMLButtonElement>('button')?.focus();
  return () => { changed.forEach(([element, inert]) => { element.inert = inert; }); document.body.style.overflow = overflow; previous?.focus(); };
 }, []);
 return <div className="modal-backdrop"><div ref={ref} className="modal" role="dialog" aria-modal="true" aria-labelledby="confirm-title" aria-describedby="confirm-description" tabIndex={-1} onKeyDown={e => {
  if (e.key === 'Escape' && !busy) cancel();
  if (e.key === 'Tab') { const items = ref.current?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)'); if (!items?.length) { e.preventDefault(); ref.current?.focus(); } if (items?.length) { const first = items[0], last = items[items.length - 1]; if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); } else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); } } }
 }}><span className="eyebrow">Confirmación de seguridad</span><h2 id="confirm-title">{title}</h2><p id="confirm-description">{children}</p><div className="actions"><button className="button secondary" disabled={busy} onClick={cancel}>Cancelar</button><button className="button" disabled={busy} onClick={confirm}>{busy ? 'Aplicando…' : 'Confirmar cambio'}</button></div></div></div>;
}
export function decimal(value: string) { if (!/^-?\d+(\.\d+)?$/.test(value)) return value; const [whole, fraction] = value.split('.'); return `${BigInt(whole).toLocaleString('es-PE')}${fraction === undefined ? '' : `.${fraction}`}`; }
export function instant(value: string) { const date = new Date(value); return Number.isFinite(date.getTime()) ? date.toLocaleString('es-PE') : 'Fecha no disponible'; }

export type IconName = 'home' | 'users' | 'reports' | 'profile' | 'arrow' | 'lock' | 'menu' | 'close' | 'eye' | 'eye-off';
const paths: Record<IconName, string> = {
 home: 'M3 10 12 3l9 7v10H3z M9 20v-7h6v7',
 users: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M16 4a4 4 0 0 1 0 8 M22 21v-2a4 4 0 0 0-3-4 M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
 reports: 'M5 3h10l4 4v14H5z M14 3v5h5 M8 17v-3 M12 17v-6 M16 17v-4',
 profile: 'M20 21a8 8 0 0 0-16 0 M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
 arrow: 'M5 12h14 M13 6l6 6-6 6', lock: 'M5 10h14v11H5z M8 10V6a4 4 0 0 1 8 0v4',
 menu: 'M3 6h18 M3 12h18 M3 18h18', close: 'm6 6 12 12 M6 18 18 6',
 eye: 'M2 12s4-7 10-7 10 7 10 7-4 7-10 7-10-7-10-7 M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0',
 'eye-off': 'm3 3 18 18 M10 5h2c6 0 10 7 10 7a21 21 0 0 1-4 4 M6 6a22 22 0 0 0-4 6s4 7 10 7a13 13 0 0 0 5-1',
};
export function Icon({ name }: { name: IconName }) { return <svg className="icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[name]}/></svg>; }
export function PasswordInput(props: InputHTMLAttributes<HTMLInputElement>) {
 const [visible, setVisible] = useState(false);
 return <span className="password-control"><input {...props} type={visible ? 'text' : 'password'}/><button type="button" className="password-toggle" aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'} aria-pressed={visible} disabled={props.disabled} onClick={() => setVisible(v => !v)}><Icon name={visible ? 'eye-off' : 'eye'}/></button></span>;
}
export const roleLabel = (role: string) => ({ ADMINISTRADOR: 'Administrador', VENDEDOR: 'Vendedor', ALMACENERO: 'Almacenero' }[role] ?? role);
export function initials(name: string) { return name.trim().split(/\s+/).slice(0, 2).map(n => n[0]).join('').toUpperCase(); }
