package com.cavetale.magicmap.webserver;

import com.cavetale.webserver.message.ClientMessage;

public final class ScrollMapMessage extends ClientMessage {
    private final int x;
    private final int z;

    public ScrollMapMessage(final int x, final int z) {
        super("magicmap:scroll_map");
        this.x = x;
        this.z = z;
    }
}
