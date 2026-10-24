package com.ryxmc.protection.factory;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.energy.EnergyManager;
import com.ryxmc.protection.gui.GuiManager;
import com.ryxmc.protection.gui.modern.GuiStyle;
import com.ryxmc.protection.gui.modern.ItemBuilder;
import com.ryxmc.protection.util.Color;
import com.ryxmc.protection.util.Txt;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GemCompressorGui v4.0 — GUI moderna del Compresor de Gemas con animaciones únicas.
 *
 * Animaciones v4.0:
 *  • Partículas púrpura girando en SLOT_ANIM durante compresión
 *  • Partículas de gema viajando de INPUT → ANIM → OUTPUT
 *  • Brillo intenso en OUTPUT cuando hay gemas listas
 *  • Partículas de transporte al entregar a cofre/siguiente compresor
 *
 * Layout (54 slots):
 *  Fila 0: borde púrpura oscuro
 *  Fila 1: bordes púrpura, centro negro
 *  Fila 2: [INPUT] → [ANIM giratorio] → [OUTPUT brillante]
 *  Fila 3: [MODEL] en centro
 *  Fila 4: bordes
 *  Fila 5: [UPGRADE] [INFO] ... [CERRAR]
 */
public class GemCompressorGui {

    private static final int SLOT_INPUT  = 20;
    private static final int SLOT_ANIM   = 22;
    private static final int SLOT_OUTPUT = 24;
    private static final int SLOT_MODEL  = 31;
    private static final int SLOT_UPGRADE= 42;
    private static final int SLOT_INFO   = 47;
    private static final int SLOT_CLOSE  = 53;

    private static final String TITLE = "§5§l⚗ §d§lCompresor de Gemas";

    private final RyxProtection plugin;
    private final Map<UUID, GemCompressor> openMap   = new HashMap<>();
    private final Map<UUID, BukkitTask>    animTasks = new HashMap<>();
    /** Partículas de transporte activas por jugador */
    private final Map<UUID, BukkitTask>    transportTasks = new ConcurrentHashMap<>();

    // Materiales que rotan en SLOT_ANIM durante el procesado
    private static final Material[] ANIM_MATS = {
        Material.AMETHYST_SHARD,
        Material.BLAZE_ROD,
        Material.BLAZE_POWDER,
        Material.AMETHYST_SHARD,
        Material.GLOWSTONE_DUST,
        Material.BLAZE_POWDER
    };

    public GemCompressorGui(RyxProtection plugin) { this.plugin = plugin; }

    // ─────────────────────────────────────────────────
    //  Abrir / Cerrar
    // ─────────────────────────────────────────────────

