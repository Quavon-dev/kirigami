package kirigami.parallel;

import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class PluginGuard {

    private static final boolean SERIAL_WITH_PLUGINS = Boolean.getBoolean("kirigami.serialWithPlugins");
    private static final int RECHECK_TICKS = 600;

    private static long lastCheckTick = Long.MIN_VALUE;
    private static boolean pluginsPresent;
    private static boolean warned;

    private PluginGuard() {
    }

    public static boolean parallelAllowed() {
        if (!SERIAL_WITH_PLUGINS) {
            return true;
        }
        final MinecraftServer server = MinecraftServer.getServer();
        final long tick = server.getTickCount();
        if (lastCheckTick == Long.MIN_VALUE || tick - lastCheckTick >= RECHECK_TICKS || tick < lastCheckTick) {
            lastCheckTick = tick;
            pluginsPresent = server.server.getPluginManager().getPlugins().length > 0;
            if (pluginsPresent && !warned) {
                warned = true;
                MinecraftServer.LOGGER.warn("Plugins are installed and -Dkirigami.serialWithPlugins=true is set: parallel ticking is disabled.");
            }
        }
        return !pluginsPresent;
    }
}
