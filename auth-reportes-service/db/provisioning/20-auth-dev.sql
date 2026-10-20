-- Ejecutar UNICAMENTE mediante New-Logins.ps1 -Stage (preflight compartido).
IF @PreflightPassed<>1 THROW 51210, 'Falta preflight aprobado.', 1;
IF @@TRANCOUNT<>0 OR XACT_STATE()<>0 THROW 51211, 'Se requiere conexion nueva.', 1;
IF DB_NAME()<>N'ferrestock_auth_dev' THROW 51212, 'Base de etapa incorrecta.', 1;
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
-- PROPUESTA: ejecutar manualmente como administrador tras revision.
-- No crea tablas, no cambia propietarios existentes ni elimina objetos.


IF EXISTS (SELECT 1 FROM sys.databases WHERE database_id=DB_ID() AND (is_trustworthy_on=1 OR is_db_chaining_on=1))
 THROW 51013, 'Revisar TRUSTWORTHY/DB_CHAINING antes de continuar.', 1;
IF USER_ID(N'auth_owner') IS NULL CREATE USER [auth_owner] WITHOUT LOGIN;
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name=N'auth_owner' AND type='S' AND authentication_type=0)
 THROW 51014, 'auth_owner debe ser un usuario sin login.', 1;
IF EXISTS (SELECT 1 FROM sys.schemas WHERE principal_id=USER_ID(N'auth_owner') AND name<>N'auth')
 THROW 51015, 'auth_owner no debe poseer otros esquemas.', 1;
IF SCHEMA_ID(N'auth') IS NULL EXEC(N'CREATE SCHEMA [auth] AUTHORIZATION [auth_owner]');
IF (SELECT principal_id FROM sys.schemas WHERE name=N'auth')<>USER_ID(N'auth_owner')
 THROW 51016, 'Propietario de esquema auth inesperado.', 1;
IF SUSER_ID(N'ferrestock_auth_app') IS NULL THROW 51010, 'Falta login ferrestock_auth_app.', 1;
IF USER_ID(N'ferrestock_auth_app') IS NULL
 CREATE USER [ferrestock_auth_app] FOR LOGIN [ferrestock_auth_app] WITH DEFAULT_SCHEMA=[auth];
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name=N'ferrestock_auth_app' AND sid=SUSER_SID(N'ferrestock_auth_app') AND type='S')
 THROW 51011, 'Usuario existente no corresponde al login esperado.', 1;
IF EXISTS (SELECT 1 FROM sys.database_role_members WHERE member_principal_id=USER_ID(N'ferrestock_auth_app'))
 THROW 51012, 'Usuario existente pertenece a roles; revisar antes de continuar.', 1;

GRANT CONNECT TO [ferrestock_auth_app];
-- Sin DML hasta aprobar objetos concretos en 40-auth-object-grants.sql.
DENY CREATE TABLE, CREATE VIEW, CREATE PROCEDURE, CREATE FUNCTION, ALTER ANY SCHEMA TO [ferrestock_auth_app];

IF SUSER_ID(N'ferrestock_auth_migrator') IS NULL THROW 51010, 'Falta login ferrestock_auth_migrator.', 1;
IF USER_ID(N'ferrestock_auth_migrator') IS NULL
 CREATE USER [ferrestock_auth_migrator] FOR LOGIN [ferrestock_auth_migrator] WITH DEFAULT_SCHEMA=[auth];
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name=N'ferrestock_auth_migrator' AND sid=SUSER_SID(N'ferrestock_auth_migrator') AND type='S')
 THROW 51011, 'Usuario existente no corresponde al login esperado.', 1;
IF EXISTS (SELECT 1 FROM sys.database_role_members WHERE member_principal_id=USER_ID(N'ferrestock_auth_migrator'))
 THROW 51012, 'Usuario existente pertenece a roles; revisar antes de continuar.', 1;

GRANT CONNECT, CREATE TABLE TO [ferrestock_auth_migrator];
GRANT ALTER, REFERENCES, SELECT, INSERT, UPDATE, DELETE, VIEW DEFINITION
 ON SCHEMA::[auth] TO [ferrestock_auth_migrator];
-- ALTER permite modificar/eliminar objetos de auth: solo uso administrativo controlado.
-- No concede CREATE VIEW/PROCEDURE/FUNCTION ni ALTER ANY SCHEMA.
IF SUSER_ID(N'ferrestock_auth_test_app') IS NULL THROW 51010, 'Falta login ferrestock_auth_test_app.', 1;
IF USER_ID(N'ferrestock_auth_test_app') IS NULL
 CREATE USER [ferrestock_auth_test_app] FOR LOGIN [ferrestock_auth_test_app] WITH DEFAULT_SCHEMA=[dbo];
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name=N'ferrestock_auth_test_app' AND sid=SUSER_SID(N'ferrestock_auth_test_app') AND type='S')
 THROW 51011, 'Usuario existente no corresponde al login esperado.', 1;
IF EXISTS (SELECT 1 FROM sys.database_role_members WHERE member_principal_id=USER_ID(N'ferrestock_auth_test_app'))
 THROW 51012, 'Usuario existente pertenece a roles; revisar antes de continuar.', 1;

DENY CONNECT TO [ferrestock_auth_test_app];
-- Mapeo exclusivamente para denegar acceso a dev incluso si guest cambia.
 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
