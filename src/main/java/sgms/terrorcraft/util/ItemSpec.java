package sgms.terrorcraft.util;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Item description from config.
 *   "GLASS_BOTTLE"                      vanilla
 *   "ia:namespace:id"                   ItemsAdder
 *   "GLASS_BOTTLE x3"                   with amount
 * or a map: {item: ..., amount: 2, name: "&a...", lore: [...], glow: true}
 */
public class ItemSpec {
    public String id;            // material name or ia:ns:id
    public int amount = 1;
    public String name;          // optional display name (colour codes)
    public List<String> lore;
    public boolean glow;
    public boolean matchName;    // when true, ingredient must also have this name

    public boolean isItemsAdder() { return id != null && id.toLowerCase().startsWith("ia:"); }
    public String iaId() { return id.substring(3); }

    public static ItemSpec parse(Object o) {
        ItemSpec s = new ItemSpec();
        if (o instanceof String str) {
            str = str.trim();
            int x = str.toLowerCase().lastIndexOf(" x");
            if (x > 0) {
                try { s.amount = Integer.parseInt(str.substring(x + 2).trim()); str = str.substring(0, x).trim(); }
                catch (NumberFormatException ignored) {}
            }
            s.id = str;
        } else if (o instanceof Map<?, ?> m) {
            Object id = m.get("item");
            if (id == null) id = m.get("material");
            s.id = id == null ? "STONE" : id.toString();
            if (m.get("amount") instanceof Number n) s.amount = n.intValue();
            if (m.get("name") != null) s.name = m.get("name").toString();
            if (m.get("lore") instanceof List<?> l) {
                s.lore = new ArrayList<>();
                for (Object e : l) s.lore.add(String.valueOf(e));
            }
            if (m.get("glow") instanceof Boolean b) s.glow = b;
            if (m.get("match-name") instanceof Boolean b) s.matchName = b;
        } else if (o instanceof ConfigurationSection c) {
            s.id = c.getString("item", c.getString("material", "STONE"));
            s.amount = c.getInt("amount", 1);
            s.name = c.getString("name");
            if (c.contains("lore")) s.lore = c.getStringList("lore");
            s.glow = c.getBoolean("glow", false);
            s.matchName = c.getBoolean("match-name", false);
        }
        return s;
    }

    public static List<ItemSpec> parseList(List<?> list) {
        List<ItemSpec> out = new ArrayList<>();
        if (list != null) for (Object o : list) out.add(parse(o));
        return out;
    }

    /** Builds the ItemStack (amount included). Returns null when the item cannot be resolved. */
    public ItemStack build() {
        ItemStack it;
        if (isItemsAdder()) {
            it = IA.get(iaId());
            if (it == null) return null;
            it = it.clone();
        } else {
            Material m = Material.matchMaterial(id);
            if (m == null) return null;
            it = new ItemStack(m, 1);
        }
        it.setAmount(Math.max(1, amount));
        if (name != null || lore != null || glow) {
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                if (name != null) meta.setDisplayName(Text.color(name));
                if (lore != null) meta.setLore(Text.color(lore));
                if (glow) {
                    try { meta.setEnchantmentGlintOverride(Boolean.TRUE); } catch (Throwable ignored) {}
                }
                it.setItemMeta(meta);
            }
        }
        return it;
    }

    /** Does the given stack count as this ingredient (amount not checked). */
    public boolean matches(ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        if (isItemsAdder()) {
            String got = IA.idOf(it);
            return got != null && got.equalsIgnoreCase(iaId());
        }
        Material m = Material.matchMaterial(id);
        if (m == null || it.getType() != m) return false;
        if (IA.idOf(it) != null) return false; // a custom item on a vanilla base is not the vanilla ingredient
        if (matchName && name != null) {
            ItemMeta meta = it.getItemMeta();
            return meta != null && meta.hasDisplayName() && Text.strip(meta.getDisplayName()).equals(Text.strip(Text.color(name)));
        }
        return true;
    }

    /** Human readable name for holograms/lore. */
    public String displayName() {
        if (name != null) return Text.color(name);
        if (isItemsAdder()) {
            ItemStack it = IA.get(iaId());
            if (it != null) {
                ItemMeta meta = it.getItemMeta();
                if (meta != null && meta.hasDisplayName()) return meta.getDisplayName();
            }
            String s = iaId();
            return "§f" + s.substring(s.indexOf(':') + 1).replace('_', ' ');
        }
        return "§f" + id.toLowerCase().replace('_', ' ');
    }

    /** ItemsAdder via reflection (no hard dependency). */
    public static final class IA {
        private static Class<?> customStack;
        private static Method getInstance, getItemStack, byItemStack, getNamespacedID;
        private static boolean resolved, available;

        private static boolean init() {
            if (resolved) return available;
            resolved = true;
            try {
                customStack = Class.forName("dev.lone.itemsadder.api.CustomStack");
                getInstance = customStack.getMethod("getInstance", String.class);
                getItemStack = customStack.getMethod("getItemStack");
                byItemStack = customStack.getMethod("byItemStack", ItemStack.class);
                getNamespacedID = customStack.getMethod("getNamespacedID");
                available = true;
            } catch (Throwable t) {
                available = false;
            }
            return available;
        }

        public static ItemStack get(String nsId) {
            if (!init()) return null;
            try {
                Object cs = getInstance.invoke(null, nsId);
                return cs == null ? null : (ItemStack) getItemStack.invoke(cs);
            } catch (Throwable t) {
                return null;
            }
        }

        public static String idOf(ItemStack it) {
            if (it == null || !init()) return null;
            try {
                Object cs = byItemStack.invoke(null, it);
                return cs == null ? null : (String) getNamespacedID.invoke(cs);
            } catch (Throwable t) {
                return null;
            }
        }
    }
}
