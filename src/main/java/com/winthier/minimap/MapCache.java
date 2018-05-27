package com.winthier.minimap;

import org.bukkit.map.MapCanvas;

final class MapCache {
    final int[] pixels = new int[128 * 128];

    void setPixel(int x, int y, int color) {
        if (x < 0 || x >= 128) return;
        if (y < 0 || y >= 128) return;
        pixels[y * 128 + x] = color;
    }

    int getPixel(int x, int y) {
        if (x < 0 || y >= 128) return 0;
        if (y < 0 || y >= 128) return 0;
        return pixels[y * 128 + x];
    }

    void paste(MapCanvas canvas) {
        for (int y = 0; y < 128; y += 1) {
            for (int x = 0; x < 128; x += 1) {
                canvas.setPixel(x, y, (byte)pixels[y * 128 + x]);
            }
        }
    }
}
