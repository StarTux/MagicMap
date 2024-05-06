package com.cavetale.magicmap.webserver;

import com.cavetale.webserver.WebserverPlugin;

/**
 * Register our content delivery.
 */
public final class WebserverManager {
    private MagicMapContentDelivery contentDelivery;
    public boolean enable() {
        if (!WebserverPlugin.plugin().isWebserverEnabled()) return false;
        contentDelivery = new MagicMapContentDelivery();
        contentDelivery.enable();
        WebserverPlugin.plugin().getContentManager().register(contentDelivery);
        return true;
    }

    public void disable() {
        WebserverPlugin.plugin().getContentManager().unregister(contentDelivery);
        contentDelivery = null;
    }
}
