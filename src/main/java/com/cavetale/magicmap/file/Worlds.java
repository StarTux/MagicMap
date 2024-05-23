package com.cavetale.magicmap.file;

import com.cavetale.magicmap.MagicMapPlugin;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/**
 * Manage all mapped worlds for the world server (not the web server).
 */
public final class Worlds implements Listener {
    private final Map<String, WorldFileCache> worldMap = new HashMap<>();

    public void enableWorldServer() {
        Bukkit.getScheduler().runTaskTimer(MagicMapPlugin.getInstance(), this::tick, 1L, 1L);
        Bukkit.getPluginManager().registerEvents(this, MagicMapPlugin.getInstance());
        enableAllWorlds();
    }

    public void disableWorldServer() {
        disableAllWorlds();
    }

    public void enableAllWorlds() {
        for (World world : Bukkit.getWorlds()) {
            enableWorld(world);
        }
    }

    public void disableAllWorlds() {
        for (WorldFileCache it : worldMap.values()) {
            it.disableWorld();
        }
        worldMap.clear();
    }

    private void tick() {
        final long startTime = System.currentTimeMillis();
        for (WorldFileCache it : worldMap.values()) {
            it.tick(startTime);
        }
    }

    private void enableWorld(World world) {
        final var plugin = MagicMapPlugin.getInstance();
        final String name = world.getName();
        if (worldMap.containsKey(name)) return;
        final boolean persistent = plugin.getConfig().getBoolean("AllWorlds") || plugin.getConfig().getStringList("MapWorlds").contains(name);
        WorldFileCache cache = new WorldFileCache(name, persistent ? world.getWorldFolder() : null);
        worldMap.put(name, cache);
        cache.enableWorld(world);
        plugin.getLogger().info("World Cache loaded: " + name);
    }

    private void disableWorld(World world) {
        final WorldFileCache old = worldMap.remove(world.getName());
        if (old != null) {
            old.disableWorld();
        }
    }

    @EventHandler
    private void onWorldLoad(WorldLoadEvent event) {
        enableWorld(event.getWorld());
    }

    @EventHandler
    private void onWorldUnload(WorldUnloadEvent event) {
        disableWorld(event.getWorld());
    }

    public List<String> getWorldNames() {
        return List.copyOf(worldMap.keySet());
    }

    public WorldFileCache getWorld(final String worldName) {
        return worldMap.get(worldName);
    }
}
