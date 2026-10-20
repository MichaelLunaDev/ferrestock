-- SOLO LECTURA. Ejecutar como administrador para visibilidad completa.
SET NOCOUNT ON;
SELECT SERVERPROPERTY('ProductVersion') AS version, SERVERPROPERTY('Edition') AS edition;
SELECT name,state_desc,is_trustworthy_on,is_db_chaining_on,SUSER_SNAME(owner_sid) AS database_owner
FROM sys.databases WHERE name IN ('ferrestock_ventas','ferrestock_auth_dev','ferrestock_auth_test');
SELECT name,type_desc,is_disabled FROM sys.server_principals WHERE name LIKE 'ferrestock%';
SELECT r.name AS role_name,p.name AS member_name
FROM sys.server_role_members m JOIN sys.server_principals r ON r.principal_id=m.role_principal_id
JOIN sys.server_principals p ON p.principal_id=m.member_principal_id;
SELECT name,value_in_use FROM sys.configurations WHERE name='cross db ownership chaining';

USE [ferrestock_ventas];
SELECT DB_NAME() AS database_name;
SELECT name,USER_NAME(principal_id) AS owner_name FROM sys.schemas;
SELECT s.name AS schema_name,o.name,o.type_desc FROM sys.objects o
JOIN sys.schemas s ON s.schema_id=o.schema_id WHERE o.is_ms_shipped=0;
SELECT name,type_desc,default_schema_name FROM sys.database_principals;
SELECT r.name AS role_name,p.name AS member_name FROM sys.database_role_members m
JOIN sys.database_principals r ON r.principal_id=m.role_principal_id
JOIN sys.database_principals p ON p.principal_id=m.member_principal_id;
SELECT USER_NAME(grantee_principal_id) AS grantee,state_desc,permission_name,class_desc,major_id,minor_id
FROM sys.database_permissions;

USE [ferrestock_auth_dev];
SELECT DB_NAME() AS database_name;
SELECT name,USER_NAME(principal_id) AS owner_name FROM sys.schemas;
SELECT s.name AS schema_name,o.name,o.type_desc FROM sys.objects o
JOIN sys.schemas s ON s.schema_id=o.schema_id WHERE o.is_ms_shipped=0;
SELECT name,type_desc,default_schema_name FROM sys.database_principals;
SELECT r.name AS role_name,p.name AS member_name FROM sys.database_role_members m
JOIN sys.database_principals r ON r.principal_id=m.role_principal_id
JOIN sys.database_principals p ON p.principal_id=m.member_principal_id;
SELECT USER_NAME(grantee_principal_id) AS grantee,state_desc,permission_name,class_desc,major_id,minor_id
FROM sys.database_permissions;

USE [ferrestock_auth_test];
SELECT DB_NAME() AS database_name;
SELECT name,USER_NAME(principal_id) AS owner_name FROM sys.schemas;
SELECT s.name AS schema_name,o.name,o.type_desc FROM sys.objects o
JOIN sys.schemas s ON s.schema_id=o.schema_id WHERE o.is_ms_shipped=0;
SELECT name,type_desc,default_schema_name FROM sys.database_principals;
SELECT r.name AS role_name,p.name AS member_name FROM sys.database_role_members m
JOIN sys.database_principals r ON r.principal_id=m.role_principal_id
JOIN sys.database_principals p ON p.principal_id=m.member_principal_id;
SELECT USER_NAME(grantee_principal_id) AS grantee,state_desc,permission_name,class_desc,major_id,minor_id
FROM sys.database_permissions;

-- H1/H2: propietarios, permisos directos de servidor y relaciones de suplantacion.
SELECT SERVERPROPERTY('MachineName') AS machine_name,SERVERPROPERTY('ServerName') AS server_name,
 SERVERPROPERTY('InstanceName') AS instance_name,DB_NAME() AS current_db,@@TRANCOUNT AS transaction_count;
SELECT p.name,x.class_desc,x.major_id,x.permission_name,x.state_desc
 FROM sys.server_permissions x JOIN sys.server_principals p ON p.principal_id=x.grantee_principal_id;
SELECT name,default_database_name,default_language_name,is_disabled,is_policy_checked,is_expiration_checked,
 DATALENGTH(sid) AS sid_bytes FROM sys.sql_logins WHERE name LIKE 'ferrestock%';

USE [ferrestock_auth_dev];
SELECT DB_NAME() AS database_name,s.name AS schema_name,o.name,o.type_desc,
 USER_NAME(COALESCE(o.principal_id,s.principal_id)) AS effective_owner,
 o.principal_id AS explicit_owner,m.execute_as_principal_id
 FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id
 LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE o.is_ms_shipped=0;
SELECT name,type_desc,owning_principal_id FROM sys.database_principals;
SELECT * FROM sys.crypt_properties;
SELECT object_id,execute_as_principal_id FROM sys.assembly_modules;
SELECT * FROM sys.synonyms;

USE [ferrestock_auth_test];
SELECT DB_NAME() AS database_name,s.name AS schema_name,o.name,o.type_desc,
 USER_NAME(COALESCE(o.principal_id,s.principal_id)) AS effective_owner,
 o.principal_id AS explicit_owner,m.execute_as_principal_id
 FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id
 LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE o.is_ms_shipped=0;
SELECT name,type_desc,owning_principal_id FROM sys.database_principals;
SELECT * FROM sys.crypt_properties;
SELECT object_id,execute_as_principal_id FROM sys.assembly_modules;
SELECT * FROM sys.synonyms;

USE [ferrestock_ventas];
SELECT DB_NAME() AS database_name,s.name AS schema_name,o.name,o.type_desc,
 USER_NAME(COALESCE(o.principal_id,s.principal_id)) AS effective_owner,
 o.principal_id AS explicit_owner,m.execute_as_principal_id
 FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id
 LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE o.is_ms_shipped=0;
SELECT name,type_desc,owning_principal_id FROM sys.database_principals;
SELECT * FROM sys.crypt_properties;
SELECT object_id,execute_as_principal_id FROM sys.assembly_modules;
SELECT * FROM sys.synonyms;
