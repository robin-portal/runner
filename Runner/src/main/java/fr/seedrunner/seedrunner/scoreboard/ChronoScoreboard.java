package fr.seedrunner.seedrunner.scoreboard;

import fr.seedrunner.seedrunner.SeedRunner;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

/**
 * ChronoScoreboard — scoreboard latéral avec chrono.
 *
 * Repris et adapté de SharedLife v3.1 :
 * - Plus de commande /chrono : le chrono se reset via resetAndStart()
 *   appelé par WorldManager lors du /run.
 * - Titre : ⏱ [blanc gras] Timer [jaune clair] chrono
 * - Le chrono s'affiche en jaune clair (#FFFF55)
 * - Format : 00h 00m 00s
 *
 * Structure du scoreboard (entrées factices = lignes d'affichage) :
 *   [blank1]
 *   00h 00m 00s   ← temps écoulé
 *   [blank2]
 */
public class ChronoScoreboard implements Listener {

    private static final TextColor YELLOW_LIGHT = TextColor.fromHexString("#FFFF55");

    private final SeedRunner plugin;
    private Scoreboard board;
    private Objective objective;
    private int taskId = -1;
    private long startTimeMillis = 0;
    private boolean running = false;

    public ChronoScoreboard(SeedRunner plugin) {
        this.plugin = plugin;
        buildScoreboard();
        // Écoute join/quit pour assigner le scoreboard
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ─────────────────────────────────────────────
    //  Construction du scoreboard
    // ─────────────────────────────────────────────

    private void buildScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        board = manager.getNewScoreboard();

        // Titre : ⏱ [blanc gras] + " Timer" [jaune clair]
        Component title = Component.empty()
                .append(Component.text("⏱ ", NamedTextColor.WHITE))
                .append(Component.text("Timer", NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" ", NamedTextColor.WHITE));

        objective = board.registerNewObjective("runner_chrono", Criteria.DUMMY, title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        renderStopped();
    }

    // ─────────────────────────────────────────────
    //  Démarrage / reset
    // ─────────────────────────────────────────────

    /**
     * Remet le chrono à 0 et le démarre.
     * Appelé par WorldManager à chaque /run.
     */
    public void resetAndStart() {
        // Annule la tâche existante
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        startTimeMillis = System.currentTimeMillis();
        running = true;

        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 20L).getTaskId();

        // Assigne le scoreboard à tous les joueurs connectés
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(board);
        }
    }

    // ─────────────────────────────────────────────
    //  Arrêt
    // ─────────────────────────────────────────────

    public void stop() {
        running = false;
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager != null) p.setScoreboard(manager.getMainScoreboard());
        }
    }

    /**
     * Retourne le temps écoulé formaté "0h 8m 7s" — utilisé par DimensionAnnounceListener.
     */
    public String getElapsedFormatted() {
        if (!running) return "0h 0m 0s";
        long elapsed  = (System.currentTimeMillis() - startTimeMillis) / 1000;
        long hours    = elapsed / 3600;
        long minutes  = (elapsed % 3600) / 60;
        long seconds  = elapsed % 60;
        return hours + "h " + minutes + "m " + seconds + "s";
    }

    // ─────────────────────────────────────────────
    //  Tick (toutes les secondes)
    // ─────────────────────────────────────────────

    private void tick() {
        if (!running) return;

        long elapsed  = (System.currentTimeMillis() - startTimeMillis) / 1000;
        long hours    = elapsed / 3600;
        long minutes  = (elapsed % 3600) / 60;
        long seconds  = elapsed % 60;

        String timeStr = String.format("%02dh %02dm %02ds", hours, minutes, seconds);

        // Efface les anciennes entrées
        for (String entry : new java.util.HashSet<>(board.getEntries())) {
            board.resetScores(entry);
        }

        // Ligne vide haute
        objective.getScore(" ").setScore(3);
        // Chrono en jaune clair — on encode la couleur dans le nom de l'entrée
        // via un préfixe de couleur legacy (§e = jaune)
        objective.getScore("§e" + timeStr).setScore(2);
        // Ligne vide basse
        objective.getScore("  ").setScore(1);
    }

    // ─────────────────────────────────────────────
    //  Affichage avant démarrage
    // ─────────────────────────────────────────────

    private void renderStopped() {
        objective.getScore(" ").setScore(2);
        objective.getScore("§7/run pour démarrer").setScore(1);
    }

    // ─────────────────────────────────────────────
    //  Events joueur
    // ─────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Assigne le scoreboard 1 tick après la connexion (Paper recommandé)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player p = event.getPlayer();
            if (p.isOnline()) p.setScoreboard(board);
        }, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) event.getPlayer().setScoreboard(manager.getMainScoreboard());
    }
}
