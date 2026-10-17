package mtprobe;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProbePlugin extends JavaPlugin implements Listener {

    private final Map<String, Integer> counts = new HashMap<>();
    private boolean inside;
    private int overlaps;
    private int offThread;
    private boolean marker;

    @Override
    public void onEnable() {
        this.getServer().getPluginManager().registerEvents(this, this);
    }

    private void count(final Event event) {
        if (this.inside) {
            this.overlaps++;
        }
        this.inside = true;
        if (!Bukkit.isPrimaryThread()) {
            this.offThread++;
        }
        this.counts.merge(event.getEventName(), 1, Integer::sum);
        this.inside = false;
    }

    @EventHandler
    public void onPreSpawn(final PreCreatureSpawnEvent event) {
        this.count(event);
        if (!Boolean.getBoolean("mtprobe.cancelSpawns")) {
            return;
        }
        if (event.getType() == EntityType.ZOMBIE) {
            event.setCancelled(true);
        } else if (event.getType() == EntityType.SPIDER) {
            event.setShouldAbortSpawn(true);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMove(final InventoryMoveItemEvent event) {
        this.count(event);
    }

    @EventHandler
    public void onDamage(final EntityDamageEvent event) {
        this.count(event);
    }

    @EventHandler
    public void onSpawn(final CreatureSpawnEvent event) {
        this.count(event);
    }

    @EventHandler
    public void onTarget(final EntityTargetEvent event) {
        this.count(event);
    }

    @EventHandler
    public void onItem(final ItemSpawnEvent event) {
        this.count(event);
    }

    @EventHandler
    public void onRedstone(final BlockRedstoneEvent event) {
        this.count(event);
    }

    @EventHandler
    public void onDeath(final EntityDeathEvent event) {
        this.count(event);
        this.marker = !this.marker;
        event.getEntity().getWorld().getBlockAt(0, 300, 0).setType(this.marker ? Material.GLASS : Material.AIR);
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        this.getLogger().info("mtprobe overlaps=" + this.overlaps + " offThread=" + this.offThread + " counts=" + new TreeMap<>(this.counts));
        return true;
    }
}
