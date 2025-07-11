package me.twojanazwa.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
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

        // Sprawdź czy gracz jest w pobliżu działki (promień 10 bloków)
        ProtectedRegion nearbyRegion = dzialkaCommand.getNearbyRegion(to, 10);
        ProtectedRegion previousNearbyRegion = dzialkaCommand.getNearbyRegion(from, 10);

        // === OBSŁUGA WEJŚCIA/WYJŚCIA Z DZIAŁKI ===
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

                // OBSŁUGA CZASU - ustawia czas działki dla gracza
                long t = currentRegion.isDay ? 1000L : 13000L;
                player.setPlayerTime(t, false);
            } else {
                // Gracz opuścił działkę
                player.sendMessage("§cOpuszczasz działkę.");
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent("§cOpuszczasz działkę."));

                // OBSŁUGA CZASU - przywraca serwerowy czas graczowi
                player.resetPlayerTime();
            }
        }

        // === OBSŁUGA CZĄSTECZEK DLA POBLISKICH DZIAŁEK ===
        if (!regionsAreEqual(nearbyRegion, previousNearbyRegion)) {
            // Zatrzymaj poprzednie cząsteczki jeśli się oddala lub zmienia działkę
            if (previousNearbyRegion != null && !regionsAreEqual(nearbyRegion, previousNearbyRegion)) {
                dzialkaCommand.stopParticles(previousNearbyRegion);
            }

            // Uruchom nowe cząsteczki jeśli się zbliża do nowej działki
            if (nearbyRegion != null && !regionsAreEqual(nearbyRegion, previousNearbyRegion)) {
                // Tylko jeśli gracz NIE jest już na działce (żeby nie duplikować wiadomości)
                if (currentRegion == null) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            new TextComponent("§7Zbliżasz się do działki: §e" + nearbyRegion.plotName));
                }
                dzialkaCommand.scheduleBoundaryParticles(nearbyRegion, player);
            }
        }

        // === DODATKOWA OBSŁUGA: POKAZUJ CZĄSTECZKI PO WYJŚCIU Z DZIAŁKI ===
        // Jeśli gracz właśnie wyszedł z działki, ale nadal jest w pobliżu - pokaż cząsteczki
        if (currentRegion == null && previousRegion != null && nearbyRegion != null
                && regionsAreEqual(nearbyRegion, previousRegion)) {
            // Gracz właśnie wyszedł z działki, ale nadal jest w pobliżu - pokaż granice
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent("§7Opuściłeś działkę: §e" + previousRegion.plotName + " §7(granice widoczne)"));
            dzialkaCommand.scheduleBoundaryParticles(previousRegion, player);
        }

        // === ZAWSZE AKTUALIZUJ BOSSBAR ===
        // Zaktualizuj BossBar niezależnie od tego, czy gracz jest na działce czy w pobliżu
        dzialkaCommand.updatePlayerBossBar(player);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Zaktualizuj BossBar po dołączeniu gracza
        Bukkit.getScheduler().runTaskLater(dzialkaCommand.getPlugin(), () -> {
            dzialkaCommand.updatePlayerBossBar(player);
        }, 5L); // Opóźnienie 5 ticków, aby gracz był w pełni załadowany
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

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        Location explodeLocation = event.getLocation();
        ProtectedRegion region = dzialkaCommand.getRegion(explodeLocation);

        if (region != null && !region.mobGriefing) {
            // Zabloń eksplozję jeśli mobGriefing jest wyłączone
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Location blockLocation = event.getBlock().getLocation();
        ProtectedRegion region = dzialkaCommand.getRegion(blockLocation);

        if (region != null && !region.mobGriefing) {
            // Zabloń zmienianie bloków przez moby jeśli mobGriefing jest wyłączone
            event.setCancelled(true);
        }
    }
}
