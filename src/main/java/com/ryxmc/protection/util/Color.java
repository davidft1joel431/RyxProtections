package com.ryxmc.protection.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Colores HEX, &X y utilidades de texto Adventure */
public final class Color {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern PS_HEX_PATTERN = Pattern.compile(
            "&x&([A-Fa-f0-9])&([A-Fa-f0-9])&([A-Fa-f0-9])&([A-Fa-f0-9])&([A-Fa-f0-9])&([A-Fa-f0-9])");

    private Color() {}

    public static String of(String s) {
        if (s == null) return "";
        // PS hex: &x&R&R&G&G&B&B
        Matcher m = PS_HEX_PATTERN.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String hex = "#"+m.group(1)+m.group(2)+m.group(3)+m.group(4)+m.group(5)+m.group(6);
            m.appendReplacement(sb, ChatColor.of(hex).toString());
        }
        m.appendTail(sb);
        s = sb.toString();
        // &#RRGGBB
        m = HEX_PATTERN.matcher(s);
        sb = new StringBuffer();
        while (m.find()) m.appendReplacement(sb, ChatColor.of("#"+m.group(1)).toString());
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    public static Component comp(String s) {
        return LegacyComponentSerializer.legacySection().deserialize(of(s));
    }

    public static String strip(String s) { return ChatColor.stripColor(of(s)); }
}
