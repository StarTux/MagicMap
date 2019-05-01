package com.cavetale.magicmap;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.bukkit.map.MapCursorCollection;

@RequiredArgsConstructor
final class Session {
    final UUID player;
    long lastRender;
    boolean forceUpdate;
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
