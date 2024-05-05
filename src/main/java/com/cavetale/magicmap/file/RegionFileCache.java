package com.cavetale.magicmap.file;

import com.cavetale.core.struct.Vec2i;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
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

    public enum State {
        INIT,
        LOADING,
        SAVING,
        LOADED,
        OUT_OF_BOUNDS;
        ;
    }

    protected RegionFileCache enable() {
        imageFile = new File(worldRenderCache.getMapFolder(), "r." + region.x + "." + region.z + ".png");
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
                image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
            }
        } else {
            image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        }
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
}
