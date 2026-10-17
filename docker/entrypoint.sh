#!/bin/sh
set -euf

case "${EULA:-}" in
  true|TRUE) echo "eula=true" > eula.txt ;;
  *)
    echo "Set EULA=true to accept the Minecraft EULA (https://aka.ms/MinecraftEULA)." >&2
    exit 1
    ;;
esac

exec java -Xms"$MEMORY" -Xmx"$MEMORY" $JAVA_OPTS -jar /opt/kirigami/kirigami.jar --nogui "$@"
