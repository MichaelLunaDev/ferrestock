export const roles = ['ADMINISTRADOR', 'VENDEDOR', 'ALMACENERO'] as const;
export type Role = typeof roles[number];
// Números JSON conservados como texto decimal para no perder bigint/BigDecimal.
export interface User { id: string; login: string; nombre: string; estado: 'ACTIVO' | 'INACTIVO'; rol: Role; versionSeguridad: string; creadoEn: string; actualizadoEn: string }
export interface LoginResult { accessToken: string; tokenType: 'Bearer'; expiresAt: string; usuario: User }
export interface Sale { id: string; fecha: string; vendedor: string; total: string; estado: string }
export interface TopProduct { productoId: string; sku: string; nombre: string; unidades: string; importe: string }
export interface Stock { productoId: string; sku: string; nombre: string; stock: string }
export type ReportKind = 'ventas' | 'productos-mas-vendidos' | 'stock-bajo';
export type ReportFormat = 'JSON' | 'PDF' | 'XLSX';
export interface ReportQuery { kind: ReportKind; desde: string; hasta: string; top: string; umbral: string }
export interface Report { filas: Array<Sale | TopProduct | Stock> }
export interface Categoria { id: string; nombre: string; descripcion: string | null }
export interface Cliente { id: string; nombre: string; documento: string; tipoDocumento: string }
export interface Producto { id: string; nombre: string; descripcion: string | null; precio: string; stock: string; stockMinimo: string; categoriaId: string }
export interface ItemVenta { productoId: string; cantidad: string }
export interface DetalleVenta { id: string; productoId: string; productoNombre: string; cantidad: string; precioUnitario: string; subtotal: string }
export interface Venta { id: string; fecha: string; clienteId: string; clienteNombre: string; clienteDocumento: string; vendedor: string; total: string; estado: 'COMPLETADA' | 'ANULADA'; detalles: DetalleVenta[] | null }
