package com.cavetale.magicmap.webserver;

import com.cavetale.core.util.Json;
import com.cavetale.magicmap.file.WorldBorderCache;
import com.cavetale.webserver.html.HtmlDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import static com.cavetale.magicmap.MagicMapPlugin.plugin;

public final class MagicMapScript {
    private static String code;

    public static void init() {
        final String resourceName = "magicmap.js";
        try {
            code = new String(plugin().getResource(resourceName).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ioe) {
            plugin().getLogger().log(Level.SEVERE, resourceName, ioe);
        }
    }

    public static void install(HtmlDocument document, String mapName, WorldBorderCache worldBorder, int scalingFactor) {
        final String text = code
            .replace("map_name", mapName)
            .replace("world_border", Json.prettyPrint(worldBorder))
            .replace("scaling_factor", "" + scalingFactor);
        document.getBody().addElement("script", script -> script.addRawText(text));
    }

    private MagicMapScript() { }
}
