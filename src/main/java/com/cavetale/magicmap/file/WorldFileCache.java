package com.cavetale.magicmap.file;

import com.cavetale.core.struct.Vec2i;
import com.cavetale.core.util.Json;
import com.cavetale.magicmap.RenderType;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import static com.cavetale.magicmap.MagicMapPlugin.plugin;

/**
 * Represent one mapped world.
 * Map files go in the "magicmap" folder inside the world folder.
 */
@Data
public final class WorldFileCache {
    public static final int NO_TICK_THRESHOLD = 20 * 60;
    private final String name;
    private final File worldFolder;
    private final File magicMapFolder;
    // Regions
    private final Map<Vec2i, RegionFileCache> regionMap = new HashMap<>();
    private final List<Vec2i> unloadRegions = new ArrayList<>();
    // Chunks
    private final Map<Vec2i, Integer> chunkTicketMap = new HashMap<>();
    private final Set<Vec2i> chunksLoading = new HashSet<>();
    // Consider removing chunk callbacks!
    private final Map<Vec2i, List<Consumer<Chunk>>> chunkCallbacks = new HashMap<>();
    // Saving
    private File tagFile;
    private WorldFileTag tag;
    // Async thread
    private Vec2i currentAsyncRegion;
    // Usage:
    // - Set state of regionFileCache to LOADING or SAVING
    // - Add region vector to asyncQueue
    // - Call checkAsyncQueue()
    private final List<Vec2i> asyncQueue = new ArrayList<>();

    public WorldFileCache(final String name, final File worldFolder) {
        this.name = name;
        this.worldFolder = worldFolder;
        this.magicMapFolder = new File(worldFolder, "magicmap");
    }

    /**
     * The enable method for the world server.
     */
    public void enableWorld() {
        this.tagFile = new File(magicMapFolder, "tag.json");
        magicMapFolder.mkdirs();
        loadTag();
    }

    public void disableWorld() {
        saveTag();
        disableAll();
        unholdAllChunks();
    }

    private void disableAll() {
        regionMap.clear();
        currentAsyncRegion = null;
        asyncQueue.clear();
        unloadRegions.clear();
        tag = null;
    }

    /**
     * Load a chunk asynchronously and keep it loaded via chunk ticket.
     * This method, along with unhold and unholdAll, utilizes the
     * chunkTicketMap, chunkCallbacks, and chunksLoading exclusively.
     */
    private void holdChunk(final Vec2i chunk, Consumer<Chunk> callback) {
        final int value = chunkTicketMap.getOrDefault(chunk, 0);
        chunkTicketMap.put(chunk, value + 1);
        final World world = getWorld();
        if (world.isChunkLoaded(chunk.x, chunk.z)) {
            if (callback != null) {
                final Chunk loadedChunk = world.getChunkAt(chunk.x, chunk.z);
                callback.accept(loadedChunk);
            }
            return;
        }
        if (callback != null) {
            chunkCallbacks.computeIfAbsent(chunk, c -> new ArrayList<>()).add(callback);
        }
        if (value > 0) return;
        if (chunksLoading.contains(chunk)) return;
        chunksLoading.add(chunk);
        world.getChunkAtAsync(chunk.x, chunk.z, (Consumer<Chunk>) loadedChunk -> {
                if (!chunksLoading.contains(chunk)) return;
                chunksLoading.remove(chunk);
                loadedChunk.addPluginChunkTicket(plugin());
                final List<Consumer<Chunk>> callbacks = chunkCallbacks.remove(chunk);
                if (callbacks != null) {
                    for (Consumer<Chunk> storedCallback : callbacks) {
                        storedCallback.accept(loadedChunk);
                    }
                }
            });
    }

    private void holdChunk(Vec2i chunk) {
        holdChunk(chunk, null);
    }

