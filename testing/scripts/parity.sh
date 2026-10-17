#!/usr/bin/env bash
set -euo pipefail

usage="usage: parity.sh <vanilla-server-jar> <kirigami-jar>"
vanilla="$(cd "$(dirname "${1:?$usage}")" && pwd)/$(basename "$1")"
kirigami="$(cd "$(dirname "${2:?$usage}")" && pwd)/$(basename "$2")"

root="$(cd "$(dirname "$0")/.." && pwd)"
name="parity-$(date +%s)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

VANILLA_DIR="$root/servers/runs/$name-vanilla" VANILLA_JAR="$vanilla" PORT=25590 \
  DET_SUMMARY="$work/summary.tsv" "$root/scripts/det.sh" "$name-vanilla" vanilla > "$work/vanilla.out" 2>&1 &
vanilla_pid=$!
JAR="$kirigami" PORT=25591 EXTRA_ARGS="-Dkirigami.minParallelItems=0 -Dkirigami.minSpeculativeJobs=0" \
  DET_SUMMARY="$work/summary.tsv" "$root/scripts/det.sh" "$name-kirigami" paper > "$work/kirigami.out" 2>&1 &
kirigami_pid=$!
status=0
wait "$vanilla_pid" || status=1
wait "$kirigami_pid" || status=1
if [ "$status" -ne 0 ]; then
  echo "A deterministic run failed:" >&2
  tail -20 "$work/vanilla.out" "$work/kirigami.out" >&2
  exit 1
fi

counters() {
  tail -1 "$1" | cut -f3 | tr ' ' '\n' | grep '=' | grep -v '^t=' | sort
}
counters "$work/vanilla.out" > "$work/vanilla.txt"
counters "$work/kirigami.out" > "$work/kirigami.txt"

expected="$(tr ' ' '\n' < "$root/farms/mtdet/keys.txt" | grep -cvx 't')"
for side in vanilla kirigami; do
  found="$(wc -l < "$work/$side.txt" | tr -d ' ')"
  if [ "$found" -ne "$expected" ]; then
    echo "$side reported $found of $expected counters, so part of the suite did not run" >&2
    exit 1
  fi
done

if ! diff "$work/vanilla.txt" "$work/kirigami.txt" > "$work/diff.txt"; then
  echo "Kirigami differs from vanilla:" >&2
  cat "$work/diff.txt" >&2
  exit 1
fi
echo "Parity OK: all $expected counters identical to vanilla"
