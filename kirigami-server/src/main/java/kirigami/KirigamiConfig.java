package kirigami;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class KirigamiConfig {

    private static final String FILE_NAME = "kirigami.yml";
    private static final String HEADER = """
        Kirigami settings. A -Dkirigami.* JVM flag with the same meaning overrides the value here.
        Workers: -1 uses one thread per CPU core minus one, 0 disables parallel ticking.
        paper-behaviour: true restores Paper's behaviour instead of vanilla's for that mechanic.
        Docs: https://github.com/Quavon-dev/kirigami#settings""";

    private record Setting(String path, String property, Object defaultValue) {
    }

    private static final List<Setting> SETTINGS = List.of(
        new Setting("parallel.region-ticking", "kirigami.regionTicking", true),
        new Setting("parallel.phases.entities", "kirigami.region.entities", true),
        new Setting("parallel.phases.random-ticks", "kirigami.region.chunks", true),
        new Setting("parallel.phases.block-entities", "kirigami.region.blockEntities", true),
        new Setting("parallel.phases.scheduled-ticks", "kirigami.region.scheduledTicks", true),
        new Setting("parallel.phases.entity-tracker", "kirigami.region.tracker", true),
        new Setting("parallel.spawning", "kirigami.parallelSpawning", true),
        new Setting("parallel.region-workers", "kirigami.regionWorkers", -1),
        new Setting("parallel.spawn-workers", "kirigami.tickWorkers", -1),
        new Setting("parallel.min-parallel-items", "kirigami.minParallelItems", 256),
        new Setting("parallel.min-speculative-jobs", "kirigami.minSpeculativeJobs", 512),
        new Setting("plugins.serial-with-plugins", "kirigami.serialWithPlugins", false),
        new Setting("paper-behaviour.chest-delay", "kirigami.paper.chestDelay", false),
        new Setting("paper-behaviour.pathfinding-backoff", "kirigami.paper.pathfindingBackoff", false),
        new Setting("paper-behaviour.fluid-random-tick-fix", "kirigami.paper.fluidRandomTickFix", false),
        new Setting("paper-behaviour.raid-banner-griefing", "kirigami.paper.raidBannerGriefing", false),
        new Setting("paper-behaviour.boat-rise-collision", "kirigami.paper.boatRiseCollision", false),
        new Setting("paper-behaviour.raid-omen-fix", "kirigami.craftbukkit.raidOmenFix", false)
    );

    private KirigamiConfig() {
    }

    public static void load(final File directory) {
        final File file = new File(directory, FILE_NAME);
        final YamlConfiguration yaml = new YamlConfiguration();
        if (file.exists()) {
            try {
                yaml.load(file);
            } catch (final IOException | InvalidConfigurationException e) {
                throw new IllegalStateException("Could not read " + file + ": " + e.getMessage(), e);
            }
        }
        boolean changed = !file.exists();
        for (final Setting setting : SETTINGS) {
            if (!yaml.contains(setting.path())) {
                yaml.set(setting.path(), setting.defaultValue());
                changed = true;
            }
            final Object value = yaml.get(setting.path());
            if (value == null || value.getClass() != setting.defaultValue().getClass()) {
                throw new IllegalStateException("Invalid value for " + setting.path() + " in " + file + ": expected "
                    + (setting.defaultValue() instanceof Boolean ? "true or false" : "a whole number") + ", found " + value);
            }
            if (System.getProperty(setting.property()) == null && !(value instanceof Integer number && number < 0)) {
                System.setProperty(setting.property(), value.toString());
            }
        }
        if (changed) {
            yaml.options().setHeader(HEADER.lines().toList());
            try {
                directory.mkdirs();
                yaml.save(file);
            } catch (final IOException e) {
                throw new IllegalStateException("Could not write " + file + ": " + e.getMessage(), e);
            }
        }
    }
}
