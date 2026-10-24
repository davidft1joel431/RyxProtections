package com.ryxmc.protection.listener;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.config.ProtectionBlock;
import com.ryxmc.protection.dtr.DTRData;
import com.ryxmc.protection.manager.RegionStore;
import com.ryxmc.protection.util.Txt;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * ProtectionListener v2.5
 *
 * ── Interacción con el bloque físico de protección ───────────────────────
 *
 *  CLICK DERECHO sobre el bloque físico  → abre InfoMenu (gestión de región)
 *  ROMPER (picar) el bloque físico       → dueño/admin: abre ConfirmMenu
 *                                          otros: mensaje de error
 *
 *  CONDICIÓN A (RIGHT_CLICK_BLOCK sobre suelo vacío): colocar región
 *  CONDICIÓN B (RIGHT_CLICK_AIR):                    vista previa de radio
 *  CONDICIÓN C (RIGHT_CLICK_BLOCK sobre bloque de protección): abrir InfoMenu
 *
 * ── DTR regeneración ──────────────────────────────────────────────────────
 *  - Cooldown: 10 minutos sin morir → inicia la regen
 *  - Regen: +0.1 DTR cada 2 minutos
 *  - Solo regenera si DTR < maxDTR y cooldown expiró
 *  - El hologram actualiza cada 5 ticks para countdown en tiempo real
 */
public class ProtectionListener implements Listener {

    private final RyxProtection plugin;

    public ProtectionListener(RyxProtection plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────────────────────────────────
    //  CONDICIÓN C — Click derecho en bloque físico → InfoMenu
    //  CONDICIÓN B — Click derecho al aire → Preview
    //  CONDICIÓN A — Click derecho sobre suelo → colocar bloque
    // ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player p = event.getPlayer();

        // ── CONDICIÓN C: click derecho sobre un bloque de protección ya colocado ──
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null) {

            Location loc = event.getClickedBlock().getLocation();
            RegionStore.RegionRecord reg = plugin.getRegionStore().getByBlockLoc(loc);

            if (reg != null) {
                // Es un bloque de protección registrado → abrir InfoMenu
                event.setCancelled(true);
                plugin.getGuiManager().openInfoGui(p, reg.regionId);
                return;
            }

            // No es un bloque de protección → dejar pasar a onBlockPlace
            // (si el jugador tiene uno en mano, onBlockPlace lo maneja)
            return;
        }

        // ── CONDICIÓN B: click derecho al aire → vista previa ──────────────────
        if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            ItemStack item = p.getInventory().getItemInMainHand();
            if (!ProtectionBlock.isProtectionBlock(plugin, item)) return;
            if (!plugin.getConfig().getBoolean("protection.preview_on_place", true)) return;

            // Toggle: segundo clic al aire apaga el preview
            if (plugin.getPreviewManager().hasPreview(p.getUniqueId())
                    && plugin.getPreviewManager().getMode(p.getUniqueId())
                       == com.ryxmc.protection.manager.PreviewManager.PreviewMode.HELD) {
                plugin.getPreviewManager().stopPreview(p.getUniqueId());
                p.sendMessage(Txt.warn("§b👁 Vista previa §cdesactivada§e."));
                return;
            }

