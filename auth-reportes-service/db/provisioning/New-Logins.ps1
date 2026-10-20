[CmdletBinding()]
param(
    [switch]$Apply,
    [ValidateSet('Logins','AuthDev','AuthTest','Reports','AuthGrantsDev','AuthGrantsTest','ReportGrants','Preflight')]
    [string]$Stage = 'Logins',
    [switch]$TrustLocalDevelopmentCertificate
)
$ErrorActionPreference = 'Stop'
# Barrera anterior a Docker, ficheros, credenciales y conexiones.
if (-not $Apply) {
    Write-Output 'NO APLICADO: sin Docker, credenciales ni conexiones. Requiere revision y -Apply.'
    return
}

function Format-F2Failure {
    param([Exception]$Exception, [string]$Step, [hashtable]$Context)
    # Solo etiquetas cerradas y campos numericos; nunca Message, Data o ToString().
    $steps = @('Logins','AuthDev','AuthTest','Reports','AuthGrantsDev','AuthGrantsTest',
        'ReportGrants','Preflight','ferrestock_auth_app','ferrestock_auth_migrator',
        'ferrestock_auth_test_app','ferrestock_report_reader')
    if ($Step -notin $steps) { $Step = 'Desconocida' }
    $phases = @('Preparacion','DockerEstado','DockerRed','DockerPuertos','DockerIdentidad',
        'Credencial','PreparacionSQL','LecturaSQL','PoliticaSecreto','ConexionSQL','EjecucionSQL','Resultado')
    $phase = if ($Context.Phase -in $phases) { $Context.Phase } else { 'Desconocida' }
    $cycle = if ($Context.Cycle -in @('Inicial','Creacion','Posterior')) { $Context.Cycle } else { 'Inicial' }
    $origin = switch -Wildcard ($phase) {
        'Docker*' { 'Docker'; break }
        'Credencial' { 'Credencial'; break }
        'LecturaSQL' { 'LecturaSQL'; break }
        'PoliticaSecreto' { 'PoliticaSecreto'; break }
        'ConexionSQL' { 'ConexionSQL'; break }
        'EjecucionSQL' { 'EjecucionSQL'; break }
        'Resultado' { 'Resultado'; break }
        default { 'Preparacion' }
    }
    $sql = $null; $tls = $false; $cursor = $Exception
    # PowerShell puede envolver SqlException en MethodInvocationException.
    for ($depth = 0; $null -ne $cursor -and $depth -lt 16; $depth++) {
        if ($cursor -is [System.Data.SqlClient.SqlException] -and $null -eq $sql) { $sql = $cursor }
        if ($phase -eq 'ConexionSQL') {
            if ($cursor -is [System.Security.Authentication.AuthenticationException]) { $tls = $true }
            # Codigos nativos de certificado: raiz no confiable, nombre y vigencia.
            if ($cursor -is [System.ComponentModel.Win32Exception] -and
                $cursor.NativeErrorCode -in @(-2146893019,-2146762487,-2146762481,-2146762495)) { $tls = $true }
        }
        $cursor = $cursor.InnerException
    }
    $detail = ''
    if ($null -ne $sql) {
        $origin = if ($phase -eq 'ConexionSQL') { 'ConexionSQL' } else { 'TSQL' }
        if ($sql.Number -in @(18452,18456,18470,18486,18487,18488)) { $origin = 'AutenticacionSQL' }
        elseif ($sql.Number -eq 51101) { $origin = 'PermisosAdministrativos' }
        elseif ($sql.Number -eq 51102) { $origin = 'IdentidadSQL' }
        elseif ($phase -eq 'EjecucionSQL' -and $sql.Number -in @(102,105,156,207,208,209,2812,4104)) { $origin = 'CompilacionTSQL' }
        elseif ($phase -eq 'EjecucionSQL' -and $sql.Number -in @(229,230,262,297,300,916)) { $origin = 'PermisosSQL' }
        $detail = ' | Error SQL: {0} | Severidad: {1} | Estado: {2}' -f $sql.Number,$sql.Class,$sql.State
    }
    if ($tls) { $origin = 'ConexionTLS' }
    return "BLOQUEADO: $Step | Fase: $phase | Comprobacion: $cycle | Origen: $origin$detail"
}

