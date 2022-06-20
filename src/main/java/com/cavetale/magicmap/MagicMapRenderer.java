package com.cavetale.magicmap;

import com.cavetale.magicmap.event.MagicMapCursorEvent;
import com.cavetale.magicmap.util.Cursors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.text;

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
            session.cooldown = now + plugin.renderCooldown;
            session.lastRender = now;
            Bukkit.getScheduler().runTask(plugin, () -> newRender(player, session));
        }
        if (!session.cursoring && plugin.doCursors) {
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
        if (!world.equals(session.world)) return true;
        if (Math.abs(loc.getBlockX() - session.centerX) >= 32) return true;
        if (Math.abs(loc.getBlockZ() - session.centerZ) >= 32) return true;
        return now > session.lastRender + plugin.renderRefresh;
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
        case PHANTOM:
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
        int minX = session.centerX - 63;
        int minZ = session.centerZ - 63;
        int maxX = session.centerX + 64;
        int maxZ = session.centerZ + 64;
        MapCursorCollection cursors = new MapCursorCollection();
        World world = player.getWorld();
        if (plugin.renderAnimals || plugin.renderVillagers || plugin.renderMonsters || plugin.renderMarkerArmorStands || plugin.renderPlayers) {
            int minChunkX = minX >> 4;
            int maxChunkX = maxX >> 4;
            int minChunkZ = minZ >> 4;
            int maxChunkZ = maxZ >> 4;
            int animals = 0;
            int villagers = 0;
            int monsters = 0;
            int players = 0;
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ += 1) {
                for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX += 1) {
                    if (!world.isChunkLoaded(chunkX, chunkZ)) continue;
                    Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                    for (Entity e: chunk.getEntities()) {
                        if (!(e instanceof LivingEntity)) continue;
                        Location at = e.getLocation();
                        int x = at.getBlockX();
                        int z = at.getBlockZ();
                        if (x < minX || x > maxX) continue;
                        if (z < minZ || z > maxZ) continue;
                        MapCursor mapCursor;
                        if (e instanceof Mob && isHostile(e)) {
                            if (!plugin.renderMonsters) continue;
                            if (monsters++ >= plugin.maxMonsters) continue;
                            Mob mob = (Mob) e;
                            if (mob.isInvisible()) continue;
                            if (mob.hasMetadata("nomap")) continue;
                            mapCursor = Cursors.make(MapCursor.Type.RED_POINTER, at, session.centerX, session.centerZ);
                            Component customName = e.customName();
                            if (customName != null && !customName.equals(empty())) {
                                mapCursor.caption(customName);
                            }
                        } else if (e instanceof Animals) {
                            if (!plugin.renderAnimals) continue;
                            if (animals++ >= plugin.maxAnimals) continue;
                            Mob mob = (Mob) e; // Mob instanceof Animals!
                            if (mob.isInvisible()) continue;
                            if (mob.hasMetadata("nomap")) continue;
                            at.setPitch(0);
                            at.setYaw(0);
                            mapCursor = Cursors.make(MapCursor.Type.SMALL_WHITE_CIRCLE, at, session.centerX, session.centerZ);
                            Component customName = e.customName();
                            if (customName != null && !customName.equals(empty())) {
                                mapCursor.caption(customName);
                            }
                        } else if (e instanceof Villager) {
                            if (!plugin.renderVillagers) continue;
                            if (villagers++ >= plugin.maxVillagers) continue;
                            Villager villager = (Villager) e;
                            if (villager.isInvisible()) continue;
                            if (villager.hasMetadata("nomap")) continue;
                            mapCursor = Cursors.make(MapCursor.Type.WHITE_POINTER, at, session.centerX, session.centerZ);
                            Component customName = e.customName();
                            if (customName != null && !Component.empty().equals(customName)) {
                                mapCursor.caption(customName);
                            }
                        } else if (e instanceof ArmorStand) {
                            if (!plugin.renderMarkerArmorStands) continue;
                            ArmorStand stand = (ArmorStand) e;
                            if (!stand.isMarker()) continue;
                            if (stand.hasMetadata("nomap")) continue;
                            Component name = stand.customName();
                            if (name == null || name.equals(empty())) continue;
                            at.setPitch(0);
                            at.setYaw(0);
                            mapCursor = Cursors.make(MapCursor.Type.WHITE_CROSS, at, session.centerX, session.centerZ);
                            mapCursor.caption(name);
                        } else if (e instanceof Player) {
                            if (!plugin.renderPlayers) continue;
                            if (players++ >= plugin.maxPlayers) continue;
                            Player o = (Player) e;
                            if (o.hasMetadata("nomap")) continue;
                            if (o.isInvisible()) continue;
                            if (player.equals(o)) continue;
                            if (!player.canSee(o)) continue;
                            if (o.getGameMode() == GameMode.SPECTATOR) continue;
                            if (o.isSneaking()) continue;
                            mapCursor = Cursors.make(MapCursor.Type.BLUE_POINTER, at, session.centerX, session.centerZ);
                            if (plugin.renderPlayerNames) {
                                mapCursor.caption(o.displayName());
                            }
                        } else {
                            continue;
                        }
                        cursors.addCursor(mapCursor);
                    }
                }
            }
        }
        MapCursor pcur = Cursors.make(MapCursor.Type.GREEN_POINTER, loc,
                                      session.centerX, session.centerZ);
        pcur.caption(player.displayName());
        cursors.addCursor(pcur);
        if (plugin.renderCoordinates) {
            final int y = 127;
            final int rot = 8;
            final MapCursor.Type type = MapCursor.Type.SMALL_WHITE_CIRCLE;
            final int dist = 20;
            MapCursor icur;
            icur = Cursors.make(MapCursor.Type.WHITE_CIRCLE, 0, y, rot);
            icur.caption(text(plugin.getWorldName(player.getWorld())));
            cursors.addCursor(icur);
            icur = Cursors.make(type, 127 - dist - dist, y, rot);
            icur.caption(text(loc.getBlockX()));
            cursors.addCursor(icur);
            icur = Cursors.make(type, 127 - dist, y, rot);
            icur.caption(text(loc.getBlockY()));
            cursors.addCursor(icur);
            icur = Cursors.make(type, 127, y, rot);
            icur.caption(text(loc.getBlockZ()));
            cursors.addCursor(icur);
        }
        new MagicMapCursorEvent(player, cursors,
                                session.centerX, session.centerZ,
                                minX, minZ, maxX, maxZ).callEvent();
        session.pasteCursors = cursors;
        session.cursoring = false;
    }
}
