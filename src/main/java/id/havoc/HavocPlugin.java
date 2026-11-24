package id.havoc;

import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class HavocPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Map<String, Long> activeUntil = new HashMap<>();
    private final Map<String, Double> activeBoosts = new HashMap<>();
    private final Map<String, BossBar> bossBars = new HashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        Objects.requireNonNull(getCommand("havoc")).setExecutor(this);
        Objects.requireNonNull(getCommand("havoc")).setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Havoc enabled.");
    }

    @Override public void onDisable() {
        bossBars.values().forEach(BossBar::removeAll);
        bossBars.clear();
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("havoc.admin")) {
            sender.sendMessage(color("&cKamu tidak punya izin."));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage(color("&aConfig Havoc berhasil di-reload."));
            sender.sendMessage(color("&7Event yang sedang berjalan tetap memakai timer dan boost sebelumnya."));
            return true;
        }
        if (args.length > 0) {
            sender.sendMessage(color("&cPenggunaan: /hvc [reload]"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&cCommand ini hanya bisa digunakan pemain."));
            return true;
        }
        openMain(player);
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("havoc.admin")) {
            return Collections.singletonList("reload").stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
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
        Inventory inv = Bukkit.createInventory(null, 27, color("&0Havoc Hardcore System"));
        inv.setItem(11, item(Material.NETHER_STAR, "&6Event", "&7Atur chance dan mulai boost event."));
        inv.setItem(15, item(Material.WRITABLE_BOOK, "&dRoleplay", "&7Mulai pengumuman roleplay."));
        fill(inv);
        p.openInventory(inv);
    }

    private void openEvent(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&0Event"));
        inv.setItem(10, item(Material.ENCHANTED_BOOK, "&bMending Boost", "&7Mulai event boost Mending.", "&8Hanya durasi/boost dari config.yml"));
        inv.setItem(12, item(Material.TOTEM_OF_UNDYING, "&eTotem Boost", "&7Mulai event boost Totem.", "&8Hanya durasi/boost dari config.yml"));
        inv.setItem(14, item(Material.PAPER, "&bChance Mending", "&7Saat ini: &f" + getConfig().getDouble("fishing.mending-chance") + "%", "&cKlik kiri: -0.1% | Shift+kiri: -1%", "&aKlik kanan: +0.1% | Shift+kanan: +1%"));
        inv.setItem(16, item(Material.TOTEM_OF_UNDYING, "&eChance Totem", "&7Saat ini: &f" + getConfig().getDouble("evoker.totem-chance") + "%", "&cKlik kiri: -0.1% | Shift+kiri: -1%", "&aKlik kanan: +0.1% | Shift+kanan: +1%"));
        fill(inv);
        p.openInventory(inv);
    }

    private void openRoleplay(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&0Roleplay"));
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
        if (!title.equals(color("&0Havoc Hardcore System")) && !title.equals(color("&0Event")) && !title.equals(color("&0Roleplay"))) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        if (title.equals(color("&0Havoc Hardcore System"))) {
            if (e.getRawSlot() == 11) openEvent(p);
            else if (e.getRawSlot() == 15) openRoleplay(p);
            return;
        }
        if (!title.equals(color("&0Event"))) return;

        int slot = e.getRawSlot();
        if (slot == 10) startBoost("mending", p);
        else if (slot == 12) startBoost("totem", p);
        else if (slot == 14 || slot == 16) {
            String path = slot == 14 ? "fishing.mending-chance" : "evoker.totem-chance";
            // Shift + click changes by 1%; normal click changes by 0.1%.
            // Only left and right clicks are accepted.
            if (!e.isLeftClick() && !e.isRightClick()) return;
            double delta = e.isShiftClick() ? 1.0 : 0.1;
            if (e.isLeftClick()) delta = -delta;
            double value = Math.max(0.0, Math.min(100.0,
                    getConfig().getDouble(path) + delta));
            getConfig().set(path, Math.round(value * 10.0) / 10.0);
            saveConfig();
            openEvent(p);
        }
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
