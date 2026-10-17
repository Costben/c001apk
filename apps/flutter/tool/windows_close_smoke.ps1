param(
  [Parameter(Mandatory=$true)][string]$ExePath,
  [Parameter(Mandatory=$true)][string]$ReportPath,
  [int]$Cycles=3,
  [string]$BeforeCloseScript
)
$ErrorActionPreference='Stop'
$executable=(Resolve-Path -LiteralPath $ExePath).Path
if (Get-Process | Where-Object {-not $_.HasExited -and $_.Path -eq $executable}) {
  throw 'Close the existing application before running this smoke check'
}
$started=Get-Date
$results=@()
for($cycle=1;$cycle -le $Cycles;$cycle++) {
  $app=Start-Process -FilePath $executable -WorkingDirectory (Split-Path $executable) -WindowStyle Hidden -PassThru
  try {
    $deadline=(Get-Date).AddSeconds(15)
    do {
      Start-Sleep -Milliseconds 200
      $app.Refresh()
    } while(-not $app.HasExited -and $app.MainWindowHandle -eq 0 -and (Get-Date) -lt $deadline)
    if($app.HasExited -or $app.MainWindowHandle -eq 0){throw 'Application did not open its window'}
    Start-Sleep -Milliseconds 800
    if($BeforeCloseScript){
      & (Resolve-Path -LiteralPath $BeforeCloseScript).Path -ProcessId $app.Id
    }
    if(-not $app.CloseMainWindow()){throw 'Application did not accept window close'}
    if(-not $app.WaitForExit(8000)){throw 'Application remained alive after closing its window'}
    if($app.ExitCode -ne 0){throw "Application exited with code $($app.ExitCode)"}
    $results += [pscustomobject]@{cycle=$cycle;exitCode=$app.ExitCode;exited=$true}
  } finally {
    if(-not $app.HasExited){$app.Kill();$app.WaitForExit()}
    $app.Dispose()
  }
}
$popups=@(Get-WinEvent -FilterHashtable @{LogName='System';Id=26;StartTime=$started} -ErrorAction SilentlyContinue |
  Where-Object {$_.Message -like '*c001apk.exe*'})
if($popups.Count -gt 0){throw 'Windows recorded an application system-error popup'}
$report=[pscustomobject]@{executable=$executable;started=$started.ToString('o');cycles=$results;systemErrorPopups=$popups.Count}
$report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $ReportPath -Encoding UTF8
$report | ConvertTo-Json -Depth 5 -Compress
