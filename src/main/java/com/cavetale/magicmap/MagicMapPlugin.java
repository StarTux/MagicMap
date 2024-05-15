package com.cavetale.magicmap;

import com.cavetale.magicmap.file.Worlds;
import com.cavetale.magicmap.home.MagicMapHome;
import com.cavetale.magicmap.mytems.MagicMapMytem;
import com.cavetale.magicmap.webserver.WebserverManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
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
    protected long cursorTicks = 10;
    protected long renderCooldown = 5;
    protected long renderRefresh = 30;
    protected long teleportCooldown = 5;
    // Tools
    private TinyFont tinyFont;
    private MagicMapRenderer magicMapRenderer;
    private MapGiver mapGiver;
    private MagicMapCommand magicMapCommand;
    private final Map<String, Boolean> enableCaveView = new HashMap<>();
    static final String MAP_ID_PATH = "mapid.json";
    protected Json json = new Json(this);
    // Queues
    private List<SyncMapRenderer> mainQueue = new ArrayList<>();
    private Map<UUID, Session> sessions = new HashMap<>();
    // Worlds
    private final Worlds worlds = new Worlds();
    // Other plugin modules
    private MagicMapMytem magicMapMytem;
    private WebserverManager webserverManager;
    private MagicMapHome magicMapHome;
    // Player locations for the web server
    private Map<UUID, PlayerLocationTag> playerLocationTags = new HashMap<>();

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
        mapGiver = new MapGiver(this);
        getServer().getPluginManager().registerEvents(mapGiver, this);
        magicMapCommand = new MagicMapCommand(this);
        magicMapCommand.enable();
        importConfig();
        getServer().getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
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

    // --- Configuration

    protected void importConfig() {
        reloadConfig();
        debug = getConfig().getBoolean("debug");
        mapGiver.setEnabled(getConfig().getBoolean("give.enabled"));
        mapGiver.setPersist(getConfig().getBoolean("give.persist"));
        mapGiver.setMessage(getConfig().getString("give.message"));
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
        renderMarkerArmorStands = getConfig().getBoolean("cursor.markerArmorStands");
        cursorTicks = getConfig().getLong("cursor.ticks");
        renderCooldown = getConfig().getLong("render.cooldown", 5L);
        renderRefresh = getConfig().getLong("render.refresh", 30L);
        teleportCooldown = getConfig().getLong("render.teleportCooldown", 5L);
        enableCaveView.clear();
        ConfigurationSection section = getConfig().getConfigurationSection("EnableCaveView");
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

    private void tick() {
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
        updatePlayerLocationTags();
    }

    /**
     * This is run on every live server for the web server to pick it
     * up in WebserverManager with a method of the same name.
     */
    private void updatePlayerLocationTags() {
        // Remove offline players
        for (Iterator<Map.Entry<UUID, PlayerLocationTag>> iter = playerLocationTags.entrySet().iterator(); iter.hasNext();) {
            final Map.Entry<UUID, PlayerLocationTag> entry = iter.next();
            final UUID uuid = entry.getKey();
            final PlayerLocationTag tag = entry.getValue();
            if (Bukkit.getPlayer(uuid) == null) {
                tag.removeFromRedisAsync(uuid, null);
                iter.remove();
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            final UUID uuid = player.getUniqueId();
            final Location location = player.getLocation();
            PlayerLocationTag tag = playerLocationTags.get(uuid);
            if (tag == null) {
                tag = new PlayerLocationTag();
                tag.update(location);
                playerLocationTags.put(uuid, tag);
                tag.saveToRedisAsync(uuid, null);
            } else if (tag.isOutOfDate() || tag.didChange(location)) {
                tag.update(location);
                tag.saveToRedisAsync(uuid, null);
            }
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

    public static MagicMapPlugin plugin() {
        return instance;
    }
}
