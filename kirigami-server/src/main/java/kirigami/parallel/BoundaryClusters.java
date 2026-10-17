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
