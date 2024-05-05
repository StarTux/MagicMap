package com.cavetale.magicmap.file;

import com.cavetale.core.struct.Vec2i;
import com.cavetale.magicmap.RenderType;
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

    public WorldRenderCache(final WorldFileCache worldFileCache, final RenderType renderType, final File magicMapFolder) {
        this.worldFileCache = worldFileCache;
        this.renderType = renderType;
        this.mapFolder = new File(magicMapFolder, renderType.name().toLowerCase());
    }

    public void enable() {
        mapFolder.mkdirs();
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
            if (!worldFileCache.getWorldBorder().containsRegion(regionFileCache.getRegion())) {
                regionFileCache.setState(RegionFileCache.State.OUT_OF_BOUNDS);
            } else {
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
}
