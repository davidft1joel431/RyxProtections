package com.ryxmc.protection.command;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.war.WarManager;
import com.ryxmc.protection.util.Color;
import com.ryxmc.protection.util.Txt;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * WarCommand v1.0 — Comando /war
 *
 * Subcomandos:
 *   /war                    → GUI del panel de guerra (solo players)
 *   /war start              → Iniciar guerra con duración configurada
 *   /war stop               → Detener guerra activa
 *   /war status             → Ver estado actual de la guerra
 *   /war duration <min>     → Cambiar la duración (en minutos, 5-120)
 *   /war top                → Ver ranking de kills y saqueos
 *   /war help               → Ayuda
 *
 * Permisos:
 *   ryx.admin               → todos los subcomandos
 *   ryx.war.status          → /war status y /war top (default: true)
 */
public class WarCommand implements CommandExecutor, TabCompleter {

    private final RyxProtection plugin;

    private static final List<String> ADMIN_SUBS = List.of(
            "start", "stop", "status", "duration", "top", "help"
    );

    public WarCommand(RyxProtection plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        WarManager war = plugin.getWarManager();
        if (war == null) {
            sender.sendMessage(Txt.err("El sistema de guerras no está disponible."));
            return true;
        }

        // Sin argumentos → abrir GUI (solo jugadores con permiso admin)
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sendHelp(sender, war);
                return true;
            }
            if (!p.hasPermission("ryx.admin")) {
                // Jugadores normales ven el estado
                cmdStatus(sender, war);
                return true;
            }
            plugin.getGuiManager().openWarGui(p);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start"    -> cmdStart(sender, war);
            case "stop"     -> cmdStop(sender, war);
            case "status"   -> cmdStatus(sender, war);
            case "duration" -> cmdDuration(sender, war, args);
            case "top"      -> cmdTop(sender, war);
            case "help"     -> sendHelp(sender, war);
            default         -> {
                sender.sendMessage(Txt.err("Subcomando desconocido. Usa §f/war help§c."));
            }
        }
        return true;
    }

    // ─────────────────────────────────────────────────
    //  Subcomandos
    // ─────────────────────────────────────────────────

    private void cmdStart(CommandSender s, WarManager war) {
        if (!s.hasPermission("ryx.admin")) {
            s.sendMessage(Txt.err("Sin permiso.")); return;
        }
        if (war.isWarActive()) {
            s.sendMessage(Txt.err("Ya hay una guerra activa. Usa §f/war stop §cprimero."));
            return;
        }
        boolean ok = war.startWar();
        if (ok) {
            s.sendMessage(Txt.ok("§c⚔ §f§l¡GUERRA INICIADA! §aDuración: §f"
                    + WarManager.formatDuration(war.getDurationMs())));
        } else {
            s.sendMessage(Txt.err("No se pudo iniciar la guerra."));
        }
    }

    private void cmdStop(CommandSender s, WarManager war) {
        if (!s.hasPermission("ryx.admin")) {
            s.sendMessage(Txt.err("Sin permiso.")); return;
        }
        if (!war.isWarActive()) {
            s.sendMessage(Txt.warn("No hay guerra activa."));
            return;
        }
        war.endWar(true);
        s.sendMessage(Txt.ok("§c⚔ Guerra §cdetenida §apor §f" + s.getName() + "§a."));
    }

    private void cmdStatus(CommandSender s, WarManager war) {
        if (!s.hasPermission("ryx.admin") && !s.hasPermission("ryx.war.status")) {
            s.sendMessage(Txt.err("Sin permiso.")); return;
        }
        String sep = Color.of("§c§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        s.sendMessage(sep);
        s.sendMessage(Color.of("  §c⚔ §f§lGUERRA DE CLANES §c⚔"));
        s.sendMessage(sep);
        if (war.isWarActive()) {
            s.sendMessage(Color.of("  §9ᴇꜱᴛᴀᴅᴏ:  §c§lACTIVA"));
            s.sendMessage(Color.of("  §9ᴅᴜʀᴀᴄɪóɴ:  §f" + WarManager.formatDuration(war.getDurationMs())));
            s.sendMessage(Color.of("  §9ᴛɪᴇᴍᴘᴏ ʀᴇꜱᴛ: §e"
                    + WarManager.formatDuration(war.getRemainingMs())));
            s.sendMessage(Color.of("  §9ᴛʀᴀɴꜱᴄᴜʀʀɪᴅᴏ: §f"
                    + WarManager.formatDuration(war.getElapsedMs())));
            s.sendMessage(Color.of("  §9ᴘʀᴏɢʀᴇꜱᴏ:  §f"
                    + String.format("%.1f", war.getProgress() * 100) + "%"));
            s.sendMessage(Color.of("  §7▶ §f/war stop §7para detenerla."));
        } else {
            s.sendMessage(Color.of("  §9ᴇꜱᴛᴀᴅᴏ:  §7§lINACTIVA"));
            s.sendMessage(Color.of("  §9ᴅᴜʀᴀᴄɪóɴ ᴄᴏɴꜰ: §f"
                    + WarManager.formatDuration(war.getDurationMs())));
            s.sendMessage(Color.of("  §7▶ §f/war start §7para iniciarla."));
        }
        s.sendMessage(sep);
    }

    private void cmdDuration(CommandSender s, WarManager war, String[] args) {
        if (!s.hasPermission("ryx.admin")) {
            s.sendMessage(Txt.err("Sin permiso.")); return;
        }
        if (war.isWarActive()) {
            s.sendMessage(Txt.err("No puedes cambiar la duración con una guerra activa."));
            return;
        }
        if (args.length < 2) {
            s.sendMessage(Txt.err("Uso: §f/war duration <minutos>"));
            s.sendMessage(Txt.info("§7Rango: §f5 §7— §f120 §7minutos. §8(Paso: 5 min)"));
            return;
        }
        try {
            long minutes = Long.parseLong(args[1]);
            if (minutes < 5 || minutes > 120) {
                s.sendMessage(Txt.err("La duración debe estar entre §f5 §cy §f120 §cminutos."));
                return;
            }
            war.setDurationMs(minutes * 60 * 1000L);
            s.sendMessage(Txt.ok("Duración configurada a §e" + minutes + " minutos §a("
                    + WarManager.formatDuration(war.getDurationMs()) + ")§a."));
        } catch (NumberFormatException e) {
            s.sendMessage(Txt.err("Número inválido: §f" + args[1]));
        }
    }

    private void cmdTop(CommandSender s, WarManager war) {
        if (!s.hasPermission("ryx.admin") && !s.hasPermission("ryx.war.status")) {
            s.sendMessage(Txt.err("Sin permiso.")); return;
        }
        String sep = Color.of("§c§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        s.sendMessage(sep);
        s.sendMessage(Color.of("  §c⚔ §f§lESTADÍSTICAS DE GUERRA §c⚔"));
        s.sendMessage(sep);

        // Top Kills
        var topKills = war.getTopKills();
        s.sendMessage(Color.of("  §c§l💀 TOP KILLS:"));
        if (topKills.isEmpty()) {
            s.sendMessage(Color.of("  §7§o(Sin kills registrados)"));
        } else {
            String[] medals = {"§6🥇","§7🥈","§c🥉","§8#4","§8#5"};
            for (int i = 0; i < Math.min(5, topKills.size()); i++) {
                String name = org.bukkit.Bukkit.getOfflinePlayer(topKills.get(i).getKey()).getName();
                s.sendMessage(Color.of("  " + medals[i] + " §f" + (name != null ? name : "?")
                        + " §8— §c" + topKills.get(i).getValue() + " kills"));
            }
        }

        s.sendMessage(Color.of("  "));

        // Top Saqueadores
        var topLoot = war.getTopLooters();
        s.sendMessage(Color.of("  §5§l💎 TOP SAQUEADORES:"));
        if (topLoot.isEmpty()) {
            s.sendMessage(Color.of("  §7§o(Sin saqueos registrados)"));
        } else {
            String[] medals = {"§6🥇","§7🥈","§c🥉","§8#4","§8#5"};
            for (int i = 0; i < Math.min(5, topLoot.size()); i++) {
                String name = org.bukkit.Bukkit.getOfflinePlayer(topLoot.get(i).getKey()).getName();
                s.sendMessage(Color.of("  " + medals[i] + " §f" + (name != null ? name : "?")
                        + " §8— §d" + topLoot.get(i).getValue() + " accesos a contenedores"));
            }
        }
        s.sendMessage(sep);
    }

    private void sendHelp(CommandSender s, WarManager war) {
        String sep = Color.of("§c§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        s.sendMessage(sep);
        s.sendMessage(Color.of("  §c⚔ §f§lCOMANDOS DE GUERRA §c⚔"));
        s.sendMessage(sep);
        s.sendMessage(Color.of("  §e/war §7— §7Ver estado / abrir panel GUI"));
        s.sendMessage(Color.of("  §e/war status §7— §7Estado actual de la guerra"));
        s.sendMessage(Color.of("  §e/war top §7— §7Ranking de kills y saqueos"));
        if (s.hasPermission("ryx.admin")) {
            s.sendMessage(Color.of("  §c/war start §7— §7Iniciar la guerra"));
            s.sendMessage(Color.of("  §c/war stop §7— §7Detener la guerra"));
            s.sendMessage(Color.of("  §c/war duration §8<min> §7— §7Cambiar duración §8(5-120 min)"));
        }
        s.sendMessage(sep);
        s.sendMessage(Color.of("  §9Estado actual: "
                + (war.isWarActive()
                    ? "§c§lACTIVA §8(" + WarManager.formatDuration(war.getRemainingMs()) + " restantes)"
                    : "§7INACTIVA")));
        s.sendMessage(sep);
    }

    // ─────────────────────────────────────────────────
    //  Tab Complete
    // ─────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1) {
            String pre = args[0].toLowerCase();
            List<String> opts = new ArrayList<>();
            List.of("start","stop","status","top","help").stream()
                    .filter(x -> x.startsWith(pre)).forEach(opts::add);
            if (s.hasPermission("ryx.admin"))
                List.of("duration").stream()
                        .filter(x -> x.startsWith(pre)).forEach(opts::add);
            return opts;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("duration")) {
            return List.of("5","10","15","20","30","45","60","90","120");
        }
        return List.of();
    }
}
