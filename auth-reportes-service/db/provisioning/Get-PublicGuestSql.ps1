# Emite inventario o regresion SQL, sin conexiones ni secretos.
[CmdletBinding()]
param([switch]$Test)
$ErrorActionPreference = 'Stop'
$sql = Get-Content -LiteralPath (Join-Path $PSScriptRoot '05-preflight.sql') -Raw
$hidden = [regex]::Match($sql,'(?ms)^DECLARE @F2HiddenSystemSelect TABLE.*?;\s*INSERT @F2HiddenSystemSelect VALUES.*?;')
if (-not $hidden.Success) { throw 'Allowlist Resource no reconocida.' }
Write-Output ('SET NOCOUNT ON;' + [Environment]::NewLine + $hidden.Value)
$databases = @('ferrestock_auth_dev','ferrestock_auth_test','ferrestock_ventas')
$allCases = 0
foreach ($db in $databases) {
    $pattern = '(?ms)^IF EXISTS \(SELECT 1 FROM \['+$db+'\]\.sys\.database_permissions x\s+JOIN \['+$db+'\]\.sys\.database_principals p ON p.principal_id=x.grantee_principal_id\s+WHERE p.name IN \(''public'',''guest''\) AND NOT\s*(?<allow>.*?)\)\s+THROW 51121,'
    $match = [regex]::Match($sql,$pattern)
    if (-not $match.Success) { throw 'Guardia public/guest no reconocida.' }
    $allow = $match.Groups['allow'].Value
    if (-not $Test) {
        Write-Output @"
USE [$db];
SELECT N'$db' AS database_name,p.name AS principal,x.class_desc,x.major_id,x.minor_id,x.permission_name,x.state_desc,
OBJECT_SCHEMA_NAME(x.major_id,DB_ID('$db')) AS schema_name,OBJECT_NAME(x.major_id,DB_ID('$db')) AS object_name,
CASE WHEN x.class=1 AND x.minor_id>0 THEN (SELECT c.name FROM [$db].sys.all_columns c WHERE c.object_id=x.major_id AND c.column_id=x.minor_id) END AS column_name,
CASE WHEN $allow THEN 'ALLOWLIST' ELSE 'DESVIACION' END AS result,
CASE WHEN $allow THEN CASE WHEN x.class=1 AND EXISTS(SELECT 1 FROM @F2HiddenSystemSelect a WHERE a.ObjectId=x.major_id) THEN 'A: Resource auditado'
ELSE 'A: baseline de sistema' END ELSE 'C: requiere investigacion' END AS classification,
CASE WHEN x.class=1 THEN CONVERT(int,OBJECTPROPERTYEX(x.major_id,'IsMSShipped')) END AS is_ms_shipped,
CASE WHEN EXISTS(SELECT 1 FROM model.sys.database_permissions m JOIN model.sys.database_principals mp ON mp.principal_id=m.grantee_principal_id
WHERE mp.name=p.name AND m.class=x.class AND m.major_id=x.major_id AND m.minor_id=x.minor_id AND m.permission_name=x.permission_name AND m.state=x.state) THEN 1 ELSE 0 END AS matches_model
FROM [$db].sys.database_permissions x JOIN [$db].sys.database_principals p ON p.principal_id=x.grantee_principal_id
WHERE p.name IN ('public','guest') ORDER BY p.name,x.class,x.major_id,x.minor_id,x.permission_name;
"@
    } else {
        # Datos ficticios en CTE, no ejecutar GRANT/DENY ni crear objetos.
        # Los ids negativos se resuelven con metadatos reales de cada base.
        $fixtures = @"
('public',1,-675,0,'SELECT','G',1),('public',1,-101,0,'SELECT','G',1),
('public',0,0,0,'VIEW ANY COLUMN ENCRYPTION KEY DEFINITION','G',1),
('public',0,0,0,'VIEW ANY COLUMN MASTER KEY DEFINITION','G',1),
('guest',0,0,0,'CONNECT','D',1),('guest',0,0,0,'CONNECT','G',0),
('public',1,2147483647,0,'SELECT','G',0),('public',1,-675,0,'INSERT','G',0),
('public',1,-675,0,'UPDATE','G',0),('public',1,-675,0,'DELETE','G',0),
('public',1,-675,0,'EXECUTE','G',0),('public',1,-675,0,'SELECT','D',0),
('public',1,-675,0,'SELECT','W',0),('public',1,-675,0,'SELECT','R',0),
('public',1,-675,1,'SELECT','G',0),('public',1,-101,1,'SELECT','G',0),
('guest',1,-675,0,'SELECT','G',0),('guest',1,2147483647,0,'SELECT','G',0),
('guest',0,0,0,'SELECT','D',0),('public',0,0,0,'CONTROL','G',0),
('public',0,0,0,'VIEW ANY COLUMN MASTER KEY DEFINITION','D',0),
('public',0,1,0,'VIEW ANY COLUMN MASTER KEY DEFINITION','G',0),
('public',4,-675,0,'SELECT','G',0),('public',1,-2147483647,0,'SELECT','G',0)
"@
        # Diferencia entre bases: simular una desviacion solo en auth_test.
        if ($db -eq 'ferrestock_auth_test') { $fixtures += ",('public',0,0,0,'IMPERSONATE ANY USER','G',0)" }
        $count = [regex]::Matches($fixtures,"\('(public|guest)',").Count
        $allCases += $count
        Write-Output @"
IF (SELECT COUNT(*) FROM (VALUES $fixtures) x(principal,class,major_id,minor_id,permission_name,state,expected)
CROSS APPLY (SELECT x.principal AS name) p
WHERE CASE WHEN $allow THEN 1 ELSE 0 END<>x.expected)<>0
THROW 51282,'Regresion public/guest: $db',1;
"@
        Write-Output "SELECT N'PASS: $db; $count casos public/guest sobre predicado real' AS result;"
    }
}
if ($Test) { Write-Output "SELECT N'PASS: $allCases casos SQL; sin cambios persistentes' AS result;" }
