package com.cavetale.magicmap.webserver;

import com.cavetale.webserver.message.ClientMessage;
import java.util.UUID;

public final class PlayerRemoveMessage extends ClientMessage {
    private final UUID player;

    public PlayerRemoveMessage(final UUID player) {
        super("magicmap:player_remove");
        this.player = player;
    }
}
