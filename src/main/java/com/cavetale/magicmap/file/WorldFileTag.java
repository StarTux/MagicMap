package com.cavetale.magicmap.file;

import com.cavetale.magicmap.RenderType;
import java.io.Serializable;
import java.util.List;
import lombok.Data;

@Data
public final class WorldFileTag implements Serializable {
    private WorldBorderCache worldBorder;
    private List<RenderType> renderTypes;
    private FullRenderTag fullRender;
}
