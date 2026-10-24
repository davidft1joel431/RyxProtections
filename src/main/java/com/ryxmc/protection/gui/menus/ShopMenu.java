package com.ryxmc.protection.gui.menus;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.config.ProtectionBlock;
import com.ryxmc.protection.gui.GuiManager;
import com.ryxmc.protection.util.Txt;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * ShopMenu v2.2 — precios desde config, nunca gratis, DTR por nivel correcto.
 *
 * PRECIOS DEFAULT (config.yml → shop.prices):
 *   Nv.1  Básico      →  $50,000
 *   Nv.2  Carbón      → $200,000
 *   Nv.3  Hierro      → $500,000
 *   Nv.4  Oro         → $1,200,000
 *   Nv.5  Diamante    → $3,000,000
 *   Nv.6  Esmeralda   → $7,500,000  [PREMIUM]
 *   Nv.7  Netherite   → $18,000,000 [PREMIUM]
 */
public class ShopMenu extends BaseMenu {

    private static final int[] BLOCK_SLOTS = {19, 21, 23, 25, 28, 30, 32};

    // Precios default — se sobreescriben desde config.yml (shop.prices.level_N)
    private static final double[] DEFAULT_PRICES = {
        50_000, 200_000, 500_000, 1_200_000,
        3_000_000, 7_500_000, 18_000_000
    };

    public ShopMenu(RyxProtection plugin, GuiManager gui, Menus menus) {
        super(plugin, gui, menus);
    }

    public void open(Player p) {
        gui.openShopGui(p);
    }

    public void onClick(Player p, GuiManager.RyxHolder h, int slot) {

        // Bloques de protección (niveles 1-7)
        for (int i = 0; i < BLOCK_SLOTS.length; i++) {
            if (BLOCK_SLOTS[i] == slot) {
                handleBlockPurchase(p, i + 1);
                return;
            }
        }

        switch (slot) {
            case 34 -> handleCompressorPurchase(p);
            case 44 -> {
                sound(p, Sound.UI_BUTTON_CLICK, 1f, 1f);
                plugin.getFactoryShopGui().openShop(p);
            }
            case 49 -> {
                sClose(p);
                p.closeInventory();
            }
        }
    }

    // ── Compra de bloque de protección ────────────────────────────────────

    private void handleBlockPurchase(Player p, int level) {
        if (level > ProtectionBlock.maxLevel()) return;

        // Verificar permiso premium
        if (ProtectionBlock.isPremium(level) && !p.hasPermission("ryx.upgrade.premium")) {
            sError(p);
            p.sendMessage(Txt.err("§dNivel §6" + ProtectionBlock.getName(level)
                    + "§c requiere rango §dPREMIUM§c."));
            return;
        }

        // Verificar máximo de regiones
        int maxOwned = plugin.getConfig().getInt("protection.max_per_player", 1);
        int owned    = plugin.getRegionStore().getOwnedByPlayer(p.getUniqueId()).size();
        if (owned >= maxOwned) {
            sError(p);
            p.sendMessage(Txt.warn("Ya tienes §e" + owned + "§e/§e" + maxOwned
                    + " §eregión(es). Elimina una con §f/ps unclaim§e."));
            return;
        }

        // No permitir comprar nivel 1 si ya tienes una región
        if (level == 1 && owned > 0) {
            sError(p);
            p.sendMessage(Txt.warn("Ya tienes una región. Mejora la existente con §f/ps mejoras§e."));
            return;
        }

        double price = getLevelPrice(level);

        // Verificar que el precio nunca sea 0 (bug de seguridad económica)
        if (price <= 0) {
            sError(p);
            p.sendMessage(Txt.err("Error de configuración: precio inválido. Contacta a un administrador."));
            plugin.getLogger().warning("[ShopMenu] Precio <= 0 para nivel " + level
                    + " — revisa config.yml → shop.prices.level_" + level);
            return;
        }

        if (!chargePlayer(p, price, ProtectionBlock.getName(level) + " §fBloque")) return;

        ItemStack item = ProtectionBlock.buildItem(plugin, level);
        Map<Integer, ItemStack> leftover = p.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            refundPlayer(p, price);
            leftover.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
            p.sendMessage(Txt.warn("§cSin espacio en inventario. Bloque dropeado."));
        } else {
            sLevelUp(p);
            sound(p, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            p.sendMessage(Txt.ok("§a✔ §fBloque §e" + ProtectionBlock.getName(level)
                    + "§a comprado." + priceTag(price)));
            p.sendMessage(Txt.info("§7Colócalo en el suelo para crear tu §eregión protegida§7."));
            p.closeInventory();
        }
    }

    // ── Compresor de Gemas ────────────────────────────────────────────────

    private void handleCompressorPurchase(Player p) {
        double price = plugin.getConfig().getDouble("factory.compressor_price", 8000);
        if (!chargePlayer(p, price, "§5Compresor de Gemas")) return;

        ItemStack item = plugin.getCompressorManager().createCompressorItem();
        Map<Integer, ItemStack> leftover = p.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            refundPlayer(p, price);
            leftover.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
            p.sendMessage(Txt.warn("Sin espacio. Compresor dropeado."));
        } else {
            sound(p, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
            p.sendMessage(Txt.ok("§5§l⚗ §d§lCompresor de Gemas §acomprado." + priceTag(price)));
            p.closeInventory();
        }
    }

    // ── Precio por nivel ──────────────────────────────────────────────────

    public double getLevelPrice(int level) {
        int idx = Math.max(0, Math.min(level - 1, DEFAULT_PRICES.length - 1));
        double configured = plugin.getConfig().getDouble("shop.prices.level_" + level, -1);
        // Si está configurado en config.yml, usar ese valor; si no, usar el default
        if (configured > 0) return configured;
        return DEFAULT_PRICES[idx];
    }

    // ── Helpers de economía ───────────────────────────────────────────────

    private boolean chargePlayer(Player p, double price, String itemName) {
        if (!plugin.getVaultHook().isAvailable()) {
            p.sendMessage(Txt.err("Economía no disponible. Contacta a un administrador."));
            return false;
        }
        double balance = plugin.getVaultHook().getBalance(p);
        if (balance < price) {
            sError(p);
            p.sendMessage(Txt.err("Necesitas §e$" + String.format("%,.0f", price)
                    + "§c para §f" + itemName + "§c."
                    + " §8(Tienes §e$" + String.format("%,.0f", balance) + "§8)"));
            return false;
        }
        plugin.getVaultHook().withdraw(p, price);
        return true;
    }

    private void refundPlayer(Player p, double price) {
        if (price > 0 && plugin.getVaultHook().isAvailable())
            plugin.getVaultHook().deposit(p, price);
    }

    private String priceTag(double price) {
        return price > 0 ? " §8(§e-$" + String.format("%,.0f", price) + "§8)" : "";
    }
}
