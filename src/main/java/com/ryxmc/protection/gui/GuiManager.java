package com.ryxmc.protection.gui;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.dtr.DTRData;
import com.ryxmc.protection.manager.RegionStore;
import com.ryxmc.protection.model.ProtectionRole;
import com.ryxmc.protection.upgrade.UpgradeManager;
import com.ryxmc.protection.util.Color;
import com.ryxmc.protection.util.Txt;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;

import java.util.*;

/**
 * GuiManager v2.4
 *
 * Cambios v2.4 (solo adiciones):
 *  + ChatInputType: TRANSFER_OWNERSHIP, SET_MOTD.
 *  + buildSettingsGui: renovado con PvP desactivado, versalitas y nuevos ítems
 *    (slot 20 Transferir, 22 Ver lista negra, 29 Bloquear región, 33 MOTD, 41 Mobs).
 *  + buildUpgradeGui: versalitas en todos los textos.
 *  + buildShopGui: versalitas.
 *  + buildEnergyGui: versalitas.
 *  + openConfirmRemoveGui: versalitas.
 */
public class GuiManager {

    // ── Tipos de GUI ──────────────────────────────────
    public enum GuiType {
        PROTECTION_INFO_OWNER, PROTECTION_INFO_MEMBER, PROTECTION_INFO_VISITOR,
        PROTECTION_SETTINGS, PROTECTION_MEMBERS, PROTECTION_ENERGY,
        PROTECTION_UPGRADE, PROTECTION_UPGRADE_SUB, PROTECTION_SHOP,
        FACTORY_MAIN, FACTORY_UPGRADE,
        ADMIN_MAIN, ADMIN_REGIONS, ADMIN_PLAYERS, ADMIN_DTR,
        PROTECTION_OPTIONS,
        WAR_EVENT,
        CONFIRM
    }

    public static class RyxHolder implements InventoryHolder {
        private final GuiType type;
        private final UUID    playerUUID;
        private final String  regionId;
        private Inventory     inventory;
        private int extra = 0;

        public RyxHolder(GuiType t, UUID u, String r)            { type=t; playerUUID=u; regionId=r; }
        public RyxHolder(GuiType t, UUID u, String r, int extra) { this(t,u,r); this.extra=extra; }

        @Override public Inventory getInventory()      { return inventory; }
        public void setInventory(Inventory i)          { this.inventory = i; }
        public GuiType getType()                       { return type; }
        public UUID    getPlayerUUID()                 { return playerUUID; }
        public String  getRegionId()                   { return regionId; }
        public int     getExtra()                      { return extra; }
        public void    setExtra(int v)                 { this.extra = v; }
    }

    private final RyxProtection         plugin;
    private final Map<UUID, RyxHolder>  openGuis   = new HashMap<>();
    private final Map<UUID, ChatInput>  chatInputs = new HashMap<>();
    private final GuiAnimator           animator;

    public record ChatInput(ChatInputType type, String regionId) {}

    public enum ChatInputType {
        ADD_MEMBER, REMOVE_MEMBER, RENAME, BLACKLIST_ADD, BLACKLIST_REMOVE,
        // v3.3 nuevos
        TRANSFER_OWNERSHIP, SET_MOTD,
        // Admin v3.0 / v4.0
        ADMIN_GIVE_BLOCK, ADMIN_GIVE_FACTORY, ADMIN_TP_REGION, ADMIN_BROADCAST,
        ADMIN_FORCE_REMOVE_REGION
    }

    public GuiManager(RyxProtection plugin) {
        this.plugin   = plugin;
        this.animator = new GuiAnimator(plugin);
    }

    // ── Registry ──────────────────────────────────────
    public void register(UUID u, RyxHolder h)    { openGuis.put(u, h); }
    public void unregister(UUID u)               { openGuis.remove(u); }
    public boolean isLegit(UUID u, RyxHolder h)  { return openGuis.get(u) == h; }
    public RyxHolder getOpen(UUID u)             { return openGuis.get(u); }
    public boolean hasOpen(UUID u)               { return openGuis.containsKey(u); }
    public void setChatInput(UUID u, ChatInput c){ chatInputs.put(u, c); }
    public ChatInput getChatInput(UUID u)        { return chatInputs.get(u); }
    public boolean hasChatInput(UUID u)          { return chatInputs.containsKey(u); }
    public void clearChatInput(UUID u)           { chatInputs.remove(u); }
    public GuiAnimator getAnimator()             { return animator; }
    public void shutdown()                       { animator.stopAll(); }

    // ── Abrir GUIs ────────────────────────────────────

