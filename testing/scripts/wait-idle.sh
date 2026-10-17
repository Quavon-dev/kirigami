#!/usr/bin/env bash
set -euo pipefail

limit="${1:-2.5}"
timeout="${2:-1800}"
waited=0
while true; do
  load="$(sysctl -n vm.loadavg | awk '{print $2}')"
  if awk -v l="$load" -v m="$limit" 'BEGIN{exit !(l < m)}'; then
    echo "load $load below $limit after ${waited}s"
    exit 0
  fi
  if [ "$waited" -ge "$timeout" ]; then
    echo "load still $load after ${waited}s" >&2
    exit 1
  fi
  sleep 15
  waited=$((waited + 15))
done
