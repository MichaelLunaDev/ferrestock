# FRONTEND-UX-01 — Rediseño UX/UI de FerreStock

Fecha: 21 de octubre de 2026. Rama: `feature/auth-reportes-service`.

## Alcance y diagnóstico inicial

Se inspeccionaron el README, `src/`, el cliente HTTP, el contexto de sesión, los estilos, las rutas, las pruebas y las dependencias. No se encontraron instrucciones AGENTS.md aplicables. React/TypeScript/Vite y HashRouter ya estaban implementados; no se añadió otra biblioteca ni una fuente remota.

| Pantalla | Diagnóstico | Resultado implementado |
| --- | --- | --- |
| Login | Identidad dividida válida; texto y controles secundarios pequeños; contraseña sin revelado | Composición más equilibrada, formulario de 420 px, marca compacta en móvil, mostrar/ocultar contraseña accesible |
| Vista general | Banner decorativo dominaba las acciones | Cabecera y CTA por rol, contexto compacto y accesos útiles sin métricas inventadas |
| Usuarios | Tabla con scroll horizontal en móvil y metadatos pequeños | Tabla legible en escritorio y tarjetas en móvil; cantidad de cuentas de la página, badges y paginación |
| Crear usuario | Formulario funcional con jerarquía limitada | Campos cómodos, selector Rol explícitamente etiquetado, ayuda de credenciales y error de coincidencia |
| Administrar cuenta | Resumen oculto en móvil; campos según operación ya existentes | Resumen visible, cambios pendientes, Guardar deshabilitado para datos incompletos o sin cambio, confirmación de seguridad |
| Reportes | Exportaciones accesibles antes de saber si había datos | Tabs adaptables, estados separados, 503 explícito y exportaciones condicionadas a una consulta con registros |
| Mi perfil | Una tarjeta extensa con poca diferenciación | Identidad, rol/permisos y seguridad agrupados; iniciales y fechas legibles |

## Decisiones y sistema de diseño

Se reutilizan `AuthProvider`, `ApiClient`, los guards por rol, las rutas y la validación de respuestas. Se refactorizan Layout, Dashboard, Profile, Login y los componentes visuales existentes. No se creó una abstracción para cada etiqueta HTML.

Tokens centralizados en `src/styles.css`:

- Marca `#173E32`, hover `#245941`, verde suave `#E7EDDC`, acento `#B5CB83`.
- Fondo `#F7F8F5`, superficie blanca, texto `#172B26`, secundario `#596B61`.
- Éxito, error y advertencia con fondos y textos diferenciados; estado siempre acompañado de texto.
- Espaciado 4/8/12/16/24/32/48 px, radios 8/12 px, altura de control 46 px, sidebar 248 px, contenido máximo 1400 px y transición de 150 ms.
- Segoe UI/Inter/system-ui local, cuerpo 15 px, etiquetas 14 px y metadatos 13 px. Títulos fluidos de 28–36 px.
- Breakpoints documentados junto al CSS: 600/900/1200 px. Se usan media queries literales porque las variables CSS no pueden sustituir sus condiciones de ancho.

Componentes comunes añadidos: `Icon` con SVG locales de trazo coherente, `PasswordInput`, `initials` y `roleLabel`. Se conservan Button mediante clases, Heading, Badge, Busy, Empty, ErrorNotice, Pagination, Confirm y `useAction`.

## UX, accesibilidad y seguridad

- Sidebar móvil con overlay, botón de cierre, Escape, cierre al navegar y al ampliar viewport, bloqueo de scroll y ciclo de foco. El contenido restante queda `inert` mientras está abierto.
- Diálogo de confirmación con descripción asociada, foco inicial en Cancelar, ciclo de Tab/Shift+Tab, Escape, restauración de foco, fondo `inert` y altura con scroll interno. Editar nombre sigue sin confirmación adicional.
- Campos etiquetados, `autocomplete` del login, contraseña borrada del estado antes de solicitar autenticación, bloqueo de doble envío y errores sanitizados existentes.
- Selector Rol accesible por label y rol combobox; los valores enviados siguen siendo ADMINISTRADOR/VENDEDOR/ALMACENERO.
- Tablas con caption, encabezados y scope. La tabla de usuarios conserva roles explícitos al transformarse visualmente en tarjetas.
- Foco visible, controles de 44–46 px como mínimo habitual, texto legible y respeto de `prefers-reduced-motion`.
- Reportes distingue consulta inicial, carga, datos, vacío, validación y error. Un 503 de contrato pendiente informa habilitación comercial pendiente y no representa cero ventas. No hay descargas ficticias. PDF/Excel requieren una consulta JSON válida con registros y se deshabilitan cuando cambian los filtros.
- No se modificaron tokens, persistencia, contratos HTTP ni autorización backend. Los guards siguen aplicando las restricciones de cada rol. No se incorporaron módulos comerciales inexistentes.

## Verificación ejecutada

Las pruebas de navegador usan exclusivamente API interceptada con fixtures de prueba. No constituyen integración SQL real y no crean cuentas reales.

Resultados de la última ejecución, sobre el código final:

| Comprobación | Resultado |
| --- | --- |
| `npm.cmd run typecheck` | PASS |
| `npm.cmd test` | PASS: 50 pruebas, 2 archivos; las 44 originales más 6 regresiones |
| `npm.cmd run build` | PASS: Vite genera el bundle; sin dependencias nuevas |
| `npm.cmd run test:browser` | PASS: 12 pruebas Chromium, sin reintentos automáticos |
| Matriz responsive | PASS: ocho vistas en cada uno de los siete anchos; sin overflow global |
| Foco, teclado, roles, etiquetas y movimiento reducido | PASS en las comprobaciones automatizadas descritas abajo |
| Contraste | PASS: ocho pares de texto ≥4.5:1; foco y borde de campo ≥3:1 |
| `git diff --check -- frontend` | PASS |

