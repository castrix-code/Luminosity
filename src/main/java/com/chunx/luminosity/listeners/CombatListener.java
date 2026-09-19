package com.chunx.luminosity.listeners;

import com.chunx.luminosity.abilities.SolarBurn;
import com.chunx.luminosity.core.LuminosityService;
import com.chunx.luminosity.data.PlayerData;
import com.chunx.luminosity.traits.Env;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Every trait that reads or rewrites a damage event. */
public final class CombatListener implements Listener {

    private static final double SUN_STIGMA_MULTIPLIER = 1.10;
    private static final int SUN_AURA_SECONDS = 4;

    /**
     * The Void path's combat numbers, as fractions, read from {@code config.yml} so they
     * can be balanced on a live server without a rebuild.
     *
     * @param executionerBonus  extra melee damage against Light-path players
     * @param phaseStrikePierce share of armour points and Resistance a Revenant ignores
     * @param vampirism         share of melee damage dealt that heals the attacker
     */
    public record Tuning(double executionerBonus, double phaseStrikePierce, double vampirism) {
    }

    private final LuminosityService service;
    private final SolarBurn solarBurn;
    private final Tuning tuning;

    public CombatListener(LuminosityService service, SolarBurn solarBurn, Tuning tuning) {
        this.service = service;
        this.solarBurn = solarBurn;
        this.tuning = tuning;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player victim = event.getEntity() instanceof Player p ? p : null;
        Player attacker = resolveAttacker(event);
        boolean melee = event.getDamager() instanceof Player;

        if (victim != null) {
            applyVictimTraits(event, victim, attacker, melee);
        }
        if (attacker != null) {
            applyAttackerTraits(event, attacker, victim);
        }
        // Vampirism reads the damage that actually lands, so it runs last.
        if (attacker != null && victim != null && melee) {
            applyVampirism(event, attacker);
        }
    }

    private void applyVictimTraits(EntityDamageByEntityEvent event, Player victim,
                                   Player attacker, boolean melee) {
        PlayerData data = service.data(victim);

        // Void Stage 1 penalty - Sun Stigma: +10% incoming damage in direct sunlight.
        if (data.hasVoidTier(1) && !data.eclipse() && Env.inDirectSunlight(victim)) {
            event.setDamage(event.getDamage() * SUN_STIGMA_MULTIPLIER);
        }

        // Light Stage 2 - Sun Aura: melee attackers catch unquenchable solar fire.
        if (data.hasLightTier(2) && melee && attacker != null && !attacker.equals(victim)) {
            solarBurn.ignite(attacker, SUN_AURA_SECONDS);
        }
    }

    private void applyAttackerTraits(EntityDamageByEntityEvent event, Player attacker, Player victim) {
        PlayerData data = service.data(attacker);

        // Void Stage 2 - Executioner: bonus damage against anyone on the Light path.
        if (data.hasVoidTier(2) && victim != null && service.data(victim).stage().isLight()) {
            event.setDamage(event.getDamage() * (1.0 + tuning.executionerBonus()));
        }

        // Void Stage 3 - Phase Strike: pierce part of the target's armour and Resistance.
        if (data.hasVoidTier(3)) {
            applyPhaseStrike(event);
        }
    }

    /**
     * Pierces a fraction of the target's armour points and Resistance effect, and nothing
     * else. Bukkit has no "ignore armour" switch, so this runs vanilla's armour formula
     * twice — once with the target's real armour, once with the pierced amount — and
     * scales the hit by the difference.
     *
     * <p>An earlier version instead undid a share of <em>all</em> mitigation between base
     * and final damage, which quietly also cut through absorption hearts, Protection
     * enchantments and shield blocks. That is why it hit far harder than its number read.
     */
    private void applyPhaseStrike(EntityDamageByEntityEvent event) {
        if (tuning.phaseStrikePierce() <= 0 || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        double base = event.getDamage();
        if (base <= 0) {
            return;
        }
        double armor = attribute(target, Attribute.ARMOR);
        double toughness = attribute(target, Attribute.ARMOR_TOUGHNESS);
        PotionEffect resistanceEffect = target.getPotionEffect(PotionEffectType.RESISTANCE);
        int resistance = resistanceEffect == null ? 0 : resistanceEffect.getAmplifier() + 1;

        double keep = 1.0 - tuning.phaseStrikePierce();
        double resistanceNow = resistanceFactor(resistance);
        if (resistanceNow <= 0) {
            return; // Resistance V: nothing lands either way
        }
        // The damage this hit should deal after armour, had the target's armour been pierced.
        double wanted = base * armorFactor(base, armor * keep, toughness)
                * resistanceFactor(resistance * keep) / resistanceNow;

        // Scaling the base by a simple ratio overshoots: a bigger hit also makes vanilla
        // armour less effective. So find the base that lands exactly `wanted` against the
        // real armour. Damage-after-armour rises with base damage, so bisection converges.
        double low = base;
        double high = base * 16;
        for (int i = 0; i < 40; i++) {
            double mid = (low + high) / 2;
            if (mid * armorFactor(mid, armor, toughness) < wanted) {
                low = mid;
            } else {
                high = mid;
            }
        }
        event.setDamage((low + high) / 2);
    }

    /** Vanilla's damage-after-armour multiplier (CombatRules.getDamageAfterAbsorb). */
    private static double armorFactor(double damage, double armor, double toughness) {
        double f = 2.0 + toughness / 4.0;
        double effective = Math.max(armor * 0.2, Math.min(20.0, armor - damage / f));
        return 1.0 - effective / 25.0;
    }

    /** Each Resistance level removes 20% of incoming damage. */
    private static double resistanceFactor(double levels) {
        return Math.max(0.0, 1.0 - 0.2 * levels);
    }

    private static double attribute(LivingEntity entity, Attribute attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? 0.0 : instance.getValue();
    }

    /** Void Stage 1 - Vampirism: lifesteal on melee hits at night or underground. */
    private void applyVampirism(EntityDamageByEntityEvent event, Player attacker) {
        PlayerData data = service.data(attacker);
        if (!data.hasVoidTier(1) || !Env.inNightOrCave(attacker)) {
            return;
        }
        double max = attacker.getAttribute(Attribute.MAX_HEALTH) == null
                ? 20.0 : attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
        double healed = Math.min(max, attacker.getHealth() + event.getFinalDamage() * tuning.vampirism());
        attacker.setHealth(healed);
    }

    /**
     * Light Stage 2 penalty - Overheating: netherite chestplates wear through twice as
     * fast under a Radiant Guardian's own heat.
     */
    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        PlayerData data = service.data(event.getPlayer());
        if (!data.hasLightTier(2)) {
            return;
        }
        ItemStack item = event.getItem();
        if (item.getType() != Material.NETHERITE_CHESTPLATE) {
            return;
        }
        if (!(item.getItemMeta() instanceof Damageable)) {
            return;
        }
        event.setDamage(event.getDamage() * 2);
    }

    /** Unwraps arrows and other projectiles back to the player who fired them. */
    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player direct) {
            return direct;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
