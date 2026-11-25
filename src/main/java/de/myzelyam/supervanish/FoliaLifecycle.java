package de.myzelyam.supervanish;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.command.CommandSender;
import java.util.Set;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/** Folia-only owner dispatch and the pre-disable barrier for this plugin. */
public final class FoliaLifecycle implements Listener {
    private final SuperVanish plugin;
    private final Set<CompletableFuture<Void>> pending = ConcurrentHashMap.newKeySet();
    private final Set<ScheduledTask> timers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean draining = new AtomicBoolean();
    private volatile boolean stopped;
    private volatile boolean ready;
    private volatile CompletableFuture<Void> preparation;

    public FoliaLifecycle(SuperVanish plugin) { this.plugin = plugin; }
    public boolean accepting() { return !draining.get() && !stopped && plugin.isEnabled(); }
    public boolean ready() { return ready; }

    public CompletableFuture<Void> player(Player player, Runnable action) {
        return player(player, action, 0, false);
    }
    public CompletableFuture<Void> later(Player player, long ticks, Runnable action) {
        return player(player, action, ticks, false);
    }
    public CompletableFuture<Void> entity(org.bukkit.entity.Entity entity, Runnable action) {
        return player(entity, action, 0, false);
    }
    private CompletableFuture<Void> player(org.bukkit.entity.Entity player, Runnable action, long delay, boolean cleanup) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        synchronized (this) {
            if ((!cleanup && !accepting()) || stopped) { result.complete(null); return result; }
            pending.add(result);
        }
        result.whenComplete((v, failure) -> pending.remove(result));
        Runnable execute = () -> {
            try {
                if ((cleanup || accepting()) && !stopped && (player instanceof Player p ? p.isOnline() : player.isValid())) action.run();
                result.complete(null);
            } catch (Throwable failure) { result.completeExceptionally(failure); plugin.logException(failure); }
        };
        Runnable retired = () -> result.complete(null); // A disconnected viewer has no client state to restore.
        try {
            if (delay == 0 && Bukkit.isOwnedByCurrentRegion(player)) execute.run();
            else if (!player.getScheduler().execute(plugin, execute, retired, Math.max(1, delay))) retired.run();
        } catch (RuntimeException failure) { result.completeExceptionally(failure); }
        return result;
    }

    public ScheduledTask timer(long period, Runnable action) {
        ScheduledTask task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,
                t -> { if (accepting()) action.run(); }, period, period);
        timers.add(task);
        return task;
    }
    public void global(Runnable action) {
        if (stopped || !plugin.isEnabled()) return;
        if (Bukkit.isGlobalTickThread()) action.run();
        else Bukkit.getGlobalRegionScheduler().execute(plugin, action);
    }
    public CompletableFuture<Void> async(Runnable action) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        synchronized (this) {
            if (!accepting()) { result.complete(null); return result; }
            pending.add(result);
        }
        result.whenComplete((v, error) -> pending.remove(result));
        try {
            Bukkit.getAsyncScheduler().runNow(plugin, task -> {
                try { if (accepting()) action.run(); result.complete(null); }
                catch (Throwable error) { result.completeExceptionally(error); }
            });
        } catch (RuntimeException error) { result.completeExceptionally(error); }
        return result;
    }
    public <T> CompletableFuture<T> track(CompletableFuture<T> future) {
        CompletableFuture<Void> marker = future.thenApply(value -> null);
        pending.add(marker);
        marker.whenComplete((value, error) -> pending.remove(marker));
        return future;
    }
    public synchronized CompletableFuture<Void> prepare() {
        return prepare(null);
    }
    public synchronized CompletableFuture<Void> prepare(java.util.function.Supplier<CompletableFuture<Void>> files) {
        if (preparation != null) return preparation;
        if (!plugin.isInitialized()) return CompletableFuture.failedFuture(
                new IllegalStateException("SuperVanish 正在初始化，请稍后重试。"));
        if (plugin.hasOnlineVanishedPlayers()) return CompletableFuture.failedFuture(
                new IllegalStateException("仍有在线隐身玩家；为防止暴露位置，请先显形或离线，再重载/卸载。"));
        draining.set(true);
        preparation = new CompletableFuture<>();
        CompletableFuture<Void> result = preparation;
        // Wait without blocking tick threads. Owner callbacks queued before the freeze must retire first.
        global(() -> awaitPending(System.nanoTime() + TimeUnit.SECONDS.toNanos(10), files));
        return result;
    }
    private void awaitPending(long deadline, java.util.function.Supplier<CompletableFuture<Void>> files) {
        if (System.nanoTime() > deadline) { fail(new IllegalStateException("Owner tasks did not drain within 10 seconds")); return; }
        if (!pending.isEmpty()) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> awaitPending(deadline, files), 1);
            return;
        }
        // An operation already executing when admission closed may have just vanished a player.
        if (plugin.hasOnlineVanishedPlayers()) {
            abortPreparation(new IllegalStateException("仍有在线隐身玩家；已取消维护操作并保留隐身。"));
            return;
        }
        if (files != null) {
            try {
                files.get().orTimeout(10, TimeUnit.SECONDS).whenComplete((v, error) -> global(() -> {
                    if (error != null) filePreparationFailed(error);
                    else cleanPlayers();
                }));
            } catch (RuntimeException error) { filePreparationFailed(error); }
        } else cleanPlayers();
    }
    private void filePreparationFailed(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null)
            cause = cause.getCause();
        // A timed-out disk operation may still be writing. Keep admission frozen until restart.
        if (cause instanceof java.util.concurrent.TimeoutException) fail(cause);
        else abortPreparation(failure);
    }
    private void cleanPlayers() {
        timers.forEach(ScheduledTask::cancel);
        timers.clear();
        List<Player> players = List.copyOf(plugin.onlinePlayers());
        CompletableFuture<?>[] cleanups = players.stream()
                .map(p -> player(p, () -> plugin.cleanPlayer(p), 0, true))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(cleanups).orTimeout(10, TimeUnit.SECONDS)
                .thenCompose(v -> plugin.getConfigMgr().getPlayerDataFile().flush())
                .orTimeout(10, TimeUnit.SECONDS)
                .whenComplete((v, failure) -> global(() -> {
                    if (failure != null) { fail(failure); return; }
                    // Give the core's already-cancelled global scheduler entries one full tick to expire.
                    Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                        if (stopped) return;
                        ready = true;
                        plugin.getLogger().info("Folia unload preparation complete: players=" + players.size() + ", pending=" + pending.size());
                        preparation.complete(null);
                    }, 42);
                }));
    }
    private synchronized void abortPreparation(Throwable failure) {
        CompletableFuture<Void> rejected = preparation;
        preparation = null;
        draining.set(false);
        rejected.completeExceptionally(failure);
    }
    private void fail(Throwable failure) {
        plugin.getLogger().log(Level.SEVERE, "Unload preparation failed; plugin remains frozen. Restart the server to recover.", failure);
        preparation.completeExceptionally(failure);
    }
    public void stop() {
        stopped = true;
        draining.set(true);
        timers.forEach(ScheduledTask::cancel);
        timers.clear();
        pending.forEach(f -> f.completeExceptionally(new IllegalStateException("Plugin disabled")));
        pending.clear();
        if (preparation != null) preparation.completeExceptionally(new IllegalStateException("Plugin disabled"));
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(org.bukkit.event.player.AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result.ALLOWED
                && (!plugin.isInitialized() || !accepting())) event.disallow(
                org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                "SuperVanish 正在启动或维护，请稍后重新连接。");
    }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (intercept(event.getSender(), event.getCommand())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRemoteCommand(org.bukkit.event.server.RemoteServerCommandEvent event) { onServerCommand(event); }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (intercept(event.getPlayer(), event.getMessage())) event.setCancelled(true);
    }
    private boolean intercept(CommandSender sender, String text) {
        String[] words = text.trim().replaceFirst("^/", "").split("\\s+");
        if (words.length < 3) return false;
        String command = words[0].toLowerCase(java.util.Locale.ROOT);
        var resolved = Bukkit.getCommandMap().getCommand(command);
        if (!(resolved instanceof org.bukkit.command.PluginIdentifiableCommand owned)
                || !owned.getPlugin().getName().equals("PlugManX")) return false;
        if (command.contains(":")) command = command.substring(command.lastIndexOf(':') + 1);
        if (!command.equals("plugman") && !command.equals("plm")) return false;
        String operation = words[1].toLowerCase(java.util.Locale.ROOT);
        if (!Set.of("unload", "reload", "restart", "disable").contains(operation)) return false;
        if (!sender.hasPermission("plugman." + operation)) return false; // Let PlugMan enforce the original permission.
        if (words[2].equalsIgnoreCase("all") || words[2].equals("*")) {
            sender.sendMessage("SuperVanish: 请指定 SuperVanish 单独操作；批量操作不提供跨插件的清理保证。");
            return true;
        }
        if (!words[2].equalsIgnoreCase(plugin.getName())) return false;
        if (ready) return false;
        if (draining.get()) { sender.sendMessage("SuperVanish: 清理正在进行或已失败，请等待日志结果。"); return true; }
        if (!plugin.isInitialized() || plugin.hasOnlineVanishedPlayers()) {
            sender.sendMessage("SuperVanish: 正在初始化或仍有在线隐身玩家；请等待初始化完成，并让隐身玩家显形/离线后重试。");
            return true;
        }
        sender.sendMessage("SuperVanish: 正在恢复玩家状态，完成后将执行原操作。");
        String original = text.trim().replaceFirst("^/", "");
        prepare().whenComplete((v, failure) -> {
            if (failure == null) global(() -> queueReplay(original));
            else plugin.sendLifecycleFailure(sender, failure);
        });
        return true;
    }
    private void queueReplay(String original) {
        try {
            // This core stores an initialization Throwable in the new PluginClassLoader.
            // Replay next tick through JDK handles, so its backtrace contains no old plugin frame.
            var handles = java.lang.invoke.MethodHandles.publicLookup();
            var dispatch = handles.findStatic(Bukkit.class, "dispatchCommand",
                    java.lang.invoke.MethodType.methodType(boolean.class, CommandSender.class, String.class));
            dispatch = java.lang.invoke.MethodHandles.insertArguments(dispatch, 0, Bukkit.getConsoleSender(), original)
                    .asType(java.lang.invoke.MethodType.methodType(void.class));
            var cleanup = handles.findVirtual(SuperVanish.class, "removeOwnHelpTopics",
                    java.lang.invoke.MethodType.methodType(void.class)).bindTo(plugin);
            Runnable replay = java.lang.invoke.MethodHandleProxies.asInterfaceInstance(Runnable.class,
                    java.lang.invoke.MethodHandles.foldArguments(cleanup, dispatch));
            if (replay.getClass().getClassLoader() == plugin.getClass().getClassLoader())
                throw new IllegalStateException("Replay wrapper retains the old plugin loader");
            Bukkit.getGlobalRegionScheduler().execute(plugin, replay);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "Command replay failed; plugin remains prepared and operation was not executed", failure);
        }
    }
}
