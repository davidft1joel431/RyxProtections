package com.ryxmc.protection.war;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.util.Txt;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * WarListener v1.1
 *
 * ── Fix v1.1 — Cofres no se podían abrir ─────────────────────────────────
 *
 *  CAUSA: WorldGuard maneja PlayerInteractEvent en prioridad HIGH y cancela
 *  cualquier interacción con contenedores en regiones protegidas. Nuestro
 *  listener anterior usaba HIGH, por lo que corría AL MISMO TIEMPO o ANTES
 *  de WorldGuard — el setCancelled(false) era pisado por WorldGuard después.
 *
 *  FIX 1 — onInteract usa EventPriority.HIGHEST:
 *   Corre DESPUÉS de WorldGuard (que usa HIGH). Si WorldGuard canceló la
 *   interacción, nosotros la re-habilitamos con setCancelled(false).
 *
 *  FIX 2 — onInventoryOpen usa HIGHEST + ignoreCancelled=false:
 *   WorldGuard también cancela InventoryOpenEvent. Lo forzamos a abrir si
 *   el bloque detrás es un contenedor válido y la guerra está activa.
 *
 *  FIX 3 — onBlockInteract separado para mecanismos (puertas, botones):
 *   También en HIGHEST para que WorldGuard no los bloquee.
 *
 * ── Reglas durante la guerra ─────────────────────────────────────────────
 *  ✔ Abrir cofres, barriles, tolvas, shulkers, etc.
 *  ✔ Abrir/cerrar puertas, vallas, trampillas
 *  ✔ Activar botones, palancas, placas de presión
 *  ✔ PvP activo en todo el mapa
 *  ✔ Sistema DTR sigue funcionando
 *  ✘ Romper bloques (excepto admins con ryx.admin)
 *  ✘ Acceder al Ender Chest (es personal)
 */
public class WarListener implements Listener {

    private final RyxProtection plugin;

