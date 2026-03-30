package fr.seedrunner.seedrunner.shared;

import fr.seedrunner.seedrunner.SeedRunner;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * SyncManager — synchronise HP, absorption et faim entre tous les joueurs en ligne.
 *
 * Repris de SharedLife v3.1 :
 * - propagating : set de UUID en cours de propagation (verrou anti-boucle infinie)
 * - propagateHealth / propagateAbsorption / propagateFood : applique la valeur
 *   à tous les autres joueurs vivants
 * - startSecuritySync : tâche toutes les 20 ticks qui force la resynchronisation
 *   en cas de désync (lag, connexion, etc.)
 *
 * Ajout : enabled flag pour activer/désactiver via /share.
 */
public class SyncManager {

    private final SeedRunner plugin;
    private final Set<UUID> propagating = new HashSet<>();
    private boolean enabled = false; // désactivé par défaut, /share pour activer

    public SyncManager(SeedRunner plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────────────────────────────
    //  Activation
    // ─────────────────────────────────────────────

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    // ─────────────────────────────────────────────
    //  Verrou anti-boucle
    // ─────────────────────────────────────────────

    public boolean isPropagating(UUID uuid) { return propagating.contains(uuid); }

    public void lock(UUID uuid)   { propagating.add(uuid); }
    public void unlock(UUID uuid) { propagating.remove(uuid); }

    // ─────────────────────────────────────────────
    //  Propagation HP
    // ─────────────────────────────────────────────

    public void propagateHealth(Player source, double newHp) {
        if (!enabled) return;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(source)) continue;
            if (other.isDead()) continue;
            try {
                lock(other.getUniqueId());
                double targetHp = Math.min(newHp, other.getMaxHealth());
                other.setHealth(targetHp);
                unlock(other.getUniqueId());
            } catch (Exception e) {
                unlock(other.getUniqueId());
                plugin.getLogger().warning("propagateHealth error: " + e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────
    //  Propagation absorption
    // ─────────────────────────────────────────────

    public void propagateAbsorption(Player source, double targetAbs) {
        if (!enabled) return;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(source)) continue;
            try {
                lock(other.getUniqueId());
                other.setAbsorptionAmount(targetAbs);
                unlock(other.getUniqueId());
            } catch (Exception e) {
                unlock(other.getUniqueId());
                plugin.getLogger().warning("propagateAbsorption error: " + e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────
    //  Propagation faim
    // ─────────────────────────────────────────────

    public void propagateFood(Player source, int targetFood) {
        if (!enabled) return;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(source)) continue;
            try {
                lock(other.getUniqueId());
                other.setFoodLevel(targetFood);
                unlock(other.getUniqueId());
            } catch (Exception e) {
                unlock(other.getUniqueId());
                plugin.getLogger().warning("propagateFood error: " + e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────
    //  Resync de sécurité (toutes les 20 ticks)
    // ─────────────────────────────────────────────

    /**
     * Tâche périodique : toutes les 20 ticks, on prend le minimum de HP/abs/faim
     * parmi tous les joueurs vivants et on force tout le monde à cette valeur.
     * Évite les désynchronisations dues au lag ou aux reconnexions.
     */
    public void startSecuritySync() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::securityResync, 20L, 20L);
    }

    private void securityResync() {
        if (!enabled) return;

        List<Player> alive = new ArrayList<>(Bukkit.getOnlinePlayers())
                .stream()
                .filter(p -> !p.isDead())
                .collect(Collectors.toList());

        if (alive.size() < 2) return;

        double minHp  = alive.stream().mapToDouble(Player::getHealth).min().orElse(20);
        double minAbs = alive.stream().mapToDouble(Player::getAbsorptionAmount).min().orElse(0);
        int minFood   = alive.stream().mapToInt(Player::getFoodLevel).min().orElse(20);

        boolean anyFixed = false;
        for (Player p : alive) {
            if (isPropagating(p.getUniqueId())) continue;
            boolean fixed = false;
            if (p.getHealth() > minHp) { p.setHealth(minHp); fixed = true; }
            if (p.getAbsorptionAmount() > minAbs) { p.setAbsorptionAmount(minAbs); fixed = true; }
            if (p.getFoodLevel() > minFood) { p.setFoodLevel(minFood); fixed = true; }
            if (fixed) anyFixed = true;
        }

        if (anyFixed) {
            plugin.getLogger().info(String.format(
                    "[Runner] Resync sécurité - HP: %.1f | Abs: %.1f | Faim: %d",
                    minHp, minAbs, minFood));
        }
    }
}
