# Windows: isto kao tools/review-novi.sh (pokreće ga kroz Git Bash, koji Claude Code ionako koristi).
& bash (Join-Path $PSScriptRoot 'review-novi.sh') @args
exit $LASTEXITCODE
