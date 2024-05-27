package com.cavetale.magicmap.webserver;

import com.cavetale.core.playercache.PlayerCache;
import com.cavetale.magicmap.PlayerLocationTag;
import com.cavetale.magicmap.file.WorldFileCache;
import com.cavetale.webserver.content.ContentDeliverySessionData;
import java.util.ArrayList;
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
    private final List<PlayerCache> playerList = new ArrayList<>();
    private final Map<UUID, Integer> missingPlayers = new HashMap<>();
    private boolean loadingMap = false;
    private boolean sendAllClaims = true;

    public boolean isInWorld(PlayerLocationTag tag) {
        return worldFileCache != null
            && tag.getServer() == worldFileCache.getServer()
            && Objects.equals(tag.getWorld(), worldFileCache.getName());
    }
}
