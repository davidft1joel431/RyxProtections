package com.ryxmc.protection.gui.menus;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.gui.GuiManager;
import com.ryxmc.protection.gui.modern.GuiStyle;
import com.ryxmc.protection.gui.modern.ItemBuilder;
import com.ryxmc.protection.manager.RegionStore;
import com.ryxmc.protection.util.Txt;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * SettingsMenu v4.1 — Menú de configuración de región con más funciones.
 *
 * Mejoras v4.1:
 *  • GuiStyle.SETTINGS — tema naranja/dorado unificado
 *  • ItemBuilder — ítems con brillo, bordes, divisores
 *  • Más opciones: PvP, Lock, Mobs, MOTD, Home, Miembros, Lista negra
 *  • Estado visual claro (activado/desactivado)
 *  • Confirmación para acciones peligrosas (eliminar región)
 *
 * Slots:
 *  19 → Agregar miembro
 *  20 → Transferir propiedad
 *  21 → Remover miembro
 *  22 → Ver lista negra
 *  23 → Lista negra: añadir
 *  25 → Renombrar región
 *  28 → Toggle PvP
 *  29 → Toggle Lock
 *  30 → Lista negra: quitar
 *  31 → Ver miembros
 *  32 → Establecer Home
 *  33 → MOTD/Descripción
 *  34 → Eliminar región (confirmar)
 *  41 → Toggle Mobs
 *  46 → Volver a Info
 *  49 → Cerrar
 */
public class SettingsMenu extends BaseMenu {

    public SettingsMenu(RyxProtection plugin, GuiManager gui, Menus menus) {
        super(plugin, gui, menus);
    }

    public void open(Player p, String regionId) {
        gui.openSettingsGui(p, regionId);
    }

