package com.ryxmc.protection.gui.menus;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.energy.EnergyManager;
import com.ryxmc.protection.gui.GuiManager;
import com.ryxmc.protection.util.Txt;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * EnergyMenu v2.2
 *
 * Slots existentes (sin cambios):
 *  19 → Gema Pequeña
 *  21 → Gema Mediana
 *  23 → Gema Grande
 *  25 → Gema Divina
 *  31 → Info de cómo conseguir gemas
 *  46 → Volver
 *  49 → Cerrar
 *
 * Slot NUEVO (v2.2):
 *  35 → ᴄᴏᴍᴘʀᴀʀ +1 ᴅᴛʀ ᴍáxɪᴍᴏ [NUEVO]
 *       Coste: dtr.upgrade_cost PP (default 10)
 *       Máximo: 4 compras por región
 *       Se pierde si la región es eliminada
 */
public class EnergyMenu extends BaseMenu {

    public EnergyMenu(RyxProtection plugin, GuiManager gui, Menus menus) {
        super(plugin, gui, menus);
    }

    public void open(Player p, String regionId) {
        gui.openEnergyGui(p, regionId);
    }

    public void onClick(Player p, GuiManager.RyxHolder h, int slot) {
        String rid = h.getRegionId();

        // ── Slots de gemas ────────────────────────────
        EnergyManager.GemType type = switch (slot) {
            case 19 -> EnergyManager.GemType.SMALL;
            case 21 -> EnergyManager.GemType.MEDIUM;
            case 23 -> EnergyManager.GemType.LARGE;
            case 25 -> EnergyManager.GemType.DIVINE;
            default -> null;
        };

        if (type != null) {
            int gemSlot = plugin.getEnergyManager().findGemSlot(p, type);
            if (gemSlot < 0) {
                sError(p);
                p.sendMessage(Txt.err("No tienes §f" + type.getDisplayName()
                        + "§c en tu inventario."));
                return;
            }
            boolean applied = plugin.getEnergyManager().applyGem(p, rid, type, gemSlot);
            if (applied) {
                switch (type) {
                    case DIVINE -> {
                        sLevelUp(p);
                        sound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
                        p.sendMessage(Txt.ok("§5§l✦ §dɢᴇᴍᴀ ᴅɪᴠɪɴᴀ §ausada. ᴅᴛʀ restaurado al máximo."));
                    }
                    case LARGE -> {
                        sound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
                        p.sendMessage(Txt.ok("§6§l✦ §6ɢᴇᴍᴀ ɢʀᴀɴᴅᴇ §ausada correctamente."));
                    }
                    case MEDIUM -> {
                        sound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                        p.sendMessage(Txt.ok("§b§l✦ §bɢᴇᴍᴀ ᴍᴇᴅɪᴀɴᴀ §ausada correctamente."));
                    }
                    case SMALL -> {
                        sound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.0f);
                        p.sendMessage(Txt.ok("§7§l✦ §fɢᴇᴍᴀ ᴘᴇǫᴜᴇñᴀ §ausada correctamente."));
                    }
                }
                gui.openEnergyGui(p, rid);
            }
            return;
        }

        switch (slot) {

            // ── [NUEVO] Slot 35: Comprar +1 DTR máximo ───
            case 35 -> {
                // Verificar que el jugador es dueño o admin
                var r = plugin.getRegionStore().getRecord(rid);
                if (r == null) { sError(p); return; }
                if (!r.ownerUUID.equals(p.getUniqueId()) && !p.hasPermission("ryx.admin")) {
                    sError(p);
                    p.sendMessage(Txt.err("Solo el §fdueño §cpuede comprar mejoras de ᴅᴛʀ."));
                    return;
                }

                // Intentar la compra
                String err = plugin.getDtrUpgradeManager().purchase(rid, p.getUniqueId());
                if (err == null) {
                    // Éxito
                    sLevelUp(p);
                    sound(p, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.6f);
                    int extras = plugin.getDtrUpgradeManager().getExtras(rid);
                    double newMax = plugin.getDtrManager().get(rid) != null
                            ? plugin.getDtrManager().get(rid).getMaxDtr() : 0;
                    p.sendMessage(Txt.ok("§c§l+1 ᴅᴛʀ ᴍáxɪᴍᴏ §acomprado."
                            + " §8(§e" + extras + "§8/§e"
                            + plugin.getDtrUpgradeManager().MAX_EXTRAS
                            + "§8 mejoras)"
                            + " §8— §9ɴᴜᴇᴠᴏ ᴍáx: §e" + String.format("%.1f", newMax)));
                    // Actualizar holograma con el nuevo DTR máximo
                    if (plugin.getHologramManager() != null)
                        plugin.getHologramManager().updateRegionHologram(rid);
                    // Reabrir para refrescar el ítem visual
                    gui.openEnergyGui(p, rid);
                } else {
                    sError(p);
                    p.sendMessage(Txt.err(err));
                }
            }

            // ── Navegación ────────────────────────────────
            case 46 -> { sBack(p);  menus.info.open(p, rid); }
            case 49 -> { sClose(p); p.closeInventory(); }
        }
    }
}
