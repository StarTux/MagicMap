package com.winthier.minimap;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.bukkit.map.MapPalette;
import org.junit.Assert;
import org.junit.Test;

public final class MiniMapTest {
    @Test
    public void main() throws Exception {
        BufferedImage image = ImageIO.read(new File("src/main/resources/Font4x4.png"));
        Font4x4 font4x4 = new Font4x4(image);
        for (org.bukkit.DyeColor dye: org.bukkit.DyeColor.values()) {
            // org.bukkit.Color c = dye.getColor();
            // int result = MapPalette.matchColor(new java.awt.Color(c.getRed(), c.getGreen(), c.getBlue()));
            System.out.println("case " + dye + "_GLAZED_TERRACOTTA: return Colors.WOOL_" + dye + " + shade;");
        }
        for (Field field: MapPalette.class.getDeclaredFields()) {
            if (field.getType() != byte.class) continue;
            field.setAccessible(true);
            System.out.println("public static final int " + field.getName() + " = " + field.getInt(null) + ";");
        }
    }
}
