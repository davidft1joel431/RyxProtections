package com.ryxmc.protection.factory;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.gui.GuiManager;
import com.ryxmc.protection.util.Txt;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * GemCompressorListener v2.0
 *
 * FLUJO DE INTERACCIÓN:
 *  - Click derecho con Armor Trim en mano → insertar modelo
 *  - Click derecho con gema en mano → depositar input
 *  - Click derecho con mano vacía → abrir GUI
 *  - Bedrock: agacharse + click derecho → insertar modelo
 *
 * GUI (solo lectura excepto depositar/retirar):
 *  - Slot INPUT  → depositar/retirar gemas
 *  - Slot MODELO → solo retirar (inserción es desde fuera)
 *  - Slot CERRAR → cerrar
 */
public class GemCompressorListener implements Listener {

    private final RyxProtection    plugin;
    private final GemCompressorGui gui;

    private final Map<UUID, Long> clickCD = new HashMap<>();
    private static final long CD_MS = 300L;

    public GemCompressorListener(RyxProtection plugin, GemCompressorGui gui) {
        this.plugin = plugin;
        this.gui    = gui;
    }

    // ─────────────────────────────────────────────────
    //  Colocar compresor
    // ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player    p    = event.getPlayer();
        ItemStack hand = event.getItemInHand();
        if (!plugin.getCompressorManager().isCompressorItem(hand)) return;
        if (event.getBlockPlaced().getType() != Material.CRAFTER) {
            event.setCancelled(true); return;
        }
        if (!p.hasPermission("ryx.compressor.place")) {
            p.sendMessage(Txt.err("No tienes permiso para colocar un Compresor de Gemas."));
            event.setCancelled(true); return;
        }
        BlockFace facing = toHorizontal(p.getFacing());
        GemCompressor comp = plugin.getCompressorManager()
                .place(p, event.getBlockPlaced().getLocation(), facing);
        if (comp == null) {
            p.sendMessage(Txt.err("Ya existe un compresor en esta ubicación."));
            event.setCancelled(true); return;
        }
        Location loc = event.getBlockPlaced().getLocation();
        loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0.5, 1.2, 0.5),
                20, 0.3, 0.3, 0.3, 0.05);
        loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_PLACE, 1f, 0.8f);
        p.sendMessage(Txt.ok("§5§l⚗ §d§lCompresor §acolocado. "
                + "Sostén un §bArmor Trim §ay haz §bclic derecho §apara insertar el modelo."));
    }

    // ─────────────────────────────────────────────────
    //  Romper compresor
    // ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        Block  b = event.getBlock();
        GemCompressor comp = plugin.getCompressorManager().getAt(b.getLocation());
        if (comp == null) return;

        // Prevenir drop natural SIEMPRE antes de cualquier check
        event.setDropItems(false);

        if (!comp.getOwnerUUID().equals(p.getUniqueId()) && !p.hasPermission("ryx.admin")) {
            p.sendMessage(Txt.err("No puedes romper el compresor de §e" + comp.getOwnerName() + "§c."));
            event.setCancelled(true); return;
        }

        // Cerrar GUI de cualquier jugador que lo tenga abierto
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (gui.isOpen(online.getUniqueId())
                    && gui.getOpen(online.getUniqueId()) == comp) {
                gui.close(online.getUniqueId());
                online.closeInventory();
                if (!online.equals(p))
                    online.sendMessage(Txt.warn("El compresor fue roto."));
            }
        }

        plugin.getCompressorManager().remove(b.getLocation());
        Location drop = b.getLocation().clone().add(0.5, 0.5, 0.5);
        World world = b.getWorld();

        // Dropear ítem del compresor (1 solo)
        world.dropItemNaturally(drop, plugin.getCompressorManager().createCompressorItem());

        // Devolver modelo si había uno
        if (comp.hasModel())
            world.dropItemNaturally(drop, comp.getModel().clone());

        // Devolver input en stacks de 64
        if (comp.getInputStack() != null && comp.getInputCount() > 0)
            world.dropItemNaturally(drop, comp.getInputStack().clone());

        // Dropear output pendiente
        int pending = comp.getPendingOutput();
        com.ryxmc.protection.energy.EnergyManager.GemType type =
                comp.getMode() == GemCompressor.Mode.LARGE
                ? com.ryxmc.protection.energy.EnergyManager.GemType.LARGE
                : com.ryxmc.protection.energy.EnergyManager.GemType.MEDIUM;
        while (pending > 0) {
            int amt = Math.min(pending, 64);
            ItemStack gem = plugin.getEnergyManager().createGem(type);
            gem.setAmount(amt);
            world.dropItemNaturally(drop, gem);
            pending -= amt;
        }

        // Efectos
        world.spawnParticle(Particle.SMOKE, drop, 20, 0.4, 0.4, 0.4, 0.02);
        world.playSound(drop, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 0.8f);
        p.sendMessage(Txt.ok("§5§l⚗ §dCompresor §aremovido. Materiales dropeados."));
    }

    // ─────────────────────────────────────────────────
    //  Click derecho en el bloque
    // ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block b = event.getClickedBlock();
        if (b == null || b.getType() != Material.CRAFTER) return;

        GemCompressor comp = plugin.getCompressorManager().getAt(b.getLocation());
        if (comp == null) return;

        Player p   = event.getPlayer();
        long   now = System.currentTimeMillis();
        if (now - clickCD.getOrDefault(p.getUniqueId(), 0L) < CD_MS) return;
        clickCD.put(p.getUniqueId(), now);
        event.setCancelled(true);

        ItemStack hand = p.getInventory().getItemInMainHand();
        boolean   sneak = p.isSneaking();
        boolean   isBedrock = isBedrock(p);

        // ── 1. Insertar modelo (Armor Trim) ───────────
        // Java: clic derecho con trim en mano
        // Bedrock: agacharse + clic derecho con trim en mano
        if (!hand.getType().isAir()) {
            GemCompressor.Mode detectedMode = gui.detectMode(hand);
            boolean wantsInsert = detectedMode != GemCompressor.Mode.NONE
                    && (!isBedrock || sneak);

            if (wantsInsert) {
                if (comp.hasModel()) {
                    p.sendMessage(Txt.warn("§bEste compresor ya tiene un modelo. "
                            + "Entra al GUI para retirarlo primero."));
                    return;
                }
                ItemStack model = hand.clone();
                model.setAmount(1);
                comp.setModel(model, detectedMode);

                // Quitar 1 del stack en mano
                hand.setAmount(hand.getAmount() - 1);
                if (hand.getAmount() <= 0)
                    p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

                String modeName = detectedMode == GemCompressor.Mode.LARGE
                        ? "§5Grande §8(4 med. → 1 gde.)"
                        : "§bMediano §8(16 peq. → 1 med.)";
                p.sendMessage(Txt.ok("§bModelo " + modeName + "§a insertado."));

                // Efectos
                b.getLocation().getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                        b.getLocation().clone().add(0.5, 1.1, 0.5), 8, 0.3, 0.2, 0.3, 0.01);
                b.getLocation().getWorld().playSound(b.getLocation(),
                        Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);

                plugin.getCompressorManager().save();
                comp.tryStart();
                return;
            }

            // ── 2. Depositar gemas directamente ──────
            com.ryxmc.protection.energy.EnergyManager.GemType detectedGem =
                    plugin.getEnergyManager().detectGem(hand);
            com.ryxmc.protection.energy.EnergyManager.GemType expectedGem =
                    comp.getMode() == GemCompressor.Mode.LARGE
                    ? com.ryxmc.protection.energy.EnergyManager.GemType.MEDIUM
                    : com.ryxmc.protection.energy.EnergyManager.GemType.SMALL;

            if (detectedGem == expectedGem) {
                int existing = comp.getInputCount();
                int space    = 64 - existing;
                if (space <= 0) {
                    p.sendMessage(Txt.warn("§dEntrada llena §8(64/64)§e."));
                    return;
                }
                int toAdd = Math.min(hand.getAmount(), space);
                if (comp.getInputStack() == null) {
                    ItemStack ns = hand.clone(); ns.setAmount(toAdd);
                    comp.setInputStack(ns);
                } else {
                    comp.getInputStack().setAmount(existing + toAdd);
                }
                hand.setAmount(hand.getAmount() - toAdd);
                if (hand.getAmount() <= 0)
                    p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

                p.sendMessage(Txt.ok("§d+" + toAdd + " §a" + comp.getInputGemName()
                        + "§a depositadas. §8(" + (existing + toAdd) + "§8/64)"));
                comp.tryStart();
                plugin.getCompressorManager().save();
                return;
            }
        }

        // ── 3. Abrir GUI (mano vacía o ítem no válido) ─
        gui.open(p, comp);
    }

    /** Prevenir apertura del inventario natural del Crafter si es un compresor */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrafterOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player p)) return;
        if (event.getInventory().getHolder() instanceof org.bukkit.block.Crafter cr) {
            Location loc = cr.getBlock().getLocation();
            if (plugin.getCompressorManager().isCompressor(loc)) {
                event.setCancelled(true);
                if (!gui.isOpen(p.getUniqueId())) {
                    GemCompressor comp = plugin.getCompressorManager().getAt(loc);
                    if (comp != null)
                        Bukkit.getScheduler().runTaskLater(plugin, () -> gui.open(p, comp), 1L);
                }
            }
        }
    }

    // ─────────────────────────────────────────────────
    //  Clicks en el GUI
    // ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (!(event.getInventory().getHolder() instanceof GuiManager.RyxHolder h)) return;
        if (h.getType() != GuiManager.GuiType.FACTORY_MAIN) return;
        String fId = h.getRegionId();
        if (fId == null || !fId.startsWith("comp_")) return;
        GemCompressor comp = gui.getOpen(p.getUniqueId());
        if (comp == null || !comp.getId().equals(fId)) return;
        if (event.getRawSlot() >= event.getInventory().getSize()) return;

        event.setCancelled(true);
        // Sin cursor — todo se maneja desde la mano del jugador
        gui.handleClick(p, comp, event.getSlot(), event.getClick());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player p)) return;
        if (event.getInventory().getHolder() instanceof GuiManager.RyxHolder h
                && h.getType() == GuiManager.GuiType.FACTORY_MAIN
                && h.getRegionId() != null
                && h.getRegionId().startsWith("comp_")) {
            gui.close(p.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        gui.close(uuid);
        clickCD.remove(uuid);
    }

    // ─────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────

    private BlockFace toHorizontal(BlockFace raw) {
        return switch (raw) {
            case NORTH, SOUTH, EAST, WEST -> raw;
            default -> BlockFace.NORTH;
        };
    }

    private boolean isBedrock(Player player) {
        try {
            Class<?> api = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Object instance = api.getMethod("api").invoke(null);
            return (boolean) instance.getClass()
                    .getMethod("isBedrockPlayer", UUID.class)
                    .invoke(instance, player.getUniqueId());
        } catch (Exception ignored) { return false; }
    }
}
