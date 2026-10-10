package sgms.terrorcraft;

import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import sgms.terrorcraft.data.Defs.RecipeDef;
import sgms.terrorcraft.data.Defs.StationDef;
import sgms.terrorcraft.data.StationManager.Bound;
import sgms.terrorcraft.gui.Guis.GameGui;
import sgms.terrorcraft.gui.Guis.RecipeMenu;
import sgms.terrorcraft.session.Session;
import sgms.terrorcraft.util.Compat;

public class Listeners implements Listener {
    private final TerrorCraft plugin;

    public Listeners(TerrorCraft plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        Bound bound = plugin.stations().at(b);
        if (bound == null) return;
        StationDef def = plugin.stationDef(bound);
        if (def == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        Session s = plugin.stations().session(bound);
        if (s != null) {
            s.onStationClick(p);
            return;
        }
        Session mine = plugin.stations().sessionOf(p.getUniqueId());
        if (mine != null) {
            plugin.msg(p, "already-crafting");
            return;
        }
        p.openInventory(new RecipeMenu(plugin, bound, p).getInventory());
        Compat.sound(p, "block.barrel.open", 0.6f, 1.3f);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        String id = plugin.stationIdOf(e.getItemInHand());
        if (id == null) return;
        StationDef def = plugin.stationDef(id);
        if (def == null) {
            plugin.msg(e.getPlayer(), "unknown-station", "%station%", id);
            return;
        }
        plugin.stations().bind(e.getBlockPlaced(), id, true);
        plugin.msg(e.getPlayer(), "placed", "%station%", def.name);
        Compat.particle(e.getBlockPlaced().getLocation().add(0.5, 1.1, 0.5), "HAPPY_VILLAGER", 12, 0.3, 0.2, 0.3, 0.05);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Bound bound = plugin.stations().at(e.getBlock());
        if (bound == null) return;
        Player p = e.getPlayer();
        if (plugin.stations().session(bound) != null) {
            e.setCancelled(true);
            plugin.msg(p, "station-busy-break");
            return;
        }
        if (!bound.placed && !p.hasPermission("terrorcraft.admin")) {
            e.setCancelled(true);
            plugin.msg(p, "station-protected");
            return;
        }
        StationDef def = plugin.stationDef(bound);
        plugin.stations().unbind(bound);
        if (bound.placed && def != null) {
            e.setDropItems(false);
            e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation().add(0.5, 0.5, 0.5), plugin.stationItem(def, 1));
        }
        plugin.msg(p, "removed");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        InventoryHolder h = e.getInventory().getHolder();
        if (h instanceof RecipeMenu menu) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null || e.getRawSlot() >= e.getInventory().getSize()) return;
            HumanEntity he = e.getWhoClicked();
            if (!(he instanceof Player p)) return;
            RecipeDef r = menu.slots.get(e.getRawSlot());
            if (r == null) return;
            if (!r.permission.isEmpty() && !p.hasPermission(r.permission)) {
                plugin.msg(p, "no-permission");
                Compat.sound(p, "entity.villager.no", 0.8f, 1f);
                return;
            }
            if (plugin.stations().session(menu.station) != null) {
                plugin.msg(p, "busy", "%player%", "?");
                p.closeInventory();
                return;
            }
            if (r.stages.isEmpty()) {
                plugin.msg(p, "empty-recipe");
                return;
            }
            p.closeInventory();
            Session s = new Session(plugin, menu.station, r, p, false);
            plugin.stations().startSession(s);
            plugin.msg(p, "started", "%recipe%", r.name);
            s.start();
        } else if (h instanceof GameGui g) {
            e.setCancelled(true);
            if (e.getRawSlot() < 0 || e.getRawSlot() >= e.getInventory().getSize()) return;
            if (!e.getWhoClicked().getUniqueId().equals(g.game.sessionOwner())) return;
            g.game.onGuiClick(e.getRawSlot());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder h = e.getInventory().getHolder();
        if (h instanceof RecipeMenu || h instanceof GameGui) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof GameGui g)) return;
        if (g.closingByPlugin) {
            g.closingByPlugin = false;
            return;
        }
        if (g.game.finished()) return;
        g.game.guiOpen = false;
        g.game.onGuiClose();
        if (e.getPlayer() instanceof Player p) plugin.msg(p, "gui-closed");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Session s = plugin.stations().sessionOf(e.getPlayer().getUniqueId());
        if (s != null) s.fail(plugin.msgRaw("left-game"));
    }
}
