package com.ryxmc.protection.factory;

import com.ryxmc.protection.RyxProtection;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FactoryTransportAnimator v1.0 — Animaciones de transporte entre fábricas y compresores.
 *
 * Cada sistema tiene su propia animación única de partículas:
 *  • Fábrica de Energía → Compresor: partículas naranja/amarilla (minerales fundiéndose)
 *  • Compresor → Compresor: partículas púrpura (compresión de gemas)
 *  • Fábrica Industrial → Dropper: partículas verde/azul (procesamiento industrial)
 *  • Compresor → Cofre: partículas dorada (entrega de gemas)
 *
 * Las partículas se mueven en línea recta desde el origen al destino,
 * simulando el transporte físico de materiales a través de tubos.
 */
public class FactoryTransportAnimator {

    private final RyxProtection plugin;
    /** Clave del transporte → tarea activa */
    private final Map<String, BukkitTask> activeTransports = new ConcurrentHashMap<>();

    public FactoryTransportAnimator(RyxProtection plugin) { this.plugin = plugin; }

    public void init() {
        plugin.getLogger().info("[TransportAnimator] Iniciado.");
    }

    public void shutdown() {
        activeTransports.values().forEach(t -> { if (!t.isCancelled()) t.cancel(); });
        activeTransports.clear();
    }

    // ─────────────────────────────────────────────────
    //  Animaciones de transporte únicas por sistema
    // ─────────────────────────────────────────────────

