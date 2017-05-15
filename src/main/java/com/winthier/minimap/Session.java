package com.winthier.minimap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;

@Data
final class Session {
    private long lastRender;
    private final Map<Class<?>, Object> storage = new HashMap<>();
}
