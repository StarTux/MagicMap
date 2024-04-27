package com.cavetale.magicmap.file;

import java.io.Serializable;
import lombok.Data;

@Data
public final class WorldFileTag implements Serializable {
    private FullRenderTag fullRender;
}
