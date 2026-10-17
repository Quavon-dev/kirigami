package kirigami.parallel;

import ca.spottedleaf.moonrise.common.util.ThreadUnsafeRandom;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.BitRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class LevelRandom implements BitRandomSource {

    private final Level level;
    private final ThreadUnsafeRandom base;

    public LevelRandom(final Level level, final long seed) {
        this.level = level;
        this.base = new ThreadUnsafeRandom(seed);
    }

    private BitRandomSource target() {
        final CellContext context = CellContext.currentFor(this.level);
        return context == null ? this.base : (BitRandomSource) context.random();
    }

    @Override
    public int next(final int bits) {
        return this.target().next(bits);
    }

    @Override
    public RandomSource fork() {
        return this.target().fork();
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return this.target().forkPositional();
    }

    @Override
    public void setSeed(final long seed) {
        this.target().setSeed(seed);
    }

    @Override
    public double nextGaussian() {
        return this.target().nextGaussian();
    }
}
