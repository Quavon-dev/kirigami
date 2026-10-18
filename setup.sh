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
  echo "${reply:-$default}"
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
    URL="https://github.com/$REPO/releases/download/$TAG"
    ;;
esac

ram=$(( $(total_ram_mb) / 2 / 1024 ))
[ "$ram" -lt 2 ] && ram=2
[ "$ram" -gt 16 ] && ram=16
MEMORY="$(ask "Heap size" "${ram}G")"
[[ "$MEMORY" =~ ^[0-9]+[MG]$ ]] || die "heap size must look like 8G or 4096M"
PORT="$(ask "Port" 25565)"
[[ "$PORT" =~ ^[0-9]+$ ]] && [ "$PORT" -ge 1 ] && [ "$PORT" -le 65535 ] || die "invalid port '$PORT'"

if [[ "$METHOD" == *container ]]; then
  NAME="$(ask "Container name" kirigami)"
  [[ "$NAME" =~ ^[a-zA-Z0-9][a-zA-Z0-9_.-]*$ ]] || die "invalid container name '$NAME'"
  "$ENGINE" container inspect "$NAME" > /dev/null 2>&1 && die "a container named '$NAME' already exists"
  VOLUME="$(ask "Data volume name" "$NAME-data")"
  [[ "$VOLUME" =~ ^[a-zA-Z0-9][a-zA-Z0-9_.-]*$ ]] || die "invalid volume name '$VOLUME'"
  info "Pulling $IMAGE:$TAG"
  "$ENGINE" pull "$IMAGE:$TAG" || die "no $TAG release found; see https://github.com/$REPO/releases"
  "$ENGINE" run -d -it --name "$NAME" --restart unless-stopped -p "$PORT:25565" \
    -e EULA=true -e MEMORY="$MEMORY" -v "$VOLUME:/data" "$IMAGE:$TAG"
  echo
  info "Server is starting. Console log: $ENGINE logs -f $NAME"
  info "Commands: $ENGINE attach $NAME (detach with Ctrl-P Ctrl-Q), stop with $ENGINE stop $NAME"
  exit 0
fi

if [ -z "$JAVA" ]; then
  install_java || die "Java 25 or newer is required"
  JAVA="$(find_java)" || die "Java 25 was installed but not found; open a new shell and run this script again"
fi
info "Using Java $(java_major "$JAVA") at $JAVA"

DIR="$(ask "Server directory" "$HOME/kirigami")"
mkdir -p "$DIR"
DIR="$(cd "$DIR" && pwd)"
if [ -f "$DIR/kirigami.jar" ] && ! confirm "$DIR/kirigami.jar exists. Replace the jar (world data is kept)?"; then
  die "aborted"
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
info "Downloading kirigami.jar ($TAG)"
download "$URL/kirigami.jar" "$tmp/kirigami.jar" || die "no $TAG release found; see https://github.com/$REPO/releases"
download "$URL/kirigami.jar.sha256" "$tmp/kirigami.jar.sha256" || die "no $TAG release found; see https://github.com/$REPO/releases"
expected="$(awk '{print $1; exit}' "$tmp/kirigami.jar.sha256")"
[ "$(sha256_of "$tmp/kirigami.jar")" = "$expected" ] || die "checksum mismatch; the download is not trusted"
info "Checksum verified"
mv "$tmp/kirigami.jar" "$DIR/kirigami.jar"

echo "eula=true" > "$DIR/eula.txt"
if [ -f "$DIR/server.properties" ]; then
  info "Keeping existing server.properties"
else
  echo "server-port=$PORT" > "$DIR/server.properties"
fi

cat > "$DIR/start.sh" <<EOF
#!/usr/bin/env bash
cd "\$(dirname "\$0")"
exec "$JAVA" -Xms$MEMORY -Xmx$MEMORY $G1_FLAGS -jar kirigami.jar --nogui "\$@"
EOF
chmod 755 "$DIR/start.sh"

echo
info "Installed to $DIR. Start it with: $DIR/start.sh"
info "Open port $PORT/tcp in your firewall if players connect from outside."
if confirm "Start the server now?"; then
  exec "$DIR/start.sh"
fi
