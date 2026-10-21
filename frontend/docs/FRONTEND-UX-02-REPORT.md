# FRONTEND-UX-02 — Pulido visual y de usabilidad

Fecha: 21 de octubre de 2026. Rama: `feature/auth-reportes-service`.

## Alcance

Iteración sobre FRONTEND-UX-01, sin rehacer la arquitectura ni añadir dependencias. Se inspeccionaron los componentes reales, los tokens de `styles.css`, los formularios, la sesión y las pruebas. Se reutilizan Heading, Badge, Icon, PasswordInput, Pagination, Confirm, ErrorNotice y las clases de botones/paneles.

Se preservaron todos los cambios previos del frontend y los cambios locales ajenos al encargo. No se modificaron backend, SQL, migraciones, Ventas, contratos HTTP, almacenamiento de tokens, README raíz o main. No se utilizaron credenciales reales ni se ejecutaron operaciones SQL, bootstrap o pruebas mutadoras reales.

## Decisiones y mejoras

| Vista/componente | Pulido aplicado |
| --- | --- |
| Login | Espaciado entre ayuda y CTA, altura y feedback de botón principal, equilibrio vertical y arco decorativo discreto en el panel institucional |
| Sistema visual | Tokens de superficie secundaria, borde interactivo y sombras de foco/hover; botones deshabilitados legibles y diferenciados; estados pressed y focus |
| Cabeceras | CTA alineado con el encabezado en escritorio y ancho completo en móvil; breadcrumb con alineación vertical uniforme |
| Dashboard | Tarjetas de altura consistente, CTA textual por destino, hover/foco compartido y resumen de acceso más escaneable |
| Usuarios desktop | Separación más clara de encabezados/metadatos, login secundario y acción Administrar con control visible e icono SVG común |
| Usuarios móvil | Tarjetas separadas, identidad diferenciada de datos secundarios, acción de ancho completo y paginación con dos controles amplios |
| Perfil | Identidad con acento de marca; metadatos y secciones de permisos/seguridad delimitadas; información de recuperación con tono neutro |
| Reportes | Filtros agrupados sobre superficie secundaria, fechas apiladas en móvil, grupo de exportación etiquetado y un único aviso ante contrato comercial pendiente |

No se añadieron KPIs numéricos de usuarios o reportes: no existe un contrato global aprobado para esos datos. El resumen muestra únicamente cuenta, estado de sesión y vencimiento ya disponibles en memoria. No realiza solicitudes adicionales ni presenta mocks en producción.

Los roles usan una variante `neutral` de Badge; los estados Activo/Inactivo mantienen sus etiquetas y tratamiento propio. No se interpreta el rol como un estado de éxito.

En Reportes, el 503 sigue siendo un fallo de la operación real. La vista conserva el mensaje sanitizado del cliente HTTP, agrega la explicación de dependencia y lo anuncia mediante un solo `role="alert"`. Los otros errores siguen usando ErrorNotice. No se transforma un error en un conjunto vacío ni se habilitan PDF/XLSX sin una consulta válida con registros.

## Componentes refinados

- Badge: variante neutra reutilizable, compatible con la variante muted existente.
- QuickCard: enlace existente con texto de acción explícito; no introduce botones anidados ni acciones nuevas.
- Resumen de sesión: datos existentes agrupados, vencimiento con elemento `time` y vínculo al perfil.
- Controles compartidos: tokens de foco, bordes, hover, pressed y disabled sin cambiar envío o validación.
- Users: se conserva la tabla semántica que se transforma en tarjetas; acción Administrar usa Icon.
- Reports: estado de dependencia consolidado y exportación con nombre de grupo accesible.

## Validaciones

Resultado real de la ejecución final:

| Comprobación | Resultado |
| --- | --- |
| `npm.cmd run typecheck` | PASS |
| `npm.cmd test` | PASS: 52 pruebas en 2 archivos |
| `npm.cmd run build` | PASS |
| `npm.cmd run test:browser` | PASS: 12 pruebas Chromium |
| Responsive | PASS a los siete anchos de la matriz; sin overflow global |
| Contraste y teclado | PASS en las comprobaciones automatizadas existentes |
| `git diff --check -- frontend` | PASS |