            int lvl     = ProtectionBlock.getItemLevel(plugin, item);
            int xRadius = ProtectionBlock.getXRadius(lvl);
            plugin.getPreviewManager().startHeldPreview(p, xRadius);
            p.sendMessage(Txt.ok("§b👁 Vista previa §aactivada§a."
                    + " Radio: §e±" + xRadius
                    + " §8(" + (xRadius * 2) + "×" + (xRadius * 2) + " bloques)§8."
                    + " §7Clic al aire de nuevo o agáchate para desactivar."));
        }
    }

    // ─────────────────────────────────────────────────
    //  CONDICIÓN A — Colocar región (onBlockPlace)
    // ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player    p    = event.getPlayer();
        ItemStack item = event.getItemInHand();
        if (!ProtectionBlock.isProtectionBlock(plugin, item)) return;

        event.setCancelled(true);

        // ── Validaciones ──────────────────────────────
        int maxOwned = plugin.getConfig().getInt("protection.max_per_player", 1);
        int ownedNow = plugin.getRegionStore().getOwnedByPlayer(p.getUniqueId()).size();
        if (ownedNow >= maxOwned) {
            p.sendMessage(Txt.err("Ya tienes §e" + ownedNow + "§c/§e" + maxOwned
                    + " §eregión(es). Usa §f/ps unclaim §cpara eliminar una."));
            return;
        }

        Location blockLoc  = event.getBlockPlaced().getLocation();
        String   worldName = blockLoc.getWorld().getName();

        if (plugin.getConfig().getStringList("protection.disabled_worlds")
                .contains(worldName)) {
            p.sendMessage(Txt.err("Protecciones desactivadas en §f" + worldName + "§c."));
            return;
        }

        int minDist = plugin.getConfig().getInt("protection.min_distance", 20);
        for (RegionStore.RegionRecord r : plugin.getRegionStore().getAll()) {
            if (r.blockLoc == null
                    || !blockLoc.getWorld().equals(r.blockLoc.getWorld())) continue;
            double dist = blockLoc.distance(r.blockLoc);
            if (dist < minDist) {
                p.sendMessage(Txt.err("Muy cerca de otra protección §8(§fdist: "
                        + String.format("%.1f", dist) + " §8< §f" + minDist + "§8)."));
                return;
            }
        }

        // ── Datos ────────────────────────────────────
        final int      lvl      = ProtectionBlock.getItemLevel(plugin, item);
        final int      xr       = ProtectionBlock.getXRadius(lvl);
        final Material mat      = ProtectionBlock.getMaterial(lvl);
        final String   configId = ProtectionBlock.getName(lvl)
                                        .replace(" ", "_").toLowerCase();
        final Location finalLoc = blockLoc.clone();

        // Descontar ítem ahora (antes del runTaskLater)
        item.setAmount(item.getAmount() - 1);
        plugin.getPreviewManager().stopPreview(p.getUniqueId());

        // ── [FIX] Colocar bloque en el tick siguiente ─
        // event.setCancelled(true) hace que Bukkit revierta el bloque a AIR
        // al final del evento. Con runTaskLater(1L) lo colocamos después.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;

            // Colocar bloque físico
            finalLoc.getBlock().setType(mat, false);

            // Crear región — firma: createRegion(Player, Location, xR, zR, configId)
            String regionId = plugin.getRegionStore()
                    .createRegion(p, finalLoc, xr, xr, configId);

            if (regionId == null) {
                finalLoc.getBlock().setType(Material.AIR, false);
                p.getInventory().addItem(ProtectionBlock.buildItem(plugin, lvl));
                p.sendMessage(Txt.err("No se pudo crear la región. §8(¿Hay una WG aquí?)"));
                return;
            }

            plugin.getUpgradeManager().setLevel(regionId, lvl);
            plugin.getDtrManager().recalcMax(regionId);

            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.3f);
            p.sendMessage(Txt.ok("§a✔ ʀᴇɢɪóɴ §ecreada §8(§f" + regionId + "§8)§a."
                    + " Radio: §f±" + xr
                    + " §8(" + (xr * 2) + "×" + (xr * 2) + " bloques)§8."
                    + " §7Click derecho al bloque para gestionar."));
        }, 1L);
    }

    // ─────────────────────────────────────────────────
    //  Romper el bloque de protección → ConfirmMenu
    // ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player   p   = event.getPlayer();
        Location loc = event.getBlock().getLocation();

        RegionStore.RegionRecord r = plugin.getRegionStore().getByBlockLoc(loc);
        if (r == null) return;

        event.setCancelled(true);

        if (r.ownerUUID.equals(p.getUniqueId()) || p.hasPermission("ryx.admin")) {
            // Dueño/admin: abrir ConfirmMenu directamente
            p.sendMessage(Txt.warn("§7Confirma la eliminación de la región:"));
            // ConfirmMenu necesita que el inventario esté cerrado primero
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    plugin.getGuiManager().openConfirmRemoveGui(p, r.regionId), 1L);
        } else {
            p.sendMessage(Txt.err("No puedes romper el bloque de §e"
                    + r.ownerName + "§c."));
        }
    }

    // ─────────────────────────────────────────────────
    //  Cancelación del preview
    // ─────────────────────────────────────────────────

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player p = event.getPlayer();
        if (!plugin.getPreviewManager().hasPreview(p.getUniqueId())) return;
        if (plugin.getPreviewManager().getMode(p.getUniqueId())
                == com.ryxmc.protection.manager.PreviewManager.PreviewMode.HELD) {
            plugin.getPreviewManager().stopPreview(p.getUniqueId());
            p.sendMessage(Txt.warn("§b👁 Vista previa cancelada."));
        }
    }

    @EventHandler
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        Player p = event.getPlayer();
        if (!plugin.getPreviewManager().hasPreview(p.getUniqueId())) return;
        plugin.getPreviewManager().stopPreview(p.getUniqueId());
        p.sendMessage(Txt.warn("§b👁 Vista previa §cdesactivada§e."));
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player p = event.getPlayer();
        if (!plugin.getPreviewManager().hasPreview(p.getUniqueId())) return;
        if (plugin.getPreviewManager().getMode(p.getUniqueId())
                == com.ryxmc.protection.manager.PreviewManager.PreviewMode.HELD)
            plugin.getPreviewManager().stopPreview(p.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPreviewManager().stopPreview(event.getPlayer().getUniqueId());
    }

    // ─────────────────────────────────────────────────
    //  PvP
    // ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity()  instanceof Player victim))   return;
        if (attacker.equals(victim)) return;

        // ─── Solo aplicar reglas de región si ambos jugadores están en una región ───
        RegionStore.RegionRecord rAtk = plugin.getRegionStore().getAt(attacker.getLocation());
        RegionStore.RegionRecord rVic = plugin.getRegionStore().getAt(victim.getLocation());

        // Región activa (la del atacante tiene prioridad; si no, la de la víctima)
        RegionStore.RegionRecord r = rAtk != null ? rAtk : rVic;
        if (r == null) return; // Fuera de cualquier región → las reglas del server aplican

        // ─── PvP global desactivado → bloquear PvP dentro de regiones (no fuera) ───
        if (!plugin.getConfig().getBoolean("protection.pvp_enabled", false)) {
            event.setCancelled(true);
            attacker.sendMessage(Txt.warn("§c⚔ ᴘᴠᴘ §7está §cdesactivado §7globalmente por un administrador."));
            return;
        }

        DTRData dtr = plugin.getDtrManager().get(r.regionId);
        if (dtr != null && dtr.isRaidable()) return; // raideable → PvP permitido

        if (!plugin.getRegionStore().isPvPEnabled(r.regionId)) {
            event.setCancelled(true);
            attacker.sendMessage(Txt.warn("§c⚔ ᴘᴠᴘ §7desactivado en §e"
                    + plugin.getRegionStore().getDisplayName(r.regionId) + "§e."));
        }
    }

    // ─────────────────────────────────────────────────
    //  Muerte → penalidad DTR
    // ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        java.util.List<String> regions =
                plugin.getRegionStore().getRegionsByPlayer(p.getUniqueId());
        if (!regions.isEmpty())
            plugin.getDtrManager().applyDeath(p, regions);
    }

    // ─────────────────────────────────────────────────
    //  Mobs
    // ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION) return;

        RegionStore.RegionRecord r = plugin.getRegionStore().getAt(event.getLocation());
        if (r == null) return;
        if (plugin.getRegionStoreExt() != null
                && !plugin.getRegionStoreExt().isMobSpawningEnabled(r.regionId))
            event.setCancelled(true);
    }

    // ─────────────────────────────────────────────────
    //  Explosiones
    // ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        if (!plugin.getConfig().getBoolean("protection.block_explosions", true)) return;
        event.blockList().removeIf(block -> {
            RegionStore.RegionRecord r = plugin.getRegionStore().getAt(block.getLocation());
            return r != null && !plugin.getDtrManager().isRaidable(r.regionId);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        if (!plugin.getConfig().getBoolean("protection.block_explosions", true)) return;
        event.blockList().removeIf(block -> {
            RegionStore.RegionRecord r = plugin.getRegionStore().getAt(block.getLocation());
            return r != null && !plugin.getDtrManager().isRaidable(r.regionId);
        });
    }

    // ─────────────────────────────────────────────────
    //  Acceso — lista negra y región bloqueada
    // ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return;
        Player p = event.getPlayer();
        if (p.hasPermission("ryx.admin")) return;

        Location to = event.getTo();
        if (to == null) return;

        RegionStore.RegionRecord r = plugin.getRegionStore().getAt(to);
        if (r == null) return;
        if (r.ownerUUID.equals(p.getUniqueId())) return;
        if (plugin.getRegionStoreExt() == null) return;

        if (plugin.getRegionStoreExt().getBlacklist(r.regionId).contains(p.getUniqueId())) {
            event.setCancelled(true);
            p.sendMessage(Txt.err("§c🚫 Estás en la §4ʟɪꜱᴛᴀ ɴᴇɢʀᴀ §cde esta región."));
            return;
        }

        if (plugin.getRegionStoreExt().isLocked(r.regionId)
                && !r.roles.containsKey(p.getUniqueId())) {
            event.setCancelled(true);
            p.sendMessage(Txt.err("§c🔒 Esta región está §cBLOQUEADA §cpara visitantes."));
        }
    }
}
