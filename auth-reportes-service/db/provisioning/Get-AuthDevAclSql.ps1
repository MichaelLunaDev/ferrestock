# Emite consultas de inventario/regresion, sin conexiones ni aprovisionamiento.
[CmdletBinding()]
param([switch]$Test)
$ErrorActionPreference = 'Stop'
$source = Get-Content -LiteralPath (Join-Path $PSScriptRoot '05-preflight.sql') -Raw
# Los comentarios de linea pueden contener ';' sin terminar un INSERT.
$source = [regex]::Replace($source,'(?m)^[ \t]*--[^\r\n]*','')
$parts = @()
foreach ($name in @('F2Logins','F2Acl')) {
    $declaration = [regex]::Match($source,'(?ms)^DECLARE @'+$name+' TABLE.*?;')
    $inserts = [regex]::Matches($source,'(?ms)^INSERT @'+$name+' VALUES.*?;')
    if (-not $declaration.Success -or $inserts.Count -eq 0) { throw 'Lista de politica no reconocida.' }
    $parts += $declaration.Value
    $parts += @($inserts | ForEach-Object { $_.Value })
}
$checks = [regex]::Matches($source,"(?ms)^IF (?<condition>.*?)\s+THROW (?<code>511\d\d), '[^']*', 1;")
$check = @($checks | Where-Object { $_.Groups['code'].Value -eq '51119' -and $_.Groups['condition'].Value.Contains('[ferrestock_auth_dev]') })
if ($check.Count -ne 1) { throw 'Guardia AuthDev no reconocida.' }
$condition = $check[0].Groups['condition'].Value.Trim()
if (-not $condition.StartsWith('EXISTS (') -or -not $condition.EndsWith(')')) { throw 'Formato inesperado de guardia.' }
$body = $condition.Substring(8,$condition.Length-9)
$start = $body.IndexOf('AND NOT EXISTS ')
if ($start -lt 0) { throw 'Predicado ACL no reconocido.' }
$reject = $body.Substring($start+4)
$sidCheck = @($checks | Where-Object { $_.Groups['code'].Value -eq '51114' -and $_.Groups['condition'].Value.Contains('[ferrestock_auth_dev]') })
$ownerCheck = @($checks | Where-Object { $_.Groups['code'].Value -eq '51125' -and $_.Groups['condition'].Value.Contains('[ferrestock_auth_dev]') })
if ($sidCheck.Count -ne 1 -or $ownerCheck.Count -ne 1) { throw 'Guardias de identidad/propiedad no reconocidas.' }
$sidCondition = $sidCheck[0].Groups['condition'].Value
$ownerCondition = $ownerCheck[0].Groups['condition'].Value
$sidFixture = $sidCondition.Replace('[ferrestock_auth_dev].sys.database_principals p',
    '(SELECT name,type,authentication_type,CONVERT(varbinary(85),0x00000000000000000000000000000000) AS sid FROM [ferrestock_auth_dev].sys.database_principals) p')
$ownerFixture = $ownerCondition.Replace('[ferrestock_auth_dev].sys.database_principals p',
    '(SELECT principal_id,CASE WHEN name=''auth_owner'' THEN N''fixture_wrong_owner'' ELSE name END AS name FROM [ferrestock_auth_dev].sys.database_principals) p')
