package com.cavetale.magicmap.event;

import com.cavetale.core.struct.Vec2i;
import com.cavetale.magicmap.MagicMapScale;
import com.cavetale.magicmap.Rendered;
import com.cavetale.magicmap.file.WorldBorderCache;
import java.awt.image.BufferedImage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

@Getter
@RequiredArgsConstructor
public final class MagicMapPostRenderEvent extends Event {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final Player player;
    private final Rendered rendered;
    private final BufferedImage image;

    public WorldBorderCache getMapArea() {
        return rendered.getMapArea();
    }

    public MagicMapScale getMapScale() {
        return rendered.getMapScale();
    }

    public World getWorld() {
        return Bukkit.getWorld(rendered.getWorldName());
    }

    public Vec2i worldToMap(int blockX, int blockZ) {
        final double scale = getMapScale().getScale();
        return Vec2i.of((int) Math.round((blockX - getMapArea().getMinX()) / scale),
                        (int) Math.round((blockZ - getMapArea().getMinZ()) / scale));
    }

    public Vec2i worldToMap(Vec2i in) {
        return worldToMap(in.x, in.z);
    }

    public Vec2i clampMap(int mapX, int mapZ) {
        return Vec2i.of(Math.max(0, Math.min(127, mapX)),
                        Math.max(0, Math.min(127, mapZ)));
    }

    public Vec2i clampMap(Vec2i in) {
        return clampMap(in.x, in.z);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
