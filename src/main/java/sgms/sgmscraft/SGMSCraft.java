package sgms.sgmscraft;

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
import sgms.sgmscraft.data.Defs.RecipeDef;
import sgms.sgmscraft.data.Defs.StationDef;
import sgms.sgmscraft.data.ConfigStore;
import sgms.sgmscraft.data.ConfigStore.Kind;
import sgms.sgmscraft.data.StationManager;
import sgms.sgmscraft.data.StationManager.Bound;
import sgms.sgmscraft.gui.Guis.GameGui;
import sgms.sgmscraft.session.Session;
import sgms.sgmscraft.util.Compat;
import sgms.sgmscraft.util.Text;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class SGMSCraft extends JavaPlugin {
    private static SGMSCraft instance;

    private final Map<String, StationDef> stations = new LinkedHashMap<>();
    private final Map<String, RecipeDef> recipes = new LinkedHashMap<>();
    private final Map<String, String> messages = new HashMap<>();
    private StationManager manager;
    private ConfigStore store;
    private BukkitTask ticker;
    private NamespacedKey stationKey;
    private NamespacedKey legacyStationKey; // метка старых предметов (плагин назывался TerrorCraft), только чтение
    private double maxDistance = 8;
    private boolean holograms = true;

    public static SGMSCraft get() { return instance; }

    @Override
    public void onEnable() {
        instance = this;
        migrateOldFolder();
        stationKey = new NamespacedKey(this, "station");
        legacyStationKey = new NamespacedKey("terrorcraft", "station");
        manager = new StationManager(this);
        store = new ConfigStore(this);
        loadAll();
        Bukkit.getPluginManager().registerEvents(new Listeners(this), this);
        Bukkit.getPluginManager().registerEvents(new sgms.sgmscraft.editor.EditorListener(this), this);
        Commands cmd = new Commands(this);
        if (getCommand("tcraft") != null) {
            getCommand("tcraft").setExecutor(cmd);
            getCommand("tcraft").setTabCompleter(cmd);
        }
        ticker = Bukkit.getScheduler().runTaskTimer(this, this::tickAll, 1L, 1L);
        getLogger().info("Loaded " + stations.size() + " stations, " + recipes.size() + " recipes, "
                + manager.all().size() + " placed stations");
    }

    /** Плагин раньше назывался TerrorCraft: переносим его папку настроек, если новой ещё нет. */
    private void migrateOldFolder() {
        File data = getDataFolder();
        File old = new File(data.getParentFile(), "TerrorCraft");
        if (old.isDirectory() && !data.exists()) {
            if (old.renameTo(data)) getLogger().info("Папка plugins/TerrorCraft перенесена в plugins/" + data.getName());
            else getLogger().warning("Не удалось перенести plugins/TerrorCraft — перенеси вручную в plugins/" + data.getName());
        }
    }

    @Override
    public void onDisable() {
        if (ticker != null) ticker.cancel();
        if (manager != null) for (Session s : manager.sessions()) s.cancel();
    }

    public void loadAll() {
        if (manager != null) for (Session s : manager.sessions()) s.cancel();
        boolean fresh = !new File(getDataFolder(), "config.yml").exists();
        if (fresh) saveResource("config.yml", false);
        if (!store.folder(Kind.STATION).exists() && !new File(getDataFolder(), "stations.yml").exists())
            saveResource("stations.yml", true);
        if (!store.folder(Kind.RECIPE).exists() && !new File(getDataFolder(), "recipes.yml").exists())
            saveResource("recipes.yml", true);
        store.migrate(Kind.STATION);
        store.migrate(Kind.RECIPE);

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml"));
        maxDistance = cfg.getDouble("settings.max-distance", 8);
        holograms = cfg.getBoolean("settings.holograms", true);
        messages.clear();
        ConfigurationSection ms = cfg.getConfigurationSection("messages");
        if (ms != null) for (String k : ms.getKeys(false)) messages.put(k, ms.getString(k, ""));

        loadDefinitions();
        manager.load();
    }

    /** Re-reads stations and recipes only (running crafts keep going). Used by the editor after each change. */
    public void loadDefinitions() {
        stations.clear();
        for (Map.Entry<String, ConfigurationSection> e : store.load(Kind.STATION).entrySet()) {
            try {
                stations.put(e.getKey(), StationDef.load(e.getKey(), e.getValue()));
            } catch (Exception ex) {
                getLogger().warning("Station " + e.getKey() + " has an error: " + ex.getMessage());
            }
        }
        recipes.clear();
        for (Map.Entry<String, ConfigurationSection> e : store.load(Kind.RECIPE).entrySet()) {
            try {
                recipes.put(e.getKey(), RecipeDef.load(e.getKey(), e.getValue()));
            } catch (Exception ex) {
                getLogger().warning("Recipe " + e.getKey() + " has an error: " + ex.getMessage());
            }
        }
        for (StationDef s : stations.values()) {
            for (String r : s.recipes) {
                if (!recipes.containsKey(r)) getLogger().warning("Station " + s.id + " uses unknown recipe '" + r + "'");
            }
        }
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
    public ConfigStore store() { return store; }
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
        if (v == null) v = meta.getPersistentDataContainer().get(legacyStationKey, PersistentDataType.STRING);
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
