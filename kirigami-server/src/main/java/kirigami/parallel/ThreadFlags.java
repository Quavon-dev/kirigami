package kirigami.parallel;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ThreadFlags {

    public static final int SKIP_PULL_EVENT = 0;
    public static final int SKIP_PUSH_EVENT = 1;
    public static final int IGNORE_BLOCK_ENTITY_UPDATES = 2;

    private static final ThreadLocal<boolean[]> FLAGS = ThreadLocal.withInitial(() -> new boolean[3]);

    private ThreadFlags() {
    }

    public static boolean get(final int flag) {
        return FLAGS.get()[flag];
    }

    public static void set(final int flag, final boolean value) {
        FLAGS.get()[flag] = value;
    }
}
