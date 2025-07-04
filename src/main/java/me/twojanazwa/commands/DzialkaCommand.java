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
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import me.twojanazwa.points.PlotPointsManager;
// Suppress spell-check warnings for specific words
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
                    "zapros", "opusc", "zastepca", "admintp", "adminusun", "test", "debug"
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
                if (newRegion.intersects(region)) {
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

    // --- wklej poniżej w klasie DzialkaCommand, zamiast starego openPanel(...) ---
    private void openPanel(ProtectedRegion r, Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, "Panel Działki: " + r.plotName);

        // === SEKCJA INFORMACYJNA (góra, lewo) ===
        inv.setItem(0, item(Material.OAK_SIGN,
                "§dPodstawowe informacje",
                List.of(
                        "§7Właściciel: §e" + r.owner,
                        "§7Data utworzenia: §e" + new SimpleDateFormat("dd/MM/yyyy HH:mm")
                                .format(new Date(r.creationTime)),
                        "§7Punkty działki: §a" + r.points
                )
        ));

        // === SEKCJA ROLOWA (góra, środek) ===
        inv.setItem(4, head(r.owner, "Właściciel"));
        if (r.deputy != null) {
            OfflinePlayer d = Bukkit.getOfflinePlayer(r.deputy);
            inv.setItem(5, head(d.getName(), "Zastępca"));
        } else {
            inv.setItem(5, item(Material.GRAY_WOOL, "§7Brak zastępcy"));
        }

        // === SEKCJA TELEPORTACJI (góra, prawo) ===
        inv.setItem(8, item(Material.ENDER_PEARL, "§aTeleportuj na środek"));

        // === SEPARATOR ===
        for (int i = 9; i < 18; i++) {
            inv.setItem(i, item(Material.GRAY_STAINED_GLASS_PANE, "§7▬▬▬ UPRAWNIENIA GLOBALNE ▬▬▬"));
        }

        // === UPRAWNIENIA GLOBALNE (środkowe rzędy) ===
        // Pierwsza linia uprawnień
        inv.setItem(18, toggleItem(r.allowBuild, "Stawianie bloków", "Pozwala nieznajomym graczom stawiać bloki", Material.BRICKS));
        inv.setItem(19, toggleItem(r.allowDestroy, "Niszczenie bloków", "Pozwala nieznajomym graczom niszczyć bloki", Material.TNT));
        inv.setItem(20, toggleItem(r.allowChest, "Otwieranie skrzyń", "Pozwala nieznajomym graczom używać skrzyń", Material.CHEST));
        inv.setItem(21, toggleItem(r.allowFlight, "Latanie", "Pozwala nieznajomym graczom latać", Material.ELYTRA));
        inv.setItem(22, toggleItem(r.allowEnter, "Wejście na działkę", "Pozwala nieznajomym graczom wchodzić", Material.OAK_DOOR));
        inv.setItem(23, toggleItem(r.isDay, "Przełącz dzień/noc", "Ustawia czas na działce", Material.CLOCK));
        inv.setItem(24, toggleItem(r.allowPickup, "Podnoszenie itemów", "Pozwala nieznajomym graczom podnosić przedmioty", Material.HOPPER));
        inv.setItem(25, toggleItem(r.allowPotion, "Rzucanie mikstur", "Pozwala nieznajomym graczom używać mikstur", Material.SPLASH_POTION));
        inv.setItem(26, toggleItem(r.allowKillMobs, "Bicie mobów", "Pozwala nieznajomym graczom atakować moby", Material.IRON_SWORD));

        // Druga linia uprawnień
        inv.setItem(27, toggleItem(r.allowSpawnMobs, "Respienie mobów", "Pozwala nieznajomym graczom przyzywać moby", Material.ZOMBIE_SPAWN_EGG));
        inv.setItem(28, toggleItem(r.allowSpawnerBreak, "Niszczenie spawnerów", "Pozwala nieznajomym graczom niszczyć spawnery", Material.SPAWNER));
        inv.setItem(29, toggleItem(r.allowBeaconPlace, "Stawianie beaconów", "Pozwala nieznajomym graczom stawiać beacony", Material.BEACON));
        inv.setItem(30, toggleItem(r.allowBeaconBreak, "Niszczenie beaconów", "Pozwala nieznajomym graczom niszczyć beacony", Material.BEACON));

        // === ZAKŁADKA GRACZE ===
        inv.setItem(31, item(Material.PLAYER_HEAD, "§6§lGracze działki", List.of("§7Kliknij aby zarządzać", "§7uprawnieniami graczy")));

        // === ZAPROSZENI GRACZE (dół) ===
        int slotIndex = 36;
        for (UUID invitedUuid : r.invitedPlayers) {
            if (slotIndex >= 54) {
                break; // Zabezpieczenie przed przepełnieniem

            }
            OfflinePlayer invPlayer = Bukkit.getOfflinePlayer(invitedUuid);
            inv.setItem(slotIndex, head(invPlayer.getName(), "Zaproszony"));
            slotIndex++;
        }

        p.openInventory(inv);
    }

