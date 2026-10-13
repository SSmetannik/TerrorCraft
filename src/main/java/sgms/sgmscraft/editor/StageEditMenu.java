package sgms.sgmscraft.editor;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import sgms.sgmscraft.data.Defs.RecipeDef;
import sgms.sgmscraft.data.Defs.StageDef;
import sgms.sgmscraft.game.GameInfo;
import sgms.sgmscraft.game.GameInfo.Param;
import sgms.sgmscraft.game.GameInfo.ParamType;
import sgms.sgmscraft.util.ItemSpec;
import sgms.sgmscraft.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StageEditMenu extends Menu {
    private final RecipeEditMenu recipe;
    private int index;

    public StageEditMenu(sgms.sgmscraft.SGMSCraft plugin, org.bukkit.entity.Player player, RecipeEditMenu recipe, int index) {
        super(plugin, player, recipe);
        this.recipe = recipe;
        this.index = index;
    }

    @Override protected String title() { return "§6Этап " + (index + 1) + " §7— рецепт " + recipe.id; }
    @Override protected int rows() { return 6; }

    private Map<String, Object> stage() {
        List<Map<String, Object>> l = recipe.stages();
        return index >= 0 && index < l.size() ? l.get(index) : null;
    }

    private void save() { recipe.save(); redraw(); }

    @Override
    protected void draw() {
        Map<String, Object> st = stage();
        if (st == null) { recipe.open(); return; }
        for (int i = 0; i < 45; i++) deco(i);
        String type = RecipeEditMenu.typeOf(st);
        set(4, RecipeEditMenu.stageIcon(st, index + 1), null);

        String typeName = switch (type) {
            case "insert" -> "Положить ингредиенты";
            case "game" -> "Мини-игра";
            default -> "Ожидание";
        };
        set(10, Ed.icon("COMPASS", "§fТип этапа: §e" + typeName, Ed.lore("", "§eЛКМ §7— сменить тип",
                "§8(настройки этапа сбросятся)")), c ->
                new ChoiceMenu(plugin, player, this, "§6Новый тип этапа", RecipeEditMenu.stageTypeOptions(), null, t -> {
                    Map<String, Object> s = stage();
                    if (s == null) return;
                    s.clear();
                    s.putAll(RecipeEditMenu.newStage(t));
                    recipe.save();
                }).open());

        String name = Ed.str(st, "name", typeName);
        set(11, Ed.icon("NAME_TAG", "§fНазвание этапа", Ed.lore("§7Сейчас: §r" + Text.color(name),
                "§7Показывается игроку на экране и в голограмме", "", "§eЛКМ §7— изменить")), c ->
                Input.ask(this, "Введи название этапа", name, text -> { st.put("name", text); recipe.save(); return null; }));

        double time = Ed.dbl(st, "time", type.equals("wait") ? 5 : 30);
        String timeTitle = type.equals("wait") ? "§fДлительность" : "§fЛимит времени";
        String timeHint = switch (type) {
            case "wait" -> "§7Сколько секунд ждать";
            case "insert" -> "§7Сколько секунд даётся на ингредиенты (0 — без лимита)";
            default -> "§7Сколько секунд на мини-игру — не успел = провал";
        };
        set(12, Ed.icon("CLOCK", timeTitle + ": §e" + (time <= 0 ? "без лимита" : Ed.num(time) + " с"), Ed.lore(timeHint,
                "", "§eЛКМ §7+1   §eПКМ §7−1   §eShift §7×5")), c -> {
            double min = type.equals("insert") ? 0 : 1;
            st.put("time", (int) Ed.step(c, time, 1, min, 3600));
            save();
        });

        switch (type) {
            case "insert" -> drawInsert(st);
            case "wait" -> drawWait(st);
            default -> drawGame(st);
        }

        bottomBar();
        set(47, Ed.icon("SPECTRAL_ARROW", "§7◀ Сдвинуть раньше", null), c -> {
            if (index <= 0) return;
            recipe.move(index, -1);
            index--;
            redraw();
        });
        set(48, Ed.icon("SPECTRAL_ARROW", "§7Сдвинуть позже ▶", null), c -> {
            if (index >= recipe.stages().size() - 1) return;
            recipe.move(index, 1);
            index++;
            redraw();
        });
        set(53, Ed.icon("TNT", "§cУдалить этап", null), c ->
                new ConfirmMenu(plugin, player, this, "Удалить этап " + (index + 1) + "?", null, () -> {
                    Ed.list(recipe.raw, "stages").remove(index);
                    recipe.save();
                    recipe.open();
                }).open());
    }

    // ------------------------------------------------------------------ insert
    private void drawInsert(Map<String, Object> st) {
        List<ItemSpec> items = ItemSpec.parseList(RecipeEditMenu.rawList(st, "items"));
        List<String> l = new ArrayList<>();
        l.add("§7Что игрок должен положить в станцию:");
        for (ItemSpec s : items) l.add("§8 • §f" + Text.strip(s.displayName()) + " §7x" + s.amount);
        if (items.isEmpty()) l.add("§c (пусто — добавь предметы)");
        l.add("");
        l.add("§7Игрок кладёт их §fПКМ по станции§7 с предметом в руке,");
        l.add("§7или §fShift+ПКМ§7 — сразу всё из инвентаря.");
        l.add("");
        l.add("§eЛКМ §7— изменить");
        ItemStack ic = items.isEmpty() ? null : items.get(0).build();
        set(14, Ed.named(ic == null ? Ed.icon("CHEST", null, null) : ic, "§bИнгредиенты", l), c ->
                new ItemsMenu(plugin, player, this, "§bИнгредиенты этапа " + (index + 1), items, 45, true, list -> {
                    Map<String, Object> s = stage();
                    if (s == null) return;
                    List<Object> out = new ArrayList<>();
                    for (ItemSpec i : list) out.add(i.toYaml());
                    s.put("items", out);
                    recipe.save();
                }).open());
    }

    // ------------------------------------------------------------------ wait
    private void drawWait(Map<String, Object> st) {
        String particle = Ed.str(st, "particle", "");
        set(14, Ed.icon("BLAZE_POWDER", "§fЧастицы", Ed.lore("§7Сейчас: §f" + (particle.isEmpty() ? "дым (по умолчанию)" : particle),
                "", "§eЛКМ §7— выбрать")), c -> {
            List<ChoiceMenu.Option> opts = new ArrayList<>();
            for (String[] p : Ed.PARTICLES) opts.add(new ChoiceMenu.Option(p[0], p[1], p[2]));
            new ChoiceMenu(plugin, player, this, "§fЧастицы ожидания", opts, particle, v -> {
                Map<String, Object> s = stage();
                if (s == null) return;
                if (v.isEmpty()) s.remove("particle"); else s.put("particle", v.toUpperCase());
                recipe.save();
            }).none("По умолчанию (дым)").custom("Введи название частицы Bukkit, например §fFLAME").open();
        });
        String sound = Ed.str(st, "sound", "");
        set(15, Ed.icon("NOTE_BLOCK", "§fЗвук", Ed.lore("§7Сейчас: §f" + (sound.isEmpty() ? "нет" : sound),
                "§7Играет раз в 1.5 секунды", "", "§eЛКМ §7— выбрать")), c -> {
            List<ChoiceMenu.Option> opts = new ArrayList<>();
            for (String[] p : Ed.SOUNDS) opts.add(new ChoiceMenu.Option(p[0], p[1], p[2]));
            new ChoiceMenu(plugin, player, this, "§fЗвук ожидания", opts, sound, v -> {
                Map<String, Object> s = stage();
                if (s == null) return;
                if (v.isEmpty()) s.remove("sound"); else s.put("sound", v);
                recipe.save();
            }).none("Без звука").custom("Введи id звука, например §fblock.brewing_stand.brew §7или §fsgms:toxic_zombie.idle").open();
        });
    }

    // ------------------------------------------------------------------ game
    private void drawGame(Map<String, Object> st) {
        GameInfo.Info g = GameInfo.get(Ed.str(st, "game", "timing"));
        List<String> gl = new ArrayList<>();
        for (String d : g.description) gl.add("§7" + d);
        gl.add("");
        gl.add("§eЛКМ §7— выбрать другую игру");
        set(14, Ed.icon(g.icon, "§6Мини-игра: §e" + g.name, gl), c -> {
            List<ChoiceMenu.Option> opts = new ArrayList<>();
            for (GameInfo.Info i : GameInfo.ALL.values()) {
                List<String> l = new ArrayList<>();
                for (String d : i.description) l.add("§7" + d);
                opts.add(new ChoiceMenu.Option(i.id, Ed.icon(i.icon, null, null), i.name, l));
            }
            new ChoiceMenu(plugin, player, this, "§6Мини-игра", opts, g.id, v -> {
                Map<String, Object> s = stage();
                if (s == null) return;
                for (Param p : g.params) s.remove(p.key);
                s.put("game", v);
                recipe.save();
            }).open();
        });

        set(16, Ed.icon("SPYGLASS", "§b▶ Попробовать", Ed.lore(
                "§7Запускает только эту мини-игру на станции,",
                "§7на которую смотришь (или ближайшей рядом)")), c -> {
            Map<String, Object> s = stage();
            if (s == null) return;
            YamlConfiguration y = new YamlConfiguration();
            for (Map.Entry<String, Object> e : s.entrySet()) {
                if (e.getValue() instanceof Map<?, ?> m) y.createSection(e.getKey(), m);
                else y.set(e.getKey(), e.getValue());
            }
            RecipeDef r = new RecipeDef();
            r.id = "_test";
            r.name = Text.color("&eТест: " + g.name);
            r.stages.add(StageDef.load(y));
            RecipeEditMenu.startTest(plugin, player, r);
        });

        set(22, Ed.icon("OAK_SIGN", "§6Настройки игры ↓", Ed.lore(
                "§eЛКМ §7+   §eПКМ §7−   §eShift §7×5",
                "§7Серые — значения по умолчанию")), null);
        int slot = 28;
        for (Param p : g.params) {
            if (slot == 35) slot = 37;
            if (slot > 43) break;
            set(slot++, paramIcon(st, p), c -> clickParam(p, c));
        }
        set(44, Ed.icon("WATER_BUCKET", "§7↺ Сбросить настройки", Ed.lore("§7Вернуть все значения по умолчанию")), c -> {
            Map<String, Object> s = stage();
            if (s == null) return;
            for (Param p : g.params) s.remove(p.key);
            save();
        });
    }

    private ItemStack paramIcon(Map<String, Object> st, Param p) {
        boolean set = st.containsKey(p.key);
        String mat = switch (p.type) {
            case BOOL -> set && Ed.bool(st, p.key, p.def > 0) || !set && p.def > 0 ? "LIME_DYE" : "GRAY_DYE";
            case RANGE_LO, RANGE_HI -> set ? "TRIPWIRE_HOOK" : "STRING";
            case MATERIALS -> "CHEST";
            case CHOICE -> set ? "PAINTING" : "ITEM_FRAME";
            default -> set ? "REPEATER" : "LEVER";
        };
        List<String> l = new ArrayList<>();
        l.add("§7" + p.hint);
        l.add("");
        l.add("§7Сейчас: §f" + valueText(st, p) + (set ? "" : " §8(по умолчанию)"));
        l.add("");
        switch (p.type) {
            case BOOL -> l.add("§eЛКМ §7— переключить");
            case MATERIALS -> l.add(p.max <= 1 ? "§eЛКМ §7— выбрать предмет" : "§eЛКМ §7— выбрать предметы");
            case CHOICE -> {
                String cur = Ed.str(st, p.key, p.choices[0][0]);
                for (String[] ch : p.choices) l.add((ch[0].equalsIgnoreCase(cur) ? "§a ▶ " : "§8   ") + ch[1]);
                l.add("");
                l.add("§eЛКМ §7— следующий   §eПКМ §7— предыдущий");
            }
            default -> l.add("§eЛКМ §7+" + Ed.num(p.step) + "   §eПКМ §7−" + Ed.num(p.step) + "   §eShift §7×5");
        }
        return Ed.icon(mat, (set ? "§e" : "§7") + p.label, l);
    }

    static String valueText(Map<String, Object> st, Param p) {
        switch (p.type) {
            case BOOL:
                return Ed.bool(st, p.key, p.def > 0) ? "да" : "нет";
            case RANGE_LO:
            case RANGE_HI: {
                double[] r = range(st, p);
                return Ed.num(p.type == ParamType.RANGE_LO ? r[0] : r[1]);
            }
            case MATERIALS: {
                List<String> l = Ed.strings(st, p.key);
                return l.isEmpty() ? "стандартные" : String.join(", ", l).toLowerCase();
            }
            case CHOICE: {
                String cur = Ed.str(st, p.key, p.choices[0][0]);
                for (String[] ch : p.choices) if (ch[0].equalsIgnoreCase(cur)) return ch[1];
                return cur;
            }
            default:
                return Ed.num(Ed.dbl(st, p.key, p.def));
        }
    }

    private static double[] range(Map<String, Object> st, Param p) {
        double lo = 55, hi = 75;
        for (Param q : GameInfo.get(Ed.str(st, "game", "heat")).params) {
            if (!q.key.equals(p.key)) continue;
            if (q.type == ParamType.RANGE_LO) lo = q.def;
            if (q.type == ParamType.RANGE_HI) hi = q.def;
        }
        Object o = st.get(p.key);
        if (o instanceof List<?> l && l.size() == 2 && l.get(0) instanceof Number a && l.get(1) instanceof Number b) {
            lo = a.doubleValue();
            hi = b.doubleValue();
        }
        return new double[]{lo, hi};
    }

    private void clickParam(Param p, Click c) {
        Map<String, Object> st = stage();
        if (st == null) return;
        switch (p.type) {
            case BOOL -> st.put(p.key, !Ed.bool(st, p.key, p.def > 0));
            case CHOICE -> {
                String cur = Ed.str(st, p.key, p.choices[0][0]);
                int idx = 0;
                for (int k = 0; k < p.choices.length; k++) if (p.choices[k][0].equalsIgnoreCase(cur)) idx = k;
                idx = (idx + (c.right ? -1 : 1) + p.choices.length) % p.choices.length;
                st.put(p.key, p.choices[idx][0]);
            }
            case INT -> st.put(p.key, (int) Ed.step(c, Ed.dbl(st, p.key, p.def), p.step, p.min, p.max));
            case DOUBLE -> st.put(p.key, Ed.step(c, Ed.dbl(st, p.key, p.def), p.step, p.min, p.max));
            case RANGE_LO, RANGE_HI -> {
                double[] r = range(st, p);
                if (p.type == ParamType.RANGE_LO) r[0] = Ed.step(c, r[0], p.step, p.min, r[1] - 1);
                else r[1] = Ed.step(c, r[1], p.step, r[0] + 1, p.max);
                List<Object> l = new ArrayList<>();
                l.add((int) r[0]);
                l.add((int) r[1]);
                st.put(p.key, l);
            }
            case MATERIALS -> {
                List<ItemSpec> cur = new ArrayList<>();
                for (String s : Ed.strings(st, p.key)) cur.add(ItemSpec.parse(s));
                new ItemsMenu(plugin, player, this, "§6" + p.label + (p.max <= 1 ? "" : " (до " + (int) p.max + ")"), cur, (int) p.max, false, list -> {
                    Map<String, Object> s = stage();
                    if (s == null) return;
                    List<Object> out = new ArrayList<>();
                    for (ItemSpec i : list) {
                        ItemStack b = i.build();
                        out.add(b != null ? b.getType().name() : i.id);
                    }
                    if (out.isEmpty()) s.remove(p.key); else s.put(p.key, out);
                    recipe.save();
                }).open();
                return;
            }
        }
        save();
    }
}
