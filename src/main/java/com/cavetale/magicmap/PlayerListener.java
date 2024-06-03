package com.cavetale.magicmap;

import com.cavetale.mytems.Mytems;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import static com.cavetale.core.font.Unicode.tiny;
import static com.cavetale.magicmap.MagicMapPlugin.isMagicMap;
import static com.cavetale.magicmap.MagicMapPlugin.plugin;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.format.NamedTextColor.*;

/**
 * Listen for player interactions.
 */
@RequiredArgsConstructor
public final class PlayerListener implements Listener {
    private final MagicMapPlugin plugin;

    public PlayerListener enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        return this;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getSessions().remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getSession(event.getPlayer());
        if (plugin.getMagicMapMytem() != null) {
            plugin.getMagicMapMytem().fixPlayerInventory(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    private void onPlayerItemHeld(PlayerItemHeldEvent event) {
        final Player player = event.getPlayer();
        if (!isMagicMap(player.getInventory().getItemInOffHand())) return;
        if (!player.isSneaking()) return;
        final Session session = plugin.getSession(player);
        final MagicMapScale mapScale = MagicMapScale.values()[event.getNewSlot()];
        session.setMapScale(mapScale);
        player.sendActionBar(textOfChildren(Mytems.MAGIC_MAP,
                                            text(tiny(" zoom "), DARK_PURPLE),
                                            text(mapScale.zoomFormat, LIGHT_PURPLE)));
        player.playSound(player.getLocation(), Sound.ITEM_SPYGLASS_USE, 1f, 2f);
    }
}
