package com.ryxmc.protection.dtr;

import com.ryxmc.protection.RyxProtection;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * DtrUpgradeManager v1.0
 * Gestiona las mejoras de DTR máximo por región.
 * Ubicación: src/main/java/com/ryxmc/protection/dtr/DtrUpgradeManager.java
 *
 * Reglas:
 *  - Máximo 4 compras por región (+1.0 DTR cada una)
 *  - Coste: dtr.upgrade_cost PP (default 10)
 *  - Se pierden si la región es eliminada o transferida
 *  - Persiste en data/dtr_upgrades.yml
 */
public class DtrUpgradeManager {

    public static final int    MAX_EXTRAS      = 4;
    public static final double DTR_PER_UPGRADE = 1.0;
    private static final String CONFIG_COST_KEY = "dtr.upgrade_cost";
    private static final int    DEFAULT_COST    = 10;

    private final RyxProtection            plugin;
    private final File                     dataFile;
    private YamlConfiguration             data;
    private final Map<String, Integer>    extras = new HashMap<>();

    public DtrUpgradeManager(RyxProtection plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "dtr_upgrades.yml");
    }

    public void init() {
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); }
            catch (IOException e) {
                plugin.getLogger().severe("[DtrUpgrade] No se pudo crear dtr_upgrades.yml: " + e.getMessage());
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : data.getKeys(false))
            extras.put(key, Math.max(0, Math.min(MAX_EXTRAS, data.getInt(key, 0))));
        plugin.getLogger().info("[DtrUpgrade] Cargados " + extras.size() + " registros.");
    }

    public void shutdown() { save(); }

    public int    getExtras(String regionId)        { return extras.getOrDefault(regionId, 0); }
    public double getExtraMaxDtr(String regionId)   { return getExtras(regionId) * DTR_PER_UPGRADE; }
    public int    getUpgradeCost()                  { return plugin.getConfig().getInt(CONFIG_COST_KEY, DEFAULT_COST); }
    public boolean canUpgrade(String regionId)      { return getExtras(regionId) < MAX_EXTRAS; }

    public String purchase(String regionId, UUID playerUUID) {
        if (!canUpgrade(regionId))
            return "§cEsta región ya tiene el máximo de §e" + MAX_EXTRAS + " §cmejoras de ᴅᴛʀ.";
        int cost = getUpgradeCost();
        int pp   = plugin.getUpgradeManager().getPP(playerUUID);
        if (pp < cost)
            return "§cNecesitas §e" + cost + " ᴘᴘ§c. Tienes §e" + pp + "§c.";
        plugin.getUpgradeManager().addPP(playerUUID, -cost);
        extras.put(regionId, getExtras(regionId) + 1);
        saveAsync();
        plugin.getDtrManager().recalcMax(regionId);
        return null;
    }

    public void removeRegion(String regionId) {
        if (extras.remove(regionId) != null) saveAsync();
    }

    private void save() {
        if (data == null) return;
        for (String k : new HashSet<>(data.getKeys(false))) data.set(k, null);
        extras.forEach((k, v) -> { if (v > 0) data.set(k, v); });
        try { data.save(dataFile); }
        catch (IOException e) {
            plugin.getLogger().severe("[DtrUpgrade] Error al guardar: " + e.getMessage());
        }
    }

    private void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::save);
    }
}
