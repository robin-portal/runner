package fr.seedrunner.seedrunner.listeners;

import fr.seedrunner.seedrunner.SeedRunner;
import fr.seedrunner.seedrunner.managers.LobbyManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * LobbySignListener — gère les interactions dans le lobby.
 *
 * - Clic droit sur la pancarte → TP dans runner
 * - BlockBreakEvent dans le lobby → annulé (personne ne peut casser quoi que ce soit)
 * - BlockPlaceEvent dans le lobby → annulé
 */
public class LobbySignListener implements Listener {

    private final SeedRunner plugin;
    private final LobbyManager lobbyManager;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public LobbySignListener(SeedRunner plugin) {
        this.plugin = plugin;
        this.lobbyManager = plugin.getLobbyManager();
    }

    // ── Clic droit sur la pancarte ────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        var clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;
        if (!lobbyManager.isLobbySign(clickedBlock)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        World runner = plugin.getServer().getWorld("runner");

        if (runner == null) {
            player.sendMessage(mm.deserialize(
                    "<dark_gray>[<gold>Runner</gold>]</dark_gray> <yellow>La map est en cours de création, patiente..."));
            return;
        }

        player.teleport(runner.getSpawnLocation());
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.sendMessage(mm.deserialize(
                "<dark_gray>[<gold>Runner</gold>]</dark_gray> <green>Bonne chance !"));
    }

    // ── Protection totale du lobby ────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (lobbyManager.isLobbyWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (lobbyManager.isLobbyWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }
}
