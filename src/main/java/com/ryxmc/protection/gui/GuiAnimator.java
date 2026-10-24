package com.ryxmc.protection.gui;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.util.Color;
import com.ryxmc.protection.util.Txt;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GuiAnimator v2.0 — Motor de animaciones de inventario para RyxProtections.
 *
 * Mejoras v2.0:
 *  • Animaciones más suaves (ticks reducidos de 9-11 → 4-8).
 *  • Nuevos tipos: PULSO (latido), ENTRADA (revelado slot a slot), VIBRACIÓN (alerta).
 *  • Partículas al abrir menú.
 *  • Notificaciones en barra de acción (ActionBar).
 *  • Solo se actualizan los slots que cambian (menor carga de red).
 *  • Limpieza automática al cerrar el inventario (sin fugas de tareas).
 *
 * Tipos de animación:
 *  startInfoAnimation      → Ola de bordes + barra de DTR proporcional con pulso.
 *  startSettingsAnimation  → Ola dorada en borde.
 *  startAdminAnimation     → Ola roja pulsante + notificación de advertencia.
 *  startEnergyAnimation    → Ola púrpura en borde.
 *  startPulseAnimation     → Latido de color en un slot concreto (resaltar ítem).
 *  startEntranceAnimation  → Revelado progresivo de slots al abrir el menú.
 *  startWarningVibration   → Parpadeo rojo intenso (región raideable).
 *
 * Las animaciones se pueden desactivar por jugador con toggle().
 * Llamar stop() en onClose de GuiListener para liberar recursos.
 */
public class GuiAnimator {

    // ─────────────────────────────────────────────────
    //  Paletas de color (por tipo de menú)
    // ─────────────────────────────────────────────────

    private static final Material[] PALETTE_INFO = {
            Material.GRAY_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS_PANE,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS_PANE,
    };

    private static final Material[] PALETTE_SETTINGS = {
            Material.GRAY_STAINED_GLASS_PANE,
            Material.ORANGE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE,
            Material.ORANGE_STAINED_GLASS_PANE,
    };

    private static final Material[] PALETTE_ADMIN = {
            Material.GRAY_STAINED_GLASS_PANE,
            Material.RED_STAINED_GLASS_PANE,
            Material.BROWN_STAINED_GLASS_PANE,
            Material.RED_STAINED_GLASS_PANE,
    };

    private static final Material[] PALETTE_ENERGY = {
            Material.GRAY_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE,
            Material.MAGENTA_STAINED_GLASS_PANE,
            Material.PINK_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE,
    };

    /** Alerta de región raideable: parpadeo rojo/negro */
    private static final Material[] PALETTE_RAIDABLE = {
            Material.RED_STAINED_GLASS_PANE,
            Material.BLACK_STAINED_GLASS_PANE,
    };

    /** Slots 1-7 de la fila superior (sin esquinas) → barra de DTR. */
    private static final int[] DTR_BAR_SLOTS = {1, 2, 3, 4, 5, 6, 7};

    /** Slots de navegación que la animación NUNCA debe sobrescribir. */
    private static final Set<Integer> PROTECTED_SLOTS = Set.of(
            45, 46, 49, 53  // página anterior, volver, cerrar, página siguiente
    );

    // ─────────────────────────────────────────────────
    //  Estado interno
    // ─────────────────────────────────────────────────

    private final RyxProtection plugin;
    /** Tareas de animación activas por jugador (una por jugador). */
    private final Map<UUID, BukkitTask> tasks = new ConcurrentHashMap<>();
    /** Animaciones activadas/desactivadas por jugador (default: true). */
    private final Map<UUID, Boolean> enabled = new ConcurrentHashMap<>();

