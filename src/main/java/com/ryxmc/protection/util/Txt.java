package com.ryxmc.protection.util;

/** Prefijos y texto con paleta RyxMC */
public final class Txt {
    private Txt() {}

    public static final String PRE      = "§6§lRyx§e§lMC §8§l- §f§lProtections §8» §r";
    public static final String PRE_OK   = "§6§lRyx§e§lMC §8§l- §a§lProtections §8» §r";
    public static final String PRE_ERR  = "§6§lRyx§e§lMC §8§l- §c§lProtections §8» §r";
    public static final String PRE_WARN = "§6§lRyx§e§lMC §8§l- §e§lProtections §8» §r";
    public static final String SEP      = "§8§m══════════════════════════════§r";
    public static final String THIN     = "§8⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯";

    public static String ok(String m)   { return PRE_OK   + "§a" + m; }
    public static String err(String m)  { return PRE_ERR  + "§c" + m; }
    public static String warn(String m) { return PRE_WARN + "§e" + m; }
    public static String info(String m) { return PRE      + "§7" + m; }

    // Small caps unicode
    private static final char[] SC =
            "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ".toCharArray();

    public static String sc(String t) {
        if (t == null) return "";
        StringBuilder sb = new StringBuilder(t.length());
        for (char c : t.toCharArray()) {
            if (c >= 'a' && c <= 'z') sb.append(SC[c - 'a']);
            else if (c >= 'A' && c <= 'Z') sb.append(SC[c - 'A']);
            else sb.append(c);
        }
        return sb.toString();
    }

    /** Barra de progreso coloreada */
    public static String bar(double ratio, int len, String colorFull, String colorEmpty) {
        int filled = (int) Math.round(ratio * len);
        int empty  = len - filled;
        return colorFull + "█".repeat(Math.max(0, filled)) + colorEmpty + "░".repeat(Math.max(0, empty));
    }

    /** Formatea segundos → "7m 32s" */
    public static String time(long seconds) {
        long m = seconds / 60, s = seconds % 60;
        return m > 0 ? m + "m " + s + "s" : s + "s";
    }

    /** Formatea dinero → "$5,000" */
    public static String money(double amount) {
        return "$" + String.format("%,.0f", amount);
    }

    /** Formatea número entero con separador de miles → "1,234" */
    public static String num(long n) {
        return String.format("%,d", n);
    }

    /** Formatea decimal con 1 dígito → "4.5" */
    public static String dec(double d) {
        return String.format("%.1f", d);
    }

    /** Texto con color de salud: verde >66%, amarillo >33%, rojo resto */
    public static String healthColor(double ratio) {
        return ratio > 0.66 ? "§a" : ratio > 0.33 ? "§e" : ratio > 0 ? "§c" : "§4";
    }
}
