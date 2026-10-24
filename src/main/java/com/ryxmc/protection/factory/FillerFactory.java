package com.ryxmc.protection.factory;

import com.ryxmc.protection.RyxProtection;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * FillerFactory v1.5
 *
 * ── EL BUG REAL (confirmado con captura del jugador) ─────────────────────
 *
 *  ANTES: getChestsBehind() solo comprobaba el bloque en la posición
 *  EXACTA opuesta al facing del dispensador (+ arriba/abajo de ESA
 *  posición única). Un cofre conectado por tubos desde CUALQUIER OTRA
 *  cara del rellenador —o alcanzable más lejos a través de la red—
 *  simplemente NUNCA se encontraba. Por eso "no detecta los tubos que
 *  tiene detrás": el sistema ni siquiera intentaba mirar ahí.
 *
 *  AHORA: se usa la MISMA búsqueda BFS por red de tubos
 *  (TubeManager.findDestinations) tanto para encontrar ORÍGENES
 *  (cofres, barriles, shulkers) como DESTINOS (fábrica, compresor,
 *  tolva industrial). Esa búsqueda ya revisaba las 6 caras del bloque
 *  en cada paso — es completamente direccional-agnóstica. Al usarla
 *  también para el origen, un contenedor conectado por tubos desde
 *  CUALQUIER lado del rellenador (o de cualquier tubo de la red) se
 *  detecta correctamente. Esto es lo que hace que el tubo reemplace
 *  de verdad a las tolvas: transfiere "todo, en cualquier dirección".
 *
 *  CONSECUENCIA: esto también arregla el compresor — antes nunca
 *  recibía nada porque el origen (cofre) nunca se encontraba, así
 *  que no había materiales que enviarle en primer lugar.
 */
public class FillerFactory {

    public static final Material FILLER_MATERIAL  = Material.DISPENSER;
    public static final int      FILLER_CMD       = 3002;
    public static final int      FILL_INTERVAL    = 60;

    public static final List<Material> ACCEPTED_MATERIALS = List.of(
            Material.COPPER_INGOT,
            Material.GOLD_INGOT,
            Material.COAL,
            Material.CHARCOAL,
            Material.AMETHYST_SHARD   // gemas (pequeña/mediana según meta/CMD)
    );

