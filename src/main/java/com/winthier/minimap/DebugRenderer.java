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
        for (int dx = 0; dx < 128; dx += 8) {
            int v = dx / 4;
            int color = (dx / 8) % 2 == 0 ? Colors.WHITE + 2 : Colors.LIGHT_GRAY + 2;
            plugin.getFont4x4().print("" + v, dx, 5, (mx, my, mb) -> canvas.setPixel(mx, my, (byte)(!mb ? color : Colors.WOOL_BLACK)));
            plugin.getFont4x4().print("" + (v + 32), dx, 118, (mx, my, mb) -> canvas.setPixel(mx, my, (byte)(!mb ? color : Colors.WOOL_BLACK)));
        }
        MapCursorCollection cursors = canvas.getCursors();
        int i = 0;
        for (MapCursor.Type type: MapCursor.Type.values()) {
            i += 1;
            cursors.addCursor(new MapCursor((byte)-100, (byte)(-127 + i * 20 + 10), (byte)8, type.getValue(), true));
            plugin.getFont4x4().print(type.name(), 20, i * 10 + 5, (mx, my, mb) -> canvas.setPixel(mx, my, (byte)(!mb ? Colors.WOOL_BLACK : Colors.WHITE)));
        }
        canvas.setCursors(cursors);
    }
}
