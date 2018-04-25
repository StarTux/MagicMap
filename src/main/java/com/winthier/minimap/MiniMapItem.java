package com.winthier.minimap;

import com.winthier.custom.CustomPlugin;
import com.winthier.custom.item.CustomItem;
import com.winthier.custom.item.ItemContext;
import com.winthier.custom.item.ItemDescription;
import com.winthier.custom.item.UncraftableItem;
import com.winthier.custom.item.UpdatableItem;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class MiniMapItem implements CustomItem, UncraftableItem, UpdatableItem {
    public static final String CUSTOM_ID = "minimap:minimap";
    private final MiniMapPlugin plugin;
    private final ItemStack itemStack;
    private final ItemDescription itemDescription;

    MiniMapItem(MiniMapPlugin plugin) {
        this.plugin = plugin;
        itemStack = new ItemStack(Material.MAP, 1, (short)plugin.getMapId());
        itemDescription = ItemDescription.of(plugin.getConfig().getConfigurationSection("item"));
        itemDescription.apply(itemStack);
        ItemMeta meta = itemStack.getItemMeta();
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        itemStack.setItemMeta(meta);
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
        if (event.getHand() != EquipmentSlot.HAND) return;
        final Player player;
        final Session session;
        switch (event.getAction()) {
        case RIGHT_CLICK_BLOCK:
        case RIGHT_CLICK_AIR:
            player = event.getPlayer();
            session = plugin.getSession(player);
            if (session.getMode() == Session.Mode.MAP) {
                player.playSound(player.getEyeLocation(), Sound.UI_TOAST_IN, 2.0f, 1.0f);
                session.setMode(Session.Mode.MENU);
                session.setMenuNeedsUpdate(true);
                session.setMouseX(64f);
                session.setMouseY(64f);
                if (player.getInventory().getItemInOffHand().getType() == Material.AIR
                    && plugin.getUserSettings(player.getUniqueId()).getBoolean("FillOffHand", true)) {
                    for (int i = 35; i >= 0; i -= 1) {
                        if (i != player.getInventory().getHeldItemSlot()) {
                            ItemStack item = player.getInventory().getItem(i);
                            if (item != null && item.getType() != Material.AIR) {
                                player.getInventory().setItem(i, null);
                                player.getInventory().setItemInOffHand(item);
                                break;
                            }
                        }
                    }
                }
            } else if (session.getMode() == Session.Mode.MENU) {
                player.playSound(player.getEyeLocation(), Sound.UI_TOAST_OUT, 2.0f, 1.0f);
                if ("main".equals(session.getMenuLocation())) {
                    session.setMode(Session.Mode.MAP);
                    session.setLastRender(0);
                } else {
                    session.setMenuLocation("main");
                    session.setMenuNeedsUpdate(true);
                    session.setMouseX(64f);
                    session.setMouseY(64f);
                }
            }
            break;
        case LEFT_CLICK_BLOCK:
        case LEFT_CLICK_AIR:
            player = event.getPlayer();
            session = plugin.getSession(player);
            if (session.getMode() == Session.Mode.MENU) {
                MiniMapPlugin.getInstance().getRenderer().onClickMenu(player, session, (int)session.getMouseX(), (int)session.getMouseY());
            }
        default:
            return;
        }
        event.setCancelled(true);
        // CustomPlugin.getInstance().getInventoryManager().openInventory(event.getPlayer(), new MiniMapInventory(plugin, event.getPlayer()));
        // event.getPlayer().performCommand("help");
    }

    @Override
    public void updateItem(ItemStack item) {
        item.setDurability((short)plugin.getMapId());
        itemDescription.apply(item);
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }
}
