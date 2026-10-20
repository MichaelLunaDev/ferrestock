-- F2A: guardia local de lista cerrada. El helper concatena este lote antes de cada etapa.
-- Parametros ADO.NET: @ExpectedMachine, @ExpectedDatabase. No usar SQLCMD ni SSMS directamente.
SET NOCOUNT ON;
IF @@TRANCOUNT<>0 OR XACT_STATE()<>0 THROW 51100, 'Se requiere conexion nueva sin transaccion.', 1;
IF ISNULL(IS_SRVROLEMEMBER('sysadmin'),0)<>1
 THROW 51101, 'Se requiere administrador con visibilidad completa para preflight.', 1;
IF @ExpectedMachine IS NULL OR CONVERT(nvarchar(128),SERVERPROPERTY('MachineName'))<>@ExpectedMachine
 OR CONVERT(nvarchar(128),SERVERPROPERTY('ServerName'))<>@ExpectedMachine
 OR SERVERPROPERTY('InstanceName') IS NOT NULL
 THROW 51102, 'Identidad SQL no coincide con el contenedor local.', 1;
IF CONVERT(nvarchar(128),SERVERPROPERTY('ProductVersion'))<>N'16.0.4250.1'
 OR CONVERT(nvarchar(128),SERVERPROPERTY('Edition'))<>N'Developer Edition (64-bit)'
 THROW 51103, 'Version o edicion distinta de la revision aprobada.', 1;
IF @ExpectedDatabase NOT IN (N'master',N'ferrestock_auth_dev',N'ferrestock_auth_test',N'ferrestock_ventas')
 OR @ExpectedDatabase IS NULL OR DB_NAME()<>@ExpectedDatabase
 THROW 51104, 'Base actual no autorizada.', 1;
IF (SELECT COUNT(*) FROM sys.databases WHERE name IN
 ('ferrestock_auth_dev','ferrestock_auth_test','ferrestock_ventas') AND state_desc='ONLINE'
 AND is_trustworthy_on=0 AND is_db_chaining_on=0)<>3
 THROW 51105, 'Bases ausentes/offline o aislamiento deshabilitado.', 1;
IF EXISTS (SELECT 1 FROM sys.configurations WHERE name='cross db ownership chaining' AND value_in_use<>0)
 THROW 51106, 'Encadenamiento global entre bases habilitado.', 1;

DECLARE @F2Logins TABLE (LoginName sysname PRIMARY KEY, DefaultDb sysname);
INSERT @F2Logins VALUES
 ('ferrestock_auth_app','ferrestock_auth_dev'),('ferrestock_auth_migrator','ferrestock_auth_dev'),
 ('ferrestock_auth_test_app','ferrestock_auth_test'),('ferrestock_report_reader','ferrestock_ventas');
IF EXISTS (SELECT 1 FROM sys.server_principals p JOIN @F2Logins a ON a.LoginName=p.name
 LEFT JOIN sys.sql_logins l ON l.principal_id=p.principal_id
 WHERE p.type<>'S' OR p.is_disabled<>0 OR DATALENGTH(p.sid)<>16 OR p.sid=0x01
 OR p.default_language_name<>N'us_english' OR p.default_database_name<>a.DefaultDb OR p.credential_id IS NOT NULL
 OR ISNULL(l.is_policy_checked,0)<>1 OR ISNULL(l.is_expiration_checked,1)<>0
 OR ISNULL(CONVERT(int,LOGINPROPERTY(p.name,'IsLocked')),1)<>0
 OR ISNULL(CONVERT(int,LOGINPROPERTY(p.name,'IsMustChange')),1)<>0)
 THROW 51107, 'Login existente incompatible; no se modifica.', 1;
IF EXISTS (SELECT 1 FROM sys.server_role_members m JOIN sys.server_principals p
 ON p.principal_id=m.member_principal_id JOIN @F2Logins a ON a.LoginName=p.name)
 THROW 51108, 'Cuenta prevista con rol de servidor.', 1;
