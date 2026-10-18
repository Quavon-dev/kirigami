#!/usr/bin/env bash
set -euo pipefail
umask 022

REPO="Quavon-dev/kirigami"
IMAGE="ghcr.io/quavon-dev/kirigami"
G1_FLAGS="-XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+ParallelRefProcEnabled -XX:+PerfDisableSharedMem -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1HeapRegionSize=8M -XX:G1HeapWastePercent=5 -XX:G1MaxNewSizePercent=40 -XX:G1MixedGCCountTarget=4 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1NewSizePercent=30 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=15 -XX:MaxGCPauseMillis=200 -XX:MaxTenuringThreshold=1 -XX:SurvivorRatio=32"

die() { echo "error: $*" >&2; exit 1; }
info() { echo "==> $*"; }

[ -r /dev/tty ] || die "no terminal available; run this script interactively"

ask() {
  local prompt="$1" default="${2:-}" reply
  if [ -n "$default" ]; then
    read -r -p "$prompt [$default]: " reply < /dev/tty
  else
    read -r -p "$prompt: " reply < /dev/tty
  fi
  printf '%s\n' "${reply:-$default}"
}

confirm() {
  local reply
  reply="$(ask "$1 (y/n)" "${2:-n}")"
  case "$reply" in y|Y|yes|YES|Yes) return 0 ;; *) return 1 ;; esac
}

choose() {
  local prompt="$1" default="$2" reply i
  shift 2
  for i in $(seq 1 $#); do echo "  $i) ${!i}" > /dev/tty; done
  while :; do
    reply="$(ask "$prompt" "$default")"
    if [[ "$reply" =~ ^[0-9]+$ ]] && [ "$reply" -ge 1 ] && [ "$reply" -le $# ]; then
      echo "${!reply}"
      return
    fi
    echo "Pick a number from 1 to $#." > /dev/tty
  done
}

case "$(uname -s)" in
  Darwin) OS=macos ;;
  Linux) OS=linux ;;
  MINGW*|MSYS*|CYGWIN*) OS=windows ;;
  *) die "unsupported system $(uname -s); on Windows use setup.ps1" ;;
esac
[ "$(id -u)" -eq 0 ] && echo "warning: running as root; a dedicated user is safer for a game server" >&2

total_ram_mb() {
  case "$OS" in
    macos) echo $(( $(sysctl -n hw.memsize) / 1024 / 1024 )) ;;
    linux) awk '/MemTotal/ {print int($2 / 1024)}' /proc/meminfo ;;
    *) echo 8192 ;;
  esac
}

java_major() {
  "$1" -version 2>&1 | awk -F'"' '/version/ {split($2, v, "."); print v[1]; exit}'
}

find_java() {
  local c major
  for c in "${JAVA_HOME:+$JAVA_HOME/bin/java}" "$(command -v java || true)" \
    /opt/homebrew/opt/openjdk@25/bin/java /usr/local/opt/openjdk@25/bin/java \
    /usr/lib/jvm/*/bin/java /Library/Java/JavaVirtualMachines/*/Contents/Home/bin/java; do
    [ -n "$c" ] && [ -x "$c" ] || continue
    major="$(java_major "$c")"
    [[ "$major" =~ ^[0-9]+$ ]] && [ "$major" -ge 25 ] && { echo "$c"; return 0; }
  done
  return 1
}

install_java() {
  local cmd=""
  if [ "$OS" = macos ] && command -v brew > /dev/null; then
    cmd="brew install openjdk@25"
  elif command -v apt-get > /dev/null; then
    cmd="sudo apt-get update && sudo apt-get install -y openjdk-25-jre-headless"
  elif command -v dnf > /dev/null; then
    cmd="sudo dnf install -y java-25-openjdk-headless"
  elif command -v pacman > /dev/null; then
    cmd="sudo pacman -S --needed jre-openjdk-headless"
  elif [ "$OS" = windows ] && command -v winget > /dev/null; then
    cmd="winget install --exact --id EclipseAdoptium.Temurin.25.JDK"
  fi
  if [ -z "$cmd" ]; then
    echo "Install Java 25 from https://adoptium.net and run this script again." >&2
    return 1
  fi
  confirm "Java 25 is missing. Run '$cmd'?" || return 1
  bash -c "$cmd" || { echo "Install failed. Get Java 25 from https://adoptium.net." >&2; return 1; }
}

container_engine() {
  local e
  for e in docker podman; do
    command -v "$e" > /dev/null && "$e" info > /dev/null 2>&1 && { echo "$e"; return 0; }
  done
  return 1
}

