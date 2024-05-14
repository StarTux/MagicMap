package com.cavetale.magicmap;

import com.cavetale.core.connect.NetworkServer;
import com.cavetale.core.util.Json;
import com.winthier.connect.Redis;
import java.io.Serializable;
import java.util.UUID;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import static com.cavetale.magicmap.MagicMapPlugin.plugin;

/**
 * Each server stores this info in Redis for the Webserver to pick up.
 */
@Data
public final class PlayerLocationTag implements Serializable {
    public static final String KEY_PREFIX = "MagicMap.PlayerLocation.";
    private NetworkServer server;
    private String world;
    private int x;
    private int z;
    private transient long lastSave;

    public PlayerLocationTag() { }

    public PlayerLocationTag(final PlayerLocationTag old) {
        this.server = old.server;
        this.world = old.world;
        this.x = old.x;
        this.z = old.z;
    }

    public PlayerLocationTag clone() {
        return new PlayerLocationTag(this);
    }

    public boolean isOutOfDate() {
        return lastSave + 10_000L < System.currentTimeMillis();
    }

    public boolean didChange(Location location) {
        return !location.getWorld().getName().equals(world)
            || location.getBlockX() != x
            || location.getBlockZ() != z;
    }

    public void update(Location location) {
        this.server = NetworkServer.current();
        this.world = location.getWorld().getName();
        this.x = location.getBlockX();
        this.z = location.getBlockZ();
    }

    public void makeUnknown() {
        this.server = NetworkServer.UNKNOWN;
        this.world = "unknown";
        this.x = 0;
        this.z = 0;
    }

    private static String getKey(NetworkServer server, UUID uuid) {
        return KEY_PREFIX + server.name().toLowerCase() + "." + uuid;
    }

    public void saveToRedis(UUID uuid) {
        Redis.set(getKey(server, uuid), Json.serialize(this), 60L);
    }

    public void saveToRedisAsync(UUID uuid, Runnable callback) {
        lastSave = System.currentTimeMillis();
        Bukkit.getScheduler().runTaskAsynchronously(plugin(), () -> {
                saveToRedis(uuid);
                if (callback != null) {
                    Bukkit.getScheduler().runTask(plugin(), () -> {
                            callback.run();
                        });
                }
            });
    }

    public static PlayerLocationTag loadFromRedis(NetworkServer server, UUID uuid) {
        final String value = Redis.get(getKey(server, uuid));
        if (value == null) return null;
        return Json.deserialize(value, PlayerLocationTag.class, () -> null);
    }

    public void removeFromRedis(UUID uuid) {
        Redis.del(getKey(server, uuid));
    }

    public void removeFromRedisAsync(UUID uuid, Runnable callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin(), () -> {
                removeFromRedis(uuid);
                if (callback != null) {
                    Bukkit.getScheduler().runTask(plugin(), () -> {
                            callback.run();
                        });
                }
            });
    }
}
