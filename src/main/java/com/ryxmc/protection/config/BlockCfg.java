package com.ryxmc.protection.config;

import com.ryxmc.protection.util.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * BlockCfg — Configuración de un bloque de protección.
 *
 * @deprecated Reemplazado por {@link ProtectionBlock} en v2.3.
 *   Este sistema (blocks/block1-5.yml) fue eliminado.
 *   Mantener solo por compatibilidad de compilación con código legacy.
 *   No instanciar — usar ProtectionBlock static methods.
 */
@Deprecated(since = "2.3", forRemoval = true)
public class BlockCfg {

    private final String   alias;
    private final String   displayName;
    private final String   description;
    private final Material material;
    private final int      customModelData;
    private final double   price;
    private final int      xRadius;
    private final int      zRadius;
    private final String   upgradesTo;      // alias del siguiente bloque, null si es último
    private final double   baseDtr;

    public BlockCfg(String alias, String displayName, String description,
                    Material material, int customModelData,
                    double price, int xRadius, int zRadius,
                    String upgradesTo, double baseDtr) {
        this.alias           = alias;
        this.displayName     = displayName;
        this.description     = description;
        this.material        = material;
        this.customModelData = customModelData;
        this.price           = price;
        this.xRadius         = xRadius;
        this.zRadius         = zRadius;
        this.upgradesTo      = upgradesTo;
        this.baseDtr         = baseDtr;
    }

    // ─────────────────────────────────────────────────
    //  Crear ítem para inventario
    // ─────────────────────────────────────────────────

    public ItemStack buildItem() {
        ItemStack item = new ItemStack(material);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(Color.of(displayName));
        meta.setCustomModelData(customModelData);

        List<String> lore = new ArrayList<>();
        lore.add(Color.of("§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
        lore.add(Color.of("§7" + description));
        lore.add(Color.of("§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
        lore.add(Color.of("§9Radio: §f±" + xRadius + " §8(§f" + (xRadius*2) + "×" + (zRadius*2) + "§8)"));
        lore.add(Color.of("§9DTR base: §f" + baseDtr));
        lore.add(Color.of("§9Precio: " + (price > 0 ? "§e$" + String.format("%,.0f", price) : "§aGratis")));
        if (canUpgrade()) lore.add(Color.of("§9Mejora a: §b" + upgradesTo));
        lore.add(Color.of("§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
        lore.add(Color.of("§7Colócalo para crear una región protegida."));

        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    // ─────────────────────────────────────────────────
    //  Getters
    // ─────────────────────────────────────────────────

    public String   getAlias()           { return alias; }
    public String   getDisplayName()     { return displayName; }
    public String   getDescription()     { return description; }
    public Material getMaterial()        { return material; }
    public int      getCustomModelData() { return customModelData; }
    public double   getPrice()           { return price; }
    public int      getXRadius()         { return xRadius; }
    public int      getZRadius()         { return zRadius; }
    public String   getUpgradesTo()      { return upgradesTo; }
    public double   getBaseDtr()         { return baseDtr; }
    public boolean  canUpgrade()         { return upgradesTo != null && !upgradesTo.isEmpty(); }

    /** ¿Este ítem de inventario corresponde a este bloque? */
    public boolean matches(ItemStack item) {
        if (item == null || item.getType() != material) return false;
        ItemMeta m = item.getItemMeta();
        return m != null && m.hasCustomModelData() && m.getCustomModelData() == customModelData;
    }
}
