package com.ryxmc.protection.factory;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.util.Color;
import com.ryxmc.protection.util.Txt;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IndustrialFactoryGui v3.0 — Panel de fábrica industrial con animaciones únicas.
 *
 * Animaciones v3.0:
 *  • Partículas verde/cian girando cuando la fábrica está produciendo
 *  • Partículas de transporte cuando hay output en el dropper
 *  • Partículas de materiales entrando a la tolva
 *  • Brillo en el producto seleccionado
 *
 * Layout principal (45 slots):
 *  Fila 0: borde naranja industrial
 *  Fila 1: [Info fábrica] en centro
 *  Fila 2: [Producción actual] [Selector de productos]
 *  Fila 3: [Mejoras] [Output en dropper]
 *  Fila 4: bordes
 *  Fila 5: [Cerrar]
 *
 * Layout mejoras (27 slots):
 *  Fila 0: borde amarillo
 *  Fila 1: [Sistema de mejoras] [Tiers disponibles]
 *  Fila 2: [Confirmar mejora]
 *  Fila 3: bordes
 *  Fila 4: [Volver] [Cerrar]
 */
public class IndustrialFactoryGui implements Listener {

    private static final String MAIN_TITLE_TAG    = "ᴘᴀɴᴇʟ ɪɴᴅᴜꜱᴛʀɪᴀʟ";
    private static final String UPGRADE_TITLE_TAG = "ᴍᴇᴊᴏʀᴀꜱ ɪɴᴅᴜꜱᴛʀɪᴀʟᴇꜱ";

    private final RyxProtection plugin;
    private final org.bukkit.NamespacedKey productKey;
    private final org.bukkit.NamespacedKey actionKey;

    private final Map<UUID, String> openViewers = new HashMap<>();
    private final Map<UUID, String> viewerScreen = new HashMap<>();
    /** Tareas de animación de partículas por jugador */
    private final Map<UUID, BukkitTask> particleTasks = new ConcurrentHashMap<>();

    private org.bukkit.scheduler.BukkitTask refreshTask;

    public IndustrialFactoryGui(RyxProtection plugin) {
        this.plugin     = plugin;
        this.productKey = new org.bukkit.NamespacedKey(plugin, "indus_gui_product");
        this.actionKey  = new org.bukkit.NamespacedKey(plugin, "indus_gui_action");
    }

