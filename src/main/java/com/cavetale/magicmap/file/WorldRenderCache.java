package com.cavetale.magicmap.file;

import com.cavetale.core.struct.Vec2i;
import com.cavetale.magicmap.RenderType;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NonNull;
import org.bukkit.Bukkit;
import static com.cavetale.magicmap.MagicMapPlugin.plugin;

/**
 * Caching the map files of one RenderType of one world.
 * WorldFileCache manages this in the renderTypeMap.
 */
@Data
public final class WorldRenderCache {
    public static final int NO_TICK_THRESHOLD = 20 * 60;
    // Init
    private final WorldFileCache worldFileCache; // parent
    private final RenderType renderType;
    private final File mapFolder;
    private final boolean persistent;
    // Runtime
    private final Map<Vec2i, RegionFileCache> regionMap = new HashMap<>();
    private final List<Vec2i> unloadRegions = new ArrayList<>();
    // Async thread
    private Vec2i currentAsyncRegion;
    // Usage:
    // - Set state of regionFileCache to LOADING or SAVING
    // - Add region vector to asyncQueue
    // - Call checkAsyncQueue()
    private final List<Vec2i> asyncQueue = new ArrayList<>();
    // The Chunk Render Queue is only used in non-persistent worlds.
    private final List<Vec2i> chunkRenderQueue = new ArrayList<>();
    private ChunkRenderTask chunkRenderTask = null;

    public WorldRenderCache(final WorldFileCache worldFileCache, final RenderType renderType, final File magicMapFolder) {
        this.worldFileCache = worldFileCache;
        this.renderType = renderType;
        this.persistent = worldFileCache.isPersistent();
        if (persistent) {
            this.mapFolder = new File(magicMapFolder, renderType.name().toLowerCase());
        } else {
            this.mapFolder = null;
        }
    }

    public void enable() {
        if (persistent) {
            mapFolder.mkdirs();
        }
    }

    public void disable() {
        regionMap.clear();
        unloadRegions.clear();
        currentAsyncRegion = null;
        asyncQueue.clear();
    }

    public RegionFileCache getRegion(@NonNull Vec2i vec) {
        return regionMap.get(vec);
    }

    public RegionFileCache loadRegion(@NonNull Vec2i vec) {
        final RegionFileCache result = regionMap.computeIfAbsent(vec, v -> new RegionFileCache(this, vec).enable());
        result.resetNoTick();
        return result;
    }

    protected void scheduleSave(RegionFileCache regionFileCache) {
        if (regionFileCache.getState() != RegionFileCache.State.LOADED) {
            throw new IllegalStateException("world:" + worldFileCache.getName()
                                            + " render:" + renderType
                                            + " region:" + regionFileCache.getRegion()
                                            + " state:" + regionFileCache.getState());
        }
        regionFileCache.setState(RegionFileCache.State.SAVING);
        asyncQueue.add(regionFileCache.getRegion());
        checkAsyncQueue();
    }

    protected void scheduleLoad(RegionFileCache regionFileCache) {
        if (regionFileCache.getState() != RegionFileCache.State.INIT) {
            throw new IllegalStateException("world:" + worldFileCache.getName()
                                            + " render:" + renderType
                                            + " region:" + regionFileCache.getRegion()
                                            + " state:" + regionFileCache.getState());
        }
        regionFileCache.setState(RegionFileCache.State.LOADING);
        asyncQueue.add(regionFileCache.getRegion());
        checkAsyncQueue();
    }

    public void cleanUp() {
        for (RegionFileCache it : regionMap.values()) {
            cleanUpIter(it);
        }
        for (Vec2i region : unloadRegions) {
            final RegionFileCache old = regionMap.remove(region);
            if (old == null) continue;
            old.disable();
        }
        unloadRegions.clear();
    }

    /**
     * In the main thread, we check on each region file and see if it
     * needs loading or unloading.  Loading is done in an off-thread,
     * one region at a time.  The asyncQueue remembers which regions
     * need saving or loading.
     */
    private void cleanUpIter(RegionFileCache regionFileCache) {
        switch (regionFileCache.getState()) {
        case INIT: {
            if (!worldFileCache.getEffectiveWorldBorder().containsRegion(regionFileCache.getRegion())) {
                regionFileCache.setState(RegionFileCache.State.OUT_OF_BOUNDS);
            } else if (persistent) {
                scheduleLoad(regionFileCache);
            }
            break;
        }
        case OUT_OF_BOUNDS:
        case LOADED: {
            regionFileCache.increaseNoTick();
            if (regionFileCache.getNoTicks() > NO_TICK_THRESHOLD) {
                unloadRegions.add(regionFileCache.getRegion());
            }
            break;
        }
        // Fallthrough
        case LOADING:
        case SAVING:
        default: break;
        }
    }

