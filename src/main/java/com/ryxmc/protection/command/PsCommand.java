package com.ryxmc.protection.command;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.dtr.DTRData;
import com.ryxmc.protection.manager.RegionStore;
import com.ryxmc.protection.model.ProtectionRole;
import com.ryxmc.protection.util.Color;
import com.ryxmc.protection.util.Txt;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * PsCommand v3.2
 *
 * ── FIX CRÍTICO v3.2 — PvP global vs por región ────────────────────────────
 *  CAUSA: cmdPvp() permitía togglear el PvP por región sin verificar el
 *  toggle GLOBAL del admin (protection.pvp_enabled, configurado desde el
 *  Panel de Opciones de Protecciones). Esto generaba confusión: el admin
 *  activaba PvP global y veía "ACTIVADO" en su panel, pero los jugadores
 *  no podían activar PvP en su propia región porque el mensaje de error
 *  era ambiguo o el toggle no tenía efecto visible.
 *
 *  FIX: cmdPvp() ahora:
 *   1. Verifica protection.pvp_enabled (global) PRIMERO.
 *      Si está desactivado → mensaje claro: "el admin lo desactivó
 *      globalmente, no puedes activarlo en tu región."
 *   2. Si el global está activo → permite el toggle normal por región.
 *
 * ── Restaurados en v3.2 (estaban en versiones anteriores, no en este
 *    archivo base) ──────────────────────────────────────────────────────────
 *  transfer, lock, motd, mobs, members, anims, energy (usuario)
 *  setmotd, setlock, setmobs, transferforce, online, toplevel,
 *  resetraidable, admindtr, adminregions (admin)
 */
public class PsCommand implements CommandExecutor, TabCompleter {

    private final RyxProtection plugin;

    private static final List<String> USER_SUBS = List.of(
            "help", "info", "home", "list", "get", "name", "add", "remove",
            "addowner", "removeowner", "pvp", "blacklist", "unclaim", "count",
            "dtr", "settings", "pp",
            "upgrade", "preview", "setname", "top", "tienda", "fabrica",
            "transfer", "lock", "motd", "mobs", "members", "anims", "energy"
    );

    private static final List<String> ADMIN_SUBS = List.of(
            "admin", "setdtr", "resetdtr", "forceremove", "setpp", "addpp",
            "give", "givefactory", "givecompressor", "reload", "setlevel", "bypass", "expandir",
            "setmotd", "setlock", "setmobs", "transferforce",
            "online", "toplevel", "resetraidable", "admindtr", "adminregions"
    );

    public PsCommand(RyxProtection plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String lbl, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { sendHelp(sender); return true; }

        String sub = args[0].toLowerCase();
        if (!(sender instanceof Player) && !isConsoleOk(sub)) {
            sender.sendMessage(Txt.err("Este comando requiere ser jugador.")); return true;
        }
        Player p = sender instanceof Player pl ? pl : null;

        switch (sub) {
            // ── Usuario ────────────────────────────────
            case "info"         -> cmdInfo(p);
            case "settings"     -> cmdSettings(p, args);
            case "home"         -> cmdHome(p, args);
            case "list"         -> cmdList(p, args);
            case "get"          -> cmdGet(p, args);
            case "name"         -> cmdName(p, args);
            case "add"          -> cmdAddRemove(p, args, false, false);
            case "remove"       -> cmdAddRemove(p, args, true, false);
            case "addowner"     -> cmdAddRemove(p, args, false, true);
            case "removeowner"  -> cmdAddRemove(p, args, true, true);
            case "pvp"          -> cmdPvp(p, args);
            case "blacklist"    -> cmdBlacklist(p, args);
            case "unclaim"      -> cmdUnclaim(p);
            case "count"        -> cmdCount(p, args);
            case "dtr"          -> cmdDtr(p, args);
            case "pp"           -> cmdPp(p);
            case "upgrade"      -> cmdUpgrade(p, args);
            case "preview"      -> cmdPreview(p, args);
            case "setname"      -> cmdSetName(p, args);
            case "top"          -> cmdTop(sender, args);
            case "tienda", "shop"            -> cmdTienda(p);
            case "fabrica", "factory", "fab" -> cmdFabrica(p);
            // v3.0 usuario
            case "transfer"     -> cmdTransfer(p, args);
            case "lock"         -> cmdLock(p, args);
            case "motd"         -> cmdMotd(p, args);
            case "mobs"         -> cmdMobs(p, args);
            case "members"      -> cmdMembers(p, args);
            case "anims"        -> cmdAnims(p, args);
            case "energy"       -> cmdEnergy(p, args);
            // ── Admin ──────────────────────────────────
            case "admin"        -> cmdAdminGui(p);
            case "setdtr"       -> cmdSetDtr(sender, args);
            case "resetdtr"     -> cmdResetDtr(sender, args);
            case "forceremove"  -> cmdForceRemove(sender, args);
            case "setpp"        -> cmdSetPP(sender, args);
            case "addpp"        -> cmdAddPP(sender, args);
            case "give"         -> cmdGive(sender, args);
            case "givefactory"  -> cmdGiveFactory(sender, args);
            case "givecompressor", "givecomp" -> cmdGiveCompressor(sender, args);
            case "setlevel"     -> cmdSetLevel(sender, args);
            case "reload"       -> cmdReload(sender);
            case "expandir", "expand" -> cmdExpandir(sender, args);
            case "bypass"       -> cmdBypass(sender, p);
            // v3.0 admin
            case "setmotd"      -> cmdSetMotd(sender, args);
            case "setlock"      -> cmdSetLock(sender, args);
            case "setmobs"      -> cmdSetMobs(sender, args);
            case "transferforce"-> cmdTransferForce(sender, args);
            case "online"       -> cmdOnline(sender);
            case "toplevel"     -> cmdTopLevel(sender, args);
            case "resetraidable"-> cmdResetRaidable(sender);
            case "admindtr"     -> cmdAdminDtr(p, args);
            case "adminregions" -> cmdAdminRegions(p, args);

            default -> sender.sendMessage(Txt.err("Subcomando desconocido. Usa §f/ps help§c."));
        }
        return true;
    }

    // ─────────────────────────────────────────────────
    //  Subcomandos de usuario (base)
    // ─────────────────────────────────────────────────

    private void cmdInfo(Player p) {
        RegionStore.RegionRecord r = plugin.getRegionStore().getAt(p.getLocation());
        if (r == null) { p.sendMessage(Txt.err("No estás dentro de ninguna región protegida.")); return; }
        plugin.getGuiManager().openInfoGui(p, r.regionId);
    }

    private void cmdSettings(Player p, String[] args) {
        RegionStore.RegionRecord r = args.length > 1
                ? plugin.getRegionStore().getRecord(args[1])
                : plugin.getRegionStore().getAt(p.getLocation());
        if (r == null) { p.sendMessage(Txt.err("Región no encontrada.")); return; }
        plugin.getGuiManager().openSettingsGui(p, r.regionId);
    }

