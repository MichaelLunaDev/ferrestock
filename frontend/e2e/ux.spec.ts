import { test, expect, type Page } from '@playwright/test';
import { resolve } from 'node:path';
const admin = { id: 1, login: 'elena.prueba', nombre: 'Elena Prueba', rol: 'ADMINISTRADOR', estado: 'ACTIVO', versionSeguridad: 1, creadoEn: '2026-01-01T00:00:00Z', actualizadoEn: '2026-01-01T00:00:00Z' };
async function isolatedApi(page: Page, role = 'ADMINISTRADOR') {
 const reportRequests: string[] = [];
 await page.route('**/api/**', async route => {
  const path = new URL(route.request().url()).pathname; const user = { ...admin, rol: role };
  if (path === '/api/auth/login') return route.fulfill({ json: { accessToken: 'v'.repeat(43), tokenType: 'Bearer', expiresAt: new Date(Date.now() + 3600000).toISOString(), usuario: user } });
  if (path === '/api/auth/me' || path === '/api/usuarios/1') return route.fulfill({ json: user });
  if (path === '/api/usuarios') return route.fulfill({ json: [user, { ...user, id: 2, nombre: 'Marco Salazar', login: 'marco.salazar', rol: 'VENDEDOR', estado: 'INACTIVO' }] });
  if (path.startsWith('/api/reportes/')) { reportRequests.push(route.request().url()); return route.fulfill({ status: 503, json: { code: 'CONTRATO_COMERCIAL_PENDIENTE' } }); }
  return route.fulfill({ status: 404, json: { code: 'NO_ENCONTRADO' } });
 });
 return reportRequests;
}
async function login(page: Page) {
 await page.getByLabel('Usuario', { exact: true }).fill('elena.prueba');
 await page.getByLabel('Contraseña', { exact: true }).fill('fixture-password');
 await page.getByRole('button', { name: 'Ingresar al panel' }).click();
 await expect(page.getByRole('heading', { name: 'Hola, Elena.' })).toBeVisible();
}
async function layoutChecks(page: Page) {
 expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
 const missingLabels = await page.locator('input, select').evaluateAll(items => items.filter(item => !(item as HTMLInputElement).labels?.length && !item.getAttribute('aria-label')).length);
 expect(missingLabels).toBe(0);
 await expect(page.locator('html')).toHaveAttribute('lang', 'es');
 // Basic DOM accessibility checks; this is not a WCAG certification or an axe audit.
 expect(await page.locator('button').evaluateAll(items => items.filter(e => !e.textContent?.trim() && !e.getAttribute('aria-label')).length)).toBe(0);
}
for (const width of [320, 375, 390, 768, 1024, 1366, 1440]) {
 test(`ocho vistas a ${width}px, API aislada sin SQL`, async ({ page }) => {
  const errors: string[] = []; page.on('pageerror', e => errors.push(e.name));
  page.on('console', message => { if (message.type() === 'error' && !message.text().includes('status of 503')) errors.push('console-error'); });
  await page.setViewportSize({ width, height: 950 }); const reportRequests = await isolatedApi(page); await page.goto('/');
  const capture = async (name: string) => { await layoutChecks(page); await page.screenshot({ path: resolve(`test-results/ux-after-${name}-${width}.png`), fullPage: true }); };
  await capture('login');
  await page.getByRole('button', { name: 'Mostrar contraseña' }).click();
  await expect(page.getByLabel('Contraseña', { exact: true })).toHaveAttribute('type', 'text');
  await page.getByRole('button', { name: 'Ocultar contraseña' }).click();
  await expect(page.getByLabel('Contraseña', { exact: true })).toHaveAttribute('type', 'password');
  await login(page); await capture('dashboard');
  if (width <= 900) {
   const opener = page.getByRole('button', { name: 'Abrir navegación' }); await opener.click();
   await expect(page.getByRole('button', { name: 'Cerrar menú' })).toBeFocused();
   await page.screenshot({ path: resolve(`test-results/ux-after-menu-${width}.png`), fullPage: true });
   await page.keyboard.press('Shift+Tab'); await expect(page.getByRole('link', { name: 'Mi perfil', exact: true })).toBeFocused();
   await page.keyboard.press('Tab'); await expect(page.getByRole('button', { name: 'Cerrar menú' })).toBeFocused();
   await page.keyboard.press('Escape'); await expect(opener).toBeFocused();
   await opener.click(); await page.getByRole('navigation', { name: 'Principal' }).getByRole('link', { name: 'Usuarios', exact: true }).click();
   await expect(opener).toHaveAttribute('aria-expanded', 'false');
  } else await page.goto('/#/usuarios');
  await expect(page.getByRole('link', { name: 'Administrar a Elena Prueba' })).toBeVisible(); await capture('users');
  await expect(page.getByRole('button', { name: 'Anterior' })).toBeDisabled(); await expect(page.getByRole('button', { name: 'Siguiente' })).toBeDisabled();
  await page.getByRole('link', { name: 'Crear usuario', exact: false }).click();
  await expect(page.getByRole('combobox', { name: 'Rol', exact: true })).toHaveValue('VENDEDOR'); await capture('create');
  await page.goto('/#/usuarios/1'); await expect(page.getByRole('heading', { name: 'Administrar cuenta' })).toBeVisible(); await capture('detail');
  await expect(page.getByRole('button', { name: 'Guardar cambios' })).toBeDisabled();
  await page.getByLabel('Operación').selectOption('rol'); await page.getByRole('combobox', { name: 'Nuevo rol' }).selectOption('VENDEDOR');
  await page.getByRole('button', { name: 'Guardar cambios' }).click();
  await expect(page.getByRole('dialog')).toContainText('Se cerrarán las sesiones'); await capture('confirmation');
  await expect(page.getByRole('button', { name: 'Cancelar', exact: true })).toBeFocused();
  await page.keyboard.press('Shift+Tab'); await expect(page.getByRole('button', { name: 'Confirmar cambio' })).toBeFocused();
  await page.keyboard.press('Escape'); await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.goto('/#/perfil'); await expect(page.getByRole('heading', { name: 'Mi perfil', exact: true })).toBeVisible(); await capture('profile');
  await page.goto('/#/reportes'); await expect(page.getByRole('tab', { name: 'Ventas por período' })).toBeVisible();
  await expect(page.getByRole('button', { name: /PDF/ })).toBeDisabled();
  await page.getByRole('button', { name: 'Consultar reporte' }).click(); await expect(page.getByRole('alert')).toContainText('estarán disponibles');
  await expect(page.getByRole('button', { name: /Excel/ })).toBeDisabled();
  await expect(page.locator('#report-panel > .notice:visible')).toHaveCount(1);
  await expect(page.locator('.report-unavailable:visible')).toHaveCount(1);
  await expect(page.locator('#report-panel .notice.danger')).toHaveCount(0);
  await expect(page.getByText('No se pudo completar la operación', { exact: true })).toHaveCount(0);
  expect(reportRequests).toHaveLength(1);
  expect(new URL(reportRequests[0]).searchParams.get('formato')).toBe('JSON');
  await capture('reports-503');
  expect(errors).toEqual([]);
 });
}
test('reflujo equivalente a zoom 200% y movimiento reducido', async ({ page }) => {
 await page.setViewportSize({ width: 683, height: 500 }); await page.emulateMedia({ reducedMotion: 'reduce' });
 await isolatedApi(page); await page.goto('/'); await login(page); await layoutChecks(page);
 expect(await page.locator('.quick-card').first().evaluate(e => getComputedStyle(e).transitionDuration)).toBe('0s');
});
test('roles no administrativos mantienen accesos limitados', async ({ browser }) => {
 for (const role of ['VENDEDOR', 'ALMACENERO']) {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 } }); const page = await context.newPage();
  await isolatedApi(page, role); await page.goto('/'); await login(page);
  await expect(page.getByRole('link', { name: 'Crear usuario' })).toHaveCount(0);
  await page.goto('/#/usuarios'); await expect(page.getByRole('heading', { name: 'Esta sección requiere otro permiso.' })).toBeVisible();
  expect(await page.evaluate(() => localStorage.length + sessionStorage.length)).toBe(0); await context.close();
 }
});
test('contraste de tokens de texto, marca, alertas y borde de campo', async ({ page }) => {
 await isolatedApi(page); await page.goto('/');
 const ratios = await page.evaluate(() => {
  const css = getComputedStyle(document.documentElement);
  const value = (name: string) => css.getPropertyValue(name).trim();
  const luminance = (hex: string) => {
   const raw = hex.replace('#', '');
   const normalized = raw.length === 3 ? raw.split('').map(n => n + n).join('') : raw;
   const values = normalized.match(/../g)!.map(n => parseInt(n, 16) / 255).map(n => n <= .04045 ? n / 12.92 : ((n + .055) / 1.055) ** 2.4);
   return values[0] * .2126 + values[1] * .7152 + values[2] * .0722;
  };
  const ratio = (a: string, b: string) => { const x = luminance(a), y = luminance(b); return (Math.max(x, y) + .05) / (Math.min(x, y) + .05); };
  return [
   ratio(value('--text'), value('--surface')), ratio(value('--muted'), value('--background')),
   ratio('#ffffff', value('--green')), ratio(value('--green'), value('--green-soft')),
   ratio(value('--error'), value('--error-bg')), ratio(value('--warning'), value('--warning-bg')),
   ratio(value('--success'), value('--success-bg')), ratio('#c7d6cd', value('--green')),
   ratio(value('--focus'), value('--surface')), ratio('#7b8c7f', value('--surface')),
  ];
 });
 ratios.slice(0, 8).forEach(value => expect(value).toBeGreaterThanOrEqual(4.5));
 ratios.slice(8).forEach(value => expect(value).toBeGreaterThanOrEqual(3));
});

