package com.cavetale.magicmap;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.magicmap.file.MapImageRenderer;
import com.cavetale.magicmap.file.WorldFileCache;
import com.cavetale.magicmap.file.WorldRenderCache;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;
import static net.kyori.adventure.text.format.NamedTextColor.*;

final class MagicMapCommand extends AbstractCommand<MagicMapPlugin> {
    protected MagicMapCommand(final MagicMapPlugin plugin) {
        super(plugin, "magicmap");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("reload").denyTabCompletion()
            .description("Reload config")
            .senderCaller(this::reload);
        rootNode.addChild("debug").denyTabCompletion()
            .description("Debug stuff")
            .playerCaller(this::debug);
        rootNode.addChild("give").arguments("[player]")
            .description("Spawn map item for player")
            .completers(CommandArgCompleter.NULL)
            .senderCaller(this::give);
        rootNode.addChild("rerender").denyTabCompletion()
            .description("Trigger map rerender")
            .playerCaller(this::rerender);
        rootNode.addChild("area").denyTabCompletion()
            .description("Render areas")
            .playerCaller(this::area);
        rootNode.addChild("grab").denyTabCompletion()
            .description("Grab colors")
            .senderCaller(this::grab);
        rootNode.addChild("getcolor").denyTabCompletion()
            .description("Get color of current block")
            .playerCaller(this::getColor);
        rootNode.addChild("renderchunk")
            .description("Render current chunk to file")
            .playerCaller(this::renderChunk);
        final CommandNode worldsNode = rootNode.addChild("worlds")
            .description("World server commands");
        worldsNode.addChild("list").denyTabCompletion()
            .description("List mapped worlds")
            .senderCaller(this::worldsList);
        worldsNode.addChild("info").arguments("<world>")
            .description("Get mapped world info")
            .completers(CommandArgCompleter.supplyList(() -> plugin.getWorlds().getWorldNames()))
            .senderCaller(this::worldsInfo);
        final CommandNode fullRenderNode = rootNode.addChild("fullrender")
            .description("Full render command");
        fullRenderNode.addChild("start").arguments("<world>")
            .completers(CommandArgCompleter.supplyList(() -> plugin.getWorlds().getWorldNames()))
            .description("Start a full world render")
            .senderCaller(this::fullRenderStart);
        fullRenderNode.addChild("stop").arguments("<world>")
            .completers(CommandArgCompleter.supplyList(() -> plugin.getWorlds().getWorldNames()))
            .description("Stop a full world render")
            .senderCaller(this::fullRenderStop);
        fullRenderNode.addChild("info").arguments("<world>")
            .completers(CommandArgCompleter.supplyList(() -> plugin.getWorlds().getWorldNames()))
            .description("Print full world render info")
            .senderCaller(this::fullRenderInfo);
    }

    private void reload(CommandSender sender) {
        plugin.setupMap();
        plugin.importConfig();
        plugin.getMapGiver().reset();
        plugin.getSessions().clear();
        plugin.getWorlds().enableAllWorlds();
        sender.sendMessage(text("MagicMap config reloaded", AQUA));
    }

    private void debug(Player player) {
        Session session = plugin.getSession(player);
        session.debug = !session.debug;
        if (session.debug) {
            player.sendMessage(text("Debug mode enabled", GREEN));
        } else {
            player.sendMessage(text("Debug mode disabled", YELLOW));
        }
    }

