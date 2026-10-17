#!/usr/bin/env bash
set -euo pipefail

usage="usage: compat.sh <run-name> <jar> <paper-server-dir> [seconds]"
name="${1:?$usage}"
jar="${2:?$usage}"
source_dir="${3:?$usage}"
seconds="${4:-300}"

root="$(cd "$(dirname "$0")/.." && pwd)"
export HEAP="${HEAP:-8G}"
export JVM_ARGS="${JVM_ARGS:--XX:+UseG1GC} ${EXTRA_ARGS:-}"
results="$root/results/compat"
probe="$root/plugins/mtprobe/mtprobe.jar"
error_pattern="Error occurred while enabling|Could not load '|Exception|ERROR"

[ -f "$source_dir/server.properties" ] || { echo "$source_dir is not a server directory" >&2; exit 1; }
[ -f "$probe" ] || { echo "missing $probe, build testing/plugins/mtprobe first" >&2; exit 1; }

srv() {
  "$root/scripts/server.sh" "$@"
}

run_server() {
  local run="$1" server_jar="$2"
  [ -e "$run" ] && { echo "run directory $run already exists" >&2; exit 1; }
  mkdir -p "$run/plugins"
  rsync -a --exclude logs --exclude crash-reports --exclude 'session.lock' --exclude '*.jar' --exclude cache --exclude libraries --exclude versions "$source_dir/" "$run/"
  find "$source_dir/plugins" -maxdepth 1 -name '*.jar' -exec cp {} "$run/plugins/" \; 2>/dev/null || true
  cp "$probe" "$run/plugins/"
  if [ -n "${PORT:-}" ]; then
    grep -v "^server-port=" "$run/server.properties" > "$run/server.properties.tmp" || true
    echo "server-port=$PORT" >> "$run/server.properties.tmp"
    mv "$run/server.properties.tmp" "$run/server.properties"
  fi
  srv start "$run" "$server_jar" > /dev/null
  trap 'srv stop "$run"' EXIT
  srv cmd "$run" "mtbench bots add ${BOTS:-32} circle 160"
  sleep "$seconds"
  srv cmd "$run" "plugins"
  srv cmd "$run" "mtprobe"
  srv cmd "$run" "kirigami"
  sleep 3
  srv stop "$run"
  trap - EXIT
}

errors() {
  grep -E "$error_pattern" "$1" | sed -E 's/^\[[0-9:]+ //; s/[0-9]+/N/g' | sort -u || true
}

mkdir -p "$results"
run="$root/servers/runs/compat-$name"
run_server "$run" "$jar"
installed="$(find "$run/plugins" -maxdepth 1 -name '*.jar' | wc -l | tr -d ' ')"
log="$run/console.log"
new_errors="$(errors "$log")"
if [ -n "${PAPER_JAR:-}" ]; then
  run_server "$run-paper" "$PAPER_JAR"
  new_errors="$(comm -23 <(echo "$new_errors") <(errors "$run-paper/console.log"))"
fi

probe_line="$(grep -o "mtprobe overlaps=.*" "$log" | tail -1)"
enabled="$(grep -oE "Server Plugins \([0-9]+\)" "$log" | grep -oE "[0-9]+" | tail -1)"
overlaps="$(echo "$probe_line" | sed -E 's/.*overlaps=([0-9]+).*/\1/')"
off_thread="$(echo "$probe_line" | sed -E 's/.*offThread=([0-9]+).*/\1/')"
scheduled="$(echo "$probe_line" | sed -E 's/.*scheduled=([0-9]+).*/\1/')"
ran="$(echo "$probe_line" | sed -E 's/.* ran=([0-9]+).*/\1/')"
error_count="$(echo -n "$new_errors" | grep -c . || true)"

status=pass
[ "${enabled:-0}" = "$installed" ] || status=fail
[ "${overlaps:-1}" = 0 ] && [ "${off_thread:-1}" = 0 ] || status=fail
[ "$((scheduled - ran))" -le 1 ] || status=fail
[ "$error_count" = 0 ] || status=fail
[ -n "$new_errors" ] && echo "$new_errors" >&2
printf "%s\t%s\tplugins=%s/%s new-errors=%s %s\n" "$name" "$status" "${enabled:-0}" "$installed" "$error_count" "$probe_line" | tee -a "$results/summary.tsv"
[ "$status" = pass ]
