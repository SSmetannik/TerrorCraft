package sgms.sgmscraft.game;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import sgms.sgmscraft.gui.Guis;
import sgms.sgmscraft.session.Session;
import sgms.sgmscraft.util.Compat;

import java.util.ArrayList;
import java.util.List;

/**
 * "Рецепт по памяти": the ingredients light up one after another — repeat the order by clicking.
 * Each round the sequence grows by one.
 * Options: rounds (4), start-length (3), lives (3), speed (8 ticks per flash),
 *          items ([GLOW_BERRIES, SUGAR, SPIDER_EYE, SLIME_BALL, BONE_MEAL, GLOWSTONE_DUST])
 */
public class MemoryGame extends Minigame {
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final float[] PITCH = {0.6f, 0.75f, 0.9f, 1.05f, 1.2f, 1.4f, 1.6f};
    private final List<Material> buttons = new ArrayList<>();
    private final List<Integer> seq = new ArrayList<>();
    private Inventory inv;
    private int round, rounds, lives, maxLives, flash, input;
    private boolean showing;
    private int showStep, showTimer, pause, redTicks;

    public MemoryGame(Session s, ConfigurationSection c, int t) { super(s, c, t); }

    @Override public String title() { return "Рецепт по памяти"; }

    @Override
    public void start() {
        rounds = i("rounds", 4);
        maxLives = lives = i("lives", 3);
        flash = Math.max(4, i("speed", 8));
        List<String> names = list("items");
        if (names.isEmpty()) names = List.of("GLOW_BERRIES", "SUGAR", "SPIDER_EYE", "SLIME_BALL", "BONE_MEAL", "GLOWSTONE_DUST");
        for (String n : names) {
            Material m = Material.matchMaterial(n);
            if (m != null && buttons.size() < SLOTS.length) buttons.add(m);
        }
        if (buttons.size() < 3) buttons.addAll(List.of(Material.SUGAR, Material.SLIME_BALL, Material.BONE_MEAL));
        int len = Math.max(1, i("start-length", 3));
        for (int k = 0; k < len; k++) seq.add(rnd.nextInt(buttons.size()));
        inv = openGui(3, "§8Запомни порядок ингредиентов");
        ItemStack pane = Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int k = 0; k < inv.getSize(); k++) inv.setItem(k, pane);
        drawButtons();
        beginShow(20);
    }

    private ItemStack button(int b, boolean lit) {
        ItemStack it = new ItemStack(lit ? Material.LIME_STAINED_GLASS_PANE : buttons.get(b), 1);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(lit ? "§a§l✦ ✦ ✦" : "§f" + buttons.get(b).name().toLowerCase().replace('_', ' '));
            try { if (lit) m.setEnchantmentGlintOverride(Boolean.TRUE); } catch (Throwable ignored) {}
            it.setItemMeta(m);
        }
        return it;
    }

    private void drawButtons() {
        int off = (SLOTS.length - buttons.size()) / 2;
        for (int b = 0; b < buttons.size(); b++) inv.setItem(SLOTS[b + off], button(b, false));
        status();
    }

    private int slotOf(int b) { return SLOTS[b + (SLOTS.length - buttons.size()) / 2]; }

    private void status() {
        String head = showing ? "§eЗапоминай..." : "§aПовтори! §7(" + input + "/" + seq.size() + ")";
        inv.setItem(4, Guis.icon(Material.BREWING_STAND, head,
                List.of("&7Раунд &f" + (round + 1) + "&7/&f" + rounds, "&7Жизни: " + hearts(lives, maxLives))));
    }

    private void beginShow(int delay) {
        showing = true;
        showStep = 0;
        showTimer = -delay;
        input = 0;
        drawButtons();
    }

    @Override
    protected void update() {
        if (redTicks > 0 && --redTicks == 0) {
            ItemStack pane = Guis.icon(Material.GRAY_STAINED_GLASS_PANE, " ", null);
            for (int k = 18; k < 27; k++) inv.setItem(k, pane);
        }
        if (pause > 0) { pause--; return; }
        if (showing) {
            showTimer++;
            if (showTimer < 0) return;
            int cycle = flash + 4;
            int idx = showTimer / cycle, phase = showTimer % cycle;
            if (idx >= seq.size()) {
                showing = false;
                drawButtons();
                Compat.sound(player, "block.note_block.pling", 0.6f, 2f);
                return;
            }
            int b = seq.get(idx);
            if (phase == 0) {
                inv.setItem(slotOf(b), button(b, true));
                Compat.sound(player, "block.note_block.harp", 0.8f, PITCH[b % PITCH.length]);
            } else if (phase == flash) {
                inv.setItem(slotOf(b), button(b, false));
            }
        }
        if (ticks % 10 == 0) bar("§d🧠 " + title() + " §7— раунд " + (round + 1) + "/" + rounds + "  " + hearts(lives, maxLives)
                + "  §c⌛ " + (timeLeft() / 20) + "с");
    }

    @Override
    public void onGuiClick(int slot) {
        if (showing || pause > 0) return;
        int b = -1;
        for (int k = 0; k < buttons.size(); k++) if (slotOf(k) == slot) b = k;
        if (b < 0) return;
        if (seq.get(input) == b) {
            Compat.sound(player, "block.note_block.harp", 0.9f, PITCH[b % PITCH.length]);
            input++;
            if (input >= seq.size()) {
                round++;
                Compat.sound(player, "entity.player.levelup", 0.6f, 1.5f);
                particle("HAPPY_VILLAGER", 10, 0.3, 0.05);
                if (round >= rounds) { win(); return; }
                seq.add(rnd.nextInt(buttons.size()));
                pause = 10;
                beginShow(10);
            } else status();
        } else {
            lives--;
            Compat.sound(player, "block.note_block.didgeridoo", 1f, 0.6f);
            ItemStack red = Guis.icon(Material.RED_STAINED_GLASS_PANE, "§c✖", null);
            for (int k = 18; k < 27; k++) inv.setItem(k, red);
            if (lives <= 0) { lose("Перепутал порядок"); return; }
            redTicks = 15;
            pause = 15;
            beginShow(15);
        }
    }
}
