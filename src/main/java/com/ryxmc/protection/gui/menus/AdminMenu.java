package com.ryxmc.protection.gui.menus;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.gui.GuiManager;
import com.ryxmc.protection.gui.modern.GuiStyle;
import com.ryxmc.protection.gui.modern.ItemBuilder;
import com.ryxmc.protection.manager.RegionStore;
import com.ryxmc.protection.util.Txt;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * AdminMenu v4.1 — Panel de administración reescrito con animaciones y más funciones.
 *
 * Mejoras v4.1:
 *  • GuiStyle.ADMIN — tema rojo oscuro unificado
 *  • ItemBuilder — ítems con brillo, bordes, divisores
 *  • Botón de auto-actualización desde GitHub (slot 36)
 *  • Estadísticas en tiempo real con TPS, jugadores, regiones
 *  • Gestión completa: regiones, DTR, fábricas, economía, guerra
 *
 * Secciones (slots):
 *  10 → Listar regiones
 *  12 → Gestión DTR
 *  14 → Gestión PP
 *  16 → Estado fábricas
 *  19 → Dar bloque de protección
 *  20 → Dar fábrica
 *  21 → Forzar eliminar región
 *  22 → Estadísticas servidor
 *  24 → Teleportar a región
 *  25 → Recalcular DTR máximo
 *  28 → Resetear regiones raideables
 *  30 → Recargar plugin
 *  32 → Broadcast / anuncio
 *  34 → Opciones de protecciones globales
 *  36 → Auto-actualizar plugin (GitHub)
 *  44 → Guerra de clanes
 *  46 → Volver / Cerrar
 *  49 → Cerrar
 */
public class AdminMenu extends BaseMenu {

    public AdminMenu(RyxProtection plugin, GuiManager gui, Menus menus) {
        super(plugin, gui, menus);
    }

    public void openMain(Player p) { gui.openAdminGui(p); }

