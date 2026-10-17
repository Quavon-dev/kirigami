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
    private static final java.util.concurrent.atomic.AtomicLong PAUSES = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.Map<Object, long[]> SOURCES = new java.util.HashMap<>();
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

    public static long pauses() {
        return PAUSES.get();
    }

    public static boolean insideItem() {
        return BUSY.get(SLOT.get()) != 0;
    }

    public static java.util.Map<Object, Long> sources() {
        EXCLUSIVE.lock();
        try {
            final java.util.Map<Object, Long> copy = new java.util.HashMap<>();
            SOURCES.forEach((source, count) -> copy.put(source, count[0]));
            return copy;
        } finally {
            EXCLUSIVE.unlock();
        }
    }

    public static void resetSources() {
        EXCLUSIVE.lock();
        try {
            SOURCES.clear();
        } finally {
            EXCLUSIVE.unlock();
        }
    }

    public static String describe(final Object source) {
        if (source instanceof Class<?> type && org.bukkit.event.Event.class.isAssignableFrom(type)) {
            final java.util.Set<String> plugins = new java.util.TreeSet<>();
            try {
                final org.bukkit.event.HandlerList handlers = (org.bukkit.event.HandlerList) type.getMethod("getHandlerList").invoke(null);
                for (final org.bukkit.plugin.RegisteredListener listener : handlers.getRegisteredListeners()) {
                    plugins.add(listener.getPlugin().getName());
                }
            } catch (final ReflectiveOperationException | ClassCastException ignored) {
            }
            return type.getSimpleName() + (plugins.isEmpty() ? "" : " " + plugins);
        }
        if (source instanceof Class<?> type) {
            return "mob goal " + type.getName();
        }
        return source.toString();
    }

    public static <T> T exclusive(final Object source, final Supplier<T> action) {
        if (!insideItem()) {
            return action.get();
        }
        final CellContext context = CellContext.detach();
        exitItem();
        EXCLUSIVE.lock();
        PAUSES.incrementAndGet();
        SOURCES.computeIfAbsent(source, unused -> new long[1])[0]++;
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

    public static void exclusiveRun(final Object source, final Runnable action) {
        exclusive(source, () -> {
            action.run();
            return null;
        });
    }
}
