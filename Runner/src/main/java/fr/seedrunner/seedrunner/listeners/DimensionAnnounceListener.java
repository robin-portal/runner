package fr.seedrunner.seedrunner.listeners;

import fr.seedrunner.seedrunner.SeedRunner;
import fr.seedrunner.seedrunner.scoreboard.ChronoScoreboard;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * DimensionAnnounceListener — annonce le premier passage dans le Nether ou l'End.
 *
 * ═══════════════════════════════════════════════════════════════════
 * COMPORTEMENT
 * ═══════════════════════════════════════════════════════════════════
 *
 * Dès qu'un joueur arrive pour la première fois dans runner_nether
 * ou runner_the_end (dans la run en cours), on broadcast :
 *
 *   [NETHER] : 0h 8m 7s    (violet clair, NETHER en gras)
 *   [ENDER] : 0h 46m 12s   (violet clair, ENDER en gras)
 *
 * Sons :
 * - Le joueur qui entre : ENTITY_EXPERIENCE_ORB_PICKUP (discret, pour lui)
 * - Tous les autres joueurs connectés : ENTITY_PLAYER_LEVELUP (festif)
 *
 * Reset à chaque /run via resetAnnouncements().
 *
 * ═══════════════════════════════════════════════════════════════════
 * TECHNIQUE
 * ═══════════════════════════════════════════════════════════════════
 *
 * On écoute PlayerTeleportEvent avec cause NETHER_PORTAL ou END_PORTAL,
 * après que PortalListener a fait son setTo(). On vérifie le monde
 * de destination (event.getTo().getWorld()) pour savoir si c'est
 * runner_nether ou runner_the_end.
 *
 * On utilise deux boolean (netherAnnounced, endAnnounced) pour ne
 * broadcaster qu'une seule fois par run.
 */
public class DimensionAnnounceListener implements Listener {

    // Violet clair (light purple)
    private static final TextColor LIGHT_PURPLE = TextColor.fromHexString("#FF55FF");

    private final SeedRunner plugin;
    private final ChronoScoreboard chrono;

    private boolean netherAnnounced = false;
    private boolean endAnnounced    = false;

    public DimensionAnnounceListener(SeedRunner plugin) {
        this.plugin = plugin;
        this.chrono = plugin.getChronoScoreboard();
    }

    // ─────────────────────────────────────────────
    //  Reset à chaque /run
    // ─────────────────────────────────────────────

    public void resetAnnouncements() {
        netherAnnounced = false;
        endAnnounced    = false;
    }

    // ─────────────────────────────────────────────
    //  Écoute des téléportations par portail
    // ─────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortalTeleport(PlayerTeleportEvent event) {
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                && cause != PlayerTeleportEvent.TeleportCause.END_PORTAL) return;

        var to = event.getTo();
        if (to == null || to.getWorld() == null) return;

        String worldName = to.getWorld().getName();
        Player player = event.getPlayer();

        if (worldName.equals("runner_nether") && !netherAnnounced) {
            netherAnnounced = true;
            // On schedule 1 tick pour être sûr que le joueur est bien arrivé
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    announce(player, "NETHER"), 1L);
        } else if (worldName.equals("runner_the_end") && !endAnnounced) {
            endAnnounced = true;
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    announce(player, "ENDER"), 1L);
        }
    }

    // ─────────────────────────────────────────────
    //  Broadcast + sons
    // ─────────────────────────────────────────────

    private void announce(Player trigger, String dimension) {
        String timeStr = chrono.getElapsedFormatted();

        // Message : [NETHER] : 0h 8m 7s
        Component msg = Component.empty()
                .append(Component.text("[", LIGHT_PURPLE))
                .append(Component.text(dimension, LIGHT_PURPLE, TextDecoration.BOLD))
                .append(Component.text("] : ", LIGHT_PURPLE))
                .append(Component.text(timeStr, LIGHT_PURPLE));

        Bukkit.broadcast(msg);

        // Sons
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(trigger)) {
                // Son discret pour le joueur qui entre
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            } else {
                // Son festif pour tous les autres
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
        }
    }
}
