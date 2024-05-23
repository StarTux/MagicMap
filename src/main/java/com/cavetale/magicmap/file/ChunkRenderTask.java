package com.cavetale.magicmap.file;

import com.cavetale.core.struct.Vec2i;
import com.cavetale.magicmap.RenderType;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import static com.cavetale.magicmap.MagicMapPlugin.plugin;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.format.NamedTextColor.*;

/**
 * Store some state info for rendering or refreshing of chunks.  This
 * is used by WorldFileCache and WorldRenderCache for their respective
 * obligations.
 */
@RequiredArgsConstructor
public final class ChunkRenderTask {
    private final WorldFileCache worldFileCache;
    private final Consumer<Vec2i> chunkRemoveCallback;
    private State state = State.IDLE;
    private final List<Vec2i> chunksToRender = new ArrayList<>(); // TODO local?
    private final List<Vec2i> chunksToLoad = new ArrayList<>();
    private List<TypeSpecific> renderTypes;
    private String debugMessage;

    @RequiredArgsConstructor
    private static final class TypeSpecific {
        private final WorldRenderCache worldRenderCache;
        private final RenderType renderType;
        private final List<Vec2i> chunksNotRendered;
        private final List<RendererPair> renderingChunks = new ArrayList<>();
        private final List<RendererPair> pastingChunks = new ArrayList<>();
    }

    @RequiredArgsConstructor
    private static final class RendererPair {
        private final Vec2i chunk;
        private final MapImageRenderer renderer;
    }

    public enum State {
        IDLE,
        RENDER_CHUNKS,
        DONE,
        ;
    }

    public Component getInfoComponent() {
        int chunksNotRenderedCount = 0;
        int renderingChunksCount = 0;
        int pastingChunksCount = 0;
        if (renderTypes != null) {
            for (var it : renderTypes) {
                chunksNotRenderedCount += it.chunksNotRendered.size();
                renderingChunksCount += it.renderingChunks.size();
                pastingChunksCount += it.pastingChunks.size();
            }
        }
        return textOfChildren(text(state.name(), YELLOW),
                              space(),
                              text("toRender:", GRAY), text(chunksNotRenderedCount), text("/", DARK_GRAY), text(chunksToRender.size()),
                              space(),
                              text("toPaste:", GRAY), text(pastingChunksCount),
                              (debugMessage != null
                               ? text(" " + debugMessage, RED)
                               : empty()));
    }

    /**
     * Pick some chunks and start holding them.
     *
     * @param worldRenderCacheList the WorldRenderCache instances to render
     * @param allQueuedChunks All queued chunks, either from the
     *   WorldFileCache or the WorldRenderCache issuing this.
     */
    public void init(List<WorldRenderCache> worldRenderCacheList, List<Vec2i> allQueuedChunks) {
        assert state == State.IDLE;
        // Load one region of chunks so they benefit from each others'
        // neighborhood.
        Vec2i pivot = allQueuedChunks.get(0);
        final int regionX = pivot.x >> 5;
        final int regionZ = pivot.z >> 5;
        chunksToRender.add(pivot);
        for (int i = 1; i < allQueuedChunks.size(); i += 1) {
            final Vec2i chunk = allQueuedChunks.get(i);
            if ((chunk.x >> 5) != regionX || (chunk.z >> 5) != regionZ) {
                continue;
            }
            chunksToRender.add(chunk);
        }
        final Set<Vec2i> chunksToLoadSet = new HashSet<>(chunksToRender);
        for (Vec2i it : chunksToRender) {
            chunksToLoadSet.add(it.add(0, 1));
        }
        chunksToLoad.addAll(chunksToLoadSet);
        for (Vec2i chunk : chunksToLoad) {
            worldFileCache.holdChunk(chunk);
        }
        renderTypes = new ArrayList<>(worldRenderCacheList.size());
        for (WorldRenderCache worldRenderCache : worldRenderCacheList) {
            renderTypes.add(new TypeSpecific(worldRenderCache, worldRenderCache.getRenderType(), new ArrayList<>(chunksToRender)));
        }
        state = State.RENDER_CHUNKS;
    }

    public boolean isDone() {
        return state == State.DONE;
    }

