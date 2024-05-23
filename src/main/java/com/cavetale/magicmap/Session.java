package com.cavetale.magicmap;

import java.awt.Image;
import java.util.UUID;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@Data
public final class Session {
    private final UUID uuid;
    private boolean debug;
    private String shownArea;
    private Image pasteImage;
    private Rendered lastRender;
    private Rendered currentRender;
    private MagicMapScale mapScale = MagicMapScale.DEFAULT;

    public Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    public boolean isRendering() {
        return currentRender != null;
    }
}
