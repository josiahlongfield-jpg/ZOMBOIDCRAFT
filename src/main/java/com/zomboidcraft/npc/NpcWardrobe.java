package com.zomboidcraft.npc;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * What a skinned companion wears, and why: she knows her outfits, what each is good for (work, looking nice, keeping
 * cosy), which ones she likes, and what you've said about them. She picks for herself: boots and practical clothes for
 * work or out with you, something nicer in her free time or when she's feeling romantic, a sweater when she's low,
 * the slip dress for bed, and boots after she's had to run in heels. She changes when it's safe (a few seconds,
 * kneeling), sometimes asks what you think, and listens to your answer and your suggestions as much as she feels like.
 * You can also pick her outfit yourself (the green dot at her waist); she wears it, and says what she thinks.
 * Her underwear and hair stay on; she always puts a whole outfit on.
 */
public final class NpcWardrobe {
    /** Her outfits: name, how practical, pretty and cosy (0..1), and the pieces it's made of. */
    public enum Outfit {
        WORK("work clothes", 1.0f, 0.2f, 0.6f, "sweater", "shorts", "stockings", "boots"),
        SKIRT("sweater and skirt", 0.6f, 0.6f, 0.6f, "sweater", "skirt", "stockings", "boots"),
        RED("sweater and red shorts", 0.7f, 0.4f, 0.5f, "sweater", "redshorts", "boots"),
        DRESS("the white dress", 0.0f, 1.0f, 0.1f, "dress", "heels"),
        DRESS_BOOTS("the dress and boots", 0.4f, 0.7f, 0.2f, "dress", "stockings", "boots"),
        DATE("skirt and heels", 0.1f, 0.9f, 0.5f, "sweater", "skirt", "stockings", "heels"),
        SLEEP("her sleepwear", 0f, 0f, 0f, "dress");

        public final String label;
        final float practical, pretty, cosy;
        final Set<String> pieces;

        Outfit(String label, float practical, float pretty, float cosy, String... pieces) {
            this.label = label;
            this.practical = practical;
            this.pretty = pretty;
            this.cosy = cosy;
            Set<String> p = new TreeSet<>(Set.of(pieces));
            p.add("bra");
            p.add("hair");
            this.pieces = p;
        }

        public static Outfit of(int i) {
            return i >= 0 && i < values().length ? values()[i] : null;
        }

        public static Outfit named(String s) {
            for (Outfit o : values()) {
                if (o.name().equalsIgnoreCase(s)) return o;
            }
            return null;
        }

        boolean heels() {
            return pieces.contains("heels");
        }
    }

    /** What you can say from the panels (sent as NpcNet.Talk acts): like it, not keen, suggest one, or dress her yourself. */
    public static final int LIKE = 100, DISLIKE = 101, SUGGEST = 200, DRESS = 300;
    /** How long she keeps something you picked, and how long she kneels to change. */
    private static final int KEEP = 12000, CHANGE_TICKS = 40;

    Outfit worn = Outfit.WORK;
    String why = "it's what she had on";
    final float[] taste = new float[Outfit.values().length];
    final float[] his = new float[Outfit.values().length];
    private Outfit kept;
    private long keepUntil = -1, bootsUntil = -1, askedUntil = -1, lastChange = -1_000_000, lastAsk = -1_000_000;
    private Outfit next;
    int changeTicks;
    private int heelRun, offFor, checkIn;

    void roll(RandomSource random) {
        for (Outfit o : Outfit.values()) {
            taste[o.ordinal()] = o == Outfit.SLEEP ? 0 : Mth.clamp((float) random.nextGaussian() * 0.5f, -1f, 1f);
        }
    }

    /** Dressed straight away, no fuss (a new companion, or one from before she had a wardrobe). */
    void dress(NpcEntity npc, Outfit o) {
        put(npc, o, "it's what she had on");
    }

    boolean asking(long now) {
        return now < askedUntil;
    }

    /** The pieces she has off, for the client (NpcEntity.HIDDEN). */
    Set<String> hidden() {
        Set<String> off = new TreeSet<>(Npcs.PIECES);
        off.removeAll(worn.pieces);
        return off;
    }

    private static boolean danger(NpcEntity npc) {
        return npc.mind.get(NpcMind.Need.FEAR) > 40 || NpcBrain.nearestZombie(npc, 16, false) != null;
    }

