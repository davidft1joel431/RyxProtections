package com.ryxmc.protection.factory;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.energy.EnergyManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * IndustrialFactory v3.0 — Fábrica Industrial (estructura 3×1×1)
 *
 * [FIX CRÍTICO] Recetas de gemas rotas: GEM_MEDIUM y GEM_LARGE contaban
 * y producían Material.AMETHYST_SHARD genérico sin distinguir tamaño
 * (pequeña/mediana/grande se diferencian por meta vía EnergyManager,
 * no por Material). Ahora usa detectGem()/createGem() — misma API del
 * compresor — con recetas reales:
 *   16 Gemas Pequeñas → 1 Gema Mediana
 *   4  Gemas Medianas → 1 Gema Grande (ruta preferida)
 *   64 Gemas Pequeñas → 1 Gema Grande (ruta alternativa)
 */
public class IndustrialFactory {

    public static final Material HEART_MATERIAL   = Material.BLAST_FURNACE;
    public static final Material HOPPER_MATERIAL  = Material.HOPPER;
    public static final Material DROPPER_MATERIAL = Material.DROPPER;
    public static final int      HEART_CMD        = 3003;

    public static final int SMALL_TO_MEDIUM  = 16;
    public static final int MEDIUM_TO_LARGE  = 4;
    public static final int SMALL_TO_LARGE   = 64;

    public enum ProductType {
        GEM_MEDIUM("§dGema Mediana",   Material.AMETHYST_SHARD, SMALL_TO_MEDIUM, 1, 200),
        GEM_LARGE ("§5Gema Grande",    Material.AMETHYST_SHARD, SMALL_TO_LARGE,  1, 400),
        PETROLEUM ("§8Petróleo",       Material.BLACK_DYE,      8,               1, 300),
        COAL_BLOCK("§8Bloque Carbón",  Material.COAL_BLOCK,     9,               1, 150);

        public final String  displayName;
        public final Material outputMaterial;
        public final int      inputRequired;
        public final int      outputAmount;
        public final int      ticksToProcess;

        ProductType(String n, Material mat, int req, int out, int ticks) {
            displayName    = n; outputMaterial = mat;
            inputRequired  = req; outputAmount = out; ticksToProcess = ticks;
        }

        public boolean isGemProduct() { return this == GEM_MEDIUM || this == GEM_LARGE; }
    }

    public enum State { IDLE, RUNNING, DONE, MISSING_HOPPER, MISSING_DROPPER }

    public enum Tier {
        BASIC   ("§7Básica",           1.0,  1),
        ADVANCED("§aAvanzada",         0.75, 2),
        ELITE   ("§bÉlite",            0.5,  3),
        MASTER  ("§6§lMaestra",        0.3,  4);

        public final String displayName;
        public final double speedMultiplier;
        public final int    level;

        Tier(String displayName, double speedMultiplier, int level) {
            this.displayName     = displayName;
            this.speedMultiplier = speedMultiplier;
            this.level           = level;
        }

        public boolean hasNext() { return ordinal() < values().length - 1; }
        public Tier    next()    { return hasNext() ? values()[ordinal() + 1] : null; }

        public ItemStack[] getUpgradeCost() {
            return switch (this) {
                case ADVANCED -> new ItemStack[]{
                        new ItemStack(Material.COPPER_INGOT, 16),
                        new ItemStack(Material.REDSTONE, 32)
                };
                case ELITE -> new ItemStack[]{
                        new ItemStack(Material.GOLD_INGOT, 8),
                        new ItemStack(Material.DIAMOND, 4)
                };
                case MASTER -> new ItemStack[]{
                        new ItemStack(Material.NETHERITE_INGOT, 2),
                        new ItemStack(Material.EMERALD_BLOCK, 3)
                };
                case BASIC -> new ItemStack[0];
            };
        }

        public static Tier fromLevel(int level) {
            for (Tier t : values()) if (t.level == level) return t;
            return BASIC;
        }
    }

    private final String    factoryId;
    private final Location  heartLoc;
    private final Location  hopperLoc;
    private final Location  dropperLoc;
    private final BlockFace facing;
    private final UUID      ownerUUID;
    private final String    ownerName;

    private State       state         = State.IDLE;
    private ProductType currentProduct = ProductType.GEM_MEDIUM;
    private int         progressTicks = 0;
    private int         outputReady   = 0;
    private Tier        tier          = Tier.BASIC;
    private boolean     largeFromMedium = false;

