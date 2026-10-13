package sgms.sgmscraft.game;

import org.bukkit.configuration.ConfigurationSection;
import sgms.sgmscraft.session.Session;

import java.util.List;

public final class Games {
    private Games() {}

    public static final List<String> IDS = List.of("timing", "forge", "stir", "heat", "memory", "bubbles", "sew");

    public static Minigame create(String id, Session s, ConfigurationSection cfg, int timeLimit) {
        return switch (id == null ? "" : id.toLowerCase()) {
            case "forge" -> new ForgeGame(s, cfg, timeLimit);
            case "stir" -> new StirGame(s, cfg, timeLimit);
            case "heat" -> new HeatGame(s, cfg, timeLimit);
            case "memory" -> new MemoryGame(s, cfg, timeLimit);
            case "bubbles" -> new BubblesGame(s, cfg, timeLimit);
            case "sew" -> new SewGame(s, cfg, timeLimit);
            default -> new TimingGame(s, cfg, timeLimit);
        };
    }
}
