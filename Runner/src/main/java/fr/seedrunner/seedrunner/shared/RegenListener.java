package fr.seedrunner.seedrunner.shared;

import fr.seedrunner.seedrunner.SeedRunner;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;

/**
 * RegenListener — propage la régénération de HP.
 * Logique reprise de SharedLife v3.1.
 *
 * On schedule la propagation 1 tick plus tard pour lire le HP final
 * après que Paper l'a appliqué.
 */
public class RegenListener implements Listener {

    private final SeedRunner plugin;
    private final SyncManager sync;

    public RegenListener(SeedRunner plugin, SyncManager sync) {
        this.plugin = plugin;
        this.sync = sync;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player source)) return;
        if (sync.isPropagating(source.getUniqueId())) return;

        // Lecture du HP réel 1 tick après l'event pour avoir la valeur finale
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!source.isOnline() || source.isDead()) return;
            sync.propagateHealth(source, source.getHealth());
        });
    }
}
