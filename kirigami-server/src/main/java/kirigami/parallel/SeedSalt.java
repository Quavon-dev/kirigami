package kirigami.parallel;

import net.minecraft.world.level.levelgen.RandomSupport;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class SeedSalt {

    public static final long VALUE = Long.getLong("kirigami.seedSalt", RandomSupport.generateUniqueSeed());

    private SeedSalt() {
    }
}
