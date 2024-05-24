package com.cavetale.magicmap;

import com.cavetale.core.event.block.PlayerBreakBlockEvent;
import com.cavetale.core.event.block.PlayerChangeBlockEvent;
import com.cavetale.core.event.block.PluginBlockEvent;
import com.cavetale.core.struct.Vec2i;
import com.cavetale.magicmap.file.WorldFileCache;
import com.cavetale.mytems.Mytems;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.StructureGrowEvent;
import static com.cavetale.core.font.Unicode.tiny;
import static com.cavetale.magicmap.MagicMapPlugin.isMagicMap;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@RequiredArgsConstructor
public final class MagicMapListener implements Listener {
    private final MagicMapPlugin plugin;

    public MagicMapListener enable() {
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

    private boolean requestBlockRerender(Block block) {
        return requestChunkRerender(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
    }

    private boolean requestChunkRerender(World world, int chunkX, int chunkZ) {
        final WorldFileCache worldFileCache = plugin.getWorlds().getWorld(world);
        if (worldFileCache == null) return false;
        return worldFileCache.requestChunkRerender(chunkX, chunkZ);
    }

    private void requestBlockListRerender(Iterable<Block> blockList) {
        final Set<Vec2i> chunks = new HashSet<>();
        for (Block block : blockList) {
            if (!chunks.add(Vec2i.of(block.getX() >> 4, block.getZ() >> 4))) continue;
            requestBlockRerender(block);
        }
    }

    private void requestBlockStateListRerender(Iterable<BlockState> blockList) {
        final Set<Vec2i> chunks = new HashSet<>();
        for (BlockState blockState : blockList) {
            if (!chunks.add(Vec2i.of(blockState.getX() >> 4, blockState.getZ() >> 4))) continue;
            requestBlockRerender(blockState.getBlock());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onBlockPlace(BlockPlaceEvent event) {
        requestBlockRerender(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onBlockBreak(BlockBreakEvent event) {
        requestBlockRerender(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onBlockGrow(BlockGrowEvent event) {
        requestBlockRerender(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onBlockFade(BlockFadeEvent event) {
        requestBlockRerender(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onLeavesDecay(LeavesDecayEvent event) {
        requestBlockRerender(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onBlockBurn(BlockBurnEvent event) {
        requestBlockRerender(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onBlockFromTo(BlockFromToEvent event) {
        requestBlockRerender(event.getBlock());
        requestBlockRerender(event.getToBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onBlockIgnite(BlockIgniteEvent event) {
        requestBlockRerender(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onTNTPrime(TNTPrimeEvent event) {
        requestBlockRerender(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onPlayerBreakBlock(PlayerBreakBlockEvent event) {
        requestBlockRerender(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onPlayerChangeBlock(PlayerChangeBlockEvent event) {
        requestBlockRerender(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onPluginBlock(PluginBlockEvent event) {
        requestBlockRerender(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onBlockExplode(BlockExplodeEvent event) {
        requestBlockListRerender(event.blockList());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onEntityExplode(EntityExplodeEvent event) {
        requestBlockListRerender(event.blockList());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onBlockPistonExtend(BlockPistonExtendEvent event) {
        requestBlockListRerender(event.getBlocks());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onBlockPistonRetract(BlockPistonRetractEvent event) {
        requestBlockListRerender(event.getBlocks());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onSpongeAbsorb(SpongeAbsorbEvent event) {
        requestBlockStateListRerender(event.getBlocks());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onStructureGrow(StructureGrowEvent event) {
        requestBlockStateListRerender(event.getBlocks());
    }
}