IF EXISTS (SELECT 1 FROM sys.server_permissions x JOIN sys.server_principals p
 ON p.principal_id=x.grantee_principal_id JOIN @F2Logins a ON a.LoginName=p.name
 WHERE NOT (x.class=100 AND x.major_id=0 AND x.permission_name='CONNECT SQL' AND x.state='G'))
 THROW 51109, 'Permiso directo de servidor no aprobado.', 1;
IF EXISTS (SELECT 1 FROM sys.databases d JOIN sys.server_principals p ON p.sid=d.owner_sid
 JOIN @F2Logins a ON a.LoginName=p.name)
 THROW 51110, 'Cuenta prevista propietaria de base.', 1;
IF EXISTS (SELECT 1 FROM sys.server_permissions x JOIN sys.server_principals p
 ON x.class=101 AND x.major_id=p.principal_id JOIN @F2Logins a ON a.LoginName=p.name)
 THROW 51111, 'Relacion de permiso sobre login no aprobada.', 1;
-- public: conservar permisos estandar; no aceptar control/impersonacion/acceso global.
IF EXISTS (SELECT 1 FROM sys.server_permissions x WHERE x.grantee_principal_id=(SELECT principal_id FROM sys.server_principals WHERE name='public' AND type='R')
 AND NOT (x.state='G' AND
 ((x.class=100 AND x.permission_name IN ('CONNECT SQL','VIEW ANY DATABASE'))
 OR (x.class=105 AND x.permission_name='CONNECT' AND EXISTS
 (SELECT 1 FROM sys.endpoints e WHERE e.endpoint_id=x.major_id AND e.type_desc='TSQL')))))
 THROW 51112, 'Permisos de public de servidor fuera de baseline.', 1;

-- Allowlist de objetos Auth: VACIA en F2A. Agregar nombres/tipos y permisos
-- exactos solo tras revision de migraciones; no detectar/aceptar automaticamente.
DECLARE @F2AuthObjects TABLE (DatabaseName sysname, ObjectName sysname, ObjectType char(2),
 PRIMARY KEY(DatabaseName,ObjectName));
DECLARE @F2Acl TABLE (DatabaseName sysname, PrincipalName sysname, Class int,
 ScopeName sysname, ObjectName sysname, PermissionName nvarchar(128), State char(1));
-- permission_name/state/type del catalogo usan Latin1_General_CI_AS_KS_WS,
-- distinta del default local. BIN2 explicito solo en estas comparaciones de
-- codigos de allowlist evita 468 sin aceptar variantes de mayusculas o acentos.

-- SQL 2022 16.0.4250.1: SELECT de public sobre objetos Resource no enumerados
-- por sys.all_objects. Verificados individualmente contra model e IsMSShipped.
-- Solo estas identidades exactas; no aceptar ids negativos desconocidos.
DECLARE @F2HiddenSystemSelect TABLE (ObjectId int PRIMARY KEY, ObjectName sysname);
INSERT @F2HiddenSystemSelect VALUES
 (-675,'sysquery_store_runtime_stats_2017'),(-674,'sysexternal_data_sources_2017'),
 (-667,'sysquery_store_query_hints_2019'),(-656,'materialized_views'),
 (-654,'sysquery_store_wait_stats_2019'),(-652,'sysquery_store_runtime_stats_2019'),
 (-649,'sysdatabase_principals_2021'),(-648,'sysserver_principals_2021'),
 (-647,'syscolumn_store_segments_2020'),(-641,'sysquery_context_settings_2016'),
 (-640,'sysquery_store_runtime_stats_interval_2016'),(-638,'sysquery_store_runtime_stats_2016'),
 (-637,'sysexternal_tables_2016'),(-636,'sysexternal_file_formats_2016'),
 (-635,'sysexternal_data_sources_2016'),(-628,'syscolumns_2019'),
 (-627,'pdw_permanent_table_mappings'),(-612,'pdw_materialized_view_column_distribution_properties'),
 (-611,'pdw_materialized_view_distribution_properties'),(-610,'pdw_materialized_view_mappings'),
 (-593,'pdw_table_mappings'),(-592,'pdw_index_mappings'),
 (-591,'pdw_column_distribution_properties'),(-590,'pdw_table_distribution_properties');

