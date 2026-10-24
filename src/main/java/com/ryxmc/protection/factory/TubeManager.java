package com.ryxmc.protection.factory;

import com.ryxmc.protection.RyxProtection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * TubeManager v2.2
 *
 * [FIX] Red completa: Fábrica de Energía y Compresor ahora auto-entregan
 * su output a través de la red hacia cofres, otro compresor, o la tolva
 * (HOPPER) de una Fábrica Industrial. Antes solo el compresor lo hacía
 * y únicamente hacia cofres/otro compresor — la fábrica de energía nunca
 * empujaba nada, quedando su producción atrapada.
 *
 * Cadena completa soportada:
 *  Fábrica de Energía → tubos → Compresor Mediano → tubos →
 *  Compresor Grande → tubos → Tolva de Fábrica Industrial → tubos → Cofre
 */
public class TubeManager {

    private static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.SOUTH,
            BlockFace.EAST,  BlockFace.WEST,
            BlockFace.UP,    BlockFace.DOWN
    };

    private static final BlockFace[] CYCLE_ORDER = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH,
            BlockFace.WEST,  BlockFace.UP,   BlockFace.DOWN
    };

    private static final Set<Material> DESTINATION_TYPES = Set.of(
            Material.BLAST_FURNACE, Material.CRAFTER, Material.DISPENSER,
            Material.HOPPER, Material.DROPPER
    );

    /** [FIX] Destinos válidos para salida de fábricas/compresores — incluye HOPPER industrial. */
    public static final Set<Material> NETWORK_OUTPUT_DEST = Set.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL, Material.CRAFTER,
            Material.HOPPER,
            Material.SHULKER_BOX,
            Material.WHITE_SHULKER_BOX, Material.ORANGE_SHULKER_BOX, Material.MAGENTA_SHULKER_BOX,
            Material.LIGHT_BLUE_SHULKER_BOX, Material.YELLOW_SHULKER_BOX, Material.LIME_SHULKER_BOX,
            Material.PINK_SHULKER_BOX, Material.GRAY_SHULKER_BOX, Material.LIGHT_GRAY_SHULKER_BOX,
            Material.CYAN_SHULKER_BOX, Material.PURPLE_SHULKER_BOX, Material.BLUE_SHULKER_BOX,
            Material.BROWN_SHULKER_BOX, Material.GREEN_SHULKER_BOX, Material.RED_SHULKER_BOX,
            Material.BLACK_SHULKER_BOX
    );

    private static final int MAX_BFS_DISTANCE = 64;

    private final RyxProtection plugin;
    private final File          dataFile;
    private YamlConfiguration  data;

    private final Set<String> tubes = new HashSet<>();
    private final Map<String, String> directionals = new HashMap<>();
    private final Map<String, String> combiners = new HashMap<>();

    private BukkitTask networkDeliveryTask;
    private static final long NETWORK_DELIVERY_PERIOD = 40L; // 2s

    public TubeManager(RyxProtection plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "tubes.yml");
    }

    public void init() {
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); }
            catch (IOException e) { plugin.getLogger().severe("[Tubes] " + e.getMessage()); }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);

        tubes.addAll(data.getStringList("tubes"));
        if (data.isConfigurationSection("directionals")) {
            for (String key : data.getConfigurationSection("directionals").getKeys(false)) {
                directionals.put(key, data.getString("directionals." + key, "UP"));
            }
        }
        if (data.isConfigurationSection("combiners")) {
            for (String key : data.getConfigurationSection("combiners").getKeys(false)) {
                combiners.put(key, data.getString("combiners." + key, "NORTH"));
            }
        }

        networkDeliveryTask = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> tickNetworkOutputDelivery(plugin), 60L, NETWORK_DELIVERY_PERIOD);

        plugin.getLogger().info("[Tubes] Iniciado — " + tubes.size() + " tubos, "
                + directionals.size() + " dianas, " + combiners.size() + " combinadores.");
    }

    public void shutdown() {
        if (networkDeliveryTask != null) networkDeliveryTask.cancel();
        save();
    }

    public void register(Location loc) { tubes.add(locKey(loc)); saveAsync(); }
    public void unregister(Location loc) { if (tubes.remove(locKey(loc))) saveAsync(); }
    public boolean isTube(Location loc) { return tubes.contains(locKey(loc)); }
    public int getTubeCount() { return tubes.size(); }

    public void registerDirectional(Location loc, BlockFace initialFace) {
        directionals.put(locKey(loc), initialFace.name());
        saveAsync();
    }

    public void unregisterDirectional(Location loc) {
        if (directionals.remove(locKey(loc)) != null) saveAsync();
    }

    public boolean isDirectional(Location loc) { return directionals.containsKey(locKey(loc)); }
    public BlockFace getOutputFace(Location loc) { return parseFaceOrDefault(directionals.get(locKey(loc)), BlockFace.UP); }

    public BlockFace cycleDirectional(Location loc) {
        BlockFace next = nextInCycle(getOutputFace(loc));
        directionals.put(locKey(loc), next.name());
        saveAsync();
        return next;
    }

    public int getDirectionalCount() { return directionals.size(); }

    public void registerCombiner(Location loc, BlockFace outputFace) {
        combiners.put(locKey(loc), outputFace.name());
        saveAsync();
    }

    public void unregisterCombiner(Location loc) {
        if (combiners.remove(locKey(loc)) != null) saveAsync();
    }

    public boolean isCombiner(Location loc) { return combiners.containsKey(locKey(loc)); }
    public BlockFace getCombinerOutputFace(Location loc) { return parseFaceOrDefault(combiners.get(locKey(loc)), BlockFace.NORTH); }

    public BlockFace cycleCombiner(Location loc) {
        BlockFace next = nextInCycle(getCombinerOutputFace(loc));
        combiners.put(locKey(loc), next.name());
        saveAsync();
        applyCombinerBlockFacing(loc, next);
        return next;
    }

    public int getCombinerCount() { return combiners.size(); }

    public void applyCombinerBlockFacing(Location loc, BlockFace face) {
        try {
            Block b = loc.getBlock();
            if (b.getBlockData() instanceof Directional dir) {
                dir.setFacing(face);
                b.setBlockData(dir, false);
            }
        } catch (Exception ignored) {}
    }

    private BlockFace nextInCycle(BlockFace current) {
        int idx = 0;
        for (int i = 0; i < CYCLE_ORDER.length; i++)
            if (CYCLE_ORDER[i] == current) { idx = i; break; }
        return CYCLE_ORDER[(idx + 1) % CYCLE_ORDER.length];
    }

    private BlockFace parseFaceOrDefault(String raw, BlockFace fallback) {
        if (raw == null) return fallback;
        try { return BlockFace.valueOf(raw); }
        catch (Exception e) { return fallback; }
    }

    public void placeTube(Block place) {
        place.setType(TubeNetwork.TUBE_MATERIAL, false);
        BlockFace orientation = determineOrientation(place);
        try {
            if (place.getBlockData() instanceof Directional dir) {
                dir.setFacing(orientation);
                place.setBlockData(dir, false);
            }
        } catch (Exception ignored) {}
        register(place.getLocation());
    }

    private BlockFace determineOrientation(Block placed) {
        List<BlockFace> connections = new ArrayList<>();
        for (BlockFace face : FACES) {
            Block neighbor = placed.getRelative(face);
            Location nloc = neighbor.getLocation();
            if (isTube(nloc) || isDirectional(nloc) || isCombiner(nloc) || isDestination(neighbor)) {
                connections.add(face);
            }
        }
        if (connections.isEmpty()) return BlockFace.UP;
        for (BlockFace face : connections) {
            if (connections.contains(face.getOppositeFace())) return face;
        }
        return connections.get(0);
    }

    private boolean isDestination(Block block) { return DESTINATION_TYPES.contains(block.getType()); }
    private boolean isNetworkNode(Location loc) { return isTube(loc) || isDirectional(loc) || isCombiner(loc); }

    public List<Block> findDestinations(Block origin, Set<Material> destTypes) {
        List<Block>   results = new ArrayList<>();
        Set<Location> visited = new HashSet<>();
        Queue<Block>  queue   = new LinkedList<>();

        visited.add(origin.getLocation());
        queue.add(origin);

        int steps = 0;
        while (!queue.isEmpty() && steps < MAX_BFS_DISTANCE) {
            Block current = queue.poll();
            steps++;
            Location curLoc = current.getLocation();

            BlockFace[] facesToExplore;
            if (isDirectional(curLoc))    facesToExplore = new BlockFace[]{ getOutputFace(curLoc) };
            else if (isCombiner(curLoc))  facesToExplore = new BlockFace[]{ getCombinerOutputFace(curLoc) };
            else                          facesToExplore = FACES;

            for (BlockFace face : facesToExplore) {
                Block neighbor = current.getRelative(face);
                Location nloc = neighbor.getLocation();
                if (visited.contains(nloc)) continue;
                visited.add(nloc);

                if (isNetworkNode(nloc)) {
                    queue.add(neighbor);
                } else if (destTypes.contains(neighbor.getType())) {
                    results.add(neighbor);
                }
            }
        }
        return results;
    }

    public boolean areConnected(Block a, Block b) {
        Set<Location> visited = new HashSet<>();
        Queue<Block>  queue   = new LinkedList<>();
        visited.add(a.getLocation());
        queue.add(a);

        int steps = 0;
        while (!queue.isEmpty() && steps < MAX_BFS_DISTANCE) {
            Block current = queue.poll();
            steps++;
            Location curLoc = current.getLocation();
            BlockFace[] facesToExplore;
            if (isDirectional(curLoc))    facesToExplore = new BlockFace[]{ getOutputFace(curLoc) };
            else if (isCombiner(curLoc))  facesToExplore = new BlockFace[]{ getCombinerOutputFace(curLoc) };
            else                          facesToExplore = FACES;

            for (BlockFace face : facesToExplore) {
                Block nb = current.getRelative(face);
                Location nloc = nb.getLocation();
                if (visited.contains(nloc)) continue;
                visited.add(nloc);
                if (nloc.equals(b.getLocation())) return true;
                if (isNetworkNode(nloc)) queue.add(nb);
            }
        }
        return false;
    }

    // ─────────────────────────────────────────────────
    //  [FIX] Auto-entrega — Fábrica de Energía y Compresor
    // ─────────────────────────────────────────────────

    public void tickNetworkOutputDelivery(RyxProtection plugin) {
        Set<String> processed = new HashSet<>();
        Set<String> allNodes  = new HashSet<>(tubes);
        allNodes.addAll(directionals.keySet());
        allNodes.addAll(combiners.keySet());

        for (String key : allNodes) {
            Location nodeLoc = parseLocKey(key);
            if (nodeLoc == null || nodeLoc.getWorld() == null) continue;
            Block nodeBlock = nodeLoc.getBlock();

            for (BlockFace face : FACES) {
                Block neighbor = nodeBlock.getRelative(face);
                Material type  = neighbor.getType();
                String neighborKey = locKey(neighbor.getLocation());

                if (type == Material.CRAFTER) {
                    if (processed.contains(neighborKey)) continue;
                    processed.add(neighborKey);
                    deliverFromCompressor(plugin, neighbor);
                } else if (type == Material.BLAST_FURNACE) {
                    if (processed.contains(neighborKey)) continue;
                    processed.add(neighborKey);
                    deliverFromEnergyFactory(plugin, neighbor);
                }
            }
        }
    }

    private void deliverFromCompressor(RyxProtection plugin, Block compressorBlock) {
        if (plugin.getCompressorManager() == null) return;
        try {
            GemCompressor comp = plugin.getCompressorManager().getAt(compressorBlock.getLocation());
            if (comp == null || !comp.hasPendingOutput()) return;

            int remaining = comp.getPendingOutput();
            com.ryxmc.protection.energy.EnergyManager.GemType outType =
                    comp.getMode() == GemCompressor.Mode.LARGE
                            ? com.ryxmc.protection.energy.EnergyManager.GemType.LARGE
                            : com.ryxmc.protection.energy.EnergyManager.GemType.MEDIUM;

            ItemStack sample = plugin.getEnergyManager().createGem(outType);
            if (sample == null) return;

            List<Block> targets = findDestinations(compressorBlock, NETWORK_OUTPUT_DEST);
            if (targets.isEmpty()) return;

            int delivered = 0;
            for (Block target : targets) {
                int stillNeeded = remaining - delivered;
                if (stillNeeded <= 0) break;
                delivered += TransferBridge.deposit(plugin, target, sample, stillNeeded);
            }

            if (delivered > 0) {
                comp.setPendingOutput(remaining - delivered);
                plugin.getCompressorManager().save();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Tubes] Error entregando output de compresor: " + e.getMessage());
        }
    }

    /**
     * [FIX] Empuja el output de una Fábrica de Energía a través de la red.
     * Verifica isHeartBlock() para no confundirla con el corazón de una
     * Fábrica Industrial (ambas usan BLAST_FURNACE como marcador visual).
     */
    private void deliverFromEnergyFactory(RyxProtection plugin, Block factoryBlock) {
        if (plugin.getFactoryManager() == null) return;
        if (plugin.getIndustrialFactoryManager() != null
                && plugin.getIndustrialFactoryManager().isHeartBlock(factoryBlock.getLocation())) return;

        try {
            var factory = plugin.getFactoryManager().getFactoryAt(factoryBlock.getLocation());
            if (factory == null || !factory.hasOutput()) return;

            int available = factory.getOutputReady();
            if (available <= 0) return;

            ItemStack sample = plugin.getEnergyManager()
                    .createGem(com.ryxmc.protection.energy.EnergyManager.GemType.SMALL);
            if (sample == null) return;

            List<Block> targets = findDestinations(factoryBlock, NETWORK_OUTPUT_DEST);
            if (targets.isEmpty()) return;

            int delivered = 0;
            for (Block target : targets) {
                int stillNeeded = available - delivered;
                if (stillNeeded <= 0) break;
                delivered += TransferBridge.deposit(plugin, target, sample, stillNeeded);
            }

            if (delivered > 0) {
                int collected = factory.collectOutput();
                if (delivered < collected) {
                    factory.setOutputReady(factory.getOutputReady() + (collected - delivered));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Tubes] Error entregando output de fábrica: " + e.getMessage());
        }
    }

    private void save() {
        if (data == null) return;
        data.set("tubes", new ArrayList<>(tubes));

        data.set("directionals", null);
        for (Map.Entry<String, String> e : directionals.entrySet())
            data.set("directionals." + e.getKey(), e.getValue());

        data.set("combiners", null);
        for (Map.Entry<String, String> e : combiners.entrySet())
            data.set("combiners." + e.getKey(), e.getValue());

        try { data.save(dataFile); }
        catch (IOException e) { plugin.getLogger().severe("[Tubes] Error al guardar: " + e.getMessage()); }
    }

    private void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::save);
    }

    private static String locKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX()
                + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private static Location parseLocKey(String key) {
        try {
            String[] parts = key.split(":");
            if (parts.length != 4) return null;
            World w = Bukkit.getWorld(parts[0]);
            if (w == null) return null;
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(w, x, y, z);
        } catch (Exception e) {
            return null;
        }
    }
}
