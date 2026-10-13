package sgms.sgmscraft.editor;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import sgms.sgmscraft.SGMSCraft;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Asks the player to type a value in chat. */
public final class Input {
    private Input() {}

    private static final class Prompt {
        final Menu menu;
        final Function<String, Menu> onText;
        Prompt(Menu menu, Function<String, Menu> onText) { this.menu = menu; this.onText = onText; }
    }

    private static final Map<UUID, Prompt> pending = new ConcurrentHashMap<>();

    /**
     * Closes the menu and waits for a chat message.
     * onText returns the menu to open next (null = reopen the same menu).
     */
    public static void ask(Menu menu, String question, String current, Function<String, Menu> onText) {
        Player p = menu.player();
        pending.put(p.getUniqueId(), new Prompt(menu, onText));
        p.closeInventory();
        p.sendMessage("");
        p.sendMessage("§2☣ §a✎ " + question);
        if (current != null && !current.isEmpty()) p.sendMessage("§7Сейчас: §f" + current.replace('§', '&'));
        p.sendMessage("§7Напиши в чат. §8(§7«§c-§7» — отмена, цвета: §f&a&l§7, §f&#ff8800§8)");
    }

    public static boolean waiting(UUID id) { return pending.containsKey(id); }

    public static void cancel(UUID id) { pending.remove(id); }

    /** Called from the (async) chat event. Returns true when the message was an answer. */
    static boolean handle(SGMSCraft plugin, Player p, String message) {
        Prompt pr = pending.remove(p.getUniqueId());
        if (pr == null) return false;
        Bukkit.getScheduler().runTask(plugin, () -> {
            String text = message.trim();
            if (text.equals("-") || text.equalsIgnoreCase("отмена") || text.equalsIgnoreCase("cancel")) {
                Ed.msg(p, "§7Отменено");
                pr.menu.open();
                return;
            }
            Menu next;
            try {
                next = pr.onText.apply(text);
            } catch (Exception ex) {
                Ed.msg(p, "§cОшибка: " + ex.getMessage());
                next = null;
            }
            (next == null ? pr.menu : next).open();
        });
        return true;
    }
}