    private static boolean working(NpcEntity npc, ServerLevel level) {
        return !npc.following && npc.work.home != null && npc.work.job != NpcWork.Job.NONE && npc.work.phase(level) == NpcWork.Phase.WORK;
    }

    /** How much she'd like to be in this outfit right now, and the reason that weighs most. */
    private float score(NpcEntity npc, ServerLevel level, Outfit o, long now, String[] reason) {
        NpcBond b = npc.bond;
        boolean work = working(npc, level);
        float practicalW = work ? 2.5f : npc.following ? 1.5f : 0.3f;
        float prettyW = b.romantic(npc, now) ? 1.2f : b.flirty(npc, now) ? 0.6f : 0f;
        float cosyW = npc.mind.get(NpcMind.Need.MORALE) < 40 || npc.mind.get(NpcMind.Need.LONELY) > 60 ? 0.8f : 0f;
        float[] parts = {
                taste[o.ordinal()] * 1.2f,
                his[o.ordinal()] * (0.5f + b.affection / 100f),
                o.practical * practicalW,
                o.pretty * prettyW,
                o.cosy * cosyW,
                o == kept && now < keepUntil && (!work || o.practical >= 0.4f) ? 2.5f : 0f,
        };
        String[] words = {"she likes it", "you like it on her", work ? "she's working" : "she's out with you", "she's feeling romantic",
                "she wants something cosy", "you picked it"};
        float s = (o == worn ? 0.5f : 0f) - (o.heels() && now < bootsUntil ? 3f : 0f);
        int top = 0;
        for (int i = 0; i < parts.length; i++) {
            s += parts[i];
            if (parts[i] > parts[top]) top = i;
        }
        if (reason != null) reason[0] = o.heels() && now < bootsUntil ? "her feet still hurt" : words[top];
        return s;
    }

    /** Once a second: her heels on a run, kneeling to change, someone else's change to her clothes, and her own picks. */
    void tick(NpcEntity npc, ServerLevel level) {
        long now = level.getGameTime();
        if (!Npcs.skinned(npc.model())) return;
        boolean asleep = npc.work.asleep;
        // running in heels: she'll want boots for a while
        boolean running = npc.getDeltaMovement().horizontalDistanceSqr() > 0.2 * 0.2;
        heelRun = worn.heels() && running && !asleep ? heelRun + 1 : 0;
        if (heelRun == 3) {
            bootsUntil = now + 20 * 60 * 20;
            taste[worn.ordinal()] = Mth.clamp(taste[worn.ordinal()] - 0.05f, -1f, 1f);
            npc.lines.sayLine(npc, NpcLines.Kind.OUTFIT, NpcWork.pick(npc, "Running in heels. Brilliant plan, me.",
                    "These heels were a mistake.", "Next time I'm wearing boots. Remind me."));
        }
        if (changeTicks > 0) return;
        if (asleep) {
            if (worn != Outfit.SLEEP) put(npc, Outfit.SLEEP, "she's in bed");
            return;
        }
        // something of hers came off (or went on) that isn't her outfit: she sorts herself out when she can
        offFor = npc.hidden().equals(hidden()) ? 0 : offFor + 1;
        if (offFor >= 10 && !danger(npc)) {
            offFor = 0;
            change(npc, worn, "she fixed her clothes", now);
            npc.lines.sayLine(npc, NpcLines.Kind.OUTFIT, NpcWork.pick(npc, "Hang on. Let me sort this out.", "One second."));
            return;
        }
        // she thinks about it every half minute (straight away on waking), and not again for two minutes after a change
        if (++checkIn >= 30 || worn == Outfit.SLEEP) {
            checkIn = 0;
            if (danger(npc) || worn != Outfit.SLEEP && now - lastChange < 20 * 120) return;
            String[] reason = new String[1], bestWhy = new String[1];
            Outfit best = worn;
            float bestS = -1e9f;
            for (Outfit o : Outfit.values()) {
                if (o == Outfit.SLEEP) continue;
                float s = score(npc, level, o, now, reason);
                if (s > bestS) {
                    bestS = s;
                    best = o;
                    bestWhy[0] = reason[0];
                }
            }
            if (best != worn) {
                Outfit was = worn;
                change(npc, best, bestWhy[0], now);
                changeLine(npc, level, was, best);
                maybeAsk(npc, level, best, now);
            }
        }
    }

    /** Kneels a moment and changes into this. */
    private void change(NpcEntity npc, Outfit o, String reason, long now) {
        next = o;
        why = reason;
        changeTicks = CHANGE_TICKS;
        lastChange = now;
        npc.getNavigation().stop();
    }

