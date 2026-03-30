package fr.seedrunner.seedrunner.listeners;

import fr.seedrunner.seedrunner.SeedRunner;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * JoinListener — gère la reconnexion d'un joueur pendant une run active.
 *
 * ═══════════════════════════════════════════════════════════════════
 * PROBLÈME
 * ═══════════════════════════════════════════════════════════════════
 *
 * Scénario : un joueur se déconnecte pendant une run, un /run est
 * lancé (les mondes runner/runner_nether/runner_the_end sont supprimés
 * et recréés), puis le joueur se reconnecte.
 *
 * Paper sauvegarde la position du joueur dans son monde au moment de
 * la déco. Si ce monde n'existe plus (ancien runner supprimé), Paper
 * le fait apparaître dans le monde principal du serveur (level-name
 * dans server.properties, souvent "world" vanilla).
 *
 * ═══════════════════════════════════════════════════════════════════
 * SOLUTION
 * ═══════════════════════════════════════════════════════════════════
 *
 * À chaque connexion, on vérifie si une run est active (le monde
 * "runner" existe et est chargé). Si oui, on TP le joueur dans runner
 * 2 ticks après sa connexion (le délai laisse Paper finir son
 * initialisation du joueur).
 *
 * Cas couverts :
 * 1. Déco pendant une run → reco → TP dans runner actuel ✓
 * 2. Run change pendant la déco → reco → TP dans le nouveau runner ✓
 * 3. Pas de run active → connexion normale, pas de TP ✓
 * 4. Joueur déco depuis le lobby → reco → reste dans le lobby
 *    (runner n'existe pas encore ou le joueur était déjà au lobby) ✓
 */
public class JoinListener implements Listener {

    private final SeedRunner plugin;

    public JoinListener(SeedRunner plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Délai de 2 ticks pour laisser Paper initialiser le joueur
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            World runner = plugin.getServer().getWorld("runner");

            // Pas de run active → on ne fait rien
            if (runner == null) return;

            // Le joueur est déjà dans un monde SeedRunner → on ne le déplace pas
            String currentWorld = player.getWorld().getName();
            if (currentWorld.equals("runner")
                    || currentWorld.equals("runner_nether")
                    || currentWorld.equals("runner_the_end")
                    || currentWorld.equals("seedrunner_lobby")) return;

            // Le joueur est dans un monde vanilla (world, world_nether, etc.)
            // → il s'est reconnecté après un /run qui a changé les mondes
            player.teleport(runner.getSpawnLocation());
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
            player.sendMessage(
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                    "<dark_gray>[<gold>Runner</gold>]</dark_gray> <gray>Run en cours — tu as été replacé dans la map."));

        }, 2L);
    }
}
