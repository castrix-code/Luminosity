package com.chunx.luminosity.tasks;

import com.chunx.luminosity.core.LuminosityService;
import com.chunx.luminosity.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Void Stage 3 - Void Sight. Bukkit's {@code setGlowing} is global entity metadata and
 * would reveal targets to the whole server, so instead each Revenant is sent a private
 * silhouette of every player within 40 blocks. {@link Player#spawnParticle} delivers to
 * that one client only, and particles render through walls.
 */
public final class VoidSightTask extends BukkitRunnable {

    public static final long PERIOD_TICKS = 10L;

    private static final double RADIUS = 40.0;
    private static final Particle.DustOptions OUTLINE =
            new Particle.DustOptions(Color.fromRGB(190, 60, 255), 1.0f);

    private final LuminosityService service;

    public VoidSightTask(LuminosityService service) {
        this.service = service;
    }

    @Override
    public void run() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            PlayerData data = service.data(viewer);
            if (data.voidLocked() || !data.hasVoidTier(3)) {
                continue;
            }
            for (Player target : viewer.getWorld().getPlayers()) {
                if (target.equals(viewer)) {
                    continue;
                }
                if (target.getLocation().distanceSquared(viewer.getLocation()) > RADIUS * RADIUS) {
                    continue;
                }
                outline(viewer, target);
            }
        }
    }

    /** Traces a short column up the target's body so it reads as an outline, not a blob. */
    private void outline(Player viewer, Player target) {
        Location base = target.getLocation();
        for (double y = 0.15; y <= 1.9; y += 0.35) {
            viewer.spawnParticle(Particle.DUST, base.clone().add(0, y, 0), 1, 0.18, 0.0, 0.18, 0.0, OUTLINE);
        }
    }
}
