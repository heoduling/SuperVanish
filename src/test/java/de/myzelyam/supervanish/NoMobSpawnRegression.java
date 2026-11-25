package de.myzelyam.supervanish;

import com.destroystokyo.paper.event.entity.PlayerNaturallySpawnCreaturesEvent;
import com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent;
import com.destroystokyo.paper.event.entity.PreSpawnerSpawnEvent;
import com.destroystokyo.paper.event.entity.SkeletonHorseTrapEvent;
import de.myzelyam.supervanish.features.NoMobSpawn;
import de.myzelyam.supervanish.visibility.FileVanishStateMgr;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.plugin.*;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/** Runs real feature methods and real API HandlerLists; proxies supply owner/world/player inputs. */
public final class NoMobSpawnRegression {
    private static final Set<UUID> hidden = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> online = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> foreign = ConcurrentHashMap.newKeySet();
    private static final List<Player> nearby = new ArrayList<>();
    private static int checks;
    private static World world;
    private static final YamlConfiguration settings = new YamlConfiguration();

    public static class State extends FileVanishStateMgr {
        public State() { super(null); }
        @Override public boolean isVanished(UUID id) { return hidden.contains(id); }
        @Override public Collection<UUID> getOnlineVanishedPlayers() {
            return hidden.stream().filter(online::contains).toList();
        }
    }
    public static class Fixture extends SuperVanish {
        State state;
        @Override public FileVanishStateMgr getVanishStateMgr() { return state; }
        @Override public org.bukkit.configuration.file.FileConfiguration getSettings() { return settings; }
        @Override public void logException(Throwable failure) { throw new AssertionError(failure); }
    }
    private static <T> T allocate(Class<T> type) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p,m,a) -> {
            if (m.getName().equals("equals")) return p == a[0];
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            if (m.getName().equals("toString")) return type.getSimpleName() + " fixture";
            return handler.invoke(p,m,a);
        });
    }
    private static Player player(GameMode mode, double x) {
        UUID id = UUID.randomUUID();
        return proxy(Player.class, (p,m,a) -> switch (m.getName()) {
            case "getUniqueId" -> id;
            case "getGameMode" -> mode;
            case "getWorld" -> world;
            case "getLocation" -> new Location(world,x,64,0);
            default -> throw new AssertionError("Unexpected player read: " + m);
        });
    }
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static long listeners(NoMobSpawn feature) {
        return Arrays.stream(PreCreatureSpawnEvent.getHandlerList().getRegisteredListeners())
                .filter(listener -> listener.getListener() == feature).count();
    }
    private static void refresh(NoMobSpawn feature) throws Exception {
        NoMobSpawn.class.getMethod("refreshSpawnerListener").invoke(feature);
    }
    private static void spawner(NoMobSpawn feature, boolean expected, Player... players) {
        nearby.clear(); nearby.addAll(List.of(players));
        var event = new PreSpawnerSpawnEvent(new Location(world,1,64,0), EntityType.PIG, new Location(world,0,64,0));
        feature.onEntitySpawnerSpawn(event);
        check(event.isCancelled() == expected, "Spawner result expected " + expected);
    }
    public static void main(String[] args) throws Exception {
        // Regression gate: subscribing on enable must not unconditionally populate the shared table.
        for (var method : NoMobSpawn.class.getMethods())
            if (method.isAnnotationPresent(EventHandler.class) && method.getParameterCount() == 1)
                check(!PreCreatureSpawnEvent.class.isAssignableFrom(method.getParameterTypes()[0]),
                        "Idle NoMobSpawn automatically subscribes to shared pre-spawn table");
        PluginManager manager = proxy(PluginManager.class, (p,m,a) -> {
            if (!m.getName().equals("registerEvent")) throw new AssertionError("Unexpected registration: " + m);
            check(a[0] == PreSpawnerSpawnEvent.class, "Only original pre-spawner type may be registered");
            PreSpawnerSpawnEvent.getHandlerList().register(new RegisteredListener((Listener)a[1],
                    (EventExecutor)a[3], (EventPriority)a[2], (Plugin)a[4], a.length == 6 && (boolean)a[5]));
            return null;
        });
        Server server = proxy(Server.class, (p,m,a) -> switch (m.getName()) {
            case "getPluginManager" -> manager;
            case "isOwnedByCurrentRegion" -> !foreign.contains(((Player)a[0]).getUniqueId());
            case "getLogger" -> Logger.getLogger("NoMobSpawnRegression");
            case "getName", "getVersion", "getBukkitVersion" -> "regression";
            default -> throw new AssertionError("Unexpected server read: " + m);
        });
        // Avoid the real server boot/version service in this isolated API fixture.
        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true); serverField.set(null, server);
        world = proxy(World.class, (p,m,a) -> {
            if (m.getName().equals("getNearbyPlayers") || m.getName().equals("getNearbyEntitiesByType")) return nearby;
            throw new AssertionError("Unexpected world read: " + m);
        });
        Fixture plugin = allocate(Fixture.class); plugin.state = allocate(State.class);
        NoMobSpawn feature = new NoMobSpawn(plugin);
        settings.set("InvisibilityFeatures.PreventMobSpawning", true);
        check(feature.isActive(), "Config true enables feature");
        settings.set("InvisibilityFeatures.PreventMobSpawning", false);
        check(!feature.isActive(), "Config false disables feature");
        feature.onEnable(); check(listeners(feature) == 0, "Idle table empty");
        Player vanished = player(GameMode.SURVIVAL, 1), visible = player(GameMode.SURVIVAL,2);
        Player spectator = player(GameMode.SPECTATOR,2), outside = player(GameMode.SURVIVAL,17);
        hidden.add(vanished.getUniqueId()); refresh(feature);
        check(listeners(feature) == 0, "Offline saved vanish does not subscribe");
        online.add(vanished.getUniqueId()); refresh(feature); refresh(feature);
        check(listeners(feature) == 1, "Online vanish subscribes exactly once");
        var natural = new PlayerNaturallySpawnCreaturesEvent(vanished,(byte)8);
        feature.onEntitySpawn(natural); check(natural.isCancelled(), "Vanished natural spawning cancelled");
        natural = new PlayerNaturallySpawnCreaturesEvent(visible,(byte)8);
        feature.onEntitySpawn(natural); check(!natural.isCancelled(), "Visible natural spawning retained");
        spawner(feature,true,vanished); spawner(feature,false,visible);
        spawner(feature,false,vanished,visible); spawner(feature,true,vanished,spectator);
        spawner(feature,true,vanished,outside); spawner(feature,false,spectator);
        foreign.add(visible.getUniqueId()); spawner(feature,false,vanished,visible); foreign.clear();
        SkeletonHorse horse = proxy(SkeletonHorse.class,(p,m,a) -> {throw new AssertionError(m);});
        var trap = new SkeletonHorseTrapEvent(horse,List.of(vanished));
        feature.onSkeletonHorseTrap(trap); check(trap.isCancelled(), "Hidden-only trap cancelled");
        trap = new SkeletonHorseTrapEvent(horse,List.of(vanished,visible));
        feature.onSkeletonHorseTrap(trap); check(!trap.isCancelled(), "Mixed trap retained");
        online.clear(); refresh(feature); check(listeners(feature) == 0, "Last hidden quit unregisters");
        online.add(vanished.getUniqueId()); refresh(feature); check(listeners(feature) == 1, "Hidden rejoin registers");
        feature.onDisable(); check(listeners(feature) == 0, "Disable unregisters");
        refresh(feature); check(listeners(feature) == 0, "Late refresh cannot register after disable");
        feature.onEnable(); check(listeners(feature) == 1, "Reenable restores hidden requirement");
        hidden.clear(); refresh(feature); check(listeners(feature) == 0, "Last show unregisters");
        ExecutorService workers = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new ArrayList<>();
        for (int i=0;i<1000;i++) {
            futures.add(workers.submit(() -> { try { hidden.add(vanished.getUniqueId()); refresh(feature);
                hidden.remove(vanished.getUniqueId()); refresh(feature); } catch(Exception error){throw new RuntimeException(error);} }));
        }
        for (var future:futures) future.get(20,TimeUnit.SECONDS);
        workers.shutdown(); hidden.clear(); refresh(feature);
        check(listeners(feature) == 0, "Concurrent changes converge without duplicate listeners");
        feature.onDisable();
        System.out.println("NO_MOB_SPAWN_REGRESSION_PASS checks="+checks+" concurrentTransitions=2000");
    }
}
