package com.winthier.minimap;

import com.winthier.creative.CreativePlugin;
import com.winthier.creative.Warp;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapView;

@RequiredArgsConstructor
final class CreativeRenderer {
    private final MiniMapPlugin plugin;

    void render(MapCanvas canvas, Player player, int ax, int az) {
        String worldName = player.getWorld().getName();
        for (Warp warp: CreativePlugin.getInstance().getWarps().values()) {
            if (!worldName.equals(warp.getWorld())) continue;
            int x = (int)warp.getX() - ax - (plugin.getFont4x4().widthOf(warp.getDisplayName()) / 2);
            int y = (int)warp.getZ() - az - 2;
            if (x < 0 || x > 127) continue;
            if (y < 0 || y > 127) continue;
            // MapCursor cursor = Util.makeCursor(MapCursor.Type.SMALL_WHITE_CIRCLE, warp.getLocation(), ax, az);
            // MapCursorCollection cursors = canvas.getCursors();
            // cursors.addCursor(cursor);
            // canvas.setCursors(cursors);
            plugin.getFont4x4().print(canvas, warp.getDisplayName(), x, y, -1, -1, (byte)MapPalette.WHITE + 2, (byte)MapPalette.DARK_GRAY + 3);
        }
    }
}
