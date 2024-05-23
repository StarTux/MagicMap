package com.cavetale.magicmap;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.magicmap.file.WorldBorderCache;
import com.cavetale.magicmap.file.WorldFileCache;
import com.cavetale.magicmap.file.WorldRenderCache;
import java.io.File;
import java.util.Date;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import static java.util.Arrays.copyOfRange;
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
        rootNode.addChild("area").denyTabCompletion()
            .description("Render areas")
            .playerCaller(this::area);
        rootNode.addChild("grab").denyTabCompletion()
            .description("Grab colors")
            .senderCaller(this::grab);
        rootNode.addChild("getcolor").denyTabCompletion()
            .description("Get color of current block")
            .playerCaller(this::getColor);
        final CommandNode worldsNode = rootNode.addChild("worlds")
            .description("World server commands");
        worldsNode.addChild("list").denyTabCompletion()
            .description("List mapped worlds")
            .senderCaller(this::worldsList);
        worldsNode.addChild("info").arguments("<world>")
            .description("Get mapped world info")
            .completers(CommandArgCompleter.supplyList(() -> plugin.getWorlds().getWorldNames()))
            .senderCaller(this::worldsInfo);
        final CommandNode worldsBorderNode = worldsNode.addChild("border")
            .description("Custom world border");
        worldsBorderNode.addChild("reset").arguments("<world>")
            .description("Reset the custom world border")
            .completers(CommandArgCompleter.supplyList(() -> plugin.getWorlds().getWorldNames()))
            .senderCaller(this::worldsBorderReset);
        worldsBorderNode.addChild("set").arguments("<world> <centerX> <centerZ> <minX> <minZ> <maxX> <maxZ>")
            .description("Set custom world border")
            .completers(CommandArgCompleter.supplyList(() -> plugin.getWorlds().getWorldNames()),
                        CommandArgCompleter.INTEGER,
                        CommandArgCompleter.INTEGER,
                        CommandArgCompleter.INTEGER,
                        CommandArgCompleter.INTEGER,
                        CommandArgCompleter.INTEGER,
                        CommandArgCompleter.INTEGER)
            .senderCaller(this::worldsBorderSet);
        final CommandNode worldsDisplayNameNode = worldsNode.addChild("displayname")
            .description("World display name");
        worldsDisplayNameNode.addChild("reset").arguments("<world>")
            .description("Reset the display name")
            .completers(CommandArgCompleter.supplyList(() -> plugin.getWorlds().getWorldNames()))
            .senderCaller(this::worldsDisplayNameReset);
        worldsDisplayNameNode.addChild("set").arguments("<world> <displayname...>")
            .description("Set the display name")
            .completers(CommandArgCompleter.supplyList(() -> plugin.getWorlds().getWorldNames()))
            .senderCaller(this::worldsDisplayNameSet);
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
        fullRenderNode.addChild("pause").arguments("<world>")
            .completers(CommandArgCompleter.supplyList(() -> plugin.getWorlds().getWorldNames()))
            .description("Pause a full render")
            .senderCaller(this::fullRenderPause);
        fullRenderNode.addChild("unpause").arguments("<world>")
            .completers(CommandArgCompleter.supplyList(() -> plugin.getWorlds().getWorldNames()))
            .description("Unpause a full render")
            .senderCaller(this::fullRenderUnpause);
    }

    private WorldFileCache requireWorldFileCache(String worldName) {
        final WorldFileCache cache = plugin.getWorlds().getWorld(worldName);
        if (cache == null) {
            throw new CommandWarn("World not loaded or not managed: " + worldName);
        }
        return cache;
    }

    private void reload(CommandSender sender) {
        plugin.getWorlds().disableAllWorlds();
        plugin.setupMap();
        plugin.importConfig();
        plugin.getSessions().clear();
        plugin.getWorlds().enableAllWorlds();
        sender.sendMessage(text("MagicMap config reloaded", AQUA));
    }

    private void debug(Player player) {
        Session session = plugin.getSession(player);
        session.setDebug(!session.isDebug());
        if (session.isDebug()) {
            player.sendMessage(text("Debug mode enabled", GREEN));
        } else {
            player.sendMessage(text("Debug mode disabled", YELLOW));
        }
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
            session.setShownArea(null);
            player.sendMessage(text("Not showing any area", YELLOW));
            return true;
        } else if (args.length == 1) {
            session.setShownArea(args[0]);
            player.sendMessage(text("Showing area " + session.getShownArea(), AQUA));
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
        final WorldFileCache cache = requireWorldFileCache(args[0]);
        sender.sendMessage(textOfChildren(text("Name ", GRAY),
                                          text(cache.getName(), WHITE)));
        sender.sendMessage(textOfChildren(text("Persistent ", GRAY),
                                          (cache.isPersistent()
                                           ? text("Yes", GREEN)
                                           : text("No", RED))));
        sender.sendMessage(textOfChildren(text("Border ", GRAY),
                                          text("" + cache.getTag().getWorldBorder(), WHITE)));
        sender.sendMessage(textOfChildren(text("Custom Border ", GRAY),
                                          text("" + cache.getTag().getCustomWorldBorder(), WHITE)));
        sender.sendMessage(textOfChildren(text("Display Name ", GRAY),
                                          text("" + cache.getTag().getDisplayName(), WHITE)));
        sender.sendMessage(textOfChildren(text("Chunk Tickets ", GRAY),
                                          text(cache.getChunkTicketMap().size(), WHITE),
                                          text("/", DARK_GRAY),
                                          text(cache.countChunkTickets(), WHITE)));
        sender.sendMessage(textOfChildren(text("World Chunk Tickets ", GRAY),
                                          text(cache.getWorldChunkTicketCount(), WHITE)));
        sender.sendMessage(textOfChildren(text("Chunks Loading ", GRAY),
                                          text(cache.getChunksLoading().size(), WHITE)));
        sender.sendMessage(textOfChildren(text("Full Render ", GRAY),
                                          (cache.isFullRenderScheduled()
                                           ? text("Active", RED)
                                           : text("Inactive", DARK_GRAY))));
        for (WorldRenderCache renderCache : cache.getRenderTypeMap().values()) {
            sender.sendMessage(text(renderCache.getRenderType().getHumanName(), YELLOW));
            sender.sendMessage(textOfChildren(text(" Regions Loaded ", GRAY),
                                              text(renderCache.getRegionMap().size(), WHITE)));
            sender.sendMessage(textOfChildren(text(" Current Async Region ", GRAY),
                                              text("" + renderCache.getCurrentAsyncRegion(), WHITE)));
            sender.sendMessage(textOfChildren(text(" Async Queue ", GRAY),
                                              text(renderCache.getAsyncQueue().size(), WHITE)));
            sender.sendMessage(textOfChildren(text(" Chunk Render Queue ", GRAY),
                                              text(renderCache.getChunkRenderQueue().size(), WHITE)));
            sender.sendMessage(textOfChildren(text(" Chunk Render Task ", GRAY),
                                              (renderCache.getChunkRenderTask() != null
                                               ? renderCache.getChunkRenderTask().getInfoComponent()
                                               : text("None", DARK_GRAY))));
        }
        return true;
    }

    private boolean worldsBorderReset(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        final WorldFileCache cache = requireWorldFileCache(args[0]);
        if (cache.getTag().getCustomWorldBorder() == null) {
            throw new CommandWarn(cache.getName() + " does not have a custom world border");
        }
        cache.getTag().setCustomWorldBorder(null);
        cache.saveTag();
        sender.sendMessage(text("Custom world border was reset: " + cache.getName(), YELLOW));
        return true;
    }

    private boolean worldsBorderSet(CommandSender sender, String[] args) {
        if (args.length != 7) return false;
        final WorldFileCache cache = requireWorldFileCache(args[0]);
        final WorldBorderCache border = new WorldBorderCache(CommandArgCompleter.requireInt(args[1]),
                                                             CommandArgCompleter.requireInt(args[2]),
                                                             CommandArgCompleter.requireInt(args[3]),
                                                             CommandArgCompleter.requireInt(args[4]),
                                                             CommandArgCompleter.requireInt(args[5]),
                                                             CommandArgCompleter.requireInt(args[6]));
        if (border.isMalformed()) {
            throw new CommandWarn("Malformed border: " + border);
        }
        cache.getTag().setCustomWorldBorder(border);
        cache.saveTag();
        sender.sendMessage(text("Custom world border updated: " + cache.getName() + ", " + border, YELLOW));
        return true;
    }

    private boolean worldsDisplayNameReset(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        final WorldFileCache cache = requireWorldFileCache(args[0]);
        if (cache.getTag().getDisplayName() == null) {
            throw new CommandWarn(cache.getName() + " does not set a display name");
        }
        cache.getTag().setDisplayName(null);
        cache.saveTag();
        sender.sendMessage(text("Display name was reset: " + cache.getName(), YELLOW));
        return true;
    }

    private boolean worldsDisplayNameSet(CommandSender sender, String[] args) {
        if (args.length < 2) return false;
        final WorldFileCache cache = requireWorldFileCache(args[0]);
        cache.getTag().setDisplayName(String.join(" ", copyOfRange(args, 1, args.length)));
        cache.saveTag();
        sender.sendMessage(text("Display name was updated: " + cache.getName() + ", " + cache.getDisplayName(), YELLOW));
        return true;
    }

    private boolean fullRenderStart(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        final WorldFileCache cache = requireWorldFileCache(args[0]);
        if (cache.isFullRenderScheduled()) {
            throw new CommandWarn("Full render is already scheduled: " + cache.getName());
        }
        if (!cache.isPersistent()) {
            throw new CommandWarn("Not a persistent world: " + cache.getName());
        }
        cache.scheduleFullRender();
        sender.sendMessage(text("Full render scheduled: " + cache.getName(), YELLOW));
        return true;
    }

    private boolean fullRenderStop(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        final WorldFileCache cache = requireWorldFileCache(args[0]);
        if (!cache.isFullRenderScheduled()) {
            throw new CommandWarn(cache.getName() + " does not have an active full render");
        }
        cache.cancelFullRender();
        sender.sendMessage(text("Full render stopped: " + cache.getName(), YELLOW));
        return true;
    }

    private boolean fullRenderInfo(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        final WorldFileCache cache = requireWorldFileCache(args[0]);
        if (!cache.isFullRenderScheduled()) {
            throw new CommandWarn(cache.getName() + " does not have an active full render");
        }
        final var render = cache.getTag().getFullRender();
        sender.sendMessage(text("Full render: " + cache.getName(), AQUA));
        sender.sendMessage(textOfChildren(text("Status ", GRAY),
                                          (render.getStatus() != null
                                           ? text(render.getStatus(), WHITE)
                                           : text("None", DARK_GRAY))));
        sender.sendMessage(textOfChildren(text("Start Time ", GRAY),
                                          text("" + new Date(render.getStartTime()), WHITE)));
        sender.sendMessage(textOfChildren(text("Timeout ", GRAY),
                                          (render.getTimeout() > 0L
                                           ? text("" + new Date(render.getTimeout()), WHITE)
                                           : text("None", DARK_GRAY))));
        sender.sendMessage(textOfChildren(text("Max Millis ", GRAY),
                                          text(render.getMaxMillisPerTick(), WHITE)));
        sender.sendMessage(textOfChildren(text("Ring ", GRAY),
                                          text(render.getCurrentRing(), WHITE)));
        sender.sendMessage(textOfChildren(text("Border ", GRAY),
                                          text("" + render.getWorldBorder(), WHITE)));
        sender.sendMessage(textOfChildren(text("Current Region ", GRAY),
                                          text("" + render.getCurrentRegion(), WHITE)));
        sender.sendMessage(textOfChildren(text("Current Chunks ", GRAY),
                                          text(render.getCurrentChunks().size(), WHITE)));
        sender.sendMessage(textOfChildren(text("Chunks Held ", GRAY),
                                          (render.isChunksHeld()
                                           ? text("Chunks held", WHITE)
                                           : text("Not held", DARK_GRAY))));
        sender.sendMessage(textOfChildren(text("Region Queue ", GRAY),
                                          text(render.getRegionQueue().size(), WHITE)));
        sender.sendMessage(textOfChildren(text("Renderers ", GRAY),
                                          (render.getRenderers() != null
                                           ? text("" + render.getRenderers().size(), WHITE)
                                           : text("None", DARK_GRAY))));
        sender.sendMessage(textOfChildren(text("Paused ", GRAY),
                                          (render.isPaused()
                                           ? text("Paused", YELLOW)
                                           : text("Not paused", DARK_GRAY))));
        return true;
    }

    private boolean fullRenderPause(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        final WorldFileCache cache = requireWorldFileCache(args[0]);
        if (!cache.isFullRenderScheduled()) {
            throw new CommandWarn(cache.getName() + " does not have an active full render");
        }
        if (cache.getTag().getFullRender().isPaused()) {
            throw new CommandWarn("Full render already paused: " + cache.getName());
        }
        cache.getTag().getFullRender().setPaused(true);
        sender.sendMessage(text("Full render paused: " + cache.getName()));
        return true;
    }

    private boolean fullRenderUnpause(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        final WorldFileCache cache = requireWorldFileCache(args[0]);
        if (!cache.isFullRenderScheduled()) {
            throw new CommandWarn(cache.getName() + " does not have an active full render");
        }
        if (!cache.getTag().getFullRender().isPaused()) {
            throw new CommandWarn("Full render not paused: " + cache.getName());
        }
        cache.getTag().getFullRender().setPaused(false);
        sender.sendMessage(text("Full render unpaused: " + cache.getName()));
        return true;
    }
}
