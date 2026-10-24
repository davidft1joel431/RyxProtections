package com.ryxmc.protection.factory;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.gui.GuiManager;
import com.ryxmc.protection.util.Color;
import com.ryxmc.protection.util.Txt;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/** FactoryListener v2.2 — GUI fábrica reescrita, sin mensajes duplicados */
public class FactoryListener implements Listener {

    private final RyxProtection plugin;
    private final Map<UUID, EnergyFactory> openFactoryGuis = new HashMap<>();
    private final Map<UUID, org.bukkit.scheduler.BukkitTask> animTasks = new HashMap<>();
    private final Map<UUID, Long>          clickCD         = new HashMap<>();
    private static final long CD_MS = 400L;

    // Títulos de GUI (sin prefijo del plugin)
    private static final String TITLE_FACTORY = "§6⚙ §e§lFábrica de Energía";

    public FactoryListener(RyxProtection plugin) { this.plugin = plugin; }

    // ─────────────────────────────────────────────────
    //  Colocar fábrica
    // ─────────────────────────────────────────────────

    private void stopAnim(java.util.UUID uuid) {
        org.bukkit.scheduler.BukkitTask t = animTasks.remove(uuid);
        if (t != null && !t.isCancelled()) t.cancel();
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        if (!plugin.getFactoryManager().isFactoryItem(event.getItemInHand())) return;
        if (event.getBlockPlaced().getType() != Material.BLAST_FURNACE) {
            event.setCancelled(true); return;
        }
        if (!p.hasPermission("ryx.factory.place")) {
            p.sendMessage(Txt.err("No tienes permiso para colocar fábricas."));
            event.setCancelled(true); return;
        }

        // Límite de fábricas por jugador
        int max = plugin.getConfig().getInt("factory.max_per_player", 3);
        if (max > 0 && plugin.getFactoryManager().getPlayerFactories(p.getUniqueId()).size() >= max) {
            p.sendMessage(Txt.err("Límite de §e" + max + " §cfábricas alcanzado."));
            event.setCancelled(true); return;
        }

        EnergyFactory f = plugin.getFactoryManager().placeFactory(p, event.getBlockPlaced().getLocation());
        if (f == null) {
            p.sendMessage(Txt.err("Ya existe una fábrica aquí."));
            event.setCancelled(true); return;
        }
        p.sendMessage(Txt.ok("§6§l⚙ §e§l¡Fábrica colocada! §7Haz click derecho para abrirla."));
    }

    // ─────────────────────────────────────────────────
    //  Romper fábrica
    // ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        Block  b = event.getBlock();
        if (b.getType() != Material.BLAST_FURNACE) return;

        EnergyFactory f = plugin.getFactoryManager().getFactoryAt(b.getLocation());
        if (f == null) return;

        if (!f.getOwnerUUID().equals(p.getUniqueId()) && !p.hasPermission("ryx.admin")) {
            p.sendMessage(Txt.err("No puedes romper la fábrica de §e" + f.getOwnerName() + "§c."));
            event.setCancelled(true); return;
        }

