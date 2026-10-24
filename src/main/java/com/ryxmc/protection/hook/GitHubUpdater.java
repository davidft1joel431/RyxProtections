package com.ryxmc.protection.hook;

import com.ryxmc.protection.RyxProtection;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

/**
 * GitHubUpdater v1.0 — Sistema de auto-actualización desde GitHub.
 *
 * Verifica si hay una versión nueva disponible en el repositorio de GitHub
 * y permite actualizar el plugin desde el juego o la consola.
 *
 * Configuración:
 *  - GITHUB_REPO_USER: Usuario u organización de GitHub
 *  - GITHUB_REPO_NAME: Nombre del repositorio
 *  - GITHUB_VERSION_FILE: Ruta al archivo de versión (ej: "version.txt" o "latest.json")
 *
 * Uso:
 *  - /ryx update — Verificar y descargar actualización (solo admins)
 *  - Se verifica automáticamente al iniciar el plugin
 */
public class GitHubUpdater implements CommandExecutor {

    private static final String GITHUB_REPO_USER = "Joelc";
    private static final String GITHUB_REPO_NAME = "RyxProtections";
    private static final String GITHUB_VERSION_FILE = "version.txt";
    private static final String GITHUB_RAW_BASE = "https://raw.githubusercontent.com/%s/%s/main/%s";
    private static final String GITHUB_RELEASES_API = "https://api.github.com/repos/%s/%s/releases/latest";

    private final RyxProtection plugin;
    private String currentVersion;
    private String latestVersion;
    private String downloadUrl;
    private boolean updateAvailable = false;
    private boolean checked = false;

