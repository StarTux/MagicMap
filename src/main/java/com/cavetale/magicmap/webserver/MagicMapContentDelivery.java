package com.cavetale.magicmap.webserver;

import com.cavetale.core.connect.NetworkServer;
import com.cavetale.core.playercache.PlayerCache;
import com.cavetale.magicmap.PlayerLocationTag;
import com.cavetale.magicmap.RenderType;
import com.cavetale.magicmap.file.WorldBorderCache;
import com.cavetale.magicmap.file.WorldFileCache;
import com.cavetale.magicmap.file.WorldRenderCache;
import com.cavetale.webserver.content.ContentDelivery;
import com.cavetale.webserver.content.ContentDeliverySession;
import com.cavetale.webserver.content.FileContentProvider;
import com.cavetale.webserver.html.CachedHtmlContentProvider;
import com.cavetale.webserver.html.DefaultStyleSheet;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
        final WorldBorderCache worldBorder = worldFileCache.getEffectiveWorldBorder();
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
        final MagicMapContentDeliverySessionData sessionData = new MagicMapContentDeliverySessionData();
        sessionData.setServer(worldFileCache.getServer());
        sessionData.setMapName(mapName);
        sessionData.setWorldName(worldFileCache.getName());
        for (Map.Entry<UUID, PlayerLocationTag> entry : plugin().getWebserverManager().getPlayerLocationTags().entrySet()) {
            sessionData.getPlayerLocationTags().put(entry.getKey(), entry.getValue().clone());
        }
        session.setContentDeliverySessionData(sessionData);
        final CachedHtmlContentProvider provider = new CachedHtmlContentProvider();
        session.getResponse().setContentProvider(provider);
        session.attachWebsocketScript(provider.getDocument());
        DefaultStyleSheet.install(provider.getDocument());
        MagicMapScript.install(provider.getDocument(), mapName, worldBorder, scalingFactor);
        provider.getDocument().getHead().addElement("title", t -> t.addText(worldFileCache.getDisplayName()));
        final var mapFrame = provider.getDocument().getBody().addElement("div");
        mapFrame.setId("map_frame").style(style -> {
                style.put("position", "absolute");
                style.put("width", "100%");
                style.put("height", "100%");
                style.put("overflow", "scroll");
                style.put("padding", "0px");
                style.put("margin", "0px");
                style.put("left", "0");
                style.put("right", "0");
                style.put("top", "0");
                style.put("bottom", "0");
                style.put("background-color", "#cda882");
            });
        final int minRegionX = worldBorder.getMinX() >> 9;
        final int maxRegionX = worldBorder.getMaxX() >> 9;
        final int minRegionZ = worldBorder.getMinZ() >> 9;
        final int maxRegionZ = worldBorder.getMaxZ() >> 9;
        for (int rz = minRegionZ; rz <= maxRegionZ; rz += 1) {
            for (int rx = minRegionX; rx <= maxRegionX; rx += 1) {
                final int left = (rx - minRegionX) * 512;
                final int top = (rz - minRegionZ) * 512;
                final String id = "r." + rx + "." + rz;
                mapFrame.addElement("img", img -> {
                        img.setId(id)
                            .setClassName("map_region")
                            .setAttribute("draggable", "false")
                            .style(style -> {
                                    style.put("width", scalingFactor * 512 + "px");
                                    style.put("height", scalingFactor * 512 + "px");
                                    style.put("position", "absolute");
                                    style.put("top", scalingFactor * top + "px");
                                    style.put("left", scalingFactor * left + "px");
                                    style.put("image-rendering", "pixelated");
                                    style.put("user-select", "none");
                                });
                    });
            }
        }
        final String chatBoxHeight = "200px";
        final var chatBox = mapFrame.addElement("div");
        chatBox.setId("chat_box")
            .setClassName("minecraft-chat")
            .style(style -> {
                    style.put("position", "fixed");
                    style.put("overflow-x", "none");
                    style.put("overflow-y", "scroll");
                    style.put("background-color", "rgba(0, 0, 0, 0.5)");
                    style.put("margin", "0");
                    style.put("padding", "20px");
                    style.put("border", "0");
                    style.put("border-radius", "8px");
                    style.put("width", "auto");
                    style.put("height", chatBoxHeight);
                    style.put("max-height", chatBoxHeight);
                    style.put("bottom", "30px");
                    style.put("left", "20px");
                    style.put("right", "30px");
                });
        for (Map.Entry<UUID, PlayerLocationTag> entry : sessionData.getPlayerLocationTags().entrySet()) {
            final UUID uuid = entry.getKey();
            final PlayerLocationTag tag = entry.getValue();
            if (!sessionData.isInWorld(tag)) continue;
            mapFrame.addElement("img", img -> {
                    img.setClassName("live-player");
                    img.setAttribute("src", "/skin/face/" + uuid + ".png");
                    img.style(style -> {
                            final int left = tag.getX() - (minRegionX << 9) - 8;
                            final int top = tag.getZ() - (minRegionZ << 9) - 8;
                            style.put("width", scalingFactor * 16 + "px");
                            style.put("height", scalingFactor * 16 + "px");
                            style.put("position", "absolute");
                            style.put("top", scalingFactor * top + "px");
                            style.put("left", scalingFactor * left + "px");
                            style.put("z-index", "100");
                            style.put("image-rendering", "pixelated");
                            style.put("user-select", "none");
                            style.put("outline", "2px solid white");
                        });
                });
        }
        session.getResponse().setStatus(HttpResponseStatus.OK);
        session.setReceiveChatMessages(true);
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
        if (!worldFileCache.getEffectiveWorldBorder().containsRegion(x, z) || !sendFile.exists()) {
            session.getResponse().setContentProvider(emptyRegionPngProvider);
        } else {
            session.getResponse().setContentProvider(new FileContentProvider(HttpContentType.IMAGE_PNG, sendFile));
        }
        session.getResponse().setStatus(HttpResponseStatus.OK);
        session.send();
    }

    @Override
    public void tick(ContentDeliverySession session) {
        final MagicMapContentDeliverySessionData data = (MagicMapContentDeliverySessionData) session.getContentDeliverySessionData();
        if (data == null) return;
        // Prune our map
        for (Iterator<Map.Entry<UUID, PlayerLocationTag>> iter = data.getPlayerLocationTags().entrySet().iterator(); iter.hasNext();) {
            Map.Entry<UUID, PlayerLocationTag> entry = iter.next();
            final UUID uuid = entry.getKey();
            if (plugin().getWebserverManager().getPlayerLocationTags().containsKey(uuid)) continue;
            final PlayerLocationTag tag = entry.getValue();
            iter.remove();
            if (data.isInWorld(tag)) {
                session.sendMessage(new PlayerRemoveMessage(uuid));
            }
        }
        // Update
        for (Map.Entry<UUID, PlayerLocationTag> entry : plugin().getWebserverManager().getPlayerLocationTags().entrySet()) {
            final UUID uuid = entry.getKey();
            final PlayerLocationTag tag = entry.getValue();
            final PlayerLocationTag old = data.getPlayerLocationTags().get(uuid);
            if (old == null) {
                data.getPlayerLocationTags().put(uuid, tag.clone());
                if (data.isInWorld(tag)) {
                    session.sendMessage(new PlayerAddMessage(PlayerCache.forUuid(uuid), tag.getX(), tag.getZ()));
                }
            } else if (!old.equals(tag)) {
                data.getPlayerLocationTags().put(uuid, tag.clone());
                final boolean oldIsInWorld = data.isInWorld(old);
                final boolean newIsInWorld = data.isInWorld(tag);
                if (!oldIsInWorld && newIsInWorld) {
                    session.sendMessage(new PlayerAddMessage(PlayerCache.forUuid(uuid), tag.getX(), tag.getZ()));
                } else if (oldIsInWorld && !newIsInWorld) {
                    session.sendMessage(new PlayerRemoveMessage(uuid));
                } else if (oldIsInWorld && newIsInWorld) {
                    session.sendMessage(new PlayerUpdateMessage(uuid, tag.getX(), tag.getZ()));
                }
            }
        }
    }

    @Override
    public void onWebsocketReceiveText(String text) {
    }
}
