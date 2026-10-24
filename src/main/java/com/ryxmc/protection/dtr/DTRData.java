package com.ryxmc.protection.dtr;

import com.ryxmc.protection.util.Txt;


/**
 * DTRData — Datos DTR de una región de protección.
 *
 * REGLAS:
 *  • maxDtr = baseDtr(nivel) + miembros×1.5 + clan×0.5  (cap 10)
 *  • Muerte → dtr -= 1.0
 *  • dtr = 0 → RAIDEABLE, inicia cooldown 10 min
 *  • Muere durante cooldown → cooldown reinicia
 *  • Cooldown expira → regen +0.1 cada 2 min
 *  • dtr ≥ maxDtr → regen detiene
 */
public class DTRData {

    // Configuración estática
    public static final double BASE_DTR         = 5.0;
    public static final double MEMBER_BONUS     = 1.5;
    public static final double CLAN_BONUS       = 0.5;
    public static final double MAX_CAP          = 10.0;
    public static final double DEATH_PENALTY    = 1.0;
    public static final double REGEN_AMOUNT     = 0.1;
    public static final long   COOLDOWN_MS      = 10L * 60 * 1000; // 10 min
    public static final long   REGEN_PERIOD_MS  = 2L  * 60 * 1000; // 2 min

    // ── Campos ───────────────────────────────────────
    private double  dtr;
    private double  maxDtr;
    private boolean raidable;

    /** true = esperando que expire el cooldown antes de regenerar */
    private boolean inCooldown;
    /** timestamp cuando expira el cooldown (0 si no hay cooldown) */
    private long    cooldownExpires;

    /** conteos de miembros para recálculo */
    private int     regionMembers;
    private int     clanMembers;

    // ─────────────────────────────────────────────────

    public DTRData(double maxDtr) {
        this.maxDtr         = Math.min(MAX_CAP, maxDtr);
        this.dtr            = this.maxDtr;
        this.raidable       = false;
        this.inCooldown     = false;
        this.cooldownExpires= 0L;
        this.regionMembers  = 0;
        this.clanMembers    = 0;
    }

    // ── Mutaciones principales ────────────────────────

    /** Aplica la penalidad por muerte. Retorna true si la región se volvió raideable. */
    public boolean applyDeath() {
        if (inCooldown) {
            // Murió durante el cooldown → reiniciar cooldown
            cooldownExpires = System.currentTimeMillis() + COOLDOWN_MS;
            return false; // ya era raideable
        }

        dtr = Math.max(0.0, dtr - DEATH_PENALTY);

        if (dtr <= 0.0 && !raidable) {
            raidable        = true;
            inCooldown      = true;
            cooldownExpires = System.currentTimeMillis() + COOLDOWN_MS;
            return true; // SE VOLVIÓ raideable ahora
        }
        return false;
    }

    /** Verifica si el cooldown expiró y activa el modo regen si es así. */
    public boolean checkCooldownExpired() {
        if (!inCooldown) return false;
        if (System.currentTimeMillis() >= cooldownExpires) {
            inCooldown = false;
            return true; // cooldown expiró, puede regenerar
        }
        return false;
    }

    /** Aplica un tick de regeneración. Retorna true si dejó de ser raideable. */
    public boolean applyRegen() {
        if (inCooldown) return false;
        if (dtr >= maxDtr) return false;

        dtr = Math.min(maxDtr, dtr + REGEN_AMOUNT);

        if (raidable && dtr > 0.0) {
            raidable = false;
            return true; // dejó de ser raideable
        }
        return false;
    }

    /** Recalcula el maxDtr según nivel de mejora y miembros */
    public void recalcMax(double baseDtr, int regionMembers, int clanMembers) {
        recalcMaxWithExtras(baseDtr, regionMembers, clanMembers, 0);
    }

