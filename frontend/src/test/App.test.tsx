import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { App } from '../App';
import { ApiClient } from '../api';
const fixture = { id: 1, login: 'admin', nombre: 'Elena Prueba', rol: 'ADMINISTRADOR', estado: 'ACTIVO', versionSeguridad: 1, creadoEn: '2026-01-01T00:00:00Z', actualizadoEn: '2026-01-01T00:00:00Z' };
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });
function app(role = 'ADMINISTRADOR', handler?: (url: string, options?: RequestInit) => Response | undefined) {
 const fetcher = vi.fn(async (url: RequestInfo | URL, options?: RequestInit) => {
  const path = String(url); const custom = handler?.(path, options); if (custom) return custom;
  if (path.endsWith('/auth/login')) return json({ accessToken: 'a'.repeat(43), tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString(), usuario: { ...fixture, rol: role } });
  if (path.endsWith('/auth/me')) return json({ ...fixture, rol: role });
  if (path.endsWith('/auth/logout')) return new Response(null, { status: 204 });
  if (path.includes('/usuarios?')) return json([{ ...fixture, rol: role }]);
  if (path.endsWith('/usuarios/1') && options?.method === 'GET') return json(fixture);
  if (path.includes('/reportes/')) return json({ code: 'CONTRATO_COMERCIAL_PENDIENTE' }, 503);
  return json({}, 404);
 });
 render(<App client={new ApiClient('/api', fetcher)}/>); return fetcher;
}
async function login() { const events = userEvent.setup(); await events.type(screen.getByLabelText('Usuario'), 'admin'); await events.type(screen.getByLabelText('Contraseña'), 'test-password'); await events.click(screen.getByRole('button', { name: /Ingresar al panel/ })); await screen.findByRole('heading', { name: 'Hola, Elena.' }); return events; }
describe('flujos del panel', () => {
 it('crear usuario valida y confirma antes de enviar credencial', async () => { const fetcher = app('ADMINISTRADOR', (url, options) => url === '/api/usuarios' && options?.method === 'POST' ? json({ ...fixture, id: 2, login: 'nuevo', nombre: 'Nuevo Prueba' }, 201) : url.endsWith('/usuarios/2') ? json({ ...fixture, id: 2, login: 'nuevo', nombre: 'Nuevo Prueba' }) : undefined); const events = await login(); window.location.hash = '#/usuarios/nuevo'; await screen.findByLabelText('Nombre y apellidos'); await events.type(screen.getByLabelText('Nombre y apellidos'), 'Nuevo Prueba'); await events.type(screen.getByLabelText('Usuario', { exact: true }), 'nuevo'); await events.type(screen.getByLabelText('Contraseña inicial'), 'fixture-password'); await events.type(screen.getByLabelText('Repetir contraseña'), 'fixture-password'); await events.click(screen.getByRole('button', { name: 'Crear cuenta' })); await screen.findByRole('dialog'); expect(fetcher.mock.calls.filter(([url, options]) => String(url) === '/api/usuarios' && options?.method === 'POST')).toHaveLength(0); await events.click(screen.getByRole('button', { name: 'Confirmar cambio' })); await screen.findByRole('heading', { name: 'Administrar cuenta' }); expect(fetcher.mock.calls.filter(([url, options]) => String(url) === '/api/usuarios' && options?.method === 'POST')).toHaveLength(1); });
 it('pestañas de reportes permiten navegación con teclado', async () => { app(); const events = await login(); window.location.hash = '#/reportes'; const tab = await screen.findByRole('tab', { name: 'Ventas por período' }); tab.focus(); await events.keyboard('{ArrowRight}'); expect(screen.getByRole('tab', { name: 'Productos más vendidos' })).toHaveAttribute('aria-selected', 'true'); await events.keyboard('{End}'); expect(screen.getByRole('tab', { name: 'Stock bajo' })).toHaveFocus(); });
 it('ruta protegida sin sesión presenta login y no llama API', () => { window.location.hash = '#/usuarios'; const fetcher = app(); expect(screen.getByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument(); expect(fetcher).not.toHaveBeenCalled(); });
 it('login consulta me y guarda cero secretos en storage', async () => { const storage = vi.spyOn(Storage.prototype, 'setItem'); const fetcher = app(); await login(); expect(fetcher.mock.calls.some(([url]) => String(url).endsWith('/auth/me'))).toBe(true); expect(storage).not.toHaveBeenCalled(); storage.mockRestore(); });
 it('credenciales incorrectas dejan mensaje claro y contraseña vacía', async () => { app('ADMINISTRADOR', url => url.endsWith('/auth/login') ? json({}, 401) : undefined); const events = userEvent.setup(); await events.type(screen.getByLabelText('Usuario'), 'admin'); await events.type(screen.getByLabelText('Contraseña'), 'test-password'); await events.click(screen.getByRole('button', { name: /Ingresar al panel/ })); expect(await screen.findByRole('alert')).toHaveTextContent('Usuario o contraseña incorrectos'); expect(screen.getByLabelText('Contraseña')).toHaveValue(''); });
 it('logout elimina acceso y requiere reingreso', async () => { app(); const events = await login(); await events.click(screen.getByRole('button', { name: /Salir/ })); expect(await screen.findByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument(); expect(screen.getByRole('status')).toHaveTextContent('cerrado sesión'); });
 it.each(['VENDEDOR', 'ALMACENERO'])('%s no ve navegación administrativa y ruta directa es 403', async role => { app(role); await login(); expect(screen.queryByRole('link', { name: /^Usuarios$/ })).not.toBeInTheDocument(); fireEvent.click(screen.getByRole('link', { name: 'Consultar mi perfil' })); await screen.findByRole('heading', { name: 'Mi perfil' }); window.location.hash = '#/reportes'; await waitFor(() => expect(screen.getByRole('heading', { name: 'Esta sección requiere otro permiso.' })).toBeInTheDocument()); });
 it('usuarios pide página real y permite consulta del detalle', async () => { const fetcher = app(); const events = await login(); await events.click(screen.getByRole('link', { name: /Usuarios$/ })); await screen.findByRole('link', { name: 'Administrar a Elena Prueba' }); expect(fetcher.mock.calls.some(([url]) => String(url).includes('/usuarios?page=0&size=25'))).toBe(true); expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled(); await events.click(screen.getByRole('link', { name: 'Administrar a Elena Prueba' })); await screen.findByRole('heading', { name: 'Administrar cuenta' }); expect(screen.getByLabelText('Nombre y apellidos')).toHaveValue('Elena Prueba'); });
 it('409 muestra conflicto sin sobrescribir ni reintentar automáticamente', async () => { const fetcher = app('ADMINISTRADOR', (url, options) => url.endsWith('/usuarios/1') && options?.method === 'PUT' ? json({ code: 'VERSION_CONFLICTO' }, 409) : undefined); const events = await login(); window.location.hash = '#/usuarios/1'; await screen.findByLabelText('Nombre y apellidos'); await events.clear(screen.getByLabelText('Nombre y apellidos')); await events.type(screen.getByLabelText('Nombre y apellidos'), 'Elena Editada'); await events.click(screen.getByRole('button', { name: 'Guardar cambios' })); expect(await screen.findByRole('alert')).toHaveTextContent('Recarga sus datos'); expect(fetcher.mock.calls.filter(([, opt]) => opt?.method === 'PUT')).toHaveLength(1); const body = JSON.parse(fetcher.mock.calls.find(([, opt]) => opt?.method === 'PUT')![1]!.body as string); expect(body).toEqual({ nombre: 'Elena Editada', versionEsperada: 1, actualizadoEnEsperado: fixture.actualizadoEn }); });
 it('cambio de rol requiere confirmación y muestra protección del último admin', async () => { const fetcher = app('ADMINISTRADOR', (url, options) => url.endsWith('/usuarios/1/rol') && options?.method === 'PUT' ? json({ code: 'ULTIMO_ADMINISTRADOR' }, 409) : undefined); const events = await login(); window.location.hash = '#/usuarios/1'; await screen.findByLabelText('Operación'); await events.selectOptions(screen.getByLabelText('Operación'), 'rol'); await events.selectOptions(screen.getByLabelText('Nuevo rol'), 'VENDEDOR'); await events.click(screen.getByRole('button', { name: 'Guardar cambios' })); const dialog = await screen.findByRole('dialog'); expect(fetcher.mock.calls.filter(([, options]) => options?.method === 'PUT')).toHaveLength(0); await events.click(within(dialog).getByRole('button', { name: 'Confirmar cambio' })); expect(await screen.findByRole('alert')).toHaveTextContent('al menos un administrador activo'); });
 it('reporte 503 no inventa filas ni descarga como PDF', async () => { const fetcher = app(); const events = await login(); await events.click(screen.getByRole('link', { name: /Reportes$/ })); await events.click(screen.getByRole('button', { name: 'Consultar reporte' })); expect(await screen.findByRole('alert')).toHaveTextContent('reportes comerciales estarán disponibles'); expect(screen.queryByRole('table')).not.toBeInTheDocument(); expect(screen.getByRole('button', { name: /PDF/ })).toBeDisabled(); expect(screen.getByRole('button', { name: /Excel/ })).toBeDisabled(); expect(fetcher.mock.calls.filter(([url]) => String(url).includes('formato=PDF'))).toHaveLength(0); });
 it('cambia pestaña y envía umbral sin fechas', async () => { const fetcher = app(); const events = await login(); window.location.hash = '#/reportes'; await screen.findByRole('tab', { name: 'Stock bajo' }); await events.click(screen.getByRole('tab', { name: 'Stock bajo' })); await events.clear(screen.getByLabelText('Umbral de stock')); await events.type(screen.getByLabelText('Umbral de stock'), '8'); await events.click(screen.getByRole('button', { name: 'Consultar reporte' })); await screen.findByRole('alert'); expect(fetcher.mock.calls.some(([url]) => String(url).endsWith('/reportes/stock-bajo?formato=JSON&umbral=8'))).toBe(true); });
 it('401 de operación expira interfaz y no conserva tabla previa', async () => { app('ADMINISTRADOR', url => url.includes('/usuarios?') ? json({}, 401) : undefined); const events = await login(); await events.click(screen.getByRole('link', { name: /Usuarios$/ })); expect(await screen.findByRole('heading', { name: 'Inicia sesión' })).toBeInTheDocument(); expect(screen.queryByRole('table')).not.toBeInTheDocument(); expect(screen.getByRole('status')).toHaveTextContent('vencido o fue revocada'); });
});

describe('regresiones UX y seguridad del rediseño', () => {
 it('muestra y oculta contraseña sin almacenarla y la borra tras un error', async () => {
  app('ADMINISTRADOR', url => url.endsWith('/auth/login') ? json({}, 401) : undefined);
  const events = userEvent.setup(); const password = screen.getByLabelText('Contraseña', { exact: true });
  await events.type(screen.getByLabelText('Usuario'), 'admin'); await events.type(password, 'test-password');
  await events.click(screen.getByRole('button', { name: 'Mostrar contraseña' })); expect(password).toHaveAttribute('type', 'text');
  await events.click(screen.getByRole('button', { name: 'Ocultar contraseña' })); expect(password).toHaveAttribute('type', 'password');
  await events.click(screen.getByRole('button', { name: /Ingresar al panel/ })); await screen.findByRole('alert'); expect(password).toHaveValue('');
 });
 it('selector Rol conserva nombre accesible y valores del contrato', async () => {
  app(); await login(); window.location.hash = '#/usuarios/nuevo';
  const role = await screen.findByRole('combobox', { name: 'Rol' });
  expect(role).toHaveValue('VENDEDOR'); expect(within(role).getByRole('option', { name: 'Administrador' })).toHaveValue('ADMINISTRADOR');
 });
 it('bloquea guardar una contraseña incompleta o distinta de su confirmación', async () => {
  app(); const events = await login(); window.location.hash = '#/usuarios/1'; await screen.findByLabelText('Operación');
  await events.selectOptions(screen.getByLabelText('Operación'), 'password');
  const save = screen.getByRole('button', { name: 'Guardar cambios' }); expect(save).toBeDisabled();
  await events.type(screen.getByLabelText('Nueva contraseña', { exact: true }), 'fixture-password'); expect(save).toBeDisabled();
  await events.type(screen.getByLabelText('Repetir nueva contraseña'), 'fixture-password'); expect(save).toBeEnabled();
 });
 it('no ofrece una búsqueda local engañosa ni una página siguiente inexistente', async () => {
  app(); await login(); window.location.hash = '#/usuarios'; await screen.findByRole('link', { name: 'Administrar a Elena Prueba' });
  expect(screen.queryByRole('searchbox')).not.toBeInTheDocument(); expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled();
  expect(screen.getByText('1 cuenta en esta página · Hasta 25 por página')).toBeInTheDocument();
 });
 it('exportaciones requieren consulta válida y se deshabilitan al cambiar filtros', async () => {
  app('ADMINISTRADOR', url => url.includes('/reportes/') ? json({ filas: [{ id: 1, fecha: '2026-01-01T10:00:00', vendedor: 'Prueba', total: 12, estado: 'COMPLETADA' }] }) : undefined);
  const events = await login(); window.location.hash = '#/reportes'; const pdf = await screen.findByRole('button', { name: /PDF/ }); expect(pdf).toBeDisabled();
  await events.click(screen.getByRole('button', { name: 'Consultar reporte' })); await screen.findByRole('table'); expect(pdf).toBeEnabled();
  await events.click(screen.getByRole('tab', { name: 'Stock bajo' })); expect(pdf).toBeDisabled(); expect(screen.queryByRole('table')).not.toBeInTheDocument();
 });
 it('reportes 503 muestra habilitación pendiente sin confundirlo con cero ventas', async () => {
  app(); const events = await login(); window.location.hash = '#/reportes';
  await events.click(await screen.findByRole('button', { name: 'Consultar reporte' }));
  await screen.findByRole('alert'); expect(screen.getByText('Reportes pendientes de habilitación')).toBeInTheDocument();
  expect(screen.queryByText('Sin registros para estos filtros')).not.toBeInTheDocument();
 });
});

it('resumen de acceso usa solo la sesión y las tarjetas explican su destino', async () => {
 const fetcher = app(); await login();
 const summary = screen.getByRole('region', { name: 'Resumen de tu acceso' });
 expect(within(summary).getByText('admin')).toBeInTheDocument();
 expect(within(summary).getByText('Sesión activa')).toBeInTheDocument();
 expect(screen.getByText('Administrar equipo')).toBeInTheDocument();
 expect(screen.getByText('Explorar reportes')).toBeInTheDocument();
 expect(fetcher.mock.calls.some(([url]) => String(url).includes('/usuarios') || String(url).includes('/reportes'))).toBe(false);
});
it('503 presenta un único aviso de dependencia comercial y no habilita exportaciones', async () => {
 const fetcher = app(); const events = await login(); window.location.hash = '#/reportes';
 await events.click(await screen.findByRole('button', { name: 'Consultar reporte' }));
 const alert = await screen.findByRole('alert');
 expect(screen.getAllByRole('alert')).toHaveLength(1);
 expect(alert).toHaveTextContent('Reportes pendientes de habilitación');
 expect(alert).toHaveTextContent('Esto no significa que no existan ventas');
 expect(document.querySelectorAll('#report-panel > .notice')).toHaveLength(1);
 expect(document.querySelectorAll('#report-panel .notice.danger')).toHaveLength(0);
 expect(screen.queryByText('No se pudo completar la operación')).not.toBeInTheDocument();
 expect(fetcher.mock.calls.filter(([url]) => /formato=(PDF|XLSX)/.test(String(url)))).toHaveLength(0);
 expect(screen.getByRole('button', { name: /PDF/ })).toBeDisabled();
 expect(screen.getByRole('button', { name: /Excel/ })).toBeDisabled();
});

it.each([400, 403, 500, 503])('error HTTP %s sin código comercial conserva el aviso genérico', async status => {
 app('ADMINISTRADOR', url => url.includes('/reportes/') ? json({ code: 'FALLO_DESCONOCIDO' }, status) : undefined);
 const events = await login(); window.location.hash = '#/reportes';
 await events.click(await screen.findByRole('button', { name: 'Consultar reporte' }));
 expect(await screen.findByRole('alert')).toHaveTextContent(status === 403 ? 'Acceso restringido' : 'No se pudo completar la operación');
 expect(document.querySelector('.report-unavailable')).toBeNull();
});
it('error de red de Reportes no se etiqueta como habilitación comercial', async () => {
 const fetcher = app(); const events = await login();
 fetcher.mockRejectedValueOnce(new TypeError('Network unavailable fixture'));
 window.location.hash = '#/reportes'; await events.click(await screen.findByRole('button', { name: 'Consultar reporte' }));
 expect(await screen.findByRole('alert')).toHaveTextContent('Comprueba tu conexión');
 expect(document.querySelector('.report-unavailable')).toBeNull();
});
it('401 de Reportes conserva la expiración de sesión', async () => {
 app('ADMINISTRADOR', url => url.includes('/reportes/') ? json({}, 401) : undefined);
 const events = await login(); window.location.hash = '#/reportes';
 await events.click(await screen.findByRole('button', { name: 'Consultar reporte' }));
 await screen.findByRole('heading', { name: 'Inicia sesión' });
 expect(document.querySelector('.report-unavailable')).toBeNull();
 expect(screen.getByRole('status')).toHaveTextContent('vencido o fue revocada');
});
it('validación de filtros no se transforma en aviso comercial ni solicita exportación', async () => {
 const fetcher = app(); const events = await login(); window.location.hash = '#/reportes';
 await screen.findByLabelText('Desde');
 fireEvent.change(screen.getByLabelText('Desde'), { target: { value: '2026-10-20' } });
 fireEvent.change(screen.getByLabelText('Hasta'), { target: { value: '2026-10-01' } });
 await events.click(screen.getByRole('button', { name: 'Consultar reporte' }));
 expect(await screen.findByRole('alert')).toHaveTextContent('366 días');
 expect(document.querySelector('.report-unavailable')).toBeNull();
 expect(fetcher.mock.calls.filter(([url]) => String(url).includes('/reportes/'))).toHaveLength(0);
});
