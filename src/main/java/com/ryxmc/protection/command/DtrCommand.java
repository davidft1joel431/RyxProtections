package com.ryxmc.protection.command;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.dtr.DTRData;
import com.ryxmc.protection.manager.RegionStore;
import com.ryxmc.protection.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

/** /dtr — Sistema DTR completo para usuarios y administradores */
public class DtrCommand implements CommandExecutor, TabCompleter {

    private final RyxProtection plugin;

    public DtrCommand(RyxProtection plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String lbl, String[] args) {
        if (!(s instanceof Player p)) {
            showAllDtr(s); return true;
        }

        if (args.length == 0) { showPlayerDtr(p); return true; }

        switch (args[0].toLowerCase()) {
            case "top"   -> showTop(p);
            case "set"   -> adminSet(s, args);
            case "reset" -> adminReset(s, args);
            case "max"   -> adminSetMax(s, args);
            case "info"  -> showRegionDtr(p, args.length > 1 ? args[1] : null);
            default      -> showRegionDtr(p, args[0]);
        }
        return true;
    }

    private void showPlayerDtr(Player p) {
        if (!p.hasPermission("ryx.dtr.view")) { p.sendMessage(Txt.err("Sin permiso.")); return; }
        List<String> regions = plugin.getRegionStore().getRegionsByPlayer(p.getUniqueId());
        if (regions.isEmpty()) { p.sendMessage(Txt.warn("No tienes regiones registradas.")); return; }
        p.sendMessage(Txt.SEP);
        p.sendMessage("  §c§l⚡ §fTu Estado DTR");
        p.sendMessage(Txt.SEP);
        for (String rid : regions) {
            DTRData d = plugin.getDtrManager().get(rid);
            String  n = plugin.getRegionStore().getDisplayName(rid);
            p.sendMessage("§7▸ §e" + n);
            p.sendMessage("  §9DTR: " + d.formatted());
            p.sendMessage("  §9Máximo: §f" + String.format("%.1f", d.getMaxDtr())
                    + " §8(base " + String.format("%.0f",
                        plugin.getUpgradeManager().getBaseDtr(rid))
                    + " + §b" + d.getRegionMembers() + "§8×1.5 mem"
                    + " + §a" + d.getClanMembers() + "§8×0.5 clan§8)");
            if (d.isRaidable()) {
                if (d.isInCooldown())
                    p.sendMessage("  §c§l⚠ RAIDEABLE — regen en §f" + Txt.time(d.cooldownSecondsLeft()));
                else
                    p.sendMessage("  §c§l⚠ RAIDEABLE — §aregenerando +0.1/2min");
            }
        }
        p.sendMessage(Txt.SEP);
    }

    private void showRegionDtr(Player p, String regionId) {
        if (regionId == null) { showPlayerDtr(p); return; }
        RegionStore.RegionRecord r = plugin.getRegionStore().getRecord(regionId);
        if (r == null) { p.sendMessage(Txt.err("Región §f" + regionId + "§c no encontrada.")); return; }
        DTRData d = plugin.getDtrManager().get(regionId);
        p.sendMessage(Txt.SEP);
        p.sendMessage("  §c§l⚡ §fDTR: §e" + plugin.getRegionStore().getDisplayName(regionId));
        p.sendMessage(Txt.SEP);
        p.sendMessage("§9Dueño: §f" + r.ownerName);
        p.sendMessage("§9DTR: " + d.formatted());
        p.sendMessage("§9Raideable: " + (d.isRaidable() ? "§4§lSÍ" : "§aNO"));
        if (d.isInCooldown()) p.sendMessage("§9Cooldown: §f" + Txt.time(d.cooldownSecondsLeft()));
        p.sendMessage(Txt.SEP);
    }

