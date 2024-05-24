package com.cavetale.magicmap;

import com.cavetale.magicmap.file.CopyResult;
import com.cavetale.magicmap.file.WorldBorderCache;
import java.awt.image.BufferedImage;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * This object is stored in Session to remember the current and
 * previous render.  It is created by MagicMapRenderer do hold its
 * data for determining if a new render is advised, as well as the
 * Image that will be pasted onto the MapCanvas.
 */
@Data
@RequiredArgsConstructor
public final class Rendered {
    private final String worldName;
    private final WorldBorderCache mapArea;
    private final MagicMapScale mapScale;
    private BufferedImage image;
    private CopyResult copyResult;
    private volatile boolean finished;
    private List<MagicMapCursor> cursors;

    /**
     * Figure out if a new render is advised, assuming this Rendered
     * was created by the previous render.
     * Called by MagicMapRenderer#renderNow.
     * Accept whichever arguments are necessary to determine this.
     */
    public boolean didChange(final Session session, final String newWorldName, final int newCenterX, final int newCenterZ, final MagicMapScale newMapScale) {
        return copyResult != CopyResult.FULL
            || Math.abs(newCenterX - mapArea.getCenterX()) >= mapScale.minWalkingDistance
            || Math.abs(newCenterZ - mapArea.getCenterZ()) >= mapScale.minWalkingDistance
            || newMapScale != mapScale
            || !newWorldName.equals(worldName);
    }

    /**
     * Discard the image which is no longer in use because it has been
     * pasted to the MapCanvas in MagicMapRenderer#render.
     */
    public void discard() {
        image = null;
    }
}
