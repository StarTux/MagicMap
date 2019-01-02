package com.cavetale.magicmap;

import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.plugin.IllegalPluginAccessException;

@RequiredArgsConstructor
final class AsyncMapRenderer implements Runnable {
    public static final int NORMAL = 0;
    public static final int LIGHT = 1;
    public static final int BRIGHT = 2;
    public static final int DARK = 3;
    private static final int[] RAW_COLORS;
    private final MagicMapPlugin plugin;
    private final Session session;
    private final Type type;
    private final String worldName;
    private final int centerX, centerZ; // center coords
    private final long dayTime;
    //
    private final MapCache mapCache = new MapCache();
    final HashMap<Long, ChunkSnapshot> chunks = new HashMap<>();
    // Side effect variables
    private ChunkSnapshot chunkSnapshot;
    private int innerX, innerZ;

    enum Type {
        SURFACE, CAVE, NETHER;
    }

    static {
        RAW_COLORS = new int[Material.values().length];
        for (Material mat: Material.values()) {
            RAW_COLORS[mat.ordinal()] = rippedColorOf(mat) << 2;
        }
    }

    @Override
    public void run() {
        // Top left world coords
        int ax = this.centerX - 63;
        int az = this.centerZ - 63;
        // Bottom right world coords
        int bx = this.centerX + 64;
        int bz = this.centerZ + 64;
        for (int canvasY = 0; canvasY < 128; canvasY += 1) {
            for (int canvasX = 0; canvasX < 128; canvasX += 1) {
                // x/z are world coords of the block to render
                int x = canvasX + ax;
                int z = canvasY + az;
                int highest = highestBlockAt(x, z);
                if (highest < 0) {
                    this.mapCache.setPixel(canvasX, canvasY, (29 << 2) + 3);
                    continue;
                }
                Material mat = this.chunkSnapshot.getBlockType(this.innerX, highest, this.innerZ);
                int color = RAW_COLORS[mat.ordinal()];
                if (color == 48) { // water
                    int lbottom = highest - 1;
                    while (lbottom > 0 && color == RAW_COLORS[this.chunkSnapshot.getBlockType(this.innerX, lbottom, this.innerZ).ordinal()]) {
                        lbottom -= 1;
                    }
                    int depth = highest - lbottom;
                    if (depth <= 2) {
                        color += BRIGHT;
                    } else if (depth <= 4) {
                        color += (canvasX & 1) == (canvasY & 1) ? BRIGHT : LIGHT;
                    } else if (depth <= 6) {
                        color += LIGHT;
                    } else if (depth <= 8) {
                        color += (canvasX & 1) == (canvasY & 1) ? NORMAL : LIGHT;
                    } else if (depth <= 12) {
                        color += NORMAL;
                    } else if (depth <= 16) {
                        color += (canvasX & 1) == (canvasY & 1) ? NORMAL : DARK;
                    } else {
                        color += DARK;
                    }
                    this.mapCache.setPixel(canvasX, canvasY, color);
                } else {
                    // Neighbor block where the sunlight comes from.
                    int lx, ly;
                    if (this.dayTime < 1500) {
                        lx = 1; ly = 0;
                    } else if (this.dayTime < 4500) {
                        lx = 1; ly = -1;
                    } else if (this.dayTime < 7500) {
                        lx = 0; ly = -1;
                    } else if (this.dayTime < 10500) {
                        lx = -1; ly = -1;
                    } else if (this.dayTime < 13500) {
                        lx = -1; ly = 0;
                    } else if (this.dayTime < 16500) {
                        lx = -1; ly = 1;
                    } else if (this.dayTime < 19500) {
                        lx = 0; ly = 1;
                    } else if (this.dayTime < 22500) {
                        lx = 1; ly = 1;
                    } else {
                        lx = 1; ly = 0;
                    }
                    int nx = x + lx;
                    int nz = z + ly;
                    // 1 == Bright
                    // 2 == Super Bright
                    // 3 == Dark
                    int highestN = highestBlockAt(nx, nz);
                    if (highestN >= 0) {
                        if (highest > highestN) {
                            color += BRIGHT;
                        } else if (highest < highestN) {
                            color += NORMAL;
                        } else {
                            color += LIGHT;
                        }
                    }
                    this.mapCache.setPixel(canvasX, canvasY, color);
                }
            }
        }
        if (this.chunkSnapshot != null) {
            if (this.worldName != null) this.plugin.getTinyFont().print(this.mapCache, this.worldName, 1, 1, 32 + BRIGHT, 116);
        }
        try {
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                    this.session.pasteMap = this.mapCache;
                    this.session.rendering = false;
                    this.session.centerX = this.centerX;
                    this.session.centerZ = this.centerZ;
                });
        } catch (IllegalPluginAccessException ipae) { }
    }

    /**
     * Side effects: Sets the member following variables:
     * - chunkSnapshot
     * - innerX, innerZ
     *
     * @return the y-coordinate of the highest block if it exists and
     * all member variables were set. -1 otherwise.
     */
    private int highestBlockAt(int x, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        long chunkIndex = ((long)chunkZ << 32) + (long)chunkX;
        ChunkSnapshot snap = this.chunks.get(chunkIndex);
        if (snap == null) return -1;
        // Inner coords
        int ix = x % 16;
        if (ix < 0) ix += 16;
        int iz = z % 16;
        if (iz < 0) iz += 16;
        this.chunkSnapshot = snap;
        this.innerX = ix;
        this.innerZ = iz;
        return highest(this.chunkSnapshot, ix, iz);
    }

    private int highest(ChunkSnapshot snap, int x, int z) {
        switch (this.type) {
        case NETHER: {
            int y = 127;
            while (y >= 0 && !snap.getBlockType(x, y, z).isEmpty()) y -= 1; // skip blocks
            while (y >= 0 && snap.getBlockType(x, y, z).isEmpty()) y -= 1; // skip air
            while (y >= 0 && RAW_COLORS[snap.getBlockType(x, y, z).ordinal()] == 0) y -= 1; // skip transparent
            return y;
        }
        case CAVE: {
            int y = 255;
            while (y >= 0 && snap.getBlockType(x, y, z).isEmpty()) y -= 1; // skip air
            while (y >= 0 && !snap.getBlockType(x, y, z).isEmpty()) y -= 1; // skip blocks
            while (y >= 0 && snap.getBlockType(x, y, z).isEmpty()) y -= 1; // skip air
            while (y >= 0 && RAW_COLORS[snap.getBlockType(x, y, z).ordinal()] == 0) y -= 1; // skip transparent
            return y;
        }
        case SURFACE:
        default: {
            int y = 255;
            while (y >= 0 && snap.getBlockType(x, y, z).isEmpty()) y -= 1; // skip air
            while (y >= 0 && RAW_COLORS[snap.getBlockType(x, y, z).ordinal()] == 0) y -= 1; // skip transparent
            return y;
        }
        }
    }

    private static int rippedColorOf(Material mat) {
        switch (mat) {
        case AIR: return 0;
        case STONE: return 11;
        case GRANITE: return 10;
        case POLISHED_GRANITE: return 10;
        case DIORITE: return 14;
        case POLISHED_DIORITE: return 14;
        case ANDESITE: return 11;
        case POLISHED_ANDESITE: return 11;
        case GRASS_BLOCK: return 1;
        case DIRT: return 10;
        case COARSE_DIRT: return 10;
        case PODZOL: return 34;
        case COBBLESTONE: return 11;
        case OAK_PLANKS: return 13;
        case SPRUCE_PLANKS: return 34;
        case BIRCH_PLANKS: return 2;
        case JUNGLE_PLANKS: return 10;
        case ACACIA_PLANKS: return 15;
        case DARK_OAK_PLANKS: return 26;
        case OAK_SAPLING: return 7;
        case SPRUCE_SAPLING: return 7;
        case BIRCH_SAPLING: return 7;
        case JUNGLE_SAPLING: return 7;
        case ACACIA_SAPLING: return 7;
        case DARK_OAK_SAPLING: return 7;
        case BEDROCK: return 11;
        case WATER: return 12;
        case BUBBLE_COLUMN: return 12;
        case LAVA: return 4;
        case SAND: return 2;
        case RED_SAND: return 15;
        case GRAVEL: return 11;
        case GOLD_ORE: return 11;
        case IRON_ORE: return 11;
        case COAL_ORE: return 11;
        case OAK_LOG: return 34;
        case SPRUCE_LOG: return 26;
        case BIRCH_LOG: return 14;
        case JUNGLE_LOG: return 34;
        case ACACIA_LOG: return 11;
        case DARK_OAK_LOG: return 26;
        case STRIPPED_OAK_LOG: return 13;
        case STRIPPED_SPRUCE_LOG: return 34;
        case STRIPPED_BIRCH_LOG: return 2;
        case STRIPPED_JUNGLE_LOG: return 10;
        case STRIPPED_ACACIA_LOG: return 15;
        case STRIPPED_DARK_OAK_LOG: return 26;
        case OAK_WOOD: return 13;
        case SPRUCE_WOOD: return 34;
        case BIRCH_WOOD: return 2;
        case JUNGLE_WOOD: return 10;
        case ACACIA_WOOD: return 15;
        case DARK_OAK_WOOD: return 26;
        case STRIPPED_OAK_WOOD: return 13;
        case STRIPPED_SPRUCE_WOOD: return 34;
        case STRIPPED_BIRCH_WOOD: return 2;
        case STRIPPED_JUNGLE_WOOD: return 10;
        case STRIPPED_ACACIA_WOOD: return 15;
        case STRIPPED_DARK_OAK_WOOD: return 26;
        case OAK_LEAVES: return 7;
        case SPRUCE_LEAVES: return 7;
        case BIRCH_LEAVES: return 7;
        case JUNGLE_LEAVES: return 7;
        case ACACIA_LEAVES: return 7;
        case DARK_OAK_LEAVES: return 7;
        case SPONGE: return 18;
        case WET_SPONGE: return 18;
        case GLASS: return 0;
        case LAPIS_ORE: return 11;
        case LAPIS_BLOCK: return 32;
        case DISPENSER: return 11;
        case SANDSTONE: return 2;
        case CHISELED_SANDSTONE: return 2;
        case CUT_SANDSTONE: return 2;
        case NOTE_BLOCK: return 13;
        case WHITE_BED: return 3;
        case ORANGE_BED: return 3;
        case MAGENTA_BED: return 3;
        case LIGHT_BLUE_BED: return 3;
        case YELLOW_BED: return 3;
        case LIME_BED: return 3;
        case PINK_BED: return 3;
        case GRAY_BED: return 3;
        case LIGHT_GRAY_BED: return 3;
        case CYAN_BED: return 3;
        case PURPLE_BED: return 3;
        case BLUE_BED: return 3;
        case BROWN_BED: return 3;
        case GREEN_BED: return 3;
        case RED_BED: return 3;
        case BLACK_BED: return 3;
        case POWERED_RAIL: return 0;
        case DETECTOR_RAIL: return 0;
        case STICKY_PISTON: return 11;
        case COBWEB: return 3;
        case GRASS: return 7;
        case FERN: return 7;
        case DEAD_BUSH: return 13;
        case SEAGRASS: return 12;
        case TALL_SEAGRASS: return 12;
        case PISTON: return 11;
        case PISTON_HEAD: return 11;
        case WHITE_WOOL: return 8;
        case ORANGE_WOOL: return 15;
        case MAGENTA_WOOL: return 16;
        case LIGHT_BLUE_WOOL: return 17;
        case YELLOW_WOOL: return 18;
        case LIME_WOOL: return 19;
        case PINK_WOOL: return 20;
        case GRAY_WOOL: return 21;
        case LIGHT_GRAY_WOOL: return 22;
        case CYAN_WOOL: return 23;
        case PURPLE_WOOL: return 24;
        case BLUE_WOOL: return 25;
        case BROWN_WOOL: return 26;
        case GREEN_WOOL: return 27;
        case RED_WOOL: return 28;
        case BLACK_WOOL: return 29;
        case MOVING_PISTON: return 11;
        case DANDELION: return 7;
        case POPPY: return 7;
        case BLUE_ORCHID: return 7;
        case ALLIUM: return 7;
        case AZURE_BLUET: return 7;
        case RED_TULIP: return 7;
        case ORANGE_TULIP: return 7;
        case WHITE_TULIP: return 7;
        case PINK_TULIP: return 7;
        case OXEYE_DAISY: return 7;
        case BROWN_MUSHROOM: return 7;
        case RED_MUSHROOM: return 7;
        case GOLD_BLOCK: return 30;
        case IRON_BLOCK: return 6;
        case OAK_SLAB: return 13;
        case SPRUCE_SLAB: return 34;
        case BIRCH_SLAB: return 2;
        case JUNGLE_SLAB: return 10;
        case ACACIA_SLAB: return 15;
        case DARK_OAK_SLAB: return 26;
        case STONE_SLAB: return 11;
        case SANDSTONE_SLAB: return 2;
        case PETRIFIED_OAK_SLAB: return 13;
        case COBBLESTONE_SLAB: return 11;
        case BRICK_SLAB: return 28;
        case STONE_BRICK_SLAB: return 11;
        case NETHER_BRICK_SLAB: return 35;
        case QUARTZ_SLAB: return 14;
        case RED_SANDSTONE_SLAB: return 15;
        case PURPUR_SLAB: return 16;
        case PRISMARINE_SLAB: return 23;
        case PRISMARINE_BRICK_SLAB: return 31;
        case DARK_PRISMARINE_SLAB: return 31;
        case SMOOTH_STONE: return 11;
        case SMOOTH_SANDSTONE: return 2;
        case SMOOTH_QUARTZ: return 14;
        case SMOOTH_RED_SANDSTONE: return 15;
        case BRICKS: return 28;
        case TNT: return 4;
        case BOOKSHELF: return 13;
        case MOSSY_COBBLESTONE: return 11;
        case OBSIDIAN: return 29;
        case TORCH: return 0;
        case WALL_TORCH: return 0;
        case FIRE: return 4;
        case SPAWNER: return 11;
        case OAK_STAIRS: return 13;
        case CHEST: return 13;
        case REDSTONE_WIRE: return 0;
        case DIAMOND_ORE: return 11;
        case DIAMOND_BLOCK: return 31;
        case CRAFTING_TABLE: return 13;
        case WHEAT: return 7;
        case FARMLAND: return 10;
        case FURNACE: return 11;
        case SIGN: return 13;
        case OAK_DOOR: return 13;
        case SPRUCE_DOOR: return 34;
        case BIRCH_DOOR: return 2;
        case JUNGLE_DOOR: return 10;
        case ACACIA_DOOR: return 15;
        case DARK_OAK_DOOR: return 26;
        case LADDER: return 0;
        case RAIL: return 0;
        case COBBLESTONE_STAIRS: return 11;
        case WALL_SIGN: return 13;
        case LEVER: return 0;
        case STONE_PRESSURE_PLATE: return 11;
        case IRON_DOOR: return 6;
        case OAK_PRESSURE_PLATE: return 13;
        case SPRUCE_PRESSURE_PLATE: return 34;
        case BIRCH_PRESSURE_PLATE: return 2;
        case JUNGLE_PRESSURE_PLATE: return 10;
        case ACACIA_PRESSURE_PLATE: return 15;
        case DARK_OAK_PRESSURE_PLATE: return 26;
        case REDSTONE_ORE: return 11;
        case REDSTONE_TORCH: return 0;
        case REDSTONE_WALL_TORCH: return 0;
        case STONE_BUTTON: return 0;
        case SNOW: return 8;
        case ICE: return 5;
        case SNOW_BLOCK: return 8;
        case CACTUS: return 7;
        case CLAY: return 9;
        case SUGAR_CANE: return 7;
        case JUKEBOX: return 10;
        case OAK_FENCE: return 13;
        case SPRUCE_FENCE: return 34;
        case BIRCH_FENCE: return 2;
        case JUNGLE_FENCE: return 10;
        case DARK_OAK_FENCE: return 26;
        case ACACIA_FENCE: return 15;
        case PUMPKIN: return 15;
        case NETHERRACK: return 35;
        case SOUL_SAND: return 26;
        case GLOWSTONE: return 2;
        case NETHER_PORTAL: return 0;
        case CARVED_PUMPKIN: return 15;
        case JACK_O_LANTERN: return 15;
        case CAKE: return 0;
        case REPEATER: return 0;
        case OAK_TRAPDOOR: return 13;
        case SPRUCE_TRAPDOOR: return 34;
        case BIRCH_TRAPDOOR: return 2;
        case JUNGLE_TRAPDOOR: return 10;
        case ACACIA_TRAPDOOR: return 15;
        case DARK_OAK_TRAPDOOR: return 26;
        case INFESTED_STONE: return 9;
        case INFESTED_COBBLESTONE: return 9;
        case INFESTED_STONE_BRICKS: return 9;
        case INFESTED_MOSSY_STONE_BRICKS: return 9;
        case INFESTED_CRACKED_STONE_BRICKS: return 9;
        case INFESTED_CHISELED_STONE_BRICKS: return 9;
        case STONE_BRICKS: return 11;
        case MOSSY_STONE_BRICKS: return 11;
        case CRACKED_STONE_BRICKS: return 11;
        case CHISELED_STONE_BRICKS: return 11;
        case BROWN_MUSHROOM_BLOCK: return 10;
        case RED_MUSHROOM_BLOCK: return 28;
        case MUSHROOM_STEM: return 36;
        case IRON_BARS: return 0;
        case GLASS_PANE: return 0;
        case MELON: return 19;
        case ATTACHED_PUMPKIN_STEM: return 7;
        case ATTACHED_MELON_STEM: return 7;
        case PUMPKIN_STEM: return 7;
        case MELON_STEM: return 7;
        case VINE: return 7;
        case OAK_FENCE_GATE: return 13;
        case SPRUCE_FENCE_GATE: return 34;
        case BIRCH_FENCE_GATE: return 2;
        case JUNGLE_FENCE_GATE: return 10;
        case DARK_OAK_FENCE_GATE: return 26;
        case ACACIA_FENCE_GATE: return 15;
        case BRICK_STAIRS: return 28;
        case STONE_BRICK_STAIRS: return 11;
        case MYCELIUM: return 24;
        case LILY_PAD: return 7;
        case NETHER_BRICKS: return 35;
        case NETHER_BRICK_FENCE: return 35;
        case NETHER_BRICK_STAIRS: return 35;
        case NETHER_WART: return 28;
        case ENCHANTING_TABLE: return 28;
        case BREWING_STAND: return 6;
        case CAULDRON: return 11;
        case END_PORTAL: return 29;
        case END_PORTAL_FRAME: return 27;
        case END_STONE: return 2;
        case DRAGON_EGG: return 29;
        case REDSTONE_LAMP: return 0;
        case COCOA: return 7;
        case SANDSTONE_STAIRS: return 2;
        case EMERALD_ORE: return 11;
        case ENDER_CHEST: return 11;
        case TRIPWIRE_HOOK: return 0;
        case TRIPWIRE: return 0;
        case EMERALD_BLOCK: return 33;
        case SPRUCE_STAIRS: return 34;
        case BIRCH_STAIRS: return 2;
        case JUNGLE_STAIRS: return 10;
        case COMMAND_BLOCK: return 26;
        case BEACON: return 31;
        case COBBLESTONE_WALL: return 11;
        case MOSSY_COBBLESTONE_WALL: return 11;
        case FLOWER_POT: return 0;
        case POTTED_POPPY: return 0;
        case POTTED_BLUE_ORCHID: return 0;
        case POTTED_ALLIUM: return 0;
        case POTTED_AZURE_BLUET: return 0;
        case POTTED_RED_TULIP: return 0;
        case POTTED_ORANGE_TULIP: return 0;
        case POTTED_WHITE_TULIP: return 0;
        case POTTED_PINK_TULIP: return 0;
        case POTTED_OXEYE_DAISY: return 0;
        case POTTED_DANDELION: return 0;
        case POTTED_OAK_SAPLING: return 0;
        case POTTED_SPRUCE_SAPLING: return 0;
        case POTTED_BIRCH_SAPLING: return 0;
        case POTTED_JUNGLE_SAPLING: return 0;
        case POTTED_ACACIA_SAPLING: return 0;
        case POTTED_DARK_OAK_SAPLING: return 0;
        case POTTED_RED_MUSHROOM: return 0;
        case POTTED_BROWN_MUSHROOM: return 0;
        case POTTED_DEAD_BUSH: return 0;
        case POTTED_FERN: return 0;
        case POTTED_CACTUS: return 0;
        case CARROTS: return 7;
        case POTATOES: return 7;
        case OAK_BUTTON: return 0;
        case SPRUCE_BUTTON: return 0;
        case BIRCH_BUTTON: return 0;
        case JUNGLE_BUTTON: return 0;
        case ACACIA_BUTTON: return 0;
        case DARK_OAK_BUTTON: return 0;
        case SKELETON_WALL_SKULL: return 0;
        case SKELETON_SKULL: return 0;
        case WITHER_SKELETON_WALL_SKULL: return 0;
        case WITHER_SKELETON_SKULL: return 0;
        case ZOMBIE_WALL_HEAD: return 0;
        case ZOMBIE_HEAD: return 0;
        case PLAYER_WALL_HEAD: return 0;
        case PLAYER_HEAD: return 0;
        case CREEPER_WALL_HEAD: return 0;
        case CREEPER_HEAD: return 0;
        case DRAGON_WALL_HEAD: return 0;
        case DRAGON_HEAD: return 0;
        case ANVIL: return 6;
        case CHIPPED_ANVIL: return 6;
        case DAMAGED_ANVIL: return 6;
        case TRAPPED_CHEST: return 13;
        case LIGHT_WEIGHTED_PRESSURE_PLATE: return 30;
        case HEAVY_WEIGHTED_PRESSURE_PLATE: return 6;
        case COMPARATOR: return 0;
        case DAYLIGHT_DETECTOR: return 13;
        case REDSTONE_BLOCK: return 4;
        case NETHER_QUARTZ_ORE: return 35;
        case HOPPER: return 11;
        case QUARTZ_BLOCK: return 14;
        case QUARTZ_PILLAR: return 14;
        case CHISELED_QUARTZ_BLOCK: return 14;
        case QUARTZ_STAIRS: return 14;
        case ACTIVATOR_RAIL: return 0;
        case DROPPER: return 11;
        case WHITE_TERRACOTTA: return 36;
        case ORANGE_TERRACOTTA: return 37;
        case MAGENTA_TERRACOTTA: return 38;
        case LIGHT_BLUE_TERRACOTTA: return 39;
        case YELLOW_TERRACOTTA: return 40;
        case LIME_TERRACOTTA: return 41;
        case PINK_TERRACOTTA: return 42;
        case GRAY_TERRACOTTA: return 43;
        case LIGHT_GRAY_TERRACOTTA: return 44;
        case CYAN_TERRACOTTA: return 45;
        case PURPLE_TERRACOTTA: return 46;
        case BLUE_TERRACOTTA: return 47;
        case BROWN_TERRACOTTA: return 48;
        case GREEN_TERRACOTTA: return 49;
        case RED_TERRACOTTA: return 50;
        case BLACK_TERRACOTTA: return 51;
        case BARRIER: return 0;
        case IRON_TRAPDOOR: return 6;
        case HAY_BLOCK: return 18;
        case WHITE_CARPET: return 8;
        case ORANGE_CARPET: return 15;
        case MAGENTA_CARPET: return 16;
        case LIGHT_BLUE_CARPET: return 17;
        case YELLOW_CARPET: return 18;
        case LIME_CARPET: return 19;
        case PINK_CARPET: return 20;
        case GRAY_CARPET: return 21;
        case LIGHT_GRAY_CARPET: return 22;
        case CYAN_CARPET: return 23;
        case PURPLE_CARPET: return 24;
        case BLUE_CARPET: return 25;
        case BROWN_CARPET: return 26;
        case GREEN_CARPET: return 27;
        case RED_CARPET: return 28;
        case BLACK_CARPET: return 29;
        case TERRACOTTA: return 15;
        case COAL_BLOCK: return 29;
        case PACKED_ICE: return 5;
        case BLUE_ICE: return 5;
        case ACACIA_STAIRS: return 15;
        case DARK_OAK_STAIRS: return 26;
        case SLIME_BLOCK: return 1;
        case SUNFLOWER: return 7;
        case LILAC: return 7;
        case ROSE_BUSH: return 7;
        case PEONY: return 7;
        case TALL_GRASS: return 7;
        case LARGE_FERN: return 7;
        case WHITE_STAINED_GLASS: return 8;
        case ORANGE_STAINED_GLASS: return 15;
        case MAGENTA_STAINED_GLASS: return 16;
        case LIGHT_BLUE_STAINED_GLASS: return 17;
        case YELLOW_STAINED_GLASS: return 18;
        case LIME_STAINED_GLASS: return 19;
        case PINK_STAINED_GLASS: return 20;
        case GRAY_STAINED_GLASS: return 21;
        case LIGHT_GRAY_STAINED_GLASS: return 22;
        case CYAN_STAINED_GLASS: return 23;
        case PURPLE_STAINED_GLASS: return 24;
        case BLUE_STAINED_GLASS: return 25;
        case BROWN_STAINED_GLASS: return 26;
        case GREEN_STAINED_GLASS: return 27;
        case RED_STAINED_GLASS: return 28;
        case BLACK_STAINED_GLASS: return 29;
        case WHITE_STAINED_GLASS_PANE: return 0;
        case ORANGE_STAINED_GLASS_PANE: return 0;
        case MAGENTA_STAINED_GLASS_PANE: return 0;
        case LIGHT_BLUE_STAINED_GLASS_PANE: return 0;
        case YELLOW_STAINED_GLASS_PANE: return 0;
        case LIME_STAINED_GLASS_PANE: return 0;
        case PINK_STAINED_GLASS_PANE: return 0;
        case GRAY_STAINED_GLASS_PANE: return 0;
        case LIGHT_GRAY_STAINED_GLASS_PANE: return 0;
        case CYAN_STAINED_GLASS_PANE: return 0;
        case PURPLE_STAINED_GLASS_PANE: return 0;
        case BLUE_STAINED_GLASS_PANE: return 0;
        case BROWN_STAINED_GLASS_PANE: return 0;
        case GREEN_STAINED_GLASS_PANE: return 0;
        case RED_STAINED_GLASS_PANE: return 0;
        case BLACK_STAINED_GLASS_PANE: return 0;
        case PRISMARINE: return 23;
        case PRISMARINE_BRICKS: return 31;
        case DARK_PRISMARINE: return 31;
        case PRISMARINE_STAIRS: return 23;
        case PRISMARINE_BRICK_STAIRS: return 31;
        case DARK_PRISMARINE_STAIRS: return 31;
        case SEA_LANTERN: return 14;
        case WHITE_BANNER: return 13;
        case ORANGE_BANNER: return 13;
        case MAGENTA_BANNER: return 13;
        case LIGHT_BLUE_BANNER: return 13;
        case YELLOW_BANNER: return 13;
        case LIME_BANNER: return 13;
        case PINK_BANNER: return 13;
        case GRAY_BANNER: return 13;
        case LIGHT_GRAY_BANNER: return 13;
        case CYAN_BANNER: return 13;
        case PURPLE_BANNER: return 13;
        case BLUE_BANNER: return 13;
        case BROWN_BANNER: return 13;
        case GREEN_BANNER: return 13;
        case RED_BANNER: return 13;
        case BLACK_BANNER: return 13;
        case WHITE_WALL_BANNER: return 13;
        case ORANGE_WALL_BANNER: return 13;
        case MAGENTA_WALL_BANNER: return 13;
        case LIGHT_BLUE_WALL_BANNER: return 13;
        case YELLOW_WALL_BANNER: return 13;
        case LIME_WALL_BANNER: return 13;
        case PINK_WALL_BANNER: return 13;
        case GRAY_WALL_BANNER: return 13;
        case LIGHT_GRAY_WALL_BANNER: return 13;
        case CYAN_WALL_BANNER: return 13;
        case PURPLE_WALL_BANNER: return 13;
        case BLUE_WALL_BANNER: return 13;
        case BROWN_WALL_BANNER: return 13;
        case GREEN_WALL_BANNER: return 13;
        case RED_WALL_BANNER: return 13;
        case BLACK_WALL_BANNER: return 13;
        case RED_SANDSTONE: return 15;
        case CHISELED_RED_SANDSTONE: return 15;
        case CUT_RED_SANDSTONE: return 15;
        case RED_SANDSTONE_STAIRS: return 15;
        case END_ROD: return 0;
        case CHORUS_PLANT: return 24;
        case CHORUS_FLOWER: return 24;
        case PURPUR_BLOCK: return 16;
        case PURPUR_PILLAR: return 16;
        case PURPUR_STAIRS: return 16;
        case END_STONE_BRICKS: return 2;
        case BEETROOTS: return 7;
        case GRASS_PATH: return 10;
        case END_GATEWAY: return 29;
        case REPEATING_COMMAND_BLOCK: return 24;
        case CHAIN_COMMAND_BLOCK: return 27;
        case FROSTED_ICE: return 5;
        case MAGMA_BLOCK: return 35;
        case NETHER_WART_BLOCK: return 28;
        case RED_NETHER_BRICKS: return 35;
        case BONE_BLOCK: return 2;
        case STRUCTURE_VOID: return 0;
        case OBSERVER: return 11;
        case SHULKER_BOX: return 24;
        case WHITE_SHULKER_BOX: return 8;
        case ORANGE_SHULKER_BOX: return 15;
        case MAGENTA_SHULKER_BOX: return 16;
        case LIGHT_BLUE_SHULKER_BOX: return 17;
        case YELLOW_SHULKER_BOX: return 18;
        case LIME_SHULKER_BOX: return 19;
        case PINK_SHULKER_BOX: return 20;
        case GRAY_SHULKER_BOX: return 21;
        case LIGHT_GRAY_SHULKER_BOX: return 22;
        case CYAN_SHULKER_BOX: return 23;
        case PURPLE_SHULKER_BOX: return 46;
        case BLUE_SHULKER_BOX: return 25;
        case BROWN_SHULKER_BOX: return 26;
        case GREEN_SHULKER_BOX: return 27;
        case RED_SHULKER_BOX: return 28;
        case BLACK_SHULKER_BOX: return 29;
        case WHITE_GLAZED_TERRACOTTA: return 8;
        case ORANGE_GLAZED_TERRACOTTA: return 15;
        case MAGENTA_GLAZED_TERRACOTTA: return 16;
        case LIGHT_BLUE_GLAZED_TERRACOTTA: return 17;
        case YELLOW_GLAZED_TERRACOTTA: return 18;
        case LIME_GLAZED_TERRACOTTA: return 19;
        case PINK_GLAZED_TERRACOTTA: return 20;
        case GRAY_GLAZED_TERRACOTTA: return 21;
        case LIGHT_GRAY_GLAZED_TERRACOTTA: return 22;
        case CYAN_GLAZED_TERRACOTTA: return 23;
        case PURPLE_GLAZED_TERRACOTTA: return 24;
        case BLUE_GLAZED_TERRACOTTA: return 25;
        case BROWN_GLAZED_TERRACOTTA: return 26;
        case GREEN_GLAZED_TERRACOTTA: return 27;
        case RED_GLAZED_TERRACOTTA: return 28;
        case BLACK_GLAZED_TERRACOTTA: return 29;
        case WHITE_CONCRETE: return 8;
        case ORANGE_CONCRETE: return 15;
        case MAGENTA_CONCRETE: return 16;
        case LIGHT_BLUE_CONCRETE: return 17;
        case YELLOW_CONCRETE: return 18;
        case LIME_CONCRETE: return 19;
        case PINK_CONCRETE: return 20;
        case GRAY_CONCRETE: return 21;
        case LIGHT_GRAY_CONCRETE: return 22;
        case CYAN_CONCRETE: return 23;
        case PURPLE_CONCRETE: return 24;
        case BLUE_CONCRETE: return 25;
        case BROWN_CONCRETE: return 26;
        case GREEN_CONCRETE: return 27;
        case RED_CONCRETE: return 28;
        case BLACK_CONCRETE: return 29;
        case WHITE_CONCRETE_POWDER: return 8;
        case ORANGE_CONCRETE_POWDER: return 15;
        case MAGENTA_CONCRETE_POWDER: return 16;
        case LIGHT_BLUE_CONCRETE_POWDER: return 17;
        case YELLOW_CONCRETE_POWDER: return 18;
        case LIME_CONCRETE_POWDER: return 19;
        case PINK_CONCRETE_POWDER: return 20;
        case GRAY_CONCRETE_POWDER: return 21;
        case LIGHT_GRAY_CONCRETE_POWDER: return 22;
        case CYAN_CONCRETE_POWDER: return 23;
        case PURPLE_CONCRETE_POWDER: return 24;
        case BLUE_CONCRETE_POWDER: return 25;
        case BROWN_CONCRETE_POWDER: return 26;
        case GREEN_CONCRETE_POWDER: return 27;
        case RED_CONCRETE_POWDER: return 28;
        case BLACK_CONCRETE_POWDER: return 29;
        case KELP_PLANT: return 12;
        case KELP: return 12;
        case DRIED_KELP_BLOCK: return 26;
        case TURTLE_EGG: return 22;
        case VOID_AIR: return 0;
        case CAVE_AIR: return 0;
        case DEAD_TUBE_CORAL_BLOCK: return 21;
        case DEAD_BRAIN_CORAL_BLOCK: return 21;
        case DEAD_BUBBLE_CORAL_BLOCK: return 21;
        case DEAD_FIRE_CORAL_BLOCK: return 21;
        case DEAD_HORN_CORAL_BLOCK: return 21;
        case TUBE_CORAL_BLOCK: return 25;
        case BRAIN_CORAL_BLOCK: return 20;
        case BUBBLE_CORAL_BLOCK: return 24;
        case FIRE_CORAL_BLOCK: return 28;
        case HORN_CORAL_BLOCK: return 18;
        case TUBE_CORAL: return 25;
        case BRAIN_CORAL: return 20;
        case BUBBLE_CORAL: return 24;
        case FIRE_CORAL: return 28;
        case HORN_CORAL: return 18;
        case DEAD_TUBE_CORAL: return 21;
        case DEAD_BRAIN_CORAL: return 21;
        case DEAD_BUBBLE_CORAL: return 21;
        case DEAD_FIRE_CORAL: return 21;
        case DEAD_HORN_CORAL: return 21;
        case TUBE_CORAL_WALL_FAN: return 25;
        case BRAIN_CORAL_WALL_FAN: return 20;
        case BUBBLE_CORAL_WALL_FAN: return 24;
        case FIRE_CORAL_WALL_FAN: return 28;
        case HORN_CORAL_WALL_FAN: return 18;
        case TUBE_CORAL_FAN: return 25;
        case BRAIN_CORAL_FAN: return 20;
        case BUBBLE_CORAL_FAN: return 24;
        case FIRE_CORAL_FAN: return 28;
        case HORN_CORAL_FAN: return 18;
        case SEA_PICKLE: return 27;
        case CONDUIT: return 31;
        case DEAD_TUBE_CORAL_WALL_FAN: return 21;
        case DEAD_BRAIN_CORAL_WALL_FAN: return 21;
        case DEAD_BUBBLE_CORAL_WALL_FAN: return 21;
        case DEAD_FIRE_CORAL_WALL_FAN: return 21;
        case DEAD_HORN_CORAL_WALL_FAN: return 21;
        case DEAD_TUBE_CORAL_FAN: return 21;
        case DEAD_BRAIN_CORAL_FAN: return 21;
        case DEAD_BUBBLE_CORAL_FAN: return 21;
        case DEAD_FIRE_CORAL_FAN: return 21;
        case DEAD_HORN_CORAL_FAN: return 21;
        case STRUCTURE_BLOCK: return 22;
        default: return 0;
        }
    }
}
