package com.ryxmc.protection.energy;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.util.Color;
import com.ryxmc.protection.util.Txt;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/** Gestión de Gemas de Energía e intercambio por Puntos de Protección */
public class EnergyManager {

    public enum GemType {
        SMALL ("Gema de Energía Pequeña",  Material.AMETHYST_SHARD, 0.5,  1001),
        MEDIUM("Gema de Energía Media",    Material.AMETHYST_SHARD, 1.0,  1002),
        LARGE ("Gema de Energía Grande",   Material.AMETHYST_SHARD, 2.0,  1003),
        DIVINE("Gema de Energía Divina",   Material.NETHER_STAR,    -1.0, 1004);

        private final String   name;
        private final Material mat;
        private final double   restore; // -1 = full
        private final int      cmd;

        GemType(String n, Material m, double r, int c) { name=n; mat=m; restore=r; cmd=c; }

        public String   getDisplayName()    { return name; }
        public Material getMaterial()        { return mat; }
        public double   getRestore()         { return restore; }
        public int      getCustomModelData() { return cmd; }
        public boolean  isFullRestore()      { return restore < 0; }
    }

    public static final int GEMS_PER_PP = 100;

    private final RyxProtection plugin;

    public EnergyManager(RyxProtection plugin) { this.plugin = plugin; }

    // ─────────────────────────────────────────────────
    //  Crear ítems de gema
    // ─────────────────────────────────────────────────

    public ItemStack createGem(GemType type) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        String color = switch (type) {
            case SMALL  -> "§a";
            case MEDIUM -> "§b";
            case LARGE  -> "§6";
            case DIVINE -> "§d§l";
        };
        meta.setDisplayName(color + type.getDisplayName());
        meta.setCustomModelData(type.getCustomModelData());

        List<String> lore = new ArrayList<>(Arrays.asList(
                Color.of(Txt.THIN),
                Color.of(type.isFullRestore() ? "§9Restaura: §d§lDTR COMPLETO" : "§9Restaura DTR: §f+" + type.getRestore()),
                Color.of(Txt.THIN),
                Color.of("§7Úsala en el menú de energía"),
                Color.of("§7de tu región para restaurar DTR."),
                Color.of(Txt.THIN)));
        meta.setLore(lore);

