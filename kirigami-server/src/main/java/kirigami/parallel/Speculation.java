package kirigami.parallel;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class Speculation {

    public static final class Abort extends RuntimeException {
        private Abort() {
            super(null, null, false, false);
        }
    }

    private static final Abort ABORT = new Abort();
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private Speculation() {
    }

    public static boolean isActive() {
        return ACTIVE.get();
    }

    public static void abortIfActive() {
        if (ACTIVE.get()) {
            throw ABORT;
        }
    }

    static void enter() {
        ACTIVE.set(Boolean.TRUE);
    }

    static void exit() {
        ACTIVE.set(Boolean.FALSE);
    }
}
