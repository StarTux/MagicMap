package com.winthier.minimap;

import org.bukkit.map.MapCursorCollection;

final class Session {
    long lastRender;
    boolean rendering;
    boolean cursoring;
    boolean debug;
    int centerX, centerZ;
    // Results
    MapCache pasteMap;
    MapCursorCollection pasteCursors;
}
