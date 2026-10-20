# FerreStock — Provisioning y verificacion SQL

Estado actualizado al 2026-10-17: [F2A-VERIFY-014](F2A-VERIFY-014.md).
El responsable ejecuto y confirmo Preflight, Logins, AuthDev, AuthTest y Reports.
El inventario administrativo real y las 54 condiciones son conformes.
**F2A PENDIENTE DE PRUEBAS INTERACTIVAS:** falta demostrar las doce conexiones
de la matriz con las credenciales reales de las cuatro cuentas.

Este incremento autoriza solo verificacion. No repetir aprovisionamiento,
migraciones ni concesiones de objetos. Los informes previos se conservan como
historial; sus estados anteriores no describen el inventario vigente.
Solo se modifica esta carpeta; Java, dependencias y configuracion F1 intactos.

## Estado contrastado en solo lectura

SQL Server 2022 Developer 16.0.4250.1; contenedor ferrestock-mssql activo,
publicación exclusiva 127.0.0.1:14333. MachineName y ServerName coinciden con
Config.Hostname consultado en Docker. Ese identificador efímero NO se guarda aquí.
Las tres bases estan ONLINE y sin tablas/objetos comerciales. Los cuatro logins
existen, estan habilitados, tienen la base predeterminada prevista y no pertenecen
a roles de servidor. Solo tienen GRANT CONNECT SQL directo en el servidor.
AuthDev tiene cuatro usuarios propios y 17 ACL; AuthTest, dos usuarios y 7 ACL;
Ventas, un usuario propio y 10 ACL. Las 34 ACL coinciden exactamente con la politica,
sin grant option ni permisos por columna. Los SID corresponden a los logins;
ambos esquemas auth pertenecen a auth_owner SQL_USER/NONE sin login.
TRUSTWORTHY y DB_CHAINING están desactivados en las tres bases.
La auditoria administrativa no sustituye las conexiones con cada identidad.

## Completar la verificacion interactiva

