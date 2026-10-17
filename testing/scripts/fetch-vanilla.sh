#!/usr/bin/env bash
set -euo pipefail

usage="usage: fetch-vanilla.sh <minecraft-version> <output-jar>"
mc="${1:?$usage}"
out="${2:?$usage}"

url="$(curl -sSf https://piston-meta.mojang.com/mc/game/version_manifest_v2.json | jq -r --arg mc "$mc" '.versions[] | select(.id == $mc) | .url')"
if [ -z "$url" ]; then
  echo "Minecraft $mc is not in Mojang's version manifest" >&2
  exit 1
fi
read -r server_url server_sha1 < <(curl -sSf "$url" | jq -r '.downloads.server | .url + " " + .sha1')
curl -sSfL -o "$out" "$server_url"
echo "$server_sha1  $out" | shasum -a 1 -c -
