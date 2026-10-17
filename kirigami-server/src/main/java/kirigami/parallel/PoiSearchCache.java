package kirigami.parallel;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class PoiSearchCache {

    private static final int MAX_ENTRIES = 8192;
    private static volatile @Nullable List<Holder.Reference<PoiType>> types;

    private final AtomicLong version = new AtomicLong();
    private final Map<Key, Long> emptyAreas = new ConcurrentHashMap<>();

    private record Key(long typeMask, int lowerX, int lowerZ, int upperX, int upperZ) {
    }

    public void changed() {
        this.version.incrementAndGet();
    }

    public long version() {
        return this.version.get();
    }

    public static @Nullable Object key(final Predicate<Holder<PoiType>> typePredicate, final double maxDistanceSquared,
                                       final int lowerX, final int lowerZ, final int upperX, final int upperZ) {
        if (maxDistanceSquared != Double.MAX_VALUE) {
            return null;
        }
        List<Holder.Reference<PoiType>> all = types;
        if (all == null) {
            all = BuiltInRegistries.POINT_OF_INTEREST_TYPE.listElements().toList();
            types = all;
        }
        if (all.size() > Long.SIZE) {
            return null;
        }
        long mask = 0L;
        for (int i = 0; i < all.size(); i++) {
            if (typePredicate.test(all.get(i))) {
                mask |= 1L << i;
            }
        }
        return new Key(mask, lowerX, lowerZ, upperX, upperZ);
    }

    public boolean knownEmpty(final @Nullable Object key, final long version) {
        if (key == null) {
            return false;
        }
        final Long cached = this.emptyAreas.get((Key) key);
        return cached != null && cached == version;
    }

    public void markEmpty(final @Nullable Object key, final long versionBeforeSearch) {
        if (key == null || this.version.get() != versionBeforeSearch) {
            return;
        }
        if (this.emptyAreas.size() >= MAX_ENTRIES) {
            this.emptyAreas.clear();
        }
        this.emptyAreas.put((Key) key, versionBeforeSearch);
    }
}
