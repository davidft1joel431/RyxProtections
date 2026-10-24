package com.ryxmc.protection.manager;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.config.ProtectionBlock;
import com.ryxmc.protection.manager.RegionStore;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * PreviewManager v3.0 — Vista previa de radio con partículas.
 *
 * 【BUG FIX v3.0】
 *  ANTES: las partículas se spawneaban en online.getLocation() cada tick,
 *  por lo que seguían al jugador en X y Z al moverse. Ahora se guarda una
 *  LOCACIÓN FIJA (origin) donde se activó el preview y las partículas
 *  giran alrededor de ese punto siempre (solo Y varía para el contorno
 *  vertical). Así el contorno queda centrado en el bloque de protección.
 *
 * MODOS:
 *  HELD    — clic derecho al aire con bloque en mano (dorado)
 *  REGION  — desde InfoMenu (cian, centrado en el bloque de la región)
 *  UPGRADE — desde UpgradeMenu (morado, 10 s timeout)
 */
public class PreviewManager {

    public enum PreviewMode { HELD, REGION, UPGRADE }

    private final RyxProtection plugin;
    private final Map<UUID, BukkitTask>  tasks  = new HashMap<>();
    private final Map<UUID, PreviewMode> modes  = new HashMap<>();
    private final Map<UUID, Integer>     radii  = new HashMap<>();
    /** Ubicación FIJA donde se activó el preview (no sigue al jugador). */
    private final Map<UUID, Location>    origins= new HashMap<>();

    private static final long PARTICLE_PERIOD   = 2L;
    private static final int  POINTS_PER_SIDE   = 40;
    private static final int  UPGRADE_TIMEOUT_S = 10;

    public PreviewManager(RyxProtection plugin) { this.plugin = plugin; }

    public void init()     { plugin.getLogger().info("[Preview] v3.0 iniciado."); }
    public void shutdown() { stopAll(); }

    // ── API pública ───────────────────────────────────

    /** Preview desde el aire: guarda la ubicación del jugador en el momento del clic. */
    public void startHeldPreview(Player p, int xRadius) {
        stopPreview(p.getUniqueId());
        modes.put(p.getUniqueId(), PreviewMode.HELD);
        radii.put(p.getUniqueId(), xRadius);
        origins.put(p.getUniqueId(), p.getLocation().clone());
        startTask(p, xRadius, PreviewMode.HELD, -1);
    }

    /** Preview de región: centrado en el bloque de protección de la región. */
    public void startRegionPreview(Player p, String regionId) {
        RegionStore.RegionRecord r = plugin.getRegionStore().getRecord(regionId);
        int xr = r != null ? plugin.getUpgradeManager().getXRadius(regionId) : 10;
        stopPreview(p.getUniqueId());
        modes.put(p.getUniqueId(), PreviewMode.REGION);
        radii.put(p.getUniqueId(), xr);
        // Centro fijo: bloque de la región (o jugador si no hay registro)
        Location center = (r != null && r.blockLoc != null)
                ? r.blockLoc.clone().add(0.5, 0, 0.5)
                : p.getLocation().clone();
        origins.put(p.getUniqueId(), center);
        startTask(p, xr, PreviewMode.REGION, -1);
    }

    public void startUpgradePreview(Player p, int nextXRadius) {
        stopPreview(p.getUniqueId());
        modes.put(p.getUniqueId(), PreviewMode.UPGRADE);
        radii.put(p.getUniqueId(), nextXRadius);
        origins.put(p.getUniqueId(), p.getLocation().clone());
        startTask(p, nextXRadius, PreviewMode.UPGRADE, UPGRADE_TIMEOUT_S);
    }

    public void stopPreview(UUID uuid) {
        BukkitTask t = tasks.remove(uuid);
        if (t != null && !t.isCancelled()) t.cancel();
        modes.remove(uuid);
        radii.remove(uuid);
        origins.remove(uuid);
    }

