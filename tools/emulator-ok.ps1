# Windows: isto kao tools/emulator-ok.sh (pokreće ga kroz Git Bash, koji Claude Code ionako koristi).
& bash (Join-Path $PSScriptRoot 'emulator-ok.sh') @args
exit $LASTEXITCODE
