package com.cavetale.magicmap;

import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
final class MagicMapCommand implements CommandExecutor {
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
            for (Player target: plugin.getServer().getOnlinePlayers()) {
                target.removeMetadata("magicmap.session", plugin);
            }
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
                sender.sendMessage("MagicMap given to " + target.getName() + ".");
            } else {
                sender.sendMessage("Could not give MagicMap to " + target.getName()
                                   + ". Inventory is full.");
            }
            return true;
        }
        case "rerender": {
            if (player == null) {
                sender.sendMessage("Player expected.");
                return true;
            }
            plugin.triggerRerender(player);
            sender.sendMessage("Rerender triggered.");
            return true;
        }
        case "grab": {
            try {
                Object file = ColorGrabber.grab(plugin);
                sender.sendMessage("Colors grabbed, see console or "
                                   + file + ".");
            } catch (Exception e) {
                sender.sendMessage("An error occured. See console.");
                e.printStackTrace();
            }
            return true;
        }
        default: return false;
        }
    }
}
