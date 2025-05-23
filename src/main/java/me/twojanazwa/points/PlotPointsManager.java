package me.twojanazwa.points;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import me.twojanazwa.commands.DzialkaCommand;
import me.twojanazwa.commands.DzialkaCommand.ProtectedRegion;

public class PlotPointsManager {

    /**
     * Przydziela punkty za postawienie bloku. Zwraca liczbę punktów, którą
     * dodano (możesz jej użyć np. do wiadomości).
     */
    public static int handleBlockPlace(DzialkaCommand plugin,
            ProtectedRegion region,
            Player player,
            Material blockType) {
        int points = switch (blockType) {
            case DIRT ->
                1;
            case STONE ->
                2;
            case DIAMOND_BLOCK ->
                10;
            default ->
                0;
        };

        if (points > 0) {
            region.points += points;
            plugin.savePlots();
            return points;
        }
        return 0;
    }

    /**
     * Przydziela punkty za interakcję (klika zaproszony gracz). Zwraca zawsze 1
     * punkt, jeśli gracz jest zaproszony; inaczej 0.
     */
    public static int handlePlayerInteract(DzialkaCommand plugin,
            ProtectedRegion region,
            Player player) {
        // tylko zaproszony, nie właściciel
        if (!region.owner.equals(player.getName())
                && region.invitedPlayers.contains(player.getUniqueId())) {
            region.points += 1;
            plugin.savePlots();
            return 1;
        }
        return 0;
    }
}