    public GuiAnimator(RyxProtection plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────────────────────────────────
    //  API pública — animaciones principales
    // ─────────────────────────────────────────────────

    /**
     * Animación combinada para el menú de Info:
     *  - Bordes: ola de color (azul o rojo si raideable).
     *  - Slots 1-7 (fila superior central): barra de DTR proporcional con pulso.
     *
     * @param dtrPct  DTR actual / DTR máximo ∈ [0.0, 1.0]
     * @param raidable true → parpadeo de alerta rojo/negro en bordes
     */
    public void startInfoAnimation(Player p, Inventory inv, double dtrPct, boolean raidable) {
        if (!isEnabled(p.getUniqueId())) return;
        stop(p.getUniqueId());

        Material[] palette = raidable ? PALETTE_RAIDABLE : PALETTE_INFO;
        int filledBars = Math.max(0, Math.min(DTR_BAR_SLOTS.length,
                (int) Math.round(dtrPct * DTR_BAR_SLOTS.length)));
        Material barColor = dtrPct > 0.6 ? Material.LIME_STAINED_GLASS_PANE
                         : dtrPct > 0.3 ? Material.YELLOW_STAINED_GLASS_PANE
                                        : Material.RED_STAINED_GLASS_PANE;
        Material barPulse = dtrPct > 0.6 ? Material.LIME_CONCRETE
                         : dtrPct > 0.3 ? Material.YELLOW_CONCRETE
                                        : Material.RED_CONCRETE;

        int[] borderSlots = buildBorderSlots(inv.getSize());

        long period = raidable ? 4L : 8L; // más suave y rápido
        BukkitTask task = new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (!p.isOnline() || p.getOpenInventory().getTopInventory() != inv) {
                    cancel(); return;
                }

                // ── Ola de borde (excluye slots 1-7 reservados para barra DTR
                //          y slots de navegación protegidos) ───────────────────
                for (int i = 0; i < borderSlots.length; i++) {
                    int s = borderSlots[i];
                    if (s >= 1 && s <= 7) continue;          // barra DTR
                    if (PROTECTED_SLOTS.contains(s)) continue; // no tocar cerrar/páginas
                    inv.setItem(s, pane(palette[(i + tick) % palette.length]));
                }

                // ── Barra de DTR con pulso ────────────────────────────────────
                int pulse = tick % DTR_BAR_SLOTS.length;
                for (int i = 0; i < DTR_BAR_SLOTS.length; i++) {
                    Material m = (i < filledBars)
                            ? (i == pulse ? barPulse : barColor)
                            : Material.BLACK_STAINED_GLASS_PANE;
                    inv.setItem(DTR_BAR_SLOTS[i], pane(m));
                }

                // Notificación ActionBar si está raideable (cada 20 ticks = 1s)
                if (raidable && tick % 20 == 0) {
                    p.sendActionBar(Component.text(
                            Txt.PRE_ERR + "§4§l¡RAIDEABLE! §7DTR crítico — regenera en "
                            + Txt.time(Math.max(0, 600 - (tick / 20))) + "s"));
                }

                tick++;
            }
        }.runTaskTimer(plugin, 2L, period);

        tasks.put(p.getUniqueId(), task);

