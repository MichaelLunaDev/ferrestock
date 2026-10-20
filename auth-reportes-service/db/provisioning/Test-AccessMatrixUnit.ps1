# Pruebas complementarias: objetos locales y transporte simulado, nunca conexiones reales.
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Data
$unitProvisioningRoot=$PSScriptRoot
$path=Join-Path $PSScriptRoot 'Verify-AccessMatrix.ps1'
$source=Get-Content -LiteralPath $path -Raw
$tokens=$null; $parseErrors=$null
$ast=[Management.Automation.Language.Parser]::ParseInput($source,[ref]$tokens,[ref]$parseErrors)
if ($parseErrors.Count) { throw 'Parser Verify-AccessMatrix: FAIL' }
$functions=@($ast.FindAll({param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst]},$true))
foreach ($function in $functions) { . ([scriptblock]::Create($function.Extent.Text)) }
$script:assertions=0
function Assert-Unit([bool]$Condition,[string]$Label) {
    if (-not $Condition) { throw "FAIL: $Label" }; $script:assertions++
}
$canary='CANARY_SECRET_NOT_REAL!; Password=CANARY; GRANT CONTROL TO fake;'
function Assert-UnitSafe([string]$Text) {
    Assert-Unit (-not ($Text -match 'CANARY|Password=|GRANT CONTROL|Data Source=')) 'sin secretos en salida'
}
function New-UnitSql([int[]]$Numbers,[Exception]$Inner=$null) {
    $flags=[Reflection.BindingFlags]'Instance,NonPublic'
    $ctor=[System.Data.SqlClient.SqlError].GetConstructors($flags) | Where-Object { $_.GetParameters().Count -eq 7 } | Select-Object -First 1
    $collection=[Activator]::CreateInstance([System.Data.SqlClient.SqlErrorCollection],$true)
    foreach ($number in $Numbers) {
        $errorObject=$ctor.Invoke(@($number,[byte]1,[byte]14,'fake',$canary,'fake',[int]1))
        [void]$collection.GetType().GetMethod('Add',$flags).Invoke($collection,@($errorObject))
    }
    $factory=[System.Data.SqlClient.SqlException].GetMethods([Reflection.BindingFlags]'Static,NonPublic') |
        Where-Object { $_.Name -eq 'CreateException' -and $_.GetParameters().Count -eq 4 -and $_.GetParameters()[2].ParameterType -eq [Guid] } | Select-Object -First 1
    return $factory.Invoke($null,@($collection,'16.0',[Guid]::Empty,$Inner))
}

Assert-VerifyReadOnly (Get-Content -LiteralPath (Join-Path $PSScriptRoot '90-effective-permissions.sql') -Raw)
Assert-VerifyReadOnly "SELECT 'GRANT EXECUTE INSERT', 'a''b'; -- DELETE fake"
foreach ($sql in @('GRANT CONNECT TO fake;','DENY CONNECT TO fake;','REVOKE CONNECT TO fake;',
    'INSERT fake VALUES(1);','UPDATE fake SET a=1;','DELETE fake;','CREATE TABLE fake(a int);',
    'SELECT 1 INTO fake;','EXECUTE AS LOGIN=''fake'';','EXEC(''SELECT 1'');','DBCC CHECKDB;',
    'SELECT 1; /* nested */')) {
    $blocked=$false; try { Assert-VerifyReadOnly $sql } catch { $blocked=$true; Assert-UnitSafe $_.Exception.Message }
    Assert-Unit $blocked 'SQL no aprobado bloqueado'
}
foreach ($actual in @($null,[DBNull]::Value,-1,2)) { Assert-Unit ((Get-PermissionVerdict $actual 0) -eq 'PENDIENTE') 'permiso indeterminado nunca PASS' }
Assert-Unit ((Get-PermissionVerdict 0 0) -eq 'PASS') 'permiso esperado'
Assert-Unit ((Get-PermissionVerdict 1 0) -eq 'FAIL') 'permiso excesivo'
$reserved=@($ast.FindAll({param($n) $n -is [Management.Automation.Language.VariableExpressionAst] -and
    $n.VariablePath.UserPath -in @('home','profile','CODEX_HOME')},$true))
Assert-Unit ($reserved.Count -eq 0) 'sin reutilizar variables automaticas reservadas'

