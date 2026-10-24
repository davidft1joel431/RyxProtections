package com.ryxmc.protection.gui.menus;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.gui.GuiManager;
import com.ryxmc.protection.util.Txt;
import org.bukkit.entity.Player;

/**
 * ProtectionOptionsMenu v2.0
 *
 * 17 toggles globales — todos conectados a config.yml en tiempo real.
 *
 *  FILA 2 — Combate:  10=PvP global, 11=toggle PvP x región, 12=explosiones,
 *                     14=preview, 16=/ps home
 *  FILA 3 — Región:   19=unclaim, 21=lock, 22=miembros, 23=motd,
 *                     24=blacklist, 25=mobs
 *  FILA 4 — Economía: 28=transfer, 29=upgrade, 30=broadcast raid,
 *                     31=tienda, 32=broadcast restored, 33=energía
 *  FILA 5 — Sistemas: 37=fábricas, 38=compresor, 40=info DTR, 42=debug
 */
public class ProtectionOptionsMenu extends BaseMenu {

    public ProtectionOptionsMenu(RyxProtection plugin, GuiManager gui, Menus menus) {
        super(plugin, gui, menus);
    }

    public void open(Player p) { gui.openProtectionOptionsGui(p); }

    public void onClick(Player p, GuiManager.RyxHolder h, int slot) {
        switch (slot) {

            // ══ FILA 2 — Combate y física ══════════════════════
            case 10 -> toggle(p, "protection.pvp_enabled", true,
                    "§c⚔ ᴘᴠᴘ ɢʟᴏʙᴀʟ §aactivado§a. Los jugadores pueden atacarse en regiones.",
                    "§c⚔ ᴘᴠᴘ ɢʟᴏʙᴀʟ §cdesactivado§a. Ninguna región tendrá PvP.");
            case 11 -> toggle(p, "protection.allow_pvp_toggle", true,
                    "§a⚔ §fᴛᴏɢɢʟᴇ ᴘᴠᴘ §ahabilitado§a. Dueños pueden configurar PvP en su región.",
                    "§c⚔ §fᴛᴏɢɢʟᴇ ᴘᴠᴘ §cdeshabilitado§a. Solo admin puede cambiar el PvP.");
            case 12 -> toggle(p, "protection.block_explosions", true,
                    "§6💥 Explosiones §cBLOQUEADAS §aen zonas protegidas.",
                    "§6💥 Explosiones §apermitidas §aen zonas protegidas.");
            case 14 -> toggle(p, "protection.preview_on_place", true,
                    "§b👁 Preview de radio §aactivado§a.",
                    "§b👁 Preview de radio §cdesactivado§a.");
            case 16 -> toggle(p, "protection.allow_home", true,
                    "§a⌂ §f/ps home §ahabilitado§a.",
                    "§a⌂ §f/ps home §cdeshabilitado§a.");

            // ══ FILA 3 — Gestión de región ═════════════════════
            case 19 -> toggle(p, "protection.allow_unclaim", true,
                    "§c✖ §f/ps unclaim §ahabilitado§a. Dueños pueden eliminar su región.",
                    "§c✖ §f/ps unclaim §cdeshabilitado§a. Solo admins pueden eliminar regiones.");
            case 21 -> toggle(p, "region.allow_lock", true,
                    "§c🔒 Lock de visitantes §ahabilitado§a.",
                    "§c🔒 Lock de visitantes §cdeshabilitado§a.");
            case 22 -> toggle(p, "protection.allow_members", true,
                    "§b👥 Gestión de miembros §ahabilitada§a.",
                    "§b👥 Gestión de miembros §cdeshabilitada§a.");
            case 23 -> toggle(p, "region.allow_motd", true,
                    "§e✉ MOTD §ahabilitado§a.",
                    "§e✉ MOTD §cdeshabilitado§a.");
            case 24 -> toggle(p, "protection.allow_blacklist", true,
                    "§4☠ Lista negra §ahabilitada§a.",
                    "§4☠ Lista negra §cdeshabilitada§a.");
            case 25 -> toggle(p, "region.allow_mob_toggle", true,
                    "§a🐾 Toggle mobs §ahabilitado§a.",
                    "§a🐾 Toggle mobs §cdeshabilitado§a.");

            // ══ FILA 4 — Economía y progresión ═════════════════
            case 28 -> toggle(p, "region.allow_transfer", true,
                    "§6⇄ Transferencia §ahabilitada§a.",
                    "§6⇄ Transferencia §cdeshabilitada§a.");
            case 29 -> toggle(p, "protection.allow_upgrade", true,
                    "§b⬆ Mejoras de región §ahabilitadas§a.",
                    "§b⬆ Mejoras de región §cdeshabilitadas§a.");
            case 30 -> toggle(p, "dtr.broadcast_raidable", true,
                    "§c📢 Anuncio RAIDEABLE §aactivado§a.",
                    "§c📢 Anuncio RAIDEABLE §cdesactivado§a.");
            case 31 -> toggle(p, "protection.allow_shop", true,
                    "§6🛒 Tienda §ahabilitada§a.",
                    "§6🛒 Tienda §cdeshabilitada§a.");
            case 32 -> toggle(p, "dtr.broadcast_restored", true,
                    "§a📢 Anuncio RESTAURADA §aactivado§a.",
                    "§a📢 Anuncio RESTAURADA §cdesactivado§a.");
            case 33 -> toggle(p, "protection.allow_energy", true,
                    "§5💎 Sistema de energía/gemas §ahabilitado§a.",
                    "§5💎 Sistema de energía/gemas §cdeshabilitado§a.");

            // ══ FILA 5 — Sistemas ══════════════════════════════
            case 37 -> toggle(p, "protection.allow_factory", true,
                    "§6⚙ Sistema de fábricas §ahabilitado§a.",
                    "§6⚙ Sistema de fábricas §cdeshabilitado§a.");
            case 38 -> toggle(p, "protection.allow_compressor", true,
                    "§5⚗ Compresor de gemas §ahabilitado§a.",
                    "§5⚗ Compresor de gemas §cdeshabilitado§a.");
            case 40 -> {
                // Solo lectura — info del costo de DTR extra
                sError(p);
                p.sendMessage(Txt.info("§7Edita §fdtr.upgrade_cost §7en §fconfig.yml §7para cambiar el costo."));
            }
            case 42 -> toggle(p, "plugin.debug", false,
                    "§c🔧 Modo debug §aactivado§a. Logs detallados en consola.",
                    "§7🔧 Modo debug §cdesactivado§a.");

            // ══ Navegación ═════════════════════════════════════
            case 46 -> { sBack(p); menus.admin.openMain(p); }
            case 49 -> { sClose(p); p.closeInventory(); }
        }
    }

    /**
     * Toggle genérico — invierte la config key, guarda y reabre el GUI.
     *
     * @param defaultVal valor por defecto si la key no está en config
     * @param msgOn      mensaje cuando nuevo estado es true (habilitado)
     * @param msgOff     mensaje cuando nuevo estado es false (deshabilitado)
     */
    private void toggle(Player p, String key, boolean defaultVal,
                        String msgOn, String msgOff) {
        boolean newVal = !plugin.getConfig().getBoolean(key, defaultVal);
        plugin.getConfig().set(key, newVal);
        plugin.saveConfig();
        if (newVal) { sToggleOn(p);  p.sendMessage(Txt.ok(msgOn)); }
        else        { sToggleOff(p); p.sendMessage(Txt.warn(msgOff)); }
        gui.openProtectionOptionsGui(p);
    }
}