    private void unholdChunk(Vec2i chunk) {
        final var value = chunkTicketMap.getOrDefault(chunk, 0);
        if (value == 0) return;
        if (value > 1) {
            chunkTicketMap.put(chunk, value - 1);
        } else {
            chunkTicketMap.remove(chunk);
            getWorld().removePluginChunkTicket(chunk.x, chunk.z, plugin());
        }
    }

    private void unholdAllChunks() {
        chunksLoading.clear();
        chunkTicketMap.clear();
        getWorld().removePluginChunkTickets(plugin());
    }

    private void loadTag() {
        tag = Json.load(tagFile, WorldFileTag.class, WorldFileTag::new);
    }

    private void saveTag() {
        if (tag == null) return;
        Json.save(tagFile, tag, true);
    }

    public World getWorld() {
        return Bukkit.getWorld(name);
    }

    public RegionFileCache getRegion(Vec2i vec) {
        return regionMap.get(vec);
    }

    public RegionFileCache loadRegion(Vec2i vec) {
        final RegionFileCache result = regionMap.computeIfAbsent(vec, v -> new RegionFileCache(this, vec).enable());
        result.resetNoTick();
        return result;
    }

    public WorldBorderCache getWorldBorder() {
        final World world = getWorld();
        if (world == null) return null;
        return WorldBorderCache.of(world);
    }

    protected void tick() {
        final FullRenderTag fullRender = tag.getFullRender();
        if (fullRender != null) {
            fullRenderIter(fullRender);
        } else {
            // TODO regular render
        }
        cleanUp();
    }

    private void fullRenderIter(FullRenderTag fullRender) {
        final long startTime = System.currentTimeMillis();
        final long stopTime = startTime + fullRender.getMaxMillisPerTick();
        final World world = getWorld();
        final Vec2i currentRegion = fullRender.getCurrentRegion();
        if (currentRegion != null) {
            final RegionFileCache regionFileCache = loadRegion(currentRegion);
            int unloadedChunks = 0;
            for (Vec2i chunk : List.copyOf(fullRender.getCurrentChunks())) {
                if (world.isChunkLoaded(chunk.x, chunk.z)) continue;
                unloadedChunks += 1;
                if (!chunksLoading.contains(chunk)) {
                    holdChunk(chunk);
                }
            }
            if (unloadedChunks > 0) {
                return;
            }
            if (regionFileCache.getState() != RegionFileCache.State.LOADED) {
                return;
            }
            // All chunks loaded!
            // Make sure there is a renderer and start rendering
            final MapImageRenderer renderer;
            if (fullRender.getRenderer() == null) {
                final int x = currentRegion.x << 9;
                final int z = currentRegion.z << 9;
                renderer = new MapImageRenderer(world,
                                                regionFileCache.getImage(),
                                                (world.getEnvironment() == World.Environment.NETHER
                                                 ? RenderType.NETHER
                                                 : RenderType.SURFACE),
                                                x, z, 512, 512);
                fullRender.setRenderer(renderer);
            } else {
                renderer = fullRender.getRenderer();
            }
            do {
                if (renderer.isFinished()) {
                    for (Vec2i chunk : fullRender.getCurrentChunks()) {
                        unholdChunk(chunk);
                    }
                    // Region finished
                    fullRender.setRenderer(null);
                    fullRender.setCurrentRegion(null);
                    fullRender.getCurrentChunks().clear();
                    // Schedule saving
                    scheduleSave(regionFileCache);
                    break;
                }
                renderer.run(16);
            } while (System.currentTimeMillis() < stopTime);
            return;
        }
        final List<Vec2i> regionQueue = fullRender.getRegionQueue();
        if (!regionQueue.isEmpty()) {
            final Vec2i nextRegion = regionQueue.remove(0);
            fullRender.setCurrentRegion(nextRegion);
            // Compute chunks: All chunk within the region, plus one
            // for padding, because the renderer needs one additional
            // block right outside the region border for shading.
            final int ax = (nextRegion.x << 5) - 1;
            final int az = (nextRegion.z << 5) - 1;
            final int bx = ax + 33;
            final int bz = az + 33;
            for (int z = az; z <= bz; z += 1) {
                for (int x = ax; x <= bx; x += 1) {
                    fullRender.getCurrentChunks().add(Vec2i.of(x, z));
                }
            }
            saveTag();
            return;
        }
        // No current region, no region queue.  Let's build a new
        // region queue around the current ring and quit if none are
        // within the border.
        final int currentRing = fullRender.getCurrentRing();
        final Vec2i centerRegion = Vec2i.of(fullRender.getWorldBorder().getCenterX() >> 9,
                                            fullRender.getWorldBorder().getCenterZ() >> 9);
        if (currentRing == 0) {
            fullRender.getRegionQueue().add(centerRegion);
        } else {
            final int ringLength = currentRing * 2 + 1;
            final List<Vec2i> list = new ArrayList<>();
            for (int i = 0; i < ringLength; i += 1) {
                list.add(Vec2i.of(centerRegion.x - currentRing + i, centerRegion.z - currentRing));
                list.add(Vec2i.of(centerRegion.x + currentRing, centerRegion.z - currentRing + i));
                list.add(Vec2i.of(centerRegion.x + currentRing - i, centerRegion.z + currentRing));
                list.add(Vec2i.of(centerRegion.x - currentRing, centerRegion.z + currentRing - i));
            }
            for (Vec2i region : list) {
                if (!fullRender.getWorldBorder().containsRegion(region)) continue;
                fullRender.getRegionQueue().add(region);
            }
        }
        fullRender.setCurrentRing(currentRing + 1);
        if (fullRender.getRegionQueue().isEmpty()) {
            tag.setFullRender(null);
            saveTag();
            plugin().getLogger().info("[" + name + "] full render finished!");
        }
    }

