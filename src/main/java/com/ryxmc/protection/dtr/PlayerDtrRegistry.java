package com.ryxmc.protection.dtr;

import com.ryxmc.protection.RyxProtection;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlayerDtrRegistry v1.0
 *
 * Registra el estado DTR por jugador (UUID) para evitar el exploit de:
 *   1. Tener región raideable (DTR=0)
 *   2. Eliminar la región → /ps unclaim
 *   3. Colocar nueva región → DTR se reseteaba a máximo
 *   4. Resultado: exploit para escapar de ser raideable
 *
 * ── Lógica ────────────────────────────────────────────────────────────────
 *
 *  onRegionRemoved(ownerUUID, dtrData):
 *    Llamado por DTRManager.unregister() antes de borrar el estado.
 *    Si el DTR no estaba lleno (dtr < maxDtr) O estaba raideable/cooldown,
 *    guarda el estado DTR del jugador en el registro.
 *    Si el DTR estaba lleno → no guarda nada (no había penalización).
 *
 *  applyToNewRegion(ownerUUID, newRegionId):
 *    Llamado por DTRManager después de recalcMax() cuando se crea una región.
 *    Si el jugador tiene un estado DTR guardado, lo aplica a la nueva región:
 *      - DTR actual = min(savedDtr, newMaxDtr)
 *      - Si era raideable → la nueva región también queda raideable
 *      - Si estaba en cooldown → preserva el tiempo restante del cooldown
 *
 *  clearIfRecovered(ownerUUID, dtrData):
 *    Llamado periódicamente por DTRManager durante la regeneración.
 *    Si el DTR llegó a maxDtr y ya no es raideable → borra el registro
 *    (el jugador "pagó" su deuda de DTR → limpio para la siguiente región).
 *
 * ── Persistencia ──────────────────────────────────────────────────────────
 *  data/player_dtr.yml
 *    <uuid>:
 *      dtr: <double>
 *      maxDtr: <double>
 *      raidable: <boolean>
 *      inCooldown: <boolean>
 *      cooldownExpires: <long>
 *      savedAt: <long>   ← timestamp para expiración (24h si el servidor no vuelve a cargar)
 */
public class PlayerDtrRegistry {

    private static final long EXPIRE_MS = 24L * 60 * 60 * 1000; // 24 horas

    // Estado DTR guardado por jugador
    public static class PlayerDtrState {
        public final double  dtr;
        public final double  maxDtr;
        public final boolean raidable;
        public final boolean inCooldown;
        public final long    cooldownExpires;
        public final long    savedAt;

        public PlayerDtrState(DTRData d) {
            this.dtr             = d.getDtr();
            this.maxDtr          = d.getMaxDtr();
            this.raidable        = d.isRaidable();
            this.inCooldown      = d.isInCooldown();
            this.cooldownExpires = d.getCooldownExpires();
            this.savedAt         = System.currentTimeMillis();
        }

        // Constructor para deserialización
        public PlayerDtrState(double dtr, double maxDtr, boolean raidable,
                              boolean inCooldown, long cooldownExpires, long savedAt) {
            this.dtr             = dtr;
            this.maxDtr          = maxDtr;
            this.raidable        = raidable;
            this.inCooldown      = inCooldown;
            this.cooldownExpires = cooldownExpires;
            this.savedAt         = savedAt;
        }

        /** ¿Sigue siendo relevante? (no expiró) */
        public boolean isActive() {
            return System.currentTimeMillis() - savedAt < EXPIRE_MS;
        }

        /** ¿El jugador tenía penalización DTR activa? */
        public boolean hasDebt() {
            return raidable || inCooldown || dtr < maxDtr;
        }
    }

    private final RyxProtection               plugin;
    private final File                        dataFile;
    private YamlConfiguration               data;
    private final Map<UUID, PlayerDtrState>  states = new ConcurrentHashMap<>();

