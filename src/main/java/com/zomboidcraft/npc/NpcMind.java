package com.zomboidcraft.npc;

import com.zomboidcraft.creature.HarvestmanEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;

/**
 * A companion's inner life: what she needs and feels (0..100 each), the traits she was born with (-1..1 each), and her
 * diary of what's happened to her. NpcEntity ticks it once a second on the server and saves it with her.
 * <p>
 * Needs build up on their own (hunger, thirst, tiredness, loneliness) or follow the world (pain from her wounds, fear
 * from zombies, the dark and the Harvestman, adrenaline from a fight); morale drifts toward how well all that is going.
 * They change what she does: how soon she runs, how steady her aim is, how fast she keeps up, when she goes for food,
 * and what she says. Small memories fade over a few days; strong ones stay.
 */
public final class NpcMind {
    public enum Need {
        HUNGER("hunger"), THIRST("thirst"), TIRED("tiredness"), PAIN("pain"), FEAR("fear"), ADRENALINE("adrenaline"),
        LONELY("loneliness"), MORALE("morale");

        public final String label;

        Need(String label) {
            this.label = label;
        }
    }

    /** Each runs -1..1 from the first word to the second. */
    public enum Trait {
        BRAVE("cautious", "brave"), KIND("cold", "kind"), TALKATIVE("quiet", "talkative"), LOYAL("independent", "loyal"),
        OPTIMIST("pessimist", "optimist");

        public final String low, high;

        Trait(String low, String high) {
            this.low = low;
            this.high = high;
        }
    }

    /** Something that happened to her: when (game ticks), what, where, how it felt (-1 bad .. 1 good) and how strongly (0..1). */
    record Memory(long at, String what, int x, int z, float feel, float strength) {
        Memory fade(float by) {
            return new Memory(at, what, x, z, feel, strength - by);
        }
    }

    /** Per second: hunger fills in a day and a half of game time, thirst in a day, tiredness in a day awake. */
    private static final float HUNGER_RATE = 100f / (1.5f * 1200f), THIRST_RATE = 100f / 1200f, TIRED_RATE = 100f / 1200f;
    /** Alone (nobody within LONELY_RANGE) loneliness fills in about fourteen minutes; with her leader close it eases. */
    private static final float LONELY_RISE = 0.12f, LONELY_EASE = 0.5f;
    private static final double LONELY_RANGE = 12, CLOSE = 6;
    private static final int DAY = 24000, DIARY_MAX = 60;
    /** A weak memory (below this) loses this much strength a day; strong ones keep. */
    private static final float WEAK = 0.6f, FADE = 0.12f;

    final float[] need = new float[Need.values().length];
    final float[] trait = new float[Trait.values().length];
    final List<Memory> diary = new ArrayList<>();
    private long lastFade = -1, lastHarvestman = -1_000_000;
    private int kills;
    private boolean leftAloneNoted, overrunNoted;

    NpcMind() {
        need[Need.MORALE.ordinal()] = 60;
    }

    /** Rolls her traits: most people are a bit of everything, a few lean hard one way. */
    void roll(RandomSource random) {
        for (int i = 0; i < trait.length; i++) {
            trait[i] = Mth.clamp((float) random.nextGaussian() * 0.45f, -1f, 1f);
        }
    }

    public float get(Need n) {
        return need[n.ordinal()];
    }

    public float trait(Trait t) {
        return trait[t.ordinal()];
    }

    void set(Need n, float v) {
        need[n.ordinal()] = Mth.clamp(v, 0f, 100f);
    }

    void add(Need n, float by) {
        set(n, get(n) + by);
    }

    void setTrait(Trait t, float v) {
        trait[t.ordinal()] = Mth.clamp(v, -1f, 1f);
    }

    /** A sudden jolt: adrenaline up, and a little fear unless she's brave. */
    void jolt(float amount) {
        add(Need.ADRENALINE, amount);
        add(Need.FEAR, amount * 0.4f * (1f - 0.5f * trait(Trait.BRAVE)));
    }