        if (type == GemType.DIVINE) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    /** Crea el botón de gema para el GUI de energía, mostrando cuántas tiene el jugador */
    public ItemStack makeGemButton(Player p, GemType type) {
        int count = countGemsInInventory(p, type);
        ItemStack item = createGem(type);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;
        List<String> lore = meta.getLore() != null ? meta.getLore() : new ArrayList<>();
        lore.add(Color.of("§9En tu inventario: §f" + count));
        lore.add(Color.of(count > 0 ? "§a§l▶ Click para usar 1 gema." : "§c✘ Sin gemas de este tipo."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ─────────────────────────────────────────────────
    //  Detectar gema
    // ─────────────────────────────────────────────────

    public GemType detectGem(ItemStack item) {
        if (item == null) return null;
        ItemMeta m = item.getItemMeta();
        if (m == null || !m.hasCustomModelData()) return null;
        int cmd = m.getCustomModelData();
        for (GemType t : GemType.values()) if (t.getCustomModelData() == cmd && item.getType() == t.getMaterial()) return t;
        return null;
    }

    // ─────────────────────────────────────────────────
    //  Aplicar gema a región
    // ─────────────────────────────────────────────────

    public boolean applyGem(Player player, String regionId, GemType gem, int invSlot) {
        com.ryxmc.protection.dtr.DTRData entry = plugin.getDtrManager().get(regionId);

        if (!entry.isRaidable() && entry.getDtr() >= entry.getMaxDtr()) {
            player.sendMessage(Txt.warn("Tu región ya tiene el DTR máximo.")); return false;
        }

        ItemStack gemItem = player.getInventory().getItem(invSlot);
        if (gemItem == null || detectGem(gemItem) != gem) {
            player.sendMessage(Txt.err("No tienes esa gema disponible.")); return false;
        }

        // Consumir gema
        if (gemItem.getAmount() > 1) gemItem.setAmount(gemItem.getAmount() - 1);
        else player.getInventory().setItem(invSlot, null);

        double before = entry.getDtr();

        if (gem.isFullRestore()) {
            plugin.getDtrManager().reset(regionId);
            player.sendMessage(Txt.ok("§d§l✦ ¡Gema Divina usada! DTR restaurado al máximo §f("
                    + String.format("%.1f", entry.getMaxDtr()) + ")§a."));
        } else {
            plugin.getDtrManager().addEnergy(regionId, gem.getRestore());
            player.sendMessage(Txt.ok(gem.getDisplayName() + " §ausada. DTR: §f"
                    + String.format("%.1f", before) + "§7 → §f"
                    + String.format("%.1f", plugin.getDtrManager().get(regionId).getDtr())));
        }

        plugin.getHologramManager().updateRegionHologram(regionId);
        return true;
    }

    // ─────────────────────────────────────────────────
    //  Conteo de gemas
    // ─────────────────────────────────────────────────

    public int countGemsInInventory(Player p, GemType type) {
        int count = 0;
        for (ItemStack item : p.getInventory().getContents())
            if (item != null && detectGem(item) == type) count += item.getAmount();
        return count;
    }

    public int countAllGems(Player p) {
        int total = 0;
        for (GemType t : GemType.values()) total += countGemsInInventory(p, t);
        return total;
    }

    public int findGemSlot(Player p, GemType type) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++)
            if (contents[i] != null && detectGem(contents[i]) == type) return i;
        return -1;
    }

    // ─────────────────────────────────────────────────
    //  Energía del jugador (para upgrades)
    // ─────────────────────────────────────────────────

    /** Devuelve la energía total del jugador (= cantidad total de gemas en su inventario). */
    public int getPlayerEnergy(Player p) {
        return countAllGems(p);
    }

    /**
     * Intenta consumir {@code amount} unidades de energía del inventario del jugador.
     * @return true si había suficientes gemas y se consumieron; false en caso contrario.
     */
    public boolean removePlayerEnergy(Player p, int amount) {
        if (countAllGems(p) < amount) return false;
        int remaining = amount;
        for (GemType type : GemType.values()) {
            if (remaining <= 0) break;
            // Recorrer hasta 41 slots del inventario principal (sin armario)
            for (int i = 0; i < 41 && remaining > 0; i++) {
                ItemStack item = p.getInventory().getItem(i);
                if (item == null || detectGem(item) != type) continue;
                int take = Math.min(item.getAmount(), remaining);
                item.setAmount(item.getAmount() - take);
                p.getInventory().setItem(i, item.getAmount() <= 0 ? null : item);
                remaining -= take;
            }
        }
        return remaining <= 0;
    }

    // ─────────────────────────────────────────────────
    //  Intercambio por PP (100 gemas → 1 PP)
    // ─────────────────────────────────────────────────

    public boolean exchangeGemsForPP(Player player, int ppAmount) {
        int needed    = ppAmount * GEMS_PER_PP;
        int available = countAllGems(player);

        if (available < needed) {
            player.sendMessage(Txt.err("Necesitas §f" + needed + "§c gemas. Tienes §f" + available + "§c.")); return false;
        }

        // Consumir gemas (SMALL primero, luego MEDIUM, LARGE, DIVINE)
        int remaining = needed;
        for (GemType type : GemType.values()) {
            if (remaining <= 0) break;
            ItemStack[] contents = player.getInventory().getContents();
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                if (contents[i] == null || detectGem(contents[i]) != type) continue;
                int take = Math.min(contents[i].getAmount(), remaining);
                contents[i].setAmount(contents[i].getAmount() - take);
                remaining -= take;
            }
        }

        plugin.getUpgradeManager().addPP(player.getUniqueId(), ppAmount);
        int totalPP = plugin.getUpgradeManager().getPP(player.getUniqueId());
        player.sendMessage(Txt.ok("§d§l✦ §f" + needed + " Gemas §a→ §e" + ppAmount
                + " PP§a. Total PP: §e" + totalPP));
        return true;
    }
}
