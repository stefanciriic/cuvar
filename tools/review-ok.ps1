# Windows: isto kao tools/review-ok.sh (pokreće ga kroz Git Bash, koji Claude Code ionako koristi).
& bash (Join-Path $PSScriptRoot 'review-ok.sh') @args
exit $LASTEXITCODE
