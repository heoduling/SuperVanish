/*
 * Copyright © 2015, Leon Mangler and the SuperVanish contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.myzelyam.supervanish.features;

import de.myzelyam.api.vanish.PlayerShowEvent;
import de.myzelyam.api.vanish.PostPlayerHideEvent;
import de.myzelyam.supervanish.SuperVanish;
import de.myzelyam.supervanish.VanishPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

public class Broadcast extends Feature {
    public Broadcast(SuperVanish plugin) { super(plugin); }

    public static void announceSilentJoin(Player vanished, SuperVanish plugin) {
        if (plugin.getSettings().getBoolean("MessageOptions.AnnounceRealJoinQuitToAdmins", true))
            for (Player viewer : plugin.onlinePlayers())
                if (viewer != vanished) deliver(vanished, viewer, plugin, null, plugin.getMessage("SilentJoinMessageForAdmins"));
    }

    public static void announceSilentDeath(Player player, SuperVanish plugin, String deathMessage) {
        if (plugin.getSettings().getBoolean("MessageOptions.AnnounceDeathToAdmins", true)) {
            String message = plugin.getMessage("SilentDeathMessage").replace("%deathmsg%", deathMessage);
            for (Player viewer : plugin.onlinePlayers())
                if (viewer != player) deliver(player, viewer, plugin, null, message);
        }
    }

    public static void announceSilentQuit(Player player, SuperVanish plugin) {
        if (plugin.getSettings().getBoolean("MessageOptions.AnnounceRealJoinQuitToAdmins", true))
            for (Player viewer : plugin.onlinePlayers())
                if (viewer != player) deliver(player, viewer, plugin, null, plugin.getMessage("SilentQuitMessageForAdmins"));
    }

    @Override public boolean isActive() {
        return plugin.getSettings().getBoolean("MessageOptions.FakeJoinQuitMessages.BroadcastFakeQuitOnVanish")
                || plugin.getSettings().getBoolean("MessageOptions.FakeJoinQuitMessages.BroadcastFakeJoinOnReappear");
    }

    @EventHandler public void onVanish(PostPlayerHideEvent event) {
        if (!event.isSilent() && plugin.getSettings().getBoolean("MessageOptions.FakeJoinQuitMessages.BroadcastFakeQuitOnVanish"))
            broadcastTransition(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onReappear(PlayerShowEvent event) {
        if (!event.isSilent() && plugin.getSettings().getBoolean("MessageOptions.FakeJoinQuitMessages.BroadcastFakeJoinOnReappear"))
            broadcastTransition(event.getPlayer(), true);
    }

    private void broadcastTransition(Player subject, boolean reappear) {
        String prefix = "MessageOptions.FakeJoinQuitMessages.";
        boolean onlyAdmins = plugin.getSettings().getBoolean(prefix + "SendMessageOnlyToAdmins");
        boolean onlyUsers = plugin.getSettings().getBoolean(prefix + "SendMessageOnlyToUsers");
        boolean announce = plugin.getSettings().getBoolean(prefix + "AnnounceVanishReappearToAdmins");
        String ordinary = plugin.getMessage(reappear ? "ReappearMessage" : "VanishMessage");
        String administrator = plugin.getMessage(reappear ? "ReappearMessageWithPermission" : "VanishMessageWithPermission");
        for (Player viewer : plugin.onlinePlayers()) {
            String privileged;
            if (onlyUsers) privileged = null;
            else if (!announce) privileged = reappear ? administrator : ordinary;
            else if (viewer == subject) privileged = onlyAdmins ? null : ordinary;
            else privileged = administrator;
            deliver(subject, viewer, plugin, onlyAdmins ? null : ordinary, privileged);
        }
    }

    private static String render(String template, Player subject, Player viewer, SuperVanish plugin) {
        return template == null || template.isEmpty() ? null : plugin.replacePlaceholders(template, subject, viewer);
    }

    private static void deliver(Player subject, Player viewer, SuperVanish plugin, String ordinary, String administrator) {
        if ((ordinary == null || ordinary.isEmpty()) && (administrator == null || administrator.isEmpty())) return;
        // Capture source-dependent text and level while the source still owns its live state (including quit).
        java.util.UUID subjectId = subject.getUniqueId();
        VanishPlayer state = plugin.findVanishPlayer(subjectId);
        int subjectLevel = state == null ? 1 : state.getUsePermissionLevel();
        if (Bukkit.isOwnedByCurrentRegion(viewer)) {
            String selected = plugin.hasPermissionToSee(viewer, subjectId, subjectLevel) ? administrator : ordinary;
            String rendered = render(selected, subject, viewer, plugin);
            if (rendered != null) viewer.sendMessage(rendered);
            return;
        }
        String ordinaryText = render(ordinary, subject, viewer, plugin);
        String administratorText = java.util.Objects.equals(ordinary, administrator) ? ordinaryText : render(administrator, subject, viewer, plugin);
        plugin.getLifecycle().player(viewer, () -> {
            // Basic sv.see is checked at delivery on this viewer's owner; no target access after retirement.
            String selected = plugin.hasPermissionToSee(viewer, subjectId, subjectLevel) ? administratorText : ordinaryText;
            if (selected != null) viewer.sendMessage(selected);
        });
    }
}
