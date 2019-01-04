package com.cavetale.magicmap;

import org.bukkit.map.MapCursorCollection;

final class Session {
    long lastRender;
    boolean rendering;
    boolean cursoring;
    boolean debug;
    String world = "";
    int centerX, centerZ;
    boolean partial;
    // Results
    MapCache pasteMap;
    MapCursorCollection pasteCursors;
}
