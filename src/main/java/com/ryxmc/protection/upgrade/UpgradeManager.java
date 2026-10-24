package com.ryxmc.protection.upgrade;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.util.Txt;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * UpgradeManager — Sistema de mejoras de región basado en Puntos de Protección (PP).
 *
 * ── Niveles ─────────────────────────────────────────────
 *  Hay 12 niveles (1-12). Cada nivel define:
 *    [0] xRadius   — radio del bloque en el eje X/Z
 *    [1] baseDtr   — DTR base del nivel
 *    [2] costPP    — PP necesarios para subir a este nivel
 *    [3] isPremium — 1 si requiere rango premium
 *
 * ── Sub-upgrades ────────────────────────────────────────
 *  Cada nivel admite hasta 4 sub-upgrades de +5 radio.
 *  Costo: SUB_UPGRADE_COST PP cada uno.
 *
 * ── PP (Puntos de Protección) ──────────────────────────
 *  Moneda especial obtenida intercambiando gemas (100 → 1 PP).
 *  Se almacena por UUID de jugador.
 */
public class UpgradeManager {

    public static final int SUB_UPGRADE_COST = 10;
    public static final int MAX_SUB          = 4;

    // ── Datos en memoria ──────────────────────────────────

    /** regionId → datos de upgrade */
    private final Map<String, RegionData> regionData = new HashMap<>();

    /** UUID jugador → PP */
    private final Map<UUID, Integer> playerPP = new HashMap<>();

    private final RyxProtection plugin;
    private File saveFile;

    public UpgradeManager(RyxProtection plugin) {
        this.plugin = plugin;
    }

    public void init() {
        saveFile = new File(plugin.getDataFolder(), "upgrades.yml");
        load();
        plugin.getLogger().info("[Upgrade] Iniciado. Regiones: " + regionData.size()
                + " | Jugadores PP: " + playerPP.size());
    }

    public void shutdown() { save(); }

    // ─────────────────────────────────────────────────
    //  Datos de nivel por región
    // ─────────────────────────────────────────────────

    private RegionData getOrCreate(String regionId) {
        return regionData.computeIfAbsent(regionId, k -> new RegionData(1, 0));
    }

    public int getLevel(String regionId) {
        return getOrCreate(regionId).level;
    }

    public int getSubBonus(String regionId) {
        return getOrCreate(regionId).subBonus;
    }

    /** Radio efectivo = base del nivel + subBonus × 5. */
    public int getXRadius(String regionId) {
        RegionData d = getOrCreate(regionId);
        return com.ryxmc.protection.config.ProtectionBlock.getXRadius(d.level) + d.subBonus * 5;
    }

    public double getBaseDtr(String regionId) {
        int lvl = getOrCreate(regionId).level;
        return com.ryxmc.protection.config.ProtectionBlock.getBaseDtr(lvl);
    }

    public String getLevelName(int level) {
        return com.ryxmc.protection.config.ProtectionBlock.getName(level);
    }

    public int getNextLevelCost(String regionId) {
        int lvl = getOrCreate(regionId).level;
        if (!com.ryxmc.protection.config.ProtectionBlock.hasNext(lvl)) return -1;
        return com.ryxmc.protection.config.ProtectionBlock.getPPCost(com.ryxmc.protection.config.ProtectionBlock.nextLevel(lvl));
    }

    public boolean canSubUpgrade(String regionId) {
        return getOrCreate(regionId).subBonus < MAX_SUB;
    }

    public int[][] getLevelsData() {
        return com.ryxmc.protection.config.ProtectionBlock.LEVELS;
    }

    // ─────────────────────────────────────────────────
    //  Mejorar — Con PP
    // ─────────────────────────────────────────────────

