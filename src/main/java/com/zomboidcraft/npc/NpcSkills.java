package com.zomboidcraft.npc;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.skills.SkillBooks;
import com.zomboidcraft.survival.character.CharacterData;
import com.zomboidcraft.survival.character.Traits;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

/**
 * What a companion is good at: the player's own Zomboid skills (com.zomboidcraft.skills.Skill), 0..10 on the same XP
 * curve, kept by the skill's name so every skill the game grows has a place for her too. She gets better the way you do:
 * every shot, bandage, meal, repair, search and harvest earns experience; a skill book she's read multiplies it for that
 * volume's levels (SkillBooks: x3, x5, x8, x12, x16); and working near you she picks up a share of what you learn
 * (ZcConfig companions.watchShare). The eight her jobs use today are named below.
 * <p>
 * Where she starts is a Zomboid past: an occupation (you pick it on her page; a new companion comes with one at random)
 * and a hobby or two off her trade (Zomboid's skill traits: Angler, Cook, Gardener...), at the levels character
 * creation gives a survivor, with Zomboid's faster learning in the skills they raised (+75/100/125%). Picking another
 * occupation swaps that start and keeps everything she has learned on top of it.
 */
public final class NpcSkills {
    /** The skills her work and her fighting use now, by the shared skill each one is. */
    public enum Skill {
        AIM("Aiming", "AIMING"), FIRST_AID("First Aid", "FIRST_AID"), COOKING("Cooking", "COOKING"),
        CARPENTRY("Carpentry", "CARPENTRY"), SCAVENGING("Foraging", "FORAGING"), FARMING("Agriculture", "AGRICULTURE"),
        TAILORING("Tailoring", "TAILORING"), MECHANICS("Mechanics", "MECHANICS");

        public final String label;
        /** The shared skill's name (com.zomboidcraft.skills.Skill.name()). */
        public final String key;

        Skill(String label, String key) {
            this.label = label;
            this.key = key;
        }
    }

    public static final int MAX = 10;
    /** How near you she has to be, and see you, to learn from watching. */
    private static final double WATCH = 8;

    /** Experience by shared skill name. */
    final Map<String, Float> xp = new HashMap<>();
    /** Skill book volumes she's read, a bit per volume, by shared skill name. */
    final Map<String, Integer> read = new HashMap<>();
    /** Her occupation (CharacterData id) and her hobbies (skill trait ids). */
    String occupation = CharacterData.UNEMPLOYED;
    final List<String> hobbies = new ArrayList<>();
    /** The experience her past gave her, by skill: what a new occupation takes away before it gives its own. */
    final Map<String, Float> base = new HashMap<>();

    /** Zomboid's skill traits, the hobbies a companion can come with. */
    static final List<String> HOBBIES = List.of("angler", "baseball_player", "brawler", "cook", "first_aider", "gardener", "handy",
            "herbalist", "hiker", "hunter", "jogger", "sewer", "whittler", "artisan");
    /** Off-trade skill levels her hobbies add: at least this many, and no more than the most. */
    private static final int SPREAD_MIN = 2, SPREAD_MAX = 3;

    public String occupation() {
        return occupation;
    }

    public List<String> hobbies() {
        return Collections.unmodifiableList(hobbies);
    }

    /**
     * Gives her this occupation and a hobby spread off it: her starting levels become theirs, and what she's learned
     * since she started stays. An unknown id does nothing and returns false.
     */
    boolean occupy(String id, RandomSource random) {
        CharacterData.Occupation o = CharacterData.occupation(id);
        if (o == null) return false;
        List<String> picked = new ArrayList<>();
        java.util.Set<com.zomboidcraft.skills.Skill> taken = new java.util.HashSet<>(o.skills().keySet());
        int spread = 0;
        List<String> pool = new ArrayList<>(HOBBIES);
        Collections.shuffle(pool, new java.util.Random(random.nextLong()));
        for (String h : pool) {
            if (spread >= SPREAD_MIN) break;
            CharacterData.Trait t = CharacterData.trait(h);
            if (t == null || Traits.blocked(id, picked, h) != null) continue;
            int add = 0;
            boolean clash = false;
            for (Map.Entry<com.zomboidcraft.skills.Skill, Integer> e : t.skills().entrySet()) {
                if (taken.contains(e.getKey())) clash = true;
                add += e.getValue();
            }
            if (clash || spread + add > SPREAD_MAX) continue;
            picked.add(h);
            taken.addAll(t.skills().keySet());
            spread += add;
        }
        start(id, picked);
        return true;
    }

