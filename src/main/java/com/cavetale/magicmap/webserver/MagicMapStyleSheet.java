package com.cavetale.magicmap.webserver;

import com.cavetale.webserver.html.HtmlDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import static com.cavetale.magicmap.MagicMapPlugin.plugin;

public final class MagicMapStyleSheet {
    private static String code;

    public static void load() {
        final String resourceName = "magicmap.css";
        try {
            code = new String(plugin().getResource(resourceName).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ioe) {
            plugin().getLogger().log(Level.SEVERE, resourceName, ioe);
        }
    }

    public static void install(HtmlDocument document) {
        document.getHead().addElement("style", style -> style.addRawText(code));
    }

    private MagicMapStyleSheet() { }
}
