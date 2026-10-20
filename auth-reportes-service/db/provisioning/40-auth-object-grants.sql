-- Ejecutar UNICAMENTE mediante New-Logins.ps1 -Stage (preflight compartido).
IF @PreflightPassed<>1 THROW 51210, 'Falta preflight aprobado.', 1;
IF @@TRANCOUNT<>0 OR XACT_STATE()<>0 THROW 51211, 'Se requiere conexion nueva.', 1;
IF DB_NAME() NOT IN ('ferrestock_auth_dev','ferrestock_auth_test') THROW 51212, 'Base de etapa incorrecta.', 1;
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
-- PROPUESTA. Conectar explicitamente a auth_dev o auth_test como administrador.
-- Aprobar nombres de tablas antes de completar la lista. No conceder permisos
-- sobre historial de migraciones. Usuario/Rol/Sesion aun no estan definidos.

DECLARE @Principal sysname=CASE DB_NAME()
 WHEN 'ferrestock_auth_dev' THEN 'ferrestock_auth_app'
 WHEN 'ferrestock_auth_test' THEN 'ferrestock_auth_test_app' END;
IF @Principal IS NULL THROW 51020, 'Base no autorizada para permisos Auth.', 1;
IF USER_ID(@Principal) IS NULL THROW 51021, 'Falta usuario Auth.', 1;
DECLARE @ApprovedObjects TABLE (TableName sysname PRIMARY KEY);
-- Lista vacia intencional. Tras revision: agregar INSERT a esta variable de tabla.
IF NOT EXISTS (SELECT 1 FROM @ApprovedObjects)
 THROW 51022, 'Lista Auth pendiente de aprobacion; no se otorgaron permisos.', 1;
IF EXISTS (SELECT 1 FROM @ApprovedObjects a WHERE OBJECT_ID(N'auth.'+QUOTENAME(a.TableName),'U') IS NULL)
 THROW 51023, 'Falta tabla Auth aprobada; no se otorgaron permisos.', 1;

DECLARE @Name sysname,@Sql nvarchar(max);
DECLARE objects_cursor CURSOR LOCAL FAST_FORWARD FOR SELECT TableName FROM @ApprovedObjects;
OPEN objects_cursor;
FETCH NEXT FROM objects_cursor INTO @Name;
WHILE @@FETCH_STATUS=0
BEGIN
 SET @Sql=N'GRANT SELECT, INSERT, UPDATE, DELETE ON OBJECT::[auth].'+QUOTENAME(@Name)+N' TO '+QUOTENAME(@Principal)+N';';
 EXEC sys.sp_executesql @Sql;
 FETCH NEXT FROM objects_cursor INTO @Name;
END;
CLOSE objects_cursor;
DEALLOCATE objects_cursor;
 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
