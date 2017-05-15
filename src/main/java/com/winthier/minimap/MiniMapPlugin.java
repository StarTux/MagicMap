package com.winthier.minimap;

import com.winthier.custom.CustomPlugin;
import com.winthier.custom.event.CustomRegisterEvent;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
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
    private HashSet<UUID> given;
    private int mapId;
    private boolean debug, give, persist;
    private MiniMapItem miniMapItem;
    private MapView mapView;

    @Override
    public void onEnable() {
        setupConfiguration();
    }

    @Override
    public void onDisable() {
        sessions.clear();
        resetMapView();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = args.length == 0 ? null : args[0].toLowerCase();
        if ("reload".equals(cmd)) {
            setupConfiguration();
            sessions.clear();
            given = null;
            sender.sendMessage("MiniMap config reloaded");
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

    private void setupConfiguration() {
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
        getServer().getPluginManager().registerEvents(this, this);
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
        setupConfiguration();
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
}
