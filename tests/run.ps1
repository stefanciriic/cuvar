param([string]$OutputDirectory)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $projectRoot 'build/core-tests' }
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$sources = @(Get-Content (Join-Path $PSScriptRoot 'sources.txt') |
    Where-Object { $_.Trim() -and -not $_.StartsWith('#') } |
    ForEach-Object { Join-Path $projectRoot $_.Trim() })
$tests = @(Get-ChildItem (Join-Path $PSScriptRoot 'com/cuvar/app') -Filter '*Test.java' | Sort-Object Name)
if ($tests.Count -eq 0) { throw 'Nema testova.' }
$sources += @($tests | ForEach-Object { $_.FullName })
& javac --release 11 -encoding UTF-8 -d $OutputDirectory @sources
if ($LASTEXITCODE -ne 0) { throw 'Kompilacija testova nije uspela.' }
foreach ($test in $tests) {
    Write-Output $test.BaseName
    & java '-Dfile.encoding=UTF-8' -cp $OutputDirectory "com.cuvar.app.$($test.BaseName)"
    if ($LASTEXITCODE -ne 0) { throw "Test $($test.BaseName) nije prošao." }
}
Write-Output "Prošlo je svih $($tests.Count) test grupa."
