package com.cavetale.magicmap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import lombok.Getter;
import org.bukkit.Material;

@Getter
public final class MapColor {
    private int[] colors;
    private int count;

    public int of(Material mat) {
        return raw(mat) << 2;
    }

    public int raw(Material mat) {
        return colors[mat.ordinal()];
    }

    public boolean load(InputStream inputStream) {
        Material[] mats = Material.values();
        count = 0;
        colors = new int[mats.length];
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while (null != (line = reader.readLine())) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] toks = line.split(" ", 2);
                int val;
                Material material;
                try {
                    val = Integer.parseInt(toks[0]);
                } catch (IllegalArgumentException iae) {
                    MagicMapPlugin.getInstance().getLogger().warning("Invalid value: " + line);
                    continue;
                }
                try {
                    material = Material.valueOf(toks[1]);
                } catch (IllegalArgumentException iae) {
                    MagicMapPlugin.getInstance().getLogger().warning("Unknown material: " + line);
                    continue;
                }
                colors[material.ordinal()] = val;
                count += 1;
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return false;
        }
        colors[Material.WATER.ordinal()] = 12;
        colors[Material.LAVA.ordinal()] = 15;
        return count > 0;
    }
}
