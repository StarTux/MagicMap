package com.winthier.minimap;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.map.MapCursor;

final class Util {
    private Util() { }

    static MapCursor makeCursor(MapCursor.Type cursorType, Location location, int ax, int az) {
        int dir = (int)(location.getYaw() + 11.25f);
        while (dir < 0) dir += 360;
        while (dir > 360) dir -= 360;
        dir = dir * 2 / 45;
        if (dir < 0) dir = 0;
        if (dir > 15) dir = 15;
        int x = (location.getBlockX() - ax - 64) * 2;
        int y = (location.getBlockZ() - az - 64) * 2;
        if (x < -127) x = -127;
        if (y < -119) y = -119;
        if (x > 127) x = 127;
        if (y > 127) y = 127;
        return new MapCursor((byte)x, (byte)y, (byte)dir, cursorType.getValue(), true);
    }

    static MapCursor makeCursor(MapCursor.Type cursorType, Block block, int ax, int az) {
        int x = (block.getX() - ax - 64) * 2;
        int y = (block.getZ() - az - 64) * 2;
        if (x < -127) x = -127;
        if (y < -119) y = -119;
        if (x > 127) x = 127;
        if (y > 127) y = 127;
        return new MapCursor((byte)x, (byte)y, (byte)8, cursorType.getValue(), true);
    }

    static MapCursor makeCursor(MapCursor.Type cursorType, int x, int y, int rot) {
        return new MapCursor((byte)((x - 64) * 2), (byte)((y - 64) * 2), (byte)rot, cursorType.getValue(), true);
    }
}
