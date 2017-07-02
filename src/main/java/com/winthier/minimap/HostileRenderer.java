package com.winthier.minimap;

import com.winthier.hostile.HostilePlugin.Loc;
import com.winthier.hostile.HostilePlugin;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;

@RequiredArgsConstructor
final class HostileRenderer {
    private final MiniMapPlugin plugin;

    void updateCursors(MapCursorCollection cursors, Player player, int ax, int az) {
        Map<Loc, Integer> hives = ((HostilePlugin)plugin.getServer().getPluginManager().getPlugin("Hostile")).getHiveBlocks(player);
        if (hives.isEmpty()) return;
        for (Loc loc: hives.keySet()) {
            cursors.addCursor(Util.makeCursor(MapCursor.Type.WHITE_CROSS, loc.getBlock(), ax, az));
        }
    }
}
