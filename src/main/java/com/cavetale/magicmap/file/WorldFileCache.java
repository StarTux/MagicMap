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
import org.bukkit.World.Environment;
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
    private final boolean persistent;
    // Renders
    private final EnumMap<RenderType, WorldRenderCache> renderTypeMap = new EnumMap<>(RenderType.class);
    // Chunks
    private final Map<Vec2i, Integer> chunkTicketMap = new HashMap<>();
    private final Set<Vec2i> chunksLoading = new HashSet<>();
    // Meta
    private File tagFile;
    private WorldFileTag tag;
    // Timing
    private static final double TPS_THRESHOLD = 19.9;
    private long timeAdjustmentCooldown = 0L;
    // Rendering
    private ChunkRenderTask chunkRenderTask;
    private List<Vec2i> chunkRenderQueue = new ArrayList<>();

    public WorldFileCache(final NetworkServer server, final String name, final File worldFolder) {
        this.server = server;
        this.name = name;
        if (worldFolder != null) {
            this.magicMapFolder = new File(worldFolder, "magicmap");
            persistent = true;
        } else {
            this.magicMapFolder = null;
            persistent = false;
        }
    }

    public WorldFileCache(final String name, final File worldFolder) {
        this(NetworkServer.current(), name, worldFolder);
    }

    /**
     * The enable method for the world server.
     */
    public void enableWorld(World world) {
        if (persistent) {
            magicMapFolder.mkdirs();
            loadTag();
        } else {
            tag = new WorldFileTag();
        }
        boolean doSaveTag = false;
        final WorldBorderCache worldBorder = WorldBorderCache.of(world);
        if (!worldBorder.equals(tag.getWorldBorder())) {
            tag.setWorldBorder(computeWorldBorder());
            doSaveTag = true;
        }
        final Environment environment = world.getEnvironment();
        if (environment != tag.getEnvironment()) {
            tag.setEnvironment(environment);
            doSaveTag = true;
        }
        final List<RenderType> renderTypes = new ArrayList<>();
        switch (environment) {
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
        if (persistent && doSaveTag) {
            saveTag();
        }
        for (RenderType renderType : renderTypes) {
            WorldRenderCache worldRenderCache = new WorldRenderCache(this, renderType, magicMapFolder);
            renderTypeMap.put(renderType, worldRenderCache);
            worldRenderCache.enable();
        }
    }

    public void disableWorld() {
        if (persistent) {
            saveTag();
        }
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
     * chunkTicketMap and chunksLoading exclusively.
     */
    protected void holdChunk(final Vec2i chunk) {
        final int value = chunkTicketMap.getOrDefault(chunk, 0);
        chunkTicketMap.put(chunk, value + 1);
        if (value > 0) return;
        if (chunksLoading.contains(chunk)) return;
        chunksLoading.add(chunk);
        final World world = getWorld();
        world.getChunkAtAsync(chunk.x, chunk.z, (Consumer<Chunk>) loadedChunk -> {
                if (!chunksLoading.remove(chunk)) return;
                loadedChunk.addPluginChunkTicket(plugin());
            });
    }

    protected void unholdChunk(Vec2i chunk) {
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

    public int countChunkTickets() {
        int result = 0;
        for (var it : chunkTicketMap.values()) {
            result += it;
        }
        return result;
    }

    public int getWorldChunkTicketCount() {
        final var collection = getWorld().getPluginChunkTickets().get(plugin());
        return collection != null
            ? collection.size()
            : -1;
    }

    private void loadTag() {
        if (!persistent) {
            throw new IllegalStateException("!persistent: " + name);
        }
        if (tagFile == null) {
            tagFile = new File(magicMapFolder, "tag.json");
        }
        tag = Json.load(tagFile, WorldFileTag.class, WorldFileTag::new);
    }

    public void saveTag() {
        if (!persistent) {
            throw new IllegalStateException("!persistent: " + name);
        }
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

    public boolean hasRenderCache(RenderType renderType) {
        return renderTypeMap.containsKey(renderType);
    }

    public WorldRenderCache getRenderCache(RenderType renderType) {
        return renderTypeMap.get(renderType);
    }

    public WorldRenderCache getMainRenderCache() {
        final RenderType renderType = getMainRenderType();
        if (renderType == null) return null;
        return renderTypeMap.get(renderType);
    }

    protected void tick(final long startTime) {
        final FullRenderTag fullRender = tag.getFullRender();
        if (fullRender != null && !fullRender.isPaused()) {
            fullRenderIter(fullRender, startTime);
        } else {
            boolean didSomething = false;
            if (!persistent) {
                for (WorldRenderCache render : renderTypeMap.values()) {
                    if (render.tickChunkRenderer(startTime)) {
                        didSomething = true;
                    }
                }
            }
            if (!didSomething) {
                if (tickChunkRenderer(startTime)) {
                    didSomething = true;
                }
            }
        }
        cleanUp();
    }

    private void fullRenderIter(FullRenderTag fullRender, final long startTime) {
        final double tps = Bukkit.getTPS()[0];
        if (fullRender.getTimeout() > startTime) {
            if (tps > TPS_THRESHOLD) {
                fullRender.setTimeout(0L);
            } else {
                fullRender.setStatus("Timeout");
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
            fullRender.setStatus("Decreasing max millis per tick");
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
            if (!fullRender.isChunksHeld()) {
                // After a restart or reload, chunks will no longer be
                // held.
                for (Vec2i chunkVector : fullRender.getCurrentChunks()) {
                    holdChunk(chunkVector);
                }
                fullRender.setChunksHeld(true);
            }
            // Make sure all chunks are loaded
            int unloadedChunkCount = 0;
            for (Vec2i chunk : fullRender.getCurrentChunks()) {
                if (world.isChunkLoaded(chunk.x, chunk.z)) continue;
                unloadedChunkCount += 1;
            }
            if (unloadedChunkCount > 0) {
                fullRender.setStatus("Waiting on chunks");
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
                fullRender.setStatus("Waiting on map regions");
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
                                                    fullRender.getWorldBorder());
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
                        ChunkLoadListener.chunkLoadListener().setCallback(c -> {
                                final Vec2i chunkVector = Vec2i.of(c);
                                plugin().getLogger().warning("[ChunkLoad] WorldFileCache#fullRenderIter " + name + " Region"
                                                             + " " + (renderer.getMinWorldX() >> 9)
                                                             + "," + (renderer.getMinWorldZ() >> 9)
                                                             + " Chunk " + chunkVector
                                                             + " " + (fullRender.getCurrentChunks().contains(chunkVector)
                                                                      ? "in currentChunks"
                                                                      : "NOT in currentChunks"));
                            });
                        renderer.run(16);
                        ChunkLoadListener.chunkLoadListener().setCallback(null);
                        if (System.currentTimeMillis() >= stopTime) {
                            break;
                        }
                    }
                } while (System.currentTimeMillis() < stopTime);
                fullRender.setStatus("Rendering");
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
        fullRender.setChunksHeld(false);
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
            //
            // NOTE this appeared to work fine even if we did not
            // select some of the outermost layers of chunks.  Up to
            // two layers could be omitted.  We still do them all to
            // be on the safe side.
            final int ax = (nextRegion.x << 5);
            final int az = (nextRegion.z << 5);
            final int bx = ax + 31;
            final int bz = az + 32;
            for (int z = az; z <= bz; z += 1) {
                for (int x = ax; x <= bx; x += 1) {
                    final Vec2i chunkVector = Vec2i.of(x, z);
                    fullRender.getCurrentChunks().add(chunkVector);
                    holdChunk(chunkVector);
                }
            }
            fullRender.setChunksHeld(true);
            saveTag();
            fullRender.setStatus("Region pulled");
            return;
        }
        fullRender.setStatus("New ring");
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
            fullRender.setStatus("Finished");
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

    private boolean tickChunkRenderer(final long startTime) {
        if (chunkRenderTask != null) {
            if (!chunkRenderTask.isDone()) {
                chunkRenderTask.tick(startTime);
            }
            if (chunkRenderTask.isDone()) {
                chunkRenderQueue.removeAll(chunkRenderTask.getChunksToRender());
                if (persistent) {
                    // This is most likekly just one region, as per
                    // the loading strategy of the ChunkRenderTask.
                    final Set<Vec2i> regions = new HashSet<>();
                    for (Vec2i chunk : chunkRenderTask.getChunksToRender()) {
                        final Vec2i regionVector = Vec2i.of(chunk.x >> 5, chunk.z >> 5);
                        if (!regions.add(regionVector)) continue;
                        for (WorldRenderCache render : renderTypeMap.values()) {
                            RegionFileCache region = render.getRegion(regionVector);
                            if (region != null && region.getState() == RegionFileCache.State.LOADED) {
                                render.scheduleSave(region);
                            }
                        }
                    }
                }
                chunkRenderTask = null;
            }
            return true;
        } else if (!chunkRenderQueue.isEmpty()) {
            chunkRenderTask = new ChunkRenderTask(this, this::onChunkDidRerender);
            chunkRenderTask.init(List.copyOf(renderTypeMap.values()), chunkRenderQueue);
            return true;
        } else {
            return false;
        }
    }

    /**
     * This will get called once per RenderType, thus once per
     * ChunkRenderCache!  Via ChunkRenderTask#chunkRemoveCallback.
     *
     * We remove the chunk from the queue once the task is done, so
     * this does nothing.
     */
    private void onChunkDidRerender(Vec2i chunk) {
    }

    /**
     * Force a chunk render in the future.
     *
     * @return true if the render was scheduled, false if a render for
     *   this chunk was already scheduled or the chunk is out of
     *   bounds.
     */
    public boolean requestChunkRerender(int chunkX, int chunkZ) {
        if (!getEffectiveWorldBorder().containsChunk(chunkX, chunkZ)) {
            return false;
        }
        final Vec2i chunkVector = Vec2i.of(chunkX, chunkZ);
        if (chunkRenderQueue.contains(chunkVector)) {
            return false;
        }
        chunkRenderQueue.add(chunkVector);
        return true;
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
        if (!persistent) {
            throw new IllegalStateException("!persistent: " + name);
        }
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
