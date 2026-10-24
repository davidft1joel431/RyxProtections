package com.ryxmc.protection.factory;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * TubeNetwork v4.0
 *
 * [NUEVO] Combinador — Material.DROPPER encantado.
 *
 * Es el inverso conceptual de la Diana: en vez de UNA entrada y elegir
 * hacia dónde sale, el Combinador acepta materiales desde las 5 CARAS
 * que NO son la boquilla, y todo converge hacia UNA sola salida — la
 * cara donde apunta la boquilla del dropper (igual que un dropper
 * normal expulsa ítems por su frente).
 *
 * Ventaja visual: a diferencia de la Diana (Target, sin rotación
 * visual propia), el Dropper SÍ tiene una boquilla que rota con el
 * bloque — se ve físicamente hacia dónde apunta la salida.
 *
 * Interacción:
 *  - Clic derecho con el combinador en mano → lo coloca (boquilla
 *    inicial = hacia donde mira el jugador).
 *  - Clic derecho con MANO VACÍA sobre un combinador ya colocado →
 *    cicla la dirección de su boquilla (y el bloque rota visualmente).
 */
public class TubeNetwork {

    public static final Material TUBE_MATERIAL = Material.END_ROD;
    public static final int      TUBE_CMD      = 3001;

    public static final Material DIRECTIONAL_MATERIAL = Material.TARGET;
    public static final int      DIRECTIONAL_CMD       = 3005;

    /** [NUEVO] Material del Combinador (5 entradas → 1 salida) */
    public static final Material COMBINER_MATERIAL = Material.DROPPER;
    public static final int      COMBINER_CMD        = 3006;

    // ─────────────────────────────────────────────────
    //  Tubo lineal
    // ─────────────────────────────────────────────────

    public static ItemStack buildTubeItem() {
        ItemStack item = new ItemStack(TUBE_MATERIAL);
        var meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(com.ryxmc.protection.util.Color.of(
                "§b§l⚡ §fᴛᴜʙᴏ ᴅᴇ ᴄᴏɴᴅᴜᴄᴛɪᴠɪᴅᴀᴅ"));
        meta.setCustomModelData(TUBE_CMD);
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        meta.setLore(List.of(
                com.ryxmc.protection.util.Color.of(com.ryxmc.protection.util.Txt.THIN),
                com.ryxmc.protection.util.Color.of("§7Conducto §flineal§7 — sigue una línea recta."),
                com.ryxmc.protection.util.Color.of("§7Conecta: §6Rellenador §7→ §aFábricas §7/ §5Compresores§7."),
                com.ryxmc.protection.util.Color.of("§7Rango máximo: §f64 bloques."),
                com.ryxmc.protection.util.Color.of(com.ryxmc.protection.util.Txt.THIN),
                com.ryxmc.protection.util.Color.of("§7¿Cruce con 1 salida elegida? §c◆ Diana§7."),
                com.ryxmc.protection.util.Color.of("§7¿Varias entradas → 1 salida? §6▼ Combinador§7."),
                com.ryxmc.protection.util.Color.of(com.ryxmc.protection.util.Txt.THIN),
                com.ryxmc.protection.util.Color.of("§b▶ Clic derecho para colocar y conectar")
        ));
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isTubeItem(ItemStack item) {
        if (item == null || item.getType() != TUBE_MATERIAL) return false;
        var meta = item.getItemMeta();
        return meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == TUBE_CMD;
    }

    // ─────────────────────────────────────────────────
    //  Diana Direccional (1 entrada libre → 1 salida elegida)
    // ─────────────────────────────────────────────────

    public static ItemStack buildDirectionalItem() {
        ItemStack item = new ItemStack(DIRECTIONAL_MATERIAL);
        var meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(com.ryxmc.protection.util.Color.of(
                "§c§l◆ §f§lᴅɪᴀɴᴀ ᴅɪʀᴇᴄᴄɪᴏɴᴀʟ"));
        meta.setCustomModelData(DIRECTIONAL_CMD);
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        meta.setLore(List.of(
                com.ryxmc.protection.util.Color.of(com.ryxmc.protection.util.Txt.THIN),
                com.ryxmc.protection.util.Color.of("§7Nodo direccional de la red de tubos."),
                com.ryxmc.protection.util.Color.of("§7Redirige el flujo hacia §fUNA sola cara"),
                com.ryxmc.protection.util.Color.of("§7elegida §7— control total en cruces y ramales."),
                com.ryxmc.protection.util.Color.of(com.ryxmc.protection.util.Txt.THIN),
                com.ryxmc.protection.util.Color.of("§9Uso:"),
                com.ryxmc.protection.util.Color.of("  §7Clic derecho §f(mano vacía)§7 sobre la diana"),
                com.ryxmc.protection.util.Color.of("  §7ya colocada para §fcambiar su dirección§7."),
                com.ryxmc.protection.util.Color.of(com.ryxmc.protection.util.Txt.THIN),
                com.ryxmc.protection.util.Color.of("§c▶ Clic derecho para colocar")
        ));
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isDirectionalItem(ItemStack item) {
        if (item == null || item.getType() != DIRECTIONAL_MATERIAL) return false;
        var meta = item.getItemMeta();
        return meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == DIRECTIONAL_CMD;
    }

    // ─────────────────────────────────────────────────
    //  [NUEVO] Combinador (5 entradas → 1 salida por la boquilla)
    // ─────────────────────────────────────────────────

    public static ItemStack buildCombinerItem() {
        ItemStack item = new ItemStack(COMBINER_MATERIAL);
        var meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(com.ryxmc.protection.util.Color.of(
                "§6§l▼ §f§lᴄᴏᴍʙɪɴᴀᴅᴏʀ"));
        meta.setCustomModelData(COMBINER_CMD);
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        meta.setLore(List.of(
                com.ryxmc.protection.util.Color.of(com.ryxmc.protection.util.Txt.THIN),
                com.ryxmc.protection.util.Color.of("§7Nodo multi-direccional: §a5 entradas §7→ §c1 salida§7."),
                com.ryxmc.protection.util.Color.of("§7Acepta materiales desde §fcualquiera de sus"),
                com.ryxmc.protection.util.Color.of("§7otras 5 caras§7, y todo converge hacia la"),
                com.ryxmc.protection.util.Color.of("§7única cara de la §6boquilla§7."),
                com.ryxmc.protection.util.Color.of(com.ryxmc.protection.util.Txt.THIN),
                com.ryxmc.protection.util.Color.of("§9Uso:"),
                com.ryxmc.protection.util.Color.of("  §7Clic derecho §f(mano vacía)§7 sobre el"),
                com.ryxmc.protection.util.Color.of("  §7combinador para §frotar la boquilla§7."),
                com.ryxmc.protection.util.Color.of("  §7§o(la boquilla se ve físicamente — sin adivinar)"),
                com.ryxmc.protection.util.Color.of(com.ryxmc.protection.util.Txt.THIN),
                com.ryxmc.protection.util.Color.of("§6▶ Clic derecho para colocar")
        ));
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isCombinerItem(ItemStack item) {
        if (item == null || item.getType() != COMBINER_MATERIAL) return false;
        var meta = item.getItemMeta();
        return meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == COMBINER_CMD;
    }
}
