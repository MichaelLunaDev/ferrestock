# PRECHECK-011. Emite SQL de simulacion; nunca ejecuta los scripts mutadores.
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidatePattern('\A[A-Za-z0-9-]{1,128}\z')][string]$ExpectedMachine,
    [ValidateSet('Current','AuthTest','Reports','Both')][string]$Scenario='Both',
    [ValidateSet('None','OriginalPolicy','ReportSelect','ReportWrite','TestDdl','OwnerLogin',
        'Sid','Role','SchemaOwner','CrossDatabase','Column','GrantOption')][string]$Fault='None'
)
$ErrorActionPreference='Stop'
if ($Fault -ne 'None' -and $Scenario -ne 'Both') { throw 'Los casos de deriva requieren Scenario Both.' }
function Read-ExpectedAcl([string]$File,[string[]]$Users) {
    $sql=Get-Content -LiteralPath (Join-Path $PSScriptRoot $File) -Raw
    $sql=[regex]::Replace($sql,'(?m)^[ \t]*--[^\r\n]*','')
    $created=[regex]::Matches($sql,'CREATE USER \[(?<user>\w+)\] (?<mapping>WITHOUT LOGIN|FOR LOGIN \[(?<login>\w+)\])')
    if ($created.Count -ne $Users.Count) { throw 'Conjunto CREATE USER no reconocido.' }
    $acl=@{}
    foreach ($create in $created) {
        $user=$create.Groups['user'].Value
        if ($user -notin $Users) { throw 'Identidad futura no reconocida.' }
        if (($user -eq 'auth_owner' -and $create.Groups['mapping'].Value -ne 'WITHOUT LOGIN') -or
            ($user -ne 'auth_owner' -and $create.Groups['login'].Value -ne $user)) { throw 'Mapeo de CREATE USER inesperado.' }
        # CONNECT por CREATE USER: observado en dev; el estado futuro sigue simulado.
        $acl["$user|CONNECT"]='G'
    }
    $grants=[regex]::Matches($sql,'(?ms)^(?<verb>GRANT|DENY) (?<permissions>.*?)\s+TO \[(?<user>\w+)\];')
    if ($grants.Count -ne [regex]::Matches($sql,'(?m)^(GRANT|DENY)\b').Count) { throw 'ACL de etapa no reconocida.' }
    $known=@('CONNECT','CREATE TABLE','CREATE VIEW','CREATE PROCEDURE','CREATE FUNCTION',
        'ALTER ANY SCHEMA','INSERT','UPDATE','DELETE','EXECUTE')
    foreach ($grant in $grants) {
        $user=$grant.Groups['user'].Value
        if ($user -notin $Users) { throw 'Principal de ACL inesperado.' }
        foreach ($part in $grant.Groups['permissions'].Value.Split(',')) {
            $permission=([regex]::Replace($part,'\s+',' ')).Trim()
            if ($permission -notin $known) { throw 'Permiso de etapa no reconocido.' }
            $acl["$user|$permission"]=if ($grant.Groups['verb'].Value -eq 'GRANT') { 'G' } else { 'D' }
        }
    }
    foreach ($key in @($acl.Keys | Sort-Object)) {
        $pair=$key.Split('|')
        "('$($pair[0])','$($pair[1])','$($acl[$key])')"
    }
}
$testAcl=@(Read-ExpectedAcl '21-auth-test.sql' @('auth_owner','ferrestock_auth_test_app'))
$reportAcl=@(Read-ExpectedAcl '30-report-reader.sql' @('ferrestock_report_reader'))
if ($testAcl.Count -ne 7 -or $reportAcl.Count -ne 10) { throw 'Conteo de ACL futuras inesperado.' }

