package com.ryxmc.protection.factory;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.gui.GuiManager;
import com.ryxmc.protection.gui.modern.GuiStyle;
import com.ryxmc.protection.gui.modern.ItemBuilder;
import com.ryxmc.protection.util.Color;
import com.ryxmc.protection.util.Txt;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * FactoryShopGui v4.0 — GUI moderna de fábrica y tienda.
 *
 * Mejoras v4.0:
 *  • Usa GuiStyle + ItemBuilder (temas unificados, ítems con brillo, bordes limpios)
 *  • Botones de compra con estado visual (puede comprar / sin saldo / límite)
 *  • Stats de fábrica en tiempo real (velocidad, proceso, duplicador, tier)
 *  • Intercambio gemas → PP con contador visual
 *  • Navegación fluida entre Shop ↔ Upgrade ↔ Fábrica en el mundo
 *  • Animación de entrada integrada
 *  • Prevención de doble-click y cierre seguro de inventarios
 */
public class FactoryShopGui {

    private final RyxProtection plugin;
    /** Jugador → fábrica en el GUI de upgrade abierto actualmente. */
    private final Map<UUID, EnergyFactory> upgradeTargets = new HashMap<>();

    public FactoryShopGui(RyxProtection plugin) { this.plugin = plugin; }

    // ─────────────────────────────────────────────────
    //  GUI Principal de Tienda de Fábricas  (FACTORY_MAIN)
    // ─────────────────────────────────────────────────

