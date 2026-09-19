package com.chunx.luminosity.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * The focus a Stage 3 player holds to cast their active ability. Binding the ability
 * to a held item keeps it from misfiring during ordinary right-clicks.
 *
 * <p>Right-click casts Supernova (Light), crouch + right-click casts Shadow Realm (Void).
 * An Eclipse player holds both on the one sigil.
 */
public final class LuminousSigil {

    private final NamespacedKey key;

    public LuminousSigil(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "sigil");
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Luminous Sigil", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Right-click: Supernova", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Crouch + right-click: Shadow Realm", NamedTextColor.DARK_PURPLE)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Only answers to a Stage 3 bearer.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isSigil(ItemStack item) {
        if (item == null || item.getType() != Material.AMETHYST_SHARD) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** True if the player is already carrying one, so Stage 3 never hands out a second. */
    public boolean carries(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isSigil(item)) {
                return true;
            }
        }
        return isSigil(player.getInventory().getItemInOffHand());
    }
}