    public IndustrialFactory(String id, Location heart, BlockFace facing,
                             UUID owner, String ownerName) {
        this.factoryId  = id;
        this.heartLoc   = heart;
        this.facing     = facing;
        this.ownerUUID  = owner;
        this.ownerName  = ownerName;
        Block heartBlock = heart.getBlock();
        this.hopperLoc  = heartBlock.getRelative(facing.getOppositeFace()).getLocation();
        this.dropperLoc = heartBlock.getRelative(facing).getLocation();
    }

    public boolean tick(RyxProtection plugin) {
        if (!isStructureIntact()) {
            state = heartLoc.getBlock().getRelative(facing.getOppositeFace()).getType() != HOPPER_MATERIAL
                    ? State.MISSING_HOPPER : State.MISSING_DROPPER;
            return false;
        }

        if (state == State.IDLE || state == State.MISSING_HOPPER || state == State.MISSING_DROPPER) {
            if (tryStart(plugin)) state = State.RUNNING;
            else return false;
        }

        if (state == State.RUNNING) {
            progressTicks++;
            if (progressTicks >= getEffectiveTicks()) {
                progressTicks = 0;
                outputReady++;
                if (!tryDeposit(plugin)) {
                    state = State.DONE;
                } else {
                    state = canContinue(plugin) ? State.RUNNING : State.IDLE;
                }
                return true;
            }
        }
        return false;
    }

    public int getEffectiveTicks() {
        return Math.max(1, (int) Math.round(currentProduct.ticksToProcess * tier.speedMultiplier));
    }

    private boolean tryStart(RyxProtection plugin) {
        Inventory hopperInv = getHopperInv();
        if (hopperInv == null) return false;
        if (currentProduct.isGemProduct()) return tryStartGem(plugin, hopperInv);
        return countMaterial(hopperInv, getMaterialForProduct()) >= currentProduct.inputRequired;
    }

    private boolean tryStartGem(RyxProtection plugin, Inventory hopperInv) {
        if (currentProduct == ProductType.GEM_MEDIUM) {
            return countGems(plugin, hopperInv, EnergyManager.GemType.SMALL) >= SMALL_TO_MEDIUM;
        }
        int mediums = countGems(plugin, hopperInv, EnergyManager.GemType.MEDIUM);
        if (mediums >= MEDIUM_TO_LARGE) { largeFromMedium = true; return true; }
        int smalls = countGems(plugin, hopperInv, EnergyManager.GemType.SMALL);
        if (smalls >= SMALL_TO_LARGE) { largeFromMedium = false; return true; }
        return false;
    }

    private boolean canContinue(RyxProtection plugin) {
        Inventory hopperInv = getHopperInv();
        if (hopperInv == null) return false;
        if (currentProduct.isGemProduct()) return tryStartGem(plugin, hopperInv);
        return countMaterial(hopperInv, getMaterialForProduct()) >= currentProduct.inputRequired;
    }

    private boolean tryDeposit(RyxProtection plugin) {
        var dropper = getDropper();
        if (dropper == null) return false;

        ItemStack product = buildOutputItem(plugin);
        var leftover = dropper.getInventory().addItem(product);
        if (!leftover.isEmpty()) {
            org.bukkit.World w = dropperLoc.getWorld();
            if (w != null) w.dropItemNaturally(
                    dropperLoc.clone().add(0.5, 1.0, 0.5), product);
        }
        consumeInput(plugin);
        return true;
    }

    private ItemStack buildOutputItem(RyxProtection plugin) {
        if (currentProduct == ProductType.GEM_MEDIUM)
            return plugin.getEnergyManager().createGem(EnergyManager.GemType.MEDIUM);
        if (currentProduct == ProductType.GEM_LARGE)
            return plugin.getEnergyManager().createGem(EnergyManager.GemType.LARGE);
        return new ItemStack(currentProduct.outputMaterial, currentProduct.outputAmount);
    }

    private void consumeInput(RyxProtection plugin) {
        Inventory hopperInv = getHopperInv();
        if (hopperInv == null) return;

        if (currentProduct == ProductType.GEM_MEDIUM) {
            consumeGems(plugin, hopperInv, EnergyManager.GemType.SMALL, SMALL_TO_MEDIUM);
            return;
        }
        if (currentProduct == ProductType.GEM_LARGE) {
            if (largeFromMedium) consumeGems(plugin, hopperInv, EnergyManager.GemType.MEDIUM, MEDIUM_TO_LARGE);
            else                 consumeGems(plugin, hopperInv, EnergyManager.GemType.SMALL, SMALL_TO_LARGE);
            return;
        }
        consumeGeneric(hopperInv, getMaterialForProduct(), currentProduct.inputRequired);
    }

