package sgms.sgmscraft.editor;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import sgms.sgmscraft.SGMSCraft;

import java.util.HashMap;
import java.util.Map;

/** Base class of all editor menus: a chest inventory with clickable buttons. */
public abstract class Menu implements InventoryHolder {
    /** What the player did. cursor = copy of the item on the mouse cursor (or null). */
    public static final class Click {
        public final boolean left, right, shift;
        public final ItemStack cursor;

        Click(boolean left, boolean right, boolean shift, ItemStack cursor) {
            this.left = left; this.right = right; this.shift = shift; this.cursor = cursor;
        }
    }

    public interface Button { void click(Click c); }

    protected final SGMSCraft plugin;
    protected final Player player;
    protected final Menu parent;
    private Inventory inv;
    private final Map<Integer, Button> buttons = new HashMap<>();

    protected Menu(SGMSCraft plugin, Player player, Menu parent) {
        this.plugin = plugin;
        this.player = player;
        this.parent = parent;
    }

    protected abstract String title();
    protected abstract int rows();
    protected abstract void draw();

    public void open() {
        inv = Bukkit.createInventory(this, rows() * 9, title());
        fill();
        player.openInventory(inv);
    }

    /** Re-draws the content without re-opening (no flicker). */
    public void redraw() {
        if (inv == null) { open(); return; }
        fill();
    }

    private void fill() {
        buttons.clear();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, null);
        draw();
    }

    protected void set(int slot, ItemStack icon, Button b) {
        if (slot < 0 || slot >= inv.getSize()) return;
        inv.setItem(slot, icon);
        if (b != null) buttons.put(slot, b);
        else buttons.remove(slot);
    }

    protected void deco(int slot) { set(slot, Ed.pane("GRAY_STAINED_GLASS_PANE"), null); }

    /** Fills the bottom row with glass and puts the back button into slot 45. */
    protected void bottomBar() {
        int start = (rows() - 1) * 9;
        for (int i = start; i < start + 9; i++) deco(i);
        set(start, Ed.icon("ARROW", "§7◀ Назад", null), c -> back());
    }

    public void back() {
        if (parent != null) parent.open();
        else player.closeInventory();
    }

    void handle(int slot, Click c) {
        Button b = buttons.get(slot);
        if (b == null) return;
        try {
            b.click(c);
        } catch (Exception ex) {
            plugin.getLogger().warning("Editor error: " + ex);
            ex.printStackTrace();
            Ed.msg(player, "§cОшибка: " + ex.getMessage());
        }
    }

    /** Shift-click on an item in the player's own inventory. */
    public void onBottomShiftClick(ItemStack it) {}

    public Player player() { return player; }

    @Override
    public Inventory getInventory() { return inv; }
}
