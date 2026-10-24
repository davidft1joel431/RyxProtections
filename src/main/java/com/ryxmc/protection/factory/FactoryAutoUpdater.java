package com.ryxmc.protection.factory;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.energy.EnergyManager;
import com.ryxmc.protection.util.Txt;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FactoryAutoUpdater v1.0 — Sistema de actualización automática para fábricas.
 *
 * Permite a los jugadores pagar PP para activar "auto-upgrades" en sus fábricas:
 *  • Auto-Recolección: la fábrica recolecta su output automáticamente al llenarse
 *  • Auto-Entrega: envía output al compresor/cofres vía tubos sin intervención
 *  • Turbo-Producción: reduce el tiempo entre ciclos (costo PP por nivel)
 *
 * Cada actualización es por fábrica y persiste en factories.yml.
 *
 * Uso: /factory auto <nivel> <fabrica> — solo dueño o admin
 */
public class FactoryAutoUpdater {

    private static final NamespacedKey PDC_KEY_AUTO_LEVEL = new NamespacedKey(
            com.ryxmc.protection.RyxProtection.getInstance(), "factory_auto_level");

    // Costos PP por nivel de auto-mejora (acumulativo)
    private static final int[] AUTO_LEVEL_COSTS = {0, 50, 150, 300, 600};

    private final RyxProtection plugin;
    /** factoryId → nivel de auto-mejora (0 = desactivado, 1-4 = activo) */
    private final Map<String, Integer> autoLevels = new ConcurrentHashMap<>();
    private BukkitTask autoTask;

    public FactoryAutoUpdater(RyxProtection plugin) { this.plugin = plugin; }

