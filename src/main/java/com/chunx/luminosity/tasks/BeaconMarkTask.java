package com.chunx.luminosity.tasks;

import com.chunx.luminosity.core.LuminosityService;
import com.chunx.luminosity.data.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Light Stage 3 penalty - Beacon Mark. Every five minutes a Solar Deity's position is
 * announced to the server and a pillar of light marks the spot.
 */
public final class BeaconMarkTask extends BukkitRunnable {

    public static final long PERIOD_TICKS = 20L * 60 * 5;

    private static final int PILLAR_HEIGHT = 60;
    private static final Particle.DustOptions BEAM =
            new Particle.DustOptions(Color.fromRGB(255, 246, 176), 2.5f);

    private final LuminosityService service;

    public BeaconMarkTask(LuminosityService service) {
        this.service = service;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = service.data(player);
            if (data.voidLocked() || !data.hasLightTier(3)) {
                continue;
            }
            mark(player);
        }
    }

    private void mark(Player player) {
        Location base = player.getLocation();
        for (int y = 0; y < PILLAR_HEIGHT; y++) {
            base.getWorld().spawnParticle(Particle.DUST, base.clone().add(0, y, 0), 4,
                    0.15, 0.2, 0.15, BEAM);
        }
        base.getWorld().playSound(base, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.0f);

        Bukkit.broadcast(Component.text("The light of " + player.getName() + " burns at ",
                        NamedTextColor.GOLD)
                .append(Component.text(base.getBlockX() + ", " + base.getBlockY() + ", " + base.getBlockZ()
                        + " (" + base.getWorld().getName() + ")", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("Your Beacon Mark has given you away.", NamedTextColor.GRAY));
    }
}
