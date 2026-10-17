#!/usr/bin/env bash
set -euo pipefail
dir="${1:?server dir}"
mkdir -p "$dir/config"
cat > "$dir/spigot.yml" <<'YAML'
world-settings:
  default:
    entity-activation-range:
      animals: 0
      monsters: 0
      raiders: 0
      misc: 0
      water: 0
      villagers: 0
      flying-monsters: 0
    max-tnt-per-tick: 1000000
YAML
cat > "$dir/config/paper-world-defaults.yml" <<'YAML'
collisions:
  max-entity-collisions: 2147483647
entities:
  spawning:
    per-player-mob-spawns: false
    count-all-mobs-for-spawning: true
YAML
