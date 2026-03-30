package fr.seedrunner.seedrunner.listeners;

import fr.seedrunner.seedrunner.SeedRunner;
import fr.seedrunner.seedrunner.managers.LobbyManager;
import fr.seedrunner.seedrunner.managers.WorldManager;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * RespawnListener — à chaque mort dans un monde SeedRunner,
 * le joueur respawn dans le lobby (pas dans world vanilla, pas dans runner).
 *
 * ═══════════════════════════════════════════════════════════════════
 * COMPORTEMENT
 * ═══════════════════════════════════════════════════════════════════
 *
 * Mort dans un monde SeedRunner (runner / runner_nether / runner_the_end)
 *   → respawn dans seedrunner_lobby
 *
 * Mort dans le lobby (normalement impossible mais géré)
 *   → respawn dans le lobby
 *
 * Mort ailleurs (monde vanilla "world", etc.)
 *   → Paper gère normalement (non concerné par SeedRunner)
 *
 * ═══════════════════════════════════════════════════════════════════
 * POURQUOI ON N'UTILISE PAS PlayerDeathEvent
 * ═══════════════════════════════════════════════════════════════════
 *
 * PlayerDeathEvent se déclenche au moment de la mort, mais le joueur
 * n'est pas encore en train de respawn — on ne peut pas encore le TP.
 * PlayerRespawnEvent est l'endroit correct pour changer la destination
 * de respawn : Paper utilise directement event.getRespawnLocation()
 * pour décider où placer le joueur.
 *
 * On ignore isBedSpawn() et isAnchorSpawn() intentionnellement ici :
 * on veut TOUJOURS envoyer au lobby, même si le joueur a un lit,
 * pour que la mécanique de "mort = lobby" soit cohérente.
 */
public class RespawnListener implements Listener {

    private final SeedRunner plugin;
    private final WorldManager worldManager;
    private final LobbyManager lobbyManager;

    public RespawnListener(SeedRunner plugin) {
        this.plugin = plugin;
        this.worldManager = plugin.getWorldManager();
        this.lobbyManager = plugin.getLobbyManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        World deathWorld = event.getPlayer().getWorld();

        // Mort dans le lobby → reste dans le lobby
        if (lobbyManager.isLobbyWorld(deathWorld)) {
            event.setRespawnLocation(lobbyManager.getLobbyWorld().getSpawnLocation());
            return;
        }

        // Mort dans un monde SeedRunner → lobby
        if (worldManager.getRunnerBase(deathWorld.getName()) != null) {
            World lobby = lobbyManager.getLobbyWorld();
            if (lobby != null) {
                event.setRespawnLocation(lobby.getSpawnLocation());
            }
        }
    }
}
