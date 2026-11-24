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
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class HavocPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Map<String, Long> activeUntil = new HashMap<>();
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
        if (!(sender instanceof Player player)) { sender.sendMessage("Command ini hanya bisa digunakan pemain."); return true; }
        if (!player.hasPermission("havoc.admin")) { player.sendMessage(color("&cKamu tidak punya izin.")); return true; }
        openMain(player);
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
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
        inv.setItem(14, item(Material.PAPER, "&bChance Mending", "&7Saat ini: &f" + getConfig().getDouble("fishing.mending-chance") + "%", "&cKlik kiri: -0.1% | Shift: -1%", "&aKlik kanan: +0.1% | Shift: +1%"));
        inv.setItem(16, item(Material.TOTEM_OF_UNDYING, "&eChance Totem", "&7Saat ini: &f" + getConfig().getDouble("evoker.totem-chance") + "%", "&cKlik kiri: -0.1% | Shift: -1%", "&aKlik kanan: +0.1% | Shift: +1%"));
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
            double delta = e.isShiftClick() ? 1.0 : 0.1;
            if (e.isLeftClick()) delta = -delta;
            double value = Math.max(0, Math.min(100, getConfig().getDouble(path) + delta));
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
        Bukkit.broadcastMessage(color(getConfig().getString(path + "announcement", "")));
        String title = color(getConfig().getString(path + "title", ""));
        String subtitle = color(getConfig().getString(path + "subtitle", ""));
        for (Player online : Bukkit.getOnlinePlayers()) online.sendTitle(title, subtitle, 10, 60, 10);

        BossBar old = bossBars.remove(type);
        if (old != null) old.removeAll();
        BossBar bar = Bukkit.createBossBar(color(getConfig().getString(path + "bossbar", "&6Havoc Boost")),
                type.equals("mending") ? BarColor.PURPLE : BarColor.YELLOW, BarStyle.SOLID);
        bar.setProgress(1.0);
        Bukkit.getOnlinePlayers().forEach(bar::addPlayer);
        bossBars.put(type, bar);

        new BukkitRunnable() {
            int remaining = duration;
            @Override public void run() {
                if (remaining <= 0) {
                    activeUntil.remove(type);
                    bar.removeAll();
                    bossBars.remove(type);
                    Bukkit.broadcastMessage(color("&c[HAVOC] Event " + type + " telah berakhir."));
                    cancel();
                    return;
                }
                bar.setProgress(Math.max(0.0, Math.min(1.0, remaining / (double) duration)));
                remaining--;
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
            base += getConfig().getDouble("events." + type + ".boost", 0.0);
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
