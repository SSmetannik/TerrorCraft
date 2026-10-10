package sgms.terrorcraft.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Version-tolerant helpers. Everything that changed between Minecraft versions
 * (action bar API, particle names) goes through reflection so the plugin keeps
 * working on new server builds.
 */
public final class Compat {
    private Compat() {}

    private static Method actionBarString;
    private static Method legacySerializer, legacyDeserialize, audienceActionBar;
    private static boolean actionBarResolved;

    public static void actionBar(Player p, String text) {
        try {
            if (!actionBarResolved) {
                actionBarResolved = true;
                try {
                    Class<?> lcs = Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
                    Class<?> comp = Class.forName("net.kyori.adventure.text.Component");
                    Class<?> aud = Class.forName("net.kyori.adventure.audience.Audience");
                    legacySerializer = lcs.getMethod("legacySection");
                    legacyDeserialize = lcs.getMethod("deserialize", String.class);
                    audienceActionBar = aud.getMethod("sendActionBar", comp);
                } catch (Throwable ignored) {
                    legacySerializer = null;
                }
                if (legacySerializer == null) {
                    try {
                        actionBarString = ((Object) p).getClass().getMethod("sendActionBar", String.class);
                    } catch (Throwable ignored) {}
                }
            }
            if (legacySerializer != null) {
                Object ser = legacySerializer.invoke(null);
                Object c = legacyDeserialize.invoke(ser, text);
                audienceActionBar.invoke(p, c);
                return;
            }
            if (actionBarString != null) {
                actionBarString.invoke(p, text);
                return;
            }
        } catch (Throwable ignored) {}
        p.sendTitle("", text, 0, 10, 0);
    }

    private static final Map<String, Object> PARTICLES = new HashMap<>();
    private static Method spawnParticle;
    private static Class<?> particleClass;
    private static boolean particleBroken;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object particle(String name) {
        String key = name.toUpperCase();
        if (PARTICLES.containsKey(key)) return PARTICLES.get(key);
        Object result = null;
        try {
            if (particleClass == null) particleClass = Class.forName("org.bukkit.Particle");
            if (particleClass.isEnum()) {
                try { result = Enum.valueOf((Class) particleClass, key); } catch (IllegalArgumentException ignored) {}
            }
            if (result == null) {
                try { result = particleClass.getField(key).get(null); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        PARTICLES.put(key, result);
        return result;
    }

    /** Spawns a particle by its modern Bukkit name (HAPPY_VILLAGER, BUBBLE_POP, SMOKE, FLAME, ...). Silently ignores unknown names. */
    public static void particle(Location l, String name, int count, double dx, double dy, double dz, double speed) {
        if (particleBroken || name == null || name.isEmpty() || l.getWorld() == null) return;
        Object p = particle(name);
        if (p == null) return;
        try {
            if (spawnParticle == null) {
                spawnParticle = World.class.getMethod("spawnParticle", particleClass, Location.class, int.class,
                        double.class, double.class, double.class, double.class);
            }
            spawnParticle.invoke(l.getWorld(), p, l, count, dx, dy, dz, speed);
        } catch (Throwable t) {
            particleBroken = true;
        }
    }

    public static void sound(Location l, String sound, float volume, float pitch) {
        if (sound == null || sound.isEmpty() || l.getWorld() == null) return;
        try { l.getWorld().playSound(l, sound, volume, pitch); } catch (Throwable ignored) {}
    }

    public static void sound(Player p, String sound, float volume, float pitch) {
        if (sound == null || sound.isEmpty()) return;
        try { p.playSound(p.getLocation(), sound, volume, pitch); } catch (Throwable ignored) {}
    }
}
