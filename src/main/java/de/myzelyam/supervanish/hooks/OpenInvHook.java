/* Copyright © 2015, Leon Mangler and the SuperVanish contributors. MPL-2.0. */
package de.myzelyam.supervanish.hooks;
import com.lishid.openinv.IOpenInv;
import de.myzelyam.api.vanish.PostPlayerHideEvent;
import de.myzelyam.api.vanish.PostPlayerShowEvent;
import de.myzelyam.supervanish.SuperVanish;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.Inventory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OpenInvHook extends PluginHook {
    private final Map<UUID, Boolean> previous = new ConcurrentHashMap<>();
    private final Map<UUID, Inventory> opened = new ConcurrentHashMap<>();
    public OpenInvHook(SuperVanish plugin) { super(plugin); }
    @EventHandler public void onVanish(PostPlayerHideEvent event) {
        Player p = event.getPlayer();
        IOpenInv openInv = (IOpenInv) plugin;
        previous.putIfAbsent(p.getUniqueId(), openInv.getSilentContainerStatus(p));
        if (p.hasPermission("sv.silentchest")) openInv.setSilentContainerStatus(p, true);
    }
    @EventHandler public void onReappear(PostPlayerShowEvent event) { cleanupPlayer(event.getPlayer()); }
    @Override public void cleanupPlayer(Player p) {
        Inventory inventory = opened.remove(p.getUniqueId());
        if (inventory != null && p.getOpenInventory().getTopInventory() == inventory) p.closeInventory();
        Boolean value = previous.remove(p.getUniqueId());
        if (value != null && plugin != null && plugin.isEnabled()) ((IOpenInv) plugin).setSilentContainerStatus(p, value);
    }
    public boolean openPlayerInventory(Player vanished, Player target) {
        IOpenInv openInv = (IOpenInv) plugin;
        superVanish.getLifecycle().player(target, () -> {
            try {
            var inventory = openInv.getSpecialInventory(target, true);
            superVanish.getLifecycle().player(vanished, () -> {
                if (!superVanish.getVanishStateMgr().isVanished(vanished.getUniqueId())) return;
                openInv.openInventory(vanished, inventory);
                opened.put(vanished.getUniqueId(), vanished.getOpenInventory().getTopInventory());
            });
            } catch (InstantiationException failure) { throw new IllegalStateException(failure); }
        });
        return true;
    }
    @Override public void onPluginDisable(org.bukkit.plugin.Plugin dependency) { previous.clear(); opened.clear(); }
}
