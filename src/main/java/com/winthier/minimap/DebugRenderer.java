package com.winthier.minimap;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;

@RequiredArgsConstructor
public class DebugRenderer {
    private final MiniMapPlugin plugin;

    public void render(MapCanvas canvas, Player player, int ax, int az) {
        for (int dz = 0; dz < 4; dz += 1) {
            for (int dx = 0; dx < 128; dx += 1) {
                if (dz < 2) {
                    canvas.setPixel(dx, dz, (byte)(dx / 4 * 4));
                } else {
                    canvas.setPixel(dx, dz, (byte)dx);
                }
            }
            for (int dx = 0; dx < 128; dx += 1) {
                int color = dx + 128;
                if (dz < 2) {
                    canvas.setPixel(dx, dz + 123, (byte)(color / 4 * 4));
                } else {
                    canvas.setPixel(dx, dz + 123, (byte)color);
                }
            }
        }
    }
}
