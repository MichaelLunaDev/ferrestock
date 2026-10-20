# Pruebas en memoria: sin Docker, credenciales reales ni conexiones SQL.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Data
$helper = Join-Path $PSScriptRoot 'New-Logins.ps1'
$source = Get-Content -LiteralPath $helper -Raw
$tokens = $null; $parseErrors = $null
$ast = [Management.Automation.Language.Parser]::ParseInput($source,[ref]$tokens,[ref]$parseErrors)
if ($parseErrors.Count) { throw 'Parser del helper: FAIL' }
$functions = @($ast.FindAll({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] },$true))
$formatter = $functions | Where-Object Name -eq 'Format-F2Failure'
. ([scriptblock]::Create($formatter.Extent.Text))
$script:checks = 0
$canary = 'CANARY_ONLY_Password=NeverPrint!; CREATE LOGIN fake WITH PASSWORD=NeverPrint!'
function Assert-True([bool]$Condition, [string]$Label) {
    if (-not $Condition) { throw "FAIL: $Label" }
    $script:checks++
}
function Assert-Safe([string]$Text) {
    Assert-True (-not ($Text -match 'CANARY|NeverPrint|Password=|CREATE LOGIN|Server=|Data Source=')) 'salida sin secretos'
}
function New-SimulatedSqlException([int]$Number, [Exception]$Inner = $null) {
    # Reflection SOLO en tests: crear SqlException real sin servidor ni red.
    $flags = [Reflection.BindingFlags]'Instance,NonPublic'
    $ctor = [System.Data.SqlClient.SqlError].GetConstructors($flags) |
        Where-Object { $_.GetParameters().Count -eq 7 } | Select-Object -First 1
    $errorObject = $ctor.Invoke(@($Number,[byte]7,[byte]16,'fake',$canary,'fake',[int]1))
    $collection = [Activator]::CreateInstance([System.Data.SqlClient.SqlErrorCollection],$true)
    [void]$collection.GetType().GetMethod('Add',$flags).Invoke($collection,@($errorObject))
    $factory = [System.Data.SqlClient.SqlException].GetMethods([Reflection.BindingFlags]'Static,NonPublic') |
        Where-Object { $_.Name -eq 'CreateException' -and $_.GetParameters().Count -eq 4 -and
            $_.GetParameters()[2].ParameterType -eq [Guid] } | Select-Object -First 1
    return $factory.Invoke($null,@($collection,'16.0',[Guid]::Empty,$Inner))
}

$cases = @(
    @(51103,'EjecucionSQL','TSQL'), @(51101,'EjecucionSQL','PermisosAdministrativos'),
    @(51102,'EjecucionSQL','IdentidadSQL'), @(18456,'ConexionSQL','AutenticacionSQL'),
    @(102,'EjecucionSQL','CompilacionTSQL'), @(207,'EjecucionSQL','CompilacionTSQL'),
    @(229,'EjecucionSQL','PermisosSQL'), @(-2,'ConexionSQL','ConexionSQL'),
    @(50000,'EjecucionSQL','TSQL')
)
foreach ($case in $cases) {
    $sql = New-SimulatedSqlException $case[0]
    $wrapped = [InvalidOperationException]::new($canary,$sql)
    $text = Format-F2Failure $wrapped 'Preflight' @{ Phase=$case[1]; Cycle='Inicial' }
    Assert-True ($text.Contains("Origen: $($case[2]) |")) 'categoria SQL'
    Assert-True ($text.Contains("Error SQL: $($case[0]) | Severidad: 16 | Estado: 7")) 'campos SQL reales'
    Assert-Safe $text
}
foreach ($phase in @('DockerEstado','DockerRed','DockerPuertos','DockerIdentidad','Credencial','LecturaSQL','PoliticaSecreto','ConexionSQL','EjecucionSQL','Resultado')) {
    $text = Format-F2Failure ([Exception]::new($canary)) 'Preflight' @{ Phase=$phase; Cycle='Posterior' }
    Assert-True ($text.Contains("Fase: $phase | Comprobacion: Posterior")) 'fase especifica'
    Assert-Safe $text
}
$tls = [System.ComponentModel.Win32Exception]::new(-2146893019,$canary)
$sql = New-SimulatedSqlException -2146893019 $tls
$text = Format-F2Failure $sql 'Preflight' @{ Phase='ConexionSQL'; Cycle='Inicial' }
Assert-True ($text.Contains('Origen: ConexionTLS | Error SQL: -2146893019')) 'TLS por codigo nativo tipado'
Assert-Safe $text
$text = Format-F2Failure ([System.Security.Authentication.AuthenticationException]::new($canary)) 'Preflight' @{ Phase='ConexionSQL'; Cycle='Inicial' }
Assert-True ($text.Contains('Origen: ConexionTLS')) 'TLS por tipo de excepcion'
$text = Format-F2Failure ([Exception]::new('SSL TLS certificate '+$canary)) 'Preflight' @{ Phase='ConexionSQL'; Cycle='Inicial' }
Assert-True ($text.Contains('Origen: ConexionSQL') -and -not $text.Contains('Origen: ConexionTLS')) 'no inferir TLS por mensajes'
$text = Format-F2Failure ([Exception]::new($canary)) $canary @{ Phase=$canary; Cycle=$canary }
Assert-Safe $text
Assert-True ($text.Contains('Desconocida')) 'etiquetas cerradas'