foreach ($codes in @(@(4060),@(4060,18456),@(18456),@(-2),@(4064),@(4060,53))) {
    $failure=Get-AccessFailure -Exception (New-UnitSql $codes) -ExpectedAllow $false -HomeVerified $true -OnlineBefore $true -OnlineAfter $true
    $expected=if (($codes -contains 4060) -and @($codes | Where-Object { $_ -notin @(4060,18456) }).Count -eq 0) {'PASS'}else{'PENDIENTE'}
    Assert-Unit ($failure.Result -eq $expected) 'clasificacion de rechazo por codigos'
    Assert-UnitSafe ($failure | ConvertTo-Json -Depth 5)
}
foreach ($condition in @(@($false,$true,$true),@($true,$false,$true),@($true,$true,$false))) {
    $failure=Get-AccessFailure -Exception (New-UnitSql @(4060)) -ExpectedAllow $false -HomeVerified $condition[0] -OnlineBefore $condition[1] -OnlineAfter $condition[2]
    Assert-Unit ($failure.Result -eq 'PENDIENTE') '4060 sin contexto no demuestra aislamiento'
}
$tls=[ComponentModel.Win32Exception]::new(-2146893019,$canary)
$failure=Get-AccessFailure -Exception (New-UnitSql @(4060) $tls) -ExpectedAllow $false -HomeVerified $true -OnlineBefore $true -OnlineAfter $true
Assert-Unit ($failure.Result -eq 'PENDIENTE' -and $failure.Origin -eq 'ConexionTLS') 'TLS nunca se acepta como rechazo esperado'
Assert-UnitSafe ($failure | ConvertTo-Json -Depth 5)
$failure=Get-AccessFailure -Exception ([Exception]::new('TLS 4060 '+$canary)) -ExpectedAllow $false -HomeVerified $true -OnlineBefore $true -OnlineAfter $true
Assert-Unit ($failure.Result -eq 'PENDIENTE') 'no inferir por mensaje desconocido'
Assert-UnitSafe ($failure | ConvertTo-Json -Depth 5)

$secret=ConvertTo-SecureString 'FakeOnly_Verification!123' -AsPlainText -Force; $secret.MakeReadOnly()
$credential=[System.Data.SqlClient.SqlCredential]::new('fake',$secret)
try {
    foreach ($trust in @($false,$true)) {
        $connection=New-VerifyConnection 'ferrestock_auth_dev' $credential $trust
        try {
            $builder=[System.Data.SqlClient.SqlConnectionStringBuilder]::new($connection.ConnectionString)
            Assert-Unit ($connection.State -eq 'Closed' -and $connection.Credential -eq $credential) 'preparacion ADO.NET cerrada'
            Assert-Unit ($builder.Encrypt -and $builder.TrustServerCertificate -eq $trust) 'TLS explicito'
            Assert-Unit (-not $builder.Pooling -and -not $builder.PersistSecurityInfo -and $builder.ConnectRetryCount -eq 0 -and $builder.ConnectTimeout -eq 10) 'sin pooling ni reintentos'
            Assert-Unit (-not $builder.UserID -and -not $builder.Password) 'sin secretos en cadena'
        } finally { $connection.Dispose() }
    }
} finally { $credential=$null; $secret.Dispose() }

# Target real se prueba con una respuesta Docker simulada, sin consultar Docker.
$script:inspect='true|bridge|{"1433/tcp":[{"HostIp":"127.0.0.1","HostPort":"14333"}]}|unit-machine'
function docker { $global:LASTEXITCODE=0; return $script:inspect }
Assert-Unit ((Get-VerifyTarget) -eq 'unit-machine') 'target local fijo'
foreach ($bad in @($script:inspect.Replace('127.0.0.1','0.0.0.0'),$script:inspect.Replace('bridge','host'),$script:inspect.Replace('true','false'))) {
    $good=$script:inspect; $script:inspect=$bad; $blocked=$false
    try { Get-VerifyTarget | Out-Null } catch { $blocked=$true }; $script:inspect=$good
    Assert-Unit $blocked 'target no aprobado rechazado'
}

# Probar el presupuesto en Open-VerifyConnection REAL, con objeto de transporte local.
$script:unitBuilds=0; $script:unitOpens=0; $script:unitDisposes=0
function New-VerifyConnection {
    $script:unitBuilds++
    $fake=[pscustomobject]@{}
    $fake | Add-Member -MemberType ScriptMethod -Name Open -Value { $script:unitOpens++; throw [Exception]::new($canary) }
    $fake | Add-Member -MemberType ScriptMethod -Name Dispose -Value { $script:unitDisposes++ }
    return $fake
}
$script:verifyAttempts=12; $caught=$null
try { Open-VerifyConnection 'ferrestock_auth_dev' $null $false 'unit-machine' | Out-Null } catch { $caught=$_.Exception }
Assert-Unit ($null -ne $caught -and $script:unitBuilds -eq 0 -and $script:unitOpens -eq 0 -and $script:verifyAttempts -eq 12) 'intento 13 bloqueado antes del transporte'
$script:verifyAttempts=11; $caught=$null
try { Open-VerifyConnection 'ferrestock_auth_dev' $null $false 'unit-machine' | Out-Null } catch { $caught=$_.Exception }
Assert-Unit ($null -ne $caught -and $script:verifyAttempts -eq 12 -and $script:unitOpens -eq 1 -and $script:unitDisposes -eq 1) 'ultimo intento contado y conexion fallida liberada'
$script:verifyAttempts=0; $builds=$script:unitBuilds; $caught=$null
try { Open-VerifyConnection 'ferrestock_auth_dev' $null $false 'different-machine' | Out-Null } catch { $caught=$_.Exception }
Assert-Unit ($null -ne $caught -and $script:unitBuilds -eq $builds -and $script:verifyAttempts -eq 0) 'cambio de identidad bloqueado sin conectar'

