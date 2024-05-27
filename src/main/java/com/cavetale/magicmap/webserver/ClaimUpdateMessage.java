package com.cavetale.magicmap.webserver;

import com.cavetale.home.Area;
import com.cavetale.webserver.message.ClientMessage;

public final class ClaimUpdateMessage extends ClientMessage {
    final int claimId;
    final Area area; // May be null
    final String name;

    public ClaimUpdateMessage(final int claimId, final Area area, final String name) {
        super("magicmap:claim_update");
        this.claimId = claimId;
        this.area = area;
        this.name = name;
    }
}
