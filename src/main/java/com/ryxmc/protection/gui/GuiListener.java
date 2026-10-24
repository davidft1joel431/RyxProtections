package com.ryxmc.protection.gui;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.gui.menus.Menus;
import com.ryxmc.protection.util.Txt;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;

/**
 * GuiListener v3.0 — Router limpio.
 *
 * RESPONSABILIDAD UNICA: recibir eventos de Bukkit y delegar al menu correcto.
 * Toda la logica de negocio vive en com.ryxmc.protection.gui.menus.*
 *
 * Al agregar un nuevo menu:
 *  1. Crear la clase en gui/menus/
 *  2. Registrarla en Menus.java
 *  3. Agregar un case en handleClick()
 */
public class GuiListener implements Listener {

    private final RyxProtection plugin;
    private final GuiManager    gui;
    private final Menus         menus;

    public GuiListener(RyxProtection plugin, GuiManager gui) {
        this.plugin = plugin;
        this.gui    = gui;
        this.menus  = new Menus(plugin, gui);
    }

    // ─────────────────────────────────────────────────
    //  Anti-exploit + routing de clicks
    // ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        var holder = event.getInventory().getHolder();

        if (holder instanceof GuiManager.RyxHolder rxh) {
            if (!gui.isLegit(p.getUniqueId(), rxh)) {
                event.setCancelled(true);
                p.closeInventory();
                return;
            }
            event.setCancelled(true);
            if (event.getRawSlot() < event.getInventory().getSize())
                handleClick(p, rxh, event.getSlot(), event.getClick());
            return;
        }

        // Anti-exploit: titulo parece del plugin pero sin RyxHolder registrado
        if (gui.isPluginTitle(event.getView().getTitle()) && !gui.hasOpen(p.getUniqueId())) {
            event.setCancelled(true);
            p.closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof GuiManager.RyxHolder)
            event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player p)) return;
        if (event.getInventory().getHolder() instanceof GuiManager.RyxHolder rxh) {
            if (gui.getOpen(p.getUniqueId()) == rxh)
                gui.unregister(p.getUniqueId());
        }
    }

    // ─────────────────────────────────────────────────
    //  Router principal
    // ─────────────────────────────────────────────────

    private void handleClick(Player p, GuiManager.RyxHolder h,
                             int slot, ClickType click) {
        switch (h.getType()) {
            case PROTECTION_INFO_OWNER,
                 PROTECTION_INFO_MEMBER,
                 PROTECTION_INFO_VISITOR  -> menus.info.onClick(p, h, slot);
            case PROTECTION_SETTINGS      -> menus.settings.onClick(p, h, slot);
            case PROTECTION_MEMBERS       -> menus.members.onClick(p, h, slot, click);
            case PROTECTION_ENERGY        -> menus.energy.onClick(p, h, slot);
            case PROTECTION_UPGRADE,
                 PROTECTION_UPGRADE_SUB   -> menus.upgrade.onClick(p, h, slot);
            case PROTECTION_SHOP          -> menus.shop.onClick(p, h, slot);
            case FACTORY_MAIN -> {
                if (h.getRegionId() == null)
                    plugin.getFactoryShopGui().handleShopClick(p, slot);
                // regionId != null: lo maneja FactoryListener directamente
            }
            case FACTORY_UPGRADE ->
                plugin.getFactoryShopGui().handleUpgradeClick(p, h.getRegionId(), slot);
            case ADMIN_MAIN         -> menus.admin.onClickMain(p, h, slot);
            case ADMIN_REGIONS      -> menus.admin.onClickRegions(p, h, slot, click);
            case ADMIN_PLAYERS      -> p.closeInventory();
            case ADMIN_DTR          -> menus.admin.onClickDtr(p, h, slot, click);
            case PROTECTION_OPTIONS -> menus.protectionOptions.onClick(p, h, slot);
            case WAR_EVENT          -> menus.war.onClick(p, h, slot);
            case CONFIRM            -> menus.confirm.onClick(p, h, slot);
        }
    }

    // ─────────────────────────────────────────────────
    //  Chat input
    // ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        if (!gui.hasChatInput(p.getUniqueId())) return;

        event.setCancelled(true);
        GuiManager.ChatInput ci = gui.getChatInput(p.getUniqueId());
        gui.clearChatInput(p.getUniqueId());

        String input = PlainTextComponentSerializer.plainText()
                .serialize(event.message()).trim();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (input.equalsIgnoreCase("cancelar")) {
                p.sendMessage(Txt.warn("Operacion cancelada."));
                // Si es un input de admin (sin regionId), abrir el panel admin
                if (ci.regionId() == null) {
                    gui.openAdminGui(p);
                } else {
                    gui.openSettingsGui(p, ci.regionId());
                }
                return;
            }
            switch (ci.type()) {
                // ── Configuracion de region ──────────────────────────
                case ADD_MEMBER       -> menus.settings.chatAddMember      (p, ci.regionId(), input);
                case REMOVE_MEMBER    -> menus.settings.chatRemoveMember   (p, ci.regionId(), input);
                case RENAME           -> menus.settings.chatRename         (p, ci.regionId(), input);
                case BLACKLIST_ADD    -> menus.settings.chatBlacklistAdd   (p, ci.regionId(), input);
                case BLACKLIST_REMOVE -> menus.settings.chatBlacklistRemove(p, ci.regionId(), input);
                // ── Admin v3.0 ────────────────────────────────────────
                case ADMIN_GIVE_BLOCK   -> menus.admin.chatAdminGiveBlock  (p, input);
                case ADMIN_GIVE_FACTORY -> menus.admin.chatAdminGiveFactory(p, input);
                case ADMIN_TP_REGION    -> menus.admin.chatAdminTpRegion   (p, input);
                case ADMIN_BROADCAST    -> menus.admin.chatAdminBroadcast  (p, input);
                case ADMIN_FORCE_REMOVE_REGION -> menus.admin.chatAdminForceRemoveRegion(p, input);
            }
        });
    }
}