Las pruebas de navegador no detectaron errores JavaScript ni errores de consola inesperados en la matriz. El recurso HTTP 503 esperado se comprueba por separado.

Se agregaron dos regresiones Vitest: el resumen usa exclusivamente la sesión y no consulta usuarios/reportes desde el dashboard; el 503 muestra un solo aviso de dependencia y mantiene las exportaciones deshabilitadas. Se mantienen las 50 pruebas del incremento anterior.

La suite Playwright existente comprueba los recorridos desktop/móvil, las ocho vistas a 320/375/390/768/1024/1366/1440 px, foco, Escape, labels, roles, paginación, no overflow global, movimiento reducido y diez pares de contraste. Usa API interceptada exclusivamente dentro de pruebas y servidor propio en 5189. No constituye integración SQL real.

## Capturas y revisión visual

Las seis capturas seleccionadas del estado anterior se conservaron en `frontend/test-results/ux02-before/` antes de ejecutar las pruebas. Las nuevas se regeneran en `frontend/test-results/`, ignorado por Git; contienen solo fixtures de prueba.

Capturas de referencia sugeridas para la revisión del responsable:

1. `ux-after-login-1440.png`
2. `ux-after-dashboard-1440.png`
3. `ux-after-users-1440.png`
4. `ux-after-users-390.png`
5. `ux-after-profile-1440.png`
6. `ux-after-reports-503-390.png`

Las seis capturas enumeradas fueron abiertas e inspeccionadas visualmente después de la ejecución final: controles legibles, CTA de tarjetas alineados, acciones de usuarios accesibles, filtros apilados en móvil y aviso comercial único. Además, la matriz genera cada vista a los siete anchos, incluidas creación/administración y confirmación de seguridad. No se publican imágenes con cuentas reales ni evidencias privadas.

## Archivos de este incremento

1. `frontend/src/App.tsx`
2. `frontend/src/Users.tsx`
3. `frontend/src/Reports.tsx`
4. `frontend/src/ui.tsx`
5. `frontend/src/styles.css`
6. `frontend/src/test/App.test.tsx`
7. `frontend/docs/FRONTEND-UX-02-REPORT.md` (nuevo)

Las modificaciones previas de Playwright y el informe UX-01 se preservan; no son cambios nuevos de UX-02. No se modificó package.json ni package-lock.json.

## Visualización y pendientes

Abrir `http://127.0.0.1:5173` y recargar si Vite ya está activo. Si no, ejecutar `npm.cmd run dev` desde `frontend/`. Para regenerar capturas aisladas: `npm.cmd run test:browser`. No se necesita volver a crear usuarios, ejecutar migraciones o bootstrap.

Pendientes: habilitación comercial y contrato de Ventas, soporte backend de filtros globales/total de usuarios y revisión humana con lector de pantalla, zoom nativo y teclado móvil. Las comprobaciones automatizadas no certifican por sí solas WCAG AA ni prueban la integración SQL real. No se añadieron flujos de recuperación de contraseña ni operaciones sin contrato.

Sin staging, commit ni push. HEAD se conserva en `7d280e32eea42d17664ae42605853fc0b56454c2`. README raíz mantiene su modificación previa; Ventas no tiene cambios.

## Cierre FRONTEND-UX-02A — Reportes y preparación de publicación

### Causa y alcance de la corrección

La captura anterior a UX-02, conservada en `test-results/ux02-before/ux-after-reports-503-390.png`, muestra los dos paneles: el error genérico y la explicación comercial. En UX-01 se renderizaban ErrorNotice y el aviso condicionado por `unavailable` de forma independiente. La prueba verificaba el texto del error, pero no la cantidad de paneles visuales; contar solamente `role="alert"` tampoco detectaba el aviso adicional sin ese rol.

Al iniciar 02A, el código real de UX-02 ya tenía una rama excluyente entre el aviso comercial y ErrorNotice. No se reprodujo la duplicación en esa versión ni se atribuye sin evidencia a caché o al backend. Esta corrección puntual mantiene esa exclusión y sustituye el contenido por un solo título y párrafo:

> **Reportes pendientes de habilitación**
>
> Los reportes comerciales estarán disponibles cuando se complete la integración con Ventas. Esto no significa que no existan ventas o productos.

