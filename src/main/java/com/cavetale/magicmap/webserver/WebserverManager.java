package com.cavetale.magicmap.webserver;

import com.cavetale.core.command.RemotePlayer;
import com.cavetale.core.connect.Connect;
import com.cavetale.magicmap.PlayerLocationTag;
import com.cavetale.webserver.WebserverPlugin;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import org.bukkit.Bukkit;
import static com.cavetale.magicmap.MagicMapPlugin.plugin;

/**
 * Register our content delivery.
 */
@Getter
public final class WebserverManager {
    private MagicMapContentDelivery contentDelivery;
    private Map<UUID, PlayerLocationTag> playerLocationTags = new HashMap<>();
    private boolean updatingPlayerLocations = false;

    public boolean enable() {
        if (!WebserverPlugin.plugin().isWebserverEnabled()) return false;
        contentDelivery = new MagicMapContentDelivery();
        contentDelivery.enable();
        WebserverPlugin.plugin().getContentManager().register(contentDelivery);
        Bukkit.getScheduler().runTaskTimer(plugin(), this::updatePlayerLocationTags, 5L, 5L);
        return true;
    }

    public void disable() {
        WebserverPlugin.plugin().getContentManager().unregister(contentDelivery);
        contentDelivery = null;
    }

    private void updatePlayerLocationTags() {
        if (updatingPlayerLocations) return;
        updatingPlayerLocations = true;
        final List<RemotePlayer> players = Connect.get().getRemotePlayers();
        Bukkit.getScheduler().runTaskAsynchronously(plugin(), () -> {
                // Async thread
                final Map<UUID, PlayerLocationTag> map = new HashMap<>();
                for (RemotePlayer player : players) {
                    final UUID uuid = player.getUniqueId();
                    PlayerLocationTag tag = PlayerLocationTag.loadFromRedis(player.getOriginServer(), uuid);
                    if (tag == null) {
                        tag = new PlayerLocationTag();
                        tag.makeUnknown();
                    }
                    map.put(uuid, tag);
                }
                Bukkit.getScheduler().runTask(plugin(), () -> {
                        // Main thread
                        updatingPlayerLocations = false;
                        playerLocationTags.entrySet().retainAll(map.keySet());
                        playerLocationTags.putAll(map);
                    });
            });
    }
}
