package com.ryxmc.protection;

import com.ryxmc.protection.command.*;
import com.ryxmc.protection.dtr.DTRManager;
import com.ryxmc.protection.energy.EnergyManager;
import com.ryxmc.protection.factory.*;
import com.ryxmc.protection.gui.*;
import com.ryxmc.protection.hologram.HologramManager;
import com.ryxmc.protection.hook.*;
import com.ryxmc.protection.listener.ProtectionListener;
import com.ryxmc.protection.manager.*;
import com.ryxmc.protection.manager.RegionStoreExtension;
import com.ryxmc.protection.dtr.DtrUpgradeManager;
import com.ryxmc.protection.dtr.PlayerDtrRegistry;
import com.ryxmc.protection.war.WarManager;
import com.ryxmc.protection.war.WarListener;
import com.ryxmc.protection.command.WarCommand;
import com.ryxmc.protection.factory.FillerManager;
import com.ryxmc.protection.hook.CommandRestrictor;
import com.ryxmc.protection.factory.TubeManager;
import com.ryxmc.protection.factory.IndustrialFactoryManager;
import com.ryxmc.protection.factory.IndustrialFactoryListener;
import com.ryxmc.protection.factory.IndustrialFactoryGui;
import com.ryxmc.protection.upgrade.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * RyxProtections v4 — Plugin de protecciones para RyxMC (Paper 1.21.4)
 *
 * ── Sistemas ───────────────────────────────────────────────────────────────
 *  RegionStore      — CRUD de regiones WorldGuard
 *  DTRManager       — Sistema de DTR con regeneración y cooldown
 *  UpgradeManager   — Mejoras PP: niveles + sub-upgrades
 *  BlockUpgradeManager — Mejoras admin forzadas
 *  EnergyManager    — Gemas de energía y conversión a PP
 *  FactoryManager   — Fábricas de producción de gemas
 *  HologramManager  — TextDisplay hologramas sobre bloques y fábricas
 *  PreviewManager   — Partículas de radio de región
 *  GuiManager       — Todos los GUIs del plugin
 *  GuiAnimator      — Animaciones de menú (olas, pulsos, entrada, partículas)
 *  FactoryShopGui   — Tienda y mejoras de fábricas
 *
 * ── Hooks ─────────────────────────────────────────────────────────────────
 *  VaultHook     — Economía
 *  TeamHook      — Clanes/Equipos (SimpleClans, BetterTeams, UltimateClans)
 *  PlaceholderAPI — Placeholders para %ryxprotections_dtr%, etc. (opcional)
 *
 * ── Regla de diseño ────────────────────────────────────────────────────────
 *  • Los títulos de GUI NO llevan el prefijo del plugin.
 *  • Todos los mensajes al chat usan Txt.ok/err/warn (que agregan el prefijo).
 *  • Las animaciones de GUI son opcionales por jugador (ryx.gui.animation).
 */
public class RyxProtection extends JavaPlugin {

    private static RyxProtection instance;

    // ── Hooks ──────────────────────────────────────────
    private VaultHook      vaultHook;
    private TeamHook       teamHook;
    private GitHubUpdater  githubUpdater;

    // ── Managers de datos ──────────────────────────────
    private RegionStore             regionStore;
    private RegionStoreExtension    regionStoreExt;
    private DtrUpgradeManager       dtrUpgradeManager;
    private PlayerDtrRegistry       playerDtrRegistry;
    private WarManager              warManager;
    private TubeManager             tubeManager;
    private FillerManager           fillerManager;
    private FactoryAutoUpdater      factoryAutoUpdater;
    private FactoryTransportAnimator transportAnimator;
    private IndustrialFactoryManager industrialFactoryManager;
    private IndustrialFactoryGui    industrialFactoryGui;
    private DTRManager     dtrManager;
    private UpgradeManager upgradeManager;
    private BlockUpgradeManager blockUpgradeManager;
    private EnergyManager  energyManager;
    private FactoryManager factoryManager;
    private GemCompressorManager compressorManager;

    // ── Managers de visualización / UX ─────────────────
    private HologramManager hologramManager;
    private PreviewManager  previewManager;

    // ── GUI ────────────────────────────────────────────
    private GuiManager       guiManager;
    private FactoryShopGui   factoryShopGui;
    private GemCompressorGui compressorGui;

    // ── Listeners con referencias ──────────────────────
    private FactoryListener       factoryListener;
    private GemCompressorListener compressorListener;

