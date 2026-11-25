/* Copyright © 2015, Leon Mangler and the SuperVanish contributors. MPL-2.0. */
package de.myzelyam.supervanish.hooks;
import com.earth2me.essentials.Essentials;
import de.myzelyam.api.vanish.PostPlayerHideEvent;
import de.myzelyam.api.vanish.PostPlayerShowEvent;
import de.myzelyam.supervanish.SuperVanish;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.Plugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EssentialsHook extends PluginHook {
    private volatile Essentials essentials;
    private ScheduledTask timer;
    private final Map<UUID, Boolean> previous = new ConcurrentHashMap<>();
    public EssentialsHook(SuperVanish plugin) { super(plugin); }
    @Override public void onPluginEnable(Plugin plugin) {
        essentials = (Essentials) plugin;
        timer = superVanish.getLifecycle().timer(40, () -> {
            for (UUID id : superVanish.getVanishStateMgr().getOnlineVanishedPlayers()) {
                Player p = superVanish.player(id);
                if (p != null) superVanish.getLifecycle().player(p, () -> hide(p));
            }
        });
    }
    private void hide(Player p) {
        Essentials current = essentials;
        if (current == null || !current.isEnabled()) return;
        var user = current.getUser(p);
        if (user == null) return;
        previous.putIfAbsent(p.getUniqueId(), user.isHidden());
        user.setHidden(true);
    }
    @EventHandler public void onVanish(PostPlayerHideEvent event) { hide(event.getPlayer()); }
    @EventHandler public void onReappear(PostPlayerShowEvent event) { cleanupPlayer(event.getPlayer()); }
    @Override public void cleanupPlayer(Player p) {
        Boolean hidden = previous.remove(p.getUniqueId());
        Essentials current = essentials;
        if (current != null && current.isEnabled() && hidden != null) {
            var user = current.getUser(p); if (user != null) user.setHidden(hidden);
        }
    }
    @Override public void onPluginDisable(Plugin plugin) {
        if (timer != null) timer.cancel();
        essentials = null; previous.clear();
    }
}