INSERT @F2Acl VALUES
 -- CREATE USER auth_owner WITHOUT LOGIN deja CONNECT/G a nivel DATABASE.
 -- Excepcion solo en dev; 51124 mantiene type=S y authentication_type=NONE.
 ('ferrestock_auth_dev','auth_owner',0,'','','CONNECT','G'),
 ('ferrestock_auth_dev','ferrestock_auth_app',0,'','','CONNECT','G'),
 ('ferrestock_auth_dev','ferrestock_auth_app',0,'','','CREATE TABLE','D'),
 ('ferrestock_auth_dev','ferrestock_auth_app',0,'','','CREATE VIEW','D'),
 ('ferrestock_auth_dev','ferrestock_auth_app',0,'','','CREATE PROCEDURE','D'),
 ('ferrestock_auth_dev','ferrestock_auth_app',0,'','','CREATE FUNCTION','D'),
 ('ferrestock_auth_dev','ferrestock_auth_app',0,'','','ALTER ANY SCHEMA','D'),
 ('ferrestock_auth_dev','ferrestock_auth_test_app',0,'','','CONNECT','D'),
 ('ferrestock_auth_dev','ferrestock_auth_migrator',0,'','','CONNECT','G'),
 ('ferrestock_auth_dev','ferrestock_auth_migrator',0,'','','CREATE TABLE','G'),
 ('ferrestock_auth_dev','ferrestock_auth_migrator',3,'auth','','ALTER','G'),
 ('ferrestock_auth_dev','ferrestock_auth_migrator',3,'auth','','REFERENCES','G'),
 ('ferrestock_auth_dev','ferrestock_auth_migrator',3,'auth','','SELECT','G'),
 ('ferrestock_auth_dev','ferrestock_auth_migrator',3,'auth','','INSERT','G'),
 ('ferrestock_auth_dev','ferrestock_auth_migrator',3,'auth','','UPDATE','G'),
 ('ferrestock_auth_dev','ferrestock_auth_migrator',3,'auth','','DELETE','G'),
 ('ferrestock_auth_dev','ferrestock_auth_migrator',3,'auth','','VIEW DEFINITION','G');