    /** One second of her life. */
    void tick(NpcEntity npc, ServerLevel level) {
        long now = level.getGameTime();
        boolean moving = !npc.getNavigation().isDone();
        boolean sprinting = moving && npc.getDeltaMovement().horizontalDistanceSqr() > 0.2 * 0.2;
        double threat = threat(npc, level, now);
        Player leader = npc.leader();
        double leaderD = leader == null ? Double.MAX_VALUE : leader.distanceTo(npc);

        add(Need.HUNGER, HUNGER_RATE * (sprinting ? 2f : 1f));
        add(Need.THIRST, THIRST_RATE * (sprinting ? 2.5f : 1f));
        // waiting somewhere quiet she rests; on the move it builds, faster at a run
        // asleep she recovers in a few minutes; waiting somewhere quiet she rests; on the move it builds, faster at a run
        boolean resting = !npc.following && !moving && threat < 5;
        add(Need.TIRED, npc.work.asleep ? -0.6f : resting ? -0.15f : TIRED_RATE * (sprinting ? 2.5f : 1f));

        // pain follows her wounds: quick to come, slow to go
        float wound = (1f - npc.getHealth() / npc.getMaxHealth()) * 100f;
        float pain = get(Need.PAIN);
        set(Need.PAIN, wound > pain ? pain + (wound - pain) * 0.5f : pain - 0.6f);

        // fear rises fast toward what's around her and settles slowly; brave ones feel less of it
        float target = (float) threat * (1f - 0.35f * trait(Trait.BRAVE)) + (get(Need.PAIN) > 60 ? 10f : 0f);
        float fear = get(Need.FEAR);
        set(Need.FEAR, target > fear ? fear + (target - fear) * 0.5f : fear - 1.5f);

        add(Need.ADRENALINE, -1.5f);

        boolean company = leaderD < CLOSE;
        boolean anyone = level.getNearestPlayer(npc, LONELY_RANGE) != null;
        add(Need.LONELY, company ? -LONELY_EASE : anyone || npc.work.asleep ? 0f : LONELY_RISE * (1f + 0.4f * trait(Trait.TALKATIVE)));

        // morale drifts toward how things are going; optimists aim higher and drift back faster
        float aim = 60f + 15f * trait(Trait.OPTIMIST) - 0.2f * get(Need.HUNGER) - 0.25f * get(Need.THIRST)
                - 0.15f * get(Need.TIRED) - 0.25f * get(Need.PAIN) - 0.2f * get(Need.FEAR) - 0.2f * get(Need.LONELY);
        float morale = get(Need.MORALE);
        set(Need.MORALE, morale + Mth.clamp(aim - morale, -1f, 1f) * (0.06f + 0.03f * trait(Trait.OPTIMIST)));

        if (get(Need.LONELY) > 80 && !leftAloneNoted) {
            leftAloneNoted = true;
            remember(npc, "Left on my own for ages", -0.4f, 0.5f);
        } else if (get(Need.LONELY) < 30) {
            leftAloneNoted = false;
        }
        if (get(Need.FEAR) > 90 && !overrunNoted) {
            overrunNoted = true;
            remember(npc, "Nearly overrun", -0.6f, 0.7f);
        } else if (get(Need.FEAR) < 40) {
            overrunNoted = false;
        }
        fadeDiary(now);
    }

    /** How frightening it is around her (0..100): zombies by how close, the dark, and the Harvestman above all. */
    private double threat(NpcEntity npc, ServerLevel level, long now) {
        double t = 0;
        for (Zombie z : level.getEntitiesOfClass(Zombie.class, npc.getBoundingBox().inflate(16), Zombie::isAlive)) {
            t += Math.max(0, 1 - z.distanceTo(npc) / 16) * 22;
        }
        if (level.getMaxLocalRawBrightness(npc.blockPosition()) < 4) {
            t += 12;
        }
        HarvestmanEntity h = level.getEntitiesOfClass(HarvestmanEntity.class, npc.getBoundingBox().inflate(40),
                HarvestmanEntity::isAlive).stream().findFirst().orElse(null);
        if (h != null) {
            t += 70 + Math.max(0, 1 - h.distanceTo(npc) / 40) * 30;
            if (now - lastHarvestman > 20 * 300 && npc.getSensing().hasLineOfSight(h)) {
                lastHarvestman = now;
                remember(npc, "Saw the Harvestman", -0.9f, 1f);
            }
        }
        return Math.min(100, t);
    }

    /** She killed a zombie: a bit of pride, and a memory of the first and every tenth. */
    void killed(NpcEntity npc) {
        kills++;
        add(Need.MORALE, 1.5f);
        if (kills == 1) {
            remember(npc, "Killed my first zombie", 0.3f, 0.8f);
        } else if (kills % 10 == 0) {
            remember(npc, kills + " zombies down", 0.3f, 0.4f);
        }
    }

    /** Badly hurt: it stays with her. */
    void badlyHurt(NpcEntity npc) {
        add(Need.MORALE, -8f);
        remember(npc, npc.getHealth() < npc.getMaxHealth() * 0.2f ? "Nearly died" : "Badly hurt", -0.8f, 0.9f);
    }

    /** She ate or drank something; with her leader close by it's a moment shared. */
    void ate(NpcEntity npc, String what, float hunger, float thirst) {
        add(Need.HUNGER, -hunger);
        add(Need.THIRST, -thirst);
        add(Need.MORALE, 2f);
        Player leader = npc.leader();
        if (leader != null && leader.distanceTo(npc) < CLOSE) {
            add(Need.LONELY, -10f);
            if (npc.bond.about(leader)) npc.bond.sharedMeal();
            remember(npc, "Shared a meal with " + leader.getName().getString() + " (" + what + ")", 0.4f, 0.35f);
        }
    }

