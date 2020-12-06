package com.cavetale.magicmap;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
final class MagicMapCommand implements TabExecutor {
    private final MagicMapPlugin plugin;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return false;
        Player player = sender instanceof Player ? (Player) sender : null;
        switch (args[0]) {
        case "reload": {
            plugin.setupMap();
            plugin.importConfig();
            plugin.getMapGiver().reset();
            plugin.loadMapColors();
            plugin.getSessions().clear();
            sender.sendMessage("MagicMap config reloaded");
            return true;
        }
        case "debug": {
            if (player == null) return false;
            Session session = plugin.getSession(player);
            session.debug = !session.debug;
            if (session.debug) {
                sender.sendMessage("Debug mode enabled");
            } else {
                sender.sendMessage("Debug mode disabled");
            }
            return true;
        }
        case "give": {
            if (args.length > 2) return false;
            if (player == null && args.length < 2) return false;
            Player target;
            if (args.length >= 2) {
                target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                    return true;
                }
            } else {
                target = player;
            }
            if (target.getInventory().addItem(plugin.createMapItem()).isEmpty()) {
                sender.sendMessage("MagicMap given to " + target.getName());
            } else {
                sender.sendMessage("Could not give MagicMap to " + target.getName()
                                   + ". Inventory is full");
            }
            return true;
        }
        case "rerender": {
            if (player == null) {
                sender.sendMessage("Player expected");
                return true;
            }
            plugin.triggerRerender(player);
            sender.sendMessage("Rerender triggered");
            return true;
        }
        case "grab": {
            File file = new File(plugin.getDataFolder(), "colors.txt");
            try {
                ColorGrabber.grab(file);
            } catch (Exception e) {
                sender.sendMessage("An error occured. See console");
                e.printStackTrace();
                return true;
            }
            plugin.loadMapColors();
            sender.sendMessage("Colors grabbed, see " + file + "");
            return true;
        }
        default: return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return null;
        String cmd = args[0];
        if (args.length == 1) {
            return Stream.of("reload", "debug", "give", "rerender", "grab")
                .filter(it -> it.startsWith(cmd))
                .collect(Collectors.toList());
        }
        switch (cmd) {
        case "give":
            if (args.length == 2) return null;
            return Collections.emptyList();
        default:
            return Collections.emptyList();
        }
    }
}