    /**
     * Intenta mejorar al siguiente nivel principal.
     * @return null si éxito, mensaje de error en caso contrario.
     */
    public String upgradePrincipal(Player player, String regionId) {
        RegionData d = getOrCreate(regionId);

        int maxLvl = com.ryxmc.protection.config.ProtectionBlock.maxLevel();
        if (d.level >= maxLvl)
            return "Tu región ya está al nivel máximo (Nv." + maxLvl + ").";

        int nextLvl = d.level + 1;
        int cost    = com.ryxmc.protection.config.ProtectionBlock.getPPCost(nextLvl);
        if (com.ryxmc.protection.config.ProtectionBlock.isPremium(nextLvl) && !player.hasPermission("ryx.upgrade.premium"))
            return "§dNivel " + com.ryxmc.protection.config.ProtectionBlock.getName(nextLvl) + "§c requiere rango §dPREMIUM§c.";

        if (getPP(player.getUniqueId()) < cost)
            return "Necesitas §e" + cost + " PP§c. Tienes §e"
                    + getPP(player.getUniqueId()) + " PP§c.";

        removePP(player.getUniqueId(), cost);
        d.level    = nextLvl;
        d.subBonus = 0;
        save();
        return null;
    }

    /**
     * Sub-upgrade: +5 al radio. Máximo 4 por nivel.
     * @return null si éxito, mensaje de error en caso contrario.
     */
    public String subUpgrade(Player player, String regionId) {
        RegionData d = getOrCreate(regionId);

        if (d.subBonus >= MAX_SUB)
            return "Ya tienes el máximo de sub-upgrades para este nivel (§e4/4§c).";

        if (getPP(player.getUniqueId()) < SUB_UPGRADE_COST)
            return "Necesitas §e" + SUB_UPGRADE_COST + " PP§c para el sub-upgrade. Tienes §e"
                    + getPP(player.getUniqueId()) + "§c.";

        removePP(player.getUniqueId(), SUB_UPGRADE_COST);
        d.subBonus++;
        save();
        return null;
    }

    // ─────────────────────────────────────────────────
    //  Admin — Sin costo
    // ─────────────────────────────────────────────────

    public void setLevel(String regionId, int level) {
        RegionData d = getOrCreate(regionId);
        d.level    = Math.max(1, Math.min(com.ryxmc.protection.config.ProtectionBlock.maxLevel(), level));
        d.subBonus = 0;
        save();
    }

    public void initRegion(String regionId) {
        regionData.put(regionId, new RegionData(1, 0));
        save();
    }

    public void removeRegion(String regionId) {
        regionData.remove(regionId);
        save();
    }

    // ─────────────────────────────────────────────────
    //  PP — Puntos de Protección
    // ─────────────────────────────────────────────────

    public int getPP(UUID uuid)              { return playerPP.getOrDefault(uuid, 0); }
    public void setPP(UUID uuid, int amount) { playerPP.put(uuid, Math.max(0, amount)); save(); }
    public void addPP(UUID uuid, int amount) { setPP(uuid, getPP(uuid) + amount); }

    private void removePP(UUID uuid, int amount) {
        setPP(uuid, Math.max(0, getPP(uuid) - amount));
    }

    // ─────────────────────────────────────────────────
    //  Persistencia
    // ─────────────────────────────────────────────────

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();

        for (Map.Entry<String, RegionData> e : regionData.entrySet()) {
            String path = "regions." + e.getKey();
            cfg.set(path + ".level",    e.getValue().level);
            cfg.set(path + ".subBonus", e.getValue().subBonus);
        }
        for (Map.Entry<UUID, Integer> e : playerPP.entrySet()) {
            cfg.set("pp." + e.getKey(), e.getValue());
        }

        try { cfg.save(saveFile); }
        catch (IOException ex) {
            plugin.getLogger().severe("[Upgrade] Error guardando: " + ex.getMessage());
        }
    }

    private void load() {
        if (!saveFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(saveFile);

        if (cfg.isConfigurationSection("regions")) {
            for (String rid : cfg.getConfigurationSection("regions").getKeys(false)) {
                int lvl = cfg.getInt("regions." + rid + ".level", 1);
                int sub = cfg.getInt("regions." + rid + ".subBonus", 0);
                regionData.put(rid, new RegionData(lvl, sub));
            }
        }
        if (cfg.isConfigurationSection("pp")) {
            for (String uuidStr : cfg.getConfigurationSection("pp").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    playerPP.put(uuid, cfg.getInt("pp." + uuidStr, 0));
                } catch (Exception ignored) {}
            }
        }
    }

    // ─────────────────────────────────────────────────
    //  Datos internos
    // ─────────────────────────────────────────────────

    private static class RegionData {
        int level;
        int subBonus;
        RegionData(int l, int s) { this.level = l; this.subBonus = s; }
    }
}
