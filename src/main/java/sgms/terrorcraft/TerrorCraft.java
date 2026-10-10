package sgms.terrorcraft;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import sgms.terrorcraft.data.Defs.RecipeDef;
import sgms.terrorcraft.data.Defs.StationDef;
import sgms.terrorcraft.data.StationManager;
import sgms.terrorcraft.data.StationManager.Bound;
import sgms.terrorcraft.gui.Guis.GameGui;
import sgms.terrorcraft.session.Session;
import sgms.terrorcraft.util.Compat;
import sgms.terrorcraft.util.Text;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class TerrorCraft extends JavaPlugin {
    private static TerrorCraft instance;

    private final Map<String, StationDef> stations = new LinkedHashMap<>();
    private final Map<String, RecipeDef> recipes = new LinkedHashMap<>();
    private final Map<String, String> messages = new HashMap<>();
    private StationManager manager;
    private BukkitTask ticker;
    private NamespacedKey stationKey;
    private double maxDistance = 8;
    private boolean holograms = true;

    public static TerrorCraft get() { return instance; }

    @Override
    public void onEnable() {
        instance = this;
        stationKey = new NamespacedKey(this, "station");
        manager = new StationManager(this);
        loadAll();
        Bukkit.getPluginManager().registerEvents(new Listeners(this), this);
        Commands cmd = new Commands(this);
        if (getCommand("tcraft") != null) {
            getCommand("tcraft").setExecutor(cmd);
            getCommand("tcraft").setTabCompleter(cmd);
        }
        ticker = Bukkit.getScheduler().runTaskTimer(this, this::tickAll, 1L, 1L);
        getLogger().info("Loaded " + stations.size() + " stations, " + recipes.size() + " recipes, "
                + manager.all().size() + " placed stations");
    }

    @Override
    public void onDisable() {
        if (ticker != null) ticker.cancel();
        if (manager != null) for (Session s : manager.sessions()) s.cancel();
    }

    public void loadAll() {
        if (manager != null) for (Session s : manager.sessions()) s.cancel();
        for (String f : new String[]{"config.yml", "stations.yml", "recipes.yml"}) {
            if (!new File(getDataFolder(), f).exists()) saveResource(f, false);
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml"));
        maxDistance = cfg.getDouble("settings.max-distance", 8);
        holograms = cfg.getBoolean("settings.holograms", true);
        messages.clear();
        ConfigurationSection ms = cfg.getConfigurationSection("messages");
        if (ms != null) for (String k : ms.getKeys(false)) messages.put(k, ms.getString(k, ""));

        stations.clear();
        YamlConfiguration st = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "stations.yml"));
        ConfigurationSection ss = st.getConfigurationSection("stations");
        if (ss != null) for (String id : ss.getKeys(false)) {
            ConfigurationSection c = ss.getConfigurationSection(id);
            if (c != null) stations.put(id, StationDef.load(id, c));
        }

        recipes.clear();
        YamlConfiguration rc = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "recipes.yml"));
        ConfigurationSection rs = rc.getConfigurationSection("recipes");
        if (rs != null) for (String id : rs.getKeys(false)) {
            ConfigurationSection c = rs.getConfigurationSection(id);
            if (c == null) continue;
            try {
                recipes.put(id, RecipeDef.load(id, c));
            } catch (Exception e) {
                getLogger().warning("Recipe " + id + " has an error: " + e.getMessage());
            }
        }
        for (StationDef s : stations.values()) {
            for (String r : s.recipes) {
                if (!recipes.containsKey(r)) getLogger().warning("Station " + s.id + " uses unknown recipe '" + r + "'");
            }
        }
        manager.load();
    }

    private int tickCounter;

    private void tickAll() {
        if (++tickCounter % 40 == 0) ambient();
        for (Session s : manager.sessions()) {
            try {
                s.tick();
            } catch (Throwable t) {
                getLogger().warning("Session error: " + t);
                s.cancel();
            }
        }
    }

    // ------------------------------------------------------------ accessors
    public StationManager stations() { return manager; }
    public StationDef stationDef(Bound b) { return stations.get(b.stationId); }
    public StationDef stationDef(String id) { return stations.get(id); }
    public Map<String, StationDef> stationDefs() { return stations; }
    public RecipeDef recipe(String id) { return recipes.get(id); }
    public Map<String, RecipeDef> recipes() { return recipes; }
    public double maxDistance() { return maxDistance; }
    public boolean hologramsEnabled() { return holograms; }

    // ------------------------------------------------------------ messages
    public String msgRaw(String key) {
        return Text.color(messages.getOrDefault(key, key));
    }

    public void msg(org.bukkit.command.CommandSender to, String key, String... repl) {
        String m = messages.get(key);
        if (m == null || m.isEmpty()) return;
        for (int i = 0; i + 1 < repl.length; i += 2) m = m.replace(repl[i], repl[i + 1]);
        to.sendMessage(Text.color(messages.getOrDefault("prefix", "") + m));
    }

    // ------------------------------------------------------------ station items
    public ItemStack stationItem(StationDef def, int amount) {
        ItemStack it = def.item == null ? null : def.item.build();
        if (it == null) it = new ItemStack(org.bukkit.Material.CRAFTING_TABLE, 1);
        it.setAmount(Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            if (!meta.hasDisplayName()) meta.setDisplayName(def.name);
            meta.getPersistentDataContainer().set(stationKey, PersistentDataType.STRING, def.id);
            it.setItemMeta(meta);
        }
        return it;
    }

    public String stationIdOf(ItemStack it) {
        if (it == null || it.getType().isAir()) return null;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return null;
        Object v = meta.getPersistentDataContainer().get(stationKey, PersistentDataType.STRING);
        return v == null ? null : v.toString();
    }

    // ------------------------------------------------------------ helpers
    public void closeGameGui(Session s) {
        Player p = s.player();
        if (p == null || s.game() == null) return;
        GameGui g = s.game().gui();
        if (g != null && s.game().guiOpen) {
            g.closingByPlugin = true;
            s.game().guiOpen = false;
            p.closeInventory();
        }
    }

    public void ambient() {
        for (Bound b : manager.all()) {
            StationDef d = stationDef(b);
            if (d == null || d.ambientParticle.isEmpty() || manager.session(b) != null) continue;
            if (b.location().getWorld() == null) continue;
            Compat.particle(b.location().add(0.5, 1.05, 0.5), d.ambientParticle, 2, 0.2, 0.05, 0.2, 0.01);
        }
    }
}
