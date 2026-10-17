#!/usr/bin/env bash
set -euo pipefail

usage="usage: det.sh <run-name> <vanilla|paper>"
name="${1:?$usage}"
kind="${2:?$usage}"

root="$(cd "$(dirname "$0")/.." && pwd)"
results="$root/results/det"
mkdir -p "$results"
export HEAP="${HEAP:-4G}"
export JVM_ARGS="-XX:+UseG1GC ${EXTRA_ARGS:-}"

case "$kind" in
  vanilla)
    dir="$root/servers/vanilla-ref"
    jar="$dir/fabric-server.jar"
    ;;
  paper)
    dir="$root/servers/runs/det-$name"
    jar="${JAR:-$root/../kirigami-server/build/libs/kirigami-paperclip-26.3.local-SNAPSHOT.jar}"
    [ -e "$dir" ] && { echo "run directory $dir already exists" >&2; exit 1; }
    mkdir -p "$dir"
    ;;
  *)
    echo "$usage" >&2
    exit 1
    ;;
esac

world="det-$name"
[ -e "$dir/$world" ] && { echo "world $dir/$world already exists" >&2; exit 1; }
cat > "$dir/server.properties" <<EOF
level-name=$world
level-seed=mtdet
level-type=minecraft\:flat
generator-settings={"biome"\:"minecraft\:plains","layers"\:[{"block"\:"minecraft\:air","height"\:1}],"lakes"\:false,"features"\:false,"structure_overrides"\:[]}
difficulty=peaceful
spawn-protection=0
online-mode=false
pause-when-empty-seconds=-1
server-port=${PORT:-25590}
EOF
mkdir -p "$dir/$world/datapacks"
if [ "$kind" = paper ] && [ -n "${PLUGIN:-}" ]; then
  mkdir -p "$dir/plugins"
  cp $PLUGIN "$dir/plugins/"
fi
cp -R "$root/farms/mtdet" "$dir/$world/datapacks/mtdet"

srv() {
  "$root/scripts/server.sh" "$@"
}

srv start "$dir" "$jar" > /dev/null
trap 'srv stop "$dir"' EXIT
srv cmd "$dir" "forceload add -32 16 64 64"
sleep 8
srv cmd "$dir" "function mtdet:build"
sleep 2
srv cmd "$dir" "scoreboard players set #t mt_det 0"
srv cmd "$dir" "scoreboard players set #go mt_det 1"
start_line="$(wc -l < "$dir/console.log" | tr -d ' ')"
srv cmd "$dir" "tick sprint 6100"
for _ in $(seq 1 600); do
  tail -n +"$start_line" "$dir/console.log" | grep -qiE "sprint (completed|finished|ended)" && break
  sleep 1
done
report_line="$(wc -l < "$dir/console.log" | tr -d ' ')"
[ "$kind" = paper ] && [ -n "${PLUGIN:-}" ] && srv cmd "$dir" "mtprobe"
for key in $(cat "$root/farms/mtdet/keys.txt"); do
  srv cmd "$dir" "scoreboard players get #$key mt_det"
done
sleep 3
srv stop "$dir"
trap - EXIT

values="$(tail -n +"$report_line" "$dir/console.log" | grep -oE "#[a-z]+ has -?[0-9]+" | sed -E 's/#([a-z]+) has (-?[0-9]+)/\1=\2/' | tr '\n' ' ')"
printf "%s\t%s\t%s\n" "$name" "$kind" "$values" | tee -a "$results/summary.tsv"
