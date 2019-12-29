package com.cavetale.magicmap;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import lombok.Value;

public final class TinyFont {
    static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890!\"?()[]+-*/=.,:;'_";
    private final Map<Character, Char> charMap = new HashMap<>();

    @Value
    static class Pixel {
        private final int x;
        private final int y;
    }

    @Value
    static class Char {
        private final int width;
        private final int height;
        private final List<Pixel> pixels;
        private final List<Pixel> shadowPixels;
    }

    TinyFont(final BufferedImage image) {
        load(image);
    }

    TinyFont(final MagicMapPlugin plugin) {
        try {
            load(ImageIO.read(plugin.getResource("TinyFont.png")));
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
                Pixel shadowPixel;
                shadowPixel = new Pixel(pixel.x + 1, pixel.y);
                if (!pixels.contains(shadowPixel) && !shadowPixels.contains(shadowPixel)) {
                    shadowPixels.add(shadowPixel);
                }
                shadowPixel = new Pixel(pixel.x, pixel.y + 1);
                if (!pixels.contains(shadowPixel) && !shadowPixels.contains(shadowPixel)) {
                    shadowPixels.add(shadowPixel);
                }
                shadowPixel = new Pixel(pixel.x - 1, pixel.y);
                if (!pixels.contains(shadowPixel) && !shadowPixels.contains(shadowPixel)) {
                    shadowPixels.add(shadowPixel);
                }
                shadowPixel = new Pixel(pixel.x, pixel.y - 1);
                if (!pixels.contains(shadowPixel) && !shadowPixels.contains(shadowPixel)) {
                    shadowPixels.add(shadowPixel);
                }
            }
            charMap.put(c, new Char(width, height, pixels, shadowPixels));
        }
        charMap.put(' ', new Char(2, 4, new ArrayList<Pixel>(), new ArrayList<Pixel>()));
    }

    public interface Drawer {
        void draw(int x, int y);
    }

    public int print(String msg, int x, int y, Drawer drawer, Drawer shadowDrawer) {
        if (msg == null) return 0;
        msg = msg.toUpperCase();
        int length = 0;
        for (int i = 0; i < msg.length(); i += 1) {
            char c = msg.charAt(i);
            Char chr = charMap.get(c);
            if (chr == null) continue;
            for (Pixel pixel: chr.pixels) {
                drawer.draw(length + x + pixel.x, y + pixel.y);
            }
            for (Pixel pixel: chr.shadowPixels) {
                shadowDrawer.draw(length + x + pixel.x, y + pixel.y);
            }
            length += chr.width + 1;
        }
        return length;
    }

    public int print(MapCache mapCache, String msg, int x, int y, int color, int shadow) {
        return print(msg, x, y,
                     (px, py) -> mapCache.setPixel(px, py, color),
                     (px, py) -> mapCache.setPixel(px, py, shadow));
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
