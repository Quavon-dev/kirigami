package kirigami.parallel;

import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
final class SpawnSearch {

    enum Status { RUNNING, CANDIDATE, DONE, ABORTED }

    record Snapshot(Status status, int groupCount, int step, int x, int z, int max, int draws, @Nullable EntityType<?> type) {
    }

    private static final int GROUPS = 3;

    private final ServerLevel level;
    private final LevelChunk chunk;
    private final MobCategory category;
    private final CountingRandom random;
    private final boolean fireEvents;
    private final StructureManager structureManager;
    private final ChunkGenerator generator;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    private Status status = Status.RUNNING;
    private int yStart;
    private int startX;
    private int startZ;
    private int groupCount;
    private int step;
    private int x;
    private int z;
    private int max;
    private int groupSize;
    private int clusterSize;
    private double nearestPlayerDistanceSqr;
    private MobSpawnSettings.@Nullable SpawnerData currentSpawnData;
    private @Nullable SpawnGroupData groupData;

    SpawnSearch(final ServerLevel level, final LevelChunk chunk, final MobCategory category, final long seed, final boolean fireEvents) {
        this.level = level;
        this.chunk = chunk;
        this.category = category;
        this.random = new CountingRandom(seed);
        this.fireEvents = fireEvents;
        this.structureManager = level.structureManager();
        this.generator = level.getChunkSource().getGenerator();
        this.begin();
    }

    Status status() {
        return this.status;
    }

    MobCategory category() {
        return this.category;
    }

    Snapshot snapshot() {
        return new Snapshot(this.status, this.groupCount, this.step, this.x, this.z, this.max, this.random.draws(),
            this.currentSpawnData == null ? null : this.currentSpawnData.type());
    }

    void speculate() {
        try {
            this.advance();
        } catch (final RuntimeException e) {
            this.status = Status.ABORTED;
        }
    }

    void runSerial(final NaturalSpawner.SpawnPredicate extraTest, final NaturalSpawner.AfterSpawnCallback spawnCallback,
                   final int maxSpawns, final @Nullable Consumer<Entity> trackEntity) {
        if (this.status == Status.RUNNING) {
            this.advance();
        }
        while (this.status == Status.CANDIDATE) {
            if (this.commitCandidate(extraTest, spawnCallback, maxSpawns, trackEntity)) {
                this.status = Status.DONE;
                return;
            }
            this.advance();
        }
    }

