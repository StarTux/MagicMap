package com.cavetale.magicmap.mytems;

import com.cavetale.magicmap.MagicMapPlugin;
import com.cavetale.mytems.Mytem;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.MytemsPlugin;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import static com.cavetale.magicmap.MagicMapPlugin.plugin;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

@Getter @RequiredArgsConstructor
public final class MagicMapMytem implements Mytem {
    private final MagicMapPlugin plugin;
    private final Mytems key = Mytems.MAGIC_MAP;
    private Component displayName = Component.text("Magic Map", LIGHT_PURPLE).decoration(ITALIC, false);

    @Override
    public void enable() { }

    @Override
    public ItemStack createItemStack() {
        return plugin.createMapItem();
    }

    public void markItemMeta(ItemMeta meta) {
        key.markItemMeta(meta);
    }

    public void register() {
        MytemsPlugin.registerMytem(plugin, key, this);
    }

    public void fixPlayerInventory(Player player) {
        for (ItemStack item : player.getInventory()) {
            if (plugin.isMagicMap(item) && Mytems.forItem(item) != key) {
                key.markItemStack(item);
            }
        }
        for (ItemStack item : player.getEnderChest()) {
            if (plugin.isMagicMap(item) && Mytems.forItem(item) != key) {
                key.markItemStack(item);
            }
        }
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public void onPlayerRightClick(PlayerInteractEvent event, Player player, ItemStack item) {
        // Session session = plugin().getSession(player);
        // double mapScale = session.getMapScale();
        // mapScale *= 2;
        // if (mapScale > 16.0) {
        //     mapScale = 0.25;
        // }
        // session.setMapScale(mapScale);
        // player.sendMessage("New scale " + mapScale);
    }
}
