package com.cavetale.magicmap.webserver;

import com.cavetale.core.connect.NetworkServer;
import com.cavetale.magicmap.PlayerLocationTag;
import com.cavetale.webserver.content.ContentDeliverySessionData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public final class MagicMapContentDeliverySessionData implements ContentDeliverySessionData {
    private NetworkServer server;
    private String mapName;
    private String worldName;
    private final Map<UUID, PlayerLocationTag> playerLocationTags = new HashMap<>();

    public boolean isInWorld(PlayerLocationTag tag) {
        return tag.getServer() == server
            && worldName.equals(tag.getWorld());
    }
}
