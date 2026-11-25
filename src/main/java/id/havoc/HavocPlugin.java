package id.havoc;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.boss.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class HavocPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Map<String, Long> activeUntil = new HashMap<>();
    private final Map<String, Double> activeBoosts = new HashMap<>();
    private final Map<String, BossBar> bossBars = new HashMap<>();
    private BossBar shopBossBar;
    private long shopEndsAt = 0L;
    private BukkitTask shopTimerTask;
    private final Set<UUID> activeShopViewers = new HashSet<>();
    private static final String MAIN_TITLE = "&0Havoc Hardcore System";
    private static final String EVENT_TITLE = "&0Event";
    private static final String ROLEPLAY_TITLE = "&0Roleplay";
    private static final String EDITOR_TITLE = "&0Shop Editor - Input / Output";

    @Override public void onEnable() {
        saveDefaultConfig();
        Objects.requireNonNull(getCommand("havoc")).setExecutor(this);
        Objects.requireNonNull(getCommand("havoc")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("shop")).setExecutor(this);
        Objects.requireNonNull(getCommand("shop")).setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Havoc enabled.");
    }

    @Override public void onDisable() {
        bossBars.values().forEach(BossBar::removeAll);
        bossBars.clear();
        if (shopBossBar != null) shopBossBar.removeAll();
        if (shopTimerTask != null) shopTimerTask.cancel();
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("shop")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("stop")) {
                if (!sender.hasPermission("havoc.admin")) {
                    sender.sendMessage(color("&cKamu tidak punya izin."));
                    return true;
                }
                stopShopEvent(sender);
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("edit")) {
                if (!sender.hasPermission("havoc.admin")) {
                    sender.sendMessage(color("&cKamu tidak punya izin."));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Command ini hanya bisa digunakan pemain.");
                    return true;
                }
                openShopEditor(player);
                return true;
            }
            if (args.length > 0) {
                sender.sendMessage(color("&cPenggunaan: /shop, /shop edit, atau /shop stop"));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Shop hanya bisa dibuka oleh pemain.");
                return true;
            }
            if (!isShopActive()) {
                player.sendMessage(color("&cShop event sedang tidak aktif."));
                return true;
            }
            openShop(player);
            return true;
        }

        if (!sender.hasPermission("havoc.admin")) {
            sender.sendMessage(color("&cKamu tidak punya izin."));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage(color("&aConfig Havoc berhasil di-reload."));
            sender.sendMessage(color("&7Event aktif tetap memakai timer dan boost sebelumnya."));
            return true;
        }
        if (args.length > 0) {
            sender.sendMessage(color("&cPenggunaan: /hvc [reload]"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Command ini hanya bisa digunakan pemain.");
            return true;
        }
        openMain(player);
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("shop")) {
            if (args.length == 1 && sender.hasPermission("havoc.admin"))
                return Arrays.asList("edit", "stop").stream()
                        .filter(x -> x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
            return Collections.emptyList();
        }
        if (args.length == 1 && sender.hasPermission("havoc.admin"))
            return Collections.singletonList("reload").stream().filter(x -> x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        return Collections.emptyList();
    }

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(color(name));
        List<String> lines = new ArrayList<>();
        for (String line : lore) lines.add(color(line));
        meta.setLore(lines);
        stack.setItemMeta(meta);
        return stack;
    }

    private void openMain(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color(MAIN_TITLE));
        inv.setItem(11, item(Material.NETHER_STAR, "&6Event", "&7Atur chance dan mulai boost event."));
        inv.setItem(15, item(Material.WRITABLE_BOOK, "&dRoleplay", "&7Mulai pengumuman roleplay."));
        fill(inv);
        p.openInventory(inv);
    }

    private void openEvent(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color(EVENT_TITLE));
        inv.setItem(10, item(Material.ENCHANTED_BOOK, "&bMending Boost", "&7Mulai event boost Mending.", "&8Hanya durasi/boost dari config.yml"));
        inv.setItem(12, item(Material.TOTEM_OF_UNDYING, "&eTotem Boost", "&7Mulai event boost Totem.", "&8Hanya durasi/boost dari config.yml"));
        inv.setItem(14, item(Material.PAPER, "&bChance Mending", "&7Saat ini: &f" + getConfig().getDouble("fishing.mending-chance") + "%", "&cKlik kiri: -0.1% | Shift+kiri: -1%", "&aKlik kanan: +0.1% | Shift+kanan: +1%"));
        inv.setItem(16, item(Material.TOTEM_OF_UNDYING, "&eChance Totem", "&7Saat ini: &f" + getConfig().getDouble("evoker.totem-chance") + "%", "&cKlik kiri: -0.1% | Shift+kiri: -1%", "&aKlik kanan: +0.1% | Shift+kanan: +1%"));
        inv.setItem(22, item(Material.EMERALD, "&aBarter Shop Event", "&7Mulai event shop untuk semua pemain.", "&8Durasi dan pesan dari config.yml"));
        fill(inv);
        p.openInventory(inv);
    }

    private void openRoleplay(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color(ROLEPLAY_TITLE));
        inv.setItem(13, item(Material.WRITABLE_BOOK, "&dRoleplay dimulai", "&7Pengumuman telah dikirim."));
        fill(inv);
        p.openInventory(inv);
        String path = "roleplay.";
        Bukkit.broadcastMessage(color(getConfig().getString(path + "announcement", "")));
        for (Player online : Bukkit.getOnlinePlayers())
            online.sendTitle(color(getConfig().getString(path + "title", "")),
                    color(getConfig().getString(path + "subtitle", "")), 10, 60, 10);
    }

    private void fill(Inventory inv) {
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, pane);
    }

    @EventHandler public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();

        // Biarkan inventory Merchant bawaan Minecraft menangani transaksi secara native.
        if (activeShopViewers.contains(p.getUniqueId()) && e.getView().getTopInventory() instanceof MerchantInventory) {
            if (!isShopActive()) {
                e.setCancelled(true);
                p.closeInventory();
                p.sendMessage(color("&cShop event sudah berakhir."));
            }
            return;
        }

        if (title.equals(color(EDITOR_TITLE))) {
            // Editor sengaja membolehkan pemain memindahkan item ke/dari inventory editor.
            if (e.getClickedInventory() == null) return;
            if (e.isShiftClick() || (e.getClickedInventory() == e.getView().getTopInventory() && e.getRawSlot() == 4)) e.setCancelled(true);
            return;
        }
        if (!title.equals(color(MAIN_TITLE)) && !title.equals(color(EVENT_TITLE)) && !title.equals(color(ROLEPLAY_TITLE))) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) return;
        if (title.equals(color(MAIN_TITLE))) {
            if (e.getRawSlot() == 11) openEvent(p);
            else if (e.getRawSlot() == 15) openRoleplay(p);
            return;
        }
        if (!title.equals(color(EVENT_TITLE))) return;
        int slot = e.getRawSlot();
        if (slot == 10) startBoost("mending", p);
        else if (slot == 12) startBoost("totem", p);
        else if (slot == 22) startShopEvent(p);
        else if (slot == 14 || slot == 16) {
            String path = slot == 14 ? "fishing.mending-chance" : "evoker.totem-chance";
            if (!e.isLeftClick() && !e.isRightClick()) return;
            double delta = e.isShiftClick() ? 1.0 : 0.1;
            if (e.isLeftClick()) delta = -delta;
            double value = Math.max(0.0, Math.min(100.0, getConfig().getDouble(path) + delta));
            getConfig().set(path, Math.round(value * 10.0) / 10.0);
            saveConfig();
            openEvent(p);
        }
    }


    @EventHandler public void onInventoryDrag(InventoryDragEvent e) {
        String title = e.getView().getTitle();
        if (title.equals(color(EDITOR_TITLE)) && e.getRawSlots().contains(4)) {
            e.setCancelled(true);
        }
    }

    @EventHandler public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        if (shopBossBar != null && isShopActive()) shopBossBar.addPlayer(player);
        for (BossBar bar : bossBars.values()) bar.addPlayer(player);
    }

    @EventHandler public void onEditorClose(InventoryCloseEvent e) {
        if (e.getInventory() instanceof MerchantInventory) activeShopViewers.remove(e.getPlayer().getUniqueId());
        if (!e.getView().getTitle().equals(color(EDITOR_TITLE))) return;
        Inventory inv = e.getInventory();
        getConfig().set("shop.offers", null);
        int offer = 0;
        for (int row = 1; row <= 4; row++) {
            for (int pair = 0; pair < 4; pair++) {
                int inputSlot = row * 9 + pair * 2;
                int outputSlot = inputSlot + 1;
                ItemStack input = inv.getItem(inputSlot);
                ItemStack output = inv.getItem(outputSlot);
                if (isRealItem(input) && isRealItem(output)) {
                    String base = "shop.offers." + offer;
                    getConfig().set(base + ".input", input.clone());
                    getConfig().set(base + ".output", output.clone());
                    offer++;
                }
            }
        }
        saveConfig();
        e.getPlayer().sendMessage(color("&aShop berhasil disimpan! &f" + offer + " trade tersimpan."));
    }

    private boolean isRealItem(ItemStack stack) {
        return stack != null && !stack.getType().isAir();
    }

    private void openShopEditor(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color(EDITOR_TITLE));
        inv.setItem(4, item(Material.WRITABLE_BOOK, "&eCara mengatur shop", "&7Isi setiap pasangan slot:", "&fSlot kiri = item yang dibayar", "&fSlot kanan = item yang diterima", "&7Contoh: Emerald -> Diamond", "&aTutup menu untuk menyimpan.", "&8Maksimal 16 trade."));
        ConfigurationSection offers = getConfig().getConfigurationSection("shop.offers");
        if (offers != null) {
            int index = 0;
            for (String key : offers.getKeys(false)) {
                if (index >= 16) break;
                int row = 1 + index / 4;
                int pair = index % 4;
                int slot = row * 9 + pair * 2;
                inv.setItem(slot, offers.getItemStack(key + ".input"));
                inv.setItem(slot + 1, offers.getItemStack(key + ".output"));
                index++;
            }
        }
        p.openInventory(inv);
    }

    private boolean isShopActive() { return shopEndsAt > System.currentTimeMillis(); }

    private void stopShopEvent(CommandSender sender) {
        if (!isShopActive()) {
            sender.sendMessage(color("&cBarter Shop event sedang tidak aktif."));
            return;
        }

        shopEndsAt = 0L;
        if (shopTimerTask != null) {
            shopTimerTask.cancel();
            shopTimerTask = null;
        }
        if (shopBossBar != null) {
            shopBossBar.removeAll();
            shopBossBar = null;
        }

        for (UUID viewerId : new HashSet<>(activeShopViewers)) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null) {
                viewer.closeInventory();
                viewer.sendMessage(color("&cBarter Shop event dihentikan oleh admin."));
            }
        }
        activeShopViewers.clear();

        Bukkit.broadcastMessage(color(getConfig().getString("shop.event.stopped-announcement",
                "&c[HAVOC] Barter Shop dihentikan oleh admin.")));
        sender.sendMessage(color("&aBarter Shop event berhasil dihentikan."));
    }

    private void startShopEvent(Player starter) {
        int duration = Math.max(1, getConfig().getInt("shop.event.duration-seconds", 3600));
        shopEndsAt = System.currentTimeMillis() + duration * 1000L;
        if (shopTimerTask != null) shopTimerTask.cancel();
        if (shopBossBar != null) shopBossBar.removeAll();
        shopBossBar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID);
        Bukkit.getOnlinePlayers().forEach(shopBossBar::addPlayer);
        Bukkit.broadcastMessage(color(getConfig().getString("shop.event.announcement", "&a[HAVOC] Barter Shop dibuka!")));
        String title = color(getConfig().getString("shop.event.title", "&aBARTER SHOP"));
        String subtitle = color(getConfig().getString("shop.event.subtitle", "&eGunakan /shop untuk barter!"));
        for (Player online : Bukkit.getOnlinePlayers()) online.sendTitle(title, subtitle, 10, 60, 10);
        final long end = shopEndsAt;
        shopTimerTask = new BukkitRunnable() {
            @Override public void run() {
                long millis = Math.max(0L, end - System.currentTimeMillis());
                long seconds = (millis + 999L) / 1000L;
                if (seconds <= 0L) {
                    shopEndsAt = 0L;
                    if (shopBossBar != null) shopBossBar.removeAll();
                    shopBossBar = null;
                    shopTimerTask = null;
                    Bukkit.broadcastMessage(color(getConfig().getString("shop.event.ended-announcement", "&c[HAVOC] Barter Shop telah ditutup.")));
                    for (UUID viewerId : new HashSet<>(activeShopViewers)) {
                        Player viewer = Bukkit.getPlayer(viewerId);
                        if (viewer != null) viewer.closeInventory();
                    }
                    activeShopViewers.clear();
                    cancel();
                    return;
                }
                long h = seconds / 3600L, m = (seconds % 3600L) / 60L, sec = seconds % 60L;
                String timer = String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, sec);
                String text = getConfig().getString("shop.event.bossbar", "&aBarter Shop");
                shopBossBar.setTitle(color(text + " &8| &f" + timer));
                shopBossBar.setProgress(Math.max(0.0, Math.min(1.0, millis / (duration * 1000.0))));
            }
        }.runTaskTimer(this, 0L, 20L);
        starter.sendMessage(color("&aBarter Shop event berhasil dimulai. Semua pemain sekarang bisa memakai /shop."));
        openEvent(starter);
    }

    private void openShop(Player p) {
        if (!isShopActive()) {
            p.sendMessage(color("&cShop event sedang tidak aktif."));
            return;
        }

        Merchant merchant = Bukkit.createMerchant(color("&aHavoc Barter Shop"));
        List<MerchantRecipe> recipes = new ArrayList<>();
        ConfigurationSection offers = getConfig().getConfigurationSection("shop.offers");
        if (offers != null) {
            for (String key : offers.getKeys(false)) {
                ItemStack input = offers.getItemStack(key + ".input");
                ItemStack output = offers.getItemStack(key + ".output");
                if (!isRealItem(input) || !isRealItem(output)) continue;

                MerchantRecipe recipe = new MerchantRecipe(output.clone(), Integer.MAX_VALUE);
                recipe.setIngredients(Collections.singletonList(input.clone()));
                recipe.setExperienceReward(false);
                recipe.setVillagerExperience(0);
                recipe.setPriceMultiplier(0.0f);
                recipe.setDemand(0);
                recipes.add(recipe);
            }
        }

        if (recipes.isEmpty()) {
            p.sendMessage(color("&cBelum ada trade yang tersedia. Admin bisa mengaturnya dengan /shop edit."));
            return;
        }

        merchant.setRecipes(recipes);
        p.openMerchant(merchant, true);
        activeShopViewers.add(p.getUniqueId());
    }

    private String pretty(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return ChatColor.stripColor(meta.getDisplayName());
        String name = stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    private void startBoost(String type, Player initiator) {
        String path = "events." + type + ".";
        int duration = Math.max(1, getConfig().getInt(path + "duration-seconds", 3600));
        double boost = getConfig().getDouble(path + "boost", 5.0);
        activeUntil.put(type, System.currentTimeMillis() + duration * 1000L);
        activeBoosts.put(type, boost);
        Bukkit.broadcastMessage(color(getConfig().getString(path + "announcement", "")));
        String title = color(getConfig().getString(path + "title", ""));
        String subtitle = color(getConfig().getString(path + "subtitle", ""));
        for (Player online : Bukkit.getOnlinePlayers()) online.sendTitle(title, subtitle, 10, 60, 10);

        BossBar old = bossBars.remove(type);
        if (old != null) old.removeAll();
        String bossbarText = getConfig().getString(path + "bossbar", "&6Havoc Boost");
        BossBar bar = Bukkit.createBossBar("", 
                type.equals("mending") ? BarColor.PURPLE : BarColor.YELLOW, BarStyle.SOLID);
        Bukkit.getOnlinePlayers().forEach(bar::addPlayer);
        bossBars.put(type, bar);

        final long endTime = System.currentTimeMillis() + duration * 1000L;
        new BukkitRunnable() {
            @Override public void run() {
                long millisLeft = Math.max(0L, endTime - System.currentTimeMillis());
                long secondsLeft = (millisLeft + 999L) / 1000L;
                if (secondsLeft <= 0L) {
                    activeUntil.remove(type);
                    activeBoosts.remove(type);
                    bar.removeAll();
                    bossBars.remove(type);
                    Bukkit.broadcastMessage(color("&c[HAVOC] Event " + type + " telah berakhir."));
                    cancel();
                    return;
                }

                long hours = secondsLeft / 3600L;
                long minutes = (secondsLeft % 3600L) / 60L;
                long seconds = secondsLeft % 60L;
                String timer = String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
                bar.setTitle(color(bossbarText + " &8| &f" + timer));
                bar.setProgress(Math.max(0.0, Math.min(1.0, millisLeft / (duration * 1000.0))));
            }
        }.runTaskTimer(this, 0L, 20L);
        initiator.sendMessage(color("&aEvent boost berhasil dimulai."));
        openEvent(initiator);
    }

    private double chance(String type) {
        String path = type.equals("mending") ? "fishing.mending-chance" : "evoker.totem-chance";
        double base = getConfig().getDouble(path);
        Long end = activeUntil.get(type);
        if (end != null && end > System.currentTimeMillis())
            base += activeBoosts.getOrDefault(type, 0.0);
        return Math.max(0, Math.min(100, base));
    }

    @EventHandler public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH || e.getCaught() == null) return;
        if (!(e.getCaught() instanceof Item caught)) return;
        if (Math.random() * 100.0 >= chance("mending")) return;
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(Enchantment.MENDING, 1, true);
        book.setItemMeta(meta);
        caught.setItemStack(book);
    }

    @EventHandler public void onEvokerDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Evoker)) return;
        e.getDrops().removeIf(drop -> drop.getType() == Material.TOTEM_OF_UNDYING);
        if (Math.random() * 100.0 < chance("totem"))
            e.getDrops().add(new ItemStack(Material.TOTEM_OF_UNDYING));
    }
}
