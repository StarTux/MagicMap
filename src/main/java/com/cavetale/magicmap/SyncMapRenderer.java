package com.cavetale.magicmap;

import com.cavetale.area.struct.Area;
import com.cavetale.area.struct.AreasFile;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;

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
    private final int minY;
    private final int maxY;
    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;

    SyncMapRenderer(final MagicMapPlugin plugin,
                    final MapColor mapColor,
                    final World world,
                    final Session session,
                    final RenderType type,
                    final int centerX, final int centerZ) {
        this.plugin = plugin;
        this.mapColor = mapColor;
        this.world = world;
        this.session = session;
        this.type = type;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.dayTime = world.getTime();
        this.minX = centerX - 63;
        this.minZ = centerZ - 63;
        this.maxX = centerX + 64;
        this.maxZ = centerZ + 64;
        this.minY = world.getMinHeight();
        this.maxY = world.getMaxHeight();
    }

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
                renderAreas();
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
            if (highest < minY) {
                mapCache.setPixel(canvasX, canvasY, (29 << 2) + 3);
                continue;
            }
            Block block = world.getBlockAt(x, highest, z);
            int color;
            if (block.getType() == Material.WATER || (block.getBlockData() instanceof Waterlogged w && w.isWaterlogged())) {
                color = mapColor.of(Material.WATER);
                while (block.getY() > minY && (block.getType() == Material.WATER || (block.getBlockData() instanceof Waterlogged w2 && w2.isWaterlogged()))) {
                    block = block.getRelative(0, -1, 0);
                }
                int depth = highest - block.getY();
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
            } else if (block.getType() == Material.LAVA) {
                color = mapColor.of(Material.LAVA);
                while (block.getY() > minY && block.getType() == Material.LAVA) {
                    block = block.getRelative(0, -1, 0);
                }
                int depth = highest - block.getY();
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
                color = mapColor.of(block.getType());
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
        switch (type) {
        case NETHER: {
            int y = 127;
            // skip blocks
            while (y >= minY && !world.getBlockAt(x, y, z).isEmpty()) y -= 1;
            // skip air
            while (y >= minY && world.getBlockAt(x, y, z).isEmpty()) y -= 1;
            // skip transparent
            while (y >= minY && mapColor.of(world.getBlockAt(x, y, z).getType()) == 0) y -= 1;
            return y;
        }
        case CAVE: {
            int y = maxY;
            // skip air
            while (y >= minY && world.getBlockAt(x, y, z).isEmpty()) y -= 1;
            while (y >= minY) { // skip sunlit blocks
                Block block = world.getBlockAt(x, y, z);
                if (!block.isEmpty() || block.getLightFromSky() == 15) {
                    y -= 1;
                } else {
                    break;
                }
            }
            // skip air
            while (y >= minY && world.getBlockAt(x, y, z).isEmpty()) y -= 1;
            // skip transparent
            while (y >= minY && mapColor.of(world.getBlockAt(x, y, z).getType()) == 0) y -= 1;
            return y;
        }
        case SURFACE:
        default: {
            int y = maxY;
            // skip air
            while (y >= minY && world.getBlockAt(x, y, z).isEmpty()) y -= 1;
            // skip transparent
            while (y >= minY && mapColor.of(world.getBlockAt(x, y, z).getType()) == 0) y -= 1;
            return y;
        }
        }
    }

    private void drawDotted(int worldX, int worldZ, int color) {
        int x = worldX - minX;
        int z = worldZ - minZ;
        if ((x & 1) == (z & 1)) return;
        mapCache.setPixel(x, z, color);
    }

    private void drawRect(String label, Area area, int color) {
        for (int x = area.min.x; x <= area.max.x; x += 1) {
            drawDotted(x, area.min.z, color);
            drawDotted(x, area.max.z, color);
        }
        for (int z = area.min.z; z <= area.max.z; z += 1) {
            drawDotted(area.min.x, z, color);
            drawDotted(area.max.x, z, color);
        }
        if (label != null) {
            plugin.getTinyFont().print(label, area.min.x - minX + 2, area.min.z - minZ + 2,
                                       (x, y) -> {
                                           if (x >= area.max.x - minX - 2) return;
                                           mapCache.setPixel(x, y, color);
                                       },
                                       (x, y) -> {
                                           if (x >= area.max.x - minX - 2) return;
                                           mapCache.setPixel(x, y, 21 * 4 + 0);
                                       });
        }
    }

    private void renderAreas() {
        if (session.shownArea == null) return;
        AreasFile areasFile = AreasFile.load(world, session.shownArea);
        if (areasFile == null) return;
        final int areaColor = 8 * 4 + BRIGHT;
        for (String name : areasFile.getAreas().keySet()) {
            var list = areasFile.getAreas().get(name);
            if (list.isEmpty()) continue;
            Area area = list.get(0);
            drawRect(name, area, areaColor);
            for (int i = 1; i < list.size(); i += 1) {
                Area area2 = list.get(i);
                if (area.contains(area2.getMin()) && area.contains(area2.getMax())) continue;
                drawRect(name, area2, areaColor);
            }
        }
    }
}
