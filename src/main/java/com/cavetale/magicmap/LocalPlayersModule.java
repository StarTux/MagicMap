package com.cavetale.magicmap;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import static com.cavetale.magicmap.MagicMapPlugin.plugin;

public final class LocalPlayersModule {
    private Map<UUID, PlayerLocationTag> playerLocationTags = new HashMap<>();

    public LocalPlayersModule enable() {
        Bukkit.getScheduler().runTaskTimer(plugin(), this::updatePlayerLocationTags, 1L, 1L);
        return this;
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
            if (shouldHidePlayer(player)) {
                if (tag != null) {
                    playerLocationTags.remove(uuid);
                    tag.removeFromRedisAsync(uuid, null);
                }
            } else if (tag == null) {
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

    private static boolean shouldHidePlayer(Player player) {
        return player.isInvisible()
            || player.getGameMode() == GameMode.SPECTATOR
            || player.hasPotionEffect(PotionEffectType.INVISIBILITY)
            || player.hasPermission("magicmap.hidden");
    }
}