-- Inventario y validacion cerrada: ferrestock_auth_dev
IF EXISTS (SELECT 1 FROM [ferrestock_auth_dev].sys.database_principals p
 JOIN sys.server_principals l ON l.sid=p.sid JOIN @F2Logins a ON a.LoginName=l.name
 WHERE p.name<>l.name OR p.type<>'S'
 OR p.name NOT IN ('ferrestock_auth_app','ferrestock_auth_migrator','ferrestock_auth_test_app'))
 THROW 51113, 'Mapeo de identidad no autorizado entre bases.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_auth_dev].sys.database_principals p
 WHERE p.name IN (SELECT LoginName FROM @F2Logins)
 AND (p.type<>'S' OR p.authentication_type<>1 OR SUSER_SID(p.name) IS NULL OR p.sid<>SUSER_SID(p.name)))
 THROW 51114, 'Tipo/SID de usuario no corresponde al login.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_auth_dev].sys.database_role_members m
 JOIN [ferrestock_auth_dev].sys.database_principals p ON p.principal_id=m.member_principal_id
 WHERE p.name='auth_owner' OR p.name IN (SELECT LoginName FROM @F2Logins))
 THROW 51115, 'Membresia de rol de base no aprobada.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_auth_dev].sys.schemas s JOIN [ferrestock_auth_dev].sys.database_principals p
 ON p.principal_id=s.principal_id WHERE p.name IN (SELECT LoginName FROM @F2Logins)
 OR (p.name='auth_owner' AND (s.name<>'auth' OR 'ferrestock_auth_dev'='ferrestock_ventas')))
 THROW 51116, 'Propiedad de esquema inesperada.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_auth_dev].sys.objects o JOIN [ferrestock_auth_dev].sys.database_principals p
 ON p.principal_id=o.principal_id WHERE p.name='auth_owner' OR p.name IN (SELECT LoginName FROM @F2Logins))
 THROW 51117, 'Propietario explicito de objeto no aprobado.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_auth_dev].sys.database_principals p
 WHERE p.owning_principal_id IN (SELECT principal_id FROM [ferrestock_auth_dev].sys.database_principals
 WHERE name='auth_owner' OR name IN (SELECT LoginName FROM @F2Logins)))
 THROW 51118, 'Propiedad de principal/rol no aprobada.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_auth_dev].sys.database_permissions x
 JOIN [ferrestock_auth_dev].sys.database_principals p ON p.principal_id=x.grantee_principal_id
 WHERE (p.name='auth_owner' OR p.name IN (SELECT LoginName FROM @F2Logins))
 AND NOT EXISTS (SELECT 1 FROM @F2Acl a WHERE a.DatabaseName='ferrestock_auth_dev' AND a.PrincipalName=p.name
 AND a.Class=x.class AND a.PermissionName COLLATE Latin1_General_100_BIN2=x.permission_name AND a.State COLLATE Latin1_General_100_BIN2=x.state AND x.minor_id=0
 AND ((x.class=0 AND x.major_id=0) OR (x.class=3 AND EXISTS
 (SELECT 1 FROM [ferrestock_auth_dev].sys.schemas s WHERE s.schema_id=x.major_id AND s.name=a.ScopeName))
 OR (x.class=1 AND EXISTS (SELECT 1 FROM [ferrestock_auth_dev].sys.objects o
 JOIN [ferrestock_auth_dev].sys.schemas s ON s.schema_id=o.schema_id
 WHERE o.object_id=x.major_id AND s.name=a.ScopeName AND o.name=a.ObjectName)))))
 THROW 51119, 'ACL directa/esquema/objeto/columna fuera de allowlist.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_auth_dev].sys.database_permissions x
 JOIN [ferrestock_auth_dev].sys.database_principals p ON x.class=4 AND x.major_id=p.principal_id
 WHERE p.name='auth_owner' OR p.name IN (SELECT LoginName FROM @F2Logins))
 THROW 51120, 'Permisos sobre identidad (incluida suplantacion) no aprobados.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_auth_dev].sys.database_permissions x
 JOIN [ferrestock_auth_dev].sys.database_principals p ON p.principal_id=x.grantee_principal_id
 WHERE p.name IN ('public','guest') AND NOT
 ((x.class=1 AND x.minor_id=0 AND x.state='G' AND x.permission_name='SELECT' AND
 (EXISTS (SELECT 1 FROM [ferrestock_auth_dev].sys.all_objects o WHERE o.object_id=x.major_id AND o.is_ms_shipped=1)
 OR (p.name='public' AND OBJECT_SCHEMA_NAME(x.major_id,DB_ID('ferrestock_auth_dev'))='sys'
 AND x.major_id<0
 AND EXISTS (SELECT 1 FROM @F2HiddenSystemSelect a WHERE a.ObjectId=x.major_id
 AND a.ObjectName COLLATE Latin1_General_100_BIN2=OBJECT_NAME(x.major_id,DB_ID('ferrestock_auth_dev'))))))
 OR (p.name='public' AND x.class=0 AND x.major_id=0 AND x.minor_id=0 AND x.state='G' AND x.permission_name IN
 ('VIEW ANY COLUMN ENCRYPTION KEY DEFINITION','VIEW ANY COLUMN MASTER KEY DEFINITION'))
 OR (p.name='guest' AND x.class=0 AND x.major_id=0 AND x.minor_id=0 AND x.state='D' AND x.permission_name='CONNECT')))
 THROW 51121, 'public/guest tiene acceso no estandar a datos o identidades.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_auth_dev].sys.sql_modules WHERE execute_as_principal_id IS NOT NULL)
 THROW 51122, 'Modulo con EXECUTE AS requiere revision de acceso indirecto.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_auth_dev].sys.assembly_modules)
 OR EXISTS (SELECT 1 FROM [ferrestock_auth_dev].sys.crypt_properties)
 THROW 51123, 'CLR o firmas de modulos requieren revision adicional.', 1;

