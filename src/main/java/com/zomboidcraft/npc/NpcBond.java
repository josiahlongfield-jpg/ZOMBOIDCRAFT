package com.zomboidcraft.npc;

import com.zomboidcraft.combat.GunType;
import com.zomboidcraft.survival.Drinks;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * How a companion feels about the player she's with: trust (will she rely on you), affection (does she like you) and
 * attraction (is she drawn to you), 0..100 each, and whether the two of you are together.
 * <p>
 * They move with how you treat her: time spent close, meals shared, saving her from a zombie, a gift, a kind word,
 * leaving her on her own for ages, hurting her. You can compliment her, flirt, give her what's in your hand, hug her,
 * hold her hand or kiss her (sneak + interact on her); she says yes or no by how she feels about you and how she's
 * doing, and pushing after a no costs you. Liked enough she gets flirty; together, she's romantic, comes to you for a
 * hug after a close call, and gets jealous when another companion hangs around you. It stays at flirting, hugs,
 * holding hands and kisses.
 */
public final class NpcBond {
    public enum Act {
        COMPLIMENT, FLIRT, GIFT, HUG, HOLD, KISS;

        public static Act of(int i) {
            return i >= 0 && i < values().length ? values()[i] : null;
        }
    }

    /** Ticks a hug and a kiss hold her still, facing you. */
    static final int HUG_TICKS = 20 * 3, KISS_TICKS = 20 * 2;
    /** Within this she counts as with you (time together, flirty and romantic moods). */
    static final double CLOSE = 6;

    UUID with;
    String withName = "";
    float trust = 35, affection = 25, attraction = 15;
    boolean together, holding;
    /** The pose she's holding (NpcWork.HUG / KISS) and for how many more ticks. */
    int pose, poseTicks;
    private final long[] lastAct = new long[Act.values().length];
    private long lastPlayful = -1_000_000, lastTender = -1_000_000, jealousUntil = -1_000_000;
    private int pushes, jealousCount;
    private boolean closeCall;

    NpcBond() {
        java.util.Arrays.fill(lastAct, -1_000_000L);
    }

    /** Where she starts with whoever she's just met: her nature tilts it, and attraction is hers to roll. */
    void roll(RandomSource random, NpcMind mind) {
        trust = Mth.clamp(35 + 12 * mind.trait(NpcMind.Trait.LOYAL), 10, 60);
        affection = Mth.clamp(25 + 10 * mind.trait(NpcMind.Trait.KIND), 10, 45);
        attraction = Mth.clamp(15 + (float) random.nextGaussian() * 12, 0, 45);
    }

    void meet(Player p) {
        if (with == null) {
            with = p.getUUID();
            withName = p.getName().getString();
        }
    }

    boolean about(Player p) {
        return p != null && p.getUUID().equals(with);
    }

    Player player(ServerLevel level) {
        return with == null ? null : level.getPlayerByUUID(with);
    }

    private void add(float trust, float affection, float attraction) {
        this.trust = Mth.clamp(this.trust + trust, 0, 100);
        this.affection = Mth.clamp(this.affection + affection, 0, 100);
        this.attraction = Mth.clamp(this.attraction + attraction, 0, 100);
    }

    void set(String which, float v) {
        v = Mth.clamp(v, 0, 100);
        switch (which) {
            case "trust" -> trust = v;
            case "affection" -> affection = v;
            case "attraction" -> attraction = v;
            default -> { }
        }
    }

    // ------------------------------------------------------------------ moods

    private boolean calm(NpcEntity npc) {
        NpcMind m = npc.mind;
        return !npc.work.asleep && m.get(NpcMind.Need.FEAR) < 35 && m.get(NpcMind.Need.PAIN) < 50 && m.get(NpcMind.Need.MORALE) >= 40;
    }

    private boolean near(NpcEntity npc, double within) {
        if (!(npc.level() instanceof ServerLevel level)) return false;
        Player p = player(level);
        return p != null && p.isAlive() && p.distanceTo(npc) < within;
    }

