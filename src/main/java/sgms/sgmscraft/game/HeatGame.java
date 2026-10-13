package sgms.sgmscraft.game;

import org.bukkit.configuration.ConfigurationSection;
import sgms.sgmscraft.session.Session;
import sgms.sgmscraft.util.Text;

import java.util.List;

/**
 * "Температура": the fire keeps cooling down — right-click the station to throw in fuel.
 * Keep the temperature inside the green zone long enough. Random drafts and flare-ups
 * shake things up; staying red-hot burns the brew.
 * Options: zone ([55, 75]), hold (8 s), fuel (9), cooling (0.22/tick), events (true)
 */
public class HeatGame extends Minigame {
    private double temp = 30, lo, hi, fuel, cooling, held, needHold;
    private int overheat, eventCooldown = 60, msgTicks;
    private boolean events;
    private String eventMsg = "";

    public HeatGame(Session s, ConfigurationSection c, int t) { super(s, c, t); }

    @Override public String title() { return "Температура"; }

    @Override
    public void start() {
        lo = 55; hi = 75;
        if (cfg != null) {
            List<?> z = cfg.getList("zone");
            if (z != null && z.size() == 2 && z.get(0) instanceof Number a && z.get(1) instanceof Number b) {
                lo = a.doubleValue(); hi = b.doubleValue();
            }
        }
        needHold = d("hold", 8) * 20;
        fuel = d("fuel", 9);
        cooling = d("cooling", 0.22);
        events = cfg == null || cfg.getBoolean("events", true);
    }

    @Override
    protected void update() {
        temp -= cooling * (0.6 + temp / 120.0);       // hotter fire loses heat faster
        if (events && --eventCooldown <= 0) {
            eventCooldown = 50 + rnd.nextInt(70);
            if (rnd.nextBoolean()) {
                temp -= 12 + rnd.nextInt(8);
                eventMsg = "§bСквозняк!";
                sound("entity.breeze.wind_burst", 0.6f, 1.2f);
                sound("item.elytra.flying", 0.3f, 1.6f);
            } else {
                temp += 10 + rnd.nextInt(8);
                eventMsg = "§6Вспышка!";
                sound("item.firecharge.use", 0.6f, 1f);
                particle("FLAME", 20, 0.3, 0.05);
            }
            msgTicks = 30;
        }
        temp = Math.max(0, Math.min(110, temp));
        if (temp >= lo && temp <= hi) {
            held++;
            if (ticks % 10 == 0) particle("HAPPY_VILLAGER", 2, 0.2, 0.02);
        } else if (temp < lo) {
            held = Math.max(0, held - 0.25);
        }
        if (temp > 97) {
            overheat++;
            if (ticks % 4 == 0) particle("LARGE_SMOKE", 4, 0.2, 0.02);
            if (overheat > 25) { lose("Перегрев — всё сгорело"); return; }
        } else {
            overheat = Math.max(0, overheat - 1);
        }
        if (ticks % 6 == 0) particle(temp > hi ? "FLAME" : "SMALL_FLAME", 1 + (int) (temp / 25), 0.2, 0.01);
        if (held >= needHold) { win(); return; }
        if (msgTicks > 0) msgTicks--;

        int cells = 30;
        int m = (int) Math.round(Math.min(1, temp / 100.0) * (cells - 1));
        int zs = (int) Math.round(lo / 100.0 * (cells - 1)), ze = (int) Math.round(hi / 100.0 * (cells - 1));
        StringBuilder g = new StringBuilder();
        for (int k = 0; k < cells; k++) {
            // same glyph everywhere = even spacing, colour shows the meaning
            if (k == m) g.append(temp > hi ? "§c" : temp >= lo ? "§f" : "§b").append("▌");
            else if (k >= zs && k <= ze) g.append("§a▌");
            else if (k > ze) g.append("§4▌");
            else g.append("§1▌");
        }
        String state = msgTicks > 0 ? eventMsg : temp < lo ? "§bхолодно — ПКМ!" : temp > hi ? "§cгорячо!" : "§aдержи!";
        bar("§6🔥 " + (int) temp + "° §8[" + g + "§8] " + state + "  §7" + Text.bar(held / needHold, 10, "§a", "§8"));
    }

    @Override
    public void onStationClick() {
        temp += fuel;
        sound("block.fire.ambient", 0.8f, 1.2f);
        sound("item.flintandsteel.use", 0.5f, 1.3f);
        particle("FLAME", 6, 0.2, 0.03);
    }
}
