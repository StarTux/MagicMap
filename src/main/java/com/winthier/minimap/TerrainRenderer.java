package com.winthier.minimap;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Value;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.material.Colorable;

@Getter
public final class TerrainRenderer extends MapRenderer {
    private final MiniMapPlugin plugin;
    private ClaimRenderer claimRenderer;

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

    TerrainRenderer(MiniMapPlugin plugin) {
        super(true);
        this.plugin = plugin;
        if (plugin.getServer().getPluginManager().getPlugin("Claims") != null) {
            claimRenderer = new ClaimRenderer();
        }
    }

    private boolean isHoldingMap(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.MAP && item.getDurability() == plugin.getMapId()) return true;
        item = player.getInventory().getItemInOffHand();
        if (item.getType() == Material.MAP && item.getDurability() == plugin.getMapId()) return true;
        return false;
    }

    @Override
    public void render(MapView view, MapCanvas canvas, Player player) {
        Session session = plugin.getSession(player);
        if (session.getLastRender() != 0 && !isHoldingMap(player)) return;
        if (player.getWorld().getEnvironment() != World.Environment.NORMAL) return;
        Storage storage = (Storage)session.getStorage().get(Storage.class);
        Location playerLocation = player.getLocation();
        Block playerBlock = playerLocation.getBlock();
        boolean needsRedraw;
        long now = System.currentTimeMillis();
        long sinceLastRender = System.currentTimeMillis() - session.getLastRender();
        if (storage == null || (sinceLastRender > 3000 && storage.isTooFar(playerBlock))) {
            storage = new Storage(playerBlock);
            session.getStorage().put(Storage.class, storage);
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
        if (needsRedraw && dist < 64) {
            World world = player.getWorld();
            boolean caveView = playerBlock.getY() < world.getHighestBlockYAt(playerBlock.getX(), playerBlock.getZ()) - 4
                && playerBlock.getLightFromSky() == 0
                && playerBlock.getRelative(0, 1, 0).getLightFromSky() == 0;
            String mapModeName;
            if (caveView) {
                drawCaveMap(canvas, world, ax, az);
                mapModeName = "Cave";
            } else {
                Map<XZ, Block> cache = new HashMap<>();
                for (int pz = 5; pz < 128; pz += 1) {
                    for (int px = 0; px < 128; px += 1) {
                        int x = ax + px;
                        int z = az + pz;
                        Block block = getHighestBlockAt(world, x, z, cache);
                        canvas.setPixel(px, pz, (byte)colorOf(block, px, pz, cache));
                    }
                }
                mapModeName = "Surface";
            }
            for (int y = 0; y < 5; y += 1) {
                for (int x = 0; x < 128; x += 1) {
                    canvas.setPixel(x, y, (byte)Colors.BLACK);
                }
            }
            final int shadowColor = Colors.DARK_GRAY + 3;
            String worldName = plugin.getWorldName(player.getWorld().getName());
            plugin.getFont4x4().print(canvas, worldName, 1, 0, -1, -1, Colors.PALE_BLUE + 2, shadowColor);
            plugin.getFont4x4().print(canvas, mapModeName, 127 - plugin.getFont4x4().widthOf(mapModeName), 0, -1, -1, Colors.RED, shadowColor);
            if (claimRenderer != null) claimRenderer.render(plugin, canvas, player, ax, az);
            for (Marker marker: plugin.getMarkers()) {
                if (!marker.getWorld().equals(player.getWorld().getName())) continue;
                int x = marker.getX() - ax;
                int z = marker.getZ() - az - 2;
                if (x < 0 || x > 127) continue;
                if (z < 5 || z > 127) continue;
                x -= plugin.getFont4x4().widthOf(marker.getMessage()) / 2;
                plugin.getFont4x4().print(canvas, marker.getMessage(), x, z, -1, -1, Colors.WHITE + 2, Colors.DARK_GRAY + 3);
            }
            session.setLastRender(System.currentTimeMillis());
        }
        MapCursorCollection cursors = new MapCursorCollection();
        cursors.addCursor(Util.makeCursor(MapCursor.Type.WHITE_POINTER, playerLocation, ax, az));
        if (dist < 128) {
            for (Player nearby: player.getWorld().getPlayers()) {
                if (nearby.equals(player)) continue;
                if (nearby.getGameMode() == GameMode.SPECTATOR) continue;
                Location loc = nearby.getLocation();
                int px = (loc.getBlockX() - cx) * 2;
                int pz = (loc.getBlockZ() - cz) * 2;
                if (px < -127 || px > 127 || pz < -127 || pz > 127) continue;
                cursors.addCursor(Util.makeCursor(MapCursor.Type.BLUE_POINTER, loc, ax, az));
            }
        }
        canvas.setCursors(cursors);
        if (plugin.getCreativeRenderer() != null) plugin.getCreativeRenderer().render(canvas, player, ax, az);
    }

    void drawCaveMap(MapCanvas canvas, World world, int ax, int az) {
        Map<XZ, Block> cache = new HashMap<>();
        for (int pz = 5; pz < 128; pz += 1) {
            for (int px = 0; px < 128; px += 1) {
                int dx = px - 64;
                int dz = pz - 64;
                int dist = dx * dx + dz * dz;
                if (dist >= 60 * 60) {
                    canvas.setPixel(px, pz, (byte)Colors.BLACK);
                } else if (dist >= 40 * 40 && (px % 2 == 0 ^ pz % 2 == 0)) {
                    canvas.setPixel(px, pz, (byte)Colors.BLACK);
                } else {
                    int x = ax + px;
                    int z = az + pz;
                    Block block = getHighestBlockAt(world, x, z, cache);
                    while (block.getY() >= 0 && (block.getType() != Material.AIR || block.getLightFromSky() > 0)) {
                        block = block.getRelative(0, -1, 0);
                    }
                    while (block.getY() > 0 && !block.getType().isSolid() && !block.isLiquid()) block = block.getRelative(0, -1, 0);
                    if (block.getY() < 0) {
                        canvas.setPixel(px, pz, (byte)Colors.BLACK);
                    } else {
                        int color;
                        int y = block.getY();
                        switch (block.getType()) {
                        case LAVA: case STATIONARY_LAVA:
                            color = Colors.RED + 3;
                            break;
                        case WATER: case STATIONARY_WATER:
                            color = Colors.BLUE + 3;
                            break;
                        default:
                            if (y < 8) {
                                color = Colors.DARK_GREEN + 3;
                            } else if (y < 16) {
                                color = Colors.DARK_GREEN;
                            } else if (y < 24) {
                                color = Colors.DARK_GREEN + 1;
                            } else if (y < 32) {
                                color = Colors.DARK_GREEN + 2;
                            } else if (y < 40) {
                                color = Colors.YELLOW + 3;
                            } else if (y < 48) {
                                color = Colors.YELLOW;
                            } else if (y < 56) {
                                color = Colors.YELLOW + 1;
                            } else {
                                color = Colors.YELLOW + 2;
                            }
                        }
                        canvas.setPixel(px, pz, (byte)color);
                    }
                }
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

    private static int colorOf(Block block, int x, int y, Map<XZ, Block> cache) {
        if (block.getY() < 0) return Colors.TRANSPARENT;
        final int shade;
        int lx, ly;
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
        int heightDiff = block.getY() - getHighestBlockAt(block.getWorld(), block.getX() + lx, block.getZ() + ly, cache).getY();
        if (heightDiff == 0) {
            shade = 1;
        } else if (heightDiff > 0) {
            shade = 2;
        } else if (heightDiff < -3) {
            shade = 3;
        } else {
            shade = 0;
        }
        switch (block.getType()) {
        case WATER: case STATIONARY_WATER:
            int depth = 1;
            while (block.getRelative(0, -depth, 0).isLiquid()) depth += 1;
            final int depthShade;
            if (depth <= 2) {
                depthShade = 2;
            } else if (depth <= 4) {
                boolean x1 = x % 2 == 0;
                boolean x2 = y % 2 == 0;
                if (x1 ^ x2) {
                    depthShade = 2;
                } else {
                    depthShade = 1;
                }
            } else if (depth <= 6) {
                depthShade = 1;
            } else if (depth <= 8) {
                boolean x1 = x % 2 == 0;
                boolean x2 = y % 2 == 0;
                if (x1 ^ x2) {
                    depthShade = 1;
                } else {
                    depthShade = 0;
                }
            } else {
                depthShade = 0;
            }
            return Colors.BLUE + depthShade;
        case LAVA: case STATIONARY_LAVA: return Colors.RED + shade;
        case GRASS: return Colors.LIGHT_GREEN + shade;
        case LEAVES: case LEAVES_2: return Colors.DARK_GREEN + shade;
        case SAND:
            switch (block.getData()) {
            case 1: return Colors.PALE_RED + shade;
            default: return Colors.LIGHT_BROWN + shade;
            }
        case GRASS_PATH: return Colors.BROWN + shade;
        case SANDSTONE: case SANDSTONE_STAIRS: return Colors.LIGHT_BROWN + shade;
        case RED_SANDSTONE: case RED_SANDSTONE_STAIRS: return Colors.PALE_RED + shade;
        case DIRT: case SOIL: case LOG: case LOG_2: return Colors.DARK_BROWN + shade;
        case STONE: case COBBLESTONE: case MOSSY_COBBLESTONE: case GRAVEL: case SMOOTH_BRICK: case SMOOTH_STAIRS: return Colors.LIGHT_GRAY + shade;
        case ICE: case PACKED_ICE: return Colors.PALE_BLUE + shade;
        case SNOW: case SNOW_BLOCK: return Colors.WHITE + shade;
        case PUMPKIN: case JACK_O_LANTERN: return Colors.RED + shade;
        case CLAY: case CLAY_BRICK: case HARD_CLAY: return Colors.PALE_RED + shade;
        case QUARTZ_BLOCK: case QUARTZ_STAIRS: return Colors.WHITE + shade;
        case WOOD: case BIRCH_WOOD_STAIRS: case DARK_OAK_STAIRS: case JUNGLE_WOOD_STAIRS: case SPRUCE_WOOD_STAIRS: case WOOD_STAIRS: case WOOD_STEP: case WOOD_DOUBLE_STEP: case TRAP_DOOR: return Colors.BROWN + shade;
        case STEP:
        case DOUBLE_STEP:
            switch (block.getData() & 0x7) {
            case 0: return Colors.LIGHT_GRAY + shade; // Stone
            case 1: return Colors.LIGHT_BROWN + shade; // Sandstone
            case 2: return Colors.LIGHT_BROWN + shade; // Wood
            case 3: return Colors.LIGHT_GRAY + shade; // Cobble
            case 4: return Colors.PALE_RED + shade; // Brick
            case 5: return Colors.LIGHT_GRAY + shade; // Stone Brick
            case 6: return Colors.BLACK + shade; // Nether Brick
            case 7: return Colors.WHITE + shade; // Quartz
            default: return 0;
            }
        case HUGE_MUSHROOM_1: return Colors.BROWN + shade;
        case HUGE_MUSHROOM_2: return Colors.RED + shade;
        case LAPIS_BLOCK: return Colors.BLUE + shade;
        case EMERALD_BLOCK: return Colors.LIGHT_GREEN + shade;
        case REDSTONE_BLOCK: return Colors.RED + shade;
        case DIAMOND_BLOCK: return Colors.PALE_BLUE + shade;
        case GOLD_BLOCK: return Colors.YELLOW + shade;
        case IRON_BLOCK: return Colors.WHITE + shade;
        case MYCEL: return Colors.DARK_PURPLE + shade;
        case WOOL:
        case STAINED_CLAY:
            switch (block.getData()) {
            case 0: return 32; // DyeColor.WHITE
            case 1: return 60; // DyeColor.ORANGE
            case 2: return 64; // DyeColor.MAGENTA
            case 3: return 68; // DyeColor.LIGHT_BLUE
            case 4: return 72; // DyeColor.YELLOW
            case 5: return 76; // DyeColor.LIME
            case 6: return 80; // DyeColor.PINK
            case 7: return 84; // DyeColor.GRAY
            case 8: return 88; // DyeColor.SILVER
            case 9: return 92; // DyeColor.CYAN
            case 10: return 96; // DyeColor.PURPLE
            case 11: return 100; // DyeColor.BLUE
            case 12: return 104; // DyeColor.BROWN
            case 13: return 108; // DyeColor.GREEN
            case 14: return 112; // DyeColor.RED
            case 15: return 116; // DyeColor.BLACK
            default: return 0;
            }
        case SUGAR_CANE_BLOCK: return Colors.LIGHT_GREEN + shade;
        case WATER_LILY: return Colors.DARK_GREEN + shade;
        case CACTUS: return Colors.DARK_GREEN + shade;
        default: return Colors.BROWN + shade;
        }
    }

    private static Block getHighestBlockAt(World world, int x, int z, Map<XZ, Block> cache) {
        XZ xz = new XZ(x, z);
        Block block = cache.get(xz);
        if (block != null) return block;
        block = world.getHighestBlockAt(x, z);
        LOOP:
        while (block.getY() >= 0 && !block.getType().isSolid() && !block.isLiquid()) {
            switch (block.getType()) {
            case SNOW:
            case SUGAR_CANE_BLOCK:
            case WATER_LILY:
                break LOOP;
            default: break;
            }
            block = block.getRelative(0, -1, 0);
        }
        cache.put(xz, block);
        return block;
    }
}
