package com.ryxmc.protection.hook;

import org.bukkit.Bukkit;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/** Hook de plugins de teams/clanes. Usa reflexión para no requerir depend dura. */
public class TeamHook {

    private boolean available = false;
    private String  pluginName = null;
    private final Logger log;

    public TeamHook(Logger log) {
        this.log = log;
        detect();
    }

    private void detect() {
        String[] plugins = {"SimpleClans", "BetterTeams", "UltimateClans"};
        String[] classes = {
                "net.sacredlabyrinth.phaed.simpleclans.SimpleClans",
                "com.booksaw.betterTeams.Team",
                "me.ulrich.clans.interfaces.API"
        };
        for (int i = 0; i < plugins.length; i++) {
            if (Bukkit.getPluginManager().getPlugin(plugins[i]) != null) {
                try {
                    Class.forName(classes[i]);
                    available   = true;
                    pluginName  = plugins[i];
                    log.info("[TeamHook] Conectado a: " + pluginName);
                    return;
                } catch (ClassNotFoundException ignored) {}
            }
        }
        log.info("[TeamHook] Ningún plugin de teams detectado.");
    }

    public boolean isAvailable() { return available; }
    public String  getPlugin()   { return pluginName; }

    /** Devuelve UUIDs de compañeros del jugador (sin él mismo) */
    public Set<UUID> getTeammates(UUID playerUUID) {
        if (!available) return Collections.emptySet();
        try {
            return switch (pluginName) {
                case "SimpleClans"   -> simpleClansTeammates(playerUUID);
                case "BetterTeams"   -> betterTeamsTeammates(playerUUID);
                case "UltimateClans" -> ultimateClansTeammates(playerUUID);
                default              -> Collections.emptySet();
            };
        } catch (Exception e) { return Collections.emptySet(); }
    }

    public int getTeammateCount(UUID playerUUID) { return getTeammates(playerUUID).size(); }

    public String getTeamName(UUID playerUUID) {
        if (!available) return null;
        try {
            return switch (pluginName) {
                case "SimpleClans"   -> simpleClansName(playerUUID);
                case "BetterTeams"   -> betterTeamsName(playerUUID);
                case "UltimateClans" -> ultimateClansName(playerUUID);
                default              -> null;
            };
        } catch (Exception e) { return null; }
    }

    // ── Implementaciones por reflexión ────────────────

    private Set<UUID> simpleClansTeammates(UUID uuid) throws Exception {
        Class<?> sc = Class.forName("net.sacredlabyrinth.phaed.simpleclans.SimpleClans");
        Object inst = sc.getMethod("getInstance").invoke(null);
        Object cm   = inst.getClass().getMethod("getClanManager").invoke(inst);
        Object cp   = cm.getClass().getMethod("getClanPlayer", UUID.class).invoke(cm, uuid);
        if (cp == null) return Collections.emptySet();
        Object clan = cp.getClass().getMethod("getClan").invoke(cp);
        if (clan == null) return Collections.emptySet();
        java.util.List<?> members = (java.util.List<?>) clan.getClass().getMethod("getMembers").invoke(clan);
        Set<UUID> result = new HashSet<>();
        for (Object m : members) {
            UUID mu = (UUID) m.getClass().getMethod("getUniqueId").invoke(m);
            if (mu != null && !mu.equals(uuid)) result.add(mu);
        }
        return result;
    }

    private Set<UUID> betterTeamsTeammates(UUID uuid) throws Exception {
        Class<?> tc = Class.forName("com.booksaw.betterTeams.Team");
        Object opt  = tc.getMethod("getTeam", UUID.class).invoke(null, uuid);
        if (!(boolean) opt.getClass().getMethod("isPresent").invoke(opt)) return Collections.emptySet();
        Object team = opt.getClass().getMethod("get").invoke(opt);
        java.util.Collection<?> members = (java.util.Collection<?>) team.getClass().getMethod("getMembers").invoke(team);
        Set<UUID> result = new HashSet<>();
        for (Object m : members) {
            UUID mu = (UUID) m.getClass().getMethod("getUUID").invoke(m);
            if (!mu.equals(uuid)) result.add(mu);
        }
        return result;
    }

    private Set<UUID> ultimateClansTeammates(UUID uuid) throws Exception {
        Class<?> ac = Class.forName("me.ulrich.clans.interfaces.API");
        Object api  = ac.getMethod("getInstance").invoke(null);
        Object opt  = api.getClass().getMethod("getPlayerClan", UUID.class).invoke(api, uuid);
        if (!(boolean) opt.getClass().getMethod("isPresent").invoke(opt)) return Collections.emptySet();
        Object clan = opt.getClass().getMethod("get").invoke(opt);
        @SuppressWarnings("unchecked")
        Set<UUID> members = (Set<UUID>) clan.getClass().getMethod("getMembers").invoke(clan);
        Set<UUID> result  = new HashSet<>(members);
        result.remove(uuid);
        return result;
    }

    private String simpleClansName(UUID uuid) throws Exception {
        Class<?> sc = Class.forName("net.sacredlabyrinth.phaed.simpleclans.SimpleClans");
        Object inst = sc.getMethod("getInstance").invoke(null);
        Object cm   = inst.getClass().getMethod("getClanManager").invoke(inst);
        Object cp   = cm.getClass().getMethod("getClanPlayer", UUID.class).invoke(cm, uuid);
        if (cp == null) return null;
        Object clan = cp.getClass().getMethod("getClan").invoke(cp);
        return clan == null ? null : (String) clan.getClass().getMethod("getName").invoke(clan);
    }

    private String betterTeamsName(UUID uuid) throws Exception {
        Class<?> tc = Class.forName("com.booksaw.betterTeams.Team");
        Object opt  = tc.getMethod("getTeam", UUID.class).invoke(null, uuid);
        if (!(boolean) opt.getClass().getMethod("isPresent").invoke(opt)) return null;
        Object team = opt.getClass().getMethod("get").invoke(opt);
        return (String) team.getClass().getMethod("getName").invoke(team);
    }

    private String ultimateClansName(UUID uuid) throws Exception {
        Class<?> ac = Class.forName("me.ulrich.clans.interfaces.API");
        Object api  = ac.getMethod("getInstance").invoke(null);
        Object opt  = api.getClass().getMethod("getPlayerClan", UUID.class).invoke(api, uuid);
        if (!(boolean) opt.getClass().getMethod("isPresent").invoke(opt)) return null;
        Object clan = opt.getClass().getMethod("get").invoke(opt);
        return (String) clan.getClass().getMethod("getName").invoke(clan);
    }
}
