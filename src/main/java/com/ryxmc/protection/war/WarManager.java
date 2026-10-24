package com.ryxmc.protection.war;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.util.Color;
import com.ryxmc.protection.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * WarManager v1.0 — Sistema de Guerra de Clanes
 *
 * ── Reglas de la guerra ────────────────────────────────────────────────────
 *
 *  PERMITIDO durante la guerra:
 *   ✔ Abrir contenedores (cofres, barriles, tolvas, etc.)
 *   ✔ Robar ítems de cualquier bloque con inventario (excepto EnderChest)
 *   ✔ Abrir/cerrar puertas, vallas, trampillas
 *   ✔ Activar botones, palancas, placas de presión
 *   ✔ PvP (siempre activo durante la guerra, independiente del toggle de región)
 *   ✔ El sistema DTR sigue funcionando (muertes bajan DTR)
 *
 *  PROHIBIDO durante la guerra:
 *   ✘ Romper bloques (NINGÚN bloque — ni la base ni el terreno)
 *   ✘ Solo los admins pueden romper bloques (bypass con ryx.admin)
 *
 *  HOLOGRAMAS durante la guerra:
 *   → Todas las regiones muestran "⚔ GUERRA ACTIVA — ¡Cuida tu DTR!"
 *   → Barra de jefe (BossBar) con el tiempo restante visible para todos
 *
 * ── Configuración ──────────────────────────────────────────────────────────
 *  Duración ajustable desde el panel admin (default: 30 min)
 *  Mínimo: 5 min | Máximo: 120 min
 *  Incrementos: ±5 min desde el GUI
 */
public class WarManager {

    // ─────────────────────────────────────────────────
    //  Constantes
    // ─────────────────────────────────────────────────

    public static final long MIN_DURATION_MS  = 5L  * 60 * 1000;   // 5 min
    public static final long MAX_DURATION_MS  = 120L * 60 * 1000;  // 120 min
    public static final long DEFAULT_DURATION = 30L * 60 * 1000;   // 30 min
    public static final long STEP_MS          = 5L  * 60 * 1000;   // paso de 5 min

    // ─────────────────────────────────────────────────
    //  Estado
    // ─────────────────────────────────────────────────

    private final RyxProtection plugin;

    private boolean  warActive    = false;
    private long     startedAt    = 0;
    private long     durationMs   = DEFAULT_DURATION;
    private String   warName      = "⚔ Guerra de Clanes";

    /** Kills por jugador durante la guerra actual */
    private final Map<UUID, Integer> kills  = new HashMap<>();
    /** Ítems robados (stacks) por jugador durante la guerra actual */
    private final Map<UUID, Integer> loots  = new HashMap<>();

    private BossBar  bossBar;
    private BukkitTask countdownTask;
    private BukkitTask holoRefreshTask;

    public WarManager(RyxProtection plugin) { this.plugin = plugin; }

    // ─────────────────────────────────────────────────
    //  Ciclo de vida
    // ─────────────────────────────────────────────────

    public void init() {
        plugin.getLogger().info("[War] WarManager iniciado. Duración default: "
                + formatDuration(durationMs));
    }

    public void shutdown() {
        if (warActive) endWar(true);
    }

    // ─────────────────────────────────────────────────
    //  Control de guerra
    // ─────────────────────────────────────────────────

