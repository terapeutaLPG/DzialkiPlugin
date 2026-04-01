package me.twojanazwa;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import me.twojanazwa.commands.DzialkaCommand;
import me.twojanazwa.listeners.DzialkaPvPListener;
import net.milkbowl.vault.economy.Economy;

public class App extends JavaPlugin {

    private DzialkaCommand dzialkaCommand;
    private Economy economy;

    @Override
    public void onEnable() {
        if (setupEconomy()) {
            getLogger().info("Wykryto ekonomie Vault: " + economy.getName());
        } else {
            getLogger().warning("Nie wykryto Vault/economy provider. Kupno dzialek na rynku bedzie niedostepne.");
        }

        dzialkaCommand = new DzialkaCommand(this);

        if (getCommand("dzialka") != null) {
            getCommand("dzialka").setExecutor(dzialkaCommand);
            getCommand("dzialka").setTabCompleter(dzialkaCommand);
        } else {
            getLogger().warning("Command 'dzialka' is not defined in plugin.yml!");
        }
        Bukkit.getPluginManager().registerEvents(dzialkaCommand, this);
        Bukkit.getPluginManager().registerEvents(
                new DzialkaPvPListener(dzialkaCommand),
                this
        );

        dzialkaCommand.loadPlots();

        // Automatyczne zapisywanie co 5 minut (6000 ticków)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (dzialkaCommand != null) {
                dzialkaCommand.savePlots();
                getLogger().info("Automatyczny zapis działek wykonany.");
            }
        }, 6000L, 6000L);

        getLogger().info("Plugin dzialkiplugin został włączony!");
    }

    @Override
    public void onDisable() {
        if (dzialkaCommand != null) {
            dzialkaCommand.savePlots();
        }
        getLogger().info("Plugin dzialkiplugin został wyłączony!");
    }

    public DzialkaCommand getDzialkaCommand() {
        return dzialkaCommand;
    }

    public Economy getEconomy() {
        return economy;
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> registration = getServer()
                .getServicesManager()
                .getRegistration(Economy.class);
        if (registration == null) {
            return false;
        }

        economy = registration.getProvider();
        return economy != null;
    }
}
