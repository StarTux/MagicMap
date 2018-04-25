package com.winthier.minimap;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.bukkit.map.MapPalette;

final class TileSet {
    enum Tile {
        CHECKBOX_EMPTY(0, 0, 8, 8),
        CHECKBOX_CROSS(8, 0, 8, 8),
        CHECKBOX_CHECK(0, 8, 8, 8),
        CHECKBOX_STROKE(8, 8, 8, 8),
        BUTTON_EVENTS(32, 0, 32, 32),
        BUTTON_GETTING_STARTED(64, 0, 32, 32),
        BUTTON_SETTINGS(96, 0, 32, 32),
        BUTTON_COMING_SOON(0, 32, 32, 32),
        ICON_BUILD(32, 32, 16, 16),
        ICON_RESOURCE(48, 32, 16, 16),
        ICON_SPAWN(32, 48, 16, 16),
        ICON_MARKET(48, 48, 16, 16),
        FOO(0, 0);

        int x, y, dx, dy;
        Tile(int x, int y, int dx, int dy) {
            this.x = x;
            this.y = y;
            this.dx = dx;
            this.dy = dy;
        }
        Tile(int x, int y) {
            this(x, y, 16, 16);
        }
    }

    private static TileSet instance = null;
    private byte[] pixels;
    private int width, height;

    static synchronized TileSet getInstance() {
        if (instance == null) instance = new TileSet();
        return instance;
    }

    @SuppressWarnings("deprecation")
    private TileSet() {
        final BufferedImage image;
        try {
            image = ImageIO.read(MiniMapPlugin.getInstance().getResource("tiles.png"));
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return;
        }
        pixels = MapPalette.imageToBytes(image);
        width = image.getWidth();
        height = image.getHeight();
    }

    public void paste(int tx, int ty, int tdx, int tdy, MapCache cache, int sx, int sy) {
        for (int y = 0; y < tdy; y += 1) {
            for (int x = 0; x < tdx; x += 1) {
                int pixel = (int)pixels[tx + x + (ty + y) * width];
                if (pixel == 0) continue;
                cache.setPixel(sx + x, sy + y, pixel);
            }
        }
    }

    public void paste(Tile tile, MapCache cache, int sx, int sy) {
        for (int y = 0; y < tile.dy; y += 1) {
            for (int x = 0; x < tile.dx; x += 1) {
                int pixel = (int)pixels[tile.x + x + (tile.y + y) * width];
                if (pixel == 0) continue;
                cache.setPixel(sx + x, sy + y, pixel);
            }
        }
    }
}
