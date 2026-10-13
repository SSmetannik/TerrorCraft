package sgms.sgmscraft.editor;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import sgms.sgmscraft.SGMSCraft;
import sgms.sgmscraft.data.ConfigStore.Kind;
import sgms.sgmscraft.data.StationManager.Bound;
import sgms.sgmscraft.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Small helpers shared by the editor menus. */
public final class Ed {
    private Ed() {}

    public static final String PREFIX = "§2☣ §aРедактор §8» §7";

    public static void msg(Player p, String text) { p.sendMessage(PREFIX + text); }

    // ------------------------------------------------------------------ icons
    public static Material mat(String name) {
        Material m = name == null ? null : Material.matchMaterial(name);
        return m == null ? Material.PAPER : m;
    }

    public static ItemStack icon(String material, String name, List<String> lore) {
        return named(new ItemStack(mat(material), 1), name, lore);
    }

    /** Copy of the item with a new name and lore (null name = keep). */
    public static ItemStack named(ItemStack base, String name, List<String> lore) {
        ItemStack it = base == null ? new ItemStack(Material.PAPER, 1) : base.clone();
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(Text.color(name));
            if (lore != null) meta.setLore(Text.color(lore));
            it.setItemMeta(meta);
        }
        return it;
    }

    public static ItemStack glow(ItemStack it, boolean on) {
        if (!on) return it;
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            try { meta.setEnchantmentGlintOverride(Boolean.TRUE); } catch (Throwable ignored) {}
            it.setItemMeta(meta);
        }
        return it;
    }

    public static ItemStack pane(String material) { return icon(material, " ", null); }

    public static List<String> lore(String... lines) {
        List<String> l = new ArrayList<>();
        for (String s : lines) if (s != null) l.add(s);
        return l;
    }

    /** Adds existing lore of the base item in front (used for item previews). */
    public static List<String> loreOf(ItemStack it) {
        if (it == null) return new ArrayList<>();
        ItemMeta m = it.getItemMeta();
        return m != null && m.hasLore() && m.getLore() != null ? new ArrayList<>(m.getLore()) : new ArrayList<>();
    }

    // ------------------------------------------------------------------ values
    public static String num(double v) {
        if (Math.abs(v - Math.round(v)) < 1e-9) return String.valueOf(Math.round(v));
        String s = String.format(java.util.Locale.ROOT, "%.3f", v);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s;
    }

    public static double round(double v) { return Math.round(v * 1000.0) / 1000.0; }

    public static String str(Map<String, Object> m, String key, String def) {
        Object o = m.get(key);
        return o == null ? def : String.valueOf(o);
    }

    public static double dbl(Map<String, Object> m, String key, double def) {
        Object o = m.get(key);
        if (o instanceof Number n) return n.doubleValue();
        if (o != null) try { return Double.parseDouble(o.toString()); } catch (NumberFormatException ignored) {}
        return def;
    }

    public static boolean bool(Map<String, Object> m, String key, boolean def) {
        Object o = m.get(key);
        if (o instanceof Boolean b) return b;
        if (o != null) return Boolean.parseBoolean(o.toString());
        return def;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Map<String, Object> m, String key) {
        Object o = m.get(key);
        if (o instanceof List<?> l) {
            List<Object> copy = new ArrayList<>((List<Object>) l);
            m.put(key, copy);
            return copy;
        }
        List<Object> l = new ArrayList<>();
        m.put(key, l);
        return l;
    }

    public static List<String> strings(Map<String, Object> m, String key) {
        List<String> out = new ArrayList<>();
        Object o = m.get(key);
        if (o instanceof List<?> l) for (Object e : l) out.add(String.valueOf(e));
        return out;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Map<String, Object> m, String key) {
        Object o = m.get(key);
        if (o instanceof Map<?, ?> mm) return (Map<String, Object>) mm;
        Map<String, Object> n = new java.util.LinkedHashMap<>();
        m.put(key, n);
        return n;
    }

    /** Change by step: left +, right -, shift x5. */
    public static double step(Menu.Click c, double value, double step, double min, double max) {
        double s = step * (c.shift ? 5 : 1);
        value += c.right ? -s : s;
        return round(Math.max(min, Math.min(max, value)));
    }

    public static boolean validId(String id) { return id.matches("[a-z0-9_]{1,40}"); }

    // ------------------------------------------------------------------ saving
    public static boolean commit(SGMSCraft plugin, Player p, Kind k, String id, Map<String, Object> data) {
        boolean ok = plugin.store().save(k, id, data);
        plugin.loadDefinitions();
        if (!ok) msg(p, "§cНе удалось сохранить файл, смотри консоль");
        return ok;
    }

    /** Station near the player for tests (the one they look at, else the closest within 8 blocks). */
    public static Bound nearestStation(SGMSCraft plugin, Player p) {
        Bound look = plugin.stations().at(p.getTargetBlockExact(6));
        if (look != null) return look;
        Bound best = null;
        double bestD = 64;
        for (Bound b : plugin.stations().all()) {
            if (!b.world.equals(p.getWorld().getName())) continue;
            double d = b.location().add(0.5, 0.5, 0.5).distanceSquared(p.getLocation());
            if (d < bestD) { bestD = d; best = b; }
        }
        return best;
    }

    // ------------------------------------------------------------------ choices
    public static final String[][] PARTICLES = {
            {"BUBBLE_POP", "HEART_OF_THE_SEA", "Лопающиеся пузыри"},
            {"SPLASH", "WATER_BUCKET", "Брызги"},
            {"DRIPPING_WATER", "WATER_BUCKET", "Капли воды"},
            {"SMOKE", "GUNPOWDER", "Дым"},
            {"LARGE_SMOKE", "CAMPFIRE", "Густой дым"},
            {"FLAME", "BLAZE_POWDER", "Огонь"},
            {"SMALL_FLAME", "TORCH", "Маленький огонь"},
            {"SOUL_FIRE_FLAME", "SOUL_TORCH", "Синий огонь"},
            {"LAVA", "LAVA_BUCKET", "Искры лавы"},
            {"HAPPY_VILLAGER", "EMERALD", "Зелёные искры"},
            {"ENCHANT", "ENCHANTING_TABLE", "Руны зачарования"},
            {"WITCH", "CAULDRON", "Ведьмины искры"},
            {"PORTAL", "OBSIDIAN", "Портал"},
            {"END_ROD", "END_ROD", "Светлячки"},
            {"CLOUD", "WHITE_WOOL", "Облачко"},
            {"WHITE_ASH", "BONE_MEAL", "Белый пепел"},
            {"ASH", "COAL", "Пепел"},
            {"ITEM_SLIME", "SLIME_BALL", "Слизь"},
            {"CRIT", "IRON_SWORD", "Удары"},
            {"ELECTRIC_SPARK", "LIGHTNING_ROD", "Электрические искры"},
            {"NOTE", "NOTE_BLOCK", "Ноты"},
            {"HEART", "POPPY", "Сердечки"},
            {"GLOW", "GLOW_INK_SAC", "Свечение"},
            {"SPORE_BLOSSOM_AIR", "SPORE_BLOSSOM", "Споры"},
            {"CHERRY_LEAVES", "CHERRY_LEAVES", "Лепестки"},
            {"WAX_ON", "HONEYCOMB", "Воск"},
            {"SCRAPE", "COPPER_INGOT", "Окисление"},
            {"SNOWFLAKE", "SNOWBALL", "Снежинки"},
    };

    public static final String[][] SOUNDS = {
            {"block.brewing_stand.brew", "BREWING_STAND", "Варка зелья"},
            {"block.bubble_column.upwards_inside", "HEART_OF_THE_SEA", "Бурление"},
            {"block.bubble_column.bubble_pop", "PRISMARINE_CRYSTALS", "Лопаются пузыри"},
            {"block.fire.ambient", "CAMPFIRE", "Треск огня"},
            {"block.furnace.fire_crackle", "FURNACE", "Печь"},
            {"block.blastfurnace.fire_crackle", "BLAST_FURNACE", "Плавильня"},
            {"block.smoker.smoke", "SMOKER", "Коптильня"},
            {"block.anvil.use", "ANVIL", "Наковальня"},
            {"block.smithing_table.use", "SMITHING_TABLE", "Кузнечный стол"},
            {"block.grindstone.use", "GRINDSTONE", "Точило"},
            {"ui.loom.take_result", "LOOM", "Ткацкий станок"},
            {"ui.cartography_table.take_result", "CARTOGRAPHY_TABLE", "Картография"},
            {"ui.stonecutter.take_result", "STONECUTTER", "Камнерез"},
            {"block.wool.place", "WHITE_WOOL", "Шерсть"},
            {"item.armor.equip_leather", "LEATHER_CHESTPLATE", "Кожа"},
            {"entity.sheep.shear", "SHEARS", "Ножницы"},
            {"item.book.page_turn", "BOOK", "Страницы"},
            {"block.enchantment_table.use", "ENCHANTING_TABLE", "Зачарование"},
            {"block.beacon.ambient", "BEACON", "Гул маяка"},
            {"block.amethyst_block.chime", "AMETHYST_SHARD", "Звон аметиста"},
            {"block.note_block.chime", "NOTE_BLOCK", "Колокольчик"},
            {"entity.experience_orb.pickup", "EXPERIENCE_BOTTLE", "Опыт"},
            {"block.composter.fill", "COMPOSTER", "Компостер"},
            {"block.slime_block.step", "SLIME_BLOCK", "Хлюпанье"},
            {"entity.generic.splash", "WATER_BUCKET", "Всплеск"},
            {"block.lava.pop", "LAVA_BUCKET", "Лава"},
    };
}
