package com.cavetale.magicmap.webserver;

import com.cavetale.core.playercache.PlayerCache;
import com.cavetale.magicmap.PlayerLocationTag;
import com.cavetale.magicmap.file.WorldFileCache;
import com.cavetale.webserver.content.ContentDeliverySessionData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.Data;

@Data
public final class MagicMapContentDeliverySessionData implements ContentDeliverySessionData {
    private String mapName;
    private WorldFileCache worldFileCache;
    private final Map<UUID, PlayerLocationTag> playerLocationTags = new HashMap<>();
    private List<PlayerCache> playerList;

    public boolean isInWorld(PlayerLocationTag tag) {
        return worldFileCache != null
            && tag.getServer() == worldFileCache.getServer()
            && Objects.equals(tag.getWorld(), worldFileCache.getName());
    }
}
