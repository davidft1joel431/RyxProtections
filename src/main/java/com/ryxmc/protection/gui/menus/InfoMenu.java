package com.ryxmc.protection.gui.menus;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.config.ProtectionBlock;
import com.ryxmc.protection.dtr.DTRData;
import com.ryxmc.protection.gui.GuiManager;
import com.ryxmc.protection.gui.modern.GuiStyle;
import com.ryxmc.protection.gui.modern.ItemBuilder;
import com.ryxmc.protection.manager.PreviewManager;
import com.ryxmc.protection.manager.RegionStore;
import com.ryxmc.protection.util.Txt;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * InfoMenu v4.1 — Menú de información de región con animaciones y nuevas funciones.
 *
 * Mejoras v4.1:
 *  • GuiStyle.INFO — tema azul/cian unificado
 *  • ItemBuilder — ítems con brillo, bordes, divisores
 *  • Partículas de entrada al abrir el menú
 *  • Slot de estadísticas en tiempo real (TPS, jugadores, regiones)
 *  • Slot de holograma (ver/ocultar)
 *  • Slot de exportar datos (para admins)
 *  • Animación de partículas girando alrededor del ícono de región
 *
 * Slots:
 *  13 → Info completa en chat
 *  15 → Estadísticas del servidor
 *  20 → ID de región
 *  22 → Energía/DTR (EnergyMenu)
 *  25 → Holograma (ver/ocultar)
 *  30 → Ver radio (preview partículas)
 *  31 → Home (teletransportar)
 *  32 → Miembros (MembersMenu)
 *  33 → Exportar región (solo admin)
 *  37 → Configuración (solo dueño → SettingsMenu)
 *  40 → Mejoras PP (solo dueño → UpgradeMenu)
 *  46 → Volver
 *  49 → Cerrar
 */
public class InfoMenu extends BaseMenu {

    private static final int SLOT_STATS    = 15;
    private static final int SLOT_HOLOGRAM = 25;
    private static final int SLOT_EXPORT   = 33;

    private final Map<UUID, BukkitTask> particleTasks = new HashMap<>();
    private final Set<String> hiddenHolograms = new HashSet<>();

    public InfoMenu(RyxProtection plugin, GuiManager gui, Menus menus) {
        super(plugin, gui, menus);
    }