    private void cmdHome(Player p, String[] args) {
        // [FIX] Check toggle global — protection.allow_home
        if (!plugin.getConfig().getBoolean("protection.allow_home", true) && p != null && !p.hasPermission("ryx.admin")) {
            p.sendMessage(com.ryxmc.protection.util.Txt.err("§a⌂ §f/ps home §7está §cdeshabilitado §7por el administrador."));
            return;
        }
        if (!p.hasPermission("ryx.protection.home")) { p.sendMessage(Txt.err("Sin permiso.")); return; }
        List<String> owned = plugin.getRegionStore().getOwnedByPlayer(p.getUniqueId());
        if (owned.isEmpty()) { p.sendMessage(Txt.err("No tienes regiones.")); return; }
        String targetId = args.length > 1 ? args[1] : owned.get(0);
        RegionStore.RegionRecord r = plugin.getRegionStore().getRecord(targetId);
        if (r == null || r.blockLoc == null) { p.sendMessage(Txt.err("Región o home no encontrado.")); return; }
        p.teleport(r.blockLoc.clone().add(0.5, 1, 0.5));
        p.sendMessage(Txt.ok("Teletransportado a §e" + plugin.getRegionStore().getDisplayName(targetId)));
    }

    private void cmdList(Player p, String[] args) {
        List<String> regions = plugin.getRegionStore().getRegionsByPlayer(p.getUniqueId());
        p.sendMessage(Txt.SEP);
        p.sendMessage("  §6§l✦ §eTus Regiones §8(" + regions.size() + ")");
        p.sendMessage(Txt.SEP);
        if (regions.isEmpty()) { p.sendMessage(Txt.warn("No tienes ninguna región.")); return; }
        for (String rid : regions) {
            String name = plugin.getRegionStore().getDisplayName(rid);
            String dtr  = plugin.getDtrManager().format(rid);
            int    lvl  = plugin.getUpgradeManager().getLevel(rid);
            ProtectionRole role = plugin.getRegionStore().getRole(rid, p.getUniqueId());
            String roleTag = role != null ? role.tag() : ProtectionRole.OWNER.tag();
            p.sendMessage("§7• " + roleTag + " §e" + name + " §8[§7Nv." + lvl + "§8] §8| §9DTR: " + dtr);
        }
        p.sendMessage(Txt.SEP);
    }

    private void cmdGet(Player p, String[] args) {
        if (!p.hasPermission("ryx.protection.use")) { p.sendMessage(Txt.err("Sin permiso.")); return; }
        plugin.getGuiManager().openShopGui(p);
    }

