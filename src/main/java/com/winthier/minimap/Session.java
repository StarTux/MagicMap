package com.winthier.minimap;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;

@Data
final class Session {
    private long lastRender;
    private final Map<Class<?>, Object> storage = new HashMap<>();
    private boolean debug;

    void store(Object o) {
        storage.put(o.getClass(), o);
    }

    <T> T fetch(Class<T> clazz) {
        return clazz.cast(storage.get(clazz));
    }

    <T> T remove(Class<T> clazz) {
        return clazz.cast(storage.remove(clazz));
    }
}
