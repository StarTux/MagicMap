package com.winthier.minimap;

import com.winthier.claims.Claim;
import com.winthier.claims.bukkit.BukkitClaimsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapView;

final class ClaimRenderer {
    void render(MiniMapPlugin plugin, MapCanvas canvas, Player player, int ax, int az) {
        BukkitClaimsPlugin claims = (BukkitClaimsPlugin)plugin.getServer().getPluginManager().getPlugin("Claims");
        int[] coords = new int[4];
        for (Claim claim: claims.findClaimsNear(player.getLocation(), 128)) {
            if (claim.getSuperClaim() != null) continue;
            coords[0] = claim.getWestBorder() - ax;
            if (coords[0] > 127) continue;
            coords[1] = claim.getNorthBorder() - az;
            if (coords[1] > 127) continue;
            coords[2] = claim.getEastBorder() - ax;
            if (coords[2] < 0) continue;
            coords[3] = claim.getSouthBorder() - az;
            if (coords[3] < 0) continue;
            for (int i = 0; i < 4; i += 1) {
                if (coords[i] < 0) coords[i] = 0;
                if (coords[i] > 127) coords[i] = 127;
            }
            for (int x = coords[0]; x <= coords[2]; x += 1) {
                int frameColor = x % 2 == 0 ? Colors.BLACK + 3 : MapPalette.WHITE + 2;
                canvas.setPixel(x, coords[1], (byte)frameColor);
                canvas.setPixel(x, coords[3], (byte)frameColor);
            }
            for (int z = coords[1]; z <= coords[3]; z += 1) {
                int frameColor = z % 2 == 0 ? Colors.BLACK + 3 : MapPalette.WHITE + 2;
                canvas.setPixel(coords[0], z, (byte)frameColor);
                canvas.setPixel(coords[2], z, (byte)frameColor);
            }
        }
    }
}
