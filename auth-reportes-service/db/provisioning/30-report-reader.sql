-- Ejecutar UNICAMENTE mediante New-Logins.ps1 -Stage (preflight compartido).
IF @PreflightPassed<>1 THROW 51210, 'Falta preflight aprobado.', 1;
IF @@TRANCOUNT<>0 OR XACT_STATE()<>0 THROW 51211, 'Se requiere conexion nueva.', 1;
IF DB_NAME()<>N'ferrestock_ventas' THROW 51212, 'Base de etapa incorrecta.', 1;
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
-- PROPUESTA: ejecutar como administrador tras revision.


IF SUSER_ID(N'ferrestock_report_reader') IS NULL THROW 51010, 'Falta login ferrestock_report_reader.', 1;
IF USER_ID(N'ferrestock_report_reader') IS NULL
 CREATE USER [ferrestock_report_reader] FOR LOGIN [ferrestock_report_reader] WITH DEFAULT_SCHEMA=[dbo];
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name=N'ferrestock_report_reader' AND sid=SUSER_SID(N'ferrestock_report_reader') AND type='S')
 THROW 51011, 'Usuario existente no corresponde al login esperado.', 1;
IF EXISTS (SELECT 1 FROM sys.database_role_members WHERE member_principal_id=USER_ID(N'ferrestock_report_reader'))
 THROW 51012, 'Usuario existente pertenece a roles; revisar antes de continuar.', 1;

GRANT CONNECT TO [ferrestock_report_reader];
DENY INSERT, UPDATE, DELETE, EXECUTE TO [ferrestock_report_reader];
DENY CREATE TABLE, CREATE VIEW, CREATE PROCEDURE, CREATE FUNCTION, ALTER ANY SCHEMA
 TO [ferrestock_report_reader];
-- No se concede SELECT aqui: ninguna tabla comercial existe en la inspeccion F2A.
 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