    private void begin() {
        final int blockX = this.chunk.getPos().getMinBlockX() + this.random.nextInt(16);
        final int blockZ = this.chunk.getPos().getMinBlockZ() + this.random.nextInt(16);
        final int topEmptyY = this.chunk.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ) + 1;
        this.yStart = Mth.randomBetweenInclusive(this.random, this.level.getMinY(), topEmptyY);
        this.startX = blockX;
        this.startZ = blockZ;
        this.groupCount = -1;
        this.step = 0;
        this.max = 0;
    }

    private void advance() {
        if (this.groupCount == -1) {
            if (this.yStart < this.level.getMinY() + 1) {
                this.status = Status.DONE;
                return;
            }
            final BlockPos start = new BlockPos(this.startX, this.yStart, this.startZ);
            final BlockState state = this.level.getBlockStateIfLoadedAndInBounds(start);
            if (state == null || state.isRedstoneConductor(this.chunk, start)) {
                this.status = Status.DONE;
                return;
            }
            this.startGroup(0);
        }
        while (this.groupCount < GROUPS) {
            while (this.step < this.max) {
                this.step++;
                this.x += this.random.nextInt(6) - this.random.nextInt(6);
                this.z += this.random.nextInt(6) - this.random.nextInt(6);
                this.pos.set(this.x, this.yStart, this.z);
                final double xx = this.x + 0.5;
                final double zz = this.z + 0.5;
                final Player nearestPlayer = this.level.getNearestPlayer(xx, this.yStart, zz, -1.0, false);
                if (nearestPlayer == null) {
                    continue;
                }
                this.nearestPlayerDistanceSqr = nearestPlayer.distanceToSqr(xx, this.yStart, zz);
                if (!this.level.isLoadedAndInBounds(this.pos)
                    || !NaturalSpawner.isRightDistanceToPlayerAndSpawnPoint(this.level, this.chunk, this.pos, this.nearestPlayerDistanceSqr)) {
                    continue;
                }
                if (this.currentSpawnData == null) {
                    final Optional<MobSpawnSettings.SpawnerData> next = NaturalSpawner.getRandomSpawnMobAt(
                        this.level, this.structureManager, this.generator, this.category, this.random, this.pos
                    );
                    if (next.isEmpty()) {
                        break;
                    }
                    this.currentSpawnData = next.get();
                    this.max = this.currentSpawnData.count().sample(this.random);
                }
                final NaturalSpawner.PreSpawnStatus result = this.checkPosition();
                if (result == NaturalSpawner.PreSpawnStatus.ABORT || result == NaturalSpawner.PreSpawnStatus.CANCELLED) {
                    this.level.getChunkSource().chunkMap.updateFailurePlayerMobTypeMap(this.pos.getX() >> 4, this.pos.getZ() >> 4, this.category);
                }
                if (result == NaturalSpawner.PreSpawnStatus.ABORT) {
                    this.status = Status.DONE;
                    return;
                }
                if (result == NaturalSpawner.PreSpawnStatus.SUCCESS) {
                    this.status = Status.CANDIDATE;
                    return;
                }
            }
            this.startGroup(this.groupCount + 1);
        }
        this.status = Status.DONE;
    }

    private void startGroup(final int group) {
        this.groupCount = group;
        if (group >= GROUPS) {
            return;
        }
        this.x = this.startX;
        this.z = this.startZ;
        this.currentSpawnData = null;
        this.groupData = null;
        this.max = Mth.ceil(this.random.nextFloat() * 4.0F);
        this.step = 0;
        this.groupSize = 0;
    }

    private NaturalSpawner.PreSpawnStatus checkPosition() {
        final MobSpawnSettings.SpawnerData spawnData = this.currentSpawnData;
        final EntityType<?> type = spawnData.type();
        if (this.fireEvents) {
            final com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent event = new com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent(
                org.bukkit.craftbukkit.util.CraftLocation.toBukkit(this.pos, this.level),
                org.bukkit.craftbukkit.entity.CraftEntityType.minecraftToBukkit(type),
                org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL
            );
            if (!event.callEvent()) {
                return event.shouldAbortSpawn() ? NaturalSpawner.PreSpawnStatus.ABORT : NaturalSpawner.PreSpawnStatus.CANCELLED;
            }
        }
        final double despawnDistance = type.getCategory().getDespawnDistance();
        final boolean success = type.getCategory() != MobCategory.MISC
            && (type.canSpawnFarFromPlayer() || !(this.nearestPlayerDistanceSqr > despawnDistance * despawnDistance))
            && type.canSummon()
            && NaturalSpawner.canSpawnMobAt(this.level, this.structureManager, this.generator, this.category, spawnData, this.pos)
            && SpawnPlacements.isSpawnPositionOk(type, this.level, this.pos)
            && SpawnPlacements.checkSpawnRules(type, this.level, EntitySpawnReason.NATURAL, this.pos, this.random)
            && this.level.noCollision(type.getSpawnAABB(this.pos.getX() + 0.5, this.pos.getY(), this.pos.getZ() + 0.5));
        return success ? NaturalSpawner.PreSpawnStatus.SUCCESS : NaturalSpawner.PreSpawnStatus.FAIL;
    }

    private boolean commitCandidate(final NaturalSpawner.SpawnPredicate extraTest, final NaturalSpawner.AfterSpawnCallback spawnCallback,
                                    final int maxSpawns, final @Nullable Consumer<Entity> trackEntity) {
        this.status = Status.RUNNING;
        final MobSpawnSettings.SpawnerData spawnData = this.currentSpawnData;
        if (!extraTest.test(spawnData.type(), this.level, this.pos, this.chunk)) {
            return false;
        }
        final Mob mob = NaturalSpawner.getMobForSpawn(this.level, spawnData.type());
        if (mob == null) {
            return true;
        }
        mob.snapTo(this.x + 0.5, this.yStart, this.z + 0.5, this.random.nextFloat() * 360.0F, 0.0F);
        if (!NaturalSpawner.isValidPositionForMob(this.level, mob, this.nearestPlayerDistanceSqr)) {
            return false;
        }
        this.groupData = mob.finalizeSpawn(this.level, this.level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.NATURAL, this.groupData);
        this.level.addFreshEntityWithPassengers(mob,
            mob instanceof net.minecraft.world.entity.animal.feline.Ocelot && !((org.bukkit.entity.Ageable) mob.getBukkitEntity()).isAdult()
                ? org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.OCELOT_BABY
                : org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL);
        if (!mob.isRemoved()) {
            this.clusterSize++;
            this.groupSize++;
            spawnCallback.run(mob, this.chunk);
            if (trackEntity != null) {
                trackEntity.accept(mob);
            }
        }
        if (this.clusterSize >= maxSpawns || this.clusterSize >= mob.getMaxSpawnClusterSize()) {
            return true;
        }
        if (mob.isMaxGroupSizeReached(this.groupSize)) {
            this.step = this.max;
        }
        return false;
    }

    private static final class CountingRandom extends net.minecraft.world.level.levelgen.XoroshiroRandomSource {
        private int draws;

        private CountingRandom(final long seed) {
            super(seed);
        }

        int draws() {
            return this.draws;
        }

        @Override
        public long nextLong() {
            this.draws++;
            return super.nextLong();
        }

        @Override
        public int nextInt() {
            this.draws++;
            return super.nextInt();
        }

        @Override
        public int nextInt(final int bound) {
            this.draws++;
            return super.nextInt(bound);
        }

        @Override
        public boolean nextBoolean() {
            this.draws++;
            return super.nextBoolean();
        }

        @Override
        public float nextFloat() {
            this.draws++;
            return super.nextFloat();
        }

        @Override
        public double nextDouble() {
            this.draws++;
            return super.nextDouble();
        }

        @Override
        public double nextGaussian() {
            this.draws++;
            return super.nextGaussian();
        }
    }
}
