package com.cavetale.magicmap;

// import java.awt.image.BufferedImage;
// import java.io.File;
// import java.lang.reflect.Field;
// import javax.imageio.ImageIO;
// import net.minecraft.server.v1_14_R1.Block;
// import net.minecraft.server.v1_14_R1.Blocks;
// import net.minecraft.server.v1_14_R1.MaterialMapColor;
// import org.bukkit.Material;
// import org.bukkit.map.MapPalette;

import java.io.File;
import org.bukkit.plugin.java.JavaPlugin;

final class ColorGrabber {
    static File grab(JavaPlugin plugin) throws Exception {
        throw new IllegalStateException();
        // File file = new File(plugin.getDataFolder(), "colors.txt");
        // java.io.PrintStream out = new java.io.PrintStream(file);
        // for (Field field: Blocks.class.getDeclaredFields()) {
        //     if (field.getType().equals(Block.class)) {
        //         Block block = (Block)field.get(null);
        //         Field tField = Block.class.getDeclaredField("t");
        //         tField.setAccessible(true);
        //         MaterialMapColor color = (MaterialMapColor) tField.get(block);
        //         String line = " case " + field.getName()
        //             + ": return " + color.ac + ";";
        //         out.println(line);
        //     }
        // }
        // out.close();
        // return file;
    }
}
