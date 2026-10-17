#!/usr/bin/env bash
set -uo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
prefix="${1:?prefix}"
runs="${2:?runs}"
shift 2
for r in $(seq 1 "$runs"); do
  for cfg in "$@"; do
    case "$cfg" in
      V) TICKS="${TICKS:-120000}" "$root/scripts/farm.sh" "$prefix-V-r$r" vanilla ;;
      F) TICKS="${TICKS:-120000}" "$root/scripts/farm.sh" "$prefix-F-r$r" paper default ;;
      FV) TICKS="${TICKS:-120000}" "$root/scripts/farm.sh" "$prefix-FV-r$r" paper vanillalike ;;
      FP) EXTRA_ARGS="-Dkirigami.minParallelItems=0 -Dkirigami.minSpeculativeJobs=0" TICKS="${TICKS:-120000}" "$root/scripts/farm.sh" "$prefix-FP-r$r" paper default ;;
      FS) EXTRA_ARGS="-Dkirigami.parallelSpawning=false" TICKS="${TICKS:-120000}" "$root/scripts/farm.sh" "$prefix-FS-r$r" paper default ;;
      FE) EXTRA_ARGS="-Dkirigami.minParallelItems=0 -Dkirigami.minSpeculativeJobs=0 -Dkirigami.region.chunks=false -Dkirigami.region.blockEntities=false" TICKS="${TICKS:-120000}" "$root/scripts/farm.sh" "$prefix-FE-r$r" paper default ;;
      FR) EXTRA_ARGS="-Dkirigami.regionTicking=false" TICKS="${TICKS:-120000}" "$root/scripts/farm.sh" "$prefix-FR-r$r" paper default ;;
      FN) EXTRA_ARGS="-Dkirigami.minParallelItems=0 -Dkirigami.parallelSpawning=false" TICKS="${TICKS:-120000}" "$root/scripts/farm.sh" "$prefix-FN-r$r" paper default ;;
    esac > /dev/null 2>&1
    echo "$(date +%H:%M:%S) finished $prefix-$cfg-r$r"
  done
done
echo "$(date +%H:%M:%S) matrix done"
