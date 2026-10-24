package com.ryxmc.protection.config;

import com.ryxmc.protection.util.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * ProtectionBlock — Sistema de bloque de protección único con 7 niveles de ore.
 *
 * Reemplaza BlockCfg + BlockLoader (system de block1-5.yml eliminado).
 * Un solo bloque que se mejora in-situ cambiando el material en el mundo.
 *
 * Niveles y materiales:
 *  1 → STONE           (básico, gratis)
 *  2 → COAL_ORE        (carbón)
 *  3 → IRON_ORE        (hierro)
 *  4 → GOLD_ORE        (oro)
 *  5 → DIAMOND_ORE     (diamante)
 *  6 → EMERALD_ORE     (esmeralda)
 *  7 → ANCIENT_DEBRIS  (netherite)
 */
public class ProtectionBlock {

    // Identificador PDC del ítem de protección
    public static final String PDC_KEY_BLOCK = "ps_block";
    public static final String PDC_KEY_LEVEL = "ps_level";

    // CustomModelData del ítem
    public static final int ITEM_CMD = 3001;

    // Datos de cada nivel: {xRadius, baseDtr, ppCost, isPremium(0/1)}
    public static final int[][] LEVELS = {
        {10,  2,    0, 0}, // 1  Básico   — Gratis
        {15,  3,   10, 0}, // 2  Carbón
        {20,  4,   25, 0}, // 3  Hierro
        {25,  5,   50, 0}, // 4  Oro
        {30,  6,  100, 0}, // 5  Diamante
        {40,  8,  250, 1}, // 6  Esmeralda — Premium
        {55, 10,  600, 1}, // 7  Netherite — Premium
    };

    public static final String[] LEVEL_NAMES = {
        "§7ʙáꜱɪᴄᴏ",
        "§8ᴄᴀʀʙóɴ",
        "§fʜɪᴇʀʀᴏ",
        "§6ᴏʀᴏ",
        "§bᴅɪᴀᴍᴀɴᴛᴇ",
        "§aᴇꜱᴍᴇʀᴀʟᴅᴀ",
        "§dɴᴇᴛʜᴇʀɪᴛᴇ",
    };

    public static final Material[] LEVEL_MATERIALS = {
        Material.STONE,
        Material.COAL_ORE,
        Material.IRON_ORE,
        Material.GOLD_ORE,
        Material.DIAMOND_ORE,
        Material.EMERALD_ORE,
        Material.ANCIENT_DEBRIS,
    };

    // ─────────────────────────────────────────────────
    //  Crear ítem
    // ─────────────────────────────────────────────────

