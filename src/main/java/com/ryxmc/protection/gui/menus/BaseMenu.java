package com.ryxmc.protection.gui.menus;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.gui.GuiManager;
import com.ryxmc.protection.util.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.Arrays;

public abstract class BaseMenu {
    protected final RyxProtection plugin;
    protected final GuiManager    gui;
    protected final Menus         menus;

    protected BaseMenu(RyxProtection plugin, GuiManager gui, Menus menus) {
        this.plugin = plugin; this.gui = gui; this.menus = menus;
    }

    protected ItemStack makeItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(Color.of(name));
        if (lore.length > 0) meta.setLore(Arrays.stream(lore).map(Color::of).toList());
        item.setItemMeta(meta); return item;
    }

    protected ItemStack pane(Material mat) {
        ItemStack item = new ItemStack(mat); ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(" "); item.setItemMeta(meta); } return item;
    }
    protected ItemStack pane()       { return pane(Material.GRAY_STAINED_GLASS_PANE); }
    protected ItemStack blackPane()  { return pane(Material.BLACK_STAINED_GLASS_PANE); }
    protected ItemStack purplePane() { return pane(Material.PURPLE_STAINED_GLASS_PANE); }

    protected void sClick   (Player p) { sound(p, Sound.UI_BUTTON_CLICK,                 1f, 1.0f); }
    protected void sBack    (Player p) { sound(p, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON,    1f, 0.8f); }
    protected void sSuccess (Player p) { sound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP,    1f, 1.2f); }
    protected void sError   (Player p) { sound(p, Sound.BLOCK_NOTE_BLOCK_BASS,           1f, 0.5f); }
    protected void sDanger  (Player p) { sound(p, Sound.BLOCK_ANVIL_LAND,               0.5f, 0.8f); }
    protected void sLevelUp (Player p) { sound(p, Sound.ENTITY_PLAYER_LEVELUP,          0.8f, 1.4f); }
    protected void sTeleport(Player p) { sound(p, Sound.ENTITY_ENDERMAN_TELEPORT,       0.6f, 1.2f); }
    protected void sToggleOn (Player p){ sound(p, Sound.BLOCK_NOTE_BLOCK_PLING,         0.8f, 1.5f); }
    protected void sToggleOff(Player p){ sound(p, Sound.BLOCK_NOTE_BLOCK_BASS,          0.8f, 0.7f); }
    protected void sBuy     (Player p) { sound(p, Sound.ENTITY_PLAYER_LEVELUP,          0.7f, 1.6f); }
    protected void sClose   (Player p) { sound(p, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON,   0.8f, 0.6f); }

    protected void sound(Player p, Sound s, float vol, float pitch) {
        p.playSound(p.getLocation(), s, vol, pitch);
    }
}
