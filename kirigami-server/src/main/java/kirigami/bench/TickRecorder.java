package kirigami.bench;

import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
final class TickRecorder {

    record Summary(int ticks, double meanMs, double p50Ms, double p95Ms, double p99Ms, double maxMs, Path file) {
        String format() {
            return String.format(Locale.ROOT, "ticks=%d mean=%.2fms p50=%.2fms p95=%.2fms p99=%.2fms max=%.2fms -> %s",
                this.ticks, this.meanMs, this.p50Ms, this.p95Ms, this.p99Ms, this.maxMs, this.file);
        }
    }

    private static final int WORLD_SAMPLE_INTERVAL = 20;

    private record WorldSample(int entities, int loadedChunks, int tickingChunks, int players) {
        private static final WorldSample EMPTY = new WorldSample(0, 0, 0, 0);

        private static WorldSample capture(final MinecraftServer server) {
            int entities = 0;
            int loadedChunks = 0;
            int tickingChunks = 0;
            for (final ServerLevel level : server.getAllLevels()) {
                for (final Entity ignored : level.getAllEntities()) {
                    entities++;
                }
                loadedChunks += level.getChunkSource().getLoadedChunksCount();
                tickingChunks += ((ChunkSystemServerLevel) level).moonrise$getEntityTickingChunks().size();
            }
            return new WorldSample(entities, loadedChunks, tickingChunks, server.getPlayerCount());
        }
    }

    private @Nullable String name;
    private final LongArrayList samples = new LongArrayList();
    private final List<WorldSample> worldSamples = new ArrayList<>();
    private WorldSample lastWorldSample = WorldSample.EMPTY;

    boolean recording() {
        return this.name != null;
    }

    void start(final String name) {
        this.name = name;
        this.samples.clear();
        this.worldSamples.clear();
        this.lastWorldSample = WorldSample.EMPTY;
    }

    void tick(final MinecraftServer server) {
        if (this.name == null) {
            return;
        }
        final int previousTick = server.getTickCount() - 1;
        if (previousTick < 0) {
            return;
        }
        if (this.samples.size() % WORLD_SAMPLE_INTERVAL == 0) {
            this.lastWorldSample = WorldSample.capture(server);
        }
        this.samples.add(server.getTickTimesNanos()[previousTick % 100]);
        this.worldSamples.add(this.lastWorldSample);
    }

    Summary stop(final Path directory) throws IOException {
        final String runName = this.name;
        if (runName == null) {
            throw new IllegalStateException("Not recording");
        }
        this.name = null;
        Files.createDirectories(directory);
        final Path file = directory.resolve(runName + ".csv");
        final StringBuilder csv = new StringBuilder("tick,nanos,entities,loaded_chunks,ticking_chunks,players\n");
        for (int i = 0; i < this.samples.size(); i++) {
            final WorldSample world = this.worldSamples.get(i);
            csv.append(i).append(',').append(this.samples.getLong(i))
                .append(',').append(world.entities()).append(',').append(world.loadedChunks())
                .append(',').append(world.tickingChunks()).append(',').append(world.players()).append('\n');
        }
        Files.writeString(file, csv);
        return summarize(this.samples.toLongArray(), file);
    }

    private static Summary summarize(final long[] nanos, final Path file) {
        if (nanos.length == 0) {
            return new Summary(0, 0, 0, 0, 0, 0, file);
        }
        final long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        final double mean = Arrays.stream(sorted).average().orElse(0) / 1e6;
        return new Summary(sorted.length, mean, percentile(sorted, 0.50), percentile(sorted, 0.95),
            percentile(sorted, 0.99), sorted[sorted.length - 1] / 1e6, file);
    }

    private static double percentile(final long[] sorted, final double fraction) {
        final int index = Math.min(sorted.length - 1, (int) Math.ceil(fraction * sorted.length) - 1);
        return sorted[Math.max(0, index)] / 1e6;
    }
}
