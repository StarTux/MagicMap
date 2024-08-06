package com.cavetale.magicmap.webserver;

import com.cavetale.core.chat.ChannelChatEvent;
import com.cavetale.core.chat.Chat;
import com.cavetale.core.connect.Connect;
import com.cavetale.core.connect.NetworkServer;
import com.cavetale.core.connect.ServerCategory;
import com.cavetale.core.connect.ServerGroup;
import com.cavetale.core.playercache.PlayerCache;
import com.cavetale.home.Claim;
import com.cavetale.home.HomePlugin;
import com.cavetale.magicmap.PlayerLocationTag;
import com.cavetale.magicmap.RenderType;
import com.cavetale.magicmap.file.WorldFileCache;
import com.cavetale.magicmap.file.WorldRenderCache;
import com.cavetale.webserver.content.ContentDelivery;
import com.cavetale.webserver.content.ContentDeliverySession;
import com.cavetale.webserver.content.ContentDeliveryState;
import com.cavetale.webserver.content.FileContentProvider;
import com.cavetale.webserver.html.CachedHtmlContentProvider;
import com.cavetale.webserver.html.DefaultStyleSheet;
import com.cavetale.webserver.html.SimpleHtmlElement;
import com.cavetale.webserver.http.HttpContentType;
import com.cavetale.webserver.http.HttpResponseStatus;
import com.cavetale.webserver.http.StaticContentProvider;
import com.cavetale.webserver.message.ChatClientMessage;
import com.cavetale.webserver.message.RemoveHtmlElementMessage;
import com.cavetale.webserver.message.ServerMessage;
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
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

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
            // Show the beta server only on the test web server
            if (server.group != ServerGroup.MAIN && NetworkServer.current() != NetworkServer.BETA) continue;
            // Exclude some categories
            if (server.category == ServerCategory.TECHNICAL) continue;
            if (server.category == ServerCategory.UNKNOWN) continue;
            if (server.category == ServerCategory.WORLD_GENERATION) continue;
            if (server.category == ServerCategory.MINIGAME) continue;
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
            case BETA: {
                final String folderName = worldFolder.getFileName().toString();
                mapName = folderName.startsWith("world")
                    ? folderName.replace("world", "beta")
                    : "beta." + folderName;
                break;
            }
            case HUB:
            case MINE:
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

    private void sendMapHtml(ContentDeliverySession session, String mapName) {
        final WorldFileCache worldFileCache = worldMap.get(mapName);
        if (worldFileCache == null) {
            plugin().getLogger().warning("World not found: " + mapName);
            session.send(); // 404
            return;
        }
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
        Chat.getChannelLog("global", Instant.now().minus(12, ChronoUnit.HOURS), list -> chatHistoryCallback(session, list));
    }

    private void chatHistoryCallback(ContentDeliverySession session, List<ChannelChatEvent> chatHistory) {
        final MagicMapContentDeliverySessionData sessionData = (MagicMapContentDeliverySessionData) session.getContentDeliverySessionData();
        final CachedHtmlContentProvider provider = new CachedHtmlContentProvider();
        session.getResponse().setContentProvider(provider);
        session.attachWebsocketScript(provider.getDocument());
        DefaultStyleSheet.install(provider.getDocument());
        MagicMapStyleSheet.install(provider.getDocument());
        MagicMapScript.install(provider.getDocument(), sessionData.getMapName(), sessionData.getWorldFileCache().getEffectiveWorldBorder());
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
        for (int chatIndex = Math.max(0, chatHistory.size() - 40); chatIndex < chatHistory.size(); chatIndex += 1) {
            final ChannelChatEvent channelChatEvent = chatHistory.get(chatIndex);
            chatBox.addElement("p", p -> {
                    p.setClassName("minecraft-chat-line");
                    for (var it : ChatClientMessage.eventToHtml(channelChatEvent)) {
                        p.addChild(it);
                    }
                });
        }
        // Player List
        final var playerListBox = provider.getDocument().getBody().addElement("div");
        playerListBox.setId("player-list");
        playerListBox.setClassName("player-list");
        // World Select
        final var worldSelectBox = provider.getDocument().getBody().addElement("select");
        worldSelectBox.setId("world-select");
        worldSelectBox.setClassName("world-select");
        List<String> mapKeys = new ArrayList<>(worldMap.keySet());
        Collections.sort(mapKeys, Comparator.comparing(key -> worldMap.get(key).getServer().ordinal())
                         .thenComparing(key -> worldMap.get(key).getTag().getEnvironment().ordinal())
                         .thenComparing(key -> worldMap.get(key).getDisplayName(), String.CASE_INSENSITIVE_ORDER));
        for (String key : mapKeys) {
            final WorldFileCache worldFileCache = worldMap.get(key);
            switch (worldFileCache.getServer()) {
            case BETA:
            case CREATIVE:
                continue;
            default: break;
            }
            worldSelectBox.addElement("option", option -> {
                    option.setAttribute("value", key);
                    option.addText(worldFileCache.getDisplayName());
                    if (key.equals(sessionData.getMapName())) {
                        option.setAttribute("selected", "selected");
                    }
                });
        }
        // Hide UI
        final var hideUiButton = provider.getDocument().getBody().addElement("div");
        hideUiButton.addText("ON");
        hideUiButton.setId("hide-ui");
        hideUiButton.setClassName("hide-ui minecraft-chat");
        hideUiButton.setAttribute("title", "Toggle UI");
        // OK
        session.getResponse().setStatus(HttpResponseStatus.OK);
        session.setReceiveChatMessages(true);
        session.send();
    }

    public List<PlayerCache> fetchPlayerList() {
        final List<PlayerCache> result = new ArrayList<>();
        for (UUID online : Connect.get().getOnlinePlayers()) {
            result.add(PlayerCache.forUuid(online));
        }
        return result;
    }

    private void sendRegionFile(ContentDeliverySession session, String mapName, String fileName) {
        final WorldFileCache worldFileCache = worldMap.get(mapName);
        if (worldFileCache == null) {
            plugin().getLogger().warning("World not found: " + mapName);
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
        if (session.getState() != ContentDeliveryState.WEBSOCKET_CONNECTED) return;
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
        // Update Live Players
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
                    session.sendMessage(new RemoveHtmlElementMessage("live-player-" + uuid));
                } else if (oldIsInWorld && newIsInWorld) {
                    session.sendMessage(new PlayerUpdateMessage(uuid, tag.getX(), tag.getZ()));
                }
            }
        }
        // Update Player List
        boolean doUpdatePlayerList = false;
        final List<PlayerCache> playerList = fetchPlayerList();
        for (PlayerCache it : playerList) {
            if (!sessionData.getPlayerList().contains(it)) {
                sessionData.getPlayerList().add(it);
                doUpdatePlayerList = true;
            }
        }
        for (Iterator<PlayerCache> iter = sessionData.getPlayerList().iterator(); iter.hasNext();) {
            final PlayerCache it = iter.next();
            if (!playerList.contains(it)) {
                final int ticks = sessionData.getMissingPlayers().getOrDefault(it.uuid, 0);
                if (ticks > 20) {
                    iter.remove();
                    sessionData.getMissingPlayers().remove(it.uuid);
                    doUpdatePlayerList = true;
                } else {
                    sessionData.getMissingPlayers().put(it.uuid, ticks + 1);
                }
            } else {
                sessionData.getMissingPlayers().remove(it.uuid);
            }
        }
        if (doUpdatePlayerList) {
            updatePlayerList(session, sessionData);
        }
        // Update Claims
        if (sessionData.isSendAllClaims()) {
            sessionData.setSendAllClaims(false);
            for (Claim claim : HomePlugin.getInstance().getClaimCache().getAllClaims()) {
                if (!claim.getWorld().equals(sessionData.getWorldFileCache().getName())) continue;
                final String claimName = claim.getName() != null
                    ? claim.getName()
                    : claim.getOwnerName();
                session.sendMessage(new ClaimUpdateMessage(claim.getId(), claim.getArea(), claimName));
            }
        }
    }

    private void updatePlayerList(ContentDeliverySession session, MagicMapContentDeliverySessionData sessionData) {
        Collections.sort(sessionData.getPlayerList(), Comparator.comparing(PlayerCache::getName, String.CASE_INSENSITIVE_ORDER));
        session.sendMessage(new PlayerListMessage(sessionData.getPlayerList()));
    }

    @Override
    public void onReceiveMessage(ContentDeliverySession session, ServerMessage message) {
        final MagicMapContentDeliverySessionData sessionData = (MagicMapContentDeliverySessionData) session.getContentDeliverySessionData();
        if (sessionData == null) return;
        switch (message.getId()) {
        case "magicmap:did_change_map":
            sessionData.setLoadingMap(false);
            sessionData.getPlayerLocationTags().clear();
            sessionData.setSendAllClaims(true);
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
            if (tag == null) {
                session.sendChatMessage(text(PlayerCache.nameForUuid(uuid) + " is in an unmapped world", DARK_RED));
                return;
            }
            if (sessionData.isInWorld(tag)) {
                session.sendChatMessage(text("Jumping to " + PlayerCache.nameForUuid(uuid), GREEN));
                session.sendMessage(new ScrollMapMessage(tag.getX(), tag.getZ()));
            } else {
                changeMap(session, sessionData, tag.getServer(), tag.getWorld(), tag.getX(), tag.getZ(), uuid);
            }
            break;
        }
        case "magicmap:select_world": {
            if (message.getValue() == null) return;
            final WorldFileCache worldFileCache = worldMap.get(message.getValue());
            if (worldFileCache == null) return;
            changeMap(session, sessionData, worldFileCache.getServer(), worldFileCache.getName(),
                      worldFileCache.getEffectiveWorldBorder().getCenterX(),
                      worldFileCache.getEffectiveWorldBorder().getCenterZ(),
                      null);
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

    private boolean changeMap(ContentDeliverySession session, MagicMapContentDeliverySessionData sessionData, NetworkServer server, String worldName, int x, int z, UUID uuid) {
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
        if (worldFileCache == null) {
            if (uuid != null) {
                session.sendChatMessage(text(PlayerCache.nameForUuid(uuid) + " is in an unmapped world", DARK_RED));
            }
            return false;
        }
        if (uuid != null) {
            session.sendChatMessage(text("Jumping to " + PlayerCache.nameForUuid(uuid), GREEN));
        }
        sessionData.setLoadingMap(true);
        sessionData.setMapName(mapName);
        sessionData.setWorldFileCache(worldFileCache);
        session.sendMessage(new ChangeMapMessage(mapName, worldFileCache.getDisplayName() + " - Magic Map",
                                                 worldFileCache.getEffectiveWorldBorder(),
                                                 worldFileCache.getTag().getEnvironment().toString(),
                                                 x, z));
        return true;
    }
}