    public void onClickMain(Player p, GuiManager.RyxHolder h, int slot) {
        switch (slot) {

            // ═════════════════════════════════════════════════════════════════════════════
            //  SECCIÓN 1: REGIONES (slots 10, 12, 14, 16)
            // ══════════════════════════════════════════════════════════════════════════════

            case 10 -> { // Listar regiones
                sClick(p);
                gui.openAdminRegionsGui(p, 0);
            }

            case 12 -> { // Gestión DTR
                sClick(p);
                gui.openAdminDtrGui(p, 0);
            }

            case 14 -> { // Gestión PP
                sSuccess(p);
                p.sendMessage(Txt.SEP);
                p.sendMessage("  §6§l⭐ §fGestión de PP §8(Puntos de Protección)");
                p.sendMessage(Txt.SEP);
                p.sendMessage("§f/pp set §b<jugador> §e<cantidad>  §7→ Establecer PP exacto");
                p.sendMessage("§f/pp add §b<jugador> §e<cantidad>  §7→ Sumar PP");
                p.sendMessage("§f/pp info §b<jugador>              §7→ Ver PP de jugador");
                p.sendMessage("§f/pp top                          §7→ Top de PP");
                p.sendMessage(Txt.SEP);
            }

            case 16 -> { // Estado fábricas
                sound(p, Sound.BLOCK_FURNACE_FIRE_CRACKLE, 1f, 1f);
                int facs  = plugin.getFactoryManager().getAllFactories().size();
                int comps = plugin.getCompressorManager().getAll().size();
                int indus = plugin.getIndustrialFactoryManager().getAll().size();
                p.sendMessage(Txt.SEP);
                p.sendMessage("  §6§l⚙ §fEstado de Fábricas y Compresores");
                p.sendMessage(Txt.SEP);
                p.sendMessage("§9Fábricas energía:   §e" + facs);
                p.sendMessage("§9Compresores gemas:  §d" + comps);
                p.sendMessage("§9Fábricas indus:     §c" + indus);
                p.sendMessage(Txt.THIN);
                p.sendMessage("§f/factory give §b<jugador> §7→ Dar fábrica energía");
                p.sendMessage("§f/ps givecompressor §b<jugador> §7→ Dar compresor");
                p.sendMessage(Txt.SEP);
            }

            // ═══════════════════════════════════════════════════════════════════════════════
            //  SECCIÓN 2: ACCIONES RÁPIDAS ADMIN (slots 19-25)
            // ═══════════════════════════════════════════════════════════════════════════════

            case 19 -> { // Dar bloque de protección
                sClick(p);
                p.closeInventory();
                p.sendMessage(Txt.SEP);
                p.sendMessage("§b✦ §fDar bloque de protección");
                p.sendMessage("§7Escribe: §f<jugador> <nivel> §8(ej: §fSteve 3§8)");
                p.sendMessage("§8(§fcancelar §8para abortar)");
                p.sendMessage(Txt.SEP);
                gui.setChatInput(p.getUniqueId(),
                        new GuiManager.ChatInput(GuiManager.ChatInputType.ADMIN_GIVE_BLOCK, null));
            }

            case 20 -> { // Dar fábrica
                sClick(p);
                p.closeInventory();
                p.sendMessage(Txt.SEP);
                p.sendMessage("§6✦ §fDar fábrica de energía");
                p.sendMessage("§7Escribe el nombre del jugador.");
                p.sendMessage("§8(§fcancelar §8para abortar)");
                p.sendMessage(Txt.SEP);
                gui.setChatInput(p.getUniqueId(),
                        new GuiManager.ChatInput(GuiManager.ChatInputType.ADMIN_GIVE_FACTORY, null));
            }

            case 21 -> { // Forzar eliminar región
                sDanger(p);
                p.closeInventory();
                p.sendMessage(Txt.SEP);
                p.sendMessage("§4⚠ §fForzar eliminación de región");
                p.sendMessage("§7Escribe el ID de la región.");
                p.sendMessage("§8(§fcancelar §8para abortar)");
                p.sendMessage(Txt.SEP);
                gui.setChatInput(p.getUniqueId(),
                        new GuiManager.ChatInput(GuiManager.ChatInputType.ADMIN_FORCE_REMOVE_REGION, null));
            }

            case 22 -> { // Estadísticas servidor
                sSuccess(p);
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
                p.sendMessage("§9Jugadores online:  §f" + online + "§8/§f" + Bukkit.getMaxPlayers());
                p.sendMessage("§9Regiones totales:  §f" + regions + " §8(§c" + raidable + " raideables§8)");
                p.sendMessage("§9Fábricas activas:  §e" + facs);
                p.sendMessage("§9Compresores:       §d" + comps);
                p.sendMessage("§9TPS actual:        §f" + String.format("%.1f", tps[0]));
                p.sendMessage(Txt.SEP);
            }

            case 24 -> { // Teleportar a región
                sClick(p);
                p.closeInventory();
                p.sendMessage(Txt.SEP);
                p.sendMessage("§d✦ §fTeleportar a región");
                p.sendMessage("§7Escribe el ID o nombre de la región.");
                p.sendMessage("§8(§fcancelar §8para abortar)");
                p.sendMessage(Txt.SEP);
                gui.setChatInput(p.getUniqueId(),
                        new GuiManager.ChatInput(GuiManager.ChatInputType.ADMIN_TP_REGION, null));
            }

            case 25 -> { // Recalcular DTR máximo
                sDanger(p);
                int count = 0;
                for (RegionStore.RegionRecord r : plugin.getRegionStore().getAll()) {
                    plugin.getDtrManager().recalcMax(r.regionId);
                    count++;
                }
                sSuccess(p);
                p.sendMessage(Txt.ok("§a✔ DTR máximo recalculado para §f" + count + "§a regiones."));
            }

            // ═══════════════════════════════════════════════════════════════════════════════
            //  SECCIÓN 3: CONTROL DE GUERRA Y SISTEMA (slots 28-36)
            // ════════════════════════════════════════════════════════════════════════════════

            case 28 -> { // Resetear regiones raideables
                sound(p, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.3f, 1.5f);
                int count = 0;
                for (RegionStore.RegionRecord r : plugin.getRegionStore().getAll()) {
                    if (plugin.getDtrManager().isRaidable(r.regionId)) {
                        plugin.getDtrManager().reset(r.regionId);
                        count++;
                    }
                }
                sSuccess(p);
                p.sendMessage(Txt.ok("§a✔ §f" + count + "§a regiones raideables reseteadas al DTR máximo."));
            }

            case 30 -> { // Recargar plugin
                sound(p, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.3f, 2.0f);
                long start = System.currentTimeMillis();
                plugin.getHologramManager().reload();
                plugin.getDtrManager().reload();
                plugin.reloadConfig();
                long ms = System.currentTimeMillis() - start;
                sSuccess(p);
                p.sendMessage(Txt.ok("§a✔ Plugin recargado en §f" + ms + "ms§a."));
                Bukkit.getScheduler().runTaskLater(plugin, (Runnable) p::closeInventory, 20L);
            }

            case 32 -> { // Broadcast / anuncio
                sClick(p);
                p.closeInventory();
                p.sendMessage(Txt.SEP);
                p.sendMessage("§c✦ §fAnuncio global (broadcast)");
                p.sendMessage("§7Escribe el mensaje a enviar a todos los jugadores.");
                p.sendMessage("§8(§fcancelar §8para abortar)");
                p.sendMessage(Txt.SEP);
                gui.setChatInput(p.getUniqueId(),
                        new GuiManager.ChatInput(GuiManager.ChatInputType.ADMIN_BROADCAST, null));
            }

            case 34 -> { // Opciones de protecciones globales
                sClick(p);
                menus.protectionOptions.open(p);
            }

            case 36 -> { // Auto-actualizar plugin desde GitHub
                var updater = plugin.getGitHubUpdater();
                if (updater != null && updater.isUpdateAvailable()) {
                    sSuccess(p);
                    p.sendMessage(Txt.ok("§6⬇ §fDescargando actualización v" + updater.getLatestVersion() + "..."));
                    updater.performUpdate(p);
                } else {
                    sError(p);
                    p.sendMessage(Txt.err("No hay actualización disponible."));
                    p.sendMessage(Txt.warn("Usa: §f/ryx check §7para verificar."));
                }
            }

            // ═══════════════════════════════════════════════════════════════════════════════
            //  SECCIÓN 4: GUERRA DE CLANES (slot 44)
            // ════════════════════════════════════════════════════════════════════════════════

            case 44 -> { // Guerra de clanes
                sClick(p);
                menus.war.open(p);
            }

            // ════════════════════════════════════════════════════════════════════════════════
            //  NAVEGACIÓN (slots 46, 49)
            // ═════════════════════════════════════════════════════════════════════════════════

            case 46 -> { sBack(p); p.closeInventory(); }
            case 49 -> { sClose(p); p.closeInventory(); }
        }
    }

