package com.ryxmc.protection.gui.modern;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.gui.GuiManager;
import com.ryxmc.protection.util.Color;
import com.ryxmc.protection.util.Txt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.function.Consumer;

/**
 * ModernMenu v1.0 — Base abstracta para menús modernos de RyxProtections.
 *
 * Proporciona:
 *  • Cabecera animada con título centrado y estilo consistente
 *  • Borde automático (slots de perímetro)
 *  • Pie de navegación (volver / cerrar)
 *  • Paginación reutilizable
 *  • Sonidos contextuales
 *  • Animación de entrada (fade-in de slots)
 *
 * Los subclases deben implementar buildContents() y registrar los clicks en onClick().
 */
public abstract class ModernMenu {

    protected final RyxProtection plugin;
    protected final GuiManager    gui;
    protected final GuiStyle      style;

    protected Player  viewer;
    protected String  regionId;
    protected String  title;
    protected int     size;        // 27, 36, 45 o 54
    protected int     page;
    protected int     perPage;
    protected boolean animated;    // ¿activar animación de entrada?

    public ModernMenu(RyxProtection plugin, GuiManager gui, GuiStyle style) {
        this.plugin = plugin;
        this.gui    = gui;
        this.style  = style;
        this.size   = 54;
        this.page   = 0;
        this.perPage = 28;
        this.animated = true;
    }

    // ─────────────────────────────────────────────────
    //  Configuración
    // ─────────────────────────────────────────────────

    public ModernMenu viewer(Player p)        { this.viewer = p; return this; }
    public ModernMenu regionId(String id)     { this.regionId = id; return this; }
    public ModernMenu title(String t)         { this.title = t; return this; }
    public ModernMenu size(int s)             { this.size = s; return this; }
    public ModernMenu page(int p)             { this.page = Math.max(0, p); return this; }
    public ModernMenu perPage(int n)          { this.perPage = n; return this; }
    public ModernMenu animated(boolean a)     { this.animated = a; return this; }

    // ─────────────────────────────────────────────────
    //  Apertura
    // ─────────────────────────────────────────────────

    public void open() {
        if (viewer == null) throw new IllegalStateException("viewer no definido");
        Inventory inv = Bukkit.createInventory(new GuiManager.RyxHolder(
                guiType(), viewer.getUniqueId(), regionId), size,
                Color.of(title == null ? "§fMenú" : title));
        holder().setInventory(inv);

        // 1. Borde y relleno
        applyBorder(inv);

        // 2. Contenido del menú
        buildContents(inv);

        // 3. Pie de navegación (volver / cerrar) si aplica
        buildFooter(inv);

        // 4. Registrar y abrir
        gui.register(viewer.getUniqueId(), holder());
        viewer.openInventory(inv);

        // 5. Animación de entrada
        if (animated) {
            animateEntrance(inv);
        }
    }

    /** Tipo de GUI asociado a este menú (para el holder). */
    protected GuiManager.GuiType guiType() {
        return GuiManager.GuiType.PROTECTION_INFO_OWNER;
    }

    /** Holder asociado (se crea en open() y se rellena aquí). */
    protected GuiManager.RyxHolder holder() {
        return new GuiManager.RyxHolder(guiType(), viewer.getUniqueId(), regionId);
    }

    // ─────────────────────────────────────────────────
    //  Construcción visual
    // ─────────────────────────────────────────────────

