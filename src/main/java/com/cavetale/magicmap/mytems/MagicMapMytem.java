package com.cavetale.magicmap.mytems;

import com.cavetale.magicmap.MagicMapPlugin;
import com.cavetale.mytems.Mytem;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.MytemsPlugin;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

@Getter @RequiredArgsConstructor
public final class MagicMapMytem implements Mytem {
    private final MagicMapPlugin plugin;
    private final Mytems key = Mytems.MAGIC_MAP;
    private Component displayName;

    @Override
    public void enable() {
        displayName = Component.text("Magic Map").color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false);
    }

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
}
