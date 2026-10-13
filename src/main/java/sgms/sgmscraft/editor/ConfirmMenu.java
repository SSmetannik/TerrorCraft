package sgms.sgmscraft.editor;

import org.bukkit.entity.Player;
import sgms.sgmscraft.SGMSCraft;

import java.util.List;

/** "Are you sure?" */
public class ConfirmMenu extends Menu {
    private final String question;
    private final List<String> details;
    private final Runnable yes;

    public ConfirmMenu(SGMSCraft plugin, Player player, Menu parent, String question, List<String> details, Runnable yes) {
        super(plugin, player, parent);
        this.question = question;
        this.details = details;
        this.yes = yes;
    }

    @Override protected String title() { return "§4Точно?"; }
    @Override protected int rows() { return 3; }

    @Override
    protected void draw() {
        for (int i = 0; i < 27; i++) deco(i);
        set(13, Ed.icon("PAPER", "§f" + question, details), null);
        set(11, Ed.icon("LIME_CONCRETE", "§a✔ Да", null), c -> yes.run());
        set(15, Ed.icon("RED_CONCRETE", "§c✖ Нет, назад", null), c -> back());
    }
}