    /** Aplica borde de estilo y relleno negro al inventario. */
    protected void applyBorder(Inventory inv) {
        int rows = size / 9;
        ItemStack border = style.borderPane();
        ItemStack fill   = style.fillPane();
        for (int i = 0; i < size; i++) {
            int row = i / 9, col = i % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                inv.setItem(i, border);
            } else {
                inv.setItem(i, fill);
            }
        }
    }

    /** Cabecera del menú: título en slot 4 y decoración. */
    protected void header(String title, ItemStack icon) {
        if (viewer == null) return;
        // slot 4 = título principal
        guiOpenInventory().setItem(4, ItemBuilder.of(icon.getType())
                .name(style.accent + "§l" + icon.getItemMeta().getDisplayName() + " §f" + title)
                .hideFlags().build());
    }

    /** Pie de navegación estándar (volver + cerrar). */
    protected void buildFooter(Inventory inv) {
        inv.setItem(size - 3, ItemBuilder.of(Material.ARROW)
                .name(style.secondary + "§l◀ §fVolver")
                .lore("§7Regresar al menú anterior")
                .hideFlags().build());
        inv.setItem(size - 1, ItemBuilder.of(Material.BARRIER)
                .name(style.danger + "§l✖ §fCerrar")
                .lore("§7Cerrar este menú")
                .hideFlags().build());
    }

    /** Limpia el relleno de una zona de slots (para redibujar páginas). */
    protected void clearArea(Inventory inv, int[] slots) {
        ItemStack fill = style.fillPane();
        for (int s : slots) inv.setItem(s, fill);
    }

    /** Retorna el inventario abierto actualmente por el viewer. */
    private Inventory guiOpenInventory() {
        return viewer.getOpenInventory().getTopInventory();
    }

    // ─────────────────────────────────────────────────
    //  Animación de entrada
    // ─────────────────────────────────────────────────

    /** Animación de entrada: los slots se iluminan uno a uno desde el centro. */
    private void animateEntrance(Inventory inv) {
        new BukkitRunnable() {
            int slot = 4; // empieza por el título
            final int total = size;

            @Override public void run() {
                if (!viewer.isOnline()
                        || viewer.getOpenInventory().getTopInventory() != inv) {
                    cancel(); return;
                }

                // Restaurar slot anterior al estado normal (borde/relleno)
                if (slot > 4) {
                    int prev = slot - 1;
                    if (isBorderSlot(prev)) inv.setItem(prev, style.borderPane());
                    else                    inv.setItem(prev, style.fillPane());
                }

                // Resaltar slot actual
                ItemStack current = inv.getItem(slot);
                if (current != null && current.getType() != Material.AIR) {
                    inv.setItem(slot, ItemBuilder.of(current.getType())
                            .name(Color.of(current.getItemMeta().getDisplayName()))
                            .glow(true).hideFlags().build());
                }

                slot++;
                if (slot >= total) {
                    // Último paso: volver a dibujar sin brillo y finalizar
                    cancel();
                    // Reaplicar contenido limpio (sin glow)
                    rebuildContents(inv);
                }
            }
        }.runTaskTimer(plugin, 1L, 2L);
    }

    private boolean isBorderSlot(int slot) {
        int rows = size / 9;
        int row = slot / 9, col = slot % 9;
        return row == 0 || row == rows - 1 || col == 0 || col == 8;
    }

    /** Reconstruye el contenido sin efectos de animación (llamado al finalizar entrada). */
    protected void rebuildContents(Inventory inv) {
        applyBorder(inv);
        buildContents(inv);
        buildFooter(inv);
    }

    // ─────────────────────────────────────────────────
    //  Abstractos (implementar en subclases)
    // ─────────────────────────────────────────────────

    /** Construye el contenido del menú (slots de ítems). */
    protected abstract void buildContents(Inventory inv);

    /** Maneja los clicks del menú. */
    public abstract void onClick(Player p, ClickType click, int slot);

    // ─────────────────────────────────────────────────
    //  Utilidades
    // ─────────────────────────────────────────────────

    protected void send(String message) {
        if (viewer != null) viewer.sendMessage(message);
    }

    protected void sound(org.bukkit.Sound s, float vol, float pitch) {
        if (viewer != null) viewer.playSound(viewer.getLocation(), s, vol, pitch);
    }

    protected void sClick()      { sound(org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f); }
    protected void sBack()       { sound(org.bukkit.Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1f, 0.8f); }
    protected void sSuccess()    { sound(org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f); }
    protected void sError()      { sound(org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f); }
    protected void sToggleOn()   { sound(org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f); }
    protected void sToggleOff()  { sound(org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f); }
    protected void sLevelUp()    { sound(org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f); }
    protected void sDanger()     { sound(org.bukkit.Sound.BLOCK_ANVIL_LAND, 0.5f, 0.8f); }

    /** Barra de progreso visual para DTR. */
    protected String dtrBar(double ratio, int len) {
        return Txt.bar(ratio, len, "§a", "§7");
    }

    /** Crea un ítem de estilo con nombre y lore. */
    protected ItemStack styledItem(Material mat, String name, String... lore) {
        return ItemBuilder.of(mat)
                .name(Color.of(name))
                .lore(lore)
                .hideFlags()
                .build();
    }
}
