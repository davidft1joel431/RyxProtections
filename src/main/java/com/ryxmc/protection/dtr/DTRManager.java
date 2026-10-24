package com.ryxmc.protection.dtr;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * DTRManager v2 — Sistema DTR completamente funcional.
 *
 * BUGS CORREGIDOS:
 *  BUG #2 — save/load corrompía los IDs de región:
 *    ANTES: save hacía replace(".", "_") y load hacía replace("_", ".")
 *    → Un ID como "ryx_abraham" (con guión bajo, sin puntos) se guardaba
 *      como "ryx_abraham" pero se cargaba como "ryx.abraham" → DTR perdido al reiniciar.
 *    AHORA: el regionId se guarda como campo explícito "regionId" en el YAML,
 *      eliminando la ambigüedad de transformación de caracteres.
 */
public class DTRManager {

    private final RyxProtection plugin;

    /** regionId → DTRData */
    private final Map<String, DTRData> data = new LinkedHashMap<>();

    private BukkitTask cooldownTask;
    private BukkitTask regenTask;
    private File       saveFile;

    private static final long COOLDOWN_CHECK_TICKS = 200L;  // 10s
    private static final long REGEN_TICKS          = 2400L; // 2min

    public DTRManager(RyxProtection plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────────────────────────────────
    //  Init / Shutdown
    // ─────────────────────────────────────────────────

    public void init() {
        saveFile = new File(plugin.getDataFolder(), "dtr.yml");
        load();
        startTasks();
        plugin.getLogger().info("[DTR] Iniciado. Regiones: " + data.size());
    }

    public void shutdown() {
        stopTasks();
        save();
    }

    public void reload() {
        stopTasks();
        data.clear();
        load();
        startTasks();
        recalcAllMax();
    }

    // ─────────────────────────────────────────────────
    //  Registro de regiones
    // ─────────────────────────────────────────────────

    public DTRData register(String regionId, double baseDtr) {
        DTRData d = new DTRData(baseDtr);
        data.put(regionId, d);
        plugin.getLogger().info("[DTR] Región registrada: " + regionId + " maxDtr=" + baseDtr);
        return d;
    }

    public void unregister(String regionId) {
        data.remove(regionId);
        plugin.getLogger().info("[DTR] Región removida del DTR: " + regionId);
    }

    /**
     * [FIX exploit] Guarda el estado DTR ANTES de desregistrar.
     * Llamado desde RegionStore.removeRegion() con ownerUUID directo del record,
     * ANTES de regions.remove() — así el ownerUUID siempre es válido.
     */
    public void saveAndUnregister(String regionId, UUID ownerUUID) {
        DTRData dtr = data.get(regionId);
        if (dtr != null && plugin.getPlayerDtrRegistry() != null && ownerUUID != null)
            plugin.getPlayerDtrRegistry().onRegionRemoved(ownerUUID, dtr);
        data.remove(regionId);
        plugin.getLogger().info("[DTR] Región removida del DTR: " + regionId
                + (ownerUUID != null ? " (owner=" + ownerUUID + ")" : ""));
    }

    public DTRData get(String regionId) {
        return data.computeIfAbsent(regionId, id -> new DTRData(DTRData.BASE_DTR));
    }

    public boolean exists(String regionId) {
        return data.containsKey(regionId);
    }

    // ─────────────────────────────────────────────────
    //  Penalidad de muerte
    // ─────────────────────────────────────────────────

    public void applyDeath(Player player, List<String> regionIds) {
        if (regionIds.isEmpty()) return;
        if (player.hasPermission("ryx.dtr.bypass")) {
            // ✅ Ahora siempre logueamos el bypass para diagnóstico
            plugin.getLogger().info("[DTR] BYPASS: " + player.getName()
                    + " murió con ryx.dtr.bypass — sin penalidad.");
            return;
        }

        for (String regionId : regionIds) {
            DTRData d = get(regionId);
            boolean becameRaidable = d.applyDeath();
            String regionName = plugin.getRegionStore().getDisplayName(regionId);

            if (becameRaidable) {
                String ownerName = plugin.getRegionStore().getOwnerName(regionId);
                if (plugin.getConfig().getBoolean("dtr.broadcast_raidable", true)) {
                    Bukkit.broadcastMessage(Txt.err("¡La región §e" + regionName
                            + " §cde §f" + ownerName + "§c es ahora §4§lRAIDEABLE§c!"));
                }
                plugin.getRegionStore().setRaidable(regionId, true);
                msg(player, Txt.err("¡Tu región §e" + regionName + "§c llegó a §40 DTR§c!"));
                msg(player, Txt.warn("Espera §f10 minutos§e sin morir para iniciar la regeneración."));

            } else if (d.isInCooldown()) {
                msg(player, Txt.err("¡Moriste durante el período de espera! El cooldown de §f10 min§c se reinició."));

            } else {
                msg(player, Txt.warn("DTR de §e" + regionName + "§e → " + d.formatted()
                        + " §8(−" + DTRData.DEATH_PENALTY + " por muerte)"));
            }

            plugin.getLogger().info("[DTR] MUERTE: " + player.getName()
                    + " | región=" + regionId
                    + " | dtr=" + String.format("%.1f", d.getDtr())
                    + " | raideable=" + d.isRaidable());

            scheduleHologramUpdate(regionId);
        }
    }

    // ─────────────────────────────────────────────────
    //  Operaciones admin / gemas
    // ─────────────────────────────────────────────────

    public void reset(String regionId) {
        DTRData d = get(regionId);
        boolean wasRaidable = d.isRaidable();
        d.reset();
        if (wasRaidable) {
            plugin.getRegionStore().setRaidable(regionId, false);
            announceRestored(regionId);
        }
        // [FIX] Limpiar registro del jugador inmediatamente — DTR volvió al máximo
        clearRegistryForOwner(regionId, d);
        scheduleHologramUpdate(regionId);
    }

    public void set(String regionId, double value) {
        DTRData d = get(regionId);
        boolean wasRaidable = d.isRaidable();
        d.setDtr(value);
        if (!wasRaidable && d.isRaidable()) {
            plugin.getRegionStore().setRaidable(regionId, true);
            announceRaidable(regionId);
        } else if (wasRaidable && !d.isRaidable()) {
            plugin.getRegionStore().setRaidable(regionId, false);
            announceRestored(regionId);
        }
        // [FIX] Si el admin puso el DTR al máximo, limpiar registro
        if (d.getDtr() >= d.getMaxDtr()) clearRegistryForOwner(regionId, d);
        scheduleHologramUpdate(regionId);
    }

    public void addEnergy(String regionId, double amount) {
        DTRData d = get(regionId);
        boolean wasRaidable = d.isRaidable();
        double newDtr = Math.min(d.getMaxDtr(), d.getDtr() + amount);
        d.setDtr(newDtr);
        if (wasRaidable && !d.isRaidable()) {
            plugin.getRegionStore().setRaidable(regionId, false);
            announceRestored(regionId);
        }
        // [FIX] Si el DTR llegó al máximo, limpiar registro del jugador
        if (d.getDtr() >= d.getMaxDtr()) clearRegistryForOwner(regionId, d);
        scheduleHologramUpdate(regionId);
    }

    public void recalcMax(String regionId) {
        DTRData d = get(regionId);
        double baseDtr = plugin.getUpgradeManager().getBaseDtr(regionId);
        int regionMembers = plugin.getRegionStore().getMemberCount(regionId);
        int clanMembers = 0;
        UUID ownerUuidObj = null;
        String ownerUUID = plugin.getRegionStore().getOwnerUUID(regionId);
        if (ownerUUID != null) {
            try {
                ownerUuidObj = UUID.fromString(ownerUUID);
                clanMembers = plugin.getTeamHook().getTeammateCount(ownerUuidObj);
            } catch (Exception ignored) {}
        }
        double dtrExtras = plugin.getDtrUpgradeManager() != null
                ? plugin.getDtrUpgradeManager().getExtraMaxDtr(regionId) : 0;
        d.recalcMaxWithExtras(baseDtr, regionMembers, clanMembers, dtrExtras);
        // [FIX exploit] Aplicar deuda DTR del jugador DESPUÉS de fijar maxDtr
        if (ownerUuidObj != null && plugin.getPlayerDtrRegistry() != null)
            plugin.getPlayerDtrRegistry().applyToNewRegion(ownerUuidObj, regionId);
        plugin.getLogger().info("[DTR] recalcMax " + regionId
                + " maxDtr=" + String.format("%.1f", d.getMaxDtr())
                + " dtr=" + String.format("%.1f", d.getDtr())
                + " base=" + baseDtr + " mem=" + regionMembers
                + " clan=" + clanMembers + " extras=+" + String.format("%.1f", dtrExtras));
    }

    public void recalcAllMax() {
        int count = 0;
        for (String regionId : new ArrayList<>(data.keySet())) {
            try {
                DTRData d = get(regionId);
                double baseDtr = plugin.getUpgradeManager().getBaseDtr(regionId);
                int regionMembers = plugin.getRegionStore().getMemberCount(regionId);
                int clanMembers = 0;
                String ownerUUID = plugin.getRegionStore().getOwnerUUID(regionId);
                if (ownerUUID != null) {
                    try { clanMembers = plugin.getTeamHook()
                            .getTeammateCount(UUID.fromString(ownerUUID)); }
                    catch (Exception ignored) {}
                }
                double dtrExtras = plugin.getDtrUpgradeManager() != null
                        ? plugin.getDtrUpgradeManager().getExtraMaxDtr(regionId) : 0;
                d.recalcMaxWithExtras(baseDtr, regionMembers, clanMembers, dtrExtras);
                count++;
            } catch (Exception e) {
                plugin.getLogger().warning("[DTR] Error recalcMax " + regionId + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("[DTR] recalcAllMax completado — " + count + " regiones.");
    }

    // ─────────────────────────────────────────────────
    //  Tareas periódicas
    // ─────────────────────────────────────────────────

    private void startTasks() {
        cooldownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<String, DTRData> e : new HashMap<>(data).entrySet()) {
                DTRData d = e.getValue();
                if (d.checkCooldownExpired()) {
                    String name = plugin.getRegionStore().getDisplayName(e.getKey());
                    notifyOwner(e.getKey(),
                            Txt.ok("¡El período de espera de §e" + name + "§a terminó! El DTR comenzará a regenerarse."));
                }
                // [FIX] Verificar cada 10s si el DTR está lleno para limpiar registro
                // Así no hay que esperar 2 minutos (regenTask) para que se limpie
                if (d.getDtr() >= d.getMaxDtr() && !d.isRaidable() && !d.isInCooldown())
                    clearRegistryForOwner(e.getKey(), d);
            }
        }, COOLDOWN_CHECK_TICKS, COOLDOWN_CHECK_TICKS);

        regenTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<String, DTRData> e : new HashMap<>(data).entrySet()) {
                DTRData d = e.getValue();
                boolean stoppedRaid = d.applyRegen();
                if (stoppedRaid) {
                    plugin.getRegionStore().setRaidable(e.getKey(), false);
                    announceRestored(e.getKey());
                }
                // [FIX] Limpiar registro si DTR llegó al máximo
                if (d.getDtr() >= d.getMaxDtr() && !d.isRaidable() && !d.isInCooldown())
                    clearRegistryForOwner(e.getKey(), d);
                if (d.getDtr() < d.getMaxDtr()) scheduleHologramUpdate(e.getKey());
            }
        }, REGEN_TICKS, REGEN_TICKS);
    }

    private void stopTasks() {
        if (cooldownTask != null) { cooldownTask.cancel(); cooldownTask = null; }
        if (regenTask    != null) { regenTask.cancel();    regenTask    = null; }
    }

    // ─────────────────────────────────────────────────
    //  Anuncios
    // ─────────────────────────────────────────────────

    private void announceRaidable(String regionId) {
        String name  = plugin.getRegionStore().getDisplayName(regionId);
        String owner = plugin.getRegionStore().getOwnerName(regionId);
        if (plugin.getConfig().getBoolean("dtr.broadcast_raidable", true))
            Bukkit.broadcastMessage(Txt.err("¡La región §e" + name + " §cde §f" + owner + "§c es §4§lRAIDEABLE§c!"));
        plugin.getRegionStore().setRaidable(regionId, true);
    }

    private void announceRestored(String regionId) {
        String name = plugin.getRegionStore().getDisplayName(regionId);
        if (plugin.getConfig().getBoolean("dtr.broadcast_restored", true))
            Bukkit.broadcastMessage(Txt.ok("La región §e" + name + "§a ya no es raideable."));
    }

    private void notifyOwner(String regionId, String message) {
        String uuidStr = plugin.getRegionStore().getOwnerUUID(regionId);
        if (uuidStr == null) return;
        try {
            Player owner = Bukkit.getPlayer(UUID.fromString(uuidStr));
            if (owner != null) owner.sendMessage(message);
        } catch (Exception ignored) {}
    }

    private void scheduleHologramUpdate(String regionId) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.getHologramManager() != null)
                plugin.getHologramManager().updateRegionHologram(regionId);
        });
    }

    /**
     * Helper — limpia el registro del jugador si el DTR está completamente lleno.
     * Llamado desde reset(), addEnergy(), set(), regenTask y cooldownTask.
     */
    private void clearRegistryForOwner(String regionId, DTRData d) {
        if (plugin.getPlayerDtrRegistry() == null) return;
        String ownerUUID = plugin.getRegionStore().getOwnerUUID(regionId);
        if (ownerUUID == null) return;
        try {
            UUID uuid = UUID.fromString(ownerUUID);
            plugin.getPlayerDtrRegistry().clearIfRecovered(uuid, d);
        } catch (Exception ignored) {}
    }

    private void msg(Player player, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(message));
    }

    // ─────────────────────────────────────────────────
    //  Persistencia — BUG #2 CORREGIDO
    //  ANTES: replace(".", "_") en save y replace("_", ".") en load
    //         corrompía IDs como "ryx_abraham" → "ryx.abraham"
    //  AHORA: el regionId real se guarda como campo "id" explícito,
    //         sin ninguna transformación de caracteres.
    // ─────────────────────────────────────────────────

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        int i = 0;
        for (Map.Entry<String, DTRData> e : data.entrySet()) {
            // ✅ Clave numérica secuencial — evita cualquier colisión de caracteres
            String path = "dtr." + i;
            DTRData d   = e.getValue();
            cfg.set(path + ".id",              e.getKey()); // ID real sin transformar
            cfg.set(path + ".dtr",             d.getDtr());
            cfg.set(path + ".maxDtr",          d.getMaxDtr());
            cfg.set(path + ".raidable",        d.isRaidable());
            cfg.set(path + ".inCooldown",      d.isInCooldown());
            cfg.set(path + ".cooldownExpires", d.getCooldownExpires());
            cfg.set(path + ".regionMembers",   d.getRegionMembers());
            cfg.set(path + ".clanMembers",     d.getClanMembers());
            i++;
        }
        try { cfg.save(saveFile); }
        catch (IOException ex) { plugin.getLogger().severe("[DTR] Error guardando: " + ex.getMessage()); }
    }

    private void load() {
        if (!saveFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(saveFile);
        if (!cfg.isConfigurationSection("dtr")) return;

        for (String key : cfg.getConfigurationSection("dtr").getKeys(false)) {
            String path = "dtr." + key;
            try {
                // ✅ Leer el ID real directamente — sin transformaciones
                String regionId = cfg.getString(path + ".id", key);
                DTRData d = new DTRData(cfg.getDouble(path + ".maxDtr", DTRData.BASE_DTR));
                d.setDtrDirect(cfg.getDouble(path + ".dtr", d.getMaxDtr()));
                d.setMaxDtrDirect(cfg.getDouble(path + ".maxDtr", DTRData.BASE_DTR));
                d.setRaidable(cfg.getBoolean(path + ".raidable", false));
                d.setInCooldown(cfg.getBoolean(path + ".inCooldown", false));
                d.setCooldownExpires(cfg.getLong(path + ".cooldownExpires", 0L));
                d.setRegionMembersDirect(cfg.getInt(path + ".regionMembers", 0));
                d.setClanMembersDirect(cfg.getInt(path + ".clanMembers", 0));
                data.put(regionId, d);
            } catch (Exception ex) {
                plugin.getLogger().warning("[DTR] Error cargando entrada " + key + ": " + ex.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────────
    //  Acceso global
    // ─────────────────────────────────────────────────

    public Map<String, DTRData> getAll()       { return Collections.unmodifiableMap(data); }
    public boolean isRaidable(String regionId) { DTRData d = data.get(regionId); return d != null && d.isRaidable(); }
    public String  format(String regionId)     { return get(regionId).formatted(); }
}