    void remember(NpcEntity npc, String what, float feel, float strength) {
        diary.add(new Memory(npc.level().getGameTime(), what, npc.getBlockX(), npc.getBlockZ(), feel, strength));
        while (diary.size() > DIARY_MAX) {
            // the faintest goes first
            Memory faint = diary.get(0);
            for (Memory m : diary) {
                if (m.strength() < faint.strength()) {
                    faint = m;
                }
            }
            diary.remove(faint);
        }
    }

    private void fadeDiary(long now) {
        if (lastFade < 0) {
            lastFade = now;
        }
        if (now - lastFade < DAY) {
            return;
        }
        lastFade = now;
        diary.replaceAll(m -> m.strength() < WEAK ? m.fade(FADE) : m);
        diary.removeIf(m -> m.strength() <= 0);
    }

    /** Her mood in a word or two, from whichever feeling is loudest. */
    public String mood() {
        if (get(Need.FEAR) > 75) return "terrified";
        if (get(Need.PAIN) > 70) return "in agony";
        if (get(Need.ADRENALINE) > 70) return "wired";
        if (get(Need.THIRST) > 80) return "parched";
        if (get(Need.HUNGER) > 80) return "starving";
        if (get(Need.TIRED) > 80) return "exhausted";
        if (get(Need.FEAR) > 45) return "on edge";
        if (get(Need.LONELY) > 65) return "lonely";
        if (get(Need.MORALE) < 25) return "hopeless";
        if (get(Need.MORALE) < 40) return "low";
        if (get(Need.MORALE) > 75) return "in good spirits";
        return "steady";
    }

    /** How fast she can keep up (1 = her normal pace): slowed by wounds, tiredness and hunger, quickened by adrenaline. */
    public float pace() {
        float p = 1f - get(Need.PAIN) / 300f - Math.max(0, get(Need.TIRED) - 50) / 200f - (get(Need.HUNGER) > 85 ? 0.12f : 0f)
                + get(Need.ADRENALINE) / 600f;
        return Mth.clamp(p, 0.6f, 1.15f);
    }

    /** How shaky her aim is (1 = steady): pain, tiredness and fear shake it, a bit of adrenaline steadies it. */
    public float wobble() {
        return 1f + get(Need.PAIN) / 100f + get(Need.TIRED) / 150f + get(Need.FEAR) / 120f - Math.min(get(Need.ADRENALINE), 40) / 200f;
    }

    /** How much further off a zombie sends her running (1 = as now): fear pushes it out, bravery pulls it in. */
    public float flightScale() {
        return Mth.clamp(1f + get(Need.FEAR) / 120f - 0.3f * trait(Trait.BRAVE), 0.6f, 2.2f);
    }

    /** Whether she's panicking: even armed, she runs. */
    public boolean panicking() {
        return get(Need.FEAR) > 88 + 10 * trait(Trait.BRAVE);
    }

    /** Her traits in words, the strong ones only ("brave, quiet"), or "even-tempered". */
    public String traitWords() {
        List<String> out = new ArrayList<>();
        for (Trait t : Trait.values()) {
            float v = trait(t);
            if (Math.abs(v) >= 0.25f) {
                out.add((Math.abs(v) > 0.65f ? "very " : "") + (v > 0 ? t.high : t.low));
            }
        }
        return out.isEmpty() ? "even-tempered" : String.join(", ", out);
    }

    /** Saved as plain text: needs, then traits, then kills, then one memory a line. */
    String save() {
        StringBuilder b = new StringBuilder();
        for (float v : need) b.append(String.format(Locale.ROOT, "%.2f ", v));
        b.append('\n');
        for (float v : trait) b.append(String.format(Locale.ROOT, "%.3f ", v));
        b.append('\n').append(kills).append(' ').append(lastFade).append('\n');
        for (Memory m : diary) {
            b.append(m.at()).append('\t').append(m.x()).append('\t').append(m.z()).append('\t')
                    .append(String.format(Locale.ROOT, "%.3f\t%.3f\t", m.feel(), m.strength()))
                    .append(m.what().replace('\t', ' ').replace('\n', ' ')).append('\n');
        }
        return b.toString();
    }

    /** Reads what save() wrote; anything unreadable is left as it was. Returns whether there was a saved mind. */
    boolean load(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        try {
            String[] lines = s.split("\n");
            String[] n = lines[0].trim().split(" ");
            for (int i = 0; i < Math.min(n.length, need.length); i++) need[i] = Float.parseFloat(n[i]);
            String[] t = lines[1].trim().split(" ");
            for (int i = 0; i < Math.min(t.length, trait.length); i++) trait[i] = Float.parseFloat(t[i]);
            String[] k = lines[2].trim().split(" ");
            kills = Integer.parseInt(k[0]);
            lastFade = Long.parseLong(k[1]);
            diary.clear();
            for (int i = 3; i < lines.length; i++) {
                String[] f = lines[i].split("\t", 6);
                if (f.length == 6) {
                    diary.add(new Memory(Long.parseLong(f[0]), f[5], Integer.parseInt(f[1]), Integer.parseInt(f[2]),
                            Float.parseFloat(f[3]), Float.parseFloat(f[4])));
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
