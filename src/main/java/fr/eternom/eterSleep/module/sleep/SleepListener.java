package fr.eternom.eterSleep.module.sleep;

import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class SleepListener implements Listener {

    private final JavaPlugin plugin;
    private final SleepService sleep;

    public SleepListener(JavaPlugin plugin, SleepService sleep) {
        this.plugin = plugin;
        this.sleep = sleep;
    }

    /** Le joueur n'est vraiment couché qu'au tick suivant (refusé : entered ne le voit pas au lit et ne dit rien). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                sleep.entered(player);
            }
        });
    }

    /** Un monde chargé plus tard (multivers...) : la règle de Minecraft laisse aussi la main à EterSleep. */
    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (sleep.manages(event.getWorld())) {
            event.getWorld().setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE, 101);
        }
    }
}
