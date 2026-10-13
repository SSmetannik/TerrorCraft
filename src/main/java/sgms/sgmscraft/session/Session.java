package sgms.sgmscraft.session;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import sgms.sgmscraft.SGMSCraft;
import sgms.sgmscraft.data.Defs.RecipeDef;
import sgms.sgmscraft.data.Defs.StageDef;
import sgms.sgmscraft.data.Defs.StageType;
import sgms.sgmscraft.data.StationManager.Bound;
import sgms.sgmscraft.game.Games;
import sgms.sgmscraft.game.Minigame;
import sgms.sgmscraft.util.Compat;
import sgms.sgmscraft.util.ItemSpec;
import sgms.sgmscraft.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One crafting run: a player working through the stages of a recipe at a station. */
public class Session {
    private final SGMSCraft plugin;
    private final Bound station;
    private final RecipeDef recipe;
    private final UUID owner;
    private final boolean test;

    private int stage = -1;
    private int stageTicks;
    private int[] inserted;                 // per ingredient of the current insert stage
    private final List<ItemStack> taken = new ArrayList<>(); // everything put into the station
    private Minigame game;
    private boolean ended;

    private TextDisplay holo;
    private final List<ItemDisplay> floating = new ArrayList<>();
    private float spin;

    public Session(SGMSCraft plugin, Bound station, RecipeDef recipe, Player owner, boolean test) {
        this.plugin = plugin;
        this.station = station;
        this.recipe = recipe;
        this.owner = owner.getUniqueId();
        this.test = test;
    }

    public Player player() { return Bukkit.getPlayer(owner); }
    public UUID owner() { return owner; }
    public Bound station() { return station; }
    public RecipeDef recipe() { return recipe; }
    public Minigame game() { return game; }
    public boolean ended() { return ended; }

    /** Centre of the top face of the station block. */
    public Location top() { return station.location().add(0.5, 1.0, 0.5); }

    public void start() {
        spawnHologram();
        next();
    }

    // ------------------------------------------------------------------ flow
    private StageDef cur() { return stage >= 0 && stage < recipe.stages.size() ? recipe.stages.get(stage) : null; }

    private void next() {
        if (ended) return;
        stage++;
        stageTicks = 0;
        game = null;
        StageDef st = cur();
        if (st == null) {
            complete();
            return;
        }
        Player p = player();
        Compat.sound(top(), "block.note_block.chime", 0.8f, 1.2f + 0.1f * stage);
        if (st.type == StageType.INSERT) {
            inserted = new int[st.items.size()];
            if (p != null) p.sendTitle("", "§e" + Text.strip(st.name) + " §7— положи ингредиенты", 0, 30, 10);
        } else if (st.type == StageType.GAME) {
            game = Games.create(st.game, this, st.params, st.timeTicks);
            if (p != null) p.sendTitle("§6" + game.title(), "§7" + Text.strip(st.name), 5, 30, 10);
            game.start();
        }
        updateHologram();
    }

    public void tick() {
        if (ended) return;
        Player p = player();
        if (p == null || !p.isOnline()) {
            fail(plugin.msgRaw("left-game"));
            return;
        }
        Location t = top();
        if (!p.getWorld().getName().equals(station.world)
                || p.getLocation().distanceSquared(t) > plugin.maxDistance() * plugin.maxDistance()) {
            fail(plugin.msgRaw("too-far"));
            return;
        }
        StageDef st = cur();
        if (st == null) return;
        stageTicks++;
        switch (st.type) {
            case INSERT -> {
                if (st.timeTicks > 0 && stageTicks > st.timeTicks) {
                    fail(plugin.msgRaw("time-out"));
                    return;
                }
                if (stageTicks % 20 == 0) Compat.particle(t, "ENCHANT", 6, 0.3, 0.3, 0.3, 0.6);
            }
            case WAIT -> {
                if (stageTicks % 10 == 0) {
                    Compat.particle(t, st.particle.isEmpty() ? "SMOKE" : st.particle, 4, 0.2, 0.15, 0.2, 0.01);
                }
                if (!st.sound.isEmpty() && stageTicks % 30 == 1) Compat.sound(t, st.sound, 0.6f, 1f);
                if (p != null && stageTicks % 4 == 0) {
                    Compat.actionBar(p, "§7" + Text.strip(st.name) + " " + Text.bar((double) stageTicks / Math.max(1, st.timeTicks), 20, "§a", "§8")
                            + " §f" + Text.seconds(st.timeTicks - stageTicks));
                }
                if (stageTicks >= st.timeTicks) {
                    next();
                    return;
                }
            }
            case GAME -> {
                if (game != null) game.tick();
            }
        }
        if (ended) return;
        animateItems();
        if (stageTicks % 5 == 0) updateHologram();
    }

