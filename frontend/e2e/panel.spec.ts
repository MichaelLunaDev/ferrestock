import { test, expect } from '@playwright/test';
import { resolve } from 'node:path';
// Fixture únicamente en test. Ninguna ruta/producto mock se importa en src ni en dist.
const admin = { id: 1, login: 'elena.prueba', nombre: 'Elena Prueba', rol: 'ADMINISTRADOR', estado: 'ACTIVO', versionSeguridad: 1, creadoEn: '2026-01-01T00:00:00Z', actualizadoEn: '2026-01-01T00:00:00Z' };
test('panel completo con API interceptada, sin SQL', async ({ page }, testInfo) => {
 const errors: string[] = []; page.on('pageerror', error => errors.push(error.message));
 let role = 'ADMINISTRADOR'; let name = admin.nombre; let version = 1; let created: Record<string, unknown> | null = null; const requests: Array<{ path: string; method: string }> = [];
 await page.route('**/api/**', async route => {
  const request = route.request(); const url = new URL(request.url()); requests.push({ path: url.pathname, method: request.method() });
  const user = { ...admin, nombre: name, rol: role, versionSeguridad: version };
  if (url.pathname === '/api/auth/login') return route.fulfill({ json: { accessToken: 'a'.repeat(43), tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString(), usuario: user } });
  if (url.pathname === '/api/auth/me') return route.fulfill({ json: user });
  if (url.pathname === '/api/auth/logout') return route.fulfill({ status: 204 });
  if (url.pathname === '/api/usuarios' && request.method() === 'GET') return route.fulfill({ json: [user, { ...admin, id: 2, login: 'vendedor.prueba', nombre: 'Marco Prueba', rol: 'VENDEDOR' }] });
  if (url.pathname === '/api/usuarios' && request.method() === 'POST') { created = { ...admin, id: 3, ...request.postDataJSON(), password: undefined }; return route.fulfill({ status: 201, json: created }); }
  if (url.pathname === '/api/usuarios/3' && created) return route.fulfill({ json: created });
  if (url.pathname === '/api/usuarios/1' && request.method() === 'PUT') { name = request.postDataJSON().nombre; return route.fulfill({ json: { ...user, nombre: name, actualizadoEn: '2026-01-01T00:00:01Z' } }); }
  if (url.pathname === '/api/usuarios/1/rol') { role = request.postDataJSON().rol; version++; return route.fulfill({ json: { ...user, rol: role, versionSeguridad: version } }); }
  if (url.pathname.startsWith('/api/usuarios/')) return route.fulfill({ json: user });
  if (url.pathname.startsWith('/api/reportes/')) return route.fulfill({ status: 503, json: { code: 'CONTRATO_COMERCIAL_PENDIENTE' } });
  return route.fulfill({ status: 404, json: { code: 'NO_ENCONTRADO' } });
 });
 await page.goto('/');
 await expect(page.getByRole('heading', { name: 'Inicia sesión' })).toBeVisible();
 await page.screenshot({ path: resolve(`../auth-reportes-service/target/fullstack01-login-${testInfo.project.name}.png`), fullPage: true });
 await page.getByLabel('Usuario', { exact: true }).fill('elena.prueba'); await page.getByLabel('Contraseña', { exact: true }).fill('fixture-password');
 await page.getByRole('button', { name: 'Ingresar al panel' }).click(); await expect(page.getByRole('heading', { name: 'Hola, Elena.' })).toBeVisible();
 await page.screenshot({ path: resolve(`../auth-reportes-service/target/fullstack01-dashboard-${testInfo.project.name}.png`), fullPage: true });
 async function navigate(name: string) { if (testInfo.project.name === 'mobile') await page.getByRole('button', { name: 'Abrir navegación' }).click(); await page.getByRole('navigation', { name: 'Principal' }).getByRole('link', { name, exact: true }).click(); }
 await navigate('Usuarios'); await expect(page.getByRole('link', { name: 'Administrar a Elena Prueba' })).toBeVisible();
 await page.screenshot({ path: resolve(`../auth-reportes-service/target/fullstack01-users-${testInfo.project.name}.png`), fullPage: true });
 await page.getByRole('link', { name: 'Crear usuario' }).click();
 await page.getByLabel('Nombre y apellidos').fill('Nuevo Prueba'); await page.getByLabel('Usuario', { exact: true }).fill('nuevo.prueba');
 await page.getByLabel('Contraseña inicial').fill('fixture-password'); await page.getByLabel('Repetir contraseña').fill('fixture-password');
 await page.getByRole('button', { name: 'Crear cuenta' }).click(); await expect(page.getByRole('dialog')).toBeVisible(); await page.getByRole('button', { name: 'Cancelar', exact: true }).click();
 await page.getByRole('button', { name: 'Crear cuenta' }).click(); await page.getByRole('button', { name: 'Confirmar cambio' }).click();
 await expect(page.getByRole('heading', { name: 'Administrar cuenta' })).toBeVisible();
 await page.goto('/#/usuarios/1'); await page.getByLabel('Nombre y apellidos').fill('Elena Editada'); await page.getByRole('button', { name: 'Guardar cambios' }).click();
 await expect(page.getByRole('status')).toHaveText('El cambio se aplicó correctamente.');
 await navigate('Reportes');
 for (const name of ['Ventas por período', 'Productos más vendidos', 'Stock bajo']) {
  await page.getByRole('tab', { name, exact: true }).click(); await page.getByRole('button', { name: 'Consultar reporte' }).click();
  await expect(page.getByRole('alert')).toContainText('Los reportes comerciales todavía no están disponibles');
 }
 await page.getByRole('button', { name: /PDF/ }).click(); await expect(page.getByRole('alert')).toContainText('todavía no están disponibles');
 await page.getByRole('button', { name: /Excel/ }).click(); await expect(page.getByRole('alert')).toContainText('todavía no están disponibles');
 await page.screenshot({ path: resolve(`../auth-reportes-service/target/fullstack01-report-unavailable-${testInfo.project.name}.png`), fullPage: true });
 expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
 await page.goto('/#/usuarios/1'); await page.getByLabel('Operación').selectOption('rol'); await page.getByLabel('Nuevo rol').selectOption('VENDEDOR');
 await page.getByRole('button', { name: 'Guardar cambios' }).click(); await page.getByRole('button', { name: 'Confirmar cambio' }).click();
 await expect(page.getByRole('heading', { name: 'Inicia sesión' })).toBeVisible();
 await page.getByLabel('Usuario', { exact: true }).fill('elena.prueba'); await page.getByLabel('Contraseña', { exact: true }).fill('fixture-password'); await page.getByRole('button', { name: 'Ingresar al panel' }).click();
 await expect(page.getByRole('heading', { name: 'Hola, Elena.' })).toBeVisible(); await page.goto('/#/reportes'); await expect(page.getByRole('heading', { name: 'Esta sección requiere otro permiso.' })).toBeVisible();
 expect(await page.evaluate(() => ({ local: localStorage.length, session: sessionStorage.length }))).toEqual({ local: 0, session: 0 });
 await page.getByRole('button', { name: 'Salir', exact: false }).click(); await expect(page.getByRole('heading', { name: 'Inicia sesión' })).toBeVisible();
 expect(requests.filter(r => r.path === '/api/usuarios' && r.method === 'POST')).toHaveLength(1);
 expect(errors).toEqual([]);
});