# Orquestacion real del helper con UNICA sustitucion de Invoke-LocalStage en memoria.
# La funcion sustituta registra destino/archivo; nunca crea SqlConnection.
$invokeAst = $functions | Where-Object Name -eq 'Invoke-LocalStage'
$stub = @'
function Invoke-LocalStage {
    param($Database,$File,$LoginName,$InspectOnly,$Secret)
    [void]$script:calls.Add("$Database/$File")
    $script:f2Diagnostic.Phase = 'EjecucionSQL'
    if ($script:failAt -eq $script:calls.Count) { throw (New-SimulatedSqlException 51103) }
    $script:f2Diagnostic.Phase = 'Resultado'
    return 'PREFLIGHT VALIDADO'
}
'@
$testSource = $source.Substring(0,$invokeAst.Extent.StartOffset) + $stub + $source.Substring($invokeAst.Extent.EndOffset)
function docker {
    if ($script:dockerFail) { throw [Exception]::new($canary) }
    $global:LASTEXITCODE = 0
    switch ($args[2]) {
        '{{json .State.Running}}' { 'true' }
        '{{.HostConfig.NetworkMode}}' { 'bridge' }
        '{{json .NetworkSettings.Ports}}' { '{"1433/tcp":[{"HostIp":"127.0.0.1","HostPort":"14333"}]}' }
        '{{.Config.Hostname}}' { 'fake' }
        default { throw 'Docker mock: argumento inesperado' }
    }
}
function Get-Credential {
    $secret = ConvertTo-SecureString 'FakeOnly_NotARealCredential!123' -AsPlainText -Force
    return [Management.Automation.PSCredential]::new('fake',$secret)
}
foreach ($failAt in @(0,1,2)) {
    $script:failAt = $failAt; $script:dockerFail = $false
    $script:calls = [Collections.Generic.List[string]]::new()
    $caught = $null; $output = $null
    try { $output = & ([scriptblock]::Create($testSource)) -Apply -Stage Preflight }
    catch { $caught = $_.Exception }
    Assert-True (@($script:calls | Where-Object { $_ -ne 'master/' }).Count -eq 0) 'Preflight solo master y archivo vacio'
    if ($failAt -eq 0) {
        Assert-True ($null -eq $caught -and $output -eq 'Preflight VALIDADO' -and $script:calls.Count -eq 2) 'dos comprobaciones exitosas'
    } else {
        Assert-True ($null -ne $caught -and $script:calls.Count -eq $failAt) 'bloqueo inmediato'
        Assert-True ($null -eq $caught.InnerException) 'sin excepcion original adjunta'
        $cycle = if ($failAt -eq 2) { 'Posterior' } else { 'Inicial' }
        Assert-True ($caught.Message.Contains("Comprobacion: $cycle | Origen: TSQL | Error SQL: 51103")) 'catch conserva diagnostico'
        Assert-Safe ($caught | Out-String)
    }
}
$script:dockerFail = $true; $script:calls.Clear(); $caught = $null
try { & ([scriptblock]::Create($testSource)) -Apply -Stage Preflight } catch { $caught = $_.Exception }
Assert-True ($null -ne $caught -and $caught.Message.Contains('Fase: DockerEstado') -and $script:calls.Count -eq 0) 'Docker bloquea antes de SQL'
Assert-Safe ($caught | Out-String)

# La misma sanitizacion para errores de creacion con mensajes contaminados.
$text = Format-F2Failure (New-SimulatedSqlException 15118) 'ferrestock_auth_app' @{ Phase='EjecucionSQL'; Cycle='Creacion' }
Assert-Safe $text
Assert-True ($text.Contains('Error SQL: 15118')) 'creacion conserva solo codigo'
Assert-True ($source.Contains('$builder.Encrypt = $true') -and
    $source.Contains('$builder.TrustServerCertificate = [bool]$TrustLocalDevelopmentCertificate') -and
    $source.Contains('$builder.Pooling = $false')) 'controles de conexion preservados'
Write-Output "PASS: $script:checks aserciones de diagnostico y orquestacion simulada; 0 conexiones SQL reales."
