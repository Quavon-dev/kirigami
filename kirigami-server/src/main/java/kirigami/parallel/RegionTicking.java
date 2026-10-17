package kirigami.parallel;

import ca.spottedleaf.moonrise.common.util.TickThread;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class RegionTicking {

    public static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("kirigami.regionTicking", "true"));
    private static final int CELL_SHIFT = 3;
    private static final int SCHEDULED_TICK_REACH = 24;
    private static final int PAUSE_FALLBACK_TICKS = 600;
    private static final long MIN_FALLBACK_NANOS = 1_000_000L;
    private static final java.util.Set<String> WARNED = new java.util.HashSet<>();
    private static int trackerSerialUntilTick;
    private static final int COLORS = 4;
    private static final int MIN_PARALLEL_ITEMS = Integer.getInteger("kirigami.minParallelItems", 256);
    private static final int WORKERS = Integer.getInteger("kirigami.regionWorkers", Math.max(0, Runtime.getRuntime().availableProcessors() - 1));
    private static final ExecutorService POOL = WORKERS == 0 ? null : Executors.newFixedThreadPool(WORKERS, runnable -> {
        final TickThread thread = new TickThread(runnable, "Kirigami Region Worker");
        thread.setDaemon(true);
        return thread;
    });

    public static final class Stats {
        private long ticks;
        private final long[] parallel = new long[Phase.values().length];
        private final long[] serial = new long[Phase.values().length];
        private long cells;
        private long boundaryBlockEntities;
        private long boundaryScheduledTicks;
        private long pauseFallbacks;

        public long[] snapshot() {
            final int phases = Phase.values().length;
            final long[] values = new long[2 * phases + 4];
            for (int i = 0; i < phases; i++) {
                values[2 * i] = this.parallel[i];
                values[2 * i + 1] = this.serial[i];
            }
            values[2 * phases] = this.cells;
            values[2 * phases + 1] = this.boundaryBlockEntities;
            values[2 * phases + 2] = this.boundaryScheduledTicks;
            values[2 * phases + 3] = this.pauseFallbacks;
            return values;
        }

        public String format() {
            final StringBuilder builder = new StringBuilder("ticks=").append(this.ticks);
            for (final Phase phase : Phase.values()) {
                builder.append(' ').append(phase.label).append('=').append(this.parallel[phase.ordinal()]).append('/').append(this.serial[phase.ordinal()]);
            }
            return builder.append(" cells=").append(this.cells).append(" boundaryBlockEntities=").append(this.boundaryBlockEntities)
                .append(" boundaryScheduledTicks=").append(this.boundaryScheduledTicks).append(" violations=").append(CellContext.violations())
                .append(' ').append(CellContext.violationReport()).toString();
        }
    }

    private enum Phase {
        ENTITIES("entities", 0x9E3779B97F4A7C15L),
        CHUNKS("chunks", 0xC6BC279692B5C323L),
        BLOCK_ENTITIES("blockEntities", 0xD6E8FEB86659FD93L),
        SCHEDULED_TICKS("scheduledTicks", 0xA0761D6478BD642FL);

        private final String label;
        private final long salt;
        private final boolean enabled;
        private int serialUntilTick;
        private long fallbackNanos;
        private long fallbackItems;

        Phase(final String label, final long salt) {
            this.label = label;
            this.salt = salt;
            this.enabled = Boolean.parseBoolean(System.getProperty("kirigami.region." + label, "true"));
        }
    }

    private static final boolean PARALLEL_TRACKER = Boolean.parseBoolean(System.getProperty("kirigami.region.tracker", "true"));
    public static final Stats STATS = new Stats();
    private static final java.util.Set<String> SERIAL_BLOCK_ENTITIES = java.util.Set.of(
        "minecraft:beacon", "minecraft:conduit", "minecraft:bell", "minecraft:end_gateway", "minecraft:creaking_heart"
    );

    private record Cell<T>(CellContext context, List<T> items) {
    }

    private RegionTicking() {
    }

    public static int workers() {
        return WORKERS;
    }

    public static boolean phaseEnabled(final int index) {
        return Phase.values()[index].enabled;
    }

    public static boolean trackerEnabled() {
        return PARALLEL_TRACKER;
    }

    public static void tickEntities(final ServerLevel level, final Consumer<Entity> vanillaTick, final List<Entity> snapshot, final int maxChainedNeighborUpdates) {
        STATS.ticks++;
        run(level, Phase.ENTITIES, snapshot, entity -> entity.chunkPosition().pack(), RegionTicking::requiresSerial, vanillaTick, maxChainedNeighborUpdates, null);
    }

    public static void tickChunks(final ServerLevel level, final LevelChunk[] chunks, final int size, final int randomTickSpeed) {
        final List<LevelChunk> items = java.util.Arrays.asList(chunks).subList(0, size);
        run(level, Phase.CHUNKS, items, chunk -> chunk.getPos().pack(), chunk -> false, chunk -> level.tickChunk(chunk, randomTickSpeed),
            level.mtMaxChainedNeighborUpdates, () -> ((ca.spottedleaf.moonrise.patches.chunk_system.server.ChunkSystemMinecraftServer) level.getServer()).moonrise$executeMidTickTasks());
    }

    public static void tickBlockEntities(final ServerLevel level, final List<net.minecraft.world.level.block.entity.TickingBlockEntity> tickers, final boolean tick,
                                         final java.util.Set<net.minecraft.world.level.block.entity.TickingBlockEntity> toRemove) {
        final List<net.minecraft.world.level.block.entity.TickingBlockEntity> items = new ArrayList<>(tickers.size());
        for (final net.minecraft.world.level.block.entity.TickingBlockEntity ticker : tickers) {
            if (ticker.isRemoved()) {
                toRemove.add(ticker);
            } else if (tick && level.shouldTickBlocksAt(ticker.getPos())) {
                items.add(ticker);
            }
        }
        final java.util.Set<net.minecraft.world.level.block.entity.TickingBlockEntity> crossing = items.size() < MIN_PARALLEL_ITEMS
            ? java.util.Set.of()
            : BoundaryClusters.crossingCells(items, CELL_SHIFT + 4);
        STATS.boundaryBlockEntities += crossing.size();
        run(level, Phase.BLOCK_ENTITIES, items, ticker -> ChunkPos.pack(ticker.getPos()),
            ticker -> SERIAL_BLOCK_ENTITIES.contains(ticker.getType()) || crossing.contains(ticker),
            net.minecraft.world.level.block.entity.TickingBlockEntity::tick, level.mtMaxChainedNeighborUpdates,
            () -> ((ca.spottedleaf.moonrise.patches.chunk_system.server.ChunkSystemMinecraftServer) level.getServer()).moonrise$executeMidTickTasks());
    }

    public static <T> void tickScheduled(final ServerLevel level, final List<net.minecraft.world.ticks.ScheduledTick<T>> ticks,
                                         final Consumer<net.minecraft.world.ticks.ScheduledTick<T>> action) {
        final java.util.function.Predicate<net.minecraft.world.ticks.ScheduledTick<T>> serial;
        if (POOL == null || !Phase.SCHEDULED_TICKS.enabled || ticks.size() < MIN_PARALLEL_ITEMS || runsCommands(ticks)) {
            serial = tick -> true;
        } else {
            final int[] bucketOfTick = new int[ticks.size()];
            final boolean[] spanning = BoundaryClusters.spanningClusters(ticks, CELL_SHIFT + 4, 2 * SCHEDULED_TICK_REACH, bucketOfTick);
            final java.util.Set<net.minecraft.world.ticks.ScheduledTick<T>> serialTicks = new it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<>();
            for (int i = 0; i < ticks.size(); i++) {
                if (spanning[bucketOfTick[i]]) {
                    serialTicks.add(ticks.get(i));
                }
            }
            STATS.boundaryScheduledTicks += serialTicks.size();
            serial = serialTicks::contains;
        }
        run(level, Phase.SCHEDULED_TICKS, ticks, tick -> ChunkPos.pack(tick.pos()), serial, action, level.mtMaxChainedNeighborUpdates,
            () -> ((ca.spottedleaf.moonrise.patches.chunk_system.server.ChunkSystemMinecraftServer) level.getServer()).moonrise$executeMidTickTasks());
    }

    private static boolean runsCommands(final List<? extends net.minecraft.world.ticks.ScheduledTick<?>> ticks) {
        for (final net.minecraft.world.ticks.ScheduledTick<?> tick : ticks) {
            if (tick.type() instanceof net.minecraft.world.level.block.CommandBlock) {
                return true;
            }
        }
        return false;
    }

    private static <T> void run(final ServerLevel level, final Phase phase, final List<T> items, final java.util.function.ToLongFunction<T> chunkKey,
                                final java.util.function.Predicate<T> serialPredicate, final Consumer<T> action, final int maxChainedNeighborUpdates,
                                final @org.jspecify.annotations.Nullable Runnable betweenColors) {
        final boolean fallback = net.minecraft.server.MinecraftServer.currentTick < phase.serialUntilTick;
        if (POOL == null || !PluginGuard.parallelAllowed() || !phase.enabled || items.size() < MIN_PARALLEL_ITEMS || fallback) {
            final long start = System.nanoTime();
            STATS.serial[phase.ordinal()] += items.size();
            items.forEach(action);
            if (fallback) {
                phase.fallbackNanos += System.nanoTime() - start;
                phase.fallbackItems += items.size();
            }
            return;
        }
        final long startNanos = System.nanoTime();
        final long pausedBefore = WorldPause.pausedNanos();
        final List<T> serial = new ArrayList<>();
        @SuppressWarnings("unchecked")
        final Long2ObjectLinkedOpenHashMap<List<T>>[] byColor = new Long2ObjectLinkedOpenHashMap[COLORS];
        for (int i = 0; i < COLORS; i++) {
            byColor[i] = new Long2ObjectLinkedOpenHashMap<>();
        }
        for (final T item : items) {
            if (serialPredicate.test(item)) {
                serial.add(item);
                continue;
            }
            final long key = chunkKey.applyAsLong(item);
            final int cellX = ChunkPos.getX(key) >> CELL_SHIFT;
            final int cellZ = ChunkPos.getZ(key) >> CELL_SHIFT;
            final int color = (cellX & 1) | ((cellZ & 1) << 1);
            byColor[color].computeIfAbsent(ChunkPos.pack(cellX, cellZ), unused -> new ArrayList<>()).add(item);
        }

        final long baseSeed = level.getSeed() ^ SeedSalt.VALUE ^ level.getGameTime() * phase.salt;
        for (int color = 0; color < COLORS; color++) {
            final List<Cell<T>> cells = new ArrayList<>(byColor[color].size());
            for (final var entry : byColor[color].long2ObjectEntrySet()) {
                final long seed = net.minecraft.world.level.levelgen.RandomSupport.mixStafford13(baseSeed ^ entry.getLongKey() * 0xC2B2AE3D27D4EB4FL);
                cells.add(new Cell<>(new CellContext(level, ChunkPos.getX(entry.getLongKey()), ChunkPos.getZ(entry.getLongKey()), CELL_SHIFT, seed, maxChainedNeighborUpdates), entry.getValue()));
                STATS.parallel[phase.ordinal()] += entry.getValue().size();
            }
            STATS.cells += cells.size();
            runCells(cells, action);
            for (final Cell<T> cell : cells) {
                cell.context().runDeferred();
            }
            if (betweenColors != null) {
                betweenColors.run();
            }
        }

        STATS.serial[phase.ordinal()] += serial.size();
        for (final T item : serial) {
            action.accept(item);
        }

        final long wall = System.nanoTime() - startNanos;
        final boolean serialWasFaster = phase.fallbackItems == 0 || wall * phase.fallbackItems > phase.fallbackNanos * items.size();
        if (serialWasFaster && mostlyPaused(startNanos, pausedBefore, phase.label)) {
            phase.serialUntilTick = net.minecraft.server.MinecraftServer.currentTick + PAUSE_FALLBACK_TICKS;
            phase.fallbackNanos = 0;
            phase.fallbackItems = 0;
        }
    }

    public static <T> boolean forEachParallel(final ServerLevel level, final T[] items, final int size, final Consumer<T> action) {
        if (!ENABLED || !PARALLEL_TRACKER || POOL == null || size < Math.max(2, MIN_PARALLEL_ITEMS) || !PluginGuard.parallelAllowed()
            || net.minecraft.server.MinecraftServer.currentTick < trackerSerialUntilTick) {
            return false;
        }
        final long startNanos = System.nanoTime();
        final long pausedBefore = WorldPause.pausedNanos();
        final int slices = Math.min(size, (WORKERS + 1) * 4);
        final AtomicInteger next = new AtomicInteger();
        runOnWorkers(slices, () -> {
            int slice;
            while ((slice = next.getAndIncrement()) < slices) {
                for (int i = (int) ((long) slice * size / slices), end = (int) ((long) (slice + 1) * size / slices); i < end; i++) {
                    WorldPause.enterItem();
                    try {
                        action.accept(items[i]);
                    } finally {
                        WorldPause.exitItem();
                    }
                }
            }
        });
        if (mostlyPaused(startNanos, pausedBefore, "entityTracker")) {
            trackerSerialUntilTick = net.minecraft.server.MinecraftServer.currentTick + PAUSE_FALLBACK_TICKS;
        }
        return true;
    }

    private static boolean mostlyPaused(final long startNanos, final long pausedBefore, final String label) {
        final long wall = System.nanoTime() - startNanos;
        final long paused = WorldPause.pausedNanos() - pausedBefore;
        if (wall < MIN_FALLBACK_NANOS || paused * 2 < wall) {
            return false;
        }
        STATS.pauseFallbacks++;
        if (WARNED.add(label)) {
            net.minecraft.server.MinecraftServer.LOGGER.warn("World pauses for plugin code took {} of {} ms while running {} in parallel; {} now runs serially for {} ticks each time this happens. /kirigami lists the events that cause it.",
                String.format(java.util.Locale.ROOT, "%.1f", paused / 1e6), String.format(java.util.Locale.ROOT, "%.1f", wall / 1e6), label, label, PAUSE_FALLBACK_TICKS);
        }
        return true;
    }

    private static boolean requiresSerial(final Entity entity) {
        return entity instanceof Player
            || entity.isVehicle()
            || entity.isPassenger()
            || entity.portalProcess != null
            || entity instanceof ThrownEnderpearl
            || entity instanceof Projectile projectile && projectile.getOwner() instanceof Player
            || entity instanceof Raider raider && raider.getCurrentRaid() != null
            || entity instanceof AbstractVillager villager && villager.isTrading()
            || entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
            || entity instanceof net.minecraft.world.entity.boss.wither.WitherBoss
            || entity instanceof net.minecraft.world.entity.vehicle.minecart.MinecartHopper
            || entity instanceof net.minecraft.world.entity.vehicle.minecart.MinecartCommandBlock
            || entity.isRemoved();
    }

    private static <T> void runCells(final List<Cell<T>> cells, final Consumer<T> action) {
        final AtomicInteger next = new AtomicInteger();
        final Runnable drain = () -> {
            int index;
            while ((index = next.getAndIncrement()) < cells.size()) {
                final Cell<T> cell = cells.get(index);
                cell.context().enter();
                try {
                    for (final T item : cell.items()) {
                        WorldPause.enterItem();
                        try {
                            action.accept(item);
                        } finally {
                            WorldPause.exitItem();
                        }
                    }
                } finally {
                    cell.context().exit();
                }
            }
        };
        if (POOL == null || cells.size() <= 1) {
            drain.run();
            return;
        }
        runOnWorkers(cells.size(), drain);
    }

    private static void runOnWorkers(final int tasks, final Runnable drain) {
        final int helpers = Math.min(WORKERS, tasks - 1);
        final Future<?>[] futures = new Future<?>[helpers];
        for (int i = 0; i < helpers; i++) {
            futures[i] = POOL.submit(drain);
        }
        drain.run();
        for (final Future<?> future : futures) {
            try {
                future.get();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for region workers", e);
            } catch (final ExecutionException e) {
                throw new IllegalStateException("Region worker failed", e.getCause());
            }
        }
    }
}
