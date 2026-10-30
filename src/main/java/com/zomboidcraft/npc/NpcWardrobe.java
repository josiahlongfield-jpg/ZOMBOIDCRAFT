package com.zomboidcraft.npc;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * What a skinned companion wears, piece by piece, and why. She knows each piece (what it's good for: work, looking
 * nice, keeping cosy), which ones she likes, and what you've said about them. She dresses herself: boots and practical
 * clothes for work or out with you, something nicer in her free time or when she's feeling romantic, a sweater when
 * she's low, the slip dress for bed, and boots after she's had to run in heels. She changes when it's safe (a few
 * seconds, kneeling), sometimes asks what you think, and listens to your answers and suggestions as much as she feels like.
 * <p>
 * Each piece she's wearing has its own green dot: you can ask her to take it off, or to put another piece on, and she
 * says yes or no by her taste, her mood, how she feels about you and what she's doing. Her bra counts as a top, so
 * she can be down to it if she's comfortable with you (she needs to trust you, or be with you, to go about in just her
 * underwear); the bra, her underwear and her hair always stay on.
 */
public final class NpcWardrobe {
    /** Her pieces: the name in her model, what you'd call it, and how practical, pretty and cosy it is. */
    public enum Piece {
        SWEATER("sweater", "sweater", 0.2f, 0f, 0.5f),
        DRESS("dress", "slip dress", -0.2f, 0.6f, 0f),
        SKIRT("skirt", "skirt", 0.1f, 0.3f, 0.1f),
        SHORTS("shorts", "denim shorts", 0.3f, 0.1f, 0f),
        STOCKINGS("stockings", "stockings", 0f, 0.15f, 0.2f),
        BOOTS("boots", "boots", 0.5f, 0f, 0.1f),
        HEELS("heels", "heels", -0.5f, 0.4f, 0f);

        public final String id, label;
        final float practical, pretty, cosy;

        Piece(String id, String label, float practical, float pretty, float cosy) {
            this.id = id;
            this.label = label;
            this.practical = practical;
            this.pretty = pretty;
            this.cosy = cosy;
        }

        public static Piece of(int i) {
            return i >= 0 && i < values().length ? values()[i] : null;
        }

        public static Piece named(String s) {
            for (Piece p : values()) {
                if (p.id.equalsIgnoreCase(s) || p.name().equalsIgnoreCase(s)) return p;
            }
            return null;
        }

        /** What can't be worn with it: the slip dress with a sweater, skirt or shorts, a skirt with shorts, boots with heels. */
        boolean clashes(Piece o) {
            if (this == o) return false;
            return this == DRESS && (o == SWEATER || o == SKIRT || o == SHORTS) || o == DRESS && (this == SWEATER || this == SKIRT || this == SHORTS)
                    || (this == SKIRT && o == SHORTS || this == SHORTS && o == SKIRT) || (this == BOOTS && o == HEELS || this == HEELS && o == BOOTS);
        }
    }

    /** The combinations she reaches for herself (with what she's wearing, if that's something else). */
    public enum Outfit {
        WORK("work clothes", Piece.SWEATER, Piece.SHORTS, Piece.STOCKINGS, Piece.BOOTS),
        SKIRT("sweater and skirt", Piece.SWEATER, Piece.SKIRT, Piece.STOCKINGS, Piece.BOOTS),
        DRESS("the slip dress", Piece.DRESS, Piece.HEELS),
        DRESS_BOOTS("the slip dress and boots", Piece.DRESS, Piece.STOCKINGS, Piece.BOOTS),
        DATE("skirt and heels", Piece.SWEATER, Piece.SKIRT, Piece.STOCKINGS, Piece.HEELS);

        public final String label;
        final EnumSet<Piece> pieces;

        Outfit(String label, Piece... pieces) {
            this.label = label;
            this.pieces = EnumSet.of(pieces[0], pieces);
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
    }

    /** For bed: the slip dress, barefoot. */
    private static final EnumSet<Piece> SLEEP = EnumSet.of(Piece.DRESS);

    /** What you can say or do from the panels (NpcNet.Talk acts): like it, not keen, suggest an outfit, put a piece on, take one off. */
    public static final int LIKE = 100, DISLIKE = 101, SUGGEST = 200, PUT_ON = 300, TAKE_OFF = 400;
    /** How long she keeps something you picked, and how long she kneels to change. */
    private static final int KEEP = 12000, CHANGE_TICKS = 40;

