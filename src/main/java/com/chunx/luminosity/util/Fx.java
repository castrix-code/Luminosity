package com.chunx.luminosity.util;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Version-tolerant particle spawning.
 *
 * <p>Minecraft keeps adding required options to existing particles: by 1.21.11
 * {@code FLASH} demands a {@link Color} and {@code DRAGON_BREATH} a {@link Float},
 * where 1.21.4 took neither. A particle spawned without its required data throws,
 * and when that happens mid-ability it aborts the gameplay after it. So this asks
 * the running server what each particle needs, supplies a sensible default, and
 * never lets a purely cosmetic failure escape.
 */
public final class Fx {

    private static final Color DEFAULT_COLOR = Color.fromRGB(255, 240, 150);
    private static final Set<Particle> WARNED = ConcurrentHashMap.newKeySet();

    private Fx() {
    }

    /** Spawns a particle, filling in any data the running server version requires. */
    public static void spawn(Location at, Particle particle, int count,
                             double offsetX, double offsetY, double offsetZ, double extra) {
        spawn(at, particle, count, offsetX, offsetY, offsetZ, extra, defaultData(particle));
    }

    /** Spawns a particle with explicit data, e.g. {@link Particle.DustOptions}. */
    public static void spawn(Location at, Particle particle, int count,
                             double offsetX, double offsetY, double offsetZ, double extra, Object data) {
        if (at.getWorld() == null) {
            return;
        }
        try {
            at.getWorld().spawnParticle(particle, at, count, offsetX, offsetY, offsetZ, extra, data);
        } catch (RuntimeException ex) {
            if (WARNED.add(particle)) {
                Bukkit.getLogger().warning("[Luminosity] Skipping particle " + particle
                        + " on this server version: " + ex.getMessage());
            }
        }
    }

    private static Object defaultData(Particle particle) {
        Class<?> type = particle.getDataType();
        if (type == Color.class) {
            return DEFAULT_COLOR;
        }
        if (type == Float.class) {
            return 1.0f;
        }
        return null;
    }
}
