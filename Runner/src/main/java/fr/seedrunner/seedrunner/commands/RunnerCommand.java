package fr.seedrunner.seedrunner.commands;

import fr.seedrunner.seedrunner.SeedRunner;
import fr.seedrunner.seedrunner.managers.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/**
 * Commande unique : /runner [seed]
 *
 * Sans argument  → reset complet avec seed aléatoire
 * /runner 12345  → reset complet avec seed numérique
 * /runner abc    → reset complet avec seed textuelle (hashée)
 *
 * Crée runner + runner_nether + runner_the_end,
 * supprime les anciens mondes SeedRunner,
 * TP + reset tous les joueurs dans l'overworld.
 */
public class RunnerCommand implements CommandExecutor, TabCompleter {

    private final SeedRunner plugin;
    private final MessageManager msg;

    public RunnerCommand(SeedRunner plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission("seedrunner.run")) {
            msg.send(sender, MessageManager.NO_PERMISSION);
            return true;
        }

        long seed = 0;
        if (args.length >= 1) {
            try {
                seed = Long.parseLong(args[0]);
            } catch (NumberFormatException e) {
                seed = args[0].hashCode();
            }
        }

        plugin.getWorldManager().resetAll(seed, sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) return List.of("<seed>");
        return List.of();
    }
}
