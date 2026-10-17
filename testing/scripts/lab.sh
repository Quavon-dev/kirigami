#!/usr/bin/env bash
set -euo pipefail

usage="usage: lab.sh <run-name> <jar>"
name="${1:?$usage}"
jar="${2:?$usage}"

root="$(cd "$(dirname "$0")/.." && pwd)"
export HEAP="${HEAP:-8G}"
EXTRA_ARGS="${EXTRA_ARGS:-}"
export JVM_ARGS="-XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+ParallelRefProcEnabled -XX:+PerfDisableSharedMem -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1HeapRegionSize=8M -XX:G1HeapWastePercent=5 -XX:G1MaxNewSizePercent=40 -XX:G1MixedGCCountTarget=4 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1NewSizePercent=30 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=15 -XX:MaxGCPauseMillis=200 -XX:MaxTenuringThreshold=1 -XX:SurvivorRatio=32 $EXTRA_ARGS"
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

srv start "$run" "$jar"
trap 'srv stop "$run"' EXIT
srv cmd "$run" "mtbench bots add 64 circle 192"
srv cmd "$run" "mtbench lab observers"
sleep 25
srv cmd "$run" "mtbench lab build"
sleep 5
srv cmd "$run" "mtbench lab start"
srv cmd "$run" "mtbench record start $name"
elapsed=0
for checkpoint in 10 30 60 120; do
  sleep $((checkpoint - elapsed))
  elapsed=$checkpoint
  srv cmd "$run" "mtbench lab report"
done
srv cmd "$run" "mtbench record stop"
srv cmd "$run" "mtbench regionstats"
sleep 3
srv stop "$run"
trap - EXIT

cp "$run/mtbench/$name.csv" "$results/$name.csv"
echo "$name load: $(sysctl -n vm.loadavg) busy-before: ${busy:-none}" >> "$results/load.log"
grep -o "mtbench lab lab .*" "$run/console.log" > "$results/$name.lab.txt" || true
grep -o "mtbench \(ticks\|regionstats\).*" "$run/console.log" >> "$results/$name.lab.txt" || true
echo "errors=$(grep -cE 'ERROR|Exception' "$run/console.log" || true)" >> "$results/$name.lab.txt"
cat "$results/$name.lab.txt"
