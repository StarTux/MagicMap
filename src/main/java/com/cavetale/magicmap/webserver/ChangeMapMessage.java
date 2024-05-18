package com.cavetale.magicmap.webserver;

import com.cavetale.magicmap.file.WorldBorderCache;
import com.cavetale.webserver.message.ClientMessage;

public final class ChangeMapMessage extends ClientMessage {
    private final String mapName;
    private final String displayName;
    private final WorldBorderCache worldBorder;
    private final String innerHtml;
    private final String environment;
    private final int x;
    private final int z;

    public ChangeMapMessage(final String mapName, final String displayName, final WorldBorderCache worldBorder, final String innerHtml, final String environment, final int x, final int z) {
        super("magicmap:change_map");
        this.mapName = mapName;
        this.displayName = displayName;
        this.worldBorder = worldBorder;
        this.innerHtml = innerHtml;
        this.environment = environment;
        this.x = x;
        this.z = z;
    }
}
