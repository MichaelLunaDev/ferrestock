# VERIFY-014: cuatro credenciales interactivas, como maximo 12 aperturas, solo lectura.
[CmdletBinding()]
param([switch]$TrustLocalDevelopmentCertificate)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Data

function Get-VerifyTarget {
    $raw=& docker inspect --format '{{json .State.Running}}|{{.HostConfig.NetworkMode}}|{{json .NetworkSettings.Ports}}|{{.Config.Hostname}}' ferrestock-mssql 2>$null
    if ($LASTEXITCODE -ne 0) { throw [InvalidOperationException]::new('VERIFY_TARGET') }
    $fields=($raw -join '').Split('|')
    if ($fields.Count -ne 4 -or $fields[0] -ne 'true' -or $fields[1] -eq 'host' -or
        $fields[3] -notmatch '\A[A-Za-z0-9-]{1,128}\z') { throw [InvalidOperationException]::new('VERIFY_TARGET') }
    $ports=$fields[2] | ConvertFrom-Json
    $published=@($ports.PSObject.Properties | Where-Object { $null -ne $_.Value })
    $bindings=@($ports.'1433/tcp')
    if ($published.Count -ne 1 -or $published[0].Name -ne '1433/tcp' -or $bindings.Count -ne 1 -or
        $bindings[0].HostIp -ne '127.0.0.1' -or $bindings[0].HostPort -ne '14333') { throw [InvalidOperationException]::new('VERIFY_TARGET') }
    return $fields[3]
}
function Assert-VerifyReadOnly([string]$Sql) {
    # Control adicional para el archivo revisado, no un parser SQL de uso general.
    # No admitir bloques de comentario ni SQL dinamico en este verificador.
    if ($Sql -match '/\*|\*/') { throw [InvalidOperationException]::new('VERIFY_SQL') }
    $code=[regex]::Replace($Sql,"'(?:''|[^'])*'|--[^\r\n]*",' ')
    if ($code -match '(?i)\b(CREATE|ALTER|DROP|GRANT|DENY|REVOKE|INSERT|UPDATE|DELETE|MERGE|TRUNCATE|INTO|EXEC|EXECUTE|REVERT|DBCC|BACKUP|RESTORE|RECONFIGURE|OPENROWSET|OPENQUERY|OPENDATASOURCE)\b') {
        throw [InvalidOperationException]::new('VERIFY_SQL')
    }
}
function New-VerifyConnection([string]$Database,[System.Data.SqlClient.SqlCredential]$Credential,[bool]$Trust) {
    if ($Database -notin @('ferrestock_auth_dev','ferrestock_auth_test','ferrestock_ventas')) { throw [InvalidOperationException]::new('VERIFY_DATABASE') }
    $builder=[System.Data.SqlClient.SqlConnectionStringBuilder]::new()
    $builder.set_DataSource('127.0.0.1,14333'); $builder.set_InitialCatalog($Database)
    $builder.set_Encrypt($true); $builder.set_TrustServerCertificate($Trust)
    $builder.set_PersistSecurityInfo($false); $builder.set_Pooling($false)
    $builder.set_ConnectTimeout(10); $builder.set_ConnectRetryCount(0)
    $connection=[System.Data.SqlClient.SqlConnection]::new($builder.ConnectionString)
    $connection.Credential=$Credential
    return $connection
}
function Open-VerifyConnection([string]$Database,[System.Data.SqlClient.SqlCredential]$Credential,[bool]$Trust,[string]$Machine) {
    if ((Get-VerifyTarget) -cne $Machine -or $script:verifyAttempts -ge 12) { throw [InvalidOperationException]::new('VERIFY_TARGET_OR_LIMIT') }
    $connection=New-VerifyConnection $Database $Credential $Trust
    try {
        $script:verifyAttempts++
        $connection.Open()
        return $connection
    } catch { $connection.Dispose(); throw }
}
function Get-AccessFailure {
    param([Exception]$Exception,[bool]$ExpectedAllow=$true,[bool]$HomeVerified=$false,
        [bool]$OnlineBefore=$false,[bool]$OnlineAfter=$false,
        [ValidateSet('Preparacion','Destino','ArchivoSQL','Credencial','Conexion','Identidad','Permisos','SQL90','EstadoBases')][string]$Operation='Conexion')
    $sql=$null; $tls=$false; $cursor=$Exception
    for ($i=0;$null -ne $cursor -and $i -lt 16;$i++) {
        if ($cursor -is [System.Data.SqlClient.SqlException] -and $null -eq $sql) { $sql=$cursor }
        if ($cursor -is [System.Security.Authentication.AuthenticationException] -or
            ($cursor -is [ComponentModel.Win32Exception] -and $cursor.NativeErrorCode -in @(-2146893019,-2146762487,-2146762481,-2146762495))) { $tls=$true }
        $cursor=$cursor.InnerException
    }
    $errors=@(); $numbers=@()
    if ($null -ne $sql) {
        $errors=@($sql.Errors | ForEach-Object { [pscustomobject]@{Number=[int]$_.Number; Severity=[int]$_.Class; State=[int]$_.State} })
        $numbers=@($errors | ForEach-Object { $_.Number })
    }
    $status='PENDIENTE'; $origin=$Operation
    if ($tls) { $origin='ConexionTLS' }
    elseif ($numbers -contains 4060 -and @($numbers | Where-Object { $_ -notin @(4060,18456) }).Count -eq 0 -and
        $Operation -eq 'Conexion' -and -not $ExpectedAllow -and $HomeVerified -and $OnlineBefore -and $OnlineAfter) {
        $status='PASS'; $origin='AccesoBaseRechazado'
    }
    elseif (@($numbers | Where-Object { $_ -in @(18452,18456,18470,18486,18487,18488) }).Count) { $origin='AutenticacionNoDemostrada' }
    elseif (@($numbers | Where-Object { $_ -in @(-2,2,53,64,121,233,258,10060,10061,11001) }).Count) { $origin='Infraestructura' }
    elseif ($numbers -contains 4060 -and $ExpectedAllow) { $status='FAIL'; $origin='BaseAsignadaRechazada' }
    elseif (@($numbers | Where-Object { $_ -ge 51300 -and $_ -le 51306 }).Count) { $status='FAIL'; $origin='ValidacionSQL90' }
    elseif ($Operation -in @('ArchivoSQL','Identidad')) { $status='FAIL' }
    elseif ($numbers.Count) { $origin='SQLNoClasificado' }
    return [pscustomobject]@{Result=$status; Origin=$origin; SqlErrors=$errors}
}
function Get-VerifyTable($Connection,[string]$Sql,[hashtable]$Parameters=@{}) {
    Assert-VerifyReadOnly $Sql
    $command=$Connection.CreateCommand(); $reader=$null
    try {
        $command.CommandText=$Sql; $command.CommandTimeout=30
        foreach ($name in $Parameters.Keys) {
            [void]$command.Parameters.Add($name,[System.Data.SqlDbType]::NVarChar,128)
            $command.Parameters[$name].Value=$Parameters[$name]
        }
        $reader=$command.ExecuteReader()
        $table=[System.Data.DataTable]::new(); $table.Load($reader)
        return ,$table
    } finally { if ($null -ne $reader) { $reader.Dispose() }; $command.Dispose() }
}
function Test-VerifyOnline($Connection,[string]$Database) {
    $table=Get-VerifyTable $Connection "SELECT COUNT(*) AS matches FROM sys.databases WHERE name=@Database AND state_desc='ONLINE';" @{'@Database'=$Database}
    return $table.Rows.Count -eq 1 -and [int]$table.Rows[0]['matches'] -eq 1
}
function Get-PermissionVerdict($Actual,[int]$Expected) {
    if ($null -eq $Actual -or $Actual -is [DBNull]) { return 'PENDIENTE' }
    if ([int]$Actual -notin @(0,1)) { return 'PENDIENTE' }
    if ([int]$Actual -eq $Expected) { return 'PASS' }
    return 'FAIL'
}
function Invoke-Verify90($Connection,[string]$Sql) {
    Assert-VerifyReadOnly $Sql
    $command=$Connection.CreateCommand(); $reader=$null
    try {
        $command.CommandText=$Sql; $command.CommandTimeout=30; $reader=$command.ExecuteReader()
        do { while ($reader.Read()) { } } while ($reader.NextResult())
    } finally { if ($null -ne $reader) { $reader.Dispose() }; $command.Dispose() }
}
function Set-VerifyRow($Row,[string]$Result,[string]$Origin,$SqlErrors=@()) {
    $Row.Result=$Result; $Row.Origin=$Origin; $Row.SqlErrors=@($SqlErrors)
}

