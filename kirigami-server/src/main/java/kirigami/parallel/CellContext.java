package kirigami.parallel;

import ca.spottedleaf.moonrise.common.util.ThreadUnsafeRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class CellContext {

    public static final Object GLOBAL_LOCK = new Object();
    public static final boolean CHECK_READS = Boolean.getBoolean("kirigami.checkReads");
    public static final int READ_REACH = Integer.getInteger("kirigami.readReach", 6);
    public static final int WRITE_REACH = Integer.getInteger("kirigami.writeReach", 2);
    private static final ThreadLocal<@Nullable CellContext> CURRENT = new ThreadLocal<>();
    private static final int MAX_DISTANCE_BUCKET = 16;
    private static final AtomicLongArray READ_BY_DISTANCE = new AtomicLongArray(MAX_DISTANCE_BUCKET + 1);
    private static final AtomicLongArray WRITE_BY_DISTANCE = new AtomicLongArray(MAX_DISTANCE_BUCKET + 1);
    private static final AtomicLong LOGGED = new AtomicLong();
    private static final AtomicLong BLOCKING_LOADS = new AtomicLong();
    private static final int MAX_LOGGED = 30;

    private final ServerLevel level;
    private final int cellMinChunkX;
    private final int cellMinChunkZ;
    private final int cellMaxChunkX;
    private final int cellMaxChunkZ;
    private final RandomSource random;
    private final ca.spottedleaf.moonrise.common.util.SimpleThreadUnsafeRandom simpleRandom;
    private int randValue;
    private final CollectingNeighborUpdater neighborUpdater;
    private final List<Runnable> deferred = new ArrayList<>();
    private static final ThreadLocal<net.minecraft.world.level.pathfinder.PathTypeCache> PATH_TYPE_CACHE =
        ThreadLocal.withInitial(net.minecraft.world.level.pathfinder.PathTypeCache::new);

    CellContext(final ServerLevel level, final int cellX, final int cellZ, final int cellShift, final long seed, final int maxChainedNeighborUpdates) {
        this.level = level;
        this.cellMinChunkX = cellX << cellShift;
        this.cellMinChunkZ = cellZ << cellShift;
        this.cellMaxChunkX = ((cellX + 1) << cellShift) - 1;
        this.cellMaxChunkZ = ((cellZ + 1) << cellShift) - 1;
        this.random = new ThreadUnsafeRandom(seed);
        this.simpleRandom = new ca.spottedleaf.moonrise.common.util.SimpleThreadUnsafeRandom(seed ^ 0x5DEECE66DL);
        this.randValue = (int) (seed >>> 32);
        this.neighborUpdater = new CollectingNeighborUpdater(level, maxChainedNeighborUpdates);
    }

    public static @Nullable CellContext current() {
        return CURRENT.get();
    }

    public static @Nullable CellContext currentFor(final Level level) {
        final CellContext context = CURRENT.get();
        return context != null && context.level == level ? context : null;
    }

    public static boolean ownsChunk(final Level level, final int chunkX, final int chunkZ) {
        final CellContext context = currentFor(level);
        if (context != null) {
            context.record(chunkX, chunkZ, false);
        }
        return true;
    }

    public static void checkRead(final Level level, final int chunkX, final int chunkZ) {
        final CellContext context = currentFor(level);
        if (context != null) {
            context.record(chunkX, chunkZ, true);
        }
    }

    public static void checkBox(final Level level, final net.minecraft.world.phys.AABB box) {
        final CellContext context = currentFor(level);
        if (context == null) {
            return;
        }
        final int minChunkX = (net.minecraft.util.Mth.floor(box.minX) - 2) >> 4;
        final int minChunkZ = (net.minecraft.util.Mth.floor(box.minZ) - 2) >> 4;
        final int maxChunkX = (net.minecraft.util.Mth.floor(box.maxX) + 2) >> 4;
        final int maxChunkZ = (net.minecraft.util.Mth.floor(box.maxZ) + 2) >> 4;
        context.record(
            context.distance(minChunkX, minChunkZ) >= context.distance(maxChunkX, maxChunkZ) ? minChunkX : maxChunkX,
            context.distance(minChunkX, minChunkZ) >= context.distance(maxChunkX, maxChunkZ) ? minChunkZ : maxChunkZ,
            true
        );
    }

    private int distance(final int chunkX, final int chunkZ) {
        final int dx = Math.max(Math.max(this.cellMinChunkX - chunkX, chunkX - this.cellMaxChunkX), 0);
        final int dz = Math.max(Math.max(this.cellMinChunkZ - chunkZ, chunkZ - this.cellMaxChunkZ), 0);
        return Math.max(dx, dz);
    }

    private void record(final int chunkX, final int chunkZ, final boolean read) {
        final int distance = this.distance(chunkX, chunkZ);
        if (distance <= (read ? READ_REACH : WRITE_REACH)) {
            return;
        }
        (read ? READ_BY_DISTANCE : WRITE_BY_DISTANCE).incrementAndGet(Math.min(distance, MAX_DISTANCE_BUCKET));
        if (LOGGED.get() < MAX_LOGGED && LOGGED.incrementAndGet() <= MAX_LOGGED) {
            net.minecraft.server.MinecraftServer.LOGGER.warn("Cell {} violation: chunk {},{} is {} chunks outside cell [{},{}]..[{},{}]",
                read ? "read" : "write", chunkX, chunkZ, distance, this.cellMinChunkX, this.cellMinChunkZ, this.cellMaxChunkX, this.cellMaxChunkZ, new Throwable());
        }
    }

    public static void recordBlockingLoad(final Level level) {
        if (currentFor(level) == null) {
            return;
        }
        final long count = BLOCKING_LOADS.incrementAndGet();
        if (count <= 5) {
            net.minecraft.server.MinecraftServer.LOGGER.warn("Blocking load #{} requested from a cell", count, new Throwable());
        }
    }

    public static String violationReport() {
        return "reads" + histogram(READ_BY_DISTANCE) + " writes" + histogram(WRITE_BY_DISTANCE) + " blockingLoads=" + BLOCKING_LOADS.get();
    }

    private static String histogram(final AtomicLongArray counts) {
        final StringBuilder builder = new StringBuilder("{");
        for (int i = 0; i <= MAX_DISTANCE_BUCKET; i++) {
            final long count = counts.get(i);
            if (count > 0) {
                builder.append(builder.length() > 1 ? "," : "").append(i).append(i == MAX_DISTANCE_BUCKET ? "+" : "").append('=').append(count);
            }
        }
        return builder.append('}').toString();
    }

    public static long blockingLoads() {
        return BLOCKING_LOADS.get();
    }

    public static long violations() {
        long total = 0;
        for (int i = 0; i <= MAX_DISTANCE_BUCKET; i++) {
            total += READ_BY_DISTANCE.get(i) + WRITE_BY_DISTANCE.get(i);
        }
        return total;
    }

    public static <T> T exclusive(final Level level, final java.util.function.Supplier<T> action) {
        WorldPause.reason("blocking chunk loads");
        return WorldPause.exclusive(action);
    }

    static @Nullable CellContext detach() {
        final CellContext context = CURRENT.get();
        CURRENT.remove();
        return context;
    }

    static void attach(final @Nullable CellContext context) {
        if (context != null) {
            CURRENT.set(context);
        }
    }

    public static boolean defer(final Level level, final Runnable action) {
        final CellContext context = currentFor(level);
        if (context == null) {
            return false;
        }
        context.deferred.add(action);
        return true;
    }

    public ca.spottedleaf.moonrise.common.util.SimpleThreadUnsafeRandom simpleRandom() {
        return this.simpleRandom;
    }

    public int nextRandValue() {
        this.randValue = this.randValue * 3 + 1013904223;
        return this.randValue;
    }

    public RandomSource random() {
        return this.random;
    }

    public net.minecraft.world.level.pathfinder.PathTypeCache pathTypeCache() {
        return PATH_TYPE_CACHE.get();
    }

    public CollectingNeighborUpdater neighborUpdater() {
        return this.neighborUpdater;
    }

    void enter() {
        CURRENT.set(this);
        PATH_TYPE_CACHE.get().clear();
    }

    void exit() {
        CURRENT.remove();
    }

    void runDeferred() {
        for (final Runnable action : this.deferred) {
            action.run();
        }
        this.deferred.clear();
    }
}
