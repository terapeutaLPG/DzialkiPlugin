package me.twojanazwa.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import me.twojanazwa.commands.DzialkaCommand;
import me.twojanazwa.commands.DzialkaCommand.ProtectedRegion;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
//elo

public class DzialkaPvPListener implements Listener {

    private final DzialkaCommand dzialkaCommand;

    public DzialkaPvPListener(DzialkaCommand dzialkaCommand) {
        this.dzialkaCommand = dzialkaCommand;
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player damager)) {
            return;
        }
        if (dzialkaCommand.isInAnyPlot(victim) && dzialkaCommand.isInAnyPlot(damager)) {
            event.setCancelled(true);
            damager.sendMessage("§cPvP jest zabronione na działkach!");
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ProtectedRegion region = dzialkaCommand.getRegion(player.getLocation());
        if (region != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Jeśli gracz nie zmienił bloku, pomijamy zdarzenie
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        ProtectedRegion currentRegion = dzialkaCommand.getRegion(to);
        ProtectedRegion previousRegion = dzialkaCommand.getRegion(from);

        if (!regionsAreEqual(currentRegion, previousRegion)) {
            Bukkit.getLogger().info(String.format("[DzialkaPvPListener] Zmiana regionu gracza %s: poprzedni = %s, aktualny = %s",
                    player.getName(),
                    previousRegion != null ? previousRegion.plotName : "brak",
                    currentRegion != null ? currentRegion.plotName : "brak"));
            if (currentRegion != null) {
                // Gracz wszedł na działkę
                player.sendMessage("§aZnajdujesz się na działce: §e" + currentRegion.plotName);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent("§aWchodzisz na działkę: §e" + currentRegion.plotName));
                dzialkaCommand.showBossBar(currentRegion, player);
                dzialkaCommand.scheduleBoundaryParticles(currentRegion, player);
            } else {
                // Gracz opuścił działkę
                player.sendMessage("§cOpuszczasz działkę.");
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent("§cOpuszczasz działkę."));
                BossBar bossBar = dzialkaCommand.getBossBar(player);
                if (bossBar != null) {
                    bossBar.setVisible(false);
                }
                if (previousRegion != null) {
                    dzialkaCommand.stopParticles(previousRegion);
                }
            }
        }
    }

    // Pomocnicza metoda porównująca regiony
    private boolean regionsAreEqual(ProtectedRegion r1, ProtectedRegion r2) {
        if (r1 == r2) {
            return true;
        }
        if (r1 == null || r2 == null) {
            return false;
        }
        // Użyj bezpiecznego porównania nazw
        return r1.plotName != null && r2.plotName != null && r1.plotName.equalsIgnoreCase(r2.plotName);
        // lub jeśli chcesz użyć static z DzialkaCommand:
        // return me.twojanazwa.commands.DzialkaCommand.samePlotName(r1.plotName, r2.plotName);
    }
}
