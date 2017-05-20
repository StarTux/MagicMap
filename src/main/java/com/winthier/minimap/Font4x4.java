package com.winthier.minimap;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import lombok.Value;
import org.bukkit.map.MapCanvas;

public final class Font4x4 {
    static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890!\"?()[]+-*/=.,:;'_";
    private final Map<Character, Char> charMap = new HashMap<>();

    @Value
    static class Pixel {
        private final int x, y;
    }

    @Value
    static class Char {
        private final int width, height;
        private final List<Pixel> pixels;
        private final List<Pixel> shadowPixels;
    }

    Font4x4(BufferedImage image) {
        load(image);
    }

    Font4x4(MiniMapPlugin plugin) {
        try {
            load(ImageIO.read(plugin.getResource("Font4x4.png")));
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private void load(BufferedImage image) {
        ArrayList<Integer> offsets = new ArrayList<>();
        for (int x = 0; x < image.getWidth(); ++x) {
            int pix = image.getRGB(x, 0);
            if ((pix & 0xffffff) != 0xffffff) {
                offsets.add(x);
            }
        }
        if (offsets.size() != CHARS.length()) throw new IllegalStateException("Invalid font size");
        ArrayList<Integer> widths = new ArrayList<>();
        for (int i = 1; i < offsets.size(); i += 1) widths.add(offsets.get(i) - offsets.get(i - 1));
        widths.add(image.getWidth() - offsets.get(offsets.size() - 1));
        for (int i = 0; i < CHARS.length(); i += 1) {
            char c = CHARS.charAt(i);
            int ax = offsets.get(i);
            int ay = 1;
            int width = widths.get(i);
            int height = image.getHeight() - 1;
            List<Pixel> pixels = new ArrayList<>();
            List<Pixel> shadowPixels = new ArrayList<>();
            for (int x = 0; x < width; x += 1) {
                for (int y = 0; y < height; y += 1) {
                    if ((image.getRGB(ax + x, ay + y) & 0xffffff) != 0xffffff) {
                        pixels.add(new Pixel(x, y));
                    }
                }
            }
            for (Pixel pixel: pixels) {
                Pixel shadowPixel = new Pixel(pixel.x + 1, pixel.y + 1);
                if (!pixels.contains(shadowPixel)) shadowPixels.add(shadowPixel);
            }
            charMap.put(c, new Char(width, height, pixels, shadowPixels));
        }
        charMap.put(' ', new Char(2, 4, new ArrayList<Pixel>(), new ArrayList<Pixel>()));
    }

    // Return width in pixels
    public int print(MapCanvas canvas, String msg, int x, int y, int width, int height, int color, int shadowColor) {
        msg = msg.toUpperCase();
        int length = 0;
        for (int i = 0; i < msg.length(); i += 1) {
            char c = msg.charAt(i);
            Char chr = charMap.get(c);
            if (chr == null) continue;
            for (Pixel pixel: chr.pixels) {
                if ((width < 0 || width >= length + pixel.x) && (height < 0 || height >= pixel.y)) {
                    canvas.setPixel(length + x + pixel.x, y + pixel.y, (byte)color);
                }
            }
            if (shadowColor >= 0) {
                for (Pixel pixel: chr.shadowPixels) {
                    if ((width < 0 || width >= length + pixel.x) && (height < 0 || height >= pixel.y)) {
                        canvas.setPixel(length + x + pixel.x, y + pixel.y, (byte)shadowColor);
                    }
                }
            }
            length += chr.width + 1;
        }
        return length;
    }

    public int widthOf(String msg) {
        msg = msg.toUpperCase();
        int width = 0;
        for (int i = 0; i < msg.length(); i += 1) {
            char c = msg.charAt(i);
            Char chr = charMap.get(c);
            if (chr != null) width += chr.width + 1;
        }
        return width;
    }
}
