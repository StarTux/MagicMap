package com.winthier.minimap;

import com.winthier.custom.CustomPlugin;
import com.winthier.custom.item.CustomItem;
import com.winthier.custom.item.ItemContext;
import com.winthier.custom.item.ItemDescription;
import com.winthier.custom.item.UncraftableItem;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class MiniMapItem implements CustomItem, UncraftableItem {
    public static final String CUSTOM_ID = "minimap:minimap";
    private final MiniMapPlugin plugin;
    private final ItemStack itemStack;
    private final ItemDescription itemDescription;

    MiniMapItem(MiniMapPlugin plugin) {
        this.plugin = plugin;
        itemStack = new ItemStack(Material.MAP, 1, (short)plugin.getMapId());
        itemDescription = ItemDescription.of(plugin.getConfig().getConfigurationSection("item"));
        itemDescription.apply(itemStack);
    }

    @Override
    public String getCustomId() {
        return CUSTOM_ID;
    }

    @Override
    public ItemStack spawnItemStack(int amount) {
        return itemStack.clone();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event, ItemContext context) {
        switch (event.getAction()) {
        case RIGHT_CLICK_BLOCK:
        case RIGHT_CLICK_AIR:
            break;
        default:
            return;
        }
        event.setCancelled(true);
        // CustomPlugin.getInstance().getInventoryManager().openInventory(event.getPlayer(), new MiniMapInventory(plugin, event.getPlayer()));
        event.getPlayer().performCommand("help");
    }
}
