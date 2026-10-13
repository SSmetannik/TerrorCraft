package sgms.sgmscraft.util;

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
    public ItemStack exact;      // full item (enchantments, potions...) saved by the editor

    public boolean isItemsAdder() { return id != null && id.toLowerCase().startsWith("ia:"); }
    public String iaId() { return id.substring(3); }

    public static ItemSpec parse(Object o) {
        ItemSpec s = new ItemSpec();
        if (o instanceof ItemStack st) {
            s.exact = st.clone();
            s.id = st.getType().name();
            s.amount = Math.max(1, st.getAmount());
            return s;
        }
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
        if (exact != null) {
            it = exact.clone();
            it.setAmount(Math.max(1, amount));
            return it;
        }
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
        if (exact != null) return exact.isSimilar(it);
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
        if (exact != null) {
            ItemMeta meta = exact.getItemMeta();
            if (meta != null && meta.hasDisplayName()) return meta.getDisplayName();
            return "§f" + exact.getType().name().toLowerCase().replace('_', ' ');
        }
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

    // ------------------------------------------------------------------ editor support

    /**
     * Turns a real item into a spec. ItemsAdder items are saved by id, plain items as
     * "MATERIAL xN" (+ name/lore), anything more complex (enchantments, potions...) as the full item.
     */
    public static ItemSpec fromStack(ItemStack st) {
        ItemSpec s = new ItemSpec();
        s.amount = Math.max(1, st.getAmount());
        String ia = IA.idOf(st);
        if (ia != null) {
            s.id = "ia:" + ia;
            return s;
        }
        s.id = st.getType().name();
        ItemMeta meta = st.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) s.name = Text.uncolor(meta.getDisplayName());
            if (meta.hasLore() && meta.getLore() != null) {
                s.lore = new ArrayList<>();
                for (String l : meta.getLore()) s.lore.add(Text.uncolor(l));
            }
            s.matchName = s.name != null;
        }
        ItemStack test = s.build();
        if (test == null || !test.isSimilar(st)) {
            s.name = null;
            s.lore = null;
            s.matchName = false;
            s.exact = st.clone();
            s.exact.setAmount(1);
        }
        return s;
    }

    /** Value to write into YAML. */
    public Object toYaml() {
        if (exact != null) {
            ItemStack it = exact.clone();
            it.setAmount(Math.max(1, amount));
            return it;
        }
        if (name == null && lore == null && !glow && !matchName) return amount > 1 ? id + " x" + amount : id;
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("item", id);
        if (amount > 1) m.put("amount", amount);
        if (name != null) m.put("name", name);
        if (lore != null) m.put("lore", new ArrayList<>(lore));
        if (glow) m.put("glow", true);
        if (matchName) m.put("match-name", true);
        return m;
    }

    /** Same item kind (amount ignored) — used to merge stacks in the editor. */
    public boolean sameKind(ItemSpec o) {
        if (exact != null || o.exact != null) return exact != null && o.exact != null && exact.isSimilar(o.exact);
        return id.equalsIgnoreCase(o.id) && java.util.Objects.equals(name, o.name) && java.util.Objects.equals(lore, o.lore)
                && glow == o.glow;
    }

    public ItemSpec copy() {
        ItemSpec s = new ItemSpec();
        s.id = id; s.amount = amount; s.name = name; s.lore = lore == null ? null : new ArrayList<>(lore);
        s.glow = glow; s.matchName = matchName; s.exact = exact == null ? null : exact.clone();
        return s;
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
