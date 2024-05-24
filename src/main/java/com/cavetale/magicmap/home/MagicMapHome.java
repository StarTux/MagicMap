package com.cavetale.magicmap.home;

import com.cavetale.core.struct.Vec2i;
import com.cavetale.home.Area;
import com.cavetale.home.Claim;
import com.cavetale.home.HomePlugin;
import com.cavetale.home.Subclaim;
import com.cavetale.magicmap.ColorIndex;
import com.cavetale.magicmap.event.MagicMapPostRenderEvent;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import static com.cavetale.magicmap.MagicMapPlugin.plugin;

@RequiredArgsConstructor
public final class MagicMapHome implements Listener {
    public MagicMapHome enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin());
        return this;
    }

    private static HomePlugin homePlugin() {
        return HomePlugin.getInstance();
    }

    @EventHandler
    private void onMagicMapPostRender(MagicMapPostRenderEvent event) {
        if (!homePlugin().isLocalHomeWorld(event.getRendered().getWorldName())) return;
        final Area mapArea = new Area(event.getMapArea().getMinX(), event.getMapArea().getMinZ(),
                                      event.getMapArea().getMaxX(), event.getMapArea().getMaxZ());
        for (Claim claim : homePlugin().findClaimsInWorld(event.getRendered().getWorldName())) {
            if (!claim.getArea().overlaps(mapArea)) continue;
            if (claim.isHidden() && !claim.getTrustType(event.getPlayer()).canBuild()) continue;
            final String caption = claim.getName() != null ? claim.getName() : claim.getOwnerName();
            drawRect(event, claim.getArea(), ColorIndex.COLOR_25, caption);
            for (Subclaim subclaim : claim.getSubclaims()) {
                drawRect(event, subclaim.getArea(), ColorIndex.WHITE, null);
            }
        }
    }

    static void drawRect(MagicMapPostRenderEvent event, Area claimArea, ColorIndex color, String label) {
        Vec2i min = event.clampMap(event.worldToMap(claimArea.ax, claimArea.ay));
        Vec2i max = event.clampMap(event.worldToMap(claimArea.bx, claimArea.by));
        for (int x = min.x; x <= max.x; x += 1) {
            drawDotted(event, x, min.z, color);
            drawDotted(event, x, max.z, color);
        }
        for (int z = min.z; z <= max.z; z += 1) {
            drawDotted(event, min.x, z, color);
            drawDotted(event, max.x, z, color);
        }
        if (label != null) {
            plugin().getTinyFont()
                .print(label, min.x + 2, min.z + 2,
                       (x, y) -> {
                           if (x <= min.x || x >= max.x || y <= min.z || y >= max.z) return;
                           event.getImage().setRGB(x, y, color.brightRgb);
                       },
                       (x, y) -> {
                           if (x <= min.x || x >= max.x || y <= min.z || y >= max.z) return;
                           event.getImage().setRGB(x, y, color.darkRgb);
                       });
        }
    }

    static void drawDotted(MagicMapPostRenderEvent event, int x, int y, ColorIndex color) {
        final boolean b = (x & 1) == (y & 1);
        event.getImage().setRGB(x, y, b ? color.brightRgb : color.normalRgb);
    }
}
