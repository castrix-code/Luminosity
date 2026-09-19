package com.chunx.luminosity.items;

import com.chunx.luminosity.core.Luminosity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * The Luminous Core: an end-game artifact that stores poured luminosity and, once
 * charged to +100, powers the Revival Ritual.
 */
public final class LuminousCore {

    private final NamespacedKey chargeKey;
    private final NamespacedKey recipeKey;

    public LuminousCore(Plugin plugin) {
        this.chargeKey = new NamespacedKey(plugin, "core_charge");
        this.recipeKey = new NamespacedKey(plugin, "luminous_core");
    }

    /** A freshly crafted Core carries its innate +20 charge. */
    public ItemStack create(int charge) {
        ItemStack item = new ItemStack(Material.HEART_OF_THE_SEA);
        setCharge(item, charge);
        return item;
    }

    public boolean isCore(ItemStack item) {
        if (item == null || item.getType() != Material.HEART_OF_THE_SEA) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(chargeKey, PersistentDataType.INTEGER);
    }

    public int charge(ItemStack item) {
        if (item == null) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        Integer stored = meta.getPersistentDataContainer().get(chargeKey, PersistentDataType.INTEGER);
        return stored == null ? 0 : stored;
    }

    public boolean isCharged(ItemStack item) {
        return charge(item) >= Luminosity.CORE_CHARGED_AT;
    }

    /** Writes the charge into the item and rebuilds its name and lore to match. */
    public void setCharge(ItemStack item, int charge) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        int clamped = Math.max(0, Math.min(Luminosity.CORE_CHARGED_AT, charge));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(chargeKey, PersistentDataType.INTEGER, clamped);

        boolean charged = clamped >= Luminosity.CORE_CHARGED_AT;
        meta.displayName(Component.text(charged ? "Charged Core" : "Luminous Core",
                        charged ? NamedTextColor.GOLD : NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Charge: " + clamped + " / " + Luminosity.CORE_CHARGED_AT,
                        NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text(charged
                                ? "Right-click an altar to perform the Revival Ritual."
                                : "Crouch + right-click to pour " + Luminosity.STEP + " Luminosity.",
                        charged ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.setEnchantmentGlintOverride(charged);
        item.setItemMeta(meta);
    }

    /**
     * Registers the 3x3 recipe:
     * <pre>
     *   .  dragon head  .
     *  echo  nether star  echo
     *  diamond  netherite  diamond
     * </pre>
     */
    public ShapedRecipe recipe() {
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, create(Luminosity.STEP));
        recipe.shape(" D ", "ENE", "XIX");
        recipe.setIngredient('D', Material.DRAGON_HEAD);
        recipe.setIngredient('E', Material.ECHO_SHARD);
        recipe.setIngredient('N', Material.NETHER_STAR);
        recipe.setIngredient('X', Material.DIAMOND);
        recipe.setIngredient('I', Material.NETHERITE_INGOT);
        return recipe;
    }

    public NamespacedKey recipeKey() {
        return recipeKey;
    }
}
