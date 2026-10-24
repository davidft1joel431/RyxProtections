package com.ryxmc.protection.gui.modern;

import com.ryxmc.protection.util.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * ItemBuilder v1.0 — Constructor fluido de ítems para menús modernos.
 *
 * Ejemplo:
 *   ItemStack item = ItemBuilder.of(Material.NETHER_STAR)
 *           .name("§6§l⬛ §e§lMi Región")
 *           .lore("§9Nivel: §f5", "§9Radio: §f±30")
 *           .thinLine()
 *           .glow(true)
 *           .hideFlags()
 *           .build();
 */
public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta  meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    public ItemBuilder name(String name) {
        if (meta != null) meta.setDisplayName(Color.of(name));
        return this;
    }

    public ItemBuilder lore(String... lines) {
        if (meta == null) return this;
        List<String> lore = new ArrayList<>();
        for (String line : lines) {
            if (line == null) continue;
            lore.add(Color.of(line));
        }
        meta.setLore(lore);
        return this;
    }

    public ItemBuilder addLore(String line) {
        if (meta == null) return this;
        List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        if (line != null) lore.add(Color.of(line));
        meta.setLore(lore);
        return this;
    }

    /** Añade una línea divisoria delgada (§8⎯⎯...). */
    public ItemBuilder thinLine() {
        return addLore("§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯");
    }

    /** Añade un espacio en blanco en el lore. */
    public ItemBuilder spacer() {
        return addLore(" ");
    }

    /** Añade brillo (encantamiento oculto) al ítem. */
    public ItemBuilder glow(boolean glow) {
        if (meta == null) return this;
        if (glow) {
            // Brillo visual sin efecto real (ocultamos el encantamiento)
            for (Enchantment ench : Enchantment.values()) {
                if (ench.getName().equals("arrow_damage") || ench.getName().equals("damage_all")) {
                    meta.addEnchant(ench, 1, true);
                    break;
                }
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    /** Oculta atributos, encantamientos y tooltip adicional. */
    public ItemBuilder hideFlags() {
        if (meta == null) return this;
        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP,
                ItemFlag.HIDE_DESTROYS,
                ItemFlag.HIDE_PLACED_ON);
        return this;
    }

    /** Establece el modelo personalizado (CustomModelData). */
    public ItemBuilder customModelData(int cmd) {
        if (meta != null) meta.setCustomModelData(cmd);
        return this;
    }

    public ItemStack build() {
        if (meta != null) item.setItemMeta(meta);
        return item;
    }
}