    public PlayerDtrRegistry(RyxProtection plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "player_dtr.yml");
    }

    // ─────────────────────────────────────────────────
    //  Ciclo de vida
    // ─────────────────────────────────────────────────

    public void init() {
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); }
            catch (IOException e) {
                plugin.getLogger().severe("[PlayerDtrRegistry] No se pudo crear player_dtr.yml: " + e.getMessage());
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        loadFromDisk();
        plugin.getLogger().info("[PlayerDtrRegistry] Cargados " + states.size() + " registros de DTR.");
    }

    public void shutdown() { saveToDisk(); }

    // ─────────────────────────────────────────────────
    //  API principal
    // ─────────────────────────────────────────────────

    /**
     * Guardar el estado DTR del jugador cuando elimina su región.
     * Solo guarda si había penalización (dtr < max, raideable, o en cooldown).
     *
     * @param ownerUUID UUID del dueño de la región eliminada
     * @param dtr       Estado DTR actual de la región (antes de unregister)
     */
    public void onRegionRemoved(UUID ownerUUID, DTRData dtr) {
        if (dtr == null) return;

        // Si el DTR estaba lleno y sin penalización → no guardar nada
        if (dtr.getDtr() >= dtr.getMaxDtr() && !dtr.isRaidable() && !dtr.isInCooldown()) {
            plugin.getLogger().info("[PlayerDtrRegistry] " + ownerUUID
                    + " eliminó región con DTR lleno — sin deuda guardada.");
            return;
        }

        // Guardar estado con deuda
        PlayerDtrState state = new PlayerDtrState(dtr);
        states.put(ownerUUID, state);
        saveAsync();

        plugin.getLogger().info("[PlayerDtrRegistry] Guardado estado DTR para "
                + ownerUUID + ": dtr=" + String.format("%.1f", dtr.getDtr())
                + "/" + String.format("%.1f", dtr.getMaxDtr())
                + " raideable=" + dtr.isRaidable()
                + " cooldown=" + dtr.isInCooldown());
    }

    /**
     * Aplica el estado DTR guardado a la nueva región del jugador.
     * Llamar justo DESPUÉS de recalcMax() para la nueva región.
     *
     * @param ownerUUID   UUID del dueño de la nueva región
     * @param newRegionId ID de la región recién creada
     */
    public void applyToNewRegion(UUID ownerUUID, String newRegionId) {
        PlayerDtrState saved = states.get(ownerUUID);
        if (saved == null || !saved.isActive() || !saved.hasDebt()) {
            // Sin deuda o expirada → limpiar y dejar el DTR nuevo intacto
            if (saved != null) states.remove(ownerUUID);
            return;
        }

        DTRData newDtr = plugin.getDtrManager().get(newRegionId);
        if (newDtr == null) return;

        double newMaxDtr = newDtr.getMaxDtr();

        // El DTR de la nueva región = min(DTR guardado, nuevo máximo)
        // Esto evita que suban de nivel de bloque para "lavar" el DTR
        double appliedDtr = Math.min(saved.dtr, newMaxDtr);
        newDtr.setDtrDirect(appliedDtr);

        if (saved.raidable) {
            // Preservar estado raideable
            newDtr.setRaidable(true);
            newDtr.setInCooldown(saved.inCooldown);
            // Preservar tiempo restante del cooldown (si no expiró)
            if (saved.inCooldown && saved.cooldownExpires > System.currentTimeMillis()) {
                newDtr.setCooldownExpires(saved.cooldownExpires);
            } else if (saved.inCooldown) {
                // El cooldown ya expiró mientras no tenía región → iniciar regen
                newDtr.setInCooldown(false);
            }
            // Actualizar la región en RegionStore
            if (plugin.getRegionStore() != null)
                plugin.getRegionStore().setRaidable(newRegionId, true);
        } else if (saved.inCooldown) {
            newDtr.setRaidable(false);
            newDtr.setInCooldown(true);
            if (saved.cooldownExpires > System.currentTimeMillis()) {
                newDtr.setCooldownExpires(saved.cooldownExpires);
            } else {
                newDtr.setInCooldown(false);
            }
        }

        // Actualizar hologram para reflejar el DTR real
        if (plugin.getHologramManager() != null)
            plugin.getHologramManager().updateRegionHologram(newRegionId);

        plugin.getLogger().info("[PlayerDtrRegistry] Estado DTR aplicado a nueva región "
                + newRegionId + " de " + ownerUUID
                + ": dtr=" + String.format("%.1f", appliedDtr)
                + "/" + String.format("%.1f", newMaxDtr)
                + " raideable=" + saved.raidable);

        // Notificar al jugador
        org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayer(ownerUUID);
        if (online != null) {
            if (saved.raidable) {
                online.sendMessage(com.ryxmc.protection.util.Txt.err(
                        "§c⚠ §7Tu nueva región heredó el estado §c§lRAIDEABLE§7."
                        + " Debes recuperar tu ᴅᴛʀ antes de que tu región sea segura."));
            } else {
                online.sendMessage(com.ryxmc.protection.util.Txt.warn(
                        "§e⚠ §7Tu nueva región heredó tu ᴅᴛʀ previo: §e"
                        + String.format("%.1f", appliedDtr) + "§8/§f"
                        + String.format("%.1f", newMaxDtr)
                        + " §7(no se resetea al recrear la región)."));
            }
        }
    }

    /**
     * Verifica si el DTR del jugador se recuperó completamente.
     * Si es así, borra su registro → la próxima región empieza limpia.
     *
     * @param ownerUUID UUID del dueño
     * @param dtr       Estado DTR actual de su región activa
     */
    public void clearIfRecovered(UUID ownerUUID, DTRData dtr) {
        if (!states.containsKey(ownerUUID)) return;
        if (dtr == null) return;
        if (dtr.getDtr() >= dtr.getMaxDtr() && !dtr.isRaidable() && !dtr.isInCooldown()) {
            states.remove(ownerUUID);
            saveAsync();
            plugin.getLogger().info("[PlayerDtrRegistry] DTR recuperado para "
                    + ownerUUID + " — registro limpiado.");
        }
    }

    /** ¿El jugador tiene deuda de DTR pendiente? */
    public boolean hasDebt(UUID ownerUUID) {
        PlayerDtrState s = states.get(ownerUUID);
        return s != null && s.isActive() && s.hasDebt();
    }

    /** Elimina manualmente el registro de un jugador (admin). */
    public void clearPlayer(UUID ownerUUID) {
        states.remove(ownerUUID);
        saveAsync();
    }

    // ─────────────────────────────────────────────────
    //  Persistencia
    // ─────────────────────────────────────────────────

    private void loadFromDisk() {
        if (data == null) return;
        long now = System.currentTimeMillis();
        for (String uuidStr : data.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                String path = uuidStr;
                long savedAt = data.getLong(path + ".savedAt", 0L);
                // Descartar entradas expiradas al cargar
                if (now - savedAt >= EXPIRE_MS) continue;

                PlayerDtrState state = new PlayerDtrState(
                        data.getDouble(path + ".dtr",             5.0),
                        data.getDouble(path + ".maxDtr",          5.0),
                        data.getBoolean(path + ".raidable",       false),
                        data.getBoolean(path + ".inCooldown",     false),
                        data.getLong(path + ".cooldownExpires",   0L),
                        savedAt
                );
                if (state.hasDebt()) states.put(uuid, state);
            } catch (Exception e) {
                plugin.getLogger().warning("[PlayerDtrRegistry] Error cargando "
                        + uuidStr + ": " + e.getMessage());
            }
        }
    }

    private void saveToDisk() {
        if (data == null) return;
        // Limpiar YAML
        new HashSet<>(data.getKeys(false)).forEach(k -> data.set(k, null));
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, PlayerDtrState> entry : states.entrySet()) {
            PlayerDtrState s = entry.getValue();
            if (!s.isActive() || !s.hasDebt()) continue; // no guardar entradas inútiles
            if (now - s.savedAt >= EXPIRE_MS) continue;  // no guardar expiradas
            String path = entry.getKey().toString();
            data.set(path + ".dtr",             s.dtr);
            data.set(path + ".maxDtr",          s.maxDtr);
            data.set(path + ".raidable",        s.raidable);
            data.set(path + ".inCooldown",      s.inCooldown);
            data.set(path + ".cooldownExpires", s.cooldownExpires);
            data.set(path + ".savedAt",         s.savedAt);
        }
        try { data.save(dataFile); }
        catch (IOException e) {
            plugin.getLogger().severe("[PlayerDtrRegistry] Error al guardar: " + e.getMessage());
        }
    }

    private void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveToDisk);
    }
}
