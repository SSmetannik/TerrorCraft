package sgms.sgmscraft.game;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import sgms.sgmscraft.gui.Guis;
import sgms.sgmscraft.session.Session;
import sgms.sgmscraft.util.Compat;
import sgms.sgmscraft.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Шитьё по контуру": a seam is laid out on the cloth. Click the cells one after another
 * along the seam — the highlighted cell is where the needle goes next. Clicking off the seam
 * tangles the thread, waiting too long makes the stitch slip.
 * Options: shape (random|line|snake|shirt|pants|hat), length (18, for random), lives (3),
 *          stitch-time (30 ticks per stitch), thread ([WHITE_WOOL])
 */
public class SewGame extends Minigame {
    private static final int COLS = 9, ROWS = 5;           // cloth area: rows 0..4, row 5 is the status bar

    private final List<Integer> path = new ArrayList<>();
    private final Set<Integer> onPath = new HashSet<>();
    private final Map<Integer, Integer> redFlash = new HashMap<>();
    private Inventory inv;
    private int done, lives, maxLives, stitchTime, sinceStitch, quiet;
    private Material thread;
    private ItemStack cloth, seam, needle, nextHint, wrong;

    public SewGame(Session s, ConfigurationSection c, int t) { super(s, c, t); }

    @Override public String title() { return "Шитьё"; }

    @Override
    public void start() {
        maxLives = lives = Math.max(1, i("lives", 3));
        stitchTime = Math.max(8, i("stitch-time", 30));
        List<String> th = list("thread");
        thread = th.isEmpty() ? null : Material.matchMaterial(th.get(0));
        if (thread == null) thread = Material.WHITE_WOOL;
        buildPath(s("shape", "random").toLowerCase(), Math.max(6, Math.min(28, i("length", 18))));
        onPath.addAll(path);

        cloth = Guis.icon(mat("LIGHT_GRAY_STAINED_GLASS_PANE"), " ", null);
        seam = Guis.icon(mat("BLACK_STAINED_GLASS_PANE"), "§8┈ строчка", null);
        needle = Guis.icon(mat("LIME_STAINED_GLASS_PANE"), "§a§l➜ Шей сюда!", List.of("&7Кликни, чтобы сделать стежок"));
        glint(needle);
        nextHint = Guis.icon(mat("YELLOW_STAINED_GLASS_PANE"), "§e┈ дальше сюда", null);
        wrong = Guis.icon(mat("RED_STAINED_GLASS_PANE"), "§c✖ Нитка запуталась!", null);

        inv = openGui(6, "§8✂ Прошей по строчке");
        sinceStitch = -stitchTime;                          // first stitch: double time to look around
        redraw();
    }

    private static Material mat(String n) {
        Material m = Material.matchMaterial(n);
        return m == null ? Material.PAPER : m;
    }

    private static void glint(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        if (m == null) return;
        try { m.setEnchantmentGlintOverride(Boolean.TRUE); } catch (Throwable ignored) {}
        it.setItemMeta(m);
    }

    // ------------------------------------------------------------------ shapes
    private static int slot(int r, int c) { return r * COLS + c; }

    private void buildPath(String shape, int length) {
        int[][] cells = switch (shape) {
            case "line", "прямая" -> new int[][]{{2, 0}, {2, 1}, {2, 2}, {2, 3}, {2, 4}, {2, 5}, {2, 6}, {2, 7}, {2, 8}};
            case "snake", "змейка" -> new int[][]{
                    {0, 1}, {0, 2}, {0, 3}, {0, 4}, {0, 5}, {0, 6}, {0, 7}, {1, 7},
                    {2, 7}, {2, 6}, {2, 5}, {2, 4}, {2, 3}, {2, 2}, {2, 1}, {3, 1},
                    {4, 1}, {4, 2}, {4, 3}, {4, 4}, {4, 5}, {4, 6}, {4, 7}};
            case "shirt", "рубашка" -> new int[][]{
                    {0, 5}, {0, 6}, {0, 7}, {0, 8}, {1, 8}, {2, 8}, {2, 7}, {3, 7}, {4, 7},
                    {4, 6}, {4, 5}, {4, 4}, {4, 3}, {4, 2}, {4, 1}, {3, 1}, {2, 1}, {2, 0},
                    {1, 0}, {0, 0}, {0, 1}, {0, 2}, {0, 3}, {1, 3}, {1, 4}, {1, 5}};
            case "pants", "штаны" -> new int[][]{
                    {0, 1}, {0, 2}, {0, 3}, {0, 4}, {0, 5}, {0, 6}, {0, 7}, {1, 7}, {2, 7},
                    {3, 7}, {4, 7}, {4, 6}, {4, 5}, {3, 5}, {2, 5}, {2, 4}, {2, 3}, {3, 3},
                    {4, 3}, {4, 2}, {4, 1}, {3, 1}, {2, 1}, {1, 1}};
            case "hat", "шапка" -> new int[][]{
                    {0, 2}, {0, 3}, {0, 4}, {0, 5}, {0, 6}, {1, 6}, {1, 7}, {2, 7}, {3, 7},
                    {3, 8}, {4, 8}, {4, 7}, {4, 6}, {4, 5}, {4, 4}, {4, 3}, {4, 2}, {4, 1},
                    {4, 0}, {3, 0}, {3, 1}, {2, 1}, {1, 1}, {1, 2}};
            default -> null;
        };
        if (cells != null) {
            // closed outlines start at a random point and go in a random direction
            boolean loop = shape.equals("shirt") || shape.equals("pants") || shape.equals("hat")
                    || shape.equals("рубашка") || shape.equals("штаны") || shape.equals("шапка");
            int n = cells.length;
            int off = loop ? rnd.nextInt(n) : 0;
            boolean rev = rnd.nextBoolean();
            for (int k = 0; k < n; k++) {
                int idx = rev ? (off - k + n) % n : (off + k) % n;
                path.add(slot(cells[idx][0], cells[idx][1]));
            }
            return;
        }
        randomPath(length);
    }

