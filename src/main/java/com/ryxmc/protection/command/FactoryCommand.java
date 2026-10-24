package com.ryxmc.protection.command;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.factory.EnergyFactory;
import com.ryxmc.protection.util.Txt;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

/** /factory — Gestión de fábricas de energía */
public class FactoryCommand implements CommandExecutor, TabCompleter {

    private final RyxProtection plugin;

    public FactoryCommand(RyxProtection plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String lbl, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Comando solo para jugadores."); return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { sendHelp(p); return true; }

        switch (args[0].toLowerCase()) {
            case "list"     -> listFactories(p);
            case "info"     -> factoryInfo(p);
            case "open"     -> openNearest(p);
            case "exchange" -> exchangeGems(p, args);
            case "give"           -> adminGive(s, args);
            case "remove"         -> adminRemove(s, args);
            case "giverellenador", "givefiller"    -> adminGiveFiller(s, args);
            case "givetubo", "givetube"             -> adminGiveTube(s, args);
            case "givediana", "givedirectional"     -> adminGiveDirectional(s, args);
            case "givecombinador", "givecombiner"   -> adminGiveCombiner(s, args);
            case "giveindustrial", "giveindus"      -> adminGiveIndustrial(s, args);
            case "tienda2", "shop2", "shopindus"    -> plugin.getFactoryShopGui().openShop(p);
            case "auto"            -> plugin.getFactoryAutoUpdater().cmdAuto(p, args);
            default               -> sendHelp(p);
        }
        return true;
    }

    private void sendHelp(Player p) {
        p.sendMessage(Txt.SEP);
        p.sendMessage("  §6§l⚙ §fFábrica de Energía — Comandos");
        p.sendMessage(Txt.SEP);
        p.sendMessage("§b/factory list §7— Ver tus fábricas");
        p.sendMessage("§b/factory info §7— Info de la fábrica en tu ubicación");
        p.sendMessage("§b/factory open §7— Abrir la fábrica más cercana");
        p.sendMessage("§b/factory exchange <cantidad> §7— Intercambiar gemas por PP");
        p.sendMessage("  §8(100 gemas = 1 PP)");
        p.sendMessage("§b/factory auto <nivel> <fabricaId> §7— Auto-mejora de fábrica (PP)");
        p.sendMessage("  §8Niveles 1-4, costo acumulativo");
        if (p.hasPermission("ryx.admin.factory")) {
            p.sendMessage("§c/factory give <jugador> §7— Dar fábrica (admin)");
            p.sendMessage("§c/factory remove <jugador> §7— Remover fábrica (admin)");
            p.sendMessage("§c/factory giverellenador <jugador> [cant] §7— Dar rellenador");
            p.sendMessage("§c/factory givetubo <jugador> [cant] §7— Dar tubos de conductividad");
            p.sendMessage("§c/factory givediana <jugador> [cant] §7— Dar diana direccional");
            p.sendMessage("§c/factory givecombinador <jugador> [cant] §7— Dar combinador");
            p.sendMessage("§c/factory giveindustrial <jugador> [cant] §7— Dar fábrica industrial");
        }
        p.sendMessage(Txt.SEP);
    }

    private void listFactories(Player p) {
        Collection<EnergyFactory> factories = plugin.getFactoryManager().getPlayerFactories(p.getUniqueId());
        p.sendMessage(Txt.SEP);
        p.sendMessage("  §6§l⚙ §fTus Fábricas (" + factories.size() + ")");
        p.sendMessage(Txt.SEP);
        if (factories.isEmpty()) { p.sendMessage(Txt.warn("No tienes fábricas. Coloca una §6§lFábrica de Energía§e.")); return; }
        for (EnergyFactory f : factories) {
            Location loc = f.getLocation();
            String state = switch (f.getState()) {
                case IDLE    -> "§7EN ESPERA";
                case RUNNING -> "§aFUNCIONANDO";
                case DONE    -> "§e§l¡LISTO!";
            };
            p.sendMessage("§7• §6" + f.getFactoryId()
                    + " §8@ §7" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()
                    + " §8| " + state
                    + " §8| §dOutput: §f" + f.getOutputReady()
                    + " §8| §9Velocidad: §fNv." + f.getSpeedLevel());
        }
        p.sendMessage(Txt.SEP);
    }

