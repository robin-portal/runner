package fr.seedrunner.seedrunner.commands;

import fr.seedrunner.seedrunner.SeedRunner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /lobby — téléporte le joueur dans le lobby.
 */
public class LobbyCommand implements CommandExecutor {

    private final SeedRunner plugin;

    public LobbyCommand(SeedRunner plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Commande réservée aux joueurs.");
            return true;
        }
        plugin.getLobbyManager().sendToLobby(player);
        return true;
    }
}
