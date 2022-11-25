package com.cavetale.magicmap;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandWarn;
import java.io.File;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.text;
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
    }

    private void reload(CommandSender sender) {
        plugin.setupMap();
        plugin.importConfig();
        plugin.getMapGiver().reset();
        plugin.loadMapColors();
        plugin.getSessions().clear();
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
            ColorGrabber.grab(file);
        } catch (Exception e) {
            e.printStackTrace();
            throw new CommandWarn("An error occured. See console");
        }
        plugin.loadMapColors();
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
}
