package kirigami;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.ServerBuildInfo;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Locale;
import kirigami.parallel.CellContext;
import kirigami.parallel.ParallelSpawning;
import kirigami.parallel.PluginGuard;
import kirigami.parallel.RegionTicking;
import kirigami.parallel.TickWorkers;
import kirigami.parallel.WorldPause;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Bukkit;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class KirigamiCommand {

    public static final String DESCRIPTION = "Shows how Kirigami spreads the server tick across threads";
    private static final String[] PHASES = {"Entities", "Random ticks", "Block entities"};

    private long baseTick;
    private long[] baseRegion = RegionTicking.STATS.snapshot();
    private long[] baseSpawn = ParallelSpawning.STATS.snapshot();
    private long basePauses = WorldPause.pauses();
    private long baseLoads = CellContext.blockingLoads();
    private java.util.Map<String, Long> baseReasons = WorldPause.reasons();

    private KirigamiCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create() {
        final KirigamiCommand command = new KirigamiCommand();
        return Commands.literal("kirigami")
            .requires(source -> source.getSender().hasPermission("kirigami.command"))
            .executes(command::status)
            .then(Commands.literal("reset").executes(command::reset))
            .build();
    }

    private int reset(final CommandContext<CommandSourceStack> ctx) {
        this.baseTick = MinecraftServer.getServer().getTickCount();
        this.baseRegion = RegionTicking.STATS.snapshot();
        this.baseSpawn = ParallelSpawning.STATS.snapshot();
        this.basePauses = WorldPause.pauses();
        this.baseLoads = CellContext.blockingLoads();
        this.baseReasons = WorldPause.reasons();
        ctx.getSource().getSender().sendMessage(Component.text("Kirigami statistics reset.", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private int status(final CommandContext<CommandSourceStack> ctx) {
        final long ticks = Math.max(1, MinecraftServer.getServer().getTickCount() - this.baseTick);
        final long[] region = RegionTicking.STATS.snapshot();
        final long[] spawn = ParallelSpawning.STATS.snapshot();
        final StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT, "Kirigami %s: %.1f TPS, %.1f ms per tick%n",
            ServerBuildInfo.buildInfo().asString(ServerBuildInfo.StringRepresentation.VERSION_SIMPLE), Bukkit.getTPS()[0], Bukkit.getAverageTickTime()));
        text.append(String.format(Locale.ROOT, "Region ticking %s, spawning %s, entity tracker %s, %s%n",
            onOff(RegionTicking.ENABLED), onOff(ParallelSpawning.ENABLED), onOff(RegionTicking.trackerEnabled()),
            PluginGuard.parallelAllowed() ? "plugins do not limit it" : "all serial because plugins are installed (serial-with-plugins)"));
        text.append(String.format(Locale.ROOT, "Workers: %d region, %d spawning. Averages per tick since start or the last /kirigami reset (%d ticks):%n",
            RegionTicking.workers(), TickWorkers.workers(), ticks));
        for (int i = 0; i < PHASES.length; i++) {
            text.append(String.format(Locale.ROOT, "  %s: %.0f parallel, %.0f serial%s%n", PHASES[i],
                per(region[2 * i] - this.baseRegion[2 * i], ticks), per(region[2 * i + 1] - this.baseRegion[2 * i + 1], ticks),
                RegionTicking.phaseEnabled(i) ? "" : " (phase disabled)"));
        }
        text.append(String.format(Locale.ROOT, "  Cell runs across all phases: %.1f, block entities kept serial at cell borders: %.1f%n",
            per(region[6] - this.baseRegion[6], ticks), per(region[7] - this.baseRegion[7], ticks)));
        final long searches = spawn[0] - this.baseSpawn[0];
        final long speculated = spawn[1] - this.baseSpawn[1];
        text.append(String.format(Locale.ROOT, "  Spawn searches: %.0f, %.1f%% precomputed in parallel, %d mismatches%n",
            per(searches, ticks), speculated == 0 ? 0.0 : 100.0 * (spawn[2] - this.baseSpawn[2]) / speculated, spawn[5] - this.baseSpawn[5]));
        text.append(String.format(Locale.ROOT, "  World pauses (plugin code, blocking chunk loads): %.2f, blocking loads in total: %d",
            per(WorldPause.pauses() - this.basePauses, ticks), CellContext.blockingLoads() - this.baseLoads));
        WorldPause.reasons().entrySet().stream()
            .map(entry -> java.util.Map.entry(entry.getKey(), entry.getValue() - this.baseReasons.getOrDefault(entry.getKey(), 0L)))
            .filter(entry -> entry.getValue() > 0)
            .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .forEach(entry -> text.append(String.format(Locale.ROOT, "%n    %s: %.3f per tick", entry.getKey(), per(entry.getValue(), ticks))));
        ctx.getSource().getSender().sendMessage(Component.text(text.toString()));
        return Command.SINGLE_SUCCESS;
    }

    private static double per(final long value, final long ticks) {
        return (double) value / ticks;
    }

    private static String onOff(final boolean enabled) {
        return enabled ? "on" : "off";
    }
}