$script:f2Diagnostic = @{ Phase = 'Preparacion'; Cycle = 'Inicial' }
function Get-LocalTarget {
    # No inspeccionar Config.Env: contiene secretos. Identidad efimera solo en memoria.
    $script:f2Diagnostic.Phase = 'DockerEstado'
    $state = & docker inspect --format '{{json .State.Running}}' ferrestock-mssql 2>$null
    if ($LASTEXITCODE -ne 0 -or $state -ne 'true') { throw 'Contenedor local no disponible.' }
    $script:f2Diagnostic.Phase = 'DockerRed'
    $network = & docker inspect --format '{{.HostConfig.NetworkMode}}' ferrestock-mssql 2>$null
    if ($LASTEXITCODE -ne 0 -or $network -eq 'host') { throw 'Red Docker no admitida.' }
    $script:f2Diagnostic.Phase = 'DockerPuertos'
    $bindingsJson = & docker inspect --format '{{json .NetworkSettings.Ports}}' ferrestock-mssql 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo comprobar publicacion.' }
    $bindings = $bindingsJson | ConvertFrom-Json
    $published = @($bindings.PSObject.Properties | Where-Object { $null -ne $_.Value })
    $port = @($bindings.'1433/tcp')
    if ($published.Count -ne 1 -or $published[0].Name -ne '1433/tcp' -or
        $port.Count -ne 1 -or $port[0].HostIp -ne '127.0.0.1' -or $port[0].HostPort -ne '14333') {
        throw 'Publicacion Docker distinta de 127.0.0.1:14333.'
    }
    $script:f2Diagnostic.Phase = 'DockerIdentidad'
    $machine = & docker inspect --format '{{.Config.Hostname}}' ferrestock-mssql 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($machine)) { throw 'Identidad Docker ausente.' }
    return $machine.Trim()
}

# Cada llamada abre una conexion fisica nueva, ejecuta preflight+etapa y la cierra.
function Invoke-LocalStage {
    param([string]$Database, [string]$File, [string]$LoginName = '',
          [bool]$InspectOnly = $true, [Security.SecureString]$Secret)
    $connection = $null; $command = $null; $buffer = [IntPtr]::Zero; $plain = $null
    try {
        $machine = Get-LocalTarget
        $script:f2Diagnostic.Phase = 'PreparacionSQL'
        $builder = [System.Data.SqlClient.SqlConnectionStringBuilder]::new()
        # Setters CLR explicitos: evitar que PowerShell use el adaptador IDictionary.
        $builder.set_DataSource('127.0.0.1,14333')
        $builder.set_InitialCatalog($Database)
        $builder.Encrypt = $true
        $builder.TrustServerCertificate = [bool]$TrustLocalDevelopmentCertificate
        $builder.PersistSecurityInfo = $false
        $builder.Pooling = $false
        $builder.set_ConnectTimeout(10)
        $connection = [System.Data.SqlClient.SqlConnection]::new()
        $connection.ConnectionString = $builder.ConnectionString
        $connection.Credential = $sqlCredential
        $script:f2Diagnostic.Phase = 'LecturaSQL'
        $preflight = Get-Content -LiteralPath (Join-Path $PSScriptRoot '05-preflight.sql') -Raw
        $body = if ($File) { Get-Content -LiteralPath (Join-Path $PSScriptRoot $File) -Raw } else { "SELECT N'PREFLIGHT VALIDADO';" }
        $script:f2Diagnostic.Phase = 'PreparacionSQL'
        $command = $connection.CreateCommand()
        $command.CommandText = $preflight + [Environment]::NewLine + $body
        $command.CommandTimeout = 30
        [void]$command.Parameters.Add('@ExpectedMachine',[System.Data.SqlDbType]::NVarChar,128)
        $command.Parameters['@ExpectedMachine'].Value = $machine
        [void]$command.Parameters.Add('@ExpectedDatabase',[System.Data.SqlDbType]::NVarChar,128)
        $command.Parameters['@ExpectedDatabase'].Value = $Database
        if ($File -eq '10-create-login.sql') {
            [void]$command.Parameters.Add('@LoginName',[System.Data.SqlDbType]::NVarChar,128)
            $command.Parameters['@LoginName'].Value = $LoginName
            [void]$command.Parameters.Add('@InspectOnly',[System.Data.SqlDbType]::Bit)
            $command.Parameters['@InspectOnly'].Value = $InspectOnly
            [void]$command.Parameters.Add('@LoginPassword',[System.Data.SqlDbType]::NVarChar,256)
            $command.Parameters['@LoginPassword'].Value = [DBNull]::Value
            if ($null -ne $Secret) {
                $script:f2Diagnostic.Phase = 'PoliticaSecreto'
                $buffer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secret)
                $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($buffer)
                if ($plain.Length -lt 16 -or $plain.Length -gt 128 -or
                    $plain -cnotmatch '\A[A-Za-z0-9!@#$%^&*_+=-]+\z' -or
                    $plain -cnotmatch '[A-Z]' -or $plain -cnotmatch '[a-z]' -or
                    $plain -cnotmatch '[0-9]' -or $plain -cnotmatch '[!@#$%^&*_+=-]' -or
                    $plain.IndexOf($LoginName,[StringComparison]::OrdinalIgnoreCase) -ge 0) {
                    throw 'Contrasena fuera de politica ASCII.'
                }
                $command.Parameters['@LoginPassword'].Value = $plain
            }
        }
        $script:f2Diagnostic.Phase = 'ConexionSQL'
        $connection.Open()
        $script:f2Diagnostic.Phase = 'EjecucionSQL'
        $result = $command.ExecuteScalar()
        $script:f2Diagnostic.Phase = 'Resultado'
        return $result
    }
    finally {
        if ($null -ne $command) { $command.Parameters.Clear(); $command.Dispose() }
        $plain = $null
        if ($buffer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($buffer) }
        # Cerrar conexion sin pooling revierte transacciones abiertas aun por errores de compilacion.
        if ($null -ne $connection) { $connection.Dispose() }
    }
}