    /**
     * Inicia la guerra. Si ya hay una activa, no hace nada.
     * @return true si se inició, false si ya había una activa
     */
    public boolean startWar() {
        if (warActive) return false;

        warActive  = true;
        startedAt  = System.currentTimeMillis();
        kills.clear();
        loots.clear();

        // ── BossBar ─────────────────────────────────────────────────────────
        bossBar = Bukkit.createBossBar(
                Color.of("§c⚔ §f§l" + warName + " §c⚔ §8— " + formatDuration(durationMs) + " restantes"),
                BarColor.RED, BarStyle.SEGMENTED_10);
        bossBar.setVisible(true);
        Bukkit.getOnlinePlayers().forEach(bossBar::addPlayer);

        // ── Anuncio inicial ─────────────────────────────────────────────────
        broadcastWarStart();

        // ── Actualización del BossBar cada segundo ───────────────────────────
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long remaining = getRemainingMs();
            if (remaining <= 0) { endWar(false); return; }
            // Añadir jugadores online que se conectaron después del inicio
            Bukkit.getOnlinePlayers().forEach(pp -> {
                if (!bossBar.getPlayers().contains(pp)) bossBar.addPlayer(pp);
            });
            double progress = Math.max(0, Math.min(1, (double) remaining / durationMs));
            bossBar.setProgress(progress);
            bossBar.setTitle(Color.of("§c⚔ §f§l" + warName
                    + " §c⚔ §8— §e" + formatDuration(remaining) + " §8restantes"));
            // Cambiar color de barra según tiempo restante
            if (remaining < 5 * 60 * 1000)       bossBar.setColor(BarColor.RED);
            else if (remaining < 15 * 60 * 1000)  bossBar.setColor(BarColor.YELLOW);
            else                                   bossBar.setColor(BarColor.PURPLE);
        }, 20L, 20L); // cada segundo

        // ── Refrescar hologramas cada 5 segundos ─────────────────────────────
        holoRefreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.getHologramManager() != null)
                plugin.getRegionStore().getAll().forEach(r ->
                        plugin.getHologramManager().updateRegionHologram(r.regionId));
        }, 40L, 100L);

        plugin.getLogger().info("[War] ¡GUERRA INICIADA! Duración: " + formatDuration(durationMs));
        return true;
    }

    /**
     * Finaliza la guerra.
     * @param forced si fue finalizada a la fuerza por admin o shutdown
     */
    public void endWar(boolean forced) {
        if (!warActive) return;
        warActive = false;

        if (countdownTask  != null) { countdownTask.cancel();  countdownTask  = null; }
        if (holoRefreshTask != null) { holoRefreshTask.cancel(); holoRefreshTask = null; }
        if (bossBar        != null) { bossBar.removeAll(); bossBar.setVisible(false); bossBar = null; }

        // Refrescar hologramas a estado normal
        if (plugin.getHologramManager() != null)
            plugin.getRegionStore().getAll().forEach(r ->
                    plugin.getHologramManager().updateRegionHologram(r.regionId));

        broadcastWarEnd(forced);
        plugin.getLogger().info("[War] Guerra finalizada. Forzado=" + forced);
    }

    // ─────────────────────────────────────────────────
    //  Estadísticas
    // ─────────────────────────────────────────────────

    public void registerKill(UUID killer) {
        if (!warActive) return;
        kills.merge(killer, 1, Integer::sum);
    }

    public void registerLoot(UUID looter, int stacks) {
        if (!warActive) return;
        loots.merge(looter, stacks, Integer::sum);
    }

    public int getKills(UUID uuid) { return kills.getOrDefault(uuid, 0); }
    public int getLoot(UUID uuid)  { return loots.getOrDefault(uuid, 0); }

    /** Top jugadores por kills durante la guerra actual */
    public List<Map.Entry<UUID, Integer>> getTopKills() {
        return kills.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .toList();
    }

    public List<Map.Entry<UUID, Integer>> getTopLooters() {
        return loots.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .toList();
    }

    // ─────────────────────────────────────────────────
    //  Consultas
    // ─────────────────────────────────────────────────

    public boolean isWarActive()    { return warActive; }
    public long    getDurationMs()  { return durationMs; }
    public String  getWarName()     { return warName; }

    public long getRemainingMs() {
        if (!warActive) return 0;
        long elapsed = System.currentTimeMillis() - startedAt;
        return Math.max(0, durationMs - elapsed);
    }

    public long getElapsedMs() {
        if (!warActive) return 0;
        return System.currentTimeMillis() - startedAt;
    }

    public double getProgress() {
        if (!warActive || durationMs <= 0) return 0;
        return Math.max(0, Math.min(1, 1.0 - (double) getRemainingMs() / durationMs));
    }

    // ─────────────────────────────────────────────────
    //  Configuración (desde el panel admin)
    // ─────────────────────────────────────────────────

    public void setDurationMs(long ms) {
        this.durationMs = Math.max(MIN_DURATION_MS, Math.min(MAX_DURATION_MS, ms));
    }

    public void increaseDuration() { setDurationMs(durationMs + STEP_MS); }
    public void decreaseDuration() { setDurationMs(durationMs - STEP_MS); }

    public void setWarName(String name) { this.warName = name; }

    /** Añade un jugador a la bossbar cuando se conecta durante la guerra */
    public void onPlayerJoin(Player p) {
        if (warActive && bossBar != null) {
            bossBar.addPlayer(p);
            p.sendMessage(Txt.err("§c⚔ §f§lHay una §c§lGUERRA §f§lactiva en el servidor."));
            p.sendMessage(Txt.warn("§7▶ Puedes robar ítems de cofres y contenedores."));
            p.sendMessage(Txt.warn("§c✘ §7No puedes §cROMPER BLOQUES §7durante la guerra."));
            p.sendMessage(Txt.warn("§e⚠ §7Tiempo restante: §f" + formatDuration(getRemainingMs())));
        }
    }

    public void onPlayerQuit(Player p) {
        if (bossBar != null) bossBar.removePlayer(p);
    }

    // ─────────────────────────────────────────────────
    //  Anuncios
    // ─────────────────────────────────────────────────

    private void broadcastWarStart() {
        String sep = Color.of("§c§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Bukkit.broadcastMessage(sep);
        Bukkit.broadcastMessage(Color.of("  §c⚔ §f§l¡ " + warName.toUpperCase() + " HA COMENZADO! §c⚔"));
        Bukkit.broadcastMessage(sep);
        Bukkit.broadcastMessage(Color.of("  §e▶ §7Duración: §f" + formatDuration(durationMs)));
        Bukkit.broadcastMessage(Color.of("  §a✔ §7Puedes §aabrir cofres y robar §7ítems."));
        Bukkit.broadcastMessage(Color.of("  §a✔ §7Puedes §aabrir puertas, vallas y activar mecanismos."));
        Bukkit.broadcastMessage(Color.of("  §a✔ §7PvP §aACTIVO §7— el DTR sigue funcionando."));
        Bukkit.broadcastMessage(Color.of("  §c✘ §7§oROMPER BLOQUES §7está §cPROHIBIDO§7."));
        Bukkit.broadcastMessage(Color.of("  §e⚠ §7Si tu región llega a 0 DTR durante la guerra,"));
        Bukkit.broadcastMessage(Color.of("  §e⚠ §7§l¡quedará completamente abierta!"));
        Bukkit.broadcastMessage(sep);

        // Sonido de trompeta de batalla
        Bukkit.getOnlinePlayers().forEach(pp -> {
            pp.playSound(pp.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
        });
    }

    private void broadcastWarEnd(boolean forced) {
        String sep = Color.of("§c§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Bukkit.broadcastMessage(sep);
        Bukkit.broadcastMessage(Color.of("  §a⚔ §f§l¡La " + warName + " ha TERMINADO! §a⚔"));
        Bukkit.broadcastMessage(sep);

        if (!kills.isEmpty()) {
            Bukkit.broadcastMessage(Color.of("  §6§l🏆 TOP KILLS:"));
            getTopKills().stream().limit(3).forEach(e -> {
                String name = Bukkit.getOfflinePlayer(e.getKey()).getName();
                if (name != null)
                    Bukkit.broadcastMessage(Color.of("    §e" + name + " §8— §c" + e.getValue() + " kills"));
            });
        }
        if (!loots.isEmpty()) {
            Bukkit.broadcastMessage(Color.of("  §5§l💎 TOP SAQUEADORES:"));
            getTopLooters().stream().limit(3).forEach(e -> {
                String name = Bukkit.getOfflinePlayer(e.getKey()).getName();
                if (name != null)
                    Bukkit.broadcastMessage(Color.of("    §e" + name + " §8— §d" + e.getValue() + " stacks saqueados"));
            });
        }

        Bukkit.broadcastMessage(forced
                ? Color.of("  §8(Finalizada por un administrador)")
                : Color.of("  §7¡El tiempo se acabó!"));
        Bukkit.broadcastMessage(sep);

        Bukkit.getOnlinePlayers().forEach(pp ->
                pp.playSound(pp.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 1.2f));
    }

    // ─────────────────────────────────────────────────
    //  Utilidades
    // ─────────────────────────────────────────────────

    /**
     * Formatea milisegundos en "Xh Ym Zs" o "Xm Zs".
     */
    public static String formatDuration(long ms) {
        if (ms <= 0) return "0s";
        long totalSecs = ms / 1000;
        long hours   = totalSecs / 3600;
        long minutes = (totalSecs % 3600) / 60;
        long seconds = totalSecs % 60;
        StringBuilder sb = new StringBuilder();
        if (hours   > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    /**
     * Texto de estado que se muestra en los hologramas de las regiones
     * mientras la guerra está activa.
     */
    public String getHologramWarLine() {
        if (!warActive) return null;
        return "§c§l⚔ §f§lGUERRA ACTIVA §c§l⚔ §8— §eCuida tu ᴅᴛʀ § — " + formatDuration(getRemainingMs());
    }
}
