package sgms.sgmscraft.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import sgms.sgmscraft.SGMSCraft;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stations and recipes live in folders, one object per file:
 *   plugins/SGMSCraft/stations/<id>.yml
 *   plugins/SGMSCraft/recipes/<id>.yml      (sub-folders allowed: recipes/clothes/shirt.yml)
 * A file may also hold several objects under a "recipes:" / "stations:" section (old format).
 * The in-game editor reads and writes these files through this class.
 */
public class ConfigStore {
    public enum Kind {
        STATION("stations"), RECIPE("recipes");
        public final String folder;
        Kind(String folder) { this.folder = folder; }
    }

    /** Where an object is stored: file + path inside the file ("" = the whole file). */
    public static final class Source {
        public final File file;
        public final String path;
        Source(File file, String path) { this.file = file; this.path = path; }
    }

    private final SGMSCraft plugin;
    private final Map<Kind, Map<String, Source>> sources = new LinkedHashMap<>();

    public ConfigStore(SGMSCraft plugin) {
        this.plugin = plugin;
        for (Kind k : Kind.values()) sources.put(k, new LinkedHashMap<>());
    }

    public File folder(Kind k) { return new File(plugin.getDataFolder(), k.folder); }

    // ------------------------------------------------------------------ migration
    /** Moves old single-file stations.yml / recipes.yml into the folders (once). */
    public void migrate(Kind k) {
        File dir = folder(k);
        File old = new File(plugin.getDataFolder(), k.folder + ".yml");
        if (!old.exists()) return;
        if (!dir.exists() && !dir.mkdirs()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(old);
        ConfigurationSection sec = y.getConfigurationSection(k.folder);
        int moved = 0;
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection c = sec.getConfigurationSection(id);
                if (c == null) continue;
                File f = new File(dir, id + ".yml");
                if (f.exists()) continue;
                writeRoot(f, toMap(c));
                moved++;
            }
        }
        File backup = new File(plugin.getDataFolder(), k.folder + ".yml.old");
        if (backup.exists()) backup.delete();
        if (old.renameTo(backup)) {
            plugin.getLogger().info("Moved " + moved + " " + k.folder + " from " + old.getName()
                    + " into the folder " + k.folder + "/ (old file kept as " + backup.getName() + ")");
        }
    }

    // ------------------------------------------------------------------ loading
    /** Reads every object of a kind. Keeps track of where each one came from. */
    public Map<String, ConfigurationSection> load(Kind k) {
        Map<String, Source> src = sources.get(k);
        src.clear();
        Map<String, ConfigurationSection> out = new LinkedHashMap<>();
        File dir = folder(k);
        if (!dir.exists()) dir.mkdirs();
        List<File> files = new ArrayList<>();
        collect(dir, files);
        for (File f : files) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            String stem = f.getName().substring(0, f.getName().length() - 4);
            if (looksLikeObject(k, y)) {
                put(k, out, stem, y, new Source(f, ""), f);
                continue;
            }
            ConfigurationSection multi = y.getConfigurationSection(k.folder);
            String prefix = k.folder + ".";
            if (multi == null) { multi = y; prefix = ""; }
            for (String id : multi.getKeys(false)) {
                ConfigurationSection c = multi.getConfigurationSection(id);
                if (c != null) put(k, out, id, c, new Source(f, prefix + id), f);
            }
        }
        return out;
    }

    private void put(Kind k, Map<String, ConfigurationSection> out, String id, ConfigurationSection c, Source s, File f) {
        if (out.containsKey(id)) {
            plugin.getLogger().warning("Duplicate " + k.name().toLowerCase() + " id '" + id + "' in " + f.getPath() + " — skipped");
            return;
        }
        out.put(id, c);
        sources.get(k).put(id, s);
    }

    private static boolean looksLikeObject(Kind k, ConfigurationSection y) {
        if (k == Kind.RECIPE) return y.contains("stages") || y.contains("rewards");
        return y.contains("recipes") && !y.isConfigurationSection("recipes") || y.contains("item") || y.contains("hologram-height");
    }

    private static void collect(File dir, List<File> out) {
        File[] list = dir.listFiles();
        if (list == null) return;
        Arrays.sort(list, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File f : list) {
            if (f.isDirectory()) collect(f, out);
            else if (f.getName().toLowerCase().endsWith(".yml")) out.add(f);
        }
    }

    // ------------------------------------------------------------------ editor access
    public boolean exists(Kind k, String id) { return sources.get(k).containsKey(id); }

    public Source source(Kind k, String id) { return sources.get(k).get(id); }

    /** Fresh copy of the object's data (nested maps / lists), read from disk. */
    public Map<String, Object> raw(Kind k, String id) {
        Source s = sources.get(k).get(id);
        if (s == null) return new LinkedHashMap<>();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(s.file);
        ConfigurationSection c = s.path.isEmpty() ? y : y.getConfigurationSection(s.path);
        return c == null ? new LinkedHashMap<>() : toMap(c);
    }

    /** Writes the object back to where it came from (or into a new file). */
    public boolean save(Kind k, String id, Map<String, Object> data) {
        Source s = sources.get(k).get(id);
        try {
            if (s == null) {
                File f = new File(folder(k), id + ".yml");
                f.getParentFile().mkdirs();
                writeRoot(f, data);
                sources.get(k).put(id, new Source(f, ""));
            } else if (s.path.isEmpty()) {
                writeRoot(s.file, data);
            } else {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(s.file);
                y.set(s.path, null);
                y.createSection(s.path, data);
                y.save(s.file);
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Cannot save " + k.name().toLowerCase() + " " + id + ": " + e);
            return false;
        }
    }

    public boolean delete(Kind k, String id) {
        Source s = sources.get(k).remove(id);
        if (s == null) return false;
        try {
            if (s.path.isEmpty()) return s.file.delete();
            YamlConfiguration y = YamlConfiguration.loadConfiguration(s.file);
            y.set(s.path, null);
            y.save(s.file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Cannot delete " + id + ": " + e);
            return false;
        }
    }

    private void writeRoot(File f, Map<String, Object> data) {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Object> e : data.entrySet()) {
            if (e.getValue() instanceof Map<?, ?> m) y.createSection(e.getKey(), m);
            else y.set(e.getKey(), e.getValue());
        }
        try {
            y.save(f);
        } catch (IOException e) {
            plugin.getLogger().warning("Cannot write " + f.getPath() + ": " + e);
        }
    }

    // ------------------------------------------------------------------ helpers
    /** Section -> plain LinkedHashMap tree (sections become maps, lists are copied). */
    public static Map<String, Object> toMap(ConfigurationSection c) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : c.getKeys(false)) {
            if (key.startsWith("__")) continue; // runtime helper sections
            out.put(key, copy(c.isConfigurationSection(key) ? c.getConfigurationSection(key) : c.get(key)));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static Object copy(Object o) {
        if (o instanceof ConfigurationSection cs) return toMap(cs);
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) out.put(String.valueOf(e.getKey()), copy(e.getValue()));
            return out;
        }
        if (o instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object e : l) out.add(copy(e));
            return out;
        }
        if (o instanceof org.bukkit.inventory.ItemStack it) return it.clone();
        return o;
    }
}
