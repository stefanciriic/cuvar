# Provera pre commita na Windowsu: testovi kao u CI-u, pa debug build. Ispisuje samo greške.
# Upotreba: powershell ./tools/check.ps1 [-NoBuild]
param([switch]$NoBuild)
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$out = Join-Path $env:TEMP 'cuvar-check'
$log = & powershell -NoProfile -File (Join-Path $root 'tests/run.ps1') -OutputDirectory $out 2>&1
if ($LASTEXITCODE -ne 0) { $log | Select-Object -Last 20; Write-Output 'NIJE PROŠLO: testovi'; exit 1 }
$missing = ($log | Select-String 'Bez engleskog').Count
Write-Output "Testovi: OK (tekstova bez engleskog prevoda: $missing)"
if ($NoBuild) { exit 0 }
$glog = Join-Path $out 'gradle.log'
& ./gradlew.bat -q --console=plain assembleDebug *> $glog
if ($LASTEXITCODE -ne 0) {
    Select-String -Path $glog -Pattern 'error:|What went wrong|FAILURE|Lint found' -Context 0,3 | Select-Object -First 15
    Write-Output "NIJE PROŠLO: Gradle build (ceo log: $glog)"; exit 1
}
# Oznaka za hook pred push: stablo radnog direktorijuma koje je prošlo proveru (pravi index se ne dira).
$idx = [System.IO.Path]::GetTempFileName()
Copy-Item (git rev-parse --git-path index) $idx -Force
$env:GIT_INDEX_FILE = $idx
git add -A . 2>$null | Out-Null
git write-tree | Set-Content -NoNewline (git rev-parse --git-path cuvar-check-ok)
Remove-Item Env:GIT_INDEX_FILE
Remove-Item $idx -Force
Write-Output 'Build: OK (app/build/outputs/apk/debug/)'
