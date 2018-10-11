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
    private long lastRender;
    private final Map<Class<?>, Object> storage = new HashMap<>();
    private boolean debug;
    private MapCache drawMap, lastMap;

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
