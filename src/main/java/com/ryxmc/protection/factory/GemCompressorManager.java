package com.ryxmc.protection.factory;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.energy.EnergyManager;
import com.ryxmc.protection.util.Txt;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * GemCompressorManager v2.0 — Cadena de produccion.
 *
 * Fabrica → Comp.Mediano → Comp.Grande → Cofre
 * (peq.)    (peq.→med.)    (med.→gde.)
 *
 * Cada compresor tiene un solo modelo (MEDIUM o LARGE).
 * Entrega automatica via cadena de facing.
 */
public class GemCompressorManager {

    private final RyxProtection plugin;
    private final Map<String, GemCompressor> compressors = new LinkedHashMap<>();
    private final Map<String, Long> recentPlace = new HashMap<>();
    private static final long PLACE_CD_MS = 1000L;

    private BukkitTask tickTask;
    private BukkitTask saveTask;
    private BukkitTask particleTask;
    private File saveFile;

    private static final String COMPRESSOR_NAME = "§5§l⚗ §d§lCompresor de Gemas";
    private static final String PDC_KEY = "gem_compressor";

    private static final BlockFace[] H_FACES = {
        BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    public GemCompressorManager(RyxProtection plugin) { this.plugin = plugin; }

    public void init() {
        saveFile = new File(plugin.getDataFolder(), "compressors.yml");
        load();
        startTasks();
        plugin.getLogger().info("[Compressor] v2.0 iniciado. Compresores: " + compressors.size());
    }

    public void shutdown() {
        save();
        cancelTask(tickTask); cancelTask(saveTask); cancelTask(particleTask);
    }

    private void cancelTask(BukkitTask t) { if (t != null && !t.isCancelled()) t.cancel(); }

    // ── Registro ──────────────────────────────────────

    public GemCompressor place(Player player, Location loc, BlockFace facing) {
        String key = GemCompressor.makeId(loc);
        if (compressors.containsKey(key)) return null;
        long now = System.currentTimeMillis();
        if (now - recentPlace.getOrDefault(key, 0L) < PLACE_CD_MS) return null;
        recentPlace.put(key, now);
        String id = "comp_" + player.getName().toLowerCase()
                + "_" + loc.getBlockX() + "_" + loc.getBlockZ();
        GemCompressor c = new GemCompressor(id, loc, player.getUniqueId(), player.getName(), facing);
        compressors.put(key, c);
        save();
        return c;
    }

    public boolean remove(Location loc) {
        String key = GemCompressor.makeId(loc);
        if (compressors.remove(key) != null) { recentPlace.remove(key); save(); return true; }
        return false;
    }

    public GemCompressor getAt(Location loc) { return compressors.get(GemCompressor.makeId(loc)); }
    public boolean isCompressor(Location loc) { return compressors.containsKey(GemCompressor.makeId(loc)); }
    public Collection<GemCompressor> getAll() { return Collections.unmodifiableCollection(compressors.values()); }

    /** Intenta mejorar el compresor. Retorna null si éxito, mensaje de error si falla. */
    public String upgrade(Player player, Location loc) {
        GemCompressor c = getAt(loc);
        if (c == null) return "Compresor no encontrado.";
        if (!c.getOwnerUUID().equals(player.getUniqueId()) && !player.hasPermission("ryx.admin"))
            return "No eres el dueño de este compresor.";
        if (!c.canUpgrade()) return "El compresor ya está al nivel máximo.";

        int cost = c.getUpgradeCost();
        int pp = plugin.getUpgradeManager().getPP(player.getUniqueId());
        if (pp < cost) return "Necesitas §e" + cost + " PP§c. Tienes §e" + pp + "§c PP.";

        plugin.getUpgradeManager().setPP(player.getUniqueId(), pp - cost);
        c.upgrade();
        save();
        return null;
    }

    // ── Tasks ─────────────────────────────────────────

    private void startTasks() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (GemCompressor c : new ArrayList<>(compressors.values())) {
                if (!c.isProcessing()) c.tryStart();
                c.tick();
                if (c.hasPendingOutput()) deliverOutput(c);
            }
        }, 4L, 4L);

        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (GemCompressor c : new ArrayList<>(compressors.values()))
                if (c.isProcessing()) spawnSmeltParticles(c);
        }, 0L, 6L);

        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::save, 6000L, 6000L);
    }

    // ── Partículas de fundición ───────────────────────

    private void spawnSmeltParticles(GemCompressor c) {
        Location loc = c.getLocation();
        World world = loc.getWorld();
        if (world == null) return;
        Location center = loc.clone().add(0.5, 1.1, 0.5);
        double ratio = c.getProgressRatio();

        int rgb = c.getMode() == GemCompressor.Mode.LARGE ? 0xAA00FF : 0x00BFFF;
        java.awt.Color col = new java.awt.Color(rgb);
        Particle.DustOptions dust = new Particle.DustOptions(
                Color.fromRGB(col.getRed(), col.getGreen(), col.getBlue()), 0.9f);

        for (int i = 0; i < 3; i++) {
            double angle = Math.random() * Math.PI * 2;
            double r = 0.25 + Math.random() * 0.15;
            world.spawnParticle(Particle.DUST,
                    center.clone().add(r * Math.cos(angle), Math.random() * 0.3, r * Math.sin(angle)),
                    1, 0, 0, 0, 0, dust);
        }
        if (ratio > 0.5)  world.spawnParticle(Particle.SMALL_FLAME, center, 1, 0.1, 0.05, 0.1, 0.01);
        if (ratio > 0.95) world.spawnParticle(Particle.END_ROD, center, 5, 0.15, 0.15, 0.15, 0.05);

        if (c.hasPendingOutput()) {
            Location nextLoc = findNextBlockLoc(c);
            if (nextLoc != null) spawnTransferLine(world,
                    loc.clone().add(0.5,0.5,0.5), nextLoc.clone().add(0.5,0.5,0.5));
        }
    }

    private void spawnTransferLine(World world, Location from, Location to) {
        double dx = (to.getX() - from.getX()) / 6.0;
        double dy = (to.getY() - from.getY()) / 6.0;
        double dz = (to.getZ() - from.getZ()) / 6.0;
        Particle.DustOptions dot = new Particle.DustOptions(Color.fromRGB(221,160,221), 0.6f);
        for (int i = 1; i <= 5; i++)
            world.spawnParticle(Particle.DUST,
                    from.getX()+dx*i, from.getY()+dy*i+0.1, from.getZ()+dz*i,
                    1,0,0,0,0,dot);
    }

    // ── Entrega automática en cadena ──────────────────

    private void deliverOutput(GemCompressor c) {
        World world = c.getLocation().getWorld();
        if (world == null || c.getPendingOutput() <= 0) return;

        EnergyManager.GemType gemType = c.getMode() == GemCompressor.Mode.LARGE
                ? EnergyManager.GemType.LARGE : EnergyManager.GemType.MEDIUM;

        Block compressorBlock = c.getLocation().getBlock();
        ItemStack sample = plugin.getEnergyManager().createGem(gemType);
        if (sample == null) return;

        // ─── 1. ENTREGA POR RED DE TUBOS (prioridad) ───
        // Detecta si hay tubos conectados y busca destinos (cofre, otro
        // compresor, fábrica, tolva) a través de la red usando BFS.
        if (plugin.getTubeManager() != null) {
            List<Block> targets = plugin.getTubeManager().findDestinations(
                    compressorBlock, TubeManager.NETWORK_OUTPUT_DEST);
            if (!targets.isEmpty()) {
                int remaining = c.getPendingOutput();
                int delivered = 0;
                for (Block target : targets) {
                    int stillNeeded = remaining - delivered;
                    if (stillNeeded <= 0) break;
                    delivered += TransferBridge.deposit(plugin, target, sample, stillNeeded);
                }
                if (delivered > 0) {
                    c.setPendingOutput(remaining - delivered);
                    save();
                    if (c.hasPendingOutput() && c.getMode() != GemCompressor.Mode.NONE)
                        c.tryStart();
                    return; // Entregado vía tubos
                }
            }
        }

        // ─── 2. FALLBACK: compresor adyacente directo (sin tubos) ───
        // Solo MEDIUM → LARGE, bloques adyacentes (no usa red de tubos)
        if (gemType == EnergyManager.GemType.MEDIUM) {
            GemCompressor nextComp = findNextCompressor(c, gemType);
            if (nextComp != null) {
                int space = 64 - nextComp.getInputCount();
                if (space > 0) {
                    int toSend = Math.min(c.getPendingOutput(), space);
                    if (nextComp.getInputStack() == null) {
                        ItemStack gs = plugin.getEnergyManager().createGem(gemType);
                        gs.setAmount(toSend);
                        nextComp.setInputStack(gs);
                    } else {
                        nextComp.getInputStack().setAmount(nextComp.getInputCount() + toSend);
                    }
                    c.setPendingOutput(c.getPendingOutput() - toSend);
                    nextComp.tryStart();
                    save();
                    if (c.hasPendingOutput()) c.tryStart();
                    return;
                }
            }
        }

        // ─── 3. FALLBACK: cofre adyacente directo ───
        Inventory chest = findAdjacentChest(c);
        if (chest != null) {
            dropAllPending(c, gemType, world, chest);
            return;
        }

        // ─── 4. Último recurso: dropear al suelo ───
        dropAllPending(c, gemType, world, null);
    }

    private void dropAllPending(GemCompressor c, EnergyManager.GemType type,
                                 World world, Inventory chest) {
        int pending = c.getPendingOutput();
        Location dropAt = c.getLocation().clone().add(0.5, 1, 0.5);
        while (pending > 0) {
            int amt = Math.min(pending, 64);
            ItemStack gem = plugin.getEnergyManager().createGem(type);
            gem.setAmount(amt);
            if (chest != null) {
                Map<Integer, ItemStack> left = chest.addItem(gem);
                left.values().forEach(it -> world.dropItemNaturally(dropAt, it));
            } else {
                world.dropItemNaturally(dropAt, gem);
            }
            pending -= amt;
        }
        c.setPendingOutput(0);
        save();
    }

    // Output MEDIUM → buscar compresor LARGE adyacente
    private GemCompressor findNextCompressor(GemCompressor c, EnergyManager.GemType outputType) {
        if (outputType != EnergyManager.GemType.MEDIUM) return null;
        List<BlockFace> order = orderedFaces(c.getFacing());
        for (BlockFace face : order) {
            Location adj = c.getLocation().clone().add(face.getModX(), 0, face.getModZ());
            GemCompressor next = getAt(adj);
            if (next != null && next.getMode() == GemCompressor.Mode.LARGE) return next;
        }
        return null;
    }

    private Location findNextBlockLoc(GemCompressor c) {
        for (BlockFace face : orderedFaces(c.getFacing())) {
            Location adj = c.getLocation().clone().add(face.getModX(), 0, face.getModZ());
            if (getAt(adj) != null) return adj;
            Block b = adj.getBlock();
            if (b.getType() == Material.CHEST || b.getType() == Material.TRAPPED_CHEST) return adj;
        }
        return null;
    }

    private Inventory findAdjacentChest(GemCompressor c) {
        for (BlockFace face : orderedFaces(c.getFacing())) {
            Block adj = c.getLocation().getBlock().getRelative(face);
            if (adj.getType() == Material.CHEST || adj.getType() == Material.TRAPPED_CHEST) {
                Chest chest = (Chest) adj.getState();
                if (chest.getInventory().getHolder() instanceof DoubleChest dc)
                    return dc.getInventory();
                return chest.getInventory();
            }
        }
        return null;
    }

    private List<BlockFace> orderedFaces(BlockFace primary) {
        List<BlockFace> list = new ArrayList<>();
        list.add(primary);
        for (BlockFace f : H_FACES) if (f != primary) list.add(f);
        return list;
    }

    // ── Ítem del compresor ────────────────────────────

    public ItemStack createCompressorItem() {
        ItemStack item = new ItemStack(Material.CRAFTER);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(com.ryxmc.protection.util.Color.of(COMPRESSOR_NAME));
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.setLore(Arrays.asList(
                com.ryxmc.protection.util.Color.of(Txt.THIN),
                com.ryxmc.protection.util.Color.of("§7Un solo modelo por compresor:"),
                com.ryxmc.protection.util.Color.of("§8• §bTrim comun  §7→ §d16 peq. = 1 med."),
                com.ryxmc.protection.util.Color.of("§8• §5Trim raro   §7→ §54 med. = 1 gde."),
                com.ryxmc.protection.util.Color.of(Txt.THIN),
                com.ryxmc.protection.util.Color.of("§9Cadena: §bMed. §8→ §5Gde. §8→ §7Cofre"),
                com.ryxmc.protection.util.Color.of(Txt.THIN),
                com.ryxmc.protection.util.Color.of("§7Sostén el Armor Trim y haz §bclic derecho.")));
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, PDC_KEY),
                org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isCompressorItem(ItemStack item) {
        if (item == null || item.getType() != Material.CRAFTER) return false;
        ItemMeta m = item.getItemMeta();
        if (m == null) return false;
        if (m.getPersistentDataContainer().has(
                new org.bukkit.NamespacedKey(plugin, PDC_KEY),
                org.bukkit.persistence.PersistentDataType.BYTE)) return true;
        if (!m.hasDisplayName()) return false;
        String name = org.bukkit.ChatColor.stripColor(m.getDisplayName());
        return name != null && name.contains("Compresor de Gemas");
    }

    // ── Persistencia ──────────────────────────────────

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        int i = 0;
        for (GemCompressor c : compressors.values()) {
            String p = "comp." + i;
            Location l = c.getLocation();
            cfg.set(p+".id",           c.getId());
            cfg.set(p+".owner",        c.getOwnerUUID().toString());
            cfg.set(p+".ownerName",    c.getOwnerName());
            cfg.set(p+".world",        l.getWorld() != null ? l.getWorld().getName() : "world");
            cfg.set(p+".x", l.getBlockX()); cfg.set(p+".y", l.getBlockY()); cfg.set(p+".z", l.getBlockZ());
            cfg.set(p+".facing",       c.getFacing().name());
            cfg.set(p+".mode",         c.getMode().name());
            cfg.set(p+".pendingOutput", c.getPendingOutput());
            cfg.set(p+".upgradeLevel", c.getUpgradeLevel());
            if (c.getModel()      != null) cfg.set(p+".model",      c.getModel());
            if (c.getInputStack() != null) cfg.set(p+".inputStack", c.getInputStack());
            i++;
        }
        try { cfg.save(saveFile); }
        catch (IOException ex) { plugin.getLogger().severe("[Compressor] Save error: " + ex.getMessage()); }
    }

    private void load() {
        if (!saveFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(saveFile);
        if (!cfg.isConfigurationSection("comp")) return;
        for (String key : cfg.getConfigurationSection("comp").getKeys(false)) {
            String p = "comp." + key;
            try {
                UUID owner = UUID.fromString(cfg.getString(p+".owner", ""));
                String name = cfg.getString(p+".ownerName", "?");
                World world = Bukkit.getWorld(cfg.getString(p+".world","world"));
                if (world == null) continue;
                int x = cfg.getInt(p+".x"), y = cfg.getInt(p+".y"), z = cfg.getInt(p+".z");
                BlockFace face = BlockFace.NORTH;
                try { face = BlockFace.valueOf(cfg.getString(p+".facing","NORTH")); } catch (Exception ignored) {}
                Location loc = new Location(world, x, y, z);
                String id = cfg.getString(p+".id", "comp_"+key);
                GemCompressor c = new GemCompressor(id, loc, owner, name, face);

                GemCompressor.Mode mode = GemCompressor.Mode.NONE;
                try { mode = GemCompressor.Mode.valueOf(cfg.getString(p+".mode","NONE")); } catch (Exception ignored) {}
                if (mode != GemCompressor.Mode.NONE && cfg.contains(p+".model"))
                    c.setModel((ItemStack) cfg.get(p+".model"), mode);

                c.setPendingOutput(cfg.getInt(p+".pendingOutput", 0));
                c.setUpgradeLevel(cfg.getInt(p+".upgradeLevel", 0));
                if (cfg.contains(p+".inputStack")) c.setInputStack((ItemStack) cfg.get(p+".inputStack"));
                compressors.put(GemCompressor.makeId(loc), c);
            } catch (Exception ex) {
                plugin.getLogger().warning("[Compressor] Error cargando "+key+": "+ex.getMessage());
            }
        }
        plugin.getLogger().info("[Compressor] Cargados " + compressors.size() + " compresores.");
    }
}
