package com.ryxmc.protection.factory;

import com.ryxmc.protection.RyxProtection;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;

/**
 * TransferBridge v2.0
 *
 * ── Cambio respecto a v1.0 ────────────────────────────────────────────────
 *
 *  Ahora recibe el ItemStack COMPLETO (no solo el Material) porque el
 *  Compresor de Gemas (GemCompressor) NO distingue sus gemas por Material
 *  puro — usa CustomModelData/meta para diferenciar Gema Pequeña de
 *  Gema Mediana, probablemente compartiendo el mismo Material base
 *  (AMETHYST_SHARD). Perder esa meta al construir un ItemStack nuevo
 *  desde solo el Material rompía la detección de tipo de gema.
 *
 * ── Ruteo por tipo de destino ─────────────────────────────────────────────
 *
 *  BLAST_FURNACE → Fábrica de Energía. Usa EnergyFactory.addMaterial()
 *                   (API interna en campos Java, no inventario).
 *
 *  CRAFTER        → Compresor de Gemas. NO usa inventario tampoco — usa
 *                   comp.setInputStack()/getInputStack() (campo Java).
 *                   Replica EXACTAMENTE la lógica que
 *                   GemCompressorListener.onInteract() usa para el
 *                   depósito manual: detecta el tipo de gema con
 *                   EnergyManager.detectGem(), lo compara contra el
 *                   tipo esperado según comp.getMode(), y si coincide
 *                   lo añade respetando el límite de 64.
 *
 *  DISPENSER      → Rellenadores. No aceptan depósitos externos (su
 *                   único inventario relevante es el cofre detrás,
 *                   gestionado por FillerFactory, no por tubos entrantes).
 *
 *  HOPPER/DROPPER/
 *  CHEST/etc.     → Inventario real de Bukkit (Container). Usado para
 *                   alimentar la tolva de una Fábrica Industrial.
 */
public class TransferBridge {

    /**
     * Intenta depositar un ítem (con toda su meta/CMD) en el bloque destino.
     *
     * @param plugin     instancia del plugin
     * @param target     bloque destino
     * @param sourceItem ítem de referencia — se usa su tipo Y meta para
     *                   decidir el destino correcto (crítico para gemas)
     * @param amount     cantidad solicitada
     * @return cantidad REAL depositada (0 si no se pudo depositar nada)
     */
    public static int deposit(RyxProtection plugin, Block target, ItemStack sourceItem, int amount) {
        if (target == null || sourceItem == null || amount <= 0) return 0;

        Material targetType = target.getType();

        // ── Fábrica de Energía — API interna, no inventario ─────────────────
        if (targetType == Material.BLAST_FURNACE) {
            try {
                var factory = plugin.getFactoryManager().getFactoryAt(target.getLocation());
                if (factory != null) return factory.addMaterial(sourceItem.getType(), amount);
                return 0;
            } catch (Exception e) {
                plugin.getLogger().warning("[TransferBridge] Error depositando en fábrica: " + e.getMessage());
                return 0;
            }
        }

        // ── Compresor de Gemas — API interna, misma lógica que el click manual ──
        if (targetType == Material.CRAFTER) {
            return depositToCompressor(plugin, target, sourceItem, amount);
        }

        // ── Rellenador — no acepta depósitos externos ────────────────────────
        if (targetType == Material.DISPENSER) {
            return 0;
        }

        // ── Contenedores reales de Bukkit (tolva, dropper, cofre, etc.) ──────
        return depositViaContainer(target, sourceItem, amount);
    }

    /**
     * Deposita gemas en un compresor, replicando exactamente la lógica de
     * GemCompressorListener.onInteract() para el depósito manual:
     *
     *   expected = mode == LARGE ? MEDIUM : SMALL
     *   if (detectGem(item) == expected) → añadir hasta el límite de 64
     */
    private static int depositToCompressor(RyxProtection plugin, Block target,
                                           ItemStack sourceItem, int amount) {
        try {
            GemCompressor comp = plugin.getCompressorManager().getAt(target.getLocation());
            if (comp == null) return 0;

            com.ryxmc.protection.energy.EnergyManager.GemType expected =
                    comp.getMode() == GemCompressor.Mode.LARGE
                            ? com.ryxmc.protection.energy.EnergyManager.GemType.MEDIUM
                            : com.ryxmc.protection.energy.EnergyManager.GemType.SMALL;

            var detected = plugin.getEnergyManager().detectGem(sourceItem);
            if (detected != expected) return 0; // no es el tipo de gema que este compresor necesita

            int existing = comp.getInputCount();
            int space    = 64 - existing;
            if (space <= 0) return 0;

            int toAdd = Math.min(amount, space);
            if (comp.getInputStack() == null) {
                ItemStack fresh = sourceItem.clone();
                fresh.setAmount(toAdd);
                comp.setInputStack(fresh);
            } else {
                comp.getInputStack().setAmount(existing + toAdd);
            }

            comp.tryStart();
            plugin.getCompressorManager().save();
            return toAdd;
        } catch (Exception e) {
            plugin.getLogger().warning("[TransferBridge] Error depositando en compresor: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Deposita vía inventario de Bukkit real (Container).
     * Usa isSimilar() para respetar meta/CMD al apilar — evita mezclar
     * ítems de distinta identidad en el mismo slot.
     */
    private static int depositViaContainer(Block target, ItemStack sourceItem, int amount) {
        try {
            if (!(target.getState() instanceof Container container)) return 0;

            var inv = container.getInventory();
            int canFit = 0;
            for (ItemStack slot : inv.getContents()) {
                if (slot == null) canFit += sourceItem.getMaxStackSize();
                else if (slot.isSimilar(sourceItem)) canFit += slot.getMaxStackSize() - slot.getAmount();
            }
            if (canFit <= 0) return 0;

            int toSend = Math.min(amount, canFit);
            ItemStack send = sourceItem.clone();
            send.setAmount(toSend);
            var leftover = inv.addItem(send);
            int actualSent = toSend - (leftover.isEmpty() ? 0 : leftover.values().iterator().next().getAmount());
            return Math.max(0, actualSent);
        } catch (Exception ignored) {
            return 0;
        }
    }

    /**
     * ¿Este bloque puede recibir materiales? (para filtrar destinos
     * antes del BFS de tubos)
     */
    public static boolean isValidDestination(Material mat) {
        return mat == Material.BLAST_FURNACE   // fábrica de energía
                || mat == Material.CRAFTER      // compresor
                || mat == Material.HOPPER;      // tolva de fábrica industrial
    }
}
