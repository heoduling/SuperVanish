/* Copyright © 2015, Leon Mangler and the SuperVanish contributors. MPL-2.0. */
package de.myzelyam.supervanish.visibility.hiders;
import de.myzelyam.supervanish.SuperVanish;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public abstract class PlayerHider implements Listener {
    protected final SuperVanish plugin;
    private final Map<UUID, Set<UUID>> hiddenFrom = new ConcurrentHashMap<>();
    public PlayerHider(SuperVanish plugin) {
        this.plugin = plugin; plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    public abstract String getName();
    public boolean isHidden(Player player, Player viewer) { return isHidden(player.getUniqueId(), viewer); }
    public boolean isHidden(UUID id, Player viewer) {
        Set<UUID> viewers = hiddenFrom.get(id);
        return !id.equals(viewer.getUniqueId()) && viewers != null && viewers.contains(viewer.getUniqueId());
    }
    public boolean isHidden(String name, Player viewer) {
        Player target = plugin.player(name);
        return target != null && isHidden(target.getUniqueId(), viewer);
    }
    public boolean setHidden(Player player, Player viewer, boolean hidden) {
        UUID id = player.getUniqueId(), viewing = viewer.getUniqueId();
        if (viewing.equals(id)) return false;
        boolean[] changed = {false};
        hiddenFrom.compute(id, (key, viewers) -> {
            // Re-check at publication: target quit can run while this viewer's task is queued.
            // Session removal precedes forget(), which uses this same target-key operation.
            if (!isCurrentSession(player) || !isCurrentSession(viewer)) return viewers;
            if (viewers == null && !hidden) return null;
            if (viewers == null) viewers = ConcurrentHashMap.newKeySet();
            changed[0] = hidden ? viewers.add(viewing) : viewers.remove(viewing);
            return viewers.isEmpty() ? null : viewers;
        });
        return changed[0];
    }
    protected final boolean isCurrentSession(Player player) {
        return plugin.player(player.getUniqueId()) == player;
    }
    public Set<Player> getHiddenPlayerKeys() {
        Set<Player> targets = new HashSet<>();
        for (UUID id : hiddenFrom.keySet()) { Player player = plugin.player(id); if (player != null) targets.add(player); }
        return Set.copyOf(targets);
    }
    @EventHandler(priority = EventPriority.MONITOR) public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        // The configurable QuitListener may also run at MONITOR after this listener.
        // It ends the session and calls forget; never clean a still-current or replacement session here.
        if (plugin.player(id) == null) forget(id);
    }
    public void forget(UUID id) {
        hiddenFrom.computeIfPresent(id, (key, viewers) -> plugin.player(id) == null ? null : viewers);
        for (UUID target : Set.copyOf(hiddenFrom.keySet())) hiddenFrom.computeIfPresent(target, (key, viewers) -> {
            if (plugin.player(id) != null) return viewers; // An old quit must not erase a replacement session's edges.
            viewers.remove(id); return viewers.isEmpty() ? null : viewers;
        });
    }
    public void clear() { hiddenFrom.clear(); }
}
