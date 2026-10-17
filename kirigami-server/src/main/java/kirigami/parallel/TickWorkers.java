package kirigami.parallel;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class TickWorkers {

    private static final int WORKERS = Integer.getInteger(
        "kirigami.tickWorkers", Math.max(0, Runtime.getRuntime().availableProcessors() - 1)
    );
    private static final ExecutorService POOL = WORKERS == 0 ? null : Executors.newFixedThreadPool(WORKERS, runnable -> {
        final Thread thread = new Thread(runnable, "Kirigami Tick Worker");
        thread.setDaemon(true);
        return thread;
    });

    private TickWorkers() {
    }

    public static int workers() {
        return WORKERS;
    }

    public static <T> void forEachSpeculative(final List<T> items, final int batchSize, final Consumer<T> task) {
        forRangeSpeculative(items.size(), batchSize, index -> task.accept(items.get(index)));
    }

    public static void forRangeSpeculative(final int size, final int batchSize, final IntConsumer task) {
        final AtomicInteger next = new AtomicInteger();
        final Runnable drain = () -> {
            Speculation.enter();
            try {
                int start;
                while ((start = next.getAndAdd(batchSize)) < size) {
                    final int end = Math.min(size, start + batchSize);
                    for (int i = start; i < end; i++) {
                        task.accept(i);
                    }
                }
            } finally {
                Speculation.exit();
            }
        };
        if (POOL == null || size <= batchSize) {
            drain.run();
            return;
        }
        final int helpers = Math.min(WORKERS, (size + batchSize - 1) / batchSize - 1);
        final Future<?>[] futures = new Future<?>[helpers];
        for (int i = 0; i < helpers; i++) {
            futures[i] = POOL.submit(drain);
        }
        drain.run();
        for (final Future<?> future : futures) {
            try {
                future.get();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for tick workers", e);
            } catch (final ExecutionException e) {
                throw new IllegalStateException("Tick worker failed", e.getCause());
            }
        }
    }
}