    public boolean isStructureIntact() {
        return heartLoc.getBlock().getType()   == HEART_MATERIAL
                && hopperLoc.getBlock().getType()  == HOPPER_MATERIAL
                && dropperLoc.getBlock().getType() == DROPPER_MATERIAL;
    }

    public static String validatePlacement(Block heartBlock, BlockFace dir) {
        Block hopperPos  = heartBlock.getRelative(dir.getOppositeFace());
        Block dropperPos = heartBlock.getRelative(dir);
        if (!heartBlock.getType().isAir())
            return "El bloque central §c" + heartBlock.getType() + " §cno es aire.";
        if (!hopperPos.getType().isAir())
            return "La posición de la §eTolva §c(detrás) no está libre.";
        if (!dropperPos.getType().isAir())
            return "La posición del §eDropper §c(adelante) no está libre.";
        return null;
    }

    private Material getMaterialForProduct() {
        return switch (currentProduct) {
            case GEM_MEDIUM, GEM_LARGE -> Material.AMETHYST_SHARD;
            case PETROLEUM, COAL_BLOCK -> Material.COAL;
        };
    }

    private int countMaterial(Inventory inv, Material mat) {
        int total = 0;
        for (ItemStack s : inv.getContents())
            if (s != null && s.getType() == mat) total += s.getAmount();
        return total;
    }

    private void consumeGeneric(Inventory inv, Material mat, int amount) {
        int remaining = amount;
        for (ItemStack slot : inv.getContents()) {
            if (slot == null || slot.getType() != mat) continue;
            int take = Math.min(remaining, slot.getAmount());
            slot.setAmount(slot.getAmount() - take);
            if (slot.getAmount() <= 0) inv.remove(slot);
            remaining -= take;
            if (remaining <= 0) break;
        }
    }

    private int countGems(RyxProtection plugin, Inventory inv, EnergyManager.GemType type) {
        int total = 0;
        for (ItemStack s : inv.getContents()) {
            if (s == null) continue;
            if (plugin.getEnergyManager().detectGem(s) == type) total += s.getAmount();
        }
        return total;
    }

    private void consumeGems(RyxProtection plugin, Inventory inv, EnergyManager.GemType type, int amount) {
        int remaining = amount;
        for (ItemStack slot : inv.getContents()) {
            if (slot == null) continue;
            if (plugin.getEnergyManager().detectGem(slot) != type) continue;
            int take = Math.min(remaining, slot.getAmount());
            slot.setAmount(slot.getAmount() - take);
            if (slot.getAmount() <= 0) inv.remove(slot);
            remaining -= take;
            if (remaining <= 0) break;
        }
    }

    public int getCurrentInputStock(RyxProtection plugin) {
        Inventory hopperInv = getHopperInv();
        if (hopperInv == null) return 0;
        if (currentProduct == ProductType.GEM_MEDIUM)
            return countGems(plugin, hopperInv, EnergyManager.GemType.SMALL);
        if (currentProduct == ProductType.GEM_LARGE) {
            int mediums = countGems(plugin, hopperInv, EnergyManager.GemType.MEDIUM);
            int smalls  = countGems(plugin, hopperInv, EnergyManager.GemType.SMALL);
            return mediums >= MEDIUM_TO_LARGE ? mediums : smalls;
        }
        return countMaterial(hopperInv, getMaterialForProduct());
    }

    public String describeRecipe() {
        return switch (currentProduct) {
            case GEM_MEDIUM -> SMALL_TO_MEDIUM + "x §7Gema Pequeña §f→ §d1x Gema Mediana";
            case GEM_LARGE  -> MEDIUM_TO_LARGE + "x §dGema Mediana §7o §f" + SMALL_TO_LARGE
                    + "x §7Gema Pequeña §f→ §51x Gema Grande";
            case PETROLEUM  -> "8x §8Carbón §f→ §81x Petróleo";
            case COAL_BLOCK -> "9x §8Carbón §f→ §81x Bloque de Carbón";
        };
    }

    private org.bukkit.block.Hopper getHopper() {
        if (hopperLoc.getBlock().getState() instanceof org.bukkit.block.Hopper h) return h;
        return null;
    }

    private Inventory getHopperInv() {
        var h = getHopper();
        return h != null ? h.getInventory() : null;
    }

    private org.bukkit.block.Dropper getDropper() {
        if (dropperLoc.getBlock().getState() instanceof org.bukkit.block.Dropper d) return d;
        return null;
    }

