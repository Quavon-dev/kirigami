#!/usr/bin/env bash
set -euo pipefail

usage="usage: farm.sh <run-name> <vanilla|paper> [default|vanillalike]"
name="${1:?$usage}"
kind="${2:?$usage}"
preset="${3:-default}"
ticks="${TICKS:-24000}"
pack="${FARM:-mtfarm}"
area="${FORCELOAD:--64 -48 56 48}"

root="$(cd "$(dirname "$0")/.." && pwd)"
results="$root/results/farms"
mkdir -p "$results"
export HEAP="${HEAP:-8G}"
export JVM_ARGS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+AlwaysPreTouch ${EXTRA_ARGS:-}"

case "$kind" in
  vanilla)
    dir="$root/servers/vanilla-ref"
    jar="$dir/fabric-server.jar"
    ;;
  paper)
    dir="$root/servers/runs/farm-$name"
    jar="${JAR:-$root/../kirigami-server/build/libs/kirigami-paperclip-26.3.local-SNAPSHOT.jar}"
    [ -e "$dir" ] && { echo "run directory $dir already exists" >&2; exit 1; }
    mkdir -p "$dir"
    ;;
  *)
    echo "$usage" >&2
    exit 1
    ;;
esac

world="farm-$name"
[ -e "$dir/$world" ] && { echo "world $dir/$world already exists" >&2; exit 1; }
cat > "$dir/server.properties" <<EOF
level-name=$world
level-seed=mtfarm
level-type=minecraft\:flat
generator-settings={"biome"\:"minecraft\:plains","layers"\:[{"block"\:"minecraft\:air","height"\:1}],"lakes"\:false,"features"\:false,"structure_overrides"\:[]}
difficulty=hard
spawn-protection=0
view-distance=10
simulation-distance=10
max-players=50
online-mode=false
pause-when-empty-seconds=-1
server-port=${PORT:-25565}
EOF
mkdir -p "$dir/$world/datapacks"
if [ "$kind" = paper ] && [ -n "${PLUGIN:-}" ]; then
  mkdir -p "$dir/plugins"
  cp $PLUGIN "$dir/plugins/"
fi
cp -R "$root/farms/$pack" "$dir/$world/datapacks/$pack"

if [ "$kind" = paper ] && [ "$preset" = vanillalike ]; then
  "$root/scripts/apply-preset.sh" "$dir"
fi

srv() {
  "$root/scripts/server.sh" "$@"
}

srv start "$dir" "$jar" > /dev/null
trap 'srv stop "$dir"' EXIT
srv cmd "$dir" "forceload add $area"
sleep 10
srv cmd "$dir" "function $pack:build"
sleep 3
if [ "$kind" = vanilla ]; then
  srv cmd "$dir" "player afk spawn at 0.5 100 0.5 facing 0 0 in minecraft:overworld in creative"
else
  srv cmd "$dir" "mtbench bots at 0.5 100 0.5"
fi
sleep 10
srv cmd "$dir" "forceload remove $area"
srv cmd "$dir" "function $pack:start"
sleep 1
start_line="$(wc -l < "$dir/console.log" | tr -d ' ')"
srv cmd "$dir" "tick sprint $ticks"
for _ in $(seq 1 1800); do
  tail -n +"$start_line" "$dir/console.log" | grep -qiE "sprint (completed|finished|ended)" && break
  sleep 1
done
tail -n +"$start_line" "$dir/console.log" | grep -iE "sprint" | tail -1

[ "$kind" = paper ] && [ -n "${PLUGIN:-}" ] && srv cmd "$dir" "mtprobe"
report_line="$(wc -l < "$dir/console.log" | tr -d ' ')"
if [ "$pack" != mtfarm ]; then
  for key in $(cat "$root/farms/$pack/keys.txt"); do
    srv cmd "$dir" "scoreboard players get #$key mt_count"
  done
  sleep 3
  srv stop "$dir"
  trap - EXIT
  values="$(tail -n +"$report_line" "$dir/console.log" | grep -oE "#[a-z]+ has -?[0-9]+" | sed -E 's/#([a-z]+) has (-?[0-9]+)/\1=\2/' | tr '\n' '\t')"
  printf "%s\t%s\t%s\tticks=%s\t%s\n" "$name" "$kind" "$preset" "$ticks" "$values" | tee -a "$results/$pack.tsv"
  exit 0
fi
for x in $(seq -1 1); do
  for z in $(seq -9 9); do
    srv cmd "$dir" "data get block $x 105 $z Items"
  done
done
srv cmd "$dir" "execute if entity @e[type=minecraft:cow,x=19,y=99,z=-1,dx=3,dy=6,dz=3]"
srv cmd "$dir" "execute if entity @e[type=!minecraft:player,x=-9,y=136,z=-9,dx=19,dy=3,dz=19]"
srv cmd "$dir" "execute if entity @e[type=minecraft:villager,x=37,y=110,z=-3,dx=7,dy=6,dz=7]"
for key in wheat wheatage cane ft pcreeper pskeleton pzombie pspider; do
  srv cmd "$dir" "scoreboard players get #$key mt_count"
done
srv cmd "$dir" "scoreboard players get #piglins mt_count"
srv cmd "$dir" "scoreboard players get #golems mt_count"
srv cmd "$dir" "scoreboard players get #platform mt_count"
sleep 5
srv stop "$dir"
trap - EXIT

tail -n +"$report_line" "$dir/console.log" > "$results/$name.log"
python3 -I "$root/scripts/farm_report.py" "$results/$name.log" "$name" "$kind" "$preset" "$ticks" | tee -a "$results/summary.tsv"
