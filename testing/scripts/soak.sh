#!/usr/bin/env bash
set -euo pipefail

usage="usage: soak.sh <run-name> <jar> <hours>"
name="${1:?$usage}"
jar="${2:?$usage}"
hours="${3:?$usage}"

root="$(cd "$(dirname "$0")/.." && pwd)"
run="$root/servers/runs/$name"
log="$root/results/$name-soak.tsv"
bots=48
dimensions=(minecraft:overworld minecraft:the_nether minecraft:the_end)
export HEAP="${HEAP:-10G}"
export JVM_ARGS="-XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+ParallelRefProcEnabled -XX:+PerfDisableSharedMem -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1HeapRegionSize=8M -XX:G1HeapWastePercent=5 -XX:G1MaxNewSizePercent=40 -XX:G1MixedGCCountTarget=4 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1NewSizePercent=30 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=15 -XX:MaxGCPauseMillis=200 -XX:MaxTenuringThreshold=1 -XX:SurvivorRatio=32 ${EXTRA_ARGS:-}"

[ -e "$run" ] && { echo "run directory $run already exists" >&2; exit 1; }
rsync -a --exclude stdin.fifo --exclude console.log --exclude logs --exclude mtbench --exclude server.pid --exclude fifo.pid --exclude crash-reports --exclude 'world/session.lock' "$root/servers/template/" "$run/"
"$root/scripts/apply-preset.sh" "$run"
if [ -n "${PLUGIN:-}" ]; then
  mkdir -p "$run/plugins"
  cp $PLUGIN "$run/plugins/"
fi

srv() {
  "$root/scripts/server.sh" "$@"
}

errors() {
  grep -E "ERROR|Exception|stopped responding" "$run/console.log" | grep -vc "unsupported server version" || true
}

srv start "$run" "$jar"
trap 'srv stop "$run"' EXIT
srv cmd "$run" "mtbench bots add $bots circle 192"
for setup in villages hotspot; do
  while IFS= read -r line; do
    srv cmd "$run" "$line"
  done < "$root/farms/bench/$setup.txt"
done
sleep 60
for i in $(seq 0 7); do
  srv cmd "$run" "execute in minecraft:the_nether run minecraft:tp bench$i $((i * 160)) 130 0"
done
for i in $(seq 8 11); do
  srv cmd "$run" "execute in minecraft:the_end run minecraft:tp bench$i $(((i - 8) * 64)) 80 0"
done
srv cmd "$run" "mtbench record start $name"

pid="$(cat "$run/server.pid")"
end=$(( $(date +%s) + ${MINUTES:-$((hours * 60))} * 60 ))
minute=0
echo "time minute heap_used_mb errors regionstats" > "$log"
while [ "$(date +%s)" -lt "$end" ]; do
  sleep 60
  minute=$((minute + 1))
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "$(date +%H:%M:%S) $minute server-died" >> "$log"
    exit 1
  fi
  if [ $((minute % 5)) -eq 0 ]; then
    for _ in 1 2 3 4; do
      bot=$((RANDOM % bots))
      dim="${dimensions[$((RANDOM % 3))]}"
      srv cmd "$run" "execute in $dim run minecraft:tp bench$bot $(((RANDOM % 1024) - 512)) 130 $(((RANDOM % 1024) - 512))"
    done
  fi
  srv cmd "$run" "mtbench regionstats"
  heap="$(/opt/homebrew/opt/openjdk@25/bin/jcmd "$pid" GC.heap_info 2>/dev/null | awk '/garbage-first heap/ {for (i = 1; i <= NF; i++) if ($i == "used") {print int($(i + 1) / 1024); exit}}')"
  stats="$(grep -oE "mtbench regionstats .*" "$run/console.log" | tail -1 | cut -c1-200)"
  echo "$(date +%H:%M:%S) $minute ${heap:-?} $(errors) $stats" >> "$log"
done

for d in "${dimensions[@]}"; do
  srv cmd "$run" "execute in $d if entity @a[distance=0..]"
done
srv cmd "$run" "mtbench record stop"
[ -n "${PLUGIN:-}" ] && srv cmd "$run" "mtprobe"
sleep 10
srv stop "$run"
trap - EXIT
cp "$run/mtbench/$name.csv" "$root/results/$name.csv"
echo "$(date +%H:%M:%S) done errors=$(errors)" >> "$log"