# Orquestacion completa: solo transporte/lecturas se reemplazan en memoria.
$stubs=@{}
$stubs['Get-VerifyTarget']='function Get-VerifyTarget { return ''unit-machine'' }'
$stubs['Open-VerifyConnection']=@'
function Open-VerifyConnection($Database,$Credential,$Trust,$Machine) {
    $script:verifyAttempts++
    $login=$Credential.UserId
    $homes=@{ferrestock_auth_app='ferrestock_auth_dev';ferrestock_auth_migrator='ferrestock_auth_dev';ferrestock_auth_test_app='ferrestock_auth_test';ferrestock_report_reader='ferrestock_ventas'}
    if ($Database -ne $homes[$login] -and $script:unitMode -ne 'UnexpectedAccess') {
        if ($script:unitMode -eq 'AuthError') { throw (New-UnitSql @(18456)) }
        if ($script:unitMode -eq 'TlsError') { throw [Security.Authentication.AuthenticationException]::new($canary) }
        throw (New-UnitSql @(4060,18456))
    }
    $connection=[pscustomobject]@{Login=$login; Database=$Database}
    $connection | Add-Member -MemberType ScriptMethod -Name Dispose -Value {} -PassThru
}
'@
$stubs['Test-VerifyOnline']='function Test-VerifyOnline { return $true }'
$stubs['Invoke-Verify90']=@'
function Invoke-Verify90 {
    if ($script:unitMode -eq 'SQL90Fail') { throw (New-UnitSql @(51304)) }
}
'@
$stubs['Get-VerifyTable']=@'
function Get-VerifyTable($Connection,$Sql,$Parameters) {
    $table=[System.Data.DataTable]::new()
    if ($Sql.Contains('AS original_login')) {
        foreach ($name in @('original_login','database_user','database_name','machine_name','server_name','version','sid_matches')) { [void]$table.Columns.Add($name) }
        [void]$table.Rows.Add($Connection.Login,$Connection.Login,$Connection.Database,'unit-machine','unit-machine','16.0.4250.1','1')
    } else {
        [void]$table.Columns.Add('check_name'); [void]$table.Columns.Add('expected',[int]); [void]$table.Columns.Add('actual',[int])
        $expected=if ($Connection.Login -eq 'ferrestock_auth_migrator') {1}else{0}
        $actual=if ($script:unitMode -eq 'NullPermission') {[DBNull]::Value}else{$expected}
        [void]$table.Rows.Add('CREATE TABLE',$expected,$actual)
    }
    return ,$table
}
'@
$simulated=$source
foreach ($function in $functions | Sort-Object { $_.Extent.StartOffset } -Descending) {
    if ($stubs.ContainsKey($function.Name)) {
        $simulated=$simulated.Substring(0,$function.Extent.StartOffset)+$stubs[$function.Name]+$simulated.Substring($function.Extent.EndOffset)
    }
}
$simulated=$simulated.Replace('$PSScriptRoot','$unitProvisioningRoot')
function Get-Credential {
    param($UserName,$Message)
    $fake=ConvertTo-SecureString 'FakeOnly_Verification!123' -AsPlainText -Force
    return [Management.Automation.PSCredential]::new($UserName,$fake)
}
foreach ($mode in @('Good','AuthError','TlsError','UnexpectedAccess','NullPermission','SQL90Fail')) {
    $script:unitMode=$mode; $output=[Collections.Generic.List[string]]::new(); $caught=$null
    try { & ([scriptblock]::Create($simulated)) -TrustLocalDevelopmentCertificate | ForEach-Object { [void]$output.Add([string]$_) } }
    catch { $caught=$_.Exception }
    $json=$output -join [Environment]::NewLine
    Assert-UnitSafe $json
    if ($null -ne $caught) { Assert-UnitSafe $caught.Message }
    $report=$json | ConvertFrom-Json
    Assert-Unit ($report.Matrix.Count -eq 12 -and $report.Attempts -le 12) 'matriz completa y presupuesto controlado'
    $expected=if ($mode -eq 'Good') {'PASS'} elseif ($mode -in @('UnexpectedAccess','SQL90Fail')) {'FAIL'} else {'PENDIENTE'}
    Assert-Unit ($report.Result -eq $expected) "orquestacion $mode"
    Assert-Unit (($null -eq $caught) -eq ($expected -eq 'PASS')) 'bloqueo ante falta de evidencia'
    if ($mode -eq 'Good') { Assert-Unit ($report.Attempts -eq 12 -and @($report.Matrix | Where-Object Result -ne 'PASS').Count -eq 0) 'doce conexiones simuladas sin saltos' }
    if ($mode -in @('AuthError','TlsError')) { Assert-Unit (@($report.Matrix | Where-Object { $_.Expected -eq 'RECHAZAR' -and $_.Result -eq 'PASS' }).Count -eq 0) 'fallo de transporte/autenticacion no prueba aislamiento' }
}
Write-Output "PASS: $script:assertions aserciones del verificador; seis flujos simulados; 0 conexiones SQL reales."