El baseline de navegador previo también pasó sus dos recorridos con API interceptada. En la ejecución final no hubo errores JavaScript ni errores de consola inesperados en la matriz; la respuesta 503 esperada se comprueba expresamente.

Se añadieron seis regresiones Vitest a las 44 existentes: revelado/borrado de contraseña, nombre accesible del selector Rol y valores HTTP, contraseña incompleta/no coincidente, ausencia de búsqueda local engañosa, habilitación de exportaciones solo tras consulta válida y explicación de 503 sin falso vacío. La regresión anterior de 503 ahora exige cero solicitudes PDF cuando el botón está deshabilitado.

Playwright utiliza un servidor propio en el puerto 5189 y no reutiliza ni detiene el entorno de trabajo en 5173. La matriz comprueba login, dashboard, usuarios, creación, administración, confirmación, perfil y reportes a 320/375/390/768/1024/1366/1440 px, sin overflow global. También comprueba menú, foco, teclado, etiquetas, idioma, botones con nombre, roles, storage vacío y errores JavaScript/consola; el error de recurso HTTP 503 esperado se distingue de errores inesperados. Incluye reflujo a 683 px equivalente al ancho de 1366 px con zoom del 200%, movimiento reducido y cálculo de contraste de diez combinaciones de texto/controles.

Los primeros intentos detectaron expectativas antiguas que intentaban pulsar exportaciones deshabilitadas y un defecto del cálculo de contraste con hexadecimales abreviados; se corrigieron las pruebas, preservando la política de no descarga. También se corrigió el texto de resultado paginado y la tipificación de una nueva consulta Testing Library.

## Capturas y revisión visual

Evidencias generadas localmente en `frontend/test-results/`, directorio ignorado por Git; solo contienen fixtures de prueba:

- Antes: `before-login-{desktop,mobile}.png`, `before-dashboard-{desktop,mobile}.png`, `before-users-{desktop,mobile}.png`, `before-report-unavailable-{desktop,mobile}.png`.
- Después: `ux-after-{login,dashboard,users,create,detail,confirmation,profile,reports-503}-{ancho}.png`, para los siete anchos. Hay además capturas del menú móvil.
- El recorrido original conserva capturas `after-…-{desktop,mobile}.png`.

Se revisaron comparaciones de dashboard y login, mosaicos de las ocho vistas en escritorio y móvil, y capturas individuales de usuarios a 320 px. El dashboard reduce el banner y adelanta las acciones; los usuarios móviles no necesitan desplazamiento horizontal; formularios, perfil y reportes comparten jerarquía y controles. No había capturas previas nuevas de creación, administración y perfil: se entrega revisión posterior sin inventar una comparación anterior.

Estas imágenes se regeneran al ejecutar Playwright y no se enlazan como archivos públicos versionados. Los mocks permanecen únicamente en tests.

## Funciones preservadas y límites

Login/logout, perfil, paginación, creación, cambio de nombre/rol/estado/contraseña, revocación reflejada mediante el contexto existente, 401/403/409 y protección del último administrador siguen cubiertos por las regresiones. No se repitieron las pruebas E2E reales mutadoras aceptadas previamente. La validación posterior al rediseño es offline y con API aislada.

La API de usuarios solo ofrece páginas de 25 cuentas sin filtros globales ni total: no se añadió búsqueda parcial ni un total engañoso. Siguiente se ofrece cuando la página está llena; una página final de exactamente 25 cuentas puede conducir a una página vacía porque no existe `hasNext` del servidor. Reportes reales sigue dependiendo del contrato de Ventas.

Las comprobaciones de contraste y DOM son acotadas: no equivalen a una certificación completa WCAG 2.2 AA. Quedan revisión humana con lector de pantalla, teclado virtual móvil y zoom nativo del navegador. Se comprobó reflujo equivalente, no el gesto de zoom nativo. No se verificaron Safari/Firefox ni se solicitaron credenciales reales.

## Cómo visualizar y reproducir

Desde `frontend/`, usar Node compatible y dependencias existentes:

```powershell
npm.cmd run typecheck
npm.cmd test
npm.cmd run build
npm.cmd run test:browser
```

Para la aplicación local, abrir `http://127.0.0.1:5173` si Vite ya está en marcha y recargar. Si no existe un proceso, ejecutar `npm.cmd run dev` desde `frontend/`. El login real requiere el backend y las credenciales existentes; no volver a ejecutar bootstrap o migraciones para probar estilos. Las pantallas con fixtures se revisan mediante Playwright; no hay un modo demo ni datos simulados en producción.

## Archivos modificados y Git

Cambios exclusivos de este incremento:

1. `frontend/src/App.tsx`
2. `frontend/src/ui.tsx`
3. `frontend/src/Users.tsx`
4. `frontend/src/Reports.tsx`
5. `frontend/src/styles.css`
6. `frontend/src/test/App.test.tsx`
7. `frontend/e2e/panel.spec.ts`
8. `frontend/e2e/ux.spec.ts` (nuevo)
9. `frontend/playwright.config.ts`
10. `frontend/docs/FRONTEND-UX-01-REPORT.md` (nuevo)

Sin cambios en dependencias, configuración del cliente HTTP o del proxy de la aplicación. No se hicieron staging, commit ni push. HEAD continúa en `7d280e32eea42d17664ae42605853fc0b56454c2`.

Se preservaron los cambios preexistentes del README raíz y los tres archivos locales de diagnóstico TLS del runner. No se modificó Ventas, Java, SQL, ACL, certificados, JKS ni `main`; no se ejecutaron migraciones, bootstrap, rotaciones, operaciones Docker o SQL.