    public GitHubUpdater(RyxProtection plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    /** Verifica si hay una actualización disponible (asíncrono). */
    public void checkForUpdate() {
        CompletableFuture.runAsync(() -> {
            try {
                String remoteVersion = fetchRemoteVersion();
                if (remoteVersion == null || remoteVersion.trim().isEmpty()) {
                    plugin.getLogger().warning("[Updater] No se pudo obtener la versión remota de GitHub.");
                    return;
                }

                latestVersion = remoteVersion.trim();
                checked = true;

                int cmp = compareVersions(currentVersion, latestVersion);
                if (cmp < 0) {
                    updateAvailable = true;
                    downloadUrl = String.format(
                            "https://github.com/%s/%s/releases/latest/download/RyxProtections-%s.jar",
                            GITHUB_REPO_USER, GITHUB_REPO_NAME, latestVersion);

                    plugin.getLogger().info("═════════════════════════════════════════════════════");
                    plugin.getLogger().info("[Updater] ¡Actualización disponible!");
                    plugin.getLogger().info("[Updater] Versión actual: " + currentVersion);
                    plugin.getLogger().info("[Updater] Versión nueva:  " + latestVersion);
                    plugin.getLogger().info("[Updater] Descarga: " + downloadUrl);
                    plugin.getLogger().info("════════════════════════════════════════════════════");

                    // Notificar a jugadores con permiso de admin en el juego
                    Bukkit.getOnlinePlayers().forEach(p -> {
                        if (p.hasPermission("ryx.admin")) {
                            p.sendMessage(ChatColor.GOLD + "═════════════════════════════════════════════════════");
                            p.sendMessage(ChatColor.YELLOW + "⚡ RyxProtections — Actualización disponible");
                            p.sendMessage(ChatColor.GRAY + "  Versión actual: " + ChatColor.RED + currentVersion);
                            p.sendMessage(ChatColor.GRAY + "  Versión nueva:  " + ChatColor.GREEN + latestVersion);
                            p.sendMessage(ChatColor.YELLOW + "  Usa: " + ChatColor.WHITE + "/ryx update" + ChatColor.YELLOW + " para actualizar");
                            p.sendMessage(ChatColor.GOLD + "═════════════════════════════════════════════════════");
                        }
                    });
                } else {
                    plugin.getLogger().info("[Updater] RyxProtections está actualizado (v" + currentVersion + ").");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Updater] Error verificando actualización: " + e.getMessage());
            }
        });
    }

    /** Descarga e instala la actualización (reemplaza el JAR actual). */
    public void performUpdate(CommandSender sender) {
        if (!updateAvailable || downloadUrl == null) {
            sender.sendMessage(ChatColor.RED + "No hay actualización disponible.");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                sender.sendMessage(ChatColor.YELLOW + "⬇ Descargando actualización v" + latestVersion + "...");
                URL url = new URL(downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    sender.sendMessage(ChatColor.RED + "Error al descargar: Código " + responseCode);
                    return;
                }

                // Guardar el JAR actualizado temporalmente
                java.nio.file.Path tempJar = java.nio.file.Files.createTempFile("RyxProtections-", ".jar");
                java.nio.file.Files.copy(conn.getInputStream(), tempJar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                conn.disconnect();

                // Obtener la ruta del JAR actual
                java.nio.file.Path currentJar = java.nio.file.Paths.get(
                        plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());

                // Reemplazar el JAR actual (se aplicará al reiniciar)
                java.nio.file.Files.move(tempJar, currentJar,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ChatColor.GREEN + "✅ Actualización descargada: v" + latestVersion);
                    sender.sendMessage(ChatColor.YELLOW + "🔄 Reinicia el servidor para aplicar los cambios.");
                    plugin.getLogger().info("[Updater] Actualización v" + latestVersion + " instalada. Reinicio requerido.");
                });
            } catch (Exception e) {
                plugin.getLogger().warning("[Updater] Error instalando actualización: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(ChatColor.RED + "❌ Error al actualizar: " + e.getMessage()));
            }
        });
    }

    /** @return true si hay una actualización disponible. */
    public boolean isUpdateAvailable() { return updateAvailable; }

    /** @return la versión más reciente obtenida de GitHub. */
    public String getLatestVersion() { return latestVersion; }

    /** @return la URL de descarga. */
    public String getDownloadUrl() { return downloadUrl; }

    /** @return true si ya se verificó al menos una vez. */
    public boolean isChecked() { return checked; }

    // ─────────────────────────────────────────────────
    //  Comandos
    // ─────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p) && !(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            sender.sendMessage(ChatColor.RED + "Solo consola o jugadores.");
            return true;
        }

        if (!sender.hasPermission("ryx.admin")) {
            sender.sendMessage(ChatColor.RED + "Sin permiso: ryx.admin");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("check")) {
            checkForUpdate();
            if (!checked) {
                sender.sendMessage(ChatColor.YELLOW + "⏳ Verificando actualización... revisa la consola.");
            } else if (updateAvailable) {
                sender.sendMessage(ChatColor.GREEN + "✅ Actualización disponible: v" + latestVersion);
                sender.sendMessage(ChatColor.YELLOW + "Usa: /ryx update");
            } else {
                sender.sendMessage(ChatColor.GREEN + "✅ RyxProtections está actualizado (v" + currentVersion + ").");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("update")) {
            if (!updateAvailable) {
                sender.sendMessage(ChatColor.RED + "No hay actualización disponible.");
                return true;
            }
            performUpdate(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("version")) {
            sender.sendMessage(ChatColor.GOLD + "RyxProtections v" + currentVersion);
            if (latestVersion != null)
                sender.sendMessage(ChatColor.GRAY + "Última en GitHub: v" + latestVersion);
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Uso: /ryx update [check|update|version]");
        return true;
    }

    // ─────────────────────────────────────────────────
    //  Utilidades privadas
    // ─────────────────────────────────────────────────

    private String fetchRemoteVersion() throws Exception {
        String urlStr = String.format(GITHUB_RAW_BASE,
                GITHUB_REPO_USER, GITHUB_REPO_NAME, GITHUB_VERSION_FILE);
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);

        int code = conn.getResponseCode();
        if (code != 200) {
            // Intentar con la API de releases como fallback
            return fetchFromReleasesAPI();
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            String line = reader.readLine();
            conn.disconnect();
            return line != null ? line.trim() : null;
        }
    }

    private String fetchFromReleasesAPI() throws Exception {
        String urlStr = String.format(GITHUB_RELEASES_API,
                GITHUB_REPO_USER, GITHUB_REPO_NAME);
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);

        int code = conn.getResponseCode();
        if (code != 200) {
            conn.disconnect();
            return null;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            conn.disconnect();
            String json = sb.toString();

            // Extraer el "tag_name" del JSON (formato: "tag_name": "v4.0.1" o "4.0.1")
            int tagIdx = json.indexOf("\"tag_name\"");
            if (tagIdx < 0) return null;
            int valStart = json.indexOf('"', tagIdx + 10);
            if (valStart < 0) return null;
            int valEnd = json.indexOf('"', valStart + 1);
            if (valEnd < 0) return null;
            String tag = json.substring(valStart + 1, valEnd);
            // Quitar el prefijo "v" si existe
            if (tag.startsWith("v")) tag = tag.substring(1);
            return tag;
        }
    }

    /**
     * Compara dos versiones. Retorna -1 si v1 < v2, 0 si iguales, 1 si v1 > v2.
     */
    private int compareVersions(String v1, String v2) {
        String[] p1 = v1.replace("v", "").split("\\.");
        String[] p2 = v2.replace("v", "").split("\\.");
        int len = Math.max(p1.length, p2.length);
        for (int i = 0; i < len; i++) {
            int n1 = i < p1.length ? Integer.parseInt(p1[i].replaceAll("[^0-9]", "0")) : 0;
            int n2 = i < p2.length ? Integer.parseInt(p2[i].replaceAll("[^0-9]", "0")) : 0;
            if (n1 < n2) return -1;
            if (n1 > n2) return 1;
        }
        return 0;
    }
}
