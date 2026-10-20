# F2A-PRECHECK-011 — PREPARADO PARA AUTORIZAR AUTHTEST Y REPORTS

Revision preventiva terminada. **No se ejecutaron AuthTest ni Reports.**
Su integracion, las comprobaciones posteriores del helper y los accesos con
cada identidad real siguen pendientes de autorizacion y ejecucion.

## A. AuthTest

Estado real consultado: ferrestock_auth_test esta ONLINE, sin auth_owner,
sin ferrestock_auth_test_app, sin esquema auth, sin ACL propias y sin tablas
de negocio. El login ferrestock_auth_test_app ya existe, habilitado y con
base predeterminada ferrestock_auth_test. En AuthDev conserva DENY CONNECT.

21-auth-test.sql creara, solo si faltan, auth_owner WITHOUT LOGIN, el esquema
auth de ese propietario y el usuario ferrestock_auth_test_app FOR LOGIN del
mismo nombre, con DEFAULT_SCHEMA=auth. El SID se obtiene del login existente;
el script lo comprueba y no lo rota. No hay alta de roles ni de otros usuarios.

| Principal | Clase/alcance | Permiso y estado esperados |
|---|---|---|
| auth_owner | DATABASE, major_id=minor_id=0 | GRANT CONNECT, generado por CREATE USER |
| ferrestock_auth_test_app | DATABASE, major_id=minor_id=0 | GRANT CONNECT |
| ferrestock_auth_test_app | DATABASE, major_id=minor_id=0 | DENY CREATE TABLE, CREATE VIEW, CREATE PROCEDURE, CREATE FUNCTION, ALTER ANY SCHEMA |

Total previsto: **7 ACL, 2 usuarios propios, 1 esquema propio**, cero concesiones
directas de DML/SELECT a test_app, cero roles y cero tablas nuevas. La propiedad
de auth de auth_owner es la prevista; no es propiedad de test_app.