    /**
     * Called occasionally to make sure we are either currently doing
     * a region in an async thread, scheduling a new region from the
     * queue, or there is nothing to do.
     */
    private void checkAsyncQueue() {
        if (currentAsyncRegion != null) return;
        if (asyncQueue.isEmpty()) return;
        currentAsyncRegion = asyncQueue.remove(0);
        final RegionFileCache regionFileCache = regionMap.get(currentAsyncRegion);
        if (regionFileCache == null) {
            currentAsyncRegion = null;
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin(), () -> {
                switch (regionFileCache.getState()) {
                case LOADING:
                    regionFileCache.load();
                    break;
                case SAVING:
                    regionFileCache.save();
                    break;
                default:
                    plugin().getLogger().severe("[" + worldFileCache.getName() + "/" + renderType + "] [Async] " + regionFileCache.getRegion()
                                                + " has unexpected state: " + regionFileCache.getState());
                    break;
                }
                Bukkit.getScheduler().runTask(plugin(), () -> {
                        if (!regionFileCache.getRegion().equals(currentAsyncRegion)) {
                            plugin().getLogger().severe("[" + worldFileCache.getName() + "/" + renderType + "] current async region changed"
                                                        + " from " + regionFileCache.getRegion()
                                                        + " to " + currentAsyncRegion);
                        } else {
                            currentAsyncRegion = null;
                        }
                        regionFileCache.setState(RegionFileCache.State.LOADED);
                        checkAsyncQueue();
                    });
            });
    }

    /**
     * Called by WorldFileCache whenever there is no full render happening.
     */
    protected void tickChunkRenderer(final long startTime) {
        if (chunkRenderTask != null) {
            if (!chunkRenderTask.isDone()) {
                chunkRenderTask.tick(startTime);
            }
            if (chunkRenderTask.isDone()) {
                chunkRenderTask = null;
            }
        } else if (!chunkRenderQueue.isEmpty()) {
            chunkRenderTask = new ChunkRenderTask(worldFileCache, (Vec2i finishedChunk) -> chunkRenderQueue.remove(finishedChunk));
            chunkRenderTask.init(List.of(this), chunkRenderQueue);
        }
    }

    /**
     * Request that a chunk be rendered.  This will be called by
     * copy() in non-persistent worlds so that the chunk will be
     * rendered soon in order to be displayed on a map.
     *
     * @return true if the render was scheduled, false if the chunk
     *   was already rendered or already scheduled, or the chunk is
     *   out of bounds.
     */
    public boolean requestChunkRender(int chunkX, int chunkZ) {
        if (!worldFileCache.getWorldBorder().containsChunk(chunkX, chunkZ)) {
            return false;
        }
        // final int regionX = chunkX >> 5;
        // final int regionZ = chunkZ >> 5;
        // final RegionFileCache region = loadRegion(Vec2i.of(regionX, regionZ));
        // if (region.isChunkRendered(chunkX, chunkZ)) {
        //     return false;
        // }
        final Vec2i chunkVector = Vec2i.of(chunkX, chunkZ);
        if (chunkRenderQueue.contains(chunkVector)) {
            return false;
        }
        chunkRenderQueue.add(chunkVector);
        return true;
    }

    // /**
    //  * Force a chunk render in the future.
    //  *
    //  * @return true if the render was scheduled, false if a render for
    //  *   this chunk was already scheduled or the chunk is out of
    //  *   bounds.
    //  *
    //  * TODO move to WorldFileCache
    //  */
    // public boolean forceChunkRender(int chunkX, int chunkZ) {
    //     if (!worldFileCache.getEffectiveWorldBorder().containsChunk(chunkX, chunkZ)) {
    //         return false;
    //     }
    //     final int regionX = chunkX >> 5;
    //     final int regionZ = chunkZ >> 5;
    //     final RegionFileCache region = loadRegion(Vec2i.of(regionX, regionZ));
    //     if (!region.isChunkRendered(chunkX, chunkZ)) return false;
    //     region.setChunkRendered(chunkX, chunkZ, false);
    //     final Vec2i chunkVector = Vec2i.of(chunkX, chunkZ);
    //     if (!chunkRenderQueue.contains(chunkVector)) {
    //         // This should already be the case
    //         chunkRenderQueue.add(chunkVector);
    //     }
    //     return true;
    // }