    EnumSet<Piece> worn = EnumSet.copyOf(Outfit.WORK.pieces);
    String why = "it's what she had on";
    final float[] taste = new float[Piece.values().length];
    final float[] his = new float[Piece.values().length];
    private EnumSet<Piece> kept, next;
    private long keepUntil = -1, bootsUntil = -1, askedUntil = -1, lastChange = -1_000_000, lastAsk = -1_000_000;
    int changeTicks;
    private int heelRun, offFor, checkIn;

    void roll(RandomSource random) {
        for (int i = 0; i < taste.length; i++) {
            taste[i] = Mth.clamp((float) random.nextGaussian() * 0.5f, -1f, 1f);
        }
    }

    /** Dressed straight away, no fuss (a new companion, or one from before she had a wardrobe). */
    void dress(NpcEntity npc, Outfit o) {
        put(npc, EnumSet.copyOf(o.pieces), "it's what she had on");
    }

    boolean asking(long now) {
        return now < askedUntil;
    }

    /** Dressed past her underwear: a sweater or the slip on top, and something on the bottom. */
    static boolean covered(Set<Piece> s) {
        return (s.contains(Piece.SWEATER) || s.contains(Piece.DRESS)) && (s.contains(Piece.DRESS) || s.contains(Piece.SKIRT) || s.contains(Piece.SHORTS));
    }

    /** The pieces she has off, for the client (NpcEntity.HIDDEN). */
    Set<String> hidden() {
        return hidden(worn);
    }

    static Set<String> hidden(Set<Piece> on) {
        Set<String> off = new TreeSet<>(Npcs.PIECES);
        off.remove("bra");
        off.remove("hair");
        for (Piece p : on) off.remove(p.id);
        return off;
    }

    private static boolean danger(NpcEntity npc) {
        return npc.mind.get(NpcMind.Need.FEAR) > 40 || NpcBrain.nearestZombie(npc, 16, false) != null;
    }

    private static boolean working(NpcEntity npc, ServerLevel level) {
        return !npc.following && npc.work.home != null && npc.work.job != NpcWork.Job.NONE && npc.work.phase(level) == NpcWork.Phase.WORK;
    }

    private static float practical(Set<Piece> s) {
        float v = 0;
        for (Piece p : s) v += p.practical;
        return Mth.clamp(v, 0f, 1f);
    }

    private float sum(float[] by, Set<Piece> s) {
        float v = 0;
        for (Piece p : s) v += by[p.ordinal()];
        return s.isEmpty() ? 0 : v / s.size();
    }

    /** How much she'd like to be in this right now, and the reason that weighs most. */
    private float score(NpcEntity npc, ServerLevel level, Set<Piece> s, long now, String[] reason) {
        NpcBond b = npc.bond;
        boolean work = working(npc, level);
        float practicalW = work ? 2.5f : npc.following ? 1.5f : 0.3f;
        float prettyW = b.romantic(npc, now) ? 1.2f : b.flirty(npc, now) ? 0.6f : 0f;
        float cosyW = npc.mind.get(NpcMind.Need.MORALE) < 40 || npc.mind.get(NpcMind.Need.LONELY) > 60 ? 0.8f : 0f;
        float pretty = 0, cosy = 0;
        for (Piece p : s) {
            pretty += p.pretty;
            cosy += p.cosy;
        }
        boolean heels = s.contains(Piece.HEELS);
        float[] parts = {
                sum(taste, s) * 1.6f,
                sum(his, s) * (0.6f + b.affection / 80f),
                practical(s) * practicalW,
                Math.min(1f, pretty) * prettyW,
                Math.min(1f, cosy) * cosyW,
                s.equals(kept) && now < keepUntil && (!work || practical(s) >= 0.4f) ? 2.5f : 0f,
        };
        String[] words = {"she likes it", "you like it on her", work ? "she's working" : "she's out with you", "she's feeling romantic",
                "she wants something cosy", "you picked it"};
        float v = (s.equals(worn) ? 0.5f : 0f) - (heels && now < bootsUntil ? 3f : 0f);
        int top = 0;
        for (int i = 0; i < parts.length; i++) {
            v += parts[i];
            if (parts[i] > parts[top]) top = i;
        }
        if (reason != null) reason[0] = heels && now < bootsUntil ? "her feet still hurt" : words[top];
        return v;
    }