        boolean ok = plugin.getFactoryManager().removeFactory(b.getLocation(), p);
        if (ok) {
            event.setDropItems(false);
            World world = b.getWorld();
            Location loc = b.getLocation();
            world.dropItemNaturally(loc, plugin.getFactoryManager().createFactoryItem());
            if (f.getCopperStock() > 0) world.dropItemNaturally(loc, new ItemStack(Material.COPPER_INGOT, Math.min(f.getCopperStock(), 64)));
            if (f.getGoldStock()   > 0) world.dropItemNaturally(loc, new ItemStack(Material.GOLD_INGOT,   Math.min(f.getGoldStock(), 64)));
            if (f.getCoalStock()   > 0) world.dropItemNaturally(loc, new ItemStack(Material.COAL,         Math.min(f.getCoalStock(), 64)));
            if (f.getOutputReady() > 0) world.dropItemNaturally(loc,
                    plugin.getEnergyManager().createGem(com.ryxmc.protection.energy.EnergyManager.GemType.SMALL));
            p.sendMessage(Txt.ok("Fábrica removida. Materiales dropeados."));
        }
    }

    // ─────────────────────────────────────────────────
    //  Click derecho → abrir GUI
    // ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block b = event.getClickedBlock();
        if (b == null || b.getType() != Material.BLAST_FURNACE) return;

        EnergyFactory f = plugin.getFactoryManager().getFactoryAt(b.getLocation());
        if (f == null) return;

        event.setCancelled(true);
        Player p = event.getPlayer();
        long now = System.currentTimeMillis();
        if (now - clickCD.getOrDefault(p.getUniqueId(), 0L) < CD_MS) return;
        clickCD.put(p.getUniqueId(), now);

        ItemStack hand = p.getInventory().getItemInMainHand();

        // Click derecho con material → depositar directamente
        if (!hand.getType().isAir() && tryDepositMaterial(p, f, hand, b.getLocation())) return;

        // Mano vacía o material no reconocido → abrir GUI
        openFactoryGui(p, f);
    }

    /** Deposita el ítem en mano como material de la fábrica. Retorna true si fue válido. */
    private boolean tryDepositMaterial(Player p, EnergyFactory f, ItemStack hand, Location loc) {
        FactoryUpgradeLevel lvl = f.getUpgradeLevel();
        Material mat = hand.getType();
        int stock, max;
        String name;
        switch (mat) {
            case COPPER_INGOT -> { stock = f.getCopperStock(); max = lvl.maxCopper; name = "§bCobre"; }
            case GOLD_INGOT   -> { stock = f.getGoldStock();   max = lvl.maxGold;   name = "§eOro"; }
            case COAL         -> { stock = f.getCoalStock();   max = lvl.maxCoal;   name = "§8Carbón"; }
            default -> { return false; }
        }
        int space = max - stock;
        if (space <= 0) {
            p.sendMessage(com.ryxmc.protection.util.Txt.warn(name + "§e lleno §8(" + max + "/" + max + ")§e."));
            return true;
        }
        int toAdd = Math.min(hand.getAmount(), space);
        switch (mat) {
            case COPPER_INGOT -> f.setCopperStock(stock + toAdd);
            case GOLD_INGOT   -> f.setGoldStock(stock + toAdd);
            case COAL         -> f.setCoalStock(stock + toAdd);
        }
        hand.setAmount(hand.getAmount() - toAdd);
        if (hand.getAmount() <= 0) p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        p.sendMessage(com.ryxmc.protection.util.Txt.ok("§a+" + toAdd + " " + name
                + "§a depositado §8(" + (stock + toAdd) + "/" + max + ")§a."));
        f.tryStart();
        plugin.getFactoryManager().save();
        World world = loc.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0.5, 1.1, 0.5), 6, 0.2, 0.1, 0.2, 0.01);
            world.playSound(loc, Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
        }
        return true;
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void preventFurnaceOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player p)) return;
        if (event.getInventory().getHolder() instanceof org.bukkit.block.BlastFurnace bf) {
            if (plugin.getFactoryManager().isFactory(bf.getLocation())) {
                event.setCancelled(true);
                EnergyFactory f = plugin.getFactoryManager().getFactoryAt(bf.getLocation());
                if (f != null) Bukkit.getScheduler().runTaskLater(plugin, () -> openFactoryGui(p, f), 1L);
            }
        }
    }

    // ─────────────────────────────────────────────────
    //  Abrir / Construir GUI
    // ─────────────────────────────────────────────────

    public void openFactoryGui(Player p, EnergyFactory f) {
        GuiManager.RyxHolder holder = new GuiManager.RyxHolder(
                GuiManager.GuiType.FACTORY_MAIN, p.getUniqueId(), f.getFactoryId());

        Inventory inv = Bukkit.createInventory(holder, 54, Color.of(TITLE_FACTORY
                + " §8| §7" + Txt.sc(f.getOwnerName())));
        holder.setInventory(inv);
        buildGui(inv, p, f);
        plugin.getGuiManager().register(p.getUniqueId(), holder);
        openFactoryGuis.put(p.getUniqueId(), f);
        p.openInventory(inv);
    }

    private void buildGui(Inventory inv, Player p, EnergyFactory f) {
        FactoryUpgradeLevel lvl = f.getUpgradeLevel();

        // Bordes
        for (int i = 0; i < 54; i++) {
            int row = i/9, col = i%9;
            if (row == 0 || row == 5 || col == 0 || col == 8)
                inv.setItem(i, pane(Material.GRAY_STAINED_GLASS_PANE));
            else
                inv.setItem(i, pane(Material.BLACK_STAINED_GLASS_PANE));
        }

        // Info general (slot 4)
        String stateStr = switch (f.getState()) {
            case IDLE    -> "§7EN ESPERA";
            case RUNNING -> "§aFUNCIONANDO";
            case DONE    -> "§e§l¡LISTO!";
        };
        inv.setItem(4, makeItem(Material.BLAST_FURNACE, "§6§l⚙ §e§lFábrica de Energía",
                Txt.THIN,
                "§7Dueño: §e" + Txt.sc(f.getOwnerName()),
                "§7Nivel: §f" + lvl.displayName,
                "§7Estado: " + stateStr,
                "§7Progreso: " + f.getProgressBar(10),
                Txt.THIN,
                "§9Velocidad: §e1 gema / " + (lvl.ticksPerGem / 20) + "s",
                "§9Output/ciclo: §e" + f.getOutputPerCycle(),
                "§9Output máx: §e" + lvl.maxOutput));

        // Cobre (slot 19)
        int cuSpace = f.getAvailableSpace(Material.COPPER_INGOT);
        inv.setItem(19, makeItem(Material.COPPER_INGOT,
                "§b§lCobre §8[click = depositar todo]",
                Txt.THIN,
                "§7En fábrica: §b" + f.getCopperStock() + "§8/§b" + lvl.maxCopper,
                "§7En inventario: §f" + countMat(p, Material.COPPER_INGOT),
                cuSpace > 0 ? "§7Espacio libre: §a" + cuSpace : "§c§lCAPACIDAD LLENA",
                Txt.THIN,
                cuSpace > 0 ? "§a▶ Click para depositar" : "§c✗ Sin espacio"));

        // Oro (slot 21)
        int auSpace = f.getAvailableSpace(Material.GOLD_INGOT);
        inv.setItem(21, makeItem(Material.GOLD_INGOT,
                "§e§lOro §8[click = depositar todo]",
                Txt.THIN,
                "§7En fábrica: §e" + f.getGoldStock() + "§8/§e" + lvl.maxGold,
                "§7En inventario: §f" + countMat(p, Material.GOLD_INGOT),
                auSpace > 0 ? "§7Espacio libre: §a" + auSpace : "§c§lCAPACIDAD LLENA",
                Txt.THIN,
                auSpace > 0 ? "§a▶ Click para depositar" : "§c✗ Sin espacio"));

        // Carbón (slot 23)
        int coSpace = f.getAvailableSpace(Material.COAL);
        inv.setItem(23, makeItem(Material.COAL,
                "§8§lCarbón §7[click = depositar todo]",
                Txt.THIN,
                "§7En fábrica: §8" + f.getCoalStock() + "§8/§7" + lvl.maxCoal,
                "§7En inventario: §f" + (countMat(p, Material.COAL) + countMat(p, Material.CHARCOAL)),
                coSpace > 0 ? "§7Espacio libre: §a" + coSpace : "§c§lCAPACIDAD LLENA",
                Txt.THIN,
                coSpace > 0 ? "§a▶ Click para depositar" : "§c✗ Sin espacio"));

        // Iniciar/Detener (slot 25)
        boolean canStart = f.hasRequiredMaterials() && f.getState() != EnergyFactory.State.RUNNING;
        inv.setItem(25, makeItem(canStart ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                canStart ? "§a§l▶ §fIniciar Producción" : "§c§l✘ §fNo disponible",
                Txt.THIN,
                "§9Cu: §b" + f.getCopperStock() + "§8/§b2 §9Au: §e" + f.getGoldStock()
                        + "§8/§e1 §9🔥: §8" + f.getCoalStock() + "§8/§71",
                Txt.THIN,
                canStart ? "§a▶ Click para iniciar"
                        : (f.getState() == EnergyFactory.State.RUNNING ? "§7Ya está funcionando."
                        : "§c✗ Faltan materiales.")));

        // Recoger output (slot 34)
        boolean hasOut = f.hasOutput();
        inv.setItem(34, makeItem(hasOut ? Material.AMETHYST_SHARD : Material.GRAY_DYE,
                hasOut ? "§d§l✦ §fRecoger §d" + f.getOutputReady() + " §fGema(s)"
                       : "§7Sin gemas disponibles",
                Txt.THIN,
                hasOut ? "§9Listo: §d" + f.getOutputReady() + "§8/§d" + lvl.maxOutput
                       : "§7Espera a que la fábrica produzca.",
                Txt.THIN,
                hasOut ? "§a▶ Click para recoger" : ""));

        // Mejorar fábrica (slot 43)
        FactoryUpgradeLevel next = lvl.next();
        inv.setItem(43, makeItem(next != null ? Material.ANVIL : Material.NETHER_STAR,
                next != null ? "§6§l⬆ §fMejorar Fábrica" : "§d§l★ §fNivel Máximo",
                Txt.THIN,
                next != null ? "§7Siguiente: §f" + next.displayName : "§7Esta fábrica es suprema.",
                next != null ? "§7Click para ver costos" : ""));

        // Cerrar (slot 49)
        inv.setItem(49, makeItem(Material.BARRIER, "§c✖ §fCerrar"));
    }

    // ─────────────────────────────────────────────────
    //  Click handler
    // ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInvClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        EnergyFactory f = openFactoryGuis.get(p.getUniqueId());
        if (f == null) return;
        if (!(event.getInventory().getHolder() instanceof GuiManager.RyxHolder h)) return;
        if (h.getType() != GuiManager.GuiType.FACTORY_MAIN) return;
        event.setCancelled(true);
        if (event.getRawSlot() >= event.getInventory().getSize()) return;
        handleClick(p, f, event.getSlot());
    }

    private void handleClick(Player p, EnergyFactory f, int slot) {
        switch (slot) {
            case 19 -> { // Cobre
                int d = plugin.getFactoryManager().depositMaterial(p, f, Material.COPPER_INGOT, 128);
                if (d > 0) p.sendMessage(Txt.ok("Depositaste §b" + d + " Cobre§a."));
                else p.sendMessage(Txt.warn(f.getAvailableSpace(Material.COPPER_INGOT) <= 0
                        ? "§cCapacidad de cobre llena." : "§cNo tienes cobre en el inventario."));
                refresh(p, f);
            }
            case 21 -> { // Oro
                int d = plugin.getFactoryManager().depositMaterial(p, f, Material.GOLD_INGOT, 128);
                if (d > 0) p.sendMessage(Txt.ok("Depositaste §e" + d + " Oro§a."));
                else p.sendMessage(Txt.warn(f.getAvailableSpace(Material.GOLD_INGOT) <= 0
                        ? "§cCapacidad de oro llena." : "§cNo tienes oro en el inventario."));
                refresh(p, f);
            }
            case 23 -> { // Carbón
                int d = plugin.getFactoryManager().depositMaterial(p, f, Material.COAL, 128);
                if (d > 0) p.sendMessage(Txt.ok("Depositaste §8" + d + " §7Carbón§a."));
                else p.sendMessage(Txt.warn(f.getAvailableSpace(Material.COAL) <= 0
                        ? "§cCapacidad de carbón llena." : "§cNo tienes carbón en el inventario."));
                refresh(p, f);
            }
            case 25 -> { // Iniciar
                if (f.tryStart()) p.sendMessage(Txt.ok("¡Fábrica iniciada!"));
                else p.sendMessage(Txt.warn(f.getState() == EnergyFactory.State.RUNNING
                        ? "Ya está funcionando." : "Faltan materiales: §bCu×2 + §eAu×1 + §8🔥×1§e."));
                refresh(p, f);
            }
            case 34 -> { // Recoger
                int c = plugin.getFactoryManager().collectOutput(p, f);
                if (c > 0) p.sendMessage(Txt.ok("Recogiste §d" + c + " Gema(s) de Energía§a."));
                else p.sendMessage(Txt.warn("Sin gemas para recoger aún."));
                refresh(p, f);
            }
            case 43 -> { // Upgrade
                // CRÍTICO: diferir al siguiente tick.
                // Llamar openInventory() dentro del mismo tick del InventoryClickEvent
                // hace que Paper cierre el nuevo inventario inmediatamente en Paper 1.21.
                final EnergyFactory fFinal = f;
                Bukkit.getScheduler().runTask(plugin,
                        (Runnable) () -> plugin.getFactoryShopGui().openUpgrade(p, fFinal));
            }
            case 49 -> { // Cerrar
                openFactoryGuis.remove(p.getUniqueId());
                stopAnim(p.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, (Runnable) p::closeInventory);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player p)) return;
        // Solo limpiar si el inventario cerrado es el GUI de la fábrica (FACTORY_MAIN con regionId real)
        if (event.getInventory().getHolder() instanceof GuiManager.RyxHolder h
                && h.getType() == GuiManager.GuiType.FACTORY_MAIN
                && h.getRegionId() != null
                && !h.getRegionId().startsWith("comp_")) {
            openFactoryGuis.remove(p.getUniqueId());
            stopAnim(p.getUniqueId());
        }
    }

    private void refresh(Player p, EnergyFactory f) {
        Inventory top = p.getOpenInventory().getTopInventory();
        if (top != null && top.getHolder() instanceof GuiManager.RyxHolder)
            buildGui(top, p, f);
    }

    // ─────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────

    private int countMat(Player p, Material mat) {
        int count = 0;
        for (ItemStack i : p.getInventory().getContents())
            if (i != null && i.getType() == mat) count += i.getAmount();
        return count;
    }

    private ItemStack pane(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(" "); item.setItemMeta(meta); }
        return item;
    }

    private ItemStack makeItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(Color.of(name));
        List<String> l = new ArrayList<>();
        for (String s : lore) if (s != null && !s.isEmpty()) l.add(Color.of(s));
        meta.setLore(l);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }
}
