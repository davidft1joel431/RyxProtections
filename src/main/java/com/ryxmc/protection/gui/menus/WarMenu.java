package com.ryxmc.protection.gui.menus;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.gui.GuiManager;
import com.ryxmc.protection.war.WarManager;
import com.ryxmc.protection.util.Txt;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * WarMenu v1.0 — Panel de administración de la Guerra de Clanes
 *
 * Layout (54 slots):
 *  Slot 4  → Título / Estado actual de la guerra
 *  Slot 13 → INICIAR / DETENER guerra (toggle)
 *  Slot 21 → Duración -5 min
 *  Slot 22 → Display de duración configurada
 *  Slot 23 → Duración +5 min
 *  Slot 28 → Estado actual (activa/inactiva + tiempo restante)
 *  Slot 30 → Reglas de la guerra (info)
 *  Slot 32 → Estadísticas de kills (top 5)
 *  Slot 34 → Estadísticas de saqueos (top 5)
 *  Slot 46 → ← Regresar al Admin Panel
 *  Slot 49 → ✖ Cerrar
 */
public class WarMenu extends BaseMenu {

    public WarMenu(RyxProtection plugin, GuiManager gui, Menus menus) {
        super(plugin, gui, menus);
    }

    public void open(Player p) {
        gui.openWarGui(p);
    }

    public void onClick(Player p, GuiManager.RyxHolder h, int slot) {
        WarManager war = plugin.getWarManager();
        if (war == null) { sError(p); return; }

        switch (slot) {

            // ── 13: INICIAR / DETENER ─────────────────────────────────────
            case 13 -> {
                if (war.isWarActive()) {
                    // Detener guerra
                    sound(p, Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);
                    war.endWar(true);
                    p.sendMessage(Txt.ok("§c⚔ Guerra §cdetenida §apor administrador."));
                } else {
                    // Iniciar guerra
                    sound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
                    boolean started = war.startWar();
                    if (started) {
                        p.sendMessage(Txt.ok("§c⚔ §f§l¡GUERRA §c§lINICIADA§f§l! §aDuración: §f"
                                + WarManager.formatDuration(war.getDurationMs())));
                    } else {
                        sError(p);
                        p.sendMessage(Txt.err("Ya hay una guerra activa."));
                    }
                }
                gui.openWarGui(p);
            }

            // ── 21: Duración -5 min ───────────────────────────────────────
            case 21 -> {
                if (war.isWarActive()) {
                    sError(p); p.sendMessage(Txt.err("No puedes cambiar la duración con la guerra activa.")); return;
                }
                war.decreaseDuration();
                sound(p, Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
                p.sendMessage(Txt.info("Duración → §e" + WarManager.formatDuration(war.getDurationMs())));
                gui.openWarGui(p);
            }

            // ── 23: Duración +5 min ───────────────────────────────────────
            case 23 -> {
                if (war.isWarActive()) {
                    sError(p); p.sendMessage(Txt.err("No puedes cambiar la duración con la guerra activa.")); return;
                }
                war.increaseDuration();
                sound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.3f);
                p.sendMessage(Txt.info("Duración → §e" + WarManager.formatDuration(war.getDurationMs())));
                gui.openWarGui(p);
            }

            // ── 46/49: Navegación ─────────────────────────────────────────
            case 46 -> { sBack(p); menus.admin.openMain(p); }
            case 49 -> { sClose(p); p.closeInventory(); }
        }
    }

    // ─────────────────────────────────────────────────
    //  Build GUI (llamado desde GuiManager)
    // ─────────────────────────────────────────────────