    // ─────────────────────────────────────────────────
    //  ADMIN REGIONES
    // ─────────────────────────────────────────────────

    public void onClickRegions(Player p, GuiManager.RyxHolder h, int slot, ClickType click) {
        if (slot == 45) { sBack(p); gui.openAdminRegionsGui(p, Math.max(0, h.getExtra()-1)); return; }
        if (slot == 53) { sClick(p); gui.openAdminRegionsGui(p, h.getExtra()+1); return; }
        if (slot == 49) { sBack(p); gui.openAdminGui(p); return; }

        Inventory inv = p.getOpenInventory().getTopInventory();
        ItemStack clicked = inv.getItem(slot);
        if (clicked == null || clicked.getItemMeta() == null) return;
        if (clicked.getType() == Material.BLACK_STAINED_GLASS_PANE
                || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        String itemName = com.ryxmc.protection.util.Color.strip(clicked.getItemMeta().getDisplayName());
        RegionStore.RegionRecord found = findRegion(itemName);
        if (found == null) return;

        if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
            // Shift+Click = teletransportar al bloque de la región
            if (found.blockLoc != null && found.blockLoc.getWorld() != null) {
                p.closeInventory();
                sTeleport(p);
                p.teleport(found.blockLoc.clone().add(0.5, 1, 0.5));
                p.sendMessage(Txt.ok("§a✔ Teletransportado a la región §e"
                        + plugin.getRegionStore().getDisplayName(found.regionId) + "§a."));
            } else {
                sError(p);
                p.sendMessage(Txt.err("La región no tiene ubicación de bloque registrada."));
            }
        } else if (click == ClickType.RIGHT) {
            // Click derecho = forzar eliminar
            sDanger(p);
            boolean ok = plugin.getRegionStore().removeRegion(found.regionId, p);
            if (ok) {
                plugin.getDtrManager().unregister(found.regionId);
                plugin.getUpgradeManager().removeRegion(found.regionId);
                plugin.getHologramManager().removeHologram(found.regionId);
                p.sendMessage(Txt.ok("§c✖ Región §f" + found.regionId + "§a eliminada."));
            } else {
                sError(p);
                p.sendMessage(Txt.err("No se pudo eliminar §f" + found.regionId + "§c."));
            }
            gui.openAdminRegionsGui(p, h.getExtra());
        } else {
            // Click izquierdo = abrir info
            sClick(p);
            gui.openInfoGui(p, found.regionId);
        }
    }

