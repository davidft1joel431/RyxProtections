package com.ryxmc.protection.hologram;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.dtr.DTRData;
import com.ryxmc.protection.war.WarManager;
import com.ryxmc.protection.manager.RegionStore;
import com.ryxmc.protection.util.Color;
import net.kyori.adventure.text.Component;
import com.ryxmc.protection.util.Txt;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

public class HologramManager {

    private final RyxProtection plugin;
    private final Map<String, List<TextDisplay>> holos = new HashMap<>();
    private BukkitTask updateTask;

    private static final double BASE_Y = 1.4;
    private static final float  GAP_Y  = 0.28f;
    private static final float  SCALE  = 0.75f;

    public HologramManager(RyxProtection plugin) { this.plugin = plugin; }

    private BukkitTask fastTask;
    private static final int BAR_LEN = 10;

    public void init() {
        // Slow task — actualiza TODAS las regiones cada 30s
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (String rid : new ArrayList<>(holos.keySet()))
                if (!rid.startsWith("fac_")) safeUpdate(rid);
        }, 600L, 600L);

        // Fast task — 5 ticks (0.25s): raideables y guerra activa en tiempo real
        fastTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            boolean warActive = plugin.getWarManager() != null
                    && plugin.getWarManager().isWarActive();
            for (String rid : new ArrayList<>(holos.keySet())) {
                if (rid.startsWith("fac_")) continue;
                DTRData d = safeDtr(rid);
                if (d != null && (d.isRaidable() || warActive))
                    safeUpdate(rid);
            }
        }, 20L, 5L);

        plugin.getLogger().info("[Hologram] Iniciado. slow=600t fast=5t.");
    }

    private DTRData safeDtr(String rid) {
        try { return plugin.getDtrManager().get(rid); } catch (Exception e) { return null; }
    }

    private void safeUpdate(String rid) {
        try { updateRegionHologram(rid); } catch (Exception ignored) {}
    }

    public void shutdown() {
        if (updateTask != null) updateTask.cancel();
        if (fastTask   != null) fastTask.cancel();
        removeAll();
    }

    public void reload() {
        removeAll();
        for (RegionStore.RegionRecord r : plugin.getRegionStore().getAll()) {
            spawnRegionHologram(r.regionId);
        }
    }

    public void spawnRegionHologram(String regionId) {
        removeHologram(regionId);
        RegionStore.RegionRecord r = plugin.getRegionStore().getRecord(regionId);
        if (r == null || r.blockLoc == null) return;

        List<String> lines = buildLines(regionId, r);
        List<TextDisplay> displays = new ArrayList<>();

        Location base = r.blockLoc.clone().add(0.5, BASE_Y, 0.5);
        World world = base.getWorld();
        if (world == null) return;

        for (int i = 0; i < lines.size(); i++) {
            double offsetY = (lines.size() - 1 - i) * GAP_Y;
            TextDisplay td = spawnTD(base.clone().add(0, offsetY, 0), lines.get(i), world);
            if (td != null) displays.add(td);
        }

        holos.put(regionId, displays);
    }

    public void updateRegionHologram(String regionId) {
        RegionStore.RegionRecord r = plugin.getRegionStore().getRecord(regionId);
        if (r == null) { removeHologram(regionId); return; }

        List<TextDisplay> existing = holos.get(regionId);
        List<String> newLines = buildLines(regionId, r);

        if (existing == null || existing.size() != newLines.size()) {
            spawnRegionHologram(regionId);
            return;
        }

        for (int i = 0; i < newLines.size(); i++) {
            TextDisplay td = existing.get(i);
            if (td == null || td.isDead()) { spawnRegionHologram(regionId); return; }
            // ✅ Spigot API: setText(String) con códigos § ya traducidos
            td.text(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize(Color.of(newLines.get(i))));
        }
    }

    public void removeHologram(String regionId) {
        List<TextDisplay> list = holos.remove(regionId);
        if (list == null) return;
        for (TextDisplay td : list) if (td != null && !td.isDead()) td.remove();
    }

    public void removeAll() {
        for (String rid : new ArrayList<>(holos.keySet())) removeHologram(rid);
    }

    private List<String> buildLines(String regionId, RegionStore.RegionRecord r) {
        DTRData dtr = safeDtr(regionId);
        List<String> lines = new ArrayList<>();

        // ── Título (con estado de locked) ─────────────────────────────────────
        boolean locked = safeIsLocked(regionId);
        String title = "§6§l⬛ §e§l" + plugin.getRegionStore().getDisplayName(regionId);
        if (locked) title += " §c🔒";
        lines.add(title);

        // ── [v3.1] GUERRA ACTIVA — línea de aviso ─────────────────────────────
        if (plugin.getWarManager() != null && plugin.getWarManager().isWarActive()) {
            String remaining = com.ryxmc.protection.war.WarManager.formatDuration(
                    plugin.getWarManager().getRemainingMs());
            lines.add("§c§l⚔ §f§lGUERRA ACTIVA §c§l⚔ §8— §e" + remaining + " §8restantes");
            lines.add("§c⚠ §7¡Cuida tu §cᴅᴛʀ§7! Los contenedores están abiertos.");
        }

        // ── MOTD ───────────────────────────────────────────────────────────────
        String motd = safeMotd(regionId);
        if (motd != null && !motd.isBlank()) lines.add("§7§o§f" + motd);

        lines.add(Txt.THIN);

        // ── Dueño + nivel ──────────────────────────────────────────────────────
        int lvl = plugin.getUpgradeManager().getLevel(regionId);
        lines.add("§9Dueño: §e" + Txt.sc(r.ownerName)
                + "  §8|  §7Nv.§e" + com.ryxmc.protection.config.ProtectionBlock.getName(lvl));

        // ── Barra de DTR visual ────────────────────────────────────────────────
        if (dtr != null) {
            double pct = dtr.getMaxDtr() > 0
                    ? Math.max(0, Math.min(1, dtr.getDtr() / dtr.getMaxDtr())) : 0;
            int filled = (int) Math.round(pct * BAR_LEN);
            String color = pct > 0.6 ? "§a" : pct > 0.3 ? "§e" : "§c";
            StringBuilder bar = new StringBuilder();
            for (int i = 0; i < BAR_LEN; i++)
                bar.append(i < filled ? color + "█" : "§8░");
            lines.add("§9DTR: " + color + Txt.dec(dtr.getDtr())
                    + "§8/§f" + Txt.dec(dtr.getMaxDtr())
                    + "  §8[" + bar + "§8]");

            if (dtr.isRaidable()) {
                if (dtr.isInCooldown()) {
                    long secs = dtr.cooldownSecondsLeft();
                    String mm = String.format("%d:%02d", secs/60, secs%60);
                    lines.add("§c§l⚠ RAIDEABLE §8— §cregen en §f" + mm);
                } else {
                    lines.add("§c§l⚠ RAIDEABLE §8— §aregenerando §7(+0.1/2min)");
                }
            } else if (dtr.getDtr() < dtr.getMaxDtr() && !dtr.isInCooldown()) {
                lines.add("§a↑ §7Regenerando §8(+0.1 cada 2 min)");
            }
        } else {
            lines.add("§9DTR: §8(cargando...)");
        }

        // ── Mobs desactivados ──────────────────────────────────────────────────
        if (!safeMobs(regionId)) lines.add("§8🐾 §7Mobs: §cdesactivados");

        return lines;
    }

    // ── Helpers null-safe para ext ─────────────────────────────────────────────
    private boolean safeIsLocked(String rid) {
        try { return plugin.getRegionStoreExt().isLocked(rid); } catch (Exception e) { return false; }
    }
    private boolean safeMobs(String rid) {
        try { return plugin.getRegionStoreExt().isMobSpawningEnabled(rid); } catch (Exception e) { return true; }
    }
    private String safeMotd(String rid) {
        try { return plugin.getRegionStoreExt().getMotd(rid); } catch (Exception e) { return null; }
    }
    private String formatDec(double v) {
        return String.format("%.1f", v);
    }

    private TextDisplay spawnTD(Location loc, String text, World world) {
        try {
            TextDisplay td = (TextDisplay) world.spawnEntity(loc, EntityType.TEXT_DISPLAY);
            // ✅ Spigot API: setText(String)
            td.text(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize(Color.of(text)));
            td.setBillboard(Display.Billboard.CENTER);
            td.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0), new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(SCALE, SCALE, SCALE), new AxisAngle4f(0, 0, 0, 1)));
            td.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            td.setShadowed(true);
            td.setPersistent(false);
            td.setGravity(false);
            return td;
        } catch (Exception e) {
            plugin.getLogger().warning("[Hologram] Error al spawnear TextDisplay: " + e.getMessage());
            return null;
        }
    }
    // ─────────────────────────────────────────────────
    //  Hologramas de Fábricas
    // ─────────────────────────────────────────────────

    public void spawnFactoryHologram(String factoryId) {
        removeHologram("fac_" + factoryId);
        com.ryxmc.protection.factory.EnergyFactory f =
                getFactoryById(factoryId);
        if (f == null || f.getLocation() == null) return;

        List<String> lines = buildFactoryLines(f);
        List<TextDisplay> displays = new ArrayList<>();
        Location base = f.getLocation().clone().add(0.5, BASE_Y, 0.5);
        World world = base.getWorld();
        if (world == null) return;

        for (int i = 0; i < lines.size(); i++) {
            double offsetY = (lines.size() - 1 - i) * GAP_Y;
            TextDisplay td = spawnTD(base.clone().add(0, offsetY, 0), lines.get(i), world);
            if (td != null) displays.add(td);
        }
        holos.put("fac_" + factoryId, displays);
    }

    public void updateFactoryHologram(String factoryId) {
        com.ryxmc.protection.factory.EnergyFactory f =
                getFactoryById(factoryId);
        if (f == null) { removeHologram("fac_" + factoryId); return; }

        List<TextDisplay> existing = holos.get("fac_" + factoryId);
        List<String> newLines = buildFactoryLines(f);

        if (existing == null || existing.size() != newLines.size()) {
            spawnFactoryHologram(factoryId);
            return;
        }
        for (int i = 0; i < newLines.size(); i++) {
            TextDisplay td = existing.get(i);
            if (td == null || td.isDead()) { spawnFactoryHologram(factoryId); return; }
            td.text(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize(Color.of(newLines.get(i))));
        }
    }

    public void removeFactoryHologram(String factoryId) {
        removeHologram("fac_" + factoryId);
    }

    private List<String> buildFactoryLines(com.ryxmc.protection.factory.EnergyFactory f) {
        List<String> lines = new ArrayList<>();
        String state = switch (f.getState()) {
            case IDLE    -> "§7EN ESPERA";
            case RUNNING -> "§aFUNCIONANDO";
            case DONE    -> "§e§l¡LISTO!";
        };
        lines.add("§6§l⚙ §e§lFábrica §7— §e" + Txt.sc(f.getOwnerName()));
        lines.add("§9Estado: " + state + " §8| §dOutput: §f" + f.getOutputReady());
        lines.add("§7Velocidad: §fNv." + f.getSpeedLevel()
                + " §8| §9Materiales: §f" + (f.getCopperStock() + f.getGoldStock() + f.getCoalStock()));
        return lines;
    }

    /** Obtener la fábrica por factoryId (no por Location) */
    private com.ryxmc.protection.factory.EnergyFactory getFactoryById(String factoryId) {
        for (com.ryxmc.protection.factory.EnergyFactory f :
                plugin.getFactoryManager().getAllFactories()) {
            if (f.getFactoryId().equals(factoryId)) return f;
        }
        return null;
    }


}