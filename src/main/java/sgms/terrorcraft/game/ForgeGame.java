package sgms.terrorcraft.game;

import org.bukkit.configuration.ConfigurationSection;
import sgms.terrorcraft.session.Session;

/**
 * "Ковка": hammer in rhythm. A beat comes every `interval` ticks; the incoming hammer
 * slides towards the anvil mark. Right-click right on the beat (± `window` ticks).
 * Hitting builds a combo; striking off-beat costs a life, a skipped beat breaks the combo.
 * Options: hits (8), interval (16 ticks), window (3), lives (3), accelerate (true)
 */
public class ForgeGame extends Minigame {
    private int interval, window, need, hits, lives, maxLives, combo, nextBeat, lastJudgedBeat = -1;
    private boolean accelerate;
    private int flashGood, flashBad;

    public ForgeGame(Session s, ConfigurationSection c, int t) { super(s, c, t); }

    @Override public String title() { return "Ковка"; }

    @Override
    public void start() {
        interval = Math.max(8, i("interval", 16));
        window = Math.max(1, i("window", 3));
        need = i("hits", 8);
        maxLives = lives = i("lives", 3);
        accelerate = cfg == null || cfg.getBoolean("accelerate", true);
        nextBeat = 30;
    }

    @Override
    protected void update() {
        if (flashGood > 0) flashGood--;
        if (flashBad > 0) flashBad--;
        // beat passed without a strike
        if (ticks > nextBeat + window) {
            if (lastJudgedBeat != nextBeat && combo > 0) {
                combo = 0;
                sound("block.note_block.hat", 0.7f, 0.6f);
            }
            nextBeat += interval;
        }
        if (ticks == nextBeat) sound("block.note_block.basedrum", 0.6f, 1f);   // metronome
        // incoming hammer: 12 cells, arrives on the anvil mark at the beat
        int dist = nextBeat - ticks;
        int cells = 12;
        int idx = (int) Math.round(Math.max(0, Math.min(1, (double) dist / interval)) * cells);
        StringBuilder b = new StringBuilder();
        b.append(flashGood > 0 ? "§a" : flashBad > 0 ? "§c" : "§6").append("⚒ ");
        b.append("§8|");
        for (int k = 0; k <= cells; k++) {
            if (k == 0) b.append(Math.abs(dist) <= window ? "§e§l◆" : "§7◆");
            else if (k == idx) b.append("§f▼");
            else b.append("§8-");
        }
        b.append("§8|  §6Удары ").append(hits).append("/").append(need)
                .append("  §eКомбо x").append(combo).append("  ").append(hearts(lives, maxLives));
        bar(b.toString());
    }

    @Override
    public void onStationClick() {
        int off = Math.abs(ticks - nextBeat);
        int prevOff = Math.abs(ticks - (nextBeat - interval));
        boolean onBeat = off <= window && lastJudgedBeat != nextBeat;
        boolean lateOnPrev = prevOff <= window && lastJudgedBeat != nextBeat - interval;
        if (onBeat || lateOnPrev) {
            lastJudgedBeat = onBeat ? nextBeat : nextBeat - interval;
            hits++;
            combo++;
            flashGood = 6;
            float pitch = (float) Math.min(2.0, 0.9 + combo * 0.12);
            sound("block.anvil.use", 0.6f, pitch);
            sound("block.smithing_table.use", 0.5f, 1.2f);
            particle("LAVA", 3, 0.15, 0.1);
            particle("CRIT", 15, 0.3, 0.35);
            if (hits >= need) { win(); return; }
            if (accelerate && combo % 3 == 0 && interval > 9) interval -= 1;
        } else {
            lives--;
            combo = 0;
            flashBad = 8;
            sound("block.anvil.land", 0.5f, 0.6f);
            particle("SMOKE", 10, 0.2, 0.02);
            if (lives <= 0) lose("Металл испорчен — сбился ритм");
        }
    }
}
