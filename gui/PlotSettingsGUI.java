    public static void open(Player player, Plot plot) {
        Inventory inv = Bukkit.createInventory(null, 27, "§d§lUstawienia działki");

        // Opcja latania dla niedodanych
        ItemStack fly = new ItemStack(Material.FEATHER);
        ItemMeta flyMeta = fly.getItemMeta();
        flyMeta.setDisplayName("§bMożliwość latania przez osoby niedodane");
        List<String> flyLore = new ArrayList<>();
        boolean enabled = plot.isFlyForVisitors();
        flyLore.add("§7Aktualnie: " + (enabled ? "§aWłączone" : "§cWyłączone"));
        flyLore.add("§8Kliknij, aby " + (enabled ? "§cwyłączyć" : "§awłączyć"));
        flyMeta.setLore(flyLore);
        fly.setItemMeta(flyMeta);

