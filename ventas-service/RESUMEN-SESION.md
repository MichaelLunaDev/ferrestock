# Resumen de sesión — ventas-service (primera versión)

Fecha: 2026-10-02 · Proyecto FerreStock (EFSRT, Cibertec) · Módulo de Ventas

## Qué se hizo

| Capa | Archivos (`src/main/java/pe/edu/cibertec/ferrestock/ventas/`) |
|---|---|
| Entidades JPA | `entity/Producto`, `Cliente`, `Venta`, `DetalleVenta`, `EstadoVenta` (COMPLETADA / ANULADA) |
| Repositorios | `repository/ProductoRepository`, `ClienteRepository`, `VentaRepository`, `DetalleVentaRepository` |
| Servicio | `service/VentaService` → `registrarVenta()`, `listar()`, `obtener(id)` |
| REST | `controller/VentaController` |
| DTOs | `dto/VentaRequest`, `ItemVentaRequest`, `VentaResponse`, `DetalleVentaResponse` |
| Errores | `exception/GlobalExceptionHandler` (409 stock, 404 no encontrado, 400 validación) |
| Datos demo | `config/DataSeeder` (CommandLineRunner: 10 productos + 3 clientes, **solo si `productos` está vacía**) |
| Config | `src/main/resources/application.yaml` (SQL Server con `DB_URL`, `DB_USER`, `DB_PASSWORD`) |
| Tests | `src/test/.../service/VentaServiceTest` (3 tests unitarios del servicio) |

### Cómo funciona `registrarVenta()`
Todo el método es **un solo `@Transactional`**:
1. Busca el cliente (404 si no existe).
2. Agrupa los items por producto (si un producto viene dos veces, suma las cantidades).
3. Carga los productos con **bloqueo pesimista** (`PESSIMISTIC_WRITE`) para que dos ventas simultáneas no vendan el mismo stock.
4. **Valida el stock de todos los productos antes de tocar nada.** Si alguno no alcanza → lanza `StockInsuficienteException` (HTTP 409) con la lista de faltantes; la transacción hace rollback y no se modifica nada.
5. Si todo es válido: descuenta stock, guarda el precio unitario del momento, calcula subtotales y total, y guarda la venta con sus detalles (cascade).

## Endpoints

| Método | URL | Descripción |
|---|---|---|
| GET | `/api/productos` | Lista productos (para armar la venta) |
| POST | `/api/ventas` | Registra una venta → 201, o 409 si falta stock |
| GET | `/api/ventas` | Lista ventas (sin detalle) |
| GET | `/api/ventas/{id}` | Venta con su detalle → 404 si no existe |

Cuerpo de `POST /api/ventas`:
```json
{
  "clienteId": 1,
  "vendedor": "jlopez",
  "items": [
    { "productoId": 1, "cantidad": 1 },
    { "productoId": 5, "cantidad": 10 }
  ]
}
```

## Cómo levantar la app

1. Tener SQL Server corriendo y crear la base (Hibernate crea las tablas, pero **no la base**):
   ```sql
   CREATE DATABASE ferrestock_ventas;
   ```
2. Definir variables de entorno (la contraseña no tiene valor por defecto):
   - PowerShell:
     ```powershell
     $env:DB_PASSWORD = "<tu_password>"
     # opcionales (estos son los defaults):
     $env:DB_USER = "sa"
     $env:DB_URL  = "jdbc:sqlserver://localhost:1433;databaseName=ferrestock_ventas;encrypt=true;trustServerCertificate=true"
     ```
   - Si usas SQL Server Express con instancia nombrada:
     `jdbc:sqlserver://localhost;instanceName=SQLEXPRESS;databaseName=ferrestock_ventas;encrypt=true;trustServerCertificate=true`
     (requiere TCP/IP habilitado y el servicio SQL Server Browser activo).
   - En IntelliJ: Run Configuration → *Environment variables*.
3. Desde `ventas-service/`:
   ```bash
   ./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
   ```
   En el log debe aparecer: `Datos de ejemplo cargados: 10 productos y 3 clientes`.

## Comandos de prueba (curl)

```bash
# 1) Ver productos cargados (probar esto primero)
curl http://localhost:8080/api/productos

# 2) Venta exitosa: 1 taladro + 10 cementos para Juan Pérez -> 201, total 617.90
curl -X POST http://localhost:8080/api/ventas -H "Content-Type: application/json" \
  -d '{"clienteId":1,"vendedor":"jlopez","items":[{"productoId":1,"cantidad":1},{"productoId":5,"cantidad":10}]}'

# 3) DEMO venta rechazada: 2 martillos (hay 40) + 5 rodillos (solo hay 2) -> 409
curl -X POST http://localhost:8080/api/ventas -H "Content-Type: application/json" \
  -d '{"clienteId":2,"vendedor":"jlopez","items":[{"productoId":3,"cantidad":2},{"productoId":10,"cantidad":5}]}'

# 4) Comprobar que NO se descontó nada: martillo (id 3) sigue en 40 y rodillo (id 10) en 2
curl http://localhost:8080/api/productos

# 5) Listar ventas y ver una con detalle
curl http://localhost:8080/api/ventas
curl http://localhost:8080/api/ventas/1
```

> En PowerShell usa `curl.exe` (no `curl`, que es alias de `Invoke-WebRequest`) y escapa las comillas del JSON,
> o simplemente usa Postman con *Body → raw → JSON*.

Productos con stock bajo pensados para la demo: **Arena gruesa (id 6, stock 3)** y **Rodillo de felpa (id 10, stock 2)**.

Respuesta esperada del caso rechazado:
```json
{
  "status": 409,
  "title": "Venta rechazada",
  "detail": "Stock insuficiente. La venta fue rechazada y no se modificó ningún producto.",
  "faltantes": ["Rodillo de felpa 9 pulg. con bandeja (id 10): solicitado 5, disponible 2"]
}
```

## Verificación realizada
- `./mvnw test -Dtest=VentaServiceTest` → 3 tests OK (venta exitosa, rechazo sin tocar stock, cantidades repetidas).
- Prueba de punta a punta contra un SQL Server 2022 temporal en Docker: arranque + seed, venta 201,
  venta rechazada 409 con stock intacto, listado, detalle, 404 y 400 de validación. Contenedor eliminado al terminar.

## Pendiente / notas
- El test `FerrestockVentasApplicationTests.contextLoads` (generado por Spring Initializr) necesita una BD
  accesible; sin SQL Server corriendo, `./mvnw test` completo falla en ese test.
- Seguridad (Spring Security) y anulación de ventas (estado ANULADA + devolución de stock) quedan para siguientes iteraciones.
- `vendedor` es un String simple por ahora.