// === POD TE METODĄ openPanel DODAJ HELPER-Y: ===
    private ItemStack item(Material mat, String name) {
        return item(mat, name, Collections.emptyList());
    }

    private ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack is = new ItemStack(mat);
        ItemMeta m = is.getItemMeta();
        m.setDisplayName(name);
        if (!lore.isEmpty()) {
            m.setLore(lore);
        }
        is.setItemMeta(m);
        return is;
    }

    private ItemStack head(String playerName, String role) {
        OfflinePlayer off = Bukkit.getOfflinePlayer(playerName);
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) skull.getItemMeta();
        m.setOwningPlayer(off);
        m.setDisplayName("§e" + playerName);
        m.setLore(List.of("§7Rola: §f" + role, "§7Kliknij, aby..."));
        skull.setItemMeta(m);
        return skull;
    }

    // zamiast Material.GREEN_CONCRETE / RED_CONCRETE
    private ItemStack toggleItem(boolean on, String name) {
        Material mat = on ? Material.LIME_WOOL : Material.RED_WOOL;
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name + ": " + (on ? "§aWŁ." : "§cWYŁ."));
        m.setLore(Collections.singletonList(
                "§7Kliknij, aby " + (on ? "zablokować" : "odblokować")
        ));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack toggleItem(boolean on, String name, String description) {
        Material mat = on ? Material.LIME_WOOL : Material.RED_WOOL;
        ItemStack is = new ItemStack(mat);
        ItemMeta m = is.getItemMeta();
        m.setDisplayName(name + ": " + (on ? "§aWŁ." : "§cWYŁ."));
        m.setLore(List.of("§7" + description, "§e§lKliknij aby przełączyć!"));
        is.setItemMeta(m);
        return is;
    }

    private ItemStack toggleItem(boolean on, String name, String description, Material material) {
        // Użyj kolorowej wełny do oznaczeń włączenia/wyłączenia, ale zachowaj oryginalny materiał w nazwie
        Material displayMaterial = on ? Material.LIME_WOOL : Material.RED_WOOL;
        ItemStack is = new ItemStack(displayMaterial);
        ItemMeta m = is.getItemMeta();
        m.setDisplayName("§f" + name + ": " + (on ? "§aWŁ." : "§cWYŁ."));
        m.setLore(List.of("§7" + description, "§e§lKliknij aby przełączyć!", "§8Material: " + material.name()));
        is.setItemMeta(m);
        return is;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Upewnij się, że to gracz
        if (!(event.getWhoClicked() instanceof Player p)) {
            return;
        }

        String title = event.getView().getTitle();

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

        // Tylko panel działki
        if (!title.startsWith("Panel Działki: ")) {
            return;
        }

        // Zablokuj wyciąganie itemów
        event.setCancelled(true);

        ItemStack it = event.getCurrentItem();
        if (it == null || !it.hasItemMeta()) {
            return;
        }

        String name = it.getItemMeta().getDisplayName();
        String plotName = title.substring("Panel Działki: ".length());
        ProtectedRegion region = getRegionByName(plotName);
        if (region == null) {
            return;
        }

        // — zakładka „Zaproszeni gracze” —  
        if (name.equals("§eZaproszeni gracze")) {
            // nic nie robimy, heady zaproszonych są już widoczne
            return;
        }

        // — teleport —  
        if (name.equals("§aTeleportuj na środek")) {
            p.teleport(region.center.clone().add(0.5, 1, 0.5));
            p.sendMessage("§aTeleport na środek działki!");
            return;
        }

        // — zakładka gracze —  
        if (name.equals("§6§lGracze działki")) {
            if (!region.owner.equals(p.getName())) {
                p.sendMessage("§cTylko właściciel może zarządzać uprawnieniami graczy!");
                return;
            }
            openPlayersPanel(region, p);
            return;
        }

        // — zasady przyznawania punktów —  
        if (it.getType() == Material.EMERALD && name.equals("§bZasady przyznawania punktów")) {
            p.closeInventory();
            p.sendMessage("§eZasady przyznawania punktów:");
            p.sendMessage(" §7+2 pkt – postawienie bloku");
            p.sendMessage(" §7+1 pkt – interakcja gościa");
            p.sendMessage(" §7… kolejne zasady …");
            return;
        }

        // — togglery uprawnień —  
        switch (it.getType()) {
            case LIME_WOOL, RED_WOOL -> {
                if (name.contains("Stawianie")) {
                    region.allowBuild = !region.allowBuild;
                    p.sendMessage(region.allowBuild
                            ? "§aStawianie bloków odblokowane"
                            : "§cStawianie bloków zablokowane");
                } else if (name.contains("Niszczenie bloków")) {
                    region.allowDestroy = !region.allowDestroy;
                    p.sendMessage(region.allowDestroy
                            ? "§aNiszczenie bloków odblokowane"
                            : "§cNiszczenie bloków zablokowane");
                } else if (name.contains("Otwieranie skrzyń")) {
                    region.allowChest = !region.allowChest;
                    p.sendMessage(region.allowChest
                            ? "§aOtwieranie skrzyń odblokowane"
                            : "§cOtwieranie skrzyń zablokowane");
                } else if (name.contains("Latanie")) {
                    region.allowFlight = !region.allowFlight;
                    p.sendMessage(region.allowFlight
                            ? "§aLatanie odblokowane"
                            : "§cLatanie zablokowane");
                } else if (name.contains("Wejście")) {
                    region.allowEnter = !region.allowEnter;
                    p.sendMessage(region.allowEnter
                            ? "§aWejście na działkę odblokowane"
                            : "§cWejście na działkę zablokowane");
                } else if (name.contains("Podnoszenie")) {
                    region.allowPickup = !region.allowPickup;
                    p.sendMessage(region.allowPickup
                            ? "§aPodnoszenie itemów odblokowane"
                            : "§cPodnoszenie itemów zablokowane");
                } else if (name.contains("Rzucanie mikstur")) {
                    region.allowPotion = !region.allowPotion;
                    p.sendMessage(region.allowPotion
                            ? "§aRzucanie mikstur odblokowane"
                            : "§cRzucanie mikstur zablokowane");
                } else if (name.contains("Bicie mobów")) {
                    region.allowKillMobs = !region.allowKillMobs;
                    p.sendMessage(region.allowKillMobs
                            ? "§aBicie mobów odblokowane"
                            : "§cBicie mobów zablokowane");
                } else if (name.contains("Respienie mobów")) {
                    region.allowSpawnMobs = !region.allowSpawnMobs;
                    p.sendMessage(region.allowSpawnMobs
                            ? "§aRespienie mobów odblokowane"
                            : "§cRespienie mobów zablokowane");
                } else if (name.contains("Niszczenie spawnerów")) {
                    region.allowSpawnerBreak = !region.allowSpawnerBreak;
                    p.sendMessage(region.allowSpawnerBreak
                            ? "§aNiszczenie spawnerów odblokowane"
                            : "§cNiszczenie spawnerów zablokowane");
                } else if (name.contains("Stawianie beaconów")) {
                    region.allowBeaconPlace = !region.allowBeaconPlace;
                    p.sendMessage(region.allowBeaconPlace
                            ? "§aStawianie beaconów odblokowane"
                            : "§cStawianie beaconów zablokowane");
                } else if (name.contains("Niszczenie beaconów")) {
                    region.allowBeaconBreak = !region.allowBeaconBreak;
                    p.sendMessage(region.allowBeaconBreak
                            ? "§aNiszczenie beaconów odblokowane"
                            : "§cNiszczenie beaconów zablokowane");
                } else if (name.contains("Przełącz dzień/noc")) {
                    region.isDay = !region.isDay;

                    // Aktualizuj czas dla wszystkich graczy na działce
                    updateTimeForPlayersInRegion(region, p);

                    p.sendMessage(region.isDay
                            ? "§aWłączyłeś dzień na tej działce."
                            : "§aWłączyłeś noc na tej działce.");
                }
                savePlots();
                openPanel(region, p);
                return;
            }
            default -> {
                /* inne itemy */ }
        }

        // — ustawianie zastępcy przez kliknięcie w head zaproszonego —  
        if (it.getType() == Material.PLAYER_HEAD && region.invitedPlayers.contains(((SkullMeta) it.getItemMeta()).getOwningPlayer().getUniqueId())) {
            // Sprawdź uprawnienie
            if (!p.hasPermission("dzialkiplugin.zastepca")) {
                p.sendMessage("§cNie masz uprawnień do ustawiania zastępcy!");
                return;
            }
            String nick = ChatColor.stripColor(name);
            OfflinePlayer off = Bukkit.getOfflinePlayer(nick);
            region.deputy = off.getUniqueId();
            savePlots();
            p.sendMessage("§a" + nick + " jest teraz zastępcą działki '" + plotName + "'.");
            openPanel(region, p);
        }
    }

    @EventHandler
    public void onTopPanelClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!event.getView().getTitle().startsWith("Ranking działek: Strona ")) {
            return;
        }

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        ItemMeta itemMeta = clickedItem.getItemMeta();
        String displayName = (itemMeta != null) ? itemMeta.getDisplayName() : null;
        String title = event.getView().getTitle();
        int currentPage = Integer.parseInt(title.split(" ")[2]);

        if ("§aPoprzednia strona".equals(displayName)) {
            openTopPanel(player, currentPage - 1);
        } else if ("§aNastępna strona".equals(displayName)) {
            openTopPanel(player, currentPage + 1);
        }
    }

    // 1) podnoszenie itemów
    @EventHandler
    public void onPickup(EntityPickupItemEvent ev) {
        if (!(ev.getEntity() instanceof Player p)) {
            return;
        }
        ProtectedRegion r = getRegion(p.getLocation());
        if (r != null && !hasPermission(r, p, "pickup")) {
            ev.setCancelled(true);
        }
    }

    // 2) rzucanie mikstur
    @EventHandler
    public void onPotionSplash(PotionSplashEvent ev) {
        if (!(ev.getEntity().getShooter() instanceof Player p)) {
            return;
        }
        ProtectedRegion r = getRegion(p.getLocation());
        if (r != null && !hasPermission(r, p, "potion")) {
            ev.setCancelled(true);
        }
    }

    // 3) bicie mobów
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent ev) {
        if (!(ev.getDamager() instanceof Player p)) {
            return;
        }
        ProtectedRegion r = getRegion(p.getLocation());
        if (r != null && ev.getEntity() instanceof LivingEntity
                && !hasPermission(r, p, "killmobs")) {
            ev.setCancelled(true);
        }
    }

    // 4) respienie mobów
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent ev) {
        if (ev.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return;
        }
        Location loc = ev.getLocation();
        ProtectedRegion r = getRegion(loc);

        // Znajdź gracza, który używa spawn egga (w pobliżu)
        Player nearestPlayer = null;
        double minDistance = Double.MAX_VALUE;
        for (Player p : loc.getWorld().getPlayers()) {
            double distance = p.getLocation().distance(loc);
            if (distance < minDistance && distance < 10) { // w promieniu 10 bloków
                minDistance = distance;
                nearestPlayer = p;
            }
        }

        if (r != null && nearestPlayer != null && !hasPermission(r, nearestPlayer, "spawnmobs")) {
            ev.setCancelled(true);
        }
    }

    // 5) niszczenie spawnerów i beaconów
    @EventHandler
    public void onBlockBreak(BlockBreakEvent ev) {
        Player p = ev.getPlayer();
        ProtectedRegion r = getRegion(p.getLocation());
        if (r != null) {
            Material m = ev.getBlock().getType();
            if (m == Material.SPAWNER && !hasPermission(r, p, "spawnerbreak")) {
                ev.setCancelled(true);
                return;
            } else if (m == Material.BEACON && !hasPermission(r, p, "beaconbreak")) {
                ev.setCancelled(true);
                return;
            } else if (m != Material.SPAWNER && m != Material.BEACON && !hasPermission(r, p, "destroy")) {
                ev.setCancelled(true);
                return;
            }
        }
        // ...istniejąca logika punktów lub inne...
    }

    // 6) stawianie beaconów
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent ev) {
        Player p = ev.getPlayer();
        ProtectedRegion r = getRegion(p.getLocation());
        if (r != null) {
            Material m = ev.getBlock().getType();
            if (m == Material.BEACON && !hasPermission(r, p, "beaconplace")) {
                ev.setCancelled(true);
                return;
            } else if (m != Material.BEACON && !hasPermission(r, p, "build")) {
                ev.setCancelled(true);
                return;
            }
        }
        // … istniejąca logika punktów …
        // ...existing code for points, etc...
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ProtectedRegion region = getRegion(player.getLocation());
        // Poza działką nie robimy nic, nie wysyłamy żadnych wiadomości
        if (region == null) {
            return;
        }

        // Jeśli nie jesteś właścicielem ani zaproszonym, zablokuj interakcję
        if (!region.owner.equals(player.getName())
                && !region.invitedPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Jeśli zaproszony gracz coś robi, daj 1 punkt
        int added = PlotPointsManager.handlePlayerInteract(this, region, player);
        if (added > 0) {
            player.sendMessage("§aDodano §b" + added + " pkt §ado działki '" + region.plotName + "'!");
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent ev) {
        Player p = ev.getPlayer();
        ProtectedRegion now = getRegion(p.getLocation());
        ProtectedRegion prev = getRegion(ev.getFrom());

        // gracz wchodzi na nową działkę lub pozostaje na tej samej
        if (now != null && now != prev) {
            showBossBar(now, p);
        } // gracz wychodzi z działki
        else if (now == null && prev != null) {
            BossBar bar = getBossBar(p);
            if (bar != null) {
                bar.setVisible(false);
                bar.removePlayer(p);
            }
        } // gracz pozostaje na tej samej działce - upewnij się że bossbar jest widoczny
        else if (now != null && now == prev) {
            BossBar bar = getBossBar(p);
            if (bar != null && !bar.isVisible()) {
                showBossBar(now, p);
            }
        }
    }

    public void showBossBar(ProtectedRegion region, Player player) {
        BossBar bossBar = bossBary.get(player.getUniqueId());

        if (bossBar == null) {
            bossBar = Bukkit.createBossBar(
                    "§eDziałka: §a" + region.plotName + " §e| Właściciel: §a" + region.owner,
                    BarColor.YELLOW,
                    BarStyle.SOLID
            );
            bossBary.put(player.getUniqueId(), bossBar);
        }

        // Zawsze aktualizuj tytuł i dodaj gracza
        bossBar.setTitle("§eDziałka: §a" + region.plotName + " §e| Właściciel: §a" + region.owner);
        if (!bossBar.getPlayers().contains(player)) {
            bossBar.addPlayer(player);
        }
        bossBar.setVisible(true);
    }

    public void showBoundaryParticles(ProtectedRegion region, Player player) {
        World world = player.getWorld();
        // Ustalamy wysokość, na której chcemy pokazać cząsteczki, np. kilka bloków nad środkiem działki
        double particleY = region.center.getY() + 1;

        // Wyświetlamy cząsteczki na górnych i dolnych granicach działki
        for (int x = region.minX; x <= region.maxX; x++) {
            Location locTop = new Location(world, x + 0.5, particleY, region.minZ + 0.5);
            world.spawnParticle(Particle.FLAME, locTop, 5, 0.2, 0, 0.2, 0);
            Location locBot = new Location(world, x + 0.5, particleY, region.maxZ + 0.5);
            world.spawnParticle(Particle.FLAME, locBot, 5, 0.2, 0.2, 0);
        }

        // Wyświetlamy cząsteczki na lewych i prawych granicach działki
        for (int z = region.minZ; z <= region.maxZ; z++) {
            Location locLeft = new Location(world, region.minX + 0.5, particleY, z + 0.5);
            world.spawnParticle(Particle.FLAME, locLeft, 5, 0.2, 0, 0.2, 0);
            Location locRight = new Location(world, region.maxX + 0.5, particleY, z + 0.5);
            world.spawnParticle(Particle.FLAME, locRight, 5, 0.2, 0, 0.2, 0);
        }
    }

    public void scheduleBoundaryParticles(ProtectedRegion region, Player player) {
        // Anuluj poprzednie zadanie dla tej działki
        stopParticles(region);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                World world = player.getWorld();

                int yStart = region.center.getBlockY();  // start na wysokości centrum działki
                int yMin = region.minY;
                int yMax = region.maxY;
                int yStep = 5;    // co 5 bloków nowy poziomy pasek
                int edgeStep = 2; // co 2 bloki cząsteczka na krawędzi

                int xMin = region.minX;
                int xMax = region.maxX;
                int zMin = region.minZ;
                int zMax = region.maxZ;

                // od yStart w górę
                for (int y = yStart; y <= yMax; y += yStep) {
                    // przednia i tylna krawędź
                    for (int x = xMin; x <= xMax; x += edgeStep) {
                        world.spawnParticle(Particle.FLAME, x + 0.5, y, zMin + 0.5, 1, 0, 0, 0, 0);
                        world.spawnParticle(Particle.FLAME, x + 0.5, y, zMax + 0.5, 1, 0, 0, 0, 0);
                    }
                    // lewa i prawa krawędź
                    for (int z = zMin; z <= zMax; z += edgeStep) {
                        world.spawnParticle(Particle.FLAME, xMin + 0.5, y, z + 0.5, 1, 0, 0, 0, 0);
                        world.spawnParticle(Particle.FLAME, xMax + 0.5, y, z + 0.5, 1, 0, 0, 0, 0);
                    }
                }
                // od yStart w dół
                for (int y = yStart - yStep; y >= yMin; y -= yStep) {
                    for (int x = xMin; x <= xMax; x += edgeStep) {
                        world.spawnParticle(Particle.FLAME, x + 0.5, y, zMin + 0.5, 1, 0, 0, 0, 0);
                        world.spawnParticle(Particle.FLAME, x + 0.5, y, zMax + 0.5, 1, 0, 0, 0, 0);
                    }
                    for (int z = zMin; z <= zMax; z += edgeStep) {
                        world.spawnParticle(Particle.FLAME, xMin + 0.5, y, z + 0.5, 1, 0, 0, 0, 0);
                        world.spawnParticle(Particle.FLAME, xMax + 0.5, y, z + 0.5, 1, 0, 0, 0, 0);
                    }
                }
            }
        };

        // Uruchom co 20 ticków (~1s)
        task.runTaskTimer(plugin, 0L, 20L);
        particleTasks.put(region, task);
    }

    // Displays the top plots ranking panel to the player, paginated by page number
    public void openTopPanel(Player player, int page) {
        // Gather all plots into a single list
        List<ProtectedRegion> allPlots = new ArrayList<>();
        for (List<ProtectedRegion> list : dzialki.values()) {
            allPlots.addAll(list);
        }
        // Sort by points descending
        allPlots.sort((a, b) -> Integer.compare(b.points, a.points));

        int plotsPerPage = 9;
        int totalPages = (int) Math.ceil((double) allPlots.size() / plotsPerPage);
        if (page < 1) {
            page = 1;
        }
        if (page > totalPages) {
            page = totalPages;
        }

        Inventory inv = Bukkit.createInventory(null, 27, "Ranking działek: Strona " + page);

        int start = (page - 1) * plotsPerPage;
        int end = Math.min(start + plotsPerPage, allPlots.size());

        for (int i = start; i < end; i++) {
            ProtectedRegion region = allPlots.get(i);
            ItemStack item = new ItemStack(Material.GRASS_BLOCK);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§e#" + (i + 1) + " §a" + region.plotName);
            List<String> lore = new ArrayList<>();
            lore.add("§7Właściciel: §f" + region.owner);
            lore.add("§7Punkty: §b" + region.points);
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(10 + (i - start), item);
        }

        // Navigation buttons
        if (page > 1) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            prevMeta.setDisplayName("§aPoprzednia strona");
            prev.setItemMeta(prevMeta);
            inv.setItem(18, prev);
        }
        if (page < totalPages) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            nextMeta.setDisplayName("§aNastępna strona");
            next.setItemMeta(nextMeta);
            inv.setItem(26, next);
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        BossBar bossBar = bossBary.get(player.getUniqueId());
        if (bossBar != null) {
            bossBar.removePlayer(player);
            bossBar.setVisible(false);
            bossBary.remove(player.getUniqueId());
        }
    }

    public static class ProtectedRegion {

        int minX, maxX, minZ, maxZ;
        int minY, maxY;
        Location center;
        public String owner;
        public String plotName;
        long creationTime;
        public List<UUID> invitedPlayers = new ArrayList<>();
        public Location warp = null;
        public int points = 0;
        public UUID deputy = null;
        public boolean allowBuild = true;
        public boolean allowDestroy = true;
        public boolean allowChest = true;
        public boolean allowFlight = false;
        public boolean allowEnter = true;
        public boolean isDay = true; // domyślnie dzień
        public boolean allowPickup = false;  // podnoszenie itemów
        public boolean allowPotion = false;  // rzucanie mikstur
        public boolean allowKillMobs = false;  // bicie mobów
        public boolean allowSpawnMobs = false;  // respienie mobów
        public boolean allowSpawnerBreak = false;  // niszczenie spawnerów
        public boolean allowBeaconPlace = false;  // stawianie beaconów
        public boolean allowBeaconBreak = false;  // niszczenie beaconów

        // Indywidualne uprawnienia dla graczy
        public Map<UUID, PlayerPermissions> playerPermissions = new HashMap<>();

        public ProtectedRegion(int minX, int maxX, int minZ, int maxZ, int minY, int maxY, Location center, String owner, String plotName, long creationTime) {
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

        public boolean intersects(ProtectedRegion other) {
            return this.minX <= other.maxX && this.maxX >= other.minX
                    && this.minZ <= other.maxZ && this.maxZ >= other.minZ
                    && this.minY <= other.maxY && this.maxY >= other.minY;
        }

        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    public static class PlayerPermissions {

        public boolean allowBuild = true;
        public boolean allowDestroy = true;
        public boolean allowChest = true;
        public boolean allowFlight = false;
        public boolean allowPickup = false;
        public boolean allowPotion = false;
        public boolean allowKillMobs = false;
        public boolean allowSpawnMobs = false;
        public boolean allowSpawnerBreak = false;
        public boolean allowBeaconPlace = false;
        public boolean allowBeaconBreak = false;

        public PlayerPermissions() {
        }
    }

    private void openPlayersPanel(ProtectedRegion region, Player owner) {
        Inventory inv = Bukkit.createInventory(null, 54, "Gracze: " + region.plotName);

        // === WŁAŚCICIEL ===
        inv.setItem(4, head(region.owner, "Właściciel"));

        // === ZASTĘPCA ===
        if (region.deputy != null) {
            OfflinePlayer deputy = Bukkit.getOfflinePlayer(region.deputy);
            inv.setItem(5, head(deputy.getName(), "Zastępca"));
        }

        // === ZAPROSZENI GRACZE ===
        int slot = 9;
        for (UUID playerUuid : region.invitedPlayers) {
            if (slot >= 54) {
                break;
            }

            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
            ItemStack playerHead = head(player.getName(), "Zaproszony");

            // Dodaj informacje o uprawnieniach do lore
            ItemMeta meta = playerHead.getItemMeta();
            List<String> lore = new ArrayList<>(meta.getLore());
            lore.add("§7Kliknij aby zarządzać uprawnieniami");
            meta.setLore(lore);
            playerHead.setItemMeta(meta);

            inv.setItem(slot, playerHead);
            slot++;
        }

        // Przycisk powrotu
        inv.setItem(49, item(Material.ARROW, "§c« Powrót", List.of("§7Wróć do głównego panelu")));

        owner.openInventory(inv);
    }

    // === OBSŁUGA KLIKNIĘĆ W PANELU GRACZY ===
    private void handlePlayersPanel(InventoryClickEvent event, Player player, String title) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        String plotName = title.substring("Gracze: ".length());
        ProtectedRegion region = getRegionByName(plotName);
        if (region == null) {
            return;
        }

        String displayName = clickedItem.getItemMeta().getDisplayName();

        // Przycisk powrotu
        if (displayName.equals("§c« Powrót")) {
            openPanel(region, player);
            return;
        }

        // Kliknięcie w główkę gracza
        if (clickedItem.getType() == Material.PLAYER_HEAD) {
            SkullMeta skullMeta = (SkullMeta) clickedItem.getItemMeta();
            if (skullMeta != null && skullMeta.getOwningPlayer() != null) {
                UUID clickedPlayerUuid = skullMeta.getOwningPlayer().getUniqueId();

                // Sprawdź czy to zaproszony gracz (nie właściciel ani zastępca)
                if (region.invitedPlayers.contains(clickedPlayerUuid)) {
                    openPlayerPermissionsPanel(region, clickedPlayerUuid, player);
                } else {
                    player.sendMessage("§cMożesz zarządzać tylko uprawnieniami zaproszonych graczy!");
                }
            }
        }
    }

    // === OBSŁUGA KLIKNIĘĆ W PANELU UPRAWNIEŃ GRACZA ===
    private void handlePlayerPermissionsPanel(InventoryClickEvent event, Player player, String title) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        String playerName = title.substring("Uprawnienia: ".length());
        String displayName = clickedItem.getItemMeta().getDisplayName();

        // Przycisk powrotu
        if (displayName.equals("§c« Powrót")) {
            // Znajdź region na podstawie otwartego panelu
            // Musimy znaleźć region, w którym jest ten gracz
            OfflinePlayer targetPlayer = null;
            for (OfflinePlayer offPlayer : Bukkit.getOfflinePlayers()) {
                if (offPlayer.getName() != null && offPlayer.getName().equals(playerName)) {
                    targetPlayer = offPlayer;
                    break;
                }
            }

            if (targetPlayer != null) {
                // Znajdź region gdzie ten gracz jest zaproszony
                for (List<ProtectedRegion> regions : dzialki.values()) {
                    for (ProtectedRegion region : regions) {
                        if (region.invitedPlayers.contains(targetPlayer.getUniqueId())) {
                            openPlayersPanel(region, player);
                            return;
                        }
                    }
                }
            }
            return;
        }

        // Znajdź region i gracza
        OfflinePlayer targetPlayer = null;
        ProtectedRegion targetRegion = null;

        for (OfflinePlayer offPlayer : Bukkit.getOfflinePlayers()) {
            if (offPlayer.getName() != null && offPlayer.getName().equals(playerName)) {
                targetPlayer = offPlayer;
                break;
            }
        }

        if (targetPlayer != null) {
            for (List<ProtectedRegion> regions : dzialki.values()) {
                for (ProtectedRegion region : regions) {
                    if (region.invitedPlayers.contains(targetPlayer.getUniqueId())) {
                        targetRegion = region;
                        break;
                    }
                }
                if (targetRegion != null) {
                    break;
                }
            }
        }

        if (targetRegion == null || targetPlayer == null) {
            player.sendMessage("§cBłąd: Nie można znaleźć gracza lub działki!");
            return;
        }

        PlayerPermissions perms = targetRegion.playerPermissions.computeIfAbsent(targetPlayer.getUniqueId(), k -> new PlayerPermissions());

        // Obsługa przełączania uprawnień
        Material matType = clickedItem.getType();
        if (matType == Material.LIME_WOOL || matType == Material.RED_WOOL) {
            if (displayName.contains("Stawianie bloków")) {
                perms.allowBuild = !perms.allowBuild;
                player.sendMessage(perms.allowBuild
                        ? "§aStawianie bloków dla " + playerName + " odblokowane"
                        : "§cStawianie bloków dla " + playerName + " zablokowane");
            } else if (displayName.contains("Niszczenie bloków")) {
                perms.allowDestroy = !perms.allowDestroy;
                player.sendMessage(perms.allowDestroy
                        ? "§aNiszczenie bloków dla " + playerName + " odblokowane"
                        : "§cNiszczenie bloków dla " + playerName + " zablokowane");
            } else if (displayName.contains("Otwieranie skrzyń")) {
                perms.allowChest = !perms.allowChest;
                player.sendMessage(perms.allowChest
                        ? "§aOtwieranie skrzyń dla " + playerName + " odblokowane"
                        : "§cOtwieranie skrzyń dla " + playerName + " zablokowane");
            } else if (displayName.contains("Latanie")) {
                perms.allowFlight = !perms.allowFlight;
                player.sendMessage(perms.allowFlight
                        ? "§aLatanie dla " + playerName + " odblokowane"
                        : "§cLatanie dla " + playerName + " zablokowane");
            } else if (displayName.contains("Podnoszenie")) {
                perms.allowPickup = !perms.allowPickup;
                player.sendMessage(perms.allowPickup
                        ? "§aPodnoszenie itemów dla " + playerName + " odblokowane"
                        : "§cPodnoszenie itemów dla " + playerName + " zablokowane");
            } else if (displayName.contains("Rzucanie mikstur")) {
                perms.allowPotion = !perms.allowPotion;
                player.sendMessage(perms.allowPotion
                        ? "§aRzucanie mikstur dla " + playerName + " odblokowane"
                        : "§cRzucanie mikstur dla " + playerName + " zablokowane");
            } else if (displayName.contains("Bicie mobów")) {
                perms.allowKillMobs = !perms.allowKillMobs;
                player.sendMessage(perms.allowKillMobs
                        ? "§aBicie mobów dla " + playerName + " odblokowane"
                        : "§cBicie mobów dla " + playerName + " zablokowane");
            } else if (displayName.contains("Respienie mobów")) {
                perms.allowSpawnMobs = !perms.allowSpawnMobs;
                player.sendMessage(perms.allowSpawnMobs
                        ? "§aRespienie mobów dla " + playerName + " odblokowane"
                        : "§cRespienie mobów dla " + playerName + " zablokowane");
            } else if (displayName.contains("Niszczenie spawnerów")) {
                perms.allowSpawnerBreak = !perms.allowSpawnerBreak;
                player.sendMessage(perms.allowSpawnerBreak
                        ? "§aNiszczenie spawnerów dla " + playerName + " odblokowane"
                        : "§cNiszczenie spawnerów dla " + playerName + " zablokowane");
            } else if (displayName.contains("Stawianie beaconów")) {
                perms.allowBeaconPlace = !perms.allowBeaconPlace;
                player.sendMessage(perms.allowBeaconPlace
                        ? "§aStawianie beaconów dla " + playerName + " odblokowane"
                        : "§cStawianie beaconów dla " + playerName + " zablokowane");
            } else if (displayName.contains("Niszczenie beaconów")) {
                perms.allowBeaconBreak = !perms.allowBeaconBreak;
                player.sendMessage(perms.allowBeaconBreak
                        ? "§aNiszczenie beaconów dla " + playerName + " odblokowane"
                        : "§cNiszczenie beaconów dla " + playerName + " zablokowane");
            }

            // Zapisz zmiany i odśwież panel
            savePlots();
            openPlayerPermissionsPanel(targetRegion, targetPlayer.getUniqueId(), player);
        }
    }

    // === PANEL UPRAWNIĘT INDYWIDUALNEGO GRACZA ===
    private void openPlayerPermissionsPanel(ProtectedRegion region, UUID playerId, Player owner) {
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerId);
        PlayerPermissions perms = region.playerPermissions.computeIfAbsent(playerId, k -> new PlayerPermissions());

        Inventory inv = Bukkit.createInventory(null, 36, "Uprawnienia: " + targetPlayer.getName());

        // === UPRAWNIENIA GRACZA ===
        inv.setItem(0, toggleItem(perms.allowBuild, "Stawianie bloków", "Pozwala graczowi stawiać bloki", Material.BRICKS));
        inv.setItem(1, toggleItem(perms.allowDestroy, "Niszczenie bloków", "Pozwala graczowi niszczyć bloki", Material.TNT));
        inv.setItem(2, toggleItem(perms.allowChest, "Otwieranie skrzyń", "Pozwala graczowi używać skrzyń", Material.CHEST));
        inv.setItem(3, toggleItem(perms.allowFlight, "Latanie", "Pozwala graczowi latać", Material.ELYTRA));
        inv.setItem(4, toggleItem(perms.allowPickup, "Podnoszenie itemów", "Pozwala graczowi podnosić przedmioty", Material.HOPPER));
        inv.setItem(5, toggleItem(perms.allowPotion, "Rzucanie mikstur", "Pozwala graczowi używać mikstur", Material.SPLASH_POTION));
        inv.setItem(6, toggleItem(perms.allowKillMobs, "Bicie mobów", "Pozwala graczowi atakować moby", Material.IRON_SWORD));
        inv.setItem(7, toggleItem(perms.allowSpawnMobs, "Respienie mobów", "Pozwala graczowi przyzywać moby", Material.ZOMBIE_SPAWN_EGG));
        inv.setItem(8, toggleItem(perms.allowSpawnerBreak, "Niszczenie spawnerów", "Pozwala graczowi niszczyć spawnery", Material.SPAWNER));
        inv.setItem(9, toggleItem(perms.allowBeaconPlace, "Stawianie beaconów", "Pozwala graczowi stawiać beacony", Material.BEACON));
        inv.setItem(10, toggleItem(perms.allowBeaconBreak, "Niszczenie beaconów", "Pozwala graczowi niszczyć beacony", Material.BEACON));

        // Główka gracza
        inv.setItem(13, head(targetPlayer.getName(), "Zarządzane uprawnienia"));

        // Przycisk powrotu
        inv.setItem(31, item(Material.ARROW, "§c« Powrót", List.of("§7Wróć do listy graczy")));

        owner.openInventory(inv);
    }

    // === HELPER DO SPRAWDZANIA UPRAWNIEŃ ===
    private boolean hasPermission(ProtectedRegion region, Player player, String permissionType) {
        // Właściciel i zastępca zawsze mają wszystkie uprawnienia
        if (region.owner.equals(player.getName())
                || (region.deputy != null && region.deputy.equals(player.getUniqueId()))) {
            return true;
        }

        // Sprawdź czy gracz jest zaproszony
        if (!region.invitedPlayers.contains(player.getUniqueId())) {
            // Jeśli nie jest zaproszony, sprawdź tylko globalne uprawnienia
            return getGlobalPermission(region, permissionType);
        }

        // Gracz jest zaproszony - sprawdź indywidualne uprawnienia
        PlayerPermissions playerPerms = region.playerPermissions.get(player.getUniqueId());
        if (playerPerms != null) {
            // Ma ustawione indywidualne uprawnienia
            return getPlayerPermission(playerPerms, permissionType);
        } else {
            // Nie ma indywidualnych uprawnień, użyj globalnych
            return getGlobalPermission(region, permissionType);
        }
    }

    private boolean getGlobalPermission(ProtectedRegion region, String permissionType) {
        return switch (permissionType) {
            case "build" ->
                region.allowBuild;
            case "destroy" ->
                region.allowDestroy;
            case "chest" ->
                region.allowChest;
            case "flight" ->
                region.allowFlight;
            case "pickup" ->
                region.allowPickup;
            case "potion" ->
                region.allowPotion;
            case "killmobs" ->
                region.allowKillMobs;
            case "spawnmobs" ->
                region.allowSpawnMobs;
            case "spawnerbreak" ->
                region.allowSpawnerBreak;
            case "beaconplace" ->
                region.allowBeaconPlace;
            case "beaconbreak" ->
                region.allowBeaconBreak;
            default ->
                false;
        };
    }

    private boolean getPlayerPermission(PlayerPermissions perms, String permissionType) {
        return switch (permissionType) {
            case "build" ->
                perms.allowBuild;
            case "destroy" ->
                perms.allowDestroy;
            case "chest" ->
                perms.allowChest;
            case "flight" ->
                perms.allowFlight;
            case "pickup" ->
                perms.allowPickup;
            case "potion" ->
                perms.allowPotion;
            case "killmobs" ->
                perms.allowKillMobs;
            case "spawnmobs" ->
                perms.allowSpawnMobs;
            case "spawnerbreak" ->
                perms.allowSpawnerBreak;
            case "beaconplace" ->
                perms.allowBeaconPlace;
            case "beaconbreak" ->
                perms.allowBeaconBreak;
            default ->
                false;
        };
    }

    // === HELPER DO AKTUALIZACJI CZASU DLA WSZYSTKICH GRACZY NA DZIAŁCE ===
    private void updateTimeForPlayersInRegion(ProtectedRegion region, Player triggeredBy) {
        long t = region.isDay ? 1000L : 13000L;

        // Znajdź wszystkich graczy na tym świecie i sprawdź czy są na tej działce
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(region.center.getWorld())) {
                Location playerLoc = player.getLocation();
                if (region.contains(playerLoc.getBlockX(), playerLoc.getBlockY(), playerLoc.getBlockZ())) {
                    player.setPlayerTime(t, false);

                    // Powiadom gracza o zmianie czasu (oprócz tego który przełączył)
                    if (!player.equals(triggeredBy)) {
                        player.sendMessage(region.isDay
                                ? "§eWłączono dzień na tej działce"
                                : "§eWłączono noc na tej działce");
                    }
                }
            }
        }
    }
}
