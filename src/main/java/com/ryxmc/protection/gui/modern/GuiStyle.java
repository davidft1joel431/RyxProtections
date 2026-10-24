package com.ryxmc.protection.gui.modern;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * GuiStyle v1.0 — Temas visuales unificados para los menús de RyxProtections.
 *
 * Cada tipo de menú tiene una paleta propia (bordes, relleno, acento, peligro,
 * éxito) que se aplica de forma consistente en todos los GUIs del plugin.
 *
 * Uso:
 *   GuiStyle style = GuiStyle.INFO;
 *   inv.setItem(4, ItemBuilder.of(Material.SHIELD).name(style.accent() + "§lTítulo").build());
 */
public enum GuiStyle {

    /** Información de región — azul / cian */
    INFO(
            Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.BLACK_STAINED_GLASS_PANE,
            "§b", "§6", "§a", "§c", "§7"),

    /** Configuración — naranja / dorado */
    SETTINGS(
            Material.ORANGE_STAINED_GLASS_PANE,
            Material.BLACK_STAINED_GLASS_PANE,
            "§6", "§e", "§a", "§c", "§7"),

    /** Miembros — azul claro */
    MEMBERS(
            Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.BLACK_STAINED_GLASS_PANE,
            "§b", "§f", "§a", "§c", "§7"),

    /** Energía / Gemas — púrpura / magenta */
    ENERGY(
            Material.PURPLE_STAINED_GLASS_PANE,
            Material.BLACK_STAINED_GLASS_PANE,
            "§d", "§f", "§a", "§c", "§7"),

    /** Mejoras — verde lima */
    UPGRADE(
            Material.LIME_STAINED_GLASS_PANE,
            Material.BLACK_STAINED_GLASS_PANE,
            "§a", "§6", "§a", "§c", "§7"),

    /** Tienda — oro */
    SHOP(
            Material.YELLOW_STAINED_GLASS_PANE,
            Material.BLACK_STAINED_GLASS_PANE,
            "§e", "§6", "§a", "§c", "§7"),

    /** Administración — rojo oscuro */
    ADMIN(
            Material.RED_STAINED_GLASS_PANE,
            Material.BLACK_STAINED_GLASS_PANE,
            "§4", "§c", "§a", "§c", "§7"),

    /** Confirmación — negro / rojo */
    CONFIRM(
            Material.BLACK_STAINED_GLASS_PANE,
            Material.BLACK_STAINED_GLASS_PANE,
            "§4", "§c", "§a", "§c", "§7"),

    /** Opciones de protección — gris / comando */
    OPTIONS(
            Material.GRAY_STAINED_GLASS_PANE,
            Material.BLACK_STAINED_GLASS_PANE,
            "§7", "§6", "§a", "§c", "§7");

    /** Material usado para el borde del menú. */
    public final Material borderMat;

    /** Material usado para rellenar slots vacíos. */
    public final Material fillMat;

    /** Color de acento principal (títulos). */
    public final String accent;

    /** Color secundario (subtítulos). */
    public final String secondary;

    /** Color de éxito. */
    public final String success;

    /** Color de peligro / error. */
    public final String danger;

    /** Color neutro / texto secundario. */
    public final String neutral;

    GuiStyle(Material border, Material fill,
             String accent, String secondary,
             String success, String danger, String neutral) {
        this.borderMat = border;
        this.fillMat   = fill;
        this.accent    = accent;
        this.secondary = secondary;
        this.success   = success;
        this.danger    = danger;
        this.neutral   = neutral;
    }

    /** Crea un panel de cristal del material de borde con nombre vacío. */
    public ItemStack borderPane() {
        return new ItemBuilder(borderMat).name(" ").glow(false).build();
    }

    /** Crea un panel de relleno con nombre vacío. */
    public ItemStack fillPane() {
        return new ItemBuilder(fillMat).name(" ").glow(false).build();
    }
}