test('consulta válida mantiene descargas PDF y Excel con API aislada', async ({ page }) => {
 await isolatedApi(page);
 const formats: string[] = [];
 await page.route('**/api/reportes/**', async route => {
  const format = new URL(route.request().url()).searchParams.get('formato')!; formats.push(format);
  if (format === 'JSON') return route.fulfill({ json: { filas: [{ id: 1, fecha: '2026-10-01T10:00:00', vendedor: 'Fixture', total: 12, estado: 'COMPLETADA' }] } });
  return route.fulfill({ contentType: format === 'PDF' ? 'application/pdf' : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', body: format === 'PDF' ? '%PDF-1.7 fixture-only' : 'xlsx-fixture-only' });
 });
 await page.goto('/'); await login(page); await page.goto('/#/reportes');
 await expect(page.getByRole('button', { name: /PDF/ })).toBeDisabled();
 await page.getByRole('button', { name: 'Consultar reporte' }).click(); await expect(page.getByRole('table')).toBeVisible();
 for (const [name, extension] of [['PDF', 'pdf'], ['Excel', 'xlsx']]) {
  const pending = page.waitForEvent('download'); await page.getByRole('button', { name: new RegExp(name) }).click();
  const download = await pending; expect(download.suggestedFilename()).toBe(`ventas.${extension}`);
 }
 expect(formats).toEqual(['JSON', 'PDF', 'XLSX']);
 await expect(page.locator('.report-unavailable')).toHaveCount(0);
});
