package sgms.terrorcraft.game;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import sgms.terrorcraft.session.Session;
import sgms.terrorcraft.util.Compat;
import sgms.terrorcraft.util.Text;

/**
 * "Помешивание": stir by turning your view in circles (always the same direction)
 * at a steady pace. Too slow — nothing happens, too fast for too long — it splashes out.
 * Options: turns (4), min-speed (150 °/s), max-speed (520 °/s), lives (3)
 */
public class StirGame extends Minigame {
    private float lastYaw;
    private double speed, progress, need, minSpeed, maxSpeed;
    private int direction, overspeed, lives, maxLives, wrongDir;

    public StirGame(Session s, ConfigurationSection c, int t) { super(s, c, t); }

    @Override public String title() { return "Помешивание"; }

    @Override
    public void start() {
        need = 360.0 * d("turns", 4);
        minSpeed = d("min-speed", 150);
        maxSpeed = d("max-speed", 520);
        maxLives = lives = i("lives", 3);
        lastYaw = player.getLocation().getYaw();
    }

    @Override
    protected void update() {
        float yaw = player.getLocation().getYaw();
        double delta = yaw - lastYaw;
        while (delta > 180) delta -= 360;
        while (delta < -180) delta += 360;
        lastYaw = yaw;
        speed = speed * 0.8 + Math.abs(delta) * 20 * 0.2;
        int dir = delta > 0.5 ? 1 : delta < -0.5 ? -1 : 0;
        if (direction == 0 && dir != 0 && Math.abs(delta) > 2) direction = dir;

        String state;
        if (dir != 0 && dir != direction) {
            wrongDir++;
            state = "§cв другую сторону!";
            if (wrongDir > 15) { progress = Math.max(0, progress - 90); wrongDir = 0; sound("entity.generic.splash", 0.5f, 1.4f); }
        } else if (speed > maxSpeed) {
            overspeed++;
            state = "§c§lСЛИШКОМ БЫСТРО";
            if (overspeed > 20) {
                overspeed = 0;
                lives--;
                progress = Math.max(0, progress - need * 0.25);
                sound("entity.generic.splash", 1f, 0.8f);
                particle("SPLASH", 40, 0.4, 0.3);
                particle("ITEM_SLIME", 15, 0.3, 0.2);
                if (lives <= 0) { lose("Всё расплескалось"); return; }
            }
        } else if (speed < minSpeed) {
            overspeed = Math.max(0, overspeed - 1);
            state = speed < 20 ? "§7крути взглядом по кругу" : "§eбыстрее";
        } else {
            overspeed = Math.max(0, overspeed - 2);
            wrongDir = 0;
            progress += Math.abs(delta);
            state = "§a✔ отлично";
            if (ticks % 3 == 0) {
                double a = Math.toRadians(ticks * 14.0 * direction);
                Location l = top().clone().add(Math.cos(a) * 0.3, -0.15, Math.sin(a) * 0.3);
                Compat.particle(l, "BUBBLE_POP", 2, 0.02, 0.02, 0.02, 0.01);
            }
            if (ticks % 8 == 0) sound("block.bubble_column.bubble_pop", 0.5f, 0.8f + (float) (progress / need));
        }
        if (progress >= need) { win(); return; }

        // speed gauge: 15 cells, green zone between min and max
        double gaugeMax = maxSpeed * 1.4;
        int cells = 15;
        int marker = (int) Math.round(Math.min(1, speed / gaugeMax) * (cells - 1));
        int zs = (int) Math.round(minSpeed / gaugeMax * (cells - 1));
        int ze = (int) Math.round(maxSpeed / gaugeMax * (cells - 1));
        StringBuilder g = new StringBuilder();
        for (int k = 0; k < cells; k++) {
            if (k == marker) g.append("§f◆");
            else g.append(k >= zs && k <= ze ? "§a-" : "§c-");
        }
        bar("§b⟳ " + Text.bar(progress / need, 16, "§b", "§8") + "  §7темп [" + g + "§7] " + state + "  " + hearts(lives, maxLives));
    }
}
