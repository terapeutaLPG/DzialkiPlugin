package me.twojanazwa.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PlotPanelGUI {
    // ...existing code...

    public static void open(Player player, Plot plot) {
        Inventory inv = Bukkit.createInventory(null, 27, "§6§lPanel Działki");

        // 1. Podstawowe informacje (tabliczka)
        ItemStack info = new ItemStack(Material.OAK_SIGN);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§e§lPodstawowe informacje");
        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7Założyciel: §a" + plot.getOwnerName());
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        infoLore.add("§7Data założenia: §b" + sdf.format(plot.getCreatedAt()));
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(10, info);

        // 2. Ustawienia działki (repeater)
        ItemStack settings = new ItemStack(Material.REPEATER);
        ItemMeta settingsMeta = settings.getItemMeta();
        settingsMeta.setDisplayName("§d§lUstawienia działki");
        List<String> settingsLore = new ArrayList<>();
        boolean flyEnabled = plot.isFlyForVisitors();
        settingsLore.add("§7Możliwość latania przez osoby niedodane:");
        settingsLore.add("  §fWłączone: " + (flyEnabled ? "§aTak" : "§cNie"));
        settingsLore.add("§8Kliknij, aby otworzyć ustawienia");
        settingsMeta.setLore(settingsLore);
        settings.setItemMeta(settingsMeta);
        inv.setItem(12, settings);

        // 3. Informacje o członkach (głowa gracza)
        ItemStack members = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta membersMeta = (SkullMeta) members.getItemMeta();
        membersMeta.setDisplayName("§b§lCzłonkowie działki");
        List<String> membersLore = new ArrayList<>();
        membersLore.add("§7Liczba członków: §a" + plot.getMembers().size());
        membersLore.add("§8Kliknij, aby zobaczyć listę członków");
        membersMeta.setLore(membersLore);
        if (!plot.getMembers().isEmpty()) {
            membersMeta.setOwningPlayer(Bukkit.getOfflinePlayer(plot.getMembers().get(0)));
        }
        members.setItemMeta(membersMeta);
        inv.setItem(14, members);

        // 4. Punkty działki (kamień)
        ItemStack points = new ItemStack(Material.STONE);
        ItemMeta pointsMeta = points.getItemMeta();
        pointsMeta.setDisplayName("§a§lPunkty działki");
        List<String> pointsLore = new ArrayList<>();
        pointsLore.add("§7Liczba punktów: §e" + plot.getPoints());
        pointsLore.add("§8Kliknij, aby zobaczyć za jakie bloki otrzymasz punkty");
        pointsMeta.setLore(pointsLore);
        points.setItemMeta(pointsMeta);
        inv.setItem(16, points);

        // 5. Rynek (pergamin)
        ItemStack market = new ItemStack(Material.PAPER);
        ItemMeta marketMeta = market.getItemMeta();
        marketMeta.setDisplayName("§6§lRynek działek");
        List<String> marketLore = new ArrayList<>();
        if (plot.isOnMarket()) {
            marketLore.add("§aTwoja działka jest wystawiona na sprzedaż!");
            marketLore.add("§7Cena: §e" + plot.getMarketPrice() + " złota");
        } else {
            marketLore.add("§cTwoja działka nie jest wystawiona na sprzedaż");
            marketLore.add("§7Aby wystawić działkę użyj:");
            marketLore.add("§f/dzialka sprzedaj " + plot.getName() + " <cena>");
            marketLore.add("§7Aby sprawdzić oferty: §f/dzialka rynek");
        }
        marketMeta.setLore(marketLore);
        market.setItemMeta(marketMeta);
        inv.setItem(22, market);

        // ...pozostałe sloty i stylistyka zgodna z pluginem...

        player.openInventory(inv);

        // Zarejestruj obsługę kliknięć (np. w listenerze InventoryClickEvent)
        // - slot 12: otwórz GUI ustawień
        // - slot 14: otwórz listę członków
        // - slot 16: otwórz GUI punktów
        // ...existing code...
    }
    // ...existing code...
}
