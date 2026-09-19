package com.chunx.luminosity.abilities;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sun Aura ignition. Vanilla fire is stopped cold by Fire Resistance and by stepping
 * into water, so this deals the burn as direct generic damage on its own timer and
 * only uses the flame overlay for the visual.
 */
public final class SolarBurn {

    private static final double DAMAGE_PER_SECOND = 1.0;

    private final Plugin plugin;
    private final Map<UUID, Integer> burning = new ConcurrentHashMap<>();

    public SolarBurn(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean isBurning(LivingEntity entity) {
        return burning.containsKey(entity.getUniqueId());
    }

    /** Ignites the target for {@code seconds}, ignoring fire resistance and water. */
    public void ignite(LivingEntity target, int seconds) {
        UUID id = target.getUniqueId();
        // Re-igniting an already burning target just refreshes the remaining time.
        if (burning.put(id, seconds) != null) {
            return;
        }

        if (target instanceof org.bukkit.entity.Player player) {
            player.sendMessage(Component.text("Solar fire clings to you. It cannot be put out.",
                    NamedTextColor.GOLD));
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                Integer remaining = burning.get(id);
                if (remaining == null || remaining <= 0 || target.isDead() || !target.isValid()) {
                    burning.remove(id);
                    target.setVisualFire(false);
                    cancel();
                    return;
                }
                burning.put(id, remaining - 1);
                target.setVisualFire(true);
                target.damage(DAMAGE_PER_SECOND);
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void clear(LivingEntity entity) {
        burning.remove(entity.getUniqueId());
        entity.setVisualFire(false);
    }
}
