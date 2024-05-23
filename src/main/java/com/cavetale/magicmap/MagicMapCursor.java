package com.cavetale.magicmap;

import lombok.Value;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.map.MapCursor;

@Value
public final class MagicMapCursor {
    private final int x;
    private final int y;
    private final int direction;
    private final MapCursor.Type cursorType;
    private final boolean visible;
    private final Component caption;

    public static MagicMapCursor make(MapCursor.Type cursorType, Location location, int centerX, int centerZ, MagicMapScale mapScale, Component caption) {
        int dir = (int) (location.getYaw() + 11.25f);
        while (dir < 0) dir += 360;
        while (dir > 360) dir -= 360;
        dir = dir * 2 / 45;
        if (dir < 0) dir = 0;
        if (dir > 15) dir = 15;
        int x = (int) Math.floor(2.0 / mapScale.scale * (location.getX() - (double) centerX));
        int y = (int) Math.floor(2.0 / mapScale.scale * (location.getZ() - (double) centerZ));
        if (x < -127) x = -127;
        if (y < -127) y = -127;
        if (x > 127) x = 127;
        if (y > 127) y = 127;
        return new MagicMapCursor(x, y, dir, cursorType, true, caption);
    }

    public MapCursor toMapCursor() {
        return new MapCursor((byte) x, (byte) y, (byte) direction, cursorType, true, caption);
    }
}
