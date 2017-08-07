package com.winthier.minimap;

import com.winthier.creative.CreativePlugin;
import com.winthier.creative.Warp;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
final class CreativeRenderer {
    private final MiniMapPlugin plugin;

    void render(MapCache mapCache, Player player, int ax, int az) {
        String worldName = player.getWorld().getName();
        for (Warp warp: CreativePlugin.getInstance().getWarps().values()) {
            if (!worldName.equals(warp.getWorld())) continue;
            int x = (int)warp.getX() - ax - (plugin.getFont4x4().widthOf(warp.getDisplayName()) / 2);
            int y = (int)warp.getZ() - az - 2;
            if (x < 0 || x > 127) continue;
            if (y < 0 || y > 127) continue;
            plugin.getFont4x4().print(warp.getDisplayName(), x, y, (mx, my, mb) -> mapCache.setPixel(mx, my, !mb ? Colors.WHITE + 2 : Colors.DARK_GRAY + 3));
        }
    }
}