    /**
     * Recalcula el maxDtr incluyendo extras de DTR comprados (v3.0).
     *
     * Regla para el dtr actual:
     *  - Si estaba LLENO antes del recálculo (dtr >= oldMax) → se mantiene lleno
     *    al nuevo máximo. Esto garantiza que una región recién creada empiece
     *    con el DTR al 100%, sin importar de qué nivel sea el bloque.
     *  - Si estaba PARCIAL o a 0 → se conserva el valor (no sube artificialmente).
     *  - Si el valor actual supera el nuevo máximo → se capea al nuevo máximo.
     *
     * Ejemplos:
     *   Región nueva    : dtr=5.0 maxOld=5.0  → wasFull=true  → dtr=10.0 ✓
     *   Raideable       : dtr=0.0 maxOld=10.0 → wasFull=false → dtr=0.0  ✓
     *   Parcialmente dañada: dtr=3.0 maxOld=5.0 → wasFull=false → dtr=3.0 ✓
     *
     * @param extras puntos adicionales de DTR máximo comprados (0.0 – 4.0)
     */
    public void recalcMaxWithExtras(double baseDtr, int regionMembers,
                                    int clanMembers, double extras) {
        this.regionMembers = regionMembers;
        this.clanMembers   = clanMembers;

        // [FIX] Verificar si el DTR estaba lleno ANTES de cambiar maxDtr
        boolean wasFull = (dtr >= maxDtr && maxDtr > 0);

        double baseMax = Math.min(MAX_CAP,
                baseDtr + (regionMembers * MEMBER_BONUS) + (clanMembers * CLAN_BONUS));
        this.maxDtr = baseMax + Math.max(0, extras);

        if (wasFull) {
            // DTR estaba lleno → mantenerlo lleno al nuevo máximo
            this.dtr = this.maxDtr;
        } else if (dtr > this.maxDtr) {
            // DTR supera el nuevo máximo → capear (no subir artificialmente)
            this.dtr = this.maxDtr;
        }
        // Si dtr < newMaxDtr y no estaba lleno → conservar valor (regen, deuda, etc.)
    }

    /** Reset completo al máximo (admin o gema divina) */
    public void reset() {
        dtr             = maxDtr;
        raidable        = false;
        inCooldown      = false;
        cooldownExpires = 0L;
    }

    /** Ajusta el DTR manualmente */
    public void setDtr(double value) {
        this.dtr = Math.max(0.0, Math.min(maxDtr, value));
        if (this.dtr <= 0.0 && !raidable) {
            raidable        = true;
            inCooldown      = true;
            cooldownExpires = System.currentTimeMillis() + COOLDOWN_MS;
        } else if (this.dtr > 0.0 && raidable) {
            raidable        = false;
            inCooldown      = false;
            cooldownExpires = 0L;
        }
    }

    // ── Consultas ─────────────────────────────────────

    public long cooldownSecondsLeft() {
        if (!inCooldown || cooldownExpires == 0) return 0;
        return Math.max(0, (cooldownExpires - System.currentTimeMillis()) / 1000);
    }

    /** Formato visual del DTR: §a4.5§7/§f5.0 o §4§l[RAIDEABLE] */
    public String formatted() {
        double ratio  = maxDtr > 0 ? dtr / maxDtr : 0;
        String c      = ratio > 0.66 ? "§a" : ratio > 0.33 ? "§e" : ratio > 0 ? "§c" : "§4";
        String base   = c + String.format("%.1f", dtr) + "§7/§f" + String.format("%.1f", maxDtr);

        if (raidable) {
            long secs = cooldownSecondsLeft();
            String cd = inCooldown && secs > 0
                    ? " §8(regen en §f" + Txt.time(secs) + "§8)"
                    : " §8(§aregenerando§8)";
            return "§4§l[RAIDEABLE] " + base + cd;
        }
        if (dtr < maxDtr && !inCooldown) return base + " §8(§a+§8)";
        return base;
    }

    // ── Getters / Setters ─────────────────────────────

    public double  getDtr()              { return dtr; }
    public double  getMaxDtr()           { return maxDtr; }
    public boolean isRaidable()          { return raidable; }
    public boolean isInCooldown()        { return inCooldown; }
    public long    getCooldownExpires()  { return cooldownExpires; }
    public int     getRegionMembers()    { return regionMembers; }
    public int     getClanMembers()      { return clanMembers; }

    // Para deserialización
    public void setDtrDirect(double v)           { this.dtr = v; }
    public void setMaxDtrDirect(double v)        { this.maxDtr = v; }
    public void setRaidable(boolean v)           { this.raidable = v; }
    public void setInCooldown(boolean v)         { this.inCooldown = v; }
    public void setCooldownExpires(long v)       { this.cooldownExpires = v; }
    public void setRegionMembersDirect(int v)    { this.regionMembers = v; }
    public void setClanMembersDirect(int v)      { this.clanMembers = v; }
}