    public static void build(Inventory inv, Player p, RyxProtection plugin) {
        border(inv, plugin);
        WarManager war = plugin.getWarManager();
        if (war == null) return;

        boolean active = war.isWarActive();

        // ── Slot 4: Título ────────────────────────────────────────────────
        inv.setItem(4, item(Material.BEACON,
                active ? "§c§l⚔ §f§lGUERRA §c§lACTIVA §c§l⚔" : "§8§l⚔ §7§lGUERRA DE CLANES",
                Txt.THIN,
                active ? "§9ᴛɪᴇᴍᴘᴏ ʀᴇꜱᴛᴀɴᴛᴇ: §e" + WarManager.formatDuration(war.getRemainingMs())
                       : "§7Sin guerra activa.",
                Txt.THIN,
                "§7Gestiona la guerra de clanes del servidor."));

        // ── Slot 13: Botón iniciar/detener ───────────────────────────────
        if (active) {
            inv.setItem(13, item(Material.BARRIER,
                    "§c§l✖ §fᴅᴇᴛᴇɴᴇʀ §c§lGUERRA",
                    Txt.THIN,
                    "§9ᴛɪᴇᴍᴘᴏ ʀᴇꜱᴛᴀɴᴛᴇ: §e" + WarManager.formatDuration(war.getRemainingMs()),
                    "§9ᴘʀᴏɢʀᴇꜱᴏ: §f" + (int)(war.getProgress() * 100) + "%",
                    Txt.THIN,
                    "§c▶ Click para DETENER la guerra"));
        } else {
            inv.setItem(13, itemGlow(Material.NETHER_STAR,
                    "§a§l▶ §f§lINICIAR §a§lGUERRA",
                    Txt.THIN,
                    "§9ᴅᴜʀᴀᴄɪóɴ: §e" + WarManager.formatDuration(war.getDurationMs()),
                    Txt.THIN,
                    "§7Al iniciar la guerra:",
                    "§a✔ §7Todos verán la bossbar de tiempo",
                    "§a✔ §7Contenedores quedan accesibles",
                    "§a✔ §7PvP activo en todo el mapa",
                    "§c✘ §7No se podrán romper bloques",
                    Txt.THIN,
                    "§a▶ Click para INICIAR"));
        }

        // ── Slots 21-23: Ajuste de duración ──────────────────────────────
        boolean canChange = !active;
        inv.setItem(21, item(canChange ? Material.RED_CONCRETE : Material.GRAY_CONCRETE,
                canChange ? "§c§l◀ §f-5 minutos" : "§8◀ §7-5 min §8(guerra activa)",
                Txt.THIN,
                "§7Mínimo: §f" + WarManager.formatDuration(WarManager.MIN_DURATION_MS),
                canChange ? "§c▶ Click para restar 5 min" : "§8No disponible con guerra activa"));

        long dur = war.getDurationMs();
        inv.setItem(22, item(Material.CLOCK,
                "§e§l⏱ §fᴅᴜʀᴀᴄɪóɴ ᴄᴏɴꜰɪɢᴜʀᴀᴅᴀ",
                Txt.THIN,
                "§9ᴅᴜʀᴀᴄɪóɴ: §e§l" + WarManager.formatDuration(dur),
                Txt.THIN,
                "§7Min: §f" + WarManager.formatDuration(WarManager.MIN_DURATION_MS),
                "§7Max: §f" + WarManager.formatDuration(WarManager.MAX_DURATION_MS),
                "§7Paso: §f±5 min",
                Txt.THIN,
                active ? "§8(No modificable durante la guerra)" : "§7Usa ◀ / ▶ para ajustar"));

        inv.setItem(23, item(canChange ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
                canChange ? "§a§l▶ §f+5 minutos" : "§8▶ §7+5 min §8(guerra activa)",
                Txt.THIN,
                "§7Máximo: §f" + WarManager.formatDuration(WarManager.MAX_DURATION_MS),
                canChange ? "§a▶ Click para sumar 5 min" : "§8No disponible con guerra activa"));

        // ── Slot 28: Estado actual ────────────────────────────────────────
        inv.setItem(28, item(active ? Material.FIRE_CHARGE : Material.GRAY_DYE,
                active ? "§c§l🔥 §fᴇꜱᴛᴀᴅᴏ: §c§lGUERRA ACTIVA" : "§7§l⚪ §fᴇꜱᴛᴀᴅᴏ: §7SIN GUERRA",
                Txt.THIN,
                active ? "§9ɪɴɪᴄɪᴀᴅᴀ ʜᴀᴄᴇ: §f" + WarManager.formatDuration(war.getElapsedMs())
                       : "§7La guerra no ha comenzado.",
                active ? "§9ᴛɪᴇᴍᴘᴏ ʀᴇꜱᴛᴀɴᴛᴇ: §e" + WarManager.formatDuration(war.getRemainingMs())
                       : "§7Configura la duración y presiona §aINICIAR§7.",
                Txt.THIN,
                "§9ᴊᴜɢᴀᴅᴏʀᴇꜱ ᴏɴʟɪɴᴇ: §f" + Bukkit.getOnlinePlayers().size()));

        // ── Slot 30: Reglas de la guerra ────────────────────────────────
        inv.setItem(30, item(Material.BOOK,
                "§e§l📖 §fʀᴇɢʟᴀꜱ ᴅᴇ ʟᴀ ɢᴜᴇʀʀᴀ",
                Txt.THIN,
                "§a✔ §7Abrir cofres, barriles, tolvas, etc.",
                "§a✔ §7Robar ítems de contenedores.",
                "§a✔ §7Activar puertas, vallas, botones.",
                "§a✔ §7PvP activo en todo el mapa.",
                "§a✔ §7Sistema DTR sigue funcionando.",
                Txt.THIN,
                "§c✘ §7Romper bloques §c§lPROHIBIDO§7.",
                "§c✘ §7Acceder al Ender Chest §cPROHIBIDO§7.",
                Txt.THIN,
                "§8(Admins con ryx.admin pueden romper bloques)"));

        // ── Slot 32: Top Kills ───────────────────────────────────────────
        List<Map.Entry<UUID, Integer>> topKills = war.getTopKills();
        List<String> killLore = new ArrayList<>();
        killLore.add(Txt.THIN);
        killLore.add("§7Kills registrados en la guerra actual:");
        killLore.add(Txt.THIN);
        if (topKills.isEmpty()) {
            killLore.add("§7§o(Sin kills aún)");
        } else {
            String[] medals = {"§6🥇","§7🥈","§c🥉","§8#4","§8#5"};
            for (int i = 0; i < Math.min(5, topKills.size()); i++) {
                String name = Bukkit.getOfflinePlayer(topKills.get(i).getKey()).getName();
                killLore.add(medals[i] + " §f" + (name != null ? name : "?")
                        + " §8— §c" + topKills.get(i).getValue() + " kills");
            }
        }
        inv.setItem(32, buildLoreItem(Material.SKELETON_SKULL,
                "§c§l💀 §fᴛᴏᴘ ᴋɪʟʟꜱ", killLore));

        // ── Slot 34: Top Saqueadores ─────────────────────────────────────
        List<Map.Entry<UUID, Integer>> topLoot = war.getTopLooters();
        List<String> lootLore = new ArrayList<>();
        lootLore.add(Txt.THIN);
        lootLore.add("§7Saqueos registrados en la guerra actual:");
        lootLore.add(Txt.THIN);
        if (topLoot.isEmpty()) {
            lootLore.add("§7§o(Sin saqueos aún)");
        } else {
            String[] medals = {"§6🥇","§7🥈","§c🥉","§8#4","§8#5"};
            for (int i = 0; i < Math.min(5, topLoot.size()); i++) {
                String name = Bukkit.getOfflinePlayer(topLoot.get(i).getKey()).getName();
                lootLore.add(medals[i] + " §f" + (name != null ? name : "?")
                        + " §8— §d" + topLoot.get(i).getValue() + " accesos");
            }
        }
        inv.setItem(34, buildLoreItem(Material.CHEST,
                "§5§l💎 §fᴛᴏᴘ ꜱᴀǫᴜᴇᴀᴅᴏʀᴇꜱ", lootLore));

        // ── Navegación ────────────────────────────────────────────────────
        inv.setItem(46, item(Material.ARROW, "§7§l← §fʀᴇɢʀᴇꜱᴀʀ", Txt.THIN,
                "§7Volver al Panel de Administración."));
        inv.setItem(49, item(Material.BARRIER, "§c✖ §fᴄᴇʀʀᴀʀ"));
    }

