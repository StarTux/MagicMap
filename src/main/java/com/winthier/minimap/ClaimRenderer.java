package com.winthier.minimap;

import com.winthier.claim.Claim;
import com.winthier.claim.ClaimPlugin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.bukkit.entity.Player;

final class ClaimRenderer {
    private final Comparator<Claim> claimComparator = new Comparator<Claim>() {
            @Override
            public int compare(Claim a, Claim b) {
                if (a.getSuperclaim() == null && b.getSuperclaim() != null) {
                    return 1;
                } else if (a.getSuperclaim() != null && b.getSuperclaim() == null) {
                    return -1;
                } else {
                    return 0;
                }
            }
        };

    void render(MiniMapPlugin plugin, MapCache mapCache, Player player, int ax, int az) {
        ClaimPlugin claimPlugin = (ClaimPlugin)plugin.getServer().getPluginManager().getPlugin("Claim");
        if (claimPlugin == null) return;
        int[] coords = new int[4];
        List<Claim> claimList = new ArrayList<>(claimPlugin.getNearbyClaims(player.getLocation().getBlock(), 128));
        Collections.sort(claimList, claimComparator);
        for (Claim claim: claimList) {
            int colorA, colorB, fontA, fontB;
            if (claim.getSuperclaim() == null) {
                colorA = Colors.WHITE + 2;
                colorB = Colors.WOOL_BLACK + 3;
                fontA = Colors.WHITE + 2;
                fontB = Colors.DARK_GRAY + 3;
            } else {
                colorA = Colors.DARK_GRAY + 3;
                colorB = Colors.LIGHT_GRAY + 2;
                fontA = Colors.WHITE + 1;
                fontB = Colors.DARK_GRAY + 3;
            }
            coords[0] = claim.getRectangle().getWestBorder() - ax;
            if (coords[0] > 127) continue;
            coords[1] = claim.getRectangle().getNorthBorder() - az;
            if (coords[1] > 127) continue;
            coords[2] = claim.getRectangle().getEastBorder() - ax;
            if (coords[2] < 0) continue;
            coords[3] = claim.getRectangle().getSouthBorder() - az;
            if (coords[3] < 5) continue;
            for (int i = 0; i < 4; i += 1) {
                if (coords[i] < 0) coords[i] = 0;
                if (coords[i] > 127) coords[i] = 127;
            }
            if (coords[1] < 5) coords[1] = 5;
            for (int x = coords[0]; x <= coords[2]; x += 1) {
                dottedLine(mapCache, x, coords[1], colorA, colorB);
                dottedLine(mapCache, x, coords[3], colorA, colorB);
            }
            for (int z = coords[1]; z <= coords[3]; z += 1) {
                dottedLine(mapCache, coords[0], z, colorA, colorB);
                dottedLine(mapCache, coords[2], z, colorA, colorB);
            }
            String claimName;
            if (claim.isAdminClaim()) {
                claimName = "Admin";
            } else {
                claimName = claim.getOwnerName();
            }
            plugin.getFont4x4().print(claimName, coords[0] + 2, coords[1] + 2, (x, y, shadow) -> { if (x < coords[2] - 1 && y < coords[3] - 1) mapCache.setPixel(x, y, shadow ? (mapCache.getPixel(x, y) & ~0x3) + 3 : Colors.WHITE + 2); });
        }
    }

    private void dottedLine(MapCache mapCache, int x, int y, int colorA, int colorB) {
        int color = mapCache.getPixel(x, y) & ~0x3;
        if ((x % 2 == 0) ^ (y % 2 == 0)) {
            mapCache.setPixel(x, y, color + 3);
        } else {
            mapCache.setPixel(x, y, Colors.WHITE + 2);
        }
    }
}
