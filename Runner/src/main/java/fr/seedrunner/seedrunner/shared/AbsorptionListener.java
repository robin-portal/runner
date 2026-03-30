package fr.seedrunner.seedrunner.shared;

import fr.seedrunner.seedrunner.SeedRunner;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffectType;

/**
 * AbsorptionListener — propage les coeurs d'absorption.
 * Logique reprise de SharedLife v3.1.
 *
 * On écoute EntityPotionEffectEvent pour détecter l'ajout/suppression
 * d'absorption, puis on schedule la lecture du montant réel 1 tick après.
 */
public class AbsorptionListener implements Listener {

    private final SeedRunner plugin;
    private final SyncManager sync;

    public AbsorptionListener(SeedRunner plugin, SyncManager sync) {
        this.plugin = plugin;
        this.sync = sync;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player source)) return;
        if (sync.isPropagating(source.getUniqueId())) return;

        // On ne gère que l'absorption
        boolean isAbsorption =
                (event.getNewEffect() != null && event.getNewEffect().getType().equals(PotionEffectType.ABSORPTION))
                || (event.getOldEffect() != null && event.getOldEffect().getType().equals(PotionEffectType.ABSORPTION));
        if (!isAbsorption) return;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!source.isOnline()) return;
            sync.propagateAbsorption(source, source.getAbsorptionAmount());
        });
    }
}