IF EXISTS (SELECT 1 FROM [ferrestock_auth_dev].sys.database_principals WHERE name='auth_owner'
 AND (type<>'S' OR authentication_type<>0))
 THROW 51124, 'auth_owner no es usuario sin login.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_auth_dev].sys.schemas s
 LEFT JOIN [ferrestock_auth_dev].sys.database_principals p ON p.principal_id=s.principal_id
 WHERE s.name='auth' AND ISNULL(p.name,'')<>'auth_owner')
 THROW 51125, 'Propietario efectivo del esquema auth inesperado.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_auth_dev].sys.objects o JOIN [ferrestock_auth_dev].sys.schemas s ON s.schema_id=o.schema_id
 WHERE o.is_ms_shipped=0 AND o.parent_object_id=0 AND
 (s.name<>'auth' OR o.principal_id IS NOT NULL OR NOT EXISTS
 (SELECT 1 FROM @F2AuthObjects a WHERE a.DatabaseName='ferrestock_auth_dev' AND a.ObjectName=o.name AND a.ObjectType COLLATE Latin1_General_100_BIN2=o.type)))
 THROW 51126, 'Objeto Auth fuera de lista aprobada (vacia en F2A).', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_auth_dev].sys.triggers WHERE is_ms_shipped=0)
 THROW 51127, 'Trigger Auth requiere revision de efectos indirectos.', 1;

INSERT @F2Acl VALUES
 -- Misma ACL de CREATE USER WITHOUT LOGIN ya demostrada en AuthDev.
 ('ferrestock_auth_test','auth_owner',0,'','','CONNECT','G'),
 ('ferrestock_auth_test','ferrestock_auth_test_app',0,'','','CONNECT','G'),
 ('ferrestock_auth_test','ferrestock_auth_test_app',0,'','','CREATE TABLE','D'),
 ('ferrestock_auth_test','ferrestock_auth_test_app',0,'','','CREATE VIEW','D'),
 ('ferrestock_auth_test','ferrestock_auth_test_app',0,'','','CREATE PROCEDURE','D'),
 ('ferrestock_auth_test','ferrestock_auth_test_app',0,'','','CREATE FUNCTION','D'),
 ('ferrestock_auth_test','ferrestock_auth_test_app',0,'','','ALTER ANY SCHEMA','D');

