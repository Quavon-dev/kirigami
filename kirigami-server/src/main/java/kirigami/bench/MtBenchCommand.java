package kirigami.bench;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class MtBenchCommand {

    public static final String DESCRIPTION = "Benchmark tooling for multithreading work";
    private static final Path RESULTS = Path.of("mtbench");

    private final BenchPlayers players = new BenchPlayers();
    private final TickRecorder recorder = new TickRecorder();
    private final Lab lab = new Lab();
    private boolean tickableRegistered;

    private MtBenchCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create() {
        final MtBenchCommand command = new MtBenchCommand();
        return Commands.literal("mtbench")
            .requires(source -> source.getSender().hasPermission("kirigami.bench"))
            .then(Commands.literal("bots")
                .then(Commands.literal("add")
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 1000))
                        .then(Commands.argument("mode", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                for (final BenchPlayers.Mode mode : BenchPlayers.Mode.values()) {
                                    builder.suggest(mode.name().toLowerCase(Locale.ROOT));
                                }
                                return builder.buildFuture();
                            })
                            .then(Commands.argument("spacing", IntegerArgumentType.integer(0, 10000))
                                .executes(command::addBots)))))
                .then(Commands.literal("clear").executes(command::clearBots))
                .then(Commands.literal("at")
                    .then(Commands.argument("x", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                        .then(Commands.argument("y", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                            .then(Commands.argument("z", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                                .executes(command::addBotAt))))))
            .then(Commands.literal("lab")
                .then(Commands.literal("build").executes(ctx -> command.labAction(ctx, "build")))
                .then(Commands.literal("observers").executes(ctx -> command.labAction(ctx, "observers")))
                .then(Commands.literal("start").executes(ctx -> command.labAction(ctx, "start")))
                .then(Commands.literal("report").executes(ctx -> command.labAction(ctx, "report"))))
            .then(Commands.literal("regionstats").executes(ctx -> {
                ctx.getSource().getSender().sendMessage(Component.text("mtbench regionstats " + kirigami.parallel.RegionTicking.STATS.format()));
                return Command.SINGLE_SUCCESS;
            }))
            .then(Commands.literal("spawnstats").executes(ctx -> {
                ctx.getSource().getSender().sendMessage(Component.text("mtbench spawnstats workers=" + kirigami.parallel.TickWorkers.workers()
                    + " " + kirigami.parallel.ParallelSpawning.STATS.format()));
                return Command.SINGLE_SUCCESS;
            }))
            .then(Commands.literal("record")
                .then(Commands.literal("start")
                    .then(Commands.argument("name", StringArgumentType.word()).executes(command::startRecording)))
                .then(Commands.literal("stop").executes(command::stopRecording)))
            .build();
    }

    private void ensureTicking() {
        if (this.tickableRegistered) {
            return;
        }
        final MinecraftServer server = MinecraftServer.getServer();
        server.addTickable(() -> {
            this.players.tick();
            this.lab.tick(server.overworld());
            this.recorder.tick(server);
        });
        this.tickableRegistered = true;
    }

    private int labAction(final CommandContext<CommandSourceStack> ctx, final String action) {
        this.ensureTicking();
        final MinecraftServer server = MinecraftServer.getServer();
        final net.minecraft.server.level.ServerLevel level = server.overworld();
        final String result = switch (action) {
            case "build" -> {
                this.lab.build(level);
                yield "built";
            }
            case "observers" -> {
                for (final int[] pos : Lab.observerPositions()) {
                    this.players.addAt(server, level, pos[0] + 0.5, pos[1], pos[2] + 0.5);
                }
                yield "observers requested";
            }
            case "start" -> {
                this.lab.start(level);
                yield "started";
            }
            default -> this.lab.report(level);
        };
        ctx.getSource().getSender().sendMessage(Component.text("mtbench lab " + result));
        return Command.SINGLE_SUCCESS;
    }

    private int addBots(final CommandContext<CommandSourceStack> ctx) {
        final BenchPlayers.Mode mode;
        try {
            mode = BenchPlayers.Mode.valueOf(StringArgumentType.getString(ctx, "mode").toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            ctx.getSource().getSender().sendMessage(Component.text("Unknown mode, use idle, circle or line"));
            return 0;
        }
        this.ensureTicking();
        final MinecraftServer server = MinecraftServer.getServer();
        this.players.add(server, server.overworld(), IntegerArgumentType.getInteger(ctx, "count"), mode, IntegerArgumentType.getInteger(ctx, "spacing"));
        ctx.getSource().getSender().sendMessage(Component.text("Bench bots: " + this.players.count()));
        return Command.SINGLE_SUCCESS;
    }

    private int addBotAt(final CommandContext<CommandSourceStack> ctx) {
        this.ensureTicking();
        final MinecraftServer server = MinecraftServer.getServer();
        this.players.addAt(server, server.overworld(), com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "x"),
            com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "y"), com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "z"));
        ctx.getSource().getSender().sendMessage(Component.text("Bench bot requested"));
        return Command.SINGLE_SUCCESS;
    }

    private int clearBots(final CommandContext<CommandSourceStack> ctx) {
        this.players.clear();
        ctx.getSource().getSender().sendMessage(Component.text("Bench bots cleared"));
        return Command.SINGLE_SUCCESS;
    }

    private int startRecording(final CommandContext<CommandSourceStack> ctx) {
        this.ensureTicking();
        this.recorder.start(StringArgumentType.getString(ctx, "name"));
        ctx.getSource().getSender().sendMessage(Component.text("Recording tick times"));
        return Command.SINGLE_SUCCESS;
    }

    private int stopRecording(final CommandContext<CommandSourceStack> ctx) {
        if (!this.recorder.recording()) {
            ctx.getSource().getSender().sendMessage(Component.text("Not recording"));
            return 0;
        }
        try {
            ctx.getSource().getSender().sendMessage(Component.text("mtbench " + this.recorder.stop(RESULTS).format()));
            return Command.SINGLE_SUCCESS;
        } catch (final IOException e) {
            MinecraftServer.LOGGER.error("Failed to write benchmark results", e);
            ctx.getSource().getSender().sendMessage(Component.text("Failed to write results: " + e.getMessage()));
            return 0;
        }
    }
}
