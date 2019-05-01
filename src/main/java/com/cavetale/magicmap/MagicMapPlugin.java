package com.cavetale.magicmap;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
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
    private boolean debug, persist;
    private boolean renderAsync, renderPlayers, renderEntities;
    // Tools
    private TinyFont tinyFont;
    private MagicMapRenderer magicMapRenderer;
    private MapGiver mapGiver;
    private MagicMapCommand magicMapCommand;
    private final Map<String, String> worldNames = new HashMap<>();
    private final Map<String, Boolean> enableCaveView = new HashMap<>();
    // Queues
    private List<SyncMapRenderer> mainQueue = new ArrayList<>();

    // --- Plugin

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        this.magicMapRenderer = new MagicMapRenderer(this);
        setupMap();
        getLogger().info("Using map #" + this.mapId + ".");
        this.tinyFont = new TinyFont(this);
        this.mapGiver = new MapGiver(this);
        getServer().getPluginManager().registerEvents(this.mapGiver, this);
        this.magicMapCommand = new MagicMapCommand(this);
        getCommand("magicmap").setExecutor(this.magicMapCommand);
        importConfig();
        if (this.mapGiver.isEnabled()) {
            for (Player player: this.getServer().getOnlinePlayers()) {
                this.mapGiver.maybeGiveMap(player);
            }
        }
        getServer().getScheduler().runTaskTimer(this, () -> this.onTick(), 1L, 1L);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        resetMapView();
        for (Player player: this.getServer().getOnlinePlayers()) {
            player.removeMetadata("magicmap.session", this);
        }
    }

    // --- Configuration

    void importConfig() {
        reloadConfig();
        this.debug = getConfig().getBoolean("debug");
        this.mapGiver.setEnabled(getConfig().getBoolean("give.enabled"));
        this.mapGiver.setPersist(getConfig().getBoolean("give.persist"));
        this.mapGiver.setMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("give.message")));
        this.mapColor = getConfig().getInt("map.color");
        this.mapName = ChatColor.translateAlternateColorCodes('&', getConfig().getString("map.name"));
        this.renderPlayers = getConfig().getBoolean("render.players");
        this.renderEntities = getConfig().getBoolean("render.entities");
        this.renderAsync = getConfig().getBoolean("render.async");
        this.worldNames.clear();
        ConfigurationSection section = getConfig().getConfigurationSection("WorldNames");
        if (section != null) {
            for (String key: section.getKeys(false)) {
                this.worldNames.put(key, section.getString(key));
            }
        }
        this.enableCaveView.clear();
        section = getConfig().getConfigurationSection("EnableCaveView");
        if (section != null) {
            for (String key: section.getKeys(false)) {
                this.enableCaveView.put(key, section.getBoolean(key));
            }
        }
    }

    void setupMap() {
        resetMapView();
        Gson gson = new Gson();
        File file = new File(getDataFolder(), "mapid.json");
        if (!file.exists()) {
            this.mapView = getServer().createMap(getServer().getWorlds().get(0));
            if (this.mapView == null) {
                throw new IllegalStateException("Could not create new map.");
            }
            this.mapId = (int)mapView.getId();
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(this.mapId, writer);
            } catch (IOException ioe) {
                throw new IllegalStateException("Could not write " + file, ioe);
            }
        } else {
            try (FileReader reader = new FileReader(file)) {
                this.mapId = gson.fromJson(reader, Integer.class);
            } catch (IOException ioe) {
                throw new IllegalStateException("Could not read " + file, ioe);
            }
            this.mapView = getServer().getMap((short)this.mapId);
            if (this.mapView == null) {
                throw new IllegalStateException("Could not fetch map #" + this.mapId);
            }
        }
        for (MapRenderer renderer: this.mapView.getRenderers()) {
            this.mapView.removeRenderer(renderer);
        }
        this.mapView.addRenderer(this.magicMapRenderer);
    }

    void onTick() {
        if (!this.mainQueue.isEmpty()) {
            SyncMapRenderer task = this.mainQueue.get(0);
            boolean ret;
            try {
                ret = task.run();
            } catch (Exception e) {
                e.printStackTrace();
                ret = false;
            }
            if (!ret) this.mainQueue.remove(0);
        }
    }

    // --- Utility

    Session getSession(Player player) {
        for (MetadataValue v: player.getMetadata("magicmap.session")) {
            if (v.getOwningPlugin().equals(this)) return (Session)v.value();
        }
        Session session = new Session(player.getUniqueId());
        player.setMetadata("magicmap.session", new FixedMetadataValue(this, session));
        return session;
    }

    private void resetMapView() {
        if (this.mapView != null) {
            for (MapRenderer renderer: this.mapView.getRenderers()) {
                    mapView.removeRenderer(renderer);
            }
            this.mapView = null;
        }
    }

    public ItemStack createMapItem() {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta)item.getItemMeta();
        meta.setMapId(this.mapId);
        meta.setScaling(false);
        meta.setColor(Color.fromRGB(this.mapColor));
        meta.setLocationName("MagicMap");
        meta.setDisplayName(this.mapName);
        item.setItemMeta(meta);
        return item;
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
