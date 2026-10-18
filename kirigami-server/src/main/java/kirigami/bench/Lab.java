package kirigami.bench;

import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class Lab {

    static final int CENTER_X = 4160;
    static final int CENTER_Z = 4160;
    static final int Y = 230;
    static final int OBSERVER_Y = Y + 8;

    private static final int TELEGRAPH_Z = 4100;
    private static final int TELEGRAPH_START_X = 4056;
    private static final int LOOP_SIZE = 6;
    private static final int DETECTOR_OFFSET = 3;
    private static final int TELEGRAPH_END_X = 4292;
    private static final int TNT_Z = 4340;
    private static final int TNT_START_X = 4050;
    private static final int TNT_END_X = 4290;
    private static final int GRINDER_MIN_X = 4088;
    private static final int GRINDER_MAX_X = 4104;
    private static final int GRINDER_MIN_Z = 4156;
    private static final int GRINDER_MAX_Z = 4164;
    private static final int GRINDER_DROP_HEIGHT = 32;
    private static final int GRINDER_INTERVAL = 40;
    private static final int GRINDER_BATCH = 6;
    private static final int ITEMS_MIN_X = 4080;
    private static final int ITEMS_MAX_X = 4111;
    private static final int ITEMS_MIN_Z = 4188;
    private static final int ITEMS_MAX_Z = 4192;
    private static final int ITEM_COUNT = 3000;
    private static final int FIGHT_MIN_X = 4210;
    private static final int FIGHT_MAX_X = 4238;
    private static final int FIGHT_MIN_Z = 4214;
    private static final int FIGHT_MAX_Z = 4226;
    private static final int ENDER_MIN_X = 4196;
    private static final int ENDER_MAX_X = 4252;
    private static final int ENDER_MIN_Z = 4242;
    private static final int ENDER_MAX_Z = 4258;
    private static final int ENDER_COUNT = 10;
    private static final int VILLAGE_MIN_X = 4210;
    private static final int VILLAGE_MAX_X = 4238;
    private static final int VILLAGE_MIN_Z = 4284;
    private static final int VILLAGE_MAX_Z = 4296;
    private static final int VILLAGER_COUNT = 8;
    private static final int PORTAL_MIN_X = 4085;
    private static final int PORTAL_FRAME_X = 4094;
    private static final int PORTAL_MAX_X = 4107;
    private static final int PORTAL_MIN_Z = 4054;
    private static final int PORTAL_MAX_Z = 4066;
    private static final int PORTAL_ZOMBIES = 10;
    private static final int PORTAL_INTERVAL = 100;
    private static final int CLOCK_Y = Y + 16;
    private static final int[] MACHINE_ORIGINS = {3910, 4030, 4150, 4280};
    private static final int MACHINE_CLOCKS = 5;
    private static final int CLOCK_SPACING = 10;
    private static final int CLOCK_WIRE = 7;

    private boolean running;
    private int ticks;
    private int grinderSpawned;
    private int portalSpawned;

    void build(final ServerLevel level) {
        buildTelegraph(level);
        buildTnt(level);
        buildHopperFloor(level, GRINDER_MIN_X, GRINDER_MAX_X, GRINDER_MIN_Z, GRINDER_MAX_Z, 3);
        buildHopperFloor(level, ITEMS_MIN_X, ITEMS_MAX_X, ITEMS_MIN_Z, ITEMS_MAX_Z, 2);
        buildFightPen(level);
        buildPen(level, ENDER_MIN_X, ENDER_MAX_X, ENDER_MIN_Z, ENDER_MAX_Z, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        buildVillage(level);
        buildPortal(level);
    }

    int buildRedstone(final ServerLevel level) {
        int clocks = 0;
        for (final int machineX : MACHINE_ORIGINS) {
            for (final int machineZ : MACHINE_ORIGINS) {
                for (int i = 0; i < MACHINE_CLOCKS * MACHINE_CLOCKS; i++) {
                    buildClock(level, machineX + i % MACHINE_CLOCKS * CLOCK_SPACING, machineZ + i / MACHINE_CLOCKS * CLOCK_SPACING);
                    clocks++;
                }
            }
        }
        return clocks;
    }

    private static void buildClock(final ServerLevel level, final int x, final int z) {
        for (int dx = -1; dx <= CLOCK_WIRE + 1; dx++) {
            set(level, x + dx, CLOCK_Y - 1, z, Blocks.STONE.defaultBlockState());
        }
        set(level, x - 1, CLOCK_Y, z, Blocks.REDSTONE_WIRE.defaultBlockState());
        for (int dx = 2; dx <= CLOCK_WIRE; dx++) {
            set(level, x + dx, CLOCK_Y, z, Blocks.REDSTONE_WIRE.defaultBlockState());
        }
        set(level, x + CLOCK_WIRE + 1, CLOCK_Y, z, Blocks.REDSTONE_LAMP.defaultBlockState());
        set(level, x, CLOCK_Y, z, Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.EAST));
        set(level, x + 1, CLOCK_Y, z, Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.WEST));
    }

    void start(final ServerLevel level) {
        final Entity cart = EntityTypes.MINECART.create(level, EntitySpawnReason.COMMAND);
        if (cart != null) {
            cart.snapTo(TELEGRAPH_START_X + 2.5, Y + 0.1, TELEGRAPH_Z - LOOP_SIZE + 0.5, 0.0F, 0.0F);
            cart.setDeltaMovement(0.4, 0.0, 0.0);
            level.addFreshEntity(cart);
        }
        for (int i = 0; i < ITEM_COUNT; i++) {
            final double x = ITEMS_MIN_X + 0.5 + (i * 7919 % ((ITEMS_MAX_X - ITEMS_MIN_X + 1) * 100)) / 100.0;
            final double z = ITEMS_MIN_Z + 0.5 + (i * 104729 % ((ITEMS_MAX_Z - ITEMS_MIN_Z) * 100)) / 100.0;
            final ItemEntity item = new ItemEntity(level, x, Y + 2, z, new ItemStack(Items.STONE));
            item.setDeltaMovement(0.0, 0.0, 0.0);
            level.addFreshEntity(item);
        }
        final EntityType<?>[] fighters = {EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.IRON_GOLEM, EntityTypes.SNOW_GOLEM};
        final int[] amounts = {15, 15, 6, 8};
        for (int type = 0; type < fighters.length; type++) {
            for (int i = 0; i < amounts[type]; i++) {
                final double x = FIGHT_MIN_X + 1.5 + (i * 5 + type * 3) % (FIGHT_MAX_X - FIGHT_MIN_X - 2);
                final double z = FIGHT_MIN_Z + 1.5 + (i * 3 + type) % (FIGHT_MAX_Z - FIGHT_MIN_Z - 2);
                spawnPersistent(level, fighters[type], x, Y + 1, z);
            }
        }
        for (int i = 0; i < ENDER_COUNT; i++) {
            spawnPersistent(level, EntityTypes.ENDERMAN, ENDER_MIN_X + 2.5 + i * 5 % (ENDER_MAX_X - ENDER_MIN_X - 4), Y + 1, ENDER_MIN_Z + 2.5 + i * 3 % (ENDER_MAX_Z - ENDER_MIN_Z - 4));
        }
        for (int i = 0; i < VILLAGER_COUNT; i++) {
            spawnPersistent(level, EntityTypes.VILLAGER, VILLAGE_MIN_X + 2.5 + i * 3, Y + 1, VILLAGE_MIN_Z + 6.5);
        }
        for (int i = 0; i < 3; i++) {
            spawnPersistent(level, EntityTypes.ZOMBIE, VILLAGE_MIN_X + 12.5 + i, Y + 1, VILLAGE_MAX_Z + 3.5);
        }
        for (int i = 0; i < PORTAL_ZOMBIES; i++) {
            spawnPersistent(level, EntityTypes.ZOMBIE, PORTAL_MIN_X + 2.5 + i * 2, Y + 1, PORTAL_MIN_Z + 2.5 + i % 3 * 4);
        }
        final net.minecraft.server.MinecraftServer server = level.getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set noon");
        level.setBlock(new BlockPos(TNT_START_X - 1, Y, TNT_Z), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        this.running = true;
        this.ticks = 0;
        this.grinderSpawned = 0;
        this.portalSpawned = 0;
    }

    void tick(final ServerLevel level) {
        if (!this.running) {
            return;
        }
        this.ticks++;
        if (this.ticks % PORTAL_INTERVAL == 0) {
            spawnPersistent(level, EntityTypes.ZOMBIE, PORTAL_FRAME_X + 1.5 + this.portalSpawned % 2, Y + 2, PORTAL_MIN_Z + 6.5);
            this.portalSpawned++;
        }
        if (this.ticks % GRINDER_INTERVAL != 0) {
            return;
        }
        for (int i = 0; i < GRINDER_BATCH; i++) {
            final double x = GRINDER_MIN_X + 1.5 + (this.grinderSpawned * 3) % (GRINDER_MAX_X - GRINDER_MIN_X - 2);
            final double z = GRINDER_MIN_Z + 1.5 + (this.grinderSpawned * 5) % (GRINDER_MAX_Z - GRINDER_MIN_Z - 2);
            spawnPersistent(level, EntityTypes.ZOMBIE, x, Y + GRINDER_DROP_HEIGHT, z);
            this.grinderSpawned++;
        }
    }

    String report(final ServerLevel level) {
        final StringBuilder out = new StringBuilder("lab");
        out.append(" ticks=").append(this.ticks);
        out.append(" telegraph_pulses=").append(countItems(level, new BlockPos(TELEGRAPH_END_X + 1, Y, TELEGRAPH_Z), new BlockPos(TELEGRAPH_END_X + 1, Y, TELEGRAPH_Z)).values().stream().mapToInt(Integer::intValue).sum());
        out.append(" tnt_remaining=").append(countBlocks(level, Blocks.TNT, new BlockPos(TNT_START_X - 2, Y - 3, TNT_Z - 6), new BlockPos(TNT_END_X + 2, Y + 3, TNT_Z + 6)));
        out.append(" tnt_entities=").append(countEntities(level, new AABB(TNT_START_X - 40, Y - 60, TNT_Z - 40, TNT_END_X + 40, Y + 60, TNT_Z + 40)).getOrDefault("tnt", 0));
        out.append(" grinder_spawned=").append(this.grinderSpawned);
        out.append(" grinder_items=").append(countItems(level, new BlockPos(GRINDER_MIN_X, Y - 1, GRINDER_MIN_Z), new BlockPos(GRINDER_MAX_X, Y - 1, GRINDER_MAX_Z)));
        out.append(" grinder_alive=").append(countEntities(level, new AABB(GRINDER_MIN_X - 1, Y, GRINDER_MIN_Z - 1, GRINDER_MAX_X + 2, Y + GRINDER_DROP_HEIGHT + 4, GRINDER_MAX_Z + 2)));
        final int stored = countItems(level, new BlockPos(ITEMS_MIN_X, Y - 1, ITEMS_MIN_Z), new BlockPos(ITEMS_MAX_X, Y - 1, ITEMS_MAX_Z)).getOrDefault("stone", 0);
        final int floating = level.getEntitiesOfClass(ItemEntity.class, new AABB(ITEMS_MIN_X - 2, Y - 2, ITEMS_MIN_Z - 2, ITEMS_MAX_X + 3, Y + 6, ITEMS_MAX_Z + 3))
            .stream().mapToInt(item -> item.getItem().getCount()).sum();
        out.append(" items_stored=").append(stored).append(" items_floating=").append(floating).append(" items_expected=").append(ITEM_COUNT);
        out.append(" fight_alive=").append(countEntities(level, new AABB(FIGHT_MIN_X, Y, FIGHT_MIN_Z, FIGHT_MAX_X + 1, Y + 6, FIGHT_MAX_Z + 1)));
        out.append(" ender_alive=").append(countEntities(level, new AABB(ENDER_MIN_X - 40, Y - 40, ENDER_MIN_Z - 40, ENDER_MAX_X + 40, Y + 40, ENDER_MAX_Z + 40)).getOrDefault("enderman", 0));
        out.append(" ender_grass=").append(countBlocks(level, Blocks.GRASS_BLOCK, new BlockPos(ENDER_MIN_X, Y, ENDER_MIN_Z), new BlockPos(ENDER_MAX_X, Y + 3, ENDER_MAX_Z)));
        out.append(" village=").append(countEntities(level, new AABB(VILLAGE_MIN_X - 1, Y, VILLAGE_MIN_Z - 1, VILLAGE_MAX_X + 2, Y + 6, VILLAGE_MAX_Z + 6)));
        out.append(" portal_overworld=").append(countEntities(level, new AABB(PORTAL_MIN_X, Y, PORTAL_MIN_Z, PORTAL_MAX_X + 1, Y + 6, PORTAL_MAX_Z + 1)).getOrDefault("zombie", 0));
        final ServerLevel nether = level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        int netherZombies = 0;
        if (nether != null) {
            for (final Entity entity : nether.getAllEntities()) {
                if (entity.getType() == EntityTypes.ZOMBIE) {
                    netherZombies++;
                }
            }
        }
        out.append(" portal_spawned=").append(this.portalSpawned).append(" portal_nether=").append(netherZombies);
        return out.toString();
    }

    static int[][] observerPositions() {
        final int[] xs = {4060, 4100, 4140, 4180, 4220, 4260, 4295};
        final int[] zs = {PORTAL_MIN_Z + 6, TELEGRAPH_Z, GRINDER_MAX_Z + 6, ITEMS_MIN_Z + 2, FIGHT_MIN_Z + 6, ENDER_MIN_Z + 8, VILLAGE_MIN_Z + 6, TNT_Z};
        final int[][] positions = new int[xs.length * zs.length][];
        int i = 0;
        for (final int z : zs) {
            for (final int x : xs) {
                positions[i++] = new int[] {x, OBSERVER_Y, z};
            }
        }
        return positions;
    }

    private static void buildTelegraph(final ServerLevel level) {
        final int a = TELEGRAPH_START_X;
        final int b = TELEGRAPH_Z - LOOP_SIZE;
        final int last = LOOP_SIZE - 1;
        for (int i = 0; i <= last; i++) {
            for (int j = 0; j <= last; j++) {
                final boolean edge = i == 0 || i == last || j == 0 || j == last;
                if (!edge) {
                    continue;
                }
                final boolean corner = (i == 0 || i == last) && (j == 0 || j == last);
                final boolean bottom = j == last;
                set(level, a + i, Y - 1, b + j, corner || bottom ? Blocks.STONE.defaultBlockState() : Blocks.REDSTONE_BLOCK.defaultBlockState());
            }
        }
        for (int i = 1; i < last; i++) {
            set(level, a + i, Y, b, poweredRail(RailShape.EAST_WEST));
            set(level, a, Y, b + i, poweredRail(RailShape.NORTH_SOUTH));
            set(level, a + last, Y, b + i, poweredRail(RailShape.NORTH_SOUTH));
            set(level, a + i, Y, b + last, i == DETECTOR_OFFSET
                ? Blocks.DETECTOR_RAIL.defaultBlockState().setValue(DetectorRailBlock.SHAPE, RailShape.EAST_WEST)
                : Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST));
        }
        set(level, a, Y, b, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.SOUTH_EAST));
        set(level, a + last, Y, b, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.SOUTH_WEST));
        set(level, a, Y, b + last, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.NORTH_EAST));
        set(level, a + last, Y, b + last, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.NORTH_WEST));
        final int wireStart = a + DETECTOR_OFFSET;
        for (int x = wireStart; x < TELEGRAPH_END_X; x++) {
            set(level, x, Y - 1, TELEGRAPH_Z, Blocks.STONE.defaultBlockState());
            final boolean repeater = (x - wireStart) % 15 == 14;
            set(level, x, Y, TELEGRAPH_Z, repeater
                ? Blocks.REPEATER.defaultBlockState().setValue(RepeaterBlock.FACING, Direction.WEST)
                : Blocks.REDSTONE_WIRE.defaultBlockState());
        }
        set(level, TELEGRAPH_END_X, Y, TELEGRAPH_Z, Blocks.DROPPER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        set(level, TELEGRAPH_END_X + 1, Y, TELEGRAPH_Z, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(new BlockPos(TELEGRAPH_END_X, Y, TELEGRAPH_Z)) instanceof Container dropper) {
            for (int slot = 0; slot < dropper.getContainerSize(); slot++) {
                dropper.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
        }
    }

    private static BlockState poweredRail(final RailShape shape) {
        return Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, shape).setValue(PoweredRailBlock.POWERED, true);
    }

    private static void buildTnt(final ServerLevel level) {
        for (int x = TNT_START_X - 2; x <= TNT_END_X; x++) {
            set(level, x, Y - 1, TNT_Z, Blocks.STONE.defaultBlockState());
            set(level, x, Y, TNT_Z, x < TNT_START_X ? Blocks.STONE.defaultBlockState() : Blocks.TNT.defaultBlockState());
        }
    }

    private static void buildHopperFloor(final ServerLevel level, final int minX, final int maxX, final int minZ, final int maxZ, final int wallHeight) {
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                final boolean edge = x < minX || x > maxX || z < minZ || z > maxZ;
                set(level, x, Y - 2, z, Blocks.STONE.defaultBlockState());
                set(level, x, Y - 1, z, edge ? Blocks.STONE.defaultBlockState() : Blocks.CHEST.defaultBlockState());
                set(level, x, Y, z, edge ? Blocks.GLASS.defaultBlockState() : Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
                for (int y = 1; y <= wallHeight && edge; y++) {
                    set(level, x, Y + y, z, Blocks.GLASS.defaultBlockState());
                }
            }
        }
    }

    private static void buildPen(final ServerLevel level, final int minX, final int maxX, final int minZ, final int maxZ, final BlockState floor, final int wallHeight) {
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                final boolean edge = x < minX || x > maxX || z < minZ || z > maxZ;
                set(level, x, Y - 1, z, Blocks.STONE.defaultBlockState());
                set(level, x, Y, z, edge ? Blocks.GLASS.defaultBlockState() : floor);
                for (int y = 1; y <= wallHeight; y++) {
                    set(level, x, Y + y, z, edge ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static void buildVillage(final ServerLevel level) {
        buildPen(level, VILLAGE_MIN_X, VILLAGE_MAX_X, VILLAGE_MIN_Z, VILLAGE_MAX_Z, Blocks.STONE.defaultBlockState(), 3);
        for (int i = 0; i < VILLAGER_COUNT; i++) {
            final int x = VILLAGE_MIN_X + 2 + i * 3;
            set(level, x, Y + 1, VILLAGE_MIN_Z, Blocks.BED.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
            set(level, x, Y + 1, VILLAGE_MIN_Z + 1, Blocks.BED.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
            set(level, x, Y + 1, VILLAGE_MAX_Z, Blocks.COMPOSTER.defaultBlockState());
        }
        buildPen(level, VILLAGE_MIN_X + 12, VILLAGE_MIN_X + 14, VILLAGE_MAX_Z + 3, VILLAGE_MAX_Z + 4, Blocks.STONE.defaultBlockState(), 3);
        roof(level, VILLAGE_MIN_X + 12, VILLAGE_MIN_X + 14, VILLAGE_MAX_Z + 3, VILLAGE_MAX_Z + 4, Y + 4);
    }

    private static void roof(final ServerLevel level, final int minX, final int maxX, final int minZ, final int maxZ, final int y) {
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                set(level, x, y, z, Blocks.STONE.defaultBlockState());
            }
        }
    }

    private static void buildPortal(final ServerLevel level) {
        buildPen(level, PORTAL_MIN_X, PORTAL_MAX_X, PORTAL_MIN_Z, PORTAL_MAX_Z, Blocks.STONE.defaultBlockState(), 6);
        roof(level, PORTAL_MIN_X, PORTAL_MAX_X, PORTAL_MIN_Z, PORTAL_MAX_Z, Y + 7);
        final int frameX = PORTAL_FRAME_X;
        final int frameZ = PORTAL_MIN_Z + 6;
        for (final boolean placeFrame : new boolean[] {true, false}) {
            for (int dx = 0; dx <= 3; dx++) {
                for (int dy = 0; dy <= 4; dy++) {
                    final boolean frame = dx == 0 || dx == 3 || dy == 0 || dy == 4;
                    if (frame == placeFrame) {
                        set(level, frameX + dx, Y + 1 + dy, frameZ, frame
                            ? Blocks.OBSIDIAN.defaultBlockState()
                            : Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X));
                    }
                }
            }
        }
    }

    private static void buildFightPen(final ServerLevel level) {
        for (int x = FIGHT_MIN_X - 1; x <= FIGHT_MAX_X + 1; x++) {
            for (int z = FIGHT_MIN_Z - 1; z <= FIGHT_MAX_Z + 1; z++) {
                final boolean edge = x < FIGHT_MIN_X || x > FIGHT_MAX_X || z < FIGHT_MIN_Z || z > FIGHT_MAX_Z;
                set(level, x, Y, z, Blocks.GLASS.defaultBlockState());
                for (int y = 1; y <= 4; y++) {
                    set(level, x, Y + y, z, edge ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
                set(level, x, Y + 5, z, Blocks.STONE.defaultBlockState());
            }
        }
    }

    private static void set(final ServerLevel level, final int x, final int y, final int z, final BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_ALL);
    }

    private static void spawnPersistent(final ServerLevel level, final EntityType<?> type, final double x, final double y, final double z) {
        final Entity entity = type.create(level, EntitySpawnReason.COMMAND);
        if (entity == null) {
            return;
        }
        entity.snapTo(x, y, z, 0.0F, 0.0F);
        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        level.addFreshEntity(entity, CreatureSpawnEvent.SpawnReason.CUSTOM);
    }

    private static Map<String, Integer> countItems(final ServerLevel level, final BlockPos min, final BlockPos max) {
        final Map<String, Integer> counts = new TreeMap<>();
        for (final BlockPos pos : BlockPos.betweenClosed(min, max)) {
            final BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof Container container) {
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    final ItemStack stack = container.getItem(slot);
                    if (!stack.isEmpty()) {
                        counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), stack.getCount(), Integer::sum);
                    }
                }
            }
        }
        return counts;
    }

    private static int countBlocks(final ServerLevel level, final Block block, final BlockPos min, final BlockPos max) {
        int count = 0;
        for (final BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockState(pos).is(block)) {
                count++;
            }
        }
        return count;
    }

    private static Map<String, Integer> countEntities(final ServerLevel level, final AABB box) {
        final Map<String, Integer> counts = new TreeMap<>();
        for (final Entity entity : level.getEntities((Entity) null, box, entity -> !(entity instanceof net.minecraft.world.entity.player.Player))) {
            counts.merge(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath(), 1, Integer::sum);
        }
        return counts;
    }
}