    /** Playful with you: drawn to you and in a good way, or you've just been flirting with each other. */
    boolean flirty(NpcEntity npc, long now) {
        return calm(npc) && near(npc, CLOSE) && affection >= 35
                && (attraction >= 55 || attraction >= 30 && now - lastPlayful < 20 * 120);
    }

    /** Soft on you: together, close, and a tender moment lately (or her hand in yours, or an evening sat together). */
    boolean romantic(NpcEntity npc, long now) {
        return together && calm(npc) && near(npc, 5)
                && (holding || now - lastTender < 20 * 180 || npc.work.action == NpcWork.SIT);
    }

    boolean jealous(long now) {
        return now < jealousUntil;
    }

    /** Her mood, with you in it: a strong feeling (fear, pain, hunger...) wins; otherwise jealous, romantic or flirty. */
    String mood(NpcEntity npc, long now) {
        String base = npc.mind.mood();
        if (!base.equals("steady") && !base.equals("in good spirits") && !base.equals("lonely") && !base.equals("low")) {
            return base;
        }
        if (jealous(now) && together) return "jealous";
        if (romantic(npc, now)) return "romantic";
        if (flirty(npc, now)) return "flirty";
        return base;
    }

    /** How she feels about you, in words (also used on the client from the synced numbers). */
    public static String words(float trust, float affection, float attraction, boolean together) {
        String t = trust < 20 ? "doesn't trust you" : trust < 45 ? "is wary of you" : trust < 75 ? "trusts you" : "would trust you with her life";
        String a = affection < 20 ? "dislikes you" : affection < 40 ? "is warming to you" : affection < 60 ? "likes you"
                : affection < 80 ? "is fond of you" : "cares about you deeply";
        String r = together ? "is with you" : attraction >= 70 && affection >= 60 ? "is falling for you"
                : attraction >= 55 ? "has a crush on you" : attraction >= 35 ? "thinks you're cute" : null;
        return "She " + t + ", " + a + (r == null ? "" : ", and " + r) + ".";
    }

    // ------------------------------------------------------------------ her side, once a second

    void tick(NpcEntity npc, ServerLevel level) {
        long now = level.getGameTime();
        if (with == null) {
            Player lead = npc.leader();
            if (lead != null) meet(lead);
            return;
        }
        Player p = player(level);
        NpcMind m = npc.mind;
        float fear = m.get(NpcMind.Need.FEAR);
        if (fear > 60) closeCall = true;
        if (p == null || !p.isAlive()) {
            holding = false;
            return;
        }
        withName = p.getName().getString();
        double d = p.distanceTo(npc);
        // time together, and time left alone
        if (d < CLOSE && !npc.work.asleep) {
            add(0.004f, 0.008f * (1f + 0.3f * m.trait(NpcMind.Trait.KIND)), 0);
        }
        if (m.get(NpcMind.Need.LONELY) > 80) {
            add(-0.004f, -0.01f, 0);
        }
        // she lets go when you're too far, there's a fight on, or she's staying put
        if (holding && (d > 4 || !npc.following || NpcBrain.nearestZombie(npc, 12, false) != null)) {
            holding = false;
            if (NpcBrain.nearestZombie(npc, 12, false) != null) reply(npc, "I need both hands for this.");
        }
        // together, she cools off if you've let her down for long enough
        if (together && affection < 35) {
            together = false;
            holding = false;
            m.remember(npc, "It's over with " + withName, -0.7f, 0.9f);
            m.add(NpcMind.Need.MORALE, -10);
            npc.lines.sayLine(npc, NpcLines.Kind.ROMANCE, "I can't do this anymore. We're done.");
        }
        // jealousy: another companion hanging around you while she watches
        boolean rival = together && d < 16 && !level.getEntitiesOfClass(NpcEntity.class, p.getBoundingBox().inflate(3),
                o -> o != npc && o.isAlive()).isEmpty();
        jealousCount = rival ? jealousCount + 1 : Math.max(0, jealousCount - 1);
        if (jealousCount >= 20) {
            jealousCount = 0;
            jealousUntil = now + 20 * 60;
            add(0, -1f, 0);
            npc.lines.sayLine(npc, NpcLines.Kind.JEALOUS, null);
        }
        // after a close call, she comes to you for a hug
        if (closeCall && fear < 25 && d < 5 && trust >= 50 && (together || affection >= 60) && !npc.work.asleep) {
            closeCall = false;
            hold(npc, NpcWork.HUG, HUG_TICKS);
            lastTender = now;
            m.add(NpcMind.Need.FEAR, -10);
            npc.lines.sayLine(npc, NpcLines.Kind.ROMANCE, NpcWork.pick(npc, "C'mere. I need a minute.", "Don't let go yet.",
                    "That was too close. Just... hold on to me."));
        } else if (fear < 25 && d >= 5) {
            closeCall = false;
        }
        // her own playfulness and softness, now and then
        float chatty = 1f + 0.6f * m.trait(NpcMind.Trait.TALKATIVE);
        if (romantic(npc, now) && npc.getRandom().nextFloat() < 0.03f * chatty) {
            npc.lines.say(npc, NpcLines.Kind.ROMANCE);
        } else if (flirty(npc, now) && npc.getRandom().nextFloat() < 0.04f * chatty) {
            npc.lines.say(npc, NpcLines.Kind.FLIRT);
        }
    }

