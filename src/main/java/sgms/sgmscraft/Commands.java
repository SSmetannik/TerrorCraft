package sgms.sgmscraft;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.YamlConfiguration;
import sgms.sgmscraft.data.Defs.RecipeDef;
import sgms.sgmscraft.data.Defs.StationDef;
import sgms.sgmscraft.data.StationManager.Bound;
import sgms.sgmscraft.game.Games;
import sgms.sgmscraft.session.Session;
import sgms.sgmscraft.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Commands implements CommandExecutor, TabCompleter {
    private final SGMSCraft plugin;

    public Commands(SGMSCraft plugin) { this.plugin = plugin; }

    private void help(CommandSender s) {
        s.sendMessage(Text.color("&2☣ &aSGMSCraft &7— станции крафта"));
        s.sendMessage(Text.color("&e/tcraft editor &7— &aредактор станций и рецептов в меню"));
        s.sendMessage(Text.color("&e/tcraft give <станция> [игрок] [кол-во] &7— выдать станцию-блок"));
        s.sendMessage(Text.color("&e/tcraft bind <станция> &7— сделать станцией блок, на который смотришь"));
        s.sendMessage(Text.color("&e/tcraft unbind &7— убрать станцию с блока"));
        s.sendMessage(Text.color("&e/tcraft game <мини-игра> &7— попробовать мини-игру на станции перед тобой"));
        s.sendMessage(Text.color("&e/tcraft stop &7— прервать крафт на станции перед тобой"));
        s.sendMessage(Text.color("&e/tcraft list &7— станции и рецепты"));
        s.sendMessage(Text.color("&e/tcraft reload &7— перезагрузить конфиги"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sgmscraft.admin")) {
            plugin.msg(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player p) new sgms.sgmscraft.editor.MainMenu(plugin, p).open();
            else help(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "editor", "edit", "e" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("Only in game"); return true; }
                new sgms.sgmscraft.editor.MainMenu(plugin, p).open();
            }
            case "reload" -> {
                plugin.loadAll();
                sender.sendMessage(Text.color("&aКонфиги перезагружены: &f" + plugin.stationDefs().size() + " &aстанций, &f"
                        + plugin.recipes().size() + " &aрецептов"));
            }
            case "give" -> {
                if (args.length < 2) { help(sender); return true; }
                StationDef def = plugin.stationDef(args[1]);
                if (def == null) { sender.sendMessage(Text.color("&cНет станции &f" + args[1])); return true; }
                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : (sender instanceof Player p ? p : null);
                if (target == null) { sender.sendMessage(Text.color("&cИгрок не найден")); return true; }
                int amount = 1;
                if (args.length >= 4) try { amount = Integer.parseInt(args[3]); } catch (NumberFormatException ignored) {}
                ItemStack it = plugin.stationItem(def, amount);
                for (ItemStack left : target.getInventory().addItem(it).values()) target.getWorld().dropItemNaturally(target.getLocation(), left);
                sender.sendMessage(Text.color("&aВыдано: &f" + def.name + " &7x" + amount + " &aигроку &f" + target.getName()));
            }
            case "bind" -> {
                if (!(sender instanceof Player p) || args.length < 2) { help(sender); return true; }
                StationDef def = plugin.stationDef(args[1]);
                if (def == null) { sender.sendMessage(Text.color("&cНет станции &f" + args[1])); return true; }
                Block b = p.getTargetBlockExact(6);
                if (b == null || b.getType().isAir()) { sender.sendMessage(Text.color("&cСмотри на блок (до 6 блоков)")); return true; }
                if (plugin.stations().at(b) != null) plugin.stations().unbind(plugin.stations().at(b));
                plugin.stations().bind(b, def.id, false);
                sender.sendMessage(Text.color("&aБлок теперь станция: &f" + def.name));
            }
            case "unbind" -> {
                if (!(sender instanceof Player p)) return true;
                Block b = p.getTargetBlockExact(6);
                Bound bound = plugin.stations().at(b);
                if (bound == null) { sender.sendMessage(Text.color("&cЭто не станция")); return true; }
                plugin.stations().unbind(bound);
                sender.sendMessage(Text.color("&aСтанция убрана"));
            }
            case "stop" -> {
                if (!(sender instanceof Player p)) return true;
                Bound bound = plugin.stations().at(p.getTargetBlockExact(6));
                Session s = bound == null ? null : plugin.stations().session(bound);
                if (s == null) { sender.sendMessage(Text.color("&cНа этой станции ничего не готовится")); return true; }
                s.cancel();
                sender.sendMessage(Text.color("&aКрафт прерван, ингредиенты возвращены"));
            }
            case "game" -> {
                if (!(sender instanceof Player p) || args.length < 2) { help(sender); return true; }
                if (!Games.IDS.contains(args[1].toLowerCase())) {
                    sender.sendMessage(Text.color("&cМини-игры: &f" + String.join(", ", Games.IDS)));
                    return true;
                }
                Bound bound = plugin.stations().at(p.getTargetBlockExact(6));
                if (bound == null) { sender.sendMessage(Text.color("&cСмотри на станцию")); return true; }
                if (plugin.stations().session(bound) != null) { sender.sendMessage(Text.color("&cСтанция занята")); return true; }
                YamlConfiguration y = new YamlConfiguration();
                y.set("type", "game");
                y.set("game", args[1].toLowerCase());
                y.set("time", 40);
                y.set("name", "Тест");
                RecipeDef r = new RecipeDef();
                r.id = "_test";
                r.name = Text.color("&eТест: " + args[1]);
                r.stages.add(sgms.sgmscraft.data.Defs.StageDef.load(y));
                Session s = new Session(plugin, bound, r, p, true);
                plugin.stations().startSession(s);
                s.start();
            }
            case "list" -> {
                for (Map.Entry<String, StationDef> e : plugin.stationDefs().entrySet()) {
                    long placed = plugin.stations().all().stream().filter(b -> b.stationId.equals(e.getKey())).count();
                    sender.sendMessage(Text.color("&a" + e.getKey() + " &7(" + e.getValue().name + "&7) — на карте: &f" + placed
                            + " &7рецепты: &f" + String.join(", ", e.getValue().recipes)));
                }
            }
            default -> help(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("sgmscraft.admin")) return out;
        if (args.length == 1) {
            for (String s : List.of("editor", "give", "bind", "unbind", "game", "stop", "list", "reload"))
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("bind"))) {
            for (String s : plugin.stationDefs().keySet()) if (s.startsWith(args[1].toLowerCase())) out.add(s);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("game")) {
            for (String s : Games.IDS) if (s.startsWith(args[1].toLowerCase())) out.add(s);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase().startsWith(args[2].toLowerCase())) out.add(p.getName());
        }
        return out;
    }
}
