package sgms.sgmscraft.game;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import sgms.sgmscraft.gui.Guis;
import sgms.sgmscraft.session.Session;
import sgms.sgmscraft.util.Compat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Очистка токсина": toxic bubbles rise in the flask — pop them (click) before they burst.
 * Do NOT click the clean water drops. Gets faster as you go.
 * Options: target (15), misses (5), spawn (9 ticks), life (32 ticks), clean-chance (0.25)
 */
public class BubblesGame extends Minigame {
    private static final int ROWS = 5;
    private final Map<Integer, int[]> bubbles = new HashMap<>(); // slot -> {lifeLeft, kind(0 toxic,1 clean,2 popped-red)}
    private Inventory inv;
    private int score, target, misses, maxMisses, spawn, life, spawnTimer;
    private double cleanChance;
    private ItemStack empty, toxic, toxicOld, clean, burst;

    public BubblesGame(Session s, ConfigurationSection c, int t) { super(s, c, t); }

    @Override public String title() { return "Очистка токсина"; }

    @Override
    public void start() {
        target = i("target", 15);
        maxMisses = i("misses", 5);
        spawn = Math.max(3, i("spawn", 9));
        life = Math.max(10, i("life", 32));
        cleanChance = d("clean-chance", 0.25);
        empty = Guis.icon(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        toxic = Guis.icon(Material.LIME_STAINED_GLASS_PANE, "&a&l● Токсичный пузырь", List.of("&7Лопни его!"));
        toxicOld = Guis.icon(Material.YELLOW_STAINED_GLASS_PANE, "&e&l● Сейчас лопнет!", List.of("&7Быстрее!"));
        clean = Guis.icon(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&b● Чистая вода", List.of("&cНе трогай!"));
        burst = Guis.icon(Material.RED_STAINED_GLASS_PANE, "&c✖", null);
        inv = openGui(ROWS, "§2☣ Очистка токсина");
        for (int k = 0; k < inv.getSize(); k++) inv.setItem(k, empty);
        spawnTimer = 20;
    }

    @Override
    protected void update() {
        // age bubbles
        for (Map.Entry<Integer, int[]> e : new HashMap<>(bubbles).entrySet()) {
            int slot = e.getKey();
            int[] b = e.getValue();
            b[0]--;
            if (b[1] == 2) {                       // red "burst" marker fades
                if (b[0] <= 0) { bubbles.remove(slot); inv.setItem(slot, empty); }
                continue;
            }
            if (b[1] == 0 && b.length > 2 && b[0] == b[2] / 3) inv.setItem(slot, toxicOld);
            if (b[0] <= 0) {
                if (b[1] == 0) {                   // toxic bubble burst on its own
                    miss();
                    if (finished()) return;
                    bubbles.put(slot, new int[]{8, 2});
                    inv.setItem(slot, burst);
                } else {
                    bubbles.remove(slot);
                    inv.setItem(slot, empty);
                }
            }
        }
        if (--spawnTimer <= 0) {
            int speedUp = Math.min(spawn - 3, score / 3);
            spawnTimer = spawn - speedUp + rnd.nextInt(3);
            int tries = 0;
            while (tries++ < 20) {
                int slot = rnd.nextInt(inv.getSize());
                if (bubbles.containsKey(slot)) continue;
                boolean isClean = rnd.nextDouble() < cleanChance;
                int l = Math.max(12, life - score);
                bubbles.put(slot, new int[]{l, isClean ? 1 : 0, l});
                inv.setItem(slot, isClean ? clean : toxic);
                Compat.sound(player, "block.bubble_column.upwards_inside", 0.4f, 1.2f + rnd.nextFloat() * 0.6f);
                break;
            }
        }
        if (ticks % 5 == 0) bar("§a☣ Очищено §f" + score + "/" + target + "  §7Промахи: §c" + misses + "/" + maxMisses
                + "  §c⌛ " + (timeLeft() / 20) + "с");
    }

    private void miss() {
        misses++;
        Compat.sound(player, "entity.slime.squish", 1f, 0.6f);
        if (misses >= maxMisses) lose("Токсин вырвался наружу");
    }

    @Override
    public void onGuiClick(int slot) {
        int[] b = bubbles.get(slot);
        if (b == null || b[1] == 2) return;
        if (b[1] == 0) {
            score++;
            bubbles.remove(slot);
            inv.setItem(slot, empty);
            Compat.sound(player, "block.bubble_column.bubble_pop", 1f, 1f + Math.min(1f, score * 0.04f));
            if (score % 5 == 0) particle("ITEM_SLIME", 8, 0.25, 0.1);
            if (score >= target) win();
        } else {
            bubbles.put(slot, new int[]{8, 2});
            inv.setItem(slot, burst);
            miss();
        }
    }
}
