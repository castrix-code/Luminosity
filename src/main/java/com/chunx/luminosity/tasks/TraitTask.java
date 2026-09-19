package com.chunx.luminosity.tasks;

import com.chunx.luminosity.core.LuminosityService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/** Drives every condition-dependent trait, once per second for each online player. */
public final class TraitTask extends BukkitRunnable {

    public static final long PERIOD_TICKS = 20L;

    private final LuminosityService service;

    public TraitTask(LuminosityService service) {
        this.service = service;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            service.traits().tick(player);
        }
    }
}