    /** Random non-crossing seam; prefers going straight so it looks like a real stitch line. */
    private void randomPath(int length) {
        int[][] dirs = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
        List<Integer> best = new ArrayList<>();
        for (int attempt = 0; attempt < 300 && best.size() < length; attempt++) {
            List<Integer> cur = new ArrayList<>();
            Set<Integer> used = new HashSet<>();
            int r = rnd.nextInt(ROWS), c = rnd.nextInt(COLS);
            int dir = rnd.nextInt(4);
            cur.add(slot(r, c));
            used.add(slot(r, c));
            while (cur.size() < length) {
                List<Integer> options = new ArrayList<>();
                for (int d = 0; d < 4; d++) {
                    int nr = r + dirs[d][0], nc = c + dirs[d][1];
                    if (nr < 0 || nr >= ROWS || nc < 0 || nc >= COLS || used.contains(slot(nr, nc))) continue;
                    // keep the seam readable: never touch earlier parts of itself
                    int touching = 0;
                    for (int[] e : dirs) {
                        int ar = nr + e[0], ac = nc + e[1];
                        if (ar >= 0 && ar < ROWS && ac >= 0 && ac < COLS && used.contains(slot(ar, ac))) touching++;
                    }
                    if (touching > 1) continue;
                    options.add(d);
                    if (d == dir) { options.add(d); options.add(d); } // going straight is 3x more likely
                }
                if (options.isEmpty()) break;
                dir = options.get(rnd.nextInt(options.size()));
                r += dirs[dir][0];
                c += dirs[dir][1];
                cur.add(slot(r, c));
                used.add(slot(r, c));
            }
            if (cur.size() > best.size()) best = cur;
        }
        path.clear();
        path.addAll(best);
    }

    // ------------------------------------------------------------------ drawing
    private void redraw() {
        for (int k = 0; k < ROWS * COLS; k++) {
            ItemStack it;
            if (redFlash.containsKey(k)) it = wrong;
            else if (!onPath.contains(k)) it = cloth;
            else {
                int idx = path.indexOf(k);
                if (idx < done) it = stitch(idx);
                else if (idx == done) it = needle;
                else if (idx == done + 1) it = nextHint;
                else it = seam;
            }
            inv.setItem(k, it);
        }
        ItemStack bar = Guis.icon(mat("GRAY_STAINED_GLASS_PANE"), " ", null);
        for (int k = 45; k < 54; k++) inv.setItem(k, bar);
        inv.setItem(49, Guis.icon(mat("SHEARS"), "§fСтежки: §a" + done + "§7/§f" + path.size(),
                List.of("&7Жизни: " + hearts(lives, maxLives), "&7Кликай по подсвеченной клетке")));
    }

    private ItemStack stitch(int idx) {
        ItemStack it = new ItemStack(thread, 1);
        ItemMeta m = it.getItemMeta();
        if (m != null) { m.setDisplayName("§f✔ стежок " + (idx + 1)); it.setItemMeta(m); }
        return it;
    }

    // ------------------------------------------------------------------ logic
    @Override
    protected void update() {
        sinceStitch++;
        if (!redFlash.isEmpty()) {
            boolean changed = false;
            for (Integer k : new ArrayList<>(redFlash.keySet())) {
                int left = redFlash.get(k) - 1;
                if (left <= 0) { redFlash.remove(k); changed = true; }
                else redFlash.put(k, left);
            }
            if (changed) redraw();
        }
        if (sinceStitch > stitchTime) {
            sinceStitch = 0;
            mistake(path.get(done), "Стежок съехал — слишком медленно");
            if (finished()) return;
        }
        if (quiet > 0) quiet--;
        else if (ticks % 2 == 0) {
            double left = 1.0 - Math.max(0, sinceStitch) / (double) stitchTime;
            String col = left > 0.5 ? "§a" : left > 0.25 ? "§e" : "§c";
            bar("§f✂ " + done + "/" + path.size() + "  §7игла " + Text.bar(left, 10, col, "§8") + "  "
                    + hearts(lives, maxLives) + "  §c⌛ " + (timeLeft() / 20) + "с");
        }
    }

    @Override
    public void onGuiClick(int slot) {
        if (finished() || slot < 0 || slot >= ROWS * COLS) return;
        int idx = path.indexOf(slot);
        if (idx >= 0 && idx < done) return;                // already stitched — harmless
        if (idx == done) {
            done++;
            sinceStitch = 0;
            float pitch = 0.9f + 0.8f * done / path.size();
            Compat.sound(player, "block.wool.place", 0.8f, pitch);
            if (done % 4 == 0) Compat.sound(player, "entity.sheep.shear", 0.4f, 1.4f);
            if (done >= path.size()) {
                Compat.sound(player, "ui.loom.take_result", 1f, 1f);
                particle("HAPPY_VILLAGER", 12, 0.3, 0.05);
                redraw();
                win();
                return;
            }
            redraw();
            return;
        }
        mistake(slot, "Нитка запуталась");
    }

    private void mistake(int slot, String why) {
        lives--;
        redFlash.put(slot, 8);
        Compat.sound(player, "entity.item.break", 0.7f, 1.3f);
        quiet = 25;
        Compat.actionBar(player, "§c✖ " + why + "  " + hearts(Math.max(0, lives), maxLives));
        if (lives <= 0) {
            lose(why);
            return;
        }
        redraw();
    }
}