    // ── Bloques contenedor que se pueden abrir (robar) ───────────────────
    private static final Set<Material> CONTAINER_TYPES = Set.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
            Material.HOPPER, Material.DROPPER, Material.DISPENSER,
            Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.BREWING_STAND, Material.CHISELED_BOOKSHELF,
            Material.LECTERN,
            Material.SHULKER_BOX,
            Material.WHITE_SHULKER_BOX,    Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX,  Material.LIGHT_BLUE_SHULKER_BOX,
            Material.YELLOW_SHULKER_BOX,   Material.LIME_SHULKER_BOX,
            Material.PINK_SHULKER_BOX,     Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX, Material.CYAN_SHULKER_BOX,
            Material.PURPLE_SHULKER_BOX,   Material.BLUE_SHULKER_BOX,
            Material.BROWN_SHULKER_BOX,    Material.GREEN_SHULKER_BOX,
            Material.RED_SHULKER_BOX,      Material.BLACK_SHULKER_BOX
    );

    // ── Mecanismos activables (puertas, botones, etc.) ───────────────────
    private static final Set<Material> ACTIVATABLE_TYPES = Set.of(
            // Puertas de madera
            Material.OAK_DOOR, Material.BIRCH_DOOR, Material.SPRUCE_DOOR,
            Material.JUNGLE_DOOR, Material.ACACIA_DOOR, Material.DARK_OAK_DOOR,
            Material.MANGROVE_DOOR, Material.CHERRY_DOOR, Material.BAMBOO_DOOR,
            Material.CRIMSON_DOOR, Material.WARPED_DOOR,
            // Puerta de hierro
            Material.IRON_DOOR,
            // Vallas (fence gates)
            Material.OAK_FENCE_GATE,    Material.BIRCH_FENCE_GATE,
            Material.SPRUCE_FENCE_GATE, Material.JUNGLE_FENCE_GATE,
            Material.ACACIA_FENCE_GATE, Material.DARK_OAK_FENCE_GATE,
            Material.MANGROVE_FENCE_GATE, Material.CHERRY_FENCE_GATE,
            Material.BAMBOO_FENCE_GATE, Material.CRIMSON_FENCE_GATE,
            Material.WARPED_FENCE_GATE,
            // Trampillas
            Material.OAK_TRAPDOOR,    Material.BIRCH_TRAPDOOR,
            Material.SPRUCE_TRAPDOOR, Material.JUNGLE_TRAPDOOR,
            Material.ACACIA_TRAPDOOR, Material.DARK_OAK_TRAPDOOR,
            Material.MANGROVE_TRAPDOOR, Material.CHERRY_TRAPDOOR,
            Material.BAMBOO_TRAPDOOR, Material.CRIMSON_TRAPDOOR,
            Material.WARPED_TRAPDOOR, Material.IRON_TRAPDOOR,
            // Botones
            Material.STONE_BUTTON,    Material.OAK_BUTTON,
            Material.BIRCH_BUTTON,    Material.SPRUCE_BUTTON,
            Material.JUNGLE_BUTTON,   Material.ACACIA_BUTTON,
            Material.DARK_OAK_BUTTON, Material.MANGROVE_BUTTON,
            Material.CHERRY_BUTTON,   Material.BAMBOO_BUTTON,
            Material.CRIMSON_BUTTON,  Material.WARPED_BUTTON,
            Material.POLISHED_BLACKSTONE_BUTTON,
            // Palanca
            Material.LEVER,
            // Placas de presión
            Material.OAK_PRESSURE_PLATE,    Material.BIRCH_PRESSURE_PLATE,
            Material.SPRUCE_PRESSURE_PLATE, Material.JUNGLE_PRESSURE_PLATE,
            Material.ACACIA_PRESSURE_PLATE, Material.DARK_OAK_PRESSURE_PLATE,
            Material.MANGROVE_PRESSURE_PLATE, Material.CHERRY_PRESSURE_PLATE,
            Material.BAMBOO_PRESSURE_PLATE, Material.CRIMSON_PRESSURE_PLATE,
            Material.WARPED_PRESSURE_PLATE, Material.STONE_PRESSURE_PLATE,
            Material.POLISHED_BLACKSTONE_PRESSURE_PLATE,
            Material.LIGHT_WEIGHTED_PRESSURE_PLATE,
            Material.HEAVY_WEIGHTED_PRESSURE_PLATE
    );

    public WarListener(RyxProtection plugin) {
        this.plugin = plugin;
    }

    private WarManager war()  { return plugin.getWarManager(); }
    private boolean    active() { return war() != null && war().isWarActive(); }

    // ─────────────────────────────────────────────────
    //  [FIX] HIGHEST priority — corre DESPUÉS de WorldGuard
    //  Permite abrir contenedores y activar mecanismos
    // ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!active()) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Player   p   = event.getPlayer();
        Material mat = block.getType();

        // ── EnderChest — BLOQUEADO ────────────────────────────────────────
        if (mat == Material.ENDER_CHEST) {
            event.setCancelled(true);
            p.sendMessage(Txt.warn(
                    "§b🔒 §fEl §bEnder Chest §7es personal — §cno accesible §7durante la guerra."));
            return;
        }

        // ── Contenedores — FORZAR apertura ───────────────────────────────
        // setCancelled(false) revierte la cancelación de WorldGuard
        if (CONTAINER_TYPES.contains(mat)) {
            event.setCancelled(false);
            // No hace falta hacer nada más — Bukkit abrirá el inventario
            return;
        }

        // ── Mecanismos activables — FORZAR ────────────────────────────────
        if (ACTIVATABLE_TYPES.contains(mat)) {
            event.setCancelled(false);
        }
    }

    // ─────────────────────────────────────────────────
    //  [FIX] InventoryOpenEvent — WorldGuard también cancela esto
    //  Forzar apertura de cualquier contenedor durante la guerra
    // ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!active()) return;
        if (!(event.getPlayer() instanceof Player p)) return;

        // Solo des-cancelar inventarios de bloques (no el propio del jugador)
        InventoryType type = event.getInventory().getType();
        if (type == InventoryType.PLAYER || type == InventoryType.CREATIVE) return;

        // No des-cancelar EnderChest
        if (type == InventoryType.ENDER_CHEST) return;

        // Si WorldGuard lo canceló, nosotros lo des-cancelamos
        if (event.isCancelled()) {
            event.setCancelled(false);
        }
    }

    // ─────────────────────────────────────────────────
    //  Registrar saqueo al cerrar inventario de contenedor
    // ─────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!active()) return;
        if (!(event.getPlayer() instanceof Player p)) return;

        org.bukkit.inventory.Inventory inv = event.getInventory();
        if (inv.getHolder() == null) return;
        if (inv.getHolder() instanceof Player) return;
        if (inv.getType() == InventoryType.PLAYER) return;
        if (inv.getType() == InventoryType.ENDER_CHEST) return;

        // Registrar 1 acceso a contenedor
        war().registerLoot(p.getUniqueId(), 1);
    }

    // ─────────────────────────────────────────────────
    //  BLOQUEAR romper bloques
    // ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!active()) return;
        Player p = event.getPlayer();
        if (p.hasPermission("ryx.admin")) return;

        event.setCancelled(true);
        p.sendMessage(Txt.err(
                "§c✘ §f§lROMPER BLOQUES §cPROHIBIDO §fdurante la §c§lGUERRA§f."));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.5f);
    }

    // ─────────────────────────────────────────────────
    //  PvP siempre activo durante la guerra (HIGHEST para forzar)
    // ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!active()) return;
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity()  instanceof Player)) return;
        // Durante la guerra el PvP está activo sin restricciones de región
        event.setCancelled(false);
    }

    // ─────────────────────────────────────────────────
    //  Registrar kills
    // ─────────────────────────────────────────────────

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!active()) return;
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            war().registerKill(killer.getUniqueId());
            killer.sendMessage(Txt.ok("§c⚔ §fKill registrado §8— §c+1 §fen el ranking de guerra."));
        }
    }

    // ─────────────────────────────────────────────────
    //  Jugadores que se conectan/desconectan
    // ─────────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        war().onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        war().onPlayerQuit(event.getPlayer());
    }
}
