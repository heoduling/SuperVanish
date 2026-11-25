/* Copyright © 2015, Leon Mangler and the SuperVanish contributors. MPL-2.0. */
package de.myzelyam.supervanish.features;
import de.myzelyam.api.vanish.PostPlayerHideEvent;
import de.myzelyam.api.vanish.PostPlayerShowEvent;
import de.myzelyam.supervanish.SuperVanish;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.potion.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NightVision extends Feature {
    public static final int INFINITE_POTION_EFFECT_LENGTH = -1;
    private record Previous(PotionEffect effect, long savedAt) { }
    private final Map<UUID, Previous> previous = new ConcurrentHashMap<>();
    public NightVision(SuperVanish plugin) { super(plugin); }
    @Override public boolean isActive() { return plugin.getSettings().getBoolean("InvisibilityFeatures.NightVisionEffect"); }
    @EventHandler public void onVanish(PostPlayerHideEvent event) {
        Player p = event.getPlayer();
        previous.putIfAbsent(p.getUniqueId(), new Previous(p.getPotionEffect(PotionEffectType.NIGHT_VISION), System.nanoTime()));
        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, -1, 0, true, false));
    }
    @EventHandler public void onReappear(PostPlayerShowEvent event) { cleanupPlayer(event.getPlayer()); }
    @Override public void cleanupPlayer(Player player) {
        Previous saved = previous.remove(player.getUniqueId());
        if (saved == null) return;
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        PotionEffect effect = saved.effect();
        if (effect == null) return;
        int duration = effect.isInfinite() ? -1 : effect.getDuration() - (int)((System.nanoTime() - saved.savedAt()) / 50_000_000L);
        if (effect.isInfinite() || duration > 0) player.addPotionEffect(new PotionEffect(effect.getType(), duration,
                effect.getAmplifier(), effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
    }
    @Override public void onDisable() { previous.clear(); }
}