    public void tick(final long startTime) {
        assert state == State.RENDER_CHUNKS;
        final long stopTime = startTime + 25L;
        final World world = worldFileCache.getWorld();
        boolean didAnything = false;
        for (TypeSpecific typeSpecific : renderTypes) {
            if (!typeSpecific.chunksNotRendered.isEmpty()) {
                didAnything = true;
                prepareRendererType(world, typeSpecific);
            }
        }
        for (TypeSpecific typeSpecific : renderTypes) {
            while (!typeSpecific.renderingChunks.isEmpty()) {
                didAnything = true;
                final RendererPair pair = typeSpecific.renderingChunks.get(0);
                if (renderType(typeSpecific, pair, stopTime)) {
                    typeSpecific.renderingChunks.remove(pair);
                    typeSpecific.pastingChunks.add(pair);
                }
                if (System.currentTimeMillis() >= stopTime) return;
            }
        }
        for (TypeSpecific typeSpecific : renderTypes) {
            while (!typeSpecific.pastingChunks.isEmpty()) {
                didAnything = true;
                for (Iterator<RendererPair> iter = typeSpecific.pastingChunks.iterator(); iter.hasNext();) {
                    if (pasteType(typeSpecific, iter.next())) {
                        iter.remove();
                    }
                    if (System.currentTimeMillis() >= stopTime) return;
                }
            }
        }
        if (!didAnything) {
            finishUp();
            state = State.DONE;
        }
    }

    private void prepareRendererType(World world, TypeSpecific typeSpecific) {
        for (Iterator<Vec2i> iter = typeSpecific.chunksNotRendered.iterator(); iter.hasNext();) {
            final Vec2i chunk = iter.next();
            final Vec2i chunkBelow = chunk.add(0, 1);
            if (!world.isChunkLoaded(chunk.x, chunk.z) || !world.isChunkLoaded(chunkBelow.x, chunkBelow.z)) {
                debugMessage = "" + chunk + "=" + world.isChunkLoaded(chunk.x, chunk.z)
                    + "/" + chunksToLoad.contains(chunk)
                    + "/" + worldFileCache.getChunkTicketMap().get(chunk)
                    + "/" + worldFileCache.getChunksLoading().contains(chunk)
                    + " " + chunkBelow + "=" + world.isChunkLoaded(chunkBelow.x, chunkBelow.z)
                    + "/" + chunksToLoad.contains(chunkBelow)
                    + "/" + worldFileCache.getChunkTicketMap().get(chunkBelow)
                    + "/" + worldFileCache.getChunksLoading().contains(chunkBelow);
                continue;
            }
            debugMessage = null;
            iter.remove();
            final MapImageRenderer renderer = new MapImageRenderer(world,
                                                                   new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB),
                                                                   typeSpecific.renderType,
                                                                   chunk.x << 4, chunk.z << 4, 16, 16,
                                                                   worldFileCache.getWorldBorder());
            final RendererPair pair = new RendererPair(chunk, renderer);
            typeSpecific.renderingChunks.add(pair);
        }
    }

    /**
     * Render the first chunk from the renderingChunks queue.  Move it
     * to the pastingChunks queue when it's done.
     */
    private boolean renderType(TypeSpecific typeSpecific, RendererPair pair, final long stopTime) {
        while (!pair.renderer.isFinished()) {
            ChunkLoadListener.chunkLoadListener().setCallback(c -> {
                    plugin().getLogger().warning("[ChunkLoad] ChunkRenderTask#renderType"
                                                 + " " + worldFileCache.getName() + "/" + typeSpecific.renderType
                                                 + " " + pair.chunk
                                                 + " " + Vec2i.of(c));
                });
            pair.renderer.run(16);
            ChunkLoadListener.chunkLoadListener().setCallback(null);
            if (System.currentTimeMillis() >= stopTime) break;
        }
        return pair.renderer.isFinished();
    }

    private boolean pasteType(TypeSpecific typeSpecific, RendererPair pair) {
        final Vec2i region = Vec2i.of(pair.chunk.x >> 5, pair.chunk.z >> 5);
        final RegionFileCache regionFileCache = typeSpecific.worldRenderCache.loadRegion(region);
        if (regionFileCache.getState() != RegionFileCache.State.LOADED) return false;
        final Graphics2D gfx = regionFileCache.getImage().createGraphics();
        final int offsetX = (pair.chunk.x << 4) & 0x1FF;
        final int offsetY = (pair.chunk.z << 4) & 0x1FF;
        gfx.drawImage(pair.renderer.getImage(), offsetX, offsetY, null);
        gfx.dispose();
        regionFileCache.setChunkRendered(pair.chunk.x, pair.chunk.z, true);
        chunkRemoveCallback.accept(pair.chunk);
        return true;
    }

    private void finishUp() {
        for (Vec2i chunk : chunksToLoad) {
            worldFileCache.unholdChunk(chunk);
        }
    }
}
