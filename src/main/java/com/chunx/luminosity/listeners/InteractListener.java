package com.chunx.luminosity.listeners;

import com.chunx.luminosity.abilities.Abilities;
import com.chunx.luminosity.core.Luminosity;
import com.chunx.luminosity.core.LuminosityService;
import com.chunx.luminosity.data.PlayerData;
import com.chunx.luminosity.items.LuminousCore;
import com.chunx.luminosity.items.LuminousSigil;
import com.chunx.luminosity.util.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Right-click and sneak driven mechanics: the Egg, the Core, the altar and the sigil. */
public final class InteractListener implements Listener {

    /** A second crouch inside this window counts as a double-tap. */
    private static final long DASH_TAP_WINDOW_MS = 400;

    private final LuminosityService service;
    private final LuminousCore core;
    private final LuminousSigil sigil;
    private final Abilities abilities;
    private final Material altarMaterial;
    private final Map<UUID, Long> lastSneak = new HashMap<>();

    public InteractListener(LuminosityService service, LuminousCore core, LuminousSigil sigil,
                            Abilities abilities, Material altarMaterial) {
        this.service = service;
        this.core = core;
        this.sigil = sigil;
        this.abilities = abilities;
        this.altarMaterial = altarMaterial;
    }

    // Deliberately NOT ignoreCancelled: PlayerInteractEvent#isCancelled() reports true for
    // every right-click on air, because there is no block for useInteractedBlock() to allow.
    // Filtering on it would drop the sigil and every pour.
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.useItemInHand() == org.bukkit.event.Event.Result.DENY) {
            return;
        }
        Player player = event.getPlayer();
        PlayerData data = service.data(player);
        if (data.voidLocked()) {
            return;
        }

        ItemStack held = event.getItem();
        Block clicked = event.getClickedBlock();
        boolean rightClick = event.getAction() == Action.RIGHT_CLICK_BLOCK
                || event.getAction() == Action.RIGHT_CLICK_AIR;
        if (!rightClick) {
            return;
        }

        // Dragon Egg Eclipse: only at a full +100.
        if (clicked != null && clicked.getType() == Material.DRAGON_EGG) {
            event.setCancelled(true);
            consumeDragonEgg(player, data, clicked);
            return;
        }

        // Revival Ritual: a Charged Core struck against the altar.
        if (clicked != null && clicked.getType() == altarMaterial
                && core.isCore(held) && core.isCharged(held)) {
            event.setCancelled(true);
            new RevivalMenu(service, clicked.getLocation()).open(player);
            return;
        }

        // Pouring: crouch + right-click moves 20 Luminosity into the Core.
        if (core.isCore(held) && player.isSneaking()) {
            event.setCancelled(true);
            pour(player, data, held);
            return;
        }

        if (sigil.isSigil(held)) {
            event.setCancelled(true);
            abilities.castSigil(player, data, player.isSneaking());
        }
    }

    private void consumeDragonEgg(Player player, PlayerData data, Block egg) {
        if (data.eclipse()) {
            player.sendActionBar(Component.text("You already carry the Eclipse.", NamedTextColor.GRAY));
            return;
        }
        if (data.luminosity() < Luminosity.MAX) {
            player.sendActionBar(Component.text("The Egg rejects you. Reach +" + Luminosity.MAX
                    + " Luminosity first.", NamedTextColor.RED));
            return;
        }

        egg.setType(Material.AIR);
        data.eclipse(true);
        service.traits().apply(player);

        Bukkit.broadcast(Component.text(player.getName() + " has consumed the Dragon Egg.",
                NamedTextColor.LIGHT_PURPLE));
        Bukkit.broadcast(Component.text("The Eclipse has begun.", NamedTextColor.DARK_PURPLE));
        player.showTitle(Title.title(
                Component.text("ECLIPSE", NamedTextColor.DARK_PURPLE),
                Component.text("Solar body, Void hands.", NamedTextColor.GOLD)));
        egg.getWorld().playSound(egg.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.4f);
        Fx.spawn(egg.getLocation().add(0.5, 1, 0.5), Particle.DRAGON_BREATH, 150, 0.5, 0.8, 0.5, 0.05);
    }

    private void pour(Player player, PlayerData data, ItemStack held) {
        int charge = core.charge(held);
        if (charge >= Luminosity.CORE_CHARGED_AT) {
            player.sendActionBar(Component.text("The Core is already fully charged.", NamedTextColor.GOLD));
            return;
        }
        if (data.luminosity() < Luminosity.STEP) {
            player.sendActionBar(Component.text("You need at least +" + Luminosity.STEP
                    + " Luminosity to pour.", NamedTextColor.RED));
            return;
        }
        // A stack would share one charge value, so pouring only works one Core at a time.
        if (held.getAmount() > 1) {
            player.sendActionBar(Component.text("Hold a single Core to pour into it.", NamedTextColor.RED));
            return;
        }

        service.add(player, -Luminosity.STEP, "poured into the Luminous Core");
        core.setCharge(held, charge + Luminosity.STEP);

        int now = core.charge(held);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f,
                0.8f + (now / (float) Luminosity.CORE_CHARGED_AT));
        player.sendActionBar(Component.text("Core charge " + now + " / "
                + Luminosity.CORE_CHARGED_AT, NamedTextColor.AQUA));
        if (now >= Luminosity.CORE_CHARGED_AT) {
            player.sendMessage(Component.text("The Core is charged. Strike an altar to perform the ritual.",
                    NamedTextColor.GOLD));
        }
    }

    /** Void Stage 2 - Shadow Dash on a double-tap of crouch. */
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        PlayerData data = service.data(player);
        if (data.voidLocked() || !data.hasVoidTier(2)) {
            return;
        }

        long now = System.currentTimeMillis();
        Long previous = lastSneak.put(player.getUniqueId(), now);
        if (previous != null && now - previous <= DASH_TAP_WINDOW_MS) {
            lastSneak.remove(player.getUniqueId());
            abilities.shadowDash(player);
        }
    }
}
