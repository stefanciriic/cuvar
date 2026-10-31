#!/usr/bin/env bash
# Provera pre commita: testovi kao u CI-u, pa debug build. Ispisuje samo greške i kratak rezultat.
# Upotreba: tools/check.sh [--no-build]
set -uo pipefail
mark_ok() {
  # Oznaka za hook pred push: stablo radnog direktorijuma koje je prošlo proveru (pravi index se ne dira).
  idx=$(mktemp); cp "$(git rev-parse --git-path index)" "$idx" 2>/dev/null
  GIT_INDEX_FILE="$idx" git add -A . >/dev/null 2>&1
  GIT_INDEX_FILE="$idx" git write-tree > "$(git rev-parse --git-path cuvar-check-ok)" 2>/dev/null
  rm -f "$idx"
}
cd "$(dirname "$0")/.."
out="${TMPDIR:-/tmp}/cuvar-check"
rm -rf "$out" && mkdir -p "$out"

if ! javac -nowarn --release 11 -encoding UTF-8 -d "$out" $(grep -v '^#' tests/sources.txt) tests/com/cuvar/app/*Test.java 2>"$out/javac.log"; then
  grep -E 'error|\^' "$out/javac.log" | head -30
  echo "NIJE PROŠLO: kompilacija testova"; exit 1
fi
for t in tests/com/cuvar/app/*Test.java; do
  n=$(basename "$t" .java)
  if ! java -Dfile.encoding=UTF-8 -cp "$out" "com.cuvar.app.$n" >"$out/$n.log" 2>&1; then
    tail -15 "$out/$n.log"; echo "NIJE PROŠLO: $n"; exit 1
  fi
done
missing=$(grep -c 'Bez engleskog' "$out/LangTest.log" || true)
echo "Testovi: OK (tekstova bez engleskog prevoda: $missing)"

[ "${1:-}" = "--no-build" ] && exit 0
if [ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ] && [ ! -f local.properties ]; then
  echo "Build preskočen: nema Android SDK-a (ANDROID_HOME). CI će ga proveriti posle push-a."
  mark_ok; exit 0
fi
if ! ./gradlew -q --console=plain assembleDebug >"$out/gradle.log" 2>&1; then
  grep -E 'error:|e: |What went wrong|FAILURE|Lint found' -A3 "$out/gradle.log" | head -40
  echo "NIJE PROŠLO: Gradle build (ceo log: $out/gradle.log)"; exit 1
fi
mark_ok
echo "Build: OK (app/build/outputs/apk/debug/)"
