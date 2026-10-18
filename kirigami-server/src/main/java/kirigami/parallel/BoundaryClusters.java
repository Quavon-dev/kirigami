package kirigami.parallel;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class BoundaryClusters {

    private static final int LINK = 2;

    private BoundaryClusters() {
    }

    static Set<TickingBlockEntity> crossingCells(final List<TickingBlockEntity> items, final int cellShiftBlocks) {
        final Set<TickingBlockEntity> result = new ReferenceOpenHashSet<>();
        final int cellSize = 1 << cellShiftBlocks;
        final Long2IntOpenHashMap index = new Long2IntOpenHashMap(items.size());
        index.defaultReturnValue(-1);
        for (int i = 0; i < items.size(); i++) {
            index.put(items.get(i).getPos().asLong(), i);
        }
        final boolean[] marked = new boolean[items.size()];
        final IntArrayList queue = new IntArrayList();
        for (int i = 0; i < items.size(); i++) {
            final BlockPos pos = items.get(i).getPos();
            if (!nearEdge(pos.getX(), cellSize) && !nearEdge(pos.getZ(), cellSize)) {
                continue;
            }
            final long cell = cellOf(pos.getX(), pos.getZ(), cellShiftBlocks);
            final int self = i;
            forEachNeighbour(pos, index, neighbour -> {
                final BlockPos other = items.get(neighbour).getPos();
                if (cellOf(other.getX(), other.getZ(), cellShiftBlocks) != cell) {
                    mark(self, marked, queue);
                    mark(neighbour, marked, queue);
                }
            });
        }
        while (!queue.isEmpty()) {
            final int current = queue.removeInt(queue.size() - 1);
            forEachNeighbour(items.get(current).getPos(), index, neighbour -> mark(neighbour, marked, queue));
        }
        for (int i = 0; i < items.size(); i++) {
            if (marked[i]) {
                result.add(items.get(i));
            }
        }
        return result;
    }

    static Set<net.minecraft.world.ticks.ScheduledTick<?>> spanningClusters(final List<? extends net.minecraft.world.ticks.ScheduledTick<?>> ticks, final int cellShiftBlocks,
                                                                          final int linkDistance) {
        final int bucketShift = 4;
        final int[] bucketOfTick = new int[ticks.size()];
        final int linkBuckets = (linkDistance + (1 << bucketShift) - 1) >> bucketShift;
        final Long2IntOpenHashMap buckets = new Long2IntOpenHashMap();
        buckets.defaultReturnValue(-1);
        final it.unimi.dsi.fastutil.longs.LongArrayList keys = new it.unimi.dsi.fastutil.longs.LongArrayList();
        for (int i = 0; i < ticks.size(); i++) {
            final BlockPos pos = ticks.get(i).pos();
            final long key = cellOf(pos.getX(), pos.getZ(), bucketShift);
            int bucket = buckets.get(key);
            if (bucket < 0) {
                bucket = keys.size();
                buckets.put(key, bucket);
                keys.add(key);
            }
            bucketOfTick[i] = bucket;
        }
        final int[] parent = new int[keys.size()];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < keys.size(); i++) {
            final int bucketX = (int) (keys.getLong(i) >> 32);
            final int bucketZ = (int) keys.getLong(i);
            for (int dx = -linkBuckets; dx <= linkBuckets; dx++) {
                for (int dz = -linkBuckets; dz <= linkBuckets; dz++) {
                    final int other = buckets.get(cellOf(bucketX + dx, bucketZ + dz, 0));
                    if (other > i) {
                        parent[find(parent, other)] = find(parent, i);
                    }
                }
            }
        }
        final boolean[] rootSpans = new boolean[parent.length];
        final long[] rootCell = new long[parent.length];
        final boolean[] rootSeen = new boolean[parent.length];
        for (int i = 0; i < parent.length; i++) {
            final int root = find(parent, i);
            final long cell = cellOf((int) (keys.getLong(i) >> 32), (int) keys.getLong(i), cellShiftBlocks - bucketShift);
            if (!rootSeen[root]) {
                rootSeen[root] = true;
                rootCell[root] = cell;
            }
            rootSpans[root] |= rootCell[root] != cell;
        }
        final Set<net.minecraft.world.ticks.ScheduledTick<?>> result = new ReferenceOpenHashSet<>();
        for (int i = 0; i < ticks.size(); i++) {
            if (rootSpans[find(parent, bucketOfTick[i])]) {
                result.add(ticks.get(i));
            }
        }
        return result;
    }

    private static int find(final int[] parent, int index) {
        while (parent[index] != index) {
            parent[index] = parent[parent[index]];
            index = parent[index];
        }
        return index;
    }

    private static void mark(final int index, final boolean[] marked, final IntArrayList queue) {
        if (!marked[index]) {
            marked[index] = true;
            queue.add(index);
        }
    }

    private static boolean nearEdge(final int coordinate, final int cellSize) {
        final int local = Math.floorMod(coordinate, cellSize);
        return local < LINK || local >= cellSize - LINK;
    }

    private static long cellOf(final int x, final int z, final int cellShiftBlocks) {
        return ((long) (x >> cellShiftBlocks) << 32) | ((z >> cellShiftBlocks) & 0xFFFFFFFFL);
    }

    private static void forEachNeighbour(final BlockPos pos, final Long2IntOpenHashMap index, final java.util.function.IntConsumer action) {
        for (int dx = -LINK; dx <= LINK; dx++) {
            for (int dy = -LINK; dy <= LINK; dy++) {
                for (int dz = -LINK; dz <= LINK; dz++) {
                    if ((dx | dy | dz) == 0) {
                        continue;
                    }
                    final int neighbour = index.get(BlockPos.asLong(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz));
                    if (neighbour >= 0) {
                        action.accept(neighbour);
                    }
                }
            }
        }
    }
}
