# Windows: isto kao tools/review-stanje.sh (pokreće ga kroz Git Bash, koji Claude Code ionako koristi).
& bash (Join-Path $PSScriptRoot 'review-stanje.sh') @args
exit $LASTEXITCODE