    protected void scheduleSave(RegionFileCache regionFileCache) {
        if (regionFileCache.getState() != RegionFileCache.State.LOADED) {
            throw new IllegalStateException("world:" + name
                                            + " region:" + regionFileCache.getRegion()
                                            + " state:" + regionFileCache.getState());
        }
        regionFileCache.setState(RegionFileCache.State.SAVING);
        asyncQueue.add(regionFileCache.getRegion());
        checkAsyncQueue();
    }

    protected void scheduleLoad(RegionFileCache regionFileCache) {
        if (regionFileCache.getState() != RegionFileCache.State.INIT) {
            throw new IllegalStateException("world:" + name
                                            + " region:" + regionFileCache.getRegion()
                                            + " state:" + regionFileCache.getState());
        }
        regionFileCache.setState(RegionFileCache.State.LOADING);
        asyncQueue.add(regionFileCache.getRegion());
        checkAsyncQueue();
    }

    /**
     * Regular cleaning up of unused regions.
     */
    private void cleanUp() {
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
            if (!getWorldBorder().containsRegion(regionFileCache.getRegion())) {
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
                    plugin().getLogger().severe("[" + name + "] [Async] " + regionFileCache.getRegion()
                                                + " has unexpected state: " + regionFileCache.getState());
                    break;
                }
                Bukkit.getScheduler().runTask(plugin(), () -> {
                        if (!regionFileCache.getRegion().equals(currentAsyncRegion)) {
                            plugin().getLogger().severe("[" + name + "] current async region changed"
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

    public boolean isFullRenderScheduled() {
        if (tag == null) return false;
        return tag.getFullRender() != null;
    }

    public FullRenderTag scheduleFullRender() {
        final FullRenderTag fullRender = new FullRenderTag();
        fullRender.setWorldBorder(getWorldBorder());
        tag.setFullRender(fullRender);
        saveTag();
        return fullRender;
    }

    public FullRenderTag cancelFullRender() {
        final FullRenderTag fullRender = tag.getFullRender();
        if (fullRender == null) return null;
        tag.setFullRender(null);
        for (Vec2i chunk : fullRender.getCurrentChunks()) {
            unholdChunk(chunk);
        }
        return fullRender;
    }
}
