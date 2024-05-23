package com.cavetale.magicmap.file;

import com.cavetale.core.struct.Vec2i;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.BitSet;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import lombok.Data;
import static com.cavetale.magicmap.MagicMapPlugin.plugin;

/**
 * Represent one region image file.  This class is mostly dumb and
 * managed by WorldRenderCache.
 */
@Data
public final class RegionFileCache {
    private final WorldRenderCache worldRenderCache; // parent
    private final Vec2i region;
    private File imageFile;
    private BufferedImage image;
    private State state = State.INIT;
    private int noTicks = 0;
    private BitSet renderedChunks = new BitSet(1024);

    public enum State {
        INIT,
        LOADING,
        SAVING,
        LOADED,
        OUT_OF_BOUNDS;
        ;

        public boolean isLoaded() {
            return this == LOADED
                || this == SAVING;
        }
    }

    protected RegionFileCache enable() {
        if (worldRenderCache.isPersistent()) {
            imageFile = new File(worldRenderCache.getMapFolder(), "r." + region.x + "." + region.z + ".png");
            renderedChunks.set(0, renderedChunks.length(), true);
        } else {
            // Non persistent maps go straight to loaded or out of
            // bounds.
            makeEmptyImage();
            renderedChunks.clear();
            state = worldRenderCache.getWorldFileCache().getEffectiveWorldBorder().containsRegion(region)
                ? State.LOADED
                : State.OUT_OF_BOUNDS;
        }
        return this;
    }

    protected RegionFileCache disable() {
        return this;
    }

    /**
     * Load or create the image file.
     * This should be called in an async thread by
     * WorldRenderCache.loadingQueue().
     */
    protected void load() {
        if (imageFile.exists()) {
            try {
                image = ImageIO.read(imageFile);
            } catch (IOException ioe) {
                plugin().getLogger().log(Level.SEVERE,
                                         "Read " + worldRenderCache.getWorldFileCache().getName() + "/" + worldRenderCache.getRenderType() + "/" + region,
                                         ioe);
                makeEmptyImage();
            }
        } else {
            makeEmptyImage();
        }
    }

    public void makeEmptyImage() {
        image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
    }

    protected void save() {
        if (image == null) return;
        try {
            ImageIO.write(image, "png", imageFile);
        } catch (IOException ioe) {
            plugin().getLogger().log(Level.SEVERE,
                                     "Write " + worldRenderCache.getWorldFileCache().getName() + "/" + worldRenderCache.getRenderType() + "/" + region,
                                     ioe);
        }
    }

    public void resetNoTick() {
        noTicks = 0;
    }

    public void increaseNoTick() {
        noTicks += 1;
    }

    private static int getInnerChunkIndex(int chunkX, int chunkZ) {
        return (chunkX & 0x1FF) + 32 * (chunkZ & 0x1FF);
    }

    /**
     * Accept chunk coordinates.  Callers must make sure that the
     * chunk is actually contained in this region or else the results
     * will be noisy!
     */
    public boolean isChunkRendered(int chunkX, int chunkZ) {
        return renderedChunks.get(getInnerChunkIndex(chunkX, chunkZ));
    }

    public void setChunkRendered(int chunkX, int chunkZ, boolean value) {
        renderedChunks.set(getInnerChunkIndex(chunkX, chunkZ), value);
    }
}
