/*
 * Copyright © 2015, Leon Mangler and the SuperVanish contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package de.myzelyam.supervanish;

import de.myzelyam.api.vanish.VanishAPI;
import de.myzelyam.supervanish.commands.VanishCommand;
import de.myzelyam.supervanish.config.ConfigMgr;
import de.myzelyam.supervanish.listeners.*;
import de.myzelyam.supervanish.features.FeatureMgr;
import de.myzelyam.supervanish.hooks.PluginHookMgr;
import de.myzelyam.supervanish.utils.ExceptionLogger;
import de.myzelyam.supervanish.utils.VersionUtil;
import de.myzelyam.supervanish.visibility.ActionBarMgr;
import de.myzelyam.supervanish.visibility.FileVanishStateMgr;
import de.myzelyam.supervanish.visibility.VisibilityChanger;
import de.myzelyam.supervanish.visibility.hiders.PreventionHider;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public class SuperVanish extends JavaPlugin implements SuperVanishPlugin {

    public static final String[] NON_REQUIRED_SETTINGS_UPDATES = {"6.0.0", "6.0.1", "6.0.2", "6.0.3",
            "6.0.4", "6.0.5", "6.1.0", "6.1.1", "6.1.2", "6.1.3", "6.1.4", "6.1.5", "6.1.6", "6.1.7",
            "6.1.8", "6.2.0", "6.2.1", "6.2.2", "6.2.3", "6.2.4", "6.2.5", "6.2.6", "6.2.7", "6.2.8",
            "6.2.9", "6.2.10", "6.2.11", "6.2.12", "6.2.13", "6.2.14", "6.2.15", "6.2.16", "6.2.17",
            "6.2.18", "6.2.19", "6.2.20", "6.2.21"},
            NON_REQUIRED_MESSAGES_UPDATES = {"6.0.0", "6.0.1", "6.0.2", "6.0.3", "6.0.4", "6.0.5", "6.1.0",
                    "6.1.1", "6.1.2", "6.1.3", "6.1.4", "6.1.5", "6.1.6", "6.1.7", "6.1.8", "6.2.0", "6.2.1",
                    "6.2.2", "6.2.3", "6.2.4", "6.2.5", "6.2.6", "6.2.7", "6.2.8", "6.2.9", "6.2.10", "6.2.11",
                    "6.2.12", "6.2.13", "6.2.14", "6.2.15", "6.2.16", "6.2.17", "6.2.18", "6.2.19", "6.2.20", "6.2.21"};

    private AutoCloseable packetEventsHook;
    private ActionBarMgr actionBarMgr;
    private FileVanishStateMgr vanishStateMgr;
    private VersionUtil versionUtil;
    private ConfigMgr configMgr;
    private FeatureMgr featureMgr;
    private PlaceholderConverter placeholderConverter;
    private VanishCommand command;
    private VisibilityChanger visibilityChanger;
    private LoginListener loginListener;
    private LayeredPermissionChecker layeredPermissionChecker;
    private PluginHookMgr pluginHookMgr;
    private final java.util.concurrent.ConcurrentMap<java.util.UUID, VanishPlayer> vanishPlayers = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentMap<java.util.UUID, Player> players = new java.util.concurrent.ConcurrentHashMap<>();
    private FoliaLifecycle lifecycle;
    private final Object nativeVisibilityLock = new Object();
    private de.myzelyam.supervanish.hooks.PlugManHook plugManHook;
    private volatile boolean initialized;
    private volatile java.util.concurrent.CompletableFuture<ConfigMgr> loadingConfiguration;

    @Override
    public void onEnable() {
        if (!Bukkit.isGlobalTickThread()) {
            Bukkit.getGlobalRegionScheduler().execute(this, this::onEnable);
            return;
        }
        beginLifecycle();
        FoliaLifecycle expected = lifecycle;
        readConfiguration(false, false).whenComplete((prepared, error) -> {
            if (error != null) {
                if (isEnabled() && lifecycle == expected) expected.global(() -> initializationFailed(error));
                return;
            }
            if (!isEnabled() || lifecycle != expected) { prepared.close(); return; }
            try { expected.global(() -> {
                if (!isEnabled() || lifecycle != expected) { prepared.close(); return; }
                installConfiguration(prepared, null);
            }); } catch (RuntimeException failure) { prepared.close(); logException(failure); }
        });
    }

    private void beginLifecycle() {
        initialized = false;
        lifecycle = new FoliaLifecycle(this);
        Bukkit.getPluginManager().registerEvents(lifecycle, this);
        if (Bukkit.getPluginManager().isPluginEnabled("PlugManX"))
            plugManHook = new de.myzelyam.supervanish.hooks.PlugManHook(this);
    }

    private java.util.concurrent.CompletableFuture<ConfigMgr> readConfiguration(boolean resetSettings, boolean resetMessages) {
        var result = new java.util.concurrent.CompletableFuture<ConfigMgr>();
        loadingConfiguration = result;
        try {
            Bukkit.getAsyncScheduler().runNow(this, task -> {
                ConfigMgr prepared = new ConfigMgr(this);
                try {
                    if (result.isDone()) return;
                    prepared.recreateFiles(resetSettings, resetMessages);
                    prepared.prepareFiles();
                    if (!result.complete(prepared)) prepared.close();
                } catch (Throwable failure) { prepared.close(); result.completeExceptionally(failure); }
            });
        } catch (RuntimeException failure) { result.completeExceptionally(failure); }
        return result;
    }

    private void installConfiguration(ConfigMgr prepared, Runnable completed) {
        try {
            configMgr = prepared;
            primeVisibilityReference();
            removeOwnHelpTopics();
            for (var permission : getDescription().getPermissions()) {
                if (Bukkit.getPluginManager().getPermission(permission.getName()) == null)
                    Bukkit.getPluginManager().addPermission(permission);
            }
            configMgr.checkFilesForLeftOvers();
            placeholderConverter = new PlaceholderConverter(this);
            layeredPermissionChecker = new LayeredPermissionChecker(this);
            command = new VanishCommand(this);
            versionUtil = new VersionUtil(this);
            vanishStateMgr = new FileVanishStateMgr(this);
            visibilityChanger = new VisibilityChanger(new PreventionHider(this), this);
            actionBarMgr = new ActionBarMgr(this);
            Bukkit.getPluginManager().registerEvents(new de.myzelyam.supervanish.visibility.PaperServerPingListener(this), this);
            registerEvents();
            pluginHookMgr = new PluginHookMgr(this);
            featureMgr = new FeatureMgr(this);
            featureMgr.enableFeatures();
            if (Bukkit.getPluginManager().isPluginEnabled("packetevents"))
                packetEventsHook = new de.myzelyam.supervanish.hooks.PacketEventsHook(this);
            VanishAPI.setPlugin(this);
            FoliaLifecycle expected = lifecycle;
            onReload().whenComplete((v, error) -> expected.global(() -> {
                if (lifecycle != expected) return;
                if (error != null) { initializationFailed(error); return; }
                loadingConfiguration = null;
                initialized = true;
                getLogger().info("SuperVanish Folia ready; native owner scheduling and controlled PlugMan unload enabled");
                if (completed != null) completed.run();
            }));
        } catch (Exception e) {
            initializationFailed(e);
        }
    }

    private void initializationFailed(Throwable failure) {
        getLogger().log(Level.SEVERE, "SuperVanish 初始化失败，停止加载并保留原始数据文件。", failure);
        Bukkit.getPluginManager().disablePlugin(this);
    }

    @Override
    public void onDisable() {
        initialized = false;
        var loading = loadingConfiguration;
        loadingConfiguration = null;
        discardConfiguration(loading, new IllegalStateException("Plugin disabled during configuration loading"));
        if (lifecycle != null) lifecycle.stop();
        if (plugManHook != null) { cleanup(plugManHook::close); plugManHook = null; }
        if (packetEventsHook != null) {
            try { packetEventsHook.close(); } catch (Exception failure) { logException(failure); }
            packetEventsHook = null;
        }
        if (!isEnabled()) cleanup(this::removeOwnHelpTopics);
        if (pluginHookMgr != null) cleanup(pluginHookMgr::close);
        if (featureMgr != null) cleanup(featureMgr::disableFeatures);
        if (visibilityChanger != null) visibilityChanger.getHider().clear();
        if (configMgr != null) configMgr.close();
        vanishPlayers.clear(); players.clear();
        VanishAPI.setPlugin(null);
    }
    private void cleanup(Runnable action) {
        try { action.run(); }
        catch (VirtualMachineError fatal) { throw fatal; }
        catch (Throwable failure) { logException(failure); }
    }

    private java.util.concurrent.CompletableFuture<Void> onReload() {
        var tasks = new java.util.ArrayList<java.util.concurrent.CompletableFuture<Void>>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            tasks.add(lifecycle.player(player, () -> resumePlayer(player)));
        }
        return java.util.concurrent.CompletableFuture.allOf(tasks.toArray(java.util.concurrent.CompletableFuture[]::new));
    }

    public void resumePlayer(Player player) {
            player.recalculatePermissions();
            boolean itemPickUps = getPlayerData().getBoolean(
                    "PlayerData." + player.getUniqueId() + ".itemPickUps",
                    getSettings().getBoolean("InvisibilityFeatures.DefaultPickUpItemsOption"));
            boolean vanished = vanishStateMgr.isVanished(player.getUniqueId());
            createVanishPlayer(player, itemPickUps);
            if (vanished) visibilityChanger.resumePlayer(player);
            for (Player target : onlinePlayers())
                if (vanishStateMgr.isVanished(target.getUniqueId()))
                    visibilityChanger.getHider().setHidden(target, player, true);
    }

    public void reload() {
        reload(null);
    }

    public void reload(CommandSender requester) {
        reload(requester, false, false, null);
    }

    public void recreateFiles(CommandSender requester, boolean settings, boolean messages, String reply) {
        reload(requester, settings, messages, reply);
    }

    private void reload(CommandSender requester, boolean resetSettings, boolean resetMessages, String reply) {
        if (!initialized || !lifecycle.accepting() || hasOnlineVanishedPlayers()) {
            sendLifecycleFailure(requester, new IllegalStateException(
                    "请等待初始化/维护完成，并让在线隐身玩家显形或离线后重试；当前隐身已保留。"));
            return;
        }
        long started = System.currentTimeMillis();
        if (requester != null) sendMessage(requester, "PluginReloadStarted", requester);
        var loaded = new java.util.concurrent.atomic.AtomicReference<ConfigMgr>();
        var loading = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<ConfigMgr>>();
        var discarded = new java.util.concurrent.atomic.AtomicBoolean();
        FoliaLifecycle expected = lifecycle;
        expected.prepare(() -> configMgr.getPlayerDataFile().flush()
                .thenCompose(v -> {
                    if (discarded.get()) return java.util.concurrent.CompletableFuture.<ConfigMgr>failedFuture(
                            new IllegalStateException("Configuration operation already ended"));
                    var read = readConfiguration(resetSettings, resetMessages);
                    loading.set(read);
                    if (discarded.get()) discardConfiguration(read,
                            new IllegalStateException("Configuration operation already ended"));
                    return read;
                })
                .thenAccept(loaded::set)).whenComplete((v, failure) -> {
            if (failure != null) {
                discarded.set(true);
                discardConfiguration(loading.get(), failure);
                sendLifecycleFailure(requester, failure);
                return;
            }
            expected.global(() -> {
                // The preloaded manager belongs to the next runtime; do not close it with the old one.
                loadingConfiguration = null;
                HandlerList.unregisterAll(this);
                onDisable();
                beginLifecycle();
                installConfiguration(loaded.get(), () -> {
                    if (requester != null) sendMessage(requester, reply != null ? reply
                            : getMessage("PluginReloaded").replace("%time%", Long.toString(System.currentTimeMillis() - started)), requester);
                });
            });
        });
    }

    public boolean isInitialized() { return initialized; }
    private void discardConfiguration(java.util.concurrent.CompletableFuture<ConfigMgr> read, Throwable cause) {
        if (read == null) return;
        read.thenAccept(ConfigMgr::close);
        read.completeExceptionally(cause);
    }
    public boolean hasOnlineVanishedPlayers() {
        return vanishStateMgr != null && players.keySet().stream().anyMatch(vanishStateMgr::isVanished);
    }
    public void sendLifecycleFailure(CommandSender requester, Throwable error) {
        Throwable cause = error;
        while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) cause = cause.getCause();
        String message = "§cSuperVanish: 操作未完成，未解除隐身。" + cause.getMessage();
        if (requester instanceof Player player) {
            if (isEnabled()) player.getScheduler().execute(this, () -> player.sendMessage(message), null, 1);
        } else if (requester != null) requester.sendMessage(message);
        else getLogger().warning(message);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!initialized || this.command == null || lifecycle == null || !lifecycle.accepting()) return java.util.Collections.emptyList();
        return this.command.tabComplete(command, sender, alias, args);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!initialized || this.command == null || lifecycle == null || !lifecycle.accepting()) {
            sender.sendMessage("SuperVanish: 正在初始化或清理，暂不接受操作。");
            return true;
        }
        this.command.execute(command, sender, label, args);
        return true;
    }

    private void registerEvents() {
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new GeneralListener(this), this);
        pluginManager.registerEvents(new PlayerBlockModifyListener(this), this);
        pluginManager.registerEvents(new WorldChangeListener(this), this);
        if (versionUtil.isOneDotXOrHigher(10)) {
            pluginManager.registerEvents(new TabCompleteListener(this), this);
        }
        // Login may precede entity ownership. Player state is initialized on PlayerJoinEvent instead.
        JoinListener joinListener = new JoinListener(this);
        pluginManager.registerEvent(PlayerJoinEvent.class, joinListener,
                getEventPriority(PlayerJoinEvent.class), joinListener, this, false);
        QuitListener quitListener = new QuitListener(this);
        pluginManager.registerEvent(PlayerQuitEvent.class, quitListener,
                getEventPriority(PlayerQuitEvent.class), quitListener, this, false);
    }

    private EventPriority getEventPriority(Class<? extends Event> eventClass) {
        try {
            String eventName = eventClass.getSimpleName();
            String configString = getSettings().getString("CompatibilityOptions." + eventName + "Priority");
            if (configString == null) return EventPriority.NORMAL;
            EventPriority priority = EventPriority.valueOf(configString);
            return priority;
        } catch (Exception e) {
            logException(e);
            return EventPriority.NORMAL;
        }
    }

    public String replacePlaceholders(String msg, Object... additionalPlayerInfo) {
        return placeholderConverter.replacePlaceholders(msg, additionalPlayerInfo);
    }

    public String getMessage(String path) {
        String message = getMessages().getString("Messages." + path);
        if (message == null) {
            message = configMgr.getMessagesFile().getDefaultConfig().getString("Messages." + path);
        }
        return message;
    }

    public VanishPlayer getVanishPlayer(Player player) {
        VanishPlayer existing = vanishPlayers.get(player.getUniqueId());
        if (existing != null) return existing;
        // ensure that there is always a vanish player
        boolean itemPickUps = getPlayerData().getBoolean(
                "PlayerData." + player.getUniqueId() + ".itemPickUps",
                getSettings().getBoolean("InvisibilityFeatures.DefaultPickUpItemsOption"));
        return vanishPlayers.computeIfAbsent(player.getUniqueId(), id -> new VanishPlayer(player, this, itemPickUps));
    }

    public void createVanishPlayer(Player player, boolean itemPickUps) {
        VanishPlayer vanishPlayer = new VanishPlayer(player, this, itemPickUps);
        players.compute(player.getUniqueId(), (id, old) -> {
            vanishPlayers.put(id, vanishPlayer);
            return player;
        });
        refreshMobSpawnListener();
    }

    public void removeVanishPlayer(VanishPlayer vanishPlayer) {
        if (vanishPlayer == null) return;
        removeVanishPlayer(vanishPlayer.getPlayerUUID(), null, vanishPlayer);
    }
    public void removeVanishPlayer(Player player) {
        removeVanishPlayer(player.getUniqueId(), player, null);
    }
    private void removeVanishPlayer(java.util.UUID id, Player player, VanishPlayer state) {
        var removed = new java.util.concurrent.atomic.AtomicBoolean();
        players.computeIfPresent(id, (key, current) -> {
            if (player != null && current != player || state != null && vanishPlayers.get(id) != state) return current;
            vanishPlayers.remove(id);
            removed.set(true);
            return null;
        });
        if (removed.get() && visibilityChanger != null) visibilityChanger.getHider().forget(id);
        if (removed.get()) refreshMobSpawnListener();
    }

    public void refreshMobSpawnListener() {
        if (featureMgr == null) return;
        var feature = featureMgr.getFeature(de.myzelyam.supervanish.features.NoMobSpawn.class);
        if (feature != null) feature.refreshSpawnerListener();
    }

    public void sendMessage(CommandSender p, String messagesYmlPath, Object... additionalPlayerInfo) {
        Player subject = additionalPlayerInfo.length > 0 && additionalPlayerInfo[0] instanceof Player
                ? (Player) additionalPlayerInfo[0] : null;
        if (subject != null && !Bukkit.isOwnedByCurrentRegion(subject)) {
            lifecycle.player(subject, () -> sendMessage(p, messagesYmlPath, additionalPlayerInfo));
            return;
        }
        String message;
        if (!messagesYmlPath.contains(" ") && getMessage(messagesYmlPath) != null)
            message = getMessage(messagesYmlPath);
        else message = messagesYmlPath;
        if ("".equalsIgnoreCase(message) || "".equalsIgnoreCase(messagesYmlPath))
            return;
        message = replacePlaceholders(message, additionalPlayerInfo);
        String rendered = message;
        if (p instanceof Player player) lifecycle.player(player, () -> player.sendMessage(rendered));
        else p.sendMessage(rendered);
    }

    public boolean canSee(Player viewer, Player viewed) {
        return !visibilityChanger.getHider().isHidden(viewed, viewer);
    }

    public boolean hasPermissionToVanish(CommandSender sender) {
        return layeredPermissionChecker.hasPermissionToVanish(sender);
    }

    public boolean hasPermissionToSee(Player viewer, Player viewed) {
        return layeredPermissionChecker.hasPermissionToSee(viewer, viewed);
    }

    public boolean hasPermissionToSee(Player viewer, java.util.UUID viewedId, int viewedUseLevel) {
        return layeredPermissionChecker.hasPermissionToSee(viewer, viewedId, viewedUseLevel);
    }

    public VanishPlayer findVanishPlayer(java.util.UUID id) {
        return vanishPlayers.get(id);
    }

    public int getLayeredPermissionLevel(CommandSender sender, String permission) {
        return layeredPermissionChecker.getLayeredPermissionLevel(sender, permission);
    }

    public FileConfiguration getSettings() {
        return configMgr.getSettings();
    }

    public FileConfiguration getMessages() {
        return configMgr.getMessages();
    }

    public FileConfiguration getPlayerData() {
        return configMgr.getPlayerData();
    }

    @Override
    public FileConfiguration getConfig() {
        return getSettings();
    }

    @Override
    public void log(Level level, String msg) {
        getLogger().log(level, msg);
    }

    @Override
    public void log(Level level, String msg, Throwable ex) {
        getLogger().log(level, msg, ex);
    }

    @Override
    public void logException(Throwable e) {
        ExceptionLogger.logException(e, this);
    }


    public boolean isUseProtocolLib() {
        return false; // Preserved binary API; ProtocolLib is no longer used.
    }

    public ActionBarMgr getActionBarMgr() {
        return actionBarMgr;
    }

    @Override
    public FileVanishStateMgr getVanishStateMgr() {
        return vanishStateMgr;
    }

    public VersionUtil getVersionUtil() {
        return versionUtil;
    }

    public ConfigMgr getConfigMgr() {
        return configMgr;
    }

    public FeatureMgr getFeatureMgr() {
        return featureMgr;
    }

    public PlaceholderConverter getPlaceholderConverter() {
        return placeholderConverter;
    }

    public VisibilityChanger getVisibilityChanger() {
        return visibilityChanger;
    }

    public PluginHookMgr getPluginHookMgr() {
        return pluginHookMgr;
    }

    public FoliaLifecycle getLifecycle() { return lifecycle; }
    private void primeVisibilityReference() {
        try {
            // PluginBase.equals compares names: a hot-loaded instance can inherit the old
            // instance's weak value until GC. Renew that key before any owner calls hidePlayer.
            Class<?> type = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer", false, Bukkit.getServer().getClass().getClassLoader());
            var method = type.getDeclaredMethod("getPluginWeakReference", org.bukkit.plugin.Plugin.class);
            method.setAccessible(true);
            var field = type.getDeclaredField("pluginWeakReferences"); field.setAccessible(true);
            @SuppressWarnings("unchecked") var references = (java.util.Map<org.bukkit.plugin.Plugin, java.lang.ref.WeakReference<org.bukkit.plugin.Plugin>>) field.get(null);
            synchronized (references) {
                var previous = references.get(this);
                if (previous != null && previous.get() != this) references.remove(this);
                Object initialized = method.invoke(null, this);
                if (!(initialized instanceof java.lang.ref.WeakReference<?> reference) || reference.get() != this)
                    throw new IllegalStateException("Native visibility key belongs to another plugin instance");
            }
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot initialize native visibility registration for this core", failure); }
    }
    public void nativeVisibility(Player viewer, Player target, boolean hidden) {
        if (!Bukkit.isOwnedByCurrentRegion(viewer)) throw new IllegalStateException("Visibility must run on viewer owner");
        // Native WeakHashMap reads also expunge dead keys. Serialize our calls after GC,
        // including showPlayer, to keep the core's identity-based WeakReference stable.
        synchronized (nativeVisibilityLock) {
            if (hidden) viewer.hidePlayer(this, target);
            else viewer.showPlayer(this, target);
        }
    }
    public java.util.Collection<Player> onlinePlayers() { return java.util.List.copyOf(players.values()); }
    public Player player(java.util.UUID id) { return id == null ? null : players.get(id); }
    public Player player(String name) {
        for (Player player : players.values()) if (player.getName().equalsIgnoreCase(name)) return player;
        return null;
    }
    public void cleanPlayer(Player player) {
        if (!Bukkit.isOwnedByCurrentRegion(player)) throw new IllegalStateException("Cleanup must run on player owner");
        var failures = new java.util.ArrayList<Throwable>();
        if (featureMgr != null) for (var feature : featureMgr.getActiveFeatures())
            collectCleanup(failures, () -> feature.cleanupPlayer(player));
        if (pluginHookMgr != null) collectCleanup(failures, () -> pluginHookMgr.cleanupPlayer(player));
        collectCleanup(failures, () -> visibilityChanger.restorePlayer(player));
        for (Player target : visibilityChanger.getHider().getHiddenPlayerKeys())
            if (target != player) collectCleanup(failures, () -> nativeVisibility(player, target, false));
        collectCleanup(failures, () -> player.removeMetadata("vanished", this));
        if (actionBarMgr != null) collectCleanup(failures, () -> actionBarMgr.removeActionBar(player));
        if (!failures.isEmpty()) {
            var failure = new IllegalStateException("Player cleanup failed: " + player.getUniqueId());
            failures.forEach(failure::addSuppressed);
            throw failure;
        }
    }
    private void collectCleanup(java.util.List<Throwable> failures, Runnable action) {
        try { action.run(); }
        catch (VirtualMachineError fatal) { throw fatal; }
        catch (Throwable failure) { failures.add(failure); }
    }

    public void removeOwnHelpTopics() {
        java.util.Set<String> names = new java.util.HashSet<>();
        getServer().getCommandMap().getKnownCommands().forEach((name, command) -> {
            if (!isEnabled() && command instanceof org.bukkit.command.PluginIdentifiableCommand owned && owned.getPlugin() == this)
                names.add("/" + name);
        });
        java.util.Set<org.bukkit.help.HelpTopic> removed = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        try {
            var commandField = org.bukkit.help.GenericCommandHelpTopic.class.getDeclaredField("command");
            commandField.setAccessible(true);
            for (var topic : getServer().getHelpMap().getHelpTopics()) {
                if (topic instanceof org.bukkit.help.GenericCommandHelpTopic) {
                    Object command = commandField.get(topic);
                    if (command instanceof org.bukkit.command.PluginIdentifiableCommand owned
                            && owned.getPlugin().getName().equals(getName())
                            && owned.getPlugin().getClass().getName().equals(getClass().getName())
                            && (!owned.getPlugin().isEnabled() || owned.getPlugin() != this)) removed.add(topic);
                }
                if (names.contains(topic.getName()) || (!isEnabled() && topic.getName().equals(getName()))) removed.add(topic);
            }
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        try {
            var field = org.bukkit.help.IndexHelpTopic.class.getDeclaredField("allTopics");
            field.setAccessible(true);
            for (var topic : java.util.List.copyOf(getServer().getHelpMap().getHelpTopics())) {
                if (topic instanceof org.bukkit.help.IndexHelpTopic) {
                    @SuppressWarnings("unchecked") var children = (java.util.Collection<org.bukkit.help.HelpTopic>) field.get(topic);
                    field.set(topic, new java.util.ArrayList<>(children.stream().filter(child -> !removed.contains(child)).toList()));
                }
            }
            getServer().getHelpMap().getHelpTopics().removeIf(removed::contains);
        } catch (ReflectiveOperationException failure) { logException(failure); }
    }
}