    private void showTop(Player p) {
        List<Map.Entry<String, DTRData>> list = new ArrayList<>(plugin.getDtrManager().getAll().entrySet());
        // Ordenar: raideables primero, luego por DTR ascendente (menos DTR = más en peligro)
        list.sort((a, b) -> {
            boolean ra = a.getValue().isRaidable(), rb = b.getValue().isRaidable();
            if (ra != rb) return ra ? -1 : 1;
            return Double.compare(a.getValue().getDtr(), b.getValue().getDtr());
        });
        p.sendMessage(Txt.SEP);
        p.sendMessage("  §c§l⚠ §fRegiones en mayor peligro §8(" + list.size() + " total)");
        p.sendMessage(Txt.SEP);
        for (int i = 0; i < Math.min(10, list.size()); i++) {
            String rid   = list.get(i).getKey();
            DTRData d    = list.get(i).getValue();
            String name  = plugin.getRegionStore().getDisplayName(rid);
            String owner = plugin.getRegionStore().getOwnerName(rid);
            String raid  = d.isRaidable() ? " §4§l[RAIDEABLE]" : "";
            String rank  = switch (i) { case 0 -> "§c§l#1"; case 1 -> "§6#2"; case 2 -> "§e#3"; default -> "§8#"+(i+1); };
            p.sendMessage(rank + " §f" + name + " §8(§7" + owner + "§8)"
                    + " §8— §9DTR: §f" + Txt.dec(d.getDtr()) + "§8/§f" + Txt.dec(d.getMaxDtr())
                    + raid);
        }
        p.sendMessage(Txt.SEP);
    }

    private void showAllDtr(CommandSender s) {
        s.sendMessage("=== DTR de todas las regiones ===");
        for (Map.Entry<String, DTRData> e : plugin.getDtrManager().getAll().entrySet()) {
            s.sendMessage(e.getKey() + ": " + e.getValue().formatted());
        }
    }

    private void adminSet(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin.dtr")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 3) { s.sendMessage(Txt.err("Uso: /dtr set <regionId> <valor>")); return; }
        try {
            plugin.getDtrManager().set(args[1], Double.parseDouble(args[2]));
            s.sendMessage(Txt.ok("DTR de §f" + args[1] + "§a → §f" + args[2]));
        } catch (Exception e) { s.sendMessage(Txt.err("Valores inválidos.")); }
    }

    private void adminReset(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin.dtr")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 2) { s.sendMessage(Txt.err("Uso: /dtr reset <regionId>")); return; }
        plugin.getDtrManager().reset(args[1]);
        s.sendMessage(Txt.ok("DTR de §f" + args[1] + "§a restablecido al máximo."));
    }

    private void adminSetMax(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin.dtr")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 3) { s.sendMessage(Txt.err("Uso: /dtr max <regionId> <valor>")); return; }
        try {
            double max = Double.parseDouble(args[2]);
            DTRData d  = plugin.getDtrManager().get(args[1]);
            d.recalcMax(max, d.getRegionMembers(), d.getClanMembers());
            plugin.getDtrManager().save();
            s.sendMessage(Txt.ok("DTR máximo de §f" + args[1] + "§a → §f" + max));
        } catch (Exception e) { s.sendMessage(Txt.err("Valores inválidos.")); }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("top","info"));
            if (s.hasPermission("ryx.admin.dtr")) subs.addAll(List.of("set","reset","max"));
            subs.stream().filter(x -> x.startsWith(args[0].toLowerCase())).forEach(out::add);
            // también completar regionIds
            plugin.getRegionStore().getAll().stream()
                    .map(r -> r.regionId)
                    .filter(x -> x.startsWith(args[0].toLowerCase()))
                    .forEach(out::add);
        } else if (args.length == 2 && Set.of("set","reset","max","info").contains(args[0].toLowerCase())) {
            plugin.getRegionStore().getAll().stream()
                    .map(r -> r.regionId)
                    .filter(x -> x.startsWith(args[1].toLowerCase()))
                    .forEach(out::add);
        }
        return out;
    }
}