    /** A zombie you killed near her while she was in trouble. */
    void saved(NpcEntity npc) {
        add(2f, 1f, 0.5f);
        npc.lines.say(npc, NpcLines.Kind.SAVED);
    }

    /** A meal with you close by. */
    void sharedMeal() {
        add(0.5f, 1.5f, 0);
    }

    /** You hurt her. */
    void hurtBy(NpcEntity npc) {
        add(-15f, -10f, -8f);
        holding = false;
        npc.mind.remember(npc, withName + " hurt me", -0.8f, 0.9f);
    }

    // ------------------------------------------------------------------ your side

    private void hold(NpcEntity npc, int action, int ticks) {
        pose = action;
        poseTicks = ticks;
        npc.getNavigation().stop();
    }

    private static void reply(NpcEntity npc, String line) {
        npc.lines.sayLine(npc, NpcLines.Kind.REPLY, line);
    }

    /** You did something sweet (or tried to); she answers. */
    void act(NpcEntity npc, ServerPlayer p, Act act) {
        ServerLevel level = (ServerLevel) npc.level();
        long now = level.getGameTime();
        NpcMind m = npc.mind;
        if (npc.work.asleep) {
            p.sendOverlayMessage(net.minecraft.network.chat.Component.literal("She's asleep"));
            return;
        }
        if (!about(p)) {
            reply(npc, act == Act.COMPLIMENT ? "Thanks." : with != null ? "I'm spoken for. Sort of. It's complicated." : "I don't know you like that.");
            return;
        }
        long since = now - lastAct[act.ordinal()];
        lastAct[act.ordinal()] = now;
        boolean again = since < 20 * 45;
        boolean scared = m.get(NpcMind.Need.FEAR) > 60;
        if (scared && act != Act.HUG) {
            reply(npc, NpcWork.pick(npc, "Not now!", "Are you serious? Now?", "Later. If there is a later."));
            return;
        }
        switch (act) {
            case COMPLIMENT -> {
                if (again) {
                    reply(npc, "You said that already.");
                    return;
                }
                add(0.5f, 2f * (1f - affection / 150f), attraction > 25 ? 1f : 0.5f);
                m.add(NpcMind.Need.MORALE, 1.5f);
                pushes = 0;
                reply(npc, attraction < 30 ? NpcWork.pick(npc, "Thanks. I guess.", "Uh... thanks.", "Flattery won't get you extra rations.")
                        : attraction < 60 ? NpcWork.pick(npc, "That's sweet.", "You're not so bad yourself.", "Okay, that was nice.")
                        : NpcWork.pick(npc, "Stop it. You're making me blush.", "Keep that up and I'll start believing you.",
                                "You always know what to say, don't you?"));
            }
            case FLIRT -> {
                if (again) {
                    pushes++;
                    add(0, 0, -0.5f);
                    reply(npc, "Easy, tiger.");
                    return;
                }
                if (attraction >= 30 && affection >= 30) {
                    add(0, 1f, 3f);
                    lastPlayful = now;
                    pushes = 0;
                    reply(npc, attraction < 60 ? NpcWork.pick(npc, "Is that your best line? It's the end of the world, try harder.",
                                    "Smooth. Real smooth.", "Careful. I might start to like you.")
                            : NpcWork.pick(npc, "Keep talking like that and I'll forget there are zombies out there.",
                                    "You're trouble, you know that?", "Only if you buy me dinner. A can of peaches will do."));
                } else if (affection >= 30) {
                    reply(npc, NpcWork.pick(npc, "Nice try.", "Ha. No."));
                } else {
                    pushes++;
                    add(0, -1f, 0);
                    reply(npc, "Not happening.");
                }
            }
            case GIFT -> gift(npc, p, since);
            case HUG -> {
                if (together || affection >= 45 || m.get(NpcMind.Need.FEAR) > 40 && trust >= 40) {
                    hold(npc, NpcWork.HUG, HUG_TICKS);
                    lastTender = now;
                    pushes = 0;
                    add(0.5f, again ? 0.3f : 1.5f, together ? 0.5f : 0);
                    m.add(NpcMind.Need.FEAR, -20);
                    m.add(NpcMind.Need.MORALE, 3);
                    m.add(NpcMind.Need.LONELY, -15);
                    reply(npc, NpcWork.pick(npc, "C'mere.", "...Okay. Just for a second.", "Thanks. I needed that."));
                } else {
                    pushes++;
                    add(0, -0.5f, 0);
                    reply(npc, "Whoa. Personal space.");
                }
            }
            case HOLD -> {
                if (holding) {
                    holding = false;
                    reply(npc, "Okay.");
                } else if (together || affection >= 60 && attraction >= 45) {
                    holding = true;
                    lastTender = now;
                    pushes = 0;
                    if (!npc.following) npc.toggleFollow(p);
                    reply(npc, NpcWork.pick(npc, "Don't let go.", "Your hands are freezing.", "Okay. Lead the way."));
                } else {
                    reply(npc, "Let's not.");
                }
            }
            case KISS -> kiss(npc, p, now, again);
        }
        reflectPushes(npc);
    }