    public void onClick(Player p, GuiManager.RyxHolder h, int slot) {
        String rid = h.getRegionId();

        switch (slot) {
            // ── 19: Agregar miembro ──────────────────────────────────────
            case 19 -> {
                if (!plugin.getConfig().getBoolean("protection.allow_members", true)
                        && !p.hasPermission("ryx.admin")) {
                    sError(p);
                    p.sendMessage(Txt.err("Miembros desactivado por admin."));
                    return;
                }
                sound(p, Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                askChat(p, rid, GuiManager.ChatInputType.ADD_MEMBER,
                        "§bAgregar miembro §8— escribe el nombre del jugador (§fcancelar §8para abortar)");
            }

            // ── 20: Transferir propiedad ─────────────────────────────────
            case 20 -> {
                if (!plugin.getConfig().getBoolean("region.allow_transfer", true)
                        && !p.hasPermission("ryx.admin")) {
                    sError(p);
                    p.sendMessage(Txt.err("Transferencia desactivada por admin."));
                    return;
                }
                sound(p, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.0f);
                askChat(p, rid, GuiManager.ChatInputType.TRANSFER_OWNERSHIP,
                        "§6Transferir §8— escribe el nuevo dueño. §cIRREVERSIBLE (§fcancelar §8para abortar)");
            }

            // ── 21: Remover miembro ──────────────────────────────────────
            case 21 -> {
                if (!plugin.getConfig().getBoolean("protection.allow_members", true)
                        && !p.hasPermission("ryx.admin")) {
                    sError(p);
                    p.sendMessage(Txt.err("Miembros desactivado por admin."));
                    return;
                }
                sound(p, Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.8f);
                askChat(p, rid, GuiManager.ChatInputType.REMOVE_MEMBER,
                        "§cRemover miembro §8— escribe el nombre (§fcancelar §8para abortar)");
            }

            // ── 22: Ver lista negra ─────────────────────────────────────
            case 22 -> {
                sClick(p);
                var blacklist = plugin.getRegionStoreExt().getBlacklist(rid);
                p.sendMessage(Txt.SEP);
                p.sendMessage("  §4§l☠ §fLista Negra:");
                p.sendMessage(Txt.SEP);
                if (blacklist.isEmpty()) {
                    p.sendMessage("§7  §o(Lista vacía)");
                } else {
                    int idx = 1;
                    for (java.util.UUID uuid : blacklist) {
                        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                        String n = op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
                        p.sendMessage("§c" + idx++ + ". §f" + n
                                + (op.isOnline() ? " §a● Online" : " §7● Offline"));
                    }
                }
                p.sendMessage(Txt.SEP);
                gui.openSettingsGui(p, rid);
            }

            // ── 23: Lista negra: añadir ─────────────────────────────────
            case 23 -> {
                if (!plugin.getConfig().getBoolean("protection.allow_blacklist", true)
                        && !p.hasPermission("ryx.admin")) {
                    sError(p);
                    p.sendMessage(Txt.err("Lista negra desactivada por admin."));
                    return;
                }
                sound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
                askChat(p, rid, GuiManager.ChatInputType.BLACKLIST_ADD,
                        "§4Lista negra (añadir) §8— escribe el jugador a bloquear (§fcancelar §8para abortar)");
            }

            // ── 25: Renombrar ───────────────────────────────────────────
            case 25 -> {
                sound(p, Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.4f);
                String current = plugin.getRegionStore().getDisplayName(rid);
                askChat(p, rid, GuiManager.ChatInputType.RENAME,
                        "§eRenombrar región §8— escribe el nuevo nombre (§fActual: " + current + "§8)"
                                + " (§fnone §8= quitar) (§fcancelar §8para abortar)");
            }

            // ── 28: Toggle PvP ──────────────────────────────────────────
            case 28 -> {
                boolean globalPvp = plugin.getConfig().getBoolean("protection.pvp_enabled", true);
                if (!globalPvp) {
                    sError(p);
                    p.sendMessage(Txt.err("PvP desactivado globalmente por admin."));
                    return;
                }
                boolean current = plugin.getRegionStore().isPvPEnabled(rid);
                plugin.getRegionStore().setPvP(rid, !current);
                if (!current) {
                    sound(p, Sound.ENTITY_BLAZE_SHOOT, 0.6f, 1.2f);
                    p.sendMessage(Txt.ok("§c⚔ PvP §aactivado en tu región."));
                } else {
                    sound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.5f);
                    p.sendMessage(Txt.ok("§a☮ PvP §adesactivado en tu región."));
                }
                gui.openSettingsGui(p, rid);
            }

            // ── 29: Toggle Lock ─────────────────────────────────────────
            case 29 -> {
                if (!plugin.getConfig().getBoolean("region.allow_lock", true)
                        && !p.hasPermission("ryx.admin")) {
                    sError(p);
                    p.sendMessage(Txt.err("Lock desactivado por admin."));
                    return;
                }
                boolean locked = plugin.getRegionStoreExt().isLocked(rid);
                plugin.getRegionStoreExt().setLocked(rid, !locked);
                if (!locked) {
                    sound(p, Sound.BLOCK_IRON_DOOR_CLOSE, 0.7f, 1.1f);
                    p.sendMessage(Txt.ok("§c🔒 Región bloqueada. Visitantes no pueden entrar."));
                } else {
                    sound(p, Sound.BLOCK_IRON_DOOR_OPEN, 0.7f, 1.2f);
                    p.sendMessage(Txt.ok("§a🔓 Región desbloqueada. Visitantes pueden entrar."));
                }
                gui.openSettingsGui(p, rid);
            }

            // ── 30: Lista negra: quitar ─────────────────────────────────
            case 30 -> {
                if (!plugin.getConfig().getBoolean("protection.allow_blacklist", true)
                        && !p.hasPermission("ryx.admin")) {
                    sError(p);
                    p.sendMessage(Txt.err("Lista negra desactivada por admin."));
                    return;
                }
                sound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
                askChat(p, rid, GuiManager.ChatInputType.BLACKLIST_REMOVE,
                        "§2Lista negra (quitar) §8— escribe el jugador a desbloquear (§fcancelar §8para abortar)");
            }

            // ── 31: Ver miembros ────────────────────────────────────────
            case 31 -> {
                sClick(p);
                menus.members.open(p, rid);
            }

            // ── 32: Establecer Home ─────────────────────────────────────
            case 32 -> {
                RegionStore.RegionRecord r = plugin.getRegionStore().getRecord(rid);
                if (r == null) { sError(p); return; }
                boolean inRegion = plugin.getRegionStore().getAt(p.getLocation()) != null
                        && plugin.getRegionStore().getAt(p.getLocation()).regionId.equals(rid);
                if (!inRegion) {
                    sError(p);
                    p.sendMessage(Txt.err("Debes estar dentro de tu región para establecer el home."));
                    return;
                }
                plugin.getRegionStore().setHome(rid, p.getLocation());
                sSuccess(p);
                p.sendMessage(Txt.ok("§a✔ Home actualizado a §f"
                        + p.getLocation().getBlockX() + "§a, §f"
                        + p.getLocation().getBlockY() + "§a, §f"
                        + p.getLocation().getBlockZ() + "§a."));
                gui.openSettingsGui(p, rid);
            }

            // ── 33: MOTD/Descripción ────────────────────────────────────
            case 33 -> {
                if (!plugin.getConfig().getBoolean("region.allow_motd", true)
                        && !p.hasPermission("ryx.admin")) {
                    sError(p);
                    p.sendMessage(Txt.err("MOTD desactivado por admin."));
                    return;
                }
                sound(p, Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.6f);
                String motd = plugin.getRegionStoreExt().getMotd(rid);
                askChat(p, rid, GuiManager.ChatInputType.SET_MOTD,
                        "§eDescripción §8— escribe el mensaje de bienvenida (§fActual: "
                                + (motd != null ? motd : "ninguna") + "§8)"
                                + " (§fnone §8= quitar | §fcancelar §8para abortar)");
            }

            // ── 34: Eliminar región ─────────────────────────────────────
            case 34 -> {
                if (!plugin.getConfig().getBoolean("protection.allow_unclaim", true)
                        && !p.hasPermission("ryx.admin")) {
                    sError(p);
                    p.sendMessage(Txt.err("Unclaim desactivado por admin."));
                    return;
                }
                sDanger(p);
                menus.confirm.open(p, rid);
            }

            // ── 41: Toggle Mobs ─────────────────────────────────────────
            case 41 -> {
                if (!plugin.getConfig().getBoolean("region.allow_mob_toggle", true)
                        && !p.hasPermission("ryx.admin")) {
                    sError(p);
                    p.sendMessage(Txt.err("Mobs desactivado por admin."));
                    return;
                }
                boolean mobs = plugin.getRegionStoreExt().isMobSpawningEnabled(rid);
                plugin.getRegionStoreExt().setMobSpawning(rid, !mobs);
                if (!mobs) {
                    sound(p, Sound.ENTITY_ZOMBIE_AMBIENT, 0.5f, 1.3f);
                    p.sendMessage(Txt.ok("§a🐾 Mobs activados en tu región."));
                } else {
                    sound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.7f);
                    p.sendMessage(Txt.ok("§c🐾 Mobs desactivados en tu región."));
                }
                gui.openSettingsGui(p, rid);
            }

            // ── 46: Volver a Info ───────────────────────────────────────
            case 46 -> {
                sBack(p);
                menus.info.open(p, rid);
            }

            // ── 49: Cerrar ──────────────────────────────────────────────
            case 49 -> {
                sClose(p);
                p.closeInventory();
            }
        }
    }

    // ─────────────────────────────────────────────────
    //  Handlers de Chat Input
    // ─────────────────────────────────────────────────

    public void chatAddMember(Player p, String rid, String name) {
        RegionStore.RegionRecord r = plugin.getRegionStore().getRecord(rid);
        if (r == null) return;
        World world = Bukkit.getWorld(r.worldName);
        OfflinePlayer t = Bukkit.getOfflinePlayer(name);
        boolean ok = world != null && plugin.getRegionStore().addMember(rid, t.getUniqueId(), world);
        if (ok) {
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            p.sendMessage(Txt.ok("§b" + name + "§a añadido como miembro."));
            Player online = Bukkit.getPlayer(t.getUniqueId());
            if (online != null)
                online.sendMessage(Txt.ok("§b¡Has sido añadido a la región §e"
                        + plugin.getRegionStore().getDisplayName(rid) + "§a!"));
        } else {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            p.sendMessage(Txt.err("No se pudo añadir §f" + name + "§c."));
        }
        gui.openSettingsGui(p, rid);
    }

    public void chatRemoveMember(Player p, String rid, String name) {
        RegionStore.RegionRecord r = plugin.getRegionStore().getRecord(rid);
        if (r == null) return;
        World world = Bukkit.getWorld(r.worldName);
        OfflinePlayer t = Bukkit.getOfflinePlayer(name);
        boolean ok = world != null && plugin.getRegionStore().removeMember(rid, t.getUniqueId(), world);
        if (ok) {
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.8f);
            p.sendMessage(Txt.ok("§c" + name + "§a removido de la región."));
            Player online = Bukkit.getPlayer(t.getUniqueId());
            if (online != null)
                online.sendMessage(Txt.warn("§cHas sido removido de la región §e"
                        + plugin.getRegionStore().getDisplayName(rid) + "§e."));
        } else {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            p.sendMessage(Txt.err("No se pudo remover §f" + name + "§c."));
        }
        gui.openSettingsGui(p, rid);
    }

    public void chatRename(Player p, String rid, String input) {
        String newName = input.equalsIgnoreCase("none") ? null : input.trim();
        if (newName != null && newName.length() > 64) {
            p.sendMessage(Txt.err("Nombre demasiado largo (máx 64 caracteres)."));
            gui.openSettingsGui(p, rid); return;
        }
        plugin.getRegionStore().setCustomName(rid, newName);
        plugin.getHologramManager().updateRegionHologram(rid);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
        p.sendMessage(Txt.ok("Región renombrada a §e"
                + (newName == null ? "§8(nombre por defecto)" : newName) + "§a."));
        gui.openSettingsGui(p, rid);
    }

    public void chatBlacklistAdd(Player p, String rid, String name) {
        OfflinePlayer t = Bukkit.getOfflinePlayer(name);
        plugin.getRegionStoreExt().addBlacklist(rid, t.getUniqueId());
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
        p.sendMessage(Txt.ok("§c" + name + "§a añadido a la lista negra."));
        gui.openSettingsGui(p, rid);
    }

    public void chatBlacklistRemove(Player p, String rid, String name) {
        OfflinePlayer t = Bukkit.getOfflinePlayer(name);
        plugin.getRegionStoreExt().removeBlacklist(rid, t.getUniqueId());
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
        p.sendMessage(Txt.ok("§e" + name + "§a quitado de la lista negra."));
        gui.openSettingsGui(p, rid);
    }

    public void chatSetMotd(Player p, String rid, String input) {
        String motd = input.equalsIgnoreCase("none") ? null : input.trim();
        if (motd != null && motd.length() > 64) {
            p.sendMessage(Txt.err("Descripción demasiado larga (máx 64)."));
            gui.openSettingsGui(p, rid); return;
        }
        plugin.getRegionStoreExt().setMotd(rid, motd);
        plugin.getHologramManager().updateRegionHologram(rid);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
        p.sendMessage(Txt.ok("Descripción actualizada a §f"
                + (motd == null ? "§8(ninguna)" : "\"" + motd + "\"") + "§a."));
        gui.openSettingsGui(p, rid);
    }

    public void chatTransferOwnership(Player p, String rid, String name) {
        OfflinePlayer t = Bukkit.getOfflinePlayer(name);
        if (t.getName() == null) {
            sError(p);
            p.sendMessage(Txt.err("El jugador §f" + name + "§c nunca se conectó."));
            gui.openSettingsGui(p, rid); return;
        }
        boolean ok = plugin.getRegionStoreExt().transferOwnership(rid, t.getUniqueId(), p);
        if (ok) {
            sound(p, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            p.sendMessage(Txt.ok("Transferencia completada. §f" + t.getName()
                    + "§a es el nuevo dueño de §e" + plugin.getRegionStore().getDisplayName(rid) + "§a."));
            Player online = Bukkit.getPlayer(t.getUniqueId());
            if (online != null)
                online.sendMessage(Txt.ok("§6§l⬛ §f" + p.getName() + "§a te transfirió la región §e"
                        + plugin.getRegionStore().getDisplayName(rid) + "§a."));
            plugin.getHologramManager().updateRegionHologram(rid);
            p.closeInventory();
        } else {
            sError(p);
            p.sendMessage(Txt.err("No se pudo transferir. ¿El jugador ya tiene una región?"));
            gui.openSettingsGui(p, rid);
        }
    }

    private void askChat(Player p, String regionId, GuiManager.ChatInputType type, String prompt) {
        p.closeInventory();
        p.sendMessage(Txt.SEP);
        p.sendMessage(prompt);
        p.sendMessage(Txt.SEP);
        gui.setChatInput(p.getUniqueId(), new GuiManager.ChatInput(type, regionId));
    }
}
