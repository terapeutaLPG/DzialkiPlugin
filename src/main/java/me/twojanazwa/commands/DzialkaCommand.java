package me.twojanazwa.commands;

/**
 * Plugin Działek WERSJA 2.0
 *
 * Autor: jaruso99
 *
 * Zaawansowany system zarządzania działkami z GUI i wizualizacją granic przez
 * cząsteczki. Funkcje: tworzenie działek, zarządzanie uprawnieniami, system
 * punktów, rynek działek.
 *
 * @author jaruso99
 * @version 2.0
 */
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
// Suppress spell-checking warnings for specific words
// noinspection SpellCheckingInspection

public class DzialkaCommand implements CommandExecutor, Listener, TabCompleter {

    private final JavaPlugin plugin;
    // Pierwsza deklaracja – pozostawiamy tylko tę
    private final Map<UUID, List<ProtectedRegion>> dzialki = new HashMap<>();
    private final Map<UUID, BossBar> bossBary = new HashMap<>();
    private final Map<UUID, String> pendingDeletions = new HashMap<>();

    public DzialkaCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        // Uruchom task aktywności punktów
        startActivityPointsTask();
    }

    /**
     * Bezpieczne porównanie nazw działek (ignoruje null-e)
     */
    private static boolean samePlotName(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        try {
            plugin.getLogger().info("DzialkaCommand.onCommand wywołane przez "
                    + sender.getName() + ", args=" + Arrays.toString(args));

            if (!(sender instanceof Player)) {
                sender.sendMessage("§cTylko gracz może używać tej komendy!");
                return true;
            }
            Player gracz = (Player) sender;

            if (args.length == 0) {
                gracz.sendMessage("§eUżycie komend:");
                gracz.sendMessage(" §7/dzialka stworz <nazwa> §8- §fTworzy nową działkę");
                gracz.sendMessage(" §7/dzialka usun <nazwa> §8- §fUsuwa działkę");
                gracz.sendMessage(" §7/dzialka tp <nazwa> §8- §fTeleportuje na działkę");
                gracz.sendMessage(" §7/dzialka lista §8- §fWyświetla listę działek");
                gracz.sendMessage(" §7/dzialka panel <nazwa> §8- §fOtwiera panel GUI");
                gracz.sendMessage(" §7/dzialka warp <nazwa> §8- §fTeleportuje na warp działki");
                gracz.sendMessage(" §7/dzialka stworzwarp <nazwa> §8- §fUstawia warp działki");
                gracz.sendMessage(" §7/dzialka zapros <nazwa> <nick> §8- §fZaprasza gracza na działkę");
                gracz.sendMessage(" §7/dzialka opusc <nazwa> §8- §fGracz opuszcza działkę");
                gracz.sendMessage(" §7/dzialka zastepca <nazwa> <nick> §8- §fUstawia zastępcę działki");
                gracz.sendMessage(" §7/działka admintp <nazwa> §8- §fAdmin teleportuje na działkę");
                gracz.sendMessage(" §7/działka adminusun <nazwa> §8- §fAdmin usuwa działkę");
                gracz.sendMessage(" §7/dzialka test §8- §fTworzy testową działkę do debugowania");
                gracz.sendMessage(" §7/dzialka debug §8- §fPokazuje informacje debugowania");
                return true;
            }

            String sub = args[0].toLowerCase();
            switch (sub) {
                case "stworz" -> {
                    if (!gracz.hasPermission("dzialkiplugin.stworz")) {
                        gracz.sendMessage("§cNie masz uprawnień do tej komendy.");
                        return true;
                    }
                    if (args.length < 2) {
                        gracz.sendMessage("§cMusisz podać nazwę działki! Użycie: /dzialka stworz <nazwa>");
                        return true;
                    }
                    String plotName = args[1];
                    List<ProtectedRegion> playerPlots = dzialki.computeIfAbsent(gracz.getUniqueId(), k -> new ArrayList<>());

                    if (playerPlots.size() >= 3) {
                        gracz.sendMessage("§cMożesz posiadać maksymalnie 3 działki!");
                        return true;
                    }

                    // 3) Sprawdź unikalność nazwy (pomijamy regiony bez nazwy)
                    for (List<ProtectedRegion> allList : dzialki.values()) {
                        for (ProtectedRegion r : allList) {
                            if (samePlotName(r.plotName, plotName)) {
                                gracz.sendMessage("§cDziałka o nazwie '" + plotName + "' już istnieje!");
                                return true;
                            }
                        }
                    }

                    Location center = gracz.getLocation();
                    int half = 50;
                    ProtectedRegion newRegion = new ProtectedRegion(
                            center.getBlockX() - half, center.getBlockX() + half,
                            center.getBlockZ() - half, center.getBlockZ() + half,
                            gracz.getWorld().getMinHeight(), gracz.getWorld().getMaxHeight(),
                            center, gracz.getName(), plotName, System.currentTimeMillis()
                    );
                    if (isColliding(newRegion)) {
                        gracz.sendMessage("§cNie można stworzyć działki – koliduje z inną.");
                        return true;
                    }

                    playerPlots.add(newRegion);
                    savePlots();

                    gracz.sendMessage("§aDziałka '" + plotName + "' została stworzona!");
                    scheduleBoundaryParticles(newRegion, gracz);
                    Bukkit.broadcastMessage("§6[§eDziałki§6] Gracz §b" + gracz.getName()
                            + " §astworzył działkę §e" + plotName + "§a!");
                    return true;
                }
                case "top" -> {
                    openTopPanel(gracz, 1);
                    return true;
                }
                case "usun" -> {
                    if (args.length < 2) {
                        gracz.sendMessage("§cMusisz podać nazwę działki! Użycie: /dzialka usun <nazwa>");
                        return true;
                    }
                    String plotName = args[1];
                    List<ProtectedRegion> playerPlots = dzialki.getOrDefault(gracz.getUniqueId(), Collections.emptyList());

                    ProtectedRegion toRemove = null;
                    for (ProtectedRegion r : playerPlots) {
                        if (samePlotName(r.plotName, plotName) && r.owner.equals(gracz.getName())) {
                            toRemove = r;
                            break;
                        }
                    }
                    if (toRemove == null) {
                        gracz.sendMessage("§cNie znaleziono Twojej działki o nazwie '" + plotName + "'.");
                        return true;
                    }

                    if (!pendingDeletions.containsKey(gracz.getUniqueId())) {
                        pendingDeletions.put(gracz.getUniqueId(), plotName);
                        gracz.sendMessage("§ePotwierdź usunięcie: wpisz ponownie §c/dzialka usun "
                                + plotName + " §ew ciągu 30s.");
                        Bukkit.getScheduler().runTaskLater(plugin,
                                () -> pendingDeletions.remove(gracz.getUniqueId()), 600L);
                        return true;
                    }
                    if (!pendingDeletions.get(gracz.getUniqueId()).equals(plotName)) {
                        gracz.sendMessage("§cNie masz oczekującego potwierdzenia dla tej działki.");
                        return true;
                    }

                    pendingDeletions.remove(gracz.getUniqueId());
                    stopParticles(toRemove);
                    BossBar bar = bossBary.remove(gracz.getUniqueId());
                    if (bar != null) {
                        bar.setVisible(false);
                        bar.removeAll();
                    }
                    playerPlots.remove(toRemove);
                    if (playerPlots.isEmpty()) {
                        dzialki.remove(gracz.getUniqueId());
                    }
                    savePlots();
                    gracz.sendMessage("§aDziałka '" + plotName + "' została usunięta.");
                    return true;
                }
                case "tp" -> {
                    if (args.length < 2) {
                        gracz.sendMessage("§cUżycie: /dzialka tp <nazwa>");
                        return true;
                    }
                    String nazwa = args[1];
                    List<ProtectedRegion> playerPlots = dzialki.getOrDefault(gracz.getUniqueId(), Collections.emptyList());
                    ProtectedRegion target = playerPlots.stream()
                            .filter(r -> samePlotName(r.plotName, nazwa))
                            .findFirst().orElse(null);
                    if (target == null) {
                        gracz.sendMessage("§cNie masz działki o nazwie '" + nazwa + "'.");
                        return true;
                    }
                    gracz.teleport(target.center.clone().add(0.5, 1, 0.5));
                    gracz.sendMessage("§aTeleportowano na działkę '" + nazwa + "'.");
                    return true;
                }
                case "lista" -> {
                    plugin.getLogger().info("Komenda 'lista' wywołana przez gracza: " + gracz.getUniqueId());
                    plugin.getLogger().info("Aktualna zawartość mapy dzialki: " + dzialki.size() + " graczy");

                    List<ProtectedRegion> playerPlots = dzialki.getOrDefault(gracz.getUniqueId(), Collections.emptyList());
                    plugin.getLogger().info("Działki dla gracza " + gracz.getUniqueId() + ": " + playerPlots.size());

                    if (playerPlots.isEmpty()) {
                        gracz.sendMessage("§cNie posiadasz żadnej działki.");
                        plugin.getLogger().info("Gracz " + gracz.getUniqueId() + " nie ma żadnych działek.");
                        return true;
                    }
                    gracz.sendMessage("§aTwoje działki:");
                    for (ProtectedRegion r : playerPlots) {
                        gracz.sendMessage(" §e" + r.plotName);
                        plugin.getLogger().info("Wyświetlam działkę: " + r.plotName + " dla gracza " + gracz.getUniqueId());
                    }
                    return true;
                }
                case "panel" -> {
                    if (args.length < 2) {
                        gracz.sendMessage("§cUżycie: /dzialka panel <nazwa>");
                        return true;
                    }
                    String nazwa = args[1];
                    List<ProtectedRegion> playerPlots = dzialki.getOrDefault(gracz.getUniqueId(), Collections.emptyList());
                    ProtectedRegion r = playerPlots.stream()
                            .filter(p -> samePlotName(p.plotName, nazwa))
                            .findFirst().orElse(null);
                    if (r == null) {
                        gracz.sendMessage("§cNie masz działki o nazwie '" + nazwa + "'.");
                        return true;
                    }
                    openPanel(r, gracz);
                    return true;
                } //3
                case "warp" -> {
                    if (args.length < 2) {
                        gracz.sendMessage("§cUżycie: /dzialka warp <nazwa>");
                        return true;
                    }
                    String nazwa = args[1];
                    List<ProtectedRegion> playerPlots = dzialki.getOrDefault(gracz.getUniqueId(), Collections.emptyList());
                    ProtectedRegion r = playerPlots.stream()
                            .filter(p -> samePlotName(p.plotName, nazwa))
                            .findFirst().orElse(null);
                    if (r == null || r.warp == null) {
                        gracz.sendMessage("§cBrak ustawionego warpu dla działki '" + nazwa + "'.");
                        return true;
                    }
                    gracz.teleport(r.warp);
                    gracz.sendMessage("§aTeleportowano do warpu działki '" + nazwa + "'.");
                    return true;
                }
                case "stworzwarp" -> {
                    if (args.length < 2) {
                        gracz.sendMessage("§cUżycie: /dzialka stworzwarp <nazwa>");
                        return true;
                    }
                    String nazwa = args[1];
                    List<ProtectedRegion> playerPlots = dzialki.getOrDefault(gracz.getUniqueId(), Collections.emptyList());

                    ProtectedRegion r = playerPlots.stream()
                            .filter(p -> samePlotName(p.plotName, nazwa))
                            .findFirst().orElse(null);
                    if (r == null) {
                        gracz.sendMessage("§cNie masz działki o nazwie '" + nazwa + "'.");
                        return true;
                    }

                    // *** TU NOWA SPRAWDZENIE LOKALIZACJI ***
                    Location loc = gracz.getLocation();
                    if (!r.contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
                        gracz.sendMessage("§cMusisz być na terenie działki '" + nazwa + "', aby ustawić warp!");
                        return true;
                    }

                    // ustawiamy warp tylko jeśli gracz wewnątrz działki
                    r.warp = loc;

                    // Dodaj punkty za ustawienie warpu
                    addWarpPoints(r, gracz.getName());

                    savePlots();
                    gracz.sendMessage("§aWarp ustawiony dla działki '" + nazwa + "'.");
                    return true;
                }

                case "zapros" -> {
                    if (args.length < 3) {
                        gracz.sendMessage("§cUżycie: /dzialka zapros <nazwa> <nick>");
                        return true;
                    }
                    String nazwa = args[1], nick = args[2];

                    // Sprawdź czy gracz nie próbuje zaprosić samego siebie
                    if (gracz.getName().equalsIgnoreCase(nick)) {
                        gracz.sendMessage("§cNie możesz zaprosić samego siebie na swoją działkę!");
                        return true;
                    }

                    List<ProtectedRegion> playerPlots = dzialki.getOrDefault(gracz.getUniqueId(), Collections.emptyList());
                    ProtectedRegion r = playerPlots.stream()
                            .filter(p -> samePlotName(p.plotName, nazwa))
                            .findFirst().orElse(null);
                    if (r == null) {
                        gracz.sendMessage("§cNie masz działki o nazwie '" + nazwa + "'.");
                        return true;
                    }
                    Player invited = Bukkit.getPlayer(nick);
                    if (invited == null || !invited.isOnline()) {
                        gracz.sendMessage("§cGracz '" + nick + "' nie jest online.");
                        return true;
                    }

                    // Sprawdź czy gracz już jest zaproszony
                    if (r.invitedPlayers.contains(invited.getUniqueId())) {
                        gracz.sendMessage("§cGracz '" + nick + "' już jest członkiem tej działki!");
                        return true;
                    }

                    r.invitedPlayers.add(invited.getUniqueId());

                    // Dodaj punkty za zaproszenie gracza
                    addInvitePoints(r, gracz.getName(), nick);

                    savePlots();
                    invited.sendMessage("§aZostałeś zaproszony na działkę '" + nazwa + "'.");
                    gracz.sendMessage("§aZaproszono '" + nick + "' na działkę '" + nazwa + "'.");
                    return true;
                }
                case "opusc" -> {
                    if (args.length < 2) {
                        gracz.sendMessage("§cUżycie: /dzialka opusc <nazwa>");
                        return true;
                    }
                    String nazwa = args[1];
                    List<ProtectedRegion> playerPlots = dzialki.getOrDefault(gracz.getUniqueId(), Collections.emptyList());
                    ProtectedRegion r = playerPlots.stream()
                            .filter(p -> samePlotName(p.plotName, nazwa))
                            .findFirst().orElse(null);
                    if (r == null || !r.invitedPlayers.contains(gracz.getUniqueId())) {
                        gracz.sendMessage("§cNie jesteś zaproszony na działkę '" + nazwa + "'.");
                        return true;
                    }
                    r.invitedPlayers.remove(gracz.getUniqueId());
                    savePlots();
                    gracz.sendMessage("§aOpuściłeś działkę '" + nazwa + "'.");
                    return true;
                }
                case "dolacz" -> {
                    if (args.length < 2) {
                        gracz.sendMessage("§cUżycie: /dzialka dolacz <nazwa>");
                        return true;
                    }
                    String nazwa = args[1];

                    // Znajdź działkę o podanej nazwie
                    ProtectedRegion targetRegion = null;
                    Player owner = null;

                    for (Map.Entry<UUID, List<ProtectedRegion>> entry : dzialki.entrySet()) {
                        for (ProtectedRegion r : entry.getValue()) {
                            if (samePlotName(r.plotName, nazwa)) {
                                targetRegion = r;
                                owner = Bukkit.getPlayer(entry.getKey());
                                if (owner == null) {
                                    // Właściciel offline, pobierz offline player
                                    OfflinePlayer offlineOwner = Bukkit.getOfflinePlayer(entry.getKey());
                                    if (offlineOwner.hasPlayedBefore()) {
                                        // Sprawdź czy gracz już jest członkiem
                                        if (r.owner.equals(gracz.getName())) {
                                            gracz.sendMessage("§cTo jest Twoja własna działka!");
                                            return true;
                                        }
                                        if (r.invitedPlayers.contains(gracz.getUniqueId())) {
                                            gracz.sendMessage("§cJuż jesteś członkiem działki '" + nazwa + "'!");
                                            return true;
                                        }

                                        // Dodaj gracza do listy zaproszonych
                                        r.invitedPlayers.add(gracz.getUniqueId());
                                        savePlots();
                                        gracz.sendMessage("§aDołączyłeś do działki '" + nazwa + "'!");

                                        // Powiadom właściciela jeśli jest online
                                        Player onlineOwner = Bukkit.getPlayer(r.owner);
                                        if (onlineOwner != null && onlineOwner.isOnline()) {
                                            onlineOwner.sendMessage("§aGracz '" + gracz.getName() + "' dołączył do Twojej działki '" + nazwa + "'!");
                                        }
                                        return true;
                                    }
                                }
                                break;
                            }
                        }
                        if (targetRegion != null) {
                            break;
                        }
                    }

                    if (targetRegion == null) {
                        gracz.sendMessage("§cNie znaleziono działki o nazwie '" + nazwa + "'!");
                        return true;
                    }

                    // Sprawdź czy gracz już jest członkiem
                    if (targetRegion.owner.equals(gracz.getName())) {
                        gracz.sendMessage("§cTo jest Twoja własna działka!");
                        return true;
                    }
                    if (targetRegion.invitedPlayers.contains(gracz.getUniqueId())) {
                        gracz.sendMessage("§cJuż jesteś członkiem działki '" + nazwa + "'!");
                        return true;
                    }

                    // Dodaj gracza do listy zaproszonych
                    targetRegion.invitedPlayers.add(gracz.getUniqueId());
                    savePlots();
                    gracz.sendMessage("§aDołączyłeś do działki '" + nazwa + "'!");

                    // Powiadom właściciela jeśli jest online
                    if (owner != null && owner.isOnline()) {
                        owner.sendMessage("§aGracz '" + gracz.getName() + "' dołączył do Twojej działki '" + nazwa + "'!");
                    } else {
                        // Właściciel offline, spróbuj znaleźć go po nazwie
                        Player onlineOwner = Bukkit.getPlayer(targetRegion.owner);
                        if (onlineOwner != null && onlineOwner.isOnline()) {
                            onlineOwner.sendMessage("§aGracz '" + gracz.getName() + "' dołączył do Twojej działki '" + nazwa + "'!");
                        }
                    }
                    return true;
                }
                case "oplac" -> {
                    if (args.length < 2) {
                        gracz.sendMessage("§cUżycie: /dzialka oplac <nazwa>");
                        return true;
                    }
                    String nazwa = args[1];
                    List<ProtectedRegion> playerPlots = dzialki.getOrDefault(gracz.getUniqueId(), Collections.emptyList());
                    ProtectedRegion r = playerPlots.stream()
                            .filter(p -> samePlotName(p.plotName, nazwa))
                            .findFirst().orElse(null);
                    if (r == null) {
                        gracz.sendMessage("§cNie masz działki o nazwie '" + nazwa + "'.");
                        return true;
                    }

                    // Sprawdź czy działka wymaga opłaty
                    long currentTime = System.currentTimeMillis();
                    long daysLeft = (r.paidUntil - currentTime) / (24L * 60L * 60L * 1000L);

                    if (daysLeft > 14) {
                        gracz.sendMessage("§cDziałka jest opłacona na maksymalnie 14 dni. Pozostało: " + daysLeft + " dni.");
                        return true;
                    }

                    // Sprawdź czy gracz ma 20 żelaznych sztabek
                    if (!gracz.getInventory().containsAtLeast(new ItemStack(Material.IRON_INGOT), 20)) {
                        gracz.sendMessage("§cPotrzebujesz 20 żelaznych sztabek aby opłacić działkę!");
                        gracz.sendMessage("§7Koszt: §e20x Żelazna Sztabka = 3 dni");
                        return true;
                    }

                    // Zabierz żelazne sztabki
                    gracz.getInventory().removeItem(new ItemStack(Material.IRON_INGOT, 20));

                    // Dodaj 3 dni do opłaty
                    r.paidUntil += (3L * 24L * 60L * 60L * 1000L);
                    r.lastPaymentTime = currentTime;
                    savePlots();

                    long newDaysLeft = (r.paidUntil - currentTime) / (24L * 60L * 60L * 1000L);
                    gracz.sendMessage("§aOpłaciłeś działkę '" + nazwa + "' na kolejne 3 dni!");
                    gracz.sendMessage("§7Działka jest teraz opłacona na §e" + newDaysLeft + " dni§7.");

                    return true;
                }
                case "zastepca" -> {
                    if (!gracz.hasPermission("dzialkiplugin.zastepca")) {
                        gracz.sendMessage("§cNie masz uprawnień do ustawiania zastępcy!");
                        return true;
                    }
                    if (args.length < 2) {
                        gracz.sendMessage("§cUżycie: /dzialka zastepca <nazwa> <nick>");
                        return true;
                    }
                    String nazwa = args[1], nick = args[2];
                    List<ProtectedRegion> playerPlots = dzialki.getOrDefault(gracz.getUniqueId(), Collections.emptyList());
                    ProtectedRegion r = playerPlots.stream()
                            .filter(p -> samePlotName(p.plotName, nazwa))
                            .findFirst().orElse(null);
                    if (r == null) {
                        gracz.sendMessage("§cNie masz działki o nazwie '" + nazwa + "'.");
                        return true;
                    }
                    Player deputyPlayer = Bukkit.getPlayer(nick);
                    if (deputyPlayer == null || !deputyPlayer.isOnline()) {
                        gracz.sendMessage("§cGracz '" + nick + "' nie jest online.");
                        return true;
                    }
                    r.deputy = deputyPlayer.getUniqueId();
                    savePlots();
                    gracz.sendMessage("§aUstawiono '" + nick + "' jako zastępcę działki '" + nazwa + "'.");
                    return true;
                }
                case "test" -> {
                    // Tymczasowa komenda testowa do debugowania systemu zapisywania/ładowania
                    plugin.getLogger().info("Komenda test wywołana przez gracza: " + gracz.getName());
                    Location center = gracz.getLocation();
                    int half = 25; // Mniejsza działka testowa
                    ProtectedRegion testRegion = new ProtectedRegion(
                            center.getBlockX() - half, center.getBlockX() + half,
                            center.getBlockZ() - half, center.getBlockZ() + half,
                            gracz.getWorld().getMinHeight(), gracz.getWorld().getMaxHeight(),
                            center, gracz.getName(), "TestowaDzialka", System.currentTimeMillis()
                    );

                    List<ProtectedRegion> playerPlots = dzialki.computeIfAbsent(gracz.getUniqueId(), k -> new ArrayList<>());
                    playerPlots.add(testRegion);
                    savePlots();

                    gracz.sendMessage("§aTestowa działka została utworzona i zapisana!");
                    plugin.getLogger().info("Testowa działka utworzona dla gracza: " + gracz.getName());
                    return true;
                }
                case "debug" -> {
                    // Komenda debugowania do sprawdzania stanu mapy działek
                    plugin.getLogger().info("=== DEBUG DZIAŁEK ===");
                    plugin.getLogger().info("Gracz wywołujący debug: " + gracz.getName() + " (" + gracz.getUniqueId() + ")");
                    plugin.getLogger().info("Rozmiar mapy dzialki: " + dzialki.size());

                    if (dzialki.isEmpty()) {
                        gracz.sendMessage("§cMapa działek jest pusta!");
                        plugin.getLogger().info("Mapa działek jest pusta.");
                    } else {
                        gracz.sendMessage("§aZnaleziono " + dzialki.size() + " graczy z działkami:");
                        for (Map.Entry<UUID, List<ProtectedRegion>> entry : dzialki.entrySet()) {
                            UUID playerUUID = entry.getKey();
                            List<ProtectedRegion> regions = entry.getValue();
                            gracz.sendMessage("§e- Gracz " + playerUUID + ": " + regions.size() + " działek");
                            for (ProtectedRegion region : regions) {
                                gracz.sendMessage("  §7* " + region.plotName + " (właściciel: " + region.owner + ")");
                            }
                        }
                    }

                    // Sprawdź też istnienie pliku plots.yml
                    File file = new File(plugin.getDataFolder(), "plots.yml");
                    if (file.exists()) {
                        gracz.sendMessage("§aPlik plots.yml istnieje: " + file.getAbsolutePath());
                        gracz.sendMessage("§aRozmiar pliku: " + file.length() + " bajtów");
                        plugin.getLogger().info("Plik plots.yml istnieje i ma rozmiar: " + file.length() + " bajtów");
                    } else {
                        gracz.sendMessage("§cPlik plots.yml nie istnieje!");
                        plugin.getLogger().info("Plik plots.yml nie istnieje.");
                    }

                    return true;
                }
                default -> {
                    // Komenda help lub nieznana komenda
                    if (sub.equals("help") || sub.equals("pomoc")) {
                        gracz.sendMessage("§e=== § Komendy Działek §e===");
                        gracz.sendMessage("§7/dzialka stworz <nazwa> - §aUtwórz nową działkę");
                        gracz.sendMessage("§7/dzialka usun <nazwa> - §cUsuń działkę");
                        gracz.sendMessage("§7/dzialka tp <nazwa> - §eTeleportuj się na działkę");
                        gracz.sendMessage("§7/dzialka lista - §bPokaż swoje działki");
                        gracz.sendMessage("§7/dzialka panel <nazwa> - §dOtwórz panel działki");
                        gracz.sendMessage("§7/dzialka warp <nazwa> - §aTeleportuj na warp");
                        gracz.sendMessage("§7/dzialka stworzwarp <nazwa> - §aUstaw warp");
                        gracz.sendMessage("§7/dzialka zapros <nazwa> <nick> - §aZaproś gracza");
                        gracz.sendMessage("§7/dzialka dolacz <nazwa> - §aDołącz do działki");
                        gracz.sendMessage("§7/dzialka opusc <nazwa> - §cOpuść działkę");
                        gracz.sendMessage("§7/dzialka zastepca <nazwa> <nick> - §eUstaw zastępcę");
                        gracz.sendMessage("§7/dzialka top - §6Zobacz ranking działek");
                        return true;
                    } else {
                        gracz.sendMessage("§eNieznana komenda. Użyj /dzialka help");
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            if (sender instanceof Player) {
                ((Player) sender).sendMessage("§cWystąpił błąd podczas wykonywania komendy. Sprawdź konsolę serwera.");
            }
            plugin.getLogger().severe("Błąd w /dzialka:");
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!(sender instanceof Player gracz)) {
            return completions;
        }

        if (args.length == 1) {
            completions.addAll(List.of(
                    "stworz", "usun", "tp", "lista", "panel", "warp", "stworzwarp", "top",
                    "zapros", "opusc", "zastepca", "dolacz", "oplac", "admintp", "adminusun", "test", "debug", "help", "pomoc"
            ));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "usun", "tp", "panel", "warp", "stworzwarp", "opusc", "admintp", "adminusun" -> {
                    List<ProtectedRegion> playerPlots = dzialki
                            .getOrDefault(gracz.getUniqueId(), Collections.emptyList());
                    for (ProtectedRegion r : playerPlots) {
                        if (r.plotName != null) {
                            completions.add(r.plotName);
                        }
                    }
                }
                case "zapros", "zastepca" -> {
                    // Dla zapros i zastepca - pierwszym argumentem jest nazwa działki gracza
                    List<ProtectedRegion> playerPlots = dzialki
                            .getOrDefault(gracz.getUniqueId(), Collections.emptyList());
                    for (ProtectedRegion r : playerPlots) {
                        if (r.plotName != null) {
                            completions.add(r.plotName);
                        }
                    }
                }
                case "dolacz" -> {
                    // Dla dolacz - pokaż wszystkie działki w których gracz NIE jest członkiem
                    for (List<ProtectedRegion> plotList : dzialki.values()) {
                        for (ProtectedRegion r : plotList) {
                            if (r.plotName != null && !r.owner.equals(gracz.getName())
                                    && !r.invitedPlayers.contains(gracz.getUniqueId())) {
                                completions.add(r.plotName);
                            }
                        }
                    }
                }
            }
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("zapros")
                || args[0].equalsIgnoreCase("zastepca"))) {
            // Trzecim argumentem dla zapros/zastepca jest nick gracza
            for (Player p : Bukkit.getOnlinePlayers()) {
                completions.add(p.getName());
            }
        }
        return completions;
    }

    public BossBar getBossBar(Player player) {
        return bossBary.get(player.getUniqueId());
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public void savePlots() {
        // Upewnij się że folder istnieje
        if (!plugin.getDataFolder().exists()) {
            boolean created = plugin.getDataFolder().mkdirs();
            if (created) {
                plugin.getLogger().info("Utworzono folder danych pluginu: " + plugin.getDataFolder().getAbsolutePath());
            } else {
                plugin.getLogger().severe("Nie można utworzyć folderu danych pluginu: " + plugin.getDataFolder().getAbsolutePath());
                return;
            }
        }

        File file = new File(plugin.getDataFolder(), "plots.yml");
        YamlConfiguration config = new YamlConfiguration();

        plugin.getLogger().info("Zapisywanie działek... Liczba graczy z działkami: " + dzialki.size());

        for (UUID uuid : dzialki.keySet()) {
            List<ProtectedRegion> regionList = dzialki.get(uuid);
            String key = uuid.toString();
            if (regionList != null) {
                for (int i = 0; i < regionList.size(); i++) {
                    ProtectedRegion r = regionList.get(i);
                    // 3. savePlots() – pomiń uszkodzony region
                    if (r.plotName == null) {
                        plugin.getLogger().warning("Pomijam działkę bez nazwy dla gracza: " + uuid);
                        continue;
                    }
                    String regionKey = key + "." + i;
                    plugin.getLogger().info("Zapisywanie działki: " + r.plotName + " (właściciel: " + r.owner + ")");
                    config.set(regionKey + ".minX", r.minX);
                    config.set(regionKey + ".maxX", r.maxX);
                    config.set(regionKey + ".minZ", r.minZ);
                    config.set(regionKey + ".maxZ", r.maxZ);
                    config.set(regionKey + ".minY", r.minY);
                    config.set(regionKey + ".maxY", r.maxY);
                    config.set(regionKey + ".center", r.center);
                    config.set(regionKey + ".owner", r.owner);
                    config.set(regionKey + ".plotName", r.plotName);
                    config.set(regionKey + ".creationTime", r.creationTime);
                    config.set(regionKey + ".invitedPlayers", r.invitedPlayers);
                    config.set(regionKey + ".warp", r.warp);
                    config.set(regionKey + ".points", r.points);
                    config.set(regionKey + ".deputy", r.deputy);

                    // Zapisz wszystkie globalne uprawnienia
                    config.set(regionKey + ".allowBuild", r.allowBuild);
                    config.set(regionKey + ".allowDestroy", r.allowDestroy);
                    config.set(regionKey + ".allowChest", r.allowChest);
                    config.set(regionKey + ".allowFlight", r.allowFlight);
                    config.set(regionKey + ".allowEnter", r.allowEnter);
                    config.set(regionKey + ".isDay", r.isDay);
                    config.set(regionKey + ".allowPickup", r.allowPickup);
                    config.set(regionKey + ".allowPotion", r.allowPotion);
                    config.set(regionKey + ".allowKillMobs", r.allowKillMobs);
                    config.set(regionKey + ".allowSpawnMobs", r.allowSpawnMobs);
                    config.set(regionKey + ".allowSpawnerBreak", r.allowSpawnerBreak);
                    config.set(regionKey + ".allowBeaconPlace", r.allowBeaconPlace);
                    config.set(regionKey + ".allowBeaconBreak", r.allowBeaconBreak);
                    config.set(regionKey + ".mobGriefing", r.mobGriefing);

                    // Zapisz dane rynku
                    config.set(regionKey + ".isOnMarket", r.isOnMarket);
                    config.set(regionKey + ".marketPrice", r.marketPrice);

                    // Zapisz indywidualne uprawnienia graczy
                    if (!r.playerPermissions.isEmpty()) {
                        for (Map.Entry<UUID, PlayerPermissions> entry : r.playerPermissions.entrySet()) {
                            String playerKey = regionKey + ".playerPermissions." + entry.getKey().toString();
                            PlayerPermissions perms = entry.getValue();
                            config.set(playerKey + ".allowBuild", perms.allowBuild);
                            config.set(playerKey + ".allowDestroy", perms.allowDestroy);
                            config.set(playerKey + ".allowChest", perms.allowChest);
                            config.set(playerKey + ".allowFlight", perms.allowFlight);
                            config.set(playerKey + ".allowPickup", perms.allowPickup);
                            config.set(playerKey + ".allowPotion", perms.allowPotion);
                            config.set(playerKey + ".allowKillMobs", perms.allowKillMobs);
                            config.set(playerKey + ".allowSpawnMobs", perms.allowSpawnMobs);
                            config.set(playerKey + ".allowSpawnerBreak", perms.allowSpawnerBreak);
                            config.set(playerKey + ".allowBeaconPlace", perms.allowBeaconPlace);
                            config.set(playerKey + ".allowBeaconBreak", perms.allowBeaconBreak);
                        }
                    }

                }
            }
        }

        try {
            config.save(file);
            plugin.getLogger().info("Działki zostały zapisane pomyślnie do: " + file.getAbsolutePath());
        } catch (IOException e) {
            plugin.getLogger().severe(String.format("An error occurred while saving plots: %s", e.getMessage()));
            e.printStackTrace();
        }
    }

    public void loadPlots() {
        File file = new File(plugin.getDataFolder(), "plots.yml");
        if (!file.exists()) {
            plugin.getLogger().info("Plik plots.yml nie istnieje. Nie załadowano żadnych działek.");
            return;
        }

        plugin.getLogger().info("Rozpoczynam ładowanie działek z pliku: " + file.getAbsolutePath());
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Wyczyść istniejące działki przed załadowaniem nowych
        dzialki.clear();
        plugin.getLogger().info("Wyczyszczono mapę działek. Aktualna liczba głównych kluczy w pliku YAML: " + config.getKeys(false).size());

        // Iteruj przez główne klucze (UUID graczy)
        for (String playerUUIDString : config.getKeys(false)) {
            plugin.getLogger().info("Przetwarzam gracza: " + playerUUIDString);

            try {
                UUID playerUUID = UUID.fromString(playerUUIDString);
                List<ProtectedRegion> regions = new ArrayList<>();

                // Pobierz sekcję dla tego gracza
                if (config.isConfigurationSection(playerUUIDString)) {
                    Set<String> plotKeys = config.getConfigurationSection(playerUUIDString).getKeys(false);
                    plugin.getLogger().info("Znaleziono " + plotKeys.size() + " działek dla gracza " + playerUUID);

                    for (String plotKey : plotKeys) {
                        String fullKey = playerUUIDString + "." + plotKey;
                        plugin.getLogger().info("Ładowanie działki z klucza: " + fullKey);

                        String plotName = config.getString(fullKey + ".plotName");
                        if (plotName == null || plotName.isBlank()) {
                            plugin.getLogger().warning("Pomijam działkę bez nazwy (klucz: " + fullKey + ")");
                            continue;
                        }

                        int minX = config.getInt(fullKey + ".minX");
                        int maxX = config.getInt(fullKey + ".maxX");
                        int minZ = config.getInt(fullKey + ".minZ");
                        int maxZ = config.getInt(fullKey + ".maxZ");
                        int minY = config.getInt(fullKey + ".minY");
                        int maxY = config.getInt(fullKey + ".maxY");
                        Location center = config.getLocation(fullKey + ".center");
                        String owner = config.getString(fullKey + ".owner");
                        long creationTime = config.getLong(fullKey + ".creationTime");

                        List<?> rawList = config.getList(fullKey + ".invitedPlayers");
                        List<UUID> invitedPlayers = new ArrayList<>();
                        if (rawList != null) {
                            for (Object obj : rawList) {
                                if (obj instanceof String str) {
                                    invitedPlayers.add(UUID.fromString(str));
                                }
                            }
                        }

                        Location warp = config.getLocation(fullKey + ".warp");
                        int points = config.getInt(fullKey + ".points");
                        UUID deputy = (UUID) config.get(fullKey + ".deputy");

                        plugin.getLogger().info(String.format("Ładowanie działki: %s (Właściciel: %s)", plotName, owner));
                        ProtectedRegion region = new ProtectedRegion(minX, maxX, minZ, maxZ, minY, maxY, center, owner, plotName, creationTime);
                        region.invitedPlayers.addAll(invitedPlayers);
                        region.warp = warp;
                        region.points = points;
                        region.deputy = deputy;

                        // Load all permission settings
                        region.allowBuild = config.getBoolean(fullKey + ".allowBuild", true);
                        region.allowDestroy = config.getBoolean(fullKey + ".allowDestroy", true);
                        region.allowChest = config.getBoolean(fullKey + ".allowChest", true);
                        region.allowFlight = config.getBoolean(fullKey + ".allowFlight", false);
                        region.allowEnter = config.getBoolean(fullKey + ".allowEnter", true);
                        region.isDay = config.getBoolean(fullKey + ".isDay", true);
                        region.allowPickup = config.getBoolean(fullKey + ".allowPickup", false);
                        region.allowPotion = config.getBoolean(fullKey + ".allowPotion", false);
                        region.allowKillMobs = config.getBoolean(fullKey + ".allowKillMobs", false);
                        region.allowSpawnMobs = config.getBoolean(fullKey + ".allowSpawnMobs", false);
                        region.allowSpawnerBreak = config.getBoolean(fullKey + ".allowSpawnerBreak", false);
                        region.allowBeaconPlace = config.getBoolean(fullKey + ".allowBeaconPlace", false);
                        region.allowBeaconBreak = config.getBoolean(fullKey + ".allowBeaconBreak", false);
                        region.mobGriefing = config.getBoolean(fullKey + ".mobGriefing", false);

                        // Ładuj indywidualne uprawnienia graczy
                        if (config.contains(fullKey + ".playerPermissions")) {
                            for (String playerUuidString : config.getConfigurationSection(fullKey + ".playerPermissions").getKeys(false)) {
                                try {
                                    UUID playerUuid = UUID.fromString(playerUuidString);
                                    String playerPermKey = fullKey + ".playerPermissions." + playerUuidString;

                                    PlayerPermissions perms = new PlayerPermissions();
                                    perms.allowBuild = config.getBoolean(playerPermKey + ".allowBuild", false);
                                    perms.allowDestroy = config.getBoolean(playerPermKey + ".allowDestroy", false);
                                    perms.allowChest = config.getBoolean(playerPermKey + ".allowChest", false);
                                    perms.allowFlight = config.getBoolean(playerPermKey + ".allowFlight", false);
                                    perms.allowPickup = config.getBoolean(playerPermKey + ".allowPickup", false);
                                    perms.allowPotion = config.getBoolean(playerPermKey + ".allowPotion", false);
                                    perms.allowKillMobs = config.getBoolean(playerPermKey + ".allowKillMobs", false);
                                    perms.allowSpawnMobs = config.getBoolean(playerPermKey + ".allowSpawnMobs", false);
                                    perms.allowSpawnerBreak = config.getBoolean(playerPermKey + ".allowSpawnerBreak", false);
                                    perms.allowBeaconPlace = config.getBoolean(playerPermKey + ".allowBeaconPlace", false);
                                    perms.allowBeaconBreak = config.getBoolean(playerPermKey + ".allowBeaconBreak", false);

                                    region.playerPermissions.put(playerUuid, perms);
                                } catch (IllegalArgumentException e) {
                                    plugin.getLogger().warning("Nieprawidłowy UUID gracza w uprawnieniach: " + playerUuidString);
                                }
                            }
                        }

                        regions.add(region);
                    }
                }

                if (!regions.isEmpty()) {
                    dzialki.put(playerUUID, regions);
                    plugin.getLogger().info("Dodano " + regions.size() + " działek dla gracza " + playerUUID);
                } else {
                    plugin.getLogger().warning("Brak prawidłowych działek dla gracza " + playerUUID);
                }

            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Nieprawidłowy UUID gracza: " + playerUUIDString);
            }
        }

        plugin.getLogger().info("Zakończono ładowanie działek. Łączna liczba graczy z działkami: " + dzialki.size());
        plugin.getLogger().info("Szczegóły załadowanych działek:");
        for (Map.Entry<UUID, List<ProtectedRegion>> entry : dzialki.entrySet()) {
            UUID playerUUID = entry.getKey();
            List<ProtectedRegion> regions = entry.getValue();
            plugin.getLogger().info("  Gracz " + playerUUID + " ma " + regions.size() + " działek:");
            for (ProtectedRegion region : regions) {
                plugin.getLogger().info("    - " + region.plotName + " (właściciel: " + region.owner + ")");
            }
        }
    }

    private boolean isColliding(ProtectedRegion newRegion) {
        for (List<ProtectedRegion> sublist : dzialki.values()) {
            for (ProtectedRegion region : sublist) {
                if (newRegion.overlaps(region)) {
                    return true;
                }
            }
        }
        return false;
    }

    public ProtectedRegion getRegion(Location loc) {
        for (List<ProtectedRegion> sublist : dzialki.values()) {
            for (ProtectedRegion region : sublist) {
                if (region.contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
                    return region;
                }
            }
        }
        return null;
    }

    // === METODA DO SPRAWDZANIA CZY GRACZ JEST W POBLIŻU DZIAŁKI ===
    public ProtectedRegion getNearbyRegion(Location loc, int radius) {
        for (List<ProtectedRegion> sublist : dzialki.values()) {
            for (ProtectedRegion region : sublist) {
                // Sprawdź czy gracz jest w promieniu działki (rozszerzona granica)
                int expandedMinX = region.minX - radius;
                int expandedMaxX = region.maxX + radius;
                int expandedMinZ = region.minZ - radius;
                int expandedMaxZ = region.maxZ + radius;

                if (loc.getBlockX() >= expandedMinX && loc.getBlockX() <= expandedMaxX
                        && loc.getBlockZ() >= expandedMinZ && loc.getBlockZ() <= expandedMaxZ) {
                    return region;
                }
            }
        }
        return null;
    }

    // === METODA DO SPRAWDZANIA CZY GRACZ JEST NA DZIAŁCE LUB W POBLIŻU ===
    public ProtectedRegion getRegionOrNearby(Player player) {
        Location loc = player.getLocation();

        // Najpierw sprawdź czy gracz jest bezpośrednio na działce
        ProtectedRegion directRegion = getRegion(loc);
        if (directRegion != null) {
            return directRegion;
        }

        // Jeśli nie, sprawdź czy jest w pobliżu (10 bloków)
        return getNearbyRegion(loc, 10);
    }

    public boolean isInAnyPlot(Player player) {
        Location loc = player.getLocation();
        for (List<ProtectedRegion> list : dzialki.values()) {
            for (ProtectedRegion region : list) {
                if (region.contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
                    return true;
                }
            }
        }

        return false;
    }

    // --------------------------------------------------
// 1) Pomocniczka: po tytule GUI zwraca ProtectedRegion
// --------------------------------------------------
    private ProtectedRegion getRegionByName(String plotName) {
        for (List<ProtectedRegion> list : dzialki.values()) {
            for (ProtectedRegion r : list) {
                if (samePlotName(r.plotName, plotName)) {
                    return r;
                }
            }
        }
        return null;
    }

    // === GŁÓWNY PANEL DZIAŁKI ===
    private void openPanel(ProtectedRegion r, Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "§6§lPanel Działki: " + r.plotName);

        // === 1. PODSTAWOWE INFORMACJE (slot 10 - tabliczka) ===
        ItemStack info = new ItemStack(Material.OAK_SIGN);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§e§lPodstawowe informacje");
            List<String> infoLore = new ArrayList<>();
            infoLore.add("§7§l📋 Szczegóły działki");
            infoLore.add("");
            infoLore.add("§7Założyciel: §a" + r.owner);
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            infoLore.add("§7Data założenia: §b" + sdf.format(new Date(r.creationTime)));
            infoLore.add("§7Rozmiar: §e" + (r.maxX - r.minX + 1) + "x" + (r.maxZ - r.minZ + 1) + " bloków");
            infoLore.add("§7Wysokość: §e" + (r.maxY - r.minY + 1) + " bloków");
            infoLore.add("");
            infoLore.add("§8Kliknij, aby uzyskać więcej szczegółów");
            infoMeta.setLore(infoLore);
            info.setItemMeta(infoMeta);
        }
        inv.setItem(10, info);

        // === 2. USTAWIENIA DZIAŁKI (slot 12 - repeater) ===
        ItemStack settings = new ItemStack(Material.REPEATER);
        ItemMeta settingsMeta = settings.getItemMeta();
        if (settingsMeta != null) {
            settingsMeta.setDisplayName("§d§lUstawienia działki");
            List<String> settingsLore = new ArrayList<>();
            settingsLore.add("§7§l⚙ Zarządzanie działką");
            settingsLore.add("");
            settingsLore.add("§7Możliwość latania przez osoby niedodane:");
            settingsLore.add("  §fWłączone: " + (r.allowFlight ? "§a✓ Tak" : "§c✗ Nie"));
            settingsLore.add("§7Wejście na działkę: " + (r.allowEnter ? "§a✓ Tak" : "§c✗ Nie"));
            settingsLore.add("§7Stawianie bloków: " + (r.allowBuild ? "§a✓ Tak" : "§c✗ Nie"));
            settingsLore.add("§7Niszczenie bloków: " + (r.allowDestroy ? "§a✓ Tak" : "§c✗ Nie"));
            settingsLore.add("");
            settingsLore.add("§8Kliknij, aby otworzyć ustawienia");
            settingsMeta.setLore(settingsLore);
            settings.setItemMeta(settingsMeta);
        }
        inv.setItem(12, settings);

        // === 3. INFORMACJE O CZŁONKACH (slot 14 - głowa gracza) ===
        ItemStack members = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta membersMeta = (SkullMeta) members.getItemMeta();
        if (membersMeta != null) {
            membersMeta.setDisplayName("§b§lCzłonkowie działki");
            List<String> membersLore = new ArrayList<>();
            membersLore.add("§7§l👥 Informacje o członkach");
            membersLore.add("");
            membersLore.add("§7Liczba członków: §a" + r.invitedPlayers.size());
            if (r.deputy != null) {
                OfflinePlayer deputyPlayer = Bukkit.getOfflinePlayer(r.deputy);
                membersLore.add("§7Zastępca: §e" + deputyPlayer.getName());
            } else {
                membersLore.add("§7Zastępca: §cNie wyznaczony");
            }
            membersLore.add("");
            membersLore.add("§8Kliknij, aby zobaczyć listę członków");
            membersMeta.setLore(membersLore);
            // Ustaw głowicę właściciela jako ikonę
            membersMeta.setOwningPlayer(Bukkit.getOfflinePlayer(r.owner));
            members.setItemMeta(membersMeta);
        }
        inv.setItem(14, members);

        // === 4. PUNKTY DZIAŁKI (slot 16 - diament) ===
        ItemStack points = new ItemStack(Material.DIAMOND);
        ItemMeta pointsMeta = points.getItemMeta();
        if (pointsMeta != null) {
            pointsMeta.setDisplayName("§a§lPunkty działki");
            List<String> pointsLore = new ArrayList<>();
            pointsLore.add("§7§l💎 System punktów");
            pointsLore.add("");
            pointsLore.add("§7Liczba punktów: §e" + r.points);
            pointsLore.add("");
            pointsLore.add("§7Punkty otrzymujesz za:");
            pointsLore.add("§8• Stawianie dekoracyjnych bloków");
            pointsLore.add("§8• Rozbudowę działki");
            pointsLore.add("§8• Aktywność na serwerze");
            pointsLore.add("");
            pointsLore.add("§8Kliknij, aby zobaczyć za jakie bloki");
            pointsLore.add("§8otrzymasz punkty");
            pointsMeta.setLore(pointsLore);
            points.setItemMeta(pointsMeta);
        }
        inv.setItem(16, points);

        // === 5. RYNEK DZIAŁEK (slot 22 - pergamin) ===
        ItemStack market = new ItemStack(Material.PAPER);
        ItemMeta marketMeta = market.getItemMeta();
        if (marketMeta != null) {
            marketMeta.setDisplayName("§6§lRynek działek");
            List<String> marketLore = new ArrayList<>();
            marketLore.add("§7§l💰 Handel działkami");
            marketLore.add("");
            if (r.isOnMarket) {
                marketLore.add("§a✓ Twoja działka jest na sprzedaż!");
                marketLore.add("§7Cena: §e" + String.format("%.2f", r.marketPrice) + " złota");
                marketLore.add("");
                marketLore.add("§7Aby anulować sprzedaż:");
                marketLore.add("§f/dzialka anuluj " + r.plotName);
                marketLore.add("");
                marketLore.add("§8Kliknij, aby zarządzać ofertą");
            } else {
                marketLore.add("§7Twoja działka nie jest wystawiona na sprzedaż");
                marketLore.add("");
                marketLore.add("§7Aby wystawić na sprzedaż użyj:");
                marketLore.add("§f/dzialka sprzedaj " + r.plotName + " <cena>");
                marketLore.add("§8Przykład: /dzialka sprzedaj " + r.plotName + " 5000");
                marketLore.add("");
                marketLore.add("§7Sprawdź dostępne oferty:");
                marketLore.add("§f/dzialka rynek");
                marketLore.add("");
                marketLore.add("§8Kliknij, aby uzyskać więcej informacji");
            }
            marketMeta.setLore(marketLore);
            market.setItemMeta(marketMeta);
        }
        inv.setItem(22, market);

        // === DEKORACJA I DODATKOWE ELEMENTY ===
        // Separator (szklane panele)
        ItemStack separator = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta sepMeta = separator.getItemMeta();
        if (sepMeta != null) {
            sepMeta.setDisplayName("§7▬▬▬ " + r.plotName + " ▬▬▬");
            separator.setItemMeta(sepMeta);
        }

        // Wypełnij pustki separatorami (wszystkie sloty oprócz głównych funkcji)
        for (int i : new int[]{0, 1, 2, 3, 5, 6, 7, 8, 9, 11, 13, 15, 17, 18, 19, 20, 21, 23, 24, 25, 26}) {
            inv.setItem(i, separator);
        }

        // Teleport do centrum działki (slot 4)
        ItemStack teleport = new ItemStack(Material.ENDER_PEARL);
        ItemMeta tpMeta = teleport.getItemMeta();
        if (tpMeta != null) {
            tpMeta.setDisplayName("§a§lTeleportuj na środek");
            List<String> tpLore = new ArrayList<>();
            tpLore.add("§7§l⚡ Szybka podróż");
            tpLore.add("");
            tpLore.add("§7Teleportuje Cię na środek działki");
            tpLore.add("§7w bezpieczne miejsce");
            tpLore.add("");
            tpLore.add("§8Kliknij aby się teleportować");
            tpMeta.setLore(tpLore);
            teleport.setItemMeta(tpMeta);
        }
        inv.setItem(4, teleport);

        p.openInventory(inv);
    }

    // === GUI USTAWIEŃ DZIAŁKI ===
    private void openSettingsPanel(ProtectedRegion r, Player p) {
        Inventory inv = Bukkit.createInventory(null, 36, "§d§lUstawienia: " + r.plotName);

        // === USTAWIENIA LATANIA ===
        inv.setItem(10, toggleItem(r.allowFlight, "§f§lLatanie dla gości",
                "Pozwala nieznajomym graczom latać na działce", Material.ELYTRA));

        // === INNE USTAWIENIA ===
        inv.setItem(11, toggleItem(r.allowEnter, "§f§lWejście na działkę",
                "Pozwala nieznajomym graczom wchodzić na działkę", Material.OAK_DOOR));

        inv.setItem(12, toggleItem(r.allowBuild, "§f§lStawianie bloków",
                "Pozwala nieznajomym graczom stawiać bloki", Material.BRICKS));

        inv.setItem(13, toggleItem(r.allowDestroy, "§f§lNiszczenie bloków",
                "Pozwala nieznajomym graczom niszczyć bloki", Material.TNT));

        inv.setItem(14, toggleItem(r.allowChest, "§f§lOtwieranie skrzyń",
                "Pozwala nieznajomym graczom używać skrzyń", Material.CHEST));

        inv.setItem(15, toggleItem(r.allowPickup, "§f§lPodnoszenie itemów",
                "Pozwala nieznajomym graczom podnosić przedmioty", Material.HOPPER));

        inv.setItem(16, toggleItem(r.allowPotion, "§f§lRzucanie mikstur",
                "Pozwala nieznajomym graczom używać mikstur", Material.SPLASH_POTION));

        // === USTAWIENIA MOBÓW ===
        inv.setItem(19, toggleItem(r.allowKillMobs, "§f§lBicie mobów",
                "Pozwala nieznajomym graczom atakować moby", Material.IRON_SWORD));

        inv.setItem(20, toggleItem(r.allowSpawnMobs, "§f§lRespienie mobów",
                "Pozwala nieznajomym graczom przyzywać moby", Material.ZOMBIE_SPAWN_EGG));

        inv.setItem(21, toggleItem(r.mobGriefing, "§f§lNiszczenie przez moby",
                "Pozwala mobom niszczyć bloki na działce", Material.CREEPER_HEAD));

        // === USTAWIENIA SPECJALNE ===
        inv.setItem(22, toggleItem(r.isDay, "§f§lCzas na działce",
                "Ustawia dzień lub noc na działce", Material.CLOCK));

        // === PRZYCISKI NAWIGACJI ===
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName("§c« Powrót do panelu głównego");
        backMeta.setLore(List.of("§7Wróć do głównego panelu działki"));
        backButton.setItemMeta(backMeta);
        inv.setItem(31, backButton);

        p.openInventory(inv);
    }

    // === GUI PUNKTÓW DZIAŁKI ===
    private void openPointsPanel(ProtectedRegion r, Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "§a§lPunkty: " + r.plotName);

        // === AKTUALNE PUNKTY ===
        ItemStack currentPoints = new ItemStack(Material.EMERALD);
        ItemMeta currentMeta = currentPoints.getItemMeta();
        currentMeta.setDisplayName("§a§lTwoje punkty: §e" + r.points);
        currentMeta.setLore(List.of(
                "§7Punkty zdobywane są za różne aktywności",
                "§7na działce i jej rozwój"
        ));
        currentPoints.setItemMeta(currentMeta);
        inv.setItem(13, currentPoints);

        // === ZASADY PUNKTOWANIA ===
        ItemStack rules = new ItemStack(Material.BOOK);
        ItemMeta rulesMeta = rules.getItemMeta();
        rulesMeta.setDisplayName("§b§lZasady przyznawania punktów");
        rulesMeta.setLore(List.of(
                "§e+2 punkty §7- postawienie bloku przez właściciela",
                "§e+1 punkt §7- interakcja gościa na działce",
                "§e+5 punktów §7- zaproszenie nowego gracza",
                "§e+10 punktów §7- ustawienie warpu",
                "§e+3 punkty §7- aktywność członków działki",
                "§7",
                "§8Punkty wpływają na ranking działek!"
        ));
        rules.setItemMeta(rulesMeta);
        inv.setItem(11, rules);

        // === RANKING ===
        ItemStack ranking = new ItemStack(Material.GOLD_INGOT);
        ItemMeta rankingMeta = ranking.getItemMeta();
        rankingMeta.setDisplayName("§6§lRanking działek");
        rankingMeta.setLore(List.of(
                "§7Zobacz jak twoja działka wypada",
                "§7w porównaniu z innymi!",
                "§8Kliknij aby otworzyć ranking"
        ));
        ranking.setItemMeta(rankingMeta);
        inv.setItem(15, ranking);

        // === PRZYCISK POWROTU ===
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName("§c« Powrót do panelu głównego");
        backButton.setItemMeta(backMeta);
        inv.setItem(22, backButton);

        p.openInventory(inv);
    }

    private ItemStack toggleItem(boolean enabled, String name, String description, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(enabled ? "§a" + name : "§c" + name);
            List<String> lore = new ArrayList<>();
            lore.add(description);
            lore.add("");
            lore.add("§7Stan: " + (enabled ? "§aWłączone" : "§cWyłączone"));
            lore.add("");
            lore.add(enabled ? "§7Kliknij, aby wyłączyć" : "§7Kliknij, aby włączyć");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Upewnij się, że to gracz
        if (!(event.getWhoClicked() instanceof Player p)) {
            return;
        }

        String title = event.getView().getTitle();

        // === OBSŁUGA GŁÓWNEGO PANELU DZIAŁKI ===
        if (title.startsWith("§6§lPanel Działki: ")) {
            event.setCancelled(true);
            handleMainPanelClick(event, p, title);
            return;
        }

        // === OBSŁUGA PANELU USTAWIEŃ ===
        if (title.startsWith("§d§lUstawienia: ")) {
            event.setCancelled(true);
            handleSettingsPanelClick(event, p, title);
            return;
        }

        // === OBSŁUGA PANELU PUNKTÓW ===
        if (title.startsWith("§a§lPunkty: ")) {
            event.setCancelled(true);
            handlePointsPanelClick(event, p, title);
            return;
        }

        // === OBSŁUGA PANELU RANKINGU ===
        if (title.startsWith("§6§lRanking Działek - Strona ")) {
            event.setCancelled(true);
            handleRankingPanelClick(event, p, title);
            return;
        }

        // Obsługa panelu graczy
        if (title.startsWith("Gracze: ")) {
            event.setCancelled(true);
            handlePlayersPanel(event, p, title);
            return;
        }

        // Obsługa panelu uprawnień gracza
        if (title.startsWith("Uprawnienia: ")) {
            event.setCancelled(true);
            handlePlayerPermissionsPanel(event, p, title);
            return;
        }

        // ...existing code for other panels...
    }

    // === OBSŁUGA GŁÓWNEGO PANELU DZIAŁKI ===
    private void handleMainPanelClick(InventoryClickEvent event, Player player, String title) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        String plotName = title.substring("§6§lPanel Działki: ".length());
        ProtectedRegion region = getRegionByName(plotName);
        if (region == null) {
            player.sendMessage("§cBłąd: Nie można znaleźć działki!");
            return;
        }

        String displayName = clickedItem.getItemMeta().getDisplayName();

        switch (displayName) {
            case "§e§lPodstawowe informacje" -> {
                player.sendMessage("§e=== Szczegóły działki " + plotName + " ===");
                player.sendMessage("§7Założyciel: §a" + region.owner);
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
                player.sendMessage("§7Data założenia: §b" + sdf.format(new Date(region.creationTime)));
                player.sendMessage("§7Rozmiar: §e" + (region.maxX - region.minX + 1) + "x" + (region.maxZ - region.minZ + 1) + " bloków");
                player.sendMessage("§7Punkty: §a" + region.points);
                if (region.warp != null) {
                    player.sendMessage("§7Warp: §aUstawiony");
                } else {
                    player.sendMessage("§7Warp: §cNie ustawiony");
                }
            }

            case "§d§lUstawienia działki" -> {
                openSettingsPanel(region, player);
            }

            case "§b§lCzłonkowie działki" -> {
                openPlayersPanel(region, player);
            }

            case "§a§lPunkty działki" -> {
                openPointsPanel(region, player);
            }

            case "§6§lRynek działek" -> {
                if (region.isOnMarket) {
                    player.sendMessage("§aTwoja działka jest obecnie na sprzedaż!");
                    player.sendMessage("§7Cena: §e" + String.format("%.2f", region.marketPrice) + " złota");
                    player.sendMessage("§7Aby anulować sprzedaż użyj: §f/dzialka anuluj " + plotName);
                } else {
                    player.sendMessage("§7Aby wystawić działkę na sprzedaż użyj:");
                    player.sendMessage("§f/dzialka sprzedaj " + plotName + " <cena>");
                    player.sendMessage("§7Przykład: §f/dzialka sprzedaj " + plotName + " 1000");
                }
                player.closeInventory();
            }

            case "§a§lTeleportuj na środek" -> {
                player.teleport(region.center.clone().add(0.5, 1, 0.5));
                player.sendMessage("§aTeleportowano na środek działki!");
                player.closeInventory();
            }
        }
    }

    // === OBSŁUGA PANELU USTAWIEŃ ===
    private void handleSettingsPanelClick(InventoryClickEvent event, Player player, String title) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        String plotName = title.substring("§d§lUstawienia: ".length());
        ProtectedRegion region = getRegionByName(plotName);
        if (region == null) {
            return;
        }

        String displayName = clickedItem.getItemMeta().getDisplayName();

        // Przycisk powrotu
        if (displayName.equals("§c« Powrót do panelu głównego")) {
            openPanel(region, player);
            return;
        }

        // Zmienna do sprawdzenia czy nastąpiła zmiana
        boolean changed = false;
        String message = "";

        // Obsługa przełączania ustawień
        if (displayName.contains("§f§lLatanie dla gości") || displayName.contains("§a§f§lLatanie dla gości") || displayName.contains("§c§f§lLatanie dla gości")) {
            region.allowFlight = !region.allowFlight;
            message = region.allowFlight
                    ? "§aLatanie dla gości włączone!"
                    : "§cLatanie dla gości wyłączone!";
            changed = true;
        } else if (displayName.contains("§f§lWejście na działkę") || displayName.contains("§a§f§lWejście na działkę") || displayName.contains("§c§f§lWejście na działkę")) {
            region.allowEnter = !region.allowEnter;
            message = region.allowEnter
                    ? "§aWejście na działkę włączone!"
                    : "§cWejście na działkę wyłączone!";
            changed = true;
        } else if (displayName.contains("§f§lStawianie bloków") || displayName.contains("§a§f§lStawianie bloków") || displayName.contains("§c§f§lStawianie bloków")) {
            region.allowBuild = !region.allowBuild;
            message = region.allowBuild
                    ? "§aStawianie bloków włączone!"
                    : "§cStawianie bloków wyłączone!";
            changed = true;
        } else if (displayName.contains("§f§lNiszczenie bloków") || displayName.contains("§a§f§lNiszczenie bloków") || displayName.contains("§c§f§lNiszczenie bloków")) {
            region.allowDestroy = !region.allowDestroy;
            message = region.allowDestroy
                    ? "§aNiszczenie bloków włączone!"
                    : "§cNiszczenie bloków wyłączone!";
            changed = true;
        } else if (displayName.contains("§f§lOtwieranie skrzyń") || displayName.contains("§a§f§lOtwieranie skrzyń") || displayName.contains("§c§f§lOtwieranie skrzyń")) {
            region.allowChest = !region.allowChest;
            message = region.allowChest
                    ? "§aOtwieranie skrzyń włączone!"
                    : "§cOtwieranie skrzyń wyłączone!";
            changed = true;
        } else if (displayName.contains("§f§lPodnoszenie itemów") || displayName.contains("§a§f§lPodnoszenie itemów") || displayName.contains("§c§f§lPodnoszenie itemów")) {
            region.allowPickup = !region.allowPickup;
            message = region.allowPickup
                    ? "§aPodnoszenie itemów włączone!"
                    : "§cPodnoszenie itemów wyłączone!";
            changed = true;
        } else if (displayName.contains("§f§lRzucanie mikstur") || displayName.contains("§a§f§lRzucanie mikstur") || displayName.contains("§c§f§lRzucanie mikstur")) {
            region.allowPotion = !region.allowPotion;
            message = region.allowPotion
                    ? "§aRzucanie mikstur włączone!"
                    : "§cRzucanie mikstur wyłączone!";
            changed = true;
        } else if (displayName.contains("§f§lBicie mobów") || displayName.contains("§a§f§lBicie mobów") || displayName.contains("§c§f§lBicie mobów")) {
            region.allowKillMobs = !region.allowKillMobs;
            message = region.allowKillMobs
                    ? "§aBicie mobów włączone!"
                    : "§cBicie mobów wyłączone!";
            changed = true;
        } else if (displayName.contains("§f§lRespienie mobów") || displayName.contains("§a§f§lRespienie mobów") || displayName.contains("§c§f§lRespienie mobów")) {
            region.allowSpawnMobs = !region.allowSpawnMobs;
            message = region.allowSpawnMobs
                    ? "§aRespienie mobów włączone!"
                    : "§cRespienie mobów wyłączone!";
            changed = true;
        } else if (displayName.contains("§f§lNiszczenie przez moby") || displayName.contains("§a§f§lNiszczenie przez moby") || displayName.contains("§c§f§lNiszczenie przez moby")) {
            region.mobGriefing = !region.mobGriefing;
            message = region.mobGriefing
                    ? "§aMoby mogą niszczyć bloki!"
                    : "§cMoby nie mogą niszczyć bloków!";
            changed = true;
        } else if (displayName.contains("§f§lCzas na działce") || displayName.contains("§a§f§lCzas na działce") || displayName.contains("§c§f§lCzas na działce")) {
            region.isDay = !region.isDay;
            updateTimeForPlayersInRegion(region, player);
            message = region.isDay
                    ? "§aWłączono dzień na działce!"
                    : "§aWłączono noc na działce!";
            changed = true;
        }

        // Jeśli nastąpiła zmiana, zapisz i odśwież panel
        if (changed) {
            player.sendMessage(message);
            savePlots();

            // Odśwież panel z nowymi stanami
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                openSettingsPanel(region, player);
            }, 1L);
        }
    }

    // === OBSŁUGA PANELU PUNKTÓW ===
    private void handlePointsPanelClick(InventoryClickEvent event, Player player, String title) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        String plotName = title.substring("§a§lPunkty: ".length());
        ProtectedRegion region = getRegionByName(plotName);
        if (region == null) {
            return;
        }

        String displayName = clickedItem.getItemMeta().getDisplayName();

        if (displayName.equals("§c« Powrót do panelu głównego")) {
            openPanel(region, player);
        } else if (displayName.equals("§6§lRanking działek")) {
            openTopPanel(player, 1);
        }
    }

    // === OBSŁUGA PANELU RANKINGU ===
    private void handleRankingPanelClick(InventoryClickEvent event, Player player, String title) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        String displayName = clickedItem.getItemMeta().getDisplayName();

        // Pobierz aktualną stronę z tytułu
        String[] titleParts = title.split(" - Strona ");
        if (titleParts.length < 2) {
            return;
        }

        String[] pageParts = titleParts[1].split("/");
        if (pageParts.length < 2) {
            return;
        }

        int currentPage;
        int totalPages;
        try {
            currentPage = Integer.parseInt(pageParts[0]);
            totalPages = Integer.parseInt(pageParts[1]);
        } catch (NumberFormatException e) {
            return;
        }

        // Obsługa przycisków nawigacji
        if (displayName.equals("§c« Poprzednia strona") && currentPage > 1) {
            openTopPanel(player, currentPage - 1);
        } else if (displayName.equals("§aNastępna strona »") && currentPage < totalPages) {
            openTopPanel(player, currentPage + 1);
        } else if (displayName.equals("§e🔄 Odśwież ranking")) {
            openTopPanel(player, currentPage);
        } else if (displayName.equals("§b§lInformacje o rankingu")) {
            // Informacje - nic nie robimy, tylko pokazujemy lore
            return;
        } else {
            // Kliknięcie na działkę - teleportuj gracza
            List<String> lore = clickedItem.getItemMeta().getLore();
            if (lore != null && !lore.isEmpty()) {
                // Pobierz nazwę działki z displayName
                String plotName = null;
                if (displayName.contains(". ")) {
                    String[] parts = displayName.split(". ", 2);
                    if (parts.length == 2) {
                        plotName = parts[1].replaceAll("§[0-9a-f]", ""); // Usuń kody kolorów
                    }
                }

                if (plotName != null) {
                    ProtectedRegion targetPlot = getRegionByName(plotName);
                    if (targetPlot != null) {
                        // Sprawdź czy gracz ma prawo wejścia na działkę
                        if (!targetPlot.allowEnter && !targetPlot.owner.equals(player.getName())
                                && !targetPlot.invitedPlayers.contains(player.getUniqueId())) {
                            player.sendMessage("§cNie masz uprawnień do wejścia na tę działkę!");
                            return;
                        }

                        // Teleportuj gracza na środek działki
                        Location teleportLoc = targetPlot.center.clone().add(0.5, 1, 0.5);
                        player.teleport(teleportLoc);
                        player.sendMessage("§aTeleportowano na działkę §e" + targetPlot.plotName + " §a(właściciel: §b" + targetPlot.owner + "§a)");
                        player.closeInventory();

                        // Dodaj punkt za teleportację z rankingu (bonus dla właściciela)
                        if (targetPlot.owner.equals(player.getName())) {
                            targetPlot.points += 1;
                            savePlots();
                        }
                    } else {
                        player.sendMessage("§cNie można znaleźć tej działki!");
                    }
                }
            }
        }
    }

    // === OBSŁUGA PANELU CZŁONKÓW ===
    private void handlePlayersPanel(InventoryClickEvent event, Player player, String title) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        String plotName = title.substring("§b§lCzłonkowie: ".length());
        ProtectedRegion region = getRegionByName(plotName);
        if (region == null) {
            return;
        }

        String displayName = clickedItem.getItemMeta().getDisplayName();

        if (displayName.equals("§c« Powrót do panelu głównego")) {
            openPanel(region, player);
        }
        // Możliwość rozszerzenia o zarządzanie członkami w przyszłości
    }

    // === OBSŁUGA PANELU UPRAWNIEŃ GRACZA ===
    private void handlePlayerPermissionsPanel(InventoryClickEvent event, Player player, String title) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        String playerName = title.substring("§c§lUprawnienia: ".length());
        String displayName = clickedItem.getItemMeta().getDisplayName();

        if (displayName.equals("§c« Powrót do panelu członków")) {
            // Wróć do panelu członków
            player.performCommand("dzialka panel");
        }
        // Możliwość rozszerzenia o zarządzanie uprawnieniami w przyszłości
    }

    // === METODY PUBLICZNE DLA LISTENERÓW ===
    public void showBossBar(ProtectedRegion region, Player player) {
        String nazwa = region.plotName;
        String wlasciciel = region.owner;
        BossBar bar = bossBary.computeIfAbsent(player.getUniqueId(), uuid -> {
            BossBar b = org.bukkit.Bukkit.createBossBar("", org.bukkit.boss.BarColor.GREEN, org.bukkit.boss.BarStyle.SOLID);
            b.setVisible(true);
            b.addPlayer(player);
            return b;
        });
        bar.setTitle(org.bukkit.ChatColor.YELLOW + "Działka: " + org.bukkit.ChatColor.GREEN + nazwa
                + org.bukkit.ChatColor.YELLOW + " | Właściciel: " + org.bukkit.ChatColor.GREEN + wlasciciel);
        bar.setProgress(1.0);
    }

    public void hideBossBar(Player player) {
        BossBar bar = bossBary.get(player.getUniqueId());
        if (bar != null) {
            bar.setVisible(false);
            bar.removeAll();
            bossBary.remove(player.getUniqueId());
        }
    }

    public void updatePlayerBossBar(Player player) {
        // BossBar powinien być pokazywany TYLKO gdy gracz jest bezpośrednio na działce
        ProtectedRegion region = getRegion(player.getLocation());
        if (region != null) {
            showBossBar(region, player);
        } else {
            hideBossBar(player);
        }
    }

    public void stopParticles(ProtectedRegion region) {
        // Zatrzymaj cząsteczki dla konkretnego regionu
        for (Player player : Bukkit.getOnlinePlayers()) {
            ProtectedRegion currentRegion = getNearbyRegion(player.getLocation(), 10);
            if (currentRegion != null && currentRegion.equals(region)) {
                stopBoundaryParticles(player);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Sprawdź czy gracz jest bezpośrednio na działce
        ProtectedRegion region = getRegion(player.getLocation());
        if (region != null) {
            // Gracz jest na działce - pokaż BossBar i granice
            showBossBar(region, player);
            scheduleBoundaryParticles(region, player);
        } else {
            // Sprawdź czy jest w pobliżu (10 bloków) - tylko granice
            region = getNearbyRegion(player.getLocation(), 10);
            if (region != null) {
                scheduleBoundaryParticles(region, player);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        hideBossBar(player);
        stopBoundaryParticles(player);
    }

    private void openPlayersPanel(ProtectedRegion region, Player player) {
        // Zastępcza implementacja panelu członków
        player.sendMessage("§b=== Członkowie działki " + region.plotName + " ===");
        player.sendMessage("§7Właściciel: §a" + region.owner);

        if (region.deputy != null) {
            OfflinePlayer deputyPlayer = Bukkit.getOfflinePlayer(region.deputy);
            player.sendMessage("§7Zastępca: §e" + deputyPlayer.getName());
        }

        if (region.invitedPlayers.isEmpty()) {
            player.sendMessage("§7Brak zaproszonych członków");
        } else {
            player.sendMessage("§7Zaproszeni członkowie:");
            for (UUID memberId : region.invitedPlayers) {
                OfflinePlayer member = Bukkit.getOfflinePlayer(memberId);
                player.sendMessage("§8- §f" + member.getName());
            }
        }
    }

    private void updateTimeForPlayersInRegion(ProtectedRegion region, Player sender) {
        // Zaktualizuj czas dla wszystkich graczy w regionie
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (region.contains(player.getLocation().getBlockX(),
                    player.getLocation().getBlockY(),
                    player.getLocation().getBlockZ())) {
                if (region.isDay) {
                    player.setPlayerTime(6000, false); // Dzień
                } else {
                    player.setPlayerTime(18000, false); // Noc
                }
            }
        }
    }

    // === SYSTEM PUNKTOWANIA ===
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();
        ProtectedRegion region = getRegion(loc);

        if (region != null) {
            // Właściciel stawia blok na swojej działce
            if (region.owner.equals(player.getName())) {
                region.points += 2;
                savePlots();

                // Co 10 punktów powiadamiaj właściciela
                if (region.points % 10 == 0) {
                    player.sendMessage("§a✨ Działka §e" + region.plotName + " §ama teraz §b" + region.points + " §apunktów!");
                }
            } // Zaproszony gracz stawia blok
            else if (region.invitedPlayers.contains(player.getUniqueId())) {
                region.points += 1;
                savePlots();

                // Powiadom właściciela jeśli jest online
                Player owner = Bukkit.getPlayer(region.owner);
                if (owner != null && owner.isOnline()) {
                    owner.sendMessage("§a" + player.getName() + " §epostawił blok na Twojej działce! §7(+1 punkt)");
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();
        ProtectedRegion region = getRegion(loc);

        if (region != null) {
            // Właściciel niszczy blok - odejmij mniejszą liczbę punktów
            if (region.owner.equals(player.getName())) {
                if (region.points > 0) {
                    region.points = Math.max(0, region.points - 1);
                    savePlots();
                }
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }

        Player player = event.getPlayer();
        Location loc = event.getClickedBlock().getLocation();
        ProtectedRegion region = getRegion(loc);

        if (region != null && !region.owner.equals(player.getName())
                && region.invitedPlayers.contains(player.getUniqueId())) {

            // Losowe bonusowe punkty za interakcję gościa (20% szans)
            if (Math.random() < 0.2) {
                region.points += 1;
                savePlots();

                Player owner = Bukkit.getPlayer(region.owner);
                if (owner != null && owner.isOnline()) {
                    owner.sendMessage("§e" + player.getName() + " §aaktywnie korzysta z Twojej działki! §7(+1 punkt)");
                }
            }
        }
    }

    // Metoda dodawania punktów za zaproszenie gracza (wywoływana w komendzie zapros)
    public void addInvitePoints(ProtectedRegion region, String inviterName, String invitedName) {
        region.points += 5;
        savePlots();

        Player inviter = Bukkit.getPlayer(inviterName);
        if (inviter != null && inviter.isOnline()) {
            inviter.sendMessage("§a✨ Otrzymałeś 5 punktów za zaproszenie gracza §e" + invitedName + "§a!");
        }
    }

    // Metoda dodawania punktów za ustawienie warpu
    public void addWarpPoints(ProtectedRegion region, String playerName) {
        region.points += 10;
        savePlots();

        Player player = Bukkit.getPlayer(playerName);
        if (player != null && player.isOnline()) {
            player.sendMessage("§a✨ Otrzymałeś 10 punktów za ustawienie warpu na działce §e" + region.plotName + "§a!");
        }
    }

    // Metoda okresowego dodawania punktów za aktywność
    public void addActivityPoints() {
        for (List<ProtectedRegion> plotList : dzialki.values()) {
            for (ProtectedRegion region : plotList) {
                // Sprawdź czy właściciel był aktywny w ostatnich 24h
                Player owner = Bukkit.getPlayer(region.owner);
                if (owner != null && owner.isOnline()) {
                    // Sprawdź czy są aktywni członkowie
                    int activeMembers = 0;
                    for (UUID memberId : region.invitedPlayers) {
                        Player member = Bukkit.getPlayer(memberId);
                        if (member != null && member.isOnline()) {
                            activeMembers++;
                        }
                    }

                    // Dodaj punkty za aktywnych członków
                    if (activeMembers > 0) {
                        region.points += activeMembers * 3;
                        owner.sendMessage("§a✨ Twoja działka §e" + region.plotName + " §aorzymała §b"
                                + (activeMembers * 3) + " §apunktów za aktywnych członków!");
                    }
                }
            }
        }
        savePlots();
    }

    // Automatyczne uruchomienie systemu punktów aktywności
    public void startActivityPointsTask() {
        // Uruchom task co godzinę (72000 ticków)
        Bukkit.getScheduler().runTaskTimer(plugin, this::addActivityPoints, 72000L, 72000L);
    }

    // === KLASA PROTECTEDREGION ===
    public static class ProtectedRegion {

        public int minX, maxX, minZ, maxZ, minY, maxY;
        public Location center;
        public String owner;
        public String plotName;
        public long creationTime;
        public List<UUID> invitedPlayers = new ArrayList<>();
        public Location warp;
        public int points = 0;
        public UUID deputy;

        // Podstawowe uprawnienia
        public boolean allowBuild = true;
        public boolean allowDestroy = true;
        public boolean allowChest = true;
        public boolean allowFlight = false;
        public boolean allowEnter = true;
        public boolean isDay = true;
        public boolean allowPickup = false;
        public boolean allowPotion = false;
        public boolean allowKillMobs = false;
        public boolean allowSpawnMobs = false;
        public boolean allowSpawnerBreak = false;
        public boolean allowBeaconPlace = false;
        public boolean allowBeaconBreak = false;
        public boolean mobGriefing = false;  // Nowe pole - czy moby mogą niszczyć bloki

        // Rynek działek
        public boolean isOnMarket = false;
        public double marketPrice = 0.0;

        // System opłat za działkę
        public long lastPaymentTime = System.currentTimeMillis(); // Czas ostatniej opłaty
        public long paidUntil = System.currentTimeMillis() + (3L * 24L * 60L * 60L * 1000L); // Opłacone do (3 dni od utworzenia)

        // Indywidualne uprawnienia dla poszczególnych graczy
        public Map<UUID, PlayerPermissions> playerPermissions = new HashMap<>();

        public ProtectedRegion(int minX, int maxX, int minZ, int maxZ, int minY, int maxY,
                Location center, String owner, String plotName, long creationTime) {
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.minY = minY;
            this.maxY = maxY;
            this.center = center;
            this.owner = owner;
            this.plotName = plotName;
            this.creationTime = creationTime;
        }

        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        public boolean overlaps(ProtectedRegion other) {
            return !(this.maxX < other.minX || this.minX > other.maxX
                    || this.maxZ < other.minZ || this.minZ > other.maxZ
                    || this.maxY < other.minY || this.minY > other.maxY);
        }

        public String getId() {
            return plotName; // Zwraca nazwę działki
        }

        public List<String> getOwners() {
            return List.of(owner); // Zwraca listę właścicieli (na razie tylko jeden)
        }

        public Location getMinimumPoint() {
            return new Location(center.getWorld(), minX, minY, minZ);
        }

        public Location getMaximumPoint() {
            return new Location(center.getWorld(), maxX, maxY, maxZ);
        }
    }

    // === KLASA PLAYERpermissions ===
    public static class PlayerPermissions {

        public boolean allowBuild = false;
        public boolean allowDestroy = false;
        public boolean allowChest = false;
        public boolean allowFlight = false;
        public boolean allowPickup = false;
        public boolean allowPotion = false;
        public boolean allowKillMobs = false;
        public boolean allowSpawnMobs = false;
        public boolean allowSpawnerBreak = false;
        public boolean allowBeaconPlace = false;
        public boolean allowBeaconBreak = false;
    }

    // === SYSTEM WYŚWIETLANIA GRANIC DZIAŁEK ===
    // Mapa przechowująca aktywne granice dla każdego gracza
    private final Map<UUID, BukkitRunnable> playerBoundaryTasks = new HashMap<>();
    private final Map<UUID, Integer> playerParticleOffset = new HashMap<>();

    public void stopBoundaryParticles(Player player) {
        BukkitRunnable old = playerBoundaryTasks.remove(player.getUniqueId());
        if (old != null) {
            old.cancel();
        }
        playerParticleOffset.remove(player.getUniqueId());
    }

    // ========================= NOWY SYSTEM GRANIC =========================
    // Główna metoda do wyświetlania granic na określonej wysokości z płynną animacją
    public void showBoundaryParticles(ProtectedRegion region, Player player, int y) {
        World world = player.getWorld();
        int step = 1; // co 1 blok – równa linia

        // Góra i dół działki (oś X)
        for (int x = region.getMinimumPoint().getBlockX();
                x <= region.getMaximumPoint().getBlockX(); x += step) {

            spawnFireCloud(player, new Location(world, x + 0.5, y,
                    region.getMinimumPoint().getBlockZ() + 0.5)); // północ
            spawnFireCloud(player, new Location(world, x + 0.5, y,
                    region.getMaximumPoint().getBlockZ() + 0.5)); // południe
        }

        // Lewa i prawa krawędź (oś Z)
        for (int z = region.getMinimumPoint().getBlockZ();
                z <= region.getMaximumPoint().getBlockZ(); z += step) {

            spawnFireCloud(player, new Location(world,
                    region.getMinimumPoint().getBlockX() + 0.5, y, z + 0.5)); // zachód
            spawnFireCloud(player, new Location(world,
                    region.getMaximumPoint().getBlockX() + 0.5, y, z + 0.5)); // wschód
        }
    }

    // Metoda do cyklicznego wyświetlania granic na wielu poziomach z płynną animacją
    public void scheduleBoundaryParticles(ProtectedRegion region, Player player) {
        stopBoundaryParticles(player);
        playerParticleOffset.put(player.getUniqueId(), 0);

        final int minY = Math.max(region.minY, player.getWorld().getMinHeight() + 5);
        final int maxY = Math.min(region.maxY, player.getWorld().getMaxHeight() - 5);
        final int yStep = 12; // Zwiększony odstęp między poziomami dla mniejszego lagu

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    playerBoundaryTasks.remove(player.getUniqueId());
                    playerParticleOffset.remove(player.getUniqueId());
                    return;
                }

                // Sprawdź czy gracz nadal jest w promieniu 10 bloków od działki
                ProtectedRegion nearby = getNearbyRegion(player.getLocation(), 10);
                if (nearby == null || !nearby.equals(region)) {
                    cancel();
                    playerBoundaryTasks.remove(player.getUniqueId());
                    playerParticleOffset.remove(player.getUniqueId());
                    return;
                }

                // Pobierz i zaktualizuj offset dla płynnej animacji
                int offset = playerParticleOffset.getOrDefault(player.getUniqueId(), 0);
                playerParticleOffset.put(player.getUniqueId(), (offset + 1) % 20);

                // Wyświetl granice tylko na wybranym poziomie Y (rotacja)
                int currentYIndex = offset % Math.max(1, (maxY - minY) / yStep + 1);
                int currentY = minY + (currentYIndex * yStep);

                if (currentY <= maxY) {
                    showBoundaryParticles(region, player, currentY);
                }
            }
        };
        // Uruchom co 3 ticki (~0.15 sekundy) dla płynniejszej animacji
        task.runTaskTimer(plugin, 0L, 3L);
        playerBoundaryTasks.put(player.getUniqueId(), task);
    }

    // ========================= PARTICLE PACK =========================
    private void spawnSmoothFireParticles(Player player, Location loc) {
        // Delikatne cząsteczki z mniejszą liczbą i rozproszeniem
        double spread = 0.3;
        int count = 2; // Zmniejszona liczba cząsteczek

        // FLAME – ciepła mgiełka (mniej intensywna)
        player.spawnParticle(Particle.FLAME, loc, count, spread, spread * 0.5, spread, 0.01);

        // REDSTONE – pomarańczowa poświata (rzadziej)
        if (Math.random() < 0.3) { // Tylko 30% szans na dodatkową cząsteczkę
            player.spawnParticle(Particle.REDSTONE, loc, 1, spread * 0.4, spread * 0.4, spread * 0.4,
                    new Particle.DustOptions(org.bukkit.Color.ORANGE, 0.3f));
        }
    }

    private void spawnFireCloud(Player player, Location loc) {
        player.spawnParticle(Particle.FLAME, loc, 2, 0.15, 0.10, 0.15, 0.01);
        player.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 1, 0.10, 0.05, 0.10, 0.01);
        player.spawnParticle(
                Particle.REDSTONE, loc, 1, 0.10, 0.05, 0.10,
                new Particle.DustOptions(Color.RED, 1.2f)
        );
    }

    // === BRAKUJĄCE METODY - IMPLEMENTACJE ZASTĘPCZE ===
    private void openTopPanel(Player player, int page) {
        // Zbierz wszystkie działki i posortuj według punktów
        List<ProtectedRegion> allPlots = new ArrayList<>();
        for (List<ProtectedRegion> plotList : dzialki.values()) {
            allPlots.addAll(plotList);
        }

        // Sortuj malejąco według punktów
        allPlots.sort((r1, r2) -> Integer.compare(r2.points, r1.points));

        // Oblicz paginację
        int plotsPerPage = 21; // 3 rzędy po 7 slotów
        int totalPages = Math.max(1, (int) Math.ceil((double) allPlots.size() / plotsPerPage));
        page = Math.max(1, Math.min(page, totalPages));

        int startIndex = (page - 1) * plotsPerPage;
        int endIndex = Math.min(startIndex + plotsPerPage, allPlots.size());

        Inventory inv = Bukkit.createInventory(null, 54, "§6§lRanking Działek - Strona " + page + "/" + totalPages);

        // Wypełnij ranking
        for (int i = startIndex; i < endIndex; i++) {
            ProtectedRegion plot = allPlots.get(i);
            int position = i + 1;

            Material material;
            String prefix;

            // Ustaw materiał i prefix w zależności od pozycji
            if (position == 1) {
                material = Material.GOLD_INGOT;
                prefix = "§6§l🥇 1. ";
            } else if (position == 2) {
                material = Material.IRON_INGOT;
                prefix = "§7§l🥈 2. ";
            } else if (position == 3) {
                material = Material.COPPER_INGOT;
                prefix = "§c§l🥉 3. ";
            } else if (position <= 10) {
                material = Material.EMERALD;
                prefix = "§a§l" + position + ". ";
            } else {
                material = Material.DIAMOND;
                prefix = "§b§l" + position + ". ";
            }

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(prefix + "§e" + plot.plotName);
                List<String> lore = new ArrayList<>();
                lore.add("§7Właściciel: §a" + plot.owner);
                lore.add("§7Punkty: §e" + plot.points);
                lore.add("§7Członkowie: §b" + plot.invitedPlayers.size());
                lore.add("§7Rozmiar: §d" + (plot.maxX - plot.minX + 1) + "x" + (plot.maxZ - plot.minZ + 1));

                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
                lore.add("§7Utworzono: §8" + sdf.format(new Date(plot.creationTime)));
                lore.add("");
                lore.add("§8Kliknij aby teleportować się na działkę");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }

            int slot = (i - startIndex) + 9; // Zaczynamy od slotu 9 (drugi rząd)
            if (slot < 45) { // Upewnij się że nie przekraczamy granic inventory
                inv.setItem(slot, item);
            }
        }

        // Dodaj przyciski nawigacji
        if (page > 1) {
            ItemStack prevButton = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevButton.getItemMeta();
            if (prevMeta != null) {
                prevMeta.setDisplayName("§c« Poprzednia strona");
                prevMeta.setLore(List.of("§7Strona " + (page - 1) + "/" + totalPages));
                prevButton.setItemMeta(prevMeta);
            }
            inv.setItem(48, prevButton);
        }

        if (page < totalPages) {
            ItemStack nextButton = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextButton.getItemMeta();
            if (nextMeta != null) {
                nextMeta.setDisplayName("§aNastępna strona »");
                nextMeta.setLore(List.of("§7Strona " + (page + 1) + "/" + totalPages));
                nextButton.setItemMeta(nextMeta);
            }
            inv.setItem(50, nextButton);
        }

        // Przycisk odświeżenia
        ItemStack refreshButton = new ItemStack(Material.COMPASS);
        ItemMeta refreshMeta = refreshButton.getItemMeta();
        if (refreshMeta != null) {
            refreshMeta.setDisplayName("§e🔄 Odśwież ranking");
            refreshMeta.setLore(List.of("§7Kliknij aby odświeżyć listę", "§7Łącznie działek: §a" + allPlots.size()));
            refreshButton.setItemMeta(refreshMeta);
        }
        inv.setItem(49, refreshButton);

        // Przycisk informacji
        ItemStack infoButton = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoButton.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§b§lInformacje o rankingu");
            List<String> infoLore = new ArrayList<>();
            infoLore.add("§7Ranking działek jest automatycznie");
            infoLore.add("§7aktualizowany na podstawie punktów.");
            infoLore.add("");
            infoLore.add("§ePunkty otrzymujesz za:");
            infoLore.add("§8• +2 - postawienie bloku przez właściciela");
            infoLore.add("§8• +1 - interakcję gościa na działce");
            infoLore.add("§8• +5 - zaproszenie nowego gracza");
            infoLore.add("§8• +10 - ustawienie warpu");
            infoLore.add("§8• +3 - aktywność członków działki");
            infoMeta.setLore(infoLore);
            infoButton.setItemMeta(infoMeta);
        }
        inv.setItem(4, infoButton);

        player.openInventory(inv);
    }

    public void showBoundaryParticlesVertical(ProtectedRegion region, Player player, int edgeStep) {
        World world = player.getWorld();
        int minY = Math.max(region.minY, world.getMinHeight());
        int maxY = Math.min(region.maxY, world.getMaxHeight());

        for (int y = minY; y <= maxY; y += 10) {
            // Górna krawędź (północ) - z = minZ
            for (int x = region.minX; x <= region.maxX; x++) {
                Location loc = new Location(world, x + 0.5, y, region.minZ + 0.5);
                spawnSmoothFireParticles(player, loc);
            }

            // Dolna krawędź (południe) - z = maxZ
            for (int x = region.minX; x <= region.maxX; x++) {
                Location loc = new Location(world, x + 0.5, y, region.maxZ + 0.5);
                spawnSmoothFireParticles(player, loc);
            }

            for (int z = region.minZ; z <= region.maxZ; z++) {
                Location loc = new Location(world, region.minX + 0.5, y, z + 0.5);
                spawnSmoothFireParticles(player, loc);
            }

            for (int z = region.minZ; z <= region.maxZ; z++) {
                Location loc = new Location(world, region.maxX + 0.5, y, z + 0.5);
                spawnSmoothFireParticles(player, loc);
            }
        }
    }
}
