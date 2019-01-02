package com.winthier.minimap;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;

@Getter @RequiredArgsConstructor
final class MapGiver implements Listener {
    private final MiniMapPlugin plugin;
    private Persistence persistence;
    @Setter private boolean enabled;
    @Setter private boolean persist;
    @Setter private String message;

    static final class Persistence {
        private HashSet<UUID> given = new HashSet<>();
    }

    private HashSet<UUID> getGiven() {
        if (this.persistence == null) {
            if (this.persist) {
                File file = new File(this.plugin.getDataFolder(), "given.json");
                if (!file.exists()) {
                    this.persistence = new Persistence();
                } else {
                    try (FileReader reader = new FileReader(file)) {
                        Gson gson = new Gson();
                        this.persistence = gson.fromJson(reader, Persistence.class);
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                        this.persistence = new Persistence();
                    }
                }
            } else {
                this.persistence = new Persistence();
            }
        }
        return this.persistence.given;
    }

    void saveGiven() {
        if (!this.persist || this.persistence == null) return;
        File file = new File(this.plugin.getDataFolder(), "given.json");
        try (FileWriter writer = new FileWriter(file)) {
            new Gson().toJson(this.persistence, writer);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> maybeGiveMap(event.getPlayer()), 100L);
    }

    void maybeGiveMap(Player player) {
        if (!player.isValid()) return;
        if (!player.hasPermission("minimap.receive")) return;
        UUID uuid = player.getUniqueId();
        if (this.getGiven().contains(uuid)) return;
        for (ItemStack item: player.getInventory()) {
            if (item == null || item.getType() != Material.MAP || !item.hasItemMeta()) continue;
            MapMeta meta = (MapMeta)item.getItemMeta();
            if (meta.getMapId() == this.plugin.getMapId()) {
                this.getGiven().add(uuid);
                if (this.persist) saveGiven();
                return;
            }
        }
        ItemStack newMap = this.plugin.createMapItem();
        if (player.getInventory().addItem(newMap).isEmpty()) {
            this.getGiven().add(uuid);
            if (this.persist) saveGiven();
            this.plugin.getLogger().info(player.getName() + " received a minimap.");
            if (this.message != null && !this.message.isEmpty()) {
                player.sendMessage(this.message);
            }
        }
    }

    void reset() {
        this.persistence = null;
    }
}
