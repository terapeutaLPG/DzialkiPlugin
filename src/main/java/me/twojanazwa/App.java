package me.twojanazwa;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import me.twojanazwa.commands.DzialkaCommand;
import me.twojanazwa.listeners.DzialkaPvPListener;

public class App extends JavaPlugin {

    private DzialkaCommand dzialkaCommand;

    @Override
    public void onEnable() {
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
}
