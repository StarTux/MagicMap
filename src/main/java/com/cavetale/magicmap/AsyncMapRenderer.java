package com.cavetale.magicmap;

import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.plugin.IllegalPluginAccessException;

@RequiredArgsConstructor
final class AsyncMapRenderer implements Runnable {
    public static final int NORMAL = 0;
    public static final int LIGHT = 1;
    public static final int BRIGHT = 2;
    public static final int DARK = 3;
    private final MagicMapPlugin plugin;
    private final Session session;
    private final RenderType type;
    private final String worldDisplayName;
    private final int centerX, centerZ; // center coords
    private final long dayTime;
    private boolean partial = false;
    //
    private final MapCache mapCache = new MapCache();
    final HashMap<Long, ChunkSnapshot> chunks = new HashMap<>();
    // Side effect variables
    private ChunkSnapshot chunkSnapshot;
    private int innerX, innerZ;

    @Override
    public void run() {
        // Top left world coords
        int ax = this.centerX - 63;
        int az = this.centerZ - 63;
        // Bottom right world coords
        int bx = this.centerX + 64;
        int bz = this.centerZ + 64;
        for (int canvasY = 0; canvasY < 128; canvasY += 1) {
            for (int canvasX = 0; canvasX < 128; canvasX += 1) {
                // x/z are world coords of the block to render
                int x = canvasX + ax;
                int z = canvasY + az;
                int highest = highestBlockAt(x, z);
                if (highest < 0) {
                    this.mapCache.setPixel(canvasX, canvasY, (29 << 2) + 3);
                    continue;
                }
                Material mat = this.chunkSnapshot.getBlockType(this.innerX, highest, this.innerZ);
                int color = MapColor.of(mat);
                if (color == 48) { // water
                    int lbottom = highest - 1;
                    while (lbottom > 0 && color == MapColor.of(this.chunkSnapshot.getBlockType(this.innerX, lbottom, this.innerZ))) {
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
                    this.mapCache.setPixel(canvasX, canvasY, color);
                } else {
                    // Neighbor block where the sunlight comes from.
                    int lx, ly;
                    if (this.dayTime < 1500) {
                        lx = 1; ly = 0;
                    } else if (this.dayTime < 4500) {
                        lx = 1; ly = -1;
                    } else if (this.dayTime < 7500) {
                        lx = 0; ly = -1;
                    } else if (this.dayTime < 10500) {
                        lx = -1; ly = -1;
                    } else if (this.dayTime < 13500) {
                        lx = -1; ly = 0;
                    } else if (this.dayTime < 16500) {
                        lx = -1; ly = 1;
                    } else if (this.dayTime < 19500) {
                        lx = 0; ly = 1;
                    } else if (this.dayTime < 22500) {
                        lx = 1; ly = 1;
                    } else {
                        lx = 1; ly = 0;
                    }
                    int nx = x + lx;
                    int nz = z + ly;
                    // 1 == Bright
                    // 2 == Super Bright
                    // 3 == Dark
                    int highestN = highestBlockAt(nx, nz);
                    if (highestN >= 0) {
                        if (highest > highestN) {
                            color += BRIGHT;
                        } else if (highest < highestN) {
                            color += NORMAL;
                        } else {
                            color += LIGHT;
                        }
                    }
                    this.mapCache.setPixel(canvasX, canvasY, color);
                }
            }
        }
        if (this.worldDisplayName != null) this.plugin.getTinyFont().print(this.mapCache, this.worldDisplayName, 1, 1, 32 + BRIGHT, 116);
        try {
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                    this.session.pasteMap = this.mapCache;
                    this.session.rendering = false;
                    this.session.centerX = this.centerX;
                    this.session.centerZ = this.centerZ;
                    if (this.chunkSnapshot != null) this.session.world = this.chunkSnapshot.getWorldName();
                    this.session.partial = this.partial;
                    this.plugin.callPostEvent(this.session);
                });
        } catch (IllegalPluginAccessException ipae) { }
    }

    /**
     * Side effects: Sets the member following variables:
     * - chunkSnapshot
     * - innerX, innerZ
     *
     * @return the y-coordinate of the highest block if it exists and
     * all member variables were set. -1 otherwise.
     */
    private int highestBlockAt(int x, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        long chunkIndex = ((long)chunkZ << 32) + (long)chunkX;
        ChunkSnapshot snap = this.chunks.get(chunkIndex);
        if (snap == null) {
            this.partial = true;
            return -1;
        }
        // Inner coords
        int ix = x % 16;
        if (ix < 0) ix += 16;
        int iz = z % 16;
        if (iz < 0) iz += 16;
        this.chunkSnapshot = snap;
        this.innerX = ix;
        this.innerZ = iz;
        return highest(this.chunkSnapshot, ix, iz);
    }

    private int highest(ChunkSnapshot snap, int x, int z) {
        switch (this.type) {
        case NETHER: {
            int y = 127;
            while (y >= 0 && !snap.getBlockType(x, y, z).isEmpty()) y -= 1; // skip blocks
            while (y >= 0 && snap.getBlockType(x, y, z).isEmpty()) y -= 1; // skip air
            while (y >= 0 && MapColor.of(snap.getBlockType(x, y, z)) == 0) y -= 1; // skip transparent
            return y;
        }
        case CAVE: {
            int y = 255;
            while (y >= 0 && snap.getBlockType(x, y, z).isEmpty()) y -= 1; // skip air
            while (y >= 0 && !snap.getBlockType(x, y, z).isEmpty()) y -= 1; // skip blocks
            while (y >= 0 && snap.getBlockType(x, y, z).isEmpty()) y -= 1; // skip air
            while (y >= 0 && MapColor.of(snap.getBlockType(x, y, z)) == 0) y -= 1; // skip transparent
            return y;
        }
        case SURFACE:
        default: {
            int y = 255;
            while (y >= 0 && snap.getBlockType(x, y, z).isEmpty()) y -= 1; // skip air
            while (y >= 0 && MapColor.of(snap.getBlockType(x, y, z)) == 0) y -= 1; // skip transparent
            return y;
        }
        }
    }

}