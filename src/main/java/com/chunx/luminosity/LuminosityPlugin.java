package com.chunx.luminosity;

import com.chunx.luminosity.abilities.Abilities;
import com.chunx.luminosity.abilities.Cooldowns;
import com.chunx.luminosity.abilities.SolarBurn;
import com.chunx.luminosity.commands.LuminosityCommand;
import com.chunx.luminosity.core.LuminosityService;
import com.chunx.luminosity.core.VoidBans;
import com.chunx.luminosity.data.DataStore;
import com.chunx.luminosity.data.PlayerData;
import com.chunx.luminosity.items.LuminousCore;
import com.chunx.luminosity.items.LuminousSigil;
import com.chunx.luminosity.listeners.CombatListener;
import com.chunx.luminosity.listeners.InteractListener;
import com.chunx.luminosity.listeners.LifecycleListener;
import com.chunx.luminosity.listeners.RevivalMenuListener;
import com.chunx.luminosity.tasks.BeaconMarkTask;
import com.chunx.luminosity.tasks.TraitTask;
import com.chunx.luminosity.tasks.VoidSightTask;
import com.chunx.luminosity.traits.TraitApplier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class LuminosityPlugin extends JavaPlugin {

    private DataStore store;
    private TraitApplier traits;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // An existing config.yml from an older release lacks newer keys; write them in
        // so they are visible to edit, without touching values already set.
        getConfig().options().copyDefaults(true);
        saveConfig();

        store = new DataStore(this);
        store.load();

        LuminousCore core = new LuminousCore(this);
        LuminousSigil sigil = new LuminousSigil(this);

        traits = new TraitApplier(this, store, sigil);
        LuminosityService service = new LuminosityService(store, traits);

        Cooldowns cooldowns = new Cooldowns();
        SolarBurn solarBurn = new SolarBurn(this);
        Abilities abilities = new Abilities(this, cooldowns, solarBurn);

        registerRecipe(core);
        registerListeners(service, core, sigil, abilities, solarBurn, cooldowns);
        registerCommand(service, core, sigil);
        startTasks(service);

        // A /reload leaves players online with stale state.
        for (Player player : Bukkit.getOnlinePlayers()) {
            traits.apply(player);
        }

        // Players the Void claimed under the old spectator-limbo release were never put on
        // the ban list; ban them now so upgrading does not quietly set them free.
        for (PlayerData data : store.all()) {
            if (data.voidLocked()) {
                VoidBans.ban(data.uuid());
            }
        }

        getLogger().info("Luminosity enabled.");
    }

    @Override
    public void onDisable() {
        // Attribute modifiers persist in player data, so strip them before unloading.
        for (Player player : Bukkit.getOnlinePlayers()) {
            traits.clear(player);
        }
        Bukkit.getScheduler().cancelTasks(this);
        if (store != null) {
            store.save();
        }
        getLogger().info("Luminosity disabled.");
    }

    private void registerRecipe(LuminousCore core) {
        // Re-registering on /reload throws unless the previous recipe is removed first.
        Bukkit.removeRecipe(core.recipeKey());
        Bukkit.addRecipe(core.recipe());
    }

    private void registerListeners(LuminosityService service, LuminousCore core, LuminousSigil sigil,
                                   Abilities abilities, SolarBurn solarBurn, Cooldowns cooldowns) {
        Material altar = altarMaterial();
        Bukkit.getPluginManager().registerEvents(
                new LifecycleListener(this, service, cooldowns), this);
        CombatListener.Tuning tuning = new CombatListener.Tuning(
                fraction("void-path.executioner-bonus", 0.08),
                fraction("void-path.phase-strike-armor-pierce", 0.15),
                fraction("void-path.vampirism-heal", 0.25));
        Bukkit.getPluginManager().registerEvents(
                new CombatListener(service, solarBurn, tuning), this);
        Bukkit.getPluginManager().registerEvents(
                new InteractListener(service, core, sigil, abilities, altar), this);
        Bukkit.getPluginManager().registerEvents(
                new RevivalMenuListener(core), this);
    }

    /** Reads a 0-1 tuning value, clamping anything out of range rather than failing. */
    private double fraction(String path, double fallback) {
        double value = getConfig().getDouble(path, fallback);
        if (value < 0 || value > 1) {
            getLogger().warning(path + " must be between 0 and 1; got " + value + ", clamping.");
        }
        return Math.max(0, Math.min(1, value));
    }

    private Material altarMaterial() {
        String configured = getConfig().getString("altar-block", "BEACON");
        Material material = Material.matchMaterial(configured.toUpperCase(Locale.ROOT));
        if (material == null || !material.isBlock()) {
            getLogger().warning("Unknown altar-block '" + configured + "', falling back to BEACON.");
            return Material.BEACON;
        }
        return material;
    }

    private void registerCommand(LuminosityService service, LuminousCore core, LuminousSigil sigil) {
        LuminosityCommand executor = new LuminosityCommand(service, core, sigil);
        PluginCommand command = getCommand("luminosity");
        if (command == null) {
            getLogger().severe("Command 'luminosity' missing from plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void startTasks(LuminosityService service) {
        new TraitTask(service).runTaskTimer(this, TraitTask.PERIOD_TICKS, TraitTask.PERIOD_TICKS);
        new VoidSightTask(service).runTaskTimer(this, VoidSightTask.PERIOD_TICKS, VoidSightTask.PERIOD_TICKS);
        new BeaconMarkTask(service).runTaskTimer(this, BeaconMarkTask.PERIOD_TICKS, BeaconMarkTask.PERIOD_TICKS);

        long saveTicks = 20L * 60 * Math.max(1, getConfig().getInt("autosave-minutes", 5));
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, store::save, saveTicks, saveTicks);
    }
}
