package sgms.sgmscraft.editor;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import sgms.sgmscraft.SGMSCraft;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Pick one value from a list (particles, sounds, mini-games, stage types...). */
public class ChoiceMenu extends Menu {
    public static final class Option {
        final String id;
        final ItemStack icon;
        final String label;
        final List<String> lore;

        public Option(String id, ItemStack icon, String label, List<String> lore) {
            this.id = id; this.icon = icon; this.label = label; this.lore = lore;
        }

        public Option(String id, String material, String label) {
            this(id, Ed.icon(material, label, null), label, null);
        }
    }

    private final String title;
    private final List<Option> options;
    private final String current;
    private final Consumer<String> onPick;
    private String customQuestion;
    private String noneLabel;
    private int page;

    public ChoiceMenu(SGMSCraft plugin, Player player, Menu parent, String title, List<Option> options,
                      String current, Consumer<String> onPick) {
        super(plugin, player, parent);
        this.title = title;
        this.options = new ArrayList<>(options);
        this.current = current;
        this.onPick = onPick;
    }

    /** Adds a "type your own value" button. */
    public ChoiceMenu custom(String question) { this.customQuestion = question; return this; }

    /** Adds a "nothing" button that picks "". */
    public ChoiceMenu none(String label) { this.noneLabel = label; return this; }

    @Override protected String title() { return title; }
    @Override protected int rows() { return 6; }

    @Override
    protected void draw() {
        int from = page * 45;
        for (int i = 0; i < 45 && from + i < options.size(); i++) {
            Option o = options.get(from + i);
            boolean sel = o.id.equalsIgnoreCase(current == null ? "" : current);
            List<String> lore = new ArrayList<>();
            if (o.lore != null) lore.addAll(o.lore);
            lore.add("§8" + o.id);
            lore.add(sel ? "§a✔ Выбрано" : "§e▶ Нажми, чтобы выбрать");
            ItemStack icon = Ed.named(o.icon, (sel ? "§a" : "§f") + o.label, lore);
            set(i, Ed.glow(icon, sel), c -> pick(o.id));
        }
        bottomBar();
        if (page > 0) set(48, Ed.icon("SPECTRAL_ARROW", "§7◀ Страница " + page, null), c -> { page--; redraw(); });
        if (from + 45 < options.size()) set(50, Ed.icon("SPECTRAL_ARROW", "§7Страница " + (page + 2) + " ▶", null), c -> { page++; redraw(); });
        if (noneLabel != null) set(47, Ed.icon("BARRIER", "§c" + noneLabel, null), c -> pick(""));
        if (customQuestion != null) {
            set(49, Ed.icon("NAME_TAG", "§e✎ Ввести своё значение", Ed.lore("§7Если нужного варианта нет в списке")),
                    c -> Input.ask(this, customQuestion, current, text -> { onPick.accept(text); return parent; }));
        }
    }

    private void pick(String id) {
        onPick.accept(id);
        back();
    }
}
