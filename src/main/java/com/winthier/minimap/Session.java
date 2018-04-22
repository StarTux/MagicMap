package com.winthier.minimap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
final class Session {
    enum Mode {
        MAP, MENU;
    }
    @Data @RequiredArgsConstructor @AllArgsConstructor
    static class Rect {
        final int x, y, width, height;
        final String target;
        Runnable onClick = null;
        boolean contains(int x, int y) {
            if (x < this.x || x >= this.x + width) return false;
            if (y < this.y || y >= this.y + height) return false;
            return true;
        }
    }

    private long lastRender;
    private final Map<Class<?>, Object> storage = new HashMap<>();
    private boolean debug;
    private int altitude;
    private boolean drawAltitude;
    private Mode mode = Mode.MAP;
    private boolean menuNeedsUpdate = false;
    private MapCache drawMap, lastMap;
    private float mouseX = 64f, mouseY = 64f;
    private String menuLocation = "/";
    private final List<Rect> menuRects = new ArrayList<>();

    void store(Object o) {
        storage.put(o.getClass(), o);
    }

    <T> T fetch(Class<T> clazz) {
        return clazz.cast(storage.get(clazz));
    }

    <T> T remove(Class<T> clazz) {
        return clazz.cast(storage.remove(clazz));
    }

    MapCache removeDrawMap() {
        MapCache result = drawMap;
        drawMap = null;
        return result;
    }
}
