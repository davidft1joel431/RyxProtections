package com.ryxmc.protection.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Hook con Vault Economy */
public class VaultHook {

    private Economy eco;
    private boolean available = false;

    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        eco       = rsp.getProvider();
        available = eco != null;
        return available;
    }

    public boolean isAvailable()               { return available; }
    public double  getBalance(Player p)        { return available ? eco.getBalance(p) : 0; }
    public boolean has(Player p, double amount){ return available && eco.has(p, amount); }
    public boolean withdraw(Player p, double a){ return available && eco.withdrawPlayer(p, a).transactionSuccess(); }
    public boolean deposit(Player p, double a) { return available && eco.depositPlayer(p, a).transactionSuccess(); }
    public String  format(double amount)       { return available ? eco.format(amount) : "$" + amount; }
}
