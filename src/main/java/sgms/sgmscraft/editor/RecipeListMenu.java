package sgms.sgmscraft.editor;

import org.bukkit.entity.Player;
import sgms.sgmscraft.SGMSCraft;
import sgms.sgmscraft.data.ConfigStore.Kind;
import sgms.sgmscraft.data.Defs.RecipeDef;
import sgms.sgmscraft.data.Defs.StationDef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RecipeListMenu extends Menu {
    private int page;

    public RecipeListMenu(SGMSCraft plugin, Player player, Menu parent) { super(plugin, player, parent); }

    @Override protected String title() { return "§eРецепты"; }
    @Override protected int rows() { return 6; }

    @Override
    protected void draw() {
        List<RecipeDef> list = new ArrayList<>(plugin.recipes().values());
        int from = page * 45;
        for (int i = 0; i < 45 && from + i < list.size(); i++) {
            RecipeDef r = list.get(from + i);
            List<String> lore = new ArrayList<>();
            lore.add("§8id: " + r.id);
            lore.add("§7Этапов: §f" + r.stages.size());
            List<String> st = new ArrayList<>();
            for (StationDef s : plugin.stationDefs().values()) if (s.recipes.contains(r.id)) st.add(s.name);
            lore.add("§7Станции: " + (st.isEmpty() ? "§cни одной" : "§f" + String.join("§7, §f", st)));
            lore.add("");
            lore.add("§eЛКМ §7— настроить");
            set(i, Ed.named(RecipeEditMenu.iconOf(r), r.name, lore), c -> new RecipeEditMenu(plugin, player, this, r.id).open());
        }
        bottomBar();
        if (page > 0) set(48, Ed.icon("SPECTRAL_ARROW", "§7◀ Страница " + page, null), c -> { page--; redraw(); });
        if (from + 45 < list.size()) set(50, Ed.icon("SPECTRAL_ARROW", "§7Страница " + (page + 2) + " ▶", null), c -> { page++; redraw(); });
        set(49, Ed.icon("LIME_DYE", "§a＋ Новый рецепт", Ed.lore("§7Попросит ввести id в чат")), c ->
                Input.ask(this, "Введи id нового рецепта (латиница, цифры, _), например §fcloth_shirt", null, text -> {
                    String id = text.toLowerCase();
                    if (!Ed.validId(id)) { Ed.msg(player, "§cНедопустимый id: только a-z, 0-9 и _"); return null; }
                    if (plugin.recipe(id) != null) { Ed.msg(player, "§cТакой рецепт уже есть"); return null; }
                    Map<String, Object> raw = new LinkedHashMap<>();
                    raw.put("name", "&f" + id);
                    raw.put("description", new ArrayList<>());
                    List<Object> stages = new ArrayList<>();
                    stages.add(RecipeEditMenu.newStage("insert"));
                    raw.put("stages", stages);
                    Map<String, Object> rewards = new LinkedHashMap<>();
                    rewards.put("items", new ArrayList<>());
                    rewards.put("xp", 0);
                    raw.put("rewards", rewards);
                    Ed.commit(plugin, player, Kind.RECIPE, id, raw);
                    Ed.msg(player, "§aРецепт создан: §f" + id + "§a. Не забудь добавить его станции!");
                    return new RecipeEditMenu(plugin, player, this, id);
                }));
    }
}
