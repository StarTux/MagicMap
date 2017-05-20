package com.winthier.minimap;

import com.winthier.custom.CustomPlugin;
import com.winthier.custom.event.CustomRegisterEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

@Getter
public final class MiniMapPlugin extends JavaPlugin implements Listener {
    private final HashMap<UUID, Session> sessions = new HashMap<>();
    private final HashMap<String, String> worldNames = new HashMap<>();
    private List<Marker> markers;
    private HashSet<UUID> given;
    private int mapId;
    private boolean debug, give, persist;
    private MiniMapItem miniMapItem;
    private MapView mapView;
    private Font4x4 font4x4;
    // Plugins
    private CreativeRenderer creativeRenderer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        readConfiguration();
        getServer().getPluginManager().registerEvents(this, this);
        font4x4 = new Font4x4(this);
        if (getServer().getPluginManager().getPlugin("Creative") != null) {
            creativeRenderer = new CreativeRenderer(this);
        }
    }

    @Override
    public void onDisable() {
        sessions.clear();
        resetMapView();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = args.length == 0 ? null : args[0].toLowerCase();
        Player player = sender instanceof Player ? (Player)sender : null;
        if ("reload".equals(cmd)) {
            readConfiguration();
            sessions.clear();
            given = null;
            sender.sendMessage("MiniMap config reloaded");
        } else if ("setmarker".equals(cmd) && args.length >= 3) {
            if (player == null) return false;
            String name = args[1];
            if (!name.matches("[a-zA-Z0-9-_]{1,32}")) {
                sender.sendMessage("Illegal marker name " + name);
                return true;
            }
            StringBuilder sb = new StringBuilder(args[2]);
            for (int i = 3; i < args.length; i += 1) sb.append(" ").append(args[i]);
            Marker marker = null;
            for (Marker iter: getMarkers()) {
                if (iter.getName().equals(name)) marker = iter;
            }
            if (marker == null) {
                marker = new Marker();
                getMarkers().add(marker);
            }
            marker.setName(name);
            marker.setWorld(player.getWorld().getName());
            marker.setX(player.getLocation().getBlockX());
            marker.setZ(player.getLocation().getBlockZ());
            marker.setMessage(sb.toString());
            saveMarkers();
            player.sendMessage("Marker set");
        } else {
            return false;
        }
        return true;
    }

    void resetMapView() {
        if (mapView != null) {
            for (MapRenderer renderer: mapView.getRenderers()) {
                mapView.removeRenderer(renderer);
            }
            mapView = null;
        }
    }

    private void readConfiguration() {
        reloadConfig();
        resetMapView();
        mapId = getConfig().getInt("MapID");
        debug = getConfig().getBoolean("Debug");
        give = getConfig().getBoolean("Give");
        persist = getConfig().getBoolean("Persist");
        mapView = getServer().getMap((short)mapId);
        for (MapRenderer renderer: mapView.getRenderers()) {
            mapView.removeRenderer(renderer);
        }
        mapView.addRenderer(new TerrainRenderer(this));
        if (debug) mapView.addRenderer(new DebugRenderer(this));
        sessions.clear();
        worldNames.clear();
        ConfigurationSection section = getConfig().getConfigurationSection("WorldNames");
        if (section != null) {
            for (String key: section.getKeys(false)) {
                worldNames.put(key, section.getString(key));
            }
        }
        markers = null;
    }

    Session getSession(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            session = new Session();
            sessions.put(player.getUniqueId(), session);
        }
        return session;
    }

    private HashSet<UUID> getGiven() {
        if (given == null) {
            given = new HashSet<>();
            if (persist) {
                YamlConfiguration.loadConfiguration(new File(getDataFolder(), "given.yml")).getStringList("given").forEach(str -> given.add(UUID.fromString(str)));
            }
        }
        return given;
    }

    void saveGiven() {
        if (given == null || !persist) return;
        YamlConfiguration config = new YamlConfiguration();
        config.set("given", given.stream().map(uuid -> uuid.toString()).collect(Collectors.toList()));
        try {
            config.save(new File(getDataFolder(), "given.yml"));
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    @EventHandler
    public void onCustomRegister(CustomRegisterEvent event) {
        readConfiguration();
        miniMapItem = new MiniMapItem(this);
        event.addItem(miniMapItem);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (getGiven().contains(uuid)) return;
        for (ItemStack item: event.getPlayer().getInventory()) {
            if (item != null && item.getType() == Material.MAP && item.getDurability() == mapId) {
                getGiven().add(uuid);
                saveGiven();
                return;
            }
        }
        if (event.getPlayer().getInventory().addItem(CustomPlugin.getInstance().getItemManager().spawnItemStack(MiniMapItem.CUSTOM_ID, 1)).isEmpty()) {
            getLogger().info("Mini Map given to " + event.getPlayer().getName());
            getGiven().add(uuid);
            saveGiven();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();
        new BukkitRunnable() {
            @Override public void run() {
                sessions.remove(uuid);
            }
        }.runTask(this);
    }

    String getWorldName(String worldName) {
        String result;
        result = worldNames.get(worldName);
        if (result != null) return result;
        return worldName;
    }

    List<Marker> getMarkers() {
        if (markers == null) {
            markers = new ArrayList<>();
            YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "markers.yml"));
            for (String key: config.getKeys(false)) {
                ConfigurationSection section = config.getConfigurationSection(key);
                if (section == null) continue;
                Marker marker = new Marker();
                marker.setName(key);
                marker.setWorld(section.getString("world"));
                marker.setX(section.getInt("x"));
                marker.setZ(section.getInt("z"));
                marker.setMessage(section.getString("message"));
                markers.add(marker);
            }
        }
        return markers;
    }

    void saveMarkers() {
        if (markers == null) return;
        YamlConfiguration config = new YamlConfiguration();
        for (Marker marker: markers) {
            ConfigurationSection section = config.createSection(marker.getName());
            section.set("world", marker.getWorld());
            section.set("x", marker.getX());
            section.set("z", marker.getZ());
            section.set("message", marker.getMessage());
        }
        try {
            config.save(new File(getDataFolder(), "markers.yml"));
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