-- Inventario y validacion cerrada: ferrestock_auth_test
IF EXISTS (SELECT 1 FROM [ferrestock_auth_test].sys.database_principals p
 JOIN sys.server_principals l ON l.sid=p.sid JOIN @F2Logins a ON a.LoginName=l.name
 WHERE p.name<>l.name OR p.type<>'S'
 OR p.name NOT IN ('ferrestock_auth_test_app'))
 THROW 51113, 'Mapeo de identidad no autorizado entre bases.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_auth_test].sys.database_principals p
 WHERE p.name IN (SELECT LoginName FROM @F2Logins)
 AND (p.type<>'S' OR p.authentication_type<>1 OR SUSER_SID(p.name) IS NULL OR p.sid<>SUSER_SID(p.name)))
 THROW 51114, 'Tipo/SID de usuario no corresponde al login.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_auth_test].sys.database_role_members m
 JOIN [ferrestock_auth_test].sys.database_principals p ON p.principal_id=m.member_principal_id
 WHERE p.name='auth_owner' OR p.name IN (SELECT LoginName FROM @F2Logins))
 THROW 51115, 'Membresia de rol de base no aprobada.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_auth_test].sys.schemas s JOIN [ferrestock_auth_test].sys.database_principals p
 ON p.principal_id=s.principal_id WHERE p.name IN (SELECT LoginName FROM @F2Logins)
 OR (p.name='auth_owner' AND (s.name<>'auth' OR 'ferrestock_auth_test'='ferrestock_ventas')))
 THROW 51116, 'Propiedad de esquema inesperada.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_auth_test].sys.objects o JOIN [ferrestock_auth_test].sys.database_principals p
 ON p.principal_id=o.principal_id WHERE p.name='auth_owner' OR p.name IN (SELECT LoginName FROM @F2Logins))
 THROW 51117, 'Propietario explicito de objeto no aprobado.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_auth_test].sys.database_principals p
 WHERE p.owning_principal_id IN (SELECT principal_id FROM [ferrestock_auth_test].sys.database_principals
 WHERE name='auth_owner' OR name IN (SELECT LoginName FROM @F2Logins)))
 THROW 51118, 'Propiedad de principal/rol no aprobada.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_auth_test].sys.database_permissions x
 JOIN [ferrestock_auth_test].sys.database_principals p ON p.principal_id=x.grantee_principal_id
 WHERE (p.name='auth_owner' OR p.name IN (SELECT LoginName FROM @F2Logins))
 AND NOT EXISTS (SELECT 1 FROM @F2Acl a WHERE a.DatabaseName='ferrestock_auth_test' AND a.PrincipalName=p.name
 AND a.Class=x.class AND a.PermissionName COLLATE Latin1_General_100_BIN2=x.permission_name AND a.State COLLATE Latin1_General_100_BIN2=x.state AND x.minor_id=0
 AND ((x.class=0 AND x.major_id=0) OR (x.class=3 AND EXISTS
 (SELECT 1 FROM [ferrestock_auth_test].sys.schemas s WHERE s.schema_id=x.major_id AND s.name=a.ScopeName))
 OR (x.class=1 AND EXISTS (SELECT 1 FROM [ferrestock_auth_test].sys.objects o
 JOIN [ferrestock_auth_test].sys.schemas s ON s.schema_id=o.schema_id
 WHERE o.object_id=x.major_id AND s.name=a.ScopeName AND o.name=a.ObjectName)))))
 THROW 51119, 'ACL directa/esquema/objeto/columna fuera de allowlist.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_auth_test].sys.database_permissions x
 JOIN [ferrestock_auth_test].sys.database_principals p ON x.class=4 AND x.major_id=p.principal_id
 WHERE p.name='auth_owner' OR p.name IN (SELECT LoginName FROM @F2Logins))
 THROW 51120, 'Permisos sobre identidad (incluida suplantacion) no aprobados.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_auth_test].sys.database_permissions x
 JOIN [ferrestock_auth_test].sys.database_principals p ON p.principal_id=x.grantee_principal_id
 WHERE p.name IN ('public','guest') AND NOT
 ((x.class=1 AND x.minor_id=0 AND x.state='G' AND x.permission_name='SELECT' AND
 (EXISTS (SELECT 1 FROM [ferrestock_auth_test].sys.all_objects o WHERE o.object_id=x.major_id AND o.is_ms_shipped=1)
 OR (p.name='public' AND OBJECT_SCHEMA_NAME(x.major_id,DB_ID('ferrestock_auth_test'))='sys'
 AND x.major_id<0
 AND EXISTS (SELECT 1 FROM @F2HiddenSystemSelect a WHERE a.ObjectId=x.major_id
 AND a.ObjectName COLLATE Latin1_General_100_BIN2=OBJECT_NAME(x.major_id,DB_ID('ferrestock_auth_test'))))))
 OR (p.name='public' AND x.class=0 AND x.major_id=0 AND x.minor_id=0 AND x.state='G' AND x.permission_name IN
 ('VIEW ANY COLUMN ENCRYPTION KEY DEFINITION','VIEW ANY COLUMN MASTER KEY DEFINITION'))
 OR (p.name='guest' AND x.class=0 AND x.major_id=0 AND x.minor_id=0 AND x.state='D' AND x.permission_name='CONNECT')))
 THROW 51121, 'public/guest tiene acceso no estandar a datos o identidades.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_auth_test].sys.sql_modules WHERE execute_as_principal_id IS NOT NULL)
 THROW 51122, 'Modulo con EXECUTE AS requiere revision de acceso indirecto.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_auth_test].sys.assembly_modules)
 OR EXISTS (SELECT 1 FROM [ferrestock_auth_test].sys.crypt_properties)
 THROW 51123, 'CLR o firmas de modulos requieren revision adicional.', 1;

