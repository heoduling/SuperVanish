/* Copyright © 2015, Leon Mangler and the SuperVanish contributors. MPL-2.0. */
package de.myzelyam.supervanish.listeners;
import de.myzelyam.supervanish.SuperVanish;
import de.myzelyam.supervanish.commands.CommandAction;
import de.myzelyam.supervanish.features.Broadcast;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.EventExecutor;

public class JoinListener implements EventExecutor, Listener {
    private final SuperVanish plugin;
    public JoinListener(SuperVanish plugin) { this.plugin = plugin; }
    @Override public void execute(Listener listener, Event event) {
        if (!(event instanceof PlayerJoinEvent join)) return;
        Player player = join.getPlayer();
        if (!org.bukkit.Bukkit.isOwnedByCurrentRegion(player))
            throw new IllegalStateException("PlayerJoinEvent must run on its player owner");
        boolean[] started = {false};
        plugin.getLifecycle().player(player, () -> {
            started[0] = true;
            initialize(join);
        });
        if (!started[0]) {
            join.setJoinMessage(null);
            player.kickPlayer("SuperVanish 正在维护，请稍后重新连接。");
        }
    }
    private void initialize(PlayerJoinEvent join) {
        Player player = join.getPlayer();
        plugin.createVanishPlayer(player, plugin.getPlayerData().getBoolean("PlayerData." + player.getUniqueId() + ".itemPickUps",
                plugin.getSettings().getBoolean("InvisibilityFeatures.DefaultPickUpItemsOption")));
        boolean vanished = plugin.getVanishStateMgr().isVanished(player.getUniqueId());
        if (vanished && plugin.getSettings().getBoolean("VanishStateFeatures.CheckPermissionOnLogin")
                && !CommandAction.VANISH_SELF.checkPermission(player, plugin))
            plugin.getVanishStateMgr().setVanishedState(player.getUniqueId(), player.getName(), false, null);
        if (player.hasPermission("sv.joinvanished") && plugin.getSettings().getBoolean("VanishStateFeatures.AutoVanishOnJoin"))
            plugin.getVanishStateMgr().setVanishedState(player.getUniqueId(), player.getName(), true, null);
        plugin.resumePlayer(player);
        if (plugin.getVanishStateMgr().isVanished(player.getUniqueId())) {
            if (plugin.getSettings().getBoolean("MessageOptions.HideRealJoinQuitMessages")) {
                join.setJoinMessage(null);
                Broadcast.announceSilentJoin(player, plugin);
            }
            if (plugin.getSettings().getBoolean("MessageOptions.RemindVanishedOnJoin"))
                plugin.sendMessage(player, "RemindingMessage", player);
        } else player.removeMetadata("vanished", plugin);
    }
}