    /** Swaps her starting levels for this occupation and these hobbies, keeping what she's learned since. */
    void start(String id, List<String> picked) {
        Map<String, Float> next = new HashMap<>();
        for (Map.Entry<com.zomboidcraft.skills.Skill, Integer> e : Traits.bonuses(id, picked).entrySet()) {
            int level = Math.max(0, (e.getKey().passive() ? 5 : 0) + e.getValue());
            if (level > 0) next.put(e.getKey().name(), xpFor(level));
        }
        java.util.Set<String> keys = new java.util.HashSet<>(xp.keySet());
        keys.addAll(next.keySet());
        for (String k : keys) {
            float learned = Math.max(0, xp.getOrDefault(k, 0f) - base.getOrDefault(k, 0f));
            float v = next.getOrDefault(k, 0f) + learned;
            if (v > 0) xp.put(k, Math.min(v, xpFor(MAX))); else xp.remove(k);
        }
        base.clear();
        base.putAll(next);
        occupation = id;
        hobbies.clear();
        hobbies.addAll(picked);
    }

    /** Zomboid's faster learning in a skill her occupation or hobbies raised: x1.75, x2, x2.25 for one, two, three+ levels. */
    double startBoost(String key) {
        com.zomboidcraft.skills.Skill s;
        try {
            s = com.zomboidcraft.skills.Skill.valueOf(key);
        } catch (IllegalArgumentException e) {
            return 1;
        }
        if (s.passive()) return 1;
        int b = Traits.bonuses(occupation, hobbies).getOrDefault(s, 0);
        return b >= 3 ? 2.25 : b == 2 ? 2.0 : b == 1 ? 1.75 : 1;
    }

    /** "Fire Officer · Angler, Gardener" for her page and the commands. */
    public String past() {
        CharacterData.Occupation o = CharacterData.occupation(occupation);
        StringBuilder b = new StringBuilder(o == null ? "Unemployed" : o.name());
        List<String> names = new ArrayList<>();
        for (String h : hobbies) {
            CharacterData.Trait t = CharacterData.trait(h);
            if (t != null) names.add(t.name());
        }
        if (!names.isEmpty()) b.append(" · ").append(String.join(", ", names));
        return b.toString();
    }

    /** A new companion: someone with a past, an occupation at random and a hobby spread off it (you can change it on her page). */
    void roll(RandomSource random) {
        xp.clear();
        base.clear();
        List<String> all = new ArrayList<>(CharacterData.occupations().keySet());
        all.remove(CharacterData.UNEMPLOYED);
        if (all.isEmpty() || !occupy(all.get(random.nextInt(all.size())), random)) {
            // no occupation data: a little of most things, and one she's handy at
            Skill[] mine = Skill.values();
            for (Skill s : mine) set(s.key, random.nextInt(3));
            set(mine[random.nextInt(mine.length)].key, 3 + random.nextInt(2));
        }
    }

    /** Total experience for a level: the player's curve (50, 150, 300... 2750 for 10). */
    static float xpFor(int level) {
        return com.zomboidcraft.skills.Skill.xpForLevel(Math.max(0, Math.min(MAX, level)));
    }

    public int level(String key) {
        float v = xp.getOrDefault(key, 0f);
        int level = 0;
        while (level < MAX && v >= xpFor(level + 1)) level++;
        return level;
    }

    public int level(Skill s) {
        return level(s.key);
    }

    /** How far she is toward her next level (0..1). */
    public float progress(String key) {
        int l = level(key);
        if (l >= MAX) return 1f;
        return (xp.getOrDefault(key, 0f) - xpFor(l)) / (xpFor(l + 1) - xpFor(l));
    }

    public float progress(Skill s) {
        return progress(s.key);
    }

    /** Every skill she has any experience in, by name. */
    Map<String, Float> known() {
        return new TreeMap<>(xp);
    }

    /** Whether she's read this volume (1..5) of this skill's book. */
    boolean hasRead(String key, int volume) {
        return (read.getOrDefault(key, 0) & 1 << volume) != 0;
    }

    void markRead(String key, int volume) {
        read.merge(key, 1 << volume, (a, b) -> a | b);
    }

