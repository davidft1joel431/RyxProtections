package com.ryxmc.protection.hook;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Set;

/**
 * CommandRestrictor v1.0 — Bloqueo de comandos para usuarios sin rango adecuado.
 *
 * Evita que jugadores con rango "default" (LuckPerms) ejecuten comandos de
 * administración del plugin. Solo se permite el uso a jugadores con los
 * permisos correspondientes (ryx.admin, ryx.admin.*, etc.).
 *
 * Comandos protegidos:
 *   /ps admin, /ps setdtr, /ps resetdtr, /ps forceremove, /ps setpp,
 *   /ps addpp, /ps give, /ps givefactory, /ps givecompressor, /ps reload,
 *   /ps setlevel, /ps expandir, /ps bypass, /ps setmotd, /ps setlock,
 *   /ps setmobs, /ps transferforce, /ps online, /ps toplevel,
 *   /ps resetraidable, /ps admindtr, /ps adminregions
 *   /factory give, /factory remove, /factory giverellenador, etc.
 *   /dtr set, /dtr reset, /dtr max
 *   /war start, /war stop
 *
 * Los subcomandos de usuario normales (/ps info, /ps home, /ps list, etc.)
 * NO están bloqueados y funcionan con ryx.protection.use (default: true).
 */
public class CommandRestrictor implements Listener {

    private final RyxProtection plugin;

    // Subcomandos de /ps que requieren permiso de admin
    private static final Set<String> PS_ADMIN_SUBS = Set.of(
            "admin", "setdtr", "resetdtr", "forceremove", "setpp", "addpp",
            "give", "givefactory", "givecompressor", "givecomp",
            "reload", "setlevel", "expandir", "expand", "bypass",
            "setmotd", "setlock", "setmobs", "transferforce",
            "online", "toplevel", "resetraidable", "admindtr", "adminregions"
    );

    // Subcomandos de /factory que requieren permiso de admin
    private static final Set<String> FACTORY_ADMIN_SUBS = Set.of(
            "give", "remove", "giverellenador", "givefiller",
            "givetubo", "givetube", "givediana", "givedirectional",
            "givecombinador", "givecombiner", "giveindustrial", "giveindus"
    );

    // Subcomandos de /dtr que requieren permiso de admin
    private static final Set<String> DTR_ADMIN_SUBS = Set.of(
            "set", "reset", "max"
    );

    // Subcomandos de /war que requieren permiso de admin
    private static final Set<String> WAR_ADMIN_SUBS = Set.of(
            "start", "stop", "force"
    );

    public CommandRestrictor(RyxProtection plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        String raw = event.getMessage();
        if (raw == null || raw.isEmpty()) return;

        // Quitar el prefijo '/' y dividir por espacios
        String cmd = raw.startsWith("/") ? raw.substring(1) : raw;
        String[] parts = cmd.split(" ");
        String base = parts[0].toLowerCase();

        // Solo interceptar comandos del plugin RyxProtections
        if (!base.equals("ps") && !base.equals("rps")
                && !base.equals("protection") && !base.equals("proteccion")
                && !base.equals("factory") && !base.equals("fabrica")
                && !base.equals("fac")
                && !base.equals("dtr")
                && !base.equals("war") && !base.equals("guerra")
                && !base.equals("event") && !base.equals("evento")
                && !base.equals("pp")) {
            return; // No es un comando de este plugin
        }

        // ── /pp solo tiene subcomandos de admin (set, add, info, top) ──
        if (base.equals("pp")) {
            if (parts.length < 2) return; // /pp sin args = ayuda (permitido)
            String sub = parts[1].toLowerCase();
            if (Set.of("set", "add").contains(sub)) {
                if (!p.hasPermission("ryx.admin.pp")) {
                    event.setCancelled(true);
                    p.sendMessage(Txt.err("§cSin permiso: §fryx.admin.pp §crequerido para este comando."));
                }
            }
            return;
        }

        // ── /dtr ──
        if (base.equals("dtr")) {
            if (parts.length < 2) return; // /dtr sin args = ver propio (permitido)
            String sub = parts[1].toLowerCase();
            if (DTR_ADMIN_SUBS.contains(sub)) {
                if (!p.hasPermission("ryx.admin.dtr")) {
                    event.setCancelled(true);
                    p.sendMessage(Txt.err("§cSin permiso: §fryx.admin.dtr §crequerido para este comando."));
                }
            }
            return;
        }

        // ── /factory (fabrica, fac) ──
        if (base.equals("factory") || base.equals("fabrica") || base.equals("fac")) {
            if (parts.length < 2) return; // /factory sin args = ayuda (permitido)
            String sub = parts[1].toLowerCase();
            if (FACTORY_ADMIN_SUBS.contains(sub)) {
                if (!p.hasPermission("ryx.admin.factory")) {
                    event.setCancelled(true);
                    p.sendMessage(Txt.err("§cSin permiso: §fryx.admin.factory §crequerido para este comando."));
                }
            }
            return;
        }

        // ── /war (guerra, event, evento) ──
        if (base.equals("war") || base.equals("guerra")
                || base.equals("event") || base.equals("evento")) {
            if (parts.length < 2) return;
            String sub = parts[1].toLowerCase();
            if (WAR_ADMIN_SUBS.contains(sub)) {
                if (!p.hasPermission("ryx.admin")) {
                    event.setCancelled(true);
                    p.sendMessage(Txt.err("§cSin permiso: §fryx.admin §crequerido para comandos de guerra."));
                }
            }
            return;
        }

        // ── /ps (rps, protection, proteccion) ──
        if (base.equals("ps") || base.equals("rps")
                || base.equals("protection") || base.equals("proteccion")) {
            if (parts.length < 2) return; // /ps sin args = ayuda (permitido)
            String sub = parts[1].toLowerCase();

            if (PS_ADMIN_SUBS.contains(sub)) {
                if (!p.hasPermission("ryx.admin")) {
                    event.setCancelled(true);
                    p.sendMessage(Txt.err("§cSin permiso: §fryx.admin §crequerido para este comando."));
                }
            }
            // Los subcomandos de usuario normales se dejan pasar
            // (tienen sus propias comprobaciones de permiso en PsCommand)
        }
    }
}
