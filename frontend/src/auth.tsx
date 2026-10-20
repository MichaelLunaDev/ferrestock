import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { ApiClient, ApiError } from './api';
import type { User } from './types';

interface Session { user: User; expiresAt: string }
interface Auth { client: ApiClient; session: Session | null; message: string; login(login: string, password: string): Promise<void>; logout(): Promise<void>; applyUser(user: User, security: boolean): void }
const Context = createContext<Auth | null>(null);
export function AuthProvider({ client, children }: { client: ApiClient; children: ReactNode }) {
 const [session, setSession] = useState<Session | null>(null); const [message, setMessage] = useState('');
 const current = useRef(session); current.current = session;
 useEffect(() => {
  client.onExpired = () => { setSession(null); setMessage('Tu sesión ha vencido o fue revocada. Inicia sesión nuevamente.'); };
  return () => { client.onExpired = undefined; };
 }, [client]);
 useEffect(() => {
  if (!session) return;
  const timer = setTimeout(() => { client.setToken(null); setSession(null); setMessage('Tu sesión ha vencido. Inicia sesión nuevamente.'); }, Math.min(2147483647, Math.max(0, Date.parse(session.expiresAt) - Date.now())));
  const focus = () => { void client.me().then(user => setSession(s => s ? { ...s, user } : null)).catch(error => { if (error instanceof ApiError && error.status !== 401) setMessage('No se pudo comprobar la sesión. El servicio volverá a validarla en tu próxima solicitud.'); }); };
  window.addEventListener('focus', focus);
  return () => { clearTimeout(timer); window.removeEventListener('focus', focus); };
 }, [client, session?.expiresAt]);
 async function login(login: string, password: string) {
  const result = await client.login(login, password); client.setToken(result.accessToken);
  try {
   const user = await client.me();
   if (user.estado !== 'ACTIVO') throw new ApiError(401, '');
   setSession({ user, expiresAt: result.expiresAt }); setMessage('');
  } catch (error) { client.setToken(null); setSession(null); throw error; }
 }
 async function logout() {
  let confirmed = false;
  try { await client.logout(); confirmed = true; }
  catch { /* borrar acceso local incluso si no se pudo confirmar revocacion remota */ }
  finally { client.setToken(null); setSession(null); setMessage(confirmed ? 'Has cerrado sesión correctamente.' : 'Acceso local cerrado. No se pudo confirmar el cierre remoto; la sesión vencerá según la política del servicio.'); }
 }
 function applyUser(user: User, security: boolean) {
  if (current.current?.user.id !== user.id) return;
  if (security) { client.setToken(null); setSession(null); setMessage('Cambiaste tu seguridad. Inicia sesión nuevamente.'); }
  else setSession(s => s ? { ...s, user } : null);
 }
 return <Context.Provider value={{ client, session, message, login, logout, applyUser }}>{children}</Context.Provider>;
}
export function useAuth() { const context = useContext(Context); if (!context) throw new Error('Contexto no disponible'); return context; }