Write-Output 'SET NOCOUNT ON;'
$diagnostic=(& (Join-Path $PSScriptRoot 'Get-PreflightAuditSql.ps1') -ExpectedMachine $ExpectedMachine) -join [Environment]::NewLine
foreach ($item in @(@('T','ferrestock_auth_test'),@('V','ferrestock_ventas'))) {
    $prefix=$item[0]; $db=$item[1]
    $users="@${prefix}Users"; $schemas="@${prefix}Schemas"; $permissions="@${prefix}Permissions"; $roles="@${prefix}Roles"
    Write-Output @"
DECLARE $users TABLE(name sysname,principal_id int,type char(1),authentication_type int,sid varbinary(85),owning_principal_id int);
INSERT $users SELECT name,principal_id,type,authentication_type,sid,owning_principal_id FROM [$db].sys.database_principals;
DECLARE $schemas TABLE(name sysname,schema_id int,principal_id int);
INSERT $schemas SELECT name,schema_id,principal_id FROM [$db].sys.schemas;
DECLARE $permissions TABLE(class int,major_id int,minor_id int,grantee_principal_id int,permission_name nvarchar(128),state char(1));
INSERT $permissions SELECT class,major_id,minor_id,grantee_principal_id,permission_name,state FROM [$db].sys.database_permissions;
DECLARE $roles TABLE(role_principal_id int,member_principal_id int);
INSERT $roles SELECT role_principal_id,member_principal_id FROM [$db].sys.database_role_members;
"@
    $include=($prefix -eq 'T' -and $Scenario -in @('AuthTest','Both')) -or ($prefix -eq 'V' -and $Scenario -in @('Reports','Both'))
    if ($include) {
        $app=if ($prefix -eq 'T') { 'ferrestock_auth_test_app' } else { 'ferrestock_report_reader' }
        if ($prefix -eq 'T') {
            Write-Output @"
IF NOT EXISTS(SELECT 1 FROM $users WHERE name='auth_owner')
 INSERT $users VALUES('auth_owner',1000001,'S',0,0xF1,NULL);
IF NOT EXISTS(SELECT 1 FROM $schemas WHERE name='auth')
 INSERT $schemas SELECT 'auth',1000001,principal_id FROM $users WHERE name='auth_owner';
"@
        }
        Write-Output @"
IF NOT EXISTS(SELECT 1 FROM $users WHERE name='$app')
 INSERT $users VALUES('$app',1000002,'S',1,SUSER_SID('$app'),NULL);
"@
        $rows=if ($prefix -eq 'T') { $testAcl -join ',' } else { $reportAcl -join ',' }
        Write-Output @"
INSERT $permissions SELECT 0,0,0,p.principal_id,v.permission_name,v.state
FROM (VALUES $rows) v(principal,permission_name,state) JOIN $users p ON p.name=v.principal
WHERE NOT EXISTS(SELECT 1 FROM $permissions x WHERE x.class=0 AND x.major_id=0 AND x.minor_id=0
AND x.grantee_principal_id=p.principal_id AND x.permission_name=v.permission_name);
"@
    }
    # Solo las entradas a los predicados son hipoteticas; nunca tocar catalogos.
    $diagnostic=$diagnostic.Replace("[$db].sys.database_principals",$users).
        Replace("[$db].sys.schemas",$schemas).Replace("[$db].sys.database_permissions",$permissions).
        Replace("[$db].sys.database_role_members",$roles)
}
$expectedCode=51119
switch ($Fault) {
    'OriginalPolicy' {
        $marker='-- Inventario y validacion cerrada: ferrestock_auth_test'
        if (-not $diagnostic.Contains($marker)) { throw 'Punto de inyeccion no reconocido.' }
        $diagnostic=$diagnostic.Replace($marker,"DELETE @F2Acl WHERE DatabaseName='ferrestock_auth_test' AND PrincipalName='auth_owner';`n"+$marker)
    }
    'ReportSelect' { Write-Output "INSERT @VPermissions SELECT 0,0,0,principal_id,'SELECT','G' FROM @VUsers WHERE name='ferrestock_report_reader';" }
    'ReportWrite' { Write-Output "UPDATE @VPermissions SET state='G' WHERE permission_name='INSERT' AND grantee_principal_id=1000002;" }
    'TestDdl' { Write-Output "UPDATE @TPermissions SET state='G' WHERE permission_name='CREATE TABLE' AND grantee_principal_id=1000002;" }
    'OwnerLogin' { Write-Output "UPDATE @TUsers SET authentication_type=1 WHERE name='auth_owner';"; $expectedCode=51124 }
    'Sid' { Write-Output "UPDATE @TUsers SET sid=0x00 WHERE name='ferrestock_auth_test_app';"; $expectedCode=51114 }
    'Role' { Write-Output "INSERT @TRoles SELECT principal_id,1000002 FROM @TUsers WHERE name='db_owner';"; $expectedCode=51115 }
    'SchemaOwner' { Write-Output "UPDATE @TSchemas SET principal_id=1 WHERE name='auth';"; $expectedCode=51125 }
    'CrossDatabase' { Write-Output "INSERT @TUsers SELECT name,1000099,type,authentication_type,sid,owning_principal_id FROM @VUsers WHERE name='ferrestock_report_reader';"; $expectedCode=51113 }
    'Column' { Write-Output "INSERT @VPermissions VALUES(1,2147483647,1,1000002,'SELECT','G');" }
    'GrantOption' { Write-Output "UPDATE @VPermissions SET state='W' WHERE permission_name='CONNECT' AND grantee_principal_id=1000002;" }
}
# Evaluar IF fuera del INSERT evita falsear XACT_STATE con la escritura local.
Write-Output 'DECLARE @SimResults TABLE(check_id int,code int,scope sysname,result varchar(16));'
$pattern="(?ms)^SELECT (?<id>\d+) AS check_id,(?<code>511\d\d) AS code,N'(?<scope>\w+)' AS scope,CASE WHEN (?<condition>.*?) THEN 'DESVIACION' ELSE 'PASS' END AS result,N'[^']*' AS expected_guard;"
if ([regex]::Matches($diagnostic,$pattern).Count -ne 54) { throw 'No se reconocen las 54 condiciones.' }
$diagnostic=[regex]::Replace($diagnostic,$pattern,[Text.RegularExpressions.MatchEvaluator]{ param($m)
    $id=$m.Groups['id'].Value; $code=$m.Groups['code'].Value; $scope=$m.Groups['scope'].Value; $condition=$m.Groups['condition'].Value
    "IF $condition INSERT @SimResults VALUES($id,$code,N'$scope','DESVIACION'); ELSE INSERT @SimResults VALUES($id,$code,N'$scope','PASS');"
})
Write-Output $diagnostic
Write-Output "SELECT check_id,code,scope,result FROM @SimResults WHERE result='DESVIACION' ORDER BY check_id;"
if ($Fault -eq 'None') {
    Write-Output "IF (SELECT COUNT(*) FROM @SimResults)<>54 OR EXISTS(SELECT 1 FROM @SimResults WHERE result<>'PASS') THROW 51400,'Simulacion futura no conforme.',1;"
} else {
    Write-Output "IF (SELECT COUNT(*) FROM @SimResults)<>54 OR NOT EXISTS(SELECT 1 FROM @SimResults WHERE code=$expectedCode AND result='DESVIACION') THROW 51401,'Regresion: deriva futura no detectada.',1;"
}
Write-Output "SELECT N'PASS SIMULACION: $Scenario / $Fault; 54 condiciones, ACL derivadas de scripts (AuthTest=7, Reports=10); integracion pendiente' AS result;"
