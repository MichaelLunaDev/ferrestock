# F2A-DIAG-006: objetos ADO.NET reales, sin Open/Execute ni Docker.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Data
$script:checks = 0
function Assert-Preparation([bool]$Condition, [string]$Label) {
    if (-not $Condition) { throw "FAIL: $Label" }
    $script:checks++
}

# Reproducir el defecto original, sin imprimir Message ni cadenas de conexion.
foreach ($entry in @(@('DataSource','127.0.0.1,14333'),@('InitialCatalog','master'),@('ConnectTimeout',10))) {
    $builder = [System.Data.SqlClient.SqlConnectionStringBuilder]::new()
    $caught = $null
    try { $builder.($entry[0]) = $entry[1] } catch { $caught = $_.Exception }
    Assert-Preparation ($caught -is [ArgumentException]) ('reproduccion original '+$entry[0])
}

$helper = Join-Path $PSScriptRoot 'New-Logins.ps1'
$source = Get-Content -LiteralPath $helper -Raw
$tokens = $null; $errors = $null
$ast = [Management.Automation.Language.Parser]::ParseInput($source,[ref]$tokens,[ref]$errors)
Assert-Preparation ($errors.Count -eq 0) 'parser helper'
$functions = $ast.FindAll({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] },$true)
$invoke = $functions | Where-Object Name -eq 'Invoke-LocalStage'
# Se interceptan las dos llamadas de red; el ScriptBlock en memoria necesita
# recibir la carpeta original porque no tiene un archivo asociado a PSScriptRoot.
$testProvisioningRoot = $PSScriptRoot
$testFunction = $invoke.Extent.Text.Replace('$connection.Open()',
    'Assert-PreparedCommand $connection $command; throw [OperationCanceledException]::new(''TEST_PREPARED_STOP'')')
$testFunction = $testFunction.Replace('$result = $command.ExecuteScalar()',
    'throw [InvalidOperationException]::new(''TEST_UNEXPECTED_EXECUTION'')')
$testFunction = $testFunction.Replace('$PSScriptRoot','$testProvisioningRoot')
$testAst = [Management.Automation.Language.Parser]::ParseInput($testFunction,[ref]$tokens,[ref]$errors)
Assert-Preparation ($errors.Count -eq 0) 'parser funcion aislada'
$networkCalls = @($testAst.FindAll({ param($node)
    $node -is [Management.Automation.Language.InvokeMemberExpressionAst] -and
    $node.Member.Value -in @('Open','ExecuteScalar','ExecuteReader','ExecuteNonQuery','OpenAsync')
},$true))
Assert-Preparation ($networkCalls.Count -eq 0) 'ninguna llamada SQL de red en prueba'
. ([scriptblock]::Create($testFunction))
function Get-LocalTarget { return 'fake-local-machine' }
$script:reads = [Collections.Generic.List[string]]::new()
function Get-Content {
    param([string]$LiteralPath,[switch]$Raw)
    # Fallar si la preparacion intenta leer cualquier script mutador.
    if ([IO.Path]::GetFileName($LiteralPath) -ne '05-preflight.sql') { throw 'TEST_UNEXPECTED_SQL_FILE' }
    [void]$script:reads.Add('05-preflight.sql')
    Microsoft.PowerShell.Management\Get-Content -LiteralPath $LiteralPath -Raw
}
function Assert-PreparedCommand($Connection,$Command) {
    $script:prepared++
    Assert-Preparation ($Connection.State -eq [System.Data.ConnectionState]::Closed) 'conexion cerrada'
    $settings = [System.Data.SqlClient.SqlConnectionStringBuilder]::new($Connection.ConnectionString)
    Assert-Preparation ($settings.DataSource -eq '127.0.0.1,14333' -and $settings.InitialCatalog -eq 'master') 'destino fijo'
    Assert-Preparation ($settings.Encrypt -and $settings.TrustServerCertificate -eq [bool]$TrustLocalDevelopmentCertificate) 'TLS conserva politica explicita'
    Assert-Preparation (-not $settings.PersistSecurityInfo -and -not $settings.Pooling -and $settings.ConnectTimeout -eq 10) 'proteccion y timeout'
    Assert-Preparation ($Connection.Credential -eq $sqlCredential) 'SqlCredential sin interpolacion'
    Assert-Preparation ($Command -is [System.Data.SqlClient.SqlCommand] -and $Command.CommandTimeout -eq 30) 'SqlCommand real'
    Assert-Preparation ($Command.Parameters.Count -eq 2) 'solo parametros preflight'
    foreach ($name in @('@ExpectedMachine','@ExpectedDatabase')) {
        $parameter = $Command.Parameters[$name]
        Assert-Preparation ($parameter.SqlDbType -eq [System.Data.SqlDbType]::NVarChar -and $parameter.Size -eq 128) 'tipo y longitud de parametro'
    }
    Assert-Preparation ($Command.Parameters['@ExpectedMachine'].Value -eq 'fake-local-machine') 'asignacion MachineName'
    Assert-Preparation ($Command.Parameters['@ExpectedDatabase'].Value -eq 'master') 'asignacion Database'
    $preflight = Microsoft.PowerShell.Management\Get-Content -LiteralPath (Join-Path $PSScriptRoot '05-preflight.sql') -Raw
    Assert-Preparation ($Command.CommandText -ceq ($preflight + [Environment]::NewLine + "SELECT N'PREFLIGHT VALIDADO';")) 'lote original y constante sin mutadores'
}

$fakeSecret = ConvertTo-SecureString 'FakeOnly_PreparationTest!123' -AsPlainText -Force
$fakeSecret.MakeReadOnly()
$sqlCredential = [System.Data.SqlClient.SqlCredential]::new('fake',$fakeSecret)
try {
    foreach ($trust in @($false,$true)) {
        $TrustLocalDevelopmentCertificate = $trust
        $script:f2Diagnostic = @{ Phase='Preparacion'; Cycle='Inicial' }
        $script:prepared = 0; $script:reads.Clear(); $caught = $null
        try { Invoke-LocalStage -Database master -File '' } catch { $caught = $_.Exception }
        if ($caught -isnot [OperationCanceledException]) {
            Write-Output ('Preparacion aislada: Fase={0}; Tipo={1}' -f $script:f2Diagnostic.Phase,$caught.GetType().FullName)
        }
        Assert-Preparation ($caught -is [OperationCanceledException] -and $caught.Message -eq 'TEST_PREPARED_STOP') 'fin antes de Open'
        Assert-Preparation ($script:prepared -eq 1 -and $script:reads.Count -eq 1) 'preparacion real completa'
        Assert-Preparation ($script:f2Diagnostic.Phase -eq 'ConexionSQL') 'PreparacionSQL superada sin conectar'
    }
} finally { $sqlCredential = $null; $fakeSecret.Dispose() }
Write-Output "PASS: $script:checks aserciones; defecto original reproducido; preparacion ADO.NET real verificada con ambas opciones TLS; 0 conexiones."
