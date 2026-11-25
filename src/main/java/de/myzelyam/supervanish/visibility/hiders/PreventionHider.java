/* Copyright © 2015, Leon Mangler and the SuperVanish contributors. MPL-2.0. */
package de.myzelyam.supervanish.visibility.hiders;
import de.myzelyam.supervanish.SuperVanish;
import org.bukkit.entity.Player;

public class PreventionHider extends PlayerHider {
    public PreventionHider(SuperVanish plugin) { super(plugin); }
    @Override public boolean setHidden(Player player, Player viewer, boolean hidden) {
        if (player == viewer) return false;
        plugin.getLifecycle().player(viewer, () -> {
            if (!isCurrentSession(player) || !isCurrentSession(viewer)) return;
            boolean desired = plugin.getVanishStateMgr().isVanished(player.getUniqueId())
                    && !plugin.hasPermissionToSee(viewer, player);
            super.setHidden(player, viewer, desired);
            if (!isCurrentSession(player) || !isCurrentSession(viewer)) return;
            plugin.nativeVisibility(viewer, player, desired);
        });
        return true;
    }
    @Override public String getName() { return "Prevention"; }
}