if ($sidFixture -eq $sidCondition -or $ownerFixture -eq $ownerCondition) { throw 'Fixtures no reconocidos.' }
Write-Output 'SET NOCOUNT ON; USE [ferrestock_auth_dev];'
Write-Output ($parts -join [Environment]::NewLine)
if (-not $Test) {
    Write-Output @"
SELECT DB_NAME() AS database_name,p.name,p.type_desc,p.authentication_type_desc,
x.class_desc,x.major_id,x.minor_id,x.permission_name,x.state_desc,
CASE WHEN x.class=3 THEN SCHEMA_NAME(x.major_id) END AS schema_name,
CASE WHEN x.class=1 THEN OBJECT_NAME(x.major_id) END AS object_name,
CASE WHEN x.class=1 AND x.minor_id>0 THEN COL_NAME(x.major_id,x.minor_id) END AS column_name,
CASE WHEN $reject THEN 'FUERA_ALLOWLIST' ELSE 'ACEPTADO' END AS policy_result
FROM sys.database_permissions x JOIN sys.database_principals p ON p.principal_id=x.grantee_principal_id
WHERE p.name IN ('auth_owner','ferrestock_auth_app','ferrestock_auth_migrator','ferrestock_auth_test_app')
ORDER BY p.name,x.class,x.major_id,x.minor_id,x.permission_name;
"@
    return
}
# Casos ficticios: comparar con el predicado real, sin aplicar permisos.
Write-Output @"
IF (SELECT COUNT(*) FROM (VALUES
 ('auth_owner',0,0,0,'CONNECT','G',0),
 ('auth_owner',0,0,0,'CONNECT','D',1),('auth_owner',0,0,0,'CONNECT','W',1),
 ('auth_owner',0,0,0,'CONNECT','R',1),('auth_owner',0,1,0,'CONNECT','G',1),
 ('auth_owner',0,0,1,'CONNECT','G',1),('auth_owner',0,0,0,'CONTROL','G',1),
 ('auth_owner',0,0,0,'CREATE TABLE','G',1),('auth_owner',0,0,0,'IMPERSONATE ANY USER','G',1),
 ('auth_owner',3,SCHEMA_ID('auth'),0,'SELECT','G',1),
 ('auth_owner',3,SCHEMA_ID('auth'),0,'ALTER','G',1),
 ('auth_owner',1,-101,0,'SELECT','G',1),('auth_owner',1,-101,1,'SELECT','G',1),
 ('ferrestock_auth_app',0,0,0,'CONNECT','G',0),
 ('ferrestock_auth_app',0,0,0,'CREATE TABLE','D',0),
 ('ferrestock_auth_app',0,0,0,'CREATE TABLE','G',1),
 ('ferrestock_auth_app',3,SCHEMA_ID('auth'),0,'SELECT','G',1),
 ('ferrestock_auth_migrator',3,SCHEMA_ID('auth'),0,'ALTER','G',0),
 ('ferrestock_auth_migrator',3,SCHEMA_ID('dbo'),0,'ALTER','G',1),
 ('ferrestock_auth_migrator',3,SCHEMA_ID('auth'),1,'SELECT','G',1),
 ('ferrestock_auth_test_app',0,0,0,'CONNECT','D',0),
 ('ferrestock_auth_test_app',0,0,0,'CONNECT','G',1)
) x(principal,class,major_id,minor_id,permission_name,state,expected_reject)
CROSS APPLY (SELECT x.principal AS name) p
WHERE CASE WHEN $reject THEN 1 ELSE 0 END<>x.expected_reject)<>0
THROW 51283,'Regresion: politica ACL AuthDev.',1;

IF (SELECT COUNT(*) FROM @F2Acl WHERE DatabaseName='ferrestock_auth_dev' AND PrincipalName='auth_owner')<>1
 OR EXISTS (SELECT 1 FROM @F2Acl WHERE PrincipalName='auth_owner' AND
 (DatabaseName NOT IN ('ferrestock_auth_dev','ferrestock_auth_test') OR Class<>0 OR ScopeName<>'' OR ObjectName<>'' OR PermissionName<>'CONNECT' OR State<>'G'))
THROW 51284,'Regresion: excepcion auth_owner demasiado amplia.',1;

-- Estado real posterior a AuthDev: comprobar presencia ademas de ausencia de deriva.
IF (SELECT COUNT(*) FROM sys.database_permissions x JOIN sys.database_principals p ON p.principal_id=x.grantee_principal_id
WHERE p.name IN ('auth_owner','ferrestock_auth_app','ferrestock_auth_migrator','ferrestock_auth_test_app'))<>17
 OR $condition
THROW 51285,'Estado real AuthDev: faltan ACL o existen permisos fuera de lista.',1;

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name='auth_owner' AND type='S' AND authentication_type=0)
 OR EXISTS (SELECT 1 FROM sys.database_principals p JOIN sys.server_principals l ON l.sid=p.sid WHERE p.name='auth_owner')
THROW 51286,'Estado real: auth_owner debe permanecer sin login.',1;

IF (SELECT COUNT(*) FROM sys.database_principals p JOIN sys.server_principals l ON l.name=p.name AND l.sid=p.sid
WHERE p.name IN ('ferrestock_auth_app','ferrestock_auth_migrator','ferrestock_auth_test_app') AND p.type='S' AND p.authentication_type=1)<>3
THROW 51287,'Estado real: SID/tipo de usuarios no coincide.',1;

IF NOT EXISTS (SELECT 1 FROM sys.schemas s JOIN sys.database_principals p ON p.principal_id=s.principal_id WHERE s.name='auth' AND p.name='auth_owner')
 OR EXISTS (SELECT 1 FROM sys.schemas s JOIN sys.database_principals p ON p.principal_id=s.principal_id WHERE p.name='auth_owner' AND s.name<>'auth')
 OR EXISTS (SELECT 1 FROM sys.database_role_members m JOIN sys.database_principals p ON p.principal_id=m.member_principal_id
WHERE p.name IN ('auth_owner','ferrestock_auth_app','ferrestock_auth_migrator','ferrestock_auth_test_app'))
THROW 51288,'Estado real: propiedad o roles inesperados.',1;

-- Predicados 51114/51125 reales sobre proyecciones ficticias, sin tocar objetos.
IF $sidCondition OR $ownerCondition
THROW 51289,'Estado real: guardia SID o propietario no conforme.',1;
IF NOT $sidFixture
THROW 51290,'Regresion: la guardia no detecta el SID ficticio distinto.',1;
IF NOT $ownerFixture
THROW 51291,'Regresion: la guardia no detecta el propietario ficticio distinto.',1;

SELECT N'PASS: 22 casos ACL, estado real AuthDev y fixtures SID/propietario; sin DDL/DML persistente.' AS result;
"@
