package com.ryxmc.protection.factory;

/**
 * CompressorTier v1.0 — Niveles de mejora del Compresor de Gemas
 *
 * Cada tier aumenta:
 *  - maxInput:   cuántas gemas pequeñas puede acumular
 *  - outputSize: cuántas pequeñas se comprimen en 1 mediana
 *  - ticksPerOp: velocidad de compresión (menos = más rápido)
 *  - maxOutput:  capacidad de almacenamiento de gemas medianas/grandes
 *
 * Tier 1 → Tier 4 con materiales de mejora progresivos.
 *
 * Materiales para subir de tier (en el GUI):
 *  T1 → T2: 8 lingotes de cobre
 *  T2 → T3: 4 lingotes de oro + 2 diamantes
 *  T3 → T4: 2 bloques de netherite + 4 esmeraldas
 */
public enum CompressorTier {

    //           nombre            maxInput  outputSize  ticksPerOp  maxOutput
    TIER_1("§7Compresor §8[Tier I]",     64,      4,         100,        16),
    TIER_2("§a§lCompresor §a[Tier II]",  128,     8,          70,        32),
    TIER_3("§b§lCompresor §b[Tier III]", 256,    16,          45,        64),
    TIER_4("§6§lCompresor §6[Tier IV]",  512,    32,          25,       128);

    public final String displayName;
    public final int    maxInput;    // máximo de gemas pequeñas en stock
    public final int    outputSize;  // cuántas pequeñas → 1 mediana
    public final int    ticksPerOp;  // ticks entre cada compresión
    public final int    maxOutput;   // capacidad de salida (medianas/grandes)

    CompressorTier(String displayName, int maxInput, int outputSize,
                   int ticksPerOp, int maxOutput) {
        this.displayName = displayName;
        this.maxInput    = maxInput;
        this.outputSize  = outputSize;
        this.ticksPerOp  = ticksPerOp;
        this.maxOutput   = maxOutput;
    }

    /** Nivel numérico (1-4) */
    public int getLevel() { return ordinal() + 1; }

    /** ¿Hay un siguiente tier? */
    public boolean hasNext() { return ordinal() < values().length - 1; }

    /** Siguiente tier (null si ya es el máximo) */
    public CompressorTier next() {
        return hasNext() ? values()[ordinal() + 1] : null;
    }

    /** Materiales necesarios para subir al siguiente tier */
    public org.bukkit.inventory.ItemStack[] getUpgradeCost() {
        return switch (this) {
            case TIER_1 -> new org.bukkit.inventory.ItemStack[]{
                    new org.bukkit.inventory.ItemStack(org.bukkit.Material.COPPER_INGOT, 8)
            };
            case TIER_2 -> new org.bukkit.inventory.ItemStack[]{
                    new org.bukkit.inventory.ItemStack(org.bukkit.Material.GOLD_INGOT, 4),
                    new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND, 2)
            };
            case TIER_3 -> new org.bukkit.inventory.ItemStack[]{
                    new org.bukkit.inventory.ItemStack(org.bukkit.Material.NETHERITE_BLOCK, 2),
                    new org.bukkit.inventory.ItemStack(org.bukkit.Material.EMERALD, 4)
            };
            case TIER_4 -> new org.bukkit.inventory.ItemStack[0]; // máximo
        };
    }

    /** Color del tier para mensajes */
    public String color() {
        return switch (this) {
            case TIER_1 -> "§7";
            case TIER_2 -> "§a";
            case TIER_3 -> "§b";
            case TIER_4 -> "§6";
        };
    }

    /** Descripción corta del tier */
    public String shortDesc() {
        return switch (this) {
            case TIER_1 -> "§7Básico §8— 4 gemas → 1 mediana";
            case TIER_2 -> "§aAvanzado §8— 8 gemas → 1 mediana, +rápido";
            case TIER_3 -> "§bElite §8— 16 gemas → 1 mediana, ++rápido";
            case TIER_4 -> "§6§lMaestro §8— 32 gemas → 1 mediana, máx velocidad";
        };
    }

    public static CompressorTier fromLevel(int level) {
        for (CompressorTier t : values())
            if (t.getLevel() == level) return t;
        return TIER_1;
    }
}
