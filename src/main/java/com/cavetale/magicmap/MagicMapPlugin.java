package com.cavetale.magicmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class MagicMapPlugin extends JavaPlugin implements Listener {
    private static MagicMapPlugin instance;
    // Map persistence
    private MapView mapView;
    // Configuration
    private int mapId;
    private int mapColor;
    private String mapName;
    private boolean debug;
    private boolean persist;
    boolean renderAsync;
    boolean renderPlayers;
    boolean renderPlayerNames;
    boolean renderEntities;
    boolean renderMarkerArmorStands;
    boolean renderCoordinates = true;
    // Tools
    private TinyFont tinyFont;
    private MagicMapRenderer magicMapRenderer;
    private MapGiver mapGiver;
    private MagicMapCommand magicMapCommand;
    private final Map<String, String> worldNames = new HashMap<>();
    private final Map<String, Boolean> enableCaveView = new HashMap<>();
    static final String MAP_ID_PATH = "mapid.json";
    Json json = new Json(this);
    // Queues
    private List<SyncMapRenderer> mainQueue = new ArrayList<>();

    // --- Plugin

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        magicMapRenderer = new MagicMapRenderer(this);
        setupMap();
        getLogger().info("Using map #" + mapId + ".");
        tinyFont = new TinyFont(this);
        mapGiver = new MapGiver(this);
        getServer().getPluginManager().registerEvents(mapGiver, this);
        magicMapCommand = new MagicMapCommand(this);
        getCommand("magicmap").setExecutor(magicMapCommand);
        importConfig();
        if (mapGiver.isEnabled()) {
            for (Player player: getServer().getOnlinePlayers()) {
                mapGiver.maybeGiveMap(player);
            }
        }
        getServer().getScheduler().runTaskTimer(this, () -> onTick(), 1L, 1L);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        resetMapView();
        for (Player player: getServer().getOnlinePlayers()) {
            player.removeMetadata("magicmap.session", this);
        }
    }

    // --- Configuration

    String colorize(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    void importConfig() {
        reloadConfig();
        debug = getConfig().getBoolean("debug");
        mapGiver.setEnabled(getConfig().getBoolean("give.enabled"));
        mapGiver.setPersist(getConfig().getBoolean("give.persist"));
        mapGiver.setMessage(colorize(getConfig().getString("give.message")));
        mapColor = getConfig().getInt("map.color");
        mapName = colorize(getConfig().getString("map.name"));
        renderPlayers = getConfig().getBoolean("render.players");
        renderPlayerNames = getConfig().getBoolean("render.playerNames");
        renderEntities = getConfig().getBoolean("render.entities");
        renderMarkerArmorStands = getConfig().getBoolean("render.markerArmorStands");
        renderAsync = getConfig().getBoolean("render.async");
        worldNames.clear();
        ConfigurationSection section = getConfig().getConfigurationSection("WorldNames");
        if (section != null) {
            for (String key: section.getKeys(false)) {
                worldNames.put(key, section.getString(key));
            }
        }
        enableCaveView.clear();
        section = getConfig().getConfigurationSection("EnableCaveView");
        if (section != null) {
            for (String key: section.getKeys(false)) {
                enableCaveView.put(key, section.getBoolean(key));
            }
        }
    }

    void setupMap() {
        resetMapView();
        Integer id = json.load(MAP_ID_PATH, Integer.class);
        if (id == null) {
            mapView = getServer().createMap(getServer().getWorlds().get(0));
            mapId = mapView.getId();
            json.save(MAP_ID_PATH, mapId);
        }
        mapView = getServer().getMap(mapId);
        if (mapView == null) {
            mapView = getServer().createMap(getServer().getWorlds().get(0));
            mapId = mapView.getId();
            json.save(MAP_ID_PATH, mapId);
        }
        for (MapRenderer renderer : mapView.getRenderers()) {
            mapView.removeRenderer(renderer);
        }
        mapView.addRenderer(magicMapRenderer);
    }

    void onTick() {
        if (!mainQueue.isEmpty()) {
            SyncMapRenderer task = mainQueue.get(0);
            boolean ret;
            try {
                ret = task.run();
            } catch (Exception e) {
                e.printStackTrace();
                ret = false;
            }
            if (!ret) mainQueue.remove(0);
        }
    }

    // --- Utility

    Session getSession(Player player) {
        for (MetadataValue v: player.getMetadata("magicmap.session")) {
            if (v.getOwningPlugin().equals(this)) return (Session) v.value();
        }
        Session session = new Session(player.getUniqueId());
        player.setMetadata("magicmap.session", new FixedMetadataValue(this, session));
        return session;
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
        meta.setMapId(mapId);
        meta.setScaling(false);
        meta.setColor(Color.fromRGB(mapColor));
        meta.setLocationName("MagicMap");
        meta.setDisplayName(mapName);
        item.setItemMeta(meta);
        return item;
    }

    public String getWorldName(World world) {
        String name = worldNames.get(world.getName());
        if (name == null) name = worldNames.get("default");
        if (name == null) name = world.getName();
        return name;
    }

    // --- Event Handling

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.getPlayer().removeMetadata("magicmap.session", this);
    }

    // --- Event calling

    void callPostEvent(Session session) {
        Player player = getServer().getPlayer(session.player);
        if (player == null) return;
        MagicMapPostRenderEvent event = new MagicMapPostRenderEvent(player, session);
        getServer().getPluginManager().callEvent(event);
    }

    public static void triggerRerender(Player player) {
        if (instance == null) throw new IllegalStateException("MagicMapPlugin.instance is null!");
        if (player == null) throw new NullPointerException("Player cannot be null!");
        instance.getSession(player).forceUpdate = true;
    }
}
