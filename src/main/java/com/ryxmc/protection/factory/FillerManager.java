package com.ryxmc.protection.factory;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * FillerManager v1.2
 *
 * [FIX] El tick task ahora llama f.tick(plugin) — FillerFactory usa
 * internamente TransferBridge para depositar correctamente tanto en
 * fábricas (API interna EnergyFactory.addMaterial) como en contenedores
 * reales (tolva, dropper, compresor).
 */
public class FillerManager {

    private final RyxProtection            plugin;
    private final File                     dataFile;
    private YamlConfiguration             data;

    private final Map<String, FillerFactory> fillers  = new LinkedHashMap<>();
    private final Map<String, String>        locIndex = new HashMap<>();

    private BukkitTask tickTask;
    private static final int TICK_PERIOD = 5;

    public FillerManager(RyxProtection plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "fillers.yml");
    }

    public void init() {
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); }
            catch (IOException e) { plugin.getLogger().severe("[Fillers] " + e.getMessage()); }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        load();
        startTask();
        plugin.getLogger().info("[Fillers] Iniciado — " + fillers.size() + " rellenadores.");
    }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
        save();
    }

    public String register(Player owner, Location loc) {
        String key = locKey(loc);
        if (locIndex.containsKey(key)) return locIndex.get(key);

        String id = "filler_" + owner.getName().toLowerCase() + "_" + System.currentTimeMillis() % 10000;
        FillerFactory f = new FillerFactory(id, loc, owner.getUniqueId(), owner.getName());
        fillers.put(id, f);
        locIndex.put(key, id);
        saveAsync();

        spawnPlaceParticles(loc);
        owner.sendMessage(Txt.ok("§6§l⚡ §fʀᴇʟʟᴇɴᴀᴅᴏʀ §aregistrado."
                + " §7Coloca cofres §fdetrás §7y conecta con §bTubos §7a las fábricas."));
        return id;
    }

    public boolean unregister(Location loc) {
        String key = locKey(loc);
        String id  = locIndex.remove(key);
        if (id == null) return false;
        fillers.remove(id);
        saveAsync();
        return true;
    }

    public FillerFactory getByLocation(Location loc) {
        String id = locIndex.get(locKey(loc));
        return id != null ? fillers.get(id) : null;
    }

    public boolean isFillerLocation(Location loc) {
        return locIndex.containsKey(locKey(loc));
    }

    public Collection<FillerFactory> getAll() {
        return Collections.unmodifiableCollection(fillers.values());
    }

    private void startTask() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (FillerFactory f : new ArrayList<>(fillers.values())) {
                if (f.getLocation().getBlock().getType() != Material.DISPENSER) {
                    unregister(f.getLocation());
                    continue;
                }

                // [FIX] tick(plugin) — usa TransferBridge internamente,
                // que sabe depositar correctamente en fábricas y contenedores.
                boolean worked = f.tick(plugin);
                if (worked) {
                    Bukkit.getScheduler().runTask(plugin, () -> spawnTransferParticles(f.getLocation()));
                }
            }
        }, 20L, TICK_PERIOD);
    }

    private void spawnPlaceParticles(Location loc) {
        World w = loc.getWorld();
        if (w == null) return;
        w.spawnParticle(Particle.ELECTRIC_SPARK,
                loc.clone().add(0.5, 1.0, 0.5), 30, 0.4, 0.4, 0.4, 0.05);
        w.playSound(loc, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
    }

    private void spawnTransferParticles(Location loc) {
        World w = loc.getWorld();
        if (w == null) return;
        w.spawnParticle(Particle.HAPPY_VILLAGER,
                loc.clone().add(0.5, 1.2, 0.5), 5, 0.3, 0.3, 0.3, 0.0);
    }

    public ItemStack createFillerItem() {
        return FillerFactory.buildItem();
    }

    private void load() {
        if (data == null) return;
        for (String id : data.getKeys(false)) {
            try {
                String worldName = data.getString(id + ".world");
                World world = Bukkit.getWorld(worldName != null ? worldName : "");
                if (world == null) continue;
                double x = data.getDouble(id + ".x");
                double y = data.getDouble(id + ".y");
                double z = data.getDouble(id + ".z");
                Location loc = new Location(world, x, y, z);
                String ownerStr  = data.getString(id + ".owner", "");
                String ownerName = data.getString(id + ".ownerName", "?");
                UUID ownerUUID;
                try { ownerUUID = UUID.fromString(ownerStr); }
                catch (Exception e) { continue; }

                FillerFactory f = new FillerFactory(id, loc, ownerUUID, ownerName);
                f.setItemsFilled(data.getInt(id + ".itemsFilled", 0));
                fillers.put(id, f);
                locIndex.put(locKey(loc), id);
            } catch (Exception e) {
                plugin.getLogger().warning("[Fillers] Error cargando " + id + ": " + e.getMessage());
            }
        }
    }

    private void save() {
        if (data == null) return;
        new HashSet<>(data.getKeys(false)).forEach(k -> data.set(k, null));
        for (FillerFactory f : fillers.values()) {
            String path = f.getFillerId();
            data.set(path + ".world",       f.getLocation().getWorld().getName());
            data.set(path + ".x",           f.getLocation().getX());
            data.set(path + ".y",           f.getLocation().getY());
            data.set(path + ".z",           f.getLocation().getZ());
            data.set(path + ".owner",       f.getOwnerUUID().toString());
            data.set(path + ".ownerName",   f.getOwnerName());
            data.set(path + ".itemsFilled", f.getItemsFilled());
        }
        try { data.save(dataFile); }
        catch (IOException e) { plugin.getLogger().severe("[Fillers] Error guardando: " + e.getMessage()); }
    }

    private void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::save);
    }

    private static String locKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX()
                + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }
}
