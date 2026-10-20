# F2A-VERIFY-014 — Verificacion final administrativa

Fecha: 2026-10-17 (America/Lima).
Dictamen: **F2A PENDIENTE DE PRUEBAS INTERACTIVAS**.

## A. Inventario definitivo observado

Los cuatro logins SQL existen, estan habilitados y tienen CHECK_POLICY=ON y
CHECK_EXPIRATION=OFF. No pertenecen a roles de servidor; su unico permiso
directo de servidor es GRANT CONNECT SQL. Bases predeterminadas correctas.

| Login | Base predeterminada | Usuarios/mapeos observados |
|---|---|---|
| ferrestock_auth_app | ferrestock_auth_dev | SQL_USER/INSTANCE en AuthDev, SID coincidente, default_schema auth |
| ferrestock_auth_migrator | ferrestock_auth_dev | SQL_USER/INSTANCE en AuthDev, SID coincidente, default_schema auth |
| ferrestock_auth_test_app | ferrestock_auth_test | SQL_USER/INSTANCE en AuthTest y AuthDev, SID coincidente; en dev DENY CONNECT |
| ferrestock_report_reader | ferrestock_ventas | SQL_USER/INSTANCE solo en Ventas, SID coincidente, default_schema dbo |

Ambas bases Auth tienen auth_owner SQL_USER/NONE, sin login correspondiente,
propietario exclusivamente del esquema auth. No existe login auth_owner.
No hay roles explicitos para las identidades propias, permisos por columna
ni GRANT_WITH_GRANT_OPTION. La pertenencia implicita a public es esperada.

| Base | ACL propias exactas | Descripcion |
|---|---:|---|
| AuthDev | 17 | auth_owner CONNECT; app CONNECT y cinco DENY DDL; migrador CONNECT/CREATE TABLE y siete GRANT sobre auth; test_app DENY CONNECT |
| AuthTest | 7 | auth_owner CONNECT; test_app CONNECT y cinco DENY DDL |
| Ventas | 10 | reader CONNECT; DENY INSERT/UPDATE/DELETE/EXECUTE y cinco DENY DDL |

Los siete permisos del migrador sobre auth son ALTER, REFERENCES, SELECT,
INSERT, UPDATE, DELETE y VIEW DEFINITION. CREATE TABLE es un permiso a nivel
base; la capacidad prevista de crear tablas se restringe por sus permisos
sobre el esquema auth. No se concede ALTER ANY SCHEMA ni roles DDL globales.
La comprobacion funcional de esos limites queda pendiente con su login.

Se compararon las ACL esperadas y reales en ambos sentidos mediante EXCEPT:
cero filas esperadas ausentes y cero adicionales. Las tres bases estan ONLINE,
TRUSTWORTHY=0, DB_CHAINING=0 y sin tablas ni objetos comerciales. No hay SELECT
comercial anticipado para reader ni concesiones de escritura comercial.
Los permisos de catalogos de public no se cuentan como permisos comerciales.

Inventario completo ? evidencia local conservada internamente por el equipo; no incluida en el repositorio p?blico.

## B. Matriz de acceso

La configuracion administrativa respalda esta matriz, pero **las doce aperturas
reales siguen PENDIENTES**; no se uso sa ni EXECUTE AS para simularlas.

| Login | AuthDev esperado / conexion real | AuthTest esperado / conexion real | Ventas esperado / conexion real |
|---|---|---|---|
| auth_app | Permitir / PENDIENTE | Rechazar / PENDIENTE | Rechazar / PENDIENTE |
| auth_migrator | Permitir / PENDIENTE | Rechazar / PENDIENTE | Rechazar / PENDIENTE |
| auth_test_app | Rechazar / PENDIENTE | Permitir / PENDIENTE | Rechazar / PENDIENTE |
| report_reader | Rechazar / PENDIENTE | Rechazar / PENDIENTE | Permitir / PENDIENTE |

Nombres abreviados en esta tabla; el script utiliza los cuatro nombres completos
ferrestock_*. No hay un canal seguro de entrada interactiva disponible al agente
para introducir sus contrasenas. No se buscaron ni extrajeron esas contrasenas.

## C. Auditoria de 54 condiciones

**PASS: 54 condiciones conformes, cero desviaciones**, ejecutadas sobre SQL
Server real mediante Get-PreflightAuditSql.ps1. Es auditoria administrativa;
no se ejecuto ninguna etapa del helper en este incremento.
Resultados individuales ? evidencia local conservada internamente por el equipo; no incluida en el repositorio p?blico.

## D. Pruebas y mecanismo entregado

