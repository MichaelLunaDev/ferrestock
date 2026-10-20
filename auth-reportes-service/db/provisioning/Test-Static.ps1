# Verificacion local sin -Apply, Docker ni conexiones SQL. No escribe archivos.
$ErrorActionPreference = 'Stop'
$helper = Join-Path $PSScriptRoot 'New-Logins.ps1'
$tokens = $null; $parseErrors = $null
[void][Management.Automation.Language.Parser]::ParseFile($helper,[ref]$tokens,[ref]$parseErrors)
if ($parseErrors.Count) { throw 'Error sintactico PowerShell.' }
$script:externalCalls = 0
function docker { $script:externalCalls++; throw 'Docker no debe ejecutarse sin -Apply.' }
function Get-Credential { $script:externalCalls++; throw 'No solicitar credenciales sin -Apply.' }
$stages = @('Logins','AuthDev','AuthTest','Reports','AuthGrantsDev','AuthGrantsTest','ReportGrants','Preflight')
foreach ($stage in $stages) {
    $output = & $helper -Stage $stage
    if ($output -notlike 'NO APLICADO:*') { throw 'Salida sin -Apply inesperada.' }
}
if ($script:externalCalls -ne 0) { throw 'Se intento acceso externo sin -Apply.' }
$mutators = @('10-create-login.sql','20-auth-dev.sql','21-auth-test.sql',
              '30-report-reader.sql','40-auth-object-grants.sql','41-report-object-grants.sql')
foreach ($name in $mutators) {
    $sql = Get-Content -LiteralPath (Join-Path $PSScriptRoot $name) -Raw
    foreach ($required in @('@PreflightPassed','@@TRANCOUNT','SET XACT_ABORT ON',
                           'BEGIN TRY','BEGIN CATCH','XACT_STATE()','ROLLBACK TRANSACTION')) {
        if (-not $sql.Contains($required)) { throw "Falta control $required en $name" }
    }
    if ($sql -match '(?m)^\s*GO\s*$') { throw 'GO incompatible con ADO.NET.' }
}
foreach ($name in @('40-auth-object-grants.sql','41-report-object-grants.sql')) {
    $sql = Get-Content -LiteralPath (Join-Path $PSScriptRoot $name) -Raw
    if ($sql -notmatch 'IF NOT EXISTS \(SELECT 1 FROM @ApprovedObjects\)' -or
        $sql -match '(?m)^\s*INSERT\s+(INTO\s+)?@ApprovedObjects') {
        throw 'Allowlist vacia no garantizada.'
    }
}
$preflight = Get-Content -LiteralPath (Join-Path $PSScriptRoot '05-preflight.sql') -Raw
foreach ($entry in @(@('PermissionName','permission_name',3),@('State','state',3),@('ObjectType','type',2))) {
    $pattern = 'a\.'+$entry[0]+'\s+COLLATE Latin1_General_100_BIN2\s*=\s*[xo]\.'+$entry[1]+'\b'
    if ([regex]::Matches($preflight,$pattern).Count -ne $entry[2]) { throw 'Regresion: comparaciones de collation incompletas.' }
    if ($preflight -match ('a\.'+$entry[0]+'\s*=\s*[xo]\.'+$entry[1]+'\b')) { throw 'Regresion: comparacion de catalogo sin collation.' }
}
Write-Output 'PASS: parser PowerShell; 8 etapas sin -Apply; 0 llamadas externas; 6 barreras transaccionales; 2 allowlists vacias; 8 comparaciones BIN2.'
Write-Output 'LIMITACION: comprobaciones textuales no compilan T-SQL ni prueban permisos SQL reales.'