    public String getProgressBar(int len) {
        if (state == State.IDLE) return "§7" + "░".repeat(len) + " §7EN ESPERA";
        if (state == State.DONE) return "§a" + "█".repeat(len) + " §a¡LISTO!";
        if (state == State.MISSING_HOPPER) return "§c" + "░".repeat(len) + " §cFALTA TOLVA";
        if (state == State.MISSING_DROPPER) return "§c" + "░".repeat(len) + " §cFALTA DROPPER";
        double ratio  = (double) progressTicks / getEffectiveTicks();
        int    filled = (int)(ratio * len);
        String color  = ratio < 0.33 ? "§c" : ratio < 0.66 ? "§e" : "§a";
        return color + "█".repeat(Math.max(0, filled))
                + "§8░".repeat(Math.max(0, len - filled))
                + " §f" + (int)(ratio * 100) + "%";
    }

    public static ItemStack buildItem() {
        ItemStack item = new ItemStack(HEART_MATERIAL);
        var meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(com.ryxmc.protection.util.Color.of(
                "§6§l⚙ §e§lꜰáʙʀɪᴄᴀ ɪɴᴅᴜꜱᴛʀɪᴀʟ §8[Corazón]"));
        meta.setCustomModelData(HEART_CMD);
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        meta.setLore(List.of(
                com.ryxmc.protection.util.Color.of(com.ryxmc.protection.util.Txt.THIN),
                com.ryxmc.protection.util.Color.of("§7Fábrica multi-bloque §8(3×1×1)§7."),
                com.ryxmc.protection.util.Color.of(com.ryxmc.protection.util.Txt.THIN),
                com.ryxmc.protection.util.Color.of("§9Estructura:"),
                com.ryxmc.protection.util.Color.of("  §e[Tolva] §7← §f[Corazón] §7→ §e[Dropper]"),
                com.ryxmc.protection.util.Color.of("  §7Romper CUALQUIERA de los 3 bloques"),
                com.ryxmc.protection.util.Color.of("  §7destruye toda la estructura."),
                com.ryxmc.protection.util.Color.of(com.ryxmc.protection.util.Txt.THIN),
                com.ryxmc.protection.util.Color.of("§9Recetas de gemas:"),
                com.ryxmc.protection.util.Color.of("  §716x Pequeña §f→ §d1x Mediana"),
                com.ryxmc.protection.util.Color.of("  §d4x Mediana §7o §764x Pequeña §f→ §51x Grande"),
                com.ryxmc.protection.util.Color.of(com.ryxmc.protection.util.Txt.THIN),
                com.ryxmc.protection.util.Color.of("§9Otros productos:"),
                com.ryxmc.protection.util.Color.of("  §8Petróleo §7(tinte negro enc.)"),
                com.ryxmc.protection.util.Color.of("  §8Bloques de carbón"),
                com.ryxmc.protection.util.Color.of(com.ryxmc.protection.util.Txt.THIN),
                com.ryxmc.protection.util.Color.of("§7Haz §fclic derecho §7sobre el suelo para"),
                com.ryxmc.protection.util.Color.of("§7ver las partículas de colocación."),
                com.ryxmc.protection.util.Color.of(com.ryxmc.protection.util.Txt.THIN),
                com.ryxmc.protection.util.Color.of("§6▶ Clic derecho para colocar")
        ));
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isHeartItem(ItemStack item) {
        if (item == null || item.getType() != HEART_MATERIAL) return false;
        var meta = item.getItemMeta();
        return meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == HEART_CMD;
    }

    public String      getFactoryId()      { return factoryId; }
    public Location    getHeartLoc()       { return heartLoc.clone(); }
    public Location    getHopperLoc()      { return hopperLoc.clone(); }
    public Location    getDropperLoc()     { return dropperLoc.clone(); }
    public BlockFace   getFacing()         { return facing; }
    public UUID        getOwnerUUID()      { return ownerUUID; }
    public String      getOwnerName()      { return ownerName; }
    public State       getState()          { return state; }
    public ProductType getCurrentProduct() { return currentProduct; }
    public int         getProgressTicks()  { return progressTicks; }
    public int         getOutputReady()    { return outputReady; }
    public Tier        getTier()           { return tier; }

    public void setState(State s)              { this.state = s; }
    public void setCurrentProduct(ProductType p){ this.currentProduct = p; progressTicks = 0; }
    public void setProgressTicks(int v)        { this.progressTicks = v; }
    public void setOutputReady(int v)          { this.outputReady = v; }
    public void setTier(Tier t)                { this.tier = t; }
}