    public void keepAlive(int centerX, int centerZ, int radius) {
        final WorldBorderCache worldBorder = worldFileCache.getEffectiveWorldBorder();
        final int minMapX = Math.max(worldBorder.minX, centerX - radius);
        final int minMapZ = Math.max(worldBorder.minZ, centerZ - radius);
        final int maxMapX = Math.min(worldBorder.maxX, minMapX + radius);
        final int maxMapZ = Math.min(worldBorder.maxZ, minMapZ + radius);
        final int minRegionX = minMapX >> 9;
        final int minRegionZ = minMapZ >> 9;
        final int maxRegionX = maxMapX >> 9;
        final int maxRegionZ = maxMapZ >> 9;
        for (int rz = minRegionZ; rz <= maxRegionZ; rz += 1) {
            for (int rx = minRegionX; rx <= maxRegionX; rx += 1) {
                RegionFileCache rfc = loadRegion(Vec2i.of(rx, rz));
            }
        }
    }

    /**
     * Copy the part of the map into one image.
     *
     * @return FULL if all chunks in the selected area were loaded and
     *   rendered, PARTIAL otherwise.
     */
    public CopyResult copy(BufferedImage image, int centerX, int centerZ) {
        CopyResult result = CopyResult.FULL;
        final WorldBorderCache worldBorder = worldFileCache.getEffectiveWorldBorder();
        final int width = image.getWidth();
        final int height = image.getHeight();
        // World coordinates
        final int minMapX = Math.max(worldBorder.minX, centerX - width / 2);
        final int minMapZ = Math.max(worldBorder.minZ, centerZ - height / 2);
        final int maxMapX = Math.min(worldBorder.maxX, minMapX + width - 1);
        final int maxMapZ = Math.min(worldBorder.maxZ, minMapZ + height - 1);
        if (!persistent) {
            // In this loop, we make sure that all chunks within the
            // area are going to be loaded and rendered eventually.
            final int minChunkX = minMapX >> 4;
            final int minChunkZ = minMapZ >> 4;
            final int maxChunkX = maxMapX >> 4;
            final int maxChunkZ = maxMapZ >> 4;
            for (int cz = minChunkZ; cz <= maxChunkZ; cz += 1) {
                for (int cx = minChunkX; cx <= maxChunkX; cx += 1) {
                    final RegionFileCache region = loadRegion(Vec2i.of(cx >> 5, cz >> 5));
                    if (!region.isChunkRendered(cx, cz)) {
                        result = CopyResult.PARTIAL;
                        requestChunkRender(cx, cz);
                    }
                }
            }
        }
        // Region coordinates
        final int minRegionX = minMapX >> 9;
        final int minRegionZ = minMapZ >> 9;
        final int maxRegionX = maxMapX >> 9;
        final int maxRegionZ = maxMapZ >> 9;
        final Graphics2D gfx = image.createGraphics();
        for (int rz = minRegionZ; rz <= maxRegionZ; rz += 1) {
            for (int rx = minRegionX; rx <= maxRegionX; rx += 1) {
                RegionFileCache rfc = loadRegion(Vec2i.of(rx, rz));
                if (!rfc.getState().isLoaded()) {
                    result = CopyResult.PARTIAL;
                    continue;
                }
                // World coordinates
                final int minInnerX = rx << 9;
                final int minInnerZ = rz << 9;
                final int maxInnerX = minInnerX + 511;
                final int maxInnerZ = minInnerZ + 511;
                // World coordinates
                final int minClipX = Math.max(minInnerX, minMapX);
                final int minClipZ = Math.max(minInnerZ, minMapZ);
                final int maxClipX = Math.min(maxInnerX, maxMapX);
                final int maxClipZ = Math.min(maxInnerZ, maxMapZ);
                // Inner region coordinates [0..511]
                final int minSrcX = minClipX & 0x1FF;
                final int minSrcZ = minClipZ & 0x1FF;
                final int maxSrcX = maxClipX & 0x1FF;
                final int maxSrcZ = maxClipZ & 0x1FF;
                // Inner image coordinates [0..width] [0..height]
                final int minDstX = minClipX - minMapX;
                final int minDstZ = minClipZ - minMapZ;
                final int maxDstX = maxClipX - minMapX;
                final int maxDstZ = maxClipZ - minMapZ;
                gfx.drawImage(rfc.getImage(),
                              minDstX, minDstZ, maxDstX + 1, maxDstZ + 1,
                              minSrcX, minSrcZ, maxSrcX + 1, maxSrcZ + 1,
                              null);
            }
        }
        gfx.dispose();
        return result;
    }
}