    public void open(Player p, String regionId) {
        gui.openInfoGui(p, regionId);
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
            double angle = frame[0] * 0.25;
            double rad = 0.8;

            for (int i = 0; i < 6; i++) {
                double a = angle + (i * Math.PI * 2 / 6);
                Location pos = center.clone().add(
                        rad * Math.cos(a),
                        0.3 + Math.sin(frame[0] * 0.2) * 0.15,
                        rad * Math.sin(a));
                world.spawnParticle(Particle.DUST,
                        pos.getX(), pos.getY(), pos.getZ(),
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(0, 191, 255), 1.0f), true);
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

    public void onClick(Player p, GuiManager.RyxHolder h, int slot) {
        String rid = h.getRegionId();

        switch (slot) {
            // ── Slot 13: Info completa en chat ────────
            case 13 -> {
                sClick(p);
                RegionStore.RegionRecord r = plugin.getRegionStore().getRecord(rid);
                if (r == null) return;
                DTRData dtr = plugin.getDtrManager().get(rid);
                int lvl = plugin.getUpgradeManager().getLevel(rid);
                int xr  = plugin.getUpgradeManager().getXRadius(rid);
                int pp  = plugin.getUpgradeManager().getPP(p.getUniqueId());

                p.sendMessage(Txt.SEP);
                p.sendMessage("  §e§l⬛ §fRegión: §e" + plugin.getRegionStore().getDisplayName(rid));
                p.sendMessage(Txt.SEP);
                p.sendMessage("§9Dueño:    §f" + r.ownerName);
                p.sendMessage("§9Nivel:    " + ProtectionBlock.getName(lvl) + " §8(Nv." + lvl + ")");
                p.sendMessage("§9Radio:    §f±" + xr + " §8(" + (xr*2) + "x" + (xr*2) + " bloques)");
                p.sendMessage("§9Miembros: §f" + (r.roles.size() + 1));
                p.sendMessage("§9DTR:      " + dtr.formatted());
                p.sendMessage("§9Estado:   " + (dtr.isRaidable() ? "§4§l⚠ RAIDEABLE" : "§a✔ Protegida"));
                p.sendMessage("§9PvP:      " + (plugin.getRegionStore().isPvPEnabled(rid)
                        ? "§cActivado" : "§aDesactivado"));
                if (dtr.isInCooldown())
                    p.sendMessage("§9Cooldown: §f" + Txt.time(dtr.cooldownSecondsLeft()));
                if (h.getType() == GuiManager.GuiType.PROTECTION_INFO_OWNER) {
                    p.sendMessage(Txt.THIN);
                    p.sendMessage("§9PP:       §e" + pp);
                    p.sendMessage("§9Gemas:    §d" + plugin.getEnergyManager().countAllGems(p));
                }
                p.sendMessage(Txt.SEP);
            }

            // ── Slot 15: Estadísticas del servidor ─────
            case SLOT_STATS -> {
                sClick(p);
                int regions  = plugin.getRegionStore().getAll().size();
                int raidable = (int) plugin.getRegionStore().getAll().stream()
                        .filter(r -> plugin.getDtrManager().isRaidable(r.regionId)).count();
                int facs     = plugin.getFactoryManager().getAllFactories().size();
                int comps    = plugin.getCompressorManager().getAll().size();
                int online   = Bukkit.getOnlinePlayers().size();
                double[] tps = Bukkit.getServer().getTPS();

                p.sendMessage(Txt.SEP);
                p.sendMessage("  §b§l📊 §fEstadísticas del Servidor");
                p.sendMessage(Txt.SEP);
                p.sendMessage("§9Jugadores:   §f" + online + "§8/§f" + Bukkit.getMaxPlayers());
                p.sendMessage("§9Regiones:    §f" + regions + " §8(§c" + raidable + " raideables§8)");
                p.sendMessage("§9Fábricas:    §e" + facs);
                p.sendMessage("§9Compresores: §d" + comps);
                p.sendMessage("§9TPS:         §f" + String.format("%.1f", tps[0]));
                p.sendMessage(Txt.SEP);
            }

            // ── Slot 20: ID de región ──────────────────
            case 20 -> {
                sClick(p);
                p.sendMessage(Txt.SEP);
                p.sendMessage("  §7§l📋 §fID de la Región:");
                p.sendMessage(Txt.SEP);
                p.sendMessage("§9ID: §f" + rid);
                p.sendMessage("§9Nombre: §e" + plugin.getRegionStore().getDisplayName(rid));
                p.sendMessage(Txt.SEP);
            }

            // ── Slot 22: Energía / Gemas DTR ─────────
            case 22 -> {
                sClick(p);
                menus.energy.open(p, rid);
            }

            // ── Slot 25: Holograma (ver/ocultar) ───────
            case SLOT_HOLOGRAM -> {
                sClick(p);
                RegionStore.RegionRecord r = plugin.getRegionStore().getRecord(rid);
                if (r == null) return;
                if (hiddenHolograms.contains(rid)) {
                    plugin.getHologramManager().spawnRegionHologram(rid);
                    hiddenHolograms.remove(rid);
                    p.sendMessage(Txt.ok("§aHolograma mostrado."));
                } else {
                    plugin.getHologramManager().removeHologram(rid);
                    hiddenHolograms.add(rid);
                    p.sendMessage(Txt.warn("§cHolograma ocultado."));
                }
                gui.openInfoGui(p, rid);
            }

            // ── Slot 30: Ver radio ─────────────────────
            case 30 -> {
                boolean has      = plugin.getPreviewManager().hasPreview(p.getUniqueId());
                boolean isRegion = plugin.getPreviewManager()
                        .getMode(p.getUniqueId()) == PreviewManager.PreviewMode.REGION;
                if (has && isRegion) {
                    plugin.getPreviewManager().stopPreview(p.getUniqueId());
                    sToggleOff(p);
                    p.sendMessage(Txt.warn("Vista de radio §cdesactivada§e."));
                } else {
                    plugin.getPreviewManager().startRegionPreview(p, rid);
                    sToggleOn(p);
                    p.sendMessage(Txt.ok("Vista de radio §aactivada§a."
                            + " §8(Agáchate o clic derecho al aire para desactivar)"));
                }
                gui.openInfoGui(p, rid);
            }

            // ── Slot 31: Teletransportar ───────────────
            case 31 -> {
                RegionStore.RegionRecord r = plugin.getRegionStore().getRecord(rid);
                if (r != null && r.blockLoc != null) {
                    p.closeInventory();
                    stopParticleAnimation(p.getUniqueId());
                    sTeleport(p);
                    p.teleport(r.blockLoc.clone().add(0.5, 1, 0.5));
                    p.sendMessage(Txt.ok("§a✔ Teletransportado a §e"
                            + plugin.getRegionStore().getDisplayName(rid) + "§a."));
                } else {
                    sError(p);
                    p.sendMessage(Txt.err("Ubicación del bloque no encontrada."));
                }
            }

            // ── Slot 32: Ver miembros ──────────────────
            case 32 -> {
                sClick(p);
                menus.members.open(p, rid);
            }

            // ── Slot 33: Exportar región (solo admin) ──
            case SLOT_EXPORT -> {
                if (!p.hasPermission("ryx.admin")) {
                    sError(p);
                    p.sendMessage(Txt.err("Solo administradores."));
                    return;
                }
                sClick(p);
                RegionStore.RegionRecord r = plugin.getRegionStore().getRecord(rid);
                if (r == null) return;
                p.sendMessage(Txt.SEP);
                p.sendMessage("  §e§l📤 §fExportar Región:");
                p.sendMessage(Txt.SEP);
                p.sendMessage("§9ID: §f" + rid);
                p.sendMessage("§9Dueño: §f" + r.ownerName + " §8(" + r.ownerUUID + "§8)");
                p.sendMessage("§9Mundo: §f" + r.worldName);
                p.sendMessage("§9Ubicación: §f" + r.blockLoc.getBlockX() + ", "
                        + r.blockLoc.getBlockY() + ", " + r.blockLoc.getBlockZ());
                p.sendMessage("§9Nivel: §f" + plugin.getUpgradeManager().getLevel(rid));
                p.sendMessage("§9DTR: §f" + plugin.getDtrManager().get(rid).formatted());
                p.sendMessage(Txt.SEP);
            }

            // ── Slot 37: Configuración (solo dueño) ───
            case 37 -> {
                if (h.getType() == GuiManager.GuiType.PROTECTION_INFO_OWNER) {
                    sClick(p);
                    menus.settings.open(p, rid);
                } else {
                    sError(p);
                    p.sendMessage(Txt.warn("Solo el §edueño §epuede acceder a la configuración."));
                }
            }

            // ── Slot 40: Mejoras (solo dueño) ─────────
            case 40 -> {
                if (h.getType() == GuiManager.GuiType.PROTECTION_INFO_OWNER) {
                    sClick(p);
                    menus.upgrade.open(p, rid);
                } else {
                    sClose(p);
                    p.closeInventory();
                }
            }

            // ── Slot 46: Volver ────────────────────────
            case 46 -> {
                sBack(p);
                stopParticleAnimation(p.getUniqueId());
            }

            // ── Slot 49: Cerrar ────────────────────────
            case 49 -> {
                sClose(p);
                stopParticleAnimation(p.getUniqueId());
                p.closeInventory();
            }
        }
    }

    protected ItemStack styledItem(Material mat, String name, String... lore) {
        return ItemBuilder.of(mat)
                .name(com.ryxmc.protection.util.Color.of(name))
                .lore(lore)
                .hideFlags()
                .build();
    }
}
