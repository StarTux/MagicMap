package com.cavetale.magicmap.webserver;

import com.cavetale.core.connect.Connect;
import com.cavetale.core.connect.NetworkServer;
import com.cavetale.core.playercache.PlayerCache;
import com.cavetale.core.util.Json;
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
import com.cavetale.webserver.html.HtmlNodeList;
import com.cavetale.webserver.html.SimpleHtmlElement;
import com.cavetale.webserver.http.HttpContentType;
import com.cavetale.webserver.http.HttpResponseStatus;
import com.cavetale.webserver.http.StaticContentProvider;
import com.cavetale.webserver.message.ChatClientMessage;
import com.cavetale.webserver.message.EvalClientMessage;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
public final class MagicMapContentDelivery implements ContentDelivery {
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
        sessionData.setMapName(mapName);
        sessionData.setWorldFileCache(worldFileCache);
        for (Map.Entry<UUID, PlayerLocationTag> entry : plugin().getWebserverManager().getPlayerLocationTags().entrySet()) {
            sessionData.getPlayerLocationTags().put(entry.getKey(), entry.getValue().clone());
        }
        sessionData.setPlayerList(fetchPlayerList());
        session.setContentDeliverySessionData(sessionData);
        final CachedHtmlContentProvider provider = new CachedHtmlContentProvider();
        session.getResponse().setContentProvider(provider);
        session.attachWebsocketScript(provider.getDocument());
        DefaultStyleSheet.install(provider.getDocument());
        MagicMapStyleSheet.install(provider.getDocument());
        MagicMapScript.install(provider.getDocument(), mapName, worldBorder, scalingFactor);
        provider.getDocument().getHead().addElement("title", t -> t.addText(worldFileCache.getDisplayName() + " - Magic Map"));
        // Map Frame
        final var mapFrame = provider.getDocument().getBody().addElement("div");
        mapFrame.setId("map-frame");
        mapFrame.setClassName("map-frame");
        // Chat Box
        final var chatBox = provider.getDocument().getBody().addElement("div");
        chatBox.setId("chat-box");
        chatBox.setClassName("minecraft-chat chat-box");
        // Player List
        final var playerListBox = provider.getDocument().getBody().addElement("div");
        playerListBox.setId("player-list");
        playerListBox.setClassName("player-list");
        for (var node : makePlayerList(sessionData.getPlayerList())) {
            playerListBox.addChild(node);
        }
        // Regions and Live Players
        for (var node : makeMapFrameContent(sessionData)) {
            mapFrame.addChild(node);
        }
        session.getResponse().setStatus(HttpResponseStatus.OK);
        session.setReceiveChatMessages(true);
        session.send();
    }

    public HtmlNodeList makeMapFrameContent(MagicMapContentDeliverySessionData sessionData) {
        HtmlNodeList result = new HtmlNodeList();
        final var worldBorder = sessionData.getWorldFileCache().getEffectiveWorldBorder();
        final int minRegionX = worldBorder.getMinX() >> 9;
        final int maxRegionX = worldBorder.getMaxX() >> 9;
        final int minRegionZ = worldBorder.getMinZ() >> 9;
        final int maxRegionZ = worldBorder.getMaxZ() >> 9;
        for (int rz = minRegionZ; rz <= maxRegionZ; rz += 1) {
            for (int rx = minRegionX; rx <= maxRegionX; rx += 1) {
                final int left = (rx - minRegionX) * 512;
                final int top = (rz - minRegionZ) * 512;
                final String id = "r." + rx + "." + rz;
                final var mapRegion = new SimpleHtmlElement("img");
                mapRegion.setId(id)
                    .setClassName("map-region")
                    .setAttribute("draggable", "false")
                    .style(style -> {
                            style.put("width", scalingFactor * 512 + "px");
                            style.put("height", scalingFactor * 512 + "px");
                            style.put("top", scalingFactor * top + "px");
                            style.put("left", scalingFactor * left + "px");
                        });
                result.add(mapRegion);
            }
        }
        // Players
        for (Map.Entry<UUID, PlayerLocationTag> entry : sessionData.getPlayerLocationTags().entrySet()) {
            final UUID uuid = entry.getKey();
            final PlayerLocationTag tag = entry.getValue();
            if (!sessionData.isInWorld(tag)) continue;
            final var livePlayer = new SimpleHtmlElement("img");
            livePlayer.setClassName("live-player");
            livePlayer.setAttribute("src", "/skin/face/" + uuid + ".png");
            livePlayer.style(style -> {
                    final int left = tag.getX() - (minRegionX << 9) - 8;
                    final int top = tag.getZ() - (minRegionZ << 9) - 8;
                    style.put("width", scalingFactor * 16 + "px");
                    style.put("height", scalingFactor * 16 + "px");
                    style.put("top", scalingFactor * top + "px");
                    style.put("left", scalingFactor * left + "px");
                });
            result.add(livePlayer);
        }
        return result;
    }

    public List<PlayerCache> fetchPlayerList() {
        final List<PlayerCache> result = new ArrayList<>();
        for (UUID online : Connect.get().getOnlinePlayers()) {
            result.add(PlayerCache.forUuid(online));
        }
        Collections.sort(result, Comparator.comparing(PlayerCache::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public HtmlNodeList makePlayerList(List<PlayerCache> playerList) {
        final HtmlNodeList result = new HtmlNodeList();
        for (PlayerCache player : playerList) {
            final SimpleHtmlElement playerDiv = new SimpleHtmlElement("div");
            playerDiv.setClassName("player-list-box");
            playerDiv.addElement("img", img -> {
                    img.setClassName("player-list-face");
                    img.setAttribute("src", "/skin/face/" + player.uuid + ".png");
                    img.setAttribute("onclick", "onClickPlayerList('" + player.uuid + "')");
                });
            playerDiv.addElement("div", nameDiv -> {
                    nameDiv.addText(player.name);
                    nameDiv.setClassName("minecraft-chat player-list-name");
                });
            result.add(playerDiv);
        }
        return result;
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
        final MagicMapContentDeliverySessionData sessionData = (MagicMapContentDeliverySessionData) session.getContentDeliverySessionData();
        if (sessionData == null) return;
        // Prune our map
        for (Iterator<Map.Entry<UUID, PlayerLocationTag>> iter = sessionData.getPlayerLocationTags().entrySet().iterator(); iter.hasNext();) {
            Map.Entry<UUID, PlayerLocationTag> entry = iter.next();
            final UUID uuid = entry.getKey();
            if (plugin().getWebserverManager().getPlayerLocationTags().containsKey(uuid)) continue;
            final PlayerLocationTag tag = entry.getValue();
            iter.remove();
            if (sessionData.isInWorld(tag)) {
                session.sendMessage(new PlayerRemoveMessage(uuid));
            }
        }
        // Update
        for (Map.Entry<UUID, PlayerLocationTag> entry : plugin().getWebserverManager().getPlayerLocationTags().entrySet()) {
            final UUID uuid = entry.getKey();
            final PlayerLocationTag tag = entry.getValue();
            final PlayerLocationTag old = sessionData.getPlayerLocationTags().get(uuid);
            if (old == null) {
                sessionData.getPlayerLocationTags().put(uuid, tag.clone());
                if (sessionData.isInWorld(tag)) {
                    session.sendMessage(new PlayerAddMessage(PlayerCache.forUuid(uuid), tag.getX(), tag.getZ()));
                }
            } else if (!old.equals(tag)) {
                sessionData.getPlayerLocationTags().put(uuid, tag.clone());
                final boolean oldIsInWorld = sessionData.isInWorld(old);
                final boolean newIsInWorld = sessionData.isInWorld(tag);
                if (!oldIsInWorld && newIsInWorld) {
                    session.sendMessage(new PlayerAddMessage(PlayerCache.forUuid(uuid), tag.getX(), tag.getZ()));
                } else if (oldIsInWorld && !newIsInWorld) {
                    session.sendMessage(new PlayerRemoveMessage(uuid));
                } else if (oldIsInWorld && newIsInWorld) {
                    session.sendMessage(new PlayerUpdateMessage(uuid, tag.getX(), tag.getZ()));
                }
            }
        }
        // Update player list
    }

    private void updatePlayerList(ContentDeliverySession session, MagicMapContentDeliverySessionData sessionData) {
        session.sendMessage(new EvalClientMessage("document.getElementById('player-list').innerHTML = '" + makePlayerList(sessionData.getPlayerList()).writeToJavaScriptString() + "'"));
    }

    @Override
    public void onPlayerJoin(ContentDeliverySession session, PlayerCache player) {
        final MagicMapContentDeliverySessionData sessionData = (MagicMapContentDeliverySessionData) session.getContentDeliverySessionData();
        if (sessionData == null) return;
        final String raw = player.name + " joined";
        session.sendMessage(new ChatClientMessage(new PlayerCache(new UUID(0L, 0L), "Console"),
                                                  raw,
                                                  new SimpleHtmlElement("span").addText(raw).setAttribute("style", "color: #55ff55").writeToString()));
        sessionData.getPlayerList().add(player);
        Collections.sort(sessionData.getPlayerList(), Comparator.comparing(PlayerCache::getName, String.CASE_INSENSITIVE_ORDER));
        updatePlayerList(session, sessionData);
    }

    @Override
    public void onPlayerQuit(ContentDeliverySession session, PlayerCache player) {
        final MagicMapContentDeliverySessionData sessionData = (MagicMapContentDeliverySessionData) session.getContentDeliverySessionData();
        if (sessionData == null) return;
        final String raw = player.name + " disconnected";
        session.sendMessage(new ChatClientMessage(new PlayerCache(new UUID(0L, 0L), "Console"),
                                                  raw,
                                                  new SimpleHtmlElement("span").addText(raw).setAttribute("style", "color: #55ffff").writeToString()));
        sessionData.getPlayerList().removeIf(p -> p.uuid.equals(player.uuid));
        updatePlayerList(session, sessionData);
    }

    @Override
    public void onWebsocketReceiveText(ContentDeliverySession session, String text) {
        final MagicMapContentDeliverySessionData sessionData = (MagicMapContentDeliverySessionData) session.getContentDeliverySessionData();
        if (sessionData == null) return;
        final Map<?, ?> map = Json.deserialize(text, Map.class);
        if (map == null) return;
        if (map.get("id") == null) return;
        final String id = map.get("id").toString();
        switch (id) {
        case "click_player_list": {
            if (map.get("value") == null) return;
            final String value = map.get("value").toString();
            final UUID uuid;
            try {
                uuid = UUID.fromString(value);
            } catch (IllegalArgumentException iae) {
                return;
            }
            final PlayerLocationTag tag = sessionData.getPlayerLocationTags().get(uuid);
            if (tag == null) return;
            if (sessionData.isInWorld(tag)) {
                session.sendMessage(new ScrollMapMessage(tag.getX(), tag.getZ()));
            } else {
                if (changeMap(session, sessionData, tag.getServer(), tag.getWorld())) {
                    session.sendMessage(new ScrollMapMessage(tag.getX(), tag.getZ()));
                }
            }
            break;
        }
        default: break;
        }
    }

    private boolean changeMap(ContentDeliverySession session, MagicMapContentDeliverySessionData sessionData, NetworkServer server, String worldName) {
        String mapName = null;
        WorldFileCache worldFileCache = null;
        for (Map.Entry<String, WorldFileCache> entry : worldMap.entrySet()) {
            final WorldFileCache it = entry.getValue();
            if (it.getServer() == server && it.getName().equals(worldName)) {
                mapName = entry.getKey();
                worldFileCache = it;
                break;
            }
        }
        if (worldFileCache == null) return false;
        sessionData.setMapName(mapName);
        sessionData.setWorldFileCache(worldFileCache);
        final String innerHtml = makeMapFrameContent(sessionData).writeToString();
        session.sendMessage(new ChangeMapMessage(mapName, worldFileCache.getDisplayName() + " - Magic Map",
                                                 worldFileCache.getEffectiveWorldBorder(), innerHtml));
        return true;
    }
}
