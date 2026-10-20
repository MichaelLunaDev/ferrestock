# F2A-STABILIZE-008 — CORREGIDO — PENDIENTE DE VALIDACION REAL

## A. Causa raiz de 51121

Se inspeccionaron las tres bases individualmente. Cada una contiene 228 ACL de
public: 226 GRANT SELECT sobre objetos de sistema y dos GRANT de visibilidad
de claves de cifrado a nivel DATABASE. No hay ACL de guest. Las 684 filas
coinciden con model en principal/clase/id/columna/permiso/estado.

En cada base, 24 de esos SELECT se rechazaban porque los objetos Resource no
figuran en sys.all_objects, aunque OBJECTPROPERTYEX(id,'IsMSShipped') devuelve 1
y OBJECT_SCHEMA_NAME devuelve sys. Ejemplo: id -675,
sys.sysquery_store_runtime_stats_2017, OBJECT_OR_COLUMN, minor_id=0, SELECT, GRANT.
Las 24 identidades exactas estan enumeradas en @F2HiddenSystemSelect y en
el inventario completo ? evidencia local conservada internamente por el equipo; no incluida en el repositorio p?blico.

Clasificacion: **A, permisos de sistema legitimos**, 24 discrepancias por base,
72 en total. Las otras 612 filas ya estaban permitidas. No se observaron
discrepancias B (peligrosas) o C (indeterminadas). Model fue evidencia adicional;
no se utiliza para autodetectar ni aprobar permisos en el preflight.

