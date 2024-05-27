package com.cavetale.magicmap.webserver;

import com.cavetale.core.playercache.PlayerCache;
import com.cavetale.webserver.message.ClientMessage;
import java.util.List;

public final class PlayerListMessage extends ClientMessage {
    private final List<PlayerCache> players;

    public PlayerListMessage(final List<PlayerCache> players) {
        super("magicmap:player_list");
        this.players = players;
    }
}
