package com.cavetale.magicmap.webserver;

import com.cavetale.core.playercache.PlayerCache;
import com.cavetale.webserver.message.ClientMessage;

public final class PlayerAddMessage extends ClientMessage {
    private final PlayerCache player;
    private final int x;
    private final int z;

    public PlayerAddMessage(final PlayerCache player, final int x, final int z) {
        super("magicmap:player_add");
        this.player = player;
        this.x = x;
        this.z = z;
    }
}
