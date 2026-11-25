/* Copyright © 2015, Leon Mangler and the SuperVanish contributors. MPL-2.0. */
package de.myzelyam.supervanish.config;
import de.myzelyam.supervanish.SuperVanish;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ConfigurableFile implements PluginFile<FileConfiguration> {
    private final SuperVanish plugin;
    private final String name;
    private final File file;
    private FileConfiguration config, defaults;
    public ConfigurableFile(String name, SuperVanish plugin) {
        this.name = name + ".yml"; this.plugin = plugin; this.file = new File(plugin.getDataFolder(), this.name);
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(plugin.getResource(this.name)), StandardCharsets.UTF_8)) {
            YamlConfiguration parsed = new YamlConfiguration(); parsed.options().parseComments(true); parsed.load(reader); defaults = parsed;
        } catch (Exception failure) { throw new IllegalStateException("Cannot read default " + this.name, failure); }
        save();
        reload();
    }
    @Override public String getName() { return name; }
    @Override public void reload() {
        YamlConfiguration parsed = new YamlConfiguration(); parsed.options().parseComments(true);
        try { parsed.load(file); } catch (Exception failure) { throw new IllegalStateException("Cannot load " + file, failure); }
        boolean changed = false;
        String versionKey = name.equals("config.yml") ? "ConfigVersion" : "MessagesVersion";
        if (!plugin.getDescription().getVersion().equals(parsed.getString(versionKey))) {
            try (Reader reader = new InputStreamReader(Objects.requireNonNull(plugin.getResource("migration/" + name)), StandardCharsets.UTF_8)) {
                YamlConfiguration legacy = new YamlConfiguration(); legacy.options().parseComments(true); legacy.load(reader);
                changed = localizeDefaults(parsed, legacy, defaults, name.equals("messages.yml"));
                if (changed) {
                    java.nio.file.Path backup = file.toPath().resolveSibling(name + ".before-zh-cn");
                    if (!java.nio.file.Files.exists(backup)) java.nio.file.Files.copy(file.toPath(), backup);
                }
            } catch (Exception failure) { throw new IllegalStateException("Cannot localize " + file, failure); }
        }
        changed |= removeUpstreamUpdateSettings(parsed, name.equals("messages.yml"));
        for (String key : defaults.getKeys(true)) {
            if (defaults.get(key) instanceof ConfigurationSection || parsed.contains(key, true)) continue;
            boolean parentValid = true;
            String parent = key;
            while (parent.contains(".")) {
                parent = parent.substring(0, parent.lastIndexOf('.'));
                if (parsed.contains(parent, true) && !parsed.isConfigurationSection(parent)) { parentValid = false; break; }
            }
            if (!parentValid) continue;
            parsed.set(key, defaults.get(key));
            parsed.setComments(key, defaults.getComments(key));
            parsed.setInlineComments(key, defaults.getInlineComments(key)); changed = true;
        }
        if (!plugin.getDescription().getVersion().equals(parsed.getString(versionKey))) {
            parsed.set(versionKey, plugin.getDescription().getVersion()); changed = true;
        }
        if (changed) try { parsed.save(file); } catch (IOException failure) { throw new IllegalStateException("Cannot upgrade " + file, failure); }
        config = parsed;
    }
    static boolean removeUpstreamUpdateSettings(FileConfiguration config, boolean messages) {
        String key = messages ? "Messages.PluginOutdated" : "MiscellaneousOptions.UpdateChecker";
        if (!config.contains(key, true)) return false;
        config.set(key, null);
        if (!messages) {
            ConfigurationSection parent = config.getConfigurationSection("MiscellaneousOptions");
            if (parent != null && parent.getKeys(false).isEmpty()) config.set("MiscellaneousOptions", null);
        }
        return true;
    }
    // Exact legacy-default matches only: never translate user-authored values or comments.
    static boolean localizeDefaults(FileConfiguration current, FileConfiguration legacy,
                                    FileConfiguration translated, boolean messages) {
        boolean changed = false;
        for (String key : legacy.getKeys(true)) {
            if (!current.contains(key, true) || !translated.contains(key, true)) continue;
            if (messages && key.startsWith("Messages.") && !legacy.isConfigurationSection(key)
                    && Objects.equals(current.get(key), legacy.get(key))
                    && !Objects.equals(current.get(key), translated.get(key))) {
                current.set(key, translated.get(key)); changed = true;
            }
            if ((current.getComments(key).isEmpty() || normalizedHeader(current.getComments(key)).equals(normalizedHeader(legacy.getComments(key))))
                    && !current.getComments(key).equals(translated.getComments(key))) {
                current.setComments(key, translated.getComments(key)); changed = true;
            }
            if (current.getInlineComments(key).equals(legacy.getInlineComments(key))
                    && !current.getInlineComments(key).equals(translated.getInlineComments(key))) {
                current.setInlineComments(key, translated.getInlineComments(key)); changed = true;
            }
        }
        var header = current.options().getHeader();
        var oldHeader = legacy.options().getHeader();
        // Resource filtering changes the version in the stock header; compare that line without its version.
        if (normalizedHeader(header).equals(normalizedHeader(oldHeader))
                && !header.equals(translated.options().getHeader())) {
            current.options().setHeader(translated.options().getHeader()); changed = true;
        }
        return changed;
    }
    private static List<String> normalizedHeader(List<String> lines) {
        return lines.stream().map(line -> line == null ? "" : line.replaceFirst("SuperVanish v[^ ]+ -", "SuperVanish -")).toList();
    }
    @Override public FileConfiguration getConfig() { return config; }
    @Override public void save() { if (!file.exists()) plugin.saveResource(name, false); }
    public FileConfiguration getDefaultConfig() { return defaults; }
}
