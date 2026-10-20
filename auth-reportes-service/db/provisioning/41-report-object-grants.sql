-- Ejecutar UNICAMENTE mediante New-Logins.ps1 -Stage (preflight compartido).
IF @PreflightPassed<>1 THROW 51210, 'Falta preflight aprobado.', 1;
IF @@TRANCOUNT<>0 OR XACT_STATE()<>0 THROW 51211, 'Se requiere conexion nueva.', 1;
IF DB_NAME()<>N'ferrestock_ventas' THROW 51212, 'Base de etapa incorrecta.', 1;
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
-- PROPUESTA. Completar solo despues de verificar existencia Y propiedad con Ventas.

IF USER_ID(N'ferrestock_report_reader') IS NULL THROW 51030, 'Falta lector.', 1;
DECLARE @ApprovedObjects TABLE (SchemaName sysname, ObjectName sysname, PRIMARY KEY(SchemaName,ObjectName));
-- Lista vacia: no se presupone dbo ni la creacion de tablas por Hibernate.
-- Candidatos pendientes: ventas, detalle_ventas, productos; clientes solo si es necesario.
IF NOT EXISTS (SELECT 1 FROM @ApprovedObjects)
 THROW 51031, 'Lista comercial pendiente de aprobacion; no se otorgo SELECT.', 1;
IF EXISTS (SELECT 1 FROM @ApprovedObjects a WHERE NOT EXISTS
 (SELECT 1 FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id
  WHERE s.name=a.SchemaName AND o.name=a.ObjectName AND o.type IN ('U','V')))
 THROW 51032, 'Falta tabla/vista comercial aprobada.', 1;

DECLARE @Schema sysname,@Name sysname,@Sql nvarchar(max);
DECLARE objects_cursor CURSOR LOCAL FAST_FORWARD FOR SELECT SchemaName,ObjectName FROM @ApprovedObjects;
OPEN objects_cursor;
FETCH NEXT FROM objects_cursor INTO @Schema,@Name;
WHILE @@FETCH_STATUS=0
BEGIN
 SET @Sql=N'GRANT SELECT ON OBJECT::'+QUOTENAME(@Schema)+N'.'+QUOTENAME(@Name)+N' TO [ferrestock_report_reader];';
 EXEC sys.sp_executesql @Sql;
 FETCH NEXT FROM objects_cursor INTO @Schema,@Name;
END;
CLOSE objects_cursor;
DEALLOCATE objects_cursor;
 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
