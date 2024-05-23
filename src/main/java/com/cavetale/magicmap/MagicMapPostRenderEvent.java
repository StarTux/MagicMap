package com.cavetale.magicmap;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

@Getter
public final class MagicMapPostRenderEvent extends Event {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final Player player;
    private final MapCache mapCache;
    private final String worldName;
    private final int centerX;
    private final int centerZ;
    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;

    public MagicMapPostRenderEvent(final Player player, final MapCache mapCache, final String worldName, final int centerX, final int centerZ) {
        this.player = player;
        this.mapCache = mapCache;
        this.worldName = worldName;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.minX = centerX - 63;
        this.minZ = centerZ - 63;
        this.maxX = centerX + 64;
        this.maxZ = centerZ + 64;
    }

    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
