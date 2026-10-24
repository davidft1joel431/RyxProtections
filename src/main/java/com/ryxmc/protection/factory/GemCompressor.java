package com.ryxmc.protection.factory;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * GemCompressor v2.0 — Un modelo por compresor, sistema de cadena.
 *
 * MODOS:
 *  NONE   — Sin modelo: inactivo
 *  MEDIUM — Trim común (Coast, Tide, Flow...): 16 peq. → 1 med.
 *  LARGE  — Trim raro  (Ward, Bolt, Silence...): 4 med. → 1 gde.
 *
 * CADENA COMPLETA:
 *  Fábrica → Comp.Mediano → Comp.Grande → Cofre
 *
 * Auto-entrega:
 *  El CompressorManager busca el siguiente bloque en la dirección
 *  de facing para pasar el output al input del siguiente compresor o cofre.
 */
public class GemCompressor {

    // ── Modo de operación ─────────────────────────────
    public enum Mode {
        NONE,   // sin modelo
        MEDIUM, // trim común → peq. → med.
        LARGE   // trim raro  → med. → gde.
    }

    // ── Identificación ────────────────────────────────
    private final String    id;
    private final Location  location;
    private final UUID      ownerUUID;
    private final String    ownerName;
    private final BlockFace facing;

    // ── Estado ────────────────────────────────────────
    private Mode      mode         = Mode.NONE;
    private ItemStack model        = null;  // el trim insertado
    private ItemStack inputStack   = null;  // gemas en espera
    private int       pendingOutput= 0;     // gemas listas para entregar

    // ── Procesado ─────────────────────────────────────
    private boolean processing   = false;
    private int     processTicks = 0;
    private int     totalTicks   = 60;   // ticks por ciclo (3s a 20 tps)
    private int     animFrame    = 0;

    // ── Upgrade ───────────────────────────────────────
    private int       upgradeLevel = 0;   // 0 = base, 1-4 = mejorado

    // Costos por ciclo
    public static final int COST_MEDIUM = 16; // 16 pequeñas → 1 mediana
    public static final int COST_LARGE  = 4;  // 4 medianas  → 1 grande

    // Costo PP por nivel de upgrade del compresor
    public static final int[] UPGRADE_COSTS = {0, 25, 75, 200, 500};

    // ─────────────────────────────────────────────────

    public GemCompressor(String id, Location location,
                         UUID ownerUUID, String ownerName, BlockFace facing) {
        this.id        = id;
        this.location  = location.clone();
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.facing    = facing;
    }

    // ─────────────────────────────────────────────────
    //  Modo y modelo
    // ─────────────────────────────────────────────────

    public Mode     getMode()    { return mode; }
    public ItemStack getModel()  { return model; }
    public boolean  hasModel()   { return mode != Mode.NONE && model != null; }

    public void setModel(ItemStack trim, Mode newMode) {
        this.model = trim != null ? trim.clone() : null;
        this.mode  = (trim == null) ? Mode.NONE : newMode;
        // Al cambiar modelo, detener procesado
        this.processing = false;
        this.processTicks = 0;
    }

    public void clearModel() {
        this.model = null;
        this.mode  = Mode.NONE;
        this.processing = false;
        this.processTicks = 0;
    }

    // ─────────────────────────────────────────────────
    //  Input / Output
    // ─────────────────────────────────────────────────

    public ItemStack getInputStack()   { return inputStack; }
    public int       getInputCount()   { return inputStack == null ? 0 : inputStack.getAmount(); }
    public int       getPendingOutput(){ return pendingOutput; }

    public void setInputStack(ItemStack s)  { this.inputStack   = s; }
    public void setPendingOutput(int v)     { this.pendingOutput = Math.max(0, v); }

    /** Cuántas gemas necesita por ciclo según el modo. */
    public int getNeededInput() {
        return mode == Mode.LARGE ? COST_LARGE : COST_MEDIUM;
    }

    public boolean canProcess() {
        if (mode == Mode.NONE) return false;
        return getInputCount() >= getNeededInput();
    }

    // ─────────────────────────────────────────────────
    //  Lógica de tick
    // ─────────────────────────────────────────────────