    /** Right click on the station by any player. */
    public void onStationClick(Player p) {
        if (ended) return;
        if (!p.getUniqueId().equals(owner)) {
            plugin.msg(p, "busy", "%player%", ownerName());
            return;
        }
        StageDef st = cur();
        if (st == null) return;
        if (st.type == StageType.INSERT) {
            insert(p, st, p.isSneaking());
        } else if (st.type == StageType.GAME && game != null) {
            if (game.gui() != null && !game.guiOpen) game.reopen();
            else game.onStationClick();
        } else if (st.type == StageType.WAIT) {
            Compat.actionBar(p, "§7Подожди... §f" + Text.seconds(st.timeTicks - stageTicks));
        }
    }

    private void insert(Player p, StageDef st, boolean fromWholeInventory) {
        PlayerInventory inv = p.getInventory();
        boolean any = false;
        for (int j = 0; j < st.items.size(); j++) {
            ItemSpec spec = st.items.get(j);
            int need = spec.amount - inserted[j];
            if (need <= 0) continue;
            if (fromWholeInventory) {
                for (int slot = 0; slot < inv.getSize() && need > 0; slot++) {
                    ItemStack it = inv.getItem(slot);
                    if (!spec.matches(it)) continue;
                    int take = Math.min(need, it.getAmount());
                    takeFrom(it, take);
                    inv.setItem(slot, it.getAmount() <= 0 ? null : it);
                    inserted[j] += take;
                    need -= take;
                    any = true;
                }
            } else {
                ItemStack hand = inv.getItemInMainHand();
                if (!spec.matches(hand)) continue;
                int take = Math.min(need, hand.getAmount());
                takeFrom(hand, take);
                inv.setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
                inserted[j] += take;
                any = true;
                break;
            }
        }
        if (!any) {
            Compat.sound(p, "block.note_block.bass", 0.8f, 0.6f);
            Compat.actionBar(p, plugin.msgRaw("wrong-item"));
            return;
        }
        Compat.sound(top(), "entity.item.pickup", 0.8f, 0.8f + rnd() * 0.4f);
        Compat.sound(top(), "block.brewing_stand.brew", 0.4f, 1.4f);
        Compat.particle(top(), "SPLASH", 12, 0.25, 0.1, 0.25, 0.05);
        refreshFloating();
        updateHologram();
        for (int j = 0; j < st.items.size(); j++) if (inserted[j] < st.items.get(j).amount) return;
        next();
    }

    private void takeFrom(ItemStack it, int amount) {
        ItemStack copy = it.clone();
        copy.setAmount(amount);
        taken.add(copy);
        it.setAmount(it.getAmount() - amount);
    }

    public void gameFinished(boolean won, String reason) {
        if (ended) return;
        Player p = player();
        if (won) {
            Compat.sound(top(), "entity.player.levelup", 0.7f, 1.6f);
            Compat.particle(top(), "HAPPY_VILLAGER", 15, 0.3, 0.3, 0.3, 0.1);
            if (p != null) p.sendTitle("§a✔", "§7этап пройден", 0, 15, 5);
            plugin.closeGameGui(this);
            next();
        } else {
            plugin.closeGameGui(this);
            fail(reason == null ? plugin.msgRaw("game-lost") : reason);
        }
    }

