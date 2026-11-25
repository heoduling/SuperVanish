package de.myzelyam.supervanish.hooks;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import de.myzelyam.supervanish.SuperVanish;
import org.bukkit.entity.Player;
import java.util.*;

public final class PacketEventsHook extends PacketListenerAbstract implements AutoCloseable {
    private final SuperVanish plugin;
    public PacketEventsHook(SuperVanish plugin) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = plugin;
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }
    @Override public void onPacketSend(PacketSendEvent event) {
        var type = event.getPacketType();
        if (type != PacketType.Play.Server.TAB_COMPLETE && type != PacketType.Play.Server.PLAYER_INFO_UPDATE) return;
        if (event.isCancelled() || !plugin.getLifecycle().accepting()) return;
        var user = event.getUser();
        if (user == null) return;
        Player viewer = plugin.player(user.getUUID());
        if (viewer == null) return;
        if (event.getPacketType() == PacketType.Play.Server.TAB_COMPLETE
                && plugin.getSettings().getBoolean("InvisibilityFeatures.ModifyTabCompletePackets", true)) {
            var wrapper = new WrapperPlayServerTabComplete(event);
            var original = wrapper.getCommandMatches();
            var filtered = original.stream().filter(match -> !plugin.getVisibilityChanger().getHider().isHidden(match.getText(), viewer)).toList();
            if (filtered.size() != original.size() && plugin.getLifecycle().accepting()) {
                wrapper.setCommandMatches(new ArrayList<>(filtered)); event.markForReEncode(true);
            }
        }
        if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO_UPDATE) {
            var wrapper = new WrapperPlayServerPlayerInfoUpdate(event);
            boolean filter = plugin.getSettings().getBoolean("InvisibilityFeatures.ModifyTablistPackets", true);
            boolean indicate = plugin.getSettings().getBoolean("IndicationFeatures.MarkVanishedPlayersAsSpectators")
                    && wrapper.getActions().contains(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE);
            List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> entries = new ArrayList<>();
            boolean changed = false;
            for (var entry : wrapper.getEntries()) {
                UUID id = entry.getProfileId();
                if (filter && plugin.getVisibilityChanger().getHider().isHidden(id, viewer)) { changed = true; continue; }
                if (indicate && plugin.getVanishStateMgr().isVanished(id) && !id.equals(viewer.getUniqueId())) {
                    var copy = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(entry);
                    copy.setGameMode(GameMode.SPECTATOR); entries.add(copy); changed = true;
                } else entries.add(entry);
            }
            if (changed && plugin.getLifecycle().accepting()) {
                if (entries.isEmpty()) event.setCancelled(true);
                else { wrapper.setEntries(entries); event.markForReEncode(true); }
            }
        }
    }
    @Override public void close() { PacketEvents.getAPI().getEventManager().unregisterListener(this); }
}