    /**
     * Animación: Fábrica de Energía entrega gemas al compresor.
     * Partículas naranja/amarilla simulando minerales fundiéndose y viajando.
     */
    public void animateFactoryToCompressor(Location from, Location to, int amount) {
        String key = "fac2comp_" + from.getWorld().getName() + "_" + from.getBlockX()
                + "_" + from.getBlockY() + "_" + from.getBlockZ() + "_"
                + to.getBlockX() + "_" + to.getBlockY() + "_" + to.getBlockZ();
        if (activeTransports.containsKey(key)) return;

        World world = from.getWorld();
        if (world == null) return;

        Vector direction = to.clone().subtract(from).toVector().normalize();
        double distance = from.distance(to);
        int steps = Math.max(4, (int) distance);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (int i = 0; i <= steps; i++) {
                double t = (double) i / steps;
                Location pos = from.clone().add(direction.clone().multiply(distance * t));
                // Partículas de mineral fundido (naranja/amarillo)
                world.spawnParticle(Particle.DUST,
                        pos.getX(), pos.getY() + 0.3, pos.getZ(),
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 140, 0), 1.0f), true);
                if (i % 3 == 0) {
                    world.spawnParticle(Particle.FLAME, pos.getX(), pos.getY() + 0.3, pos.getZ(),
                            1, 0.05, 0.05, 0.05, 0.01);
                }
            }
        }, 0L, 3L);

        activeTransports.put(key, task);
        // Cancelar después de 1 segundo
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (task != null && !task.isCancelled()) task.cancel();
            activeTransports.remove(key);
        }, 20L);
    }

    /**
     * Animación: Compresor entrega gemas al siguiente compresor o cofre.
     * Partículas púrpura brillante simulando gemas siendo comprimidas y transportadas.
     */
    public void animateCompressorToCompressor(Location from, Location to, int amount) {
        String key = "comp2comp_" + from.getWorld().getName() + "_" + from.getBlockX()
                + "_" + from.getBlockY() + "_" + from.getBlockZ() + "_"
                + to.getBlockX() + "_" + to.getBlockY() + "_" + to.getBlockZ();
        if (activeTransports.containsKey(key)) return;

        World world = from.getWorld();
        if (world == null) return;

        Vector direction = to.clone().subtract(from).toVector().normalize();
        double distance = from.distance(to);
        int steps = Math.max(4, (int) distance);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (int i = 0; i <= steps; i++) {
                double t = (double) i / steps;
                Location pos = from.clone().add(direction.clone().multiply(distance * t));
                // Partículas de gema púrpura
                world.spawnParticle(Particle.DUST,
                        pos.getX(), pos.getY() + 0.4, pos.getZ(),
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(160, 32, 240), 1.2f), true);
                if (i % 2 == 0) {
                    world.spawnParticle(Particle.PORTAL, pos.getX(), pos.getY() + 0.4, pos.getZ(),
                            1, 0.1, 0.1, 0.1, 0.05);
                }
            }
        }, 0L, 3L);

        activeTransports.put(key, task);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (task != null && !task.isCancelled()) task.cancel();
            activeTransports.remove(key);
        }, 20L);
    }

    /**
     * Animación: Fábrica Industrial procesa y entrega al dropper.
     * Partículas verde/azul simulando procesamiento industrial.
     */
    public void animateIndustrialToDropper(Location from, Location to, int amount) {
        String key = "indus2drop_" + from.getWorld().getName() + "_" + from.getBlockX()
                + "_" + from.getBlockY() + "_" + from.getBlockZ() + "_"
                + to.getBlockX() + "_" + to.getBlockY() + "_" + to.getBlockZ();
        if (activeTransports.containsKey(key)) return;

        World world = from.getWorld();
        if (world == null) return;

        Vector direction = to.clone().subtract(from).toVector().normalize();
        double distance = from.distance(to);
        int steps = Math.max(4, (int) distance);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (int i = 0; i <= steps; i++) {
                double t = (double) i / steps;
                Location pos = from.clone().add(direction.clone().multiply(distance * t));
                // Partículas industrial verde/cian
                world.spawnParticle(Particle.DUST,
                        pos.getX(), pos.getY() + 0.5, pos.getZ(),
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(0, 200, 80), 1.1f), true);
                if (i % 4 == 0) {
                    world.spawnParticle(Particle.CRIT, pos.getX(), pos.getY() + 0.5, pos.getZ(),
                            1, 0.05, 0.05, 0.05, 0.01);
                }
            }
        }, 0L, 3L);

        activeTransports.put(key, task);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (task != null && !task.isCancelled()) task.cancel();
            activeTransports.remove(key);
        }, 20L);
    }

    /**
     * Animación: Materiales entrando a la fábrica (minerales siendo depositados).
     * Partículas cayendo desde arriba hacia el bloque de la fábrica.
     */
    public void animateMaterialsEntering(Location factoryLoc, Material material) {
        World world = factoryLoc.getWorld();
        if (world == null) return;

        org.bukkit.Color color = switch (material) {
            case COPPER_INGOT -> org.bukkit.Color.fromRGB(200, 100, 50);
            case GOLD_INGOT   -> org.bukkit.Color.fromRGB(255, 215, 0);
            case COAL, CHARCOAL -> org.bukkit.Color.fromRGB(50, 50, 50);
            default           -> org.bukkit.Color.fromRGB(200, 200, 200);
        };

        Location center = factoryLoc.clone().add(0.5, 1.5, 0.5);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (int i = 0; i < 6; i++) {
                double angle = Math.random() * Math.PI * 2;
                double r = 0.3 + Math.random() * 0.2;
                Location pos = center.clone().add(r * Math.cos(angle), 0, r * Math.sin(angle));
                world.spawnParticle(Particle.DUST,
                        pos.getX(), pos.getY(), pos.getZ(),
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(color, 0.8f), true);
                // Partícula cayendo
                world.spawnParticle(Particle.FALLING_DUST,
                        pos.getX(), pos.getY(), pos.getZ(),
                        1, 0, -0.1, 0, 0,
                        new org.bukkit.Particle.DustOptions(color, 1.0f), true);
            }
        }, 0L, 4L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (task != null && !task.isCancelled()) task.cancel();
        }, 10L);
    }

    /**
     * Animación: Compresor procesando gemas (partículas girando alrededor del compresor).
     */
    public void animateCompressorProcessing(Location compLoc, GemCompressor.Mode mode) {
        World world = compLoc.getWorld();
        if (world == null) return;

        org.bukkit.Color color = mode == GemCompressor.Mode.LARGE
                ? org.bukkit.Color.fromRGB(160, 32, 240)   // púrpura para grande
                : org.bukkit.Color.fromRGB(100, 200, 255); // azul claro para mediano

        Location center = compLoc.clone().add(0.5, 1.0, 0.5);
        final int[] frame = {0};

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            frame[0]++;
            double angle = (frame[0] * 0.3) % (Math.PI * 2);
            double r = 0.5;
            for (int i = 0; i < 5; i++) {
                double a = angle + (i * Math.PI * 2 / 5);
                Location pos = center.clone().add(
                        r * Math.cos(a), 0.2 + Math.sin(frame[0] * 0.2) * 0.1,
                        r * Math.sin(a));
                world.spawnParticle(Particle.DUST,
                        pos.getX(), pos.getY(), pos.getZ(),
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(color, 1.0f), true);
            }
            if (frame[0] % 4 == 0) {
                world.spawnParticle(Particle.SMOKE,
                        center.getX(), center.getY() + 0.5, center.getZ(),
                        1, 0.1, 0.1, 0.1, 0.05);
            }
        }, 0L, 2L);

        // La tarea se cancela desde fuera cuando el compresor deja de procesar
        activeTransports.put("comp_proc_" + compLoc.getWorld().getName() + "_" + compLoc.getBlockX()
                + "_" + compLoc.getBlockY() + "_" + compLoc.getBlockZ(), task);
    }

    public void stopCompressorProcessing(Location compLoc) {
        String key = "comp_proc_" + compLoc.getWorld().getName() + "_" + compLoc.getBlockX()
                + "_" + compLoc.getBlockY() + "_" + compLoc.getBlockZ();
        BukkitTask t = activeTransports.remove(key);
        if (t != null && !t.isCancelled()) t.cancel();
    }
}
