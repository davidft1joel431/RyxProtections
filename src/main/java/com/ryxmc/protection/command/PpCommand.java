package com.ryxmc.protection.command;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.util.Txt;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

/** /pp — Gestión de Puntos de Protección */
public class PpCommand implements CommandExecutor, TabCompleter {

    private final RyxProtection plugin;

    public PpCommand(RyxProtection plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String lbl, String[] args) {
        if (!(s instanceof Player p)) {
            if (args.length >= 3 && args[0].equalsIgnoreCase("set")) adminSet(s, args);
            else s.sendMessage("Uso: /pp set <jugador> <cantidad>");
            return true;
        }
        if (args.length == 0) { showPP(p); return true; }

        switch (args[0].toLowerCase()) {
            case "info"     -> showPP(p);
            case "exchange" -> cmdExchange(p, args);
            case "set"      -> adminSet(s, args);
            case "add"      -> adminAdd(s, args);
            case "top"      -> showTop(p);
            default         -> showPP(p);
        }
        return true;
    }

    private void showPP(Player p) {
        int pp = plugin.getUpgradeManager().getPP(p.getUniqueId());
        int gems = plugin.getEnergyManager().countAllGems(p);
        p.sendMessage(Txt.SEP);
        p.sendMessage("  §6§l⭐ §fTus Puntos de Protección");
        p.sendMessage(Txt.SEP);
        p.sendMessage("§9PP disponibles: §e" + pp);
        p.sendMessage("§9Gemas de Energía: §d" + gems);
        p.sendMessage("§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯");
        p.sendMessage("§7Intercambio: §f100 Gemas §8→ §e1 PP");
        if (gems >= 100) {
            p.sendMessage("§7Puedes intercambiar hasta §e" + (gems / 100) + " PP§7 con tus gemas.");
            p.sendMessage("§7Usa: §f/factory exchange <cantidad>");
        }
        p.sendMessage("§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯");
        p.sendMessage("§7Usa tus PP para mejorar tu región.");
        p.sendMessage("§7Abre §f/ps settings §7→ §6⬆ Mejoras");
        p.sendMessage(Txt.SEP);
    }

    private void cmdExchange(Player p, String[] args) {
        int amount = 1;
        if (args.length > 1) {
            try { amount = Integer.parseInt(args[1]); }
            catch (Exception e) { p.sendMessage(Txt.err("Cantidad inválida.")); return; }
        }
        plugin.getEnergyManager().exchangeGemsForPP(p, amount);
    }

    private void showTop(Player p) {
        // Top de jugadores con más PP — no persistimos ranking en memoria,
        // simplemente mostramos lo que hay en el UpgradeManager
        p.sendMessage(Txt.warn("El ranking de PP no está implementado aún. Próximamente."));
    }

    private void adminSet(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin.pp")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 3) { s.sendMessage(Txt.err("Uso: /pp set <jugador> <cantidad>")); return; }
        OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
        try {
            int amt = Integer.parseInt(args[2]);
            plugin.getUpgradeManager().setPP(t.getUniqueId(), amt);
            s.sendMessage(Txt.ok("PP de §f" + args[1] + "§a → §e" + amt));
            Player online = Bukkit.getPlayer(t.getUniqueId());
            if (online != null) online.sendMessage(Txt.ok("Tu PP fue ajustado a §e" + amt + "§a por §f" + s.getName()));
        } catch (Exception e) { s.sendMessage(Txt.err("Cantidad inválida.")); }
    }

    private void adminAdd(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin.pp")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 3) { s.sendMessage(Txt.err("Uso: /pp add <jugador> <cantidad>")); return; }
        OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
        try {
            int amt = Integer.parseInt(args[2]);
            plugin.getUpgradeManager().addPP(t.getUniqueId(), amt);
            int total = plugin.getUpgradeManager().getPP(t.getUniqueId());
            s.sendMessage(Txt.ok("Dado §e" + amt + " PP§a a §f" + args[1] + "§a. Total: §e" + total));
            Player online = Bukkit.getPlayer(t.getUniqueId());
            if (online != null) online.sendMessage(Txt.ok("Recibiste §e" + amt + " PP§a de §f" + s.getName() + "§a. Total: §e" + total));
        } catch (Exception e) { s.sendMessage(Txt.err("Cantidad inválida.")); }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("info","exchange","top"));
            if (s.hasPermission("ryx.admin.pp")) subs.addAll(List.of("set","add"));
            subs.stream().filter(x -> x.startsWith(args[0].toLowerCase())).forEach(out::add);
        } else if (args.length == 2 && Set.of("set","add").contains(args[0].toLowerCase())) {
            Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .forEach(out::add);
        }
        return out;
    }
}
