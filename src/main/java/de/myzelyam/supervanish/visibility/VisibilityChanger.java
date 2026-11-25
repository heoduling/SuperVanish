/* Copyright © 2015, Leon Mangler and the SuperVanish contributors. MPL-2.0. */
package de.myzelyam.supervanish.visibility;

import de.myzelyam.api.vanish.*;
import de.myzelyam.supervanish.SuperVanish;
import de.myzelyam.supervanish.features.SilentOpenChest;
import de.myzelyam.supervanish.visibility.hiders.PlayerHider;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VisibilityChanger {
    private final PlayerHider hider;
    private final SuperVanish plugin;
    private final Map<UUID, OriginalState> originalStates = new ConcurrentHashMap<>();
    private record OriginalState(boolean flight, boolean flying, boolean sleepingIgnored) { }
    public VisibilityChanger(PlayerHider hider, SuperVanish plugin) { this.hider = hider; this.plugin = plugin; }
    public void hidePlayer(Player p) { hidePlayer(p, null, false); }
    public void showPlayer(Player p) { showPlayer(p, null, false); }
    public void hidePlayer(Player p, String cause) { hidePlayer(p, cause, false); }
    public void showPlayer(Player p, String cause) { showPlayer(p, cause, false); }
    public void hidePlayer(Player p, String cause, boolean silent) {
        plugin.getLifecycle().player(p, () -> {
            if (plugin.getVanishStateMgr().isVanished(p.getUniqueId())) return;
            PlayerHideEvent event = new PlayerHideEvent(p, silent);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return;
            plugin.getVanishStateMgr().setVanishedState(p.getUniqueId(), p.getName(), true, cause);
            if (!plugin.getVanishStateMgr().isVanished(p.getUniqueId())) return;
            applyState(p);
            for (Player viewer : plugin.onlinePlayers()) hider.setHidden(p, viewer, true);
            if (plugin.getSettings().getBoolean("InvisibilityFeatures.DisableMobTarget")) {
                for (var entity : p.getNearbyEntities(64, 64, 64)) {
                    if (entity instanceof Creature mob) plugin.getLifecycle().entity(mob, () -> {
                        if (plugin.getVanishStateMgr().isVanished(p.getUniqueId()) && mob.getTarget() != null
                                && p.getUniqueId().equals(mob.getTarget().getUniqueId())) mob.setTarget(null);
                    });
                }
            }
            Bukkit.getPluginManager().callEvent(new PostPlayerHideEvent(p, event.isSilent()));
            plugin.sendMessage(p, cause == null ? "OnVanish" : "OnVanishCausedByOtherPlayer", p, cause);
        });
    }
    public void showPlayer(Player p, String cause, boolean silent) {
        plugin.getLifecycle().player(p, () -> {
            if (!plugin.getVanishStateMgr().isVanished(p.getUniqueId())) return;
            PlayerShowEvent event = new PlayerShowEvent(p, silent);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return;
            plugin.getVanishStateMgr().setVanishedState(p.getUniqueId(), p.getName(), false, cause);
            if (plugin.getVanishStateMgr().isVanished(p.getUniqueId())) return;
            // Restore temporary spectator mode before applying the final reappear flight policy.
            SilentOpenChest chest = plugin.getFeatureMgr().getFeature(SilentOpenChest.class);
            if (chest != null) chest.cleanupPlayer(p);
            p.removeMetadata("vanished", plugin);
            for (Player viewer : plugin.onlinePlayers()) hider.setHidden(p, viewer, false);
            plugin.getActionBarMgr().removeActionBar(p);
            p.setSleepingIgnored(false);
            originalStates.remove(p.getUniqueId());
            if (plugin.getSettings().getBoolean("InvisibilityFeatures.Fly.Enable")
                    && plugin.getSettings().getBoolean("InvisibilityFeatures.Fly.DisableOnReappear")
                    && !p.hasPermission("sv.keepfly") && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)) {
                p.setFlying(false);
                p.setAllowFlight(false);
            }
            Bukkit.getPluginManager().callEvent(new PostPlayerShowEvent(p, event.isSilent()));
            plugin.sendMessage(p, cause == null ? "OnReappear" : "OnReappearCausedByOtherPlayer", p, cause);
        });
    }
    private void applyState(Player p) {
        originalStates.putIfAbsent(p.getUniqueId(), new OriginalState(p.getAllowFlight(), p.isFlying(), p.isSleepingIgnored()));
        p.setMetadata("vanished", new FixedMetadataValue(plugin, true));
        p.setSleepingIgnored(true);
        if (plugin.getSettings().getBoolean("InvisibilityFeatures.Fly.Enable")) p.setAllowFlight(true);
        if (plugin.getSettings().getBoolean("MessageOptions.DisplayActionBar")) plugin.getActionBarMgr().addActionBar(p);
    }
    public void resumePlayer(Player p) {
        applyState(p);
        for (Player viewer : plugin.onlinePlayers()) hider.setHidden(p, viewer, true);
        Bukkit.getPluginManager().callEvent(new PostPlayerHideEvent(p, true));
    }
    /** Restore runtime changes without changing the persisted vanish choice. */
    public void restorePlayer(Player p) {
        OriginalState state = originalStates.remove(p.getUniqueId());
        if (state != null) {
            p.setSleepingIgnored(state.sleepingIgnored());
            p.setFlying(false);
            p.setAllowFlight(state.flight());
            if (state.flight()) p.setFlying(state.flying());
        }
    }
    public PlayerHider getHider() { return hider; }
}
