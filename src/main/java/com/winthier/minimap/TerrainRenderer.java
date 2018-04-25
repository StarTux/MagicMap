package com.winthier.minimap;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import lombok.Getter;
import lombok.Value;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.material.Colorable;
import org.bukkit.metadata.MetadataValue;

@Getter
public final class TerrainRenderer extends MapRenderer {
    private final MiniMapPlugin plugin;
    private ClaimsRenderer claimsRenderer;
    private ClaimRenderer claimRenderer;
    private DebugRenderer debugRenderer;
    private CreativeRenderer creativeRenderer;
    private static final int SHADOW_COLOR = Colors.DARK_GRAY + 3;
    private byte[] eventsImage = null;

    @Value static class XZ { private final int x, z; }

    @Value
    static class Storage {
        private final String world;
        private final int x, z;
        Storage(Block block) {
            world = block.getWorld().getName();
            x = block.getX() - 64;
            z = block.getZ() - 64;
        }
        boolean isTooFar(Block block) {
            if (!block.getWorld().getName().equals(world)) return true;
            int dx = x + 64 - block.getX();
            int dz = z + 64 - block.getZ();
            final int threshold = 24;
            return (Math.abs(dx) > threshold || Math.abs(dz) > threshold);
        }
    }

    enum RenderMode {
        SURFACE, CAVE, END, NETHER;
    }

    TerrainRenderer(MiniMapPlugin plugin) {
        super(true);
        this.plugin = plugin;
        if (plugin.getServer().getPluginManager().getPlugin("Claims") != null) {
            claimsRenderer = new ClaimsRenderer();
        }
        if (plugin.getServer().getPluginManager().getPlugin("Claim") != null) {
            claimRenderer = new ClaimRenderer();
        }
        if (plugin.getServer().getPluginManager().getPlugin("Creative") != null) {
            creativeRenderer = new CreativeRenderer(plugin);
        }
        debugRenderer = new DebugRenderer(plugin);
    }

    @Override
    public void render(MapView view, MapCanvas canvas, Player player) {
        Session session = plugin.getSession(player);
        MapCache mapCache = session.removeDrawMap();
        if (mapCache != null) mapCache.paste(canvas);
        MapCursorCollection mapCursorCollection = session.remove(MapCursorCollection.class);
        if (mapCursorCollection != null) canvas.setCursors(mapCursorCollection);
        switch (session.getMode()) {
        case MAP:
            plugin.getServer().getScheduler().runTask(plugin, () -> syncUpdateMap(player, session));
            if (session.isDrawAltitude()) {
                session.setDrawAltitude(false);
                int altitude = session.getAltitude();
                String str = "y:" + altitude;
                int width = plugin.getFont4x4().widthOf(str);
                int x = 64 - width / 2;
                for (int px = x; px < x + width; px += 1) {
                    for (int py = 0; py < 4; py += 1) {
                        canvas.setPixel(px, py, (byte)(Colors.WOOL_BLACK + 3));
                    }
                }
                int color;
                if (altitude <= 14) {
                    color = Colors.CYAN + 2;
                } else if (altitude <= 30) {
                    color = Colors.WOOL_YELLOW + 2;
                } else {
                    color = Colors.WOOL_SILVER + 2;
                }
                plugin.getFont4x4().print(str, x, 0, (mx, my, shadow) -> { if (my < 4) canvas.setPixel(mx, my, (byte)(!shadow ? color : SHADOW_COLOR));});
            }
            if (session.isDebug() && debugRenderer != null) {
                Storage storage = session.fetch(Storage.class);
                if (storage != null) {
                    debugRenderer.render(canvas, player, storage.getX(), storage.getZ());
                }
            }
            break;
        case MENU:
            plugin.getServer().getScheduler().runTask(plugin, () -> syncUpdateMenu(player, session));
            break;
        default:
            break;
        }
    }

