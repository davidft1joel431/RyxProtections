package com.ryxmc.protection.gui.menus;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.gui.GuiManager;
import com.ryxmc.protection.gui.modern.GuiStyle;
import com.ryxmc.protection.gui.modern.ItemBuilder;
import com.ryxmc.protection.manager.RegionStore;
import com.ryxmc.protection.model.ProtectionRole;
import com.ryxmc.protection.util.Txt;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MembersMenu v4.1 — Menú de gestión de miembros con animaciones.
 *
 * Mejoras v4.1:
 *  • GuiStyle.MEMBERS — tema azul claro unificado
 *  • Partículas al abrir y cerrar el menú
 *  • Mejor visualización de roles con colores
 *  • Paginación suave con indicador de página
 *  • Notificación al jugador expulsado
 *
 * Slots:
 *  4  → Info del dueño
 *  10 → Dueño (skull)
 *  11-16, 19-25, 28-34, 37-43 → Miembros (paginados)
 *  45 → Página anterior
 *  49 → Volver a Settings
 *  53 → Página siguiente
 *
 * Clicks en skulls:
 *  LEFT       → Ascender rol
 *  SHIFT_LEFT → Degradar rol
 *  RIGHT      → Expulsar miembro
 */
public class MembersMenu extends BaseMenu {

    private static final int[] MEMBER_SLOTS = {
        11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43
    };

    private final Map<UUID, BukkitTask> particleTasks = new ConcurrentHashMap<>();

    public MembersMenu(RyxProtection plugin, GuiManager gui, Menus menus) {
        super(plugin, gui, menus);
    }

    public void open(Player p, String regionId) {
        gui.openMembersGui(p, regionId);
        startParticleAnimation(p, regionId);
    }

    public void open(Player p, String regionId, int page) {
        gui.openMembersGui(p, regionId, page);
        startParticleAnimation(p, regionId);
    }

