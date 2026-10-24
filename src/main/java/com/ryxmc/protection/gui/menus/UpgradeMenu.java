package com.ryxmc.protection.gui.menus;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.config.ProtectionBlock;
import com.ryxmc.protection.gui.GuiManager;
import com.ryxmc.protection.manager.RegionStore;
import com.ryxmc.protection.upgrade.UpgradeManager;
import com.ryxmc.protection.util.Txt;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * UpgradeMenu v2.1 — versalitas en mensajes de chat.
 * Lógica original intacta.
 */
public class UpgradeMenu extends BaseMenu {

    private static final int[] LEVEL_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    public UpgradeMenu(RyxProtection plugin, GuiManager gui, Menus menus) {
        super(plugin, gui, menus);
    }

    public void open(Player p, String regionId) {
        gui.openUpgradeGui(p, regionId);
    }

    public void onClick(Player p, GuiManager.RyxHolder h, int slot) {
        String rid      = h.getRegionId();
        int currentLvl  = plugin.getUpgradeManager().getLevel(rid);
        int maxLvl      = ProtectionBlock.maxLevel();

        // ── Slots de nivel 1-7 ────────────────────────
        for (int i = 0; i < LEVEL_SLOTS.length; i++) {
            if (LEVEL_SLOTS[i] != slot) continue;
            int targetLvl = i + 1;

            if (targetLvl != currentLvl + 1) {
                sError(p);
                if (targetLvl <= currentLvl)
                    p.sendMessage(Txt.warn("Ya tienes ese nivel o uno superior."));
                else
                    p.sendMessage(Txt.warn("Mejora primero a §e"
                            + ProtectionBlock.getName(currentLvl + 1) + "§e."));
                return;
            }
            if (targetLvl > maxLvl) return;

            if (ProtectionBlock.isPremium(targetLvl) && !p.hasPermission("ryx.upgrade.premium")) {
                sError(p);
                p.sendMessage(Txt.err("§dɴɪᴠᴇʟ §6" + ProtectionBlock.getName(targetLvl)
                        + "§c requiere §dᴘʀᴇᴍɪᴜᴍ§c."));
                return;
            }

            int cost = ProtectionBlock.getPPCost(targetLvl);
            int pp   = plugin.getUpgradeManager().getPP(p.getUniqueId());
            if (pp < cost) {
                sError(p);
                p.sendMessage(Txt.err("Necesitas §e" + cost + " ᴘᴘ§c. Tienes §e" + pp
                        + "§c. §8(Usa §f/factory exchange§8)"));
                return;
            }

            plugin.getUpgradeManager().addPP(p.getUniqueId(), -cost);
            plugin.getUpgradeManager().setLevel(rid, targetLvl);
            int newXR = ProtectionBlock.getXRadius(targetLvl);

            RegionStore.RegionRecord r = plugin.getRegionStore().getRecord(rid);
            if (r != null && r.blockLoc != null) {
                r.blockLoc.getBlock().setType(ProtectionBlock.getMaterial(targetLvl));
                spawnUpgradeEffect(r.blockLoc, targetLvl);
            }

            plugin.getRegionStore().resizeRegion(rid, newXR);
            plugin.getDtrManager().recalcMax(rid);
            plugin.getHologramManager().updateRegionHologram(rid);
            plugin.getPreviewManager().startUpgradePreview(p, newXR);

            sLevelUp(p);
            sound(p, Sound.BLOCK_ANVIL_USE,                1f, 1.3f);
            sound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE,    0.4f, 1.5f);

            p.sendMessage(Txt.ok("§a§l⬆ §fʀᴇɢɪóɴ mejorada a §e"
                    + ProtectionBlock.getName(targetLvl)
                    + "§f. ʀᴀᴅɪᴏ: §b±" + newXR
                    + "§f. ᴘᴘ: §e" + plugin.getUpgradeManager().getPP(p.getUniqueId())));

            gui.openUpgradeGui(p, rid);
            return;
        }

        // ── Slot 31: Sub-upgrade +5 radio ─────────────
        if (slot == 31) {
            int pp   = plugin.getUpgradeManager().getPP(p.getUniqueId());
            int cost = UpgradeManager.SUB_UPGRADE_COST;
            if (pp < cost) {
                sError(p);
                p.sendMessage(Txt.err("Necesitas §e" + cost + " ᴘᴘ§c. Tienes §e" + pp + "§c."));
                return;
            }
            String err = plugin.getUpgradeManager().subUpgrade(p, rid);
            if (err == null) {
                int newXR = plugin.getUpgradeManager().getXRadius(rid);
                plugin.getRegionStore().resizeRegion(rid, newXR);
                plugin.getHologramManager().updateRegionHologram(rid);
                plugin.getPreviewManager().startRegionPreview(p, rid);
                sound(p, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.3f);
                p.sendMessage(Txt.ok("§b+5 §ade radio. ɴᴜᴇᴠᴏ ʀᴀᴅɪᴏ: §f±" + newXR + "§a."));
                gui.openUpgradeGui(p, rid);
            } else {
                sError(p);
                p.sendMessage(Txt.err(err));
            }
            return;
        }

        // ── Slot 35: Vista previa del siguiente nivel ──
        if (slot == 35) {
            int nextLvl = currentLvl + 1;
            if (nextLvl <= maxLvl) {
                plugin.getPreviewManager().startUpgradePreview(p,
                        ProtectionBlock.getXRadius(nextLvl));
                sound(p, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.3f);
                p.sendMessage(Txt.info("ᴠɪꜱᴛᴀ ᴘʀᴇᴠɪᴀ §8— §6"
                        + ProtectionBlock.getName(nextLvl)
                        + " §7— ʀᴀᴅɪᴏ §f±" + ProtectionBlock.getXRadius(nextLvl)
                        + "§7 (dorado)."));
            } else {
                sError(p);
                p.sendMessage(Txt.warn("Ya estás en el §eɴɪᴠᴇʟ ᴍáxɪᴍᴏ§e."));
            }
            return;
        }

        // ── Navegación ────────────────────────────────
        switch (slot) {
            case 46 -> { sBack(p);  menus.info.open(p, rid); }
            case 49 -> { sClose(p); p.closeInventory(); }
        }
    }

    // ── Efecto visual al mejorar ──────────────────────

    private void spawnUpgradeEffect(org.bukkit.Location loc, int level) {
        org.bukkit.World world = loc.getWorld();
        if (world == null) return;
        org.bukkit.Location center = loc.clone().add(0.5, 0.5, 0.5);
        int rgb = switch (level) {
            case 2 -> 0x555555; case 3 -> 0xC0C0C0; case 4 -> 0xFFD700;
            case 5 -> 0x00FFFF; case 6 -> 0x00FF66; default -> 0xAA00FF;
        };
        java.awt.Color c = new java.awt.Color(rgb);
        org.bukkit.Particle.DustOptions dust = new org.bukkit.Particle.DustOptions(
                org.bukkit.Color.fromRGB(c.getRed(), c.getGreen(), c.getBlue()), 2.5f);
        world.spawnParticle(org.bukkit.Particle.DUST,    center, 50, 0.6, 0.6, 0.6, 0, dust);
        world.spawnParticle(org.bukkit.Particle.END_ROD, center, 20, 0.4, 0.5, 0.4, 0.12);
        world.spawnParticle(org.bukkit.Particle.FLASH,   center,  3, 0.1, 0.1, 0.1, 0.01);
    }
}
