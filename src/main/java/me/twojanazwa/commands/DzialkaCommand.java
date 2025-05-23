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
                    List<ProtectedRegion> playerPlots = dzialki.getOrDefault(gracz.getUniqueId(), Collections.emptyList());
                    if (playerPlots.isEmpty()) {
                        gracz.sendMessage("§cNie posiadasz żadnej działki.");
                        return true;
                    }
                    gracz.sendMessage("§aTwoje działki:");
                    for (ProtectedRegion r : playerPlots) {
                        gracz.sendMessage(" §e" + r.plotName);
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
                    gracz.sendMessage("§aUstawiono '" + nick + "' jako zastępcę działki '" + nazwa + "'.");
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
                    "zapros", "opusc", "zastepca", "admintp", "adminusun"
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
        File file = new File(plugin.getDataFolder(), "plots.yml");
        YamlConfiguration config = new YamlConfiguration();

        for (UUID uuid : dzialki.keySet()) {
            List<ProtectedRegion> regionList = dzialki.get(uuid);
            String key = uuid.toString();
            if (regionList != null) {
                for (int i = 0; i < regionList.size(); i++) {
                    ProtectedRegion r = regionList.get(i);
                    // 3. savePlots() – pomiń uszkodzony region
                    if (r.plotName == null) {
                        continue;
                    }
                    String regionKey = key + "." + i;
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
                    config.set(regionKey + ".allowPickup", r.allowPickup);
                    config.set(regionKey + ".allowPotion", r.allowPotion);
                    config.set(regionKey + ".allowKillMobs", r.allowKillMobs);
                    config.set(regionKey + ".allowSpawnMobs", r.allowSpawnMobs);
                    config.set(regionKey + ".allowSpawnerBreak", r.allowSpawnerBreak);
                    config.set(regionKey + ".allowBeaconPlace", r.allowBeaconPlace);
                    config.set(regionKey + ".allowBeaconBreak", r.allowBeaconBreak);
                    r.allowPickup = config.getBoolean(key + ".allowPickup", false);
                    r.allowPotion = config.getBoolean(key + ".allowPotion", false);
                    r.allowKillMobs = config.getBoolean(key + ".allowKillMobs", false);
                    r.allowSpawnMobs = config.getBoolean(key + ".allowSpawnMobs", false);
                    r.allowSpawnerBreak = config.getBoolean(key + ".allowSpawnerBreak", false);
                    r.allowBeaconPlace = config.getBoolean(key + ".allowBeaconPlace", false);
                    r.allowBeaconBreak = config.getBoolean(key + ".allowBeaconBreak", false);

                }
            }
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe(String.format("An error occurred while saving plots: %s", e.getMessage()));
        }
    }

    public void loadPlots() {
        File file = new File(plugin.getDataFolder(), "plots.yml");
        if (!file.exists()) {
            plugin.getLogger().info("Plik plots.yml nie istnieje. Nie załadowano żadnych działek.");
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            UUID uuid = UUID.fromString(key);
            String plotName = config.getString(key + ".plotName");
            // 2. Pomijaj regiony bez nazwy
            if (plotName == null || plotName.isBlank()) {
                plugin.getLogger().warning("Pomijam region bez nazwy (klucz: " + key + ")");
                continue;
            }
            int minX = config.getInt(key + ".minX");
            int maxX = config.getInt(key + ".maxX");
            int minZ = config.getInt(key + ".minZ");
            int maxZ = config.getInt(key + ".maxZ");
            int minY = config.getInt(key + ".minY");
            int maxY = config.getInt(key + ".maxY");
            Location center = config.getLocation(key + ".center");
            String owner = config.getString(key + ".owner");
            long creationTime = config.getLong(key + ".creationTime");
            List<?> rawList = config.getList(key + ".invitedPlayers");
            List<UUID> invitedPlayers = new ArrayList<>();
            if (rawList != null) {
                for (Object obj : rawList) {
                    if (obj instanceof String str) {
                        invitedPlayers.add(UUID.fromString(str));
                    }
                }
            }
            Location warp = config.getLocation(key + ".warp");
            int points = config.getInt(key + ".points");
            UUID deputy = (UUID) config.get(key + ".deputy");
            plugin.getLogger().info(String.format("Ładowanie działki: %s (Właściciel: %s)", plotName, owner));
            ProtectedRegion region = new ProtectedRegion(minX, maxX, minZ, maxZ, minY, maxY, center, owner, plotName, creationTime);
            region.invitedPlayers.addAll(invitedPlayers);
            region.warp = warp;
            region.points = points;
            region.deputy = deputy;
            dzialki.computeIfAbsent(uuid, k -> new ArrayList<>()).add(region);
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
        Inventory inv = Bukkit.createInventory(null, 27, "Panel Działki: " + r.plotName);

        // rząd 1: podstawowe info + teleport + role
        inv.setItem(10, item(Material.OAK_SIGN,
                "§dPodstawowe informacje",
                List.of(
                        "§7Założyciel: §e" + r.owner,
                        "§7Data utworzenia: §e" + new SimpleDateFormat("dd/MM/yyyy HH:mm")
                                .format(new Date(r.creationTime))
                )
        ));
        inv.setItem(11, item(Material.EMERALD,
                "§bPunkty działki",
                List.of("§7Aktualnie: §a" + r.points)
        ));
        inv.setItem(12, item(Material.ENDER_PEARL, "§aTeleportuj na środek"));
        inv.setItem(13, head(r.owner, "Założyciel"));
        if (r.deputy != null) {
            OfflinePlayer d = Bukkit.getOfflinePlayer(r.deputy);
            inv.setItem(14, head(d.getName(), "Zastępca"));
        } else {
            inv.setItem(14, item(Material.GRAY_WOOL, "§7Brak zastępcy"));
        }

        // rząd 2: togglery uprawnień
        inv.setItem(19, toggleItem(r.allowBuild, "§aKładzenie bloków"));
        inv.setItem(20, toggleItem(r.allowDestroy, "§cNiszczenie bloków"));
        inv.setItem(21, toggleItem(r.allowChest, "§6Otwieranie skrzyń"));
        inv.setItem(22, toggleItem(r.allowPickup, "§ePodnoszenie itemów"));
        inv.setItem(23, toggleItem(r.allowFlight, "§bLatanie"));
        inv.setItem(24, toggleItem(r.allowKillMobs, "§cBicie mobów"));
        inv.setItem(25, toggleItem(r.allowSpawnMobs, "§dRespienie mobów"));

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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Upewnij się, że to gracz
        if (!(event.getWhoClicked() instanceof Player p)) {
            return;
        }

        String title = event.getView().getTitle();
        // Tylko panel działki
        if (!title.startsWith("Panel Działki: ")) {
            return;
        }

        // Zablokuj wyciąganie itemów
        event.setCancelled(true);

        if (title.startsWith("Panel Działki: ")) {
            ItemStack it = event.getCurrentItem();
            if (it == null || !it.hasItemMeta()) {
                return;
            }
            String name = ChatColor.stripColor(it.getItemMeta().getDisplayName());
            String plotName = title.substring("Panel Działki: ".length());
            ProtectedRegion r = getRegionByName(plotName);

            // teleportacja na środek działki
            if (name.equals("Teleportuj na środek")) {
                p.teleport(r.center.clone().add(0.5, 1, 0.5));
                p.sendMessage("§aTeleport na środek działki!");
                return;
            }

            // togglery flag
            if (name.startsWith("Kładzenie bloków")) {
                r.allowBuild = !r.allowBuild;
            } else if (name.startsWith("Niszczenie bloków")) {
                r.allowDestroy = !r.allowDestroy;
            } else if (name.startsWith("Otwieranie skrzyń")) {
                r.allowChest = !r.allowChest;
            } else if (name.startsWith("Podnoszenie itemów")) {
                r.allowPickup = !r.allowPickup;
            } else if (name.startsWith("Latanie")) {
                r.allowFlight = !r.allowFlight;
            } else if (name.startsWith("Bicie mobów")) {
                r.allowKillMobs = !r.allowKillMobs;
            } else if (name.startsWith("Respienie mobów")) {
                r.allowSpawnMobs = !r.allowSpawnMobs;
            }

            // zapisz i odśwież GUI
            savePlots();
            openPanel(r, p);
            return;
        }

        ItemStack it = event.getCurrentItem();
        if (it == null || !it.hasItemMeta()) {
            return;
        }
        String name = it.getItemMeta().getDisplayName();

        // Nazwa działki
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
        if (name.equals("§aTeleportuj na działkę")) {
            p.teleport(region.center.clone().add(0.5, 1, 0.5));
            p.sendMessage("§aTeleport!");
            return;
        }

        // — przełączanie dzień/noc —  
        if (name.equals("§ePrzełącz dzień/noc")) {
            region.isDay = !region.isDay;
            long t = region.isDay ? 1000L : 13000L;
            p.setPlayerTime(t, false);
            p.sendMessage(region.isDay
                    ? "§aWłączyłeś dzień na tej działce."
                    : "§aWłączyłeś noc na tej działce.");
            openPanel(region, p);
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
            case GREEN_CONCRETE, RED_CONCRETE -> {
                if (name.contains("Budowanie")) {
                    region.allowBuild = !region.allowBuild;
                    p.sendMessage(region.allowBuild
                            ? "§aBudowanie odblokowane"
                            : "§cBudowanie zablokowane");
                }
                if (name.contains("Niszczenie")) {
                    region.allowDestroy = !region.allowDestroy;
                    p.sendMessage(region.allowDestroy
                            ? "§aNiszczenie odblokowane"
                            : "§cNiszczenie zablokowane");
                }
                if (name.contains("Skrzynki")) {
                    region.allowChest = !region.allowChest;
                    p.sendMessage(region.allowChest
                            ? "§aOtwieranie skrzynek odblokowane"
                            : "§cOtwieranie skrzynek zablokowane");
                }
                if (name.contains("Latanie")) {
                    region.allowFlight = !region.allowFlight;
                    p.sendMessage(region.allowFlight
                            ? "§aLatanie odblokowane"
                            : "§cLatanie zablokowane");
                }
                if (name.contains("Wejście")) {
                    region.allowEnter = !region.allowEnter;
                    p.sendMessage(region.allowEnter
                            ? "§aWejście odblokowane"
                            : "§cWejście zablokowane");
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
        if (r != null && !r.owner.equals(p.getName()) && !r.invitedPlayers.contains(p.getUniqueId())
                && !r.allowPickup) {
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
        if (r != null && !r.allowPotion
                && !r.owner.equals(p.getName()) && !r.invitedPlayers.contains(p.getUniqueId())) {
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
                && !r.allowKillMobs && !r.owner.equals(p.getName())
                && !r.invitedPlayers.contains(p.getUniqueId())) {
            ev.setCancelled(true);
        }
    }

    // 4) respienie mobów
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent ev) {
        if (ev.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            return;
        }
        Location loc = ev.getLocation();
        ProtectedRegion r = getRegion(loc);
        if (r != null && !r.allowSpawnMobs) {
            ev.setCancelled(true);
        }
    }

    // 5) niszczenie spawnerów i beaconów
    @EventHandler
    public void onBlockBreak(BlockBreakEvent ev) {
        Player p = ev.getPlayer();
        ProtectedRegion r = getRegion(p.getLocation());
        if (r != null && !r.owner.equals(p.getName()) && !r.invitedPlayers.contains(p.getUniqueId())) {
            Material m = ev.getBlock().getType();
            if ((m == Material.SPAWNER && !r.allowSpawnerBreak)
                    || (m == Material.BEACON && !r.allowBeaconBreak)
                    || (m != Material.SPAWNER && m != Material.BEACON && !r.allowDestroy)) {
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
        if (r != null && !r.owner.equals(p.getName()) && !r.invitedPlayers.contains(p.getUniqueId())) {
            if (ev.getBlock().getType() == Material.BEACON && !r.allowBeaconPlace) {
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

        // gracz wchodzi na nową działkę
        if (now != null && now != prev) {
            // ustawiamy jego czas zgodnie ze stanem działki
            long t = now.isDay ? 1000L : 13000L;
            p.setPlayerTime(t, false);
            showBossBar(now, p);
        } // gracz wychodzi z działki
        else if (now == null && prev != null) {
            // przywróć mu globalny czas
            p.resetPlayerTime();
            BossBar bar = getBossBar(p);
            if (bar != null) {
                bar.setVisible(false);
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
            bossBar.addPlayer(player);
            bossBary.put(player.getUniqueId(), bossBar);
        }

        bossBar.setTitle("§eDziałka: §a" + region.plotName + " §e| Właściciel: §a" + region.owner);
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
}
