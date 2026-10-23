import { parse, stringify, LosslessNumber } from 'lossless-json';
import type { Categoria, Cliente, DetalleVenta, ItemVenta, LoginResult, Producto, Report, ReportFormat, ReportQuery, Role, User, Venta } from './types';

const messages: Record<string, string> = {
 CONTRATO_COMERCIAL_PENDIENTE: 'Los reportes comerciales todavía no están disponibles. Inténtalo cuando el servicio esté habilitado.',
 ULTIMO_ADMINISTRADOR: 'Debe permanecer al menos un administrador activo.', LOGIN_EN_USO: 'Este nombre de usuario ya está en uso.',
 VERSION_CONFLICTO: 'Otro cambio modificó este usuario. Recarga sus datos antes de continuar.',
 REPORTE_DEMASIADO_GRANDE: 'El reporte supera las 5 000 filas. Reduce el período o el umbral.',
 OPERACION_EN_CURSO: 'Esta operación ya está en curso. Espera a que termine.',
 RESPUESTA_INVALIDA: 'No se pudo verificar la respuesta del servicio.',
 STOCK_INSUFICIENTE: 'No hay stock suficiente para completar la venta. No se realizó ningún cambio.',
};
export class ApiError extends Error {
 constructor(public status: number, public code: string, public retryAfter?: number) {
  super(messages[code] ?? ({ 400: 'Revisa los datos ingresados.', 401: 'La sesión no es válida o ha vencido. Inicia sesión nuevamente.',
   403: 'No tienes permiso para esta operación.', 404: 'El recurso solicitado no está disponible.',
   409: 'No se pudo aplicar el cambio. Recarga los datos.', 422: 'El reporte solicitado no se puede generar.',
   429: 'Demasiados intentos. Espera antes de volver a ingresar.', 503: 'El servicio no está disponible. Inténtalo más tarde.' } as Record<number, string>)[status]
   ?? 'No se pudo completar la solicitud. Comprueba tu conexión.');
 }
}
const record = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value);
export function isUser(value: unknown): value is User {
 if (!record(value)) return false;
 return ['id', 'login', 'nombre', 'versionSeguridad', 'creadoEn', 'actualizadoEn'].every(k => typeof value[k] === 'string')
  && ['ADMINISTRADOR', 'VENDEDOR', 'ALMACENERO'].includes(value.rol as string)
  && ['ACTIVO', 'INACTIVO'].includes(value.estado as string) && validId(value.id as string) && validId(value.versionSeguridad as string);
}
export function validId(value: string) { return /^\d{1,19}$/.test(value) && BigInt(value) > 0n && BigInt(value) <= 9223372036854775807n; }
export function isCategoria(value: unknown): value is Categoria {
 if (!record(value)) return false;
 return typeof value.id === 'string' && validId(value.id) && typeof value.nombre === 'string' && (value.descripcion === null || typeof value.descripcion === 'string');
}
export function isCliente(value: unknown): value is Cliente {
 if (!record(value)) return false;
 return ['id', 'nombre', 'documento', 'tipoDocumento'].every(k => typeof value[k] === 'string') && validId(value.id as string);
}
export function isProducto(value: unknown): value is Producto {
 if (!record(value)) return false;
 return typeof value.id === 'string' && validId(value.id) && typeof value.nombre === 'string'
  && (value.descripcion === null || typeof value.descripcion === 'string')
  && ['precio', 'stock', 'stockMinimo', 'categoriaId'].every(k => typeof value[k] === 'string');
}
function isDetalleVenta(value: unknown): value is DetalleVenta {
 if (!record(value)) return false;
 return ['id', 'productoId', 'productoNombre', 'cantidad', 'precioUnitario', 'subtotal'].every(k => typeof value[k] === 'string');
}
export function isVenta(value: unknown): value is Venta {
 if (!record(value)) return false;
 return ['id', 'fecha', 'clienteId', 'clienteNombre', 'clienteDocumento', 'vendedor', 'total'].every(k => typeof value[k] === 'string')
  && ['COMPLETADA', 'ANULADA'].includes(value.estado as string)
  && (value.detalles === null || (Array.isArray(value.detalles) && value.detalles.every(isDetalleVenta)));
}
export function decode(text: string): unknown { return parse(text, undefined, (value: string) => value); }
export function validateReport(query: ReportQuery): string | null {
 if (query.kind === 'stock-bajo') return /^\d+$/.test(query.umbral) && Number(query.umbral) <= 1000000 ? null : 'El umbral debe estar entre 0 y 1 000 000.';
 const date = (value: string) => /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value;
 if (!date(query.desde) || !date(query.hasta)) return 'Selecciona fechas válidas.';
 const days = (Date.parse(query.hasta) - Date.parse(query.desde)) / 86400000;
 if (days < 0 || days > 365) return 'El período debe abarcar entre 1 y 366 días inclusivos.';
 if (query.kind === 'productos-mas-vendidos' && (!/^\d+$/.test(query.top) || Number(query.top) < 1 || Number(query.top) > 100)) return 'El ranking debe tener entre 1 y 100 productos.';
 return null;
}
export function reportParams(query: ReportQuery, format: ReportFormat): string {
 const params = new URLSearchParams({ formato: format });
 if (query.kind === 'stock-bajo') params.set('umbral', query.umbral);
 else { params.set('desde', query.desde); params.set('hasta', query.hasta); if (query.kind === 'productos-mas-vendidos') params.set('top', query.top); }
 return params.toString();
}
function baseUrl(value: string) {
 if (value === '/api') return value;
 const url = new URL(value);
 if (url.username || url.password || url.search || url.hash || !url.pathname.endsWith('/api')
  || (url.protocol !== 'https:' && !(url.protocol === 'http:' && ['127.0.0.1', 'localhost'].includes(url.hostname)))) throw new Error('Base API no autorizada');
 return url.href.replace(/\/$/, '');
}
export class ApiClient {
 private token: string | null = null;
 private epoch = 0;
 private pending = new Set<string>();
 onExpired: (() => void) | undefined;
 private readonly base: string;
 constructor(base = import.meta.env.VITE_API_BASE_URL || '/api', private transport: typeof fetch = (...args) => fetch(...args), private timeout = 30000) { this.base = baseUrl(base); }
 setToken(token: string | null) { this.token = token; this.epoch++; }
 private async request(path: string, options: { method?: string; body?: unknown; public?: boolean; format?: ReportFormat } = {}): Promise<unknown> {
  const method = options.method ?? 'GET'; const key = `${method}:${path}`; const mutation = method !== 'GET';
  if (mutation && this.pending.has(key)) throw new ApiError(409, 'OPERACION_EN_CURSO');
  const epoch = this.epoch; const token = options.public ? null : this.token;
  if (!options.public && !token) throw new ApiError(401, 'AUTENTICACION_REQUERIDA');
  const controller = new AbortController(); const timer = setTimeout(() => controller.abort(), this.timeout);
  if (mutation) this.pending.add(key);
  try {
   const headers: Record<string, string> = { Accept: options.format === 'PDF' ? 'application/pdf' : options.format === 'XLSX' ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' : 'application/json' };
   if (token) headers.Authorization = `Bearer ${token}`;
   if (options.body !== undefined) headers['Content-Type'] = 'application/json';
   const response = await this.transport(`${this.base}${path}`, { method, headers, body: options.body === undefined ? undefined : stringify(options.body), signal: controller.signal, credentials: 'omit', cache: 'no-store', redirect: 'error', referrerPolicy: 'no-referrer' });
   if (!options.public && epoch !== this.epoch) throw new ApiError(401, 'SESION_CAMBIADA');
   if (!response.ok) {
    let code = '';
    try { const error = decode(await response.text()); if (record(error) && typeof error.code === 'string' && Object.hasOwn(messages, error.code)) code = error.code; } catch { /* respuesta desconocida nunca se muestra */ }
    if (response.status === 401 && !options.public && epoch === this.epoch) { this.setToken(null); this.onExpired?.(); }
    const retry = Number(response.headers.get('Retry-After'));
    throw new ApiError(response.status, code, retry >= 1 && retry <= 3600 ? retry : undefined);
   }
   if (response.status === 204) return undefined;
   const content = response.headers.get('Content-Type') ?? '';
   if (options.format && options.format !== 'JSON') {
    const expected = options.format === 'PDF' ? 'application/pdf' : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
    if (content.split(';')[0].trim() !== expected) throw new ApiError(503, 'RESPUESTA_INVALIDA');
    const blob = await response.blob(); if (blob.size === 0) throw new ApiError(503, 'RESPUESTA_INVALIDA');
    if (!options.public && epoch !== this.epoch) throw new ApiError(401, 'SESION_CAMBIADA');
    return blob;
   }
   if (!content.includes('application/json')) throw new ApiError(503, 'RESPUESTA_INVALIDA');
   const result = decode(await response.text());
   if (!options.public && epoch !== this.epoch) throw new ApiError(401, 'SESION_CAMBIADA');
   return result;
  } catch (error) { if (error instanceof ApiError) throw error; throw new ApiError(0, 'RED_NO_DISPONIBLE'); }
  finally { clearTimeout(timer); this.pending.delete(key); }
 }
 async login(login: string, password: string): Promise<LoginResult> {
  const result = await this.request('/auth/login', { method: 'POST', body: { login, password }, public: true });
  if (!record(result) || typeof result.accessToken !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(result.accessToken)
   || result.tokenType !== 'Bearer' || typeof result.expiresAt !== 'string' || !Number.isFinite(Date.parse(result.expiresAt)) || !isUser(result.usuario)) throw new ApiError(503, 'RESPUESTA_INVALIDA');
  return result as unknown as LoginResult;
 }
 async me(): Promise<User> { const result = await this.request('/auth/me'); if (!isUser(result)) throw new ApiError(503, 'RESPUESTA_INVALIDA'); return result; }
 async logout(): Promise<void> { await this.request('/auth/logout', { method: 'POST' }); }
 async users(page: number, size = 25): Promise<User[]> { const result = await this.request(`/usuarios?page=${page}&size=${size}`); if (!Array.isArray(result) || !result.every(isUser)) throw new ApiError(503, 'RESPUESTA_INVALIDA'); return result; }
 async user(id: string): Promise<User> { if (!validId(id)) throw new ApiError(404, ''); const result = await this.request(`/usuarios/${id}`); if (!isUser(result) || result.id !== id) throw new ApiError(503, 'RESPUESTA_INVALIDA'); return result; }
 async create(input: { login: string; nombre: string; password: string; rol: Role }): Promise<User> { const result = await this.request('/usuarios', { method: 'POST', body: input }); if (!isUser(result)) throw new ApiError(503, 'RESPUESTA_INVALIDA'); return result; }
 async change(user: User, action: 'nombre' | 'rol' | 'estado' | 'password', value: string): Promise<User> {
  if (!validId(user.id) || !validId(user.versionSeguridad)) throw new ApiError(400, '');
  const body: Record<string, unknown> = { versionEsperada: new LosslessNumber(user.versionSeguridad) };
  body[action] = value;
  if (action === 'nombre') body.actualizadoEnEsperado = user.actualizadoEn;
  const result = await this.request(`/usuarios/${user.id}${action === 'nombre' ? '' : `/${action}`}`, { method: 'PUT', body });
  if (!isUser(result) || result.id !== user.id) throw new ApiError(503, 'RESPUESTA_INVALIDA'); return result;
 }
 async report(query: ReportQuery): Promise<Report> {
  if (validateReport(query)) throw new ApiError(400, '');
  const result = await this.request(`/reportes/${query.kind}?${reportParams(query, 'JSON')}`);
  if (!record(result) || !Array.isArray(result.filas) || result.filas.length > 5000 || !result.filas.every(row => {
   if (!record(row)) return false;
   const fields = query.kind === 'ventas' ? ['id', 'fecha', 'vendedor', 'total', 'estado'] : query.kind === 'productos-mas-vendidos' ? ['productoId', 'sku', 'nombre', 'unidades', 'importe'] : ['productoId', 'sku', 'nombre', 'stock'];
   return fields.every(k => typeof row[k] === 'string' && (row[k] as string).length <= 1000)
    && (query.kind === 'ventas' ? validId(row.id as string) && /^-?\d+(\.\d+)?$/.test(row.total as string)
     : validId(row.productoId as string) && (query.kind === 'productos-mas-vendidos' ? /^-?\d+$/.test(row.unidades as string) && /^-?\d+(\.\d+)?$/.test(row.importe as string) : /^-?\d+$/.test(row.stock as string)));
  }) || (query.kind === 'productos-mas-vendidos' && result.filas.length > Number(query.top))) throw new ApiError(503, 'RESPUESTA_INVALIDA');
  return result as unknown as Report;
 }
 async productos(): Promise<Producto[]> {
  const result = await this.request('/productos');
  if (!Array.isArray(result) || !result.every(isProducto)) throw new ApiError(503, 'RESPUESTA_INVALIDA');
  return result;
 }
 async categorias(): Promise<Categoria[]> {
  const result = await this.request('/categorias');
  if (!Array.isArray(result) || !result.every(isCategoria)) throw new ApiError(503, 'RESPUESTA_INVALIDA');
  return result;
 }
 async clientes(): Promise<Cliente[]> {
  const result = await this.request('/clientes');
  if (!Array.isArray(result) || !result.every(isCliente)) throw new ApiError(503, 'RESPUESTA_INVALIDA');
  return result;
 }
 async crearCliente(input: { nombre: string; documento: string; tipoDocumento: string }): Promise<Cliente> {
  const result = await this.request('/clientes', { method: 'POST', body: input });
  if (!isCliente(result)) throw new ApiError(503, 'RESPUESTA_INVALIDA');
  return result;
 }
 async crearProducto(input: { nombre: string; descripcion: string; precio: string; stock: string; stockMinimo: string; categoriaId: string }): Promise<Producto> {
  const result = await this.request('/productos', { method: 'POST', body: input });
  if (!isProducto(result)) throw new ApiError(503, 'RESPUESTA_INVALIDA');
  return result;
 }
 async ventas(): Promise<Venta[]> {
  const result = await this.request('/ventas');
  if (!Array.isArray(result) || !result.every(isVenta)) throw new ApiError(503, 'RESPUESTA_INVALIDA');
  return result;
 }
 async venta(id: string): Promise<Venta> {
  if (!validId(id)) throw new ApiError(404, '');
  const result = await this.request(`/ventas/${id}`);
  if (!isVenta(result) || result.id !== id) throw new ApiError(503, 'RESPUESTA_INVALIDA');
  return result;
 }
 async registrarVenta(input: { clienteId: string; vendedor: string; items: ItemVenta[] }): Promise<Venta> {
  const result = await this.request('/ventas', { method: 'POST', body: input });
  if (!isVenta(result)) throw new ApiError(503, 'RESPUESTA_INVALIDA');
  return result;
 }
 async download(query: ReportQuery, format: 'PDF' | 'XLSX'): Promise<Blob> {
  if (validateReport(query)) throw new ApiError(400, '');
  return await this.request(`/reportes/${query.kind}?${reportParams(query, format)}`, { format }) as Blob;
 }
}
export function saveDownload(blob: Blob, kind: ReportQuery['kind'], format: 'PDF' | 'XLSX') {
 const url = URL.createObjectURL(blob); const anchor = document.createElement('a');
 anchor.href = url; anchor.download = `${kind}.${format.toLowerCase()}`; document.body.append(anchor); anchor.click(); anchor.remove();
 setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export const api = new ApiClient();
