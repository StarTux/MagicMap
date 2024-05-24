package com.cavetale.magicmap.event;

import com.cavetale.magicmap.MagicMapCursor;
import com.cavetale.magicmap.MagicMapScale;
import com.cavetale.magicmap.file.WorldBorderCache;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.map.MapCursor;

/**
 * Called every time map cursors are updated for a player.
 * The cursor list will contain all added cursors, including
 * coordinates, players and entities.
 */
@Getter @RequiredArgsConstructor
public final class MagicMapCursorEvent extends Event {
    protected static final HandlerList HANDLER_LIST = new HandlerList();
    private final Player player;
    private final MagicMapScale mapScale;
    private final WorldBorderCache mapArea;
    private final List<MagicMapCursor> cursors;

    public boolean contains(Location location) {
        return mapArea.containsBlock(location.getBlockX(), location.getBlockZ());
    }

    public boolean contains(Block block) {
        return mapArea.containsBlock(block.getX(), block.getZ());
    }

    public boolean contains(int x, int z) {
        return mapArea.containsBlock(x, z);
    }

    public void addCursor(MapCursor.Type cursorType, Location location, Component caption) {
        final var cursor = MagicMapCursor.make(cursorType, location, mapArea.centerX, mapArea.centerZ, mapScale, caption);
        cursors.add(cursor);
    }

    public void addCursor(MapCursor.Type cursorType, Location location) {
        addCursor(cursorType, location, null);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
