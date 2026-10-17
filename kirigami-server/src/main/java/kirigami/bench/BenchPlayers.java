package kirigami.bench;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class BenchPlayers {

    enum Mode { IDLE, CIRCLE, LINE }

    private static final double FLY_Y = 120.0;
    private static final double CLEARANCE = 3.0;
    private static final double VERTICAL_SPEED = 1.0;
    private static final double SPEED_BLOCKS_PER_TICK = 0.5;
    private static final double CIRCLE_RADIUS = 96.0;

    private static final double CHUNK_MARGIN = 2.0;

    private static final class Bot {
        private final ServerPlayer player;
        private final BenchConnection connection;
        private final Mode mode;
        private final double originX;
        private final double originZ;
        private final double phase;
        private double distance;
        private double y;

        private Bot(final ServerPlayer player, final BenchConnection connection, final Mode mode, final double originX, final double originZ, final double phase, final double y) {
            this.y = y;
            this.player = player;
            this.connection = connection;
            this.mode = mode;
            this.originX = originX;
            this.originZ = originZ;
            this.phase = phase;
        }

        private double[] positionAt(final double distance) {
            return switch (this.mode) {
                case IDLE -> new double[] {this.originX, this.originZ};
                case CIRCLE -> {
                    final double angle = this.phase + distance / CIRCLE_RADIUS;
                    yield new double[] {this.originX + Math.cos(angle) * CIRCLE_RADIUS, this.originZ + Math.sin(angle) * CIRCLE_RADIUS};
                }
                case LINE -> new double[] {this.originX + Math.cos(this.phase) * distance, this.originZ + Math.sin(this.phase) * distance};
            };
        }
    }

    private final List<Bot> bots = new ArrayList<>();
    private final Queue<Runnable> ready = new ArrayDeque<>();
    private int requested;

    int count() {
        return this.bots.size();
    }

    void add(final MinecraftServer server, final ServerLevel level, final int amount, final Mode mode, final int spacing) {
        final int offset = this.requested;
        final int side = (int) Math.ceil(Math.sqrt(offset + amount));
        for (int i = offset; i < offset + amount; i++) {
            final double originX = (i % side - side / 2.0) * spacing;
            final double originZ = (i / side - side / 2.0) * spacing;
            this.request(server, level, mode, originX, originZ, i * 2.399963229728653, FLY_Y);
        }
    }

    void addAt(final MinecraftServer server, final ServerLevel level, final double x, final double y, final double z) {
        this.request(server, level, Mode.IDLE, x, z, 0.0, y);
    }

    private void request(final MinecraftServer server, final ServerLevel level, final Mode mode, final double originX, final double originZ,
                         final double phase, final double y) {
        final int index = this.requested++;
        final String name = "bench" + index;
        final GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("mtbench:" + name).getBytes(StandardCharsets.UTF_8)), name);
        final ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        final Bot bot = new Bot(player, new BenchConnection(index), mode, originX, originZ, phase, y);
        final double[] start = bot.positionAt(0.0);
        level.getWorld().getChunkAtAsync(Mth.floor(start[0]) >> 4, Mth.floor(start[1]) >> 4, true)
            .thenAccept(chunk -> this.ready.add(() -> this.bots.add(spawn(server, bot, profile, start))));
    }

    private static Bot spawn(final MinecraftServer server, final Bot bot, final GameProfile profile, final double[] start) {
        final ServerPlayer player = bot.player;
        final BenchConnection connection = bot.connection;
        player.snapTo(start[0], bot.y, start[1], 0.0F, 0.0F);
        server.getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(profile, false));
        server.getConnection().getConnections().add(connection);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        return bot;
    }

    void clear() {
        for (final Bot bot : this.bots) {
            bot.player.connection.disconnect(Component.literal("mtbench clear"));
        }
        this.bots.clear();
        this.ready.clear();
        this.requested = 0;
    }

    void tick() {
        this.bots.removeIf(bot -> bot.player.hasDisconnected());
        for (final Bot bot : this.bots) {
            bot.connection.runPendingTasks();
            move(bot);
            bot.player.connection.handleClientTickEnd(ServerboundClientTickEndPacket.INSTANCE);
        }
        Runnable join;
        while ((join = this.ready.poll()) != null) {
            join.run();
        }
    }

    private static void move(final Bot bot) {
        if (bot.mode == Mode.IDLE) {
            return;
        }
        final double next = bot.distance + SPEED_BLOCKS_PER_TICK;
        final double[] target = bot.positionAt(next);
        final double ground = groundHeight(bot.player.level(), target[0], target[1]);
        if (Double.isNaN(ground)) {
            return;
        }
        final double desiredY = ground + CLEARANCE;
        final double[] current = bot.positionAt(bot.distance);
        if (bot.y < desiredY) {
            bot.y = Math.min(desiredY, bot.y + VERTICAL_SPEED);
            bot.player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(current[0], bot.y, current[1], false, false));
            return;
        }
        bot.distance = next;
        bot.y = Math.max(desiredY, bot.y - VERTICAL_SPEED);
        bot.player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(target[0], bot.y, target[1], false, false));
    }

    private static double groundHeight(final ServerLevel level, final double x, final double z) {
        double highest = level.getMinY();
        for (final double dx : new double[] {-CHUNK_MARGIN, CHUNK_MARGIN}) {
            for (final double dz : new double[] {-CHUNK_MARGIN, CHUNK_MARGIN}) {
                final int blockX = Mth.floor(x + dx);
                final int blockZ = Mth.floor(z + dz);
                final LevelChunk chunk = level.getChunkSource().getChunkAtIfLoadedImmediately(blockX >> 4, blockZ >> 4);
                if (chunk == null) {
                    return Double.NaN;
                }
                highest = Math.max(highest, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX & 15, blockZ & 15) + 1);
            }
        }
        return highest;
    }
}