Microsoft documenta que los objetos de sistema residen en Resource y aparecen
logicamente en sys de cada base; los major_id negativos identifican objetos de
sistema. La lista concreta de 24 procede de la inspeccion local, no de una
suposicion sobre todas las versiones:
[Resource](https://learn.microsoft.com/en-us/sql/relational-databases/databases/resource-database?view=sql-server-ver16),
[sys.database_permissions](https://learn.microsoft.com/en-us/sql/relational-databases/system-catalog-views/sys-database-permissions-transact-sql?view=sql-server-ver16).

## B. Auditoria integral

Get-PreflightAuditSql.ps1 deriva consultas diagnosticas de **todas las 54
condiciones IF/THROW**, sin detenerse en la primera desviacion. Conserva sus
predicados y las listas locales; genera SELECT en lugar de THROW. No ejecuta
New-Logins.ps1 ni declara @PreflightPassed. Si no reconoce alguna guardia, falla.
Es diagnostico, no sustituye la ejecucion del helper en sus dos conexiones.

Inspeccion inicial: 51 condiciones conformes y tres desviaciones 51121.
Tras corregir: **54 conformes, cero desviaciones** en SQL real.
Salida completa ? evidencia local conservada internamente por el equipo; no incluida en el repositorio p?blico.

| Codigo | Condicion/resultado esperado | Estado real y resultado observado | Riesgo si falla / accion |
|---|---|---|---|
| 51100 | Conexion sin transaccion | @@TRANCOUNT=0, XACT_STATE=0; conforme | Medio: detener |
| 51101 | Operador sysadmin para visibilidad | Identidad administrativa de inspeccion; conforme | Alto: detener |
| 51102 | Identidad Docker=MachineName=ServerName, sin instancia nombrada | Coinciden; conforme | Critico: detener |
| 51103 | Version/edicion fijadas | 16.0.4250.1, Developer Edition (64-bit); conforme | Medio: revisar cambio de version |
| 51104 | Base actual autorizada | master; conforme | Critico: detener |
| 51105 | Tres bases ONLINE, TRUSTWORTHY=0, DB_CHAINING=0 | Las tres cumplen | Alto: detener |
| 51106 | Sin encadenamiento global | value_in_use=0; conforme | Alto: detener |
| 51107 | Logins existentes con tipo/SID/politicas/base/idioma aprobados | Cero logins previstos; guardia conforme | Alto: verificar despues de crearlos |
| 51108 | Sin roles de servidor propios | Cero identidades propias; conforme | Critico: verificar tras provisionar |
| 51109 | Sin ACL de servidor fuera de lista | Cero ACL propias; conforme | Critico: verificar tras provisionar |
| 51110 | Cuentas propias no propietarias de bases | Ninguna; conforme | Critico: detener si cambia |
| 51111 | Sin permisos sobre los logins previstos | Ninguno; conforme | Critico: detener si cambia |
| 51112 | public de servidor limitado al baseline | Baseline aprobado; conforme | Alto: detener si cambia |
| 51113 | Mapeos entre bases autorizados | Cero usuarios propios; conforme en tres bases | Alto: comprobar tras provisionar |
| 51114 | Tipos/SID de usuarios coherentes con login | Cero usuarios propios; conforme en tres bases | Alto: comprobar tras provisionar |
| 51115 | Sin roles de base propios | Ninguno; conforme en tres bases | Critico: comprobar tras provisionar |
| 51116 | Propiedad de esquema restringida | Sin auth/auth_owner propios; conforme | Critico: comprobar tras provisionar |
| 51117 | Sin propietarios explicitos propios de objetos | Ninguno; conforme | Critico: detener si cambia |
| 51118 | Sin propiedad de otros principales/roles | Ninguna propia; conforme | Critico: detener si cambia |
| 51119 | ACL propias exactas, sin permisos por columna | Cero ACL propias; conforme en tres bases | Critico: comprobar tras provisionar |
| 51120 | Sin permisos sobre identidades propias | Ninguno; conforme | Critico: detener si cambia |
| 51121 | public/guest segun lista cerrada | Antes: 24 discrepancias A/base. Ahora: cero | Alto: corregido el falso positivo; no reparar permisos |
| 51122 | Sin modulos EXECUTE AS | Ninguno; conforme | Alto: revisar accesos indirectos |
| 51123 | Sin CLR o firmas no revisadas | Ninguno; conforme | Alto: revisar accesos indirectos |
| 51124 | auth_owner, si existe, sin login | No existe en Auth; conforme | Alto: comprobar tras provisionar |
| 51125 | Esquema auth, si existe, pertenece a auth_owner | No existe; conforme en Auth | Critico: comprobar tras provisionar |
| 51126 | Objetos Auth dentro de lista aprobada | Cero tablas/objetos propios; conforme | Alto: revisar migraciones futuras |
| 51127 | Sin triggers Auth no revisados | Ninguno; conforme | Alto: revisar efectos indirectos |

Servidor, master y las tres bases usan SQL_Latin1_General_CP1_CI_AS. Los COLLATE
BIN2 de FIX-007 se conservan; el diagnostico completo ya ejecuta esas
comparaciones sin 468. Las cuentas todavia no existen: conformidad de guardias
no demuestra sus futuros permisos efectivos ni su aislamiento funcional.

## C. Correccion y archivos

- **05-preflight.sql**: lista cerrada de 24 parejas ID/nombre, valida solo para
  la version ya fijada. La excepcion nueva exige public, GRANT SELECT,
  OBJECT_OR_COLUMN, minor_id=0, id negativo exacto, nombre BIN2 y esquema sys.
  No acepta otros objetos negativos ni extiende el permiso a guest.
- Las tres guardias 51121 exigen minor_id=0 para SELECT de sistema. Los permisos
  DATABASE admitidos exigen major_id=minor_id=0. Se rechazan permisos por columna,
  grant option, otros permisos, otros estados y datos de usuario.
- **Get-PreflightAuditSql.ps1**: diagnostico completo sin interrupcion temprana.
- **Get-PublicGuestSql.ps1**: inventario detallado o regresion, usando el
  predicado real 51121 de cada base y la lista real de objetos.
- **evidence/**: inventario de 684 ACL, 54 resultados y snapshots comparados.
  No contiene credenciales ni cadenas de conexion.

## D/E. Pruebas y evidencia

| Prueba | Tipo | Resultado real |
|---|---|---|
| Test-Static.ps1 | Local/estatica | PASS: parser, ocho etapas sin Apply, barreras, listas vacias, BIN2 |
| Test-Diagnostics.ps1 | Simulacion | PASS: 70 aserciones, sin conexiones reales |
| Test-SqlPreparation.ps1 | ADO.NET local, apertura interceptada | PASS: 36 aserciones, sin conexiones reales |
| Test-Collation.sql | SQL real de lectura | PASS: cinco consultas y semantica BIN2 |
| Get-PublicGuestSql.ps1 -Test | SQL real con filas ficticias en VALUES | PASS: 73 casos: 24 dev, 25 test, 24 ventas |
| Inventario public/guest | SQL real de lectura | 228 ACL/base, cero desviaciones finales; 684 coinciden con model |
| Auditoria completa de condiciones | SQL real de lectura | 54 conformes, cero desviaciones |
| Helper Preflight, conexion inicial y posterior | Integracion administrativa interactiva | **NO EJECUTADO; pendiente** |

Las regresiones ejercitan SELECT estandar, objetos Resource exactos, metadata
de cifrado y guest DENY CONNECT permitidos; rechazan SELECT de datos de usuario,
INSERT/UPDATE/DELETE/EXECUTE, CONTROL, grant option, REVOKE/DENY inesperados,
permisos por columna, objetos desconocidos y guest CONNECT. Una concesion
IMPERSONATE ANY USER solo en el fixture de auth_test se detecta como rechazada.
No se aplico ninguno de esos permisos: son valores de consultas, no comandos.

## F/G. Seguridad y preservacion

Las consultas de inspeccion usan la credencial administrativa ya existente
dentro del contenedor, sin imprimirla ni incluirla en argumentos. Esto NO se
uso para sustituir Get-Credential ni ejecutar el helper. No hay canal seguro de
entrada interactiva compartido disponible para completarlo en esta sesion.

No hubo DDL/DML persistente, concesiones, revocaciones ni etapas mutadoras.
Los INSERT del diagnostico son unicamente sobre variables locales.
Las tres bases siguen ONLINE, sin tablas de usuario, usuarios propios ni
esquema auth; los cuatro logins previstos siguen ausentes.

Snapshots antes/despues de la ultima tanda de verificaciones incluyen estado
de bases, logins previstos, permisos de servidor, usuarios, roles, esquemas,
objetos y ACL de las tres bases. Son identicos (SHA-256):
`9F73A4A22B1F6C83B1EBBFDCE557974B8B7EFC46208091DB653BBEE1241A0827`.
No hay evidencia de cambios persistentes en ese inventario.

Rama: feature/auth-reportes-service. README raiz permanece modificado previamente,
con SHA-256 conservado:
`E559DE23FC936FF9974C699F34E2B33EBA788099D4F418C33210F893B945A500`.
Ventas y Java intactos. Sin commits, push, merge, despliegues ni F2B.

## H. Dictamen

**CORREGIDO — PENDIENTE DE VALIDACION REAL.** No se declara INTEGRACION PASS.
Falta ejecutar el helper completo con credencial administrativa interactiva,
validar su finalizacion de ambas comprobaciones y repetir el inventario.
Desde auth-reportes-service/db/provisioning, el responsable ejecutara solo:

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File ".\New-Logins.ps1" -Apply -Stage Preflight -TrustLocalDevelopmentCertificate
```

Se conserva la excepcion de certificado autorizada exclusivamente para desarrollo.
Un resultado distinto de Preflight VALIDADO obliga a detenerse y revisar el
codigo/fase sanitizados; no autoriza a ejecutar ninguna otra etapa.
