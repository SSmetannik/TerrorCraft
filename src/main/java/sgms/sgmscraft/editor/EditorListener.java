package sgms.sgmscraft.editor;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import sgms.sgmscraft.SGMSCraft;

public class EditorListener implements Listener {
    private final SGMSCraft plugin;

    public EditorListener(SGMSCraft plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu m)) return;
        int raw = e.getRawSlot();
        int top = e.getInventory().getSize();
        if (raw >= top) {
            // the player's own inventory: normal moves are fine, shift-click adds a copy to the menu
            if (e.isShiftClick()) {
                e.setCancelled(true);
                ItemStack cur = e.getCurrentItem();
                if (cur != null && !cur.getType().isAir()) {
                    ItemStack copy = cur.clone();
                    Bukkit.getScheduler().runTask(plugin, () -> m.onBottomShiftClick(copy));
                }
            } else if (e.getClick() == ClickType.DOUBLE_CLICK) {
                e.setCancelled(true);
            }
            return;
        }
        e.setCancelled(true);
        if (raw < 0) return;
        if (!(e.getWhoClicked() instanceof Player)) return;
        ItemStack cursor = e.getCursor();
        if (cursor != null && cursor.getType().isAir()) cursor = null;
        Menu.Click click = new Menu.Click(e.isLeftClick(), e.isRightClick(), e.isShiftClick(), cursor == null ? null : cursor.clone());
        // opening/closing inventories inside the click event is unsafe — run the button next tick
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (m.getInventory() != null && m.player().isOnline()) m.handle(raw, click);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu)) return;
        int top = e.getInventory().getSize();
        for (int s : e.getRawSlots()) {
            if (s < top) { e.setCancelled(true); return; }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        if (!Input.waiting(e.getPlayer().getUniqueId())) return;
        e.setCancelled(true);
        Input.handle(plugin, e.getPlayer(), e.getMessage());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Input.cancel(e.getPlayer().getUniqueId());
    }
}
