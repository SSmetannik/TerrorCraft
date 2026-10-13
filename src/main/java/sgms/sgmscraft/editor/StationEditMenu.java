package sgms.sgmscraft.editor;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import sgms.sgmscraft.SGMSCraft;
import sgms.sgmscraft.data.ConfigStore.Kind;
import sgms.sgmscraft.data.Defs.RecipeDef;
import sgms.sgmscraft.data.Defs.StationDef;
import sgms.sgmscraft.util.ItemSpec;
import sgms.sgmscraft.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StationEditMenu extends Menu {
    private final String id;
    private Map<String, Object> raw;

    public StationEditMenu(SGMSCraft plugin, Player player, Menu parent, String id) {
        super(plugin, player, parent);
        this.id = id;
    }

    @Override protected String title() { return "§aСтанция: §f" + id; }
    @Override protected int rows() { return 6; }

    @Override
    public void open() {
        raw = plugin.store().raw(Kind.STATION, id);
        super.open();
    }

    private void save() { Ed.commit(plugin, player, Kind.STATION, id, raw); redraw(); }

    @Override
    protected void draw() {
        StationDef def = plugin.stationDef(id);
        if (def == null) { bottomBar(); set(22, Ed.icon("BARRIER", "§cСтанция не загрузилась — смотри консоль", null), null); return; }
        for (int i = 0; i < 45; i++) deco(i);

        set(4, Ed.named(plugin.stationItem(def, 1), def.name, Ed.lore("§8id: " + id, "§7Так выглядит предмет станции")), null);

        String name = Ed.str(raw, "name", id);
        set(19, Ed.icon("NAME_TAG", "§fНазвание", Ed.lore(
                "§7Сейчас: §r" + Text.color(name),
                "§7Видно над станцией и в меню рецептов",
                "", "§eЛКМ §7— изменить")), c ->
                Input.ask(this, "Введи название станции", name, text -> { raw.put("name", text); save(); return null; }));

        ItemSpec item = raw.get("item") == null ? null : ItemSpec.parse(raw.get("item"));
        ItemStack itemIcon = item == null ? null : item.build();
        if (itemIcon == null) itemIcon = Ed.icon("CRAFTING_TABLE", null, null);
        set(20, Ed.named(itemIcon, "§fБлок станции", Ed.lore(
                "§7Какой блок ставится из предмета станции",
                "§7(можно блок ItemsAdder)",
                "",
                "§eКликни сюда предметом на курсоре§7,",
                "§7чтобы заменить")), c -> {
            if (c.cursor == null) { Ed.msg(player, "§7Возьми нужный блок на курсор и кликни по этой ячейке"); return; }
            ItemSpec s = ItemSpec.fromStack(c.cursor);
            s.amount = 1;
            if (s.exact == null && !s.isItemsAdder() && s.name == null) s.name = name;
            raw.put("item", s.toYaml());
            save();
        });

        double h = Ed.dbl(raw, "hologram-height", 1.4);
        set(21, Ed.icon("ARMOR_STAND", "§fВысота голограммы", Ed.lore(
                "§7Сейчас: §f" + Ed.num(h) + " §7блока над станцией",
                "", "§eЛКМ §7+0.1   §eПКМ §7−0.1   §eShift §7×5")), c -> {
            raw.put("hologram-height", Ed.step(c, h, 0.1, 0, 5));
            save();
        });

        String particle = Ed.str(raw, "ambient-particle", "");
        set(22, Ed.icon("BLAZE_POWDER", "§fЧастицы над станцией", Ed.lore(
                "§7Сейчас: §f" + (particle.isEmpty() ? "нет" : particle),
                "§7Появляются, пока на станции никто не крафтит",
                "", "§eЛКМ §7— выбрать")), c -> {
            List<ChoiceMenu.Option> opts = new ArrayList<>();
            for (String[] p : Ed.PARTICLES) opts.add(new ChoiceMenu.Option(p[0], p[1], p[2]));
            new ChoiceMenu(plugin, player, this, "§fЧастицы станции", opts, particle, v -> {
                raw.put("ambient-particle", v);
                Ed.commit(plugin, player, Kind.STATION, id, raw);
            }).none("Без частиц").custom("Введи название частицы Bukkit, например §fFLAME").open();
        });

        List<String> recipes = Ed.strings(raw, "recipes");
        List<String> rl = new ArrayList<>();
        rl.add("§7Какие рецепты можно сделать здесь:");
        for (int i = 0; i < Math.min(10, recipes.size()); i++) {
            RecipeDef r = plugin.recipe(recipes.get(i));
            rl.add("§8 • " + (r == null ? "§c" + recipes.get(i) + " (нет такого)" : "§f" + r.name));
        }
        if (recipes.size() > 10) rl.add("§8 ...и ещё " + (recipes.size() - 10));
        if (recipes.isEmpty()) rl.add("§8 (пока ни одного)");
        rl.add("");
        rl.add("§eЛКМ §7— выбрать рецепты");
        set(23, Ed.icon("KNOWLEDGE_BOOK", "§eРецепты станции §7(" + recipes.size() + ")", rl), c -> openRecipeToggle());

        set(24, Ed.icon("CHEST", "§aПолучить предмет станции", Ed.lore(
                "§7Поставь его — и станция готова",
                "§7(или посмотри на любой блок и введи",
                "§f/scraft bind " + id + "§7)")), c -> {
            player.getInventory().addItem(plugin.stationItem(def, 1));
            Ed.msg(player, "§aВыдано: §f" + def.name);
        });

        bottomBar();
        set(53, Ed.icon("TNT", "§cУдалить станцию", Ed.lore("§7Файл станции будет удалён")), c ->
                new ConfirmMenu(plugin, player, this, "Удалить станцию " + id + "?", Ed.lore(
                        "§7Рецепты останутся.",
                        "§7Уже поставленные блоки перестанут работать",
                        "§7(их можно убрать §f/scraft unbind§7)"), () -> {
                    plugin.store().delete(Kind.STATION, id);
                    plugin.loadDefinitions();
                    Ed.msg(player, "§cСтанция удалена: §f" + id);
                    if (parent != null) parent.open(); else player.closeInventory();
                }).open());
    }

    private void openRecipeToggle() {
        List<ToggleMenu.Entry> entries = new ArrayList<>();
        for (RecipeDef r : plugin.recipes().values()) entries.add(new ToggleMenu.Entry(r.id, RecipeEditMenu.iconOf(r), r.name));
        new ToggleMenu(plugin, player, this, "§eРецепты станции " + id, entries,
                rid -> Ed.strings(raw, "recipes").contains(rid),
                (rid, on) -> {
                    List<Object> l = Ed.list(raw, "recipes");
                    l.remove(rid);
                    if (on) l.add(rid);
                    Ed.commit(plugin, player, Kind.STATION, id, raw);
                }).open();
    }
}
