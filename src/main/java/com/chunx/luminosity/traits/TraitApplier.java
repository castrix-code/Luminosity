package com.chunx.luminosity.traits;

import com.chunx.luminosity.data.DataStore;
import com.chunx.luminosity.data.PlayerData;
import com.chunx.luminosity.items.LuminousSigil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reconciles a player's live Bukkit state (attributes, potion effects) with the traits
 * their current stage entitles them to. Structural changes go through {@link #apply};
 * per-second conditional effects go through {@link #tick}.
 */
public final class TraitApplier {

    /** Effects are renewed every second, so 5s of duration is comfortably seamless. */
    private static final int EFFECT_DURATION = 100;

    private final Plugin plugin;
    private final DataStore store;
    private final LuminousSigil sigil;
    private final NamespacedKey healthKey;
    private final NamespacedKey speedKey;
    private final Map<UUID, Integer> darknessSeconds = new ConcurrentHashMap<>();

    public TraitApplier(Plugin plugin, DataStore store, LuminousSigil sigil) {
        this.plugin = plugin;
        this.store = store;
        this.sigil = sigil;
        this.healthKey = new NamespacedKey(plugin, "vitality");
        this.speedKey = new NamespacedKey(plugin, "shadow_step");
    }

    public Plugin plugin() {
        return plugin;
    }

    /**
     * Re-applies every attribute this player's stage grants. Safe to call repeatedly.
     *
     * <p>A void-locked player owns nothing: they are held in spectator limbo until a
     * Revival Ritual frees them. Guarding here rather than at each call site means no
     * later caller can hand their traits back by accident.
     */
    public void apply(Player player) {
        PlayerData data = store.get(player.getUniqueId());
        if (data.voidLocked()) {
            clear(player);
            return;
        }
        applyMaxHealth(player, bonusHearts(data) * 2.0);
        stripForfeitedEffects(player, data);
    }

    /** Extra hearts granted by the stage. Eclipse keeps the Solar Deity pool of 32 HP. */
    private int bonusHearts(PlayerData data) {
        if (data.eclipse()) {
            return 6;
        }
        return switch (data.stage()) {
            case VOID_3 -> 5;
            case LIGHT_1 -> 2;
            case LIGHT_2 -> 4;
            case LIGHT_3 -> 6;
            default -> 0;
        };
    }

    private void applyMaxHealth(Player player, double bonusHealth) {
        AttributeInstance inst = player.getAttribute(Attribute.MAX_HEALTH);
        if (inst == null) {
            return;
        }
        removeOurModifiers(inst, healthKey);
        if (bonusHealth > 0) {
            inst.addModifier(new AttributeModifier(healthKey, bonusHealth,
                    AttributeModifier.Operation.ADD_NUMBER));
        }
        double max = inst.getValue();
        if (player.getHealth() > max) {
            player.setHealth(max);
        }
    }

    private void setSpeedBonus(Player player, double fraction) {
        AttributeInstance inst = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (inst == null) {
            return;
        }
        boolean present = inst.getModifiers().stream().anyMatch(m -> speedKey.equals(m.getKey()));
        if (fraction <= 0) {
            if (present) {
                removeOurModifiers(inst, speedKey);
            }
            return;
        }
        if (!present) {
            inst.addModifier(new AttributeModifier(speedKey, fraction,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    private void removeOurModifiers(AttributeInstance inst, NamespacedKey key) {
        List<AttributeModifier> doomed = new ArrayList<>();
        for (AttributeModifier mod : inst.getModifiers()) {
            if (key.equals(mod.getKey())) {
                doomed.add(mod);
            }
        }
        doomed.forEach(inst::removeModifier);
    }

    /** Removes stage effects the player is no longer entitled to after a change. */
    private void stripForfeitedEffects(Player player, PlayerData data) {
        if (!data.hasVoidTier(1)) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            setSpeedBonus(player, 0);
        }
        if (!data.hasVoidTier(2)) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
        if (!data.hasLightTier(1)) {
            player.removePotionEffect(PotionEffectType.HASTE);
            player.removePotionEffect(PotionEffectType.REGENERATION);
        }
        if (!data.hasLightTier(3)) {
            player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        }
    }

    /** Called once per second per online player, for every condition-dependent trait. */
    public void tick(Player player) {
        PlayerData data = store.get(player.getUniqueId());
        if (data.voidLocked()) {
            return;
        }
        grantSigil(player, data);
        tickVoid(player, data);
        tickLight(player, data);
    }

    /**
     * Stage 3 is what earns the Luminous Sigil, on either path. Granting it from the
     * per-second tick covers every route into Stage 3 — a kill, an admin set, a login,
     * or the Dragon Egg — without each of them having to remember.
     */
    private void grantSigil(Player player, PlayerData data) {
        if (!data.hasVoidTier(3) && !data.hasLightTier(3)) {
            return;
        }
        if (sigil.carries(player)) {
            return;
        }
        // A full inventory simply defers the grant to the next tick.
        if (!player.getInventory().addItem(sigil.create()).isEmpty()) {
            return;
        }
        player.sendMessage(Component.text("The Luminous Sigil answers to you.",
                NamedTextColor.LIGHT_PURPLE));
        player.sendMessage(Component.text(
                data.hasVoidTier(3) && data.hasLightTier(3)
                        ? "Right-click for Supernova, crouch + right-click for Shadow Realm."
                        : data.hasVoidTier(3)
                        ? "Crouch + right-click to cast Shadow Realm."
                        : "Right-click to cast Supernova.",
                NamedTextColor.GRAY));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.5f);
    }

    private void tickVoid(Player player, PlayerData data) {
        if (!data.hasVoidTier(1)) {
            return;
        }

        // Stage 1 - Night Vision, always on.
        give(player, PotionEffectType.NIGHT_VISION, 0);

        // Stage 1 - Shadow Step: +20% movement in darkness or at night.
        setSpeedBonus(player, Env.inShadow(player) ? 0.20 : 0);

        // Stage 2 - True Stealth: particle-free permanent invisibility.
        if (data.hasVoidTier(2)) {
            give(player, PotionEffectType.INVISIBILITY, 0);
        }

        // Stage 2 penalty - Light Decay within 2 blocks of a bright light source.
        if (data.hasVoidTier(2) && !data.eclipse() && Env.nearBrightLight(player, 2, 14)) {
            give(player, PotionEffectType.SLOWNESS, 0);
        }

        // Stage 3 penalty - Solar Combustion at midday. Eclipse players are exempt:
        // their Solar half shields them from their own sun.
        if (data.hasVoidTier(3) && !data.eclipse()
                && Env.isMidday(player.getWorld()) && Env.inDirectSunlight(player)) {
            player.setFireTicks(Math.max(player.getFireTicks(), 60));
        }
    }

    private void tickLight(Player player, PlayerData data) {
        if (!data.hasLightTier(1)) {
            return;
        }

        // Stage 1 - Solar Regeneration in direct sunlight.
        if (Env.inDirectSunlight(player)) {
            give(player, PotionEffectType.REGENERATION, 0);
        }

        // Stage 1 - Haste I during the day.
        if (Env.isDaytime(player.getWorld())) {
            give(player, PotionEffectType.HASTE, 0);
        } else {
            player.removePotionEffect(PotionEffectType.HASTE);
        }

        // Stage 1 penalty - Glow Stigma: an unmissable golden plume. Eclipse is exempt,
        // since a permanent tell would cancel out the True Stealth it just gained.
        if (!data.eclipse()) {
            player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1.0, 0), 12,
                    0.35, 0.6, 0.35,
                    new Particle.DustOptions(Color.fromRGB(255, 214, 82), 1.2f));
        }

        // Stage 3 - immunity to fire, lava and poison.
        if (data.hasLightTier(3)) {
            give(player, PotionEffectType.FIRE_RESISTANCE, 0);
            player.removePotionEffect(PotionEffectType.POISON);
        }

        // Stage 3 penalty - Darkness Suffocation after 5 continuous seconds in the black.
        // Eclipse is exempt: the Void half is meant to operate in exactly this light.
        if (data.hasLightTier(3) && !data.eclipse() && Env.inPitchBlack(player)) {
            int held = darknessSeconds.merge(player.getUniqueId(), 1, Integer::sum);
            if (held >= 5) {
                give(player, PotionEffectType.SLOWNESS, 1);
                give(player, PotionEffectType.WITHER, 0);
            }
        } else {
            darknessSeconds.remove(player.getUniqueId());
        }
    }

    /** Applies a hidden, non-ambient effect that the next tick silently renews. */
    private void give(Player player, PotionEffectType type, int amplifier) {
        player.addPotionEffect(new PotionEffect(type, EFFECT_DURATION, amplifier, false, false, false));
    }

    /** Strips everything this plugin granted. Used on void-lock and on plugin disable. */
    public void clear(Player player) {
        applyMaxHealth(player, 0);
        setSpeedBonus(player, 0);
        for (PotionEffectType type : new PotionEffectType[]{
                PotionEffectType.NIGHT_VISION, PotionEffectType.INVISIBILITY,
                PotionEffectType.HASTE, PotionEffectType.REGENERATION,
                PotionEffectType.FIRE_RESISTANCE}) {
            player.removePotionEffect(type);
        }
        darknessSeconds.remove(player.getUniqueId());
    }
}
