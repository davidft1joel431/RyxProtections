package com.ryxmc.protection.manager;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.model.ProtectionRole;
import com.ryxmc.protection.util.Txt;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * RegionStore — Registro central de todas las regiones de protección.
 *
 * BUGS CORREGIDOS:
 *  BUG #4 — blockConfigId era final en RegionRecord, impidiendo los upgrades.
 *           setBlockConfigId() no existía → BlockUpgradeManager no compilaba.
 *  SOLUCIÓN: blockConfigId ahora es mutable + método setBlockConfigId() añadido.
 */
public class RegionStore {

    private final RyxProtection plugin;

    /** regionId → RegionRecord */
    private final Map<String, RegionRecord> regions = new LinkedHashMap<>();

    /** playerUUID → set de regionIds donde es dueño o tiene rol */
    private final Map<UUID, Set<String>> playerIndex = new HashMap<>();

    /** "world:x:y:z" → regionId — índice O(1) para getByBlockLoc */
    private final Map<String, String> blockLocIndex = new HashMap<>();

    private File saveFile;

    /** Convierte coordenadas de bloque a clave de índice */
    private static String locKey(Location loc) {
        if (loc == null || loc.getWorld() == null) return "null";
        return loc.getWorld().getName() + ":" + loc.getBlockX()
                + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public RegionStore(RyxProtection plugin) {
        this.plugin = plugin;
    }

    public void init() {
        saveFile = new File(plugin.getDataFolder(), "regions.yml");
        load();
        plugin.getLogger().info("[RegionStore] Cargadas " + regions.size() + " regiones.");
    }

    public void shutdown() { save(); }

    // ─────────────────────────────────────────────────
    //  CRUD
    // ─────────────────────────────────────────────────

    public String createRegion(Player owner, Location blockLoc, int xRadius, int zRadius,
                               String blockConfigId) {
        World world = blockLoc.getWorld();
        if (world == null) return null;

        RegionManager rm = getRM(world);
        if (rm == null) return null;

        String safeName = owner.getName().toLowerCase().replaceAll("[^a-z0-9]", "");
        String baseId   = "Ryx-" + safeName;
        String regionId = baseId;
        int n = 2;
        while (rm.getRegion(regionId) != null || regions.containsKey(regionId))
            regionId = baseId + "_" + n++;

        int x = blockLoc.getBlockX(), y = blockLoc.getBlockY(), z = blockLoc.getBlockZ();
        BlockVector3 min = BlockVector3.at(x - xRadius, world.getMinHeight(), z - zRadius);
        BlockVector3 max = BlockVector3.at(x + xRadius, world.getMaxHeight(), z + zRadius);

        // Verificar distancia mínima entre bloques de protección
        int minDist = plugin.getConfig().getInt("protection.min_distance", 20);
        for (RegionRecord existing : regions.values()) {
            if (existing.blockLoc == null) continue;
            if (!existing.worldName.equals(world.getName())) continue;
            double dist = blockLoc.distance(existing.blockLoc);
            if (dist < minDist) return null; // demasiado cerca
        }

        ProtectedCuboidRegion newReg = new ProtectedCuboidRegion(regionId, min, max);
        for (ProtectedRegion existing : rm.getApplicableRegions(newReg)) {
            if (regions.containsKey(existing.getId())
                    && !existing.getOwners().contains(owner.getUniqueId()))
                return null;
        }
        for (ProtectedRegion at : rm.getApplicableRegions(BukkitAdapter.asBlockVector(blockLoc))) {
            if (regions.containsKey(at.getId())) return null;
        }

        ProtectedCuboidRegion wgRegion = new ProtectedCuboidRegion(regionId, min, max);
        wgRegion.getOwners().addPlayer(owner.getUniqueId());
        // [FIX] PvP desactivado por defecto — evita pérdida rápida de DTR
        // por combate accidental/no deseado en regiones recién creadas.
        wgRegion.setFlag(Flags.PVP,              StateFlag.State.DENY);
        wgRegion.setFlag(Flags.TNT,              StateFlag.State.DENY);
        wgRegion.setFlag(Flags.CREEPER_EXPLOSION,StateFlag.State.DENY);
        wgRegion.setFlag(Flags.WITHER_DAMAGE,    StateFlag.State.DENY);
        wgRegion.setFlag(Flags.GHAST_FIREBALL,   StateFlag.State.DENY);
        rm.addRegion(wgRegion);

        RegionRecord record = new RegionRecord(
                regionId, owner.getUniqueId(), owner.getName(),
                world.getName(), blockLoc, blockConfigId,
                Txt.sc(owner.getName()), xRadius, zRadius);
        regions.put(regionId, record);
        indexPlayer(owner.getUniqueId(), regionId);
        if (record.blockLoc != null) blockLocIndex.put(locKey(record.blockLoc), regionId);
        save();

        // [FIX] Spawn hologram 2 ticks después (bloque físico ya colocado)
        final String finalRegionId = regionId;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getHologramManager() != null)
                plugin.getHologramManager().spawnRegionHologram(finalRegionId);
        }, 2L);

        return regionId;
    }

    public boolean removeRegion(String regionId, Player actor) {
        RegionRecord record = regions.get(regionId);
        if (record == null) return false;
        if (!record.ownerUUID.equals(actor.getUniqueId())
                && !actor.hasPermission("ryx.admin")) return false;

        World world = Bukkit.getWorld(record.worldName);
        if (world != null) {
            RegionManager rm = getRM(world);
            if (rm != null) rm.removeRegion(regionId);

            // [FIX] Drop del bloque ANTES de poner AIR — garantiza el ítem siempre
            if (record.blockLoc != null && record.blockLoc.getWorld() != null) {
                org.bukkit.block.Block b = record.blockLoc.getBlock();
                if (com.ryxmc.protection.config.ProtectionBlock.isOreMaterial(b.getType())) {
                    int lvl = plugin.getUpgradeManager() != null
                            ? plugin.getUpgradeManager().getLevel(regionId) : 1;
                    org.bukkit.inventory.ItemStack drop =
                            com.ryxmc.protection.config.ProtectionBlock.buildItem(plugin, lvl);
                    world.dropItemNaturally(record.blockLoc.clone().add(0.5, 0.5, 0.5), drop);
                    world.spawnParticle(org.bukkit.Particle.SMOKE,
                            record.blockLoc.clone().add(0.5, 1.0, 0.5), 20, 0.3, 0.3, 0.3, 0.04);
                    world.playSound(record.blockLoc, org.bukkit.Sound.BLOCK_ANVIL_BREAK, 1f, 0.8f);
                    b.setType(Material.AIR, false);
                }
            }
        }

        // [FIX] Quitar hologram ANTES de borrar el record
        if (plugin.getHologramManager() != null)
            plugin.getHologramManager().removeHologram(regionId);
        // [FIX exploit] Guardar estado DTR del jugador ANTES de borrar el record
        if (plugin.getDtrManager() != null)
            plugin.getDtrManager().saveAndUnregister(regionId, record.ownerUUID);
        if (plugin.getDtrUpgradeManager() != null)
            plugin.getDtrUpgradeManager().removeRegion(regionId);
        if (plugin.getRegionStoreExt() != null)
            plugin.getRegionStoreExt().removeRegion(regionId);

        regions.remove(regionId);
        if (record.blockLoc != null) blockLocIndex.remove(locKey(record.blockLoc));
        unindexPlayer(record.ownerUUID, regionId);
        for (UUID u : record.roles.keySet()) unindexPlayer(u, regionId);

        save();
        return true;
    }

    /** Fuerza eliminación sin verificar el jugador (para uso admin/consola) */
    public boolean removeRegionForced(String regionId) {
        RegionRecord record = regions.get(regionId);
        if (record == null) return false;

        World world = Bukkit.getWorld(record.worldName);
        if (world != null) {
            RegionManager rm = getRM(world);
            if (rm != null) rm.removeRegion(regionId);

            // [FIX] Drop del bloque ANTES de poner AIR (admin forzado también devuelve el ítem)
            if (record.blockLoc != null && record.blockLoc.getWorld() != null) {
                org.bukkit.block.Block b = record.blockLoc.getBlock();
                if (com.ryxmc.protection.config.ProtectionBlock.isOreMaterial(b.getType())) {
                    int lvl = plugin.getUpgradeManager() != null
                            ? plugin.getUpgradeManager().getLevel(regionId) : 1;
                    org.bukkit.inventory.ItemStack drop =
                            com.ryxmc.protection.config.ProtectionBlock.buildItem(plugin, lvl);
                    world.dropItemNaturally(record.blockLoc.clone().add(0.5, 0.5, 0.5), drop);
                    world.spawnParticle(org.bukkit.Particle.SMOKE,
                            record.blockLoc.clone().add(0.5, 1.0, 0.5), 20, 0.3, 0.3, 0.3, 0.04);
                    world.playSound(record.blockLoc, org.bukkit.Sound.BLOCK_ANVIL_BREAK, 1f, 0.8f);
                    b.setType(Material.AIR, false);
                }
            }
        }

        // [FIX] Quitar hologram y limpiar datos asociados
        if (plugin.getHologramManager() != null)
            plugin.getHologramManager().removeHologram(regionId);
        if (plugin.getDtrManager() != null)
            plugin.getDtrManager().saveAndUnregister(regionId, record.ownerUUID);
        if (plugin.getDtrUpgradeManager() != null)
            plugin.getDtrUpgradeManager().removeRegion(regionId);
        if (plugin.getRegionStoreExt() != null)
            plugin.getRegionStoreExt().removeRegion(regionId);

        regions.remove(regionId);
        if (record.blockLoc != null) blockLocIndex.remove(locKey(record.blockLoc));
        unindexPlayer(record.ownerUUID, regionId);
        for (UUID u : record.roles.keySet()) unindexPlayer(u, regionId);

        save();
        return true;
    }

    /**
     * Redimensiona la región WG y actualiza el RecordRecord.
     * Llamado cuando se mejora el nivel de protección.
     */
    public boolean resizeRegion(String regionId, int newXRadius) {
        RegionRecord r = regions.get(regionId);
        if (r == null || r.blockLoc == null) return false;

        World world = Bukkit.getWorld(r.worldName);
        if (world == null) return false;

        RegionManager rm = getRM(world);
        if (rm == null) return false;

        ProtectedRegion existing = rm.getRegion(regionId);
        if (existing == null) return false;

        int x = r.blockLoc.getBlockX(), z = r.blockLoc.getBlockZ();
        BlockVector3 min = BlockVector3.at(x - newXRadius, world.getMinHeight(), z - newXRadius);
        BlockVector3 max = BlockVector3.at(x + newXRadius, world.getMaxHeight(), z + newXRadius);

        ProtectedCuboidRegion newReg = new ProtectedCuboidRegion(regionId, min, max);
        try { newReg.copyFrom(existing); } catch (Exception ignored) {}

        rm.removeRegion(regionId);
        rm.addRegion(newReg);

        // Actualizar el record
        r.xRadius = newXRadius;
        r.zRadius = newXRadius;

        save();
        return true;
    }


    // ─────────────────────────────────────────────────
    //  Transferencia de propiedad
    // ─────────────────────────────────────────────────

    /**
     * Transfiere la propiedad de una región a un nuevo dueño.
     * ownerUUID/ownerName son final → se crea un nuevo RegionRecord.
     * Los extras de DTR comprados NO se transfieren (se pierden).
     */
    public boolean transferOwner(String regionId, UUID newOwnerUUID, String newOwnerName) {
        RegionRecord old = regions.get(regionId);
        if (old == null) return false;
        if (old.ownerUUID.equals(newOwnerUUID)) return false;

        int maxOwned = plugin.getConfig().getInt("protection.max_per_player", 1);
        long ownedByNew = regions.values().stream()
                .filter(rec -> rec.ownerUUID.equals(newOwnerUUID)).count();
        if (ownedByNew >= maxOwned) return false;

        RegionRecord fresh = new RegionRecord(
                old.regionId, newOwnerUUID, newOwnerName,
                old.worldName, old.blockLoc,
                old.blockConfigId, old.displayName,
                old.xRadius, old.zRadius);
        fresh.customName  = old.customName;
        fresh.pvpEnabled  = old.pvpEnabled;
        fresh.roles.putAll(old.roles);
        fresh.blacklist.addAll(old.blacklist);
        fresh.roles.remove(newOwnerUUID);

        World world = Bukkit.getWorld(old.worldName);
        if (world != null) {
            RegionManager rm = getRM(world);
            if (rm != null) {
                ProtectedRegion wr = rm.getRegion(regionId);
                if (wr != null) {
                    wr.getOwners().removePlayer(old.ownerUUID);
                    wr.getOwners().addPlayer(newOwnerUUID);
                }
            }
        }

        unindexPlayer(old.ownerUUID, regionId);
        indexPlayer(newOwnerUUID, regionId);
        if (fresh.roles.containsKey(old.ownerUUID))
            indexPlayer(old.ownerUUID, regionId);

        regions.put(regionId, fresh);
        save();

        if (plugin.getDtrUpgradeManager() != null)
            plugin.getDtrUpgradeManager().removeRegion(regionId);
        if (plugin.getHologramManager() != null)
            plugin.getHologramManager().updateRegionHologram(regionId);

        return true;
    }

    // ─────────────────────────────────────────────────
    //  Búsqueda por jugador
    // ─────────────────────────────────────────────────

    public List<String> getRegionsByPlayer(UUID playerUUID) {
        Set<String> indexed = playerIndex.get(playerUUID);
        if (indexed == null || indexed.isEmpty()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (String rid : indexed) if (regions.containsKey(rid)) result.add(rid);
        return result;
    }

    public List<String> getOwnedByPlayer(UUID playerUUID) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, RegionRecord> e : regions.entrySet())
            if (e.getValue().ownerUUID.equals(playerUUID)) result.add(e.getKey());
        return result;
    }

    // ─────────────────────────────────────────────────
    //  Miembros y roles
    // ─────────────────────────────────────────────────

    public boolean addMember(String regionId, UUID memberUUID, World world) {
        RegionRecord record = regions.get(regionId);
        if (record == null) return false;
        RegionManager rm = getRM(world);
        if (rm != null) {
            ProtectedRegion wr = rm.getRegion(regionId);
            if (wr != null) wr.getMembers().addPlayer(memberUUID);
        }
        if (!record.roles.containsKey(memberUUID))
            record.roles.put(memberUUID, ProtectionRole.MEMBER);
        indexPlayer(memberUUID, regionId);
        save();
        plugin.getDtrManager().recalcMax(regionId);
        return true;
    }

    public boolean removeMember(String regionId, UUID memberUUID, World world) {
        RegionRecord record = regions.get(regionId);
        if (record == null) return false;
        RegionManager rm = getRM(world);
        if (rm != null) {
            ProtectedRegion wr = rm.getRegion(regionId);
            if (wr != null) wr.getMembers().removePlayer(memberUUID);
        }
        record.roles.remove(memberUUID);
        unindexPlayer(memberUUID, regionId);
        save();
        plugin.getDtrManager().recalcMax(regionId);
        return true;
    }

    public void setRole(String regionId, UUID memberUUID, ProtectionRole role) {
        RegionRecord record = regions.get(regionId);
        if (record == null || role == ProtectionRole.OWNER) return;
        record.roles.put(memberUUID, role);
        indexPlayer(memberUUID, regionId);
        save();
    }

    public ProtectionRole getRole(String regionId, UUID playerUUID) {
        RegionRecord r = regions.get(regionId);
        if (r == null) return null;
        if (r.ownerUUID.equals(playerUUID)) return ProtectionRole.OWNER;
        return r.roles.getOrDefault(playerUUID, null);
    }

    // ─────────────────────────────────────────────────
    //  Lista negra
    // ─────────────────────────────────────────────────

    public void addBlacklist(String regionId, UUID uuid) {
        RegionRecord r = regions.get(regionId);
        if (r != null) { r.blacklist.add(uuid); save(); }
    }

    public void removeBlacklist(String regionId, UUID uuid) {
        RegionRecord r = regions.get(regionId);
        if (r != null) { r.blacklist.remove(uuid); save(); }
    }

    public boolean isBlacklisted(String regionId, UUID uuid) {
        RegionRecord r = regions.get(regionId);
        return r != null && r.blacklist.contains(uuid);
    }

    // ─────────────────────────────────────────────────
    //  PvP
    // ─────────────────────────────────────────────────

    public void setPvP(String regionId, boolean enabled) {
        RegionRecord r = regions.get(regionId);
        if (r == null) return;
        r.pvpEnabled = enabled;
        World world = Bukkit.getWorld(r.worldName);
        if (world != null) {
            RegionManager rm = getRM(world);
            if (rm != null) {
                ProtectedRegion wr = rm.getRegion(regionId);
                if (wr != null)
                    wr.setFlag(Flags.PVP, enabled ? StateFlag.State.ALLOW : StateFlag.State.DENY);
            }
        }
        save();
    }

    public boolean isPvPEnabled(String regionId) {
        RegionRecord r = regions.get(regionId);
        return r == null || r.pvpEnabled;
    }

    // ─────────────────────────────────────────────────
    //  Estado raideable
    // ─────────────────────────────────────────────────

    public void setRaidable(String regionId, boolean raidable) {
        RegionRecord r = regions.get(regionId);
        if (r == null) return;
        World world = Bukkit.getWorld(r.worldName);
        if (world == null) return;
        RegionManager rm = getRM(world);
        if (rm == null) return;
        ProtectedRegion wr = rm.getRegion(regionId);
        if (wr == null) return;
        if (raidable) {
            wr.setFlag(Flags.BUILD, StateFlag.State.ALLOW);
            wr.setFlag(Flags.PVP,   StateFlag.State.ALLOW);
        } else {
            wr.setFlag(Flags.BUILD, null);
            wr.setFlag(Flags.PVP, r.pvpEnabled ? StateFlag.State.ALLOW : StateFlag.State.DENY);
        }
    }

    // ─────────────────────────────────────────────────
    //  Nombre / Home
    // ─────────────────────────────────────────────────

    public void setCustomName(String regionId, String name) {
        RegionRecord r = regions.get(regionId);
        if (r != null) { r.customName = name; save(); }
    }

    public void setHome(String regionId, Location loc) {
        RegionRecord r = regions.get(regionId);
        if (r == null) return;
        // Actualizar índice: quitar clave vieja, añadir nueva
        if (r.blockLoc != null) blockLocIndex.remove(locKey(r.blockLoc));
        r.blockLoc = loc;
        if (loc != null) blockLocIndex.put(locKey(loc), regionId);
        save();
    }

    // ✅ BUG #4 FIX — método que necesita BlockUpgradeManager
    //    blockConfigId ya NO es final en RegionRecord (ver abajo)
    public void setBlockConfigId(String regionId, String newBlockConfigId) {
        RegionRecord r = regions.get(regionId);
        if (r == null) return;
        r.blockConfigId = newBlockConfigId;
        save();
    }

    // ─────────────────────────────────────────────────
    //  Consultas
    // ─────────────────────────────────────────────────

    public boolean exists(String regionId)           { return regions.containsKey(regionId); }
    public RegionRecord getRecord(String regionId)   { return regions.get(regionId); }

    public String getDisplayName(String regionId) {
        RegionRecord r = regions.get(regionId);
        if (r == null) return regionId;
        if (r.customName != null && !r.customName.isEmpty()) return r.customName;
        return r.displayName;
    }

    public String getOwnerName(String regionId) {
        RegionRecord r = regions.get(regionId);
        return r != null ? r.ownerName : "?";
    }

    public String getOwnerUUID(String regionId) {
        RegionRecord r = regions.get(regionId);
        return r != null ? r.ownerUUID.toString() : null;
    }

    public int getMemberCount(String regionId) {
        RegionRecord r = regions.get(regionId);
        return r != null ? r.roles.size() : 0;
    }

    public RegionRecord getAt(Location loc) {
        World world = loc.getWorld();
        if (world == null) return null;
        RegionManager rm = getRM(world);
        if (rm == null) return null;
        for (ProtectedRegion wr : rm.getApplicableRegions(BukkitAdapter.asBlockVector(loc))) {
            RegionRecord r = regions.get(wr.getId());
            if (r != null) return r;
        }
        return null;
    }

    /** Búsqueda O(1) por coordenada del bloque físico (usa índice interno). */
    public RegionRecord getByBlockLoc(Location loc) {
        String key = locKey(loc);
        String rid = blockLocIndex.get(key);
        return rid != null ? regions.get(rid) : null;
    }

    public Collection<RegionRecord> getAll() {
        return Collections.unmodifiableCollection(regions.values());
    }

    // ─────────────────────────────────────────────────
    //  Índice de jugador
    // ─────────────────────────────────────────────────

    private void indexPlayer(UUID uuid, String regionId) {
        playerIndex.computeIfAbsent(uuid, k -> new HashSet<>()).add(regionId);
    }

    private void unindexPlayer(UUID uuid, String regionId) {
        Set<String> s = playerIndex.get(uuid);
        if (s != null) s.remove(regionId);
    }

    private void rebuildIndex() {
        playerIndex.clear();
        blockLocIndex.clear();
        for (Map.Entry<String, RegionRecord> e : regions.entrySet()) {
            RegionRecord r = e.getValue();
            indexPlayer(r.ownerUUID, e.getKey());
            for (UUID u : r.roles.keySet()) indexPlayer(u, e.getKey());
            if (r.blockLoc != null) blockLocIndex.put(locKey(r.blockLoc), e.getKey());
        }
    }

    // ─────────────────────────────────────────────────
    //  WorldGuard
    // ─────────────────────────────────────────────────

    public RegionManager getRM(World world) {
        try {
            return WorldGuard.getInstance().getPlatform().getRegionContainer()
                    .get(BukkitAdapter.adapt(world));
        } catch (Exception e) { return null; }
    }

    // ─────────────────────────────────────────────────
    //  Persistencia
    // ─────────────────────────────────────────────────

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, RegionRecord> e : regions.entrySet()) {
            String path = "regions." + e.getKey();
            RegionRecord r = e.getValue();
            cfg.set(path + ".ownerUUID",    r.ownerUUID.toString());
            cfg.set(path + ".ownerName",    r.ownerName);
            cfg.set(path + ".world",        r.worldName);
            cfg.set(path + ".displayName",  r.displayName);
            cfg.set(path + ".customName",   r.customName);
            cfg.set(path + ".blockConfigId",r.blockConfigId);
            cfg.set(path + ".pvpEnabled",   r.pvpEnabled);
            cfg.set(path + ".xRadius",      r.xRadius);
            cfg.set(path + ".zRadius",      r.zRadius);
            if (r.blockLoc != null) {
                cfg.set(path + ".bx", r.blockLoc.getBlockX());
                cfg.set(path + ".by", r.blockLoc.getBlockY());
                cfg.set(path + ".bz", r.blockLoc.getBlockZ());
            }
            for (Map.Entry<UUID, ProtectionRole> re : r.roles.entrySet())
                cfg.set(path + ".roles." + re.getKey(), re.getValue().name());
            List<String> bl = new ArrayList<>();
            for (UUID u : r.blacklist) bl.add(u.toString());
            cfg.set(path + ".blacklist", bl);
        }
        try { cfg.save(saveFile); }
        catch (IOException ex) { plugin.getLogger().severe("[RegionStore] Error guardando: " + ex.getMessage()); }
    }

    private void load() {
        if (!saveFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(saveFile);
        if (!cfg.isConfigurationSection("regions")) return;

        for (String regionId : cfg.getConfigurationSection("regions").getKeys(false)) {
            String path = "regions." + regionId;
            try {
                UUID   ownerUUID = UUID.fromString(cfg.getString(path + ".ownerUUID", ""));
                String ownerName = cfg.getString(path + ".ownerName", "?");
                String worldName = cfg.getString(path + ".world", "world");
                String dispName  = cfg.getString(path + ".displayName", Txt.sc(ownerName));
                String custName  = cfg.getString(path + ".customName", null);
                String bcId      = cfg.getString(path + ".blockConfigId", "block1");
                boolean pvp      = cfg.getBoolean(path + ".pvpEnabled", true);
                int xr           = cfg.getInt(path + ".xRadius", 10);
                int zr           = cfg.getInt(path + ".zRadius", 10);

                World world = Bukkit.getWorld(worldName);
                Location loc = null;
                if (cfg.contains(path + ".bx")) {
                    // ✅ Construir Location aunque world sea null (puede estar cargando tarde)
                    loc = new Location(world,
                            cfg.getInt(path + ".bx"),
                            cfg.getInt(path + ".by"),
                            cfg.getInt(path + ".bz"));
                }

                RegionRecord r = new RegionRecord(regionId, ownerUUID, ownerName,
                        worldName, loc, bcId, dispName, xr, zr);
                r.customName = custName;
                r.pvpEnabled = pvp;

                if (cfg.isConfigurationSection(path + ".roles")) {
                    for (String uuidStr : cfg.getConfigurationSection(path + ".roles").getKeys(false)) {
                        try {
                            UUID mu = UUID.fromString(uuidStr);
                            ProtectionRole role = ProtectionRole.fromString(
                                    cfg.getString(path + ".roles." + uuidStr, "MEMBER"));
                            r.roles.put(mu, role);
                        } catch (Exception ignored) {}
                    }
                }
                for (String uuidStr : cfg.getStringList(path + ".blacklist")) {
                    try { r.blacklist.add(UUID.fromString(uuidStr)); } catch (Exception ignored) {}
                }

                regions.put(regionId, r);
            } catch (Exception ex) {
                plugin.getLogger().warning("[RegionStore] Error cargando " + regionId + ": " + ex.getMessage());
            }
        }
        rebuildIndex();
    }

    // ─────────────────────────────────────────────────
    //  RegionRecord
    // ─────────────────────────────────────────────────

    public static class RegionRecord {
        public final String regionId;
        public final UUID   ownerUUID;
        public final String ownerName;
        public final String worldName;
        public Location blockLoc;

        // ✅ BUG #4 FIX — era "final", ahora mutable para soportar upgrades de bloque
        public String blockConfigId;

        public String  displayName;
        public String  customName;
        // [FIX] Default DESACTIVADO — el dueño debe activarlo explícitamente
        // con /ps pvp para evitar perder DTR por combate no deseado.
        public boolean pvpEnabled = false;
        public int xRadius, zRadius; // mutable — se actualiza en upgrades de radio

        public final Map<UUID, ProtectionRole> roles     = new LinkedHashMap<>();
        public final Set<UUID>                 blacklist = new HashSet<>();

        public RegionRecord(String regionId, UUID ownerUUID, String ownerName,
                            String worldName, Location blockLoc,
                            String blockConfigId, String displayName,
                            int xRadius, int zRadius) {
            this.regionId      = regionId;
            this.ownerUUID     = ownerUUID;
            this.ownerName     = ownerName;
            this.worldName     = worldName;
            this.blockLoc      = blockLoc;
            this.blockConfigId = blockConfigId;
            this.displayName   = displayName;
            this.xRadius       = xRadius;
            this.zRadius       = zRadius;
        }
    }
}