    void syncUpdateMap(Player player, Session session) {
        if (!player.isOnline()) return;
        Storage storage = session.fetch(Storage.class);
        Location playerLocation = player.getLocation();
        Block playerBlock = playerLocation.getBlock();
        boolean needsRedraw;
        long now = System.currentTimeMillis();
        long sinceLastRender = System.currentTimeMillis() - session.getLastRender();
        if (storage == null || (sinceLastRender > 3000 && storage.isTooFar(playerBlock))) {
            storage = new Storage(playerBlock);
            session.store(storage);
            needsRedraw = true;
        } else if (sinceLastRender > 10000) {
            needsRedraw = true;
        } else {
            needsRedraw = false;
        }
        int ax = storage.getX();
        int az = storage.getZ();
        int cx = ax + 64;
        int cz = az + 64;
        int dist = Math.max(Math.abs(cx - playerBlock.getX()), Math.abs(cz - playerBlock.getZ()));
        RenderMode renderMode = session.fetch(RenderMode.class);
        if (needsRedraw && dist < 64) {
            MapCache mapCache = new MapCache();
            World world = player.getWorld();
            World.Environment environment = world.getEnvironment();
            if (environment == World.Environment.NETHER) {
                renderMode = RenderMode.NETHER;
            } else if (environment == World.Environment.THE_END) {
                renderMode = RenderMode.END;
            } else if (playerBlock.getY() < world.getHighestBlockYAt(playerBlock.getX(), playerBlock.getZ()) - 4
                && playerBlock.getLightFromSky() == 0
                && playerBlock.getRelative(0, 1, 0).getLightFromSky() == 0) {
                renderMode = RenderMode.CAVE;
            } else {
                renderMode = RenderMode.SURFACE;
            }
            session.store(renderMode);
            Map<XZ, Block> cache = new HashMap<>();
            for (int pz = 4; pz < 128; pz += 1) {
                for (int px = 0; px < 128; px += 1) {
                    int x = ax + px;
                    int z = az + pz;
                    Block block = getHighestBlockAt(world, x, z, cache, renderMode);
                    int color = colorOf(block, x, z, cache);
                    int shade;
                    if (block.isLiquid()) {
                        int a = liquidShadeOf(block, x, z);
                        int b = sunlightShadeOf(block, x, z, cache, renderMode);
                        if (a == 3 || b == 3) {
                            shade = 3;
                        } else {
                            shade = Math.min(a, b);
                        }
                    } else {
                        shade = sunlightShadeOf(block, x, z, cache, renderMode);
                    }
                    mapCache.setPixel(px, pz, color + shade);
                }
            }
            for (int y = 0; y < 4; y += 1) {
                for (int x = 0; x < 128; x += 1) {
                    mapCache.setPixel(x, y, Colors.WOOL_BLACK + 3);
                }
            }
            for (int x = 0; x < 128; x += 1) {
                mapCache.setPixel(x, 4, (mapCache.getPixel(x, 4) & ~0x3) + 3);
            }
            String worldName = plugin.getWorldName(player.getWorld().getName());
            plugin.getFont4x4().print(worldName, 1, 0, (x, y, shadow) -> { if (y < 4) mapCache.setPixel(x, y, !shadow ? Colors.PALE_BLUE + 2 : SHADOW_COLOR); });
            plugin.getFont4x4().print(renderMode.name(), 128 - plugin.getFont4x4().widthOf(renderMode.name()), 0, (x, y, shadow) -> { if (y < 4) mapCache.setPixel(x, y, !shadow ? Colors.RED + 2 : SHADOW_COLOR); });
            if (claimsRenderer != null) claimsRenderer.render(plugin, mapCache, player, ax, az);
            if (claimRenderer != null) claimRenderer.render(plugin, mapCache, player, ax, az);
            if (creativeRenderer != null) creativeRenderer.render(mapCache, player, ax, az);
            for (Marker marker: plugin.getMarkers()) {
                if (!marker.getWorld().equals(player.getWorld().getName())) continue;
                int x = marker.getX() - ax;
                int z = marker.getZ() - az - 2;
                if (x < 0 || x > 127) continue;
                if (z < 5 || z > 127) continue;
                x -= plugin.getFont4x4().widthOf(marker.getMessage()) / 2;
                plugin.getFont4x4().print(marker.getMessage(), x, z, (mx, my, shadow) -> mapCache.setPixel(mx, my, shadow ? (mapCache.getPixel(mx, my) & ~0x3) + 3 : Colors.WHITE + 2));
            }
            session.setLastRender(System.currentTimeMillis());
            session.setDrawMap(mapCache);
            session.setLastMap(mapCache);
        }
        if (renderMode == RenderMode.CAVE) {
            if (needsRedraw || playerLocation.getBlockY() != session.getAltitude()) {
                session.setAltitude(playerLocation.getBlockY());
                session.setDrawAltitude(true);
            }
        } else {
            session.setAltitude(-1);
        }
        if (dist >= 128) return;
        MapCursorCollection cursors = new MapCursorCollection();
        cursors.addCursor(Util.makeCursor(MapCursor.Type.WHITE_POINTER, playerLocation, ax, az));
        for (Entity e: player.getNearbyEntities(64, 64, 64)) {
            if (e instanceof Player) {
                Player nearby = (Player)e;
                if (nearby.equals(player)) continue;
                if (nearby.getGameMode() == GameMode.SPECTATOR) continue;
                cursors.addCursor(Util.makeCursor(MapCursor.Type.BLUE_POINTER, nearby.getLocation(), ax, az));
            } else if (e.getScoreboardTags().contains("ShowOnMiniMap")) {
                cursors.addCursor(Util.makeCursor(MapCursor.Type.RED_POINTER, e.getLocation(), ax, az));
            } else if (e instanceof Tameable) {
                if (player.equals(((Tameable)e).getOwner())) {
                    cursors.addCursor(Util.makeCursor(MapCursor.Type.GREEN_POINTER, e.getLocation(), ax, az));
                }
            } else {
                switch (e.getType()) {
                case ENDER_DRAGON:
                case WITHER:
                    cursors.addCursor(Util.makeCursor(MapCursor.Type.RED_POINTER, e.getLocation(), ax, az));
                    break;
                default: break;
                }
            }
        }
        for (MetadataValue meta: player.getMetadata("MiniMapCursors")) {
            try {
                if (meta.getOwningPlugin() == null || !meta.getOwningPlugin().isEnabled()) continue;
                List list = (List)meta.value();
                for (Object o: list) {
                    Map map = (Map)o;
                    Location location = (Location)map.get("location");
                    Block block = (Block)map.get("block");
                    MapCursor.Type cursorType = (MapCursor.Type)map.get("type");
                    if (cursorType == null) cursorType = MapCursor.Type.RED_POINTER;
                    if (location != null) {
                        cursors.addCursor(Util.makeCursor(cursorType, location, ax, az));
                    } else if (block != null) {
                        cursors.addCursor(Util.makeCursor(cursorType, block, ax, az));
                    }
                }
            } catch (Exception e) {
                System.err.println("Parsing problem with MiniMapCursors metadata from " + meta.getOwningPlugin().getName());
                e.printStackTrace();
            }
        }
        session.store(cursors);
    }