    // ── Restricción de comandos ────────────────────────
    private CommandRestrictor commandRestrictor;

    // ─────────────────────────────────────────────────
    //  Lifecycle
    // ─────────────────────────────────────────────────

    @Override
    public void onEnable() {
        instance = this;
        long start = System.currentTimeMillis();

        saveDefaultConfig();

        // 1. Hooks externos
        vaultHook = new VaultHook();
        if (!vaultHook.setup())
            getLogger().warning("[Vault] No encontrado — funciones de economía desactivadas.");

        teamHook = new TeamHook(getLogger());

        // 2. CommandRestrictor (bloqueo de comandos para usuarios default)
        commandRestrictor = new CommandRestrictor(this);
        Bukkit.getPluginManager().registerEvents(commandRestrictor, this);

        // 2b. GitHubUpdater (auto-actualización desde GitHub)
        githubUpdater = new GitHubUpdater(this);
        getCommand("ryx").setExecutor(githubUpdater);
        githubUpdater.checkForUpdate();

        // 3. Managers de datos
        regionStore              = new RegionStore(this);
        regionStoreExt           = new RegionStoreExtension(this);
        dtrUpgradeManager        = new DtrUpgradeManager(this);
        playerDtrRegistry        = new PlayerDtrRegistry(this);
        warManager               = new WarManager(this);
        tubeManager              = new TubeManager(this);
        fillerManager            = new FillerManager(this);
        industrialFactoryManager = new IndustrialFactoryManager(this);
        industrialFactoryGui     = new IndustrialFactoryGui(this);
        dtrManager               = new DTRManager(this);
        upgradeManager      = new UpgradeManager(this);
        blockUpgradeManager = new BlockUpgradeManager(this);
        energyManager       = new EnergyManager(this);
        factoryManager      = new FactoryManager(this);
        compressorManager   = new GemCompressorManager(this);
        factoryAutoUpdater  = new FactoryAutoUpdater(this);
        transportAnimator   = new FactoryTransportAnimator(this);

        // 4. Managers de UX
        hologramManager = new HologramManager(this);
        previewManager  = new PreviewManager(this);

        // 5. GUI
        guiManager     = new GuiManager(this);
        factoryShopGui = new FactoryShopGui(this);
        compressorGui  = new GemCompressorGui(this);

        // 6. Iniciar sistemas (orden importa)
        try {
            regionStore.init();
            regionStoreExt.init();
            dtrUpgradeManager.init();
            playerDtrRegistry.init();
            tubeManager.init();
            fillerManager.init();
            industrialFactoryManager.init();
            industrialFactoryGui.init();
            dtrManager.init();
            upgradeManager.init();
            factoryManager.init();
            compressorManager.init();
            factoryAutoUpdater.init();
            transportAnimator.init();
            dtrManager.recalcAllMax();
            warManager.init();
            hologramManager.init();
            previewManager.init();
            Bukkit.getPluginManager().registerEvents(new WarListener(this), this);
            Bukkit.getPluginManager().registerEvents(new IndustrialFactoryListener(this), this);
            Bukkit.getPluginManager().registerEvents(industrialFactoryGui, this);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error al iniciar sistemas:", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 7. Comandos (antes de listeners para que estén disponibles si listeners fallan)
        registerCommands();

        // 8. Listeners
        factoryListener    = new FactoryListener(this);
        compressorListener = new GemCompressorListener(this, compressorGui);
        var pm = getServer().getPluginManager();
        pm.registerEvents(factoryListener,                    this);
        pm.registerEvents(compressorListener,                 this);
        pm.registerEvents(new GuiListener(this, guiManager),  this);
        pm.registerEvents(new ProtectionListener(this),       this);

        long elapsed = System.currentTimeMillis() - start;
        getLogger().info("[RyxMC-Protections] v4.0.1 activado en " + elapsed + "ms."
                + " Regiones: " + regionStore.getAll().size()
                + " | Fábricas: " + factoryManager.getAllFactories().size()
                + " | Compresores: " + compressorManager.getAll().size());
    }

    @Override
    public void onDisable() {
        if (warManager               != null) warManager.shutdown();
        if (industrialFactoryGui     != null) industrialFactoryGui.shutdown();
        if (industrialFactoryManager != null) industrialFactoryManager.shutdown();
        if (fillerManager            != null) fillerManager.shutdown();
        if (tubeManager               != null) tubeManager.shutdown();
        if (playerDtrRegistry        != null) playerDtrRegistry.shutdown();
        if (dtrUpgradeManager        != null) dtrUpgradeManager.shutdown();
        if (regionStoreExt           != null) regionStoreExt.shutdown();
        if (guiManager               != null) guiManager.shutdown();
        if (dtrManager               != null) dtrManager.shutdown();
        if (upgradeManager    != null) upgradeManager.shutdown();
        if (regionStore       != null) regionStore.shutdown();
        if (factoryManager    != null) factoryManager.shutdown();
        if (compressorManager != null) compressorManager.shutdown();
        if (factoryAutoUpdater != null) factoryAutoUpdater.shutdown();
        if (transportAnimator  != null) transportAnimator.shutdown();
        if (hologramManager   != null) hologramManager.shutdown();
        if (previewManager    != null) previewManager.shutdown();
        getLogger().info("[RyxMC-Protections] v4.0.0 desactivado correctamente.");
        instance = null;
    }

    // ─────────────────────────────────────────────────
    //  Comandos
    // ─────────────────────────────────────────────────

    private void registerCommands() {
        reg("ps",      new PsCommand(this));
        reg("dtr",     new DtrCommand(this));
        reg("war",     new WarCommand(this));
        reg("factory", new FactoryCommand(this));
        reg("pp",      new PpCommand(this));
    }

    private void reg(String name, Object executor) {
        var cmd = getCommand(name);
        if (cmd == null) { getLogger().warning("Comando '" + name + "' no registrado en plugin.yml"); return; }
        if (executor instanceof org.bukkit.command.CommandExecutor ce) cmd.setExecutor(ce);
        if (executor instanceof org.bukkit.command.TabCompleter tc)   cmd.setTabCompleter(tc);
    }

    // ─────────────────────────────────────────────────
    //  Getters
    // ─────────────────────────────────────────────────

    public static RyxProtection getInstance()            { return instance; }
    public VaultHook      getVaultHook()                 { return vaultHook; }
    public TeamHook       getTeamHook()                  { return teamHook; }
    public GitHubUpdater  getGitHubUpdater()             { return githubUpdater; }
    /**
     * @deprecated Sistema BlockCfg/BlockLoader eliminado en v2.3.
     *   Usa ProtectionBlock static methods en su lugar.
     *   Este método retorna null — cualquier llamada lanzará NPE intencionalmente
     *   para que quede visible en los logs durante la migración.
     */
    @Deprecated
    public com.ryxmc.protection.config.BlockLoader getBlockLoader() {
        getLogger().warning("[DEPRECATED] getBlockLoader() llamado — usa ProtectionBlock.*");
        return null;
    }
    public RegionStore              getRegionStore()              { return regionStore; }
    public RegionStoreExtension     getRegionStoreExt()           { return regionStoreExt; }
    public DtrUpgradeManager        getDtrUpgradeManager()        { return dtrUpgradeManager; }
    public PlayerDtrRegistry        getPlayerDtrRegistry()        { return playerDtrRegistry; }
    public WarManager               getWarManager()               { return warManager; }
    public TubeManager              getTubeManager()              { return tubeManager; }
    public FillerManager            getFillerManager()            { return fillerManager; }
    public IndustrialFactoryManager getIndustrialFactoryManager() { return industrialFactoryManager; }
    public IndustrialFactoryGui     getIndustrialFactoryGui()     { return industrialFactoryGui; }
    public DTRManager     getDtrManager()                { return dtrManager; }
    public UpgradeManager getUpgradeManager()            { return upgradeManager; }
    public BlockUpgradeManager getBlockUpgradeManager()  { return blockUpgradeManager; }
    public EnergyManager  getEnergyManager()             { return energyManager; }
    public FactoryManager getFactoryManager()            { return factoryManager; }
    public FactoryAutoUpdater getFactoryAutoUpdater()    { return factoryAutoUpdater; }
    public FactoryTransportAnimator getTransportAnimator() { return transportAnimator; }
    public HologramManager getHologramManager()          { return hologramManager; }
    public PreviewManager  getPreviewManager()           { return previewManager; }
    public GuiManager     getGuiManager()                { return guiManager; }
    public FactoryShopGui    getFactoryShopGui()          { return factoryShopGui; }
    public FactoryListener   getFactoryListener()         { return factoryListener; }
    public GemCompressorManager getCompressorManager()   { return compressorManager; }
    public GemCompressorGui  getCompressorGui()           { return compressorGui; }
}