    private void kiss(NpcEntity npc, ServerPlayer p, long now, boolean again) {
        NpcMind m = npc.mind;
        if (together) {
            hold(npc, NpcWork.KISS, KISS_TICKS);
            lastTender = now;
            pushes = 0;
            add(0.3f, again ? 0.3f : 2f, again ? 0 : 1f);
            m.add(NpcMind.Need.MORALE, again ? 1 : 5);
            m.add(NpcMind.Need.LONELY, -20);
            reply(npc, NpcWork.pick(npc, "Mm. Again later.", "...Wow.", "Don't get used to it. Okay, get used to it."));
        } else if (affection >= 70 && attraction >= 65 && trust >= 50) {
            together = true;
            hold(npc, NpcWork.KISS, KISS_TICKS);
            lastTender = now;
            pushes = 0;
            add(2f, 5f, 3f);
            m.add(NpcMind.Need.MORALE, 10);
            m.remember(npc, "First kiss with " + withName, 0.9f, 1f);
            reply(npc, NpcWork.pick(npc, "...About time.", "I was wondering when you'd do that.", "So. That happened."));
        } else if (attraction >= 45 && affection >= 45) {
            reply(npc, NpcWork.pick(npc, "Not yet. Ask me again sometime.", "Slow down. I like you, but slow down."));
        } else {
            pushes++;
            add(0, -2f, -1f);
            reply(npc, NpcWork.pick(npc, "Whoa. No.", "I don't see you like that."));
        }
    }

