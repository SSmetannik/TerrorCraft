package sgms.sgmscraft.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import sgms.sgmscraft.SGMSCraft;
import sgms.sgmscraft.data.Defs.RecipeDef;
import sgms.sgmscraft.data.Defs.StageDef;
import sgms.sgmscraft.data.Defs.StageType;
import sgms.sgmscraft.data.Defs.StationDef;
import sgms.sgmscraft.data.StationManager.Bound;
import sgms.sgmscraft.game.Games;
import sgms.sgmscraft.game.Minigame;
import sgms.sgmscraft.util.ItemSpec;
import sgms.sgmscraft.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Guis {
    private Guis() {}

    public static ItemStack icon(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            if (lore != null) meta.setLore(Text.color(lore));
            it.setItemMeta(meta);
        }
        return it;
    }

    /** Recipe selection menu of a station. */
    public static final class RecipeMenu implements InventoryHolder {
        public final Bound station;
        public final Map<Integer, RecipeDef> slots = new HashMap<>();
        private final Inventory inv;

        public RecipeMenu(SGMSCraft plugin, Bound station, Player viewer) {
            this.station = station;
            StationDef def = plugin.stationDef(station);
            List<RecipeDef> recipes = new ArrayList<>();
            for (String id : def.recipes) {
                RecipeDef r = plugin.recipe(id);
                if (r != null) recipes.add(r);
            }
            int rows = Math.max(1, Math.min(6, (recipes.size() + 8) / 9));
            inv = Bukkit.createInventory(this, rows * 9, def.name);
            int slot = 0;
            for (RecipeDef r : recipes) {
                if (slot >= rows * 9) break;
                inv.setItem(slot, recipeIcon(r, viewer));
                slots.put(slot, r);
                slot++;
            }
        }

        private static ItemStack recipeIcon(RecipeDef r, Player viewer) {
            ItemStack it = null;
            if (r.icon != null) it = r.icon.build();
            if (it == null && !r.rewardItems.isEmpty()) it = r.rewardItems.get(0).build();
            if (it == null) it = new ItemStack(Material.PAPER, 1);
            it.setAmount(1);
            ItemMeta meta = it.getItemMeta();
            if (meta == null) return it;
            meta.setDisplayName(r.name);
            List<String> lore = new ArrayList<>(r.description);
            lore.add("§7Ингредиенты:");
            for (ItemSpec s : r.allIngredients()) lore.add("§8 • §f" + Text.strip(s.displayName()) + " §7x" + s.amount);
            lore.add("§7Этапы:");
            int n = 1;
            for (StageDef st : r.stages) {
                String kind = switch (st.type) {
                    case INSERT -> "§bингредиенты";
                    case WAIT -> "§aожидание " + Text.seconds(st.timeTicks);
                    case GAME -> "§6мини-игра";
                };
                lore.add("§8 " + (n++) + ". §f" + Text.strip(st.name) + " §8(" + kind + "§8)");
            }
            if (!r.permission.isEmpty() && !viewer.hasPermission(r.permission)) {
                lore.add("");
                lore.add("§c✖ Нет доступа");
            } else {
                lore.add("");
                lore.add("§e▶ Нажми, чтобы начать");
            }
            meta.setLore(lore);
            it.setItemMeta(meta);
            return it;
        }

        @Override
        public Inventory getInventory() { return inv; }
    }

    /** Inventory used by GUI mini-games (memory, bubbles). */
    public static final class GameGui implements InventoryHolder {
        public final Minigame game;
        private final Inventory inv;
        public boolean closingByPlugin;

        public GameGui(Minigame game, int rows, String title) {
            this.game = game;
            this.inv = Bukkit.createInventory(this, rows * 9, title);
        }

        @Override
        public Inventory getInventory() { return inv; }
    }

    public static final List<String> GAME_IDS = Games.IDS;
}