La condición continúa siendo exactamente `ApiError`, HTTP 503 y código `CONTRATO_COMERCIAL_PENDIENTE`. No se modifica el cliente HTTP ni el contrato backend. Se reutiliza el estilo informativo ámbar existente sin nuevo rediseño. PDF/Excel continúan deshabilitados hasta una consulta válida con registros.

### Regresiones y evidencias

- La regresión existente exige un solo panel `.notice` dentro del panel del reporte, ningún aviso genérico y cero peticiones PDF/XLSX.
- Playwright exige exactamente un panel visible (`.notice:visible`), un aviso comercial y ningún panel danger, además de una única petición JSON. Se comprueba en los siete anchos; no se limita a contar atributos ARIA.
- Se agregan regresiones para 400, 403, 500 y 503 sin código comercial, fallo de red, 401 y validación local. Estos errores conservan sus respuestas correspondientes y no se reclasifican como habilitación pendiente.
- Un recorrido nuevo consulta datos válidos y recibe las dos descargas PDF/XLSX mediante API interceptada. Los archivos de prueba son fixtures para verificar el flujo HTTP/UI; no son exportaciones comerciales reales ni una validación de sus formatos internos.
- Las capturas finales `test-results/ux-after-reports-503-390.png` y `test-results/ux-after-reports-503-1440.png` fueron regeneradas y abiertas para revisión visual: un solo panel informativo en ambas, sin texto genérico ni explicación duplicada. Se conservan localmente, fuera del staging.

Resultados finales de 02A:

| Comprobación | Resultado |
| --- | --- |
| Typecheck | PASS |
| Vitest, `npm.cmd test -- --maxWorkers=1` | PASS: 59 pruebas en 2 archivos, sin fallos |
| Build | PASS |
| Playwright | PASS: 13 pruebas, API aislada |
| Reportes 390/1440 px | Un único panel visible, inspección de ambas capturas y aserciones DOM |
| `git diff --cached --check` | PASS sobre el staging final |

El primer `npm.cmd test` no pudo iniciar sus dos workers y no ejecutó pruebas; no se cuenta como PASS. La repetición con un solo worker ejecutó la suite completa sin cambiar configuración ni reducir cobertura. No hubo E2E contra SQL, conexiones nuevas al backend real, migraciones ni operaciones administrativas.

### Cambios propios de 02A

`src/Reports.tsx`, `src/test/App.test.tsx`, `e2e/panel.spec.ts`, `e2e/ux.spec.ts` y esta sección. Además, `frontend/README.md` corrige la ruta de evidencias a `frontend/test-results/` y enlaza los informes de UX que se preparan para publicación. No se cambiaron otras pantallas en este incremento.

### Staging acumulado de UX-01, UX-02 y UX-02A

Staging final: 12 archivos, exclusivamente frontend publicable:

1. `frontend/README.md`
2. `frontend/docs/FRONTEND-UX-01-REPORT.md` (nuevo)
3. `frontend/docs/FRONTEND-UX-02-REPORT.md` (nuevo)
4. `frontend/e2e/panel.spec.ts`
5. `frontend/e2e/ux.spec.ts` (nuevo)
6. `frontend/playwright.config.ts`
7. `frontend/src/App.tsx`
8. `frontend/src/Reports.tsx`
9. `frontend/src/Users.tsx`
10. `frontend/src/styles.css`
11. `frontend/src/test/App.test.tsx`
12. `frontend/src/ui.tsx`

Se revisaron los doce archivos por alcance, referencias y exposición pública. No se detectaron secretos reales, claves privadas ni rutas del perfil personal. Las credenciales y datos que aparecen en pruebas son fixtures explícitos. Capturas, node_modules, dist y resultados de pruebas siguen ignorados; no forman parte de esta lista. Los informes de UX contienen documentación técnica y resultados offline, sin inventarios administrativos privados.

Los cambios locales preexistentes del README raíz y de los tres archivos de diagnóstico TLS del runner se conservan fuera del staging. Ventas no tiene cambios. No hay staging de Auth, material TLS, SQL ni otros módulos.

Commit propuesto, **no ejecutado**: `feat(frontend): modernizar experiencia UX/UI de FerreStock`.

La publicación queda pendiente de aprobación del responsable. No se ejecutaron commit ni push y HEAD continúa en `7d280e32eea42d17664ae42605853fc0b56454c2`.