IF EXISTS (SELECT 1 FROM [ferrestock_auth_test].sys.database_principals WHERE name='auth_owner'
 AND (type<>'S' OR authentication_type<>0))
 THROW 51124, 'auth_owner no es usuario sin login.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_auth_test].sys.schemas s
 LEFT JOIN [ferrestock_auth_test].sys.database_principals p ON p.principal_id=s.principal_id
 WHERE s.name='auth' AND ISNULL(p.name,'')<>'auth_owner')
 THROW 51125, 'Propietario efectivo del esquema auth inesperado.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_auth_test].sys.objects o JOIN [ferrestock_auth_test].sys.schemas s ON s.schema_id=o.schema_id
 WHERE o.is_ms_shipped=0 AND o.parent_object_id=0 AND
 (s.name<>'auth' OR o.principal_id IS NOT NULL OR NOT EXISTS
 (SELECT 1 FROM @F2AuthObjects a WHERE a.DatabaseName='ferrestock_auth_test' AND a.ObjectName=o.name AND a.ObjectType COLLATE Latin1_General_100_BIN2=o.type)))
 THROW 51126, 'Objeto Auth fuera de lista aprobada (vacia en F2A).', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_auth_test].sys.triggers WHERE is_ms_shipped=0)
 THROW 51127, 'Trigger Auth requiere revision de efectos indirectos.', 1;

INSERT @F2Acl VALUES
 ('ferrestock_ventas','ferrestock_report_reader',0,'','','CONNECT','G'),
 ('ferrestock_ventas','ferrestock_report_reader',0,'','','CREATE TABLE','D'),
 ('ferrestock_ventas','ferrestock_report_reader',0,'','','CREATE VIEW','D'),
 ('ferrestock_ventas','ferrestock_report_reader',0,'','','CREATE PROCEDURE','D'),
 ('ferrestock_ventas','ferrestock_report_reader',0,'','','CREATE FUNCTION','D'),
 ('ferrestock_ventas','ferrestock_report_reader',0,'','','ALTER ANY SCHEMA','D'),
 ('ferrestock_ventas','ferrestock_report_reader',0,'','','INSERT','D'),
 ('ferrestock_ventas','ferrestock_report_reader',0,'','','UPDATE','D'),
 ('ferrestock_ventas','ferrestock_report_reader',0,'','','DELETE','D'),
 ('ferrestock_ventas','ferrestock_report_reader',0,'','','EXECUTE','D');

