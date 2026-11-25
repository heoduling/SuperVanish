/* Copyright © 2015, Leon Mangler and the SuperVanish contributors. MPL-2.0. */
package de.myzelyam.supervanish.config;
import de.myzelyam.supervanish.SuperVanish;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

public class StorageFile implements PluginFile<FileConfiguration>, AutoCloseable {
    private final Path file;
    private final String name;
    private final ExecutorService writer;
    private final AtomicReference<Map<String, Object>> values = new AtomicReference<>(Map.of());
    private final AtomicBoolean queued = new AtomicBoolean();
    private volatile Map<String, Object> committed;
    private volatile Throwable failure;
    public StorageFile(String name, SuperVanish plugin) { this(name, plugin.getDataFolder().toPath()); }
    public StorageFile(String name, Path directory) {
        this.name = name; this.file = directory.resolve(name + ".yml");
        // A failed initial read must not create a writer or publish an empty replacement.
        reload();
        writer = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "SuperVanish-data-writer"); thread.setDaemon(true); return thread;
        });
    }
    @Override public String getName() { return name; }
    @Override public void reload() {
        YamlConfiguration parsed = new YamlConfiguration();
        try {
            if (!Files.notExists(file)) parsed.load(file.toFile());
        } catch (IOException | InvalidConfigurationException error) {
            throw new IllegalStateException("Cannot load data file " + file + "; existing data was not replaced", error);
        }
        values.set(flatten(parsed));
    }
    @Override public FileConfiguration getConfig() { return yaml(values.get()); }
    public void update(Consumer<FileConfiguration> edit) {
        Map<String, Object> previous, next;
        do {
            previous = values.get();
            FileConfiguration copy = yaml(previous);
            edit.accept(copy);
            next = flatten(copy);
        } while (!values.compareAndSet(previous, next));
        save();
    }
    private static Map<String, Object> flatten(FileConfiguration config) {
        Map<String, Object> result = new HashMap<>();
        config.getValues(true).forEach((key, value) -> {
            if (!(value instanceof ConfigurationSection)) result.put(key, detach(value));
        });
        return Collections.unmodifiableMap(result);
    }
    private static Object detach(Object value) {
        if (value instanceof List<?> list) return list.stream().map(StorageFile::detach).toList();
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(key, detach(item)));
            return Collections.unmodifiableMap(result);
        }
        return value;
    }
    private static YamlConfiguration yaml(Map<String, Object> map) {
        YamlConfiguration config = new YamlConfiguration();
        map.forEach((key, value) -> config.set(key, detach(value)));
        return config;
    }
    @Override public void save() {
        if (!queued.compareAndSet(false, true)) return;
        try {
            writer.execute(() -> {
                try { writeLatest(); }
                catch (Throwable error) { failure = error; error.printStackTrace(); }
                finally {
                    queued.set(false);
                    if (failure == null && committed != values.get()) save();
                }
            });
        } catch (RejectedExecutionException failure) { queued.set(false); throw failure; }
    }
    private void writeLatest() {
        try {
            Map<String, Object> written;
            do {
                written = values.get();
                Files.createDirectories(file.getParent());
                Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
                Files.writeString(temporary, yaml(written).saveToString(), java.nio.charset.StandardCharsets.UTF_8);
                try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
                committed = written;
            } while (written != values.get());
            failure = null;
        } catch (Exception failure) { throw new CompletionException(failure); }
    }
    public CompletableFuture<Void> flush() { return CompletableFuture.runAsync(this::writeLatest, writer); }
    @Override public void close() { writer.shutdown(); }
}