$admin = $null; $adminSecret = $null; $sqlCredential = $null; $secret = $null
$currentStep = $Stage
try {
    Add-Type -AssemblyName System.Data
    [void](Get-LocalTarget)
    $script:f2Diagnostic.Phase = 'Credencial'
    $admin = Get-Credential -Message 'Administrador SQL local autorizado; no se usara en Spring Boot'
    if ($null -eq $admin) { throw 'Cancelado.' }
    $adminSecret = $admin.Password.Copy()
    $adminSecret.MakeReadOnly()
    $sqlCredential = [System.Data.SqlClient.SqlCredential]::new($admin.UserName,$adminSecret)
    if ($Stage -eq 'Logins') {
        foreach ($name in @('ferrestock_auth_app','ferrestock_auth_migrator',
                             'ferrestock_auth_test_app','ferrestock_report_reader')) {
            $currentStep = $name
            $script:f2Diagnostic.Cycle = 'Inicial'
            $status = Invoke-LocalStage -Database master -File '10-create-login.sql' -LoginName $name
            if ($status -eq 'EXISTENTE VALIDADO') { Write-Output "$name EXISTENTE VALIDADO"; continue }
            if ($status -ne 'NUEVO') { throw 'Estado inesperado.' }
            Write-Output "$name NUEVO: pendiente de creacion"
            try {
                $script:f2Diagnostic.Phase = 'Credencial'
                $secret = Read-Host 'Secreto nuevo (ASCII aprobado, 16-128, cuatro categorias)' -AsSecureString
                $script:f2Diagnostic.Cycle = 'Creacion'
                $status = Invoke-LocalStage -Database master -File '10-create-login.sql' -LoginName $name -InspectOnly $false -Secret $secret
                if ($status -notin @('CREADO','EXISTENTE VALIDADO')) { throw 'Estado inesperado.' }
                $script:f2Diagnostic.Cycle = 'Posterior'
                $verified = Invoke-LocalStage -Database master -File '10-create-login.sql' -LoginName $name
                if ($verified -ne 'EXISTENTE VALIDADO') { throw 'Validacion posterior de login fallida.' }
                Write-Output "$name $status"
            }
            finally { if ($null -ne $secret) { $secret.Dispose(); $secret = $null } }
        }
    }
    else {
        $stages = @{
            AuthDev = @('ferrestock_auth_dev','20-auth-dev.sql')
            AuthTest = @('ferrestock_auth_test','21-auth-test.sql')
            Reports = @('ferrestock_ventas','30-report-reader.sql')
            AuthGrantsDev = @('ferrestock_auth_dev','40-auth-object-grants.sql')
            AuthGrantsTest = @('ferrestock_auth_test','40-auth-object-grants.sql')
            ReportGrants = @('ferrestock_ventas','41-report-object-grants.sql')
            Preflight = @('master','')
        }
        $selected = $stages[$Stage]
        [void](Invoke-LocalStage -Database $selected[0] -File $selected[1])
        # Validacion posterior en una conexion NUEVA.
        $script:f2Diagnostic.Cycle = 'Posterior'
        [void](Invoke-LocalStage -Database $selected[0] -File '')
        Write-Output "$Stage VALIDADO"
    }
}
catch {
    # No exponer ErrorRecord, Exception, CommandText ni parametros.
    $safeMessage = Format-F2Failure -Exception $_.Exception -Step $currentStep -Context $script:f2Diagnostic
    # Nueva excepcion SIN InnerException ni ErrorRecord original.
    throw [InvalidOperationException]::new($safeMessage)
}
finally {
    if ($null -ne $secret) { $secret.Dispose() }
    $sqlCredential = $null
    if ($null -ne $adminSecret) { $adminSecret.Dispose() }
    if ($null -ne $admin) { $admin.Password.Dispose(); $admin = $null }
}