    /**
     * [FIX] Contenedores válidos como ORIGEN de materiales — detectados
     * en CUALQUIER dirección: adyacentes directos al rellenador O
     * conectados en cualquier punto de la red de tubos.
     */
    private static final Set<Material> SOURCE_TYPES = Set.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
            Material.SHULKER_BOX,
            Material.WHITE_SHULKER_BOX, Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX,
            Material.YELLOW_SHULKER_BOX, Material.LIME_SHULKER_BOX,
            Material.PINK_SHULKER_BOX, Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX, Material.CYAN_SHULKER_BOX,
            Material.PURPLE_SHULKER_BOX, Material.BLUE_SHULKER_BOX,
            Material.BROWN_SHULKER_BOX, Material.GREEN_SHULKER_BOX,
            Material.RED_SHULKER_BOX, Material.BLACK_SHULKER_BOX,
            Material.HOPPER
    );

    /** Destinos válidos: fábrica, compresor y tolva de fábrica industrial */
    private static final Set<Material> DEST_TYPES =
            Set.of(Material.BLAST_FURNACE, Material.CRAFTER, Material.HOPPER);

    public enum FillerState { IDLE, RUNNING, NO_SOURCE, NO_TARGET }

    private final String   fillerId;
    private final Location location;
    private final UUID     ownerUUID;
    private final String   ownerName;

    private FillerState state        = FillerState.IDLE;
    private int         ticksSinceOp = 0;
    private int         itemsFilled  = 0;

    public FillerFactory(String id, Location loc, UUID owner, String ownerName) {
        this.fillerId  = id;
        this.location  = loc;
        this.ownerUUID = owner;
        this.ownerName = ownerName;
    }

    public boolean tick(RyxProtection plugin) {
        ticksSinceOp++;
        if (ticksSinceOp < FILL_INTERVAL) return false;
        ticksSinceOp = 0;

        Block block = location.getBlock();
        if (block.getType() != FILLER_MATERIAL) { state = FillerState.IDLE; return false; }

        // [FIX] Origen = cualquier contenedor adyacente O conectado por tubos,
        // en CUALQUIER dirección. Ya no depende de estar "detrás" del rellenador.
        List<Block> sources = plugin.getTubeManager().findDestinations(block, SOURCE_TYPES);
        if (sources.isEmpty()) { state = FillerState.NO_SOURCE; return false; }

        List<Block> targets = plugin.getTubeManager().findDestinations(block, DEST_TYPES);
        if (targets.isEmpty()) { state = FillerState.NO_TARGET; return false; }

        boolean transferred = false;
        for (Block target : targets) {
            for (Block sourceBlock : sources) {
                if (!(sourceBlock.getState() instanceof Container container)) continue;
                transferred |= transferMaterials(plugin, container, target);
            }
        }

        state = transferred ? FillerState.RUNNING : FillerState.IDLE;
        return transferred;
    }

    private boolean transferMaterials(RyxProtection plugin, Container source, Block target) {
        boolean any = false;
        var sourceInv = source.getInventory();

        for (ItemStack item : sourceInv.getContents()) {
            if (item == null || !ACCEPTED_MATERIALS.contains(item.getType())) continue;

            int requested = item.getAmount();
            int actualSent = TransferBridge.deposit(plugin, target, item, requested);

            if (actualSent > 0) {
                item.setAmount(item.getAmount() - actualSent);
                if (item.getAmount() <= 0) sourceInv.remove(item);
                itemsFilled += actualSent;
                any = true;
            }
        }
        return any;
    }

    /** Texto legible del estado actual — usado en /ps y mensajes de info. */
    public String getStateDisplay() {
        return switch (state) {
            case IDLE      -> "§7En espera";
            case RUNNING   -> "§a▶ Transfiriendo";
            case NO_SOURCE -> "§c✘ Sin origen conectado §8(cofre/barril/shulker)";
            case NO_TARGET -> "§c✘ Sin destino conectado §8(fábrica/compresor/tolva)";
        };
    }

    public static ItemStack buildItem() {
        ItemStack item = new ItemStack(FILLER_MATERIAL);
        var meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(com.ryxmc.protection.util.Color.of("§6§l⚡ §e§lʀᴇʟʟᴇɴᴀᴅᴏʀ"));
        meta.setCustomModelData(FILLER_CMD);
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        meta.setLore(List.of(
                com.ryxmc.protection.util.Color.of(com.ryxmc.protection.util.Txt.THIN),
                com.ryxmc.protection.util.Color.of("§7Auto-rellena fábricas, compresores"),
                com.ryxmc.protection.util.Color.of("§7y fábricas industriales conectados"),
                com.ryxmc.protection.util.Color.of("§7por §bTubos de conductividad§7."),
                com.ryxmc.protection.util.Color.of(com.ryxmc.protection.util.Txt.THIN),
                com.ryxmc.protection.util.Color.of("§9Instalación:"),
                com.ryxmc.protection.util.Color.of("  §71. Coloca cofres/barriles/shulkers"),
                com.ryxmc.protection.util.Color.of("     §7en §fcualquier lado §7del rellenador"),
                com.ryxmc.protection.util.Color.of("     §7o conéctalos con §bTubos§7."),
                com.ryxmc.protection.util.Color.of("  §72. Conecta con §bTubos §7hacia las fábricas."),
                com.ryxmc.protection.util.Color.of("  §73. El rellenador opera automáticamente."),
                com.ryxmc.protection.util.Color.of(com.ryxmc.protection.util.Txt.THIN),
                com.ryxmc.protection.util.Color.of("§9Materiales aceptados:"),
                com.ryxmc.protection.util.Color.of("  §bCobre §7| §eOro §7| §8Carbón §7| §dGemas"),
                com.ryxmc.protection.util.Color.of(com.ryxmc.protection.util.Txt.THIN),
                com.ryxmc.protection.util.Color.of("§6▶ Coloca para activar")
        ));
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isFillerItem(ItemStack item) {
        if (item == null || item.getType() != FILLER_MATERIAL) return false;
        var meta = item.getItemMeta();
        return meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == FILLER_CMD;
    }

    public String      getFillerId()    { return fillerId; }
    public Location    getLocation()    { return location.clone(); }
    public UUID        getOwnerUUID()   { return ownerUUID; }
    public String      getOwnerName()   { return ownerName; }
    public FillerState getState()       { return state; }
    public int         getItemsFilled() { return itemsFilled; }
    public void        setState(FillerState s)  { this.state = s; }
    public void        setTicksSinceOp(int v)   { this.ticksSinceOp = v; }
    public void        setItemsFilled(int v)    { this.itemsFilled = v; }
}
