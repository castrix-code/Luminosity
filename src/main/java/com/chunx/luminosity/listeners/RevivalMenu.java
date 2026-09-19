package com.chunx.luminosity.listeners;

import com.chunx.luminosity.core.Luminosity;
import com.chunx.luminosity.core.LuminosityService;
import com.chunx.luminosity.data.PlayerData;
import com.chunx.luminosity.util.Fx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The chest UI a player sees after striking an altar with a Charged Core: one head per
 * void-locked player, and clicking one spends the Core to bring them back.
 */
public final class RevivalMenu implements InventoryHolder {

    private final LuminosityService service;
    private final List<UUID> slots = new ArrayList<>();
    private final Location altar;
    private Inventory inventory;

    public RevivalMenu(LuminosityService service, Location altar) {
        this.service = service;
        this.altar = altar;
    }

    /** Builds and shows the menu. Returns false when nobody is waiting to be revived. */
    public boolean open(Player viewer) {
        List<PlayerData> locked = service.store().all().stream()
                .filter(PlayerData::voidLocked)
                .toList();
        if (locked.isEmpty()) {
            viewer.sendMessage(Component.text("The Void holds no one. The Core stays cold.",
                    NamedTextColor.GRAY));
            return false;
        }

        int rows = Math.max(1, Math.min(6, (locked.size() + 8) / 9));
        inventory = Bukkit.createInventory(this, rows * 9,
                Component.text("Revival Ritual", NamedTextColor.DARK_PURPLE));

        for (PlayerData data : locked) {
            if (slots.size() >= inventory.getSize()) {
                break;
            }
            OfflinePlayer offline = Bukkit.getOfflinePlayer(data.uuid());
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta meta) {
                meta.setOwningPlayer(offline);
                String name = offline.getName() == null ? data.uuid().toString() : offline.getName();
                meta.displayName(Component.text(name, NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(Component.text(
                                "Click to return them at " + Luminosity.REVIVAL_LUMINOSITY + " Luminosity.",
                                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
                head.setItemMeta(meta);
            }
            inventory.setItem(slots.size(), head);
            slots.add(data.uuid());
        }

        viewer.openInventory(inventory);
        return true;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Slot index to the void-locked player whose head occupies it. */
    List<UUID> slots() {
        return slots;
    }

    /** @return false if someone else revived them while this menu was open */
    boolean revive(Player ritualist, UUID target) {
        if (!service.revive(target)) {
            ritualist.sendMessage(Component.text("They have already returned.", NamedTextColor.GRAY));
            return false;
        }

        String name = Bukkit.getOfflinePlayer(target).getName();
        Bukkit.broadcast(Component.text((name == null ? "A lost soul" : name)
                + " has been revived by " + ritualist.getName() + ".", NamedTextColor.LIGHT_PURPLE));
        altar.getWorld().playSound(altar, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
        Fx.spawn(altar.clone().add(0.5, 1.5, 0.5), org.bukkit.Particle.END_ROD,
                120, 0.4, 1.0, 0.4, 0.05);
        return true;
    }
}
