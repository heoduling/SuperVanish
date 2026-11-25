/* Copyright © 2015, Leon Mangler and the SuperVanish contributors. MPL-2.0. */
package de.myzelyam.supervanish.visibility;
import de.myzelyam.api.vanish.PlayerVanishStateChangeEvent;
import de.myzelyam.supervanish.SuperVanish;
import org.bukkit.Bukkit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FileVanishStateMgr extends VanishStateMgr {
    private final SuperVanish plugin;
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    public FileVanishStateMgr(SuperVanish plugin) {
        super(plugin); this.plugin = plugin;
        var data = plugin.getPlayerData();
        for (String text : data.getStringList("InvisiblePlayers")) {
            try {
                UUID id = UUID.fromString(text); vanished.add(id);
                String name = data.getString("PlayerData." + id + ".information.name");
                if (name != null) names.put(id, name);
            } catch (IllegalArgumentException invalid) { plugin.getLogger().warning("Ignored invalid vanished UUID: " + text); }
        }
    }
    @Override public boolean isVanished(UUID id) { return vanished.contains(id); }
    @Override public void setVanishedState(UUID id, String name, boolean hide, String cause) {
        if (!plugin.getLifecycle().accepting()) return;
        PlayerVanishStateChangeEvent event = new PlayerVanishStateChangeEvent(id, name, hide, cause);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
        if (hide) { vanished.add(id); names.put(id, name); } else vanished.remove(id);
        plugin.refreshMobSpawnListener();
        plugin.getConfigMgr().getPlayerDataFile().update(config -> {
            Set<String> ids = new HashSet<>(config.getStringList("InvisiblePlayers"));
            if (hide) ids.add(id.toString()); else ids.remove(id.toString());
            config.set("InvisiblePlayers", new ArrayList<>(ids));
            if (hide) config.set("PlayerData." + id + ".information.name", name);
        });
    }
    @Override public Set<UUID> getVanishedPlayers() { return Set.copyOf(vanished); }
    @Override public Collection<UUID> getOnlineVanishedPlayers() { return vanished.stream().filter(id -> plugin.player(id) != null).toList(); }
    public UUID getVanishedUUIDFromNameOnFile(String name) {
        for (UUID id : vanished) if (name.equalsIgnoreCase(names.get(id))) return id;
        return null;
    }
}
