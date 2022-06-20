package com.cavetale.magicmap.util;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.map.MapCursor;

public final class Cursors {
    private Cursors() { }

    public static MapCursor make(MapCursor.Type cursorType, Location location, int centerX, int centerZ) {
        int dir = (int) (location.getYaw() + 11.25f);
        while (dir < 0) dir += 360;
        while (dir > 360) dir -= 360;
        dir = dir * 2 / 45;
        if (dir < 0) dir = 0;
        if (dir > 15) dir = 15;
        int x = (location.getBlockX() - centerX) * 2;
        int y = (location.getBlockZ() - centerZ) * 2;
        if (x < -127) x = -127;
        if (y < -127) y = -127;
        if (x > 127) x = 127;
        if (y > 127) y = 127;
        return new MapCursor((byte) x, (byte) y, (byte) dir, cursorType, true);
    }

    public static MapCursor make(MapCursor.Type cursorType, Block block, int centerX, int centerZ) {
        int x = (block.getX() - centerX) * 2;
        int y = (block.getZ() - centerZ) * 2;
        if (x < -127) x = -127;
        if (y < -127) y = -127;
        if (x > 127) x = 127;
        if (y > 127) y = 127;
        return new MapCursor((byte) x, (byte) y, (byte) 8, cursorType, true);
    }

    public static MapCursor make(MapCursor.Type cursorType, int x, int y, int rot) {
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (x > 127) x = 127;
        if (y > 127) y = 127;
        return new MapCursor((byte) ((x - 64) * 2), (byte) ((y - 64) * 2), (byte) rot, cursorType, true);
    }
}
