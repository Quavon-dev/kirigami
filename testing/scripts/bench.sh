#!/usr/bin/env bash
set -euo pipefail

usage="usage: bench.sh <run-name> <jar> <bots> <idle|circle|line> <spacing> <warmup-seconds> <record-seconds>"
name="${1:?$usage}"
jar="${2:?$usage}"
bots="${3:?$usage}"
mode="${4:?$usage}"
spacing="${5:?$usage}"
warmup="${6:?$usage}"
record="${7:?$usage}"

root="$(cd "$(dirname "$0")/.." && pwd)"
export HEAP="${HEAP:-8G}"
EXTRA_ARGS="${EXTRA_ARGS:-}"
export JVM_ARGS="${JVM_ARGS:--XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+ParallelRefProcEnabled -XX:+PerfDisableSharedMem -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1HeapRegionSize=8M -XX:G1HeapWastePercent=5 -XX:G1MaxNewSizePercent=40 -XX:G1MixedGCCountTarget=4 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1NewSizePercent=30 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=15 -XX:MaxGCPauseMillis=200 -XX:MaxTenuringThreshold=1 -XX:SurvivorRatio=32} $EXTRA_ARGS"
template="$root/servers/template"
run="$root/servers/runs/$name"
results="$root/results"

[ -d "$template/world" ] || { echo "missing template world in $template" >&2; exit 1; }
[ -e "$run" ] && { echo "run directory $run already exists" >&2; exit 1; }

srv() {
  local rc
  for _ in 1 2 3; do
    "$root/scripts/server.sh" "$@" && return 0
    rc=$?
    [ "$rc" -eq 126 ] || return "$rc"
    sleep 1
  done
  return 126
}

mkdir -p "$run" "$results"
busy="$(ps -Ao pcpu,comm | awk 'NR>1 && $1>25 && $2 !~ /java/ {printf "%s(%s%%) ", $2, $1}')"
if [ -n "$busy" ]; then
  echo "WARNING: machine not idle before $name: $busy" >&2
fi
rsync -a --exclude stdin.fifo --exclude console.log --exclude logs --exclude mtbench --exclude server.pid --exclude fifo.pid --exclude crash-reports --exclude 'world/session.lock' "$template/" "$run/"

if [ -n "${PORT:-}" ]; then
  grep -v "^server-port=" "$run/server.properties" > "$run/server.properties.tmp" || true
  echo "server-port=$PORT" >> "$run/server.properties.tmp"
  mv "$run/server.properties.tmp" "$run/server.properties"
fi
if [ -n "${PLUGIN:-}" ]; then
  mkdir -p "$run/plugins"
  cp $PLUGIN "$run/plugins/"
fi
if [ "${PRESET:-default}" = vanillalike ]; then
  "$root/scripts/apply-preset.sh" "$run"
fi
srv start "$run" "$jar"
trap 'srv stop "$run"' EXIT
srv cmd "$run" "mtbench bots add $bots $mode $spacing"
if [ -n "${SETUP:-}" ]; then
  while IFS= read -r line; do
    srv cmd "$run" "$line"
  done < "$SETUP"
fi
sleep "$warmup"
srv cmd "$run" "mtbench record start $name"
sleep "$record"
srv cmd "$run" "mtbench record stop"
srv cmd "$run" "mtbench spawnstats"
srv cmd "$run" "mtbench regionstats"
for _ in $(seq 1 30); do
  grep -q "mtbench ticks=" "$run/console.log" && break
  sleep 1
done
srv stop "$run"
trap - EXIT

cp "$run/mtbench/$name.csv" "$results/$name.csv"
echo "$name load: $(sysctl -n vm.loadavg) busy-before: ${busy:-none}" >> "$results/load.log"
summary="$(grep -o "mtbench ticks=.*" "$run/console.log" | tail -1)"
printf "%s\t%s\t%s\n" "$name" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$summary" >> "$results/summary.tsv"
echo "$summary"
