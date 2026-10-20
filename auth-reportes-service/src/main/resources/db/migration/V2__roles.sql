-- PROPUESTA F2B-002A. No ejecutar sin autorizacion posterior.
SET XACT_ABORT ON;
-- A2-FIX: BIN2 distingue caso; DATALENGTH impide equivalencia por espacios finales.
IF NOT ((DB_NAME() IS NOT NULL AND N'ferrestock_auth_dev' IS NOT NULL AND DB_NAME() COLLATE Latin1_General_100_BIN2=N'ferrestock_auth_dev' COLLATE Latin1_General_100_BIN2 AND DATALENGTH(DB_NAME())=DATALENGTH(N'ferrestock_auth_dev')) OR (DB_NAME() IS NOT NULL AND N'ferrestock_auth_test' IS NOT NULL AND DB_NAME() COLLATE Latin1_General_100_BIN2=N'ferrestock_auth_test' COLLATE Latin1_General_100_BIN2 AND DATALENGTH(DB_NAME())=DATALENGTH(N'ferrestock_auth_test')))
 THROW 51400, 'Base de migracion Auth no autorizada.', 1;
IF NOT (ORIGINAL_LOGIN() IS NOT NULL AND N'ferrestock_auth_migrator' IS NOT NULL AND ORIGINAL_LOGIN() COLLATE Latin1_General_100_BIN2=N'ferrestock_auth_migrator' COLLATE Latin1_General_100_BIN2 AND DATALENGTH(ORIGINAL_LOGIN())=DATALENGTH(N'ferrestock_auth_migrator')) OR NOT (USER_NAME() IS NOT NULL AND N'ferrestock_auth_migrator' IS NOT NULL AND USER_NAME() COLLATE Latin1_General_100_BIN2=N'ferrestock_auth_migrator' COLLATE Latin1_General_100_BIN2 AND DATALENGTH(USER_NAME())=DATALENGTH(N'ferrestock_auth_migrator'))
 OR NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE principal_id=USER_ID()
 AND (name IS NOT NULL AND N'ferrestock_auth_migrator' IS NOT NULL AND name COLLATE Latin1_General_100_BIN2=N'ferrestock_auth_migrator' COLLATE Latin1_General_100_BIN2 AND DATALENGTH(name)=DATALENGTH(N'ferrestock_auth_migrator')) AND type='S' AND authentication_type=1
 AND sid=SUSER_SID(N'ferrestock_auth_migrator') AND (default_schema_name IS NOT NULL AND N'auth' IS NOT NULL AND default_schema_name COLLATE Latin1_General_100_BIN2=N'auth' COLLATE Latin1_General_100_BIN2 AND DATALENGTH(default_schema_name)=DATALENGTH(N'auth')))
 THROW 51401, 'Identidad de migracion Auth no autorizada.', 1;
IF NOT EXISTS (SELECT 1 FROM sys.schemas s JOIN sys.database_principals p ON p.principal_id=s.principal_id
 WHERE (s.name IS NOT NULL AND N'auth' IS NOT NULL AND s.name COLLATE Latin1_General_100_BIN2=N'auth' COLLATE Latin1_General_100_BIN2 AND DATALENGTH(s.name)=DATALENGTH(N'auth')) AND (p.name IS NOT NULL AND N'auth_owner' IS NOT NULL AND p.name COLLATE Latin1_General_100_BIN2=N'auth_owner' COLLATE Latin1_General_100_BIN2 AND DATALENGTH(p.name)=DATALENGTH(N'auth_owner')) AND p.type='S' AND p.authentication_type=0)
 THROW 51402, 'Propietario auth no conforme.', 1;
-- El historial versionado controla una sola aplicacion; no ocultar drift con IF EXISTS.
INSERT INTO auth.rol (rol_id,codigo)
VALUES (1,'ADMINISTRADOR'),(2,'VENDEDOR'),(3,'ALMACENERO');