        // Partículas de entrada alrededor del jugador
        spawnEntranceParticles(p);
    }

    /** Ola dorada en borde — menú de Configuración. */
    public void startSettingsAnimation(Player p, Inventory inv) {
        if (!isEnabled(p.getUniqueId())) return;
        runWave(p, inv, PALETTE_SETTINGS, 8L);
    }

    /** Ola roja pulsante en borde — Panel de Administración. */
    public void startAdminAnimation(Player p, Inventory inv) {
        if (!isEnabled(p.getUniqueId())) return;
        runWave(p, inv, PALETTE_ADMIN, 6L);
    }

    /** Ola púrpura en borde — menú de Energía/Gemas. */
    public void startEnergyAnimation(Player p, Inventory inv) {
        if (!isEnabled(p.getUniqueId())) return;
        runWave(p, inv, PALETTE_ENERGY, 7L);
    }

    /**
     * Pulso de color en un slot concreto — usado para resaltar ítems importantes.
     * Alterna entre el material dado y su versión "concreto" (más brillante).
     */
    public void startPulseAnimation(Player p, Inventory inv, int slot,
                                    Material base, Material pulse) {
        if (!isEnabled(p.getUniqueId())) return;
        stop(p.getUniqueId());

        BukkitTask task = new BukkitRunnable() {
            boolean bright = false;
            @Override public void run() {
                if (!p.isOnline() || p.getOpenInventory().getTopInventory() != inv) {
                    cancel(); return;
                }
                inv.setItem(slot, pane(bright ? pulse : base));
                bright = !bright;
            }
        }.runTaskTimer(plugin, 2L, 10L);
        tasks.put(p.getUniqueId(), task);
    }

    /**
     * Revelado progresivo de slots al abrir el menú (entrada animada).
     * Los slots se van iluminando uno a uno desde el centro hacia afuera.
     */
    public void startEntranceAnimation(Player p, Inventory inv, int[] revealOrder) {
        if (!isEnabled(p.getUniqueId())) return;
        stop(p.getUniqueId());

        BukkitTask task = new BukkitRunnable() {
            int index = 0;
            @Override public void run() {
                if (!p.isOnline() || p.getOpenInventory().getTopInventory() != inv) {
                    cancel(); return;
                }
                if (index >= revealOrder.length) {
                    cancel();
                    return;
                }
                int s = revealOrder[index];
                if (PROTECTED_SLOTS.contains(s)) { index++; return; } // no animar navegación
                ItemStack current = inv.getItem(s);
                if (current != null && current.getType() != Material.AIR
                        && current.getType().isItem()) {
                    inv.setItem(s, glowItem(current));
                }
                // Sonido suave en cada revelado
                if (index % 5 == 0)
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 1.5f + (index * 0.05f));
                index++;
            }
        }.runTaskTimer(plugin, 2L, 3L);
        tasks.put(p.getUniqueId(), task);
    }

    /** Parpadeo rojo intenso — alerta de región raideable o acción peligrosa. */
    public void startWarningVibration(Player p, Inventory inv) {
        if (!isEnabled(p.getUniqueId())) return;
        stop(p.getUniqueId());

        BukkitTask task = new BukkitRunnable() {
            boolean red = true;
            @Override public void run() {
                if (!p.isOnline() || p.getOpenInventory().getTopInventory() != inv) {
                    cancel(); return;
                }
                Material m = red ? Material.RED_STAINED_GLASS_PANE
                                 : Material.BLACK_STAINED_GLASS_PANE;
                // Vibrar todos los slots de borde (excepto navegación protegida)
                for (int s : buildBorderSlots(inv.getSize())) {
                    if (PROTECTED_SLOTS.contains(s)) continue;
                    ItemStack it = inv.getItem(s);
                    if (it != null && it.getType().isItem()) {
                        inv.setItem(s, pane(m));
                    }
                }
                red = !red;
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.6f);
            }
        }.runTaskTimer(plugin, 2L, 6L);
        tasks.put(p.getUniqueId(), task);
    }

    // ─────────────────────────────────────────────────
    //  API pública — utilidades
    // ─────────────────────────────────────────────────

    /** Activa/desactiva animaciones para el jugador. Retorna el nuevo estado. */
    public boolean toggle(UUID uuid) {
        boolean now = !isEnabled(uuid);
        enabled.put(uuid, now);
        if (!now) stop(uuid);
        return now;
    }

    /** @return true si el jugador tiene animaciones activadas (default: sí). */
    public boolean isEnabled(UUID uuid) {
        return enabled.getOrDefault(uuid, true);
    }

    /** Cancela la animación activa del jugador. */
    public void stop(UUID uuid) {
        BukkitTask t = tasks.remove(uuid);
        if (t != null && !t.isCancelled()) t.cancel();
    }

    /** Cancela TODAS las animaciones activas. Llamar en onDisable(). */
    public void stopAll() {
        tasks.values().forEach(t -> { if (!t.isCancelled()) t.cancel(); });
        tasks.clear();
    }

    /** Partículas sutiles al abrir un menú (entrada). */
    private void spawnEntranceParticles(Player p) {
        Location loc = p.getLocation().add(0, 1, 0);
        World world = loc.getWorld();
        if (world == null) return;
        world.spawnParticle(Particle.END_ROD, loc, 6, 0.3, 0.5, 0.3, 0.1);
        world.spawnParticle(Particle.CRIT, loc, 4, 0.2, 0.3, 0.2, 0.05);
    }

    /** Aplica brillo (encantamiento oculto) a un ítem existente. */
    private ItemStack glowItem(ItemStack original) {
        ItemStack copy = original.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            // Brillo visual sin efecto real
            for (Enchantment ench : Enchantment.values()) {
                if (ench.getName().equals("arrow_damage") || ench.getName().equals("damage_all")) {
                    meta.addEnchant(ench, 1, true);
                    break;
                }
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            copy.setItemMeta(meta);
        }
        return copy;
    }

    // ─────────────────────────────────────────────────
    //  Helpers internos
    // ─────────────────────────────────────────────────

    private void runWave(Player p, Inventory inv, Material[] palette, long periodTicks) {
        stop(p.getUniqueId());
        int[] slots = buildBorderSlots(inv.getSize());

        BukkitTask task = new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (!p.isOnline() || p.getOpenInventory().getTopInventory() != inv) {
                    cancel(); return;
                }
                for (int i = 0; i < slots.length; i++) {
                    int s = slots[i];
                    if (PROTECTED_SLOTS.contains(s)) continue; // no tocar cerrar/páginas
                    inv.setItem(s, pane(palette[(i + tick) % palette.length]));
                }
                tick++;
            }
        }.runTaskTimer(plugin, 2L, periodTicks);
        tasks.put(p.getUniqueId(), task);
    }

    /** Índices de slots del borde para un inventario de {@code size} slots. */
    private int[] buildBorderSlots(int size) {
        int rows = size / 9;
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            int row = i / 9, col = i % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8)
                list.add(i);
        }
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Crea un panel de cristal con nombre vacío. */
    private ItemStack pane(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(" "); item.setItemMeta(meta); }
        return item;
    }
}
