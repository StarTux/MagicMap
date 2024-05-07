package com.cavetale.magicmap.file;

import com.cavetale.core.struct.Vec2i;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public final class FullRenderTag implements Serializable {
    private long startTime = System.currentTimeMillis();
    private long maxMillisPerTick = 50L;
    private WorldBorderCache worldBorder;
    // Region - We load all chunks in a region with a ticket, then
    // render them all at once.
    private Vec2i currentRegion;
    private List<Vec2i> currentChunks = new ArrayList<>();
    private transient List<MapImageRenderer> renderers;
    // Queue - One ring around the center is queued.  Once done, the
    // currentRing is increased.
    private List<Vec2i> regionQueue = new ArrayList<>();
    private int currentRing;
}
