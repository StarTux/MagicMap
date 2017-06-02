package com.winthier.minimap;

import com.winthier.claims.Claim;
import com.winthier.claims.bukkit.BukkitClaimsPlugin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;

final class ClaimRenderer {
    private final Comparator<Claim> claimComparator = new Comparator<Claim>() {
            @Override
            public int compare(Claim a, Claim b) {
                if (a.getSuperClaim() == null && b.getSuperClaim() != null) {
                    return 1;
                } else if (a.getSuperClaim() != null && b.getSuperClaim() == null) {
                    return -1;
                } else {
                    return 0;
                }
            }
        };

    void render(MiniMapPlugin plugin, MapCanvas canvas, Player player, int ax, int az) {
        BukkitClaimsPlugin claims = (BukkitClaimsPlugin)plugin.getServer().getPluginManager().getPlugin("Claims");
        int[] coords = new int[4];
        List<Claim> claimList = new ArrayList<>(claims.findClaimsNear(player.getLocation(), 128));
        Collections.sort(claimList, claimComparator);
        for (Claim claim: claimList) {
            int colorA, colorB, fontA, fontB;
            if (claim.getSuperClaim() == null) {
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
            coords[0] = claim.getWestBorder() - ax;
            if (coords[0] > 127) continue;
            coords[1] = claim.getNorthBorder() - az;
            if (coords[1] > 127) continue;
            coords[2] = claim.getEastBorder() - ax;
            if (coords[2] < 0) continue;
            coords[3] = claim.getSouthBorder() - az;
            if (coords[3] < 5) continue;
            for (int i = 0; i < 4; i += 1) {
                if (coords[i] < 0) coords[i] = 0;
                if (coords[i] > 127) coords[i] = 127;
            }
            if (coords[1] < 5) coords[1] = 5;
            for (int x = coords[0]; x <= coords[2]; x += 1) {
                dottedLine(canvas, x, coords[1], colorA, colorB);
                dottedLine(canvas, x, coords[3], colorA, colorB);
            }
            for (int z = coords[1]; z <= coords[3]; z += 1) {
                dottedLine(canvas, coords[0], z, colorA, colorB);
                dottedLine(canvas, coords[2], z, colorA, colorB);
            }
            String claimName;
            if (claim.isAdminClaim()) {
                claimName = "Admin";
            } else {
                claimName = claim.getOwnerName();
            }
            int width = coords[2] - coords[0] - 4;
            int height = coords[3] - coords[1] - 4;
            if (width > 16) {
                plugin.getFont4x4().print(canvas, claimName, coords[0] + 2, coords[1] + 2, width, height, fontA, fontB);
            }
        }
    }

    private void dottedLine(MapCanvas canvas, int x, int y, int colorA, int colorB) {
        int dx = x / 2;
        int dy = y / 2;
        if ((dx % 2 == 0) ^ (dy % 2 == 0)) {
            canvas.setPixel(x, y, (byte)colorA);
        } else {
            canvas.setPixel(x, y, (byte)colorB);
        }
    }
}
