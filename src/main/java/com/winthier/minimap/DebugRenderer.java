package com.winthier.minimap;

import lombok.RequiredArgsConstructor;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;

@RequiredArgsConstructor
public class DebugRenderer extends MapRenderer {
    private final MiniMapPlugin plugin;
    static class Flag { };

    @Override
    public void render(MapView view, MapCanvas canvas, Player player) {
        if (plugin.getSession(player).getStorage().get(Flag.class) != null) return;
        plugin.getSession(player).getStorage().put(Flag.class, new Flag());
        for (int dz = 0; dz < 4; dz += 1) {
            for (int dx = 0; dx < 128; dx += 1) {
                if (dz < 2) {
                    canvas.setPixel(dx, dz, (byte)(dx / 4 * 4));
                } else {
                    canvas.setPixel(dx, dz, (byte)dx);
                }
            }
        }
        for (int dx = 0; dx < 128; dx += 8) {
            int v = dx / 4;
            // while (v >= 10) v -= 10;
            int color = (dx / 8) % 2 == 0 ? Colors.WHITE + 2 : Colors.LIGHT_GRAY + 2;
            plugin.getFont4x4().print(canvas, "" + v, dx, 5, -1, -1, color, Colors.BLACK);
            // canvas.drawText(dx, 4, MinecraftFont.Font, "" + v);
        }
        player.sendMessage("" + (int)player.getLocation().getYaw() + " " + (int)player.getLocation().getPitch());
    }
}
