package com.cavetale.magicmap;

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
    private final MagicMapPlugin plugin;
    private Persistence persistence;
    @Setter private boolean enabled;
    @Setter private boolean persist;
    @Setter private String message;
    static final String GIVEN_PATH = "given.json";

    static final class Persistence {
        private HashSet<UUID> given = new HashSet<>();
    }

    private HashSet<UUID> getGiven() {
        if (persistence == null) {
            persistence = plugin.json.load(GIVEN_PATH, Persistence.class, Persistence::new);
        }
        return persistence.given;
    }

    void saveGiven() {
        if (!persist || persistence == null) return;
        plugin.json.save(GIVEN_PATH, persistence, true);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> maybeGiveMap(event.getPlayer()), 100L);
    }

    void maybeGiveMap(Player player) {
        if (!player.isValid()) return;
        if (!player.hasPermission("magicmap.receive")) return;
        UUID uuid = player.getUniqueId();
        if (persist && getGiven().contains(uuid)) return;
        for (ItemStack item: player.getInventory()) {
            if (item == null || item.getType() != Material.FILLED_MAP || !item.hasItemMeta()) {
                continue;
            }
            MapMeta meta = (MapMeta) item.getItemMeta();
            if (meta.getMapId() == plugin.getMapId()) {
                if (persist) {
                    getGiven().add(uuid);
                    saveGiven();
                }
                return;
            }
        }
        if (plugin.giveMapItem(player)) {
            if (persist) {
                getGiven().add(uuid);
                saveGiven();
            }
            plugin.getLogger().info(player.getName() + " received a MagicMap");
            if (message != null && !message.isEmpty()) {
                player.sendMessage(message);
            }
        }
    }

    void reset() {
        persistence = null;
    }
}