    public void init() {
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, String> entry : new HashMap<>(openViewers).entrySet()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p == null || !p.isOnline()) { cleanupViewer(entry.getKey()); continue; }
                IndustrialFactory f = findFactoryById(entry.getValue());
                if (f == null) { p.closeInventory(); continue; }
                String screen = viewerScreen.getOrDefault(p.getUniqueId(), "main");
                if (screen.equals("main")) refreshMain(p, f);
                else                       refreshUpgrade(p, f);
            }
        }, 20L, 20L);
    }

    public void shutdown() {
        if (refreshTask != null) refreshTask.cancel();
        particleTasks.values().forEach(t -> { if (!t.isCancelled()) t.cancel(); });
        particleTasks.clear();
        openViewers.clear();
        viewerScreen.clear();
    }

    public void open(Player p, IndustrialFactory f) {
        stopParticles(p.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, 45,
                Color.of("§6§l⚙ " + MAIN_TITLE_TAG + " §8— §e" + f.getFactoryId()));
        buildMain(inv, p, f);
        p.openInventory(inv);
        openViewers.put(p.getUniqueId(), f.getFactoryId());
        viewerScreen.put(p.getUniqueId(), "main");
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.4f);

        // Iniciar animación de partículas si la fábrica está produciendo
        if (f.getState() == IndustrialFactory.State.RUNNING || f.getOutputReady() > 0) {
            startParticleAnimation(p, f);
        }
    }

    private void openUpgradePanel(Player p, IndustrialFactory f) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Color.of("§6§l⬆ " + UPGRADE_TITLE_TAG + " §8— §e" + f.getFactoryId()));
        buildUpgrade(inv, p, f);
        p.openInventory(inv);
        viewerScreen.put(p.getUniqueId(), "upgrade");
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.2f);
    }

    private void refreshMain(Player p, IndustrialFactory f) {
        Inventory top = p.getOpenInventory().getTopInventory();
        if (top.getSize() != 45) return;
        buildMain(top, p, f);
    }

    private void refreshUpgrade(Player p, IndustrialFactory f) {
        Inventory top = p.getOpenInventory().getTopInventory();
        if (top.getSize() != 27) return;
        buildUpgrade(top, p, f);
    }

    // ─────────────────────────────────────────────────
    //  Animaciones de partículas industriales
    // ─────────────────────────────────────────────────

    private void startParticleAnimation(Player p, IndustrialFactory f) {
        stopParticles(p.getUniqueId());

        final Location heartLoc = f.getHeartLoc().clone().add(0.5, 1, 0.5);
        final World world = heartLoc.getWorld();
        if (world == null) return;

        final int[] frame = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline()) { stopParticles(p.getUniqueId()); return; }
            Inventory top = p.getOpenInventory().getTopInventory();
            if (top.getSize() != 45) { stopParticles(p.getUniqueId()); return; }

            frame[0]++;
            double angle = frame[0] * 0.3;

            // Partículas de procesamiento industrial alrededor del corazón
            for (int i = 0; i < 6; i++) {
                double a = angle + (i * Math.PI * 2 / 6);
                double r = 0.8;
                Location pos = heartLoc.clone().add(
                        r * Math.cos(a),
                        0.3 + Math.sin(frame[0] * 0.2) * 0.1,
                        r * Math.sin(a));
                world.spawnParticle(Particle.DUST,
                        pos.getX(), pos.getY(), pos.getZ(),
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(0, 200, 80), 1.0f), true);
            }

            // Partículas de output si hay gemas listas
            if (f.getOutputReady() > 0 && frame[0] % 10 == 0) {
                Location dropperLoc = f.getDropperLoc().clone().add(0.5, 1, 0.5);
                world.spawnParticle(Particle.DUST,
                        dropperLoc.getX(), dropperLoc.getY(), dropperLoc.getZ(),
                        3, 0.2, 0.2, 0.2, 0.1,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 215, 0), 1.2f), true);
            }

            // Partículas de materiales entrando a la tolva
            if (frame[0] % 15 == 0) {
                Location hopperLoc = f.getHopperLoc().clone().add(0.5, 1.2, 0.5);
                world.spawnParticle(Particle.FALLING_DUST,
                        hopperLoc.getX(), hopperLoc.getY(), hopperLoc.getZ(),
                        2, 0, -0.1, 0, 0,
                        new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(200, 100, 50), 0.8f), true);
            }
        }, 0L, 2L);

        particleTasks.put(p.getUniqueId(), task);
    }

    private void stopParticles(UUID uuid) {
        BukkitTask t = particleTasks.remove(uuid);
        if (t != null && !t.isCancelled()) t.cancel();
    }

    private void buildMain(Inventory inv, Player p, IndustrialFactory f) {
        borderMain(inv);

        boolean intact = f.isStructureIntact();

        // ── Título con NIVEL VISIBLE ──
        IndustrialFactory.Tier tier = f.getTier();
        inv.setItem(4, item(Material.BLAST_FURNACE,
                "§6§l⚙ §f§lFábrica Industrial §8[Nv." + tier.level + "]",
                Txt.THIN,
                "§9Dueño: §e" + f.getOwnerName(),
                "§9Tier: " + tier.displayName + " §8(Nv." + tier.level + ")",
                "§9Estado: " + stateText(f),
                Txt.THIN,
                intact ? "§a✔ Estructura intacta" : "§c✘ ¡ESTRUCTURA DAÑADA!"));

        // ── Producción actual ──
        int inputStock = f.getCurrentInputStock(plugin);
        inv.setItem(13, item(Material.CLOCK,
                "§e§l⏱ §fᴘʀᴏᴅᴜᴄᴄɪóɴ ᴀᴄᴛᴜᴀʟ",
                Txt.THIN,
                "§9Producto: " + f.getCurrentProduct().displayName,
                "§9Receta: " + f.describeRecipe(),
                "§9Progreso: " + f.getProgressBar(14),
                "§9Tiempo/ciclo: §f" + f.getEffectiveTicks() + " ticks"
                        + (tier != IndustrialFactory.Tier.BASIC
                                ? " §8(base " + f.getCurrentProduct().ticksToProcess + ")" : ""),
                Txt.THIN,
                "§9Material en tolva: §f" + inputStock,
                Txt.THIN,
                "§7Rellenado vía §bTubos§7, §6Combinador §7o §6Rellenador§7."));

        // ── Selector de productos ──
        int[] productSlots = {19, 20, 21, 22};
        IndustrialFactory.ProductType[] products = IndustrialFactory.ProductType.values();
        for (int i = 0; i < products.length; i++) {
            IndustrialFactory.ProductType type = products[i];
            boolean selected = f.getCurrentProduct() == type;
            ItemStack it = item(iconFor(type),
                    (selected ? "§a✔ " : "§7") + type.displayName,
                    Txt.THIN,
                    recipeLore(type),
                    Txt.THIN,
                    selected ? "§a▶ Producto activo" : "§7▶ Click para seleccionar");
            if (selected) glow(it);
            inv.setItem(productSlots[i], tag(it, productKey, type.name()));
        }

        // ── Botón de mejoras ──
        ItemStack upgradeBtn = item(Material.ANVIL,
                "§6§l⬆ §e§lᴍᴇᴊᴏʀᴀꜱ ᴅᴇ ʟᴀ ꜰáʙʀɪᴄᴀ",
                Txt.THIN,
                "§9Tier actual: " + f.getTier().displayName,
                f.getTier().hasNext()
                        ? "§9Siguiente: " + f.getTier().next().displayName
                        : "§6§lNivel máximo alcanzado",
                Txt.THIN,
                "§7Abre el panel de mejoras para ver",
                "§7costos y confirmar la mejora.",
                Txt.THIN,
                "§e▶ Click para abrir");
        inv.setItem(31, tag(upgradeBtn, actionKey, "open_upgrade"));

        // ── Output en el dropper ──
        inv.setItem(40, item(Material.CHEST,
                "§d§l✦ §fOutput en el Dropper",
                Txt.THIN,
                "§9Productos generados: §f" + f.getOutputReady(),
                "§7(Se acumulan en el Dropper o se envían"
                        + " por §bTubos §7si hay conexión.)"));

        // ── Cerrar ──
        inv.setItem(44, item(Material.BARRIER, "§c✖ §fCerrar"));
    }

    private String recipeLore(IndustrialFactory.ProductType t) {
        return switch (t) {
            case GEM_MEDIUM -> "§916x §7Gema Pequeña §f→ §d1x Gema Mediana";
            case GEM_LARGE  -> "§94x §dGema Mediana §7o §f64x §7Gema Pequeña §f→ §51x Gema Grande";
            case PETROLEUM  -> "§98x §8Carbón §f→ §81x Petróleo";
            case COAL_BLOCK -> "§99x §8Carbón §f→ §81x Bloque de Carbón";
        };
    }

    private void buildUpgrade(Inventory inv, Player p, IndustrialFactory f) {
        borderUpgrade(inv);

        inv.setItem(4, item(Material.NETHERITE_INGOT,
                "§6§l⬆ §e§lꜱɪꜱᴛᴇᴍᴀ ᴅᴇ ᴍᴇᴊᴏʀᴀꜱ",
                Txt.THIN,
                "§9Tier actual: " + f.getTier().displayName,
                "§9Velocidad actual: §f×" + f.getTier().speedMultiplier,
                Txt.THIN,
                "§7Cada tier reduce el tiempo de fabricación."));

        IndustrialFactory.Tier[] tiers = IndustrialFactory.Tier.values();
        int[] tierSlots = {10, 11, 12, 13};
        for (int i = 0; i < tiers.length; i++) {
            IndustrialFactory.Tier t = tiers[i];
            boolean current  = f.getTier() == t;
            boolean unlocked = f.getTier().level >= t.level;
            Material mat = switch (t) {
                case BASIC    -> Material.IRON_INGOT;
                case ADVANCED -> Material.COPPER_BLOCK;
                case ELITE    -> Material.DIAMOND;
                case MASTER   -> Material.NETHERITE_BLOCK;
            };
            List<String> lore = new ArrayList<>();
            lore.add(Txt.THIN);
            lore.add("§9Velocidad: §f×" + t.speedMultiplier);
            lore.add(current ? "§a✔ Tier actual" : unlocked ? "§7Ya desbloqueado" : "§7Bloqueado");
            ItemStack it = item(mat, (current ? "§a▶ " : unlocked ? "§7" : "§8") + t.displayName,
                    lore.toArray(new String[0]));
            if (current) glow(it);
            inv.setItem(tierSlots[i], it);
        }

        IndustrialFactory.Tier next = f.getTier().next();
        if (next == null) {
            inv.setItem(16, item(Material.BARRIER,
                    "§8§l✖ §7Nivel Máximo",
                    Txt.THIN,
                    "§7Esta fábrica ya tiene la mejora máxima."));
        } else {
            List<String> lore = new ArrayList<>();
            lore.add(Txt.THIN);
            lore.add("§9Mejorar a: " + next.displayName);
            lore.add("§9Nueva velocidad: §f×" + next.speedMultiplier);
            lore.add(Txt.THIN);
            lore.add("§9Costo §8(sostén los materiales en la mano):");
            boolean canAfford = true;
            for (ItemStack cost : next.getUpgradeCost()) {
                int have = countInHand(p, cost.getType());
                boolean enough = have >= cost.getAmount();
                if (!enough) canAfford = false;
                String mark = enough ? "§a✔" : "§c✘";
                lore.add("  " + mark + " §f" + cost.getAmount() + "x " + cost.getType().name()
                        + " §8(tienes: " + have + ")");
            }
            lore.add(Txt.THIN);
            lore.add("§7Sostén el §fprimer §7material en tu mano principal.");
            lore.add(canAfford ? "§a▶ Click para confirmar mejora" : "§c✗ Materiales insuficientes");

            ItemStack confirmBtn = item(Material.ANVIL,
                    (canAfford ? "§a§l✔ " : "§c§l✖ ") + "§f§lConfirmar Mejora",
                    lore.toArray(new String[0]));
            if (canAfford) glow(confirmBtn);
            inv.setItem(16, tag(confirmBtn, actionKey, "confirm_upgrade"));
        }

        ItemStack backBtn = item(Material.ARROW, "§7§l← §fVolver al Panel Principal");
        inv.setItem(18, tag(backBtn, actionKey, "back_to_main"));
        inv.setItem(26, item(Material.BARRIER, "§c✖ §fCerrar"));
    }

    private String stateText(IndustrialFactory f) {
        return switch (f.getState()) {
            case IDLE            -> "§7En espera";
            case RUNNING         -> "§a▶ Produciendo";
            case DONE            -> "§e¡Listo!";
            case MISSING_HOPPER  -> "§c✘ Falta la tolva";
            case MISSING_DROPPER -> "§c✘ Falta el dropper";
        };
    }

    private Material iconFor(IndustrialFactory.ProductType t) {
        return switch (t) {
            case GEM_MEDIUM, GEM_LARGE -> Material.AMETHYST_CLUSTER;
            case PETROLEUM             -> Material.BLACK_DYE;
            case COAL_BLOCK            -> Material.COAL_BLOCK;
        };
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        boolean isMain    = title != null && title.contains(MAIN_TITLE_TAG);
        boolean isUpgrade = title != null && title.contains(UPGRADE_TITLE_TAG);
        if (!isMain && !isUpgrade) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;

        String factoryId = openViewers.get(p.getUniqueId());
        IndustrialFactory f = findFactoryById(factoryId);
        if (f == null) { p.closeInventory(); return; }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (isMain && event.getSlot() == 44) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            stopParticles(p.getUniqueId());
            p.closeInventory();
            return;
        }
        if (isUpgrade && event.getSlot() == 26) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            stopParticles(p.getUniqueId());
            p.closeInventory();
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        if (isMain) {
            String productName = meta.getPersistentDataContainer().get(productKey, PersistentDataType.STRING);
            if (productName != null) {
                try {
                    IndustrialFactory.ProductType type = IndustrialFactory.ProductType.valueOf(productName);
                    f.setCurrentProduct(type);
                    p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.3f);
                    p.sendMessage(Txt.ok("§6⚙ Producto cambiado a " + type.displayName + "§a."));
                    plugin.getIndustrialFactoryManager().save();
                    refreshMain(p, f);
                } catch (Exception ignored) {}
                return;
            }
            String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
            if ("open_upgrade".equals(action)) {
                openUpgradePanel(p, f);
            }
            return;
        }

        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if ("back_to_main".equals(action)) {
            open(p, f);
        } else if ("confirm_upgrade".equals(action)) {
            handleUpgrade(p, f);
        }
    }

    private void handleUpgrade(Player p, IndustrialFactory f) {
        IndustrialFactory.Tier next = f.getTier().next();
        if (next == null) {
            p.sendMessage(Txt.warn("Esta fábrica ya tiene el máximo nivel."));
            return;
        }

        ItemStack[] costs = next.getUpgradeCost();
        for (ItemStack cost : costs) {
            if (countInHand(p, cost.getType()) < cost.getAmount()) {
                p.sendMessage(Txt.err("Necesitas §e" + cost.getAmount() + "x " + cost.getType().name()
                        + " §cen la mano."));
                return;
            }
        }
        for (ItemStack cost : costs) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType() != cost.getType() || hand.getAmount() < cost.getAmount()) {
                p.sendMessage(Txt.err("Sostén §e" + cost.getAmount() + "x " + cost.getType().name()
                        + " §cen tu mano principal para continuar."));
                return;
            }
            hand.setAmount(hand.getAmount() - cost.getAmount());
        }

        f.setTier(next);
        plugin.getIndustrialFactoryManager().save();

        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        p.sendMessage(Txt.ok("§6§l⬆ §f§lFábrica mejorada §aa " + next.displayName + "§a."));
        refreshUpgrade(p, f);
    }

    private int countInHand(Player p, Material mat) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        return hand.getType() == mat ? hand.getAmount() : 0;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player p)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.getOpenInventory().getTopInventory().getType() == org.bukkit.event.inventory.InventoryType.CRAFTING) {
                cleanupViewer(p.getUniqueId());
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cleanupViewer(event.getPlayer().getUniqueId());
    }

    private void cleanupViewer(UUID uuid) {
        stopParticles(uuid);
        openViewers.remove(uuid);
        viewerScreen.remove(uuid);
    }

    private IndustrialFactory findFactoryById(String id) {
        if (id == null) return null;
        for (IndustrialFactory f : plugin.getIndustrialFactoryManager().getAll())
            if (f.getFactoryId().equals(id)) return f;
        return null;
    }

    private void borderMain(Inventory inv) {
        ItemStack glass = item(Material.ORANGE_STAINED_GLASS_PANE, " ");
        int[] slots = {0,1,2,3,5,6,7,8,9,17,18,26,27,35,36,37,38,39,41,42,43};
        for (int s : slots) inv.setItem(s, glass);
    }

    private void borderUpgrade(Inventory inv) {
        ItemStack glass = item(Material.YELLOW_STAINED_GLASS_PANE, " ");
        int[] slots = {0,1,2,3,5,6,7,8,9,15,17,19,20,21,23,24,25};
        for (int s : slots) inv.setItem(s, glass);
    }

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Color.of(name));
            if (lore.length > 0) {
                List<String> colored = new ArrayList<>();
                for (String l : lore) colored.add(Color.of(l));
                meta.setLore(colored);
            }
            it.setItemMeta(meta);
        }
        return it;
    }

    private void glow(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            it.setItemMeta(meta);
        }
    }

    private ItemStack tag(ItemStack it, org.bukkit.NamespacedKey key, String value) {
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
            it.setItemMeta(meta);
        }
        return it;
    }
}
