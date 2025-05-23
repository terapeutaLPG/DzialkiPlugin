package me.twojanazwa;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import me.twojanazwa.commands.DzialkaCommand;
import me.twojanazwa.listeners.DzialkaPvPListener; // zmieniono z DzialkaPvListener

public class Main extends JavaPlugin {

    private DzialkaCommand dzialkaCommand;

    @Override
    public void onEnable() {
        dzialkaCommand = new DzialkaCommand(this);
        if (getCommand("dzialka") != null) {
            var dzialkaCommandInstance = getCommand("dzialka");
            if (dzialkaCommandInstance != null) {
                dzialkaCommandInstance.setExecutor(dzialkaCommand);
                dzialkaCommandInstance.setTabCompleter(dzialkaCommand);
            }
        }
        Bukkit.getPluginManager().registerEvents(new DzialkaPvPListener(dzialkaCommand), this);
        Bukkit.getPluginManager().registerEvents(dzialkaCommand, this);
        dzialkaCommand.loadPlots();
    }

    @Override
    public void onDisable() {
        if (dzialkaCommand != null) {
            dzialkaCommand.savePlots();
        }
    }
}