    // ─────────────────────────────────────────────────
    //  Helpers estáticos de ítems
    // ─────────────────────────────────────────────────

    private static void border(Inventory inv, RyxProtection plugin) {
        ItemStack glass = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta m = glass.getItemMeta();
        if (m != null) { m.setDisplayName(" "); glass.setItemMeta(m); }
        int[] border = {0,1,2,3,5,6,7,8,9,17,18,26,27,35,36,44,45,47,48,50,51,52,53};
        for (int s : border) inv.setItem(s, glass);
    }

    private static ItemStack item(Material mat, String name, String... lore) {
        ItemStack is = new ItemStack(mat);
        ItemMeta m = is.getItemMeta();
        if (m == null) return is;
        m.setDisplayName(com.ryxmc.protection.util.Color.of(name));
        List<String> l = new ArrayList<>();
        for (String s : lore) l.add(com.ryxmc.protection.util.Color.of(s));
        m.setLore(l);
        is.setItemMeta(m);
        return is;
    }

    private static ItemStack itemGlow(Material mat, String name, String... lore) {
        ItemStack is = item(mat, name, lore);
        ItemMeta m = is.getItemMeta();
        if (m != null) {
            m.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            is.setItemMeta(m);
        }
        return is;
    }

    private static ItemStack buildLoreItem(Material mat, String name, List<String> lore) {
        ItemStack is = new ItemStack(mat);
        ItemMeta m = is.getItemMeta();
        if (m == null) return is;
        m.setDisplayName(com.ryxmc.protection.util.Color.of(name));
        List<String> colored = new ArrayList<>();
        for (String s : lore) colored.add(com.ryxmc.protection.util.Color.of(s));
        m.setLore(colored);
        is.setItemMeta(m);
        return is;
    }
}
