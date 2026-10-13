package sgms.sgmscraft.game;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import sgms.sgmscraft.session.Session;
import sgms.sgmscraft.util.Compat;

import java.util.List;
import java.util.Random;

/**
 * Base class for all mini-games. The session ticks the game every server tick.
 * A game ends by calling {@link #win()} or {@link #lose(String)}.
 */
public abstract class Minigame {
    protected final Session session;
    protected final Player player;
    protected final ConfigurationSection cfg;
    protected final Random rnd = new Random();
    protected int ticks;
    protected int timeLimit;
    private boolean finished;
    protected sgms.sgmscraft.gui.Guis.GameGui gui;
    public boolean guiOpen;

    public sgms.sgmscraft.gui.Guis.GameGui gui() { return gui; }

    /** Creates and opens this game's inventory. */
    protected org.bukkit.inventory.Inventory openGui(int rows, String title) {
        gui = new sgms.sgmscraft.gui.Guis.GameGui(this, rows, title);
        player.openInventory(gui.getInventory());
        guiOpen = true;
        return gui.getInventory();
    }

    /** Re-opens the GUI (e.g. after the player pressed ESC and we allow coming back). */
    public void reopen() {
        if (gui != null && !finished) { player.openInventory(gui.getInventory()); guiOpen = true; }
    }

    protected Minigame(Session session, ConfigurationSection cfg, int timeLimit) {
        this.session = session;
        this.player = session.player();
        this.cfg = cfg;
        this.timeLimit = timeLimit;
    }

    /** Display name, shown on the hologram. */
    public abstract String title();

    public abstract void start();

    /** Called every tick while running. */
    protected abstract void update();

    public final void tick() {
        if (finished) return;
        ticks++;
        if (timeLimit > 0 && ticks > timeLimit) {
            lose("Время вышло");
            return;
        }
        update();
    }

    /** Right click on the station block. */
    public void onStationClick() {}

    /** Click inside the game's GUI (raw slot). */
    public void onGuiClick(int slot) {}

    /** The player closed the game's GUI. */
    public void onGuiClose() {}

    public java.util.UUID sessionOwner() { return session.owner(); }

    public int timeLeft() { return Math.max(0, timeLimit - ticks); }

    public boolean finished() { return finished; }

    protected void win() {
        if (finished) return;
        finished = true;
        cleanup();
        session.gameFinished(true, null);
    }

    protected void lose(String reason) {
        if (finished) return;
        finished = true;
        cleanup();
        session.gameFinished(false, reason);
    }

    /** Forced stop (session cancelled). */
    public void abort() {
        if (finished) return;
        finished = true;
        cleanup();
    }

    protected void cleanup() {}

    // ---- helpers ----
    protected int i(String key, int def) { return cfg == null ? def : cfg.getInt(key, def); }
    protected double d(String key, double def) { return cfg == null ? def : cfg.getDouble(key, def); }
    protected String s(String key, String def) { return cfg == null ? def : cfg.getString(key, def); }
    protected List<String> list(String key) { return cfg == null ? List.of() : cfg.getStringList(key); }

    protected Location top() { return session.top(); }
    protected void bar(String text) { Compat.actionBar(player, text); }
    protected void sound(String s, float vol, float pitch) { Compat.sound(top(), s, vol, pitch); }
    protected void particle(String name, int count, double spread, double speed) {
        Compat.particle(top(), name, count, spread, spread * 0.6, spread, speed);
    }

    protected static String hearts(int lives, int max) {
        StringBuilder b = new StringBuilder("§c");
        for (int i = 0; i < max; i++) {
            if (i == lives) b.append("§8");
            b.append('❤');
        }
        return b.toString();
    }
}