    public void stopAll() {
        tasks.values().forEach(t -> { if (!t.isCancelled()) t.cancel(); });
        tasks.clear(); modes.clear(); radii.clear(); origins.clear();
    }

    public boolean hasPreview(UUID uuid)  { return tasks.containsKey(uuid); }
    public PreviewMode getMode(UUID uuid) { return modes.get(uuid); }
    public int getRadius(UUID uuid)       { return radii.getOrDefault(uuid, 0); }

    // ── Motor de partículas ───────────────────────────

    private void startTask(Player p, int xRadius, PreviewMode mode, int timeoutSec) {
        final UUID   uuid      = p.getUniqueId();
        final long   maxTicks  = timeoutSec > 0 ? (long)(timeoutSec * 20) : Long.MAX_VALUE;
        final long[] counter   = {0};
        final Location origin  = origins.get(uuid) != null
                ? origins.get(uuid).clone()
                : p.getLocation().clone();

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online == null || !online.isOnline()) { stopPreview(uuid); return; }
            counter[0] += PARTICLE_PERIOD;
            if (counter[0] >= maxTicks) { stopPreview(uuid); return; }

            // Cancelar HELD si ya no tiene el bloque en mano
            if (mode == PreviewMode.HELD
                    && !ProtectionBlock.isProtectionBlock(plugin,
                            online.getInventory().getItemInMainHand())) {
                stopPreview(uuid); return;
            }

            // Centro FIJO = origin (no la posición actual del jugador)
            spawnBorder(online, origin, xRadius, mode);
        }, 1L, PARTICLE_PERIOD);

        tasks.put(uuid, task);
    }

    /**
     * Dibuja el contorno cuadrado alrededor de {@code center} (ubicación fija).
     * Las partículas ahora giran SIEMPRE alrededor del mismo punto X/Z,
     * sin seguir al jugador al moverse.
     */
    private void spawnBorder(Player p, Location center, int xr, PreviewMode mode) {
        World world = center.getWorld();
        if (world == null) return;
        // Y fija en la altura del bloque (centro del bloque + pequeño offset)
        double y  = center.getY() + 0.15;
        double cx = center.getX(), cz = center.getZ();

        org.bukkit.Color color = switch (mode) {
            case HELD    -> org.bukkit.Color.fromRGB(0xFF, 0xD7, 0x00); // dorado
            case REGION  -> org.bukkit.Color.fromRGB(0x00, 0xBF, 0xFF); // cian
            case UPGRADE -> org.bukkit.Color.fromRGB(0xAA, 0x00, 0xFF); // morado
        };
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.2f);
        double step = (xr * 2.0) / POINTS_PER_SIDE;

        for (int i = 0; i <= POINTS_PER_SIDE; i++) {
            double off = -xr + i * step;
            // Bordes laterales (X varía, Z fija en los extremos)
            world.spawnParticle(Particle.DUST, new Location(world, cx + off, y, cz - xr), 1, 0,0,0,0, dust, true);
            world.spawnParticle(Particle.DUST, new Location(world, cx + off, y, cz + xr), 1, 0,0,0,0, dust, true);
            // Bordes delanteros/traseros (Z varía, X fija en los extremos)
            world.spawnParticle(Particle.DUST, new Location(world, cx - xr, y, cz + off), 1, 0,0,0,0, dust, true);
            world.spawnParticle(Particle.DUST, new Location(world, cx + xr, y, cz + off), 1, 0,0,0,0, dust, true);
            // Partículas verticales en esquinas (profundidad)
            if (i % 10 == 0) {
                world.spawnParticle(Particle.DUST, new Location(world, cx - xr, y + 0.5, cz - xr), 1, 0,0,0,0, dust, true);
                world.spawnParticle(Particle.DUST, new Location(world, cx + xr, y + 0.5, cz + xr), 1, 0,0,0,0, dust, true);
            }
        }
    }
}
