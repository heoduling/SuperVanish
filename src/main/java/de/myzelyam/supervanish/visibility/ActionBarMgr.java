/* Copyright © 2015, Leon Mangler and the SuperVanish contributors. MPL-2.0. */
package de.myzelyam.supervanish.visibility;
import de.myzelyam.supervanish.SuperVanish;
import org.bukkit.entity.Player;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ActionBarMgr {
    private final Set<UUID> actionBars = ConcurrentHashMap.newKeySet();
    public ActionBarMgr(SuperVanish plugin) {
        plugin.getLifecycle().timer(40, () -> {
            for (UUID uuid : actionBars) {
                Player player = plugin.player(uuid);
                if (player != null) plugin.getLifecycle().player(player, () -> {
                    if (actionBars.contains(uuid)) player.sendActionBar(plugin.replacePlaceholders(plugin.getMessage("ActionBarMessage"), player));
                });
            }
        });
    }
    public void addActionBar(Player player) { actionBars.add(player.getUniqueId()); }
    public void removeActionBar(Player player) {
        if (actionBars.remove(player.getUniqueId())) player.sendActionBar("");
    }
}
