package com.cavetale.magicmap.file;

import com.cavetale.core.struct.Vec2i;
import lombok.Value;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;

/**
 * This stores information of common interest about a world's border
 * for later use.
 * Namely, the block coordinates of the center, as well as the bounds
 * in all directions.
 */
@Value
public final class WorldBorderCache {
    public final int centerX;
    public final int centerZ;
    public final int minX;
    public final int minZ;
    public final int maxX;
    public final int maxZ;

    public static WorldBorderCache of(World world) {
        final WorldBorder worldBorder = world.getWorldBorder();
        final Location center = worldBorder.getCenter();
        final int centerX = center.getBlockX();
        final int centerZ = center.getBlockZ();
        final double halfSize = worldBorder.getSize() * 0.5;
        final int minX = (int) Math.floor(center.getX() - halfSize);
        final int maxX = (int) Math.floor(center.getX() + halfSize);
        final int minZ = (int) Math.floor(center.getZ() - halfSize);
        final int maxZ = (int) Math.floor(center.getX() + halfSize);
        return new WorldBorderCache(centerX, centerZ, minX, minZ, maxX, maxZ);
    }

    public boolean containsBlock(final int x, final int z) {
        return x >= minX && x <= maxX
            && z >= minZ && z <= maxZ;
    }

    public boolean containsRegion(final int rx, final int rz) {
        final int ax = rx << 9;
        if (ax > maxX) return false;
        final int bx = ax + 511;
        if (bx < minX) return false;
        final int az = rz << 9;
        if (az > maxZ) return false;
        final int bz = az + 512;
        if (bz < minZ) return false;
        return true;
    }

    public boolean containsRegion(Vec2i region) {
        return containsRegion(region.x, region.z);
    }

    @Override
    public String toString() {
        return centerX + " " + centerZ
            + " (" + minX + " " + minZ + ")-(" + maxX + " " + maxZ + ")";
    }
}
