package com.cavetale.magicmap;

import lombok.RequiredArgsConstructor;
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
        Player player = sender instanceof Player ? (Player)sender : null;
        switch (args[0]) {
        case "reload": {
            this.plugin.setupMap();
            this.plugin.importConfig();
            this.plugin.getMapGiver().reset();
            sender.sendMessage("MagicMap config reloaded");
            return true;
        }
        case "debug": {
            if (player == null) return false;
            Session session = this.plugin.getSession(player);
            session.debug = !session.debug;
            if (session.debug) {
                sender.sendMessage("Debug mode enabled");
            } else {
                sender.sendMessage("Debug mode disabled");
            }
            return true;
        }
        default: return false;
        }
    }
}