    /**
     * Crea el ítem del bloque de protección para el nivel dado.
     * Incluye PDC con nivel, encantamiento visual si nivel > 1,
     * material de ore correspondiente al nivel.
     */
    public static ItemStack buildItem(Plugin plugin, int level) {
        level = clampLevel(level);
        int[] data  = LEVELS[level - 1];
        Material mat = LEVEL_MATERIALS[level - 1];
        String lvlName = LEVEL_NAMES[level - 1];

        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        // Nombre: "Bloque de Protección [nivel]"
        meta.setDisplayName(Color.of("§f§lBloque de Protección §8[" + lvlName + "§8]"));
        meta.setCustomModelData(ITEM_CMD);

        List<String> lore = new ArrayList<>();
        lore.add(Color.of("§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
        lore.add(Color.of("§9Nivel: " + lvlName));
        lore.add(Color.of("§9Radio: §f±" + data[0] + " §8(§f" + (data[0]*2) + "×" + (data[0]*2) + "§8)"));
        lore.add(Color.of("§9DTR base: §f" + data[1]));
        if (level > 1) lore.add(Color.of("§7⚡ Nivel conservado al recolocar"));
        lore.add(Color.of("§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯"));
        lore.add(Color.of("§7Colócalo para crear una zona protegida."));
        meta.setLore(lore);

        // Encantamiento visual (brillo) según nivel
        if (level >= 2) {
            int enchLvl = Math.min((level - 1), 5);
            meta.addEnchant(Enchantment.UNBREAKING, enchLvl, true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        // PDC
        NamespacedKey keyBlock = new NamespacedKey(plugin, PDC_KEY_BLOCK);
        NamespacedKey keyLevel = new NamespacedKey(plugin, PDC_KEY_LEVEL);
        meta.getPersistentDataContainer().set(keyBlock, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(keyLevel, PersistentDataType.INTEGER, level);

        item.setItemMeta(meta);
        return item;
    }

    /** Ítem nivel 1 (básico, para dar por primera vez) */
    public static ItemStack buildBasicItem(Plugin plugin) {
        return buildItem(plugin, 1);
    }

    // ─────────────────────────────────────────────────
    //  Detección
    // ─────────────────────────────────────────────────

    /** ¿Es un ítem de bloque de protección? */
    public static boolean isProtectionBlock(Plugin plugin, ItemStack item) {
        if (item == null) return false;
        ItemMeta m = item.getItemMeta();
        if (m == null) return false;
        // Verificar por PDC primero
        if (m.getPersistentDataContainer().has(
                new NamespacedKey(plugin, PDC_KEY_BLOCK), PersistentDataType.BYTE)) return true;
        // Fallback: material de ore + display name contiene "Protección"
        if (!isOreMaterial(item.getType())) return false;
        if (!m.hasDisplayName()) return false;
        String stripped = org.bukkit.ChatColor.stripColor(m.getDisplayName());
        return stripped != null && stripped.contains("Protección");
    }

    /** Lee el nivel del ítem desde PDC (retorna 1 si no tiene) */
    public static int getItemLevel(Plugin plugin, ItemStack item) {
        if (item == null) return 1;
        ItemMeta m = item.getItemMeta();
        if (m == null) return 1;
        NamespacedKey key = new NamespacedKey(plugin, PDC_KEY_LEVEL);
        Integer lvl = m.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return lvl == null ? 1 : clampLevel(lvl);
    }

    /** ¿Es un material que puede ser bloque de protección? */
    public static boolean isOreMaterial(Material mat) {
        return switch (mat) {
            case STONE, COAL_ORE, IRON_ORE, GOLD_ORE,
                 DIAMOND_ORE, EMERALD_ORE, ANCIENT_DEBRIS -> true;
            default -> false;
        };
    }

    // ─────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────

    public static int     clampLevel(int l) { return Math.max(1, Math.min(LEVELS.length, l)); }
    public static int     maxLevel()        { return LEVELS.length; }
    public static int[]   getData(int lvl)  { return LEVELS[clampLevel(lvl) - 1]; }
    public static int     getXRadius(int lvl) { return getData(lvl)[0]; }
    public static double getBaseDtr(int level) {
        return switch (level) {
            case 1  -> 2.0;
            case 2  -> 3.0;
            case 3  -> 4.0;
            case 4  -> 5.0;
            case 5  -> 6.0;
            case 6  -> 8.0;
            case 7  -> 10.0;
            default -> 5.0;
        };
    }
    public static int     getPPCost(int lvl)  { return getData(lvl)[2]; }
    public static boolean isPremium(int lvl)  { return getData(lvl)[3] == 1; }
    public static Material getMaterial(int lvl){ return LEVEL_MATERIALS[clampLevel(lvl)-1]; }
    public static String   getName(int lvl)   { return LEVEL_NAMES[clampLevel(lvl)-1]; }
    public static boolean  hasNext(int lvl)   { return clampLevel(lvl) < LEVELS.length; }
    public static int      nextLevel(int lvl) { return Math.min(clampLevel(lvl) + 1, LEVELS.length); }

    // ─────────────────────────────────────────────────
    //  Utilidades de display
    // ─────────────────────────────────────────────────

    /** Descripción corta del nivel para logs y mensajes */
    public static String summary(int lvl) {
        return getName(lvl) + " §8(Nv." + clampLevel(lvl) + ")"
                + " §7radio:§f±" + getXRadius(lvl)
                + " §7dtr:§f" + (int)getBaseDtr(lvl)
                + (isPremium(lvl) ? " §d[PREMIUM]" : "");
    }

    /**
     * Costo total acumulado para llegar al nivel dado desde nivel 1.
     * Útil para mostrar "total invertido" en el GUI.
     */
    public static int getCumulativePPCost(int targetLvl) {
        int total = 0;
        for (int l = 2; l <= clampLevel(targetLvl); l++) total += getPPCost(l);
        return total;
    }

    /**
     * Dado un ítem de inventario que tiene PDC de ProtectionBlock,
     * retorna una String de debug con nivel, radio y nombre.
     */
    public static String debugItem(Plugin plugin, ItemStack item) {
        if (!isProtectionBlock(plugin, item)) return "NOT_A_PROTECTION_BLOCK";
        int lvl = getItemLevel(plugin, item);
        return "ProtectionBlock{lvl=" + lvl + ", name=" + getName(lvl)
                + ", xr=" + getXRadius(lvl) + "}";
    }
}
