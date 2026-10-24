package com.ryxmc.protection.factory;

/**
 * FactoryUpgradeLevel — Niveles de la Fábrica de Energía.
 *
 * Campos públicos para acceso directo (compatible con EnergyFactory y FactoryManager).
 *  displayName  : nombre visual del nivel
 *  ticksPerGem  : ticks para producir 1 gema base (20 ticks = 1s)
 *  maxOutput    : máximo de gemas acumuladas sin recoger
 *  maxCopper    : capacidad máx de lingotes de cobre
 *  maxGold      : capacidad máx de lingotes de oro
 *  maxCoal      : capacidad máx de carbón
 */
public enum FactoryUpgradeLevel {

    //           nombre          ticks  out   cu    au   coal
    NIVEL_1("Nivel I — Básica",   200,  10,   10,   5,    5),
    NIVEL_2("Nivel II — Media",   160,  20,   20,  10,   10),
    NIVEL_3("Nivel III — Avanzada",120, 30,   30,  15,   15),
    NIVEL_4("Nivel IV — Industrial", 80,50,   50,  25,   25),
    NIVEL_5("Nivel V — Maestra",   40, 100,  100,  50,   50);

    public final String displayName;
    public final int    ticksPerGem;
    public final int    maxOutput;
    public final int    maxCopper;
    public final int    maxGold;
    public final int    maxCoal;

    // PP necesarios para subir A este nivel
    private static final int[] UPGRADE_COSTS = {0, 20, 50, 120, 300};

    FactoryUpgradeLevel(String name, int ticks, int out, int cu, int au, int coal) {
        this.displayName = name;
        this.ticksPerGem = ticks;
        this.maxOutput   = out;
        this.maxCopper   = cu;
        this.maxGold     = au;
        this.maxCoal     = coal;
    }

    /** Siguiente nivel, o null si es el último. */
    public FactoryUpgradeLevel next() {
        int idx = ordinal() + 1;
        FactoryUpgradeLevel[] vals = values();
        return idx < vals.length ? vals[idx] : null;
    }

    /** Costo en PP para subir DE este nivel al siguiente (-1 si es último). */
    public int getUpgradeCost() {
        int next = ordinal() + 1;
        if (next >= UPGRADE_COSTS.length) return -1;
        return UPGRADE_COSTS[next];
    }

    /** Level 1-based (NIVEL_1=1, NIVEL_5=5). */
    public int getLevel() { return ordinal() + 1; }

    public static FactoryUpgradeLevel fromLevel(int level) {
        for (FactoryUpgradeLevel l : values()) if (l.getLevel() == level) return l;
        return NIVEL_1;
    }
}
