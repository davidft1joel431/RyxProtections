package com.ryxmc.protection.hook;

import com.ryxmc.protection.RyxProtection;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * PlaceholderHook v1.0 — Expansión de PlaceholderAPI para RyxProtections.
 *
 * Placeholders disponibles:
 *  %ryxprotections_dtr%          → DTR actual de la región del jugador
 *  %ryxprotections_dtr_max%      → DTR máximo de la región
 *  %ryxprotections_dtr_ratio%    → Porcentaje de DTR (0-100)
 *  %ryxprotections_raidable%     → "sí"/"no" si la región es raideable
 *  %ryxprotections_region%       → ID de la región donde está el jugador
 *  %ryxprotections_region_name%  → Nombre de la región
 *  %ryxprotections_region_level% → Nivel de la región
 *  %ryxprotections_pp%           → Puntos de Protección del jugador
 *  %ryxprotections_gems%         → Total de gemas del jugador
 */
public class PlaceholderHook extends PlaceholderExpansion {

    private final RyxProtection plugin;

    public PlaceholderHook(RyxProtection plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() { return "ryxprotections"; }

    @Override
    public @NotNull String getAuthor() { return "RyxMC"; }

    @Override
    public @NotNull String getVersion() { return "4.0.0"; }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        String regionId = null;
        List<String> owned = plugin.getRegionStore().getOwnedByPlayer(player.getUniqueId());
        if (!owned.isEmpty()) {
            regionId = owned.get(0);
        }

        // Si no es dueño, buscar región donde está parado
        if (regionId == null) {
            var at = plugin.getRegionStore().getAt(player.getLocation());
            if (at != null) regionId = at.regionId;
        }

        switch (params.toLowerCase()) {
            case "dtr":
                if (regionId == null) return "0";
                return String.format("%.1f", plugin.getDtrManager().get(regionId).getDtr());
            case "dtr_max":
                if (regionId == null) return "0";
                return String.format("%.1f", plugin.getDtrManager().get(regionId).getMaxDtr());
            case "dtr_ratio":
                if (regionId == null) return "0";
                var d = plugin.getDtrManager().get(regionId);
                double ratio = d.getMaxDtr() > 0 ? d.getDtr() / d.getMaxDtr() * 100 : 0;
                return String.format("%.1f", ratio);
            case "raidable":
                if (regionId == null) return "no";
                return plugin.getDtrManager().isRaidable(regionId) ? "sí" : "no";
            case "region":
                return regionId != null ? regionId : "ninguna";
            case "region_name":
                if (regionId == null) return "—";
                return plugin.getRegionStore().getDisplayName(regionId);
            case "region_level":
                if (regionId == null) return "0";
                return String.valueOf(plugin.getUpgradeManager().getLevel(regionId));
            case "pp":
                return String.valueOf(plugin.getUpgradeManager().getPP(player.getUniqueId()));
            case "gems":
                return String.valueOf(plugin.getEnergyManager().countAllGems(player));
            default:
                return null;
        }
    }
}
