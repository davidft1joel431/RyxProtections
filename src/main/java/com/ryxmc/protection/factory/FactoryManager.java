package com.ryxmc.protection.factory;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.energy.EnergyManager;
import com.ryxmc.protection.util.Color;
import com.ryxmc.protection.util.Txt;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FactoryManager v2.4 — Persistencia completa de fábricas con restauración de upgrades.
 *
 * NUEVO v2.4: Al romper una fábrica, sus datos (tier, velocidad, proceso, duplicador,
 * stocks y output) se guardan en una sección "removed_factories" de factories.yml.
 * Al volver a colocar una fábrica en la misma ubicación y por el mismo dueño, se
 * restauran automáticamente todos los upgrades — evitando que el jugador gaste PP
 * en vano.
 *
 *  • save()      — guarda fábricas activas y removidas en factories.yml
 *  • load()      — restaura ambas colecciones al iniciar
 *  • placeFactory() — busca en removed_factories antes de crear una nueva
 *  • removeFactory() — mueve a removed_factories en lugar de eliminar
 */
public class FactoryManager {

    private final RyxProtection plugin;
    /** Fábricas activas colocadas en el mundo. */
    private final Map<String, EnergyFactory>     factories = new LinkedHashMap<>();
    /** Fábricas removidas (datos guardados para restauración). */
    private final Map<String, EnergyFactory>     removedFactories = new LinkedHashMap<>();
    private final Map<String, List<TextDisplay>> holos = new HashMap<>();
    private BukkitTask tickTask;
    private BukkitTask holoTask;
    private BukkitTask saveTask;
    private File saveFile;
    public static final int FACTORY_CMD = 2001;

    public FactoryManager(RyxProtection plugin) { this.plugin = plugin; }

