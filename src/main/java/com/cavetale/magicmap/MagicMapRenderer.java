package com.cavetale.magicmap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
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
        Session session = plugin.getSession(player);
        if (session.pasteMap != null) {
            session.pasteMap.paste(canvas);
            session.pasteMap = null;
        }
        if (session.pasteCursors != null) {
            canvas.setCursors(session.pasteCursors);
            session.pasteCursors = null;
        }
        // Schedule new
        Location loc = player.getLocation();
        String world = loc.getWorld().getName();
        long now = plugin.nowInSeconds();
        if (needsToRerender(session, world, loc, now)) {
            session.forceUpdate = false;
            session.partial = false;
            session.rendering = true;
            session.cooldown = now + 5;
            session.lastRender = now;
            Bukkit.getScheduler().runTask(plugin, () -> newRender(player, session));
        }
        if (!session.cursoring) {
            if (session.cursorCooldown > 0) {
                session.cursorCooldown -= 1;
            } else {
                session.cursoring = true;
                session.cursorCooldown = plugin.cursorTicks;
                Bukkit.getScheduler().runTask(plugin, () -> newCursor(player, session));
            }
        }
    }

    boolean needsToRerender(Session session, String world, Location loc, long now) {
        if (session.rendering) return false;
        if (session.forceUpdate) return true;
        if (now < session.cooldown) return false;
        if (session.partial) return true;
        if (!world.equals(session.world)) return true;
        if (Math.abs(loc.getBlockX() - session.centerX) >= 32) return true;
        if (Math.abs(loc.getBlockZ() - session.centerZ) >= 32) return true;
        return now > session.lastRender + 30L;
    }

    /**
     * {@link render(MapView, MapCanvas, Player)} will schedule this
     * for exec in the main thread.
     */
    void newRender(Player player, Session session) {
        Location loc = player.getLocation();
        int centerX = loc.getBlockX();
        int centerZ = loc.getBlockZ();
        RenderType type;
        String worldName = loc.getWorld().getName();
        if (loc.getWorld().getEnvironment() == World.Environment.NETHER) {
            type = RenderType.NETHER;
        } else if (loc.getWorld().getEnvironment() == World.Environment.THE_END) {
            type = RenderType.SURFACE;
        } else {
            Boolean enableCaveView = plugin.getEnableCaveView().get(worldName);
            if (enableCaveView == null) enableCaveView = plugin.getEnableCaveView().get("default");
            if (enableCaveView == null || enableCaveView == Boolean.TRUE) {
                Block block = loc.getBlock();
                boolean sunlight = false;
                for (int i = 0; i < 5; i += 1) {
                    if (block.getLightFromSky() > 0) {
                        sunlight = true;
                        break;
                    }
                    block = block.getRelative(0, 1, 0);
                }
                type = sunlight ? RenderType.SURFACE : RenderType.CAVE;
            } else {
                type = RenderType.SURFACE;
            }
        }
        SyncMapRenderer renderer = new SyncMapRenderer(plugin, plugin.getMapColor(), loc.getWorld(), session, type, centerX, centerZ, loc.getWorld().getTime());
        plugin.getMainQueue().add(renderer);
    }

    private boolean isHostile(Entity entity) {
        switch (entity.getType()) {
        case GHAST:
        case SLIME:
        case MAGMA_CUBE:
        case ENDER_DRAGON:
        case SHULKER:
        case SHULKER_BULLET:
            return true;
        default:
            return entity instanceof Monster;
        }
    }

    /**
     * {@link render(MapView, MapCanvas, Player)} will Schedule this
     * for exec in the main thread.
     */
    void newCursor(Player player, Session session) {
        final Location loc = player.getLocation();
        final int px = loc.getBlockX();
        final int pz = loc.getBlockZ();
        MapCursorCollection cursors = new MapCursorCollection();
        if (plugin.renderPlayers) {
            for (Player o: player.getWorld().getPlayers()) {
                if (player.equals(o)) continue;
                if (!player.canSee(o)) continue;
                if (o.getGameMode() == GameMode.SPECTATOR) continue;
                Location ol = o.getLocation();
                if (Math.abs(ol.getBlockX() - px) > 80) continue;
                if (Math.abs(ol.getBlockZ() - pz) > 80) continue;
                MapCursor cur = makeCursor(MapCursor.Type.BLUE_POINTER, ol,
                                           session.centerX, session.centerZ);
                cursors.addCursor(cur);
            }
        }
        if (plugin.renderEntities || plugin.renderMarkerArmorStands) {
            for (Entity e: player.getNearbyEntities(64, 24, 64)) {
                if (e instanceof Player) continue;
                if (!(e instanceof LivingEntity)) continue;
                if (isHostile(e)) {
                    if (!plugin.renderEntities) continue;
                    Location at = e.getLocation();
                    cursors.addCursor(makeCursor(MapCursor.Type.RED_POINTER, at,
                                                 session.centerX, session.centerZ));
                } else if (e instanceof Animals) {
                    if (!plugin.renderEntities) continue;
                    Location at = e.getLocation().getBlock().getLocation();
                    cursors.addCursor(makeCursor(MapCursor.Type.SMALL_WHITE_CIRCLE, at,
                                                 session.centerX, session.centerZ));
                } else if (e instanceof ArmorStand) {
                    if (!plugin.renderMarkerArmorStands) continue;
                    ArmorStand stand = (ArmorStand) e;
                    if (!stand.isMarker()) continue;
                    String name = stand.getCustomName();
                    if (name == null || name.isEmpty()) continue;
                    Location at = e.getLocation().getBlock().getLocation();
                    MapCursor cur = makeCursor(MapCursor.Type.WHITE_CROSS, at,
                                               session.centerX, session.centerZ);
                    cursors.addCursor(cur);
                }
            }
        }
        MapCursor pcur = makeCursor(MapCursor.Type.GREEN_POINTER, loc,
                                    session.centerX, session.centerZ);
        ChatColor d = ChatColor.WHITE;
        String c = ChatColor.GRAY + ",";
        cursors.addCursor(pcur);
        MagicMapCursorsEvent.call(player, session, cursors);
        session.pasteCursors = cursors;
        session.cursoring = false;
    }

    static MapCursor makeCursor(MapCursor.Type cursorType, Location location,
                                int centerX, int centerZ) {
        int dir = (int) (location.getYaw() + 11.25f);
        while (dir < 0) dir += 360;
        while (dir > 360) dir -= 360;
        dir = dir * 2 / 45;
        if (dir < 0) dir = 0;
        if (dir > 15) dir = 15;
        int x = (location.getBlockX() - centerX) * 2;
        int y = (location.getBlockZ() - centerZ) * 2;
        if (x < -127) x = -127;
        if (y < -127) y = -127;
        if (x > 127) x = 127;
        if (y > 127) y = 127;
        return new MapCursor((byte) x, (byte) y, (byte) dir, cursorType.getValue(), true);
    }

    static MapCursor makeCursor(MapCursor.Type cursorType, Block block, int centerX, int centerZ) {
        int x = (block.getX() - centerX) * 2;
        int y = (block.getZ() - centerZ) * 2;
        if (x < -127) x = -127;
        if (y < -127) y = -127;
        if (x > 127) x = 127;
        if (y > 127) y = 127;
        return new MapCursor((byte) x, (byte) y, (byte) 8, cursorType.getValue(), true);
    }

    static MapCursor makeCursor(MapCursor.Type cursorType, int x, int y, int rot) {
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (x > 127) x = 127;
        if (y > 127) y = 127;
        return new MapCursor((byte) ((x - 64) * 2), (byte) ((y - 64) * 2), (byte) rot,
                             cursorType.getValue(), true);
    }
}
