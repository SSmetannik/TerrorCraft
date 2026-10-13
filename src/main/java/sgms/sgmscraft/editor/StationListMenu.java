package sgms.sgmscraft.editor;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import sgms.sgmscraft.SGMSCraft;
import sgms.sgmscraft.data.ConfigStore.Kind;
import sgms.sgmscraft.data.Defs.StationDef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StationListMenu extends Menu {
    private int page;

    public StationListMenu(SGMSCraft plugin, Player player, Menu parent) { super(plugin, player, parent); }

    @Override protected String title() { return "§aСтанции"; }
    @Override protected int rows() { return 6; }

    @Override
    protected void draw() {
        List<StationDef> list = new ArrayList<>(plugin.stationDefs().values());
        int from = page * 45;
        for (int i = 0; i < 45 && from + i < list.size(); i++) {
            StationDef d = list.get(from + i);
            long placed = plugin.stations().all().stream().filter(b -> b.stationId.equals(d.id)).count();
            ItemStack it = plugin.stationItem(d, 1);
            List<String> lore = new ArrayList<>();
            lore.add("§8id: " + d.id);
            lore.add("§7Рецептов: §f" + d.recipes.size());
            lore.add("§7Стоит в мире: §f" + placed);
            lore.add("");
            lore.add("§eЛКМ §7— настроить");
            lore.add("§eПКМ §7— получить предмет станции");
            set(i, Ed.named(it, d.name, lore), c -> {
                if (c.right) {
                    player.getInventory().addItem(plugin.stationItem(d, 1));
                    Ed.msg(player, "§aВыдано: §f" + d.name);
                } else {
                    new StationEditMenu(plugin, player, this, d.id).open();
                }
            });
        }
        bottomBar();
        if (page > 0) set(48, Ed.icon("SPECTRAL_ARROW", "§7◀ Страница " + page, null), c -> { page--; redraw(); });
        if (from + 45 < list.size()) set(50, Ed.icon("SPECTRAL_ARROW", "§7Страница " + (page + 2) + " ▶", null), c -> { page++; redraw(); });
        set(49, Ed.icon("LIME_DYE", "§a＋ Новая станция", Ed.lore("§7Попросит ввести id в чат")), c ->
                Input.ask(this, "Введи id новой станции (латиница, цифры, _), например §fsewing_table", null, text -> {
                    String id = text.toLowerCase();
                    if (!Ed.validId(id)) { Ed.msg(player, "§cНедопустимый id: только a-z, 0-9 и _"); return null; }
                    if (plugin.stationDef(id) != null) { Ed.msg(player, "§cТакая станция уже есть"); return null; }
                    Map<String, Object> raw = new LinkedHashMap<>();
                    raw.put("name", "&f" + id);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("material", "CRAFTING_TABLE");
                    item.put("name", "&f" + id);
                    raw.put("item", item);
                    raw.put("hologram-height", 1.4);
                    raw.put("ambient-particle", "");
                    raw.put("recipes", new ArrayList<>());
                    Ed.commit(plugin, player, Kind.STATION, id, raw);
                    Ed.msg(player, "§aСтанция создана: §f" + id);
                    return new StationEditMenu(plugin, player, this, id);
                }));
    }
}
