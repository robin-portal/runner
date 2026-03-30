package fr.seedrunner.seedrunner.shared;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;

/**
 * FoodListener — propage le niveau de faim.
 * Logique reprise de SharedLife v3.1.
 */
public class FoodListener implements Listener {

    private final SyncManager sync;

    public FoodListener(SyncManager sync) {
        this.sync = sync;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player source)) return;
        if (sync.isPropagating(source.getUniqueId())) return;

        int newFood = event.getFoodLevel();
        int oldFood = source.getFoodLevel();

        if (newFood > oldFood) {
            broadcastEatMessage(source, newFood);
        }

        sync.propagateFood(source, newFood);
    }

    private void broadcastEatMessage(Player source, int newFood) {
        String filled = newFood + "/20";
        Component msg = Component.empty()
                .append(Component.text(source.getName(), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" a mangé  ", NamedTextColor.GRAY))
                .append(Component.text(filled, NamedTextColor.GREEN));
        Bukkit.broadcast(msg);
    }
}
