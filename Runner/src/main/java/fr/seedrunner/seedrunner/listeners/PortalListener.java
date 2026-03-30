package fr.seedrunner.seedrunner.listeners;

import fr.seedrunner.seedrunner.SeedRunner;
import fr.seedrunner.seedrunner.managers.WorldManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * PortalListener — gestion des portails nether/end pour SeedRunner.
 *
 * ═══════════════════════════════════════════════════════════════════
 * POURQUOI CE LISTENER EST NÉCESSAIRE
 * ═══════════════════════════════════════════════════════════════════
 *
 * Paper résout la destination d'un portail nether/end en se basant
 * uniquement sur le monde principal défini dans server.properties
 * (level-name). Si level-name=world, Paper cherche "world_nether"
 * et "world_the_end" comme destinations de portail — et seulement eux.
 *
 * Quand SeedRunner crée des mondes custom ("runner", "runner_nether",
 * "runner_the_end"), Paper ne les connaît pas comme triplet
 * overworld/nether/end. Résultat : les portails dans "runner" pointent
 * vers "world_nether" (le nether vanilla), pas "runner_nether".
 *
 * Ce listener intercepte PlayerPortalEvent et EntityPortalEvent
 * AVANT que Paper ne téléporte, et redirige event.setTo() vers le
 * bon monde SeedRunner.
 *
 * ═══════════════════════════════════════════════════════════════════
 * LOGIQUE DE REDIRECTION
 * ═══════════════════════════════════════════════════════════════════
 *
 * SeedRunner nomme toujours ses mondes selon la convention :
 *   BASE           → overworld  (ex : "runner")
 *   BASE_nether    → nether     (ex : "runner_nether")
 *   BASE_the_end   → end        (ex : "runner_the_end")
 *
 * On détermine la base depuis le nom du monde d'origine :
 *   "runner"          → base = "runner"
 *   "runner_nether"   → base = "runner"
 *   "runner_the_end"  → base = "runner"
 *
 * Puis, selon l'environment d'origine et le type de portail (cause),
 * on calcule la destination attendue et on remplace event.getTo().
 *
 * ═══════════════════════════════════════════════════════════════════
 * NOTES TECHNIQUES (Paper 1.21.1)
 * ═══════════════════════════════════════════════════════════════════
 *
 * - event.getTo() peut être null sur Spigot mais jamais sur Paper 1.16+
 *   pour NETHER_PORTAL/END_PORTAL — on null-check quand même.
 * - event.setTo(location) remplace la destination ET le monde cible.
 *   Paper respecte ce setTo et téléporte vers le monde de la Location.
 * - On laisse canCreatePortal=true pour que Paper génère un portail
 *   de sortie dans le monde cible si nécessaire.
 * - On utilise EventPriority.HIGH pour s'exécuter après les plugins
 *   qui modifient le from, mais avant ceux qui annulent l'event.
 * - EntityPortalEvent est aussi intercepté pour les mobs/items qui
 *   passent dans les portails.
 *
 * ═══════════════════════════════════════════════════════════════════
 */
public class PortalListener implements Listener {

    private final SeedRunner plugin;
    private final WorldManager worldManager;

    public PortalListener(SeedRunner plugin) {
        this.plugin = plugin;
        this.worldManager = plugin.getWorldManager();
    }

    // ─────────────────────────────────────────────────────────────
    //  Portail joueur
    // ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        PlayerTeleportEvent.TeleportCause cause = event.getCause();

        // On ne gère que les portails nether et end vanilla
        if (cause != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                && cause != PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            return;
        }

        World fromWorld = event.getFrom().getWorld();
        if (fromWorld == null) return;

        // Le joueur vient-il d'un monde SeedRunner ?
        String base = worldManager.getRunnerBase(fromWorld.getName());
        if (base == null) return; // monde non géré par SeedRunner, on laisse faire Paper

        World destination = resolveDestination(base, fromWorld, cause);
        if (destination == null) {
            // Le monde cible n'est pas chargé. On annule pour éviter que Paper
            // redirige vers world_nether / world_the_end (le monde vanilla).
            event.setCancelled(true);
            return;
        }

        // Calcule la position de destination (spawn du monde cible)
        // Paper se chargera de chercher/créer un portail autour de ce point
        Location spawnLoc = destination.getSpawnLocation();

        // Pour le nether : applique l'échelle 1/8 depuis la position du joueur
        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                && fromWorld.getEnvironment() == World.Environment.NORMAL) {
            // Overworld → Nether : divise les coordonnées par 8
            Location from = event.getFrom();
            spawnLoc = new Location(
                    destination,
                    from.getX() / 8.0,
                    from.getY(),
                    from.getZ() / 8.0
            );
        } else if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                && fromWorld.getEnvironment() == World.Environment.NETHER) {
            // Nether → Overworld : multiplie les coordonnées par 8
            Location from = event.getFrom();
            spawnLoc = new Location(
                    destination,
                    from.getX() * 8.0,
                    from.getY(),
                    from.getZ() * 8.0
            );
        }

        event.setTo(spawnLoc);
        // On laisse canCreatePortal à true (valeur par défaut) :
        // Paper cherchera un portail existant autour de spawnLoc
        // et en créera un si besoin.
    }

    // ─────────────────────────────────────────────────────────────
    //  Portail entité (mobs, items qui tombent dans un portail)
    // ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        World fromWorld = event.getFrom().getWorld();
        if (fromWorld == null) return;

        String base = worldManager.getRunnerBase(fromWorld.getName());
        if (base == null) return;

        // Détermine la cause depuis l'environment (EntityPortalEvent n'a pas de TeleportCause)
        PlayerTeleportEvent.TeleportCause cause;
        if (fromWorld.getEnvironment() == World.Environment.NORMAL
                || fromWorld.getEnvironment() == World.Environment.NETHER) {
            cause = PlayerTeleportEvent.TeleportCause.NETHER_PORTAL;
        } else {
            cause = PlayerTeleportEvent.TeleportCause.END_PORTAL;
        }

        World destination = resolveDestination(base, fromWorld, cause);
        if (destination == null) {
            event.setCancelled(true);
            return;
        }

        event.setTo(destination.getSpawnLocation());
    }

    // ─────────────────────────────────────────────────────────────
    //  Calcul de la destination
    // ─────────────────────────────────────────────────────────────

    /**
     * Retourne le monde SeedRunner de destination en fonction :
     * - du base name (ex : "runner")
     * - de l'environment du monde d'origine
     * - du type de portail (cause)
     *
     * Retourne null si le monde cible n'est pas chargé.
     */
    private World resolveDestination(String base,
                                      World fromWorld,
                                      PlayerTeleportEvent.TeleportCause cause) {
        String targetName;

        switch (fromWorld.getEnvironment()) {
            case NORMAL -> {
                // Overworld → portail nether = aller au nether runner
                // Overworld → portail end    = aller à l'end runner
                if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
                    targetName = base + "_nether";
                } else {
                    targetName = base + "_the_end";
                }
            }
            case NETHER -> {
                // Nether → overworld (quel que soit le type de portail)
                targetName = base;
            }
            case THE_END -> {
                // End → overworld (le portail de fin de l'End ramène à l'overworld)
                targetName = base;
            }
            default -> { return null; }
        }

        return plugin.getServer().getWorld(targetName);
        // Retourne null si le monde n'est pas chargé — Paper annulera alors la tp
    }
}
