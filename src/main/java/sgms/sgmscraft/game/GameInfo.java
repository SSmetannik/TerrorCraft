package sgms.sgmscraft.game;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Human-readable description of every mini-game and its settings — used by the in-game editor. */
public final class GameInfo {
    private GameInfo() {}

    public enum ParamType { INT, DOUBLE, BOOL, RANGE_LO, RANGE_HI, MATERIALS, CHOICE }

    public static final class Param {
        public final String key, label, hint;
        public final ParamType type;
        public final double def, step, min, max;
        /** CHOICE: {value, label} pairs; the first one is the default. */
        public String[][] choices;

        Param(String key, String label, String hint, ParamType type, double def, double step, double min, double max) {
            this.key = key; this.label = label; this.hint = hint; this.type = type;
            this.def = def; this.step = step; this.min = min; this.max = max;
        }
    }

    public static final class Info {
        public final String id, name, icon;
        public final List<String> description;
        public final List<Param> params;

        Info(String id, String name, String icon, List<String> description, List<Param> params) {
            this.id = id; this.name = name; this.icon = icon; this.description = description; this.params = params;
        }
    }

    private static Param i(String k, String label, String hint, double def, double step, double min, double max) {
        return new Param(k, label, hint, ParamType.INT, def, step, min, max);
    }

    private static Param d(String k, String label, String hint, double def, double step, double min, double max) {
        return new Param(k, label, hint, ParamType.DOUBLE, def, step, min, max);
    }

    private static Param b(String k, String label, String hint, boolean def) {
        return new Param(k, label, hint, ParamType.BOOL, def ? 1 : 0, 1, 0, 1);
    }

    private static Param choice(String k, String label, String hint, String[][] choices) {
        Param p = new Param(k, label, hint, ParamType.CHOICE, 0, 1, 0, choices.length - 1);
        p.choices = choices;
        return p;
    }

    public static final Map<String, Info> ALL = new LinkedHashMap<>();

