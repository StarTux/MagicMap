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
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.material.Colorable;
import org.bukkit.scheduler.BukkitRunnable;

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

    enum RenderMode {
        SURFACE, CAVE, END, NETHER;
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
        if (!isHoldingMap(player)) return;
        Session session = plugin.getSession(player);
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
        if (needsRedraw && dist < 64) {
            World world = player.getWorld();
            RenderMode renderMode;
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
            switch (renderMode) {
            case CAVE:
                drawCaveMap(canvas, world, ax, az);
                break;
            case NETHER:
                drawNetherMap(canvas, world, ax, az);
                break;
            case SURFACE: case END:
                Map<XZ, Block> cache = new HashMap<>();
                for (int pz = 4; pz < 128; pz += 1) {
                    for (int px = 0; px < 128; px += 1) {
                        int x = ax + px;
                        int z = az + pz;
                        Block block = getHighestBlockAt(world, x, z, cache);
                        canvas.setPixel(px, pz, (byte)colorOf(block, px, pz, cache));
                    }
                }
                break;
            default:
                break;
            }
            for (int y = 0; y < 4; y += 1) {
                for (int x = 0; x < 128; x += 1) {
                    canvas.setPixel(x, y, (byte)(Colors.WOOL_BLACK + 3));
                }
            }
            for (int x = 0; x < 128; x += 1) {
                canvas.setPixel(x, 4, (byte)((canvas.getPixel(x, 4) & ~0x3) + 3));
            }
            final int shadowColor = Colors.DARK_GRAY + 3;
            String worldName = plugin.getWorldName(player.getWorld().getName());
            plugin.getFont4x4().print(worldName, 1, 0, (x, y, shadow) -> { if (y < 4) canvas.setPixel(x, y, !shadow ? (byte)(Colors.PALE_BLUE + 2) : (byte)shadowColor); });
            plugin.getFont4x4().print(renderMode.name(), 128 - plugin.getFont4x4().widthOf(renderMode.name()), 0, (x, y, shadow) -> { if (y < 4) canvas.setPixel(x, y, !shadow ? (byte)(Colors.RED + 2) : (byte)shadowColor); });
            if (claimRenderer != null) claimRenderer.render(plugin, canvas, player, ax, az);
            if (plugin.getCreativeRenderer() != null) plugin.getCreativeRenderer().render(canvas, player, ax, az);
            for (Marker marker: plugin.getMarkers()) {
                if (!marker.getWorld().equals(player.getWorld().getName())) continue;
                int x = marker.getX() - ax;
                int z = marker.getZ() - az - 2;
                if (x < 0 || x > 127) continue;
                if (z < 5 || z > 127) continue;
                x -= plugin.getFont4x4().widthOf(marker.getMessage()) / 2;
                plugin.getFont4x4().print(marker.getMessage(), x, z, (mx, my, shadow) -> canvas.setPixel(mx, my, shadow ? (byte)((canvas.getPixel(mx, my) & ~0x3) + 3) : (byte)Colors.WHITE + 2));
            }
            session.setLastRender(System.currentTimeMillis());
        }
        MapCursorCollection oldCursors = session.remove(MapCursorCollection.class);
        if (oldCursors != null) canvas.setCursors(oldCursors);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isValid() || !player.isOnline()) return;
                MapCursorCollection cursors = new MapCursorCollection();
                cursors.addCursor(Util.makeCursor(MapCursor.Type.WHITE_POINTER, playerLocation, ax, az));
                if (dist < 128) {
                    for (Entity e: player.getNearbyEntities(64, 64, 64)) {
                        if (e instanceof Player) {
                            Player nearby = (Player)e;
                            if (nearby.equals(player)) continue;
                            if (nearby.getGameMode() == GameMode.SPECTATOR) continue;
                            cursors.addCursor(Util.makeCursor(MapCursor.Type.BLUE_POINTER, nearby.getLocation(), ax, az));
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
                }
                session.store(cursors);
            }
        }.runTask(plugin);
    }

    void drawCaveMap(MapCanvas canvas, World world, int ax, int az) {
        Map<XZ, Block> cache = new HashMap<>();
        for (int pz = 4; pz < 128; pz += 1) {
            for (int px = 0; px < 128; px += 1) {
                int dx = px - 64;
                int dz = pz - 64;
                int dist = dx * dx + dz * dz;
                if (dist >= 60 * 60) {
                    canvas.setPixel(px, pz, (byte)Colors.WOOL_BLACK);
                } else if (dist >= 40 * 40 && (px % 2 == 0 ^ pz % 2 == 0)) {
                    canvas.setPixel(px, pz, (byte)Colors.WOOL_BLACK);
                } else {
                    int x = ax + px;
                    int z = az + pz;
                    Block block = getHighestBlockAt(world, x, z, cache);
                    while (block.getY() >= 0 && (block.getType() != Material.AIR || block.getLightFromSky() > 0)) {
                        block = block.getRelative(0, -1, 0);
                    }
                    while (block.getY() > 0 && !block.getType().isSolid() && !block.isLiquid()) block = block.getRelative(0, -1, 0);
                    if (block.getY() < 0) {
                        canvas.setPixel(px, pz, (byte)Colors.WOOL_BLACK);
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
                                color = Colors.WOOL_YELLOW + 3;
                            } else if (y < 48) {
                                color = Colors.WOOL_YELLOW;
                            } else if (y < 56) {
                                color = Colors.WOOL_YELLOW + 1;
                            } else {
                                color = Colors.WOOL_YELLOW + 2;
                            }
                        }
                        canvas.setPixel(px, pz, (byte)color);
                    }
                }
            }
        }
    }

    void drawNetherMap(MapCanvas canvas, World world, int ax, int az) {
        for (int pz = 4; pz < 128; pz += 1) {
            for (int px = 0; px < 128; px += 1) {
                int x = ax + px;
                int z = az + pz;
                Block block = world.getBlockAt(x, 127, z);
                while (block.getY() >= 0 && block.getType() != Material.AIR) {
                    block = block.getRelative(0, -1, 0);
                }
                while (block.getY() > 0 && !block.getType().isSolid() && !block.isLiquid()) block = block.getRelative(0, -1, 0);
                if (block.getY() < 0) {
                    canvas.setPixel(px, pz, (byte)Colors.WOOL_BLACK);
                } else {
                    int color;
                    int y = block.getY();
                    if (y < 8) {
                        color = Colors.MAROON + 3;
                    } else if (y < 16) {
                        color = Colors.MAROON;
                    } else if (y < 24) {
                        color = Colors.MAROON + 1;
                    } else if (y < 32) {
                        color = Colors.MAROON + 2;
                    } else if (y < 40) {
                        color = Colors.WOOL_RED + 3;
                    } else if (y < 48) {
                        color = Colors.WOOL_RED;
                    } else if (y < 56) {
                        color = Colors.WOOL_RED + 1;
                    } else if (y < 64) {
                        color = Colors.WOOL_RED + 2;
                    } else if (y < 72) {
                        color = Colors.WOOL_ORANGE + 3;
                    } else if (y < 80) {
                        color = Colors.WOOL_ORANGE;
                    } else if (y < 88) {
                        color = Colors.WOOL_ORANGE + 1;
                    } else if (y < 96) {
                        color = Colors.WOOL_ORANGE + 2;
                    } else if (y < 104) {
                        color = Colors.WOOL_YELLOW + 3;
                    } else if (y < 112) {
                        color = Colors.WOOL_YELLOW;
                    } else if (y < 120) {
                        color = Colors.WOOL_YELLOW + 1;
                    } else {
                        color = Colors.WOOL_YELLOW + 2;
                    }
                    canvas.setPixel(px, pz, (byte)color);
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
        if (block.getY() < 0) return Colors.WOOL_BLACK + 3;
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
                if ((x % 2 == 0) ^ (y % 2 == 0)) {
                    depthShade = 2;
                } else {
                    depthShade = 1;
                }
            } else if (depth <= 6) {
                depthShade = 1;
            } else if (depth <= 8) {
                if ((x % 2 == 0) ^ (y % 2 == 0)) {
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
            case 1: return Colors.WOOL_ORANGE + shade;
            default: return Colors.LIGHT_BROWN + shade;
            }
        case GRASS_PATH: return Colors.BROWN + shade;
        case SANDSTONE: case SANDSTONE_STAIRS: return Colors.LIGHT_BROWN + shade;
        case RED_SANDSTONE: case RED_SANDSTONE_STAIRS: return Colors.WOOL_ORANGE + shade;
        case DIRT: case SOIL: case LOG: case LOG_2: return Colors.DARK_BROWN + shade;
        case COBBLESTONE: case COBBLE_WALL: case MOSSY_COBBLESTONE: case SMOOTH_BRICK: case SMOOTH_STAIRS: return Colors.LIGHT_GRAY + shade;
        case GRAVEL:
            return (x % 2 == 0) ^ (y % 2 == 0) ? Colors.GRAY_1 + shade : Colors.LIGHT_GRAY + shade;
        case STONE:
            switch (block.getData() & 0x7) {
            case 0: return Colors.LIGHT_GRAY + shade; // Smoothstone
            case 1: case 2: return Colors.BROWN + shade; // Granite
            case 3: case 4: return Colors.WHITE + shade; // Diorite
            case 5: case 6: return Colors.LIGHT_GRAY + shade; // Andesite
            default: return Colors.LIGHT_GRAY + shade;
            }
        case COAL_ORE: case DIAMOND_ORE: case EMERALD_ORE: case GLOWING_REDSTONE_ORE: case GOLD_ORE: case IRON_ORE: case LAPIS_ORE: case REDSTONE_ORE: return Colors.LIGHT_GRAY + shade;
        case ICE: return Colors.ROYAL_BLUE + shade;
        case PACKED_ICE: return Colors.WOOL_LIGHT_BLUE + shade;
        case SNOW: case SNOW_BLOCK: return Colors.WHITE + shade;
        case PUMPKIN: return Colors.WOOL_ORANGE + shade;
        case CLAY: return Colors.GRAY_1 + shade;
        case HARD_CLAY: return Colors.WOOL_RED + shade;
        case BRICK: return Colors.WOOL_RED + shade;
        case QUARTZ_BLOCK: case QUARTZ_STAIRS: return Colors.WHITE + shade;
        case WOOD: case WOOD_STAIRS: case WOOD_STEP: case WOOD_DOUBLE_STEP: case TRAP_DOOR: return Colors.BROWN + shade;
        case STEP:
        case DOUBLE_STEP:
            switch (block.getData() & 0x7) {
            case 0: return Colors.LIGHT_GRAY + shade; // Stone
            case 1: return Colors.LIGHT_BROWN + shade; // Sandstone
            case 2: return Colors.LIGHT_BROWN + shade; // Wood
            case 3: return Colors.LIGHT_GRAY + shade; // Cobble
            case 4: return Colors.WOOL_RED + shade; // Brick
            case 5: return Colors.LIGHT_GRAY + shade; // Stone Brick
            case 6: return Colors.MAROON + shade; // Nether Brick
            case 7: return Colors.WHITE + shade; // Quartz
            default: return 0;
            }
        case HUGE_MUSHROOM_1: return Colors.BROWN + shade;
        case HUGE_MUSHROOM_2: return Colors.RED + shade;
        case LAPIS_BLOCK: return Colors.BLUE + shade;
        case EMERALD_BLOCK: return Colors.LIGHT_GREEN + shade;
        case REDSTONE_BLOCK: return Colors.RED + shade;
        case DIAMOND_BLOCK: return Colors.PALE_BLUE + shade;
        case GOLD_BLOCK: return Colors.WOOL_YELLOW + shade;
        case IRON_BLOCK: return Colors.WHITE + shade;
        case MYCEL: return Colors.WOOL_PURPLE + shade;
        case OBSIDIAN: case ENDER_CHEST: return Colors.WOOL_BLACK + shade;
        case WOOL:
        case STAINED_CLAY:
        case STAINED_GLASS:
        case CONCRETE:
        case CONCRETE_POWDER:
            switch (block.getData()) {
            case 0: return Colors.WOOL_WHITE + shade;
            case 1: return Colors.WOOL_ORANGE + shade;
            case 2: return Colors.WOOL_MAGENTA + shade;
            case 3: return Colors.WOOL_LIGHT_BLUE + shade;
            case 4: return Colors.WOOL_YELLOW + shade;
            case 5: return Colors.WOOL_LIME + shade;
            case 6: return Colors.WOOL_PINK + shade;
            case 7: return Colors.WOOL_GRAY + shade;
            case 8: return Colors.WOOL_SILVER + shade;
            case 9: return Colors.WOOL_CYAN + shade;
            case 10: return Colors.WOOL_PURPLE + shade;
            case 11: return Colors.WOOL_BLUE + shade;
            case 12: return Colors.WOOL_BROWN + shade;
            case 13: return Colors.WOOL_GREEN + shade;
            case 14: return Colors.WOOL_RED + shade;
            case 15: return Colors.WOOL_BLACK + shade;
            default: return 0;
            }
        // Terracotta
        case WHITE_GLAZED_TERRACOTTA: return Colors.WOOL_WHITE + shade;
        case ORANGE_GLAZED_TERRACOTTA: return Colors.WOOL_ORANGE + shade;
        case MAGENTA_GLAZED_TERRACOTTA: return Colors.WOOL_MAGENTA + shade;
        case LIGHT_BLUE_GLAZED_TERRACOTTA: return Colors.WOOL_LIGHT_BLUE + shade;
        case YELLOW_GLAZED_TERRACOTTA: return Colors.WOOL_YELLOW + shade;
        case LIME_GLAZED_TERRACOTTA: return Colors.WOOL_LIME + shade;
        case PINK_GLAZED_TERRACOTTA: return Colors.WOOL_PINK + shade;
        case GRAY_GLAZED_TERRACOTTA: return Colors.WOOL_GRAY + shade;
        case SILVER_GLAZED_TERRACOTTA: return Colors.WOOL_SILVER + shade;
        case CYAN_GLAZED_TERRACOTTA: return Colors.WOOL_CYAN + shade;
        case PURPLE_GLAZED_TERRACOTTA: return Colors.WOOL_PURPLE + shade;
        case BLUE_GLAZED_TERRACOTTA: return Colors.WOOL_BLUE + shade;
        case BROWN_GLAZED_TERRACOTTA: return Colors.WOOL_BROWN + shade;
        case GREEN_GLAZED_TERRACOTTA: return Colors.WOOL_GREEN + shade;
        case RED_GLAZED_TERRACOTTA: return Colors.WOOL_RED + shade;
        case BLACK_GLAZED_TERRACOTTA: return Colors.WOOL_BLACK + shade;
        // Shulker
        case WHITE_SHULKER_BOX: return Colors.WOOL_WHITE + shade;
        case ORANGE_SHULKER_BOX: return Colors.WOOL_ORANGE + shade;
        case MAGENTA_SHULKER_BOX: return Colors.WOOL_MAGENTA + shade;
        case LIGHT_BLUE_SHULKER_BOX: return Colors.WOOL_LIGHT_BLUE + shade;
        case YELLOW_SHULKER_BOX: return Colors.WOOL_YELLOW + shade;
        case LIME_SHULKER_BOX: return Colors.WOOL_LIME + shade;
        case PINK_SHULKER_BOX: return Colors.WOOL_PINK + shade;
        case GRAY_SHULKER_BOX: return Colors.WOOL_GRAY + shade;
        case SILVER_SHULKER_BOX: return Colors.WOOL_SILVER + shade;
        case CYAN_SHULKER_BOX: return Colors.WOOL_CYAN + shade;
        case PURPLE_SHULKER_BOX: return Colors.WOOL_PURPLE + shade;
        case BLUE_SHULKER_BOX: return Colors.WOOL_BLUE + shade;
        case BROWN_SHULKER_BOX: return Colors.WOOL_BROWN + shade;
        case GREEN_SHULKER_BOX: return Colors.WOOL_GREEN + shade;
        case RED_SHULKER_BOX: return Colors.WOOL_RED + shade;
        case BLACK_SHULKER_BOX: return Colors.WOOL_BLACK + shade;
        //
        case SUGAR_CANE_BLOCK: return Colors.LIGHT_GREEN + shade;
        case WATER_LILY: return Colors.DARK_GREEN + shade;
        case CACTUS: return Colors.DARK_GREEN + shade;
        case NETHERRACK: case QUARTZ_ORE: case SOUL_SAND: case NETHER_STALK: case NETHER_WART_BLOCK: return Colors.MAROON + shade;
        case CROPS: return Colors.LIGHT_BROWN + shade;
        case POTATO: return Colors.LIGHT_BROWN + shade;
        case CARROT: return Colors.WOOL_ORANGE + shade;
        case BEETROOT_BLOCK: return Colors.WOOL_PINK + shade;
        case ACACIA_DOOR: case ACACIA_FENCE: case ACACIA_FENCE_GATE: case ACACIA_STAIRS: return Colors.WOOL_ORANGE + shade;
        case BIRCH_DOOR: case BIRCH_FENCE: case BIRCH_WOOD_STAIRS: return Colors.LIGHT_BROWN + shade;
        case DARK_OAK_DOOR: case DARK_OAK_FENCE: case DARK_OAK_STAIRS: return Colors.DARK_BROWN + shade;
        case SPRUCE_DOOR: case SPRUCE_FENCE: case SPRUCE_WOOD_STAIRS: return Colors.DARK_BROWN + shade;
        case JUNGLE_DOOR: case JUNGLE_FENCE: case JUNGLE_WOOD_STAIRS: return Colors.BROWN + shade;
        case NETHER_FENCE: return Colors.MAROON + shade;
        case FENCE: case FENCE_GATE: return Colors.BROWN + shade;
        case IRON_DOOR: return Colors.WHITE + shade;
        case IRON_FENCE: return Colors.LIGHT_GRAY + shade;
        case VINE: return Colors.DARK_GREEN + shade;
        case WEB: return Colors.WHITE + shade;
        case MELON_BLOCK: return Colors.DARK_GREEN + shade;
        case ENDER_STONE: case END_BRICKS: return Colors.LIGHT_BROWN + shade;
        case END_ROD: case BEACON: case END_CRYSTAL: return Colors.WHITE + shade;
        case PURPUR_BLOCK: case PURPUR_DOUBLE_SLAB: case PURPUR_PILLAR: case PURPUR_SLAB: case PURPUR_STAIRS: return Colors.WOOL_MAGENTA + shade;
        case PRISMARINE: return Colors.CYAN + shade;
        case CHORUS_FLOWER: case CHORUS_FRUIT: case CHORUS_PLANT: return Colors.WOOL_PURPLE + shade;
        case BEDROCK: return Colors.DARK_GRAY + shade;
        case TORCH: case GLOWSTONE: case REDSTONE_LAMP_ON: case JACK_O_LANTERN: case FIRE: return Colors.WOOL_YELLOW + 2;
        case SEA_LANTERN: return Colors.ROYAL_BLUE + 2;
        case BED_BLOCK:
        default:
            BlockState blockState = block.getState();
            if (blockState instanceof Colorable) {
                Colorable colorable = (Colorable)blockState;
                switch (colorable.getColor()) {
                case WHITE: return Colors.WOOL_WHITE + shade;
                case ORANGE: return Colors.WOOL_ORANGE + shade;
                case MAGENTA: return Colors.WOOL_MAGENTA + shade;
                case LIGHT_BLUE: return Colors.WOOL_LIGHT_BLUE + shade;
                case YELLOW: return Colors.WOOL_YELLOW + shade;
                case LIME: return Colors.WOOL_LIME + shade;
                case PINK: return Colors.WOOL_PINK + shade;
                case GRAY: return Colors.WOOL_GRAY + shade;
                case SILVER: return Colors.WOOL_SILVER + shade;
                case CYAN: return Colors.WOOL_CYAN + shade;
                case PURPLE: return Colors.WOOL_PURPLE + shade;
                case BLUE: return Colors.WOOL_BLUE + shade;
                case BROWN: return Colors.WOOL_BROWN + shade;
                case GREEN: return Colors.WOOL_GREEN + shade;
                case RED: return Colors.WOOL_RED + shade;
                case BLACK: return Colors.WOOL_BLACK + shade;
                default: break;
                }
            }
            return Colors.WOOL_BROWN + shade;
        }
    }

    private static Block getHighestBlockAt(World world, int x, int z, Map<XZ, Block> cache) {
        XZ xz = new XZ(x, z);
        Block block = cache.get(xz);
        if (block != null) return block;
        block = world.getHighestBlockAt(x, z);
        LOOP:
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
                break LOOP;
            default: break;
            }
            block = block.getRelative(0, -1, 0);
        }
        cache.put(xz, block);
        return block;
    }
}
