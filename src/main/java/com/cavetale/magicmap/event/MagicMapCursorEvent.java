package com.cavetale.magicmap.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.map.MapCursorCollection;

/**
 * Called every time map cursors are updated for a player.
 * The cursor list will contain all added cursors, including
 * coordinates, players and entities.
 */
@Getter @RequiredArgsConstructor
public final class MagicMapCursorEvent extends Event {
    @Getter protected static HandlerList handlerList = new HandlerList();
    protected final Player player;
    protected final MapCursorCollection cursors;
    protected final int minX;
    protected final int minZ;
    protected final int maxX;
    protected final int maxZ;

    @Override
    public HandlerList getHandlers() {
        return handlerList;
    }
}