    public boolean tick() {
        if (!processing) return false;
        processTicks++;
        animFrame = (animFrame + 1) % 16;
        if (processTicks < getEffectiveTotalTicks()) return false;

        processTicks = 0;
        processing   = false;
        consumeInput();
        pendingOutput++;
        return true;
    }

    public void tryStart() {
        if (processing || !canProcess()) return;
        processing   = true;
        processTicks = 0;
    }

    private void consumeInput() {
        int needed = getNeededInput();
        if (inputStack == null) return;
        int newAmt = inputStack.getAmount() - needed;
        if (newAmt <= 0) inputStack = null;
        else             inputStack.setAmount(newAmt);
    }

    public boolean hasPendingOutput() { return pendingOutput > 0; }

    // ─────────────────────────────────────────────────
    //  Upgrade del compresor
    // ─────────────────────────────────────────────────

    public int getUpgradeLevel() { return upgradeLevel; }

    public String getUpgradeLevelName() {
        return switch (upgradeLevel) {
            case 0 -> "§7Base";
            case 1 -> "§aRápido";
            case 2 -> "§bVeloz";
            case 3 -> "§6Élite";
            case 4 -> "§c§lMáximo";
            default -> "§7Desconocido";
        };
    }

    public int getUpgradeCost() {
        int next = upgradeLevel + 1;
        if (next < 1 || next >= UPGRADE_COSTS.length) return -1;
        return UPGRADE_COSTS[next];
    }

    public boolean canUpgrade() {
        return upgradeLevel < UPGRADE_COSTS.length - 1;
    }

    /** Reduce el tiempo de procesado según el nivel de upgrade. */
    public int getEffectiveTotalTicks() {
        if (upgradeLevel == 0) return totalTicks;
        return Math.max(10, totalTicks - (upgradeLevel * 10)); // -10 ticks por nivel
    }

    public void setUpgradeLevel(int level) {
        this.upgradeLevel = Math.max(0, Math.min(UPGRADE_COSTS.length - 1, level));
    }

    public boolean upgrade() {
        if (!canUpgrade()) return false;
        this.upgradeLevel++;
        return true;
    }

    // ─────────────────────────────────────────────────
    //  Display
    // ─────────────────────────────────────────────────

    public double getProgressRatio() {
        return processing ? (double) processTicks / totalTicks : 0.0;
    }

    public String getProgressBar(int len) {
        if (!processing) return "§7" + "░".repeat(Math.max(0, len)) + " §7INACTIVO";
        double ratio  = getProgressRatio();
        int    filled = (int)(ratio * len);
        String color  = ratio < 0.33 ? "§e" : ratio < 0.66 ? "§6" : "§a";
        return color + "█".repeat(Math.max(0, filled))
                + "§8░".repeat(Math.max(0, len - filled))
                + " §f" + (int)(ratio * 100) + "%";
    }

    public String getModeDisplay() {
        return switch (mode) {
            case MEDIUM -> "§bMediano §8(16 peq. → 1 med.)";
            case LARGE  -> "§5Grande §8(4 med. → 1 gde.)";
            default     -> "§7Sin modelo";
        };
    }

    /** Nombre del tipo de gema de entrada. */
    public String getInputGemName() {
        return mode == Mode.LARGE ? "§dGemas Medianas" : "§dGemas Pequeñas";
    }

    /** Nombre del tipo de gema de salida. */
    public String getOutputGemName() {
        return mode == Mode.LARGE ? "§5Gemas Grandes" : "§dGemas Medianas";
    }

    // ─────────────────────────────────────────────────
    //  Getters básicos
    // ─────────────────────────────────────────────────

    public String    getId()         { return id; }
    public Location  getLocation()   { return location.clone(); }
    public UUID      getOwnerUUID()  { return ownerUUID; }
    public String    getOwnerName()  { return ownerName; }
    public BlockFace getFacing()     { return facing; }
    public boolean   isProcessing()  { return processing; }
    public int       getAnimFrame()  { return animFrame; }
    public int       getTotalTicks() { return totalTicks; }

    public void setProcessing(boolean v)  { this.processing   = v; }
    public void setProcessTicks(int v)    { this.processTicks = v; }
    public void setAnimFrame(int v)       { this.animFrame    = v; }
    public void setTotalTicks(int v)      { this.totalTicks   = Math.max(20, v); }

    public static String makeId(Location loc) {
        return loc.getWorld().getName() + "_"
                + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
    }
}