    /** Every tick while she's changing: halfway through, the clothes swap. */
    void changing(NpcEntity npc) {
        if (changeTicks <= 0) return;
        npc.getNavigation().stop();
        if (--changeTicks == CHANGE_TICKS / 2 && next != null) {
            put(npc, next, why);
            next = null;
        }
    }

    private void put(NpcEntity npc, Outfit o, String reason) {
        worn = o;
        why = reason;
        npc.setHidden(hidden());
    }

    private void changeLine(NpcEntity npc, ServerLevel level, Outfit was, Outfit now) {
        String line;
        if (working(npc, level) && now.practical >= 0.6f) {
            line = NpcWork.pick(npc, "Boots on. Let's get to work.", "Work clothes. Joy.", "Right. Dressed for it.");
        } else if (was == Outfit.SLEEP) {
            line = NpcWork.pick(npc, "Okay. Dressed. Human again.", "Morning outfit, done.");
        } else if (now.pretty >= 0.7f) {
            line = NpcWork.pick(npc, "Felt like dressing up. Don't laugh.", "End of the world's no excuse to look bad.",
                    "Might as well wear it while there's someone to see it.");
        } else if (now.heels() == false && was.heels()) {
            line = NpcWork.pick(npc, "Bye, heels. Hello, feet.", "Much better.");
        } else {
            line = NpcWork.pick(npc, "That's better.", "Changed. Feels good to be out of those.", "Okay, that'll do.");
        }
        npc.lines.sayLine(npc, NpcLines.Kind.OUTFIT, line);
    }

    /** After changing into something she picked for herself, she sometimes asks you what you think. */
    private void maybeAsk(NpcEntity npc, ServerLevel level, Outfit o, long now) {
        Player p = npc.bond.player(level);
        if (p == null || p.distanceTo(npc) > 8 || npc.bond.affection < 30 || o == Outfit.WORK || now - lastAsk < 20 * 600) return;
        float chance = 0.45f + 0.25f * npc.mind.trait(NpcMind.Trait.TALKATIVE) + (npc.bond.together ? 0.2f : 0f);
        if (npc.getRandom().nextFloat() > chance) return;
        lastAsk = now;
        askedUntil = now + 20 * 90;
        npc.lines.lastAny = -1_000_000;
        npc.lines.sayLine(npc, NpcLines.Kind.REPLY, NpcWork.pick(npc, "So? What do you think?", "Be honest. Does this work?",
                "Too much for the apocalypse?", "Well? Say something."));
    }

    private static void reply(NpcEntity npc, String line) {
        npc.lines.sayLine(npc, NpcLines.Kind.REPLY, line);
    }

