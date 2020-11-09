package com.cavetale.magicmap;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
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

    static void grab(File file) throws Exception {
        String ver = getServerVersion();
        if (!ver.equals("v1_16_R3")) {
            MagicMapPlugin.getInstance().getLogger().info("Using ColorGrabber for v1.16.4 on " + ver);
        }
        Class<?> blockClass = Class.forName("net.minecraft.server." + ver + ".Block");
        Class<?> blockBaseClass = Class.forName("net.minecraft.server." + ver + ".BlockBase");
        Class<?> blocksClass = Class.forName("net.minecraft.server." + ver + ".Blocks");
        Class<?> materialMapColorClass = Class.forName("net.minecraft.server." + ver + ".MaterialMapColor");
        List<String> lines = new ArrayList<>();
        for (Field field : getStaticFields(blocksClass, blockClass)) {
            Material bukkitMaterial;
            try {
                bukkitMaterial = Material.valueOf(field.getName());
            } catch (IllegalArgumentException iae) {
                continue;
            }
            Object block = field.get(null);
            Object materialMapColor = getter(block, blockBaseClass, "s");
            Object val = getField(materialMapColor, materialMapColorClass, "aj");
            lines.add("" + val + " " + bukkitMaterial.name());
        }
        if (lines.isEmpty()) throw new IllegalStateException("No colors found!");
        try (java.io.PrintStream out = new java.io.PrintStream(file)) {
            out.println("# " + ver);
            for (String line : lines) {
                out.println(line);
            }
        }
    }
}
