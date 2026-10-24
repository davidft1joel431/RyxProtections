package com.ryxmc.protection.factory;

import org.bukkit.Location;
import org.bukkit.Material;
import java.util.UUID;

/** EnergyFactory v2.2 — addMaterial() devuelve cantidad real agregada (fix crítico) */
public class EnergyFactory {

    public enum State { IDLE, RUNNING, DONE }

    private       String   factoryId;
    private       Location location;
    private final UUID     ownerUUID;
    private final String   ownerName;

    private State  state         = State.IDLE;
    private int    progressTicks = 0;
    private int    outputReady   = 0;
    private int    copperStock   = 0;
    private int    goldStock     = 0;
    private int    coalStock     = 0;

    private FactoryUpgradeLevel upgradeLevel  = FactoryUpgradeLevel.NIVEL_1;
    private int speedLevel      = 0;
    private int processLevel    = 0;
    private int duplicatorLevel = 0;

    public EnergyFactory(String id, Location loc, UUID owner, String ownerName) {
        this.factoryId = id; this.location = loc;
        this.ownerUUID = owner; this.ownerName = ownerName;
    }

    /** Actualiza el ID y ubicación (usado al restaurar una fábrica removida). */
    public void setIdAndLocation(String newId, Location newLoc) {
        this.factoryId = newId;
        this.location = newLoc.clone();
    }

    // ─────────────────────────────────────────────────
    //  Producción
    // ─────────────────────────────────────────────────

    public boolean tick() {
        if (state != State.RUNNING) return false;
        if (outputReady >= upgradeLevel.maxOutput) { state = State.DONE; return false; }
        if (!hasRequiredMaterials()) { state = State.IDLE; return false; }

        progressTicks++;
        if (progressTicks >= upgradeLevel.ticksPerGem) {
            consumeMaterials();
            int produced = getOutputPerCycle();
            if (duplicatorLevel >= 1) {
                double chance = duplicatorLevel == 1 ? 0.10 : 0.25;
                if (Math.random() < chance) produced *= 2;
            }
            outputReady = Math.min(outputReady + produced, upgradeLevel.maxOutput);
            progressTicks = 0;
            state = (outputReady >= upgradeLevel.maxOutput) ? State.DONE
                    : (hasRequiredMaterials() ? State.RUNNING : State.IDLE);
            return true;
        }
        return false;
    }

    public boolean tryStart() {
        if (state == State.RUNNING || outputReady >= upgradeLevel.maxOutput
                || !hasRequiredMaterials()) return false;
        state = State.RUNNING;
        progressTicks = 0;
        return true;
    }

    /**
     * FIX v2.2 — Agrega parcialmente si el stock no cabe todo.
     * Antes devolvía boolean false y rechazaba TODO si desbordaba.
     * Ahora devuelve la cantidad real que se agregó (0 si está lleno).
     */
    public int addMaterial(Material mat, int requested) {
        return switch (mat) {
            case COPPER_INGOT -> {
                int add = Math.max(0, Math.min(requested, upgradeLevel.maxCopper - copperStock));
                copperStock += add;
                yield add;
            }
            case GOLD_INGOT -> {
                int add = Math.max(0, Math.min(requested, upgradeLevel.maxGold - goldStock));
                goldStock += add;
                yield add;
            }
            case COAL, CHARCOAL -> {
                int add = Math.max(0, Math.min(requested, upgradeLevel.maxCoal - coalStock));
                coalStock += add;
                yield add;
            }
            default -> 0;
        };
    }

    public int getAvailableSpace(Material mat) {
        return switch (mat) {
            case COPPER_INGOT   -> upgradeLevel.maxCopper - copperStock;
            case GOLD_INGOT     -> upgradeLevel.maxGold   - goldStock;
            case COAL, CHARCOAL -> upgradeLevel.maxCoal   - coalStock;
            default             -> 0;
        };
    }

    public boolean hasRequiredMaterials() {
        return copperStock >= 2 && goldStock >= 1 && coalStock >= 1;
    }

    private void consumeMaterials() {
        copperStock = Math.max(0, copperStock - 2);
        goldStock   = Math.max(0, goldStock   - 1);
        coalStock   = Math.max(0, coalStock   - 1);
    }

    public int collectOutput() {
        int c = outputReady;
        outputReady = 0;
        if (state == State.DONE)
            state = hasRequiredMaterials() ? State.RUNNING : State.IDLE;
        return c;
    }

    // ─────────────────────────────────────────────────
    //  UI
    // ─────────────────────────────────────────────────

    public int    getOutputPerCycle() { return 1 + processLevel; }
    public int    getTotalTicks()     { return upgradeLevel.ticksPerGem; }

    public String getProgressBar(int len) {
        if (state == State.IDLE) return "§7" + "░".repeat(len) + " §7EN ESPERA";
        if (state == State.DONE) return "§a" + "█".repeat(len) + " §a¡LISTO!";
        double ratio  = (double) progressTicks / getTotalTicks();
        int    filled = (int)(ratio * len);
        String color  = ratio < 0.33 ? "§c" : ratio < 0.66 ? "§e" : "§a";
        return color + "█".repeat(Math.max(0, filled))
                + "§8░".repeat(Math.max(0, len - filled))
                + " §f" + (int)(ratio * 100) + "%";
    }

    // ─────────────────────────────────────────────────
    //  Getters / Setters
    // ─────────────────────────────────────────────────

    public FactoryUpgradeLevel getUpgradeLevel()           { return upgradeLevel; }
    public void                setUpgradeLevel(FactoryUpgradeLevel l) { this.upgradeLevel = l; }
    public String   getId()                { return factoryId; }
    public String   getFactoryId()         { return factoryId; }
    public int      getInventoryCapacity() { return upgradeLevel.maxOutput; }
    public Location getLocation()          { return location.clone(); }
    public UUID     getOwnerUUID()         { return ownerUUID; }
    public String   getOwnerName()         { return ownerName; }
    public State    getState()             { return state; }
    public int      getProgressTicks()     { return progressTicks; }
    public int      getOutputReady()       { return outputReady; }
    public boolean  hasOutput()            { return outputReady > 0; }
    public int      getCopperStock()       { return copperStock; }
    public int      getGoldStock()         { return goldStock; }
    public int      getCoalStock()         { return coalStock; }
    public int      getSpeedLevel()        { return speedLevel; }
    public int      getProcessLevel()      { return processLevel; }
    public int      getDuplicatorLevel()   { return duplicatorLevel; }
    public void setState(State s)          { this.state = s; }
    public void setProgressTicks(int v)    { this.progressTicks = v; }
    public void setOutputReady(int v)      { this.outputReady = v; }
    public void setCopperStock(int v)      { this.copperStock = v; }
    public void setGoldStock(int v)        { this.goldStock = v; }
    public void setCoalStock(int v)        { this.coalStock = v; }
    public void setSpeedLevel(int v)       { this.speedLevel = v; }
    public void setProcessLevel(int v)     { this.processLevel = v; }
    public void setDuplicatorLevel(int v)  { this.duplicatorLevel = v; }
}