    public void init() {
        saveFile = new File(plugin.getDataFolder(), "factories.yml");
        load();
        startTasks();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (EnergyFactory f : new ArrayList<>(factories.values())) spawnHolo(f);
        }, 2L);
        plugin.getLogger().info("[Factory] v2.4 iniciado. Activas: " + factories.size()
                + " | Removidas (guardadas): " + removedFactories.size());
    }

    public void shutdown() {
        save();
        stopTasks();
        removeAllHolos();
    }

    public void reload() {
        save();
        stopTasks();
        removeAllHolos();
        factories.clear();
        removedFactories.clear();
        load();
        for (EnergyFactory f : factories.values()) spawnHolo(f);
        startTasks();
    }

    // ─────────────────────────────────────────────────
    //  Persistencia
    // ─────────────────────────────────────────────────

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        int i = 0;
        for (EnergyFactory f : factories.values()) {
            String p = "factories." + i;
            Location loc = f.getLocation();
            cfg.set(p + ".id",         f.getFactoryId());
            cfg.set(p + ".ownerUUID",  f.getOwnerUUID().toString());
            cfg.set(p + ".ownerName",  f.getOwnerName());
            cfg.set(p + ".world",      loc.getWorld() != null ? loc.getWorld().getName() : "world");
            cfg.set(p + ".x",          loc.getBlockX());
            cfg.set(p + ".y",          loc.getBlockY());
            cfg.set(p + ".z",          loc.getBlockZ());
            cfg.set(p + ".tier",       f.getUpgradeLevel().getLevel());
            cfg.set(p + ".speedLevel", f.getSpeedLevel());
            cfg.set(p + ".procLevel",  f.getProcessLevel());
            cfg.set(p + ".dupLevel",   f.getDuplicatorLevel());
            cfg.set(p + ".copper",     f.getCopperStock());
            cfg.set(p + ".gold",       f.getGoldStock());
            cfg.set(p + ".coal",       f.getCoalStock());
            cfg.set(p + ".output",     f.getOutputReady());
            i++;
        }

        // Sección de fábricas removidas (para restauración al volver a colocar)
        int j = 0;
        for (EnergyFactory f : removedFactories.values()) {
            String p = "removed_factories." + j;
            Location loc = f.getLocation();
            cfg.set(p + ".id",         f.getFactoryId());
            cfg.set(p + ".ownerUUID",  f.getOwnerUUID().toString());
            cfg.set(p + ".ownerName",  f.getOwnerName());
            cfg.set(p + ".world",      loc.getWorld() != null ? loc.getWorld().getName() : "world");
            cfg.set(p + ".x",          loc.getBlockX());
            cfg.set(p + ".y",          loc.getBlockY());
            cfg.set(p + ".z",          loc.getBlockZ());
            cfg.set(p + ".tier",       f.getUpgradeLevel().getLevel());
            cfg.set(p + ".speedLevel", f.getSpeedLevel());
            cfg.set(p + ".procLevel",  f.getProcessLevel());
            cfg.set(p + ".dupLevel",   f.getDuplicatorLevel());
            cfg.set(p + ".copper",     f.getCopperStock());
            cfg.set(p + ".gold",       f.getGoldStock());
            cfg.set(p + ".coal",       f.getCoalStock());
            cfg.set(p + ".output",     f.getOutputReady());
            j++;
        }

        try { cfg.save(saveFile); }
        catch (IOException ex) {
            plugin.getLogger().severe("[Factory] Error guardando: " + ex.getMessage());
        }
    }

    private void load() {
        if (saveFile == null || !saveFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(saveFile);
        if (!cfg.isConfigurationSection("factories")) return;

        // Cargar fábricas activas
        for (String key : cfg.getConfigurationSection("factories").getKeys(false)) {
            String p = "factories." + key;
            try {
                loadFactoryEntry(cfg, p);
            } catch (Exception ex) {
                plugin.getLogger().warning("[Factory] Error cargando entrada " + key + ": " + ex.getMessage());
            }
        }

        // Cargar fábricas removidas (para restauración)
        if (cfg.isConfigurationSection("removed_factories")) {
            for (String key : cfg.getConfigurationSection("removed_factories").getKeys(false)) {
                String p = "removed_factories." + key;
                try {
                    EnergyFactory f = loadFactoryEntry(cfg, p);
                    if (f != null) removedFactories.put(f.getFactoryId(), f);
                } catch (Exception ex) {
                    plugin.getLogger().warning("[Factory] Error cargando removida " + key + ": " + ex.getMessage());
                }
            }
        }

        plugin.getLogger().info("[Factory] Cargadas " + factories.size() + " activas, "
                + removedFactories.size() + " removidas.");
    }

    private EnergyFactory loadFactoryEntry(YamlConfiguration cfg, String p) {
        String id        = cfg.getString(p + ".id", "factory_" + p);
        UUID   ownerUUID = UUID.fromString(cfg.getString(p + ".ownerUUID", ""));
        String ownerName = cfg.getString(p + ".ownerName", "?");
        String worldName = cfg.getString(p + ".world", "world");
        int    x = cfg.getInt(p + ".x"), y = cfg.getInt(p + ".y"), z = cfg.getInt(p + ".z");

        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        Location loc = new Location(world, x, y, z);
        EnergyFactory f = new EnergyFactory(id, loc, ownerUUID, ownerName);
        f.setUpgradeLevel(FactoryUpgradeLevel.fromLevel(cfg.getInt(p + ".tier", 1)));
        f.setSpeedLevel(cfg.getInt(p + ".speedLevel", 0));
        f.setProcessLevel(cfg.getInt(p + ".procLevel", 0));
        f.setDuplicatorLevel(cfg.getInt(p + ".dupLevel", 0));
        f.setCopperStock(cfg.getInt(p + ".copper", 0));
        f.setGoldStock(cfg.getInt(p + ".gold", 0));
        f.setCoalStock(cfg.getInt(p + ".coal", 0));
        f.setOutputReady(cfg.getInt(p + ".output", 0));

        factories.put(key(loc), f);
        return f;
    }

    // ─────────────────────────────────────────────────
    //  Colocar / Remover
    // ─────────────────────────────────────────────────

    public EnergyFactory placeFactory(Player player, Location loc) {
        String key = key(loc);
        if (factories.containsKey(key)) return null;

        String safeName = player.getName().toLowerCase().replaceAll("[^a-z0-9]", "");
        String id = "Ryxfb-" + safeName + "_"
                + loc.getBlockX() + "_" + loc.getBlockZ();

        // ── 1. Buscar si hay una fábrica removida en esta ubicación y dueño ──
        EnergyFactory restored = findRemovedFactory(player.getUniqueId(), loc);
        if (restored != null) {
            // Actualizar ID y ubicación a la nueva
            restored.setIdAndLocation(id, loc);
            factories.put(key, restored);
            removedFactories.remove(restored.getFactoryId());
            spawnHolo(restored);
            save();
            plugin.getLogger().info("[Factory] Restaurada fábrica removida: " + id
                    + " (tier " + restored.getUpgradeLevel().getLevel() + ")");
            return restored;
        }

        // ── 2. Crear nueva fábrica ──
        EnergyFactory f = new EnergyFactory(id, loc, player.getUniqueId(), player.getName());
        factories.put(key, f);
        spawnHolo(f);
        save();
        return f;
    }

    /**
     * Busca en las fábricas removidas una que coincida con el dueño y ubicación.
     */
    private EnergyFactory findRemovedFactory(UUID ownerUUID, Location loc) {
        for (EnergyFactory f : removedFactories.values()) {
            if (!f.getOwnerUUID().equals(ownerUUID)) continue;
            Location fl = f.getLocation();
            if (fl.getWorld() == null || !fl.getWorld().equals(loc.getWorld())) continue;
            if (fl.getBlockX() == loc.getBlockX()
                    && fl.getBlockY() == loc.getBlockY()
                    && fl.getBlockZ() == loc.getBlockZ()) {
                return f;
            }
        }
        return null;
    }

    public boolean removeFactory(Location loc, Player actor) {
        String key = key(loc);
        EnergyFactory f = factories.get(key);
        if (f == null) return false;
        if (!f.getOwnerUUID().equals(actor.getUniqueId()) && !actor.hasPermission("ryx.admin"))
            return false;

        removeHolo(key);

        // ── Mover a removed_factories en lugar de eliminar ──
        factories.remove(key);
        removedFactories.put(f.getFactoryId(), f);

        // Dropear materiales almacenados y output
        World world = loc.getWorld();
        if (world != null) {
            Location drop = loc.clone().add(0.5, 0.5, 0.5);
            if (f.getCopperStock() > 0) world.dropItemNaturally(drop, new ItemStack(Material.COPPER_INGOT, Math.min(f.getCopperStock(), 64)));
            if (f.getGoldStock()   > 0) world.dropItemNaturally(drop, new ItemStack(Material.GOLD_INGOT,   Math.min(f.getGoldStock(), 64)));
            if (f.getCoalStock()   > 0) world.dropItemNaturally(drop, new ItemStack(Material.COAL,         Math.min(f.getCoalStock(), 64)));
            if (f.getOutputReady() > 0) world.dropItemNaturally(drop,
                    plugin.getEnergyManager().createGem(com.ryxmc.protection.energy.EnergyManager.GemType.SMALL));
        }

        save();
        return true;
    }

    public EnergyFactory getFactoryAt(Location loc)  { return factories.get(key(loc)); }
    public boolean isFactory(Location loc)            { return factories.containsKey(key(loc)); }
    public Collection<EnergyFactory> getAllFactories() { return Collections.unmodifiableCollection(factories.values()); }
    public Collection<EnergyFactory> getAll()          { return getAllFactories(); }

    public Collection<EnergyFactory> getPlayerFactories(UUID uuid) {
        List<EnergyFactory> result = new ArrayList<>();
        for (EnergyFactory f : factories.values())
            if (f.getOwnerUUID().equals(uuid)) result.add(f);
        return result;
    }

    // ─────────────────────────────────────────────────
    //  Item
    // ─────────────────────────────────────────────────

    public ItemStack createFactoryItem() {
        ItemStack item = new ItemStack(Material.BLAST_FURNACE);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(Color.of("§6§l⚙ §e§lFábrica de Energía §8[Nv.1]"));
        meta.setCustomModelData(FACTORY_CMD);
        int maxFac = plugin.getConfig().getInt("factory.max_per_player", 3);
        meta.setLore(Arrays.asList(
                Color.of(Txt.THIN),
                Color.of("§7Produce §dGemas de Energía Pequeñas§7 automáticamente."),
                Color.of("§7Se pueden mejorar hasta §6Nv.6 §7con materiales."),
                Color.of(Txt.THIN),
                Color.of("§9Receta por ciclo:"),
                Color.of("  §bCobre §f×2  §eOro §f×1  §8Carbón §f×1"),
                Color.of(Txt.THIN),
                Color.of("§9Capacidad inicial:"),
                Color.of("  §bCu: §f16  §eAu: §f8  §7C: §f8  §dOut: §f10"),
                Color.of(Txt.THIN),
                Color.of("§7Mejoras disponibles:"),
                Color.of("  §6⚡ §fVelocidad §8— produce más rápido"),
                Color.of("  §b+ §fProducción §8— más gemas por ciclo"),
                Color.of("  §d✦ §fDuplicador §8— chance de doblar output"),
                Color.of("  §6⬆ §fTier §8— sube capacidad y velocidad base"),
                Color.of(Txt.THIN),
                Color.of("§7Máximo por jugador: §e" + maxFac + " fábricas"),
                Color.of(Txt.THIN),
                Color.of("§7Colócala en el suelo y haz §fclic derecho §7para gestionarla."),
                Color.of("§7Si hay un §5Compresor §7adyacente, transferirá"),
                Color.of("§7las gemas automáticamente."),
                Color.of(Txt.THIN),
                Color.of("§a▶ Coloca para activar")));
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isFactoryItem(ItemStack item) {
        if (item == null || item.getType() != Material.BLAST_FURNACE) return false;
        ItemMeta m = item.getItemMeta();
        return m != null && m.hasCustomModelData() && m.getCustomModelData() == FACTORY_CMD;
    }

    // ─────────────────────────────────────────────────
    //  Acciones
    // ─────────────────────────────────────────────────

    public int depositMaterial(Player p, EnergyFactory f, Material mat, int maxFromPlayer) {
        int deposited = 0;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != mat) continue;
            if (deposited >= maxFromPlayer) break;
            if (f.getAvailableSpace(mat) <= 0) break;

            int wantToTake = Math.min(item.getAmount(), maxFromPlayer - deposited);
            int actuallyAdded = f.addMaterial(mat, wantToTake);
            if (actuallyAdded > 0) {
                item.setAmount(item.getAmount() - actuallyAdded);
                deposited += actuallyAdded;
                // Animación: materiales entrando a la fábrica
                plugin.getTransportAnimator().animateMaterialsEntering(
                        f.getLocation(), mat);
            }
        }
        return deposited;
    }

    public int collectOutput(Player p, EnergyFactory f) {
        int qty = f.collectOutput();
        if (qty <= 0) return 0;
        int remaining = qty;
        while (remaining > 0) {
            int batch = Math.min(remaining, 64);
            ItemStack gem = plugin.getEnergyManager().createGem(EnergyManager.GemType.SMALL);
            gem.setAmount(batch);
            p.getInventory().addItem(gem).values()
                    .forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
            remaining -= batch;
        }
        updateHolo(f);
        return qty;
    }

    // ─────────────────────────────────────────────────
    //  Tareas
    // ─────────────────────────────────────────────────

    private void startTasks() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (EnergyFactory f : new ArrayList<>(factories.values())) {
                if (f.getState() == EnergyFactory.State.IDLE) f.tryStart();
                int ticksThisCycle = Math.min(1 << f.getSpeedLevel(), 16);
                for (int i = 0; i < ticksThisCycle; i++) if (f.tick()) break;
                if (f.hasOutput()) {
                    tryDeliverToCompressor(f);
                }
            }
        }, 10L, 10L);

        holoTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (EnergyFactory f : new ArrayList<>(factories.values())) updateHolo(f);
        }, 20L, 20L);

        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::save, 6000L, 6000L);
    }

    private void stopTasks() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        if (holoTask != null) { holoTask.cancel(); holoTask = null; }
        if (saveTask != null) { saveTask.cancel(); saveTask = null; }
    }

    // ─────────────────────────────────────────────────
    //  Hologramas
    // ─────────────────────────────────────────────────

    private void spawnHolo(EnergyFactory f) {
        removeHolo(key(f.getLocation()));
        Location base = f.getLocation().clone().add(0.5, 2.0, 0.5);
        World world = base.getWorld();
        if (world == null) return;

        List<String> lines = buildHoloLines(f);
        List<TextDisplay> displays = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            double y = (lines.size() - 1 - i) * 0.28;
            TextDisplay td = spawnTD(base.clone().add(0, y, 0), lines.get(i), world);
            if (td != null) displays.add(td);
        }
        holos.put(key(f.getLocation()), displays);
    }

    public void updateHolo(EnergyFactory f) {
        String key = key(f.getLocation());
        List<TextDisplay> existing = holos.get(key);
        List<String> newLines = buildHoloLines(f);
        if (existing == null || existing.size() != newLines.size()) { spawnHolo(f); return; }
        for (int i = 0; i < newLines.size(); i++) {
            TextDisplay td = existing.get(i);
            if (td == null || td.isDead()) { spawnHolo(f); return; }
            td.setText(Color.of(newLines.get(i)));
        }
    }

    private List<String> buildHoloLines(EnergyFactory f) {
        List<String> lines = new ArrayList<>();
        FactoryUpgradeLevel tier = f.getUpgradeLevel();

        String tierColor = switch (tier.getLevel()) {
            case 1 -> "§7"; case 2 -> "§a"; case 3 -> "§b";
            case 4 -> "§e"; case 5 -> "§6"; case 6 -> "§c"; default -> "§d";
        };
        lines.add("§6§l⚙ §f§lFábrica de Energía §8[Nv." + tier.getLevel() + "]");
        lines.add(tierColor + tier.displayName + " §8| §7Dueño: §e" + Txt.sc(f.getOwnerName()));

        String stateStr = switch (f.getState()) {
            case IDLE    -> "§7⏸ EN ESPERA";
            case RUNNING -> "§a▶ FUNCIONANDO";
            case DONE    -> "§e§l✦ ¡LISTO!";
        };
        lines.add(stateStr);
        lines.add(f.getProgressBar(12));

        int cuPct = tier.maxCopper > 0 ? (f.getCopperStock() * 5 / tier.maxCopper) : 0;
        int auPct = tier.maxGold   > 0 ? (f.getGoldStock()   * 5 / tier.maxGold)   : 0;
        int coPct = tier.maxCoal   > 0 ? (f.getCoalStock()   * 5 / tier.maxCoal)   : 0;
        String cuBar = "§b" + "█".repeat(Math.max(0,cuPct)) + "§8" + "░".repeat(Math.max(0,5-cuPct));
        String auBar = "§e" + "█".repeat(Math.max(0,auPct)) + "§8" + "░".repeat(Math.max(0,5-auPct));
        String coBar = "§7" + "█".repeat(Math.max(0,coPct)) + "§8" + "░".repeat(Math.max(0,5-coPct));
        lines.add("§bCu " + cuBar + " §8[§f" + f.getCopperStock() + "§8/§b" + tier.maxCopper + "§8]");
        lines.add("§eAu " + auBar + " §8[§f" + f.getGoldStock()   + "§8/§e" + tier.maxGold   + "§8]");
        lines.add("§7🔥 " + coBar + " §8[§f" + f.getCoalStock()   + "§8/§7" + tier.maxCoal   + "§8]");

        if (f.hasOutput()) {
            double outPct = tier.maxOutput > 0 ? (double)f.getOutputReady()/tier.maxOutput : 0;
            String outColor = outPct >= 1.0 ? "§a§l" : outPct >= 0.5 ? "§e" : "§d";
            lines.add(outColor + "✦ §f" + f.getOutputReady() + "§d/§f" + tier.maxOutput + " §dGemas listas"
                    + (outPct >= 1.0 ? " §a§l⬅ RECOLECTAR" : ""));
        }

        List<String> upgrades = new ArrayList<>();
        if (f.getSpeedLevel()      > 0) upgrades.add("§6⚡Vel.§f"+f.getSpeedLevel());
        if (f.getProcessLevel()    > 0) upgrades.add("§b+§f"+f.getOutputPerCycle()+"x");
        if (f.getDuplicatorLevel() > 0) upgrades.add("§d✦Dup.§f"+f.getDuplicatorLevel());
        if (!upgrades.isEmpty()) lines.add("§8Mejoras: " + String.join(" §8| ", upgrades));

        return lines;
    }

    private TextDisplay spawnTD(Location loc, String text, World world) {
        try {
            TextDisplay td = (TextDisplay) world.spawnEntity(loc, EntityType.TEXT_DISPLAY);
            td.setText(Color.of(text));
            td.setBillboard(Display.Billboard.CENTER);
            td.setTransformation(new Transformation(
                    new Vector3f(0,0,0), new AxisAngle4f(0,0,0,1),
                    new Vector3f(0.75f,0.75f,0.75f), new AxisAngle4f(0,0,0,1)));
            td.setBackgroundColor(org.bukkit.Color.fromARGB(0,0,0,0));
            td.setShadowed(true); td.setPersistent(false); td.setGravity(false);
            return td;
        } catch (Exception e) { return null; }
    }

    private void removeHolo(String key) {
        List<TextDisplay> list = holos.remove(key);
        if (list == null) return;
        for (TextDisplay td : list) if (td != null && !td.isDead()) td.remove();
    }

    private void removeAllHolos() {
        for (String k : new ArrayList<>(holos.keySet())) removeHolo(k);
    }

    private String key(Location loc) {
        return loc.getWorld().getName() + "_" + loc.getBlockX()
                + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
    }

    // ─────────────────────────────────────────────────
    //  Auto-entrega al compresor adyacente
    // ─────────────────────────────────────────────────

    public void tryDeliverToCompressor(EnergyFactory f) {
        Location factoryLoc = f.getLocation();
        World world = factoryLoc.getWorld();
        if (world == null) return;

        org.bukkit.block.Block factoryBlock = factoryLoc.getBlock();
        org.bukkit.block.BlockFace facing = org.bukkit.block.BlockFace.NORTH;
        try {
            if (factoryBlock.getBlockData() instanceof
                    org.bukkit.block.data.Directional dir) {
                facing = dir.getFacing();
            }
        } catch (Exception ignored) {}

        org.bukkit.block.BlockFace[] searchOrder = {
            facing,
            org.bukkit.block.BlockFace.NORTH,
            org.bukkit.block.BlockFace.SOUTH,
            org.bukkit.block.BlockFace.EAST,
            org.bukkit.block.BlockFace.WEST
        };

        for (org.bukkit.block.BlockFace face : searchOrder) {
            if (face == null) continue;
            Location adj = factoryLoc.clone().add(
                    face.getModX(), face.getModY(), face.getModZ());

            GemCompressor comp = plugin.getCompressorManager().getAt(adj);
            if (comp == null) continue;

            int outputReady = f.getOutputReady();
            if (outputReady <= 0) break;

            int compInput   = comp.getInputStack() == null ? 0 : comp.getInputStack().getAmount();
            int compSpace   = 64 - compInput;
            if (compSpace <= 0) break;

            int toTransfer = Math.min(outputReady, compSpace);

            f.setOutputReady(outputReady - toTransfer);

            if (comp.getInputStack() == null) {
                ItemStack gems = plugin.getEnergyManager()
                        .createGem(com.ryxmc.protection.energy.EnergyManager.GemType.SMALL);
                gems.setAmount(toTransfer);
                comp.setInputStack(gems);
            } else {
                comp.getInputStack().setAmount(compInput + toTransfer);
            }

            comp.tryStart();
            plugin.getCompressorManager().save();
            save();

            // Animación de transporte: fábrica → compresor
            plugin.getTransportAnimator().animateFactoryToCompressor(factoryLoc, adj, toTransfer);
            break;
        }
    }

    private void spawnTransferParticles(Location from, Location to, World world) {
        try {
            double dx = (to.getX() + 0.5 - (from.getX() + 0.5)) / 5.0;
            double dy = (to.getY() + 0.5 - (from.getY() + 0.5)) / 5.0;
            double dz = (to.getZ() + 0.5 - (from.getZ() + 0.5)) / 5.0;
            org.bukkit.Particle.DustOptions dust =
                    new org.bukkit.Particle.DustOptions(
                            org.bukkit.Color.fromRGB(221, 160, 221), 0.8f);
            for (int i = 0; i <= 5; i++) {
                double x = from.getX() + 0.5 + dx * i;
                double y = from.getY() + 0.5 + dy * i;
                double z = from.getZ() + 0.5 + dz * i;
                world.spawnParticle(org.bukkit.Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust);
            }
        } catch (Exception ignored) {}
    }
}