download() {
  if command -v curl > /dev/null; then
    curl -fL --proto '=https' --tlsv1.2 --retry 3 -o "$2" "$1"
  elif command -v wget > /dev/null; then
    wget --https-only -O "$2" "$1"
  else
    die "curl or wget is required"
  fi
}

sha256_of() {
  if command -v sha256sum > /dev/null; then
    sha256sum "$1" | awk '{print $1}'
  elif command -v shasum > /dev/null; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    die "sha256sum or shasum is required to verify the download"
  fi
}

fetch_text() {
  if command -v curl > /dev/null; then
    curl -fsSL --proto '=https' --tlsv1.2 --max-time 15 "$1"
  else
    wget --https-only -q -T 15 -O- "$1"
  fi
}

lookup_uuid() {
  local id
  id="$(fetch_text "https://api.mojang.com/users/profiles/minecraft/$1" 2> /dev/null | grep -o '[0-9a-f]\{32\}' | head -n 1)" || return 1
  [ "${#id}" -eq 32 ] || return 1
  echo "${id:0:8}-${id:8:4}-${id:12:4}-${id:16:4}-${id:20:12}"
}

ask_choice() {
  local prompt="$1" default="$2" reply
  shift 2
  while :; do
    reply="$(ask "$prompt ($*)" "$default")"
    for opt in "$@"; do [ "$reply" = "$opt" ] && { echo "$reply"; return; }; done
    echo "Pick one of: $*" > /dev/tty
  done
}

ask_number() {
  local prompt="$1" default="$2" min="$3" max="$4" reply
  while :; do
    reply="$(ask "$prompt ($min-$max)" "$default")"
    [[ "$reply" =~ ^[0-9]+$ ]] && [ "$reply" -ge "$min" ] && [ "$reply" -le "$max" ] && { echo "$reply"; return; }
    echo "Enter a number from $min to $max." > /dev/tty
  done
}

ask_text() {
  local reply
  while :; do
    reply="$(ask "$1" "${2:-}")"
    [[ "$reply" =~ [[:cntrl:]] ]] || { echo "${reply//\\/\\\\}"; return; }
    echo "Control characters are not allowed." > /dev/tty
  done
}

write_server_files() {
  local dir="$1" port="$2" i sep
  if [ -f "$dir/server.properties" ]; then
    info "Keeping existing server.properties"
  else
    {
      echo "server-port=$port"
      echo "motd=$MOTD"
      echo "max-players=$MAX_PLAYERS"
      echo "difficulty=$DIFFICULTY"
      echo "gamemode=$GAMEMODE"
      echo "pvp=$PVP"
      echo "online-mode=$ONLINE_MODE"
      echo "view-distance=$VIEW_DISTANCE"
      echo "simulation-distance=$SIM_DISTANCE"
      echo "level-seed=$SEED"
      echo "white-list=$WHITELIST"
      echo "enforce-whitelist=$WHITELIST"
    } > "$dir/server.properties"
  fi
  [ "${#OP_NAMES[@]}" -gt 0 ] || return 0
  if [ -f "$dir/ops.json" ]; then
    info "Keeping existing ops.json"
  else
    sep=""
    { echo "["; for i in "${!OP_NAMES[@]}"; do
        printf '%s  {"uuid": "%s", "name": "%s", "level": 4, "bypassesPlayerLimit": false}' "$sep" "${OP_UUIDS[$i]}" "${OP_NAMES[$i]}"
        sep=$',\n'
      done; printf '\n]\n'; } > "$dir/ops.json"
  fi
  if [ "$WHITELIST" = true ] && [ ! -f "$dir/whitelist.json" ]; then
    sep=""
    { echo "["; for i in "${!OP_NAMES[@]}"; do
        printf '%s  {"uuid": "%s", "name": "%s"}' "$sep" "${OP_UUIDS[$i]}" "${OP_NAMES[$i]}"
        sep=$',\n'
      done; printf '\n]\n'; } > "$dir/whitelist.json"
  fi
}

as_root() {
  if [ "$(id -u)" -eq 0 ]; then "$@"; else sudo "$@"; fi
}

open_firewall() {
  if command -v ufw > /dev/null; then
    confirm "Allow port $PORT/tcp in ufw?" && as_root ufw allow "$PORT/tcp"
  elif command -v firewall-cmd > /dev/null && firewall-cmd --state > /dev/null 2>&1; then
    confirm "Allow port $PORT/tcp in firewalld?" && as_root firewall-cmd --permanent --add-port="$PORT/tcp" && as_root firewall-cmd --reload
  else
    info "Open port $PORT/tcp in your firewall if players connect from outside."
  fi
  return 0
}

echo "Kirigami server setup"
echo

