package com.ryxmc.protection.manager;

import com.ryxmc.protection.RyxProtection;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RegionStoreExtension v1.0
 * Extiende RegionStore sin modificarlo.
 * Ubicación: src/main/java/com/ryxmc/protection/manager/RegionStoreExtension.java
 *
 * Persiste en data/region_ext.yml:
 *   locked, mobSpawning, motd, blacklist por regionId
 */
public class RegionStoreExtension {

    private static final String KEY_LOCKED    = "locked";
    private static final String KEY_MOBS      = "mobSpawning";
    private static final String KEY_MOTD      = "motd";
    private static final String KEY_BLACKLIST = "blacklist";

    private final RyxProtection            plugin;
    private final File                     dataFile;
    private YamlConfiguration             data;

    private final Map<String, Boolean>     lockedMap    = new ConcurrentHashMap<>();
    private final Map<String, Boolean>     mobsMap      = new ConcurrentHashMap<>();
    private final Map<String, String>      motdMap      = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>>   blacklistMap = new ConcurrentHashMap<>();

    public RegionStoreExtension(RyxProtection plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "region_ext.yml");
    }

    public void init() {
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); }
            catch (IOException e) {
                plugin.getLogger().severe("[RegionStoreExt] No se pudo crear region_ext.yml: " + e.getMessage());
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        loadFromDisk();
        plugin.getLogger().info("[RegionStoreExt] Cargados " + lockedMap.size() + " registros.");
    }

    public void shutdown() { saveToDisk(); }

    // ── API ──────────────────────────────────────────

    public boolean isLocked(String rid)               { return lockedMap.getOrDefault(rid, false); }
    public void setLocked(String rid, boolean v)      { lockedMap.put(rid, v); saveAsync(); }

    public boolean isMobSpawningEnabled(String rid)   { return mobsMap.getOrDefault(rid, true); }
    public void setMobSpawning(String rid, boolean v) { mobsMap.put(rid, v); saveAsync(); }

    public String getMotd(String rid)                 { return motdMap.get(rid); }
    public void setMotd(String rid, String motd) {
        if (motd == null) motdMap.remove(rid); else motdMap.put(rid, motd);
        saveAsync();
    }

    public Set<UUID> getBlacklist(String rid) {
        return Collections.unmodifiableSet(blacklistMap.getOrDefault(rid, Collections.emptySet()));
    }
    public void addBlacklist(String rid, UUID uuid) {
        blacklistMap.computeIfAbsent(rid, k -> new HashSet<>()).add(uuid);
        plugin.getRegionStore().addBlacklist(rid, uuid);
        saveAsync();
    }
    public void removeBlacklist(String rid, UUID uuid) {
        Set<UUID> set = blacklistMap.get(rid);
        if (set != null) set.remove(uuid);
        plugin.getRegionStore().removeBlacklist(rid, uuid);
        saveAsync();
    }

    public boolean transferOwnership(String regionId, UUID newOwnerUUID, Player actor) {
        RegionStore.RegionRecord r = plugin.getRegionStore().getRecord(regionId);
        if (r == null || r.ownerUUID.equals(newOwnerUUID)) return false;
        int maxOwned = plugin.getConfig().getInt("protection.max_per_player", 1);
        long ownedByNew = plugin.getRegionStore().getAll().stream()
                .filter(rec -> rec.ownerUUID.equals(newOwnerUUID)).count();
        if (ownedByNew >= maxOwned) return false;
        OfflinePlayer op = Bukkit.getOfflinePlayer(newOwnerUUID);
        String newName = op.getName() != null ? op.getName() : newOwnerUUID.toString();
        try { plugin.getRegionStore().addMember(regionId, r.ownerUUID, actor.getWorld()); }
        catch (Exception ignored) {}
        boolean ok = plugin.getRegionStore().transferOwner(regionId, newOwnerUUID, newName);
        if (!ok) return false;
        saveAsync();
        return true;
    }

    public void removeRegion(String rid) {
        lockedMap.remove(rid); mobsMap.remove(rid);
        motdMap.remove(rid);   blacklistMap.remove(rid);
        saveAsync();
    }

    // ── Persistencia ─────────────────────────────────

    private void loadFromDisk() {
        if (data == null) return;
        for (String rid : data.getKeys(false)) {
            lockedMap.put(rid, data.getBoolean(rid + "." + KEY_LOCKED, false));
            mobsMap.put(rid,   data.getBoolean(rid + "." + KEY_MOBS,   true));
            String motd = data.getString(rid + "." + KEY_MOTD, null);
            if (motd != null && !motd.isEmpty()) motdMap.put(rid, motd);
            List<String> bl = data.getStringList(rid + "." + KEY_BLACKLIST);
            if (!bl.isEmpty()) {
                Set<UUID> set = new HashSet<>();
                for (String s : bl) try { set.add(UUID.fromString(s)); } catch (Exception ignored) {}
                blacklistMap.put(rid, set);
            }
        }
    }

    private void saveToDisk() {
        if (data == null) return;
        new HashSet<>(data.getKeys(false)).forEach(k -> data.set(k, null));
        Set<String> all = new HashSet<>();
        all.addAll(lockedMap.keySet()); all.addAll(mobsMap.keySet());
        all.addAll(motdMap.keySet());   all.addAll(blacklistMap.keySet());
        for (String rid : all) {
            if (lockedMap.containsKey(rid)) data.set(rid + "." + KEY_LOCKED, lockedMap.get(rid));
            if (mobsMap.containsKey(rid))   data.set(rid + "." + KEY_MOBS,   mobsMap.get(rid));
            if (motdMap.containsKey(rid))   data.set(rid + "." + KEY_MOTD,   motdMap.get(rid));
            Set<UUID> bl = blacklistMap.get(rid);
            if (bl != null && !bl.isEmpty()) {
                List<String> list = new ArrayList<>();
                bl.forEach(u -> list.add(u.toString()));
                data.set(rid + "." + KEY_BLACKLIST, list);
            }
        }
        try { data.save(dataFile); }
        catch (IOException e) { plugin.getLogger().severe("[RegionStoreExt] Error al guardar: " + e.getMessage()); }
    }

    private void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveToDisk);
    }
}
