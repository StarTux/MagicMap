package com.cavetale.magicmap;

import java.io.File;
import java.lang.reflect.Field;
import net.minecraft.server.v1_16_R2.Block;
import net.minecraft.server.v1_16_R2.Blocks;
import net.minecraft.server.v1_16_R2.MaterialMapColor;

import org.bukkit.plugin.java.JavaPlugin;

final class ColorGrabber {
    private ColorGrabber() { }

    static File grab(JavaPlugin plugin) throws Exception {
        // throw new IllegalStateException();
        File file = new File(plugin.getDataFolder(), "colors.txt");
        java.io.PrintStream out = new java.io.PrintStream(file);
        for (Field field: Blocks.class.getDeclaredFields()) {
            if (field.getType().equals(Block.class)) {
                Block block = (Block) field.get(null);
                Field tField = Block.class.getDeclaredField("t");
                tField.setAccessible(true);
                MaterialMapColor color = (MaterialMapColor) tField.get(block);
                String line = " case " + field.getName()
                    + ": return " + color.ac + ";";
                out.println(line);
            }
        }
        out.close();
        return file;
    }
}
