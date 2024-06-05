package com.cavetale.magicmap;

import com.cavetale.core.connect.NetworkServer;
import com.cavetale.core.connect.ServerCategory;
import com.cavetale.core.util.Json;
import com.cavetale.magicmap.file.Worlds;
import com.cavetale.magicmap.home.MagicMapHome;
import com.cavetale.magicmap.mytems.MagicMapMytem;
import com.cavetale.magicmap.webserver.WebserverManager;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@Getter
public final class MagicMapPlugin extends JavaPlugin {
    @Getter private static MagicMapPlugin instance;
    // Map persistence
    private MapView mapView;
    // Configuration
    private int mapId;
    private boolean debug;
    // Tools
    private TinyFont tinyFont;
    private MagicMapRenderer magicMapRenderer;
    private MagicMapCommand magicMapCommand;
    private final Map<String, Boolean> enableCaveView = new HashMap<>();
    static final String MAP_ID_PATH = "mapid.json";
    // Queues
    private Map<UUID, Session> sessions = new HashMap<>();
    // Worlds
    private final Worlds worlds = new Worlds();
    // Other plugin modules
    private MagicMapMytem magicMapMytem;
    private WebserverManager webserverManager;
    private MagicMapHome magicMapHome;
    // Player locations for the web server

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        magicMapRenderer = new MagicMapRenderer(this);
        setupMap();
        getLogger().info("Using map #" + mapId);
        tinyFont = new TinyFont(this);
        magicMapCommand = new MagicMapCommand(this);
        magicMapCommand.enable();
        importConfig();
        if (NetworkServer.current().getCategory() != ServerCategory.WORLD_GENERATION) {
            new BlockChangeListener(this).enable();
        }
        if (getServer().getPluginManager().isPluginEnabled("Mytems")) {
            new PlayerListener(this).enable();
            getLogger().info("Player Listener enabled");
            magicMapMytem = new MagicMapMytem(this);
            magicMapMytem.register();
            getLogger().info("Mytem registered!");
            for (Player player : getServer().getOnlinePlayers()) {
                magicMapMytem.fixPlayerInventory(player);
            }
        }
        if (Bukkit.getPluginManager().isPluginEnabled("Webserver")) {
            webserverManager = new WebserverManager().enable();
            if (webserverManager != null) {
                getLogger().info("Webserver module enabled");
            } else {
                getLogger().warning("Webserver module disabled");
            }
        }
        if (Bukkit.getPluginManager().isPluginEnabled("Home")) {
            magicMapHome = new MagicMapHome().enable();
            if (magicMapHome != null) {
                getLogger().info("Home module enabled");
            } else {
                getLogger().warning("Home module disabled");
            }
        }
        if (Bukkit.getPluginManager().isPluginEnabled("Connect")) {
            new LocalPlayersModule().enable();
            getLogger().info("Local players module enabled");
        }
        worlds.enableWorldServer();
    }

    @Override
    public void onDisable() {
        resetMapView();
        sessions.clear();
        worlds.disableWorldServer();
        if (webserverManager != null) {
            webserverManager.disable();
            webserverManager = null;
        }
    }

    protected void importConfig() {
        reloadConfig();
        debug = getConfig().getBoolean("debug");
    }

    protected void setupMap() {
        resetMapView();
        final File mapIdFile = new File(getDataFolder(), MAP_ID_PATH);
        Integer id = Json.load(mapIdFile, Integer.class);
        if (id == null) {
            getLogger().info("Creating new MapView because " + MAP_ID_PATH + " does not exist");
            mapView = getServer().createMap(getServer().getWorlds().get(0));
            mapId = mapView.getId();
            Json.save(mapIdFile, mapId);
        } else {
            mapId = id;
        }
        @SuppressWarnings("deprecation") final MapView theMapView = Bukkit.getMap(mapId);
        this.mapView = theMapView;
        if (mapView == null) {
            getLogger().info("Creating new MapView because " + mapId + " is not a valid MapView");
            mapView = getServer().createMap(getServer().getWorlds().get(0));
            mapId = mapView.getId();
            Json.save(mapIdFile, mapId);
        }
        for (MapRenderer renderer : mapView.getRenderers()) {
            mapView.removeRenderer(renderer);
        }
        mapView.addRenderer(magicMapRenderer);
    }

    public Session getSession(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), Session::new);
    }

    private void resetMapView() {
        if (mapView != null) {
            for (MapRenderer renderer: mapView.getRenderers()) {
                    mapView.removeRenderer(renderer);
            }
            mapView = null;
        }
    }

    public ItemStack createMapItem() {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(mapView);
        meta.setScaling(false);
        meta.setColor(Color.fromRGB(0xFF00FF));
        meta.displayName(text("Magic Map", LIGHT_PURPLE));
        if (magicMapMytem != null) {
            magicMapMytem.markItemMeta(meta);
            meta.displayName(magicMapMytem.getDisplayName());
        }
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isMagicMap(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() != Material.FILLED_MAP || !itemStack.hasItemMeta()) {
            return false;
        }
        MapMeta meta = (MapMeta) itemStack.getItemMeta();
        return meta.getMapView().getId() == instance.mapId;
    }

    public static MagicMapPlugin plugin() {
        return instance;
    }
}
