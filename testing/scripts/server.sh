#!/usr/bin/env bash
set -euo pipefail

JAVA="${JAVA:-/opt/homebrew/opt/openjdk@25/bin/java}"
HEAP="${HEAP:-4G}"
JVM_ARGS="${JVM_ARGS:-}"
action="${1:?usage: server.sh start|cmd|stop|alive|log <server-dir> [jar|command]}"
dir="${2:?server dir required}"
cd "$dir"

alive() {
  [ -f server.pid ] && kill -0 "$(cat server.pid)" 2>/dev/null
}

case "$action" in
  start)
    jar="${3:?jar path required}"
    if alive; then
      echo "server already running in $dir" >&2
      exit 1
    fi
    rm -f stdin.fifo
    mkfifo -m 600 stdin.fifo
    echo "eula=true" > eula.txt
    : > console.log
    "$JAVA" -Xms"$HEAP" -Xmx"$HEAP" $JVM_ARGS -Dkirigami.benchTools=true -jar "$jar" --nogui < stdin.fifo > console.log 2>&1 &
    echo $! > server.pid
    exec 3> stdin.fifo
    tail -f /dev/null >&3 &
    echo $! > fifo.pid
    exec 3>&-
    for _ in $(seq 1 120); do
      grep -q "Done (" console.log && { grep "Done (" console.log; exit 0; }
      alive || { echo "server exited during startup" >&2; tail -20 console.log >&2; exit 1; }
      sleep 1
    done
    echo "server did not start in time" >&2
    tail -20 console.log >&2
    exit 1
    ;;
  cmd)
    alive || { echo "server in $dir is not running" >&2; exit 1; }
    echo "${3:?command required}" > stdin.fifo
    ;;
  stop)
    if alive; then
      echo stop > stdin.fifo
      for _ in $(seq 1 120); do
        alive || break
        sleep 1
      done
      alive && { echo "server did not stop, killing" >&2; kill "$(cat server.pid)"; }
    fi
    [ -f fifo.pid ] && kill "$(cat fifo.pid)" 2>/dev/null || true
    rm -f server.pid fifo.pid
    ;;
  alive)
    alive
    ;;
  log)
    tail -n "${3:-20}" console.log
    ;;
esac