    public void open(Player p, GemCompressor comp) {
        stopAnim(p.getUniqueId());
        stopTransport(p.getUniqueId());

        GuiManager.RyxHolder holder = new GuiManager.RyxHolder(
                GuiManager.GuiType.FACTORY_MAIN, p.getUniqueId(), comp.getId());

        Inventory inv = Bukkit.createInventory(holder, 54, Color.of(TITLE
                + " §8| §7" + Txt.sc(comp.getOwnerName())));
        holder.setInventory(inv);

        buildStatic(inv);
        populateDynamic(inv, comp);

        plugin.getGuiManager().register(p.getUniqueId(), holder);
        openMap.put(p.getUniqueId(), comp);
        p.openInventory(inv);

        // Tarea de animación principal — cada 4 ticks
        final int[] frame = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline()) { stopAnim(p.getUniqueId()); return; }
            Inventory top = p.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof GuiManager.RyxHolder rxh)
                    || !comp.getId().equals(rxh.getRegionId())) {
                stopAnim(p.getUniqueId()); return;
            }
            frame[0]++;
            animateState(top, comp, frame[0]);
            if (frame[0] % 25 == 0) populateDynamic(top, comp); // refrescar cada ~5s
        }, 2L, 4L);
        animTasks.put(p.getUniqueId(), task);

        // Animación de partículas de transporte si hay output pendiente
        if (comp.hasPendingOutput()) {
            startTransportParticles(p, comp);
        }
    }

    public void close(UUID uuid) {
        openMap.remove(uuid);
        stopAnim(uuid);
        stopTransport(uuid);
    }

    public boolean isOpen(UUID uuid)      { return openMap.containsKey(uuid); }
    public GemCompressor getOpen(UUID id) { return openMap.get(id); }

    private void stopAnim(UUID uuid) {
        BukkitTask t = animTasks.remove(uuid);
        if (t != null && !t.isCancelled()) t.cancel();
    }

    private void stopTransport(UUID uuid) {
        BukkitTask t = transportTasks.remove(uuid);
        if (t != null && !t.isCancelled()) t.cancel();
    }

    // ─────────────────────────────────────────────────
    //  Animación de partículas de transporte (gemas viajando)
    // ─────────────────────────────────────────────────

    private void startTransportParticles(Player p, GemCompressor comp) {
        final Location from = comp.getLocation().clone().add(0.5, 1, 0.5);
        final World world = from.getWorld();
        if (world == null) return;

        final int[] frame = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline()) { stopTransport(p.getUniqueId()); return; }
            Inventory top = p.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof GuiManager.RyxHolder rxh)
                    || !comp.getId().equals(rxh.getRegionId())) {
                stopTransport(p.getUniqueId()); return;
            }
            if (!comp.hasPendingOutput()) { stopTransport(p.getUniqueId()); return; }

            frame[0]++;
            double angle = frame[0] * 0.4;
            double r = 0.6;

            // Partículas de gema viajando en órbita alrededor del compresor
            for (int i = 0; i < 3; i++) {
                double a = angle + (i * Math.PI * 2 / 3);
                Location pos = from.clone().add(
                        r * Math.cos(a),
                        0.5 + Math.sin(frame[0] * 0.3) * 0.2,
                        r * Math.sin(a));
                world.spawnParticle(Particle.DUST,
                        pos.getX(), pos.getY(), pos.getZ(),
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(
                                comp.getMode() == GemCompressor.Mode.LARGE
                                        ? org.bukkit.Color.fromRGB(160, 32, 240)
                                        : org.bukkit.Color.fromRGB(100, 200, 255),
                                1.0f), true);
            }

            // Partículas de explosión suave en el OUTPUT slot (simulado en el aire)
            Location outPos = from.clone().add(0, 0.8, 0);
            if (frame[0] % 5 == 0) {
                world.spawnParticle(Particle.EXPLOSION,
                        outPos.getX(), outPos.getY(), outPos.getZ(), 1);
            }
        }, 0L, 2L);

        transportTasks.put(p.getUniqueId(), task);
    }

    // ─────────────────────────────────────────────────
    //  Construcción estática del GUI
    // ─────────────────────────────────────────────────

    private void buildStatic(Inventory inv) {
        GuiStyle style = GuiStyle.ENERGY;
        // Borde púrpura en filas 0 y 4, gris en columnas 0 y 8, negro centro
        for (int i = 0; i < 54; i++) {
            int row = i / 9, col = i % 9;
            if (i == SLOT_UPGRADE) continue; // slot de upgrade, se llena después
            if (row == 0 || row == 4) inv.setItem(i, style.borderPane());
            else if (col == 0 || col == 8) inv.setItem(i, style.borderPane());
            else inv.setItem(i, style.fillPane());
        }

        // Flechas de flujo animadas: INPUT → ANIM → OUTPUT
        inv.setItem(21, ItemBuilder.of(Material.ARROW)
                .name("§7→").hideFlags().build());
        inv.setItem(23, ItemBuilder.of(Material.ARROW)
                .name("§7→").hideFlags().build());

        // Separador debajo del anim
        inv.setItem(30, pane(Material.PURPLE_STAINED_GLASS_PANE));
        inv.setItem(32, pane(Material.PURPLE_STAINED_GLASS_PANE));

        // Slot CERRAR
        inv.setItem(SLOT_CLOSE, ItemBuilder.of(Material.BARRIER)
                .name(style.danger + "§l✖ §fCerrar")
                .lore("§7Cerrar el compresor.")
                .hideFlags().build());
    }

    private void populateDynamic(Inventory inv, GemCompressor comp) {
        GuiStyle style = GuiStyle.ENERGY;

        // ── INPUT ──
        String inputGem = comp.getMode() == GemCompressor.Mode.LARGE ? "§dMedianas" : "§dPequeñas";
        String inputType = comp.getMode() == GemCompressor.Mode.LARGE
                ? "§dGemas Medianas" : "§dGemas Pequeñas";
        int inputCount = comp.getInputCount();
        int needed     = comp.getNeededInput();
        inv.setItem(SLOT_INPUT, ItemBuilder.of(
                inputCount > 0 ? Material.AMETHYST_SHARD : Material.ITEM_FRAME)
                .name(style.accent + "§lEntrada §8— §f" + inputGem)
                .lore(
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§9En espera: §f" + inputCount + "§8/§f64",
                        "§9Por ciclo:   §f" + needed + " " + inputType,
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        inputCount > 0
                                ? "§a§l▶ Click para retirar"
                                : "§7Sostén " + inputType + " y haz §bclick §7aquí.")
                .glow(inputCount > 0).hideFlags().build());

        // ── OUTPUT ──
        int pending    = comp.getPendingOutput();
        String outGem  = comp.getMode() == GemCompressor.Mode.LARGE ? "§5Grandes" : "§dMedianas";
        inv.setItem(SLOT_OUTPUT, ItemBuilder.of(
                pending > 0 ? Material.AMETHYST_CLUSTER : Material.GRAY_DYE)
                .name(pending > 0
                        ? style.accent + "§l✦ §fSalida lista: §d" + outGem
                        : "§7Sin salida aún")
                .lore(
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        pending > 0
                                ? "§9Listo: §d" + pending + " §fgemas"
                                : "§7Esperando producción...",
                        pending > 0 ? "§8(Entregando automáticamente →)" : "")
                .glow(pending > 0).hideFlags().build());

        // ── MODEL ──
        if (comp.hasModel()) {
            ItemStack modelDisplay = comp.getModel().clone();
            ItemMeta mm = modelDisplay.getItemMeta();
            if (mm != null) {
                mm.setDisplayName(Color.of("§b§lModelo activo: " + comp.getModeDisplay()));
                mm.setLore(Arrays.asList(
                        Color.of(Txt.THIN),
                        Color.of("§9Tipo: " + comp.getModeDisplay()),
                        Color.of("§9Convierte: " + comp.getInputGemName() + " §8→ " + comp.getOutputGemName()),
                        Color.of(Txt.THIN),
                        Color.of("§c§l▶ Click para retirar el modelo.")));
                modelDisplay.setItemMeta(mm);
            }
            inv.setItem(SLOT_MODEL, modelDisplay);
        } else {
            inv.setItem(SLOT_MODEL, ItemBuilder.of(Material.ITEM_FRAME)
                    .name(style.danger + "§l[ §fSlot de Modelo §c§l]")
                    .lore(
                            "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                            "§7Sostén un §bArmor Trim §7fuera del GUI",
                            "§7y haz §bclic derecho §7en el bloque Crafter.",
                            "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                            "§bTrim común §7(Coast, Tide, Flow...) §8→ §dMediano",
                            "§5Trim raro  §7(Ward, Bolt, Silence...) §8→ §5Grande",
                            "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                            "§8Este compresor acepta UN solo modelo.")
                    .hideFlags().build());
        }

        // ── INFO ──
        boolean hasNextComp = hasNextCompressor(comp);
        boolean hasChest    = hasAdjacentChest(comp);
        inv.setItem(SLOT_INFO, ItemBuilder.of(Material.KNOWLEDGE_BOOK)
                .name(style.secondary + "§lInformación de la Cadena")
                .lore(
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§9Modo:    " + comp.getModeDisplay(),
                        "§9Estado:  " + (comp.isProcessing() ? "§aFUNCIONANDO" : "§7EN ESPERA"),
                        "§9Progreso:" + comp.getProgressBar(10),
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§9Siguiente en cadena:",
                        hasNextComp ? "§a✔ Compresor Grande detectado" : "§c✗ Sin compresor siguiente",
                        hasChest    ? "§a✔ Cofre de salida detectado"  : "§7(Sin cofre — dropea al suelo)",
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§8Cadena: §bFáb. §8→ §bMed. §8→ §5Gde. §8→ §7Cofre")
                .hideFlags().build());

        // ── UPGRADE ──
        int upLevel = comp.getUpgradeLevel();
        int upCost  = comp.getUpgradeCost();
        boolean canUp = comp.canUpgrade();
        inv.setItem(SLOT_UPGRADE, ItemBuilder.of(
                canUp ? Material.ANVIL : Material.BEACON)
                .name((canUp ? style.success : style.secondary)
                        + "§l⬆ §fUpgrade §8(Nv." + upLevel + "/4)")
                .lore(
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        "§9Nivel actual: §f" + comp.getUpgradeLevelName(),
                        canUp ? "§9Siguiente: §fNv." + (upLevel + 1)
                                : "§d✦ Nivel máximo alcanzado",
                        "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                        canUp ? "§9Costo: §e" + upCost + " PP" : "",
                        canUp ? "§7Reduce tiempo de compresión" : "")
                .glow(canUp).hideFlags().build());
    }

    // ─────────────────────────────────────────────────
    //  Animación de estado (SLOT_ANIM)
    // ─────────────────────────────────────────────────

    private void animateState(Inventory inv, GemCompressor comp, int frame) {
        if (comp.isProcessing()) {
            Material mat  = ANIM_MATS[frame % ANIM_MATS.length];
            String   color = switch (frame % 4) {
                case 0 -> "§6§l🔥 §e";
                case 1 -> "§e§l🔥 §6";
                case 2 -> "§c§l🔥 §e";
                default -> "§6§l🔥 §c";
            };
            inv.setItem(SLOT_ANIM, ItemBuilder.of(mat)
                    .name(color + "COMPRIMIENDO")
                    .lore(
                            "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                            "§7Progreso: " + comp.getProgressBar(12),
                            "§9Entrada:  §f" + comp.getInputCount() + "§8/§f" + comp.getNeededInput(),
                            "§9Modo:     " + comp.getModeDisplay())
                    .glow(true).hideFlags().build());

        } else if (comp.getPendingOutput() > 0) {
            Material mat = frame % 2 == 0 ? Material.AMETHYST_SHARD : Material.GLOWSTONE_DUST;
            inv.setItem(SLOT_ANIM, ItemBuilder.of(mat)
                    .name("§d§l✦ §fSalida lista")
                    .lore(
                            "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                            "§d" + comp.getPendingOutput() + " §fgemas producidas.",
                            "§8Entregando automáticamente →")
                    .glow(true).hideFlags().build());

        } else if (!comp.hasModel()) {
            inv.setItem(SLOT_ANIM, ItemBuilder.of(Material.BARRIER)
                    .name("§c§l✗ §fSin modelo")
                    .lore(
                            "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                            "§7Inserta un §bArmor Trim §7en el slot de modelo.")
                    .hideFlags().build());

        } else if (!comp.canProcess()) {
            inv.setItem(SLOT_ANIM, ItemBuilder.of(Material.GRAY_DYE)
                    .name("§7⏸ EN ESPERA")
                    .lore(
                            "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                            "§9Necesita: §f" + comp.getNeededInput() + " " + comp.getInputGemName(),
                            "§9Tiene:    §f" + comp.getInputCount(),
                            "§c✗ Faltan §f" + (comp.getNeededInput() - comp.getInputCount()) + " §cgemas.")
                    .hideFlags().build());

        } else {
            inv.setItem(SLOT_ANIM, ItemBuilder.of(Material.LIME_DYE)
                    .name("§a▶ Listo para iniciar")
                    .lore(
                            "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯",
                            "§9Entrada: §f" + comp.getInputCount() + " gemas disponibles.")
                    .glow(true).hideFlags().build());
        }
    }

    // ─────────────────────────────────────────────────
    //  Manejo de clicks
    // ─────────────────────────────────────────────────

    public void handleClick(Player p, GemCompressor comp, int slot, ClickType click) {
        Inventory topInv = p.getOpenInventory().getTopInventory();

        switch (slot) {
            // ── Input: depositar desde mano / retirar ──
            case SLOT_INPUT -> {
                if (comp.getInputStack() == null) {
                    ItemStack held = p.getInventory().getItemInMainHand();
                    if (held.getType().isAir()) {
                        p.sendMessage(Txt.info("§7Sostén " + comp.getInputGemName()
                                + " §7y haz click para depositar."));
                        return;
                    }
                    EnergyManager.GemType expected = comp.getMode() == GemCompressor.Mode.LARGE
                            ? EnergyManager.GemType.MEDIUM : EnergyManager.GemType.SMALL;
                    EnergyManager.GemType detected = plugin.getEnergyManager().detectGem(held);
                    if (detected != expected) {
                        p.sendMessage(Txt.err("Este compresor necesita "
                                + comp.getInputGemName() + "§c."));
                        return;
                    }
                    int toAdd = Math.min(held.getAmount(), 64);
                    ItemStack ns = held.clone(); ns.setAmount(toAdd);
                    comp.setInputStack(ns);
                    held.setAmount(held.getAmount() - toAdd);
                    if (held.getAmount() <= 0)
                        p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                    p.sendMessage(Txt.ok("§d+" + toAdd + " §agemas depositadas."));
                    comp.tryStart();
                    plugin.getCompressorManager().save();
                    populateDynamic(topInv, comp);
                } else {
                    ItemStack toReturn = comp.getInputStack().clone();
                    comp.setInputStack(null);
                    comp.setProcessing(false);
                    Map<Integer, ItemStack> left = p.getInventory().addItem(toReturn);
                    left.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
                    p.sendMessage(Txt.warn("§dGemas §eretiradas al inventario."));
                    plugin.getCompressorManager().save();
                    populateDynamic(topInv, comp);
                }
            }

            // ── Modelo: retirar ──
            case SLOT_MODEL -> {
                if (!comp.hasModel()) {
                    p.sendMessage(Txt.info("§7Sostén un §bArmor Trim §7y haz "
                            + "§bclic derecho §7en el bloque del compresor para insertar."));
                    return;
                }
                ItemStack model = comp.getModel().clone();
                comp.clearModel();
                comp.setProcessing(false);
                Map<Integer, ItemStack> left = p.getInventory().addItem(model);
                left.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
                p.sendMessage(Txt.warn("§bModelo §eretirado al inventario."));
                plugin.getCompressorManager().save();
                populateDynamic(topInv, comp);
            }

            // ── Info ──
            case SLOT_INFO -> {
                p.sendMessage("§5§l⚗ §d§lCompresor §8— §7Guía de uso:");
                p.sendMessage("§8▸ §7Inserta un §bArmor Trim §7en el bloque del compresor §8(clic derecho)");
                p.sendMessage("§8▸ §bTrim común §8→ §dModo Mediano §8(16 peq. → 1 med.)");
                p.sendMessage("§8▸ §5Trim raro  §8→ §5Modo Grande  §8(4 med. → 1 gde.)");
                p.sendMessage("§8▸ §7Para gemas grandes: pon un compresor §5Grande §7después del §bMediano");
                p.sendMessage("§8▸ §7Cadena completa: §6Fábrica §8→ §bMed. §8→ §5Gde. §8→ §7Cofre");
            }

            // ── Upgrade del compresor ──
            case SLOT_UPGRADE -> {
                if (!comp.canUpgrade()) {
                    p.sendMessage(Txt.warn("El compresor ya está al nivel máximo."));
                    return;
                }
                int cost = comp.getUpgradeCost();
                int pp = plugin.getUpgradeManager().getPP(p.getUniqueId());
                if (pp < cost) {
                    p.sendMessage(Txt.err("Necesitas §e" + cost + " PP§c. Tienes §e" + pp + "§c PP."));
                    return;
                }
                String err = plugin.getCompressorManager().upgrade(p, comp.getLocation());
                if (err != null) {
                    p.sendMessage(Txt.err(err));
                    return;
                }
                p.sendMessage(Txt.ok("§6⬆ §fCompresor mejorado a §eNv." + comp.getUpgradeLevel()
                        + " §8(§c-" + cost + " PP§8)"));
                plugin.getCompressorManager().save();
                populateDynamic(topInv, comp);
            }

            case SLOT_CLOSE -> {
                stopTransport(p.getUniqueId());
                p.closeInventory();
            }
        }
    }

    // ─────────────────────────────────────────────────
    //  Validación de modelos (expuesto al Listener)
    // ─────────────────────────────────────────────────

    public GemCompressor.Mode detectMode(ItemStack item) {
        if (item == null) return GemCompressor.Mode.NONE;
        String t = item.getType().name();
        if (!t.contains("SMITHING_TEMPLATE")) return GemCompressor.Mode.NONE;

        boolean isRare = t.contains("WARD")      || t.contains("SILENCE")
                || t.contains("WAYFINDER") || t.contains("SHAPER")
                || t.contains("RAISER")    || t.contains("HOST")
                || t.contains("BOLT")
                || t.equals("NETHERITE_UPGRADE_SMITHING_TEMPLATE");
        if (isRare) return GemCompressor.Mode.LARGE;

        boolean isCommon = t.contains("COAST")  || t.contains("DUNE")
                || t.contains("EYE")    || t.contains("RIB")
                || t.contains("SNOUT")  || t.contains("SPIRE")
                || t.contains("TIDE")   || t.contains("WILD")
                || t.contains("SENTRY") || t.contains("VEX")
                || t.contains("FLOW");
        if (isCommon) return GemCompressor.Mode.MEDIUM;

        return GemCompressor.Mode.NONE;
    }

    // ─────────────────────────────────────────────────
    //  Helpers de detección de cadena (para INFO slot)
    // ─────────────────────────────────────────────────

    private boolean hasNextCompressor(GemCompressor c) {
        if (c.getMode() != GemCompressor.Mode.MEDIUM) return false;
        org.bukkit.block.BlockFace[] faces = {
            c.getFacing(), org.bukkit.block.BlockFace.NORTH,
            org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.EAST,
            org.bukkit.block.BlockFace.WEST
        };
        for (org.bukkit.block.BlockFace face : faces) {
            Location adj = c.getLocation().clone().add(face.getModX(), 0, face.getModZ());
            GemCompressor next = plugin.getCompressorManager().getAt(adj);
            if (next != null && next.getMode() == GemCompressor.Mode.LARGE) return true;
        }
        return false;
    }

    private boolean hasAdjacentChest(GemCompressor c) {
        org.bukkit.block.BlockFace[] faces = {
            c.getFacing(), org.bukkit.block.BlockFace.NORTH,
            org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.EAST,
            org.bukkit.block.BlockFace.WEST
        };
        for (org.bukkit.block.BlockFace face : faces) {
            org.bukkit.block.Block adj = c.getLocation().getBlock().getRelative(face);
            if (adj.getType() == Material.CHEST || adj.getType() == Material.TRAPPED_CHEST)
                return true;
        }
        return false;
    }

    private ItemStack pane(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(" "); item.setItemMeta(meta); }
        return item;
    }
}
