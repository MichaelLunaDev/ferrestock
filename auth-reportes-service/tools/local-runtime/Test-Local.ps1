$ErrorActionPreference='Stop'
$path=Join-Path $PSScriptRoot 'Start-Local.ps1'
$tokens=$null;$parseErrors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile($path,[ref]$tokens,[ref]$parseErrors)
if(@($parseErrors).Count -ne 0){throw 'LAUNCHER_PARSE_FAILED'}
# Import ONLY our function definitions: never execute the launcher's main block.
$functions=$ast.FindAll({param($node)$node -is [Management.Automation.Language.FunctionDefinitionAst]},$false)
foreach($definition in $functions){. ([scriptblock]::Create($definition.Extent.Text))}
$owned=[Collections.Generic.List[Diagnostics.Process]]::new()
$old=[Environment]::GetEnvironmentVariable('FERRESTOCK_TEST_PASSWORD')
try{
 [Environment]::SetEnvironmentVariable('FERRESTOCK_TEST_PASSWORD','OFFLINE_CANARY','Process')
 $values=@{LOCAL_TEST_ONLY='true'}
 $program=Join-Path $PSScriptRoot 'test-owned-process.mjs'
 $process=Start-Owned 'node.exe' ('"'+$program+'"') $PSScriptRoot $values
 $pidOwned=$process.Id
 if($values.Count -ne 0){throw 'VALUES_NOT_CLEARED'}
 $process.StandardInput.WriteLine('STOP_FERRESTOCK_LOCAL');$process.StandardInput.Close()
 if(-not $process.WaitForExit(10000) -or $process.ExitCode -ne 0){throw 'OWNED_PROCESS_DID_NOT_STOP_SAFELY'}
 Stop-Owned
 $text=[IO.File]::ReadAllText($path)
 if($text -match 'FirstAdministrator|flyway:|\.migrate\(|ExecuteNonQuery|ALTER LOGIN|GRANT |CREATE USER|docker restart|Stop-Process|\.Kill\('){throw 'ADMIN_OR_FORCE_OPERATION_FOUND'}
 if($text.IndexOf('if($Check)') -gt $text.IndexOf("Read-Host 'Ruta")){throw 'CHECK_PROMPTS_FOR_SECRETS'}
 [pscustomobject]@{Result='PASS';Checks=5;OwnChildStopped=$true;InheritedSecretRemoved=$true;SqlOperations=0;ExistingProcessesTouched=$false}|ConvertTo-Json
}finally{[Environment]::SetEnvironmentVariable('FERRESTOCK_TEST_PASSWORD',$old,'Process');foreach($process in $owned){try{if(-not $process.HasExited){$process.StandardInput.Close()}}catch{}}}