ENGINE="$(container_engine || true)"
JAVA="$(find_java || true)"
methods=()
[ -n "$ENGINE" ] && methods+=("$ENGINE container")
methods+=("plain jar in a directory${JAVA:+ (found Java: $JAVA)}")
[ -z "$ENGINE" ] && info "No running Docker or Podman found; container setup is not offered."
echo "How do you want to run the server?"
METHOD="$(choose "Method" 1 "${methods[@]}")"

echo
echo "Minecraft requires you to accept its EULA: https://aka.ms/MinecraftEULA"
[ "$(ask "Type 'yes' to accept the EULA")" = yes ] || die "the EULA was not accepted"

echo
echo "Release channel:"
CHANNEL="$(choose "Channel" 1 "stable" "edge (every commit on main, untested)" "exact release tag")"
case "$CHANNEL" in
  stable) TAG=latest; URL="https://github.com/$REPO/releases/latest/download" ;;
  edge*) TAG=edge; URL="https://github.com/$REPO/releases/download/edge" ;;
  *)
    TAG="$(ask "Release tag (for example 26.3-4)")"
    [[ "$TAG" =~ ^[0-9]+(\.[0-9]+)*-[0-9]+$ ]] || die "invalid tag '$TAG'"
    URL="https://github.com/$REPO/releases/download/v$TAG"
    ;;
esac

