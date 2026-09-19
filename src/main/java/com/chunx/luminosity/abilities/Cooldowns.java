package com.chunx.luminosity.abilities;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-player, per-ability cooldown bookkeeping. */
public final class Cooldowns {

    private final Map<UUID, Map<String, Long>> readyAt = new HashMap<>();

    /**
     * Consumes the cooldown if the ability is ready.
     *
     * @return true if the ability fired, false if it is still cooling down (the player
     *         is told how long remains)
     */
    public boolean tryUse(Player player, String ability, int seconds) {
        long now = System.currentTimeMillis();
        Map<String, Long> perAbility = readyAt.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        long ready = perAbility.getOrDefault(ability, 0L);
        if (now < ready) {
            long remaining = (ready - now + 999) / 1000;
            player.sendActionBar(Component.text(ability + " ready in " + remaining + "s", NamedTextColor.RED));
            return false;
        }
        perAbility.put(ability, now + seconds * 1000L);
        return true;
    }

    public void clear(Player player) {
        readyAt.remove(player.getUniqueId());
    }
}
