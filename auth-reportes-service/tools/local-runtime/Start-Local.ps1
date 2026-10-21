<#
Windows PowerShell 5.1+, Java21, Node22.12+; run from any directory.
One-time classes build (no SQL): from auth-reportes-service, .\mvnw.cmd -B test
Then .\mvnw.cmd -B dependency:build-classpath '-Dmdep.outputFile=target/local-runtime-classpath.txt'
From frontend: npm.cmd ci (if node_modules is missing).
Start: powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File "<repo>\auth-reportes-service\tools\local-runtime\Start-Local.ps1"
Check only: same command with -Check. Existing verified services are never stopped.
Keep the launching console open. Enter stops only processes it created, via stdin.
No schema setup, administrator creation or SQL privilege operations are performed.
#>
param([switch]$Check)
$ErrorActionPreference='Stop'
$module=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$frontend=Join-Path (Split-Path $module) 'frontend'
$owned=[Collections.Generic.List[Diagnostics.Process]]::new()
$sqlPassword=$null;$storePassword=$null
function Test-Health {
 try{
  $response=Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8081/api/health' -TimeoutSec 3
  $body=$response.Content|ConvertFrom-Json
  return ($response.StatusCode -eq 200 -and $body.service -ceq 'auth-reportes-service' -and $body.status -ceq 'UP')
 }catch{return $false}
}
function Test-Proxy {
 try{
  $response=Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:5173/api/health' -TimeoutSec 3
  $body=$response.Content|ConvertFrom-Json
  return ($response.StatusCode -eq 200 -and $body.service -ceq 'auth-reportes-service' -and $body.status -ceq 'UP')
 }catch{return $false}
}
function Test-Listening([int]$Port){return @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue).Count -gt 0}
function Get-Plain([Security.SecureString]$Secret){
 $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secret)
 try{return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)}
 finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)}
}
function Start-Owned([string]$Executable,[string]$Arguments,[string]$Directory,[hashtable]$Values){
 $info=[Diagnostics.ProcessStartInfo]::new()
 $info.FileName=$Executable;$info.Arguments=$Arguments;$info.WorkingDirectory=$Directory
 $info.UseShellExecute=$false;$info.CreateNoWindow=$true
 $info.RedirectStandardInput=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
 # Never hand SQL/application credentials inherited by this console to Vite or Java accidentally.
 foreach($name in @($info.EnvironmentVariables.Keys)){
  if($name -match '(?i)PASSWORD|SECRET|TOKEN|^AUTH_.*DB_|^SQL_TLS_|^SPRING_PROFILES_ACTIVE$'){$info.EnvironmentVariables.Remove($name)}
 }
 foreach($name in $Values.Keys){$info.EnvironmentVariables[$name]=[string]$Values[$name]}
 $process=[Diagnostics.Process]::new();$process.StartInfo=$info
 try{
  if(-not $process.Start()){throw 'PROCESS_START_REJECTED'}
  $owned.Add($process)
  # Consume output without persisting logs or rendering unknown driver exceptions.
  $process.BeginOutputReadLine();$process.BeginErrorReadLine()
  return $process
 }finally{
  foreach($name in $Values.Keys){$info.EnvironmentVariables.Remove($name)}
  $Values.Clear()
 }
}
function Stop-Owned {
 foreach($process in $owned){
  try{
   if(-not $process.HasExited){
    $process.StandardInput.WriteLine('STOP_FERRESTOCK_LOCAL');$process.StandardInput.Close()
    if(-not $process.WaitForExit(30000)){Write-Warning ('Parada no confirmada del proceso propio PID '+$process.Id+'. No se fuerza ni se detienen procesos ajenos.')}
   }
  }catch{Write-Warning 'No se pudo confirmar la parada de un proceso propio; no se fuerza.'}
  finally{$process.Dispose()}
 }
}
try{
 $backendReady=Test-Health;$proxyReady=Test-Proxy
 if($Check){[pscustomobject]@{BackendHealth=$backendReady;FrontendProxy=$proxyReady;ReadOnly=$true;SqlOperations=0}|ConvertTo-Json;return}
 foreach($name in @('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS')){if(-not [string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($name))){throw 'AMBIENT_JAVA_OVERRIDE_REJECTED'}}
 if(-not $backendReady -and (Test-Listening 8081)){throw 'PORT_8081_IN_USE_BY_UNVERIFIED_SERVICE'}
 if(-not $proxyReady -and (Test-Listening 5173)){throw 'PORT_5173_IN_USE_BY_UNVERIFIED_SERVICE'}
 if(-not $backendReady){
  $classes=Join-Path $module 'target/classes'
  $classpathFile=Join-Path $module 'target/local-runtime-classpath.txt'
  if(-not(Test-Path (Join-Path $classes 'pe/edu/cibertec/ferrestock/authreportes/LocalRuntime.class')) -or -not(Test-Path $classpathFile)){throw 'BUILD_AND_CLASSPATH_REQUIRED'}
  $tcp=[Net.Sockets.TcpClient]::new()
  try{$connect=$tcp.ConnectAsync('127.0.0.1',14333);if(-not $connect.Wait(3000) -or -not $tcp.Connected){throw 'SQL_TCP_UNAVAILABLE'}}finally{$tcp.Dispose()}
  $storePath=Read-Host 'Ruta ABSOLUTA del JKS externo existente (no se crea ni modifica)'
  if(-not [IO.Path]::IsPathRooted($storePath)){throw 'EXTERNAL_EXISTING_JKS_REQUIRED'}
  $storePath=[IO.Path]::GetFullPath($storePath)
  $repository=[IO.Path]::GetFullPath((Split-Path $module)).TrimEnd('\')+'\'
  if($storePath.StartsWith($repository,[StringComparison]::OrdinalIgnoreCase) -or -not(Test-Path -LiteralPath $storePath -PathType Leaf)){throw 'EXTERNAL_EXISTING_JKS_REQUIRED'}
  $storePassword=Read-Host 'Password ORIGINAL del JKS existente' -AsSecureString
  $sqlPassword=Read-Host 'PasswordSQL EXISTENTE de ferrestock_auth_app (NO migrador/admin)' -AsSecureString
  if($sqlPassword.Length -eq 0 -or $storePassword.Length -eq 0){throw 'CREDENTIAL_REQUIRED'}
  $cp=$classes+';'+[IO.File]::ReadAllText($classpathFile).Trim()
  $values=@{AUTH_DB_URL='jdbc:sqlserver://127.0.0.1:14333;databaseName=ferrestock_auth_dev;encrypt=true;trustServerCertificate=false';AUTH_DB_USER='ferrestock_auth_app';AUTH_DB_PASSWORD=(Get-Plain $sqlPassword);SQL_TLS_TRUSTSTORE_PATH=$storePath;SQL_TLS_TRUSTSTORE_PASSWORD=(Get-Plain $storePassword);LOGGING_LEVEL_ROOT='INFO';SPRING_PROFILES_ACTIVE='auth-db'}
  [void](Start-Owned 'java.exe' ('-cp "'+$cp+'" pe.edu.cibertec.ferrestock.authreportes.LocalRuntime') $module $values)
  for($attempt=0;$attempt -lt 60 -and -not(Test-Health);$attempt++){Start-Sleep -Seconds 1}
  if(-not(Test-Health)){throw 'BACKEND_HEALTH_NOT_READY'}
 }else{Write-Host 'Backend existente verificado; se preserva y no se detendra.'}
 if(-not $proxyReady){
  if(-not(Test-Path (Join-Path $frontend 'node_modules/vite/package.json'))){throw 'FRONTEND_NPM_CI_REQUIRED'}
  [void](Start-Owned 'node.exe' ('"'+(Join-Path $PSScriptRoot 'frontend.mjs')+'"') $frontend @{VITE_API_BASE_URL='/api'})
  for($attempt=0;$attempt -lt 30 -and -not(Test-Proxy);$attempt++){Start-Sleep -Seconds 1}
  if(-not(Test-Proxy)){throw 'FRONTEND_PROXY_NOT_READY'}
 }else{Write-Host 'Frontend/proxy existente verificado; se preserva y no se detendra.'}
 Write-Host 'PASS: http://127.0.0.1:5173/ ; backend health yproxy conformes.'
 if($owned.Count -gt 0){[void](Read-Host 'Pulsa Enter para parada ordenada SOLO de los procesos creados por este lanzador')}
}catch{
 $known=@('PROCESS_START_REJECTED','AMBIENT_JAVA_OVERRIDE_REJECTED','PORT_8081_IN_USE_BY_UNVERIFIED_SERVICE','PORT_5173_IN_USE_BY_UNVERIFIED_SERVICE','BUILD_AND_CLASSPATH_REQUIRED','SQL_TCP_UNAVAILABLE','EXTERNAL_EXISTING_JKS_REQUIRED','CREDENTIAL_REQUIRED','BACKEND_HEALTH_NOT_READY','FRONTEND_NPM_CI_REQUIRED','FRONTEND_PROXY_NOT_READY')
 $code=if($_.Exception.Message -cin $known){$_.Exception.Message}else{'LOCAL_START_REJECTED'}
 Write-Host ('BLOQUEADO: '+$code+'. Sin mensajes de driver ni reintentos automaticos de credenciales.')
}finally{Stop-Owned;foreach($secret in @($sqlPassword,$storePassword)){if($null -ne $secret){$secret.Dispose()}}}
