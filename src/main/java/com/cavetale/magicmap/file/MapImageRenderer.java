package com.cavetale.magicmap.file;

import com.cavetale.magicmap.ColorIndex;
import com.cavetale.magicmap.RenderType;
import java.awt.image.BufferedImage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;

@Getter
@RequiredArgsConstructor
public final class MapImageRenderer {
    private final World world;
    private final BufferedImage image;
    private final RenderType renderType;
    private final int minWorldX;
    private final int minWorldZ;
    private final int sizeX;
    private final int sizeZ;
    private final WorldBorderCache worldBorder;
    private final int minWorldY;
    private final int maxWorldY;
    private int canvasX = -1;
    private int canvasY = 0;
    private boolean finished;

    public MapImageRenderer(final World world,
                            final BufferedImage image,
                            final RenderType renderType,
                            final int minWorldX, final int minWorldZ,
                            final int sizeX, final int sizeZ,
                            final WorldBorderCache worldBorder) {
        this.world = world;
        this.image = image;
        this.renderType = renderType;
        this.minWorldX = minWorldX;
        this.minWorldZ = minWorldZ;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.worldBorder = worldBorder;
        this.minWorldY = world.getMinHeight();
        this.maxWorldY = world.getMaxHeight();
    }

    public void run(int steps) {
        for (int i = 0; i < steps && !finished; i += 1) {
            step();
        }
    }

    public void run() {
        while (!finished) {
            step();
        }
    }

    private void step() {
        canvasX += 1;
        if (canvasX >= sizeX) {
            canvasX = 0;
            canvasY += 1;
            if (canvasY >= sizeZ) {
                finished = true;
                return;
            }
        }
        final int worldX = canvasX + minWorldX;
        final int worldZ = canvasY + minWorldZ;
        if (!worldBorder.containsBlock(worldX, worldZ)) {
            image.setRGB(canvasX, canvasY, 0);
            return;
        }
        final int highest = highest(worldX, worldZ);
        if (highest < minWorldY) {
            if (renderType == RenderType.SURFACE) {
                image.setRGB(canvasX, canvasY, 0);
            } else {
                image.setRGB(canvasX, canvasY, ColorIndex.BLACK.darkRgb);
            }
            return;
        }
        Block block = world.getBlockAt(worldX, highest, worldZ);
        final int color;
        if (block.getType() == Material.WATER || (block.getBlockData() instanceof Waterlogged w && w.isWaterlogged())) {
            final ColorIndex colorIndex = ColorIndex.WATER;
            while (block.getY() > minWorldY && (block.getType() == Material.WATER || (block.getBlockData() instanceof Waterlogged w2 && w2.isWaterlogged()))) {
                block = block.getRelative(0, -1, 0);
            }
            int depth = highest - block.getY();
            if (depth <= 2) {
                color = colorIndex.brightRgb;
            } else if (depth <= 4) {
                color = (canvasX & 1) == (canvasY & 1) ? colorIndex.brightRgb : colorIndex.lightRgb;
            } else if (depth <= 6) {
                color = colorIndex.lightRgb;
            } else if (depth <= 8) {
                color = (canvasX & 1) == (canvasY & 1) ? colorIndex.normalRgb : colorIndex.lightRgb;
            } else if (depth <= 12) {
                color = colorIndex.normalRgb;
            } else if (depth <= 16) {
                color = (canvasX & 1) == (canvasY & 1) ? colorIndex.normalRgb : colorIndex.darkRgb;
            } else {
                color = colorIndex.darkRgb;
            }
            image.setRGB(canvasX, canvasY, color);
        } else if (block.getType() == Material.LAVA) {
            final ColorIndex colorIndex = ColorIndex.LAVA;
            while (block.getY() > minWorldY && block.getType() == Material.LAVA) {
                block = block.getRelative(0, -1, 0);
            }
            int depth = highest - block.getY();
            if (depth <= 2) {
                color = colorIndex.brightRgb;
            } else if (depth <= 4) {
                color = (canvasX & 1) == (canvasY & 1) ? colorIndex.brightRgb : colorIndex.lightRgb;
            } else if (depth <= 6) {
                color = colorIndex.lightRgb;
            } else if (depth <= 8) {
                color = (canvasX & 1) == (canvasY & 1) ? colorIndex.normalRgb : colorIndex.lightRgb;
            } else if (depth <= 12) {
                color = colorIndex.normalRgb;
            } else if (depth <= 16) {
                color = (canvasX & 1) == (canvasY & 1) ? colorIndex.normalRgb : colorIndex.darkRgb;
            } else {
                color = colorIndex.darkRgb;
            }
            image.setRGB(canvasX, canvasY, color);
        } else {
            final ColorIndex colorIndex = ColorIndex.ofMaterial(block.getType(), ColorIndex.BLACK);
            // Neighbor block where the sunlight comes from.
            final int lx = 0;
            final int ly = 1;
            final int nx = worldX + lx;
            final int nz = worldZ + ly;
            // 1 == Bright
            // 2 == Super Bright
            // 3 == Dark
            int highestN = highest(nx, nz);
            if (highestN >= 0) {
                if (highest > highestN) {
                    color = colorIndex.brightRgb;
                } else if (highest < highestN) {
                    color = colorIndex.normalRgb;
                } else {
                    color = colorIndex.lightRgb;
                }
            } else {
                color = colorIndex.normalRgb;
            }
            try {
                image.setRGB(canvasX, canvasY, color);
            } catch (ArrayIndexOutOfBoundsException aioobe) {
                throw new RuntimeException("coords:" + canvasX + "," + canvasY
                                           + " size:" + sizeX + "," + sizeZ
                                           + " image:" + image.getWidth() + "," + image.getHeight(),
                                           aioobe);
            }
        }
    }

