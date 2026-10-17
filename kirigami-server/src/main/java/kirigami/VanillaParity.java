package kirigami;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class VanillaParity {

    public static final boolean PAPER_CHEST_DELAY = Boolean.getBoolean("kirigami.paper.chestDelay");
    public static final boolean PAPER_PATHFINDING_BACKOFF = Boolean.getBoolean("kirigami.paper.pathfindingBackoff");
    public static final boolean PAPER_FLUID_RANDOM_TICK_FIX = Boolean.getBoolean("kirigami.paper.fluidRandomTickFix");
    public static final boolean PAPER_RAID_BANNER_GRIEFING = Boolean.getBoolean("kirigami.paper.raidBannerGriefing");
    public static final boolean CRAFTBUKKIT_RAID_OMEN_FIX = Boolean.getBoolean("kirigami.craftbukkit.raidOmenFix");
    public static final boolean PAPER_BOAT_RISE_COLLISION = Boolean.getBoolean("kirigami.paper.boatRiseCollision");

    private VanillaParity() {
    }
}