    private void factoryInfo(Player p) {
        EnergyFactory f = plugin.getFactoryManager().getFactoryAt(p.getLocation());
        if (f == null) {
            // Buscar en 3 bloques alrededor
            for (int dx = -3; dx <= 3 && f == null; dx++)
                for (int dz = -3; dz <= 3 && f == null; dz++)
                    f = plugin.getFactoryManager().getFactoryAt(p.getLocation().add(dx, 0, dz));
        }
        if (f == null) { p.sendMessage(Txt.err("No hay ninguna fábrica cercana.")); return; }
        final EnergyFactory factory = f;
        plugin.getFactoryListener().openFactoryGui(p, factory);
    }

    private void openNearest(Player p) { factoryInfo(p); }

    private void exchangeGems(Player p, String[] args) {
        if (!p.hasPermission("ryx.pp.exchange")) { p.sendMessage(Txt.err("Sin permiso.")); return; }
        int amount = 1;
        if (args.length > 1) {
            try { amount = Integer.parseInt(args[1]); }
            catch (NumberFormatException e) { p.sendMessage(Txt.err("Cantidad inválida.")); return; }
        }
        if (amount < 1) { p.sendMessage(Txt.err("La cantidad debe ser mayor a 0.")); return; }
        plugin.getEnergyManager().exchangeGemsForPP(p, amount);
    }

    private void adminGive(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin.factory")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 2) { s.sendMessage(Txt.err("Uso: /factory give <jugador>")); return; }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { s.sendMessage(Txt.err("Jugador no encontrado.")); return; }
        target.getInventory().addItem(plugin.getFactoryManager().createFactoryItem());
        s.sendMessage(Txt.ok("Fábrica dada a §f" + target.getName()));
        target.sendMessage(Txt.ok("Recibiste una §6§lFábrica de Energía§a."));
    }

    private void adminRemove(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin.factory")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        s.sendMessage(Txt.warn("Para remover una fábrica, rómpela en el mundo."));
    }

    /** /factory giverellenador <jugador> [cantidad] */
    private void adminGiveFiller(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin.factory")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 2) { s.sendMessage(Txt.err("Uso: /factory giverellenador <jugador> [cantidad]")); return; }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { s.sendMessage(Txt.err("Jugador §f" + args[1] + "§c no encontrado.")); return; }
        int amount = parseAmount(args, 2, 1);
        var item = plugin.getFillerManager().createFillerItem();
        item.setAmount(amount);
        target.getInventory().addItem(item);
        s.sendMessage(Txt.ok("§6§l⚡ Rellenador §7x" + amount + " §adado a §f" + target.getName() + "§a."));
        target.sendMessage(Txt.ok("Recibiste §e" + amount + "x §6§l⚡ §e§lRellenador§a."));
    }

    /** /factory givetubo <jugador> [cantidad] */
    private void adminGiveTube(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin.factory")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 2) { s.sendMessage(Txt.err("Uso: /factory givetubo <jugador> [cantidad]")); return; }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { s.sendMessage(Txt.err("Jugador §f" + args[1] + "§c no encontrado.")); return; }
        int amount = parseAmount(args, 2, 16);
        var item = com.ryxmc.protection.factory.TubeNetwork.buildTubeItem();
        item.setAmount(amount);
        target.getInventory().addItem(item);
        s.sendMessage(Txt.ok("§b§l⚡ Tubo de conductividad §7x" + amount + " §adado a §f" + target.getName() + "§a."));
        target.sendMessage(Txt.ok("Recibiste §e" + amount + "x §b§l⚡ §f§lTubo de conductividad§a."));
    }

