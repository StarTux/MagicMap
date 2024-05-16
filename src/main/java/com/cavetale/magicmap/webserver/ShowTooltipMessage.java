package com.cavetale.magicmap.webserver;

import com.cavetale.webserver.message.ClientMessage;

public final class ShowTooltipMessage extends ClientMessage {
    private final String html;

    public ShowTooltipMessage(final String html) {
        super("magicmap:show_tooltip");
        this.html = html;
    }
}