$profiles=@(
    @('ferrestock_auth_app','ferrestock_auth_dev'),@('ferrestock_auth_migrator','ferrestock_auth_dev'),
    @('ferrestock_auth_test_app','ferrestock_auth_test'),@('ferrestock_report_reader','ferrestock_ventas')
)
$databases=@('ferrestock_auth_dev','ferrestock_auth_test','ferrestock_ventas')
$matrix=@(foreach ($account in $profiles) { foreach ($db in $databases) {
    [pscustomobject]@{Login=$account[0]; Database=$db; Expected=if($db -eq $account[1]){'PERMITIR'}else{'RECHAZAR'};
        Result='PENDIENTE'; Origin='NoEjecutado'; SqlErrors=@()}
} })
$evidence=[Collections.Generic.List[object]]::new(); $script:verifyAttempts=0
$operation='Destino'; $setupFailure=$null
try {
    $machine=Get-VerifyTarget
    $operation='ArchivoSQL'
    $sql90=Get-Content -LiteralPath (Join-Path $PSScriptRoot '90-effective-permissions.sql') -Raw
    Assert-VerifyReadOnly $sql90
} catch { $setupFailure=Get-AccessFailure -Exception $_.Exception -Operation $operation }
if ($null -ne $setupFailure) {
    foreach ($row in $matrix) { Set-VerifyRow $row $setupFailure.Result $setupFailure.Origin $setupFailure.SqlErrors }
} else {
    foreach ($account in $profiles) {
        $login=$account[0]; $homeDatabase=$account[1]
        $homeRow=$matrix | Where-Object { $_.Login -eq $login -and $_.Database -eq $homeDatabase }
        $credential=$null; $secret=$null; $sqlCredential=$null; $homeConnection=$null
        $operation='Credencial'
        try {
            $credential=Get-Credential -UserName $login -Message "Verificacion de solo lectura: $login"
            if ($null -eq $credential -or $credential.UserName -cne $login) { throw [InvalidOperationException]::new('VERIFY_CREDENTIAL') }
            $secret=$credential.Password.Copy(); $secret.MakeReadOnly()
            $sqlCredential=[System.Data.SqlClient.SqlCredential]::new($login,$secret)
            $operation='Conexion'
            $homeConnection=Open-VerifyConnection $homeDatabase $sqlCredential ([bool]$TrustLocalDevelopmentCertificate) $machine
            $operation='Identidad'
            $identity=Get-VerifyTable $homeConnection @"
SELECT ORIGINAL_LOGIN() AS original_login,USER_NAME() AS database_user,DB_NAME() AS database_name,
CONVERT(nvarchar(128),SERVERPROPERTY('MachineName')) AS machine_name,
CONVERT(nvarchar(128),SERVERPROPERTY('ServerName')) AS server_name,
CONVERT(nvarchar(128),SERVERPROPERTY('ProductVersion')) AS version,
CASE WHEN SUSER_SID()=(SELECT sid FROM sys.database_principals WHERE principal_id=USER_ID()) THEN 1 ELSE 0 END AS sid_matches;
"@
            $id=$identity.Rows[0]
            if ($identity.Rows.Count -ne 1 -or $id['original_login'] -cne $login -or $id['database_user'] -cne $login -or
                $id['database_name'] -cne $homeDatabase -or $id['machine_name'] -cne $machine -or $id['server_name'] -cne $machine -or
                $id['version'] -ne '16.0.4250.1' -or [int]$id['sid_matches'] -ne 1) { throw [InvalidOperationException]::new('VERIFY_IDENTITY') }
            $operation='Permisos'
            $migrator=if ($login -eq 'ferrestock_auth_migrator') {1}else{0}
            $permissionSql=@"
SELECT 'sysadmin' AS check_name,0 AS expected,IS_SRVROLEMEMBER('sysadmin') AS actual
UNION ALL SELECT 'db_owner',0,IS_MEMBER('db_owner')
UNION ALL SELECT 'DATABASE.'+v.permission_name,CASE WHEN v.permission_name='CREATE TABLE' THEN $migrator ELSE 0 END,
HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE',v.permission_name)
FROM (VALUES('CREATE TABLE'),('CREATE VIEW'),('CREATE PROCEDURE'),('CREATE FUNCTION'),('ALTER ANY SCHEMA'),
('CONTROL'),('SELECT'),('INSERT'),('UPDATE'),('DELETE'),('EXECUTE')) v(permission_name)
UNION ALL SELECT 'SCHEMA.ALTER.'+s.name,CASE WHEN s.name='auth' THEN $migrator ELSE 0 END,
HAS_PERMS_BY_NAME(QUOTENAME(s.name),'SCHEMA','ALTER') FROM sys.schemas s;
"@
            $permissions=Get-VerifyTable $homeConnection $permissionSql
            $verdicts=@(foreach ($p in $permissions.Rows) {
                [pscustomobject]@{Check=[string]$p['check_name']; Expected=[int]$p['expected'];
                    Actual=if($p['actual'] -is [DBNull]){$null}else{[int]$p['actual']}; Result=Get-PermissionVerdict $p['actual'] ([int]$p['expected'])}
            })
            [void]$evidence.Add([pscustomobject]@{Login=$login; Identity='PASS'; Permissions=$verdicts; Script90='PENDIENTE'; Objects='NO APLICA: sin lista comercial aprobada'})
            if (@($verdicts | Where-Object Result -eq 'FAIL').Count) { Set-VerifyRow $homeRow 'FAIL' 'PermisosEfectivos'; continue }
            if (@($verdicts | Where-Object Result -eq 'PENDIENTE').Count) { Set-VerifyRow $homeRow 'PENDIENTE' 'PermisosIndeterminados'; continue }
            $operation='SQL90'
            Invoke-Verify90 $homeConnection $sql90
            $evidence[$evidence.Count-1].Script90='PASS'
            Set-VerifyRow $homeRow 'PASS' 'ConexionIdentidadPermisos'
            foreach ($db in $databases | Where-Object { $_ -ne $homeDatabase }) {
                $row=$matrix | Where-Object { $_.Login -eq $login -and $_.Database -eq $db }
                $probe=$null; $operation='EstadoBases'
                try {
                    $before=Test-VerifyOnline $homeConnection $db
                    if (-not $before) { Set-VerifyRow $row 'PENDIENTE' 'BaseOnlineNoDemostrada'; continue }
                    $operation='Conexion'
                    try {
                        $probe=Open-VerifyConnection $db $sqlCredential ([bool]$TrustLocalDevelopmentCertificate) $machine
                        Set-VerifyRow $row 'FAIL' 'AccesoNoPermitidoConcedido'
                    } catch {
                        $connectionError=$_.Exception
                        $operation='EstadoBases'
                        $after=Test-VerifyOnline $homeConnection $db
                        $failure=Get-AccessFailure -Exception $connectionError -ExpectedAllow $false -HomeVerified $true -OnlineBefore $before -OnlineAfter $after
                        Set-VerifyRow $row $failure.Result $failure.Origin $failure.SqlErrors
                    }
                } catch {
                    $failure=Get-AccessFailure -Exception $_.Exception -Operation $operation
                    Set-VerifyRow $row $failure.Result $failure.Origin $failure.SqlErrors
                } finally { if ($null -ne $probe) { $probe.Dispose() } }
            }
        } catch {
            $failure=Get-AccessFailure -Exception $_.Exception -Operation $operation
            Set-VerifyRow $homeRow $failure.Result $failure.Origin $failure.SqlErrors
        } finally {
            if ($null -ne $homeConnection) { $homeConnection.Dispose() }
            $sqlCredential=$null
            if ($null -ne $secret) { $secret.Dispose() }; $secret=$null
            if ($null -ne $credential) { $credential.Password.Dispose() }; $credential=$null
        }
    }
}
$result=if (@($matrix | Where-Object Result -eq 'FAIL').Count) {'FAIL'} elseif (@($matrix | Where-Object Result -ne 'PASS').Count) {'PENDIENTE'} else {'PASS'}
[pscustomobject]@{Result=$result; Attempts=$script:verifyAttempts; MaximumAttempts=12; Matrix=$matrix; EffectiveChecks=@($evidence.ToArray());
    Closure='Requiere tambien inventario administrativo contemporaneo; no prueba DML por objeto sin objetos aprobados.'} | ConvertTo-Json -Depth 8
if ($result -ne 'PASS') { throw [InvalidOperationException]::new('VERIFY NO COMPLETO: revisar exclusivamente resultados sanitizados.') }
