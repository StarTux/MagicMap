package com.cavetale.magicmap;

import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

@RequiredArgsConstructor
final class SyncMapRenderer {
    public static final int NORMAL = 0;
    public static final int LIGHT = 1;
    public static final int BRIGHT = 2;
    public static final int DARK = 3;
    private final MagicMapPlugin plugin;
    private final MapColor mapColor;
    private final World world;
    private final Session session;
    private final RenderType type;
    // center coords
    private final int centerX;
    private final int centerZ;
    private int iterX;
    private int iterY;
    private final long dayTime;
    private final MapCache mapCache = new MapCache();
    private boolean partial = false;

    /**
     * Run by the plugin's mainQueue.
     */
    public boolean run() {
        for (int i = 0; i < 1024; i += 1) {
            if (iterY >= 128) {
                session.pasteMap = mapCache;
                session.rendering = false;
                session.centerX = centerX;
                session.centerZ = centerZ;
                session.world = world.getName();
                session.partial = partial;
                MagicMapPostRenderEvent.call(session);
                return false;
            }
            final int canvasX = iterX;
            final int canvasY = iterY;
            iterX += 1;
            if (iterX >= 128) {
                iterX = 0;
                iterY += 1;
            }
            // x/z are world coords of the block to render
            int x = canvasX + centerX - 63;
            int z = canvasY + centerZ - 63;
            int highest = highest(x, z);
            if (highest < 0) {
                mapCache.setPixel(canvasX, canvasY, (29 << 2) + 3);
                continue;
            }
            Material mat = world.getBlockAt(x, highest, z).getType();
            int color = mapColor.of(mat);
            if (color == 48) { // water
                int lbottom = highest - 1;
                while (lbottom > 0 && color == mapColor.of(world.getBlockAt(x, lbottom, z)
                                                           .getType())) {
                    lbottom -= 1;
                }
                int depth = highest - lbottom;
                if (depth <= 2) {
                    color += BRIGHT;
                } else if (depth <= 4) {
                    color += (canvasX & 1) == (canvasY & 1) ? BRIGHT : LIGHT;
                } else if (depth <= 6) {
                    color += LIGHT;
                } else if (depth <= 8) {
                    color += (canvasX & 1) == (canvasY & 1) ? NORMAL : LIGHT;
                } else if (depth <= 12) {
                    color += NORMAL;
                } else if (depth <= 16) {
                    color += (canvasX & 1) == (canvasY & 1) ? NORMAL : DARK;
                } else {
                    color += DARK;
                }
                mapCache.setPixel(canvasX, canvasY, color);
            } else {
                // Neighbor block where the sunlight comes from.
                int lx;
                int ly;
                if (dayTime < 1500) {
                    lx = 1; ly = 0;
                } else if (dayTime < 4500) {
                    lx = 1; ly = -1;
                } else if (dayTime < 7500) {
                    lx = 0; ly = -1;
                } else if (dayTime < 10500) {
                    lx = -1; ly = -1;
                } else if (dayTime < 13500) {
                    lx = -1; ly = 0;
                } else if (dayTime < 16500) {
                    lx = -1; ly = 1;
                } else if (dayTime < 19500) {
                    lx = 0; ly = 1;
                } else if (dayTime < 22500) {
                    lx = 1; ly = 1;
                } else {
                    lx = 1; ly = 0;
                }
                int nx = x + lx;
                int nz = z + ly;
                // 1 == Bright
                // 2 == Super Bright
                // 3 == Dark
                int highestN = highest(nx, nz);
                if (highestN >= 0) {
                    if (highest > highestN) {
                        color += BRIGHT;
                    } else if (highest < highestN) {
                        color += NORMAL;
                    } else {
                        color += LIGHT;
                    }
                }
                mapCache.setPixel(canvasX, canvasY, color);
            }
        }
        return true;
    }

    private int highest(int x, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            partial = true;
            return -1;
        }
        final int min = world.getMinHeight();
        final int max = world.getMaxHeight();
        switch (type) {
        case NETHER: {
            int y = max;
            // skip blocks
            while (y >= min && !world.getBlockAt(x, y, z).isEmpty()) y -= 1;
            // skip air
            while (y >= min && world.getBlockAt(x, y, z).isEmpty()) y -= 1;
            // skip transparent
            while (y >= min && mapColor.of(world.getBlockAt(x, y, z).getType()) == 0) y -= 1;
            return y;
        }
        case CAVE: {
            int y = max;
            // skip air
            while (y >= min && world.getBlockAt(x, y, z).isEmpty()) y -= 1;
            while (y >= min) { // skip sunlit blocks
                Block block = world.getBlockAt(x, y, z);
                if (!block.isEmpty() || block.getLightFromSky() == 15) {
                    y -= 1;
                } else {
                    break;
                }
            }
            // skip air
            while (y >= min && world.getBlockAt(x, y, z).isEmpty()) y -= 1;
            // skip transparent
            while (y >= min && mapColor.of(world.getBlockAt(x, y, z).getType()) == 0) y -= 1;
            return y;
        }
        case SURFACE:
        default: {
            int y = max;
            // skip air
            while (y >= min && world.getBlockAt(x, y, z).isEmpty()) y -= 1;
            // skip transparent
            while (y >= min && mapColor.of(world.getBlockAt(x, y, z).getType()) == 0) y -= 1;
            return y;
        }
        }
    }

}
