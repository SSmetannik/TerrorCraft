package sgms.sgmscraft.editor;

import org.bukkit.entity.Player;
import sgms.sgmscraft.SGMSCraft;

/** /scraft editor */
public class MainMenu extends Menu {
    public MainMenu(SGMSCraft plugin, Player player) { super(plugin, player, null); }

    @Override protected String title() { return "§2☣ SGMSCraft — редактор"; }
    @Override protected int rows() { return 3; }

    @Override
    protected void draw() {
        for (int i = 0; i < 27; i++) deco(i);
        set(11, Ed.icon("SMITHING_TABLE", "§a§lСтанции", Ed.lore(
                "§7Блоки, на которых крафтят:",
                "§7название, блок, какие рецепты есть.",
                "",
                "§7Всего: §f" + plugin.stationDefs().size(),
                "",
                "§e▶ Открыть")), c -> new StationListMenu(plugin, player, this).open());
        set(15, Ed.icon("KNOWLEDGE_BOOK", "§e§lРецепты", Ed.lore(
                "§7Что крафтится: ингредиенты,",
                "§7этапы, мини-игры и награда.",
                "",
                "§7Всего: §f" + plugin.recipes().size(),
                "",
                "§e▶ Открыть")), c -> new RecipeListMenu(plugin, player, this).open());
        set(13, Ed.icon("BOOK", "§fКак это устроено", Ed.lore(
                "§71. Создай §eрецепт§7: добавь этапы",
                "§7   (ингредиенты, ожидание, мини-игры)",
                "§7   и награду.",
                "§72. Открой §aстанцию §7и включи ей рецепт.",
                "§73. Выдай станцию: кнопка в её настройках",
                "§7   или §f/scraft give <id>§7, и поставь.",
                "",
                "§7Всё сохраняется сразу, в файлы",
                "§fstations/<id>.yml §7и §frecipes/<id>.yml")), null);
        set(22, Ed.icon("COMPARATOR", "§7Перечитать файлы", Ed.lore(
                "§7Если правил .yml вручную —",
                "§7нажми, чтобы подхватить изменения")), c -> {
            plugin.loadDefinitions();
            Ed.msg(player, "§aФайлы перечитаны: §f" + plugin.stationDefs().size() + " §aстанций, §f"
                    + plugin.recipes().size() + " §aрецептов");
            redraw();
        });
    }
}