    private void cmdName(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(Txt.err("Uso: §f/ps name <nuevo nombre>")); return; }
        RegionStore.RegionRecord r = plugin.getRegionStore().getAt(p.getLocation());
        if (r == null) { p.sendMessage(Txt.err("No estás en ninguna región propia.")); return; }
        if (!r.ownerUUID.equals(p.getUniqueId()) && !p.hasPermission("ryx.admin")) {
            p.sendMessage(Txt.err("Solo el dueño puede renombrar la región.")); return;
        }
        if (!p.hasPermission("ryx.protection.rename")) { p.sendMessage(Txt.err("Sin permiso.")); return; }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) { if (i > 1) sb.append(" "); sb.append(args[i]); }
        String name = sb.toString();
        plugin.getRegionStore().setCustomName(r.regionId, name.equalsIgnoreCase("none") ? null : name);
        plugin.getHologramManager().updateRegionHologram(r.regionId);
        p.sendMessage(Txt.ok("Región renombrada a §e" + name));
    }

    private void cmdAddRemove(Player p, String[] args, boolean remove, boolean isOwner) {
        if (args.length < 2) {
            p.sendMessage(Txt.err("Uso: §f/ps " + (remove ? "remove" : "add") + " <jugador>")); return;
        }
        RegionStore.RegionRecord r = plugin.getRegionStore().getAt(p.getLocation());
        if (r == null) { p.sendMessage(Txt.err("No estás dentro de tu región.")); return; }
        if (!r.ownerUUID.equals(p.getUniqueId()) && !p.hasPermission("ryx.admin")) {
            p.sendMessage(Txt.err("Solo el dueño puede gestionar miembros.")); return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        World world = p.getWorld();
        if (remove) {
            boolean ok = plugin.getRegionStore().removeMember(r.regionId, target.getUniqueId(), world);
            p.sendMessage(ok ? Txt.ok("§e" + args[1] + "§a removido.") : Txt.err("No se pudo remover."));
        } else {
            boolean ok = plugin.getRegionStore().addMember(r.regionId, target.getUniqueId(), world);
            p.sendMessage(ok ? Txt.ok("§e" + args[1] + "§a añadido.") : Txt.err("No se pudo añadir."));
        }
    }

    /**
     * [FIX v3.2] /ps pvp [on|off]
     *
     * Ahora verifica el toggle GLOBAL del admin (protection.pvp_enabled)
     * ANTES de permitir el cambio por región. Antes de este fix, el jugador
     * podía intentar togglear sin saber por qué "no funcionaba" cuando el
     * admin lo había desactivado globalmente — el mensaje no era claro.
     */
    private void cmdPvp(Player p, String[] args) {
        if (!p.hasPermission("ryx.protection.pvp")) { p.sendMessage(Txt.err("Sin permiso.")); return; }

        // [FIX] Verificar el toggle global PRIMERO
        boolean globalPvp = plugin.getConfig().getBoolean("protection.pvp_enabled", true);
        if (!globalPvp) {
            p.sendMessage(Txt.err("§c⚔ ᴘᴠᴘ §7está §cdesactivado §7globalmente por un administrador."));
            p.sendMessage(Txt.warn("§7No puedes activarlo en tu región hasta que un §cadmin §7lo habilite."));
            return;
        }

        RegionStore.RegionRecord r = plugin.getRegionStore().getAt(p.getLocation());
        if (r == null) r = getFirstOwnedRecord(p);
        if (r == null) { p.sendMessage(Txt.err("No estás en tu región ni tienes una propia.")); return; }
        if (!r.ownerUUID.equals(p.getUniqueId()) && !p.hasPermission("ryx.admin")) {
            p.sendMessage(Txt.err("Solo el dueño puede cambiar el PvP.")); return;
        }

        boolean current = plugin.getRegionStore().isPvPEnabled(r.regionId);
        boolean newVal  = args.length > 1 ? args[1].equalsIgnoreCase("on") : !current;
        plugin.getRegionStore().setPvP(r.regionId, newVal);

        p.sendMessage(newVal
                ? Txt.ok("§c⚔ ᴘᴠᴘ §aactivado §aen tu región. §8Los jugadores podrán atacarse dentro.")
                : Txt.ok("§a☮ ᴘᴠᴘ §adesactivado §aen tu región. §8Vuelve a ser zona de paz."));
    }

    private void cmdBlacklist(Player p, String[] args) {
        RegionStore.RegionRecord r = plugin.getRegionStore().getAt(p.getLocation());
        if (r == null) { p.sendMessage(Txt.err("No estás en tu región.")); return; }
        if (!r.ownerUUID.equals(p.getUniqueId()) && !p.hasPermission("ryx.admin")) {
            p.sendMessage(Txt.err("Solo el dueño puede gestionar la lista negra.")); return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            var bl = plugin.getRegionStoreExt().getBlacklist(r.regionId);
            p.sendMessage(Txt.SEP);
            p.sendMessage("  §4§l☠ §fʟɪꜱᴛᴀ ɴᴇɢʀᴀ §8— §e"
                    + plugin.getRegionStore().getDisplayName(r.regionId));
            p.sendMessage(Txt.SEP);
            if (bl.isEmpty()) {
                p.sendMessage("§7  §o(Lista vacía)");
            } else {
                int idx = 1;
                for (UUID uuid : bl) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                    String n = op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
                    p.sendMessage("§c" + idx++ + ". §f" + n
                            + (op.isOnline() ? " §a● Online" : " §7● Offline"));
                }
            }
            p.sendMessage(Txt.SEP);
            return;
        }
        if (args.length < 3) {
            p.sendMessage(Txt.err("Uso: §f/ps blacklist <add|remove|list> [jugador]")); return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        switch (args[1].toLowerCase()) {
            case "add"    -> { plugin.getRegionStoreExt().addBlacklist(r.regionId, target.getUniqueId());
                               p.sendMessage(Txt.ok("§e" + args[2] + "§a añadido a §4ʟɪꜱᴛᴀ ɴᴇɢʀᴀ§a.")); }
            case "remove" -> { plugin.getRegionStoreExt().removeBlacklist(r.regionId, target.getUniqueId());
                               p.sendMessage(Txt.ok("§e" + args[2] + "§a quitado de §4ʟɪꜱᴛᴀ ɴᴇɢʀᴀ§a.")); }
            default       -> p.sendMessage(Txt.err("Usa: add|remove|list"));
        }
    }

    private void cmdUnclaim(Player p) {
        // [FIX] Check toggle global — protection.allow_unclaim
        if (!plugin.getConfig().getBoolean("protection.allow_unclaim", true) && p != null && !p.hasPermission("ryx.admin")) {
            p.sendMessage(com.ryxmc.protection.util.Txt.err("§c✖ §f/ps unclaim §7está §cdeshabilitado §7por el administrador."));
            return;
        }
        if (!p.hasPermission("ryx.protection.unclaim")) { p.sendMessage(Txt.err("Sin permiso.")); return; }
        RegionStore.RegionRecord r = plugin.getRegionStore().getAt(p.getLocation());
        if (r == null) { p.sendMessage(Txt.err("No estás en tu región.")); return; }
        if (!r.ownerUUID.equals(p.getUniqueId()) && !p.hasPermission("ryx.admin")) {
            p.sendMessage(Txt.err("Solo el dueño puede eliminar la región.")); return;
        }
        plugin.getGuiManager().openConfirmRemoveGui(p, r.regionId);
    }

    private void cmdCount(Player p, String[] args) {
        int count = plugin.getRegionStore().getOwnedByPlayer(p.getUniqueId()).size();
        p.sendMessage(Txt.info("Tienes §e" + count + "§7 región(es) propias."));
    }

    private void cmdDtr(Player p, String[] args) {
        List<String> regions = plugin.getRegionStore().getRegionsByPlayer(p.getUniqueId());
        if (regions.isEmpty()) { p.sendMessage(Txt.warn("No tienes regiones.")); return; }
        p.sendMessage(Txt.SEP);
        p.sendMessage("  §c§l⚡ §fEstado DTR");
        p.sendMessage(Txt.SEP);
        for (String rid : regions) {
            DTRData d = plugin.getDtrManager().get(rid);
            p.sendMessage("§7▸ §e" + plugin.getRegionStore().getDisplayName(rid)
                    + " §8| §9DTR: " + d.formatted()
                    + (d.isRaidable() ? " §4§l⚠ RAIDEABLE" : ""));
        }
        p.sendMessage(Txt.SEP);
    }

    private void cmdPp(Player p) {
        int pp   = plugin.getUpgradeManager().getPP(p.getUniqueId());
        int gems = plugin.getEnergyManager().countAllGems(p);
        p.sendMessage(Txt.SEP);
        p.sendMessage("  §6§l⭐ §fPP y Gemas");
        p.sendMessage(Txt.SEP);
        p.sendMessage("§9PP: §e" + pp + "  §9Gemas: §d" + gems
                + "  §7(100 gemas = 1 PP)");
        p.sendMessage(Txt.SEP);
    }

    private void cmdUpgrade(Player p, String[] args) {
        if (!p.hasPermission("ryx.upgrade.view")) { p.sendMessage(Txt.err("Sin permiso.")); return; }
        String regionId;
        if (args.length >= 2) {
            regionId = args[1];
        } else {
            List<String> owned = plugin.getRegionStore().getOwnedByPlayer(p.getUniqueId());
            if (owned.isEmpty()) { p.sendMessage(Txt.err("No tienes regiones. Usa §f/ps upgrade <regionId>")); return; }
            if (owned.size() > 1) {
                p.sendMessage(Txt.warn("Especifica: §e/ps upgrade <regionId>"));
                owned.forEach(rid -> p.sendMessage("§8- §e" + rid));
                return;
            }
            regionId = owned.get(0);
        }
        plugin.getGuiManager().openUpgradeGui(p, regionId);
    }

    private void cmdPreview(Player p, String[] args) {
        if (!p.hasPermission("ryx.preview.use")) { p.sendMessage(Txt.err("Sin permiso.")); return; }
        String sub = args.length >= 2 ? args[1].toLowerCase() : "toggle";
        boolean active = plugin.getPreviewManager().hasPreview(p.getUniqueId());
        if ("off".equals(sub) || (!"on".equals(sub) && active)) {
            plugin.getPreviewManager().stopPreview(p.getUniqueId());
            p.sendMessage(Txt.warn("Vista previa §cdesactivada§e."));
        } else {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (!com.ryxmc.protection.config.ProtectionBlock.isProtectionBlock(plugin, hand)) {
                p.sendMessage(Txt.err("Sostén un §ebloque de protección§c para ver la vista previa.")); return;
            }
            int lvl = com.ryxmc.protection.config.ProtectionBlock.getItemLevel(plugin, hand);
            int xr  = com.ryxmc.protection.config.ProtectionBlock.getXRadius(lvl);
            plugin.getPreviewManager().startUpgradePreview(p, xr);
            p.sendMessage(Txt.ok("Vista previa §aactivada§a. Área: §e±" + xr + " §abloques."));
        }
    }

    private void cmdSetName(Player p, String[] args) {
        if (args.length < 3) { p.sendMessage(Txt.err("Uso: §f/ps setname <regionId> <nombre>")); return; }
        RegionStore.RegionRecord r = plugin.getRegionStore().getRecord(args[1]);
        if (r == null) { p.sendMessage(Txt.err("Región §e" + args[1] + "§c no encontrada.")); return; }
        if (!r.ownerUUID.equals(p.getUniqueId()) && !p.hasPermission("ryx.admin")) {
            p.sendMessage(Txt.err("No eres el dueño.")); return;
        }
        if (!p.hasPermission("ryx.protection.rename")) { p.sendMessage(Txt.err("Sin permiso.")); return; }
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < args.length; i++) { if (i > 2) sb.append(" "); sb.append(args[i]); }
        String newName = Color.of(sb.toString());
        if (newName.length() > 32) { p.sendMessage(Txt.err("Máximo 32 caracteres.")); return; }
        plugin.getRegionStore().setCustomName(args[1], newName);
        plugin.getHologramManager().updateRegionHologram(args[1]);
        p.sendMessage(Txt.ok("Nombre de §e" + args[1] + "§a cambiado."));
    }

    private void cmdTop(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length >= 2) { try { page = Math.max(1, Integer.parseInt(args[1])); } catch (Exception ignored) {} }
        List<RegionStore.RegionRecord> sorted = new ArrayList<>(plugin.getRegionStore().getAll());
        sorted.sort((a, b) -> Double.compare(
                plugin.getDtrManager().get(b.regionId).getDtr(),
                plugin.getDtrManager().get(a.regionId).getDtr()));
        int perPage = 10, from = (page-1)*perPage, to = Math.min(from+perPage, sorted.size());
        int pages = (int) Math.ceil(sorted.size() / (double) perPage);
        sender.sendMessage(Color.of("§6§l⚙ §e§lTop Regiones §8(ᴅᴛʀ) §7pág. " + page + "/" + Math.max(1,pages)));
        sender.sendMessage(Color.of(Txt.THIN));
        for (int i = from; i < to; i++) {
            RegionStore.RegionRecord r = sorted.get(i);
            String rank = switch (i) { case 0 -> "§6§l#1"; case 1 -> "§7§l#2"; case 2 -> "§c§l#3"; default -> "§8#"+(i+1); };
            sender.sendMessage(rank + " §f" + plugin.getRegionStore().getDisplayName(r.regionId)
                    + " §8| §9ᴅᴛʀ: §e" + String.format("%.1f", plugin.getDtrManager().get(r.regionId).getDtr())
                    + " §8| §7" + r.ownerName);
        }
        if (pages > 1) sender.sendMessage(Color.of("§8/ps top " + Math.min(page+1,pages) + " §8para más."));
    }

    private void cmdTienda(Player p) {
        if (!plugin.getConfig().getBoolean("protection.allow_shop", true) && !p.hasPermission("ryx.admin")) {
            p.sendMessage(Txt.err("La tienda está desactivada por un admin."));
            return;
        }
        plugin.getGuiManager().openShopGui(p);
    }
    private void cmdFabrica(Player p) {
        if (!plugin.getConfig().getBoolean("protection.allow_factory", true) && !p.hasPermission("ryx.admin")) {
            p.sendMessage(Txt.err("El sistema de fábricas está desactivado por un admin."));
            return;
        }
        plugin.getFactoryShopGui().openShop(p);
    }

    // ─────────────────────────────────────────────────
    //  v3.0 Nuevos usuario
    // ─────────────────────────────────────────────────

    private void cmdTransfer(Player p, String[] args) {
        if (!plugin.getConfig().getBoolean("region.allow_transfer", true) && !p.hasPermission("ryx.admin")) {
            p.sendMessage(Txt.err("Transferencia desactivada por un admin."));
            return;
        }
        if (!p.hasPermission("ryx.protection.transfer")) { p.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 2) { p.sendMessage(Txt.err("Uso: /ps transfer <jugador>")); return; }
        RegionStore.RegionRecord r = plugin.getRegionStore().getAt(p.getLocation());
        if (r == null) r = getFirstOwnedRecord(p);
        if (r == null) { p.sendMessage(Txt.err("No estás en tu región.")); return; }
        if (!r.ownerUUID.equals(p.getUniqueId())) { p.sendMessage(Txt.err("Solo el dueño puede transferir.")); return; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getName() == null) { p.sendMessage(Txt.err("Jugador nunca conectado.")); return; }
        boolean ok = plugin.getRegionStoreExt().transferOwnership(r.regionId, target.getUniqueId(), p);
        if (ok) {
            p.sendMessage(Txt.ok("Región transferida a §f" + target.getName() + "§a."));
            plugin.getHologramManager().updateRegionHologram(r.regionId);
            Player online = Bukkit.getPlayer(target.getUniqueId());
            if (online != null)
                online.sendMessage(Txt.ok("§f" + p.getName() + "§a te transfirió la región."));
        } else {
            p.sendMessage(Txt.err("No se pudo transferir. ¿El jugador ya tiene región?"));
        }
    }

    private void cmdLock(Player p, String[] args) {
        if (!plugin.getConfig().getBoolean("region.allow_lock", true) && !p.hasPermission("ryx.admin")) {
            p.sendMessage(Txt.err("Lock desactivado por un admin."));
            return;
        }
        if (!p.hasPermission("ryx.protection.lock")) { p.sendMessage(Txt.err("Sin permiso.")); return; }
        RegionStore.RegionRecord r = plugin.getRegionStore().getAt(p.getLocation());
        if (r == null) r = getFirstOwnedRecord(p);
        if (r == null) { p.sendMessage(Txt.err("No estás en tu región.")); return; }
        if (!r.ownerUUID.equals(p.getUniqueId()) && !p.hasPermission("ryx.admin")) {
            p.sendMessage(Txt.err("Solo el dueño puede bloquear.")); return;
        }
        boolean current = plugin.getRegionStoreExt().isLocked(r.regionId);
        boolean newVal  = args.length >= 2 ? args[1].equalsIgnoreCase("on") : !current;
        plugin.getRegionStoreExt().setLocked(r.regionId, newVal);
        p.sendMessage(newVal ? Txt.ok("🔒 Región bloqueada.")
                             : Txt.ok("🔓 Región desbloqueada."));
    }

    private void cmdMotd(Player p, String[] args) {
        if (!plugin.getConfig().getBoolean("region.allow_motd", true) && !p.hasPermission("ryx.admin")) {
            p.sendMessage(Txt.err("MOTD desactivado por un admin."));
            return;
        }
        if (!p.hasPermission("ryx.protection.motd")) { p.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 2) { p.sendMessage(Txt.err("Uso: /ps motd <texto|none>")); return; }
        RegionStore.RegionRecord r = plugin.getRegionStore().getAt(p.getLocation());
        if (r == null) r = getFirstOwnedRecord(p);
        if (r == null) { p.sendMessage(Txt.err("No estás en tu región.")); return; }
        if (!r.ownerUUID.equals(p.getUniqueId()) && !p.hasPermission("ryx.admin")) {
            p.sendMessage(Txt.err("Solo el dueño puede cambiar la descripción.")); return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) { if (i > 1) sb.append(" "); sb.append(args[i]); }
        String motd = sb.toString().equalsIgnoreCase("none") ? null : sb.toString().trim();
        if (motd != null && motd.length() > 64) { p.sendMessage(Txt.err("Máx 64 caracteres.")); return; }
        plugin.getRegionStoreExt().setMotd(r.regionId, motd);
        plugin.getHologramManager().updateRegionHologram(r.regionId);
        p.sendMessage(Txt.ok("MOTD: " + (motd == null ? "ninguno" : "\"" + motd + "\"")));
    }

    private void cmdMobs(Player p, String[] args) {
        if (!plugin.getConfig().getBoolean("region.allow_mob_toggle", true) && !p.hasPermission("ryx.admin")) {
            p.sendMessage(Txt.err("Toggle de mobs desactivado por un admin."));
            return;
        }
        if (!p.hasPermission("ryx.protection.mobs")) { p.sendMessage(Txt.err("Sin permiso.")); return; }
        RegionStore.RegionRecord r = plugin.getRegionStore().getAt(p.getLocation());
        if (r == null) r = getFirstOwnedRecord(p);
        if (r == null) { p.sendMessage(Txt.err("No estás en tu región.")); return; }
        if (!r.ownerUUID.equals(p.getUniqueId()) && !p.hasPermission("ryx.admin")) {
            p.sendMessage(Txt.err("Solo el dueño puede cambiar mobs.")); return;
        }
        boolean current = plugin.getRegionStoreExt().isMobSpawningEnabled(r.regionId);
        boolean newVal  = args.length >= 2 ? args[1].equalsIgnoreCase("on") : !current;
        plugin.getRegionStoreExt().setMobSpawning(r.regionId, newVal);
        p.sendMessage(newVal ? Txt.ok("🐾 Mobs activados.")
                             : Txt.ok("🐾 Mobs desactivados."));
    }

    private void cmdMembers(Player p, String[] args) {
        String regionId;
        if (args.length >= 2) { regionId = args[1]; }
        else {
            RegionStore.RegionRecord r = plugin.getRegionStore().getAt(p.getLocation());
            if (r == null) r = getFirstOwnedRecord(p);
            if (r == null) { p.sendMessage(Txt.err("No estás en ninguna región.")); return; }
            regionId = r.regionId;
        }
        if (plugin.getRegionStore().getRecord(regionId) == null) {
            p.sendMessage(Txt.err("Región §f" + regionId + "§c no encontrada.")); return;
        }
        plugin.getGuiManager().openMembersGui(p, regionId);
    }

    private void cmdAnims(Player p, String[] args) {
        boolean currentOn = plugin.getGuiManager().getAnimator().isEnabled(p.getUniqueId());
        if (args.length < 2) {
            boolean nowOn = plugin.getGuiManager().getAnimator().toggle(p.getUniqueId());
            p.sendMessage(nowOn ? Txt.ok("§b✨ ᴀɴɪᴍᴀᴄɪᴏɴᴇꜱ §aactivadas§a.")
                                : Txt.warn("§b✨ ᴀɴɪᴍᴀᴄɪᴏɴᴇꜱ §cdesactivadas§e."));
            return;
        }
        boolean newOn = args[1].equalsIgnoreCase("on");
        if (newOn != currentOn) plugin.getGuiManager().getAnimator().toggle(p.getUniqueId());
        p.sendMessage(newOn ? Txt.ok("§b✨ ᴀɴɪᴍᴀᴄɪᴏɴᴇꜱ §aactivadas§a.")
                            : Txt.warn("§b✨ ᴀɴɪᴍᴀᴄɪᴏɴᴇꜱ §cdesactivadas§e."));
    }

    private void cmdEnergy(Player p, String[] args) {
        // [FIX] Check toggle global — protection.allow_energy
        if (!plugin.getConfig().getBoolean("protection.allow_energy", true) && p != null && !p.hasPermission("ryx.admin")) {
            p.sendMessage(com.ryxmc.protection.util.Txt.err("§5💎 §fEl sistema de energía §7está §cdeshabilitado §7por el administrador."));
            return;
        }
        String regionId;
        if (args.length >= 2) { regionId = args[1]; }
        else {
            RegionStore.RegionRecord r = plugin.getRegionStore().getAt(p.getLocation());
            if (r == null) r = getFirstOwnedRecord(p);
            if (r == null) { p.sendMessage(Txt.err("No estás en ninguna región.")); return; }
            regionId = r.regionId;
        }
        if (plugin.getRegionStore().getRecord(regionId) == null) {
            p.sendMessage(Txt.err("Región §f" + regionId + "§c no encontrada.")); return;
        }
        plugin.getGuiManager().openEnergyGui(p, regionId);
    }

    // ─────────────────────────────────────────────────
    //  Admin (base)
    // ─────────────────────────────────────────────────

    private void cmdAdminGui(Player p) {
        if (p == null || !p.hasPermission("ryx.admin")) { if (p != null) p.sendMessage(Txt.err("Sin permiso.")); return; }
        plugin.getGuiManager().openAdminGui(p);
    }

    private void cmdSetDtr(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin.dtr")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 3) { s.sendMessage(Txt.err("Uso: /ps setdtr <regionId> <valor>")); return; }
        try { plugin.getDtrManager().set(args[1], Double.parseDouble(args[2]));
              s.sendMessage(Txt.ok("ᴅᴛʀ de §f" + args[1] + "§a → §f" + args[2])); }
        catch (NumberFormatException e) { s.sendMessage(Txt.err("Valor inválido.")); }
    }

    private void cmdResetDtr(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin.dtr")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 2) { s.sendMessage(Txt.err("Uso: /ps resetdtr <regionId>")); return; }
        plugin.getDtrManager().reset(args[1]);
        s.sendMessage(Txt.ok("ᴅᴛʀ de §f" + args[1] + "§a restablecido al máximo."));
    }

    private void cmdForceRemove(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin.region")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 2) { s.sendMessage(Txt.err("Uso: /ps forceremove <regionId>")); return; }
        RegionStore.RegionRecord r = plugin.getRegionStore().getRecord(args[1]);
        if (r == null) { s.sendMessage(Txt.err("Región no encontrada.")); return; }
        Player actor = s instanceof Player pl ? pl : Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (actor == null) { s.sendMessage("Necesitas un jugador online."); return; }
        // removeRegion() ya maneja internamente: drop bloque, hologram, DTR registry, ext
        plugin.getRegionStore().removeRegion(args[1], actor);
        plugin.getUpgradeManager().removeRegion(args[1]);
        s.sendMessage(Txt.ok("ʀᴇɢɪóɴ §f" + args[1] + "§a eliminada forzosamente."));
    }

    private void cmdSetPP(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin.pp")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 3) { s.sendMessage(Txt.err("Uso: /ps setpp <jugador> <cantidad>")); return; }
        try { int v = Integer.parseInt(args[2]);
              plugin.getUpgradeManager().setPP(Bukkit.getOfflinePlayer(args[1]).getUniqueId(), v);
              s.sendMessage(Txt.ok("ᴘᴘ de §e" + args[1] + "§a → §f" + v)); }
        catch (NumberFormatException e) { s.sendMessage(Txt.err("Cantidad inválida.")); }
    }

    private void cmdAddPP(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin.pp")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 3) { s.sendMessage(Txt.err("Uso: /ps addpp <jugador> <cantidad>")); return; }
        try { int v = Integer.parseInt(args[2]);
              UUID uuid = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
              plugin.getUpgradeManager().addPP(uuid, v);
              s.sendMessage(Txt.ok("+" + v + " ᴘᴘ a §f" + args[1]));
              Player online = Bukkit.getPlayer(uuid);
              if (online != null) online.sendMessage(Txt.ok("Recibiste §e" + v + " ᴘᴘ§a de §f" + s.getName())); }
        catch (NumberFormatException e) { s.sendMessage(Txt.err("Cantidad inválida.")); }
    }

    private void cmdGive(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 3) { s.sendMessage(Txt.err("Uso: /ps give <nivel> <jugador>")); return; }
        int level;
        try { level = Math.max(1, Math.min(com.ryxmc.protection.config.ProtectionBlock.maxLevel(), Integer.parseInt(args[1]))); }
        catch (NumberFormatException e) { s.sendMessage(Txt.err("Nivel inválido.")); return; }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) { s.sendMessage(Txt.err("Jugador no encontrado.")); return; }
        target.getInventory().addItem(com.ryxmc.protection.config.ProtectionBlock.buildItem(plugin, level));
        s.sendMessage(Txt.ok("Dado ʙʟᴏǫᴜᴇ Nv." + level + " a §f" + target.getName()));
        target.sendMessage(Txt.ok("Recibiste §fʙʟᴏǫᴜᴇ ᴅᴇ ᴘʀᴏᴛᴇᴄᴄɪóɴ Nv." + level + "§a de §f" + s.getName()));
    }

    private void cmdGiveFactory(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin.factory")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 2) { s.sendMessage(Txt.err("Uso: /ps givefactory <jugador>")); return; }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { s.sendMessage(Txt.err("Jugador no encontrado.")); return; }
        target.getInventory().addItem(plugin.getFactoryManager().createFactoryItem());
        s.sendMessage(Txt.ok("ꜰáʙʀɪᴄᴀ dada a §f" + target.getName()));
        target.sendMessage(Txt.ok("Recibiste una §6§lꜰáʙʀɪᴄᴀ ᴅᴇ ᴇɴᴇʀɢíᴀ§a."));
    }

    private void cmdGiveCompressor(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin.factory")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 2) { s.sendMessage(Txt.err("Uso: /ps givecompressor <jugador>")); return; }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { s.sendMessage(Txt.err("Jugador no encontrado.")); return; }
        target.getInventory().addItem(plugin.getCompressorManager().createCompressorItem());
        s.sendMessage(Txt.ok("§5§l⚗ ᴄᴏᴍᴘʀᴇꜱᴏʀ§a dado a §f" + target.getName()));
        target.sendMessage(Txt.ok("Recibiste un §5§l⚗ §d§lᴄᴏᴍᴘʀᴇꜱᴏʀ ᴅᴇ ɢᴇᴍᴀꜱ§a."));
    }

    private void cmdSetLevel(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 3) { s.sendMessage(Txt.err("Uso: /ps setlevel <regionId> <1-7>")); return; }
        try {
            int lvl = Integer.parseInt(args[2]);
            int max = com.ryxmc.protection.config.ProtectionBlock.maxLevel();
            if (lvl < 1 || lvl > max) { s.sendMessage(Txt.err("Nivel entre 1 y " + max + ".")); return; }
            plugin.getUpgradeManager().setLevel(args[1], lvl);
            plugin.getDtrManager().recalcMax(args[1]);
            RegionStore.RegionRecord r = plugin.getRegionStore().getRecord(args[1]);
            if (r != null && r.blockLoc != null)
                r.blockLoc.getBlock().setType(com.ryxmc.protection.config.ProtectionBlock.getMaterial(lvl));
            int newXR = plugin.getUpgradeManager().getXRadius(args[1]);
            plugin.getRegionStore().resizeRegion(args[1], newXR);
            plugin.getHologramManager().updateRegionHologram(args[1]);
            s.sendMessage(Txt.ok("ɴɪᴠᴇʟ §f" + args[1] + "§a → Nv." + lvl + " · ʀᴀᴅɪᴏ ±" + newXR));
        } catch (NumberFormatException e) { s.sendMessage(Txt.err("Nivel inválido.")); }
    }

    private void cmdReload(CommandSender s) {
        if (!s.hasPermission("ryx.admin")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        plugin.getDtrManager().reload();
        plugin.getRegionStore().shutdown(); plugin.getRegionStore().init();
        plugin.getHologramManager().reload();
        plugin.reloadConfig();
        s.sendMessage(Txt.ok("ᴘʟᴜɢɪɴ recargado."));
    }

    private void cmdExpandir(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 2) { s.sendMessage(Txt.err("Uso: §f/ps expandir <regionId>")); return; }
        if (!(s instanceof Player p)) { s.sendMessage(Txt.err("Solo jugadores.")); return; }
        boolean ok = plugin.getBlockUpgradeManager().performUpgrade(p, args[1]);
        if (!ok) s.sendMessage(Txt.err("No se pudo expandir."));
    }

    private void cmdBypass(CommandSender s, Player p) {
        if (!s.hasPermission("ryx.admin.dtr")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        if (p == null) { s.sendMessage(Txt.err("Solo jugadores.")); return; }
        boolean has = p.hasPermission("ryx.dtr.bypass");
        p.sendMessage(has
                ? Txt.info("Tienes §aACTIVO§7 el bypass de ᴅᴛʀ (§fryx.dtr.bypass§7).")
                : Txt.info("No tienes el bypass. Usa §f/lp user " + p.getName() + " permission set ryx.dtr.bypass"));
    }

    // ─────────────────────────────────────────────────
    //  v3.0 Admin
    // ─────────────────────────────────────────────────

    private void cmdSetMotd(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 3) { s.sendMessage(Txt.err("Uso: /ps setmotd <regionId> <texto|none>")); return; }
        if (plugin.getRegionStore().getRecord(args[1]) == null) { s.sendMessage(Txt.err("Región no encontrada.")); return; }
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < args.length; i++) { if (i > 2) sb.append(" "); sb.append(args[i]); }
        String motd = sb.toString().equalsIgnoreCase("none") ? null : sb.toString().trim();
        if (motd != null && motd.length() > 64) { s.sendMessage(Txt.err("Máx 64 caracteres.")); return; }
        plugin.getRegionStoreExt().setMotd(args[1], motd);
        plugin.getHologramManager().updateRegionHologram(args[1]);
        s.sendMessage(Txt.ok("ᴍᴏᴛᴅ §e" + args[1] + "§a → §f" + (motd == null ? "§8(ninguna)" : "\"" + motd + "\"")));
    }

    private void cmdSetLock(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 2) { s.sendMessage(Txt.err("Uso: /ps setlock <regionId> [on|off]")); return; }
        if (plugin.getRegionStore().getRecord(args[1]) == null) { s.sendMessage(Txt.err("Región no encontrada.")); return; }
        boolean current = plugin.getRegionStoreExt().isLocked(args[1]);
        boolean newVal  = args.length >= 3 ? args[2].equalsIgnoreCase("on") : !current;
        plugin.getRegionStoreExt().setLocked(args[1], newVal);
        s.sendMessage(Txt.ok("§e" + args[1] + "§a → " + (newVal ? "§c🔒 ʙʟᴏǫᴜᴇᴀᴅᴀ" : "§a🔓 ᴀʙɪᴇʀᴛᴀ") + "§a."));
    }

    private void cmdSetMobs(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 2) { s.sendMessage(Txt.err("Uso: /ps setmobs <regionId> [on|off]")); return; }
        if (plugin.getRegionStore().getRecord(args[1]) == null) { s.sendMessage(Txt.err("Región no encontrada.")); return; }
        boolean current = plugin.getRegionStoreExt().isMobSpawningEnabled(args[1]);
        boolean newVal  = args.length >= 3 ? args[2].equalsIgnoreCase("on") : !current;
        plugin.getRegionStoreExt().setMobSpawning(args[1], newVal);
        s.sendMessage(Txt.ok("ᴍᴏʙꜱ §e" + args[1] + "§a → " + (newVal ? "§aACTIVADOS" : "§cDESACTIVADOS") + "§a."));
    }

    private void cmdTransferForce(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 3) { s.sendMessage(Txt.err("Uso: /ps transferforce <regionId> <jugador>")); return; }
        if (plugin.getRegionStore().getRecord(args[1]) == null) { s.sendMessage(Txt.err("Región no encontrada.")); return; }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if (target.getName() == null) { s.sendMessage(Txt.err("El jugador §f" + args[2] + "§c nunca se conectó.")); return; }
        Player actor = s instanceof Player pl ? pl : Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (actor == null) { s.sendMessage("Necesitas un jugador online como actor."); return; }
        boolean ok = plugin.getRegionStoreExt().transferOwnership(args[1], target.getUniqueId(), actor);
        if (ok) {
            s.sendMessage(Txt.ok("ᴛʀᴀɴꜱꜰᴇʀᴇɴᴄɪᴀ §e" + args[1] + "§a → §f" + target.getName() + "§a."));
            plugin.getHologramManager().updateRegionHologram(args[1]);
            Player online = Bukkit.getPlayer(target.getUniqueId());
            if (online != null)
                online.sendMessage(Txt.ok("§6§l[Admin] §fUn admin te transfirió la región §e"
                        + plugin.getRegionStore().getDisplayName(args[1]) + "§a."));
        } else {
            s.sendMessage(Txt.err("No se pudo transferir. ¿El jugador ya tiene una región?"));
        }
    }

    private void cmdOnline(CommandSender s) {
        if (!s.hasPermission("ryx.admin")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        s.sendMessage(Txt.SEP);
        s.sendMessage("  §a§l👤 §fᴊᴜɢᴀᴅᴏʀᴇꜱ ᴏɴʟɪɴᴇ §8(" + Bukkit.getOnlinePlayers().size() + ")");
        s.sendMessage(Txt.SEP);
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            s.sendMessage("§7  §oNadie conectado.");
        } else {
            int idx = 1;
            for (Player online : Bukkit.getOnlinePlayers()) {
                RegionStore.RegionRecord reg = plugin.getRegionStore().getAt(online.getLocation());
                String ri = reg != null ? "§8[§e" + plugin.getRegionStore().getDisplayName(reg.regionId) + "§8]" : "§8[§7Sin región§8]";
                s.sendMessage("§9" + idx++ + ". §f" + online.getName() + " §7— §f" + online.getWorld().getName() + " — " + ri);
            }
        }
        s.sendMessage(Txt.SEP);
    }

    private void cmdTopLevel(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        int page = 1;
        if (args.length >= 2) { try { page = Math.max(1, Integer.parseInt(args[1])); } catch (Exception ignored) {} }
        List<RegionStore.RegionRecord> sorted = new ArrayList<>(plugin.getRegionStore().getAll());
        sorted.sort((a, b) -> Integer.compare(plugin.getUpgradeManager().getLevel(b.regionId), plugin.getUpgradeManager().getLevel(a.regionId)));
        int perPage = 10, from = (page-1)*perPage, to = Math.min(from+perPage, sorted.size());
        int pages = (int) Math.ceil(sorted.size() / (double) perPage);
        s.sendMessage(Color.of("§6§l🏆 §e§lTop Regiones §8(ɴɪᴠᴇʟ) pág. " + page + "/" + Math.max(1,pages)));
        s.sendMessage(Color.of(Txt.THIN));
        for (int i = from; i < to; i++) {
            RegionStore.RegionRecord r = sorted.get(i);
            int lvl = plugin.getUpgradeManager().getLevel(r.regionId);
            String medal = switch (i) { case 0 -> "§6§l🥇"; case 1 -> "§7§l🥈"; case 2 -> "§c§l🥉"; default -> "§8#"+(i+1); };
            s.sendMessage(medal + " §f" + plugin.getRegionStore().getDisplayName(r.regionId)
                    + " §8— ɴɪᴠ.§e" + lvl + " §8| §7" + r.ownerName
                    + " §8| " + plugin.getDtrManager().get(r.regionId).formatted());
        }
        if (pages > 1) s.sendMessage(Color.of("§8/ps toplevel " + Math.min(page+1,pages) + " para más."));
    }

    private void cmdResetRaidable(CommandSender s) {
        if (!s.hasPermission("ryx.admin.dtr")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        int count = 0;
        for (RegionStore.RegionRecord r : plugin.getRegionStore().getAll()) {
            if (plugin.getDtrManager().isRaidable(r.regionId)) { plugin.getDtrManager().reset(r.regionId); count++; }
        }
        s.sendMessage(Txt.ok("§f" + count + "§a regiones raideables reseteadas."));
    }

    private void cmdAdminDtr(Player p, String[] args) {
        if (p == null || !p.hasPermission("ryx.admin.dtr")) { if (p != null) p.sendMessage(Txt.err("Sin permiso.")); return; }
        int page = 0;
        if (args.length >= 2) { try { page = Math.max(0, Integer.parseInt(args[1])-1); } catch (Exception ignored) {} }
        plugin.getGuiManager().openAdminDtrGui(p, page);
    }

    private void cmdAdminRegions(Player p, String[] args) {
        if (p == null || !p.hasPermission("ryx.admin")) { if (p != null) p.sendMessage(Txt.err("Sin permiso.")); return; }
        int page = 0;
        if (args.length >= 2) { try { page = Math.max(0, Integer.parseInt(args[1])-1); } catch (Exception ignored) {} }
        plugin.getGuiManager().openAdminRegionsGui(p, page);
    }

    // ─────────────────────────────────────────────────
    //  Help
    // ─────────────────────────────────────────────────

    private void sendHelp(CommandSender s) {
        s.sendMessage(Color.of("§6§l━━━━━━ §e§lʀʏxᴍᴄ-ᴘʀᴏᴛᴇᴄᴛɪᴏɴ §6§l━━━━━━"));
        s.sendMessage(Color.of("§e/ps info        §7— GUI de la región actual"));
        s.sendMessage(Color.of("§e/ps list        §7— Tus regiones y DTR"));
        s.sendMessage(Color.of("§e/ps home §8[id]  §7— Teletransportarte"));
        s.sendMessage(Color.of("§e/ps dtr         §7— Estado DTR"));
        s.sendMessage(Color.of("§e/ps pp          §7— PP y gemas"));
        s.sendMessage(Color.of("§e/ps settings    §7— Configuración GUI"));
        s.sendMessage(Color.of("§e/ps members     §7— Lista de miembros GUI"));
        s.sendMessage(Color.of("§e/ps energy      §7— Gemas/DTR GUI"));
        s.sendMessage(Color.of("§e/ps upgrade     §7— Mejoras GUI"));
        s.sendMessage(Color.of("§e/ps top         §7— Top regiones por DTR"));
        s.sendMessage(Color.of("§e/ps tienda      §7— Tienda de bloques"));
        s.sendMessage(Color.of(Txt.THIN));
        s.sendMessage(Color.of("§b/ps pvp §8[on|off]       §7— ⚔ Toggle PvP §8(requiere global activo)"));
        s.sendMessage(Color.of("§b/ps lock §8[on|off]      §7— 🔒 Bloquear visitantes"));
        s.sendMessage(Color.of("§b/ps motd §8<texto|none>  §7— Descripción holograma"));
        s.sendMessage(Color.of("§b/ps mobs §8[on|off]      §7— 🐾 Toggle mobs"));
        s.sendMessage(Color.of("§b/ps transfer §8<jugador> §7— Transferir propiedad"));
        s.sendMessage(Color.of("§b/ps anims §8[on|off]     §7— ✨ Animaciones GUI"));
        s.sendMessage(Color.of("§b/ps add/remove §8<jug>   §7— Gestión de miembros"));
        s.sendMessage(Color.of("§b/ps blacklist §8<add|remove|list> [jug]"));
        s.sendMessage(Color.of("§b/ps unclaim              §7— Eliminar región"));
        if (s.hasPermission("ryx.admin")) {
            s.sendMessage(Color.of(Txt.THIN));
            s.sendMessage(Color.of("§c§l━━ Admin ━━"));
            s.sendMessage(Color.of("§c/ps admin / adminregions / admindtr"));
            s.sendMessage(Color.of("§c/ps online / toplevel / resetraidable"));
            s.sendMessage(Color.of("§c/ps setmotd / setlock / setmobs"));
            s.sendMessage(Color.of("§c/ps transferforce §8<id> <jugador>"));
            s.sendMessage(Color.of("§c/ps setdtr / resetdtr / forceremove"));
            s.sendMessage(Color.of("§c/ps setpp / addpp / give / setlevel / reload"));
            s.sendMessage(Color.of("§8Panel → Opciones de Protecciones: PvP global, etc."));
        }
        s.sendMessage(Color.of("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    // ─────────────────────────────────────────────────
    //  Tab Complete
    // ─────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String pre = args[0].toLowerCase();
            List<String> all = new ArrayList<>(USER_SUBS);
            if (s.hasPermission("ryx.admin")) all.addAll(ADMIN_SUBS);
            all.stream().filter(x -> x.startsWith(pre)).forEach(out::add);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(), pre = args[1].toLowerCase();
            switch (sub) {
                case "give" -> java.util.stream.IntStream.rangeClosed(1, com.ryxmc.protection.config.ProtectionBlock.maxLevel())
                        .mapToObj(String::valueOf).filter(x -> x.startsWith(pre)).forEach(out::add);
                case "setdtr","resetdtr","forceremove","setlevel","upgrade","expandir",
                     "setmotd","setlock","setmobs","transferforce","admindtr","adminregions","toplevel" ->
                        plugin.getRegionStore().getAll().stream()
                                .map(r -> r.regionId).filter(x -> x.startsWith(pre)).forEach(out::add);
                case "home","settings","members","energy" ->
                        plugin.getRegionStore().getOwnedByPlayer(
                                s instanceof Player pl ? pl.getUniqueId() : UUID.randomUUID())
                                .stream().filter(x -> x.startsWith(pre)).forEach(out::add);
                case "add","remove","addowner","removeowner","setpp","addpp",
                     "givefactory","givecompressor","givecomp","transfer" ->
                        Bukkit.getOnlinePlayers().stream().map(Player::getName)
                                .filter(n -> n.toLowerCase().startsWith(pre)).forEach(out::add);
                case "pvp","preview","lock","mobs","anims" ->
                        List.of("on","off").stream().filter(x -> x.startsWith(pre)).forEach(out::add);
                case "blacklist" ->
                        List.of("add","remove","list").stream().filter(x -> x.startsWith(pre)).forEach(out::add);
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase(), pre = args[2].toLowerCase();
            switch (sub) {
                case "give","transferforce" ->
                        Bukkit.getOnlinePlayers().stream().map(Player::getName)
                                .filter(n -> n.toLowerCase().startsWith(pre)).forEach(out::add);
                case "setlock","setmobs" ->
                        List.of("on","off").stream().filter(x -> x.startsWith(pre)).forEach(out::add);
                case "blacklist" ->
                        Bukkit.getOnlinePlayers().stream().map(Player::getName)
                                .filter(n -> n.toLowerCase().startsWith(pre)).forEach(out::add);
            }
        }
        return out;
    }

    // ─────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────

    private RegionStore.RegionRecord getFirstOwnedRecord(Player p) {
        List<String> owned = plugin.getRegionStore().getOwnedByPlayer(p.getUniqueId());
        return owned.isEmpty() ? null : plugin.getRegionStore().getRecord(owned.get(0));
    }

    private boolean isConsoleOk(String sub) {
        return Set.of(
                "setdtr","resetdtr","forceremove","setpp","addpp",
                "givefactory","givecompressor","givecomp","give","reload","setlevel","top",
                "online","toplevel","resetraidable","setmotd","setlock","setmobs","transferforce"
        ).contains(sub);
    }
}
