package fr.seedrunner.seedrunner.commands;

import fr.seedrunner.seedrunner.SeedRunner;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * /share — active ou désactive le partage de vie (SharedLife).
 *
 * Affiche dans le chat :
 *   [Runner] Share life : ON   (vert)
 *   [Runner] Share life : OFF  (rouge)
 */
public class ShareCommand implements CommandExecutor {

    private final SeedRunner plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public ShareCommand(SeedRunner plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("seedrunner.share")) {
            sender.sendMessage(mm.deserialize(
                    "<dark_gray>[<gold>Runner</gold>]</dark_gray> <red>Permission manquante."));
            return true;
        }

        boolean nowEnabled = !plugin.getSyncManager().isEnabled();
        plugin.getSyncManager().setEnabled(nowEnabled);

        String state = nowEnabled
                ? "<green>ON</green>"
                : "<red>OFF</red>";

        plugin.getServer().broadcast(mm.deserialize(
                "<dark_gray>[<gold>Runner</gold>]</dark_gray> <gray>Share life : " + state));

        return true;
    }
}