    /** /factory givediana <jugador> [cantidad] */
    private void adminGiveDirectional(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin.factory")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 2) { s.sendMessage(Txt.err("Uso: /factory givediana <jugador> [cantidad]")); return; }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { s.sendMessage(Txt.err("Jugador §f" + args[1] + "§c no encontrado.")); return; }
        int amount = parseAmount(args, 2, 4);
        var item = com.ryxmc.protection.factory.TubeNetwork.buildDirectionalItem();
        item.setAmount(amount);
        target.getInventory().addItem(item);
        s.sendMessage(Txt.ok("§c§l◆ Diana Direccional §7x" + amount + " §adada a §f" + target.getName() + "§a."));
        target.sendMessage(Txt.ok("Recibiste §e" + amount + "x §c§l◆ §f§lDiana Direccional§a."));
    }

    /** /factory givecombinador <jugador> [cantidad] */
    private void adminGiveCombiner(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin.factory")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 2) { s.sendMessage(Txt.err("Uso: /factory givecombinador <jugador> [cantidad]")); return; }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { s.sendMessage(Txt.err("Jugador §f" + args[1] + "§c no encontrado.")); return; }
        int amount = parseAmount(args, 2, 4);
        var item = com.ryxmc.protection.factory.TubeNetwork.buildCombinerItem();
        item.setAmount(amount);
        target.getInventory().addItem(item);
        s.sendMessage(Txt.ok("§6§l▼ Combinador §7x" + amount + " §adado a §f" + target.getName() + "§a."));
        target.sendMessage(Txt.ok("Recibiste §e" + amount + "x §6§l▼ §f§lCombinador§a."));
    }

    /** /factory giveindustrial <jugador> [cantidad] */
    private void adminGiveIndustrial(CommandSender s, String[] args) {
        if (!s.hasPermission("ryx.admin.factory")) { s.sendMessage(Txt.err("Sin permiso.")); return; }
        if (args.length < 2) { s.sendMessage(Txt.err("Uso: /factory giveindustrial <jugador> [cantidad]")); return; }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { s.sendMessage(Txt.err("Jugador §f" + args[1] + "§c no encontrado.")); return; }
        int amount = parseAmount(args, 2, 1);
        var item = plugin.getIndustrialFactoryManager().createHeartItem();
        item.setAmount(amount);
        target.getInventory().addItem(item);
        s.sendMessage(Txt.ok("§6§l⚙ Fábrica Industrial §7x" + amount + " §adada a §f" + target.getName() + "§a."));
        target.sendMessage(Txt.ok("Recibiste §e" + amount + "x §6§l⚙ §e§lFábrica Industrial §8[Corazón]§a."));
        target.sendMessage(Txt.warn("§7Requiere espacio §f3×1×1 §7libre para colocarla."));
    }

    /** Parsea la cantidad opcional de un comando give*, con límite 1-64 */
    private int parseAmount(String[] args, int index, int defaultVal) {
        if (args.length <= index) return defaultVal;
        try {
            int v = Integer.parseInt(args[index]);
            return Math.max(1, Math.min(64, v));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List.of("list","info","open","exchange","help","tienda2","auto").stream()
                    .filter(x -> x.startsWith(args[0].toLowerCase())).forEach(out::add);
            if (s.hasPermission("ryx.admin.factory"))
                List.of("give","remove","giverellenador","givetubo","givediana",
                        "givecombinador","giveindustrial").stream()
                        .filter(x -> x.startsWith(args[0].toLowerCase())).forEach(out::add);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("give")
                || args[0].equalsIgnoreCase("giverellenador")
                || args[0].equalsIgnoreCase("givetubo")
                || args[0].equalsIgnoreCase("givediana")
                || args[0].equalsIgnoreCase("givecombinador")
                || args[0].equalsIgnoreCase("giveindustrial"))) {
            Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .forEach(out::add);
        }
        return out;
    }
}