    public void init() {
        autoTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAutoDelivery, 20L, 20L);
        plugin.getLogger().info("[FactoryAutoUpdater] Iniciado.");
    }

    public void shutdown() {
        if (autoTask != null) autoTask.cancel();
    }

    // ─────────────────────────────────────────────────
    //  API pública
    // ─────────────────────────────────────────────────

    /** Nivel de auto-mejora de una fábrica (0-4). */
    public int getAutoLevel(String factoryId) {
        return autoLevels.getOrDefault(factoryId, 0);
    }

    /** Establece el nivel de auto-mejora. */
    public void setAutoLevel(String factoryId, int level) {
        autoLevels.put(factoryId, Math.max(0, Math.min(4, level)));
    }

    /** Costo PP para subir al siguiente nivel de auto-mejora. */
    public int getNextAutoCost(int currentLevel) {
        int next = currentLevel + 1;
        if (next < 1 || next >= AUTO_LEVEL_COSTS.length) return -1;
        return AUTO_LEVEL_COSTS[next];
    }

    /** Nombre del nivel para mostrar. */
    public String getAutoLevelName(int level) {
        return switch (level) {
            case 0 -> "§7Desactivado";
            case 1 -> "§aBásico";
            case 2 -> "§bAvanzado";
            case 3 -> "§6Élite";
            case 4 -> "§c§lMaestro";
            default -> "§7Desconocido";
        };
    }

    /** Activar auto-mejora si el jugador tiene PP suficiente. */
    public boolean activateAuto(Player p, String factoryId, int targetLevel) {
        EnergyFactory f = plugin.getFactoryManager().getAllFactories().stream()
                .filter(fac -> fac.getFactoryId().equals(factoryId))
                .findFirst().orElse(null);
        if (f == null) return false;

        int current = getAutoLevel(factoryId);
        if (targetLevel <= current) {
            p.sendMessage(Txt.err("La fábrica ya tiene auto-mejora Nv." + current + "."));
            return false;
        }
        if (targetLevel > 4) {
            p.sendMessage(Txt.err("Nivel máximo de auto-mejora es Nv.4."));
            return false;
        }

        int cost = getNextAutoCost(current);
        if (cost < 0) {
            p.sendMessage(Txt.err("Auto-mejora ya está al nivel máximo."));
            return false;
        }

        int pp = plugin.getUpgradeManager().getPP(p.getUniqueId());
        if (pp < cost) {
            p.sendMessage(Txt.err("Necesitas §e" + cost + " PP§c. Tienes §e" + pp + "§c PP."));
            return false;
        }

        // Verificar propiedad
        if (!f.getOwnerUUID().equals(p.getUniqueId()) && !p.hasPermission("ryx.admin")) {
            p.sendMessage(Txt.err("No eres el dueño de esta fábrica."));
            return false;
        }

        plugin.getUpgradeManager().setPP(p.getUniqueId(), pp - cost);
        setAutoLevel(factoryId, targetLevel);
        p.sendMessage(Txt.ok("§6⚡ §fAuto-mejora activada: §eNv." + targetLevel
                + " §8(§c-" + cost + " PP§8)"));
        return true;
    }

    // ─────────────────────────────────────────────────
    //  Tarea de auto-entrega (cada tick)
    // ─────────────────────────────────────────────────

    private void tickAutoDelivery() {
        for (EnergyFactory f : plugin.getFactoryManager().getAllFactories()) {
            String fid = f.getFactoryId();
            int autoLevel = getAutoLevel(fid);
            if (autoLevel == 0) continue;

            // Auto-recolección: si el output está lleno o casi lleno, recolectar
            if (autoLevel >= 2 && f.hasOutput()
                    && f.getOutputReady() >= f.getUpgradeLevel().maxOutput) {
                f.collectOutput();
                f.tryStart();
                // Intentar entregar vía tubos si hay nivel 3+
                if (autoLevel >= 3) {
                    plugin.getTubeManager().tickNetworkOutputDelivery(plugin);
                }
            }

            // Auto-entrega básica: nivel 1 entrega al compresor adyacente si hay output
            if (autoLevel >= 1 && f.hasOutput()) {
                plugin.getFactoryManager().tryDeliverToCompressor(f);
            }
        }
    }

    // ─────────────────────────────────────────────────
    //  Comandos de chat (llamados desde FactoryCommand)
    // ─────────────────────────────────────────────────

    public void cmdAuto(Player p, String[] args) {
        if (args.length < 3) {
            p.sendMessage(Txt.SEP);
            p.sendMessage("  §6§l⚡ §fAuto-Mejoras de Fábrica");
            p.sendMessage(Txt.SEP);
            p.sendMessage("§7Uso: §f/factory auto <nivel> <fabricaId>");
            p.sendMessage("§7Niveles: §a1 §7= Auto-recolección");
            p.sendMessage("         §b2 §7= + Auto-entrega vía tubos");
            p.sendMessage("         §63 §7= + Turbo-producción (más rápido)");
            p.sendMessage("         §c4 §7= + Máxima eficiencia");
            p.sendMessage(Txt.SEP);
            EnergyFactory f = plugin.getFactoryManager().getAllFactories().stream()
                    .filter(fac -> fac.getOwnerUUID().equals(p.getUniqueId())
                            || p.hasPermission("ryx.admin"))
                    .findFirst().orElse(null);
            if (f != null) {
                int lvl = getAutoLevel(f.getFactoryId());
                p.sendMessage("§9Tu fábrica §e" + f.getFactoryId()
                        + " §9tiene auto-mejora: §f" + getAutoLevelName(lvl));
                int nextCost = getNextAutoCost(lvl);
                if (nextCost > 0)
                    p.sendMessage("§9Siguiente nivel: §e" + nextCost + " PP");
                else
                    p.sendMessage("§9Nivel máximo alcanzado.");
            }
            p.sendMessage(Txt.SEP);
            return;
        }

        try {
            int level = Integer.parseInt(args[1]);
            String factoryId = args[2];
            if (!activateAuto(p, factoryId, level)) return;
        } catch (NumberFormatException e) {
            p.sendMessage(Txt.err("Nivel inválido. Usa 1-4."));
        }
    }
}
