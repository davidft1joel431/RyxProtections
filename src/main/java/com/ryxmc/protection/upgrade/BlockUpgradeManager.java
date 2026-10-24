package com.ryxmc.protection.upgrade;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.manager.RegionStore;
import com.ryxmc.protection.util.Txt;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * BlockUpgradeManager — Mejora administrativa de regiones.
 *
 * Usado por /ps expandir <regionId> para forzar una mejora sin costo de PP.
 * Incrementa el nivel de UpgradeManager y redimensiona la región en WorldGuard.
 */
public class BlockUpgradeManager {

    private final RyxProtection plugin;

    public BlockUpgradeManager(RyxProtection plugin) {
        this.plugin = plugin;
    }

    /**
     * Fuerza una mejora de nivel al siguiente sin cobrar PP.
     * @return true si se realizó, false si ya está al máximo o hubo error.
     */
    public boolean performUpgrade(Player actor, String regionId) {
        RegionStore.RegionRecord r = plugin.getRegionStore().getRecord(regionId);
        if (r == null) {
            actor.sendMessage(Txt.err("Región §f" + regionId + "§c no encontrada."));
            return false;
        }

        int currentLvl = plugin.getUpgradeManager().getLevel(regionId);
        int maxLvl     = plugin.getUpgradeManager().getLevelsData().length;

        if (currentLvl >= maxLvl) {
            actor.sendMessage(Txt.warn("La región §e" + regionId + "§e ya está al nivel máximo."));
            return false;
        }

        int newLevel = currentLvl + 1;
        plugin.getUpgradeManager().setLevel(regionId, newLevel);
        plugin.getDtrManager().recalcMax(regionId);

        // Redimensionar la región en WorldGuard
        boolean resized = resizeWGRegion(r);

        plugin.getHologramManager().updateRegionHologram(regionId);
        plugin.getPreviewManager().startRegionPreview(actor, regionId);

        actor.sendMessage(Txt.ok("Región §e" + regionId + "§a mejorada a §f"
                + plugin.getUpgradeManager().getLevelName(newLevel) + "§a."
                + (resized ? " Radio: §f±" + plugin.getUpgradeManager().getXRadius(regionId) : "")));

        // Notificar al dueño si está online y no es el admin
        if (!r.ownerUUID.equals(actor.getUniqueId())) {
            Player owner = Bukkit.getPlayer(r.ownerUUID);
            if (owner != null) {
                owner.sendMessage(Txt.ok("Un admin mejoró tu región §e"
                        + plugin.getRegionStore().getDisplayName(regionId)
                        + "§a al nivel §f" + plugin.getUpgradeManager().getLevelName(newLevel) + "§a."));
            }
        }
        return true;
    }

    /** Redimensiona la región WorldGuard con el nuevo radio. */
    private boolean resizeWGRegion(RegionStore.RegionRecord r) {
        if (r.blockLoc == null) return false;
        World world = Bukkit.getWorld(r.worldName);
        if (world == null) return false;

        RegionManager rm = plugin.getRegionStore().getRM(world);
        if (rm == null) return false;

        ProtectedRegion wg = rm.getRegion(r.regionId);
        if (wg == null) return false;

        int xr = plugin.getUpgradeManager().getXRadius(r.regionId);
        int x  = r.blockLoc.getBlockX();
        int z  = r.blockLoc.getBlockZ();

        BlockVector3 min = BlockVector3.at(x - xr, world.getMinHeight(), z - xr);
        BlockVector3 max = BlockVector3.at(x + xr, world.getMaxHeight(), z + xr);

        ProtectedCuboidRegion newReg = new ProtectedCuboidRegion(r.regionId, min, max);
        try { newReg.copyFrom(wg); } catch (Exception ignored) {}
        rm.removeRegion(r.regionId);
        rm.addRegion(newReg);
        return true;
    }
}
