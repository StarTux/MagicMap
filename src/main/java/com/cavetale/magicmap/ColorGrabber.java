package com.cavetale.magicmap;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Value;
import org.bukkit.Material;

final class ColorGrabber {
    private ColorGrabber() { }

    static Object getField(Object object, Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    static Object getter(Object object, Class<?> type, String methodName) throws Exception {
        Method method = type.getDeclaredMethod(methodName);
        return method.invoke(object);
    }

    static List<Field> getStaticFields(Class<?> containerType, Class<?> objectType) {
        List<Field> list = new ArrayList<>();
        for (Field field : containerType.getDeclaredFields()) {
            if ((field.getModifiers() & Modifier.STATIC) == 0) continue;
            if (!field.getType().equals(objectType)) continue;
            list.add(field);
        }
        return list;
    }

    @Value
    public static final class ColorEntry implements Comparable<ColorEntry> {
        protected final int index;
        protected final Material material;

        @Override
        public int compareTo(ColorEntry other) {
            return Integer.compare(index, other.index);
        }

        @Override
        public String toString() {
            return "" + index + " " + material;
        }
    }

    /**
     * This requires a fully initialized server runtime, thus can run
     * as a command.
     */
    static void grabMaterials(File output) throws Exception {
        Class<?> blocksClass = Class.forName("net.minecraft.world.level.block.Blocks");
        Class<?> blockClass = Class.forName("net.minecraft.world.level.block.Block");
        Class<?> blockBehaviourClass = Class.forName("net.minecraft.world.level.block.state.BlockBehaviour");
        Class<?> mapColorClass = Class.forName("net.minecraft.world.level.material.MapColor");
        List<ColorEntry> indexList = new ArrayList<>();
        // All fields in Blocks
        for (Field field : getStaticFields(blocksClass, blockClass)) {
            Object block = field.get(null);
            System.out.println("block=" + block);
            // BlockBehaviour::w() => MapColor
            Object mapColor = getter(block, blockBehaviourClass, "defaultMapColor");
            System.out.println("mapColor=" + mapColor);
            // MapColor.id => int (color id)
            int colorIndex = (Integer) getField(mapColor, mapColorClass, "id");
            System.out.println("colorIndex=" + colorIndex);
            String mat = field.getName();
            try {
                final Material material = Material.valueOf(mat.toUpperCase());
                indexList.add(new ColorEntry(colorIndex, material));
            } catch (IllegalArgumentException iae) {
                MagicMapPlugin.getInstance().getLogger().warning("Illegal material: " + mat + "/" + colorIndex);
            }
        }
        if (indexList.isEmpty()) throw new IllegalStateException("No colors found!");
        Collections.sort(indexList);
        try (java.io.PrintStream out = new java.io.PrintStream(output)) {
            for (ColorEntry it : indexList) {
                out.println("MATERIAL_MAP.put(Material." + it.material + ", " + it.index + ");");
            }
        }
    }

    private static int brightness(int hex, int bright) {
        if (hex == 0) return 0;
        int r = (hex >> 16 & 0xFF) * bright / 255;
        int g = (hex >> 8 & 0xFF) * bright / 255;
        int b = (hex & 0xFF) * bright / 255;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /**
     * This can NOT run as a test case.
     */
    public static void grabColors(File output) throws Exception {
        Class<?> materialMapColorClass = Class.forName("net.minecraft.world.level.material.MapColor");
        try (java.io.PrintStream out = new java.io.PrintStream(output)) {
            for (Field field : getStaticFields(materialMapColorClass, materialMapColorClass)) {
                Object color = field.get(null);
                int index = (Integer) getField(color, materialMapColorClass, "al");
                int hex = (Integer) getField(color, materialMapColorClass, "ak");
                out.println("COLOR_" + index
                            + "(" + index
                            + ", 0x" + Integer.toHexString(brightness(hex, 180))
                            + ", 0x" + Integer.toHexString(brightness(hex, 220))
                            + ", 0x" + Integer.toHexString(brightness(hex, 255))
                            + ", 0x" + Integer.toHexString(brightness(hex, 135))
                            + "),");
                if (index != 0) {
                    assert (0xFF000000 | hex) == brightness(hex, 255);
                }
            }
        }
    }
}
