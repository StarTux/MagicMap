package com.winthier.minimap;

import com.winthier.custom.CustomPlugin;
import com.winthier.custom.inventory.CustomInventory;
import com.winthier.custom.util.Msg;
import java.util.HashMap;
import lombok.Getter;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

@Getter
public class MiniMapInventory implements CustomInventory {
    private final MiniMapPlugin plugin;
    private final Player player;
    private final Inventory inventory;
    private final HashMap<Integer, Clickie> clickies = new HashMap<>();

    interface Clickie {
        void click();
    }

    MiniMapInventory(MiniMapPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        inventory = plugin.getServer().createInventory(player, 6 * 9, Msg.format("&aMini Map"));
    }

    ItemStack spawnItem(Material mat, int dmg, String title) {
        ItemStack result = new ItemStack(mat, 1, (short)dmg);
        ItemMeta meta = result.getItemMeta();
        meta.setDisplayName(ChatColor.RESET + title);
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        result.setItemMeta(meta);
        return result;
    }

    @Override
    public void onInventoryClick(InventoryClickEvent event) {
    }
}
