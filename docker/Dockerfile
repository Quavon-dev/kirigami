FROM eclipse-temurin:25-jre

RUN groupadd --system minecraft \
    && useradd --system --gid minecraft --home-dir /data minecraft \
    && mkdir -p /data /opt/kirigami \
    && chown minecraft:minecraft /data

COPY --chmod=755 entrypoint.sh /opt/kirigami/entrypoint.sh
COPY kirigami.jar /opt/kirigami/kirigami.jar

ENV MEMORY=4G \
    JAVA_OPTS="-XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+ParallelRefProcEnabled -XX:+PerfDisableSharedMem -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1HeapRegionSize=8M -XX:G1HeapWastePercent=5 -XX:G1MaxNewSizePercent=40 -XX:G1MixedGCCountTarget=4 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1NewSizePercent=30 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=15 -XX:MaxGCPauseMillis=200 -XX:MaxTenuringThreshold=1 -XX:SurvivorRatio=32"

USER minecraft
WORKDIR /data
VOLUME /data
EXPOSE 25565

ENTRYPOINT ["/opt/kirigami/entrypoint.sh"]
