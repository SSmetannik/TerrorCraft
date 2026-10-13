package sgms.sgmscraft.editor;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import sgms.sgmscraft.SGMSCraft;
import sgms.sgmscraft.data.ConfigStore;
import sgms.sgmscraft.data.ConfigStore.Kind;
import sgms.sgmscraft.data.Defs.RecipeDef;
import sgms.sgmscraft.data.Defs.StationDef;
import sgms.sgmscraft.data.StationManager.Bound;
import sgms.sgmscraft.game.GameInfo;
import sgms.sgmscraft.session.Session;
import sgms.sgmscraft.util.ItemSpec;
import sgms.sgmscraft.util.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RecipeEditMenu extends Menu {
    final String id;
    Map<String, Object> raw;

    public RecipeEditMenu(SGMSCraft plugin, Player player, Menu parent, String id) {
        super(plugin, player, parent);
        this.id = id;
    }

    @Override protected String title() { return "§eРецепт: §f" + id; }
    @Override protected int rows() { return 6; }

    @Override
    public void open() {
        if (raw == null) raw = plugin.store().raw(Kind.RECIPE, id);
        super.open();
    }

    void save() { Ed.commit(plugin, player, Kind.RECIPE, id, raw); }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> stages() {
        List<Object> l = Ed.list(raw, "stages");
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < l.size(); i++) {
            Object o = l.get(i);
            Map<String, Object> m;
            if (o instanceof Map<?, ?> mm) {
                m = (Map<String, Object>) mm;
            } else {
                m = new LinkedHashMap<>();
                m.put("type", "wait");
                l.set(i, m);
            }
            out.add(m);
        }
        return out;
    }

    @Override
    protected void draw() {
        RecipeDef def = plugin.recipe(id);
        for (int i = 0; i < 27; i++) deco(i);

        // ------------------------------------------------------------------ header
        ItemStack preview = def == null ? Ed.icon("BARRIER", "§c" + id, null) : iconOf(def);
        List<String> pl = new ArrayList<>();
        if (def == null) pl.add("§cРецепт с ошибкой — смотри консоль");
        else pl.addAll(def.description);
        set(4, Ed.named(preview, def == null ? null : def.name, pl), null);

        String name = Ed.str(raw, "name", id);
        set(10, Ed.icon("NAME_TAG", "§fНазвание", Ed.lore("§7Сейчас: §r" + Text.color(name), "", "§eЛКМ §7— изменить")), c ->
                Input.ask(this, "Введи название рецепта", name, text -> { raw.put("name", text); save(); return null; }));

        ItemSpec icon = raw.get("icon") == null ? null : ItemSpec.parse(raw.get("icon"));
        ItemStack iconIt = icon == null ? null : icon.build();
        set(11, Ed.named(iconIt == null ? Ed.icon("ITEM_FRAME", null, null) : iconIt, "§fИконка в меню", Ed.lore(
                "§7Сейчас: §f" + (icon == null ? "как у награды" : icon.id),
                "",
                "§eКлик предметом на курсоре §7— поставить",
                "§eПКМ §7— как у награды")), c -> {
            if (c.cursor != null) {
                ItemSpec s = ItemSpec.fromStack(c.cursor);
                s.amount = 1;
                raw.put("icon", s.toYaml());
            } else if (c.right) {
                raw.remove("icon");
            } else {
                Ed.msg(player, "§7Возьми предмет на курсор и кликни по этой ячейке");
                return;
            }
            save();
            redraw();
        });

        List<String> desc = Ed.strings(raw, "description");
        List<String> dl = new ArrayList<>();
        dl.add("§7Строки под названием в меню:");
        for (String d : desc) dl.add("§8 │ §r" + Text.color(d));
        if (desc.isEmpty()) dl.add("§8 (пусто)");
        dl.add("");
        dl.add("§eЛКМ §7— написать заново (строки через §f|§7)");
        dl.add("§eПКМ §7— очистить");
        set(12, Ed.icon("WRITABLE_BOOK", "§fОписание", dl), c -> {
            if (c.right) { raw.put("description", new ArrayList<>()); save(); redraw(); return; }
            Input.ask(this, "Введи описание. Несколько строк раздели символом §f|", String.join(" | ", desc), text -> {
                List<Object> lines = new ArrayList<>();
                for (String s : text.split("\\|")) if (!s.trim().isEmpty()) lines.add("&7" + s.trim());
                raw.put("description", lines);
                save();
                return null;
            });
        });

        String perm = Ed.str(raw, "permission", "");
        set(13, Ed.icon("TRIPWIRE_HOOK", "§fПраво доступа", Ed.lore(
                "§7Сейчас: §f" + (perm.isEmpty() ? "нет — могут все" : perm),
                "§7Без права рецепт виден, но начать нельзя",
                "",
                "§eЛКМ §7— задать   §eПКМ §7— убрать")), c -> {
            if (c.right) { raw.remove("permission"); save(); redraw(); return; }
            Input.ask(this, "Введи право, например §fsgms.craft.vip", perm, text -> {
                raw.put("permission", text);
                save();
                return null;
            });
        });

        boolean ret = Ed.bool(raw, "return-items-on-fail", false);
        set(14, Ed.icon(ret ? "LIME_DYE" : "GRAY_DYE", "§fВозврат при провале: " + (ret ? "§aда" : "§cнет"), Ed.lore(
                ret ? "§7Если игрок провалит крафт — ингредиенты вернутся"
                        : "§7Если игрок провалит крафт — ингредиенты сгорят",
                "", "§eЛКМ §7— переключить")), c -> {
            raw.put("return-items-on-fail", !ret);
            save();
            redraw();
        });

        List<String> st = new ArrayList<>();
        for (StationDef s : plugin.stationDefs().values()) if (s.recipes.contains(id)) st.add(s.name);
        List<String> sl = new ArrayList<>();
        sl.add("§7Где можно скрафтить:");
        for (String s : st) sl.add("§8 • §f" + s);
        if (st.isEmpty()) sl.add("§c ни одной — рецепт никому не виден!");
        sl.add("");
        sl.add("§eЛКМ §7— выбрать станции");
        set(15, Ed.icon("CRAFTING_TABLE", "§aСтанции §7(" + st.size() + ")", sl), c -> openStations());

        set(16, Ed.icon("MAP", "§fСделать копию", Ed.lore(
                "§7Создаёт новый рецепт с теми же этапами —",
                "§7удобно для похожих крафтов", "", "§eЛКМ §7— ввести id копии")), c ->
                Input.ask(this, "Введи id копии рецепта", null, text -> {
                    String nid = text.toLowerCase();
                    if (!Ed.validId(nid)) { Ed.msg(player, "§cНедопустимый id: только a-z, 0-9 и _"); return null; }
                    if (plugin.recipe(nid) != null) { Ed.msg(player, "§cТакой рецепт уже есть"); return null; }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> copy = (Map<String, Object>) ConfigStore.copy(raw);
                    copy.put("name", Ed.str(raw, "name", id) + " &7(копия)");
                    Ed.commit(plugin, player, Kind.RECIPE, nid, copy);
                    Ed.msg(player, "§aКопия создана: §f" + nid);
                    return new RecipeEditMenu(plugin, player, parent, nid);
                }));

        // ------------------------------------------------------------------ stages
        set(22, Ed.icon("OAK_SIGN", "§6Этапы крафта ↓", Ed.lore(
                "§7Идут по порядку слева направо.",
                "§eЛКМ §7по этапу — настроить",
                "§eShift+ЛКМ / Shift+ПКМ §7— сдвинуть влево / вправо")), null);
        List<Map<String, Object>> stages = stages();
        for (int i = 0; i < stages.size() && i < 18; i++) {
            final int idx = i;
            set(27 + i, stageIcon(stages.get(i), i + 1), c -> {
                if (c.shift) {
                    move(idx, c.right ? 1 : -1);
                    return;
                }
                new StageEditMenu(plugin, player, this, idx).open();
            });
        }
        if (stages.size() < 18) {
            set(27 + stages.size(), Ed.icon("LIME_STAINED_GLASS_PANE", "§a＋ Добавить этап", Ed.lore(
                    "§7Ингредиенты, ожидание или мини-игра")), c -> addStage());
        }

        // ------------------------------------------------------------------ rewards & actions
        bottomBar();
        Map<String, Object> rewards = Ed.map(raw, "rewards");
        List<ItemSpec> rewardItems = ItemSpec.parseList(Ed.list(rewards, "items"));
        List<String> rl = new ArrayList<>();
        rl.add("§7Что получит игрок:");
        for (ItemSpec s : rewardItems) rl.add("§8 • §f" + Text.strip(s.displayName()) + " §7x" + s.amount);
        if (rewardItems.isEmpty()) rl.add("§8 (ничего)");
        rl.add("");
        rl.add("§eЛКМ §7— изменить");
        ItemStack rIcon = rewardItems.isEmpty() ? null : rewardItems.get(0).build();
        set(47, Ed.named(rIcon == null ? Ed.icon("CHEST", null, null) : rIcon, "§aНаграда: предметы", rl), c ->
                new ItemsMenu(plugin, player, this, "§aНаграда: " + id, rewardItems, 45, true, list -> {
                    List<Object> out = new ArrayList<>();
                    for (ItemSpec s : list) out.add(s.toYaml());
                    Ed.map(raw, "rewards").put("items", out);
                    save();
                }).open());

        double xp = Ed.dbl(rewards, "xp", 0);
        set(48, Ed.icon("EXPERIENCE_BOTTLE", "§aНаграда: опыт", Ed.lore(
                "§7Сейчас: §f" + (int) xp + " §7очков опыта",
                "", "§eЛКМ §7+1   §eПКМ §7−1   §eShift §7×5")), c -> {
            Ed.map(raw, "rewards").put("xp", (int) Ed.step(c, xp, 1, 0, 100000));
            save();
            redraw();
        });

        List<String> cmds = Ed.strings(rewards, "commands");
        List<String> cl = new ArrayList<>();
        cl.add("§7Выполняются от консоли, §f%player% §7= ник");
        for (String s : cmds) cl.add("§8 / §f" + s);
        if (cmds.isEmpty()) cl.add("§8 (нет)");
        cl.add("");
        cl.add("§eЛКМ §7— добавить команду");
        cl.add("§eПКМ §7— убрать последнюю");
        set(49, Ed.icon("COMMAND_BLOCK", "§aНаграда: команды", cl), c -> {
            if (c.right) {
                if (cmds.isEmpty()) return;
                cmds.remove(cmds.size() - 1);
                Ed.map(raw, "rewards").put("commands", new ArrayList<Object>(cmds));
                save();
                redraw();
                return;
            }
            Input.ask(this, "Введи команду без «/», например §feco give %player% 50", null, text -> {
                cmds.add(text.startsWith("/") ? text.substring(1) : text);
                Ed.map(raw, "rewards").put("commands", new ArrayList<Object>(cmds));
                save();
                return null;
            });
        });

        set(51, Ed.icon("SPYGLASS", "§b▶ Проверить рецепт", Ed.lore(
                "§7Запускает рецепт на станции, на которую",
                "§7смотришь (или ближайшей рядом).",
                "§7Тестовый режим: ингредиенты вернутся,",
                "§7награда не выдаётся.")), c -> test());

        set(53, Ed.icon("TNT", "§cУдалить рецепт", null), c ->
                new ConfirmMenu(plugin, player, this, "Удалить рецепт " + id + "?", Ed.lore(
                        "§7Он пропадёт из всех станций"), () -> {
                    for (StationDef s : new ArrayList<>(plugin.stationDefs().values())) {
                        if (!s.recipes.contains(id)) continue;
                        Map<String, Object> sr = plugin.store().raw(Kind.STATION, s.id);
                        Ed.list(sr, "recipes").remove(id);
                        plugin.store().save(Kind.STATION, s.id, sr);
                    }
                    plugin.store().delete(Kind.RECIPE, id);
                    plugin.loadDefinitions();
                    Ed.msg(player, "§cРецепт удалён: §f" + id);
                    if (parent != null) parent.open(); else player.closeInventory();
                }).open());
    }

    // ------------------------------------------------------------------ actions
    void move(int idx, int dir) {
        List<Object> l = Ed.list(raw, "stages");
        int to = idx + dir;
        if (to < 0 || to >= l.size()) return;
        Object a = l.get(idx);
        l.set(idx, l.get(to));
        l.set(to, a);
        save();
        redraw();
    }

    private void addStage() {
        new ChoiceMenu(plugin, player, this, "§6Какой этап добавить?", stageTypeOptions(), null, type -> {
            Ed.list(raw, "stages").add(newStage(type));
            save();
        }).open();
    }

    private void openStations() {
        List<ToggleMenu.Entry> entries = new ArrayList<>();
        for (StationDef s : plugin.stationDefs().values()) entries.add(new ToggleMenu.Entry(s.id, plugin.stationItem(s, 1), s.name));
        new ToggleMenu(plugin, player, this, "§aСтанции рецепта " + id, entries,
                sid -> {
                    StationDef s = plugin.stationDef(sid);
                    return s != null && s.recipes.contains(id);
                },
                (sid, on) -> {
                    Map<String, Object> sr = plugin.store().raw(Kind.STATION, sid);
                    List<Object> l = Ed.list(sr, "recipes");
                    l.remove(id);
                    if (on) l.add(id);
                    Ed.commit(plugin, player, Kind.STATION, sid, sr);
                }).open();
    }

    private void test() {
        RecipeDef def = plugin.recipe(id);
        if (def == null || def.stages.isEmpty()) { Ed.msg(player, "§cУ рецепта нет этапов или он с ошибкой"); return; }
        startTest(plugin, player, def);
    }

    static void startTest(SGMSCraft plugin, Player player, RecipeDef def) {
        Bound b = Ed.nearestStation(plugin, player);
        if (b == null) { Ed.msg(player, "§cПодойди к любой станции (до 8 блоков) и нажми ещё раз"); return; }
        if (plugin.stations().session(b) != null) { Ed.msg(player, "§cЭта станция сейчас занята"); return; }
        if (plugin.stations().sessionOf(player.getUniqueId()) != null) { Ed.msg(player, "§cТы уже что-то крафтишь"); return; }
        player.closeInventory();
        Session s = new Session(plugin, b, def, player, true);
        plugin.stations().startSession(s);
        Ed.msg(player, "§bТест: §f" + def.name + " §7(ингредиенты вернутся, награды не будет)");
        s.start();
    }

    // ------------------------------------------------------------------ shared helpers
    static List<ChoiceMenu.Option> stageTypeOptions() {
        List<ChoiceMenu.Option> o = new ArrayList<>();
        o.add(new ChoiceMenu.Option("insert", Ed.icon("CHEST", null, null), "Положить ингредиенты",
                Ed.lore("§7Игрок кладёт предметы в станцию (ПКМ)")));
        o.add(new ChoiceMenu.Option("wait", Ed.icon("CLOCK", null, null), "Ожидание",
                Ed.lore("§7Просто ждать N секунд с частицами и звуком")));
        for (GameInfo.Info g : GameInfo.ALL.values()) {
            List<String> l = new ArrayList<>();
            for (String d : g.description) l.add("§7" + d);
            o.add(new ChoiceMenu.Option("game:" + g.id, Ed.icon(g.icon, null, null), "Мини-игра: " + g.name, l));
        }
        return o;
    }

    static Map<String, Object> newStage(String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (type.startsWith("game:")) {
            GameInfo.Info g = GameInfo.get(type.substring(5));
            m.put("type", "game");
            m.put("game", g.id);
            m.put("name", g.name);
            m.put("time", 30);
        } else if (type.equals("wait")) {
            m.put("type", "wait");
            m.put("name", "Ожидание");
            m.put("time", 5);
            m.put("particle", "SMOKE");
        } else {
            m.put("type", "insert");
            m.put("name", "Ингредиенты");
            m.put("time", 60);
            m.put("items", new ArrayList<>());
        }
        return m;
    }

    static String typeOf(Map<String, Object> stage) {
        String t = Ed.str(stage, "type", "wait").toLowerCase();
        if (t.startsWith("insert") || t.equals("items")) return "insert";
        if (t.contains("game")) return "game";
        return "wait";
    }

    static ItemStack stageIcon(Map<String, Object> stage, int n) {
        String type = typeOf(stage);
        String mat;
        String kind;
        List<String> lore = new ArrayList<>();
        double time = Ed.dbl(stage, "time", type.equals("wait") ? 5 : 30);
        switch (type) {
            case "insert" -> {
                mat = "CHEST";
                kind = "§bИнгредиенты";
                List<ItemSpec> items = ItemSpec.parseList(rawList(stage, "items"));
                for (ItemSpec s : items) lore.add("§8 • §f" + Text.strip(s.displayName()) + " §7x" + s.amount);
                if (items.isEmpty()) lore.add("§c (ингредиенты не заданы)");
                lore.add("§7Лимит: §f" + (time <= 0 ? "нет" : Ed.num(time) + " с"));
            }
            case "game" -> {
                GameInfo.Info g = GameInfo.get(Ed.str(stage, "game", "timing"));
                mat = g.icon;
                kind = "§6Мини-игра: " + g.name;
                for (GameInfo.Param p : g.params) {
                    if (p.type == GameInfo.ParamType.RANGE_HI || !stage.containsKey(p.key)) continue;
                    lore.add("§8 • §7" + p.label + ": §f" + StageEditMenu.valueText(stage, p));
                }
                lore.add("§7Лимит: §f" + Ed.num(time) + " с");
            }
            default -> {
                mat = "CLOCK";
                kind = "§aОжидание";
                lore.add("§7Длится: §f" + Ed.num(time) + " с");
            }
        }
        lore.add(0, kind);
        lore.add("");
        lore.add("§eЛКМ §7— настроить");
        lore.add("§eShift+ЛКМ/ПКМ §7— сдвинуть");
        ItemStack it = Ed.icon(mat, "§f" + n + ". " + Ed.str(stage, "name", kind), lore);
        it.setAmount(Math.max(1, Math.min(64, n)));
        return it;
    }

    static List<?> rawList(Map<String, Object> m, String key) {
        Object o = m.get(key);
        return o instanceof List<?> l ? l : null;
    }

    /** Icon of a recipe for lists: its icon, else the first reward, else paper. */
    public static ItemStack iconOf(RecipeDef r) {
        ItemStack it = null;
        if (r.icon != null) it = r.icon.build();
        if (it == null && !r.rewardItems.isEmpty()) it = r.rewardItems.get(0).build();
        if (it == null) it = new ItemStack(Material.PAPER, 1);
        it.setAmount(1);
        return it;
    }
}