ram_mb="$(total_ram_mb)"
ram=$(( ram_mb / 2 / 1024 ))
[ "$ram" -lt 2 ] && ram=2
[ "$ram" -gt 16 ] && ram=16
MEMORY="$(ask "Heap size" "${ram}G")"
[[ "$MEMORY" =~ ^[0-9]+[MG]$ ]] || die "heap size must look like 8G or 4096M"
heap_mb=$(( 10#${MEMORY%?} ))
[ "${MEMORY: -1}" = G ] && heap_mb=$(( heap_mb * 1024 ))
[ "$heap_mb" -lt 1024 ] && die "heap size must be at least 1G"
[ "$heap_mb" -gt $(( ram_mb * 8 / 10 )) ] && echo "warning: $MEMORY is more than 80% of the ${ram_mb}M RAM in this machine" >&2
PORT="$(ask_number "Port" 25565 1 65535)"

echo
echo "Server settings (press Enter to keep a default):"
MOTD="$(ask_text "Message of the day" "A Kirigami Server")"
MAX_PLAYERS="$(ask_number "Max players" 20 1 1000)"
DIFFICULTY="$(ask_choice "Difficulty" normal peaceful easy normal hard)"
GAMEMODE="$(ask_choice "Game mode" survival survival creative adventure spectator)"
PVP="$(ask_choice "PvP" true true false)"
VIEW_DISTANCE="$(ask_number "View distance" 10 3 32)"
SIM_DISTANCE="$(ask_number "Simulation distance" 10 3 32)"
SEED="$(ask_text "World seed (empty for random)")"
ONLINE_MODE="$(ask_choice "Online mode (verify accounts with Mojang)" true true false)"
[ "$ONLINE_MODE" = false ] && echo "warning: offline mode lets anyone join with any name; only use it behind a proxy that authenticates players" >&2
WHITELIST="$(ask_choice "Whitelist (only listed players can join)" false true false)"

OP_NAMES=()
OP_UUIDS=()
if [ "$ONLINE_MODE" = true ]; then
  op_input="$(ask "Operator player names, separated by spaces (empty for none)")"
  read -r -a op_list <<< "${op_input//,/ }"
  for name in ${op_list[@]+"${op_list[@]}"}; do
    [[ "$name" =~ ^[A-Za-z0-9_]{3,16}$ ]] || { echo "warning: skipping invalid player name '$name'" >&2; continue; }
    if uuid="$(lookup_uuid "$name")"; then
      OP_NAMES+=("$name")
      OP_UUIDS+=("$uuid")
      info "Operator $name ($uuid)"
    else
      echo "warning: no Minecraft account named '$name' found; run 'op $name' in the console later" >&2
    fi
  done
else
  info "Offline mode: add operators with 'op <name>' in the server console."
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

if [[ "$METHOD" == *container ]]; then
  NAME="$(ask "Container name" kirigami)"
  [[ "$NAME" =~ ^[a-zA-Z0-9][a-zA-Z0-9_.-]*$ ]] || die "invalid container name '$NAME'"
  "$ENGINE" container inspect "$NAME" > /dev/null 2>&1 && die "a container named '$NAME' already exists"
  VOLUME="$(ask "Data volume name" "$NAME-data")"
  [[ "$VOLUME" =~ ^[a-zA-Z0-9][a-zA-Z0-9_.-]*$ ]] || die "invalid volume name '$VOLUME'"
  info "Pulling $IMAGE:$TAG"
  "$ENGINE" pull "$IMAGE:$TAG" || die "no $TAG release found; see https://github.com/$REPO/releases"
  write_server_files "$tmp" 25565 > /dev/null
  chmod -R a+rX "$tmp"
  seed="$tmp"
  if [ "$OS" = windows ]; then export MSYS_NO_PATHCONV=1; seed="$(cygpath -w "$tmp")"; fi
  # shellcheck disable=SC2016
  "$ENGINE" run --rm --security-opt label=disable -v "$VOLUME:/data" -v "$seed:/seed:ro" --entrypoint sh "$IMAGE:$TAG" \
    -c 'for f in /seed/*; do [ -e "/data/${f##*/}" ] && echo "Keeping existing ${f##*/}" || cp "$f" /data/; done' \
    || die "could not write the server settings into the volume"
  "$ENGINE" run -d -it --name "$NAME" --restart unless-stopped -p "$PORT:25565" \
    -e EULA=true -e MEMORY="$MEMORY" -v "$VOLUME:/data" "$IMAGE:$TAG"
  echo
  info "Server is starting. Console log: $ENGINE logs -f $NAME"
  info "Console: $ENGINE attach $NAME (detach with Ctrl-P Ctrl-Q), stop with $ENGINE stop $NAME"
  info "Settings live in the '$VOLUME' volume (server.properties, config/kirigami.yml)."
  exit 0
fi

if [ -z "$JAVA" ]; then
  install_java || die "Java 25 or newer is required"
  JAVA="$(find_java)" || die "Java 25 was installed but not found; open a new shell and run this script again"
fi
info "Using Java $(java_major "$JAVA") at $JAVA"

DIR="$(ask "Server directory" "$HOME/kirigami")"
DIR="${DIR/#\~/$HOME}"
mkdir -p "$DIR"
DIR="$(cd "$DIR" && pwd)"
if [ -f "$DIR/kirigami.jar" ] && ! confirm "$DIR/kirigami.jar exists. Replace the jar (world data is kept)?"; then
  die "aborted"
fi

info "Downloading kirigami.jar ($TAG)"
download "$URL/kirigami.jar" "$tmp/kirigami.jar" || die "no $TAG release found; see https://github.com/$REPO/releases"
download "$URL/kirigami.jar.sha256" "$tmp/kirigami.jar.sha256" || die "no $TAG release found; see https://github.com/$REPO/releases"
expected="$(awk '{print $1; exit}' "$tmp/kirigami.jar.sha256")"
[ "$(sha256_of "$tmp/kirigami.jar")" = "$expected" ] || die "checksum mismatch; the download is not trusted"
info "Checksum verified"
mv "$tmp/kirigami.jar" "$DIR/kirigami.jar"

echo "eula=true" > "$DIR/eula.txt"
write_server_files "$DIR" "$PORT"

cat > "$DIR/start.sh" <<EOF
#!/usr/bin/env bash
cd "\$(dirname "\$0")"
exec "$JAVA" -Xms$MEMORY -Xmx$MEMORY $G1_FLAGS -jar kirigami.jar --nogui "\$@"
EOF
chmod 755 "$DIR/start.sh"

echo
info "Installed to $DIR. Start it with: $DIR/start.sh"
[ "$OS" = linux ] && open_firewall

SERVICE=""
if [ "$OS" = linux ] && [ -d /run/systemd/system ] && confirm "Create a systemd service so the server starts on boot?"; then
  SERVICE="kirigami-$(basename "$DIR" | tr -c 'A-Za-z0-9_.\n-' '-')"
  [ -e "/etc/systemd/system/$SERVICE.service" ] && die "/etc/systemd/system/$SERVICE.service already exists"
  as_root tee "/etc/systemd/system/$SERVICE.service" > /dev/null <<EOF
[Unit]
Description=Kirigami Minecraft server ($DIR)
After=network-online.target
Wants=network-online.target

[Service]
User=$(id -un)
WorkingDirectory=$DIR
ExecStart="$DIR/start.sh"
Restart=on-failure
RestartSec=10
SuccessExitStatus=0 143
TimeoutStopSec=120

[Install]
WantedBy=multi-user.target
EOF
  as_root systemctl daemon-reload
  as_root systemctl enable "$SERVICE"
  info "Service $SERVICE installed. Logs: journalctl -u $SERVICE -f"
fi

if confirm "Start the server now?"; then
  if [ -n "$SERVICE" ]; then
    as_root systemctl start "$SERVICE"
    info "Started. Follow the log with: journalctl -u $SERVICE -f"
  else
    rm -rf "$tmp"
    exec "$DIR/start.sh" < /dev/tty
  fi
fi
