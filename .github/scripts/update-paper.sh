#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."
output="${GITHUB_OUTPUT:-/dev/null}"
report() {
  echo "$1=$2" >> "$output"
  echo "$1=$2"
}

current="$(grep '^paperRef=' gradle.properties | cut -d= -f2 | tr -d '[:space:]')"
mc="$(grep '^mcVersion=' gradle.properties | cut -d= -f2 | tr -d '[:space:]')"
latest="${1:-$(gh api repos/PaperMC/Paper/commits/main --jq .sha)}"
paper_mc="$(gh api "repos/PaperMC/Paper/contents/gradle.properties?ref=$latest" --jq .content | base64 -d | grep '^mcVersion=' | cut -d= -f2 | tr -d '[:space:]')"
report current "$current"
report latest "$latest"
report paper_mc "$paper_mc"

if [ "$latest" = "$current" ]; then
  report status up-to-date
  exit 0
fi
if [ "$paper_mc" != "$mc" ]; then
  report status new-minecraft
  exit 0
fi

sed -i.bak "s/^paperRef=.*/paperRef=$latest/" gradle.properties && rm gradle.properties.bak
if ! ./gradlew applyAllPatches --stacktrace > update-apply.log 2>&1; then
  report status apply-failed
  exit 1
fi
./gradlew rebuildAllServerPatches > update-rebuild.log 2>&1
if ! ./gradlew :kirigami-server:createPaperclipJar --stacktrace > update-build.log 2>&1; then
  report status build-failed
  exit 1
fi
report status updated
