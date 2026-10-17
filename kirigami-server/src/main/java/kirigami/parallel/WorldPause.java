package kirigami.parallel;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class WorldPause {

    private static final int STRIDE = 16;
    private static final int MAX_THREADS = 512;
    private static final AtomicIntegerArray BUSY = new AtomicIntegerArray(MAX_THREADS * STRIDE);
    private static final AtomicInteger NEXT_SLOT = new AtomicInteger();
    private static final ThreadLocal<Integer> SLOT = ThreadLocal.withInitial(WorldPause::claimSlot);
    private static final ReentrantLock EXCLUSIVE = new ReentrantLock();
    private static volatile boolean requested;

    private WorldPause() {
    }

    private static int claimSlot() {
        final int slot = NEXT_SLOT.getAndIncrement();
        if (slot >= MAX_THREADS) {
            throw new IllegalStateException("Too many parallel tick threads: " + slot);
        }
        return slot * STRIDE;
    }

    static void enterItem() {
        final int slot = SLOT.get();
        while (true) {
            BUSY.set(slot, 1);
            if (!requested) {
                return;
            }
            BUSY.set(slot, 0);
            while (requested) {
                Thread.onSpinWait();
            }
        }
    }

    static void exitItem() {
        BUSY.set(SLOT.get(), 0);
    }

    public static boolean insideItem() {
        return BUSY.get(SLOT.get()) != 0;
    }

    public static <T> T exclusive(final Supplier<T> action) {
        if (!insideItem()) {
            return action.get();
        }
        final CellContext context = CellContext.detach();
        exitItem();
        EXCLUSIVE.lock();
        try {
            requested = true;
            final int slots = Math.min(NEXT_SLOT.get(), MAX_THREADS);
            for (int i = 0; i < slots; i++) {
                while (BUSY.get(i * STRIDE) != 0) {
                    Thread.onSpinWait();
                }
            }
            return action.get();
        } finally {
            requested = false;
            EXCLUSIVE.unlock();
            enterItem();
            CellContext.attach(context);
        }
    }

    public static void exclusiveRun(final Runnable action) {
        exclusive(() -> {
            action.run();
            return null;
        });
    }
}