    static {
        add(new Info("timing", "Меткий удар", "TARGET",
                List.of("По шкале бегает маркер — жми ПКМ по станции,", "когда он в зелёной зоне."),
                List.of(
                        i("hits", "Нужно попаданий", "сколько раз попасть в зону", 5, 1, 1, 30),
                        i("lives", "Жизни", "сколько промахов можно", 3, 1, 1, 10),
                        i("zone", "Ширина зоны", "ширина зелёной зоны в клетках (из 27)", 3, 1, 1, 12),
                        d("speed", "Скорость маркера", "клеток за тик", 0.35, 0.05, 0.05, 2),
                        d("speedup", "Ускорение", "во сколько раз быстрее после каждого попадания", 1.12, 0.02, 1, 2))));
        add(new Info("forge", "Ковка", "ANVIL",
                List.of("Метроном отбивает такт — жми ПКМ по станции", "точно в долю. Серия попаданий даёт комбо."),
                List.of(
                        i("hits", "Нужно ударов", "сколько точных ударов", 8, 1, 1, 40),
                        i("interval", "Интервал", "тиков между долями (меньше = быстрее, 20 тиков = 1 с)", 16, 1, 8, 40),
                        i("window", "Окно попадания", "сколько тиков до и после доли засчитывается", 3, 1, 1, 8),
                        i("lives", "Жизни", "сколько промахов можно", 3, 1, 1, 10),
                        b("accelerate", "Ускорение", "темп растёт по ходу игры", true))));
        add(new Info("stir", "Помешивание", "BOWL",
                List.of("Крути взглядом по кругу в одну сторону,", "держа темп в зелёной зоне."),
                List.of(
                        d("turns", "Оборотов", "сколько полных оборотов нужно", 4, 0.5, 0.5, 20),
                        d("min-speed", "Мин. темп", "градусов в секунду — медленнее не засчитывается", 150, 10, 30, 1000),
                        d("max-speed", "Макс. темп", "градусов в секунду — быстрее расплёскивается", 520, 10, 60, 2000),
                        i("lives", "Жизни", "сколько раз можно расплескать", 3, 1, 1, 10))));
        add(new Info("heat", "Температура", "BLAZE_POWDER",
                List.of("Огонь остывает — ПКМ по станции подкидывает", "топливо. Держи жар в зелёной зоне."),
                List.of(
                        new Param("zone", "Зона: от", "нижняя граница зелёной зоны (°)", ParamType.RANGE_LO, 55, 1, 0, 100),
                        new Param("zone", "Зона: до", "верхняя граница зелёной зоны (°)", ParamType.RANGE_HI, 75, 1, 0, 100),
                        d("hold", "Удержать", "сколько секунд продержаться в зоне", 8, 1, 1, 60),
                        d("fuel", "Жар за клик", "на сколько градусов греет одно ПКМ", 9, 1, 1, 40),
                        d("cooling", "Остывание", "скорость остывания", 0.22, 0.02, 0.02, 2),
                        b("events", "События", "случайные сквозняки и вспышки", true))));
        add(new Info("memory", "Рецепт по памяти", "BOOK",
                List.of("Ингредиенты подсвечиваются по очереди —", "повтори порядок кликами в окне."),
                List.of(
                        i("rounds", "Раундов", "каждый раунд цепочка длиннее на 1", 4, 1, 1, 15),
                        i("start-length", "Длина цепочки", "сколько вспышек в первом раунде", 3, 1, 1, 10),
                        i("lives", "Жизни", "сколько ошибок можно", 3, 1, 1, 10),
                        i("speed", "Длит. вспышки", "тиков на одну вспышку (меньше = сложнее)", 8, 1, 4, 30),
                        new Param("items", "Кнопки", "предметы-кнопки (до 7 штук)", ParamType.MATERIALS, 0, 0, 0, 7))));
        add(new Info("bubbles", "Очистка токсина", "SLIME_BALL",
                List.of("В окне всплывают токсичные пузыри — лопай их,", "но не трогай чистые капли."),
                List.of(
                        i("target", "Нужно лопнуть", "сколько пузырей", 15, 1, 1, 80),
                        i("misses", "Промахов", "сколько ошибок можно", 5, 1, 1, 20),
                        i("spawn", "Частота", "тиков между пузырями (меньше = чаще)", 9, 1, 3, 40),
                        i("life", "Жизнь пузыря", "тиков до того, как он лопнет сам", 32, 1, 10, 120),
                        d("clean-chance", "Чистые капли", "доля чистых капель (0–1)", 0.25, 0.05, 0, 0.9))));
    }

    static {
        add(new Info("sew", "Шитьё по контуру", "SHEARS",
                List.of("На ткани выложена строчка — кликай по клеткам", "по порядку, следуя за подсвеченной иглой."),
                List.of(
                        choice("shape", "Форма строчки", "по какому контуру шить", new String[][]{
                                {"random", "случайная"}, {"line", "прямая"}, {"snake", "змейка"},
                                {"shirt", "рубашка"}, {"pants", "штаны"}, {"hat", "шапка"}}),
                        i("length", "Длина", "сколько стежков у случайной строчки (для других форм не важно)", 18, 1, 6, 28),
                        i("stitch-time", "Время на стежок", "тиков на один стежок (20 = 1 с, меньше = сложнее)", 30, 2, 8, 100),
                        i("lives", "Жизни", "сколько ошибок можно", 3, 1, 1, 10),
                        new Param("thread", "Цвет нитки", "блок, которым рисуются готовые стежки (например RED_WOOL)",
                                ParamType.MATERIALS, 0, 0, 0, 1))));
    }

    private static void add(Info i) { ALL.put(i.id, i); }

    public static Info get(String id) { return ALL.getOrDefault(id == null ? "" : id.toLowerCase(), ALL.get("timing")); }
}