    // ─────────────────────────────────────────────────
    //  ADMIN DTR
    // ─────────────────────────────────────────────────

    public void onClickDtr(Player p, GuiManager.RyxHolder h, int slot, ClickType click) {
        if (slot == 45) { sBack(p); gui.openAdminDtrGui(p, Math.max(0, h.getExtra()-1)); return; }
        if (slot == 53) { sClick(p); gui.openAdminDtrGui(p, h.getExtra()+1); return; }
        if (slot == 49) { sBack(p); gui.openAdminGui(p); return; }

        Inventory inv = p.getOpenInventory().getTopInventory();
        ItemStack clicked = inv.getItem(slot);
        if (clicked == null || clicked.getItemMeta() == null) return;

        String itemName = com.ryxmc.protection.util.Color.strip(clicked.getItemMeta().getDisplayName());
        RegionStore.RegionRecord found = findRegion(itemName);
        if (found == null) return;
        String rid = found.regionId;

        if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
            // Shift+Click = forzar raideable
            sDanger(p);
            plugin.getDtrManager().set(rid, 0);
            String name = plugin.getRegionStore().getDisplayName(rid);
            p.sendMessage(Txt.ok("Región §e" + name + "§a forzada a §4§lRAIDEABLE§a."));
            Bukkit.broadcastMessage(Txt.err("§c§l[Admin] §fRegión §e" + name
                    + "§c forzada a §4§lRAIDEABLE §cpor §f" + p.getName() + "§c."));
            gui.openAdminDtrGui(p, h.getExtra());

        } else if (click == ClickType.RIGHT) {
            // Click derecho = reset al máximo
            sSuccess(p);
            plugin.getDtrManager().reset(rid);
            p.sendMessage(Txt.ok("§a✔ DTR de §e" + plugin.getRegionStore().getDisplayName(rid)
                    + "§a restablecido al máximo."));
            gui.openAdminDtrGui(p, h.getExtra());

        } else {
            // Click izquierdo = detalle
            sClick(p);
            var dtr = plugin.getDtrManager().get(rid);
            p.sendMessage(Txt.SEP);
            p.sendMessage("  §c§l⚡ §fDTR — §e" + plugin.getRegionStore().getDisplayName(rid));
            p.sendMessage(Txt.SEP);
            p.sendMessage("§9Dueño:     §f" + found.ownerName);
            p.sendMessage("§9Región ID: §7" + rid);
            p.sendMessage("§9DTR:       " + dtr.formatted()
                    + " §8/ §f" + String.format("%.1f", dtr.getMaxDtr()));
            p.sendMessage("§9Raideable: " + (dtr.isRaidable() ? "§4§lSÍ ⚠" : "§aNO ✔"));
            if (dtr.isInCooldown())
                p.sendMessage("§9Cooldown:  §f" + Txt.time(dtr.cooldownSecondsLeft()));
            p.sendMessage(Txt.SEP);
            p.sendMessage("§8§oClick der §7→ Reset | §8§oShift+Click §7→ Forzar raideable");
            p.sendMessage(Txt.SEP);
        }
    }

    // ─────────────────────────────────────────────────
    //  Handlers de Chat Admin (llamados desde GuiListener)
    // ─────────────────────────────────────────────────

    public void chatAdminGiveBlock(Player p, String input) {
        String[] parts = input.trim().split(" ");
        if (parts.length < 2) {
            sError(p);
            p.sendMessage(Txt.err("Uso: §f<jugador> <nivel> §8(ej: §fSteve 3§8)"));
            return;
        }
        Player target = Bukkit.getPlayer(parts[0]);
        if (target == null) {
            sError(p);
            p.sendMessage(Txt.err("Jugador §f" + parts[0] + "§c no encontrado u offline."));
            return;
        }
        int level;
        try { level = Integer.parseInt(parts[1]); }
        catch (NumberFormatException e) {
            sError(p);
            p.sendMessage(Txt.err("Nivel inválido. Usa un número del 1 al 7."));
            return;
        }
        level = Math.max(1, Math.min(7, level));
        target.getInventory().addItem(
                com.ryxmc.protection.config.ProtectionBlock.buildItem(plugin, level));
        sSuccess(p);
        p.sendMessage(Txt.ok("§a✔ Bloque Nv." + level + " dado a §f" + target.getName() + "§a."));
        target.sendMessage(Txt.ok("§a✔ Recibiste un §fBloque de Protección §8[Nv." + level + "]§a de §f" + p.getName()));
    }

    public void chatAdminGiveFactory(Player p, String name) {
        Player target = Bukkit.getPlayer(name.trim());
        if (target == null) {
            sError(p);
            p.sendMessage(Txt.err("Jugador §f" + name + "§c no encontrado u offline."));
            return;
        }
        target.getInventory().addItem(plugin.getFactoryManager().createFactoryItem());
        sSuccess(p);
        p.sendMessage(Txt.ok("§a✔ Fábrica de Energía dada a §f" + target.getName() + "§a."));
        target.sendMessage(Txt.ok("§a✔ Recibiste una §6§lFábrica de Energía §ade §f" + p.getName() + "§a."));
    }

    public void chatAdminForceRemoveRegion(Player p, String input) {
        String regionId = input.trim();
        if (plugin.getRegionStore().getRecord(regionId) == null) {
            sError(p);
            p.sendMessage(Txt.err("Región §f" + regionId + "§c no encontrada."));
            return;
        }
        boolean ok = plugin.getRegionStore().removeRegionForced(regionId);
        if (ok) {
            plugin.getDtrManager().unregister(regionId);
            plugin.getUpgradeManager().removeRegion(regionId);
            plugin.getHologramManager().removeHologram(regionId);
            sSuccess(p);
            p.sendMessage(Txt.ok("§c✖ Región §f" + regionId + "§a eliminada forzosamente."));
        } else {
            sError(p);
            p.sendMessage(Txt.err("No se pudo eliminar §f" + regionId + "§c."));
        }
        gui.openAdminGui(p);
    }

    public void chatAdminTpRegion(Player p, String input) {
        String query = input.trim().toLowerCase();
        RegionStore.RegionRecord found = null;
        for (RegionStore.RegionRecord r : plugin.getRegionStore().getAll()) {
            String dispName = com.ryxmc.protection.util.Color.strip(plugin.getRegionStore().getDisplayName(r.regionId)).toLowerCase();
            if (r.regionId.equalsIgnoreCase(query) || dispName.contains(query)) {
                found = r; break;
            }
        }
        if (found == null || found.blockLoc == null) {
            sError(p);
            p.sendMessage(Txt.err("Región §f" + input + "§c no encontrada."));
            return;
        }
        sTeleport(p);
        p.teleport(found.blockLoc.clone().add(0.5, 1, 0.5));
        p.sendMessage(Txt.ok("§a✔ Teletransportado a §e"
                + plugin.getRegionStore().getDisplayName(found.regionId) + "§a."));
    }

    public void chatAdminBroadcast(Player p, String message) {
        String formatted = "§6§l[Anuncio] §f" + message;
        Bukkit.broadcastMessage(formatted);
        sSuccess(p);
        p.sendMessage(Txt.ok("§a✔ Anuncio enviado a todos los jugadores."));
    }

    // ─────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────

    private RegionStore.RegionRecord findRegion(String itemName) {
        for (RegionStore.RegionRecord r : plugin.getRegionStore().getAll()) {
            String disp = com.ryxmc.protection.util.Color.strip(plugin.getRegionStore().getDisplayName(r.regionId));
            if (itemName.contains(disp) || itemName.contains(r.regionId)) return r;
        }
        return null;
    }

    protected ItemStack styledItem(Material mat, String name, String... lore) {
        return ItemBuilder.of(mat)
                .name(com.ryxmc.protection.util.Color.of(name))
                .lore(lore)
                .hideFlags()
                .build();
    }
}
