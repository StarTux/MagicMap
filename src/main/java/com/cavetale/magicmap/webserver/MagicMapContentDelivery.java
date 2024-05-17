package com.cavetale.magicmap.webserver;

import com.cavetale.core.chat.ChannelChatEvent;
import com.cavetale.core.chat.Chat;
import com.cavetale.core.connect.Connect;
import com.cavetale.core.connect.NetworkServer;
import com.cavetale.core.playercache.PlayerCache;
import com.cavetale.home.Claim;
import com.cavetale.home.HomePlugin;
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
import com.cavetale.webserver.message.AddHtmlElementsMessage;
import com.cavetale.webserver.message.ChatClientMessage;
import com.cavetale.webserver.message.RemoveHtmlElementMessage;
import com.cavetale.webserver.message.ServerMessage;
import com.cavetale.webserver.message.SetInnerHtmlMessage;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.bukkit.World.Environment;
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
        session.setContentDeliverySessionData(sessionData);
        Chat.getChannelLog("global", Instant.now().minus(6, ChronoUnit.HOURS), list -> chatHistoryCallback(session, list));
    }

    private void chatHistoryCallback(ContentDeliverySession session, List<ChannelChatEvent> chatHistory) {
        final MagicMapContentDeliverySessionData sessionData = (MagicMapContentDeliverySessionData) session.getContentDeliverySessionData();
        for (Map.Entry<UUID, PlayerLocationTag> entry : plugin().getWebserverManager().getPlayerLocationTags().entrySet()) {
            sessionData.getPlayerLocationTags().put(entry.getKey(), entry.getValue().clone());
        }
        sessionData.setPlayerList(fetchPlayerList());
        final CachedHtmlContentProvider provider = new CachedHtmlContentProvider();
        session.getResponse().setContentProvider(provider);
        session.attachWebsocketScript(provider.getDocument());
        DefaultStyleSheet.install(provider.getDocument());
        MagicMapStyleSheet.install(provider.getDocument());
        MagicMapScript.install(provider.getDocument(), sessionData.getMapName(), sessionData.getWorldFileCache().getEffectiveWorldBorder(), scalingFactor);
        provider.getDocument().getHead().addElement("title", t -> t.addText(sessionData.getWorldFileCache().getDisplayName() + " - Magic Map"));
        // Map Frame
        final var mapFrame = provider.getDocument().getBody().addElement("div");
        mapFrame.setId("map-frame");
        mapFrame.setClassName("map-frame");
        if (sessionData.getWorldFileCache().getTag().getEnvironment() == Environment.NETHER) {
            mapFrame.classNames(list -> list.add("map-nether"));
        } else if (sessionData.getWorldFileCache().getTag().getEnvironment() == Environment.THE_END) {
            mapFrame.classNames(list -> list.add("map-the-end"));
        }
        // Chat Box
        final var chatBox = provider.getDocument().getBody().addElement("div");
        chatBox.setId("chat-box");
        chatBox.setClassNames(List.of("minecraft-chat", "chat-box"));
        for (ChannelChatEvent event : chatHistory) {
            final PlayerCache sender = event.getSender() != null
                ? PlayerCache.forUuid(event.getSender())
                : new PlayerCache(new UUID(0L, 0L), "Console");
            chatBox.addElement("p", p -> p
                               .setClassName("minecraft-chat-line")
                               .addElement("img", img -> {
                                       img.setClassName("minecraft-chat-emoji");
                                       img.setAttribute("src", "/skin/face/" + sender.uuid + ".png");
                                   })
                               .addElement("span", span -> span.addText(sender.name + ": "))
                               .addElement("span", span -> span.addText(event.getRawMessage())));
        }
        // Player List
        final var playerListBox = provider.getDocument().getBody().addElement("div");
        playerListBox.setId("player-list");
        playerListBox.setClassName("player-list");
        for (var node : makePlayerList(sessionData.getPlayerList())) {
            playerListBox.addChild(node);
        }
        // Regions and Live Players
        for (var node : makeRegionElements(sessionData)) {
            mapFrame.addChild(node);
        }
        for (var node : makeLivePlayerElements(sessionData)) {
            mapFrame.addChild(node);
        }
        for (var node : makeClaimElements(sessionData)) {
            mapFrame.addChild(node);
        }
        session.getResponse().setStatus(HttpResponseStatus.OK);
        session.setReceiveChatMessages(true);
        session.send();
    }

    public HtmlNodeList makeRegionElements(MagicMapContentDeliverySessionData sessionData) {
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
                final var mapRegion = new SimpleHtmlElement("img");
                mapRegion.setId("region-" + rx + "-" + rz);
                mapRegion.setClassName("map-region");
                mapRegion.setAttribute("draggable", "false");
                mapRegion.style(style -> {
                        style.put("width", scalingFactor * 512 + "px");
                        style.put("height", scalingFactor * 512 + "px");
                        style.put("top", scalingFactor * top + "px");
                        style.put("left", scalingFactor * left + "px");
                    });
                result.add(mapRegion);
            }
        }
        return result;
    }

    public SimpleHtmlElement makeLivePlayer(UUID uuid, PlayerLocationTag tag, WorldBorderCache worldBorder) {
        final var livePlayer = new SimpleHtmlElement("img");
        livePlayer.setId("live-player-" + uuid);
        livePlayer.setClassName("live-player");
        livePlayer.setAttribute("src", "/skin/face/" + uuid + ".png");
        livePlayer.setAttribute("data-uuid", "" + uuid);
        livePlayer.setAttribute("data-name", PlayerCache.nameForUuid(uuid));
        livePlayer.setAttribute("onclick", "onClickLivePlayer(this, event)");
        livePlayer.style(style -> {
                final int minX = (worldBorder.getMinX() >> 9) << 9;
                final int minZ = (worldBorder.getMinZ() >> 9) << 9;
                final int left = tag.getX() - minX - 8;
                final int top = tag.getZ() - minZ - 8;
                style.put("width", scalingFactor * 16 + "px");
                style.put("height", scalingFactor * 16 + "px");
                style.put("top", scalingFactor * top + "px");
                style.put("left", scalingFactor * left + "px");
            });
        return livePlayer;
    }

    public HtmlNodeList makeLivePlayerElements(MagicMapContentDeliverySessionData sessionData) {
        HtmlNodeList result = new HtmlNodeList();
        final var worldBorder = sessionData.getWorldFileCache().getEffectiveWorldBorder();
        for (Map.Entry<UUID, PlayerLocationTag> entry : sessionData.getPlayerLocationTags().entrySet()) {
            final UUID uuid = entry.getKey();
            final PlayerLocationTag tag = entry.getValue();
            if (!sessionData.isInWorld(tag)) continue;
            result.add(makeLivePlayer(uuid, tag, worldBorder));
        }
        return result;
    }

    public HtmlNodeList makeClaimElements(MagicMapContentDeliverySessionData sessionData) {
        HtmlNodeList result = new HtmlNodeList();
        if (!HomePlugin.getInstance().isHomeWorld(sessionData.getWorldFileCache().getServer(), sessionData.getWorldFileCache().getName())) return result;
        final var worldBorder = sessionData.getWorldFileCache().getEffectiveWorldBorder();
        final int minX = (worldBorder.getMinX() >> 9) << 9;
        final int minZ = (worldBorder.getMinZ() >> 9) << 9;
        for (Claim claim : HomePlugin.getInstance().getClaimCache().getAllClaims()) {
            if (!claim.getWorld().equals(sessionData.getWorldFileCache().getName())) continue;
            final var claimRect = new SimpleHtmlElement("div");
            claimRect.setId("claim-" + claim.getId());
            claimRect.setClassName("live-claim");
            claimRect.setAttribute("data-claim-id", "" + claim.getId());
            claimRect.style(style -> {
                    final var area = claim.getArea();
                    final int left = area.getAx() - minX;
                    final int top = area.getAy() - minZ;
                    style.put("width", scalingFactor * area.width() + "px");
                    style.put("height", scalingFactor * area.height() + "px");
                    style.put("top", scalingFactor * top + "px");
                    style.put("left", scalingFactor * left + "px");
                });
            claimRect.setAttribute("onclick", "onClickClaim(this, event)");
            result.add(claimRect);
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
                    img.setAttribute("data-uuid", "" + player.uuid);
                    img.setAttribute("data-name", player.name);
                    img.setAttribute("onclick", "onClickPlayerList(this, event)");
                });
            playerDiv.addElement("div", nameDiv -> {
                    nameDiv.addText(player.name);
                    nameDiv.setClassNames(List.of("minecraft-chat", "player-list-name"));
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
                session.sendMessage(new RemoveHtmlElementMessage("live-player-" + uuid));
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
                    session.sendMessage(new AddHtmlElementsMessage("map-frame",
                                                                   makeLivePlayer(uuid, tag, sessionData.getWorldFileCache().getEffectiveWorldBorder())));
                }
            } else if (!old.equals(tag)) {
                sessionData.getPlayerLocationTags().put(uuid, tag.clone());
                final boolean oldIsInWorld = sessionData.isInWorld(old);
                final boolean newIsInWorld = sessionData.isInWorld(tag);
                if (!oldIsInWorld && newIsInWorld) {
                    session.sendMessage(new AddHtmlElementsMessage("map-frame",
                                                                   makeLivePlayer(uuid, tag, sessionData.getWorldFileCache().getEffectiveWorldBorder())));
                } else if (oldIsInWorld && !newIsInWorld) {
                    session.sendMessage(new RemoveHtmlElementMessage("live-player-" + uuid));
                } else if (oldIsInWorld && newIsInWorld) {
                    session.sendMessage(new PlayerUpdateMessage(uuid, tag.getX(), tag.getZ()));
                }
            }
        }
        // Update player list
    }

    private void updatePlayerList(ContentDeliverySession session, MagicMapContentDeliverySessionData sessionData) {
        session.sendMessage(new SetInnerHtmlMessage("player-list",
                                                    makePlayerList(sessionData.getPlayerList()).writeToString()));
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
    public void onReceiveMessage(ContentDeliverySession session, ServerMessage message) {
        final MagicMapContentDeliverySessionData sessionData = (MagicMapContentDeliverySessionData) session.getContentDeliverySessionData();
        if (sessionData == null) return;
        switch (message.getId()) {
        case "magicmap:did_change_map":
            sessionData.setLoadingMap(false);
            break;
        case "magicmap:click_player_list": {
            if (message.getValue() == null) return;
            final UUID uuid;
            try {
                uuid = UUID.fromString(message.getValue());
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
        case "magicmap:click_claim": {
            if (message.getValue() == null) return;
            final int claimId;
            try {
                claimId = Integer.parseInt(message.getValue());
            } catch (IllegalArgumentException iae) {
                return;
            }
            final Claim claim = HomePlugin.getInstance().getClaimById(claimId);
            if (claim == null || !claim.getWorld().equals(sessionData.getWorldFileCache().getName())) return;
            final var tooltip = new SimpleHtmlElement("div");
            tooltip.setId("magicmap-tooltip");
            tooltip.setClassNames(List.of("minecraft-chat", "magicmap-tooltip"));
            tooltip.addElement("p", p -> {
                    p.setClassName("minecraft-chat-line");
                    p.addElement("span", span -> {
                            span.style(style -> style.put("color", "#aaaaaa"));
                            span.addText("Claim Owner ");
                        });
                    p.addElement("span", span -> span.addText(claim.getOwnerName()));
                });
            tooltip.addElement("p", p -> {
                    p.setClassName("minecraft-chat-line");
                    p.addElement("span", span -> {
                            span.style(style -> style.put("color", "#aaaaaa"));
                            span.addText("Size ");
                        });
                    p.addElement("span", span -> span.addText("" + claim.getArea().width()));
                    p.addElement("span", span -> {
                            span.style(style -> style.put("color", "#ffffff"));
                            span.addText("\u00D7");
                        });
                    p.addElement("span", span -> span.addText("" + claim.getArea().height()));
                });
            session.sendMessage(new ShowTooltipMessage(tooltip.writeToString()));
            break;
        }
        case "magicmap:click_live_player": {
            if (message.getValue() == null) return;
            final UUID uuid;
            try {
                uuid = UUID.fromString(message.getValue());
            } catch (IllegalArgumentException iae) {
                return;
            }
            final var tooltip = new SimpleHtmlElement("div");
            tooltip.setId("magicmap-tooltip");
            tooltip.setClassNames(List.of("minecraft-chat", "magicmap-tooltip"));
            tooltip.addElement("p", p -> {
                    p.setClassName("minecraft-chat-line");
                    p.addElement("span", span -> span.addText(PlayerCache.nameForUuid(uuid)));
                });
            session.sendMessage(new ShowTooltipMessage(tooltip.writeToString()));
            break;
        }
        default: break;
        }
    }

    private boolean changeMap(ContentDeliverySession session, MagicMapContentDeliverySessionData sessionData, NetworkServer server, String worldName) {
        if (sessionData.isLoadingMap()) return false;
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
        sessionData.setLoadingMap(true);
        sessionData.setMapName(mapName);
        sessionData.setWorldFileCache(worldFileCache);
        final String innerHtml = makeRegionElements(sessionData).writeToString()
            + makeLivePlayerElements(sessionData).writeToString()
            + makeClaimElements(sessionData).writeToString();
        session.sendMessage(new ChangeMapMessage(mapName, worldFileCache.getDisplayName() + " - Magic Map",
                                                 worldFileCache.getEffectiveWorldBorder(), innerHtml,
                                                 worldFileCache.getTag().getEnvironment().toString()));
        return true;
    }
}
