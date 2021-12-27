package com.cavetale.magicmap;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Value;
import org.bukkit.Bukkit;
import org.bukkit.Material;

final class ColorGrabber {
    private ColorGrabber() { }

    static String getServerVersion() {
        return Bukkit.getServer().getClass().getPackage().getName().substring(23);
    }

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
    public static final class ColorIndex implements Comparable<ColorIndex> {
        protected final int index;
        protected final Material material;

        @Override
        public int compareTo(ColorIndex other) {
            return Integer.compare(index, other.index);
        }

        @Override
        public String toString() {
            return "" + index + " " + material;
        }
    }

    static void grab(File output) throws Exception {
        String ver = getServerVersion();
        String expectedVersion = "v1_17_R1";
        if (!ver.equals(expectedVersion)) {
            MagicMapPlugin.getInstance().getLogger().warning("Using ColorGrabber for " + expectedVersion + " on " + ver);
        }
        Class<?> blockClass = Class.forName("net.minecraft.world.level.block.Block");
        Class<?> blockBaseClass = Class.forName("net.minecraft.world.level.block.state.BlockBase");
        Class<?> blocksClass = Class.forName("net.minecraft.world.level.block.Blocks");
        Class<?> materialMapColorClass = Class.forName("net.minecraft.world.level.material.MaterialMapColor");
        List<ColorIndex> indexList = new ArrayList<>();
        // All fields in Blocks
        for (Field field : getStaticFields(blocksClass, blockClass)) {
            Object block = field.get(null);
            // BlockBase::s() => MaterialMapColor
            Object materialMapColor = getter(block, blockBaseClass, "s");
            // MaterialMapColor.am => int (color id)
            int colorIndex = (Integer) getField(materialMapColor, materialMapColorClass, "al");
            // BlockBase::r() => MinecraftKey
            String key = (String) getter(block, blockClass, "h");
            int idx = key.lastIndexOf(".");
            if (idx < 0) {
                MagicMapPlugin.getInstance().getLogger().warning("Illegal material: " + key + " / " + colorIndex);
                continue;
            }
            String mat = key.substring(idx + 1);
            try {
                final Material material = Material.valueOf(mat.toUpperCase());
                indexList.add(new ColorIndex(colorIndex, material));
            } catch (IllegalArgumentException iae) {
                MagicMapPlugin.getInstance().getLogger().warning("Illegal material: " + mat + "/" + colorIndex);
            }
        }
        if (indexList.isEmpty()) throw new IllegalStateException("No colors found!");
        Collections.sort(indexList);
        try (java.io.PrintStream out = new java.io.PrintStream(output)) {
            out.println("# " + ver);
            for (ColorIndex colorIndex : indexList) {
                out.println(colorIndex);
            }
        }
    }
}