    public void openShop(Player p) {
        if (p.getOpenInventory().getTopInventory().getType()
                != org.bukkit.event.inventory.InventoryType.CRAFTING) {
            p.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> doOpenShop(p), 1L);
        } else {
            doOpenShop(p);
        }
    }

    private void doOpenShop(Player p) {
        GuiManager.RyxHolder holder = new GuiManager.RyxHolder(
                GuiManager.GuiType.FACTORY_MAIN, p.getUniqueId(), null);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Color.of("§6⚙ §e§lTienda de Fábricas"));
        holder.setInventory(inv);
        buildShop(inv, p);
        plugin.getGuiManager().register(p.getUniqueId(), holder);
        p.openInventory(inv);
    }

    private void buildShop(Inventory inv, Player p) {
        GuiStyle style = GuiStyle.SHOP;
        // Borde y relleno
        int rows = 6;
        ItemStack border = style.borderPane();
        ItemStack fill   = style.fillPane();
        for (int i = 0; i < 54; i++) {
            int row = i / 9, col = i % 9;
            inv.setItem(i, (row == 0 || row == rows - 1 || col == 0 || col == 8) ? border : fill);
        }

        double balance = plugin.getVaultHook().isAvailable()
                ? plugin.getVaultHook().getBalance(p) : -1;
        int myPP = plugin.getUpgradeManager().getPP(p.getUniqueId());
        int gems  = plugin.getEnergyManager().countAllGems(p);
        Collection<EnergyFactory> myFactories =
                plugin.getFactoryManager().getPlayerFactories(p.getUniqueId());

        // ── Info (slot 4) ──
        inv.setItem(4, ItemBuilder.of(Material.BLAST_FURNACE)
                .name(style.accent + "§l⚙ §e§lTienda de Fábricas")
                .lore(
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        balance >= 0 ? "§9Saldo: §e$" + String.format("%,.0f", balance) : "§7Sin economía.",
                        "§9Tus PP: §e" + myPP,
                        "§9Gemas: §d" + gems,
                        "§9Tus fábricas: §f" + myFactories.size(),
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§7Compra y mejora tus fábricas aquí.")
                .glow(true).hideFlags().build());

        // ── Comprar fábrica (slot 19) ──
        double factoryPrice = plugin.getConfig().getDouble("factory.shop_price", 5000);
        boolean canAfford = balance < 0 || balance >= factoryPrice;
        inv.setItem(19, ItemBuilder.of(Material.BLAST_FURNACE)
                .name(style.accent + "§l⚙ §fComprar Fábrica de Energía")
                .lore(
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§7Produce §dGemas de Energía§7 automáticamente.",
                        "§9Receta: §bCu§f×2 + §eAu§f×1 + §8Carbón§f×1",
                        "§7Coloca hasta §e" + plugin.getConfig().getInt("factory.max_per_player", 3) + "§7 fábricas.",
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§9Precio: §e$" + String.format("%,.0f", factoryPrice),
                        canAfford ? "§a§l▶ Click para comprar" : "§c§l✗ Sin saldo suficiente")
                .glow(canAfford).hideFlags().build());

        // ── Tus fábricas (slot 21) ──
        if (!myFactories.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (EnergyFactory f : myFactories) {
                Location loc = f.getLocation();
                sb.append("§7• §6").append(f.getFactoryId())
                  .append(" §8@ §7").append(loc.getBlockX()).append(",").append(loc.getBlockZ())
                  .append("\n");
            }
            inv.setItem(21, ItemBuilder.of(Material.MAP)
                    .name(style.accent + "§l📋 §fTus Fábricas (§6" + myFactories.size() + "§f)")
                    .lore(
                            "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                            sb.toString().trim(),
                            "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                            "§7Click derecho en una fábrica para gestionarla.")
                    .hideFlags().build());
        }

        // ── Intercambiar gemas → PP (slot 25) ──
        int exchRate = 100;
        int possiblePP = gems / exchRate;
        inv.setItem(25, ItemBuilder.of(Material.AMETHYST_SHARD)
                .name(style.accent + "§l✦ §fIntercambiar Gemas → PP")
                .lore(
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§9Tus gemas: §d" + gems,
                        "§9Tasa: §f" + exchRate + " Gemas = §e1 PP",
                        possiblePP > 0 ? "§9Puedes obtener: §e" + possiblePP + " PP" : "§cNecesitas más gemas.",
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        possiblePP > 0 ? "§a§l▶ Click para intercambiar §e" + possiblePP + " PP" : "")
                .glow(possiblePP > 0).hideFlags().build());

        // ── Info upgrades fábrica (slot 34) ──
        inv.setItem(34, ItemBuilder.of(Material.ANVIL)
                .name(style.accent + "§l⬆ §fMejoras de Fábrica")
                .lore(
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§7Haz click derecho en tu fábrica en el mundo,",
                        "§7luego click en §6⬆ Mejorar§7 (slot inferior derecho).",
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§9Mejoras disponibles:",
                        "§7• Velocidad §8(menos tiempo / gema)",
                        "§7• Proceso   §8(más gemas / ciclo)",
                        "§7• Duplicador§8(chance de doblar output)",
                        "§7• Tier      §8(sube capacidad y velocidad base)")
                .hideFlags().build());

        // ── Modelo Mediano (slot 37) ──
        double modelMedPrice = plugin.getConfig().getDouble("factory.model_medium_price", 1500);
        boolean canMedModel  = balance < 0 || balance >= modelMedPrice;
        inv.setItem(37, ItemBuilder.of(Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE)
                .name(style.accent + "§lModelo §fde §bGema Mediana")
                .lore(
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§7Colócalo en el §5Compresor de Gemas§7.",
                        "§9Receta: §f16 Gemas Pequeñas §7→ §d1 Gema Mediana",
                        "§8⚠ §7Cualquier §bArmor Trim común§7 funciona:",
                        "§8Coast, Dune, Eye, Rib, Snout, Spire,",
                        "§8Tide, Wild, Sentry, Vex §7o §bFlow §8(1.21)",
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§9Precio: §e$" + String.format("%,.0f", modelMedPrice),
                        canMedModel ? "§a§l▶ Click para comprar" : "§c§l✗ Sin saldo suficiente")
                .glow(canMedModel).hideFlags().build());

        // ── Modelo Grande (slot 39) ──
        double modelLargePrice = plugin.getConfig().getDouble("factory.model_large_price", 5000);
        boolean canLargeModel  = balance < 0 || balance >= modelLargePrice;
        inv.setItem(39, ItemBuilder.of(Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE)
                .name(style.accent + "§lModelo §fde §5Gema Grande")
                .lore(
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§7Colócalo en el §5Compresor de Gemas§7.",
                        "§9Receta A: §f64 Gemas Pequeñas §7→ §51 Gema Grande",
                        "§9Receta B: §f4 Gemas Medianas §7→ §51 Gema Grande",
                        "§8(Receta B requiere también Modelo Mediano)",
                        "§8⚠ §7Cualquier §5Armor Trim raro§7 funciona:",
                        "§8Ward, Silence, Wayfinder, Shaper,",
                        "§8Raiser, Host §7o §5Bolt §8(1.21)",
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§9Precio: §e$" + String.format("%,.0f", modelLargePrice),
                        canLargeModel ? "§a§l▶ Click para comprar" : "§c§l✗ Sin saldo suficiente")
                .glow(canLargeModel).hideFlags().build());

        // ── Compresor de gemas (slot 41) ──
        double compPrice  = plugin.getConfig().getDouble("factory.compressor_price", 8000);
        boolean canComp   = balance < 0 || balance >= compPrice;
        inv.setItem(41, ItemBuilder.of(Material.CRAFTER)
                .name(style.accent + "§l⚗ §d§lCompresor de Gemas")
                .lore(
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§7Comprime §dGemas Pequeñas §7→ §dMedianas §7y §5Grandes§7.",
                        "§9Necesitas los §bModelos §8(slots izquierda)§9 para activarlo.",
                        "§9Modelo §bCobre §7→ §d16 peq. = 1 med.",
                        "§9Modelo §5End   §7→ §564 peq. = 1 gde.",
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§9Precio: §e$" + String.format("%,.0f", compPrice),
                        canComp ? "§a§l▶ Click para comprar" : "§c§l✗ Sin saldo suficiente")
                .glow(canComp).hideFlags().build());

        // ── Rellenador (slot 10) ──
        double fillerPrice = plugin.getConfig().getDouble("factory.filler_price", 12000);
        boolean canFiller  = balance < 0 || balance >= fillerPrice;
        inv.setItem(10, ItemBuilder.of(Material.DISPENSER)
                .name(style.accent + "§l⚡ §e§lRellenador")
                .lore(
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§7Auto-rellena fábricas, compresores y",
                        "§7fábricas industriales conectados por tubos.",
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§9Instalación: coloca cofres cerca y conecta",
                        "§9con §bTubos §9hacia las fábricas.",
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§9Precio: §e$" + String.format("%,.0f", fillerPrice),
                        canFiller ? "§a§l▶ Click para comprar" : "§c§l✗ Sin saldo suficiente")
                .glow(canFiller).hideFlags().build());

        // ── Tubo de conductividad (slot 12) ──
        int    tubeAmount = plugin.getConfig().getInt("factory.tube_amount", 8);
        double tubePrice  = plugin.getConfig().getDouble("factory.tube_price", 500);
        boolean canTube   = balance < 0 || balance >= tubePrice;
        inv.setItem(12, ItemBuilder.of(Material.END_ROD)
                .name(style.accent + "§l⚡ §f§lTubo de Conductividad §8(x" + tubeAmount + ")")
                .lore(
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§7Conducto §flineal§7 — sigue líneas rectas.",
                        "§7Reemplaza a las tolvas, más flexible.",
                        "§9Rango máximo: §f64 bloques.",
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§9Precio: §e$" + String.format("%,.0f", tubePrice),
                        canTube ? "§a§l▶ Click para comprar" : "§c§l✗ Sin saldo suficiente")
                .glow(canTube).hideFlags().build());

        // ── Diana Direccional (slot 14) ──
        int    dianaAmount = plugin.getConfig().getInt("factory.diana_amount", 4);
        double dianaPrice  = plugin.getConfig().getDouble("factory.diana_price", 1500);
        boolean canDiana   = balance < 0 || balance >= dianaPrice;
        inv.setItem(14, ItemBuilder.of(Material.TARGET)
                .name(style.accent + "§l◆ §f§lDiana Direccional §8(x" + dianaAmount + ")")
                .lore(
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§71 entrada libre §7→ §feliges UNA salida§7.",
                        "§7Ideal para cruces y ramales controlados.",
                        "§9Clic derecho §f(mano vacía)§9 para rotar.",
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§9Precio: §e$" + String.format("%,.0f", dianaPrice),
                        canDiana ? "§a§l▶ Click para comprar" : "§c§l✗ Sin saldo suficiente")
                .glow(canDiana).hideFlags().build());

        // ── Combinador (slot 16) ──
        int    combAmount = plugin.getConfig().getInt("factory.combiner_amount", 4);
        double combPrice  = plugin.getConfig().getDouble("factory.combiner_price", 2000);
        boolean canComb   = balance < 0 || balance >= combinerPrice();
        inv.setItem(16, ItemBuilder.of(Material.DROPPER)
                .name(style.accent + "§l▼ §f§lCombinador §8(x" + combAmount + ")")
                .lore(
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§a5 entradas §7→ §c1 salida §7(boquilla).",
                        "§7La boquilla rota físicamente — se ve",
                        "§7hacia dónde apunta sin adivinar.",
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§9Precio: §e$" + String.format("%,.0f", combPrice),
                        canComb ? "§a§l▶ Click para comprar" : "§c§l✗ Sin saldo suficiente")
                .glow(canComb).hideFlags().build());

        // ── Fábrica Industrial (slot 43) ──
        double indusPrice = plugin.getConfig().getDouble("factory.industrial_price", 25000);
        boolean canIndus  = balance < 0 || balance >= indusPrice;
        int maxIndus      = plugin.getConfig().getInt("factory.max_industrial_per_player", 2);
        long ownedIndus   = plugin.getIndustrialFactoryManager().getAll().stream()
                .filter(fac -> fac.getOwnerUUID().equals(p.getUniqueId())).count();
        inv.setItem(43, ItemBuilder.of(Material.BLAST_FURNACE)
                .name(style.accent + "§l⚙ §e§lFábrica Industrial §8[Corazón]")
                .lore(
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§7Fábrica multi-bloque §8(3×1×1)§7.",
                        "  §e[Tolva] §7← §f[Corazón] §7→ §e[Dropper]",
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§9Productos: §dGemas §7/ §8Petróleo §7/ §8Carbón",
                        "§9Tienes: §f" + ownedIndus + "§7/§f" + maxIndus,
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§c⚠ Requiere espacio libre §f3×1×1§c.",
                        "§9Precio: §e$" + String.format("%,.0f", indusPrice),
                        ownedIndus >= maxIndus ? "§c§l✗ Límite alcanzado"
                                : canIndus ? "§a§l▶ Click para comprar" : "§c§l✗ Sin saldo suficiente")
                .glow(canIndus && ownedIndus < maxIndus).hideFlags().build());

        // ── Cerrar (slot 49) ──
        inv.setItem(49, ItemBuilder.of(Material.BARRIER)
                .name(style.danger + "§l✖ §fCerrar")
                .lore("§7Cerrar esta tienda.")
                .hideFlags().build());
    }

    private double combinerPrice() {
        return plugin.getConfig().getDouble("factory.combiner_price", 2000);
    }

    // ─────────────────────────────────────────────────
    //  GUI de Upgrades de una Fábrica  (FACTORY_UPGRADE)
    // ─────────────────────────────────────────────────

    public void openUpgrade(Player p, EnergyFactory f) {
        if (p.getOpenInventory().getTopInventory().getType()
                != org.bukkit.event.inventory.InventoryType.CRAFTING) {
            p.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> doOpenUpgrade(p, f), 1L);
        } else {
            doOpenUpgrade(p, f);
        }
    }

    private void doOpenUpgrade(Player p, EnergyFactory f) {
        GuiManager.RyxHolder holder = new GuiManager.RyxHolder(
                GuiManager.GuiType.FACTORY_UPGRADE, p.getUniqueId(), f.getFactoryId());
        Inventory inv = Bukkit.createInventory(holder, 54,
                Color.of("§6⬆ §e§lMejoras §8— §7" + Txt.sc(f.getOwnerName())));
        holder.setInventory(inv);
        upgradeTargets.put(p.getUniqueId(), f);
        buildUpgrade(inv, p, f);
        plugin.getGuiManager().register(p.getUniqueId(), holder);
        p.openInventory(inv);
    }

    private void buildUpgrade(Inventory inv, Player p, EnergyFactory f) {
        GuiStyle style = GuiStyle.UPGRADE;
        int rows = 6;
        ItemStack border = style.borderPane();
        ItemStack fill   = style.fillPane();
        for (int i = 0; i < 54; i++) {
            int row = i / 9, col = i % 9;
            inv.setItem(i, (row == 0 || row == rows - 1 || col == 0 || col == 8) ? border : fill);
        }

        int myPP = plugin.getUpgradeManager().getPP(p.getUniqueId());
        FactoryUpgradeLevel lvl  = f.getUpgradeLevel();
        FactoryUpgradeLevel next = lvl.next();

        // ── Info fábrica (slot 4) ──
        inv.setItem(4, ItemBuilder.of(Material.BLAST_FURNACE)
                .name(style.accent + "§l⚙ §eMejoras §8— §7" + lvl.displayName)
                .lore(
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§9Nivel actual: §f" + lvl.getLevel() + " / " + FactoryUpgradeLevel.values().length,
                        "§9Producción: §f1 gema / §e" + (lvl.ticksPerGem / 20) + "s",
                        "§9Output máx: §e" + lvl.maxOutput,
                        "§9Tus PP: §e" + myPP,
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§7Los upgrades de velocidad/proceso/duplicador",
                        "§7se acumulan independientemente del tier.")
                .glow(true).hideFlags().build());

        // ── Velocidad (slot 10) ──
        int speedLvl  = f.getSpeedLevel();
        int maxSpeed  = 3;
        int speedCost = switch (speedLvl) { case 0 -> 5; case 1 -> 15; default -> 30; };
        boolean canSpeed = speedLvl < maxSpeed;
        inv.setItem(10, ItemBuilder.of(canSpeed ? Material.FEATHER : Material.NETHER_STAR)
                .name((canSpeed ? style.success : style.secondary) + "§l⬆ §fVelocidad §8(Nv." + speedLvl + "/" + maxSpeed + ")")
                .lore(
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§9Efecto: §f-" + (speedLvl * 10) + "% tiempo de producción",
                        canSpeed ? "§9Siguiente: §f-" + ((speedLvl + 1) * 10) + "%" : "§d✦ MÁXIMO",
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        canSpeed ? "§9Costo: §e" + speedCost + " PP" : "",
                        canSpeed ? (myPP >= speedCost ? "§a§l▶ Click para mejorar" : "§cFaltan §f" + (speedCost - myPP) + " PP") : "")
                .glow(canSpeed && myPP >= speedCost).hideFlags().build());

        // ── Proceso (slot 12) ──
        int procLvl  = f.getProcessLevel();
        int maxProc  = 2;
        int procCost = procLvl == 0 ? 5 : 15;
        boolean canProc = procLvl < maxProc;
        inv.setItem(12, ItemBuilder.of(canProc ? Material.GLASS : Material.NETHER_STAR)
                .name((canProc ? style.success : style.secondary) + "§l⬆ §fProceso §8(Nv." + procLvl + "/" + maxProc + ")")
                .lore(
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§9Efecto: §f+" + procLvl + " gema(s) extra/ciclo",
                        canProc ? "§9Siguiente: §f+" + (procLvl + 1) + " gema(s)" : "§d✦ MÁXIMO",
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        canProc ? "§9Costo: §e" + procCost + " PP" : "",
                        canProc ? (myPP >= procCost ? "§a§l▶ Click" : "§cFaltan §f" + (procCost - myPP) + " PP") : "")
                .glow(canProc && myPP >= procCost).hideFlags().build());

        // ── Duplicador (slot 14) ──
        int dupLvl  = f.getDuplicatorLevel();
        int maxDup  = 2;
        int dupCost = dupLvl == 0 ? 20 : 50;
        boolean canDup = dupLvl < maxDup;
        String dupEffect = dupLvl == 1 ? "10%" : dupLvl == 2 ? "25%" : "0%";
        String dupNext   = dupLvl == 0 ? "10%" : dupLvl == 1 ? "25%" : "-";
        inv.setItem(14, ItemBuilder.of(canDup ? Material.DIAMOND : Material.NETHER_STAR)
                .name((canDup ? style.success : style.secondary) + "§l⬆ §fDuplicador §8(Nv." + dupLvl + "/" + maxDup + ")")
                .lore(
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§9Chance de doblar output: §f" + dupEffect,
                        canDup ? "§9Siguiente: §f" + dupNext + " chance" : "§d✦ MÁXIMO",
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        canDup ? "§9Costo: §e" + dupCost + " PP" : "",
                        canDup ? (myPP >= dupCost ? "§a§l▶ Click" : "§cFaltan §f" + (dupCost - myPP) + " PP") : "")
                .glow(canDup && myPP >= dupCost).hideFlags().build());

        // ── Tier (slot 16) ──
        if (next != null) {
            int tierCost = lvl.getUpgradeCost();
            inv.setItem(16, ItemBuilder.of(Material.ANVIL)
                    .name(style.accent + "§l⬆ §fSubir Tier §8(" + lvl.getLevel() + " → " + next.getLevel() + ")")
                    .lore(
                            "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                            "§9Siguiente tier: §f" + next.displayName,
                            "§9Producción: §e1 gema / §f" + (next.ticksPerGem / 20) + "s",
                            "§9Output máx: §e" + next.maxOutput,
                            "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                            "§9Costo: §e" + tierCost + " PP",
                            myPP >= tierCost ? "§a§l▶ Click para subir tier" : "§cFaltan §f" + (tierCost - myPP) + " PP")
                    .glow(myPP >= tierCost).hideFlags().build());
        } else {
            inv.setItem(16, ItemBuilder.of(Material.BEACON)
                    .name(style.secondary + "§l★ §fTier Máximo §8(" + lvl.displayName + ")")
                    .lore(
                            "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                            "§d✦ Esta fábrica ha alcanzado su tier máximo.",
                            "§7Sigue mejorando velocidad, proceso y duplicador.")
                    .hideFlags().build());
        }

        // ── Stats actuales (slot 31) ──
        long ticksActual = Math.max(1, lvl.ticksPerGem - (f.getSpeedLevel() * lvl.ticksPerGem / 10));
        inv.setItem(31, ItemBuilder.of(Material.PAPER)
                .name(style.secondary + "§l📊 §fStats Actuales")
                .lore(
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§9Tiempo real/gema: §f" + (ticksActual / 20.0) + "s",
                        "§9Gemas/ciclo: §f" + f.getOutputPerCycle(),
                        "§9Chance doblar: §f" + (f.getDuplicatorLevel() == 0 ? "0%" :
                                f.getDuplicatorLevel() == 1 ? "10%" : "25%"),
                        "§9Velocidad Nv: §f" + f.getSpeedLevel() + "/" + 3,
                        "§9Proceso Nv: §f" + f.getProcessLevel() + "/" + 2,
                        "§9Duplicador Nv: §f" + f.getDuplicatorLevel() + "/" + 2,
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯")
                .hideFlags().build());

        // ── Navegación ──
        inv.setItem(46, ItemBuilder.of(Material.ARROW)
                .name(style.secondary + "§l« §fVolver a Fábrica")
                .lore("§7Regresar al GUI de la fábrica en el mundo.")
                .hideFlags().build());
        inv.setItem(49, ItemBuilder.of(Material.BARRIER)
                .name(style.danger + "§l✖ §fCerrar")
                .lore("§7Cerrar este menú.")
                .hideFlags().build());
    }

    // ─────────────────────────────────────────────────
    //  Handler de clicks para FACTORY_UPGRADE
    // ─────────────────────────────────────────────────

    public void handleUpgradeClick(Player p, String factoryId, int slot) {
        EnergyFactory f = upgradeTargets.get(p.getUniqueId());
        if (f == null || !f.getFactoryId().equals(factoryId)) {
            for (EnergyFactory ef : plugin.getFactoryManager().getAllFactories()) {
                if (ef.getFactoryId().equals(factoryId)) { f = ef; break; }
            }
            if (f == null) {
                Bukkit.getScheduler().runTask(plugin, (Runnable) p::closeInventory);
                return;
            }
        }
        final EnergyFactory factory = f;

        switch (slot) {
            case 10 -> buyUpgrade(p, factory, "speed");
            case 12 -> buyUpgrade(p, factory, "process");
            case 14 -> buyUpgrade(p, factory, "duplicator");
            case 16 -> buyTierUpgrade(p, factory);
            case 46 -> Bukkit.getScheduler().runTask(plugin,
                    (Runnable) () -> plugin.getFactoryListener().openFactoryGui(p, factory));
            case 49 -> {
                upgradeTargets.remove(p.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, (Runnable) p::closeInventory);
            }
        }
    }

    private void buyUpgrade(Player p, EnergyFactory f, String type) {
        int cost, currentLvl, maxLvl;
        switch (type) {
            case "speed" -> {
                currentLvl = f.getSpeedLevel(); maxLvl = 3;
                cost = switch (currentLvl) { case 0 -> 5; case 1 -> 15; default -> 30; };
                if (currentLvl >= maxLvl) { p.sendMessage(Txt.warn("Velocidad ya está al máximo.")); return; }
                if (!hasPP(p, cost)) return;
                deductPP(p, cost);
                f.setSpeedLevel(currentLvl + 1);
                p.sendMessage(Txt.ok("§a⬆ §fVelocidad Nv." + f.getSpeedLevel() + " activado."));
            }
            case "process" -> {
                currentLvl = f.getProcessLevel(); maxLvl = 2;
                cost = currentLvl == 0 ? 5 : 15;
                if (currentLvl >= maxLvl) { p.sendMessage(Txt.warn("Proceso ya está al máximo.")); return; }
                if (!hasPP(p, cost)) return;
                deductPP(p, cost);
                f.setProcessLevel(currentLvl + 1);
                p.sendMessage(Txt.ok("§a⬆ §fProceso Nv." + f.getProcessLevel() + ". Gemas/ciclo: §e" + f.getOutputPerCycle()));
            }
            case "duplicator" -> {
                currentLvl = f.getDuplicatorLevel(); maxLvl = 2;
                cost = currentLvl == 0 ? 20 : 50;
                if (currentLvl >= maxLvl) { p.sendMessage(Txt.warn("Duplicador ya está al máximo.")); return; }
                if (!hasPP(p, cost)) return;
                deductPP(p, cost);
                f.setDuplicatorLevel(currentLvl + 1);
                String chance = f.getDuplicatorLevel() == 1 ? "10%" : "25%";
                p.sendMessage(Txt.ok("§a⬆ §fDuplicador Nv." + f.getDuplicatorLevel() + " (§f" + chance + " chance§a)."));
            }
        }
        final EnergyFactory fFinal = f;
        Bukkit.getScheduler().runTask(plugin, (Runnable) () -> {
            openUpgrade(p, fFinal);
            plugin.getFactoryManager().updateHolo(fFinal);
        });
    }

    private void buyTierUpgrade(Player p, EnergyFactory f) {
        FactoryUpgradeLevel current = f.getUpgradeLevel();
        FactoryUpgradeLevel next    = current.next();
        if (next == null) { p.sendMessage(Txt.warn("Tier ya está al máximo.")); return; }
        int cost = current.getUpgradeCost();
        if (!hasPP(p, cost)) return;
        deductPP(p, cost);
        f.setUpgradeLevel(next);
        p.sendMessage(Txt.ok("§6⬆ §fFábrica mejorada a §e" + next.displayName + "§a."));
        final EnergyFactory fFinal = f;
        Bukkit.getScheduler().runTask(plugin, (Runnable) () -> {
            openUpgrade(p, fFinal);
            plugin.getFactoryManager().updateHolo(fFinal);
        });
    }

    private boolean hasPP(Player p, int cost) {
        if (plugin.getUpgradeManager().getPP(p.getUniqueId()) < cost) {
            p.sendMessage(Txt.err("Necesitas §e" + cost + " PP§c. Tienes §e"
                    + plugin.getUpgradeManager().getPP(p.getUniqueId()) + "§c."));
            return false;
        }
        return true;
    }

    private void deductPP(Player p, int cost) {
        int current = plugin.getUpgradeManager().getPP(p.getUniqueId());
        plugin.getUpgradeManager().setPP(p.getUniqueId(), current - cost);
    }

    // ── Handler de clicks para FACTORY_MAIN (compra) ──
    public void handleShopClick(Player p, int slot) {
        switch (slot) {
            case 19 -> { // Comprar fábrica
                double price = plugin.getConfig().getDouble("factory.shop_price", 5000);
                if (price > 0) {
                    if (!plugin.getVaultHook().isAvailable()) {
                        p.sendMessage(Txt.err("Economía no disponible. Pide §f/ps givefactory§c a un admin."));
                        return;
                    }
                    if (!plugin.getVaultHook().has(p, price)) {
                        p.sendMessage(Txt.err("Necesitas §e$" + String.format("%,.0f", price) + "§c."));
                        return;
                    }
                    plugin.getVaultHook().withdraw(p, price);
                }
                // Animación de celebración al comprar
                World world = p.getWorld();
                Location loc = p.getLocation().clone().add(0, 1, 0);
                if (world != null) {
                    world.spawnParticle(Particle.END_ROD, loc, 20, 0.5, 0.5, 0.5, 0.1);
                    world.spawnParticle(Particle.END_ROD, loc, 10, 0.3, 0.5, 0.3, 0.05);
                    world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
                }
                Map<Integer, ItemStack> leftover =
                        p.getInventory().addItem(plugin.getFactoryManager().createFactoryItem());
                if (!leftover.isEmpty()) {
                    if (price > 0) plugin.getVaultHook().deposit(p, price);
                    leftover.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
                    p.sendMessage(Txt.warn("Sin espacio. Fábrica dropeada."));
                } else {
                    p.sendMessage(Txt.ok("§6§l⚙ §eFábrica de Energía§a comprada."
                            + (price > 0 ? " §8(§e-$" + String.format("%,.0f", price) + "§8)" : "")));
                    p.closeInventory();
                }
            }
            case 37 -> { // Modelo Mediano
                double price = plugin.getConfig().getDouble("factory.model_medium_price", 1500);
                if (!charge(p, price, "§bModelo de Gema Mediana")) return;
                ItemStack model = new ItemStack(Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE);
                ItemMeta mm = model.getItemMeta();
                if (mm != null) {
                    mm.setDisplayName(Color.of("§b§lModelo §fde §bGema Mediana"));
                    mm.addItemFlags(ItemFlag.values());
                    model.setItemMeta(mm);
                }
                handleGive(p, model, price, "§b§lModelo de Gema Mediana §acomprado.");
            }
            case 39 -> { // Modelo Grande
                double price = plugin.getConfig().getDouble("factory.model_large_price", 5000);
                if (!charge(p, price, "§5Modelo de Gema Grande")) return;
                ItemStack model = new ItemStack(Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE);
                ItemMeta lm = model.getItemMeta();
                if (lm != null) {
                    lm.setDisplayName(Color.of("§5§lModelo §fde §5Gema Grande"));
                    lm.addItemFlags(ItemFlag.values());
                    model.setItemMeta(lm);
                }
                handleGive(p, model, price, "§5§lModelo de Gema Grande §acomprado.");
            }
            case 41 -> { // Compresor
                double price = plugin.getConfig().getDouble("factory.compressor_price", 8000);
                if (!charge(p, price, "§5§l⚗ Compresor de Gemas")) return;
                handleGive(p, plugin.getCompressorManager().createCompressorItem(),
                        price, "§5§l⚗ §d§lCompresor de Gemas §acomprado.");
            }
            case 10 -> { // Rellenador
                double price = plugin.getConfig().getDouble("factory.filler_price", 12000);
                if (!charge(p, price, "§6§l⚡ Rellenador")) return;
                handleGive(p, plugin.getFillerManager().createFillerItem(),
                        price, "§6§l⚡ §e§lRellenador §acomprado.");
            }
            case 12 -> { // Tubos
                double price = plugin.getConfig().getDouble("factory.tube_price", 500);
                int amount = plugin.getConfig().getInt("factory.tube_amount", 8);
                if (!charge(p, price, "§b§l⚡ Tubo de Conductividad")) return;
                ItemStack tube = TubeNetwork.buildTubeItem();
                tube.setAmount(amount);
                handleGive(p, tube, price,
                        "§b§l⚡ §f§lTubo de Conductividad §7x" + amount + " §acomprado.");
            }
            case 14 -> { // Diana
                double price = plugin.getConfig().getDouble("factory.diana_price", 1500);
                int amount = plugin.getConfig().getInt("factory.diana_amount", 4);
                if (!charge(p, price, "§c§l◆ Diana Direccional")) return;
                ItemStack diana = TubeNetwork.buildDirectionalItem();
                diana.setAmount(amount);
                handleGive(p, diana, price,
                        "§c§l◆ §f§lDiana Direccional §7x" + amount + " §acomprada.");
            }
            case 16 -> { // Combinador
                double price = plugin.getConfig().getDouble("factory.combiner_price", 2000);
                int amount = plugin.getConfig().getInt("factory.combiner_amount", 4);
                if (!charge(p, price, "§6§l▼ Combinador")) return;
                ItemStack comb = TubeNetwork.buildCombinerItem();
                comb.setAmount(amount);
                handleGive(p, comb, price,
                        "§6§l▼ §f§lCombinador §7x" + amount + " §acomprado.");
            }
            case 43 -> { // Fábrica Industrial
                double price  = plugin.getConfig().getDouble("factory.industrial_price", 25000);
                int maxIndus2 = plugin.getConfig().getInt("factory.max_industrial_per_player", 2);
                long owned2   = plugin.getIndustrialFactoryManager().getAll().stream()
                        .filter(fac -> fac.getOwnerUUID().equals(p.getUniqueId())).count();
                if (owned2 >= maxIndus2) {
                    p.sendMessage(Txt.err("Ya tienes §e" + owned2 + "§c/§e" + maxIndus2
                            + " §cFábricas Industriales."));
                    return;
                }
                if (!charge(p, price, "§6§l⚙ Fábrica Industrial")) return;
                handleGive(p, plugin.getIndustrialFactoryManager().createHeartItem(),
                        price, "§6§l⚙ §e§lFábrica Industrial §acomprada."
                                + "\n§7Requiere espacio libre §f3×1×1§7 para colocarla.");
            }
            case 25 -> { // Intercambiar gemas
                int gems = plugin.getEnergyManager().countAllGems(p);
                int canGet = gems / 100;
                if (canGet < 1) {
                    p.sendMessage(Txt.err("Necesitas al menos §f100 gemas§c para intercambiar."));
                    return;
                }
                plugin.getEnergyManager().exchangeGemsForPP(p, canGet);
                Bukkit.getScheduler().runTask(plugin, (Runnable) () -> doOpenShop(p));
            }
            case 49 -> p.closeInventory();
        }
    }

    private void handleGive(Player p, ItemStack item, double price, String successMsg) {
        Map<Integer, ItemStack> lr = p.getInventory().addItem(item);
        if (!lr.isEmpty()) {
            if (price > 0) plugin.getVaultHook().deposit(p, price);
            lr.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
            p.sendMessage(Txt.warn("Sin espacio. Ítem dropeado."));
        } else {
            p.sendMessage(Txt.ok(successMsg + (price > 0 ? " §8(§e-$" + String.format("%,.0f", price) + "§8)" : "")));
            p.closeInventory();
        }
    }

    /** Cobra el precio con Vault. Retorna false si no puede pagar. */
    private boolean charge(Player p, double price, String itemName) {
        if (price <= 0) return true;
        if (!plugin.getVaultHook().isAvailable()) {
            p.sendMessage(Txt.err("Economía no disponible. Contacta a un administrador."));
            return false;
        }
        if (!plugin.getVaultHook().has(p, price)) {
            p.sendMessage(Txt.err("Necesitas §e$" + String.format("%,.0f", price)
                    + "§c para comprar §f" + itemName + "§c."
                    + " Tienes §e$" + String.format("%,.0f",
                    plugin.getVaultHook().getBalance(p)) + "§c."));
            return false;
        }
        plugin.getVaultHook().withdraw(p, price);
        return true;
    }
}
