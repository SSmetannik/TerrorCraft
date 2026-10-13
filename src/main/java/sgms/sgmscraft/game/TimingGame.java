package sgms.sgmscraft.game;

import org.bukkit.configuration.ConfigurationSection;
import sgms.sgmscraft.session.Session;

/**
 * "Меткий удар": a marker runs back and forth over a bar; right-click the station
 * while it is inside the green zone. Every hit moves the zone and speeds the marker up.
 * Options: hits (5), lives (3), zone (3 cells), speed (0.35 cells/tick), speedup (1.12)
 */
public class TimingGame extends Minigame {
    private static final int LEN = 27;
    private static final String CELL = "▌";
    private double pos, speed, speedup;
    private int dir = 1, zoneStart, zone, hits, need, lives, maxLives, flash;

    public TimingGame(Session s, ConfigurationSection c, int t) { super(s, c, t); }

    @Override public String title() { return "Меткий удар"; }

    @Override
    public void start() {
        need = i("hits", 5);
        maxLives = lives = i("lives", 3);
        zone = Math.max(1, i("zone", 3));
        speed = d("speed", 0.35);
        speedup = d("speedup", 1.12);
        moveZone();
    }

    private void moveZone() { zoneStart = 2 + rnd.nextInt(LEN - zone - 3); }

    @Override
    protected void update() {
        pos += speed * dir;
        if (pos >= LEN - 1) { pos = LEN - 1; dir = -1; }
        if (pos <= 0) { pos = 0; dir = 1; }
        if (flash > 0) flash--;
        int m = (int) Math.round(pos);
        StringBuilder b = new StringBuilder("§8[");
        for (int k = 0; k < LEN; k++) {
            boolean inZone = k >= zoneStart && k < zoneStart + zone;
            // every cell is the same glyph so the bar keeps an even width; only colour changes
            if (k == m) b.append(flash > 0 ? "§c" : "§f").append(CELL);
            else b.append(inZone ? "§a" : "§8").append(CELL);
        }
        b.append("§8]  §a✔ ").append(hits).append("/").append(need).append("  ").append(hearts(lives, maxLives));
        bar(b.toString());
    }

    @Override
    public void onStationClick() {
        int m = (int) Math.round(pos);
        if (m >= zoneStart && m < zoneStart + zone) {
            hits++;
            sound("block.anvil.place", 0.5f, 1.4f + hits * 0.08f);
            sound("entity.experience_orb.pickup", 0.6f, 1.2f);
            particle("CRIT", 12, 0.25, 0.2);
            if (hits >= need) { win(); return; }
            speed *= speedup;
            moveZone();
        } else {
            lives--;
            flash = 8;
            sound("block.note_block.bass", 1f, 0.5f);
            particle("SMOKE", 8, 0.2, 0.02);
            if (lives <= 0) lose("Слишком много промахов");
        }
    }
}
