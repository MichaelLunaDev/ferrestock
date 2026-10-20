# F2A-APPLY-010-FIX — Correccion de politica; Preflight pendiente

## A. Estado real de AuthDev

Inventario administrativo de solo lectura, desde conexiones independientes.
Las tres bases siguen ONLINE, TRUSTWORTHY=0 y DB_CHAINING=0. Los cuatro
logins previstos existen y estan habilitados. En auth_test no existen todavia
auth_owner/test_app; en Ventas no existe el usuario report_reader.

| Principal en ferrestock_auth_dev | Tipo/autenticacion | SID y esquema predeterminado | ACL directas reales |
|---|---|---|---|
| auth_owner | SQL_USER / NONE | SID propio, sin login coincidente; dbo | DATABASE GRANT CONNECT |
| ferrestock_auth_app | SQL_USER / INSTANCE | SID igual al login del mismo nombre; auth | GRANT CONNECT; DENY CREATE TABLE, VIEW, PROCEDURE, FUNCTION y ALTER ANY SCHEMA |
| ferrestock_auth_migrator | SQL_USER / INSTANCE | SID igual al login del mismo nombre; auth | DATABASE GRANT CONNECT y CREATE TABLE; SCHEMA auth GRANT ALTER, REFERENCES, SELECT, INSERT, UPDATE, DELETE, VIEW DEFINITION |
| ferrestock_auth_test_app | SQL_USER / INSTANCE | SID igual al login del mismo nombre; dbo | DATABASE DENY CONNECT |

auth_owner SID: omitido del informe publicable; evidencia local excluida de Git.
Los SID de los tres usuarios vinculados coinciden individualmente con su login;
los snapshots conservan la evidencia. SID es identificador, no credencial.

El esquema auth (schema_id=5) pertenece a auth_owner. Ninguno de los cuatro
principales pertenece a roles explicitos ni posee otros principales/roles.
No hay propietarios explicitos de objetos, ACL por objeto/columna ni
GRANT_WITH_GRANT_OPTION. Hay **17 ACL propias**, 0 tablas de negocio.

El estado corresponde completamente a lo previsto por 20-auth-dev.sql, mas el
CONNECT generado al crear auth_owner. Es visible desde otras conexiones:
los cambios quedaron persistidos. El script confirma COMMIT antes de que el
helper abra la conexion posterior. El error posterior no hizo rollback de
esa transaccion ya confirmada. No se repitio AuthDev.

## B. Causa de 51119

La unica fila fuera de la allowlist original era:

| Base | Usuario | Clase | major_id | minor_id | Permiso | Estado | Otorgante |
|---|---|---|---:|---:|---|---|---|
| ferrestock_auth_dev | auth_owner | DATABASE (0) | 0 | 0 | CONNECT | GRANT (G) | dbo |

Las otras 16 filas ya coincidían. Se compararon todas, no solo la primera.
Inventario anterior ? evidencia local conservada internamente por el equipo; no incluida en el repositorio p?blico.

