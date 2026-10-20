-- SOLO LECTURA: conexion real de cada cuenta, despues de preflight administrativo.
SET NOCOUNT ON;
DECLARE @Identity sysname=ORIGINAL_LOGIN();
DECLARE @Home sysname=CASE @Identity
 WHEN 'ferrestock_auth_app' THEN 'ferrestock_auth_dev'
 WHEN 'ferrestock_auth_migrator' THEN 'ferrestock_auth_dev'
 WHEN 'ferrestock_auth_test_app' THEN 'ferrestock_auth_test'
 WHEN 'ferrestock_report_reader' THEN 'ferrestock_ventas' END;
IF @Home IS NULL THROW 51300, 'Usar una de las cuatro identidades reales, no administrador.', 1;
IF DB_NAME()<>@Home OR USER_NAME()<>@Identity
 THROW 51301, 'Base o identidad efectiva inesperada.', 1;
SELECT @Identity AS login_name,USER_NAME() AS database_user,DB_NAME() AS database_name;
-- VALUES evita ocultar bases por falta de visibilidad en sys.databases.
SELECT d.name,CASE WHEN d.name=@Home THEN 1 ELSE 0 END AS expected_access,
 HAS_DBACCESS(d.name) AS actual_access,
 CASE WHEN HAS_DBACCESS(d.name) IS NULL THEN 'INDETERMINADO'
 WHEN HAS_DBACCESS(d.name)=CASE WHEN d.name=@Home THEN 1 ELSE 0 END THEN 'CONFORME'
 ELSE 'NO CONFORME' END AS result
 FROM (VALUES('ferrestock_auth_dev'),('ferrestock_auth_test'),('ferrestock_ventas')) d(name);
IF EXISTS (SELECT 1 FROM (VALUES('ferrestock_auth_dev'),('ferrestock_auth_test'),('ferrestock_ventas')) d(name)
 WHERE HAS_DBACCESS(d.name) IS NULL OR HAS_DBACCESS(d.name)<>CASE WHEN d.name=@Home THEN 1 ELSE 0 END)
 THROW 51302, 'Aislamiento entre bases no demostrado.', 1;
IF ISNULL(IS_SRVROLEMEMBER('sysadmin'),1)<>0 OR ISNULL(IS_MEMBER('db_owner'),1)<>0
 THROW 51303, 'Identidad privilegiada o resultado indeterminado.', 1;
SELECT permission_name FROM sys.fn_my_permissions(NULL,'SERVER');
SELECT permission_name FROM sys.fn_my_permissions(NULL,'DATABASE');
DECLARE @Migrator bit=CASE WHEN @Identity='ferrestock_auth_migrator' THEN 1 ELSE 0 END;
SELECT 'CREATE TABLE' AS permission_name,@Migrator AS expected,
 HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CREATE TABLE') AS actual;
IF ISNULL(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CREATE TABLE'),-1)<>@Migrator
 OR ISNULL(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CONTROL'),-1)<>0
 OR ISNULL(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','ALTER ANY SCHEMA'),-1)<>0
 THROW 51304, 'Permisos DDL no conformes.', 1;
IF @Home<>'ferrestock_ventas'
BEGIN
 SELECT 'auth' AS schema_name,@Migrator AS expected_alter,
 HAS_PERMS_BY_NAME('auth','SCHEMA','ALTER') AS actual_alter;
 IF ISNULL(HAS_PERMS_BY_NAME('auth','SCHEMA','ALTER'),-1)<>@Migrator
  THROW 51305, 'Esquema ausente/invisible o permiso ALTER no conforme.', 1;
END;

-- Inventario ADMINISTRATIVO aprobado debe poblar esta lista en un incremento posterior.
-- Cada objeto se comprueba por nombre, no por enumeracion de objetos visibles.
DECLARE @ApprovedObjects TABLE (SchemaName sysname,ObjectName sysname,ExpectedSelect bit,
 ExpectedInsert bit,ExpectedUpdate bit,ExpectedDelete bit,PRIMARY KEY(SchemaName,ObjectName));
IF NOT EXISTS (SELECT 1 FROM @ApprovedObjects)
 SELECT 'SIN OBJETOS APROBADOS: DML/SELECT por objeto pendiente; no es prueba positiva de aislamiento.' AS object_verdict;
SELECT a.SchemaName,a.ObjectName,
 CASE WHEN OBJECT_ID(QUOTENAME(a.SchemaName)+'.'+QUOTENAME(a.ObjectName)) IS NULL
 THEN 'INEXISTENTE O NO VISIBLE: contrastar inventario administrativo'
 ELSE 'VISIBLE' END AS object_state,
 p.PermissionName,p.Expected,
 HAS_PERMS_BY_NAME(QUOTENAME(a.SchemaName)+'.'+QUOTENAME(a.ObjectName),'OBJECT',p.PermissionName) AS actual,
 CASE WHEN HAS_PERMS_BY_NAME(QUOTENAME(a.SchemaName)+'.'+QUOTENAME(a.ObjectName),'OBJECT',p.PermissionName) IS NULL
 THEN 'INDETERMINADO'
 WHEN HAS_PERMS_BY_NAME(QUOTENAME(a.SchemaName)+'.'+QUOTENAME(a.ObjectName),'OBJECT',p.PermissionName)=p.Expected
 THEN 'CONFORME' ELSE 'NO CONFORME' END AS result
 FROM @ApprovedObjects a CROSS APPLY (VALUES('SELECT',a.ExpectedSelect),('INSERT',a.ExpectedInsert),
 ('UPDATE',a.ExpectedUpdate),('DELETE',a.ExpectedDelete)) p(PermissionName,Expected);
IF EXISTS (SELECT 1 FROM @ApprovedObjects a CROSS APPLY
 (VALUES('SELECT',a.ExpectedSelect),('INSERT',a.ExpectedInsert),
 ('UPDATE',a.ExpectedUpdate),('DELETE',a.ExpectedDelete)) p(PermissionName,Expected)
 WHERE ISNULL(HAS_PERMS_BY_NAME(QUOTENAME(a.SchemaName)+'.'+QUOTENAME(a.ObjectName),'OBJECT',p.PermissionName),-1)<>p.Expected)
 THROW 51306, 'Permisos de objeto no conformes o indeterminados; contrastar inventario administrativo.', 1;
-- Procedencia visible, incluidos public y roles; no equiparar falta de GRANT a DENY.
SELECT t.name AS source_identity,x.state_desc,x.class_desc,x.permission_name,x.major_id,x.minor_id,
 CASE WHEN t.principal_id=USER_ID() THEN 'DIRECTO' ELSE 'HEREDADO' END AS origin
 FROM sys.database_permissions x JOIN sys.user_token t ON t.principal_id=x.grantee_principal_id;
SELECT s.name,o.name,USER_NAME(COALESCE(o.principal_id,s.principal_id)) AS effective_owner
 FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id WHERE o.is_ms_shipped=0;
-- NO usar estos metadatos filtrados para descartar privilegios invisibles.
-- 00-inspect + 05-preflight administrativos contrastan inexistencia, columnas,
-- propietarios, roles, public/guest y modulos. Este script no sustituye esa evidencia.