| Prueba | Categoria | Resultado |
|---|---|---|
| Test-Static.ps1 | Estatica/local | PASS: parser, ocho etapas sin Apply, barreras, listas vacias, BIN2 |
| Test-Diagnostics.ps1 | Simulada | PASS: 70 aserciones |
| Test-SqlPreparation.ps1 | ADO.NET local sin apertura | PASS: 36 aserciones |
| Test-AccessMatrixUnit.ps1 | Local/simulada, sin red | PASS: 97 aserciones y seis flujos |
| Test-Collation.sql | SQL real, lectura | PASS |
| Get-PublicGuestSql.ps1 -Test | SQL real con fixtures | PASS: 73 casos |
| Get-AuthDevAclSql.ps1 -Test | SQL real, estado dev y fixtures | PASS: 22 casos ACL y controles de identidad/propietario |
| Inventario y comparacion exacta de ACL | SQL real administrativo | PASS: 34 ACL conformes |
| Auditoria integral actual | SQL real administrativo | PASS: 54 condiciones |
| Conexiones con las cuatro cuentas | Integracion funcional | PENDIENTE: doce combinaciones |
| ORIGINAL_LOGIN/USER_NAME y permisos efectivos de esas conexiones | Integracion funcional | PENDIENTE |
| SELECT/DML por objetos comerciales aprobados | Funcional | NO APLICA: no existen objetos/lista aprobada |

Se inspecciono 90-effective-permissions.sql y se mantuvo intacto. Comprueba
identidad, HAS_DBACCESS, privilegios y DDL; informa que no hay objetos aprobados.
No utiliza escrituras ni suplantacion. Se agrego Verify-AccessMatrix.ps1 para
ejecutarlo con cada credencial real y realizar aperturas independientes.

El verificador solicita cada credencial con Get-Credential y el nombre fijo
esperado. Mantiene secretos solo durante la verificacion de esa cuenta,
mediante SecureString/SqlCredential, y libera conexiones y recursos en finally.
Fija 127.0.0.1:14333, verifica Docker y su identidad, cifra siempre y valida
certificado salvo la opcion local explicitamente autorizada. Pooling y
reintentos automaticos deshabilitados. Maximo doce llamadas Open.

Primero conecta a la base asignada, verifica ORIGINAL_LOGIN/USER_NAME/SID y
servidor, privilegios efectivos globales y ALTER en todos los esquemas, luego
ejecuta 90. Solo despues prueba las otras dos bases. Un acceso indebido
concedido es FAIL. Un rechazo 4060 solo se acepta como PASS con la misma
credencial ya validada y base ONLINE antes y despues. 18456 solo, TLS,
infraestructura, errores desconocidos o resultados NULL quedan pendientes,
no se presentan como aislamiento demostrado. La salida muestra solamente
etiquetas controladas y codigos SQL, severidad y estado, no mensajes originales.

Las pruebas nuevas incluyen seis flujos completos simulados: conforme,
autenticacion rechazada, TLS, acceso indebido concedido, permiso NULL y fallo
de 90. Tambien prueban el limite del intento 13, disposicion al fallar Open,
TLS por defecto, ausencia de secretos y bloqueo de SQL mutador. Son pruebas
complementarias: **no sustituyen la integracion real**. El filtro SQL es una
defensa adicional para el archivo revisado, no un parser SQL general.

## E. Seguridad

No se ejecutaron etapas administrativas, migraciones, DDL, DCL ni DML sobre
objetos persistentes. Las escrituras de fixtures afectan variables locales.
No se realizaron intentos de conexion con contrasenas incorrectas. Las consultas
administrativas usan la credencial existente dentro del contenedor sin imprimirla;
eso no se uso como sustituto de conexiones de las cuatro cuentas.

Snapshots antes/despues de bases, logins, SID, usuarios, roles, esquemas,
objetos y ACL son identicos. SHA-256:
`9715566BE3B0743FC1A9841412D3306CF1FCDFEF5606D90A67D120A1A5F04704`.
Los resultados respaldan ausencia de cambios persistentes en el inventario
observado durante VERIFY-014. No se expusieron contrasenas ni cadenas de conexion.

## F. Git y archivos

Rama feature/auth-reportes-service preservada. Estado:

```text
 M README.md
?? auth-reportes-service/
```

README raiz conserva su modificacion preexistente y SHA-256:
`E559DE23FC936FF9974C699F34E2B33EBA788099D4F418C33210F893B945A500`.
Ventas y Java intactos. No commits, push, merge ni despliegue. F2B no iniciado.
Archivos agregados: Verify-AccessMatrix.ps1, Test-AccessMatrixUnit.ps1,
este informe y evidencia VERIFY-014. README de provisioning actualizado;
historial y scripts SQL existentes preservados.

## G. Dictamen y unico paso pendiente

**F2A PENDIENTE DE PRUEBAS INTERACTIVAS.** No hay desviaciones administrativas
observadas, pero no corresponde declarar aislamiento funcional ni cierre definitivo.

El responsable, desde auth-reportes-service/db/provisioning, ejecutara:

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File ".\Verify-AccessMatrix.ps1" -TrustLocalDevelopmentCertificate
```

Introducir las cuatro credenciales exclusivamente en los dialogos. Revisar el
JSON de doce resultados y controles efectivos, conservarlo sin secretos y
repetir el inventario administrativo de lectura para comprobar preservacion.
Solo tras esa evidencia conforme procede decidir el cierre de F2A.

Fuentes sobre clasificacion conservadora:
[Microsoft 18456](https://learn.microsoft.com/en-us/sql/relational-databases/errors-events/mssqlserver-18456-database-engine-error?view=sql-server-ver17),
[Microsoft HAS_PERMS_BY_NAME](https://learn.microsoft.com/en-us/sql/t-sql/functions/has-perms-by-name-transact-sql).
