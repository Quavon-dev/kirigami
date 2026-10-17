package kirigami;

import com.destroystokyo.paper.util.VersionFetcher;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class KirigamiVersionFetcher implements VersionFetcher {

    @Override
    public long getCacheTime() {
        return 720000;
    }

    @Override
    public Component getVersionMessage() {
        return Component.text("Kirigami does not check for updates.");
    }
}
