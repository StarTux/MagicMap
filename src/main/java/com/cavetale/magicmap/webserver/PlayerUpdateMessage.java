package com.cavetale.magicmap.webserver;

import com.cavetale.webserver.message.ClientMessage;
import java.util.UUID;

public final class PlayerUpdateMessage extends ClientMessage {
    private final UUID player;
    private final int x;
    private final int z;

    public PlayerUpdateMessage(final UUID player, final int x, final int z) {
        super("magicmap:player_update");
        this.player = player;
        this.x = x;
        this.z = z;
    }
}
