package de.myzelyam.supervanish;
import de.myzelyam.supervanish.config.StorageFile;
import java.nio.file.*;
import java.util.concurrent.*;

public final class StorageRegression {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("supervanish-storage-regression-");
        StorageFile store = new StorageFile("data", directory);
        store.update(c -> c.set("Existing.unrelated", "preserve"));
        ExecutorService workers = Executors.newFixedThreadPool(8);
        CompletableFuture<?>[] updates = new CompletableFuture[200];
        for (int i = 0; i < updates.length; i++) {
            int id = i;
            updates[i] = CompletableFuture.runAsync(() -> store.update(c -> c.set("Players.player" + id, true)), workers);
        }
        CompletableFuture.allOf(updates).get(15, TimeUnit.SECONDS);
        store.flush().get(15, TimeUnit.SECONDS);
        workers.shutdown(); store.close();
        StorageFile restored = new StorageFile("data", directory);
        if (!"preserve".equals(restored.getConfig().getString("Existing.unrelated"))) throw new AssertionError("Existing value lost");
        for (int i = 0; i < updates.length; i++)
            if (!restored.getConfig().getBoolean("Players.player" + i)) throw new AssertionError("Concurrent update lost: " + i);
        if (Files.exists(directory.resolve("data.yml.tmp"))) throw new AssertionError("Temporary file left behind");
        restored.close();
        String malformed = "InvisiblePlayers: [\nPlayerData:\n  recoverable: original-value\n";
        Path corrupt = directory.resolve("corrupt.yml");
        Files.writeString(corrupt, malformed);
        try (StorageFile unexpected = new StorageFile("corrupt", directory)) {
            throw new AssertionError("Malformed existing data accepted");
        } catch (IllegalStateException expected) {
            if (!(expected.getCause() instanceof org.bukkit.configuration.InvalidConfigurationException)) throw expected;
        }
        if (!Files.readString(corrupt).equals(malformed)) throw new AssertionError("Malformed file was overwritten");
        try (StorageFile reload = new StorageFile("data", directory)) {
            String valid = Files.readString(directory.resolve("data.yml"));
            Files.writeString(directory.resolve("data.yml"), malformed);
            try { reload.reload(); throw new AssertionError("Malformed reload accepted"); }
            catch (IllegalStateException expected) {
                if (!(expected.getCause() instanceof org.bukkit.configuration.InvalidConfigurationException)) throw expected;
            }
            if (!"preserve".equals(reload.getConfig().getString("Existing.unrelated"))) throw new AssertionError("Failed reload replaced live snapshot");
            if (!Files.readString(directory.resolve("data.yml")).equals(malformed)) throw new AssertionError("Failed reload rewrote file");
            Files.writeString(directory.resolve("data.yml"), valid);
        }
        Path unreadable = directory.resolve("directory.yml");
        Files.createDirectory(unreadable);
        try (StorageFile unexpected = new StorageFile("directory", directory)) {
            throw new AssertionError("Unreadable existing path accepted");
        } catch (IllegalStateException expected) {
            if (!(expected.getCause() instanceof java.io.IOException)) throw expected;
        }
        Files.delete(unreadable); Files.delete(corrupt);
        Files.delete(directory.resolve("data.yml")); Files.delete(directory);
        System.out.println("STORAGE_REGRESSION_PASS: 200 concurrent updates, atomic flush, valid/missing file controls, malformed startup/reload preservation, IO failure rejection");
    }
}
