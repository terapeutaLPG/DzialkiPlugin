package me.twojanazwa.commands;

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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
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
    private final Map<ProtectedRegion, BukkitRunnable> particleTasks = new HashMap<>();
    private final Map<UUID, String> pendingDeletions = new HashMap<>();

    public DzialkaCommand(JavaPlugin plugin) {
        this.plugin = plugin;
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
                    showBossBar(newRegion, gracz);
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
                    r.invitedPlayers.add(invited.getUniqueId());
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
                case "help" -> {
                    gracz.sendMessage("§a========================================");
                    gracz.sendMessage("§6     DzialkiPlugin v2.0 - jaruso99");
                    gracz.sendMessage("§a========================================");
                    gracz.sendMessage("§7Dostępne komendy:");
                    gracz.sendMessage("§e/dzialka stworz <nazwa> §7- tworzy działkę");
                    gracz.sendMessage("§e/dzialka usun <nazwa> §7- usuwa działkę");
                    gracz.sendMessage("§e/dzialka tp <nazwa> §7- teleportuje na działkę");
                    gracz.sendMessage("§e/dzialka panel <nazwa> §7- otwiera panel GUI");
                    gracz.sendMessage("§e/dzialka lista §7- lista twoich działek");
                    gracz.sendMessage("§e/dzialka zapros <nazwa> <nick> §7- zaprasza gracza");
                    gracz.sendMessage("§e/dzialka warp <nazwa> §7- teleportuje do warpu");
                    gracz.sendMessage("§e/dzialka stworzwarp <nazwa> §7- ustawia warp");
                    gracz.sendMessage("§e/dzialka top §7- ranking działek");
                    gracz.sendMessage("§a========================================");
                    return true;
                }
                default -> {
                    gracz.sendMessage("§eNieznana komenda. Użyj /dzialka help");
                    return true;
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
                    "zapros", "opusc", "zastepca", "admintp", "adminusun", "test", "debug", "help"
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
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        completions.add(p.getName());
                    }
                }
            }
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("zapros")
                || args[0].equalsIgnoreCase("zastepca"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                completions.add(p.getName());
            }
        }
        return completions;
    }

    // Poniższe metody zostały przeniesione poza blok switch
    public void stopParticles(ProtectedRegion region) {  // zmieniono z package-private na public
        BukkitRunnable task = particleTasks.remove(region);
        if (task != null) {
            task.cancel();
        }
    }

    public BossBar getBossBar(Player player) {
        return bossBary.get(player.getUniqueId());
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
            sepMeta.setLore(List.of("§8DzialkiPlugin v2.0 by jaruso99"));
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

        // Rynek działek
        public boolean isOnMarket = false;
        public double marketPrice = 0.0;

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

    // === BRAKUJĄCE METODY ===
    public void scheduleBoundaryParticles(ProtectedRegion region, Player player) {
        // Logika wyświetlania cząsteczek na granicach działki
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                // Wyświetl cząsteczki na granicach działki
                for (int x = region.minX; x <= region.maxX; x += 5) {
                    for (int z = region.minZ; z <= region.maxZ; z += 5) {
                        Location loc1 = new Location(player.getWorld(), x, region.minY + 1, region.minZ);
                        Location loc2 = new Location(player.getWorld(), x, region.minY + 1, region.maxZ);
                        Location loc3 = new Location(player.getWorld(), region.minX, region.minY + 1, z);
                        Location loc4 = new Location(player.getWorld(), region.maxX, region.minY + 1, z);

                        player.spawnParticle(Particle.REDSTONE, loc1, 1);
                        player.spawnParticle(Particle.REDSTONE, loc2, 1);
                        player.spawnParticle(Particle.REDSTONE, loc3, 1);
                        player.spawnParticle(Particle.REDSTONE, loc4, 1);
                    }
                }
            }
        };

        task.runTaskTimer(plugin, 0L, 20L); // Co sekundę
        particleTasks.put(region, task);

        // Zatrzymaj po 30 sekundach
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            task.cancel();
            particleTasks.remove(region);
        }, 600L);
    }

    public void showBossBar(ProtectedRegion region, Player player) {
        BossBar bossBar = bossBary.get(player.getUniqueId());
        if (bossBar == null) {
            bossBar = Bukkit.createBossBar("§6Działka: " + region.plotName, BarColor.YELLOW, BarStyle.SOLID);
            bossBary.put(player.getUniqueId(), bossBar);
        } else {
            bossBar.setTitle("§6Działka: " + region.plotName);
        }

        bossBar.addPlayer(player);
        bossBar.setVisible(true);
    }

    public void openTopPanel(Player player, int page) {
        player.sendMessage("§7Ranking działek będzie dostępny wkrótce!");
    }

    public void openPlayersPanel(ProtectedRegion region, Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§b§lCzłonkowie: " + region.plotName);

        // Dodaj właściciela
        ItemStack ownerHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta ownerMeta = (SkullMeta) ownerHead.getItemMeta();
        ownerMeta.setDisplayName("§6§l" + region.owner + " §7(Właściciel)");
        ownerMeta.setOwningPlayer(Bukkit.getOfflinePlayer(region.owner));
        ownerHead.setItemMeta(ownerMeta);
        inv.setItem(4, ownerHead);

        // Dodaj zastępcę jeśli istnieje
        if (region.deputy != null) {
            ItemStack deputyHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta deputyMeta = (SkullMeta) deputyHead.getItemMeta();
            deputyMeta.setDisplayName("§e§l" + Bukkit.getOfflinePlayer(region.deputy).getName() + " §7(Zastępca)");
            deputyMeta.setOwningPlayer(Bukkit.getOfflinePlayer(region.deputy));
            deputyHead.setItemMeta(deputyMeta);
            inv.setItem(13, deputyHead);
        }

        // Dodaj członków
        int slot = 18;
        for (UUID memberId : region.invitedPlayers) {
            if (slot >= 54) {
                break;
            }

            ItemStack memberHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta memberMeta = (SkullMeta) memberHead.getItemMeta();
            OfflinePlayer member = Bukkit.getOfflinePlayer(memberId);
            memberMeta.setDisplayName("§a§l" + member.getName() + " §7(Członek)");
            memberMeta.setOwningPlayer(member);
            memberHead.setItemMeta(memberMeta);
            inv.setItem(slot++, memberHead);
        }

        // Przycisk powrotu
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§c« Powrót do panelu głównego");
        back.setItemMeta(backMeta);
        inv.setItem(49, back);

        player.openInventory(inv);
    }

    private void updateTimeForPlayersInRegion(ProtectedRegion region, Player player) {
        long time = region.isDay ? 1000L : 13000L;

        // Aktualizuj czas dla wszystkich graczy w regionie
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (getRegion(onlinePlayer.getLocation()) == region) {
                onlinePlayer.setPlayerTime(time, false);
            }
        }
    }
}
