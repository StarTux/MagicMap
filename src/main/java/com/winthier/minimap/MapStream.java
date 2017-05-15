package com.winthier.mini_map;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapFont;
import org.bukkit.map.MinecraftFont;

@Getter @Setter
class MapStream {
    private MapCanvas canvas;
    private MinecraftFont font;
    private int color = 4;
    private int x, y;

    MapStream(MapCanvas canvas) {
        this.canvas = canvas;
        this.font = MinecraftFont.Font;
    }
}