    /** Once a second: her heels on a run, someone else's change to her clothes, bed, and her own picks. */
    void tick(NpcEntity npc, ServerLevel level) {
        long now = level.getGameTime();
        if (!Npcs.dresses(npc.model())) return;
        boolean asleep = npc.work.asleep;
        // running in heels: she'll want boots for a while
        boolean running = npc.getDeltaMovement().horizontalDistanceSqr() > 0.2 * 0.2;
        heelRun = worn.contains(Piece.HEELS) && running && !asleep ? heelRun + 1 : 0;
        if (heelRun == 3) {
            bootsUntil = now + 20 * 60 * 20;
            taste[Piece.HEELS.ordinal()] = Mth.clamp(taste[Piece.HEELS.ordinal()] - 0.05f, -1f, 1f);
            npc.lines.sayLine(npc, NpcLines.Kind.OUTFIT, NpcWork.pick(npc, "Running in heels. Brilliant plan, me.",
                    "These heels were a mistake.", "Next time I'm wearing boots. Remind me."));
        }
        if (changeTicks > 0) return;
        if (asleep) {
            if (!worn.equals(SLEEP)) put(npc, EnumSet.copyOf(SLEEP), "she's in bed");
            return;
        }
        // something of hers came off (or went on) that she didn't put there: she sorts herself out when she can
        offFor = npc.hidden().equals(hidden()) ? 0 : offFor + 1;
        if (offFor >= 10 && !danger(npc)) {
            offFor = 0;
            change(npc, worn, "she fixed her clothes", now);
            npc.lines.sayLine(npc, NpcLines.Kind.OUTFIT, NpcWork.pick(npc, "Hang on. Let me sort this out.", "One second."));
            return;
        }
        // she thinks about it every half minute (straight away on waking), and not again for two minutes after a change
        boolean waking = worn.equals(SLEEP);
        if (++checkIn >= 30 || waking) {
            checkIn = 0;
            if (danger(npc) || !waking && now - lastChange < 20 * 120) return;
            String[] reason = new String[1];
            EnumSet<Piece> best = worn;
            String bestWhy = why;
            float bestS = waking ? -1e9f : score(npc, level, worn, now, reason);
            if (!waking) bestWhy = reason[0];
            for (Outfit o : Outfit.values()) {
                float s = score(npc, level, o.pieces, now, reason);
                if (s > bestS) {
                    bestS = s;
                    best = o.pieces;
                    bestWhy = reason[0];
                }
            }
            if (!best.equals(worn)) {
                EnumSet<Piece> was = worn;
                change(npc, best, bestWhy, now);
                changeLine(npc, level, was, best);
                maybeAsk(npc, level, best, now);
            }
        }
    }

