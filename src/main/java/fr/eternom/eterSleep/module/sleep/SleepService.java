package fr.eternom.eterSleep.module.sleep;

import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.module.activity.Activity;
import fr.eternom.eterLib.module.vanish.Vanish;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * La nuit passe quand assez de joueurs sont au lit : percentage % des joueurs comptés du monde. Comptés : les joueurs
 * au lit (toujours, même immobiles ou avec etersleep.ignore), et les autres s'ils ne sont pas AFK (activité d'EterLib)
 * ni etersleep.ignore ; jamais les invisibles ni les spectateurs. La nuit défile alors en quelques secondes (skip-speed ticks par tick), puis la
 * pluie s'arrête. La règle de Minecraft (playersSleepingPercentage) est mise à 101 dans les mondes gérés : c'est
 * EterSleep qui décide ; elle reprend sa valeur d'avant à l'arrêt (sinon, plugin retiré, plus personne ne passerait la
 * nuit). Thread principal.
 */
public class SleepService {

    public static final String IGNORE_PERMISSION = "etersleep.ignore";
    /** Lit possible la nuit (sans orage) entre ces heures du jour de Minecraft. */
    private static final long NIGHT_START = 12542;
    private static final long NIGHT_END = 23459;
    private static final long DAY_LENGTH = 24000;
    /** Un même joueur n'est pas annoncé au lit plus souvent (il peut se lever et se recoucher en boucle). */
    private static final long ANNOUNCE_MILLIS = 10_000;

    /** Joueurs au lit, joueurs nécessaires, joueurs comptés. */
    public record Count(int sleeping, int needed, int counted) {

        public boolean enough() {
            return sleeping > 0 && sleeping >= needed;
        }
    }

    private final JavaPlugin plugin;
    private final Messages messages;
    private final Activity activity;
    private final Vanish vanish;
    private final int percentage;
    private final long skipSpeed;
    /** Mondes gérés par nom ; vide : tous les mondes normaux (pas le Nether ni l'End). */
    private final Set<String> worlds;
    /** Mondes dont la nuit est en train de défiler. */
    private final Set<String> skipping = new HashSet<>();
    /** Valeur de playersSleepingPercentage avant EterSleep, par monde, rendue à l'arrêt. */
    private final Map<String, Integer> previousRule = new HashMap<>();
    private final Map<UUID, Long> lastAnnounce = new HashMap<>();

    public SleepService(JavaPlugin plugin, Messages messages, Activity activity, Vanish vanish, int percentage, int skipSpeed,
                        Set<String> worlds) {
        this.plugin = plugin;
        this.messages = messages;
        this.activity = activity;
        this.vanish = vanish;
        this.percentage = Math.clamp(percentage, 1, 100);
        this.skipSpeed = Math.max(10, skipSpeed);
        this.worlds = worlds;
    }

    public void start() {
        Bukkit.getWorlds().forEach(this::takeOver);
        Bukkit.getScheduler().runTaskTimer(plugin, this::check, 20, 10);
        Bukkit.getScheduler().runTaskTimer(plugin, this::advance, 1, 1);
    }

    /** La règle de Minecraft laisse la main à EterSleep dans ce monde (s'il est géré) ; l'ancienne valeur est gardée. */
    public void takeOver(World world) {
        if (manages(world)) {
            Integer before = world.getGameRuleValue(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
            // 101 : laissé par un arrêt brutal d'EterSleep (pas de stop), on rendra la valeur de Minecraft
            previousRule.putIfAbsent(world.getName(), before == null || before == 101 ? 100 : before);
            world.setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE, 101);
        }
    }

    /** À l'arrêt : chaque monde retrouve sa règle d'avant. */
    public void stop() {
        previousRule.forEach((name, value) -> {
            World world = Bukkit.getWorld(name);
            if (world != null && value != null) {
                world.setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE, value);
            }
        });
    }

    public boolean manages(World world) {
        return world.getEnvironment() == World.Environment.NORMAL && (worlds.isEmpty() || worlds.contains(world.getName()));
    }

    public Count count(World world) {
        int sleeping = 0;
        int counted = 0;
        for (Player player : world.getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || vanish.isVanished(player.getUniqueId())) {
                continue;
            }
            // Au lit : compte toujours, même avec etersleep.ignore (le staff a souvent * dans LuckPerms)
            if (player.isSleeping()) {
                sleeping++;
                counted++;
            } else if (!player.isSleepingIgnored() && !player.hasPermission(IGNORE_PERMISSION) && !activity.isAfk(player)) {
                counted++;
            }
        }
        int needed = Math.max(1, (int) Math.ceil(counted * percentage / 100.0));
        return new Count(sleeping, needed, counted);
    }

    /** Un joueur vient de se coucher (un tick après) : prévient le monde. */
    public void entered(Player player) {
        World world = player.getWorld();
        // Un invisible au lit ne compte pas : l'annoncer le trahirait
        if (!manages(world) || !player.isSleeping() || skipping.contains(world.getName())
                || vanish.isVanished(player.getUniqueId())) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastAnnounce.put(player.getUniqueId(), now);
        if (last != null && now - last < ANNOUNCE_MILLIS) {
            return;
        }
        Count count = count(world);
        for (Player other : world.getPlayers()) {
            messages.send(other, "sleep.entered", "player", player.getName(),
                    "sleeping", String.valueOf(count.sleeping()), "needed", String.valueOf(count.needed()));
        }
    }

    /** Toutes les demi-secondes : la barre d'action (compte, ou nuit qui défile), et le début de la nuit qui défile. */
    private void check() {
        for (World world : Bukkit.getWorlds()) {
            if (!manages(world)) {
                continue;
            }
            if (skipping.contains(world.getName())) {
                world.getPlayers().forEach(player -> messages.actionBar(player, "sleep.skipping"));
                continue;
            }
            Count count = count(world);
            if (count.sleeping() == 0) {
                continue;
            }
            if (count.enough() && (isNight(world) || world.isThundering())) {
                skipping.add(world.getName());
                continue;
            }
            for (Player player : world.getPlayers()) {
                messages.actionBar(player, "sleep.waiting", "sleeping", String.valueOf(count.sleeping()),
                        "needed", String.valueOf(count.needed()));
            }
        }
    }

    /** Chaque tick : la nuit défile dans les mondes concernés, jusqu'au matin. */
    private void advance() {
        if (skipping.isEmpty()) {
            return;
        }
        for (String name : Set.copyOf(skipping)) {
            World world = Bukkit.getWorld(name);
            if (world == null) {
                skipping.remove(name);
                continue;
            }
            long untilMorning = (DAY_LENGTH - world.getFullTime() % DAY_LENGTH) % DAY_LENGTH;
            if (!isNight(world) || untilMorning <= skipSpeed) {
                // Le jour complet suivant : le compteur de jours avance (phases de lune, jours de Minecraft)
                world.setFullTime(world.getFullTime() + untilMorning);
                morning(world);
                skipping.remove(name);
                continue;
            }
            world.setFullTime(world.getFullTime() + skipSpeed);
        }
    }

    private void morning(World world) {
        world.setStorm(false);
        world.setThundering(false);
        for (Player player : world.getPlayers()) {
            if (player.isSleeping()) {
                // Une nuit de sommeil : plus de phantoms pour ce joueur
                player.setStatistic(Statistic.TIME_SINCE_REST, 0);
                player.wakeup(false);
            }
            messages.send(player, "sleep.morning");
            lastAnnounce.remove(player.getUniqueId());
        }
    }

    private static boolean isNight(World world) {
        long time = world.getTime();
        return time >= NIGHT_START && time <= NIGHT_END;
    }
}