Defecto demostrado de validacion: faltaba la primera fila en la allowlist de
AuthTest. El mismo CREATE USER WITHOUT LOGIN produjo esa ACL real en AuthDev.
La regresion que retira exclusivamente esa fila reproduce 51119 para el
estado futuro simulado. No se creo un usuario para repetir el experimento.
Microsoft documenta que WITHOUT LOGIN no autentica y que crear un usuario no
concede por si mismo acceso a objetos:
[CREATE USER](https://learn.microsoft.com/en-us/sql/t-sql/statements/create-user-transact-sql?view=sql-server-ver16).

Reejecucion: CREATE USER y CREATE SCHEMA estan protegidos por comprobaciones
de existencia, tipo, SID y propietario. Los GRANT/DENY previstos se reaplican
sin recrear identidades. COMMIT ocurre antes de abrir la conexion posterior;
un fallo en esa comprobacion no revierte una etapa ya confirmada. La idempotencia
se reviso en el codigo; **no se probo ejecutando ni repitiendo la etapa**.

El helper selecciona exclusivamente ferrestock_auth_test y 21-auth-test.sql.
No contiene operaciones sobre AuthDev; su DENY CONNECT para test_app permanece.
No se requiere rol administrativo ni permisos de migracion para esa cuenta.

## B. Reports

Estado real consultado: ferrestock_ventas ONLINE, sin usuario
ferrestock_report_reader, sin ACL propias y sin tablas comerciales. Su login
SQL ya existe, habilitado y con ferrestock_ventas como base predeterminada.

30-report-reader.sql crea el usuario FOR LOGIN del mismo nombre solo si falta,
con DEFAULT_SCHEMA=dbo, comprueba tipo/SID y ausencia de roles. No crea esquemas,
no crea auth_owner ni cambia propietarios.

| Principal | Clase/alcance | Permiso y estado esperados |
|---|---|---|
| ferrestock_report_reader | DATABASE, major_id=minor_id=0 | GRANT CONNECT |
| ferrestock_report_reader | DATABASE, major_id=minor_id=0 | DENY INSERT, UPDATE, DELETE, EXECUTE |
| ferrestock_report_reader | DATABASE, major_id=minor_id=0 | DENY CREATE TABLE, CREATE VIEW, CREATE PROCEDURE, CREATE FUNCTION, ALTER ANY SCHEMA |

Total previsto: **10 ACL, 1 usuario propio**, cero esquemas nuevos, cero roles,
cero SELECT comercial. CONNECT de CREATE USER coincide con el GRANT CONNECT
explicito: no requiere una segunda excepcion ni una ACL mas amplia.
Las diez filas ya coinciden con la politica; no se modifico Reports.

Reejecucion revisada: existencia, SID/tipo y roles se verifican; no se recrea
usuario ni login y se repiten las mismas concesiones/denegaciones. No se probo
esa ejecucion. El helper fija ferrestock_ventas como unica base de esta etapa.

No hay mapeo de report_reader en ninguna base Auth ni una operacion de Reports
que lo cree. Los cuatro logins carecen de roles de servidor o permisos globales
no aprobados; guest no tiene ACL CONNECT, public solo tiene el baseline de
sistema revisado, TRUSTWORTHY/DB_CHAINING y encadenamiento global estan apagados.
Esto sustenta el aislamiento esperado; no sustituye probar conexiones reales.

90-effective-permissions.sql exige ORIGINAL_LOGIN y USER_NAME reales, comprueba
HAS_DBACCESS en las tres bases, sysadmin/db_owner y permisos DDL. Para report_reader
no exige el esquema auth. Con @ApprovedObjects vacia informa expresamente que
la prueba SELECT/DML por objeto esta pendiente. Debe usarse tras provisionar
con cada credencial de aplicacion; no se ejecuto como administrador ni mediante
suplantacion. No se declara acceso comercial validado.

## C. Correcciones

En 05-preflight.sql, dentro de la lista AuthTest, se agrego exclusivamente:

```sql
('ferrestock_auth_test','auth_owner',0,'','','CONNECT','G'),
```

Las validaciones existentes exigen major_id=0, minor_id=0, estado G y permiso
exacto. Permanecen los controles de usuario sin login, propietario, roles,
SID, objetos, columnas, collations y permisos sobre identidades. No se cambio
la politica de otro usuario/base ni se admitio GRANT_WITH_GRANT_OPTION.

Otros archivos necesarios:
- Get-AuthDevAclSql.ps1: el test de excepciones reconoce ahora dev y test como
  las dos bases autorizadas para ese CONNECT exacto; conserva sus pruebas dev.
- Get-FutureStagesSql.ps1: nueva herramienta que **emite consultas simuladas**;
  deriva las ACL de los GRANT/DENY de 21/30 y del CONNECT predeterminado.
- Este informe, el indice README de provisioning y evidencias del incremento.

21-auth-test.sql, 30-report-reader.sql, New-Logins.ps1 y
90-effective-permissions.sql quedaron intactos.

## D. Pruebas y contraste de estados

| Estado | AuthDev real | AuthTest | Ventas/Reports | Compatibilidad con las 54 guardias |
|---|---|---|---|---|
| Actual, consulta real | 4 usuarios, esquema auth, 17 ACL | Sin objetos/ACL propios | Sin usuario/ACL propios | 54 conformes, 0 desviaciones |
| Despues de AuthTest, simulado | Igual | 2 usuarios, auth, 7 ACL | Igual al actual | 54 conformes en proyeccion |
| Despues de Reports, simulado | Igual | Igual al actual | 1 usuario, 10 ACL | 54 conformes en proyeccion |
| Despues de ambas, simulado | Igual | 2 usuarios, auth, 7 ACL | 1 usuario, 10 ACL | 54 conformes en proyeccion |

Las simulaciones se ejecutaron en SQL Server mediante variables de tabla que
proyectan usuarios, permisos, roles y esquemas. Se conservan los predicados de
las 54 condiciones, obtenidos de Get-PreflightAuditSql.ps1; se reemplazan solo
sus entradas de catalogo en AuthTest/Ventas. Los SID de aplicacion se leen de
los logins actuales; los ids hipoteticos no predicen los asignados por CREATE.
No se ejecutan los lotes DDL/DCL de 21/30. Esto comprueba compatibilidad de
politica, **no ejecucion futura ni integracion**.

| Prueba | Categoria | Resultado |
|---|---|---|
| Test-Static.ps1 | PASS estatico | Parser, ocho etapas sin Apply, barreras, listas vacias y BIN2 |
| Test-Diagnostics.ps1 | PASS simulado/local | 70 aserciones, sin conexiones reales |
| Test-SqlPreparation.ps1 | PASS ADO.NET local | 36 aserciones; apertura interceptada |
| Test-Collation.sql | PASS SQL real de lectura | Cinco consultas y semantica BIN2 |
| Get-PublicGuestSql.ps1 -Test | PASS SQL con fixtures | 73 casos |
| Get-AuthDevAclSql.ps1 -Test | PASS SQL: fixtures y estado real dev | 22 casos ACL, SID, propietario y roles |
| Get-PreflightAuditSql.ps1 | PASS SQL real de lectura | 54 condiciones conformes del estado actual |
| Get-FutureStagesSql.ps1 | PASS simulacion en SQL | Cuatro escenarios positivos y once casos de rechazo |
| Ejecucion AuthTest/Reports y controles posteriores | INTEGRACION REAL PENDIENTE | No autorizada ni ejecutada en este incremento |

Casos de rechazo detectados:

| Fixture, siempre en memoria | Guardia detectada |
|---|---|
| Politica original sin CONNECT de auth_owner en test | 51119 AuthTest |
| GRANT SELECT global de report_reader | 51119 Ventas |
| Escritura INSERT de report_reader habilitada | 51119 Ventas |
| CREATE TABLE de test_app habilitado | 51119 AuthTest |
| auth_owner convertido a usuario autenticable | 51124 AuthTest |
| SID de test_app incorrecto | 51114 AuthTest |
| test_app agregado a db_owner | 51115 AuthTest |
| Esquema auth con propietario dbo | 51125 AuthTest |
| report_reader mapeado en AuthTest | 51113 AuthTest |
| GRANT SELECT por columna | 51119 Ventas |
| CONNECT con grant option | 51119 Ventas |

Cada escenario evalua las 54 condiciones y verifica el resultado esperado.
Las filas ficticias son datos de pruebas, no permisos SQL aplicados.
Auditoria real actual ? evidencia local conservada internamente por el equipo; no incluida en el repositorio p?blico,
proyeccion AuthTest ? evidencia local conservada internamente por el equipo; no incluida en el repositorio p?blico,
proyeccion Reports ? evidencia local conservada internamente por el equipo; no incluida en el repositorio p?blico,
ambas etapas ? evidencia local conservada internamente por el equipo; no incluida en el repositorio p?blico.
Los once casos negativos se guardaron separadamente con el mismo prefijo.

## E/F. Seguridad, Git y limites

Snapshots antes/despues de bases, logins, SID, usuarios, roles, esquemas,
objetos y permisos son identicos. SHA-256 de ambos:
`8FBF37759A653E2E8E1F1BCDBBD15CD9BA94FAEE4169F1D898A6F4334A321F7E`.
No se crearon usuarios, esquemas o tablas persistentes; no se ejecutaron
GRANT/DENY/REVOKE, migraciones ni ninguna etapa administrativa. Los DML de
fixtures afectan exclusivamente variables locales. No se imprimieron secretos.

Rama feature/auth-reportes-service preservada. README raiz conserva su cambio
preexistente y SHA-256:
`E559DE23FC936FF9974C699F34E2B33EBA788099D4F418C33210F893B945A500`.
Ventas y Java intactos; sin commits, push, merge, despliegue ni F2B.

## G. Dictamen

**PREPARADO PARA AUTORIZAR AUTHTEST Y REPORTS.** Esta revision no las ejecuta.
El responsable puede autorizar las etapas individualmente, verificando la
comprobacion posterior de cada una antes de continuar con la siguiente.
La version/identidad/politicas actuales deben seguir cumpliendo preflight.

No se detectaron otros defectos demostrables de validacion. Quedan pendientes
la ejecucion administrativa real, las conexiones con cada identidad, la prueba
de aislamiento de 90 y las pruebas por objeto cuando existan objetos aprobados.
Una discrepancia debe detener la secuencia, sin reejecutar ni reparar a ciegas.
