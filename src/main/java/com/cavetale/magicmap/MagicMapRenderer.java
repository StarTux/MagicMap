package com.cavetale.magicmap;

import com.cavetale.core.chat.Chat;
import com.cavetale.magicmap.event.MagicMapCursorEvent;
import com.cavetale.magicmap.event.MagicMapPostRenderEvent;
import com.cavetale.magicmap.file.CopyResult;
import com.cavetale.magicmap.file.WorldBorderCache;
import com.cavetale.magicmap.file.WorldFileCache;
import com.cavetale.magicmap.file.WorldRenderCache;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

final class MagicMapRenderer extends MapRenderer {
    private final MagicMapPlugin plugin;

    MagicMapRenderer(final MagicMapPlugin plugin) {
        super(true);
        this.plugin = plugin;
    }

    @Override
    public void initialize(MapView mapView) { }

    @Override
    public void render(MapView view, MapCanvas canvas, Player player) {
        final Session session = plugin.getSession(player);
        keepAlive(player, session);
        if (session.getCurrentRender() != null && session.getCurrentRender().isFinished()) {
            final BufferedImage image = session.getCurrentRender().getImage();
            session.getCurrentRender().discard();
            // Rotate the renders
            final Rendered rendered = session.getCurrentRender();
            session.setLastRender(rendered);
            session.setCurrentRender(null);
            // Paste
            new MagicMapPostRenderEvent(player, rendered, image).callEvent();
            canvas.drawImage(0, 0, image);
        }
        if (!session.isRendering()) {
            final boolean renderResult = renderNow(player, session);
        }
        if (session.getLastRender() != null) {
            final List<MagicMapCursor> cursorList = makeCursors(player, session.getLastRender());
            if (!cursorList.equals(session.getLastRender().getCursors())) {
                session.getLastRender().setCursors(cursorList);
                final MapCursorCollection mapCursorCollection = new MapCursorCollection();
                for (MagicMapCursor it : cursorList) {
                    mapCursorCollection.addCursor(it.toMapCursor());
                }
                canvas.setCursors(mapCursorCollection);
            }
        }
    }

    /**
     * Make sure regions around the player stay loaded even if they are not currently in use.
     */
    private void keepAlive(Player player, Session session) {
        final Location location = player.getLocation();
        final WorldFileCache worldFileCache = plugin.getWorlds().getWorld(location.getWorld().getName());
        if (worldFileCache == null) return;
        final WorldRenderCache worldRenderCache = findPreferredRenderCache(worldFileCache, location);
        if (worldRenderCache == null) return;
        worldRenderCache.keepAlive(location.getBlockX(), location.getBlockZ(), session.getMapScale().size / 2);
    }

    private boolean renderNow(Player player, Session session) {
        final Location location = player.getLocation();
        final World world = location.getWorld();
        final int centerX = location.getBlockX();
        final int centerZ = location.getBlockZ();
        final MagicMapScale mapScale = session.getMapScale();
        if (session.getLastRender() != null && !session.getLastRender().didChange(session, world.getName(), centerX, centerZ, mapScale)) {
            return false;
        }
        final WorldFileCache worldFileCache = plugin.getWorlds().getWorld(world.getName());
        if (worldFileCache == null) return false;
        final WorldRenderCache worldRenderCache = findPreferredRenderCache(worldFileCache, location);
        if (worldRenderCache == null) return false;
        // Point of no return
        final Rendered currentRender = new Rendered(world.getName(), WorldBorderCache.of(centerX, centerZ, mapScale), mapScale);
        session.setCurrentRender(currentRender);
        final BufferedImage image = new BufferedImage(mapScale.size, mapScale.size, BufferedImage.TYPE_INT_ARGB);
        final CopyResult copyResult = worldRenderCache.copy(image, centerX, centerZ);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                final BufferedImage scaled = scaleImageForMap(image);
                currentRender.setImage(scaled);
                currentRender.setCopyResult(copyResult);
                currentRender.setFinished(true);
            });
        return true;
    }

    /**
     * Find the preferred existing render cache, based environment and
     * preferences.
     * Accept whichever arguments are necessary to determine this.
     */
    private WorldRenderCache findPreferredRenderCache(WorldFileCache worldFileCache, Location location) {
        switch (worldFileCache.getTag().getEnvironment()) {
        case NORMAL:
            if (worldFileCache.hasRenderCache(RenderType.CAVE) && location.getBlock().getLightFromSky() == 0) {
                return worldFileCache.getRenderCache(RenderType.CAVE);
            } else if (worldFileCache.hasRenderCache(RenderType.SURFACE)) {
                return worldFileCache.getRenderCache(RenderType.SURFACE);
            }
            break;
        case NETHER:
            if (worldFileCache.hasRenderCache(RenderType.NETHER)) {
                return worldFileCache.getRenderCache(RenderType.NETHER);
            }
            break;
        case THE_END:
            if (worldFileCache.hasRenderCache(RenderType.SURFACE)) {
                return worldFileCache.getRenderCache(RenderType.SURFACE);
            }
            break;
        default: break;
        }
        return worldFileCache.getMainRenderCache();
    }

    /**
     * Scale an image to map size.  We assume the image is quadratic.
     */
    private static BufferedImage scaleImageForMap(BufferedImage input) {
        final Image result = input.getWidth() != 128
            ? input.getScaledInstance(128, 128, Image.SCALE_AREA_AVERAGING)
            : input;
        return toBufferedImage(result);
    }

    /**
     * Turn a map Image into a BufferedImage.  Attempt casting if
     * possible.
     *
     * Testing reveals that BufferedImage#getScaledInstance (see
     * above) returns a BufferedImage instance anyway, so this is
     * bound to be quick.
     */
    private static BufferedImage toBufferedImage(Image image) {
        if (image instanceof BufferedImage bufferedImage) {
            return bufferedImage;
        } else {
            final BufferedImage result = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D gfx = result.createGraphics();
            gfx.drawImage(image, 0, 0, null);
            gfx.dispose();
            return result;
        }
    }

    private List<MagicMapCursor> makeCursors(Player player, Rendered lastRender) {
        final List<MagicMapCursor> result = new ArrayList<>();
        final int centerX = lastRender.getMapArea().getCenterX();
        final int centerZ = lastRender.getMapArea().getCenterZ();
        final MagicMapScale mapScale = lastRender.getMapScale();
        final World world = player.getWorld();
        for (Player p : world.getPlayers()) {
            if (p.isInvisible()) continue;
            if (player.equals(p)) continue;
            if (!player.canSee(p)) continue;
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            if (Chat.doesIgnore(player.getUniqueId(), p.getUniqueId())) continue;
            final Location location = p.getLocation();
            if (!lastRender.getMapArea().containsBlock(location.getBlockX(), location.getBlockZ())) continue;
            final MagicMapCursor playerCursor = MagicMapCursor.make(MapCursor.Type.FRAME, location, centerX, centerZ, mapScale, p.displayName());
            result.add(playerCursor);
        }
        result.add(MagicMapCursor.make(MapCursor.Type.PLAYER, player.getLocation(), centerX, centerZ, mapScale, player.displayName()));
        new MagicMapCursorEvent(player, mapScale, lastRender.getMapArea(), result).callEvent();
        return result;
    }
}