    void syncUpdateMenu(Player player, Session session) {
        if (player.getInventory().getItemInMainHand().getType() != Material.MAP ||
            player.getInventory().getItemInMainHand().getDurability() != plugin.getMapId()) {
            session.setMode(Session.Mode.MAP);
            session.remove(Storage.class); // Force update
            return;
        }
        if (!player.isOnline()) return;
        MapCursorCollection mapCursors = new MapCursorCollection();
        // Draw
        if (session.isMenuNeedsUpdate()) {
            MapCache mapCache = new MapCache();
            session.getMenuRects().clear();
            final Font4x4 font4 = plugin.getFont4x4();
            switch (session.getMenuLocation()) {
            case "events":
                byte[] img = getEventsImage();
                for (int i = 0; i < img.length; i += 1) {
                    mapCache.setPixel(i % 128, i / 128, (int)img[i]);
                }
                break;
            case "getting_started":
                drawDarkenedMap(session, mapCache);
                font4.print(mapCache, "Getting Started", 64 - font4.widthOf("Getting Started") / 2, 0, Colors.WOOL_LIME + 2, Colors.WOOL_BLACK);
                TileSet.getInstance().paste(TileSet.Tile.ICON_BUILD, mapCache, 32 - 8, 8);
                session.getMenuRects().add(new Session.Rect(32 - 8, 8, 16, 16, null, () -> player.performCommand("build")));
                font4.print(mapCache, "Survival Build", 32 - font4.widthOf("Survival Build") / 2, 26, Colors.WOOL_WHITE + 2, Colors.WOOL_BLACK);
                TileSet.getInstance().paste(TileSet.Tile.ICON_RESOURCE, mapCache, 96 - 8, 8);
                session.getMenuRects().add(new Session.Rect(96 - 8, 8, 16, 16, null, () -> player.performCommand("resource random")));
                font4.print(mapCache, "Resource", 96 - font4.widthOf("Resource") / 2, 26, Colors.WOOL_WHITE + 2, Colors.WOOL_BLACK);
                TileSet.getInstance().paste(TileSet.Tile.ICON_SPAWN, mapCache, 32 - 8, 36);
                session.getMenuRects().add(new Session.Rect(32 - 8, 36, 16, 16, null, () -> player.performCommand("spawn")));
                font4.print(mapCache, "Spawn", 32 - font4.widthOf("Spawn") / 2, 54, Colors.WOOL_WHITE + 2, Colors.WOOL_BLACK);
                TileSet.getInstance().paste(TileSet.Tile.ICON_MARKET, mapCache, 96 - 8, 36);
                session.getMenuRects().add(new Session.Rect(96 - 8, 36, 16, 16, null, () -> player.performCommand("shop market")));
                font4.print(mapCache, "Market", 96 - font4.widthOf("Market") / 2, 54, Colors.WOOL_WHITE + 2, Colors.WOOL_BLACK);
                break;
            case "settings":
                drawDarkenedMap(session, mapCache);
                plugin.getFont4x4().print(mapCache, "Settings", 64 - plugin.getFont4x4().widthOf("Settings") / 2, 0, Colors.WOOL_LIGHT_BLUE + 2, Colors.WOOL_BLACK);
                List<Map> settingList = new ArrayList<>();
                for (MetadataValue meta: player.getMetadata("MiniMapSettings")) {
                    if (meta.value() instanceof List) {
                        for (Object o: (List)meta.value()) {
                            if (o instanceof Map) {
                                Map map = (Map)o;
                                settingList.add(map);
                            }
                        }
                    }
                }
                Collections.sort(settingList, (Map a, Map b) -> {
                        Integer ia = (Integer)a.get("Priority");
                        Integer ib = (Integer)b.get("Priority");
                        if (ia == null) ia = 0;
                        if (ib == null) ib = 0;
                        return Integer.compare((Integer)ib, (Integer)ia);
                    });
                int y = 10;
                for (Map map: settingList) {
                    try {
                        if ("Boolean".equals(map.get("Type"))) {
                            boolean v = map.get("Value") == Boolean.TRUE;
                            if (v) {
                                TileSet.getInstance().paste(TileSet.Tile.CHECKBOX_CHECK, mapCache, 1, y);
                            } else {
                                TileSet.getInstance().paste(TileSet.Tile.CHECKBOX_EMPTY, mapCache, 1, y);
                            }
                            String displayName = (String)map.get("DisplayName");
                            if (v) {
                                plugin.getFont4x4().print(mapCache, displayName, 12, y + 2, Colors.WOOL_YELLOW + 2, Colors.WOOL_BLACK);
                            } else {
                                plugin.getFont4x4().print(mapCache, displayName, 12, y + 2, Colors.WOOL_WHITE + 2, Colors.WOOL_BLACK);
                            }
                            Session.Rect rect = new Session.Rect(1, y, 8, 8, "settings");
                            final Runnable onClick = (Runnable)map.get("OnUpdate");
                            rect.onClick = () -> {
                                map.put("Value", !v);
                                if (onClick != null) onClick.run();
                            };
                            session.getMenuRects().add(rect);
                        }
                        y += 11;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            case "main": default:
                session.setMenuLocation("main");
                drawDarkenedMap(session, mapCache);
                java.util.Random rnd = new java.util.Random(System.currentTimeMillis());
                int ti = 0;
                TileSet.getInstance().paste(TileSet.Tile.BUTTON_GETTING_STARTED, mapCache, 8 + (ti % 3) * 40, 8 + (ti / 3) * 40);
                session.getMenuRects().add(new Session.Rect(8 + (ti % 3) * 40, 8 + (ti / 3) * 40, 32, 32, "getting_started"));
                ti += 1;
                TileSet.getInstance().paste(TileSet.Tile.BUTTON_EVENTS, mapCache, 8 + (ti % 3) * 40, 8 + (ti / 3) * 40);
                session.getMenuRects().add(new Session.Rect(8 + (ti % 3) * 40, 8 + (ti / 3) * 40, 32, 32, "events"));
                ti += 1;
                TileSet.getInstance().paste(TileSet.Tile.BUTTON_SETTINGS, mapCache, 8 + (ti % 3) * 40, 8 + (ti / 3) * 40);
                session.getMenuRects().add(new Session.Rect(8 + (ti % 3) * 40, 8 + (ti / 3) * 40, 32, 32, "settings"));
                TileSet.getInstance().paste(TileSet.Tile.BUTTON_COMING_SOON, mapCache, 8, 48);
                TileSet.getInstance().paste(TileSet.Tile.BUTTON_COMING_SOON, mapCache, 48, 48);
                TileSet.getInstance().paste(TileSet.Tile.BUTTON_COMING_SOON, mapCache, 88, 48);
                TileSet.getInstance().paste(TileSet.Tile.BUTTON_COMING_SOON, mapCache, 8, 88);
                TileSet.getInstance().paste(TileSet.Tile.BUTTON_COMING_SOON, mapCache, 48, 88);
                TileSet.getInstance().paste(TileSet.Tile.BUTTON_COMING_SOON, mapCache, 88, 88);
                session.setDrawMap(mapCache);
            }
            session.setDrawMap(mapCache);
        }
        session.setMenuNeedsUpdate(false);
        // Mouse Cursor
        Location loc = player.getLocation();
        @Value class LookAt {
            float pitch, yaw;
        }
        LookAt oldLookAt = session.remove(LookAt.class);
        LookAt newLookAt = new LookAt(loc.getPitch(), loc.getYaw());
        if (oldLookAt != null) {
            float dy = newLookAt.pitch - oldLookAt.pitch;
            if (plugin.getUserSettings(player.getUniqueId()).getBoolean("InvertMouseY")) dy = -dy;
            float dx = newLookAt.yaw - oldLookAt.yaw;
            if (dx > 180f) dx -= 360f;
            if (dx < -180f) dx += 360f;
            float mx = session.getMouseX() + dx * 2f;
            float my = session.getMouseY() + dy * 2f;
            if (mx < 0f) mx = 0f;
            if (mx > 127f) mx = 127f;
            if (my < 0f) my = 0f;
            if (my > 127f) my = 127f;
            session.setMouseX(mx);
            session.setMouseY(my);
        }
        int mouseXi = (int)session.getMouseX();
        int mouseYi = (int)session.getMouseY();
        boolean mouseOverButton = false;
        for (Session.Rect menuRect: session.getMenuRects()) {
            if (menuRect.contains(mouseXi, mouseYi)) {
                mouseOverButton = true;
                break;
            }
        }
        if (mouseOverButton) {
            mapCursors.addCursor(Util.makeCursor(MapCursor.Type.WHITE_CROSS, mouseXi, mouseYi, 0));
        } else {
            mapCursors.addCursor(Util.makeCursor(MapCursor.Type.WHITE_CIRCLE, mouseXi, mouseYi, 0));
        }
        session.store(newLookAt);
        session.store(mapCursors);
    }

    void drawDarkenedMap(Session session, MapCache mapCache) {
        MapCache lastMap = session.getLastMap();
        if (lastMap != null) {
            for (int y = 0; y < 128; y += 1) {
                for (int x = 0; x < 128; x += 1) {
                    if (1 == (y & 1)) {
                        mapCache.setPixel(x, y, Colors.WOOL_BLACK);
                    } else {
                        int pixel = lastMap.getPixel(x, y);
                        pixel = pixel | 3;
                        mapCache.setPixel(x, y, pixel);
                    }
                }
            }
        }
    }

    void onClickMenu(Player player, Session session, int x, int y) {
        for (Session.Rect rect: session.getMenuRects()) {
            if (rect.contains(x, y)) {
                Runnable onClick = rect.getOnClick();
                if (onClick != null) {
                    onClick.run();
                }
                String target = rect.getTarget();
                if (target != null) {
                    session.setMenuLocation(target);
                    session.setMenuNeedsUpdate(true);
                }
                player.playSound(player.getEyeLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            }
        }
    }

    private static int directionOf(Location location) {
        int dir = (int)(location.getYaw() + 11.25f);
        while (dir < 0) dir += 360;
        while (dir > 360) dir -= 360;
        dir = dir * 2 / 45;
        if (dir < 0) dir = 0;
        if (dir > 15) dir = 15;
        return dir;
    }

    private static int sunlightShadeOf(Block block, int x, int y, Map<XZ, Block> cache, RenderMode mode) {
        switch (block.getType()) {
        case FIRE:
        case GLOWSTONE:
        case JACK_O_LANTERN:
        case REDSTONE_LAMP_ON:
        case SEA_LANTERN:
        case TORCH:
            return 2;
        }
        int lx, ly;
        switch (mode) {
        case NETHER: case CAVE: case END:
            lx = 1; ly = 1;
            break;
        default:
            long time = block.getWorld().getTime();
            if (time < 1500) {
                lx = 1; ly = 0;
            } else if (time < 4500) {
                lx = 1; ly = -1;
            } else if (time < 7500) {
                lx = 0; ly = -1;
            } else if (time < 10500) {
                lx = -1; ly = -1;
            } else if (time < 13500) {
                lx = -1; ly = 0;
            } else if (time < 16500) {
                lx = -1; ly = 1;
            } else if (time < 19500) {
                lx = 0; ly = 1;
            } else if (time < 22500) {
                lx = 1; ly = 1;
            } else {
                lx = 1; ly = 0;
            }
        }
        int heightDiff = block.getY() - getHighestBlockAt(block.getWorld(), block.getX() + lx, block.getZ() + ly, cache, mode).getY();
        if (heightDiff == 0) {
            return 1;
        } else if (heightDiff > 0) {
            return 2;
        } else if (heightDiff < -3) {
            return 3;
        } else {
            return 0;
        }
    }

    private static int liquidShadeOf(Block block, int x, int y) {
        int depth = 1;
        while (block.getRelative(0, -depth, 0).isLiquid()) depth += 1;
        if (depth <= 2) {
            return 2;
        } else if (depth <= 4) {
            if (((x & 1) == 0) ^ ((y & 1) == 0)) {
                return 2;
            } else {
                return 1;
            }
        } else if (depth <= 6) {
            return 1;
        } else if (depth <= 8) {
            if (((x & 1) == 0) ^ ((y & 1) == 0)) {
                return 1;
            } else {
                return 0;
            }
        } else {
            return 0;
        }
    }

    private static int colorOf(Block block, int x, int y, Map<XZ, Block> cache) {
        if (block.getY() < 0) return Colors.WOOL_BLACK;
        switch (block.getType()) {
        case WATER: case STATIONARY_WATER:
            return Colors.BLUE;
        case LAVA: case STATIONARY_LAVA: return Colors.RED;
        case GRASS: return Colors.LIGHT_GREEN;
        case LEAVES: case LEAVES_2: return Colors.DARK_GREEN;
        case SAND:
            switch (block.getData()) {
            case 1: return Colors.WOOL_ORANGE;
            default: return Colors.LIGHT_BROWN;
            }
        case GRASS_PATH: return Colors.BROWN;
        case SANDSTONE: case SANDSTONE_STAIRS: return Colors.LIGHT_BROWN;
        case RED_SANDSTONE: case RED_SANDSTONE_STAIRS: return Colors.WOOL_ORANGE;
        case DIRT: case SOIL: case LOG: case LOG_2: return Colors.DARK_BROWN;
        case COBBLESTONE: case COBBLE_WALL: case MOSSY_COBBLESTONE: case SMOOTH_BRICK: case SMOOTH_STAIRS: return Colors.LIGHT_GRAY;
        case GRAVEL:
            return ((x & 1) == 0) ^ ((y & 1) == 0) ? Colors.GRAY_1 : Colors.LIGHT_GRAY;
        case STONE:
            switch (block.getData() & 0x7) {
            case 0: return Colors.LIGHT_GRAY; // Smoothstone
            case 1: case 2: return Colors.BROWN; // Granite
            case 3: case 4: return Colors.WHITE; // Diorite
            case 5: case 6: return Colors.LIGHT_GRAY; // Andesite
            default: return Colors.LIGHT_GRAY;
            }
        case COAL_ORE: case DIAMOND_ORE: case EMERALD_ORE: case GLOWING_REDSTONE_ORE: case GOLD_ORE: case IRON_ORE: case LAPIS_ORE: case REDSTONE_ORE: return Colors.LIGHT_GRAY;
        case ICE: case FROSTED_ICE: return Colors.ROYAL_BLUE;
        case PACKED_ICE: return Colors.WOOL_LIGHT_BLUE;
        case SNOW: case SNOW_BLOCK: return Colors.WHITE;
        case PUMPKIN: return Colors.WOOL_ORANGE;
        case CLAY: return Colors.GRAY_1;
        case HARD_CLAY: return Colors.WOOL_RED;
        case BRICK: return Colors.WOOL_RED;
        case QUARTZ_BLOCK: case QUARTZ_STAIRS: return Colors.WHITE;
        case WOOD: case WOOD_STAIRS: case WOOD_STEP: case WOOD_DOUBLE_STEP: case TRAP_DOOR: return Colors.BROWN;
        case STEP:
        case DOUBLE_STEP:
            switch (block.getData() & 0x7) {
            case 0: return Colors.LIGHT_GRAY; // Stone
            case 1: return Colors.LIGHT_BROWN; // Sandstone
            case 2: return Colors.LIGHT_BROWN; // Wood
            case 3: return Colors.LIGHT_GRAY; // Cobble
            case 4: return Colors.WOOL_RED; // Brick
            case 5: return Colors.LIGHT_GRAY; // Stone Brick
            case 6: return Colors.MAROON; // Nether Brick
            case 7: return Colors.WHITE; // Quartz
            default: return 0;
            }
        case HUGE_MUSHROOM_1: return Colors.BROWN;
        case HUGE_MUSHROOM_2: return Colors.RED;
        case LAPIS_BLOCK: return Colors.BLUE;
        case EMERALD_BLOCK: return Colors.LIGHT_GREEN;
        case REDSTONE_BLOCK: return Colors.RED;
        case DIAMOND_BLOCK: return Colors.PALE_BLUE;
        case GOLD_BLOCK: return Colors.WOOL_YELLOW;
        case IRON_BLOCK: return Colors.WHITE;
        case MYCEL: return Colors.WOOL_PURPLE;
        case OBSIDIAN: case ENDER_CHEST: return Colors.WOOL_BLACK;
        case WOOL:
        case STAINED_CLAY:
        case STAINED_GLASS:
        case CONCRETE:
        case CONCRETE_POWDER:
        case CARPET:
            switch (block.getData()) {
            case 0: return Colors.WOOL_WHITE;
            case 1: return Colors.WOOL_ORANGE;
            case 2: return Colors.WOOL_MAGENTA;
            case 3: return Colors.WOOL_LIGHT_BLUE;
            case 4: return Colors.WOOL_YELLOW;
            case 5: return Colors.WOOL_LIME;
            case 6: return Colors.WOOL_PINK;
            case 7: return Colors.WOOL_GRAY;
            case 8: return Colors.WOOL_SILVER;
            case 9: return Colors.WOOL_CYAN;
            case 10: return Colors.WOOL_PURPLE;
            case 11: return Colors.WOOL_BLUE;
            case 12: return Colors.WOOL_BROWN;
            case 13: return Colors.WOOL_GREEN;
            case 14: return Colors.WOOL_RED;
            case 15: return Colors.WOOL_BLACK;
            default: return 0;
            }
        // Terracotta
        case WHITE_GLAZED_TERRACOTTA: return Colors.WOOL_WHITE;
        case ORANGE_GLAZED_TERRACOTTA: return Colors.WOOL_ORANGE;
        case MAGENTA_GLAZED_TERRACOTTA: return Colors.WOOL_MAGENTA;
        case LIGHT_BLUE_GLAZED_TERRACOTTA: return Colors.WOOL_LIGHT_BLUE;
        case YELLOW_GLAZED_TERRACOTTA: return Colors.WOOL_YELLOW;
        case LIME_GLAZED_TERRACOTTA: return Colors.WOOL_LIME;
        case PINK_GLAZED_TERRACOTTA: return Colors.WOOL_PINK;
        case GRAY_GLAZED_TERRACOTTA: return Colors.WOOL_GRAY;
        case SILVER_GLAZED_TERRACOTTA: return Colors.WOOL_SILVER;
        case CYAN_GLAZED_TERRACOTTA: return Colors.WOOL_CYAN;
        case PURPLE_GLAZED_TERRACOTTA: return Colors.WOOL_PURPLE;
        case BLUE_GLAZED_TERRACOTTA: return Colors.WOOL_BLUE;
        case BROWN_GLAZED_TERRACOTTA: return Colors.WOOL_BROWN;
        case GREEN_GLAZED_TERRACOTTA: return Colors.WOOL_GREEN;
        case RED_GLAZED_TERRACOTTA: return Colors.WOOL_RED;
        case BLACK_GLAZED_TERRACOTTA: return Colors.WOOL_BLACK;
        // Shulker
        case WHITE_SHULKER_BOX: return Colors.WOOL_WHITE;
        case ORANGE_SHULKER_BOX: return Colors.WOOL_ORANGE;
        case MAGENTA_SHULKER_BOX: return Colors.WOOL_MAGENTA;
        case LIGHT_BLUE_SHULKER_BOX: return Colors.WOOL_LIGHT_BLUE;
        case YELLOW_SHULKER_BOX: return Colors.WOOL_YELLOW;
        case LIME_SHULKER_BOX: return Colors.WOOL_LIME;
        case PINK_SHULKER_BOX: return Colors.WOOL_PINK;
        case GRAY_SHULKER_BOX: return Colors.WOOL_GRAY;
        case SILVER_SHULKER_BOX: return Colors.WOOL_SILVER;
        case CYAN_SHULKER_BOX: return Colors.WOOL_CYAN;
        case PURPLE_SHULKER_BOX: return Colors.WOOL_PURPLE;
        case BLUE_SHULKER_BOX: return Colors.WOOL_BLUE;
        case BROWN_SHULKER_BOX: return Colors.WOOL_BROWN;
        case GREEN_SHULKER_BOX: return Colors.WOOL_GREEN;
        case RED_SHULKER_BOX: return Colors.WOOL_RED;
        case BLACK_SHULKER_BOX: return Colors.WOOL_BLACK;
        //
        case SUGAR_CANE_BLOCK: return Colors.LIGHT_GREEN;
        case WATER_LILY: return Colors.DARK_GREEN;
        case CACTUS: return Colors.DARK_GREEN;
        case NETHERRACK: case QUARTZ_ORE: case SOUL_SAND: case NETHER_STALK: case NETHER_WART_BLOCK: return Colors.MAROON;
        case CROPS: return Colors.LIGHT_BROWN;
        case POTATO: return Colors.LIGHT_BROWN;
        case CARROT: return Colors.WOOL_ORANGE;
        case BEETROOT_BLOCK: return Colors.WOOL_PINK;
        case ACACIA_DOOR: case ACACIA_FENCE: case ACACIA_FENCE_GATE: case ACACIA_STAIRS: return Colors.WOOL_ORANGE;
        case BIRCH_DOOR: case BIRCH_FENCE: case BIRCH_WOOD_STAIRS: return Colors.LIGHT_BROWN;
        case DARK_OAK_DOOR: case DARK_OAK_FENCE: case DARK_OAK_STAIRS: return Colors.DARK_BROWN;
        case SPRUCE_DOOR: case SPRUCE_FENCE: case SPRUCE_WOOD_STAIRS: return Colors.DARK_BROWN;
        case JUNGLE_DOOR: case JUNGLE_FENCE: case JUNGLE_WOOD_STAIRS: return Colors.BROWN;
        case NETHER_FENCE: return Colors.MAROON;
        case FENCE: case FENCE_GATE: return Colors.BROWN;
        case IRON_DOOR: return Colors.WHITE;
        case IRON_FENCE: return Colors.LIGHT_GRAY;
        case VINE: return Colors.DARK_GREEN;
        case WEB: return Colors.WHITE;
        case MELON_BLOCK: return Colors.DARK_GREEN;
        case ENDER_STONE: case END_BRICKS: return Colors.LIGHT_BROWN;
        case END_ROD: case BEACON: case END_CRYSTAL: return Colors.WHITE;
        case PURPUR_BLOCK: case PURPUR_DOUBLE_SLAB: case PURPUR_PILLAR: case PURPUR_SLAB: case PURPUR_STAIRS: return Colors.WOOL_MAGENTA;
        case PRISMARINE: return Colors.CYAN;
        case CHORUS_FLOWER: case CHORUS_FRUIT: case CHORUS_PLANT: return Colors.WOOL_PURPLE;
        case BEDROCK: return Colors.DARK_GRAY;
        case TORCH: case GLOWSTONE: case REDSTONE_LAMP_ON: case JACK_O_LANTERN: case FIRE: return Colors.WOOL_YELLOW;
        case SEA_LANTERN: return Colors.ROYAL_BLUE;
        case BED_BLOCK:
        default:
            BlockState blockState = block.getState();
            if (blockState instanceof Colorable) {
                Colorable colorable = (Colorable)blockState;
                switch (colorable.getColor()) {
                case WHITE: return Colors.WOOL_WHITE;
                case ORANGE: return Colors.WOOL_ORANGE;
                case MAGENTA: return Colors.WOOL_MAGENTA;
                case LIGHT_BLUE: return Colors.WOOL_LIGHT_BLUE;
                case YELLOW: return Colors.WOOL_YELLOW;
                case LIME: return Colors.WOOL_LIME;
                case PINK: return Colors.WOOL_PINK;
                case GRAY: return Colors.WOOL_GRAY;
                case SILVER: return Colors.WOOL_SILVER;
                case CYAN: return Colors.WOOL_CYAN;
                case PURPLE: return Colors.WOOL_PURPLE;
                case BLUE: return Colors.WOOL_BLUE;
                case BROWN: return Colors.WOOL_BROWN;
                case GREEN: return Colors.WOOL_GREEN;
                case RED: return Colors.WOOL_RED;
                case BLACK: return Colors.WOOL_BLACK;
                default: break;
                }
            }
            return Colors.WOOL_BROWN;
        }
    }

    private static Block lowerTransparent(Block block) {
        while (block.getY() >= 0 && block.getType().isTransparent()) {
            switch (block.getType()) {
            case SNOW:
            case WATER_LILY:
            case CROPS:
            case POTATO:
            case CARROT:
            case BEETROOT_BLOCK:
            case TORCH:
            case FIRE:
            case SUGAR_CANE_BLOCK:
            case CARPET:
                return block;
            default: break;
            }
            block = block.getRelative(0, -1, 0);
        }
        return block;
    }

    private static Block getHighestBlockAt(World world, int x, int z, Map<XZ, Block> cache, RenderMode mode) {
        XZ xz = new XZ(x, z);
        Block block = cache.get(xz);
        if (block != null) return block;
        switch (mode) {
        case SURFACE:
        case END:
            block = world.getHighestBlockAt(x, z);
            block = lowerTransparent(block);
            break;
        case CAVE:
            block = world.getHighestBlockAt(x, z);
            while (block.getY() >= 0 && (block.getType() != Material.AIR || block.getLightFromSky() > 0)) {
                block = block.getRelative(0, -1, 0);
            }
            block = lowerTransparent(block);
            break;
        case NETHER:
            block = world.getBlockAt(x, 127, z);
            while (block.getY() >= 0 && block.getType() != Material.AIR) {
                block = block.getRelative(0, -1, 0);
            }
            block = lowerTransparent(block);
            break;
        default:
            block = world.getBlockAt(x, 0, z);
        }
        cache.put(xz, block);
        return block;
    }

    void reload() {
        eventsImage = null;
    }

    private byte[] getEventsImage() {
        if (eventsImage == null) {
            try {
                BufferedImage image = ImageIO.read(new File(plugin.getDataFolder(), "events.png"));
                eventsImage = MapPalette.imageToBytes(image);
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        return eventsImage;
    }
}