    private void startParticleAnimation(Player p, String regionId) {
        RegionStore.RegionRecord r = plugin.getRegionStore().getRecord(regionId);
        if (r == null || r.blockLoc == null) return;

        final Location center = r.blockLoc.clone().add(0.5, 1.5, 0.5);
        final World world = center.getWorld();
        if (world == null) return;

        final int[] frame = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline()) { stopParticleAnimation(p.getUniqueId()); return; }
            Inventory top = p.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof GuiManager.RyxHolder rxh)
                    || !regionId.equals(rxh.getRegionId())) {
                stopParticleAnimation(p.getUniqueId()); return;
            }

            frame[0]++;
            double angle = frame[0] * 0.3;
            double rad = 0.6;

            for (int i = 0; i < 5; i++) {
                double a = angle + (i * Math.PI * 2 / 5);
                Location pos = center.clone().add(
                        rad * Math.cos(a),
                        0.2 + Math.sin(frame[0] * 0.25) * 0.1,
                        rad * Math.sin(a));
                world.spawnParticle(Particle.DUST,
                        pos.getX(), pos.getY(), pos.getZ(),
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(100, 200, 255), 0.9f), true);
            }
        }, 0L, 2L);

        particleTasks.put(p.getUniqueId(), task);
    }

    private void stopParticleAnimation(UUID uuid) {
        BukkitTask t = particleTasks.remove(uuid);
        if (t != null && !t.isCancelled()) t.cancel();
    }

    public void stopAllParticles() {
        particleTasks.values().forEach(t -> { if (!t.isCancelled()) t.cancel(); });
        particleTasks.clear();
    }

    public void onClick(Player p, GuiManager.RyxHolder h, int slot, ClickType click) {
        String rid = h.getRegionId();
        RegionStore.RegionRecord r = plugin.getRegionStore().getRecord(rid);
        if (r == null) return;

        // ── Paginación y navegación ───────────────────
        if (slot == 45) { sBack(p); gui.openMembersGui(p, rid, Math.max(0, h.getExtra() - 1)); return; }
        if (slot == 53) { sClick(p); gui.openMembersGui(p, rid, h.getExtra() + 1); return; }
        if (slot == 49) { sBack(p); menus.settings.open(p, rid); stopParticleAnimation(p.getUniqueId()); return; }

        // ── Solo dueño o admin puede gestionar ────────
        boolean isOwnerOrAdmin = r.ownerUUID.equals(p.getUniqueId())
                || p.hasPermission("ryx.admin");
        if (!isOwnerOrAdmin) return;

        // ── Click en skull de miembro ─────────────────
        Inventory inv = p.getOpenInventory().getTopInventory();
        ItemStack clicked = inv.getItem(slot);
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;
        if (!(clicked.getItemMeta() instanceof SkullMeta sm)) return;

        OfflinePlayer target = sm.getOwningPlayer();
        if (target == null) return;
        UUID targetUUID = target.getUniqueId();
        if (targetUUID.equals(r.ownerUUID)) return; // no tocar al dueño

        ProtectionRole currentRole = r.roles.getOrDefault(targetUUID, ProtectionRole.MEMBER);
        ProtectionRole viewerRole = r.ownerUUID.equals(p.getUniqueId())
                ? ProtectionRole.OWNER
                : r.roles.getOrDefault(p.getUniqueId(), ProtectionRole.MEMBER);

        if (!viewerRole.canManage(currentRole)) {
            p.sendMessage(Txt.err("No tienes permisos para gestionar a este miembro."));
            return;
        }

        switch (click) {
            // ── Ascender rol ──────────────────────────
            case LEFT -> {
                ProtectionRole next = nextRole(currentRole);
                if (next == ProtectionRole.OWNER) {
                    p.sendMessage(Txt.err("No se puede asignar rol de §4DUEÑO§c."));
                    return;
                }
                plugin.getRegionStore().setRole(rid, targetUUID, next);
                sound(p, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
                p.sendMessage(Txt.ok("§eRol de §f" + target.getName()
                        + "§a ascendido a §f" + next.display() + "§a."));
                gui.openMembersGui(p, rid, h.getExtra());
            }

            // ── Degradar rol ──────────────────────────
            case SHIFT_LEFT -> {
                ProtectionRole prev = prevRole(currentRole);
                plugin.getRegionStore().setRole(rid, targetUUID, prev);
                sound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
                p.sendMessage(Txt.ok("§eRol de §f" + target.getName()
                        + "§a degradado a §f" + prev.display() + "§a."));
                gui.openMembersGui(p, rid, h.getExtra());
            }

            // ── Expulsar miembro ──────────────────────
            case RIGHT -> {
                World world = Bukkit.getWorld(r.worldName);
                if (world == null) {
                    p.sendMessage(Txt.err("Mundo no disponible."));
                    return;
                }
                plugin.getRegionStore().removeMember(rid, targetUUID, world);
                sound(p, Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.0f);
                p.sendMessage(Txt.ok("§e" + target.getName() + "§a expulsado de la región."));

                // Notificar al jugador si está online
                Player tOnline = Bukkit.getPlayer(targetUUID);
                if (tOnline != null)
                    tOnline.sendMessage(Txt.warn("Has sido expulsado de la región §e"
                            + plugin.getRegionStore().getDisplayName(rid) + "§e."));

                gui.openMembersGui(p, rid, h.getExtra());
            }

            default -> { /* Ignorar otros tipos de click */ }
        }
    }

    private ProtectionRole nextRole(ProtectionRole r) {
        return switch (r) {
            case MEMBER   -> ProtectionRole.COLEADER;
            case COLEADER -> ProtectionRole.LEADER;
            case LEADER   -> ProtectionRole.COOWNER;
            default       -> r;
        };
    }

    private ProtectionRole prevRole(ProtectionRole r) {
        return switch (r) {
            case COOWNER  -> ProtectionRole.LEADER;
            case LEADER   -> ProtectionRole.COLEADER;
            case COLEADER -> ProtectionRole.MEMBER;
            default       -> r;
        };
    }

    protected ItemStack styledItem(Material mat, String name, String... lore) {
        return ItemBuilder.of(mat)
                .name(com.ryxmc.protection.util.Color.of(name))
                .lore(lore)
                .hideFlags()
                .build();
    }
}
