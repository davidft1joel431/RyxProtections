package com.ryxmc.protection.factory;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.util.Txt;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * IndustrialFactoryManager v1.3
 * [FIX] startTick() llama f.tick(plugin) — necesario para el sistema real de gemas.
 */
public class IndustrialFactoryManager {

    private final RyxProtection              plugin;
    private final File                       dataFile;
    private YamlConfiguration               data;

    private final Map<String, IndustrialFactory> factories = new LinkedHashMap<>();
    private final Map<String, String>            locIndex     = new HashMap<>();
    private final Map<String, String>            hopperIndex  = new HashMap<>();
    private final Map<String, String>            dropperIndex = new HashMap<>();

    private BukkitTask tickTask;
    private static final int TICK_PERIOD = 10;

    private final Map<UUID, BukkitTask> previews = new HashMap<>();

    public IndustrialFactoryManager(RyxProtection plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "industrial_factories.yml");
    }

    public void init() {
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); }
            catch (IOException e) { plugin.getLogger().severe("[IndustrialFactory] " + e.getMessage()); }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        load();
        startTick();
        plugin.getLogger().info("[IndustrialFactory] Iniciado — " + factories.size() + " fábricas.");
    }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
        previews.values().forEach(t -> { if (!t.isCancelled()) t.cancel(); });
        previews.clear();
        save0();
    }

    public boolean hasPreview(UUID uuid) { return previews.containsKey(uuid); }

    public void startPreview(Player p) {
        stopPreview(p.getUniqueId());
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline()) { stopPreview(p.getUniqueId()); return; }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (!IndustrialFactory.isHeartItem(hand)) { stopPreview(p.getUniqueId()); return; }

            Block target = p.getTargetBlockExact(8);
            if (target == null) return;
            Block heartBlock = target.getRelative(BlockFace.UP);

            BlockFace dir = getPlayerFacing(p);
            Block hopperPos  = heartBlock.getRelative(dir.getOppositeFace());
            Block dropperPos = heartBlock.getRelative(dir);

            World w = heartBlock.getWorld();
            if (w == null) return;

            boolean hopperFree  = hopperPos.getType().isAir();
            boolean heartFree   = heartBlock.getType().isAir();
            boolean dropperFree = dropperPos.getType().isAir();

            Particle.DustOptions blue  = new Particle.DustOptions(Color.fromRGB(0, 150, 255), 1.5f);
            Particle.DustOptions green = new Particle.DustOptions(Color.fromRGB(0, 255, 100), 1.5f);
            Particle.DustOptions red   = new Particle.DustOptions(Color.fromRGB(255, 60,  60), 1.5f);
            Particle.DustOptions gray  = new Particle.DustOptions(Color.fromRGB(150,150,150), 1.0f);

            spawnBlockParticles(w, hopperPos,  hopperFree  ? blue  : gray);
            spawnBlockParticles(w, heartBlock,  heartFree   ? green : gray);
            spawnBlockParticles(w, dropperPos, dropperFree ? red   : gray);

        }, 0L, 3L);
        previews.put(p.getUniqueId(), task);
    }

    public void stopPreview(UUID uuid) {
        BukkitTask t = previews.remove(uuid);
        if (t != null && !t.isCancelled()) t.cancel();
    }

    public boolean tryPlace(Player p, Block surface) {
        Block heartBlock = surface.getRelative(BlockFace.UP);
        BlockFace dir    = getPlayerFacing(p);

        String error = IndustrialFactory.validatePlacement(heartBlock, dir);
        if (error != null) {
            p.sendMessage(Txt.err("No se puede colocar la fábrica industrial: §c" + error));
            p.sendMessage(Txt.warn("§7Las partículas §cgris §7indican bloques §cobstruidos§7."));
            return false;
        }

        Block hopperPos  = heartBlock.getRelative(dir.getOppositeFace());
        Block dropperPos = heartBlock.getRelative(dir);

        heartBlock.setType(IndustrialFactory.HEART_MATERIAL, false);
        hopperPos.setType(IndustrialFactory.HOPPER_MATERIAL, false);
        dropperPos.setType(IndustrialFactory.DROPPER_MATERIAL, false);

        setFacing(hopperPos, dir);
        setFacing(dropperPos, dir);

        String id = "indus_" + p.getName().toLowerCase() + "_" + System.currentTimeMillis() % 10000;
        IndustrialFactory f = new IndustrialFactory(id, heartBlock.getLocation(), dir,
                p.getUniqueId(), p.getName());
        factories.put(id, f);

        locIndex.put(locKey(heartBlock.getLocation()), id);
        hopperIndex.put(locKey(hopperPos.getLocation()), id);
        dropperIndex.put(locKey(dropperPos.getLocation()), id);
        saveAsync();

        stopPreview(p.getUniqueId());

        World w = heartBlock.getWorld();
        if (w != null) {
            w.spawnParticle(Particle.EXPLOSION, heartBlock.getLocation().add(0.5, 1, 0.5),
                    1, 0, 0, 0, 0);
            w.playSound(heartBlock.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 0.9f);
        }

        p.sendMessage(Txt.ok("§6§l⚙ §f§lFábrica Industrial §acolocada."
                + " §8(ID: §e" + id + "§8)"));
        p.sendMessage(Txt.info("§7Tolva §8(detrás)§7 = entrada | Dropper §8(adelante)§7 = salida."));
        p.sendMessage(Txt.warn("§c⚠ §7Romper §fCUALQUIERA §7de los 3 bloques destruye toda la fábrica."));
        p.sendMessage(Txt.info("§7Haz §fclic derecho §7en el corazón para abrir el panel."));
        return true;
    }

    public boolean remove(Block anyPartBlock, Player actor) {
        IndustrialFactory f = getByAnyBlock(anyPartBlock.getLocation());
        if (f == null) return false;

        String id = f.getFactoryId();
        factories.remove(id);
        locIndex.remove(locKey(f.getHeartLoc()));
        hopperIndex.remove(locKey(f.getHopperLoc()));
        dropperIndex.remove(locKey(f.getDropperLoc()));

        f.getHeartLoc().getBlock().setType(Material.AIR, false);
        f.getHopperLoc().getBlock().setType(Material.AIR, false);
        f.getDropperLoc().getBlock().setType(Material.AIR, false);

        World w = f.getHeartLoc().getWorld();
        if (w != null) w.dropItemNaturally(
                f.getHeartLoc().clone().add(0.5, 0.5, 0.5), IndustrialFactory.buildItem());

        saveAsync();
        if (actor != null)
            actor.sendMessage(Txt.ok("§6§l⚙ §fFábrica Industrial §celiminada §7(estructura completa)§a."));
        return true;
    }

    public IndustrialFactory getByHeartLoc(Location loc) {
        String id = locIndex.get(locKey(loc));
        return id != null ? factories.get(id) : null;
    }

    public IndustrialFactory getByAnyBlock(Location loc) {
        String key = locKey(loc);
        String id = locIndex.get(key);
        if (id == null) id = hopperIndex.get(key);
        if (id == null) id = dropperIndex.get(key);
        return id != null ? factories.get(id) : null;
    }

    public boolean isHeartBlock(Location loc) { return locIndex.containsKey(locKey(loc)); }

    public boolean isAnyFactoryPart(Location loc) {
        String key = locKey(loc);
        return locIndex.containsKey(key) || hopperIndex.containsKey(key) || dropperIndex.containsKey(key);
    }

    public Collection<IndustrialFactory> getAll() {
        return Collections.unmodifiableCollection(factories.values());
    }

    public ItemStack createHeartItem() { return IndustrialFactory.buildItem(); }

    public void save() { saveAsync(); }

    private void startTick() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (IndustrialFactory f : new ArrayList<>(factories.values())) {
                try {
                    f.tick(plugin);
                } catch (Exception e) {
                    plugin.getLogger().warning("[IndustrialFactory] Error tick " + f.getFactoryId()
                            + ": " + e.getMessage());
                }
            }
        }, 40L, TICK_PERIOD);
    }

    private BlockFace getPlayerFacing(Player p) {
        float yaw = p.getLocation().getYaw();
        if (yaw < 0) yaw += 360;
        if (yaw < 45 || yaw >= 315) return BlockFace.SOUTH;
        if (yaw < 135) return BlockFace.WEST;
        if (yaw < 225) return BlockFace.NORTH;
        return BlockFace.EAST;
    }

    private void setFacing(Block block, BlockFace dir) {
        try {
            if (block.getBlockData() instanceof Directional d) {
                d.setFacing(dir);
                block.setBlockData(d);
            }
        } catch (Exception ignored) {}
    }

    private void spawnBlockParticles(World w, Block b, Particle.DustOptions dust) {
        Location center = b.getLocation().add(0.5, 0.5, 0.5);
        double r = 0.45;
        double[] offs = {-r, r};
        for (double dx : offs)
            for (double dz : offs)
                w.spawnParticle(Particle.DUST, center.clone().add(dx, 0, dz),
                        1, 0, 0, 0, 0, dust, true);
        for (double dy : offs)
            w.spawnParticle(Particle.DUST, center.clone().add(0, dy, 0),
                    1, 0, 0, 0, 0, dust, true);
    }

    private static String locKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX()
                + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private void load() {
        if (data == null) return;
        for (String id : data.getKeys(false)) {
            try {
                World w = Bukkit.getWorld(data.getString(id + ".world", "world"));
                if (w == null) continue;
                Location heart = new Location(w,
                        data.getDouble(id + ".heartX"),
                        data.getDouble(id + ".heartY"),
                        data.getDouble(id + ".heartZ"));
                BlockFace facing = BlockFace.valueOf(data.getString(id + ".facing", "NORTH"));
                UUID owner = UUID.fromString(data.getString(id + ".owner", UUID.randomUUID().toString()));
                String ownerName = data.getString(id + ".ownerName", "?");

                IndustrialFactory f = new IndustrialFactory(id, heart, facing, owner, ownerName);
                f.setProgressTicks(data.getInt(id + ".progress", 0));
                f.setOutputReady(data.getInt(id + ".output", 0));
                try {
                    f.setCurrentProduct(IndustrialFactory.ProductType.valueOf(
                            data.getString(id + ".product", "GEM_MEDIUM")));
                } catch (Exception ignored) {}
                try {
                    f.setTier(IndustrialFactory.Tier.valueOf(
                            data.getString(id + ".tier", "BASIC")));
                } catch (Exception ignored) {}

                factories.put(id, f);
                locIndex.put(locKey(heart), id);
                hopperIndex.put(locKey(f.getHopperLoc()), id);
                dropperIndex.put(locKey(f.getDropperLoc()), id);
            } catch (Exception e) {
                plugin.getLogger().warning("[IndustrialFactory] Error cargando " + id + ": " + e.getMessage());
            }
        }
    }

    private void save0() {
        if (data == null) return;
        new HashSet<>(data.getKeys(false)).forEach(k -> data.set(k, null));
        for (IndustrialFactory f : factories.values()) {
            String p = f.getFactoryId();
            data.set(p + ".world",     f.getHeartLoc().getWorld().getName());
            data.set(p + ".heartX",    f.getHeartLoc().getX());
            data.set(p + ".heartY",    f.getHeartLoc().getY());
            data.set(p + ".heartZ",    f.getHeartLoc().getZ());
            data.set(p + ".facing",    f.getFacing().name());
            data.set(p + ".owner",     f.getOwnerUUID().toString());
            data.set(p + ".ownerName", f.getOwnerName());
            data.set(p + ".product",   f.getCurrentProduct().name());
            data.set(p + ".progress",  f.getProgressTicks());
            data.set(p + ".output",    f.getOutputReady());
            data.set(p + ".tier",      f.getTier().name());
        }
        try { data.save(dataFile); }
        catch (IOException e) { plugin.getLogger().severe("[IndustrialFactory] Error al guardar: " + e.getMessage()); }
    }

    private void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::save0);
    }
}
