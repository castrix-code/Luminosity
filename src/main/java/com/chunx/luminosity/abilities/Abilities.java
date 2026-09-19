package com.chunx.luminosity.abilities;

import com.chunx.luminosity.data.PlayerData;
import com.chunx.luminosity.util.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** The three active abilities: Shadow Dash, Shadow Realm and Supernova. */
public final class Abilities {

    private static final int SHADOW_DASH_COOLDOWN = 8;
    private static final int SHADOW_REALM_COOLDOWN = 30;
    private static final int SUPERNOVA_COOLDOWN = 40;

    private static final double DASH_DISTANCE = 7.0;
    private static final double SHADOW_REALM_RANGE = 25.0;
    private static final int SHADOW_REALM_SECONDS = 4;
    private static final double SUPERNOVA_RADIUS = 8.0;
    private static final double SUPERNOVA_HEAL = 6.0;
    private static final int SUPERNOVA_BURN_SECONDS = 5;

    private final Plugin plugin;
    private final Cooldowns cooldowns;
    private final SolarBurn solarBurn;

    public Abilities(Plugin plugin, Cooldowns cooldowns, SolarBurn solarBurn) {
        this.plugin = plugin;
        this.cooldowns = cooldowns;
        this.solarBurn = solarBurn;
    }

    /**
     * Void Stage 2 - Shadow Dash. Teleports 7 blocks forward, stopping short of any
     * wall so the dash can never bury the player inside terrain.
     */
    public void shadowDash(Player player) {
        if (!cooldowns.tryUse(player, "Shadow Dash", SHADOW_DASH_COOLDOWN)) {
            return;
        }
        Location from = player.getLocation();
        Vector direction = from.getDirection().setY(0).normalize();
        Location to = safeDestination(player, direction, DASH_DISTANCE);

        player.teleport(to);
        Fx.spawn(from.add(0, 1, 0), Particle.SQUID_INK, 40, 0.3, 0.6, 0.3, 0.02);
        Fx.spawn(to.clone().add(0, 1, 0), Particle.SQUID_INK, 40, 0.3, 0.6, 0.3, 0.02);
        to.getWorld().playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.6f);
        player.sendActionBar(Component.text("Shadow Dash", NamedTextColor.DARK_PURPLE));
    }

    /** Walks the dash path a step at a time and returns the last passable spot. */
    private Location safeDestination(Player player, Vector direction, double maxDistance) {
        Location origin = player.getLocation();
        Location best = origin.clone();
        for (double step = 0.5; step <= maxDistance; step += 0.5) {
            Location probe = origin.clone().add(direction.clone().multiply(step));
            if (probe.getBlock().isPassable() && probe.clone().add(0, 1, 0).getBlock().isPassable()) {
                best = probe;
            } else {
                break;
            }
        }
        best.setYaw(origin.getYaw());
        best.setPitch(origin.getPitch());
        return best;
    }

    /**
     * Void Stage 3 - Shadow Realm. Traps whoever the caster is looking at in a
     * lightless bubble for 4 seconds: no sight, no sprint.
     */
    public void shadowRealm(Player caster) {
        Player target = rayTracePlayer(caster, SHADOW_REALM_RANGE);
        if (target == null) {
            caster.sendActionBar(Component.text("No target in sight.", NamedTextColor.GRAY));
            return;
        }
        if (!cooldowns.tryUse(caster, "Shadow Realm", SHADOW_REALM_COOLDOWN)) {
            return;
        }

        int ticks = SHADOW_REALM_SECONDS * 20;
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0, false, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, ticks, 0, false, false, false));
        target.sendMessage(Component.text("The Shadow Realm closes over you.", NamedTextColor.DARK_PURPLE));
        caster.sendActionBar(Component.text("Shadow Realm → " + target.getName(), NamedTextColor.DARK_PURPLE));
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 0.5f);

        // Sprint has to be suppressed every tick; the client re-enables it on its own.
        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (elapsed++ >= ticks || !target.isOnline() || target.isDead()) {
                    cancel();
                    return;
                }
                target.setSprinting(false);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Light Stage 3 - Supernova. A shockwave that throws nearby enemies back, sets
     * them alight with unquenchable solar fire, and heals the caster for 3 hearts.
     */
    public void supernova(Player caster) {
        if (!cooldowns.tryUse(caster, "Supernova", SUPERNOVA_COOLDOWN)) {
            return;
        }
        Location center = caster.getLocation();

        // Gameplay first, visuals last: a cosmetic failure must never cost the effect.
        for (Entity entity : caster.getNearbyEntities(SUPERNOVA_RADIUS, SUPERNOVA_RADIUS, SUPERNOVA_RADIUS)) {
            if (!(entity instanceof org.bukkit.entity.LivingEntity living) || living.equals(caster)) {
                continue;
            }
            Vector push = living.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() < 0.01) {
                push = caster.getLocation().getDirection();
            }
            living.setVelocity(push.normalize().multiply(1.6).setY(0.7));
            solarBurn.ignite(living, SUPERNOVA_BURN_SECONDS);
        }

        double max = caster.getAttribute(Attribute.MAX_HEALTH) == null
                ? 20.0 : caster.getAttribute(Attribute.MAX_HEALTH).getValue();
        caster.setHealth(Math.min(max, caster.getHealth() + SUPERNOVA_HEAL));
        caster.sendActionBar(Component.text("Supernova", NamedTextColor.GOLD));

        Fx.spawn(center.clone().add(0, 1, 0), Particle.DUST, 200, 3.0, 1.5, 3.0, 0,
                new Particle.DustOptions(Color.fromRGB(255, 235, 120), 2.0f));
        Fx.spawn(center, Particle.FLASH, 3, 0, 0, 0, 0);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.4f);
    }

    /** Finds the first player along the caster's line of sight, ignoring the caster. */
    private Player rayTracePlayer(Player caster, double range) {
        RayTraceResult result = caster.getWorld().rayTrace(
                caster.getEyeLocation(),
                caster.getEyeLocation().getDirection(),
                range,
                org.bukkit.FluidCollisionMode.NEVER,
                true,
                0.6,
                entity -> entity instanceof Player && !entity.equals(caster));
        if (result == null || !(result.getHitEntity() instanceof Player hit)) {
            return null;
        }
        return hit;
    }

    /** Dispatches the sigil right-click to whichever Stage 3 ability the player owns. */
    public void castSigil(Player player, PlayerData data, boolean sneaking) {
        boolean hasShadowRealm = data.hasVoidTier(3);
        boolean hasSupernova = data.hasLightTier(3);

        if (!hasShadowRealm && !hasSupernova) {
            player.sendActionBar(Component.text("The sigil is inert in your hand.", NamedTextColor.GRAY));
            return;
        }
        // Eclipse holds both: sneaking picks the Void half, standing picks the Solar half.
        if (hasShadowRealm && (sneaking || !hasSupernova)) {
            shadowRealm(player);
        } else {
            supernova(player);
        }
    }
}
