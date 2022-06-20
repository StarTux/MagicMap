package com.cavetale.magicmap;

import com.cavetale.magicmap.mytems.MagicMapMytem;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@Getter
public final class MagicMapPlugin extends JavaPlugin implements Listener {
    @Getter private static MagicMapPlugin instance;
    // Map persistence
    private MapView mapView;
    // Configuration
    private int mapId;
    private boolean debug;
    private boolean persist;
    protected boolean doCursors;
    protected boolean renderPlayers;
    protected int maxPlayers;
    protected boolean renderPlayerNames;
    protected boolean renderAnimals;
    protected int maxAnimals;
    protected boolean renderVillagers;
    protected int maxVillagers;
    protected boolean renderMonsters;
    protected int maxMonsters;
    protected boolean renderMarkerArmorStands;
    protected boolean renderCoordinates;
    protected long cursorTicks = 10;
    protected long renderCooldown = 5;
    protected long renderRefresh = 30;
    protected long teleportCooldown = 5;
    // Tools
    private TinyFont tinyFont;
    private MagicMapRenderer magicMapRenderer;
    private MapGiver mapGiver;
    private MagicMapCommand magicMapCommand;
    private final Map<String, String> worldNames = new HashMap<>();
    private final Map<String, Boolean> enableCaveView = new HashMap<>();
    static final String MAP_ID_PATH = "mapid.json";
    private final MapColor mapColor = new MapColor();
    protected Json json = new Json(this);
    // Queues
    private List<SyncMapRenderer> mainQueue = new ArrayList<>();
    private Map<UUID, Session> sessions = new HashMap<>();
    // Mytems
    protected MagicMapMytem magicMapMytem;

