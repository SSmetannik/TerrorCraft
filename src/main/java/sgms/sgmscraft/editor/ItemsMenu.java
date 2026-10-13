package sgms.sgmscraft.editor;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import sgms.sgmscraft.SGMSCraft;
import sgms.sgmscraft.util.ItemSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Edit a list of items (ingredients, rewards, buttons of the memory game).
 * Works with copies: nothing is taken from the player's inventory.
 */
public class ItemsMenu extends Menu {
    private final String title;
    private final List<ItemSpec> items;
    private final int max;
    private final boolean amounts;
    private final Consumer<List<ItemSpec>> onChange;

    public ItemsMenu(SGMSCraft plugin, Player player, Menu parent, String title, List<ItemSpec> initial,
                     int max, boolean amounts, Consumer<List<ItemSpec>> onChange) {
        super(plugin, player, parent);
        this.title = title;
        this.items = new ArrayList<>();
        for (ItemSpec s : initial) this.items.add(s.copy());
        this.max = Math.min(45, max);
        this.amounts = amounts;
        this.onChange = onChange;
    }

    @Override protected String title() { return title; }
    @Override protected int rows() { return 6; }

    @Override
    protected void draw() {
        for (int i = 0; i < 45; i++) {
            final int idx = i;
            if (i < items.size()) {
                ItemSpec s = items.get(i);
                ItemStack it = s.build();
                boolean missing = it == null;
                if (missing) it = Ed.icon("BARRIER", "§c" + s.id, null);
                it.setAmount(Math.max(1, Math.min(64, s.amount)));
                List<String> lore = Ed.loreOf(it);
                if (!lore.isEmpty()) lore.add("");
                if (missing) lore.add("§cПредмет не найден! Проверь id");
                lore.add("§8" + describe(s));
                if (amounts) {
                    lore.add("§7Количество: §f" + s.amount);
                    lore.add("§eЛКМ §7+1   §eПКМ §7−1   §eShift+ЛКМ §7+10");
                }
                lore.add("§eShift+ПКМ §7— убрать");
                lore.add("§eКлик предметом §7— заменить");
                set(i, Ed.named(it, null, lore), c -> clickEntry(idx, c));
            } else if (i < max) {
                set(i, null, c -> {
                    if (c.cursor != null) add(c.cursor);
                });
            }
        }
        bottomBar();
        set(49, Ed.icon("OAK_SIGN", "§eКак добавлять предметы", Ed.lore(
                "§7• Возьми предмет курсором и кликни",
                "§7  по пустой ячейке сверху",
                "§7• или Shift+клик по предмету",
                "§7  в своём инвентаре",
                "§7Предметы не тратятся — сохраняется копия.",
                "",
                "§7Сохраняется: тип, количество, название,",
                "§7описание. Предметы ItemsAdder — по id.",
                "§7Зачарованные и прочие особые предметы",
                "§7сохраняются целиком.",
                "§8Изменения сохраняются сразу")), null);
        if (!items.isEmpty()) {
            set(53, Ed.icon("LAVA_BUCKET", "§cОчистить всё", Ed.lore("§eShift+клик §7— убрать все предметы")), c -> {
                if (!c.shift) return;
                items.clear();
                changed();
            });
        }
    }

    private static String describe(ItemSpec s) {
        if (s.exact != null) return s.id + " (целиком)";
        return s.id;
    }

    private void clickEntry(int idx, Click c) {
        ItemSpec s = items.get(idx);
        if (c.cursor != null) {
            ItemSpec n = ItemSpec.fromStack(c.cursor);
            if (!amounts) n.amount = 1;
            items.set(idx, n);
        } else if (c.shift && c.right) {
            items.remove(idx);
        } else if (amounts) {
            int d = c.shift ? 10 : 1;
            s.amount += c.right ? -d : d;
            if (s.amount <= 0) items.remove(idx);
            else s.amount = Math.min(s.amount, 10000);
        } else if (c.right) {
            items.remove(idx);
        } else {
            return;
        }
        changed();
    }

    private void add(ItemStack st) {
        ItemSpec n = ItemSpec.fromStack(st);
        if (!amounts) n.amount = 1;
        for (ItemSpec s : items) {
            if (s.sameKind(n)) {
                if (amounts) s.amount += n.amount;
                changed();
                return;
            }
        }
        if (items.size() >= max) {
            Ed.msg(player, "§cБольше нельзя (максимум " + max + ")");
            return;
        }
        items.add(n);
        changed();
    }

    @Override
    public void onBottomShiftClick(ItemStack it) { add(it); }

    private void changed() {
        onChange.accept(new ArrayList<>(items));
        redraw();
    }
}