    private int highest(int x, int z) {
        return switch (renderType) {
        case NETHER -> highestNether(x, z);
        case CAVE -> highestCave(x, z);
        case SURFACE -> highestSurface(x, z);
        default -> highestSurface(x, z);
        };
    }

    private int highestNether(int x, int z) {
        int y = 127;
        // skip blocks
        while (y >= minWorldY && !world.getBlockAt(x, y, z).isEmpty()) y -= 1;
        // skip air
        while (y >= minWorldY && world.getBlockAt(x, y, z).isEmpty()) y -= 1;
        // skip transparent, non-lava
        while (y >= minWorldY) {
            final Block block = world.getBlockAt(x, y, z);
            if (block.isLiquid()) break;
            final ColorIndex colorIndex = ColorIndex.ofMaterial(block.getType());
            if (colorIndex != null && !colorIndex.isEmpty()) break;
            y -= 1;
        }
        return y;
    }

    private int highestCave(int x, int z) {
        int y = maxWorldY;
        // skip air
        while (y >= minWorldY && world.getBlockAt(x, y, z).isEmpty()) y -= 1;
        while (y >= minWorldY) { // skip sunlit blocks
            Block block = world.getBlockAt(x, y, z);
            if (block.isLiquid() || !block.isEmpty() || block.getLightFromSky() == 15) {
                y -= 1;
            } else {
                break;
            }
        }
        // skip air
        while (y >= minWorldY && world.getBlockAt(x, y, z).isEmpty()) y -= 1;
        // skip transparent, non-water
        while (y >= minWorldY) {
            final Block block = world.getBlockAt(x, y, z);
            if (block.isLiquid()) break;
            final ColorIndex colorIndex = ColorIndex.ofMaterial(block.getType());
            if (colorIndex != null && !colorIndex.isEmpty()) break;
            y -= 1;
        }
        return y;
    }

    private int highestSurface(int x, int z) {
        int y = maxWorldY;
        // skip air
        while (y >= minWorldY && world.getBlockAt(x, y, z).isEmpty()) y -= 1;
        // skip transparent
        while (y >= minWorldY) {
            final Block block = world.getBlockAt(x, y, z);
            if (block.isLiquid()) break;
            final ColorIndex colorIndex = ColorIndex.ofMaterial(block.getType());
            if (colorIndex != null && !colorIndex.isEmpty()) break;
            y -= 1;
        }
        return y;
    }
}
