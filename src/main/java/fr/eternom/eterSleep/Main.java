package fr.eternom.eterSleep;

import fr.eternom.eterLib.EterLib;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterSleep.module.sleep.SleepListener;
import fr.eternom.eterSleep.module.sleep.SleepService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;

/** Passer la nuit sans attendre tout le monde : seuls les joueurs actifs comptent (pas les AFK ni les invisibles). */
public final class Main extends JavaPlugin {

    /** Activité des joueurs (AFK) depuis 1.18.0. */
    private static final String REQUIRED_ETERLIB = "1.18.0";

    private SleepService sleep;

    @Override
    public void onEnable() {
        // En premier : vérifie la version d'EterLib (un EterLib < 1.3.0 n'a pas requireVersion, d'où le catch)
        try {
            if (!EterLib.requireVersion(this, REQUIRED_ETERLIB)) {
                return;
            }
        } catch (LinkageError tooOld) {
            getLogger().severe("EterLib " + REQUIRED_ETERLIB + " ou plus récent est nécessaire.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();

        EterLib lib = EterLib.get();
        Messages messages = lib.messages(this, "en_us", "fr_fr");
        sleep = new SleepService(this, messages, lib.getActivity(), lib.getVanish(),
                getConfig().getInt("percentage", 50), getConfig().getInt("skip-speed", 100),
                new HashSet<>(getConfig().getStringList("worlds")));
        getServer().getPluginManager().registerEvents(new SleepListener(this, sleep), this);
        sleep.start();
    }

    @Override
    public void onDisable() {
        if (sleep != null) {
            sleep.stop();
        }
    }
}
