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

CREATE TABLE auth.rol (
    rol_id tinyint NOT NULL,
    codigo varchar(20) COLLATE Latin1_General_100_BIN2 NOT NULL,
    CONSTRAINT PK_auth_rol PRIMARY KEY (rol_id),
    CONSTRAINT UQ_auth_rol_codigo UNIQUE (codigo),
    CONSTRAINT CK_auth_rol_catalogo CHECK (
        (rol_id = 1 AND codigo = 'ADMINISTRADOR' AND DATALENGTH(codigo) = 13) OR
        (rol_id = 2 AND codigo = 'VENDEDOR' AND DATALENGTH(codigo) = 8) OR
        (rol_id = 3 AND codigo = 'ALMACENERO' AND DATALENGTH(codigo) = 10))
);

CREATE TABLE auth.usuario (
    usuario_id bigint IDENTITY(1,1) NOT NULL,
    login varchar(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    nombre nvarchar(120) NOT NULL,
    password_hash varchar(512) COLLATE Latin1_General_100_BIN2 NOT NULL,
    estado varchar(8) COLLATE Latin1_General_100_BIN2 NOT NULL,
    rol_id tinyint NOT NULL,
    version_seguridad bigint NOT NULL CONSTRAINT DF_auth_usuario_version DEFAULT (1),
    creado_en datetime2(3) NOT NULL CONSTRAINT DF_auth_usuario_creado DEFAULT SYSUTCDATETIME(),
    actualizado_en datetime2(3) NOT NULL CONSTRAINT DF_auth_usuario_actualizado DEFAULT SYSUTCDATETIME(),
    creado_por bigint NULL,
    actualizado_por bigint NULL,
    CONSTRAINT PK_auth_usuario PRIMARY KEY (usuario_id),
    CONSTRAINT UQ_auth_usuario_login UNIQUE (login),
    CONSTRAINT FK_auth_usuario_rol FOREIGN KEY (rol_id) REFERENCES auth.rol(rol_id),
    CONSTRAINT FK_auth_usuario_creador FOREIGN KEY (creado_por) REFERENCES auth.usuario(usuario_id),
    CONSTRAINT FK_auth_usuario_editor FOREIGN KEY (actualizado_por) REFERENCES auth.usuario(usuario_id),
    CONSTRAINT CK_auth_usuario_login CHECK (
        DATALENGTH(login) BETWEEN 3 AND 64 AND
        login NOT LIKE '%[^a-z0-9._-]%' COLLATE Latin1_General_100_BIN2),
    CONSTRAINT CK_auth_usuario_nombre CHECK (LEN(LTRIM(RTRIM(nombre))) > 0),
    CONSTRAINT CK_auth_usuario_hash CHECK (DATALENGTH(password_hash) = DATALENGTH('{pbkdf2-ferrestock-v1}') + 96 AND
        LEFT(password_hash,LEN('{pbkdf2-ferrestock-v1}')) = '{pbkdf2-ferrestock-v1}' AND
        SUBSTRING(password_hash,LEN('{pbkdf2-ferrestock-v1}')+1,96) NOT LIKE '%[^0-9a-f]%'),
    CONSTRAINT CK_auth_usuario_estado CHECK (
        (estado = 'ACTIVO' AND DATALENGTH(estado) = 6) OR
        (estado = 'INACTIVO' AND DATALENGTH(estado) = 8)),
    CONSTRAINT CK_auth_usuario_version CHECK (version_seguridad >= 1),
    CONSTRAINT CK_auth_usuario_fechas CHECK (actualizado_en >= creado_en)
);
CREATE INDEX IX_auth_usuario_rol_estado ON auth.usuario(rol_id, estado);

CREATE TABLE auth.sesion (
    sesion_id bigint IDENTITY(1,1) NOT NULL,
    usuario_id bigint NOT NULL,
    token_hash binary(32) NOT NULL,
    version_seguridad bigint NOT NULL,
    creado_en datetime2(3) NOT NULL CONSTRAINT DF_auth_sesion_creado DEFAULT SYSUTCDATETIME(),
    vence_en datetime2(3) NOT NULL,
    revocado_en datetime2(3) NULL,
    motivo_revocacion varchar(24) COLLATE Latin1_General_100_BIN2 NULL,
    CONSTRAINT PK_auth_sesion PRIMARY KEY (sesion_id),
    CONSTRAINT UQ_auth_sesion_token UNIQUE (token_hash),
    CONSTRAINT FK_auth_sesion_usuario FOREIGN KEY (usuario_id) REFERENCES auth.usuario(usuario_id),
    CONSTRAINT CK_auth_sesion_version CHECK (version_seguridad >= 1),
    CONSTRAINT CK_auth_sesion_fechas CHECK (
        vence_en > creado_en AND (revocado_en IS NULL OR revocado_en >= creado_en)),
    CONSTRAINT CK_auth_sesion_revocacion CHECK (
        (revocado_en IS NULL AND motivo_revocacion IS NULL) OR
        (revocado_en IS NOT NULL AND motivo_revocacion IS NOT NULL AND
         DATALENGTH(motivo_revocacion)=DATALENGTH(RTRIM(motivo_revocacion)) AND
         motivo_revocacion IN ('LOGOUT','USUARIO_INACTIVO','CREDENCIAL_CAMBIADA','ROL_CAMBIADO')))
);
CREATE INDEX IX_auth_sesion_usuario_vigente ON auth.sesion(usuario_id, vence_en)
    INCLUDE (version_seguridad) WHERE revocado_en IS NULL;
CREATE INDEX IX_auth_sesion_vencimiento ON auth.sesion(vence_en);
