/*
 * Copyright © 2015, Leon Mangler and the SuperVanish contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package de.myzelyam.supervanish.listeners;

import de.myzelyam.supervanish.SuperVanish;
import de.myzelyam.supervanish.VanishPlayer;
import de.myzelyam.supervanish.features.Broadcast;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.persistence.PersistentDataType;


public class GeneralListener implements Listener {

    private final SuperVanish plugin;

    private final FileConfiguration config;
    private final NamespacedKey damageBypass;

    public GeneralListener(SuperVanish plugin) {
        this.plugin = plugin;
        config = plugin.getSettings();
        damageBypass = new NamespacedKey(plugin, "damage-bypass");
        // The supplied Shiroha/Luminol core resets the player after PlayerRespawnEvent.
        // Its actual completion event differs from the Paper/Folia completion event.
        Class<? extends org.bukkit.event.Event> completion;
        try {
            completion = Class.forName("me.earthme.luminol.api.entity.player.PostPlayerRespawnEvent")
                    .asSubclass(org.bukkit.event.Event.class);
        } catch (ClassNotFoundException ignored) {
            completion = com.destroystokyo.paper.event.player.PlayerPostRespawnEvent.class;
        }
        final java.lang.reflect.Method getPlayer;
        try { getPlayer = completion.getMethod("getPlayer"); }
        catch (NoSuchMethodException failure) { throw new IllegalStateException("Unsupported respawn completion event", failure); }
        org.bukkit.Bukkit.getPluginManager().registerEvent(completion, this, EventPriority.MONITOR,
                (listener, event) -> {
                    try {
                        Player player = (Player) getPlayer.invoke(event);
                        plugin.getLifecycle().player(player, () -> plugin.resumePlayer(player));
                    } catch (ReflectiveOperationException failure) { throw new org.bukkit.event.EventException(failure); }
                }, plugin, true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!Bukkit.isOwnedByCurrentRegion(projectile)) return;
        if (!(projectile.getShooter() instanceof Player player) || !Bukkit.isOwnedByCurrentRegion(player)) return;
        // A remote hit cannot read the shooter's live permissions. Preserve launch-time authorization.
        projectile.getPersistentDataContainer().set(damageBypass, PersistentDataType.STRING,
                player.hasPermission("sv.damageentities") || player.hasPermission("sv.damage") ? player.getUniqueId().toString() : "");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent e) {
        try {
            Player p = e.getDamageSource().getCausingEntity() instanceof Player player ? player : null;
            Projectile projectile = e.getDamager() instanceof Projectile shot && Bukkit.isOwnedByCurrentRegion(shot) ? shot : null;
            // Folia's native getOwner() omits an owner in another region, so DamageSource may name the arrow itself.
            java.util.UUID responsible = p != null ? p.getUniqueId() : projectile != null ? projectile.getOwnerUniqueId() : null;
            if (responsible == null) return;
            if (p == null) p = plugin.player(responsible);
            if (plugin.getVanishStateMgr().isVanished(responsible)) {
                if (!config.getBoolean("RestrictiveOptions.PreventHittingEntities")) return;
                boolean bypass = p != null && Bukkit.isOwnedByCurrentRegion(p)
                        ? p.hasPermission("sv.damageentities") || p.hasPermission("sv.damage")
                        : projectile != null
                        && responsible.toString().equals(projectile.getPersistentDataContainer().get(damageBypass, PersistentDataType.STRING));
                if (!bypass) {
                    e.setCancelled(true);
                    if (p != null) plugin.sendMessage(p, "EntityHitDenied", p);
                }
            }
        } catch (Exception er) {
            plugin.logException(er);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent e) {
        try {
            Player p = e.getEntity();
            if (plugin.getVanishStateMgr().isVanished(p.getUniqueId())) {
                String deathMessage = e.getDeathMessage();
                e.setDeathMessage(null);
                if (deathMessage != null)
                    Broadcast.announceSilentDeath(p, plugin, deathMessage);
                plugin.cleanPlayer(p);
            }
        } catch (Exception er) {
            plugin.logException(er);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFoodLevelChange(FoodLevelChangeEvent e) {
        try {
            if (e.getEntity() instanceof Player && config.getBoolean("InvisibilityFeatures.DisableHunger")) {
                Player p = (Player) e.getEntity();
                if (plugin.getVanishStateMgr().isVanished(p.getUniqueId())
                        && e.getFoodLevel() <= p.getFoodLevel())
                    e.setCancelled(true);
            }
        } catch (Exception er) {
            plugin.logException(er);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        try {
            if (!(e.getEntity() instanceof Player)) return;
            Player p = (Player) e.getEntity();
            if (!config.getBoolean("InvisibilityFeatures.DisableDamage")) return;
            if (plugin.getVanishStateMgr().isVanished(p.getUniqueId())) {
                e.setCancelled(true);
            }
        } catch (Exception er) {
            plugin.logException(er);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTarget(EntityTargetEvent e) {
        try {
            if (!(e.getTarget() instanceof Player)) return;
            if (!config.getBoolean("InvisibilityFeatures.DisableMobTarget")) return;
            Player p = (Player) e.getTarget();
            if (plugin.getVanishStateMgr().isVanished(p.getUniqueId())) {
                e.setCancelled(true);
            }
        } catch (Exception er) {
            plugin.logException(er);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onItemPickUp(PlayerPickupItemEvent e) {
        try {
            VanishPlayer vanishPlayer = plugin.getVanishPlayer(e.getPlayer());
            if (vanishPlayer == null || !vanishPlayer.isOnlineVanished()) return;
            if (!vanishPlayer.hasItemPickUpsEnabled())
                e.setCancelled(true);
            if (plugin.getSettings().getBoolean("RestrictiveOptions.PreventModifyingOwnInventory")
                    && !e.getPlayer().hasPermission("sv.modifyowninv")) {
                e.setCancelled(true);
            }
        } catch (Exception er) {
            plugin.logException(er);
        }
    }

    @EventHandler
    public void onPlayerCropTrample(PlayerInteractEvent e) {
        try {
            if (!plugin.getVanishStateMgr().isVanished(e.getPlayer().getUniqueId())) return;
            if (e.getAction() != Action.PHYSICAL) return;
            if (e.getClickedBlock() != null && e.getClickedBlock().getType().toString().matches("SOIL|FARMLAND"))
                e.setCancelled(true);
        } catch (Exception er) {
            plugin.logException(er);
        }
    }

}
