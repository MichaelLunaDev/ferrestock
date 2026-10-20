-- Solo mediante New-Logins.ps1: preflight + parametros ADO.NET, sin GO.
IF @PreflightPassed<>1 OR DB_NAME()<>'master' THROW 51200, 'Preflight/base invalidos.', 1;
IF @@TRANCOUNT<>0 THROW 51201, 'Se requiere conexion nueva.', 1;
DECLARE @DefaultDatabase sysname=(SELECT DefaultDb FROM @F2Logins WHERE LoginName=@LoginName);
IF @DefaultDatabase IS NULL THROW 51202, 'Login fuera de lista cerrada.', 1;
IF EXISTS (SELECT 1 FROM sys.server_principals WHERE name=@LoginName)
BEGIN
 SELECT N'EXISTENTE VALIDADO';
 RETURN; -- Preflight valido configuracion completa y SID/mapeos. No pide ni rota clave.
END;
IF @InspectOnly=1
BEGIN
 SELECT N'NUEVO';
 RETURN;
END;
-- ASCII: letras, digitos y !@#$%^&*_-+=. Sin espacios/comillas/Unicode.
IF @LoginPassword IS NULL OR DATALENGTH(@LoginPassword)/2 NOT BETWEEN 16 AND 128
 OR @LoginPassword COLLATE Latin1_General_100_BIN2 LIKE N'%[^A-Za-z0-9!@#$%^&*_+=-]%'
 OR @LoginPassword COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[A-Z]%'
 OR @LoginPassword COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[a-z]%'
 OR @LoginPassword COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[0-9]%'
 OR @LoginPassword COLLATE Latin1_General_100_BIN2 NOT LIKE N'%[!@#$%^&*_+=-]%'
 OR CHARINDEX(LOWER(@LoginName),LOWER(@LoginPassword))>0
 THROW 51203, 'Contrasena fuera de politica ASCII aprobada.', 1;
DECLARE @QuotedPassword nvarchar(258)=QUOTENAME(@LoginPassword,'''');
IF @QuotedPassword IS NULL THROW 51204, 'Longitud fuera del limite de QUOTENAME.', 1;
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @Sql nvarchar(max)=N'CREATE LOGIN '+QUOTENAME(@LoginName)
  +N' WITH PASSWORD='+@QuotedPassword
  +N', CHECK_POLICY=ON, CHECK_EXPIRATION=OFF, DEFAULT_LANGUAGE=[us_english], DEFAULT_DATABASE='
  +QUOTENAME(@DefaultDatabase)+N';';
 EXEC sys.sp_executesql @Sql;
 COMMIT TRANSACTION;
 SELECT N'CREADO';
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW 51205, 'Creacion de login bloqueada; revisar politica y precondiciones.', 1;
END CATCH;
