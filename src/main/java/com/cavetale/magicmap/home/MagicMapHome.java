package com.cavetale.magicmap.home;

import com.cavetale.home.Area;
import com.cavetale.home.Claim;
import com.cavetale.home.HomePlugin;
import com.cavetale.home.Subclaim;
import com.cavetale.magicmap.ColorIndex;
import com.cavetale.magicmap.MagicMapPostRenderEvent;
import com.cavetale.magicmap.MapCache;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import static com.cavetale.magicmap.MagicMapPlugin.plugin;

@RequiredArgsConstructor
public final class MagicMapHome implements Listener {
    static final int CLAIM_COLOR = ColorIndex.COLOR_28.bright;
    static final int SUBCLAIM_COLOR = ColorIndex.WHITE.bright;
    static final int BLACK = ColorIndex.BLACK.dark;
    static final int DARK_GRAY = ColorIndex.COLOR_21.dark;

    public MagicMapHome enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin());
        return this;
    }

    private static HomePlugin homePlugin() {
        return HomePlugin.getInstance();
    }

    @EventHandler
    private void onMagicMapPostRender(MagicMapPostRenderEvent event) {
        if (!homePlugin().isLocalHomeWorld(event.getWorldName())) return;
        Area mapArea = new Area(event.getMinX(), event.getMinZ(), event.getMaxX(), event.getMaxZ());
        for (Claim claim : homePlugin().findClaimsInWorld(event.getWorldName())) {
            if (!claim.getArea().overlaps(mapArea)) continue;
            if (claim.isHidden() && !claim.getTrustType(event.getPlayer()).canBuild()) continue;
            drawRect(event.getMapCache(), mapArea, claim.getArea(), CLAIM_COLOR, claim.getOwnerName() + "'s claim");
            for (Subclaim subclaim : claim.getSubclaims()) {
                drawRect(event.getMapCache(), mapArea, subclaim.getArea(), SUBCLAIM_COLOR, null);
            }
        }
    }

    static void drawRect(MapCache mapCache, Area mapArea, Area claimArea, int color, String label) {
        Area drawArea = claimArea.fitWithin(mapArea);
        for (int x = drawArea.ax; x <= drawArea.bx; x += 1) {
            drawDotted(mapCache, mapArea, x, drawArea.ay, color);
            drawDotted(mapCache, mapArea, x, drawArea.by, color);
        }
        for (int y = drawArea.ay; y <= drawArea.by; y += 1) {
            drawDotted(mapCache, mapArea, drawArea.ax, y, color);
            drawDotted(mapCache, mapArea, drawArea.bx, y, color);
        }
        if (label == null) return;
        plugin().getTinyFont()
            .print(label, drawArea.ax - mapArea.ax + 2, drawArea.ay - mapArea.ay + 2,
                   (x, y) -> mapCache.setPixel(x, y, color),
                   (x, y) -> mapCache.setPixel(x, y, DARK_GRAY));
    }

    static void drawDotted(MapCache mapCache, Area mapArea, int worldX, int worldY, int color) {
        int x = worldX - mapArea.ax;
        int y = worldY - mapArea.ay;
        if ((x & 1) == (y & 1)) return;
        mapCache.setPixel(x, y, color);
    }
}