Desde esta carpeta, el responsable ejecutara unicamente:

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File ".\Verify-AccessMatrix.ps1" -TrustLocalDevelopmentCertificate
```

La excepcion de certificado esta autorizada solo para este Docker local. Sin
esa opcion se valida el certificado TLS; el cifrado permanece activo siempre.
El script pide las cuatro credenciales mediante Get-Credential. No recibir ni
guardar contrasenas en argumentos, archivos o variables de entorno. No usa sa,
EXECUTE AS, Logins/AuthDev/AuthTest/Reports ni otra etapa del helper.

Primero valida el login en su base asignada, ORIGINAL_LOGIN, USER_NAME, SID e
identidad del servidor. Comprueba privilegios efectivos de servidor/base,
DDL, escritura/SELECT globales y ALTER de todos los esquemas, y ejecuta el
archivo revisado 90-effective-permissions.sql. Despues intenta las otras dos
bases con la misma credencial. Maximo 12 aperturas, sin pooling ni reintentos
automaticos. Si no demuestra la conexion propia, no prueba las ajenas.

Un rechazo solo obtiene PASS cuando aparece 4060, la conexion propia ya fue
validada y la base solicitada se confirma ONLINE antes y despues. Puede ir
acompanado de 18456; 18456 solo, un error TLS, timeout, base no confirmada o
permiso NULL **no son prueba de aislamiento**. Se devuelve PENDIENTE o FAIL,
nunca PASS por el mero hecho de fallar una conexion. El script no repara nada.

La salida es JSON con las doce combinaciones, controles efectivos, numero de
aperturas y codigos SQL sanitizados. No imprime SQL, cadenas de conexion ni
mensajes originales de excepciones. Si hay FAIL/PENDIENTE, termina con error
sanitizado despues del informe. El JSON puede conservarse como evidencia,
sin credenciales. Una matriz PASS debe contrastarse con el inventario
administrativo contemporaneo antes de cerrar F2A.

Los controles SELECT/DML sobre objetos comerciales son NO APLICA mientras no
existan objetos aprobados. Eso no equivale a haber probado escritura denegada.
La herramienta es especifica de F2A y rechaza SQL mutador en 90; cambios futuros
en ese archivo requieren revisar tambien la herramienta. No es un parser SQL general.

## Matriz aprobada

| Cuenta | ferrestock_auth_dev | ferrestock_auth_test | ferrestock_ventas |
|---|---|---|---|
| ferrestock_auth_app | CONNECT; DML futuro por objeto | Sin acceso de aplicación | Sin acceso |
| ferrestock_auth_migrator | CREATE TABLE; ALTER/REFERENCES/DML/VIEW DEFINITION en auth | Sin acceso de aplicación | Sin acceso |
| ferrestock_auth_test_app | DENY CONNECT | CONNECT; DML futuro por objeto | Sin acceso |
| ferrestock_report_reader | Sin acceso | Sin acceso | CONNECT; SELECT futuro por objeto |

No se otorgan roles de servidor, db_owner, db_datareader, db_datawriter ni db_ddladmin.
El migrador puede alterar/eliminar objetos de auth: no es credencial de aplicación.
No se concede EXECUTE a las aplicaciones. El historial migrador no se concede a auth_app.
La cuenta test no crea esquemas ni ejecuta migraciones; falta acordar su administrador.

## Un solo ejecutor de aprovisionamiento (referencia)

New-Logins.ps1 conserva su nombre, pero ahora selecciona una etapa fija con -Stage.
No ejecutar 10/20/21/30/40/41 directamente desde SSMS o sqlcmd. Requieren variables
y preflight proporcionados por el helper; no tienen GO. No concatenar etapas manualmente.

Sin -Apply el helper retorna ANTES de Docker, lectura de scripts, credenciales y SQL.
Cada llamada abre una conexión física nueva (sin pooling), ejecuta 05-preflight.sql
y una sola etapa como un lote ADO.NET. Los archivos nunca se interpolan con secretos.

La cuenta administrativa requiere sysadmin para visibilidad completa del preflight
local. Ese requisito es del operador, no una concesión a las cuatro cuentas nuevas.

## Controles H1/H2: política cerrada, no reconciliación

05-preflight.sql restringe cuatro logins y tres bases. Compara:
- Tipo, SID de 16 bytes y coherencia de mapeos, habilitación, base e idioma
  predeterminados, CHECK_POLICY=ON, CHECK_EXPIRATION=OFF, ausencia de bloqueo/MUST_CHANGE.
- Sin credencial asociada, roles de servidor, propiedad de bases ni permisos directos
  distintos de CONNECT SQL concedido sin grant option; DENY CONNECT SQL es incompatible.
- Mapeos solo en las bases previstas, sin alias ni roles de base.
- ACL directas exactas por base/principal/clase/permiso/estado, sin permisos por columna.
- public del servidor: VIEW ANY DATABASE/CONNECT SQL y CONNECT a endpoints TSQL.
- public de bases: SELECT estándar sobre objetos de sistema y visibilidad de claves
  de cifrado; no concesiones sobre objetos de usuario. guest no puede tener CONNECT.
- Permisos sobre las identidades (incluida suplantación), propietarios de esquemas,
  objetos y roles, módulos EXECUTE AS, CLR y firmas.

auth_owner debe ser usuario sin login y sin roles, propietario solo de auth.
En ferrestock_auth_dev y ferrestock_auth_test se admite exclusivamente su ACL directa DATABASE
GRANT CONNECT (major_id=minor_id=0), generada por CREATE USER WITHOUT LOGIN.
Los demas permisos directos siguen bloqueados. Ningún objeto puede asignarle
propietario explícito. Las tablas
aprobadas heredarán propietario del esquema. Un propietario inesperado causa bloqueo.

La allowlist de objetos Auth (@F2AuthObjects) y de permisos por objeto (@F2Acl) está
VACÍA para objetos de negocio en F2A. Se admiten únicamente las ACL de aprovisionamiento.
Cuando existan migraciones, revisar y agregar nombres/tipos y ACL concretas en 05,
listas de 40/41 y verificaciones de 90, de forma coherente. No autodescubrir ni conceder
permisos a todo un esquema de aplicación. Los triggers Auth requieren revisión explícita.

No se revoca ni repara deriva automáticamente. Un DENY previo no aprobado también
bloquea: no se permite que un GRANT de este procedimiento lo sustituya silenciosamente.
Los controles protegen frente a errores operativos; no frente a un administrador
malicioso que cambie los scripts o la infraestructura. No es un motor general de permisos.

## H3: identidad de destino

El helper inspecciona solo State.Running, NetworkMode, Ports y Config.Hostname de
ferrestock-mssql; nunca Config.Env. Exige una sola publicación, 127.0.0.1:14333 para
1433/tcp, y rechaza red host. Lee el hostname en cada conexión y lo compara por
parámetros con MachineName/ServerName SQL; exige instancia predeterminada.

SQL exige versión 16.0.4250.1 y edición Developer Edition (64-bit), las tres bases
ONLINE, TRUSTWORTHY/DB_CHAINING apagados y encadenamiento global desactivado.
Un cambio de versión requiere revisión del pin, no aceptar cualquier versión.
Se valida DB_NAME() contra la base seleccionada por una tabla fija de etapas.

Encrypt=true y validación de certificado por defecto. La opción
-TrustLocalDevelopmentCertificate solo omite esa validación para el destino local
fijo; no utilizarla como configuración de Azure/producción. Docker es parte de la
frontera de confianza; no se protege contra reconfiguración maliciosa concurrente.

## H4/H5: secretos y reejecución

ASCII permitido exactamente: A-Z, a-z, 0-9 y !@#$%^&*_-+=
Longitud 16–128; exigir al menos una mayúscula, una minúscula, un dígito y un símbolo.
No espacios, saltos, puntos, comillas ni Unicode; tampoco el nombre completo del login.
PowerShell y T-SQL aplican la misma política. SQL conserva CHECK_POLICY=ON y puede
rechazar adicionalmente una contraseña: nunca se relaja automáticamente.

El helper primero valida cada login en una conexión nueva:
- NUEVO: solicita un secreto por Read-Host -AsSecureString y vuelve a validar al crear.
- EXISTENTE VALIDADO: no solicita contraseña y no la rota.
- BLOQUEADO: error sanitizado con el nombre de etapa, sin parámetros ni excepción SQL.

LoginPassword es parámetro nvarchar(256) para detectar entradas >128 sin truncarlas
antes de validar. CREATE LOGIN necesita un literal, construido en SQL con QUOTENAME
solo después de validar ASCII y longitud; identificadores vienen de la lista cerrada.
No almacenar secretos para reejecuciones. Usar gestor de secretos independiente.

Get-Credential/SqlCredential protegen la entrada administrativa. Recursos, comandos,
parámetros y buffers se liberan en finally. Una copia temporal en memoria administrada
es inevitable: .NET NO garantiza borrarla inmediatamente. No activar transcripciones,
depuración de parámetros, volcados ni capturas RPC; el catch no imprime ErrorRecord.
El helper no pretende impedir acceso a memoria por administradores o herramientas externas.

## H8: transacciones y fallos

Se exige @@TRANCOUNT=0 y XACT_STATE()=0. Cada mutador usa XACT_ABORT, TRY/CATCH,
COMMIT y ROLLBACK si XACT_STATE() no es cero. El helper cierra conexiones sin pooling
incluso si la compilación falla: los errores de compilación no están todos cubiertos
por TRY/CATCH/XACT_ABORT. No ejecutar fragmentos en una sesión interactiva reutilizada.

Un error detiene inmediatamente la etapa/secuencia. No continuar, no elevar permisos.
Cada login se confirma separadamente: pueden quedar logins previos creados.
Cada etapa SQL tiene su transacción. Ante fallo de validación posterior, detenerse
y revisar lo ya confirmado; no borrar/rotar/revocar como recuperación automática.

Reejecución: valida estado existente, mantiene SID y contraseña, crea solo lo ausente
y reaplica únicamente las concesiones exactas. SID no se fija previamente en Git:
se valida su formato y correspondencia con TODOS los mapeos inspeccionados; no prueba
historia de identidad si se elimina/recrea un login sin conservar evidencia externa.

## Historial y punto actual de la secuencia

Preflight, cuatro Logins, AuthDev, AuthTest y Reports fueron ejecutados y
validados por el responsable. No repetirlos en VERIFY-014. Las correcciones
y evidencias previas permanecen en F2A-STABILIZE-008, F2A-APPLY-010-FIX y
F2A-PRECHECK-011. La auditoria actual se conserva en evidence/F2A-VERIFY-014-*.

Falta ejecutar Verify-AccessMatrix.ps1 con CADA identidad real para completar
90 y los intentos de conexion a las tres bases. AuthGrantsDev, AuthGrantsTest
y ReportGrants siguen prohibidos: las listas 40/41 estan vacias y requieren
revision expresa de objetos futuros. No agregar permisos para superar errores.

## H6: qué demuestra cada comprobación

90 compara acceso a las tres bases usando VALUES explícitos (no solo filas visibles
en sys.databases), identidad y permisos DDL. Una discrepancia o NULL bloquea esa comprobación.
Muestra procedencia DIRECTO/HEREDADO de las ACL visibles (incluido public), DENY/GRANT,
permisos por columna y propietarios efectivos visibles.

Para objetos, la lista aprobada se debe contrastar con el inventario del administrador:
- 1: permiso efectivo concedido.
- 0: sin permiso efectivo; no demuestra por sí solo un DENY explícito.
- NULL: indeterminado, falta de visibilidad o nombre no válido.
- OBJECT_ID NULL: inexistente O no visible; decidir con 00 administrativo.
- Una ACL DENY/GRANT directa y un permiso efectivo no son conceptos equivalentes.
- Propiedad, roles, firmas y EXECUTE AS requieren además el preflight administrativo.

Con lista de objetos vacía, 90 informa que la comprobación por objeto está pendiente.
No considera ausencia de filas prueba de aislamiento. Las cuatro cuentas ya
existen; sus conexiones y permisos efectivos aun requieren la prueba interactiva.

Después de crear tablas: probar DML solo en fixture aislado de auth_test, con cuenta
test y rollback; nunca escribir en ventas/dev para probar denegaciones. Las conexiones
con contraseña incorrecta deben rechazarse; prueba futura de un solo intento por cuenta.
Las vistas comerciales requieren revisión de definición/dependencias, no solo existencia.

## Spring Boot, sin cambios

Futuras variables: AUTH_DB_URL/USER/PASSWORD, REPORT_DB_URL/USER/PASSWORD y
AUTH_TEST_DB_URL/USER/PASSWORD. Test debe exigir base auth_test sin fallback a dev.
F1 conserva sus 14 pruebas sin datasource; no se añadieron conexiones ni dependencias.
La estrategia de sesiones opacas continúa pendiente; no se implementa F2B.

## Validacion de VERIFY-014

Ejecutar .\Test-Static.ps1: parsea PowerShell, ejecuta 8 variantes sin -Apply con
interceptores que fallarían si se invocara Docker/Get-Credential, y comprueba barreras
textuales de transacción y listas vacías. No conecta a SQL ni escribe archivos.
Test-Diagnostics.ps1 y Test-SqlPreparation.ps1 siguen siendo pruebas locales.
Test-AccessMatrixUnit.ps1 prueba el verificador con ADO.NET cerrado, errores
SQL simulados y seis flujos sin red. No constituye evidencia de acceso real.
Las consultas SQL administrativas actuales y Get-PreflightAuditSql.ps1 si
se ejecutaron: 54 condiciones conformes y 34 ACL exactas. El informe separa
estos resultados de las pruebas pendientes con cada login.

Si la política local impide ejecutar el test, se verificó con un proceso aislado:
`powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Test-Static.ps1`.
No se cambió la política persistente de Windows. Esta invocación solo prueba las
rutas sin -Apply; no constituye autorización para ejecutar aprovisionamiento.

## Historial F2A-DIAG-005: diagnostico sanitizado

El helper conserva el bloqueo y ahora informa etapa, fase y comprobacion
Inicial/Creacion/Posterior. Las fases separan DockerEstado/Red/Puertos/Identidad,
Credencial, PreparacionSQL, LecturaSQL, PoliticaSecreto, ConexionSQL,
EjecucionSQL y Resultado. Posterior identifica la segunda comprobacion en
conexion nueva; no implica que la primera operacion se haya revertido.

Para SqlException (incluso envuelta por PowerShell) solo se muestran Number,
Class (severidad) y State. No se imprime Message, SQL, parametros, servidor,
cadena de conexion ni la excepcion original. La excepcion final es nueva y
no adjunta InnerException. Las etiquetas de etapa/fase son listas cerradas.

Origen se determina por fase y tipos/codigos conocidos, nunca por textos:
- AutenticacionSQL: 18452/18456/18470/18486/18487/18488.
- PermisosAdministrativos: 51101; IdentidadSQL: 51102.
- CompilacionTSQL: 102/105/156/207/208/209/2812/4104 durante ejecucion.
- PermisosSQL: 229/230/262/297/300/916 durante ejecucion.
- Otros errores de ejecucion: TSQL, conservando su numero exacto.
- ConexionTLS: AuthenticationException en apertura SQL o Win32Exception
  interna con codigo de certificado conocido (raiz, nombre o vigencia).
  Si el proveedor no entrega esa evidencia tipada, se conserva ConexionSQL:
  no afirmar TLS por el mensaje. Un timeout tampoco prueba su causa de red.

Ejemplo: BLOQUEADO: Preflight | Fase: EjecucionSQL | Comprobacion: Inicial |
Origen: TSQL | Error SQL: 51103 | Severidad: 16 | Estado: 1
El codigo 51103 corresponde al pin de version/edicion del preflight;
este ejemplo NO identifica el bloqueo real observado anteriormente.

Pruebas sin conexiones, desde esta carpeta:
```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\Test-Static.ps1
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\Test-Diagnostics.ps1
```
Test-Diagnostics crea SqlException simuladas en memoria mediante reflection
exclusivamente en tests; reemplaza Invoke-LocalStage en memoria para comprobar
la orquestacion sin construir conexiones. No usa credenciales reales.
F2A-DIAG-005 no repite el preflight real ni determina aun la causa SQL original.

Para que el responsable repita SOLO Preflight con validacion TLS por defecto:
```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\New-Logins.ps1 -Apply -Stage Preflight
```
La variante local utilizada manualmente agrega -TrustLocalDevelopmentCertificate
solo cuando el responsable acepta expresamente esa excepcion de certificado.
No cambiar los controles de 05-preflight.sql para superar un bloqueo.

## Referencias oficiales

- [CREATE LOGIN](https://learn.microsoft.com/en-us/sql/t-sql/statements/create-login-transact-sql?view=sql-server-ver16)
- [QUOTENAME](https://learn.microsoft.com/en-us/sql/t-sql/functions/quotename-transact-sql?view=sql-server-ver16)
- [Permisos de esquema](https://learn.microsoft.com/en-us/sql/t-sql/statements/grant-schema-permissions-transact-sql?view=sql-server-ver16)
- [XACT_ABORT](https://learn.microsoft.com/en-us/sql/t-sql/statements/set-xact-abort-transact-sql?view=sql-server-ver16)
- [fn_my_permissions](https://learn.microsoft.com/en-us/sql/relational-databases/system-functions/sys-fn-my-permissions-transact-sql)

Estado actual: **APROVISIONADO POR EL RESPONSABLE; CIERRE PENDIENTE DE PRUEBAS
INTERACTIVAS**. VERIFY-014 no aprovisiono ni cambio SQL Server. Sin commits,
push, merge, despliegue ni F2B.