    public void openInfoGui(Player p, String regionId) {
        RegionStore.RegionRecord r = plugin.getRegionStore().getRecord(regionId);
        if (r == null) { p.sendMessage(Txt.err("Región no encontrada.")); return; }

        GuiType type;
        if (p.hasPermission("ryx.admin") || r.ownerUUID.equals(p.getUniqueId()))
            type = GuiType.PROTECTION_INFO_OWNER;
        else if (plugin.getRegionStore().getRole(regionId, p.getUniqueId()) != null)
            type = GuiType.PROTECTION_INFO_MEMBER;
        else
            type = GuiType.PROTECTION_INFO_VISITOR;

        String name  = plugin.getRegionStore().getDisplayName(regionId);
        String title = switch (type) {
            case PROTECTION_INFO_OWNER  -> "§6⬛ §e§l" + name + " §8[§6ᴅᴜᴇñᴏ§8]";
            case PROTECTION_INFO_MEMBER -> "§b⬛ §f§l" + name + " §8[§bᴍɪᴇᴍʙʀᴏ§8]";
            default                     -> "§7⬛ §f" + name;
        };

        RyxHolder h = new RyxHolder(type, p.getUniqueId(), regionId);
        Inventory inv = Bukkit.createInventory(h, 54, Color.of(title));
        h.setInventory(inv);
        buildInfoGui(inv, p, regionId, r, type);
        register(p.getUniqueId(), h);
        p.openInventory(inv);

        DTRData animDtr = plugin.getDtrManager().get(regionId);
        double dtrPct = animDtr.getMaxDtr() > 0
                ? Math.max(0, Math.min(1, animDtr.getDtr() / animDtr.getMaxDtr())) : 0;
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> animator.startInfoAnimation(p, inv, dtrPct, animDtr.isRaidable()), 1L);
    }

    public void openSettingsGui(Player p, String regionId) {
        RegionStore.RegionRecord r = plugin.getRegionStore().getRecord(regionId);
        if (r == null) { p.sendMessage(Txt.err("Región no encontrada.")); return; }
        if (!r.ownerUUID.equals(p.getUniqueId()) && !p.hasPermission("ryx.admin")) {
            p.sendMessage(Txt.err("Solo el dueño puede configurar la región.")); return;
        }
        RyxHolder h = new RyxHolder(GuiType.PROTECTION_SETTINGS, p.getUniqueId(), regionId);
        Inventory inv = Bukkit.createInventory(h, 54, Color.of(
                "§6⚙ §e§lᴄᴏɴꜰɪɢᴜʀᴀᴄɪóɴ §8— §f"
                        + plugin.getRegionStore().getDisplayName(regionId)));
        h.setInventory(inv);
        buildSettingsGui(inv, p, regionId, r);
        register(p.getUniqueId(), h);
        p.openInventory(inv);
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> animator.startSettingsAnimation(p, inv), 1L);
    }

    public void openMembersGui(Player p, String regionId)            { openMembersGui(p, regionId, 0); }
    public void openMembersGui(Player p, String regionId, int page) {
        RegionStore.RegionRecord r = plugin.getRegionStore().getRecord(regionId);
        if (r == null) return;
        RyxHolder h = new RyxHolder(GuiType.PROTECTION_MEMBERS, p.getUniqueId(), regionId, page);
        Inventory inv = Bukkit.createInventory(h, 54, Color.of(
                "§e§l👥 §fᴍɪᴇᴍʙʀᴏꜱ §8— §7" + plugin.getRegionStore().getDisplayName(regionId)));
        h.setInventory(inv);
        buildMembersGui(inv, p, regionId, r, page);
        register(p.getUniqueId(), h);
        p.openInventory(inv);
    }

    public void openEnergyGui(Player p, String regionId) {
        RyxHolder h = new RyxHolder(GuiType.PROTECTION_ENERGY, p.getUniqueId(), regionId);
        Inventory inv = Bukkit.createInventory(h, 54, Color.of(
                "§d✦ §f§lɢᴇᴍᴀꜱ ᴅᴇ ᴇɴᴇʀɢíᴀ §8— §7"
                        + plugin.getRegionStore().getDisplayName(regionId)));
        h.setInventory(inv);
        buildEnergyGui(inv, p, regionId);
        register(p.getUniqueId(), h);
        p.openInventory(inv);
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> animator.startEnergyAnimation(p, inv), 1L);
    }

    public void openUpgradeGui(Player p, String regionId) {
        RyxHolder h = new RyxHolder(GuiType.PROTECTION_UPGRADE, p.getUniqueId(), regionId);
        Inventory inv = Bukkit.createInventory(h, 54, Color.of(
                "§6⬆ §f§lᴍᴇᴊᴏʀᴀꜱ §8— §7" + plugin.getRegionStore().getDisplayName(regionId)));
        h.setInventory(inv);
        buildUpgradeGui(inv, p, regionId);
        register(p.getUniqueId(), h);
        p.openInventory(inv);
    }

    public void openShopGui(Player p) {
        RyxHolder h = new RyxHolder(GuiType.PROTECTION_SHOP, p.getUniqueId(), null);
        Inventory inv = Bukkit.createInventory(h, 54, Color.of("§6🛒 §e§lᴛɪᴇɴᴅᴀ ᴅᴇ ᴘʀᴏᴛᴇᴄᴄɪᴏɴᴇꜱ"));
        h.setInventory(inv);
        buildShopGui(inv, p);
        register(p.getUniqueId(), h);
        p.openInventory(inv);
    }

    public void openAdminGui(Player p) {
        if (!p.hasPermission("ryx.admin")) { p.sendMessage(Txt.err("Sin permiso.")); return; }
        RyxHolder h = new RyxHolder(GuiType.ADMIN_MAIN, p.getUniqueId(), null);
        Inventory inv = Bukkit.createInventory(h, 54,
                Color.of("§4⚠ §c§lᴘᴀɴᴇʟ ᴅᴇ ᴀᴅᴍɪɴɪꜱᴛʀᴀᴄɪóɴ"));
        h.setInventory(inv);
        buildAdminMainGui(inv);
        register(p.getUniqueId(), h);
        p.openInventory(inv);
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> animator.startAdminAnimation(p, inv), 1L);
    }

    public void openAdminRegionsGui(Player p, int page) {
        RyxHolder h = new RyxHolder(GuiType.ADMIN_REGIONS, p.getUniqueId(), null, page);
        Inventory inv = Bukkit.createInventory(h, 54,
                Color.of("§4📋 §cʟɪꜱᴛᴀ ᴅᴇ ʀᴇɢɪᴏɴᴇꜱ §8(pág." + (page+1) + ")"));
        h.setInventory(inv);
        buildAdminRegionsGui(inv, p, page);
        register(p.getUniqueId(), h);
        p.openInventory(inv);
    }

    public void openAdminDtrGui(Player p, int page) {
        RyxHolder h = new RyxHolder(GuiType.ADMIN_DTR, p.getUniqueId(), null, page);
        Inventory inv = Bukkit.createInventory(h, 54,
                Color.of("§c⚡ §4§lɢᴇꜱᴛɪóɴ ᴅᴛʀ §8(pág." + (page+1) + ")"));
        h.setInventory(inv);
        buildAdminDtrGui(inv, p, page);
        register(p.getUniqueId(), h);
        p.openInventory(inv);
    }

    public void openConfirmRemoveGui(Player p, String regionId) {
        RyxHolder h = new RyxHolder(GuiType.CONFIRM, p.getUniqueId(), regionId);
        Inventory inv = Bukkit.createInventory(h, 27,
                Color.of("§4⚠ §cᴄᴏɴꜰɪʀᴍᴀʀ ᴇʟɪᴍɪɴᴀᴄɪóɴ"));
        h.setInventory(inv);
        fill(inv, 27, pane(Material.BLACK_STAINED_GLASS_PANE));
        inv.setItem(4, makeItem(Material.TNT,
                "§4§l⚠ §c¿ᴇʟɪᴍɪɴᴀʀ ʀᴇɢɪóɴ?",
                "§7Esta acción es §c§lɪʀʀᴇᴠᴇʀꜱɪʙʟᴇ§7.",
                "§7Perderás tu protección y tus mejoras.",
                Txt.THIN,
                "§cEl bloque será dropeado en el suelo."));
        inv.setItem(11, makeItem(Material.LIME_CONCRETE,
                "§a§l✔ §fꜱÍ, ᴇʟɪᴍɪɴᴀʀ",
                "§7Confirmar eliminación permanente."));
        inv.setItem(15, makeItem(Material.RED_CONCRETE,
                "§c§l✘ §fᴄᴀɴᴄᴇʟᴀʀ",
                "§7Volver al menú de información."));
        register(p.getUniqueId(), h);
        p.openInventory(inv);
    }

    // ── Constructores de GUIs ─────────────────────────

    private void buildInfoGui(Inventory inv, Player p, String regionId,
                               RegionStore.RegionRecord r, GuiType type) {
        border(inv);
        DTRData dtr = plugin.getDtrManager().get(regionId);
        int lvl = plugin.getUpgradeManager().getLevel(regionId);
        int xr  = plugin.getUpgradeManager().getXRadius(regionId);
        String lvlName = com.ryxmc.protection.config.ProtectionBlock.getName(lvl);

        inv.setItem(13, makeItem(Material.SHIELD,
                "§6§l⬛ §e§l" + plugin.getRegionStore().getDisplayName(regionId),
                Txt.THIN,
                "§9ɪᴅ: §f" + regionId,
                "§9ᴅᴜᴇñᴏ: §e" + Txt.sc(r.ownerName),
                "§9ɴɪᴠᴇʟ: §f" + lvlName + " §8(Nv." + lvl + ")",
                "§9ʀᴀᴅɪᴏ: §f" + (xr*2) + "×" + (xr*2) + " §8(±§f" + xr + "§8)",
                Txt.THIN,
                "§9ᴅᴛʀ: " + dtr.formatted(),
                dtr.isRaidable() ? "§4§l⚠ ʀᴀɪᴅᴇᴀʙʟᴇ!" : "§a✔ ᴘʀᴏᴛᴇɢɪᴅᴀ"));

        inv.setItem(22, makeItem(dtr.isRaidable() ? Material.REDSTONE : Material.AMETHYST_SHARD,
                "§d§l✦ §fɢᴇᴍᴀꜱ ᴅᴇ ᴇɴᴇʀɢíᴀ",
                Txt.THIN,
                "§9ᴅᴛʀ: " + dtr.formatted(),
                "§9ᴍáxɪᴍᴏ: §f" + String.format("%.1f", dtr.getMaxDtr()),
                dtr.isInCooldown()
                        ? "§cᴄᴏᴏʟᴅᴏᴡɴ: §f" + Txt.time(dtr.cooldownSecondsLeft())
                        : (dtr.getDtr() < dtr.getMaxDtr() ? "§aʀᴇɢᴇɴᴇʀᴀɴᴅᴏ +0.1/2min" : "§aᴅᴛʀ ᴄᴏᴍᴘʟᴇᴛᴏ"),
                Txt.THIN,
                "§9ɢᴇᴍᴀꜱ: §d" + plugin.getEnergyManager().countAllGems(p),
                "§d▶ Click para usar gemas"));

        boolean previewActive = plugin.getPreviewManager().hasPreview(p.getUniqueId());
        inv.setItem(30, makeItem(previewActive ? Material.ENDER_EYE : Material.SPYGLASS,
                previewActive ? "§b§l👁 §fᴏᴄᴜʟᴛᴀʀ ʀᴀᴅɪᴏ" : "§b§l👁 §fᴠᴇʀ ʀᴀᴅɪᴏ",
                Txt.THIN,
                "§7ʀᴀᴅɪᴏ: §f" + (xr*2) + "×" + (xr*2) + " bloques",
                Txt.THIN,
                previewActive ? "§c▶ Click para ocultar partículas"
                              : "§b▶ Click para mostrar el radio"));

        inv.setItem(31, makeItem(Material.WHITE_BED,
                "§a§l⌂ §fᴛᴇʟᴇᴛʀᴀɴꜱᴘᴏʀᴛᴀʀꜱᴇ",
                "§7Click para ir a tu región."));

        inv.setItem(32, makeItem(Material.PLAYER_HEAD,
                "§e§l👥 §fᴍɪᴇᴍʙʀᴏꜱ",
                "§9ᴛᴏᴛᴀʟ: §f" + (r.roles.size() + 1),
                "§7Click para ver la lista."));

        inv.setItem(15, makeItem(Material.CLOCK,
                "§a§l🔄 §fᴀᴄᴛᴜᴀʟɪᴢᴀʀ",
                Txt.THIN, "§7Recarga la información del menú.", "§a▶ Click para refrescar"));

        inv.setItem(20, makeItem(Material.NAME_TAG,
                "§7§l📋 §fɪᴅ ᴅᴇ ʀᴇɢɪóɴ",
                Txt.THIN, "§9ɪᴅ: §f" + regionId, "§7▶ Click para ver en chat"));

        if (type == GuiType.PROTECTION_INFO_OWNER) {
            inv.setItem(37, makeItem(Material.COMPARATOR,
                    "§6§l⚙ §fᴄᴏɴꜰɪɢᴜʀᴀᴄɪóɴ",
                    "§7Gestionar miembros, home, descripción.", Txt.THIN, "§7Click para abrir."));

            int nextCost = plugin.getUpgradeManager().getNextLevelCost(regionId);
            int myPP     = plugin.getUpgradeManager().getPP(p.getUniqueId());
            inv.setItem(40, makeItem(Material.ANVIL,
                    "§6§l⬆ §fᴍᴇᴊᴏʀᴀꜱ ᴅᴇ ʀᴇɢɪóɴ",
                    "§9ɴɪᴠᴇʟ: §e" + lvlName + " §8(Nv." + lvl + ")",
                    "§9ᴘᴘ: §e" + myPP,
                    nextCost > 0 ? "§9ᴘʀóxɪᴍᴏ: §e" + nextCost + " ᴘᴘ" : "§7ɴɪᴠᴇʟ ᴍáxɪᴍᴏ.",
                    Txt.THIN, "§7Click para ver mejoras."));
        }

        if (type == GuiType.PROTECTION_INFO_MEMBER) {
            ProtectionRole role = plugin.getRegionStore().getRole(regionId, p.getUniqueId());
            if (role != null)
                inv.setItem(40, makeItem(Material.NAME_TAG,
                        role.color() + role.tag() + " §fᴛᴜ ʀᴏʟ: §f" + role.display(),
                        Txt.THIN,
                        role.canAddMember() ? "§a✔ ɢᴇꜱᴛɪᴏɴᴀʀ ᴍɪᴇᴍʙʀᴏꜱ" : "§7✘ ɢᴇꜱᴛɪᴏɴᴀʀ ᴍɪᴇᴍʙʀᴏꜱ",
                        role.canTogglePvP() ? "§a✔ ᴛᴏɢɢʟᴇ ᴘᴠᴘ" : "§7✘ ᴛᴏɢɢʟᴇ ᴘᴠᴘ",
                        role.canRename()    ? "§a✔ ʀᴇɴᴏᴍʙʀᴀʀ" : "§7✘ ʀᴇɴᴏᴍʙʀᴀʀ"));
        }

        if (type == GuiType.PROTECTION_INFO_VISITOR)
            inv.setItem(40, makeItem(Material.BARRIER, "§c✖ §fᴄᴇʀʀᴀʀ"));
        else
            inv.setItem(49, makeItem(Material.BARRIER, "§c✖ §fᴄᴇʀʀᴀʀ"));
    }

    private void buildSettingsGui(Inventory inv, Player p, String regionId,
                                   RegionStore.RegionRecord r) {
        border(inv);
        boolean pvp    = plugin.getRegionStore().isPvPEnabled(regionId);
        boolean locked = plugin.getRegionStoreExt().isLocked(regionId);
        boolean mobs   = plugin.getRegionStoreExt().isMobSpawningEnabled(regionId);
        boolean animOn = animator.isEnabled(p.getUniqueId());
        String  motd   = plugin.getRegionStoreExt().getMotd(regionId);

        // ── Fila 1 · Toggle Animaciones ──────────────
        inv.setItem(13, makeItem(
                animOn ? Material.ENDER_EYE : Material.SPIDER_EYE,
                "§b§l✨ §fᴀɴɪᴍᴀᴄɪᴏɴᴇꜱ ɢᴜɪ",
                Txt.THIN,
                "§9ᴇꜱᴛᴀᴅᴏ: " + (animOn ? "§a§lᴀᴄᴛɪᴠᴀᴅᴀꜱ" : "§c§lᴅᴇꜱᴀᴄᴛɪᴠᴀᴅᴀꜱ"),
                Txt.THIN,
                "§7Activa o desactiva los efectos",
                "§7visuales animados de los menús.",
                Txt.THIN,
                "§b▶ Click para cambiar"));

        // ── Fila 2 · Gestión de miembros ─────────────
        inv.setItem(19, makeItem(Material.LIME_DYE,
                "§a§l✚ §fᴀɢʀᴇɢᴀʀ ᴍɪᴇᴍʙʀᴏ",
                Txt.THIN, "§7Invita a un jugador a tu región.",
                "§7Solo puede interactuar dentro.", Txt.THIN,
                "§a▶ Click → escribe el nombre en chat"));

        inv.setItem(20, makeItem(Material.GOLD_INGOT,
                "§6§l⇄ §fᴛʀᴀɴꜱꜰᴇʀɪʀ ᴘʀᴏᴘɪᴇᴅᴀᴅ",
                Txt.THIN,
                "§7Cede la región a otro jugador.",
                "§c§lACCIÓN IRREVERSIBLE.",
                "§7El nuevo dueño tendrá control total.",
                Txt.THIN,
                "§6▶ Click → escribe el nombre en chat"));

        inv.setItem(21, makeItem(Material.RED_DYE,
                "§c§l✖ §fʀᴇᴍᴏᴠᴇʀ ᴍɪᴇᴍʙʀᴏ",
                Txt.THIN, "§7Expulsa a un miembro de tu región.",
                Txt.THIN, "§c▶ Click → escribe el nombre en chat"));

        inv.setItem(22, makeItem(Material.WRITABLE_BOOK,
                "§4§l📋 §fᴠᴇʀ ʟɪꜱᴛᴀ ɴᴇɢʀᴀ",
                Txt.THIN,
                "§7Muestra todos los jugadores bloqueados.",
                Txt.THIN,
                "§4▶ Click para ver en chat"));

        inv.setItem(23, makeItem(Material.RED_CONCRETE,
                "§4§l☠ §fʟɪꜱᴛᴀ ɴᴇɢʀᴀ: ᴀñᴀᴅɪʀ",
                Txt.THIN, "§7Bloquea a un jugador.",
                "§7No podrá entrar a tu región.", Txt.THIN,
                "§4▶ Click → escribe el nombre en chat"));

        inv.setItem(25, makeItem(Material.ANVIL,
                "§b§l✎ §fʀᴇɴᴏᴍʙʀᴀʀ ʀᴇɢɪóɴ",
                Txt.THIN,
                "§9ɴᴏᴍʙʀᴇ: §f" + plugin.getRegionStore().getDisplayName(regionId),
                Txt.THIN,
                "§b▶ Click → escribe el nombre en chat"));

        // ── Fila 3 · Opciones de región ───────────────
        inv.setItem(28, makeItem(Material.STICK,
                "§8§l⚔ §7ᴘᴠᴘ §8[§cᴅᴇꜱᴀᴄᴛɪᴠᴀᴅᴏ§8]",
                Txt.THIN,
                "§8El PvP está desactivado",
                "§8por el administrador.",
                Txt.THIN,
                "§7§oNo disponible en este servidor"));

        inv.setItem(29, makeItem(locked ? Material.IRON_DOOR : Material.OAK_DOOR,
                locked ? "§c§l🔒 §fʀᴇɢɪóɴ ʙʟᴏǫᴜᴇᴀᴅᴀ" : "§a§l🔓 §fʀᴇɢɪóɴ ᴀʙɪᴇʀᴛᴀ",
                Txt.THIN,
                "§9ᴇꜱᴛᴀᴅᴏ: " + (locked ? "§c§lʙʟᴏǫᴜᴇᴀᴅᴀ" : "§a§lᴀʙɪᴇʀᴛᴀ"),
                Txt.THIN,
                locked ? "§7Los visitantes §cno §7pueden entrar."
                       : "§7Los visitantes §apueden §7entrar.",
                Txt.THIN,
                "§7▶ Click para cambiar"));

        inv.setItem(30, makeItem(Material.LIME_CONCRETE,
                "§2§l✦ §fʟɪꜱᴛᴀ ɴᴇɢʀᴀ: ǫᴜɪᴛᴀʀ",
                Txt.THIN, "§7Desbloquea a un jugador.", Txt.THIN,
                "§a▶ Click → escribe el nombre en chat"));

        inv.setItem(31, makeItem(Material.NAME_TAG,
                "§e§l⚑ §fᴠᴇʀ ᴍɪᴇᴍʙʀᴏꜱ",
                Txt.THIN, "§9ᴍɪᴇᴍʙʀᴏꜱ: §f" + r.roles.size(),
                Txt.THIN, "§7Click para ver la lista."));

        inv.setItem(32, makeItem(Material.COMPASS,
                "§a§l⌂ §fᴇꜱᴛᴀʙʟᴇᴄᴇʀ ʜᴏᴍᴇ",
                Txt.THIN,
                "§7Mueve el punto de /ps home",
                "§7a tu posición actual.",
                "§9Debes estar dentro de tu región.",
                Txt.THIN,
                "§a▶ Click para guardar posición"));

        inv.setItem(33, makeItem(Material.WRITABLE_BOOK,
                "§e§l✉ §fᴅᴇꜱᴄʀɪᴘᴄɪóɴ §8/ §fᴍᴏᴛᴅ",
                Txt.THIN,
                "§9ᴀᴄᴛᴜᴀʟ: §f" + (motd != null ? "\"" + motd + "\"" : "§8(ninguna)"),
                Txt.THIN,
                "§7Se muestra en el holograma",
                "§7y al entrar a la región.",
                Txt.THIN,
                "§e▶ Click → escribe el mensaje en chat"));

        inv.setItem(34, makeItem(Material.TNT,
                "§4§l⚠ §cᴇʟɪᴍɪɴᴀʀ ʀᴇɢɪóɴ",
                Txt.THIN,
                "§c§lɪʀʀᴇᴠᴇʀꜱɪʙʟᴇ. §7El bloque será dropeado.",
                Txt.THIN,
                "§4▶ Click para confirmar"));

        // ── Fila 4 · Extras ───────────────────────────
        inv.setItem(37, makeItem(Material.PLAYER_HEAD,
                "§e§l👥 §fᴠᴇʀ ᴍɪᴇᴍʙʀᴏꜱ",
                "§7Click para ver la lista de miembros."));

        inv.setItem(41, makeItem(mobs ? Material.ZOMBIE_HEAD : Material.SKELETON_SKULL,
                "§a§l🐾 §fᴛᴏɢɢʟᴇ ᴍᴏʙꜱ",
                Txt.THIN,
                "§9ᴇꜱᴛᴀᴅᴏ: " + (mobs ? "§a§lᴀᴄᴛɪᴠᴀᴅᴏꜱ" : "§c§lᴅᴇꜱᴀᴄᴛɪᴠᴀᴅᴏꜱ"),
                Txt.THIN,
                "§7Permite o bloquea que los mobs",
                "§7aparezcan dentro de tu región.",
                Txt.THIN,
                "§7▶ Click para cambiar"));

        inv.setItem(43, makeItem(Material.PAPER,
                "§7§lɪɴꜰᴏ ʀáᴘɪᴅᴀ",
                Txt.THIN,
                "§9ɪᴅ: §7" + regionId,
                "§9ᴅᴜᴇñᴏ: §f" + r.ownerName,
                "§9ᴍɪᴇᴍʙʀᴏꜱ: §f" + r.roles.size(),
                "§9ᴘᴠᴘ: §c§oDesactivado",
                "§9ʙʟᴏǫᴜᴇᴀᴅᴀ: " + (locked ? "§aSí" : "§7No"),
                "§9ᴍᴏʙꜱ: " + (mobs ? "§aSí" : "§7No")));

        // ── Fila 5 · Navegación ───────────────────────
        inv.setItem(46, makeItem(Material.ARROW,
                "§7§l← §fʀᴇɢʀᴇꜱᴀʀ",
                Txt.THIN, "§7Volver al menú de información."));
        inv.setItem(49, makeItem(Material.BARRIER, "§c✖ §fᴄᴇʀʀᴀʀ"));
    }

    private void buildMembersGui(Inventory inv, Player p, String regionId,
                                  RegionStore.RegionRecord r, int page) {
        border(inv);
        OfflinePlayer ownerOp = Bukkit.getOfflinePlayer(r.ownerUUID);
        boolean isOwnerOrAdmin = r.ownerUUID.equals(p.getUniqueId()) || p.hasPermission("ryx.admin");

        inv.setItem(4, makeItem(Material.PAPER,
                "§e§l👥 §fᴍɪᴇᴍʙʀᴏꜱ §8— §e" + plugin.getRegionStore().getDisplayName(regionId),
                Txt.THIN, "§9ᴅᴜᴇñᴏ: §f1", "§9ᴍɪᴇᴍʙʀᴏꜱ: §f" + r.roles.size(),
                Txt.THIN, isOwnerOrAdmin ? "§7Click en un miembro para gestionar." : "§7Solo lectura."));

        inv.setItem(10, buildSkull(ownerOp, ProtectionRole.OWNER));

        int[] slots = {11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
        List<Map.Entry<UUID, ProtectionRole>> entries = new ArrayList<>(r.roles.entrySet());
        int start = page * slots.length, end = Math.min(start + slots.length, entries.size()), si = 0;
        for (int i = start; i < end && si < slots.length; i++, si++)
            inv.setItem(slots[si], buildSkull(Bukkit.getOfflinePlayer(entries.get(i).getKey()), entries.get(i).getValue()));

        int maxPage = (int) Math.ceil(r.roles.size() / (double) slots.length);
        if (page > 0)          inv.setItem(45, makeItem(Material.ARROW, "§7« ᴀɴᴛᴇʀɪᴏʀ"));
        if (page < maxPage-1)  inv.setItem(53, makeItem(Material.ARROW, "§7ꜱɪɢᴜɪᴇɴᴛᴇ »"));
        inv.setItem(49, makeItem(Material.ARROW, "§7« §fʀᴇɢʀᴇꜱᴀʀ"));
    }

    private void buildEnergyGui(Inventory inv, Player p, String regionId) {
        border(inv);
        DTRData dtr = plugin.getDtrManager().get(regionId);

        inv.setItem(4, makeItem(dtr.isRaidable() ? Material.REDSTONE : Material.EMERALD,
                "§c§l⚡ §fᴇꜱᴛᴀᴅᴏ ᴅᴛʀ",
                Txt.THIN,
                "§9ᴅᴛʀ: " + dtr.formatted(),
                "§9ᴍáxɪᴍᴏ: §f" + String.format("%.1f", dtr.getMaxDtr()),
                dtr.isInCooldown()
                        ? "§cᴇꜱᴘᴇʀᴀ: §f" + Txt.time(dtr.cooldownSecondsLeft())
                        : (dtr.getDtr() < dtr.getMaxDtr() ? "§aʀᴇɢᴇɴᴇʀᴀɴᴅᴏ" : "§aᴅᴛʀ ᴄᴏᴍᴘʟᴇᴛᴏ")));

        inv.setItem(19, plugin.getEnergyManager().makeGemButton(p, com.ryxmc.protection.energy.EnergyManager.GemType.SMALL));
        inv.setItem(21, plugin.getEnergyManager().makeGemButton(p, com.ryxmc.protection.energy.EnergyManager.GemType.MEDIUM));
        inv.setItem(23, plugin.getEnergyManager().makeGemButton(p, com.ryxmc.protection.energy.EnergyManager.GemType.LARGE));
        inv.setItem(25, plugin.getEnergyManager().makeGemButton(p, com.ryxmc.protection.energy.EnergyManager.GemType.DIVINE));

        inv.setItem(31, makeItem(Material.BOOK,
                "§e§l📖 §f¿ᴄóᴍᴏ ᴄᴏɴꜱᴇɢᴜɪʀ ɢᴇᴍᴀꜱ?",
                Txt.THIN,
                "§7• §6ꜰáʙʀɪᴄᴀ ᴅᴇ ᴇɴᴇʀɢíᴀ §7(colócala y produce)",
                "§7• Completar misiones del servidor",
                "§7• Tienda oficial del servidor",
                "§7• Eventos especiales"));

        inv.setItem(46, makeItem(Material.ARROW, "§7§l← §fʀᴇɢʀᴇꜱᴀʀ",
                Txt.THIN, "§7Volver al menú de información."));
        inv.setItem(49, makeItem(Material.BARRIER, "§c✖ §fᴄᴇʀʀᴀʀ"));
    }

    private void buildUpgradeGui(Inventory inv, Player p, String regionId) {
        border(inv);
        int currentLvl = plugin.getUpgradeManager().getLevel(regionId);
        int currentXR  = com.ryxmc.protection.config.ProtectionBlock.getXRadius(currentLvl)
                         + plugin.getUpgradeManager().getSubBonus(regionId) * 5;
        int subBonus   = plugin.getUpgradeManager().getSubBonus(regionId);
        int pp         = plugin.getUpgradeManager().getPP(p.getUniqueId());
        int maxLvl     = com.ryxmc.protection.config.ProtectionBlock.maxLevel();

        inv.setItem(4, makeItem(
                com.ryxmc.protection.config.ProtectionBlock.getMaterial(currentLvl),
                "§6§l⬆ §fᴍᴇᴊᴏʀᴀꜱ §8— §f" + plugin.getRegionStore().getDisplayName(regionId),
                Txt.THIN,
                "§9ɴɪᴠᴇʟ: " + com.ryxmc.protection.config.ProtectionBlock.getName(currentLvl)
                        + " §8(Nv." + currentLvl + ")",
                "§9ʀᴀᴅɪᴏ: §f±" + currentXR + " §8(" + (currentXR*2) + "×" + (currentXR*2) + ")",
                "§9ꜱᴜʙ-ʙᴏɴᴜꜱ: §e+" + (subBonus*5) + " §8(" + subBonus + "/4)",
                "§9ᴘᴘ: §e" + pp,
                Txt.THIN, "§7100 ɢᴇᴍᴀꜱ = 1 ᴘᴘ §8(§f/factory exchange§8)"));

        int[] slots = {10,11,12,13,14,15,16};
        for (int i = 0; i < slots.length && i < maxLvl; i++) {
            int lvl = i + 1, xr = com.ryxmc.protection.config.ProtectionBlock.getXRadius(lvl);
            int cost = com.ryxmc.protection.config.ProtectionBlock.getPPCost(lvl);
            boolean isCurrent = lvl == currentLvl, isUnlocked = lvl < currentLvl;
            boolean isPremium = com.ryxmc.protection.config.ProtectionBlock.isPremium(lvl);
            boolean canBuy    = lvl == currentLvl + 1 && pp >= cost
                                && (!isPremium || p.hasPermission("ryx.upgrade.premium"));

            Material mat = isCurrent  ? Material.LIME_CONCRETE
                         : isUnlocked ? Material.GREEN_STAINED_GLASS_PANE
                         : canBuy     ? Material.YELLOW_CONCRETE
                         : isPremium  ? Material.PURPLE_CONCRETE
                         :              Material.RED_CONCRETE;

            String status = isCurrent  ? "§a§l▶ ɴɪᴠᴇʟ ᴀᴄᴛᴜᴀʟ"
                          : isUnlocked ? "§7§l✔ ᴅᴇꜱʙʟᴏǫᴜᴇᴀᴅᴏ"
                          : canBuy     ? "§e§l⬆ ᴅɪꜱᴘᴏɴɪʙʟᴇ"
                          : isPremium  ? "§d§lᴘʀᴇᴍɪᴜᴍ"
                          :              "§c§l✘ ʙʟᴏǫᴜᴇᴀᴅᴏ";

            List<String> lore = new ArrayList<>(Arrays.asList(
                    Txt.THIN, status, Txt.THIN,
                    "§9ʀᴀᴅɪᴏ: §f±" + xr + " §8(" + (xr*2) + "×" + (xr*2) + ")",
                    "§9ᴅᴛʀ ʙᴀꜱᴇ: §f" + (int)com.ryxmc.protection.config.ProtectionBlock.getBaseDtr(lvl),
                    cost == 0 ? "§aᴄᴏꜱᴛᴏ: §fɢʀᴀᴛɪꜱ" : "§9ᴄᴏꜱᴛᴏ: §e" + cost + " ᴘᴘ",
                    isPremium ? "§d§l⭐ Requiere rango PREMIUM" : ""));
            if (!isUnlocked && !isCurrent && pp < cost)
                lore.add("§cꜰᴀʟᴛᴀɴ: §f" + Math.max(0, cost-pp) + " ᴘᴘ");
            if (canBuy) { lore.add(Txt.THIN); lore.add("§a§l▶ Click para mejorar"); }

            inv.setItem(slots[i], makeItemWithList(mat,
                    com.ryxmc.protection.config.ProtectionBlock.getName(lvl)
                            + " §8(Nv." + lvl + ")", lore));
        }

        boolean canSub = plugin.getUpgradeManager().canSubUpgrade(regionId);
        int subCost    = UpgradeManager.SUB_UPGRADE_COST;
        inv.setItem(31, makeItem(canSub ? Material.DIAMOND : Material.COAL,
                canSub ? "§b§l+5 §fꜱᴜʙ-ᴜᴘɢʀᴀᴅᴇ ᴅᴇ ʀᴀᴅɪᴏ" : "§7ꜱᴜʙ-ᴜᴘɢʀᴀᴅᴇ §8(ᴍáx)",
                Txt.THIN,
                "§7ʀᴀᴅɪᴏ: §f±" + currentXR,
                canSub ? "§9ᴄᴏꜱᴛᴏ: §e" + subCost + " ᴘᴘ"
                       : "§7Sube de nivel para más sub-upgrades.",
                canSub && pp >= subCost ? Txt.THIN + "§b▶ Click para +5 radio"
                                        : canSub ? "§cꜰᴀʟᴛᴀɴ §f" + (subCost-pp) + " ᴘᴘ" : ""));

        if (currentLvl < maxLvl) {
            int nextXR   = com.ryxmc.protection.config.ProtectionBlock.getXRadius(currentLvl+1);
            int nextCost = com.ryxmc.protection.config.ProtectionBlock.getPPCost(currentLvl+1);
            inv.setItem(35, makeItem(Material.ENDER_EYE,
                    "§b§l👁 §fᴠɪꜱᴛᴀ ᴘʀᴇᴠɪᴀ §8— §f"
                            + com.ryxmc.protection.config.ProtectionBlock.getName(currentLvl+1),
                    Txt.THIN,
                    "§7ʀᴀᴅɪᴏ: §f±" + nextXR + " §8(" + (nextXR*2) + "×" + (nextXR*2) + ")",
                    "§7ᴄᴏꜱᴛᴏ: §e" + nextCost + " ᴘᴘ",
                    Txt.THIN, "§b▶ Click: partículas doradas del radio siguiente"));
        }

        inv.setItem(46, makeItem(Material.ARROW, "§7§l← §fʀᴇɢʀᴇꜱᴀʀ",
                Txt.THIN, "§7Volver al menú de información."));
        inv.setItem(49, makeItem(Material.BARRIER, "§c✖ §fᴄᴇʀʀᴀʀ"));
    }

    private void buildShopGui(Inventory inv, Player p) {
        border(inv);
        double balance = plugin.getVaultHook().isAvailable()
                ? plugin.getVaultHook().getBalance(p) : -1;
        int myPP = plugin.getUpgradeManager().getPP(p.getUniqueId());

        inv.setItem(4, makeItem(Material.GOLD_INGOT,
                "§6§l🛒 §e§lᴛɪᴇɴᴅᴀ ᴅᴇ ᴘʀᴏᴛᴇᴄᴄɪᴏɴᴇꜱ",
                Txt.THIN,
                balance >= 0 ? "§9ꜱᴀʟᴅᴏ: §e$" + String.format("%,.0f", balance) : "§7ꜱɪɴ ᴇᴄᴏɴᴏᴍíᴀ.",
                "§9ᴘᴘ: §e" + myPP,
                Txt.THIN,
                "§7Niveles §f1-5 §7disponibles para todos.",
                "§7Niveles §d6-7 §7requieren rango §dᴘʀᴇᴍɪᴜᴍ§7."));

        int[] bSlots = {19,21,23,25,28,30,32};
        int maxLvl   = com.ryxmc.protection.config.ProtectionBlock.maxLevel();
        for (int i = 0; i < bSlots.length && i < maxLvl; i++) {
            int lvl = i + 1, xr = com.ryxmc.protection.config.ProtectionBlock.getXRadius(lvl);
            int ppCost = com.ryxmc.protection.config.ProtectionBlock.getPPCost(lvl);
            boolean prem   = com.ryxmc.protection.config.ProtectionBlock.isPremium(lvl);
            boolean canBuy = !prem || p.hasPermission("ryx.upgrade.premium");

            ItemStack display = com.ryxmc.protection.config.ProtectionBlock.buildItem(plugin, lvl);
            ItemMeta  meta    = display.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(Color.of(Txt.THIN));
                lore.add(Color.of("§9ʀᴀᴅɪᴏ: §f±" + xr + " §8(" + (xr*2) + "×" + (xr*2) + ")"));
                lore.add(Color.of("§9ᴅᴛʀ ʙᴀꜱᴇ: §f" + com.ryxmc.protection.config.ProtectionBlock.getBaseDtr(lvl)));
                lore.add(Color.of(ppCost > 0 ? "§9ᴄᴏꜱᴛᴏ: §e" + ppCost + " ᴘᴘ" : "§9ᴄᴏꜱᴛᴏ: §aɢʀᴀᴛɪꜱ"));
                if (prem) lore.add(Color.of("§d⭐ Requiere rango PREMIUM"));
                lore.add(Color.of(Txt.THIN));
                lore.add(Color.of(!canBuy ? "§c✗ Requiere rango Premium" : "§a▶ Click para obtener"));
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            inv.setItem(bSlots[i], display);
        }

        double compPrice = plugin.getConfig().getDouble("factory.compressor_price", 8000);
        boolean canComp  = balance < 0 || balance >= compPrice;
        inv.setItem(34, makeItem(Material.CRAFTER,
                "§5§l⚗ §d§lᴄᴏᴍᴘʀᴇꜱᴏʀ ᴅᴇ ɢᴇᴍᴀꜱ",
                Txt.THIN,
                "§7Comprime §dGemas Pequeñas§7 en",
                "§dGemas Medianas §7y §5Grandes§7.",
                Txt.THIN,
                "§9ᴘʀᴇᴄɪᴏ: §e$" + String.format("%,.0f", compPrice),
                canComp ? "§a▶ Click para comprar" : "§c✗ Saldo insuficiente"));

        inv.setItem(49, makeItem(Material.BARRIER, "§c✖ §fᴄᴇʀʀᴀʀ"));
    }

    private void buildAdminMainGui(Inventory inv) {
        border(inv);
        long total    = plugin.getRegionStore().getAll().size();
        long raidable = plugin.getDtrManager().getAll().values().stream()
                .filter(DTRData::isRaidable).count();
        long factories= plugin.getFactoryManager().getAllFactories().size();

        inv.setItem(4, makeItem(Material.NETHER_STAR,
                "§4⚠ §c§lᴘᴀɴᴇʟ ᴅᴇ ᴀᴅᴍɪɴɪꜱᴛʀᴀᴄɪóɴ",
                Txt.THIN,
                "§9ʀᴇɢɪᴏɴᴇꜱ: §f" + total,
                "§9ʀᴀɪᴅᴇᴀʙʟᴇꜱ: §c" + raidable,
                "§9ꜰáʙʀɪᴄᴀꜱ: §f" + factories));

        inv.setItem(10, makeItem(Material.GRASS_BLOCK,    "§a§l📋 §fʟɪꜱᴛᴀʀ ʀᴇɢɪᴏɴᴇꜱ",    Txt.THIN, "§7Ver todas las regiones.", "§aClick para abrir."));
        inv.setItem(12, makeItem(Material.REDSTONE,       "§c§l⚡ §fɢᴇꜱᴛɪóɴ ᴅᴛʀ",         Txt.THIN, "§7Ver DTR de todas las regiones.", "§cClick para abrir."));
        inv.setItem(14, makeItem(Material.GOLD_INGOT,     "§6§l⭐ §fɢᴇꜱᴛɪóɴ ᴘᴘ",           Txt.THIN, "§7Dar/quitar PP.", "§6/pp <jugador>"));
        inv.setItem(16, makeItem(Material.BLAST_FURNACE,  "§6§l⚙ §fꜰáʙʀɪᴄᴀꜱ",             Txt.THIN, "§fActivas: §e" + factories));

        inv.setItem(19, makeItem(Material.SHIELD,                "§b§l🛡 §fᴄʀᴇᴀʀ ʀᴇɢɪóɴ",       Txt.THIN, "§7/ps give <bloque> <jugador>"));
        inv.setItem(20, makeItem(Material.EMERALD,               "§b§l✦ §fᴅᴀʀ ʙʟᴏǫᴜᴇ",          Txt.THIN, "§b▶ Click → jugador + nivel en chat"));
        inv.setItem(21, makeItem(Material.TNT,                   "§4§l⚠ §cᴇʟɪᴍɪɴᴀʀ ʀᴇɢɪóɴ",   Txt.THIN, "§7/ps forceremove <id>"));
        inv.setItem(23, makeItem(Material.PAPER,                 "§e§l📊 §fᴇꜱᴛᴀᴅíꜱᴛɪᴄᴀꜱ",       Txt.THIN, "§7Regiones: §f" + total, "§7Raideables: §c" + raidable));
        inv.setItem(24, makeItem(Material.FURNACE,               "§6§l⚙ §fᴅᴀʀ ꜰáʙʀɪᴄᴀ",        Txt.THIN, "§6▶ Click → nombre en chat"));
        inv.setItem(25, makeItem(Material.REPEATING_COMMAND_BLOCK,"§c§l🔧 §fʀᴇʟᴏᴀᴅ",            Txt.THIN, "§7Recarga regiones/hologramas."));

        inv.setItem(28, makeItem(Material.ENDER_PEARL,           "§d§l⬡ §fᴛᴘ ᴀ ʀᴇɢɪóɴ",        Txt.THIN, "§d▶ Click → escribe ID/nombre"));
        inv.setItem(30, makeItem(Material.CLOCK,                 "§c§l⟳ §fʀᴇᴄᴀʟᴄᴜʟᴀʀ ᴅᴛʀ",    Txt.THIN, "§c▶ Click para ejecutar"));
        inv.setItem(32, makeItem(Material.WATER_BUCKET,          "§4§l⚡ §cʀᴇꜱᴇᴛ ʀᴀɪᴅᴇᴀʙʟᴇꜱ",  Txt.THIN, "§4▶ Click para ejecutar"));
        inv.setItem(34, makeItem(Material.BELL,                  "§e§l📢 §fʙʀᴏᴀᴅᴄᴀꜱᴛ",          Txt.THIN, "§e▶ Click → escribe el mensaje"));

        inv.setItem(37, makeItem(Material.COMMAND_BLOCK, "§e§l💬 §fᴄᴏᴍᴀɴᴅᴏꜱ ᴀᴅᴍɪɴ",
                Txt.THIN, "§f/pp set <jugador> <val>", "§f/ps setlevel <id> <1-7>",
                "§f/ps forceremove <id>", "§f/ps reload"));

        inv.setItem(38, makeItem(Material.PLAYER_HEAD,
                "§a§l👤 §fᴊᴜɢᴀᴅᴏʀᴇꜱ ᴏɴʟɪɴᴇ",
                Txt.THIN,
                "§9ᴏɴʟɪɴᴇ: §f" + Bukkit.getOnlinePlayers().size() + "§8/§f" + Bukkit.getMaxPlayers(),
                Txt.THIN, "§a▶ Click para mostrar en chat"));

        inv.setItem(40, makeItem(Material.NETHER_STAR,
                "§6§l🏆 §fᴛᴏᴘ ʀᴇɢɪᴏɴᴇꜱ",
                Txt.THIN, "§7Top 10 por nivel.", Txt.THIN, "§6▶ Click para mostrar en chat"));

        // Slot 42 — Opciones de Protecciones
        inv.setItem(42, makeItem(Material.COMMAND_BLOCK,
                "§4§l⚙ §cᴏᴘᴄɪᴏɴᴇꜱ ᴅᴇ ᴘʀᴏᴛᴇᴄᴄɪᴏɴᴇꜱ",
                Txt.THIN, "§7PvP, explosiones, home, lock, MOTD, mobs...",
                Txt.THIN, "§c▶ Click para abrir"));

        // Slot 44 — Guerra de Clanes
        boolean warNow = plugin.getWarManager() != null && plugin.getWarManager().isWarActive();
        inv.setItem(44, makeItem(
                warNow ? Material.FIRE_CHARGE : Material.NETHERITE_SWORD,
                warNow ? "§c§l⚔ §f§lGUERRA §c§lACTIVA" : "§8§l⚔ §fGUERRA DE CLANES",
                Txt.THIN,
                warNow ? "§9ᴛɪᴇᴍᴘᴏ: §e" + com.ryxmc.protection.war.WarManager.formatDuration(
                        plugin.getWarManager().getRemainingMs()) : "§7Sin guerra activa.",
                Txt.THIN, "§e▶ Click para gestionar"));

        inv.setItem(49, makeItem(Material.BARRIER, "§c✖ §fᴄᴇʀʀᴀʀ"));
    }

    private void buildAdminRegionsGui(Inventory inv, Player p, int page) {
        border(inv);
        List<RegionStore.RegionRecord> regions = new ArrayList<>(plugin.getRegionStore().getAll());
        int perPage = 28;
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
        int start = page * perPage, end = Math.min(start + perPage, regions.size()), si = 0;
        for (int i = start; i < end && si < slots.length; i++, si++) {
            RegionStore.RegionRecord r = regions.get(i);
            DTRData dtr = plugin.getDtrManager().get(r.regionId);
            int lvl = plugin.getUpgradeManager().getLevel(r.regionId);
            inv.setItem(slots[si], makeItem(
                    dtr.isRaidable() ? Material.REDSTONE_BLOCK : Material.SHIELD,
                    (dtr.isRaidable() ? "§4§l⚠ " : "§a")
                            + plugin.getRegionStore().getDisplayName(r.regionId),
                    Txt.THIN, "§9ɪᴅ: §f" + r.regionId, "§9ᴅᴜᴇñᴏ: §f" + r.ownerName,
                    "§9ɴɪᴠᴇʟ: §f" + com.ryxmc.protection.config.ProtectionBlock.getName(lvl),
                    "§9ᴅᴛʀ: " + dtr.formatted(),
                    Txt.THIN, "§7Click para gestionar."));
        }
        inv.setItem(4, makeItem(Material.PAPER,
                "§e§l📋 §fʀᴇɢɪᴏɴᴇꜱ §8(pág." + (page+1) + ")",
                Txt.THIN, "§9ᴛᴏᴛᴀʟ: §f" + regions.size()));
        if (page > 0)          inv.setItem(45, makeItem(Material.ARROW, "§7« ᴀɴᴛᴇʀɪᴏʀ"));
        if (end < regions.size()) inv.setItem(53, makeItem(Material.ARROW, "§7ꜱɪɢᴜɪᴇɴᴛᴇ »"));
        inv.setItem(49, makeItem(Material.ARROW, "§7« §fᴠᴏʟᴠᴇʀ"));
    }

    private void buildAdminDtrGui(Inventory inv, Player p, int page) {
        border(inv);
        List<Map.Entry<String, DTRData>> entries = new ArrayList<>(
                plugin.getDtrManager().getAll().entrySet());
        entries.sort(Comparator.comparingDouble(e -> e.getValue().getDtr()));
        int perPage = 28;
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
        int start = page * perPage, end = Math.min(start + perPage, entries.size()), si = 0;
        for (int i = start; i < end && si < slots.length; i++, si++) {
            var e = entries.get(i); var dtr = e.getValue();
            String name = plugin.getRegionStore().getDisplayName(e.getKey());
            inv.setItem(slots[si], makeItem(
                    dtr.isRaidable() ? Material.REDSTONE : Material.EMERALD,
                    (dtr.isRaidable() ? "§4§l⚠ " : "") + name,
                    Txt.THIN, "§9ᴅᴛʀ: " + dtr.formatted(),
                    "§9ᴍáx: §f" + String.format("%.1f", dtr.getMaxDtr()),
                    dtr.isRaidable() ? "§4§lʀᴀɪᴅᴇᴀʙʟᴇ" : "§aɴᴏʀᴍᴀʟ",
                    Txt.THIN, "§7Click der = reset ᴅᴛʀ"));
        }
        inv.setItem(4, makeItem(Material.REDSTONE, "§c§l⚡ §fɢᴇꜱᴛɪóɴ ᴅᴛʀ §8(pág." + (page+1) + ")",
                Txt.THIN, "§9ᴛᴏᴛᴀʟ: §f" + entries.size(),
                "§9ʀᴀɪᴅᴇᴀʙʟᴇꜱ: §c" + entries.stream().filter(e->e.getValue().isRaidable()).count()));
        if (page > 0)         inv.setItem(45, makeItem(Material.ARROW, "§7« ᴀɴᴛᴇʀɪᴏʀ"));
        if (end < entries.size()) inv.setItem(53, makeItem(Material.ARROW, "§7ꜱɪɢᴜɪᴇɴᴛᴇ »"));
        inv.setItem(49, makeItem(Material.ARROW, "§7« §fᴠᴏʟᴠᴇʀ"));
    }

    // ── Helpers ───────────────────────────────────────

    // ─────────────────────────────────────────────────
    //  Panel de Opciones de Protecciones
    // ─────────────────────────────────────────────────

    public void openProtectionOptionsGui(Player p) {
        if (!p.hasPermission("ryx.admin")) { p.sendMessage(com.ryxmc.protection.util.Txt.err("Sin permiso.")); return; }
        RyxHolder h = new RyxHolder(GuiType.PROTECTION_OPTIONS, p.getUniqueId(), null);
        Inventory inv = Bukkit.createInventory(h, 54, Color.of("§4⚙ §c§lᴏᴘᴄɪᴏɴᴇꜱ ᴅᴇ ᴘʀᴏᴛᴇᴄᴄɪᴏɴᴇꜱ"));
        h.setInventory(inv);
        buildProtectionOptionsGui(inv, p);
        register(p.getUniqueId(), h);
        p.openInventory(inv);
    }

    private void buildProtectionOptionsGui(Inventory inv, Player p) {
        border(inv);
        java.util.function.Function<String,Boolean> cfg = key -> plugin.getConfig().getBoolean(key,true);
        inv.setItem(4, makeItem(Material.COMMAND_BLOCK,"§4§l⚙ §c§lᴏᴘᴄɪᴏɴᴇꜱ ɢʟᴏʙᴀʟᴇꜱ",Txt.THIN,"§7Activa/desactiva funciones en tiempo real."));
        boolean pvp=cfg.apply("protection.pvp_enabled");
        inv.setItem(10,makeItem(pvp?Material.DIAMOND_SWORD:Material.WOODEN_SWORD,(pvp?"§a":"§c")+"§l⚔ ᴘᴠᴘ ɢʟᴏʙᴀʟ",Txt.THIN,"§9ᴇꜱᴛᴀᴅᴏ: "+(pvp?"§a§lACTIVADO":"§c§lDESACTIVADO"),Txt.THIN,"§7▶ Click para cambiar"));
        boolean pvpT=cfg.apply("protection.allow_pvp_toggle");
        inv.setItem(11,makeItem(pvpT?Material.IRON_SWORD:Material.BARRIER,(pvpT?"§a":"§c")+"§l⚔ §fᴛᴏɢɢʟᴇ ᴘᴠᴘ",Txt.THIN,"§9ᴇꜱᴛᴀᴅᴏ: "+(pvpT?"§a§lHABILITADO":"§c§lDESHABILITADO"),Txt.THIN,"§7▶ Click para cambiar"));
        boolean bExp=cfg.apply("protection.block_explosions");
        inv.setItem(12,makeItem(bExp?Material.SHIELD:Material.TNT,(bExp?"§6":"§c")+"§l💥 §fᴇxᴘʟᴏꜱɪᴏɴᴇꜱ",Txt.THIN,"§9ᴇꜱᴛᴀᴅᴏ: "+(bExp?"§a§lBLOQUEADAS":"§c§lPERMITIDAS"),Txt.THIN,"§7▶ Click para cambiar"));
        boolean prev=cfg.apply("protection.preview_on_place");
        inv.setItem(14,makeItem(prev?Material.ENDER_EYE:Material.BARRIER,(prev?"§b":"§7")+"§l👁 §fᴘʀᴇᴠɪᴇᴡ",Txt.THIN,"§9ᴇꜱᴛᴀᴅᴏ: "+(prev?"§a§lACTIVADO":"§c§lDESACTIVADO"),Txt.THIN,"§7▶ Click para cambiar"));
        boolean home=cfg.apply("protection.allow_home");
        inv.setItem(16,makeItem(home?Material.WHITE_BED:Material.BARRIER,(home?"§a":"§c")+"§l⌂ §f/ps home",Txt.THIN,"§9ᴇꜱᴛᴀᴅᴏ: "+(home?"§a§lHABILITADO":"§c§lDESHABILITADO"),Txt.THIN,"§7▶ Click para cambiar"));
        boolean unc=cfg.apply("protection.allow_unclaim");
        inv.setItem(19,makeItem(unc?Material.TNT:Material.BARRIER,(unc?"§c":"§7")+"§l✖ §f/ps unclaim",Txt.THIN,"§9ᴇꜱᴛᴀᴅᴏ: "+(unc?"§a§lHABILITADO":"§c§lDESHABILITADO"),Txt.THIN,"§7▶ Click para cambiar"));
        boolean lck=cfg.apply("region.allow_lock");
        inv.setItem(21,makeItem(lck?Material.IRON_DOOR:Material.BARRIER,(lck?"§c":"§7")+"§l🔒 §fʟᴏᴄᴋ",Txt.THIN,"§9ᴇꜱᴛᴀᴅᴏ: "+(lck?"§a§lHABILITADO":"§c§lDESHABILITADO"),Txt.THIN,"§7▶ Click para cambiar"));
        boolean mem=cfg.apply("protection.allow_members");
        inv.setItem(22,makeItem(mem?Material.PLAYER_HEAD:Material.BARRIER,(mem?"§b":"§7")+"§l👥 §fᴍɪᴇᴍʙʀᴏꜱ",Txt.THIN,"§9ᴇꜱᴛᴀᴅᴏ: "+(mem?"§a§lHABILITADA":"§c§lDESHABILITADA"),Txt.THIN,"§7▶ Click para cambiar"));
        boolean motd=cfg.apply("region.allow_motd");
        inv.setItem(23,makeItem(motd?Material.WRITABLE_BOOK:Material.BARRIER,(motd?"§e":"§7")+"§l✉ §fᴍᴏᴛᴅ",Txt.THIN,"§9ᴇꜱᴛᴀᴅᴏ: "+(motd?"§a§lHABILITADO":"§c§lDESHABILITADO"),Txt.THIN,"§7▶ Click para cambiar"));
        boolean bl=cfg.apply("protection.allow_blacklist");
        inv.setItem(24,makeItem(bl?Material.STRUCTURE_VOID:Material.BARRIER,(bl?"§4":"§7")+"§l☠ §fʙʟᴀᴄᴋʟɪꜱᴛ",Txt.THIN,"§9ᴇꜱᴛᴀᴅᴏ: "+(bl?"§a§lHABILITADA":"§c§lDESHABILITADA"),Txt.THIN,"§7▶ Click para cambiar"));
        boolean mobs=cfg.apply("region.allow_mob_toggle");
        inv.setItem(25,makeItem(mobs?Material.ZOMBIE_HEAD:Material.BARRIER,(mobs?"§a":"§7")+"§l🐾 §fᴍᴏʙꜱ",Txt.THIN,"§9ᴇꜱᴛᴀᴅᴏ: "+(mobs?"§a§lHABILITADO":"§c§lDESHABILITADO"),Txt.THIN,"§7▶ Click para cambiar"));
        boolean tr=cfg.apply("region.allow_transfer");
        inv.setItem(28,makeItem(tr?Material.GOLD_INGOT:Material.BARRIER,(tr?"§6":"§7")+"§l⇄ §fᴛʀᴀɴꜱꜰᴇʀ",Txt.THIN,"§9ᴇꜱᴛᴀᴅᴏ: "+(tr?"§a§lHABILITADA":"§c§lDESHABILITADA"),Txt.THIN,"§7▶ Click para cambiar"));
        boolean upg=cfg.apply("protection.allow_upgrade");
        inv.setItem(29,makeItem(upg?Material.ANVIL:Material.BARRIER,(upg?"§b":"§7")+"§l⬆ §fᴍᴇᴊᴏʀᴀꜱ",Txt.THIN,"§9ᴇꜱᴛᴀᴅᴏ: "+(upg?"§a§lHABILITADAS":"§c§lDESHABILITADAS"),Txt.THIN,"§7▶ Click para cambiar"));
        boolean bRaid=cfg.apply("dtr.broadcast_raidable");
        inv.setItem(30,makeItem(bRaid?Material.BELL:Material.BARRIER,(bRaid?"§c":"§7")+"§l📢 §fʀᴀɪᴅᴇᴀʙʟᴇ",Txt.THIN,"§9ᴇꜱᴛᴀᴅᴏ: "+(bRaid?"§a§lACTIVADO":"§c§lDESACTIVADO"),Txt.THIN,"§7▶ Click para cambiar"));
        boolean shop=cfg.apply("protection.allow_shop");
        inv.setItem(31,makeItem(shop?Material.GOLD_BLOCK:Material.BARRIER,(shop?"§6":"§7")+"§l🛒 §fᴛɪᴇɴᴅᴀ",Txt.THIN,"§9ᴇꜱᴛᴀᴅᴏ: "+(shop?"§a§lHABILITADA":"§c§lDESHABILITADA"),Txt.THIN,"§7▶ Click para cambiar"));
        boolean bRest=cfg.apply("dtr.broadcast_restored");
        inv.setItem(32,makeItem(bRest?Material.BELL:Material.BARRIER,(bRest?"§a":"§7")+"§l📢 §fʀᴇꜱᴛᴀᴜʀᴀᴅᴀ",Txt.THIN,"§9ᴇꜱᴛᴀᴅᴏ: "+(bRest?"§a§lACTIVADO":"§c§lDESACTIVADO"),Txt.THIN,"§7▶ Click para cambiar"));
        boolean ene=cfg.apply("protection.allow_energy");
        inv.setItem(33,makeItem(ene?Material.AMETHYST_SHARD:Material.BARRIER,(ene?"§5":"§7")+"§l💎 §fᴇɴᴇʀɢíᴀ",Txt.THIN,"§9ᴇꜱᴛᴀᴅᴏ: "+(ene?"§a§lHABILITADO":"§c§lDESHABILITADO"),Txt.THIN,"§7▶ Click para cambiar"));
        boolean fac=cfg.apply("protection.allow_factory");
        inv.setItem(37,makeItem(fac?Material.BLAST_FURNACE:Material.BARRIER,(fac?"§6":"§7")+"§l⚙ §fꜰáʙʀɪᴄᴀꜱ",Txt.THIN,"§9ᴇꜱᴛᴀᴅᴏ: "+(fac?"§a§lHABILITADO":"§c§lDESHABILITADO"),Txt.THIN,"§7▶ Click para cambiar"));
        boolean comp=cfg.apply("protection.allow_compressor");
        inv.setItem(38,makeItem(comp?Material.CRAFTER:Material.BARRIER,(comp?"§5":"§7")+"§l⚗ §fᴄᴏᴍᴘʀᴇꜱᴏʀ",Txt.THIN,"§9ᴇꜱᴛᴀᴅᴏ: "+(comp?"§a§lHABILITADO":"§c§lDESHABILITADO"),Txt.THIN,"§7▶ Click para cambiar"));
        boolean dbg=plugin.getConfig().getBoolean("plugin.debug",false);
        inv.setItem(42,makeItem(dbg?Material.REDSTONE:Material.GRAY_DYE,(dbg?"§c":"§7")+"§l🔧 §fᴅᴇʙᴜɢ",Txt.THIN,"§9ᴇꜱᴛᴀᴅᴏ: "+(dbg?"§a§lACTIVADO":"§c§lDESACTIVADO"),Txt.THIN,"§7▶ Click para cambiar"));
        inv.setItem(46,makeItem(Material.ARROW,"§7§l← §fʀᴇɢʀᴇꜱᴀʀ",Txt.THIN,"§7Volver al Admin Panel."));
        inv.setItem(49,makeItem(Material.BARRIER,"§c✖ §fᴄᴇʀʀᴀʀ"));
    }

    // ─────────────────────────────────────────────────
    //  GUI de Guerra de Clanes
    // ─────────────────────────────────────────────────

    public void openWarGui(Player p) {
        if (!p.hasPermission("ryx.admin")) { p.sendMessage(com.ryxmc.protection.util.Txt.err("Sin permiso.")); return; }
        RyxHolder h = new RyxHolder(GuiType.WAR_EVENT, p.getUniqueId(), null);
        Inventory inv = Bukkit.createInventory(h, 54, Color.of("§c⚔ §f§lGUERRA DE CLANES §c⚔"));
        h.setInventory(inv);
        com.ryxmc.protection.gui.menus.WarMenu.build(inv, p, plugin);
        register(p.getUniqueId(), h);
        p.openInventory(inv);
    }

    public boolean isPluginTitle(String title) {
        if (title == null) return false;
        return title.contains("⬛") || title.contains("ᴄᴏɴꜰɪɢᴜʀᴀᴄɪóɴ")
                || title.contains("ᴍɪᴇᴍʙʀᴏꜱ") || title.contains("ɢᴇᴍᴀꜱ")
                || title.contains("ᴍᴇᴊᴏʀᴀꜱ") || title.contains("ᴛɪᴇɴᴅᴀ")
                || title.contains("ᴀᴅᴍɪɴ") || title.contains("ᴇʟɪᴍɪɴᴀᴄɪóɴ")
                || title.contains("Regiones") || title.contains("ᴅᴛʀ");
    }

    private void border(Inventory inv) {
        int size = inv.getSize(), rows = size / 9;
        ItemStack gray = pane(Material.GRAY_STAINED_GLASS_PANE), black = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < size; i++) {
            int row = i/9, col = i%9;
            if (row == 0 || row == rows-1 || col == 0 || col == 8) inv.setItem(i, gray);
            else inv.setItem(i, black);
        }
    }

    private void fill(Inventory inv, int size, ItemStack item) {
        for (int i = 0; i < size; i++) inv.setItem(i, item);
    }

    private ItemStack pane(Material mat) {
        ItemStack i = new ItemStack(mat); ItemMeta m = i.getItemMeta();
        if (m != null) { m.setDisplayName(" "); i.setItemMeta(m); } return i;
    }

    public ItemStack makeItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat); ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(Color.of(name));
        List<String> l = new ArrayList<>();
        for (String s : lore) if (s != null) l.add(Color.of(s));
        meta.setLore(l);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta); return item;
    }

    private ItemStack makeItemWithList(Material mat, String name, List<String> lore) {
        return makeItem(mat, name, lore.toArray(new String[0]));
    }

    private ItemStack buildSkull(OfflinePlayer op, ProtectionRole role) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) skull.getItemMeta(); if (sm == null) return skull;
        sm.setOwningPlayer(op);
        String name = op.getName() != null ? op.getName() : "Desconocido";
        sm.setDisplayName(Color.of(role.color() + role.tag() + " §f" + Txt.sc(name)));
        sm.setLore(Arrays.asList(
                Color.of(Txt.THIN),
                Color.of("§9ʀᴏʟ: §f" + role.display()),
                Color.of("§9ᴇꜱᴛᴀᴅᴏ: " + (op.isOnline() ? "§a● ᴏɴʟɪɴᴇ" : "§7● ᴏꜰꜰʟɪɴᴇ")),
                Color.of(Txt.THIN),
                Color.of(role != ProtectionRole.OWNER ? "§7Click para cambiar rol / expulsar."
                        : "§7Propietario de la región.")));
        skull.setItemMeta(sm); return skull;
    }
}
