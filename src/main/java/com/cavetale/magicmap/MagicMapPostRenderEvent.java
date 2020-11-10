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
    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;
    private final MapCache mapCache;

    MagicMapPostRenderEvent(final Player player, final Session session) {
        this.player = player;
        worldName = session.world;
        mapCache = session.pasteMap;
        centerX = session.centerX;
        centerZ = session.centerZ;
        minX = centerX - 63;
        minZ = centerZ - 63;
        maxX = centerX + 64;
        maxZ = centerZ + 64;
    }

    public World getWorld() {
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

    public static void call(Session session) {
        Player player = Bukkit.getPlayer(session.player);
        if (player == null) return;
        MagicMapPostRenderEvent event = new MagicMapPostRenderEvent(player, session);
        Bukkit.getPluginManager().callEvent(event);
    }
}
