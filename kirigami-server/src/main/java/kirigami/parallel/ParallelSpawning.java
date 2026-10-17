package kirigami.parallel;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.misc.NearbyPlayers;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.PotentialCalculator;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.RandomSupport;
import org.bukkit.craftbukkit.util.CraftSpawnCategory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class ParallelSpawning {

    public static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("kirigami.parallelSpawning", "true")) && TickWorkers.workers() > 0;
    private static final boolean VERIFY = Boolean.getBoolean("kirigami.verifySpawning");
    private static final int DIRTY_RADIUS = 3;
    private static final int BATCH = 32;
    private static final int MIN_SPECULATIVE_JOBS = Integer.getInteger("kirigami.minSpeculativeJobs", 512);
    private static final int CENSUS_BATCH = 256;

    private static final class Job {
        private final LevelChunk chunk;
        private final MobCategory category;
        private final long seed;
        private @Nullable SpawnSearch search;

        private Job(final LevelChunk chunk, final MobCategory category, final long seed) {
            this.chunk = chunk;
            this.category = category;
            this.seed = seed;
        }
    }

    private static final class ChunkPlan {
        private final LevelChunk chunk;
        private final Job[] jobs;
        private boolean inBorder;
        private @Nullable ReferenceList<ServerPlayer> players;

        private ChunkPlan(final LevelChunk chunk, final int categories) {
            this.chunk = chunk;
            this.jobs = new Job[categories];
        }
    }

    public static final class Stats {
        private long searches;
        private long speculated;
        private long reused;
        private long aborted;
        private long dirty;
        private long mismatches;

        public long[] snapshot() {
            return new long[] {this.searches, this.speculated, this.reused, this.aborted, this.dirty, this.mismatches};
        }

        public String format() {
            return "searches=" + this.searches + " speculated=" + this.speculated + " reused=" + this.reused
                + " aborted=" + this.aborted + " dirty=" + this.dirty + " mismatches=" + this.mismatches;
        }
    }

    public static final Stats STATS = new Stats();

    private ParallelSpawning() {
    }

    public static void tickSpawningChunks(final ServerLevel level, final List<LevelChunk> chunks, final List<MobCategory> categories,
                                          final NaturalSpawner.SpawnState state) {
        if (categories.isEmpty()) {
            for (final LevelChunk chunk : chunks) {
                chunk.incrementInhabitedTime();
                level.tickThunder(chunk);
            }
            return;
        }
        final boolean perPlayer = level.paperConfig().entities.spawning.perPlayerMobSpawns;
        final boolean fireEvents = com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent.getHandlerList().getRegisteredListeners().length > 0;
        final boolean thunderPossible = level.isRaining() && level.isThundering();
        final int[] limits = spawnLimits(level, categories);
        final long baseSeed = RandomSupport.mixStafford13(level.getSeed() ^ SeedSalt.VALUE ^ level.getGameTime() * 0x9E3779B97F4A7C15L);

        final boolean speculate = !thunderPossible && chunks.size() * categories.size() >= MIN_SPECULATIVE_JOBS
            && PluginGuard.parallelAllowed();
        final List<ChunkPlan> plans = new ArrayList<>(chunks.size());
        for (final LevelChunk chunk : chunks) {
            plans.add(new ChunkPlan(chunk, categories.size()));
        }
        final Consumer<ChunkPlan> prepare = plan -> {
            plan.inBorder = level.getWorldBorder().isWithinBounds(plan.chunk.getPos());
            if (!plan.inBorder) {
                return;
            }
            plan.players = perPlayer ? level.moonrise$getNearbyPlayers().getPlayers(plan.chunk.getPos(), NearbyPlayers.NearbyMapType.TICK_VIEW_DISTANCE) : null;
            for (int i = 0; i < categories.size(); i++) {
                final MobCategory category = categories.get(i);
                if (maxSpawns(level, plan, category, limits[i], perPlayer, state) > 0) {
                    plan.jobs[i] = new Job(plan.chunk, category, seedFor(baseSeed, plan.chunk.getPos(), category));
                }
            }
        };
        if (perPlayer) {
            TickWorkers.forEachSpeculative(plans, BATCH, prepare);
        } else {
            plans.forEach(prepare);
        }

        if (speculate) {
            final List<Job> jobs = new ArrayList<>();
            for (final ChunkPlan plan : plans) {
                for (final Job job : plan.jobs) {
                    if (job != null) {
                        jobs.add(job);
                    }
                }
            }
            level.environmentAttributes().prepareForConcurrentReads();
            TickWorkers.forEachSpeculative(jobs, BATCH, job -> {
                try {
                    final SpawnSearch search = new SpawnSearch(level, job.chunk, job.category, job.seed, fireEvents);
                    search.speculate();
                    job.search = search;
                } catch (final RuntimeException e) {
                    job.search = null;
                }
            });
        }

        final LongOpenHashSet dirty = new LongOpenHashSet();
        final Consumer<Entity> track = perPlayer ? level.getChunkSource().chunkMap::updatePlayerMobTypeMap : null;
        for (final ChunkPlan plan : plans) {
            final LevelChunk chunk = plan.chunk;
            chunk.incrementInhabitedTime();
            level.tickThunder(chunk);
            if (!plan.inBorder) {
                continue;
            }
            for (int i = 0; i < categories.size(); i++) {
                final Job job = plan.jobs[i];
                if (job == null) {
                    continue;
                }
                final int maxSpawns = maxSpawns(level, plan, job.category, limits[i], perPlayer, state);
                if (maxSpawns <= 0) {
                    continue;
                }
                STATS.searches++;
                final SpawnSearch search = selectSearch(level, job, dirty, fireEvents);
                final int[] spawned = {0};
                search.runSerial(state::canSpawn, (mob, spawnChunk) -> {
                    spawned[0]++;
                    state.afterSpawn(mob, spawnChunk);
                }, perPlayer ? maxSpawns : Integer.MAX_VALUE, track);
                if (spawned[0] > 0) {
                    markDirty(dirty, chunk.getPos());
                }
            }
        }
    }

    private static SpawnSearch selectSearch(final ServerLevel level, final Job job, final LongOpenHashSet dirty, final boolean fireEvents) {
        final SpawnSearch speculated = job.search;
        if (speculated != null) {
            STATS.speculated++;
            if (speculated.status() == SpawnSearch.Status.ABORTED) {
                STATS.aborted++;
            } else if (dirty.contains(job.chunk.getPos().pack())) {
                STATS.dirty++;
            } else {
                STATS.reused++;
                if (VERIFY) {
                    verify(level, job, speculated, fireEvents);
                }
                return speculated;
            }
        }
        return new SpawnSearch(level, job.chunk, job.category, job.seed, fireEvents);
    }

    private static void verify(final ServerLevel level, final Job job, final SpawnSearch speculated, final boolean fireEvents) {
        final SpawnSearch reference = new SpawnSearch(level, job.chunk, job.category, job.seed, fireEvents);
        Speculation.enter();
        try {
            reference.speculate();
        } finally {
            Speculation.exit();
        }
        if (!reference.snapshot().equals(speculated.snapshot())) {
            STATS.mismatches++;
            net.minecraft.server.MinecraftServer.LOGGER.error("Spawn speculation mismatch at {} {}: speculated={} reference={}",
                job.chunk.getPos(), job.category, speculated.snapshot(), reference.snapshot());
        }
    }

    private static void markDirty(final LongOpenHashSet dirty, final ChunkPos pos) {
        for (int dx = -DIRTY_RADIUS; dx <= DIRTY_RADIUS; dx++) {
            for (int dz = -DIRTY_RADIUS; dz <= DIRTY_RADIUS; dz++) {
                dirty.add(ChunkPos.pack(pos.x() + dx, pos.z() + dz));
            }
        }
    }

    private static long seedFor(final long baseSeed, final ChunkPos pos, final MobCategory category) {
        return RandomSupport.mixStafford13(baseSeed ^ pos.pack() * 0xC2B2AE3D27D4EB4FL ^ (long) category.ordinal() * 0x165667B19E3779F9L);
    }

    private static int[] spawnLimits(final ServerLevel level, final List<MobCategory> categories) {
        final int[] limits = new int[categories.size()];
        for (int i = 0; i < categories.size(); i++) {
            final MobCategory category = categories.get(i);
            final org.bukkit.entity.SpawnCategory spawnCategory = CraftSpawnCategory.toBukkit(category);
            limits[i] = CraftSpawnCategory.isValidForLimits(spawnCategory) ? level.getWorld().getSpawnLimit(spawnCategory) : category.getMaxInstancesPerChunk();
        }
        return limits;
    }

    private static int maxSpawns(final ServerLevel level, final ChunkPlan plan, final MobCategory category, final int limit,
                                 final boolean perPlayer, final NaturalSpawner.SpawnState state) {
        if (!perPlayer) {
            return state.canSpawnForCategoryLocal(category, plan.chunk.getPos()) ? Integer.MAX_VALUE : 0;
        }
        final ReferenceList<ServerPlayer> inRange = plan.players;
        if (inRange == null) {
            return 0;
        }
        int minDiff = Integer.MAX_VALUE;
        final ServerPlayer[] players = inRange.getRawDataUnchecked();
        final int index = category.ordinal();
        for (int k = 0, len = inRange.size(); k < len; k++) {
            minDiff = Math.min(limit - players[k].mobCounts[index] - players[k].mobBackoffCounts[index], minDiff);
        }
        return minDiff == Integer.MAX_VALUE ? 0 : minDiff;
    }

    public static NaturalSpawner.SpawnState createState(final int spawnableChunkCount, final ServerLevel level, final NaturalSpawner.ChunkGetter chunkGetter,
                                                        final @Nullable LocalMobCapCalculator localMobCapCalculator, final boolean countMobs) {
        final List<Entity> entities = new ArrayList<>();
        level.getAllEntities().forEach(entities::add);
        final boolean countAll = level.paperConfig().entities.spawning.countAllMobsForSpawning;
        final int size = entities.size();
        final LevelChunk[] chunks = new LevelChunk[size];
        final double[] charges = new double[size];
        level.environmentAttributes().prepareForConcurrentReads();
        final boolean[] serial = new boolean[size];
        final IntConsumer census = index -> {
            final Entity entity = entities.get(index);
            if (entity instanceof Mob mob && (mob.isPersistenceRequired() || mob.requiresCustomPersistence())) {
                return;
            }
            final MobCategory category = entity.getType().getCategory();
            if (category == MobCategory.MISC) {
                return;
            }
            if (!countAll && entity.spawnReason != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL
                && entity.spawnReason != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CHUNK_GEN) {
                return;
            }
            final BlockPos pos = entity.blockPosition();
            chunkGetter.query(ChunkPos.pack(pos), chunk -> {
                final MobSpawnSettings.MobSpawnCost cost = level.environmentAttributes()
                    .getValue(EnvironmentAttributes.NATURAL_MOB_SPAWNS, pos).getMobSpawnCost(entity.getType());
                charges[index] = cost == null ? Double.NaN : cost.charge();
                chunks[index] = chunk;
            });
        };
        TickWorkers.forRangeSpeculative(size, CENSUS_BATCH, index -> {
            try {
                census.accept(index);
            } catch (final RuntimeException e) {
                chunks[index] = null;
                serial[index] = true;
            }
        });
        for (int i = 0; i < size; i++) {
            if (serial[i]) {
                census.accept(i);
            }
        }
        if (VERIFY) {
            final LevelChunk[] parallelChunks = chunks.clone();
            final double[] parallelCharges = charges.clone();
            java.util.Arrays.fill(chunks, null);
            for (int i = 0; i < size; i++) {
                census.accept(i);
                if (chunks[i] != parallelChunks[i] || Double.compare(charges[i], parallelCharges[i]) != 0 && chunks[i] != null) {
                    STATS.mismatches++;
                    net.minecraft.server.MinecraftServer.LOGGER.error("Census mismatch for {}", entities.get(i));
                }
            }
        }

        final PotentialCalculator spawnPotential = new PotentialCalculator();
        final Object2IntOpenHashMap<MobCategory> mobCounts = new Object2IntOpenHashMap<>();
        for (int i = 0; i < size; i++) {
            final LevelChunk chunk = chunks[i];
            if (chunk == null) {
                continue;
            }
            final Entity entity = entities.get(i);
            final MobCategory category = entity.getType().getCategory();
            if (!Double.isNaN(charges[i])) {
                spawnPotential.addCharge(entity.blockPosition(), charges[i]);
            }
            if (localMobCapCalculator != null && entity instanceof Mob) {
                localMobCapCalculator.addMob(chunk.getPos(), category);
            }
            mobCounts.addTo(category, 1);
            if (countMobs) {
                level.getChunkSource().chunkMap.updatePlayerMobTypeMap(entity);
            }
        }
        return new NaturalSpawner.SpawnState(spawnableChunkCount, mobCounts, spawnPotential, localMobCapCalculator);
    }
}