    /** Your answer from the panels: LIKE, DISLIKE, SUGGEST + outfit, DRESS + outfit. */
    void answer(NpcEntity npc, ServerPlayer p, int act) {
        ServerLevel level = (ServerLevel) npc.level();
        long now = level.getGameTime();
        NpcBond b = npc.bond;
        if (!Npcs.skinned(npc.model())) return;
        if (npc.work.asleep) {
            p.sendOverlayMessage(net.minecraft.network.chat.Component.literal("She's asleep"));
            return;
        }
        if (danger(npc)) {
            reply(npc, NpcWork.pick(npc, "Not now!", "Seriously? Now?"));
            return;
        }
        boolean mine = b.about(p), asked = asking(now);
        askedUntil = -1;
        int i = worn.ordinal();
        float t = taste[i];
        if (act == LIKE) {
            his[i] = Mth.clamp(his[i] + 0.35f, -1f, 1f);
            if (mine) b.nudge(asked ? 0.5f : 0.2f, asked ? 2f : 1f, asked ? 1f : 0.5f);
            npc.mind.add(NpcMind.Need.MORALE, asked ? 4f : 2f);
            if (asked && mine && worn.pretty >= 0.7f) npc.mind.remember(npc, b.withName + " liked me in " + worn.label, 0.4f, 0.4f);
            reply(npc, b.attraction >= 55 ? NpcWork.pick(npc, "I was hoping you'd say that.", "Yeah? Keep looking, then.", "Good. I wore it for you. A bit.")
                    : NpcWork.pick(npc, "Yeah? Thanks.", "Good. I wasn't sure.", "Okay. Keeping it."));
        } else if (act == DISLIKE) {
            his[i] = Mth.clamp(his[i] - 0.35f, -1f, 1f);
            if (mine && !asked) b.nudge(0f, -0.5f, 0f);
            if (t > 0.4f && (npc.mind.trait(NpcMind.Trait.LOYAL) < 0 || b.trust < 40)) {
                reply(npc, NpcWork.pick(npc, "Well, I like it.", "Good thing I'm not dressing for you, then.", "Noted. Ignored, but noted."));
            } else {
                keepUntil = -1;
                lastChange = -1_000_000;
                reply(npc, NpcWork.pick(npc, "Fine. I'll change later.", "Ouch. Okay.", "Really? ...Okay, noted."));
            }
        } else if (act >= SUGGEST && act < SUGGEST + Outfit.values().length) {
            Outfit o = Outfit.of(act - SUGGEST);
            if (o == null || o == Outfit.SLEEP) return;
            if (o == worn) {
                reply(npc, "I'm already wearing it.");
                return;
            }
            if (working(npc, level) && o.practical < 0.4f) {
                reply(npc, NpcWork.pick(npc, "Not for work. Later, maybe.", "In that? I've got planks to haul."));
                return;
            }
            float yes = 0.35f + b.affection / 250f + b.trust / 400f + taste[o.ordinal()] * 0.35f + 0.1f * npc.mind.trait(NpcMind.Trait.LOYAL)
                    + (b.together ? 0.15f : 0f) + (mine ? 0f : -0.3f);
            if (npc.getRandom().nextFloat() < yes) {
                his[o.ordinal()] = Mth.clamp(his[o.ordinal()] + 0.2f, -1f, 1f);
                kept = o;
                keepUntil = now + KEEP;
                change(npc, o, "you suggested it", now);
                reply(npc, taste[o.ordinal()] > 0.3f ? NpcWork.pick(npc, "Ooh, good call.", "I was thinking the same.")
                        : NpcWork.pick(npc, "Okay, give me a second.", "Fine. For you.", "If you insist."));
            } else {
                reply(npc, taste[o.ordinal()] < -0.3f ? NpcWork.pick(npc, "That one? No.", "I don't even like that one.")
                        : NpcWork.pick(npc, "Nah. I like what I've got on.", "Not today.", "Maybe later."));
            }
        } else if (act >= DRESS && act < DRESS + Outfit.values().length) {
            Outfit o = Outfit.of(act - DRESS);
            if (o == null || o == Outfit.SLEEP || o == worn) return;
            his[o.ordinal()] = Mth.clamp(his[o.ordinal()] + 0.2f, -1f, 1f);
            kept = o;
            keepUntil = now + KEEP;
            change(npc, o, "you picked it", now);
            float like = taste[o.ordinal()];
            if (like < -0.3f && mine && !b.together) b.nudge(0f, -0.3f, 0f);
            reply(npc, working(npc, level) && o.practical < 0.4f ? "Cute. I'll change back for work, though."
                    : like > 0.3f ? NpcWork.pick(npc, "Good choice.", "Oh, I like this one.", "You've got taste. Sometimes.")
                    : like < -0.3f ? NpcWork.pick(npc, "Really? ...Fine.", "Not my favourite, but okay.", "You owe me for this.")
                    : NpcWork.pick(npc, "Okay. This one.", "Sure, why not.", "Alright."));
        }
    }

    /** Synced to clients with the bond (bits 26-28 the outfit, 29 she's asked you about it). */
    int bits(long now) {
        return worn.ordinal() << 26 | (asking(now) ? 1 << 29 : 0);
    }

    /** What she's wearing and why, for /zc npc outfit and her mind readout. */
    String line() {
        return "Wearing " + worn.label + " (" + why + ")";
    }

    String save() {
        StringBuilder s = new StringBuilder(worn.name()).append('\t').append(why.replace('\t', ' '));
        for (int i = 0; i < taste.length; i++) {
            s.append('\t').append(String.format(Locale.ROOT, "%.2f,%.2f", taste[i], his[i]));
        }
        return s.toString();
    }

    boolean load(String s) {
        if (s == null || s.isBlank()) return false;
        try {
            String[] f = s.split("\t", -1);
            Outfit o = Outfit.named(f[0]);
            worn = o == null ? Outfit.WORK : o;
            why = f[1];
            for (int i = 0; i < taste.length && i + 2 < f.length; i++) {
                String[] p = f[i + 2].split(",");
                taste[i] = Float.parseFloat(p[0]);
                his[i] = Float.parseFloat(p[1]);
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
