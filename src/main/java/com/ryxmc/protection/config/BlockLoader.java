package com.ryxmc.protection.config;

import com.ryxmc.protection.RyxProtection;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;

/**
 * BlockLoader — Carga bloques de protección desde blocks/*.yml
 *
 * @deprecated Reemplazado por {@link ProtectionBlock} en v2.3.
 *   El sistema blocks/*.yml fue eliminado. Esta clase ya no se instancia
 *   en {@link com.ryxmc.protection.RyxProtection}.
 *   {@code getBlockLoader()} retorna {@code null} — no llamar.
 *
 *   Migración: sustituir {@code BlockLoader} / {@code BlockCfg} por
 *   {@code ProtectionBlock.buildItem()}, {@code ProtectionBlock.isProtectionBlock()},
 *   {@code ProtectionBlock.getXRadius()}, etc.
 */
@Deprecated(since = "2.3", forRemoval = true)
public class BlockLoader {

    private final RyxProtection plugin;

    /** Orden de inserción preservado para el GUI de tienda */
    private final Map<String, BlockCfg> byAlias = new LinkedHashMap<>();

    public BlockLoader(RyxProtection plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────────────────────────────────
    //  Cargar / Recargar
    // ─────────────────────────────────────────────────

    public void load() {
        byAlias.clear();

        // Copiar archivos por defecto si no existen
        saveDefaults();

        File blocksDir = new File(plugin.getDataFolder(), "blocks");
        if (!blocksDir.exists() || !blocksDir.isDirectory()) {
            plugin.getLogger().warning("[BlockLoader] Carpeta blocks/ no encontrada.");
            return;
        }

        File[] files = blocksDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().warning("[BlockLoader] No hay archivos de bloque en blocks/.");
            return;
        }

        // Ordenar por nombre de archivo para mantener el orden
        Arrays.sort(files, Comparator.comparing(File::getName));

        for (File f : files) {
            try {
                BlockCfg cfg = loadFile(f);
                if (cfg != null) byAlias.put(cfg.getAlias(), cfg);
            } catch (Exception e) {
                plugin.getLogger().warning("[BlockLoader] Error cargando " + f.getName() + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("[BlockLoader] Cargados " + byAlias.size() + " bloques.");
    }

    private BlockCfg loadFile(File file) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        String alias  = cfg.getString("alias", file.getName().replace(".yml", ""));
        String disp   = cfg.getString("displayName", "§7Bloque");
        String desc   = cfg.getString("description", "");
        double price  = cfg.getDouble("price", 0);
        int    xr     = cfg.getInt("xRadius", 10);
        int    zr     = cfg.getInt("zRadius", 10);
        String upgTo  = cfg.getString("upgradesTo", null);
        double bDtr   = cfg.getDouble("baseDtr", 5.0);
        int    cmd    = cfg.getInt("customModelData", 3000 + byAlias.size() + 1);

        String matName = cfg.getString("material", "STONE");
        Material mat;
        try {
            mat = Material.valueOf(matName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[BlockLoader] Material inválido en " + file.getName() + ": " + matName);
            mat = Material.STONE;
        }

        return new BlockCfg(alias, disp, desc, mat, cmd, price, xr, zr, upgTo, bDtr);
    }

    // ─────────────────────────────────────────────────
    //  Archivos por defecto
    // ─────────────────────────────────────────────────

    private void saveDefaults() {
        String[] defaults = {"block1.yml", "block2.yml", "block3.yml", "block4.yml", "block5.yml"};
        for (String name : defaults) {
            File f = new File(plugin.getDataFolder(), "blocks/" + name);
            if (!f.exists() && plugin.getResource("blocks/" + name) != null) {
                plugin.saveResource("blocks/" + name, false);
            }
        }
    }

    // ─────────────────────────────────────────────────
    //  Consultas
    // ─────────────────────────────────────────────────

    public Collection<BlockCfg> getAll()              { return byAlias.values(); }
    public BlockCfg getByAlias(String alias)           { return alias == null ? null : byAlias.get(alias.toLowerCase()); }

    public BlockCfg getByItem(ItemStack item) {
        if (item == null) return null;
        for (BlockCfg bc : byAlias.values()) if (bc.matches(item)) return bc;
        return null;
    }

    public boolean isProtectionBlock(ItemStack item) {
        return getByItem(item) != null;
    }
}
