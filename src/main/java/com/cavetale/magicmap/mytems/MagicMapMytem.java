package com.cavetale.magicmap.mytems;

import com.cavetale.magicmap.MagicMapPlugin;
import com.cavetale.mytems.Mytem;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.MytemsPlugin;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

@Getter @RequiredArgsConstructor
public final class MagicMapMytem implements Mytem {
    private final MagicMapPlugin plugin;
    private final Mytems key = Mytems.MAGIC_MAP;

    @Override
    public void enable() { }

    @Override
    public ItemStack getItem() {
        return plugin.createMapItem();
    }

    @Override
    public BaseComponent[] getDisplayName() {
        return TextComponent.fromLegacyText(plugin.getMapName());
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
