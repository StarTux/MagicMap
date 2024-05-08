package com.cavetale.magicmap.webserver;

import com.cavetale.core.connect.NetworkServer;
import com.cavetale.magicmap.RenderType;
import com.cavetale.magicmap.file.WorldBorderCache;
import com.cavetale.magicmap.file.WorldFileCache;
import com.cavetale.magicmap.file.WorldRenderCache;
import com.cavetale.webserver.content.ContentDelivery;
import com.cavetale.webserver.content.ContentDeliverySession;
import com.cavetale.webserver.content.FileContentProvider;
import com.cavetale.webserver.html.CachedHtmlContentProvider;
import com.cavetale.webserver.http.HttpContentType;
import com.cavetale.webserver.http.HttpResponseStatus;
import com.cavetale.webserver.http.StaticContentProvider;
import com.cavetale.webserver.websocket.WebsocketHook;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import lombok.Getter;
import static com.cavetale.core.util.CamelCase.toCamelCase;
import static com.cavetale.magicmap.MagicMapPlugin.plugin;

@Getter
public final class MagicMapContentDelivery implements ContentDelivery, WebsocketHook {
    private final String name = "MagicMap";
    private final List<String> paths = List.of("map");
    private final Map<String, WorldFileCache> worldMap = new HashMap<>();
    private final StaticContentProvider emptyRegionPngProvider = new StaticContentProvider(HttpContentType.IMAGE_PNG, new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB), "png");

    /**
     * Scan for available maps.
     */
    public MagicMapContentDelivery enable() {
        for (NetworkServer server : NetworkServer.values()) {
            enableNetworkServer(server);
        }
        MagicMapScript.init();
        return this;
    }

    private void enableNetworkServer(NetworkServer server) {
        final String separator = FileSystems.getDefault().getSeparator();
        final Path serverFolder = switch (server.getFolderLocation()) {
        case BASE -> Path.of(separator + "home", "cavetale", server.name().toLowerCase());
        case MINI -> Path.of(separator + "home", "cavetale", "mini", toCamelCase("", server));
        default -> null;
        };
        if (serverFolder == null) return;
        if (!Files.isDirectory(serverFolder)) {
            plugin().getLogger().warning("[ContentDelivery] Not a server folder: " + serverFolder);
            return;
        }
        final Path worldsFolder = serverFolder.resolve("worlds");
        if (!Files.isDirectory(worldsFolder)) {
            plugin().getLogger().warning("[ContentDelivery] Not a worlds folder: " + worldsFolder);
            return;
        }
        final List<Path> worldFolders;
        try {
            worldFolders = Files.list(worldsFolder).toList();
        } catch (IOException ioe) {
            plugin().getLogger().log(Level.SEVERE, "[ContentDelivery] worldsFolder:" + worldsFolder.toString(), ioe);
            return;
        }
        for (Path worldFolder : worldFolders) {
            if (!Files.isDirectory(worldFolder)) continue;
            final Path magicMapFolder = worldFolder.resolve("magicmap");
            if (!Files.isDirectory(magicMapFolder)) {
                continue;
            }
            plugin().getLogger().info("Creating map " + server + " " + worldFolder.getFileName().toString() + " " + worldFolder.toFile());
            WorldFileCache worldFileCache = new WorldFileCache(server, worldFolder.getFileName().toString(), worldFolder.toFile());
            worldFileCache.enableWebserver();
            final String mapName;
            switch (server) {
            case BETA:
                mapName = worldFolder.getFileName().toString().replace("world", "beta");
                break;
            case HUB:
            case EINS:
            case ZWEI:
            case DREI:
            case VIER:
                mapName = "" + worldFolder.getFileName();
                break;
            default:
                mapName = server.name().toLowerCase().replace("_", "") + "." + worldFolder.getFileName();
            }
            worldMap.put(mapName, worldFileCache);
            plugin().getLogger().info("[ContentDelivery] Map registered: " + server + " " + mapName + " " + worldFolder);
        }
    }

    @Override
    public void onReady(ContentDeliverySession session, List<String> usedPath, List<String> unusedPath) {
        if (unusedPath.isEmpty()) {
            sendMapHtml(session, "spawn");
            return;
        }
        if (unusedPath.size() == 1) {
            sendMapHtml(session, unusedPath.get(0));
        }
        if (unusedPath.size() == 2) {
            sendRegionFile(session, unusedPath.get(0), unusedPath.get(1));
        }
    }

    private int scalingFactor = 2;

    private void sendMapHtml(ContentDeliverySession session, String mapName) {
        final WorldFileCache worldFileCache = worldMap.get(mapName);
        if (worldFileCache == null) {
            plugin().getLogger().warning("World not found");
            session.send(); // 404
            return;
        }
        final WorldBorderCache worldBorder = worldFileCache.getWorldBorder();
        final RenderType renderType = worldFileCache.getMainRenderType();
        if (renderType == null) {
            plugin().getLogger().warning("No main render type");
            session.send(); // 404
            return;
        }
        final WorldRenderCache renderCache = worldFileCache.getRenderTypeMap().get(renderType);
        if (renderCache == null) {
            plugin().getLogger().warning("World render not found");
            session.send(); // 404
            return;
        }
        final CachedHtmlContentProvider provider = new CachedHtmlContentProvider();
        session.getResponse().setContentProvider(provider);
        session.attachWebsocketScript(provider.getDocument());
        MagicMapScript.install(provider.getDocument(), mapName, worldFileCache.getTag(), scalingFactor);
        provider.getDocument().getHead().addElement("title", t -> t.addText("Regions"));
        provider.getDocument().getBody().addElement("div", div -> {
                final Map<String, String> divStyle = new HashMap<>();
                divStyle.put("position", "absolute");
                divStyle.put("width", "100%");
                divStyle.put("height", "100%");
                divStyle.put("overflow", "scroll");
                divStyle.put("padding", "0px");
                divStyle.put("margin", "0px");
                divStyle.put("left", "0");
                divStyle.put("right", "0");
                divStyle.put("top", "0");
                divStyle.put("bottom", "0");
                divStyle.put("background-color", "#cda882");
                div.setId("map_frame").setStyle(divStyle);
                final int minRegionX = worldBorder.getMinX() >> 9;
                final int maxRegionX = worldBorder.getMaxX() >> 9;
                final int minRegionZ = worldBorder.getMinZ() >> 9;
                final int maxRegionZ = worldBorder.getMaxZ() >> 9;
                for (int rz = minRegionZ; rz <= maxRegionZ; rz += 1) {
                    for (int rx = minRegionX; rx <= maxRegionX; rx += 1) {
                        final int left = (rx - minRegionX) * 512;
                        final int top = (rz - minRegionZ) * 512;
                        final String id = "r." + rx + "." + rz;
                        div.addElement("img", img -> {
                                img.setId(id)
                                    .setClassName("tile")
                                    .setAttribute("draggable", "false")
                                    .setStyle(Map.of("width", scalingFactor * 512 + "px",
                                                     "height", scalingFactor * 512 + "px",
                                                     "position", "absolute",
                                                     "top", scalingFactor * top + "px",
                                                     "left", scalingFactor * left + "px",
                                                     "image-rendering", "pixelated"));
                            });
                    }
                }
            });
        session.getResponse().setStatus(HttpResponseStatus.OK);
        session.send();
    }

    private void sendRegionFile(ContentDeliverySession session, String mapName, String fileName) {
        final WorldFileCache worldFileCache = worldMap.get(mapName);
        if (worldFileCache == null) {
            plugin().getLogger().warning("World not found");
            session.send(); // 404
            return;
        }
        final String[] tokens = fileName.split("\\.");
        if (tokens.length != 4 || !tokens[0].equals("r") || !tokens[3].equals("png")) {
            plugin().getLogger().warning("Invalid file format: " + List.of(tokens));
            session.send(); // 404
            return;
        }
        final int x;
        final int z;
        try {
            x = Integer.parseInt(tokens[1]);
            z = Integer.parseInt(tokens[2]);
        } catch (IllegalArgumentException iae) {
            plugin().getLogger().warning("Invalid file coordinates");
            session.send(); // 404
            return;
        }
        final RenderType renderType = worldFileCache.getMainRenderType();
        if (renderType == null) {
            plugin().getLogger().warning("No main render type");
            session.send(); // 404
            return;
        }
        final WorldRenderCache worldRenderCache = worldFileCache.getRenderTypeMap().get(renderType);
        final File sendFile = new File(worldRenderCache.getMapFolder(), "r." + x + "." + z + ".png");
        if (!worldFileCache.getWorldBorder().containsRegion(x, z) || !sendFile.exists()) {
            session.getResponse().setContentProvider(emptyRegionPngProvider);
        } else {
            session.getResponse().setContentProvider(new FileContentProvider(HttpContentType.IMAGE_PNG, sendFile));
        }
        session.getResponse().setStatus(HttpResponseStatus.OK);
        session.send();
    }

    @Override
    public void onWebsocketReceiveText(String text) {
    }
}
