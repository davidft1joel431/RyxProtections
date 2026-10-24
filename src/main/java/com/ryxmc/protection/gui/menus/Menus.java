package com.ryxmc.protection.gui.menus;

import com.ryxmc.protection.RyxProtection;
import com.ryxmc.protection.gui.GuiManager;

/** Menus v3.1 — + WarMenu + ProtectionOptionsMenu */
public class Menus {
    public final InfoMenu              info;
    public final SettingsMenu          settings;
    public final MembersMenu           members;
    public final EnergyMenu            energy;
    public final UpgradeMenu           upgrade;
    public final ShopMenu              shop;
    public final AdminMenu             admin;
    public final ConfirmMenu           confirm;
    public final ProtectionOptionsMenu protectionOptions;
    public final WarMenu               war;

    public Menus(RyxProtection plugin, GuiManager gui) {
        this.info              = new InfoMenu             (plugin, gui, this);
        this.settings          = new SettingsMenu         (plugin, gui, this);
        this.members           = new MembersMenu          (plugin, gui, this);
        this.energy            = new EnergyMenu           (plugin, gui, this);
        this.upgrade           = new UpgradeMenu          (plugin, gui, this);
        this.shop              = new ShopMenu             (plugin, gui, this);
        this.admin             = new AdminMenu            (plugin, gui, this);
        this.confirm           = new ConfirmMenu          (plugin, gui, this);
        this.protectionOptions = new ProtectionOptionsMenu(plugin, gui, this);
        this.war               = new WarMenu              (plugin, gui, this);
    }
}