    /** What the books she's read multiply her experience by in this skill just now. */
    int boost(String key) {
        int l = level(key);
        if (l >= MAX) return 1;
        int volume = SkillBooks.volume(l);
        return hasRead(key, volume) ? SkillBooks.multiplier(volume) : 1;
    }

    /** Adds experience (scaled by the world's XP multiplier and her books); returns the new level if she reached one, else -1. */
    int practise(String key, float amount) {
        double world = ZcConfig.get().progression.xpMultiplier;
        if (world <= 0 || amount <= 0) return -1;
        int before = level(key);
        xp.merge(key, (float) (amount * world * boost(key) * startBoost(key)), Float::sum);
        int after = level(key);
        return after > before ? after : -1;
    }

    int practise(Skill s, float amount) {
        return practise(s.key, amount);
    }

    void set(String key, int level) {
        xp.put(key, xpFor(level));
    }

    void set(Skill s, int level) {
        set(s.key, level);
    }

    /**
     * You earned experience (Skills.addXp, before your books): every companion bonded to you who's close, awake and can
     * see you learns a share of it.
     */
    public static void watched(ServerPlayer player, com.zomboidcraft.skills.Skill skill, int amount) {
        double share = ZcConfig.get().companions.watchShare;
        if (share <= 0 || amount <= 0) return;
        for (NpcEntity npc : player.level().getEntitiesOfClass(NpcEntity.class, player.getBoundingBox().inflate(WATCH),
                n -> n.isAlive() && !n.work.asleep && n.bonds.contains(player.getUUID()) && n.getSensing().hasLineOfSight(player))) {
            int level = npc.skills.practise(skill.name(), (float) (amount * share));
            if (level > 0) {
                npc.lines.sayLine(npc, NpcLines.Kind.LEVEL, NpcWork.pick(npc, "I've been watching how you do that. I think I've got it.",
                        "Picked that up from you, you know.", "Watching you's taught me a thing or two."));
            }
        }
    }

    String save() {
        StringBuilder b = new StringBuilder("v2");
        for (Map.Entry<String, Float> e : new TreeMap<>(xp).entrySet()) {
            b.append(' ').append(e.getKey()).append('=').append(String.format(Locale.ROOT, "%.1f", e.getValue()));
        }
        for (Map.Entry<String, Integer> e : new TreeMap<>(read).entrySet()) {
            b.append(" read:").append(e.getKey()).append('=').append(e.getValue());
        }
        for (Map.Entry<String, Float> e : new TreeMap<>(base).entrySet()) {
            b.append(" base:").append(e.getKey()).append('=').append(String.format(Locale.ROOT, "%.1f", e.getValue()));
        }
        b.append(" occ:").append(occupation).append('=').append(String.join(",", hobbies));
        return b.toString();
    }

    boolean load(String s) {
        if (s == null || s.isBlank()) return false;
        xp.clear();
        read.clear();
        base.clear();
        hobbies.clear();
        occupation = CharacterData.UNEMPLOYED;
        try {
            String[] f = s.trim().split(" ");
            if (!f[0].equals("v2")) {
                // the first saves: six numbers in Skill order on her own curve (30 x level squared); keep her level and progress
                Skill[] old = Skill.values();
                for (int i = 0; i < Math.min(f.length, old.length); i++) {
                    float v = Float.parseFloat(f[i]);
                    int l = Math.min(MAX, (int) Math.sqrt(v / 30f));
                    float into = l >= MAX ? 0 : (v - 30f * l * l) / (30f * ((l + 1) * (l + 1) - l * l));
                    xp.put(old[i].key, xpFor(l) + into * (l >= MAX ? 0 : xpFor(l + 1) - xpFor(l)));
                }
                return true;
            }
            for (int i = 1; i < f.length; i++) {
                int eq = f[i].lastIndexOf('=');
                if (eq < 0) continue;
                if (f[i].startsWith("read:")) {
                    read.put(f[i].substring(5, eq), Integer.parseInt(f[i].substring(eq + 1)));
                } else if (f[i].startsWith("base:")) {
                    base.put(f[i].substring(5, eq), Float.parseFloat(f[i].substring(eq + 1)));
                } else if (f[i].startsWith("occ:")) {
                    occupation = f[i].substring(4, eq);
                    for (String h : f[i].substring(eq + 1).split(",")) if (!h.isBlank()) hobbies.add(h);
                } else {
                    xp.put(f[i].substring(0, eq), Float.parseFloat(f[i].substring(eq + 1)));
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
