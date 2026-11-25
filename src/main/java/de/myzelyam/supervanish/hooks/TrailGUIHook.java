/* Copyright © 2015, Leon Mangler and the SuperVanish contributors. MPL-2.0. */
package de.myzelyam.supervanish.hooks;
import de.myzelyam.supervanish.SuperVanish;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.*;
import java.util.*;

public class TrailGUIHook extends PluginHook {
    private final List<RegisteredListener> originals = new ArrayList<>(), wrappers = new ArrayList<>();
    public TrailGUIHook(SuperVanish plugin) { super(plugin); }
    @Override public void onPluginEnable(Plugin dependency) {
        var handlers = PlayerMoveEvent.getHandlerList();
        for (RegisteredListener original : handlers.getRegisteredListeners()) {
            if (original.getPlugin() != dependency) continue;
            originals.add(original); handlers.unregister(original);
            RegisteredListener wrapper = new RegisteredListener(new Listener(){}, (listener,event) -> {
                PlayerMoveEvent move = (PlayerMoveEvent) event;
                if (!superVanish.getVanishStateMgr().isVanished(move.getPlayer().getUniqueId())) original.callEvent(event);
            }, original.getPriority(), superVanish, original.isIgnoringCancelled());
            wrappers.add(wrapper); handlers.register(wrapper);
        }
    }
    @Override public void onPluginDisable(Plugin dependency) {
        var handlers = PlayerMoveEvent.getHandlerList();
        wrappers.forEach(handlers::unregister);
        if (dependency != null && dependency.isEnabled()) handlers.registerAll(originals);
        originals.clear(); wrappers.clear();
    }
}