    private void gift(NpcEntity npc, ServerPlayer p, long since) {
        ItemStack hand = p.getMainHandItem();
        if (hand.isEmpty()) {
            reply(npc, "Your hands are empty.");
            return;
        }
        NpcMind m = npc.mind;
        ItemStack one = hand.copyWithCount(1);
        boolean food = one.has(DataComponents.FOOD), drink = Drinks.thirstValue(one) > 0;
        boolean fresh = since > 20 * 300;
        if ((food && m.get(NpcMind.Need.HUNGER) > 30 || drink && m.get(NpcMind.Need.THIRST) > 30)) {
            var f = one.get(DataComponents.FOOD);
            m.ate(npc, one.getHoverName().getString(), f == null ? 0 : f.nutrition() * 6f, Drinks.thirstValue(one));
            add(1f, 3f, 0);
            reply(npc, NpcWork.pick(npc, "Oh, you're a lifesaver.", "For me? Thank you.", "I could kiss you. Figuratively."));
        } else if (one.getItem() instanceof net.minecraft.world.item.BlockItem bi
                && (bi.getBlock() instanceof net.minecraft.world.level.block.FlowerBlock || bi.getBlock() instanceof net.minecraft.world.level.block.TallFlowerBlock)) {
            add(0.5f, fresh ? 4f : 1f, fresh ? 2f : 0.5f);
            lastPlayful = p.level().getGameTime();
            npc.work.pack.addItem(one);
            reply(npc, NpcWork.pick(npc, "Flowers? Out here? ...Thank you.", "Where did you even find these?", "Okay, that's actually really sweet."));
        } else if (GunType.of(one) != null) {
            add(3f, 1f, 0.5f);
            npc.work.pack.addItem(one);
            reply(npc, "Now that's romantic.");
        } else {
            add(0.3f, 0.5f, 0);
            npc.work.pack.addItem(one);
            reply(npc, NpcWork.pick(npc, "Um. Thanks?", "I'll... find a use for it.", "Thanks. I think."));
        }
        hand.shrink(1);
        pushes = 0;
    }

    /** Three no's in a row and she's hurt you didn't listen. */
    private void reflectPushes(NpcEntity npc) {
        if (pushes >= 3) {
            pushes = 0;
            add(-3f, -2f, -2f);
            npc.mind.remember(npc, withName + " won't take no for an answer", -0.4f, 0.5f);
            reply(npc, "I said no. Drop it.");
        }
    }

    /** Synced to clients: trust, affection, attraction (7 bits each), then together, holding, flirty, romantic, jealous. */
    int packed(NpcEntity npc, long now) {
        int q = Math.round(trust) | Math.round(affection) << 7 | Math.round(attraction) << 14;
        if (together) q |= 1 << 21;
        if (holding) q |= 1 << 22;
        if (flirty(npc, now)) q |= 1 << 23;
        if (romantic(npc, now)) q |= 1 << 24;
        if (jealous(now) && together) q |= 1 << 25;
        return q;
    }

    String save() {
        return String.format(Locale.ROOT, "%s\t%s\t%.2f\t%.2f\t%.2f\t%d", with == null ? "" : with, withName.replace('\t', ' '),
                trust, affection, attraction, together ? 1 : 0);
    }

    boolean load(String s) {
        if (s == null || s.isBlank()) return false;
        try {
            String[] f = s.split("\t", -1);
            with = f[0].isEmpty() ? null : UUID.fromString(f[0]);
            withName = f[1];
            trust = Float.parseFloat(f[2]);
            affection = Float.parseFloat(f[3]);
            attraction = Float.parseFloat(f[4]);
            together = "1".equals(f[5]);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