-- Inventario y validacion cerrada: ferrestock_ventas
IF EXISTS (SELECT 1 FROM [ferrestock_ventas].sys.database_principals p
 JOIN sys.server_principals l ON l.sid=p.sid JOIN @F2Logins a ON a.LoginName=l.name
 WHERE p.name<>l.name OR p.type<>'S'
 OR p.name NOT IN ('ferrestock_report_reader'))
 THROW 51113, 'Mapeo de identidad no autorizado entre bases.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_ventas].sys.database_principals p
 WHERE p.name IN (SELECT LoginName FROM @F2Logins)
 AND (p.type<>'S' OR p.authentication_type<>1 OR SUSER_SID(p.name) IS NULL OR p.sid<>SUSER_SID(p.name)))
 THROW 51114, 'Tipo/SID de usuario no corresponde al login.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_ventas].sys.database_role_members m
 JOIN [ferrestock_ventas].sys.database_principals p ON p.principal_id=m.member_principal_id
 WHERE p.name='auth_owner' OR p.name IN (SELECT LoginName FROM @F2Logins))
 THROW 51115, 'Membresia de rol de base no aprobada.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_ventas].sys.schemas s JOIN [ferrestock_ventas].sys.database_principals p
 ON p.principal_id=s.principal_id WHERE p.name IN (SELECT LoginName FROM @F2Logins)
 OR (p.name='auth_owner' AND (s.name<>'auth' OR 'ferrestock_ventas'='ferrestock_ventas')))
 THROW 51116, 'Propiedad de esquema inesperada.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_ventas].sys.objects o JOIN [ferrestock_ventas].sys.database_principals p
 ON p.principal_id=o.principal_id WHERE p.name='auth_owner' OR p.name IN (SELECT LoginName FROM @F2Logins))
 THROW 51117, 'Propietario explicito de objeto no aprobado.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_ventas].sys.database_principals p
 WHERE p.owning_principal_id IN (SELECT principal_id FROM [ferrestock_ventas].sys.database_principals
 WHERE name='auth_owner' OR name IN (SELECT LoginName FROM @F2Logins)))
 THROW 51118, 'Propiedad de principal/rol no aprobada.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_ventas].sys.database_permissions x
 JOIN [ferrestock_ventas].sys.database_principals p ON p.principal_id=x.grantee_principal_id
 WHERE (p.name='auth_owner' OR p.name IN (SELECT LoginName FROM @F2Logins))
 AND NOT EXISTS (SELECT 1 FROM @F2Acl a WHERE a.DatabaseName='ferrestock_ventas' AND a.PrincipalName=p.name
 AND a.Class=x.class AND a.PermissionName COLLATE Latin1_General_100_BIN2=x.permission_name AND a.State COLLATE Latin1_General_100_BIN2=x.state AND x.minor_id=0
 AND ((x.class=0 AND x.major_id=0) OR (x.class=3 AND EXISTS
 (SELECT 1 FROM [ferrestock_ventas].sys.schemas s WHERE s.schema_id=x.major_id AND s.name=a.ScopeName))
 OR (x.class=1 AND EXISTS (SELECT 1 FROM [ferrestock_ventas].sys.objects o
 JOIN [ferrestock_ventas].sys.schemas s ON s.schema_id=o.schema_id
 WHERE o.object_id=x.major_id AND s.name=a.ScopeName AND o.name=a.ObjectName)))))
 THROW 51119, 'ACL directa/esquema/objeto/columna fuera de allowlist.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_ventas].sys.database_permissions x
 JOIN [ferrestock_ventas].sys.database_principals p ON x.class=4 AND x.major_id=p.principal_id
 WHERE p.name='auth_owner' OR p.name IN (SELECT LoginName FROM @F2Logins))
 THROW 51120, 'Permisos sobre identidad (incluida suplantacion) no aprobados.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_ventas].sys.database_permissions x
 JOIN [ferrestock_ventas].sys.database_principals p ON p.principal_id=x.grantee_principal_id
 WHERE p.name IN ('public','guest') AND NOT
 ((x.class=1 AND x.minor_id=0 AND x.state='G' AND x.permission_name='SELECT' AND
 (EXISTS (SELECT 1 FROM [ferrestock_ventas].sys.all_objects o WHERE o.object_id=x.major_id AND o.is_ms_shipped=1)
 OR (p.name='public' AND OBJECT_SCHEMA_NAME(x.major_id,DB_ID('ferrestock_ventas'))='sys'
 AND x.major_id<0
 AND EXISTS (SELECT 1 FROM @F2HiddenSystemSelect a WHERE a.ObjectId=x.major_id
 AND a.ObjectName COLLATE Latin1_General_100_BIN2=OBJECT_NAME(x.major_id,DB_ID('ferrestock_ventas'))))))
 OR (p.name='public' AND x.class=0 AND x.major_id=0 AND x.minor_id=0 AND x.state='G' AND x.permission_name IN
 ('VIEW ANY COLUMN ENCRYPTION KEY DEFINITION','VIEW ANY COLUMN MASTER KEY DEFINITION'))
 OR (p.name='guest' AND x.class=0 AND x.major_id=0 AND x.minor_id=0 AND x.state='D' AND x.permission_name='CONNECT')))
 THROW 51121, 'public/guest tiene acceso no estandar a datos o identidades.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_ventas].sys.sql_modules WHERE execute_as_principal_id IS NOT NULL)
 THROW 51122, 'Modulo con EXECUTE AS requiere revision de acceso indirecto.', 1;
IF EXISTS (SELECT 1 FROM [ferrestock_ventas].sys.assembly_modules)
 OR EXISTS (SELECT 1 FROM [ferrestock_ventas].sys.crypt_properties)
 THROW 51123, 'CLR o firmas de modulos requieren revision adicional.', 1;

DECLARE @PreflightPassed bit=1;
-- Lote de etapa concatenado por el helper a continuacion. No hay GO.
