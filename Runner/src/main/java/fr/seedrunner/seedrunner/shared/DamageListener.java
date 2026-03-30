package fr.seedrunner.seedrunner.shared;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * DamageListener — propage les dégâts à tous les joueurs.
 * Logique reprise de SharedLife v3.1.
 *
 * Priority MONITOR + ignoreCancelled=true : on agit après que Paper
 * a calculé le HP final, sans interférer avec d'autres plugins.
 * On utilise un verrou (isPropagating) pour éviter les boucles infinies.
 */
public class DamageListener implements Listener {

    private final SyncManager sync;

    public DamageListener(SyncManager sync) {
        this.sync = sync;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player source)) return;
        if (sync.isPropagating(source.getUniqueId())) return;

        double finalDamage = event.getFinalDamage();
        EntityDamageEvent.DamageCause cause = event.getCause();

        broadcastDamageMessage(source, finalDamage, cause);

        double newHp = Math.max(0, source.getHealth() - finalDamage);

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(source)) continue;
            if (other.isDead()) continue;
            other.playHurtAnimation(0f);
            sync.lock(other.getUniqueId());
            double targetHp = Math.min(newHp, other.getMaxHealth());
            other.setHealth(Math.ceil(targetHp));
            sync.unlock(other.getUniqueId());
        }
    }

    private void broadcastDamageMessage(Player source, double finalDamage,
                                         EntityDamageEvent.DamageCause cause) {
        int demiCoeurs = (int) Math.ceil(finalDamage);
        Component msg = Component.empty()
                .append(Component.text(source.getName(), NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(" a pris ", NamedTextColor.GRAY))
                .append(Component.text(demiCoeurs + "❤", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(" (" + translateCause(cause) + ")", NamedTextColor.DARK_GRAY));
        Bukkit.broadcast(msg);
    }

    private String translateCause(EntityDamageEvent.DamageCause cause) {
        return switch (cause) {
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK -> "Attaque";
            case PROJECTILE                         -> "Projectile";
            case FALL                               -> "Chute";
            case LAVA                               -> "Lave";
            case DROWNING                           -> "Noyade";
            case STARVATION                         -> "Famine";
            case POISON                             -> "Poison";
            case MAGIC                              -> "Magie";
            case VOID                               -> "Vide";
            case LIGHTNING                          -> "Foudre";
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION  -> "Explosion";
            case SUFFOCATION                        -> "Suffocation";
            case WITHER                             -> "Wither";
            case CONTACT                            -> "Contact";
            case CRAMMING                           -> "Ecrasement";
            case SONIC_BOOM                         -> "Sonic Boom";
            default                                 -> cause.name();
        };
    }
}