    /** Kneels a moment and changes into this. */
    private void change(NpcEntity npc, Set<Piece> s, String reason, long now) {
        next = EnumSet.copyOf(s);
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

    private void put(NpcEntity npc, EnumSet<Piece> s, String reason) {
        worn = EnumSet.copyOf(s);
        why = reason;
        npc.setHidden(hidden());
    }

    private void changeLine(NpcEntity npc, ServerLevel level, Set<Piece> was, Set<Piece> now) {
        float pretty = 0;
        for (Piece p : now) pretty += p.pretty;
        String line;
        if (working(npc, level) && practical(now) >= 0.6f) {
            line = NpcWork.pick(npc, "Boots on. Let's get to work.", "Work clothes. Joy.", "Right. Dressed for it.");
        } else if (was.equals(SLEEP)) {
            line = NpcWork.pick(npc, "Okay. Dressed. Human again.", "Morning outfit, done.");
        } else if (pretty >= 0.7f) {
            line = NpcWork.pick(npc, "Felt like dressing up. Don't laugh.", "End of the world's no excuse to look bad.",
                    "Might as well wear it while there's someone to see it.");
        } else if (!now.contains(Piece.HEELS) && was.contains(Piece.HEELS)) {
            line = NpcWork.pick(npc, "Bye, heels. Hello, feet.", "Much better.");
        } else {
            line = NpcWork.pick(npc, "That's better.", "Changed. Feels good to be out of those.", "Okay, that'll do.");
        }
        npc.lines.sayLine(npc, NpcLines.Kind.OUTFIT, line);
    }

    /** After changing into something she picked for herself, she sometimes asks you what you think. */
    private void maybeAsk(NpcEntity npc, ServerLevel level, Set<Piece> s, long now) {
        Player p = npc.bond.player(level);
        if (p == null || p.distanceTo(npc) > 8 || npc.bond.affection < 30 || s.equals(Outfit.WORK.pieces) || now - lastAsk < 20 * 600) return;
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

    /** With this piece on: whatever clashes comes off, and if that leaves her without a top or bottom she adds the one she likes best. */
    private EnumSet<Piece> withOn(Piece p) {
        EnumSet<Piece> s = EnumSet.copyOf(worn);
        s.removeIf(p::clashes);
        s.add(p);
        if (!s.contains(Piece.SWEATER) && !s.contains(Piece.DRESS)) s.add(Piece.SWEATER);
        if (!covered(s)) s.add(taste[Piece.SKIRT.ordinal()] >= taste[Piece.SHORTS.ordinal()] ? Piece.SKIRT : Piece.SHORTS);
        return s;
    }

    /** Would she wear this, if you asked? Her taste, how she feels about you, and her nature. */
    private boolean willing(NpcEntity npc, boolean mine, float liking) {
        NpcBond b = npc.bond;
        float yes = 0.4f + b.affection / 250f + b.trust / 400f + liking * 0.4f + 0.1f * npc.mind.trait(NpcMind.Trait.LOYAL)
                + (b.together ? 0.15f : 0f) + (mine ? 0f : -0.3f);
        return npc.getRandom().nextFloat() < yes;
    }

    /** Your answer or ask from the panels: LIKE, DISLIKE, SUGGEST + outfit, PUT_ON + piece, TAKE_OFF + piece. */
    void answer(NpcEntity npc, ServerPlayer p, int act) {
        ServerLevel level = (ServerLevel) npc.level();
        long now = level.getGameTime();
        NpcBond b = npc.bond;
        if (!Npcs.dresses(npc.model())) return;
        if (npc.work.asleep) {
            p.sendOverlayMessage(net.minecraft.network.chat.Component.literal("She's asleep"));
            return;
        }
        if (danger(npc)) {
            reply(npc, NpcWork.pick(npc, "Not now!", "Seriously? Now?"));
            return;
        }
        boolean mine = b.about(p), asked = asking(now), work = working(npc, level);
        askedUntil = -1;
        float t = sum(taste, worn);
        if (act == LIKE) {
            for (Piece x : worn) his[x.ordinal()] = Mth.clamp(his[x.ordinal()] + 0.25f, -1f, 1f);
            if (mine) b.nudge(asked ? 0.5f : 0.2f, asked ? 2f : 1f, asked ? 1f : 0.5f);
            npc.mind.add(NpcMind.Need.MORALE, asked ? 4f : 2f);
            reply(npc, b.attraction >= 55 ? NpcWork.pick(npc, "I was hoping you'd say that.", "Yeah? Keep looking, then.", "Good. I wore it for you. A bit.")
                    : NpcWork.pick(npc, "Yeah? Thanks.", "Good. I wasn't sure.", "Okay. Keeping it."));
        } else if (act == DISLIKE) {
            for (Piece x : worn) his[x.ordinal()] = Mth.clamp(his[x.ordinal()] - 0.25f, -1f, 1f);
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
            if (o.pieces.equals(worn)) {
                reply(npc, "I'm already wearing it.");
                return;
            }
            if (work && practical(o.pieces) < 0.4f) {
                reply(npc, NpcWork.pick(npc, "Not for work. Later, maybe.", "In that? I've got planks to haul."));
                return;
            }
            float like = sum(taste, o.pieces);
            if (willing(npc, mine, like)) {
                for (Piece x : o.pieces) his[x.ordinal()] = Mth.clamp(his[x.ordinal()] + 0.15f, -1f, 1f);
                keep(npc, o.pieces, "you suggested it", now);
                reply(npc, like > 0.3f ? NpcWork.pick(npc, "Ooh, good call.", "I was thinking the same.")
                        : NpcWork.pick(npc, "Okay, give me a second.", "Fine. For you.", "If you insist."));
            } else {
                reply(npc, like < -0.3f ? NpcWork.pick(npc, "That one? No.", "I don't even like that one.")
                        : NpcWork.pick(npc, "Nah. I like what I've got on.", "Not today.", "Maybe later."));
            }
        } else if (act >= PUT_ON && act < PUT_ON + Piece.values().length) {
            Piece x = Piece.of(act - PUT_ON);
            if (worn.contains(x)) return;
            EnumSet<Piece> s = withOn(x);
            if (work && (x == Piece.HEELS || x == Piece.DRESS)) {
                reply(npc, NpcWork.pick(npc, "Not while I'm working.", "In " + x.label + "? I've got work to do."));
                return;
            }
            float like = taste[x.ordinal()];
            if (!willing(npc, mine, like)) {
                reply(npc, like < -0.3f ? NpcWork.pick(npc, "I don't like the " + x.label + ".", "Not the " + x.label + ". No.")
                        : NpcWork.pick(npc, "Nah, not right now.", "Maybe later.", "I'm good, thanks."));
                return;
            }
            his[x.ordinal()] = Mth.clamp(his[x.ordinal()] + 0.15f, -1f, 1f);
            keep(npc, s, "you asked for the " + x.label, now);
            boolean more = s.size() > worn.size() + 1 || !s.containsAll(worn);
            reply(npc, like > 0.3f ? NpcWork.pick(npc, "Ooh, yes. The " + x.label + ".", "Good call.")
                    : more ? NpcWork.pick(npc, "Okay. Let me swap a few things.", "Sure. Give me a second.")
                    : NpcWork.pick(npc, "Okay.", "Sure, why not.", "Alright."));
        } else if (act >= TAKE_OFF && act < TAKE_OFF + Piece.values().length) {
            Piece x = Piece.of(act - TAKE_OFF);
            if (!worn.contains(x)) return;
            EnumSet<Piece> s = EnumSet.copyOf(worn);
            s.remove(x);
            if (!covered(s) && !(b.together || b.trust >= 60 && b.affection >= 50) || !covered(s) && !willing(npc, mine, -0.5f)) {
                // down to her bra or her underwear: only with someone she trusts, and only if she feels like it
                reply(npc, NpcWork.pick(npc, "I'm not walking around in my underwear.", "Nope. That stays on.", "Not happening."));
                return;
            }
            if (work && x == Piece.BOOTS) {
                reply(npc, "I need those for work.");
                return;
            }
            float like = -taste[x.ordinal()];
            if (!willing(npc, mine, like)) {
                reply(npc, NpcWork.pick(npc, "I like the " + x.label + ". They stay.", "Nah, I'm keeping it.", "No, I'm comfortable."));
                return;
            }
            keep(npc, s, "you asked her to take off the " + x.label, now);
            reply(npc, x == Piece.HEELS ? "Gladly. My feet thank you." : NpcWork.pick(npc, "Sure.", "Okay, off it comes.", "Fine by me."));
        }
    }

    /** Into this outfit, no questions (the /zc npc outfit test command). */
    void force(NpcEntity npc, Outfit o) {
        keep(npc, EnumSet.copyOf(o.pieces), "you picked it", npc.level().getGameTime());
    }

    private void keep(NpcEntity npc, EnumSet<Piece> s, String reason, long now) {
        kept = EnumSet.copyOf(s);
        keepUntil = now + KEEP;
        change(npc, s, reason, now);
    }

    /** Synced to clients with the bond: bit 29, she's asked you what you think. */
    int bits(long now) {
        return asking(now) ? 1 << 29 : 0;
    }

    /** What she's wearing and why, for /zc npc outfit and her mind readout. */
    String line() {
        StringBuilder s = new StringBuilder();
        for (Piece p : worn) s.append(s.length() == 0 ? "" : ", ").append(p.label);
        return "Wearing her " + (s.length() == 0 ? "underwear" : s) + " (" + why + ")";
    }

    String save() {
        StringBuilder s = new StringBuilder();
        for (Piece p : worn) s.append(s.length() == 0 ? "" : ",").append(p.id);
        s.append('\t').append(why.replace('\t', ' '));
        for (int i = 0; i < taste.length; i++) {
            s.append('\t').append(String.format(Locale.ROOT, "%.2f,%.2f", taste[i], his[i]));
        }
        return "2\t" + s;
    }

    boolean load(String s) {
        if (s == null || !s.startsWith("2\t")) return false;
        try {
            String[] f = s.substring(2).split("\t", -1);
            EnumSet<Piece> w = EnumSet.noneOf(Piece.class);
            for (String id : f[0].split(",")) {
                Piece p = Piece.named(id);
                if (p != null) w.add(p);
            }
            worn = w;
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