20-auth-dev.sql, linea 14, ejecuta CREATE USER [auth_owner] WITHOUT LOGIN.
El script no ejecuta GRANT CONNECT sobre auth_owner: el CONNECT observado es
el permiso de acceso a base que acompana CREATE USER. La lista @F2Acl omitía
esa fila. La documentacion explica que crear un usuario habilita acceso a base,
sin conceder por ello acceso a objetos, y que un usuario WITHOUT LOGIN no
puede autenticarse directamente:
[Microsoft CREATE USER](https://learn.microsoft.com/en-us/sql/t-sql/statements/create-user-transact-sql?view=sql-server-ver16).

No se hizo una nueva creacion de prueba ni una reconstruccion del historial
de comandos del operador. La causa de la discrepancia esta demostrada por
la fila real y la politica; la atribucion al CREATE USER se basa en el script
ejecutado y el comportamiento documentado, sin afirmar una auditoria forense.

Es una ACL legitima compatible con el propietario sin login. No convierte
auth_owner en una cuenta de conexion ni agrega acceso comercial. La propiedad
del esquema auth ya formaba parte del diseno y no se ha modificado.

## C. Correccion minima

En 05-preflight.sql se agrego **una sola fila** a @F2Acl:

```sql
('ferrestock_auth_dev','auth_owner',0,'','','CONNECT','G'),
```

La guardia existente requiere major_id=0, minor_id=0 y coincidencia exacta del
permiso/estado. No admite DENY, REVOKE, grant option, otros permisos, esquemas,
objetos ni columnas. La excepcion no se extiende a auth_test o Ventas.
51124 sigue exigiendo usuario SQL sin autenticacion; 51115/16/17/18/20/25
conservan los controles de roles, propietarios y permisos sobre identidades.

No se modificaron 20-auth-dev.sql, New-Logins.ps1 ni Get-PreflightAuditSql.ps1.
El generador de auditoria toma la nueva fila de la fuente sin omitir controles.
Se agregaron Get-AuthDevAclSql.ps1 (inventario o regresion de lectura), este
informe y sus evidencias; README de provisioning documenta la excepcion.

## D. Pruebas ejecutadas

| Prueba | Tipo | Resultado |
|---|---|---|
| Test-Static.ps1 | Estatica/local, sin Apply | PASS: ocho etapas sin acceso externo; barreras, listas y BIN2 conservados |
| Test-Diagnostics.ps1 | Simulacion | PASS: 70 aserciones |
| Test-SqlPreparation.ps1 | ADO.NET local sin abrir conexion | PASS: 36 aserciones |
| Test-Collation.sql | SQL real, lectura | PASS |
| Get-PublicGuestSql.ps1 -Test | SQL real, datos VALUES ficticios | PASS: 73 casos |
| Get-AuthDevAclSql.ps1 -Test | SQL real, ACL ficticias y estado persistente real | PASS: 22 casos ACL, identidades, propietario y roles |
| Get-PreflightAuditSql.ps1 | SQL real, todas las condiciones | Antes: 53 conformes y 51119 dev. Despues: 54 conformes, cero desviaciones |
| Inventario completo de ACL propias | SQL real, lectura | Despues: 17 aceptadas, 0 fuera de lista |
| Helper Preflight en sus dos conexiones | No ejecutado en este incremento | Pendiente para el responsable |

Los tests nuevos extraen los predicados 51119, 51114 y 51125 del preflight
real. No sustituyen la politica con una copia. Los casos rechazan CONNECT
con estado D/W/R, major/minor distintos de cero, CONTROL, CREATE TABLE,
IMPERSONATE ANY USER, SELECT/ALTER de auth_owner y permisos por columna.
Conservan el DENY CONNECT de test_app en dev y rechazan su GRANT CONNECT.
Las proyecciones ficticias con SID y propietario incorrectos disparan las
guardias correspondientes sin alterar usuarios ni esquemas.

Inventario posterior ? evidencia local conservada internamente por el equipo; no incluida en el repositorio p?blico.
54 resultados actuales ? evidencia local conservada internamente por el equipo; no incluida en el repositorio p?blico.

Snapshots antes/despues de esta intervencion incluyen bases, logins, SID,
usuarios, roles, esquemas, objetos y permisos. Son identicos:
`8FBF37759A653E2E8E1F1BCDBBD15CD9BA94FAEE4169F1D898A6F4334A321F7E`.
Ademas difieren del snapshot historico STABILIZE-008, anterior al
aprovisionamiento: la comparacion detecta los cambios persistentes hechos
por el responsable y verifica que este incremento no agrego otros.

## E. Riesgos y limites

No se encontro ningun permiso propio excesivo o adicional sin aprobar.
La auditoria diagnostica no equivale al exito del helper completo ni a pruebas
funcionales de acceso con cada cuenta. **AuthDev no se declara validado**.

AuthTest no fue ejecutado. 21-auth-test.sql tambien crea auth_owner WITHOUT
LOGIN: antes de autorizar esa etapa debe revisarse la excepcion equivalente
de CONNECT para esa base. Este cambio se limita a la fila real demostrada
en dev; no se aceptan permisos adicionales de otras bases anticipadamente.

## F. Git y seguridad

Rama preservada: feature/auth-reportes-service. El README raiz conserva su
modificacion previa y SHA-256:
`E559DE23FC936FF9974C699F34E2B33EBA788099D4F418C33210F893B945A500`.
Ventas y Java intactos. Sin commits, push o merge. No se alteraron contrasenas,
SID, usuarios, esquemas, roles ni permisos de SQL Server. No hubo migraciones,
etapas de aprovisionamiento ni F2B. Las consultas no imprimen credenciales.

## G. Siguiente paso

**Si: ejecutar unicamente Preflight**, con la credencial administrativa
interactiva y la excepcion TLS local ya autorizada. Desde db/provisioning:

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File ".\New-Logins.ps1" -Apply -Stage Preflight -TrustLocalDevelopmentCertificate
```

Esperar Preflight VALIDADO (comprobaciones inicial y posterior) y conservar
esa evidencia antes de declarar cerrado AuthDev. No repetir AuthDev, Logins
ni ejecutar otras etapas con base solamente en esta correccion.