    private boolean give(CommandSender sender, String[] args) {
        if (args.length > 1) return false;
        final Player target;
        if (sender instanceof Player player && args.length == 0) {
            target = player;
        } else if (args.length >= 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                throw new CommandWarn("Player not found: " + args[1]);
            }
        } else {
            return false;
        }
        if (!plugin.giveMapItem(target)) {
            throw new CommandWarn("Inventory of " + target.getName() + " is full");
        }
        sender.sendMessage(text("MagicMap given to " + target.getName(), YELLOW));
        return true;
    }

    private void rerender(Player player) {
        plugin.triggerRerender(player);
        player.sendMessage(text("Rerender triggered", AQUA));
    }

    private void grab(CommandSender sender) {
        File file = new File(plugin.getDataFolder(), "colors.txt");
        try {
            ColorGrabber.grabMaterials(file);
        } catch (Exception e) {
            e.printStackTrace();
            throw new CommandWarn("An error occured. See console");
        }
        sender.sendMessage("Colors grabbed, see " + file + "");
    }

    private boolean area(Player player, String[] args) {
        Session session = plugin.getSession(player);
        if (args.length == 0) {
            session.shownArea = null;
            plugin.triggerRerender(player);
            player.sendMessage(text("Not showing any area", YELLOW));
            return true;
        } else if (args.length == 1) {
            session.shownArea = args[0];
            plugin.triggerRerender(player);
            player.sendMessage(text("Showing area " + session.shownArea, AQUA));
            return true;
        } else {
            return false;
        }
    }

    private void getColor(Player player) {
        final var block = player.getLocation().getBlock();
        final var material = block.getType();
        final ColorIndex colorIndex = ColorIndex.ofMaterial(material);
        player.sendMessage(textOfChildren(text("block:", GRAY),
                                          text(block.getX() + " " + block.getY() + " " + block.getZ(), WHITE),
                                          text(" material:", GRAY),
                                          text("" + material, WHITE),
                                          text(" color:", GRAY),
                                          text("" + colorIndex, WHITE)));
    }

    private void renderChunk(Player player) {
        final var renderer = MapImageRenderer.renderChunk(player.getChunk());
        final var file = new File(plugin.getDataFolder(), "chunk.png");
        try {
            ImageIO.write(renderer.getImage(), "png", file);
        } catch (IOException ioe) {
            player.sendMessage(text(file + ": " + ioe.getMessage(), RED));
            return;
        }
        player.sendMessage(text("Chunk rendered to " + file + ", " + renderer.getMinWorldX() + " " + renderer.getMinWorldZ(), YELLOW));
    }

    private void worldsList(CommandSender sender) {
        sender.sendMessage(text("" + plugin.getWorlds().getWorldNames().size() + " worlds mapped", YELLOW));
        for (String name : plugin.getWorlds().getWorldNames()) {
            final WorldFileCache cache = plugin.getWorlds().getWorld(name);
            final String cmd = "/magicmap worlds info " + name;
            sender.sendMessage(textOfChildren(text(name, YELLOW),
                                              (cache.isFullRenderScheduled()
                                               ? text(" Rendering", YELLOW)
                                               : empty()))
                               .hoverEvent(text(cmd, GRAY))
                               .clickEvent(runCommand(cmd))
                               .insertion(name));
        }
    }

    private boolean worldsInfo(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        final String worldName = args[0];
        final WorldFileCache cache = plugin.getWorlds().getWorld(worldName);
        if (cache == null) {
            throw new CommandWarn("World not loaded or not managed: " + worldName);
        }
        sender.sendMessage(textOfChildren(text("Name ", GRAY),
                                          text(cache.getName(), WHITE)));
        sender.sendMessage(textOfChildren(text("Border ", GRAY),
                                          text("" + cache.getWorldBorder(), WHITE)));
        for (WorldRenderCache renderCache : cache.getRenderTypeMap().values()) {
            sender.sendMessage(text(renderCache.getRenderType().getHumanName(), YELLOW));
            sender.sendMessage(textOfChildren(text(" Regions Loaded ", GRAY),
                                              text(renderCache.getRegionMap().size(), WHITE)));
            sender.sendMessage(textOfChildren(text(" Current Async Region ", GRAY),
                                              text("" + renderCache.getCurrentAsyncRegion(), WHITE)));
            sender.sendMessage(textOfChildren(text(" Async Queue ", GRAY),
                                              text(renderCache.getAsyncQueue().size(), WHITE)));
        }
        sender.sendMessage(textOfChildren(text("Chunk Tickets ", GRAY),
                                          text(cache.getChunkTicketMap().size(), WHITE)));
        sender.sendMessage(textOfChildren(text("Chunks Loading ", GRAY),
                                          text(cache.getChunksLoading().size(), WHITE)));
        sender.sendMessage(textOfChildren(text("Chunks Callbacks ", GRAY),
                                          text(cache.getChunkCallbacks().size(), WHITE)));
        sender.sendMessage(textOfChildren(text("Full Render ", GRAY),
                                          (cache.isFullRenderScheduled()
                                           ? text("Active", RED)
                                           : text("Inactive", DARK_GRAY))));
        return true;
    }

    private boolean fullRenderStart(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        final String worldName = args[0];
        final WorldFileCache cache = plugin.getWorlds().getWorld(worldName);
        if (cache == null) {
            throw new CommandWarn("World not loaded or not managed: " + worldName);
        }
        if (cache.isFullRenderScheduled()) {
            throw new CommandWarn("Full render is already scheduled: " + worldName);
        }
        cache.scheduleFullRender();
        sender.sendMessage(text("Full render scheduled: " + worldName, YELLOW));
        return true;
    }

    private boolean fullRenderStop(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        final String worldName = args[0];
        final WorldFileCache cache = plugin.getWorlds().getWorld(worldName);
        if (cache == null) {
            throw new CommandWarn("World not loaded or not managed: " + worldName);
        }
        if (!cache.isFullRenderScheduled()) {
            throw new CommandWarn(worldName + " does not have an active full render");
        }
        cache.cancelFullRender();
        sender.sendMessage(text("Full render stopped: " + worldName, YELLOW));
        return true;
    }

    private boolean fullRenderInfo(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        final String worldName = args[0];
        final WorldFileCache cache = plugin.getWorlds().getWorld(worldName);
        if (cache == null) {
            throw new CommandWarn("World not loaded or not managed: " + worldName);
        }
        if (!cache.isFullRenderScheduled()) {
            throw new CommandWarn(worldName + " does not have an active full render");
        }
        final var render = cache.getTag().getFullRender();
        sender.sendMessage(text("Full render: " + worldName, AQUA));
        sender.sendMessage(textOfChildren(text("Ring ", GRAY),
                                          text(render.getCurrentRing(), WHITE)));
        sender.sendMessage(textOfChildren(text("Max Millis ", GRAY),
                                          text(render.getMaxMillisPerTick(), WHITE)));
        sender.sendMessage(textOfChildren(text("Border ", GRAY),
                                          text("" + render.getWorldBorder(), WHITE)));
        sender.sendMessage(textOfChildren(text("Current Region ", GRAY),
                                          text("" + render.getCurrentRegion(), WHITE)));
        sender.sendMessage(textOfChildren(text("Current Chunks ", GRAY),
                                          text(render.getCurrentChunks().size(), WHITE)));
        sender.sendMessage(textOfChildren(text("Region Queue ", GRAY),
                                          text(render.getRegionQueue().size(), WHITE)));
        sender.sendMessage(textOfChildren(text("Renderers ", GRAY),
                                          text((render.getRenderers() != null
                                                ? "" + render.getRenderers().size()
                                                : "0"), WHITE)));
        return true;
    }
}
