/* Copyright © 2015, Leon Mangler and the SuperVanish contributors. MPL-2.0. */
package de.myzelyam.supervanish.features;
import de.myzelyam.api.vanish.PostPlayerHideEvent;
import de.myzelyam.api.vanish.PostPlayerShowEvent;
import de.myzelyam.supervanish.SuperVanish;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NoPush extends Feature {
    private final Map<UUID, Boolean> previous = new ConcurrentHashMap<>();
    public NoPush(SuperVanish plugin) { super(plugin); }
    @Override public boolean isActive() { return plugin.getSettings().getBoolean("InvisibilityFeatures.DisablePush"); }
    public void setCantPush(Player player) { previous.putIfAbsent(player.getUniqueId(), player.isCollidable()); player.setCollidable(false); }
    public void setCanPush(Player player) { cleanupPlayer(player); }
    @EventHandler public void onVanish(PostPlayerHideEvent event) { setCantPush(event.getPlayer()); }
    @EventHandler public void onReappear(PostPlayerShowEvent event) { cleanupPlayer(event.getPlayer()); }
    @Override public void cleanupPlayer(Player player) {
        Boolean value = previous.remove(player.getUniqueId()); if (value != null) player.setCollidable(value);
    }
    @Override public void onDisable() { previous.clear(); }
}
