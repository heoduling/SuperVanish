package de.myzelyam.supervanish.hooks;

import bukkit.com.rylinaux.plugman.api.PlugManAPI;
import bukkit.com.rylinaux.plugman.api.GentleUnload;
import de.myzelyam.supervanish.SuperVanish;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Field;
import java.util.Map;

/** Integration with the supplied PlugManX 3.1.0-Beta.2 API. */
public final class PlugManHook implements AutoCloseable {
    private final SuperVanish plugin;
    private final GentleUnload guard;
    public PlugManHook(SuperVanish plugin) {
        this.plugin = plugin;
        guard = () -> {
            if (plugin.getLifecycle().ready()) return true;
            plugin.getLifecycle().prepare();
            return false; // Direct API callers must retry after the asynchronous preparation has completed.
        };
        if (!PlugManAPI.pleaseAddMeToGentleUnload(plugin, guard)) throw new IllegalStateException("PlugMan gentle-unload registration failed");
    }
    @Override public void close() {
        try {
            // This exact API returns a COPY and has no public unregister method.
            Field field = PlugManAPI.class.getDeclaredField("gentleUnloads");
            field.setAccessible(true);
            @SuppressWarnings("unchecked") Map<Plugin, GentleUnload> registry = (Map<Plugin, GentleUnload>) field.get(null);
            registry.remove(plugin, guard);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot release PlugMan guard", failure); }
    }
}
