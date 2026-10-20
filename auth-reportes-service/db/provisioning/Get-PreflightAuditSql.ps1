# Genera SQL diagnostico; no conecta ni solicita secretos ni ejecuta el helper.
[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidatePattern('\A[A-Za-z0-9-]{1,128}\z')][string]$ExpectedMachine)
$ErrorActionPreference = 'Stop'
$sql = Get-Content -LiteralPath (Join-Path $PSScriptRoot '05-preflight.sql') -Raw
$pattern = "(?ms)^IF (?<condition>.*?)\s+THROW (?<code>511\d\d), '(?<message>[^']*)', 1;"
$checks = [regex]::Matches($sql,$pattern)
if ($checks.Count -ne [regex]::Matches($sql,'THROW 511\d\d,').Count -or $checks.Count -eq 0) {
    throw 'Formato de guardias no reconocido; no generar diagnostico parcial.'
}
$scope = 'servidor'; $index = 0; $lastEnd = 0
$output = [Text.StringBuilder]::new()
[void]$output.AppendLine("DECLARE @ExpectedMachine nvarchar(128)=N'$ExpectedMachine', @ExpectedDatabase nvarchar(128)=N'master';")
foreach ($check in $checks) {
    $prefix = $sql.Substring($lastEnd,$check.Index-$lastEnd)
    $scopes = [regex]::Matches($prefix,'-- Inventario y validacion cerrada: (ferrestock_\w+)')
    if ($scopes.Count) { $scope = $scopes[$scopes.Count-1].Groups[1].Value }
    [void]$output.Append($prefix)
    $index++
    $condition = $check.Groups['condition'].Value
    $code = $check.Groups['code'].Value
    $message = $check.Groups['message'].Value
    [void]$output.AppendLine("SELECT $index AS check_id,$code AS code,N'$scope' AS scope,CASE WHEN $condition THEN 'DESVIACION' ELSE 'PASS' END AS result,N'$message' AS expected_guard;")
    $lastEnd = $check.Index+$check.Length
}
[void]$output.Append($sql.Substring($lastEnd))
# Es otra consulta: nunca marca una etapa del helper como VALIDADO ni habilita mutadores.
$diagnostic = $output.ToString().Replace('DECLARE @PreflightPassed bit=1;','')
Write-Output $diagnostic
