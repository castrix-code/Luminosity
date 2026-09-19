package com.chunx.luminosity.traits;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Environmental predicates shared by the sun/shadow traits. */
public final class Env {

    private Env() {
    }

    public static boolean isDaytime(World world) {
        long time = world.getTime();
        return time < 12300 || time > 23850;
    }

    public static boolean isNight(World world) {
        return !isDaytime(world);
    }

    /** Minecraft's "midday" window, when the sun is directly overhead. */
    public static boolean isMidday(World world) {
        long time = world.getTime();
        return time >= 4000 && time <= 8000;
    }

    /**
     * Direct sunlight: overworld, daytime, clear weather at the player's position,
     * and nothing between the player and the sky.
     */
    public static boolean inDirectSunlight(Player player) {
        World world = player.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return false;
        }
        if (!isDaytime(world)) {
            return false;
        }
        Location loc = player.getLocation();
        if (world.hasStorm() && world.getHighestBlockYAt(loc) <= loc.getBlockY()) {
            return false;
        }
        return loc.getBlock().getLightFromSky() == 15;
    }

    /** Light level 0 at the player's feet: full darkness. */
    public static boolean inPitchBlack(Player player) {
        return player.getLocation().getBlock().getLightLevel() == 0;
    }

    /** Shadow Step condition: standing in darkness, or simply out at night. */
    public static boolean inShadow(Player player) {
        return inPitchBlack(player) || isNight(player.getWorld());
    }

    /** Vampirism condition: night above ground, or underground away from the sky. */
    public static boolean inNightOrCave(Player player) {
        return isNight(player.getWorld()) || player.getLocation().getBlock().getLightFromSky() < 4;
    }

    /** Light Decay condition: a bright light source within {@code radius} blocks. */
    public static boolean nearBrightLight(Player player, int radius, int threshold) {
        Location base = player.getLocation();
        World world = base.getWorld();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Location probe = base.clone().add(x, y, z);
                    if (!world.isChunkLoaded(probe.getBlockX() >> 4, probe.getBlockZ() >> 4)) {
                        continue;
                    }
                    if (probe.getBlock().getLightFromBlocks() >= threshold) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
