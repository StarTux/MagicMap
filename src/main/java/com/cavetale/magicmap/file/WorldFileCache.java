package com.cavetale.magicmap.file;

import com.cavetale.core.connect.NetworkServer;
import com.cavetale.core.struct.Vec2i;
import com.cavetale.core.util.Json;
import com.cavetale.magicmap.RenderType;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
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
    private final NetworkServer server;
    private final String name;
    private final File magicMapFolder;
    // Renders
    private final EnumMap<RenderType, WorldRenderCache> renderTypeMap = new EnumMap<>(RenderType.class);
    // Chunks
    private final Map<Vec2i, Integer> chunkTicketMap = new HashMap<>();
    private final Set<Vec2i> chunksLoading = new HashSet<>();
    // Consider removing chunk callbacks!
    private final Map<Vec2i, List<Consumer<Chunk>>> chunkCallbacks = new HashMap<>();
    // Meta
    private File tagFile;
    private WorldFileTag tag;
    // Timing
    private static final double TPS_THRESHOLD = 19.9;
    private long timeAdjustmentCooldown = 0L;

    public WorldFileCache(final NetworkServer server, final String name, final File worldFolder) {
        this.server = server;
        this.name = name;
        this.magicMapFolder = new File(worldFolder, "magicmap");
    }

    public WorldFileCache(final String name, final File worldFolder) {
        this(NetworkServer.current(), name, worldFolder);
    }

    /**
     * The enable method for the world server.
     */
    public void enableWorld(World world) {
        magicMapFolder.mkdirs();
        loadTag();
        boolean doSaveTag = false;
        final WorldBorderCache worldBorder = WorldBorderCache.of(world);
        if (!worldBorder.equals(tag.getWorldBorder())) {
            tag.setWorldBorder(computeWorldBorder());
            doSaveTag = true;
        }
        final List<RenderType> renderTypes = new ArrayList<>();
        switch (world.getEnvironment()) {
        case NORMAL:
            renderTypes.add(RenderType.SURFACE);
            renderTypes.add(RenderType.CAVE);
            break;
        case NETHER:
            renderTypes.add(RenderType.NETHER);
            break;
        case THE_END:
        case CUSTOM:
        default:
            renderTypes.add(RenderType.SURFACE);
            break;
        }
        if (!renderTypes.equals(tag.getRenderTypes())) {
            tag.setRenderTypes(renderTypes);
            doSaveTag = true;
        }
        if (doSaveTag) {
            saveTag();
        }
        for (RenderType renderType : renderTypes) {
            WorldRenderCache worldRenderCache = new WorldRenderCache(this, renderType, magicMapFolder);
            renderTypeMap.put(renderType, worldRenderCache);
            worldRenderCache.enable();
        }
    }

    public void disableWorld() {
        saveTag();
        disableAll();
        unholdAllChunks();
    }

    public void enableWebserver() {
        loadTag();
        for (RenderType renderType : tag.getRenderTypes()) {
            WorldRenderCache worldRenderCache = new WorldRenderCache(this, renderType, magicMapFolder);
            renderTypeMap.put(renderType, worldRenderCache);
            worldRenderCache.enable();
        }
    }

    private void disableAll() {
        tag = null;
        for (WorldRenderCache it : renderTypeMap.values()) {
            it.disable();
        }
        renderTypeMap.clear();
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
        if (tagFile == null) {
            tagFile = new File(magicMapFolder, "tag.json");
        }
        tag = Json.load(tagFile, WorldFileTag.class, WorldFileTag::new);
    }

    public void saveTag() {
        if (tag == null) return;
        Json.save(tagFile, tag, true);
    }

    public World getWorld() {
        return Bukkit.getWorld(name);
    }

    public WorldBorderCache computeWorldBorder() {
        final World world = getWorld();
        if (world == null) return null;
        return WorldBorderCache.of(world);
    }

    public WorldBorderCache getWorldBorder() {
        return tag.getWorldBorder();
    }

    public WorldBorderCache getEffectiveWorldBorder() {
        WorldBorderCache result = tag.getCustomWorldBorder();
        if (result != null) return result;
        return tag.getWorldBorder();
    }

    public String getDisplayName() {
        String result = tag.getDisplayName();
        if (result != null) return result;
        return name;
    }

    public RenderType getMainRenderType() {
        if (tag == null) return null;
        if (tag.getRenderTypes() == null || tag.getRenderTypes().isEmpty()) return null;
        return tag.getRenderTypes().get(0);
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
        final double tps = Bukkit.getTPS()[0];
        if (fullRender.getTimeout() > startTime) {
            if (tps > TPS_THRESHOLD) {
                fullRender.setTimeout(0L);
            } else {
                return;
            }
        } else if (fullRender.getTimeout() > 0L) {
            fullRender.setTimeout(0L);
        }
        final long maxMillis = fullRender.getMaxMillisPerTick();
        if (tps < TPS_THRESHOLD) {
            // Lower millis per tick if necessary
            fullRender.setTimeout(startTime + 10_000L);
            if (maxMillis > 1L) {
                final long newMaxMillis = maxMillis - 1;
                fullRender.setMaxMillisPerTick(newMaxMillis);
                timeAdjustmentCooldown = startTime + 600_000L;
                plugin().getLogger().info("[" + name + "] Full render decreasing max millis per tick: "
                                          + maxMillis + " => " + newMaxMillis
                                          + ", tps=" + String.format("%.01f", tps));
            }
            return;
        } else if (tps > TPS_THRESHOLD && startTime > timeAdjustmentCooldown && maxMillis < 50) {
            // Every now and then, try raising millis per tick
            final long newMaxMillis = maxMillis + 1L;
            fullRender.setMaxMillisPerTick(newMaxMillis);
            timeAdjustmentCooldown = startTime + 60_000L;
            plugin().getLogger().info("[" + name + "] Full render increasing max millis per tick: "
                                      + maxMillis + " => " + newMaxMillis);
        }
        final long stopTime = startTime + maxMillis;
        final World world = getWorld();
        final Vec2i currentRegion = fullRender.getCurrentRegion();
        if (currentRegion != null) {
            int unloadedChunkCount = 0;
            for (Vec2i chunk : List.copyOf(fullRender.getCurrentChunks())) {
                if (world.isChunkLoaded(chunk.x, chunk.z)) continue;
                unloadedChunkCount += 1;
                if (!chunksLoading.contains(chunk)) {
                    holdChunk(chunk);
                }
            }
            if (unloadedChunkCount > 0) {
                return;
            }
            int unloadedRegionCount = 0;
            for (WorldRenderCache worldRenderCache : renderTypeMap.values()) {
                final RegionFileCache regionFileCache = worldRenderCache.loadRegion(currentRegion);
                if (regionFileCache.getState() != RegionFileCache.State.LOADED) {
                    unloadedRegionCount += 1;
                }
            }
            if (unloadedRegionCount > 0) {
                return;
            }
            // All chunks loaded!
            // Make sure there is a renderer and start rendering
            final List<MapImageRenderer> renderers;
            if (fullRender.getRenderers() == null) {
                renderers = new ArrayList<>();
                final int x = currentRegion.x << 9;
                final int z = currentRegion.z << 9;
                for (WorldRenderCache worldRenderCache : renderTypeMap.values()) {
                    final RegionFileCache regionFileCache = worldRenderCache.getRegion(currentRegion);
                    final MapImageRenderer renderer;
                    renderer = new MapImageRenderer(world,
                                                    regionFileCache.getImage(),
                                                    worldRenderCache.getRenderType(),
                                                    x, z, 512, 512,
                                                    getWorldBorder());
                    renderers.add(renderer);
                }
                fullRender.setRenderers(renderers);
            } else {
                renderers = fullRender.getRenderers();
            }
            // Renderers are enabled, let's see if they are
            // finished. If not, iterate a few times.
            int notFinishedCount = 0;
            for (MapImageRenderer renderer : renderers) {
                if (!renderer.isFinished()) {
                    notFinishedCount += 1;
                }
            }
            if (notFinishedCount > 0) {
                do {
                    for (MapImageRenderer renderer : renderers) {
                        if (renderer.isFinished()) {
                            continue;
                        }
                        renderer.run(16);
                        if (System.currentTimeMillis() >= stopTime) {
                            break;
                        }
                    }
                } while (System.currentTimeMillis() < stopTime);
                return;
            }
            // Schedule saving
            for (WorldRenderCache worldRenderCache : renderTypeMap.values()) {
                final RegionFileCache regionFileCache = worldRenderCache.getRegion(currentRegion);
                worldRenderCache.scheduleSave(regionFileCache);
            }
        } // end if currentRegion != null
        // Region finished!
        for (Vec2i chunk : fullRender.getCurrentChunks()) {
            unholdChunk(chunk);
        }
        fullRender.setRenderers(null);
        fullRender.setCurrentRegion(null);
        fullRender.getCurrentChunks().clear();
        // Pull the next region from the queue
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
            final Duration duration = Duration.ofMillis(System.currentTimeMillis() - fullRender.getStartTime());
            final String durationString = duration.toDays() + "d"
                + " " + duration.toHours() + "h"
                + " " + duration.toMinutes() + "m"
                + " " + duration.toSeconds() + "s";
            plugin().getLogger().info("[" + name + "] full render finished in " + durationString);
        }
    }

    /**
     * Regular cleaning up of unused regions.
     */
    private void cleanUp() {
        for (WorldRenderCache it : renderTypeMap.values()) {
            it.cleanUp();
        }
    }

    public boolean isFullRenderScheduled() {
        if (tag == null) return false;
        return tag.getFullRender() != null;
    }

    public FullRenderTag scheduleFullRender() {
        final FullRenderTag fullRender = new FullRenderTag();
        fullRender.setWorldBorder(computeWorldBorder());
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