    // --- Plugin

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        magicMapRenderer = new MagicMapRenderer(this);
        loadMapColors();
        setupMap();
        getLogger().info("Using map #" + mapId);
        tinyFont = new TinyFont(this);
        mapGiver = new MapGiver(this);
        getServer().getPluginManager().registerEvents(mapGiver, this);
        magicMapCommand = new MagicMapCommand(this);
        getCommand("magicmap").setExecutor(magicMapCommand);
        importConfig();
        getServer().getScheduler().runTaskTimer(this, () -> onTick(), 1L, 1L);
        getServer().getPluginManager().registerEvents(this, this);
        if (getServer().getPluginManager().isPluginEnabled("Mytems")) {
            magicMapMytem = new MagicMapMytem(this);
            magicMapMytem.register();
            getLogger().info("Mytem registered!");
            for (Player player : getServer().getOnlinePlayers()) {
                magicMapMytem.fixPlayerInventory(player);
            }
        }
        if (mapGiver.isEnabled()) {
            for (Player player : getServer().getOnlinePlayers()) {
                mapGiver.maybeGiveMap(player);
            }
        }
    }

    @Override
    public void onDisable() {
        resetMapView();
        sessions.clear();
    }

    // --- Configuration

    protected void loadMapColors() {
        File file = new File(getDataFolder(), "colors.txt");
        InputStream inputStream;
        if (file.exists()) {
            try {
                inputStream = new FileInputStream(file);
            } catch (FileNotFoundException fnfe) {
                throw new UncheckedIOException(fnfe);
            }
        } else {
            inputStream = getResource("colors.txt");
        }
        if (!mapColor.load(inputStream)) {
            getLogger().warning("Failed to load colors!");
        } else {
            getLogger().info(mapColor.getCount() + " map colors loaded");
        }
    }

    private String colorize(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    protected void importConfig() {
        reloadConfig();
        debug = getConfig().getBoolean("debug");
        mapGiver.setEnabled(getConfig().getBoolean("give.enabled"));
        mapGiver.setPersist(getConfig().getBoolean("give.persist"));
        mapGiver.setMessage(colorize(getConfig().getString("give.message")));
        doCursors = getConfig().getBoolean("cursor.enabled");
        renderPlayers = getConfig().getBoolean("cursor.players");
        maxPlayers = getConfig().getInt("cursor.maxPlayers");
        renderPlayerNames = getConfig().getBoolean("cursor.playerNames");
        renderAnimals = getConfig().getBoolean("cursor.animals");
        maxAnimals = getConfig().getInt("cursor.maxAnimals");
        renderVillagers = getConfig().getBoolean("cursor.villagers");
        maxVillagers = getConfig().getInt("cursor.maxVillagers");
        renderMonsters = getConfig().getBoolean("cursor.monsters");
        maxMonsters = getConfig().getInt("cursor.maxMonsters");
        renderCoordinates = getConfig().getBoolean("cursor.coordinates");
        renderMarkerArmorStands = getConfig().getBoolean("cursor.markerArmorStands");
        cursorTicks = getConfig().getLong("cursor.ticks");
        renderCooldown = getConfig().getLong("render.cooldown", 5L);
        renderRefresh = getConfig().getLong("render.refresh", 30L);
        teleportCooldown = getConfig().getLong("render.teleportCooldown", 5L);
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

    protected void setupMap() {
        resetMapView();
        Integer id = json.load(MAP_ID_PATH, Integer.class);
        if (id == null) {
            mapView = getServer().createMap(getServer().getWorlds().get(0));
            mapId = mapView.getId();
            json.save(MAP_ID_PATH, mapId);
        } else {
            mapId = id;
        }
        @SuppressWarnings("deprecation") final MapView theMapView = Bukkit.getMap(mapId);
        this.mapView = theMapView;
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

    private void onTick() {
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

    protected Session getSession(Player player) {
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
        meta.setLocationName("MagicMap");
        meta.displayName(text("Magic Map", LIGHT_PURPLE));
        if (magicMapMytem != null) {
            magicMapMytem.markItemMeta(meta);
            meta.displayName(magicMapMytem.getDisplayName());
        }
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
        sessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        getSession(event.getPlayer()).cooldown = nowInSeconds() + 5;
        if (magicMapMytem != null) {
            magicMapMytem.fixPlayerInventory(event.getPlayer());
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("Mytems")) {
            magicMapMytem = new MagicMapMytem(this);
            magicMapMytem.register();
            getLogger().info("Mytem registered!");
        }
    }

    private static boolean isFar(Location a, Location b, double far) {
        if (!a.getWorld().equals(b.getWorld())) return true;
        double dist = Math.max(Math.abs(a.getX() - b.getX()),
                               Math.abs(a.getZ() - b.getZ()));
        return dist >= far;
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (isFar(from, to, 64.0)) {
            Session session = getSession(event.getPlayer());
            session.cooldown = nowInSeconds() + teleportCooldown;
            session.lastRender = 0L;
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        getSession(event.getPlayer()).cooldown = nowInSeconds() + 5;
    }

    static long nowInSeconds() {
        return System.nanoTime() / 1000000000;
    }

    public static void triggerRerender(Player player) {
        if (instance == null) throw new IllegalStateException("MagicMapPlugin.instance is null!");
        if (player == null) throw new NullPointerException("Player cannot be null!");
        instance.getSession(player).forceUpdate = true;
    }

    public static boolean giveMapItem(Player player) {
        ItemStack mapItem = instance.createMapItem();
        ItemStack old;
        old = player.getInventory().getItemInOffHand();
        if (old == null || old.getAmount() == 0) {
            player.getInventory().setItemInOffHand(mapItem);
            return true;
        }
        old = player.getInventory().getItemInMainHand();
        if (old == null || old.getAmount() == 0) {
            player.getInventory().setItemInMainHand(mapItem);
            return true;
        }
        return player.getInventory().addItem(mapItem).isEmpty();
    }

    public static boolean isMagicMap(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() != Material.FILLED_MAP || !itemStack.hasItemMeta()) {
            return false;
        }
        MapMeta meta = (MapMeta) itemStack.getItemMeta();
        return meta.getMapView().getId() == instance.mapId;
    }
}
