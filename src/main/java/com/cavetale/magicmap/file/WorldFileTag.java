package com.cavetale.magicmap.file;

import com.cavetale.magicmap.RenderType;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import org.bukkit.World.Environment;

@Data
public final class WorldFileTag implements Serializable {
    private WorldBorderCache worldBorder;
    private WorldBorderCache customWorldBorder;
    private String displayName;
    private Environment environment;
    private List<RenderType> renderTypes;
    private FullRenderTag fullRender;
}
