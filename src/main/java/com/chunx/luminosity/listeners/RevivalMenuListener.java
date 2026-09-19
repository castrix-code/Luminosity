package com.chunx.luminosity.listeners;

import com.chunx.luminosity.items.LuminousCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** Single registered handler for every open {@link RevivalMenu}. */
public final class RevivalMenuListener implements Listener {

    private final LuminousCore core;

    public RevivalMenuListener(LuminousCore core) {
        this.core = core;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RevivalMenu menu)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= menu.slots().size()) {
            return;
        }

        // The Core is only spent once a real target has been chosen.
        ItemStack held = findChargedCore(viewer);
        if (held == null) {
            viewer.sendMessage(Component.text("You are no longer holding a Charged Core.",
                    NamedTextColor.RED));
            viewer.closeInventory();
            return;
        }

        UUID target = menu.slots().get(slot);
        viewer.closeInventory();
        if (menu.revive(viewer, target)) {
            held.setAmount(held.getAmount() - 1);
        }
    }

    private ItemStack findChargedCore(Player player) {
        for (ItemStack candidate : new ItemStack[]{
                player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand()}) {
            if (core.isCore(candidate) && core.isCharged(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