    private void complete() {
        if (ended) return;
        ended = true;
        Player p = player();
        Location t = top();
        Compat.sound(t, "entity.player.levelup", 1f, 1f);
        Compat.sound(t, "block.amethyst_block.resonate", 1f, 1.2f);
        Compat.particle(t, "TOTEM_OF_UNDYING", 40, 0.3, 0.5, 0.3, 0.35);
        Compat.particle(t, "HAPPY_VILLAGER", 25, 0.4, 0.4, 0.4, 0.1);
        if (p != null && !test) {
            for (ItemSpec spec : recipe.rewardItems) {
                ItemStack it = spec.build();
                if (it == null) {
                    plugin.getLogger().warning("Recipe " + recipe.id + ": reward item not found: " + spec.id);
                    continue;
                }
                for (ItemStack left : p.getInventory().addItem(it).values()) {
                    t.getWorld().dropItemNaturally(t, left);
                }
            }
            for (String cmd : recipe.rewardCommands) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
            }
            if (recipe.rewardXp > 0) p.giveExp(recipe.rewardXp);
            p.sendTitle("§a" + Text.strip(recipe.name), "§7Готово!", 5, 40, 15);
            plugin.msg(p, "success", "%recipe%", recipe.name);
        } else if (p != null) {
            for (ItemStack it : taken) {
                for (ItemStack left : p.getInventory().addItem(it).values()) t.getWorld().dropItemNaturally(t, left);
            }
            p.sendTitle("§aТест пройден!", "§7" + Text.strip(recipe.name), 5, 30, 10);
            if (!taken.isEmpty()) p.sendMessage("§2☣ §aРедактор §8» §7Ингредиенты возвращены, награда в тесте не выдаётся");
        }
        cleanup();
    }

    public void fail(String reason) {
        if (ended) return;
        ended = true;
        if (game != null) game.abort();
        plugin.closeGameGui(this);
        Player p = player();
        Location t = top();
        Compat.sound(t, "block.fire.extinguish", 1f, 0.8f);
        Compat.sound(t, "entity.generic.explode", 0.4f, 1.6f);
        Compat.particle(t, "LARGE_SMOKE", 25, 0.3, 0.3, 0.3, 0.03);
        Compat.particle(t, "ITEM_SLIME", 20, 0.3, 0.2, 0.3, 0.1);
        if (recipe.returnItemsOnFail || test) {
            for (ItemStack it : taken) {
                if (p != null && p.isOnline()) {
                    for (ItemStack left : p.getInventory().addItem(it).values()) t.getWorld().dropItemNaturally(t, left);
                } else {
                    t.getWorld().dropItemNaturally(t, it);
                }
            }
        }
        if (p != null) {
            p.sendTitle("§c✖ Провал", "§7" + Text.strip(reason), 0, 40, 15);
            plugin.msg(p, "fail", "%reason%", reason);
            if (!recipe.returnItemsOnFail && !taken.isEmpty() && !test) plugin.msg(p, "items-lost");
        }
        cleanup();
    }

    /** Silent stop (reload / shutdown / admin). Returns ingredients to the owner. */
    public void cancel() {
        if (ended) return;
        ended = true;
        if (game != null) game.abort();
        plugin.closeGameGui(this);
        Player p = player();
        for (ItemStack it : taken) {
            if (p != null && p.isOnline()) {
                for (ItemStack left : p.getInventory().addItem(it).values()) top().getWorld().dropItemNaturally(top(), left);
            } else {
                World w = top().getWorld();
                if (w != null) w.dropItemNaturally(top(), it);
            }
        }
        cleanup();
    }

    private void cleanup() {
        if (holo != null) holo.remove();
        for (ItemDisplay d : floating) d.remove();
        floating.clear();
        plugin.stations().endSession(station);
    }

    private String ownerName() {
        Player p = player();
        return p == null ? "?" : p.getName();
    }

    private static float rnd() { return (float) Math.random(); }

    // ------------------------------------------------------------- visuals
    private void spawnHologram() {
        if (!plugin.hologramsEnabled()) return;
        Location l = station.location().add(0.5, plugin.stationDef(station).hologramHeight, 0.5);
        try {
            holo = l.getWorld().spawn(l, TextDisplay.class);
            holo.setPersistent(false);
            holo.setBillboard(Display.Billboard.CENTER);
        } catch (Throwable t) {
            holo = null;
        }
    }

    private void updateHologram() {
        if (holo == null || ended) return;
        StageDef st = cur();
        if (st == null) return;
        StringBuilder b = new StringBuilder();
        b.append(recipe.name).append('\n');
        b.append("§7Этап §f").append(stage + 1).append("§7/§f").append(recipe.stages.size()).append(" §8• §e").append(Text.strip(st.name)).append('\n');
        switch (st.type) {
            case INSERT -> {
                for (int j = 0; j < st.items.size(); j++) {
                    ItemSpec s = st.items.get(j);
                    boolean done = inserted[j] >= s.amount;
                    b.append(done ? "§a✔ " : "§c✖ ").append(done ? "§7" : "§f").append(Text.strip(s.displayName()))
                            .append(" §8").append(Math.min(inserted[j], s.amount)).append('/').append(s.amount).append('\n');
                }
                b.append("§8ПКМ — положить, Shift+ПКМ — всё сразу\n");
                if (st.timeTicks > 0) b.append("§c⌛ ").append(Text.seconds(st.timeTicks - stageTicks));
            }
            case WAIT -> b.append(Text.bar((double) stageTicks / Math.max(1, st.timeTicks), 16, "§a", "§8"))
                    .append(" §f").append(Text.seconds(st.timeTicks - stageTicks));
            case GAME -> {
                if (game != null) b.append("§6").append(game.title()).append("\n§c⌛ ").append(Text.seconds(game.timeLeft()));
            }
        }
        try { holo.setText(b.toString()); } catch (Throwable ignored) {}
    }

    private void refreshFloating() {
        for (ItemDisplay d : floating) d.remove();
        floating.clear();
        int n = Math.min(8, taken.size());
        for (int i = 0; i < n; i++) {
            ItemStack it = taken.get(taken.size() - 1 - i).clone();
            it.setAmount(1);
            try {
                ItemDisplay d = top().getWorld().spawn(top(), ItemDisplay.class);
                d.setPersistent(false);
                d.setItemStack(it);
                try {
                    d.setTransformation(new Transformation(new Vector3f(0, 0, 0), new AxisAngle4f(),
                            new Vector3f(0.35f, 0.35f, 0.35f), new AxisAngle4f()));
                } catch (Throwable ignored) {}
                floating.add(d);
            } catch (Throwable ignored) {}
        }
        animateItems();
    }

    private void animateItems() {
        if (floating.isEmpty()) return;
        spin += 4f;
        Location c = top();
        int n = floating.size();
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(spin + i * 360.0 / n);
            Location l = c.clone().add(Math.cos(a) * 0.38, 0.25 + Math.sin(Math.toRadians(spin * 2 + i * 50)) * 0.05, Math.sin(a) * 0.38);
            l.setYaw((spin * 2) % 360);
            floating.get(i).teleport(l);
        }
    }
}
