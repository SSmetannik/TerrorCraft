package sgms.terrorcraft.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import sgms.terrorcraft.TerrorCraft;
import sgms.terrorcraft.session.Session;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Which blocks in the world are crafting stations, and which sessions run on them. */
public class StationManager {
    public static final class Bound {
        public final String world;
        public final int x, y, z;
        public final String stationId;
        public final boolean placed;  // placed from a station item (drops it when broken)

        public Bound(String world, int x, int y, int z, String stationId, boolean placed) {
            this.world = world; this.x = x; this.y = y; this.z = z; this.stationId = stationId; this.placed = placed;
        }

        public String key() { return key(world, x, y, z); }

        public Location location() {
            World w = Bukkit.getWorld(world);
            return new Location(w, x, y, z);
        }

        public static String key(String w, int x, int y, int z) { return w + ";" + x + ";" + y + ";" + z; }
    }

    private final TerrorCraft plugin;
    private final File file;
    private final Map<String, Bound> bound = new HashMap<>();
    private final Map<String, Session> sessions = new HashMap<>();

    public StationManager(TerrorCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public static String key(Block b) { return Bound.key(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()); }

    public Bound at(Block b) { return b == null ? null : bound.get(key(b)); }

    public Bound bind(Block b, String stationId, boolean placed) {
        Bound bd = new Bound(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), stationId, placed);
        bound.put(bd.key(), bd);
        save();
        return bd;
    }

    public void unbind(Bound b) {
        Session s = sessions.get(b.key());
        if (s != null) s.cancel();
        bound.remove(b.key());
        save();
    }

    public Collection<Bound> all() { return bound.values(); }

    public Session session(Bound b) { return sessions.get(b.key()); }

    public void startSession(Session s) { sessions.put(s.station().key(), s); }

    public void endSession(Bound b) { sessions.remove(b.key()); }

    public Collection<Session> sessions() { return new ArrayList<>(sessions.values()); }

    public Session sessionOf(java.util.UUID player) {
        for (Session s : sessions.values()) if (s.owner().equals(player)) return s;
        return null;
    }

    public void load() {
        bound.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String line : y.getStringList("stations")) {
            String[] p = line.split(";");
            if (p.length < 5) continue;
            try {
                Bound b = new Bound(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), p[4],
                        p.length > 5 && Boolean.parseBoolean(p[5]));
                bound.put(b.key(), b);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Bad station entry in data.yml: " + line);
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> lines = new ArrayList<>();
        for (Bound b : bound.values()) lines.add(b.key() + ";" + b.stationId + ";" + b.placed);
        y.set("stations", lines);
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Cannot save data.yml: " + e.getMessage());
        }
    }
}
