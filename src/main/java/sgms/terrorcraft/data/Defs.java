package sgms.terrorcraft.data;

import org.bukkit.configuration.ConfigurationSection;
import sgms.terrorcraft.util.ItemSpec;
import sgms.terrorcraft.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Defs {
    private Defs() {}

    public static class StationDef {
        public String id;
        public String name;
        public ItemSpec item;              // item given by /tcraft give, placed as a block
        public List<String> recipes = new ArrayList<>();
        public double hologramHeight = 1.4;
        public String ambientParticle;
        public String workSound;

        public static StationDef load(String id, ConfigurationSection c) {
            StationDef s = new StationDef();
            s.id = id;
            s.name = Text.color(c.getString("name", id));
            Object item = c.isConfigurationSection("item") ? c.getConfigurationSection("item") : c.get("item");
            s.item = item == null ? null : ItemSpec.parse(item);
            s.recipes = c.getStringList("recipes");
            s.hologramHeight = c.getDouble("hologram-height", 1.4);
            s.ambientParticle = c.getString("ambient-particle", "");
            s.workSound = c.getString("work-sound", "");
            return s;
        }
    }

    public enum StageType { INSERT, WAIT, GAME }

    public static class StageDef {
        public StageType type;
        public String name;
        public int timeTicks;              // time limit (insert/game) or duration (wait)
        public List<ItemSpec> items = new ArrayList<>();
        public String game;
        public ConfigurationSection params; // the whole stage section; minigames read their options here
        public String particle;
        public String sound;

        public static StageDef load(ConfigurationSection c) {
            StageDef s = new StageDef();
            String t = c.getString("type", "wait").toUpperCase();
            s.type = switch (t) {
                case "INSERT", "INSERT_ITEM", "ITEMS" -> StageType.INSERT;
                case "GAME", "MINIGAME", "MINI_GAME" -> StageType.GAME;
                default -> StageType.WAIT;
            };
            s.game = c.getString("game", "timing").toLowerCase();
            s.name = Text.color(c.getString("name", switch (s.type) {
                case INSERT -> "Ингредиенты";
                case WAIT -> "Ожидание";
                case GAME -> s.game;
            }));
            s.timeTicks = (int) Math.round(c.getDouble("time", s.type == StageType.WAIT ? 5 : 30) * 20);
            s.items = ItemSpec.parseList(c.getList("items"));
            s.params = c;
            s.particle = c.getString("particle", "");
            s.sound = c.getString("sound", "");
            return s;
        }
    }

    public static class RecipeDef {
        public String id;
        public String name;
        public ItemSpec icon;
        public String permission;
        public boolean returnItemsOnFail;
        public List<StageDef> stages = new ArrayList<>();
        public List<ItemSpec> rewardItems = new ArrayList<>();
        public List<String> rewardCommands = new ArrayList<>();
        public int rewardXp;
        public List<String> description = new ArrayList<>();

        @SuppressWarnings("unchecked")
        public static RecipeDef load(String id, ConfigurationSection c) {
            RecipeDef r = new RecipeDef();
            r.id = id;
            r.name = Text.color(c.getString("name", id));
            Object icon = c.isConfigurationSection("icon") ? c.getConfigurationSection("icon") : c.get("icon");
            r.icon = icon == null ? null : ItemSpec.parse(icon);
            r.permission = c.getString("permission", "");
            r.returnItemsOnFail = c.getBoolean("return-items-on-fail", false);
            r.description = Text.color(c.getStringList("description"));
            List<?> stages = c.getList("stages");
            if (stages != null) {
                int i = 0;
                for (Object o : stages) {
                    ConfigurationSection sc;
                    if (o instanceof Map<?, ?> m) {
                        sc = c.createSection("__stage_" + (i++), (Map<String, Object>) m);
                    } else continue;
                    r.stages.add(StageDef.load(sc));
                }
            }
            ConfigurationSection rw = c.getConfigurationSection("rewards");
            if (rw != null) {
                r.rewardItems = ItemSpec.parseList(rw.getList("items"));
                r.rewardCommands = rw.getStringList("commands");
                r.rewardXp = rw.getInt("xp", 0);
            }
            return r;
        }

        public List<ItemSpec> allIngredients() {
            List<ItemSpec> out = new ArrayList<>();
            for (StageDef s : stages) if (s.type == StageType.INSERT) out.addAll(s.items);
            return out;
        }
    }
}
