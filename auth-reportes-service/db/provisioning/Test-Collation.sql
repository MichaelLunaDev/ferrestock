-- F2A-FIX-007: solo SELECT/DECLARE/IF/THROW; NO ejecuta preflight ni DDL/DML persistente.
SET NOCOUNT ON;
DECLARE @Acl TABLE(PermissionName nvarchar(128), State char(1));
DECLARE @Objects TABLE(ObjectType char(2));
-- Compilar/ejecutar las tres familias de comparaciones sobre catalogos reales.
-- Las variables estan vacias: estas consultas no afirman permisos efectivos.
SELECT N'auth_dev' AS database_name,COUNT_BIG(*) AS matched FROM [ferrestock_auth_dev].sys.database_permissions x
JOIN @Acl a ON a.PermissionName COLLATE Latin1_General_100_BIN2=x.permission_name AND a.State COLLATE Latin1_General_100_BIN2=x.state;
SELECT N'auth_test' AS database_name,COUNT_BIG(*) AS matched FROM [ferrestock_auth_test].sys.database_permissions x
JOIN @Acl a ON a.PermissionName COLLATE Latin1_General_100_BIN2=x.permission_name AND a.State COLLATE Latin1_General_100_BIN2=x.state;
SELECT N'ventas' AS database_name,COUNT_BIG(*) AS matched FROM [ferrestock_ventas].sys.database_permissions x
JOIN @Acl a ON a.PermissionName COLLATE Latin1_General_100_BIN2=x.permission_name AND a.State COLLATE Latin1_General_100_BIN2=x.state;
SELECT N'auth_dev.type' AS expression,COUNT_BIG(*) AS matched FROM [ferrestock_auth_dev].sys.objects o
JOIN @Objects a ON a.ObjectType COLLATE Latin1_General_100_BIN2=o.type;
SELECT N'auth_test.type' AS expression,COUNT_BIG(*) AS matched FROM [ferrestock_auth_test].sys.objects o
JOIN @Objects a ON a.ObjectType COLLATE Latin1_General_100_BIN2=o.type;

-- Semantica: la comparacion exacta pasa; variantes de caso, acento y ancho no.
IF N'CONNECT' COLLATE Latin1_General_100_BIN2<>N'CONNECT'
 THROW 51280,'Regresion: igualdad exacta.',1;
IF N'CONNECT' COLLATE Latin1_General_100_BIN2=N'connect'
 OR N'ROLE' COLLATE Latin1_General_100_BIN2=(N'R'+NCHAR(211)+N'LE')
 OR N'A' COLLATE Latin1_General_100_BIN2=NCHAR(65313)
 OR 'G' COLLATE Latin1_General_100_BIN2='g'
 OR 'U' COLLATE Latin1_General_100_BIN2='u'
 THROW 51281,'Regresion: variante fuera de allowlist aceptada.',1;
SELECT N'PASS: cinco consultas de catalogos y semantica BIN2; sin cambios persistentes.' AS result;
