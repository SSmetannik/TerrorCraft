package sgms.sgmscraft.editor;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import sgms.sgmscraft.SGMSCraft;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** Turn entries on/off (which recipes a station has, which stations a recipe is in). */
public class ToggleMenu extends Menu {
    public static final class Entry {
        final String id;
        final ItemStack icon;
        final String label;

        public Entry(String id, ItemStack icon, String label) { this.id = id; this.icon = icon; this.label = label; }
    }

    private final String title;
    private final List<Entry> entries;
    private final Predicate<String> isOn;
    private final BiConsumer<String, Boolean> onToggle;
    private int page;

    public ToggleMenu(SGMSCraft plugin, Player player, Menu parent, String title, List<Entry> entries,
                      Predicate<String> isOn, BiConsumer<String, Boolean> onToggle) {
        super(plugin, player, parent);
        this.title = title;
        this.entries = entries;
        this.isOn = isOn;
        this.onToggle = onToggle;
    }

    @Override protected String title() { return title; }
    @Override protected int rows() { return 6; }

    @Override
    protected void draw() {
        int from = page * 45;
        for (int i = 0; i < 45 && from + i < entries.size(); i++) {
            Entry e = entries.get(from + i);
            boolean on = isOn.test(e.id);
            List<String> lore = new ArrayList<>();
            lore.add("§8" + e.id);
            lore.add(on ? "§a✔ Включено §7— нажми, чтобы убрать" : "§c✖ Выключено §7— нажми, чтобы добавить");
            ItemStack icon = Ed.named(on ? e.icon : Ed.icon("GRAY_DYE", null, null), (on ? "§a" : "§7") + e.label, lore);
            set(i, Ed.glow(icon, on), c -> {
                onToggle.accept(e.id, !isOn.test(e.id));
                redraw();
            });
        }
        bottomBar();
        if (entries.isEmpty()) set(22, Ed.icon("BARRIER", "§7Пока пусто", null), null);
        if (page > 0) set(48, Ed.icon("SPECTRAL_ARROW", "§7◀ Страница " + page, null), c -> { page--; redraw(); });
        if (from + 45 < entries.size()) set(50, Ed.icon("SPECTRAL_ARROW", "§7Страница " + (page + 2) + " ▶", null), c -> { page++; redraw(); });
    }
}
