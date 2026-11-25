package de.myzelyam.supervanish.features;

import com.destroystokyo.paper.event.entity.PlayerNaturallySpawnCreaturesEvent;
import com.destroystokyo.paper.event.entity.PreSpawnerSpawnEvent;
import com.destroystokyo.paper.event.entity.SkeletonHorseTrapEvent;
import de.myzelyam.supervanish.SuperVanish;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import java.util.List;
import java.util.UUID;

public class NoMobSpawn extends Feature {

    private boolean suppressErrors = false;
    private boolean enabled;
    private boolean spawnerRegistered;

    public NoMobSpawn(SuperVanish plugin) {
        super(plugin);
    }

    @Override
    public synchronized void onEnable() {
        enabled = true;
        refreshSpawnerListener();
    }

    @Override
    public synchronized void onDisable() {
        enabled = false;
        refreshSpawnerListener();
    }

    public synchronized void refreshSpawnerListener() {
        boolean needed = enabled && !plugin.getVanishStateMgr().getOnlineVanishedPlayers().isEmpty();
        if (needed == spawnerRegistered) return;
        if (needed) {
            // This sub-event shares the natural pre-spawn table: subscribe only while needed.
            Bukkit.getPluginManager().registerEvent(PreSpawnerSpawnEvent.class, this, EventPriority.NORMAL,
                    (listener, event) -> {
                        if (event instanceof PreSpawnerSpawnEvent spawn) onEntitySpawnerSpawn(spawn);
                    }, plugin);
        } else {
            PreSpawnerSpawnEvent.getHandlerList().unregister(this);
        }
        spawnerRegistered = needed;
    }

    @EventHandler
    public void onSkeletonHorseTrap(SkeletonHorseTrapEvent e) {
        try {
            List<HumanEntity> humans = e.getEligibleHumans();
            int humansCount = humans.size();
            for (HumanEntity human : humans) {
                if (human instanceof Player) {
                    Player p = (Player) human;
                    if (plugin.getVanishStateMgr().isVanished(p.getUniqueId())) {
                        humansCount--;
                    }
                }
            }
            if (humansCount == 0)
                e.setCancelled(true);
        } catch (Exception er) {
            if (!suppressErrors) {
                plugin.logException(er);
                suppressErrors = true;
            }
        }
    }

    @EventHandler
    public void onEntitySpawn(PlayerNaturallySpawnCreaturesEvent e) {
        try {
            if (plugin.getVanishStateMgr().isVanished(e.getPlayer().getUniqueId()))
                e.setCancelled(true);
        } catch (Exception er) {
            if (!suppressErrors) {
                plugin.logException(er);
                suppressErrors = true;
            }
        }
    }

    public void onEntitySpawnerSpawn(PreSpawnerSpawnEvent e) {
        try {
            // First check if a non-spectator vanished player is in range
            boolean vanishedPlayerInRange = false;
            java.util.Collection<Player> nearby = e.getSpawnerLocation().getNearbyPlayers(16);
            for (Player p : nearby) {
                if (!org.bukkit.Bukkit.isOwnedByCurrentRegion(p)) continue;
                if (!plugin.getVanishStateMgr().isVanished(p.getUniqueId())) continue;
                if (p.getWorld().equals(e.getSpawnerLocation().getWorld()) &&
                        p.getLocation().distanceSquared(e.getSpawnerLocation()) <= 256 &&
                        p.getGameMode() != GameMode.SPECTATOR)
                    vanishedPlayerInRange = true;
            }
            if (!vanishedPlayerInRange) return;

            // If so, only cancel if no non-vanished player is in range
            for (Player p : nearby) {
                if (!org.bukkit.Bukkit.isOwnedByCurrentRegion(p)) return; // Do not cancel based on an unowned player's live state.
                if (p.getWorld().equals(e.getSpawnerLocation().getWorld()) &&
                        p.getLocation().distanceSquared(e.getSpawnerLocation()) <= 256 &&
                        p.getGameMode() != GameMode.SPECTATOR &&
                        !plugin.getVanishStateMgr().isVanished(p.getUniqueId()))
                    return;
            }
            e.setCancelled(true);
        } catch (Exception er) {
            if (!suppressErrors) {
                plugin.logException(er);
                suppressErrors = true;
            }
        }
    }

    @Override
    public boolean isActive() {
        return plugin.getSettings().getBoolean("InvisibilityFeatures.PreventMobSpawning", true);
    }
}
