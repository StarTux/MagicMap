package com.cavetale.magicmap;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

@Getter
public final class MagicMapPostRenderEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String worldName;
    private final int centerX;
    private final int centerZ;
    private final MapCache mapCache;

    MagicMapPostRenderEvent(final Player player, final Session session) {
        this.player = player;
        worldName = session.world;
        mapCache = session.pasteMap;
        centerX = session.centerX;
        centerZ = session.centerZ;
    }

    World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    // Necessary event methods

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
