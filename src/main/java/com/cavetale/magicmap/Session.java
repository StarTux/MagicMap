package com.cavetale.magicmap;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.bukkit.map.MapCursorCollection;

@RequiredArgsConstructor
final class Session {
    final UUID player;
    boolean forceUpdate;
    boolean rendering;
    boolean cursoring;
    boolean debug;
    String world = "";
    int centerX;
    int centerZ;
    boolean partial;
    long cooldown;
    long lastRender;
    long cursorCooldown;
    // Results
    MapCache pasteMap;
    MapCursorCollection pasteCursors;
    String shownArea;
}
