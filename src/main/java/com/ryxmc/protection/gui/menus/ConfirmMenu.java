package com.ryxmc.protection.gui.menus;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.config.ProtectionBlock;
import com.ryxmc.protection.gui.GuiManager;
import com.ryxmc.protection.util.Txt;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * ConfirmMenu v2.3
 *
 * RegionStore.removeRegion() ya maneja internamente (en orden correcto):
 *   1. Drop del bloque físico
 *   2. Quitar hologram
 *   3. saveAndUnregister(DTR) → guarda estado en PlayerDtrRegistry
 *   4. Limpiar DtrUpgradeManager y RegionStoreExtension
 *   5. regions.remove()
 *
 * ConfirmMenu solo necesita:
 *   - Llamar removeRegion()
 *   - Limpiar PreviewManager
 *   - Mostrar mensaje
 *
 * NO llamar dtrManager.unregister() ni hologramManager.removeHologram()
 * desde aquí — causaría doble llamada y rompería el orden correcto.
 */
public class ConfirmMenu extends BaseMenu {

    public ConfirmMenu(RyxProtection plugin, GuiManager gui, Menus menus) {
        super(plugin, gui, menus);
    }

    public void open(Player p, String regionId) {
        gui.openConfirmRemoveGui(p, regionId);
    }

    public void onClick(Player p, GuiManager.RyxHolder h, int slot) {
        String rid = h.getRegionId();

        switch (slot) {

            // ── Slot 11: ꜱÍ, ᴇʟɪᴍɪɴᴀʀ ─────────────────────────────────────
            case 11 -> {
                sDanger(p);
                sound(p, Sound.ENTITY_WITHER_SPAWN, 0.4f, 1.5f);

                int    lvl        = plugin.getUpgradeManager().getLevel(rid);
                String lvlName    = ProtectionBlock.getName(lvl);
                String regionName = plugin.getRegionStore().getDisplayName(rid);

                // removeRegion() hace TODO en el orden correcto internamente:
                //  drop bloque → quitar hologram → saveAndUnregister(DTR)
                //  → limpiar upgrades/ext → regions.remove()
                boolean ok = plugin.getRegionStore().removeRegion(rid, p);

                if (ok) {
                    // Solo limpiamos lo que RegionStore NO maneja:
                    plugin.getPreviewManager().stopPreview(p.getUniqueId());

                    p.closeInventory();
                    p.sendMessage(Txt.ok("§c✖ §fʀᴇɢɪóɴ §e" + regionName
                            + "§a eliminada. §fʙʟᴏǫᴜᴇ §e" + lvlName
                            + "§a dropeado donde estaba."));
                } else {
                    sError(p);
                    p.sendMessage(Txt.err("No se pudo eliminar la §fʀᴇɢɪóɴ§c. Intenta de nuevo."));
                    menus.info.open(p, rid);
                }
            }

            // ── Slot 15: ᴄᴀɴᴄᴇʟᴀʀ ──────────────────────────────────────────
            case 15 -> {
                sBack(p);
                p.sendMessage(Txt.info("§7ᴇʟɪᴍɪɴᴀᴄɪóɴ cancelada. Tu §eʀᴇɢɪóɴ §7sigue intacta."));
                menus.info.open(p, rid);
            }
        }
    }
}
