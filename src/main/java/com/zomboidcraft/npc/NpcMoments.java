package com.zomboidcraft.npc;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.zombie.Corpse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A character's own lines, written by hand in config/zomboidcraft/npc/MODEL/lines.txt, and the moments that bring them
 * out. A character with that file says only what's in it (each moment's clip, voice/KEY.ogg, played by the client if
 * it's there); one without keeps the lines built into NpcLines.
 *
 * <pre>
 * voice: 8a7c...        (settings above the first moment: the fish.audio voice, read by the voice script)
 *
 * [SCARED]              a moment: one of the built-in ones (NpcLines.Kind) or a name of your own
 * when: night, fear > 60, mood = scared
 * wait: 120             seconds before this moment can come again
 * chance: 50            percent of the times it could, that it does
 * (whispering) Did you hear that?
 * </pre>
 *
 * A built-in moment fires where the game always said it; its when: only adds conditions. A moment of your own fires on
 * an event word in its when: (EVENTS, every built-in moment's name too), or, with none, whenever its conditions all
 * hold (checked every second). The file is read again when it changes, so lines can be added with the game running.
 */
public final class NpcMoments {
    /** Events a moment of your own can wait for, besides the built-in moments' names. */
    public static final Set<String> EVENTS = Set.of("she_kills", "you_kill", "you_hurt", "you_down", "sees_corpse");
    /** Words that are true or not at the moment she'd speak. */
    public static final Set<String> SITUATIONS = Set.of("night", "day", "raining", "dry", "inside", "outside", "alone",
            "near_you", "far_from_you", "following", "waiting", "working", "asleep", "awake", "armed", "unarmed",
            "zombies_near", "no_zombies", "in_car", "near_fire", "near_corpse", "hurt_badly", "together", "holding_hands");
    /** Numbers a condition can compare: her needs, how she feels about you, her nature (-100..100) and her health (%). */
    public static final Set<String> NUMBERS = Set.of("hunger", "thirst", "tired", "pain", "fear", "adrenaline", "lonely",
            "morale", "trust", "affection", "attraction", "brave", "kind", "talkative", "loyal", "optimist", "health");

    /** Default wait (seconds) for a moment of your own that doesn't give one. */
    private static final int OWN_WAIT = 60;
    private static final Pattern COMPARE = Pattern.compile("([a-z_]+)\\s*(>=|<=|!=|>|<|=)\\s*([a-z0-9_. ]+)");
    private static final Pattern TONE = Pattern.compile("\\(\\s*[A-Za-z][A-Za-z '-]{0,30}\\)\\s*");

    public record Line(String raw, String text, String clip) {
    }

    record Condition(String word, String op, String value) {
    }

    public static final class Moment {
        public final String name;
        final List<String> events = new ArrayList<>();
        final List<String> situations = new ArrayList<>();
        final List<Condition> compares = new ArrayList<>();
        public final List<Line> lines = new ArrayList<>();
        int waitTicks = -1;
        int chance = 100;
        final NpcLines.Kind kind;

        Moment(String name) {
            this.name = name;
            NpcLines.Kind k = null;
            for (NpcLines.Kind each : NpcLines.Kind.values()) {
                if (each.name().equalsIgnoreCase(name)) {
                    k = each;
                }
            }
            kind = k;
        }

        int waitTicks() {
            return waitTicks >= 0 ? waitTicks : kind != null ? kind.wait : OWN_WAIT * 20;
        }
    }

    /** One character's file, as last read. */
    public static final class Script {
        public final List<Moment> moments = new ArrayList<>();
        public final Map<String, String> settings = new HashMap<>();
        public final List<String> problems = new ArrayList<>();

        public Moment named(String name) {
            for (Moment m : moments) {
                if (m.name.equalsIgnoreCase(name)) {
                    return m;
                }
            }
            return null;
        }
    }

    private record Cached(Script script, long modified, long checked) {
    }

    private static final Map<String, Cached> CACHE = new HashMap<>();
    private static final Script NONE = new Script();

    private NpcMoments() {
    }

    public static Path folder(String model) {
        return Npcs.localFolder().resolve(model);
    }

    /** This character's script, or null if she has no lines.txt. Re-read when the file changes (looked at every 2 s). */
    public static Script of(String model) {
        if (model == null || model.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        Cached c = CACHE.get(model);
        if (c != null && now - c.checked < 2000) {
            return c.script == NONE ? null : c.script;
        }
        Path file = folder(model).resolve("lines.txt");
        long modified;
        try {
            modified = Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : -1;
        } catch (IOException e) {
            modified = -1;
        }
        Script script;
        if (c != null && c.modified == modified) {
            script = c.script;
        } else if (modified < 0) {
            script = NONE;
        } else {
            try {
                script = parse(Files.readAllLines(file, StandardCharsets.UTF_8));
                ZomboidCraft.LOGGER.info("Read {}: {} moments{}", file, script.moments.size(),
                        script.problems.isEmpty() ? "" : ", problems: " + String.join("; ", script.problems));
            } catch (IOException e) {
                ZomboidCraft.LOGGER.warn("Couldn't read {}: {}", file, e.toString());
                script = NONE;
            }
        }
        CACHE.put(model, new Cached(script, modified, now));
        return script == NONE ? null : script;
    }

    public static Script parse(List<String> text) {
        Script s = new Script();
        Moment m = null;
        int row = 0;
        for (String raw : text) {
            row++;
            String t = raw.strip();
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("//")) {
                continue;
            }
            if (t.startsWith("[") && t.endsWith("]")) {
                String name = t.substring(1, t.length() - 1).strip().toLowerCase(Locale.ROOT).replace(' ', '_');
                if (!name.matches("[a-z0-9_]+")) {
                    s.problems.add("line " + row + ": a moment's name can only have letters, numbers and _");
                    m = null;
                    continue;
                }
                m = s.named(name);
                if (m == null) {
                    m = new Moment(name);
                    s.moments.add(m);
                }
                continue;
            }
            int colon = t.indexOf(':');
            String key = colon > 0 ? t.substring(0, colon).strip().toLowerCase(Locale.ROOT) : "";
            if (m == null) {
                if (colon > 0) {
                    s.settings.put(key, t.substring(colon + 1).strip());
                } else {
                    s.problems.add("line " + row + ": a line before any [MOMENT] heading");
                }
                continue;
            }
            String value = colon > 0 ? t.substring(colon + 1).strip().toLowerCase(Locale.ROOT) : "";
            switch (key) {
                case "when" -> when(s, m, value, row);
                case "wait" -> m.waitTicks = number(s, value, row, "wait") * 20;
                case "chance" -> m.chance = Mth.clamp(number(s, value, row, "chance"), 0, 100);
                default -> m.lines.add(line(m.name, t));
            }
        }
        for (Moment each : s.moments) {
            if (each.kind == null && each.events.isEmpty() && each.situations.isEmpty() && each.compares.isEmpty()) {
                s.problems.add("[" + each.name + "] isn't a built-in moment and has no when:, so it never fires");
            }
        }
        return s;
    }

    private static void when(Script s, Moment m, String value, int row) {
        for (String part : value.split(",")) {
            String p = part.strip();
            if (p.isEmpty()) {
                continue;
            }
            Matcher c = COMPARE.matcher(p);
            if (c.matches()) {
                String word = c.group(1);
                if (!NUMBERS.contains(word) && !word.equals("mood") && !word.equals("job") && !word.equals("time")) {
                    s.problems.add("line " + row + ": don't know \"" + word + "\"");
                }
                m.compares.add(new Condition(word, c.group(2), c.group(3).strip()));
            } else if (SITUATIONS.contains(p)) {
                m.situations.add(p);
            } else if (EVENTS.contains(p) || isKind(p)) {
                m.events.add(p);
            } else {
                s.problems.add("line " + row + ": don't know \"" + p + "\"");
            }
        }
    }

    private static boolean isKind(String word) {
        for (NpcLines.Kind k : NpcLines.Kind.values()) {
            if (k.name().equalsIgnoreCase(word)) {
                return true;
            }
        }
        return false;
    }

    private static int number(Script s, String value, int row, String what) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            s.problems.add("line " + row + ": " + what + " needs a number");
            return 0;
        }
    }

    /** A line as written (tone tags and all, which the voice script sends), as shown, and its clip's file name. */
    public static Line line(String moment, String raw) {
        String text = TONE.matcher(raw).replaceAll("").strip();
        return new Line(raw, text.isEmpty() ? raw : text, moment + "_" + hash(raw));
    }

    /** The first 8 hex digits of the line's SHA-1 (the voice script names clips the same way). */
    static String hash(String raw) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d).substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- at play time ----

    /** Whether everything in the moment's when: (but its event words) holds for her right now. */
    static boolean holds(NpcEntity npc, Moment m) {
        for (String s : m.situations) {
            if (!situation(npc, s)) {
                return false;
            }
        }
        for (Condition c : m.compares) {
            if (!compare(npc, c)) {
                return false;
            }
        }
        return true;
    }

    /** Whether this moment fires on the event (a built-in moment always fires on its own). */
    static boolean on(Moment m, String event) {
        return m.kind != null && m.name.equals(event) || m.events.contains(event);
    }

    static boolean situation(NpcEntity npc, String word) {
        ServerLevel level = (ServerLevel) npc.level();
        Player leader = npc.leader();
        return switch (word) {
            case "night" -> night(level);
            case "day" -> !night(level);
            case "raining" -> level.isRaining();
            case "dry" -> !level.isRaining();
            case "inside" -> !level.canSeeSky(BlockPos.containing(npc.getEyePosition()).above());
            case "outside" -> level.canSeeSky(BlockPos.containing(npc.getEyePosition()).above());
            case "alone" -> level.getNearestPlayer(npc, 16) == null;
            case "near_you" -> leader != null && leader.distanceTo(npc) < 6;
            case "far_from_you" -> leader == null || leader.distanceTo(npc) > 16;
            case "following" -> npc.following;
            case "waiting" -> !npc.following;
            case "working" -> !npc.following && npc.work.job != NpcWork.Job.NONE && !npc.work.asleep;
            case "asleep" -> npc.work.asleep;
            case "awake" -> !npc.work.asleep;
            case "armed" -> NpcBrain.gun(npc) != null;
            case "unarmed" -> NpcBrain.gun(npc) == null;
            case "zombies_near" -> NpcBrain.nearestZombie(npc, 12, false) != null;
            case "no_zombies" -> NpcBrain.nearestZombie(npc, 24, false) == null;
            case "in_car" -> npc.isPassenger();
            case "near_fire" -> fire(npc);
            case "near_corpse" -> !level.getEntitiesOfClass(Corpse.class, npc.getBoundingBox().inflate(8)).isEmpty();
            case "hurt_badly" -> npc.getHealth() < npc.getMaxHealth() * 0.4f;
            case "together" -> npc.bond.together;
            case "holding_hands" -> npc.bond.holding;
            default -> false;
        };
    }

    /** The wristwatch's clock (game tick 0 is 09:00), as NpcWork keeps it: night runs 20:00 to 06:00. */
    private static boolean night(ServerLevel level) {
        int hour = hour(level);
        return hour >= 20 || hour < 6;
    }

    private static int hour(ServerLevel level) {
        return (int) ((level.getGameTime() + 3000) % 24000 / 1000);
    }

    private static boolean fire(NpcEntity npc) {
        BlockPos at = npc.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-6, -2, -6), at.offset(6, 2, 6))) {
            BlockState s = npc.level().getBlockState(p);
            if (s.is(BlockTags.CAMPFIRES) && s.getValue(CampfireBlock.LIT)) {
                return true;
            }
        }
        return false;
    }

    private static boolean compare(NpcEntity npc, Condition c) {
        if (c.word.equals("mood") || c.word.equals("job")) {
            String have = c.word.equals("mood") ? npc.mood().toLowerCase(Locale.ROOT)
                    : npc.work.job.name().toLowerCase(Locale.ROOT);
            boolean is = have.contains(c.value.replace('_', ' ')) || have.contains(c.value);
            return c.op.equals("!=") != is;
        }
        float have = c.word.equals("time") ? hour((ServerLevel) npc.level()) : number(npc, c.word);
        float want;
        try {
            want = Float.parseFloat(c.value);
        } catch (NumberFormatException e) {
            return false;
        }
        return switch (c.op) {
            case ">" -> have > want;
            case "<" -> have < want;
            case ">=" -> have >= want;
            case "<=" -> have <= want;
            case "!=" -> Math.abs(have - want) >= 0.5f;
            default -> Math.abs(have - want) < 0.5f;
        };
    }

    private static float number(NpcEntity npc, String word) {
        return switch (word) {
            case "hunger" -> npc.mind.get(NpcMind.Need.HUNGER);
            case "thirst" -> npc.mind.get(NpcMind.Need.THIRST);
            case "tired" -> npc.mind.get(NpcMind.Need.TIRED);
            case "pain" -> npc.mind.get(NpcMind.Need.PAIN);
            case "fear" -> npc.mind.get(NpcMind.Need.FEAR);
            case "adrenaline" -> npc.mind.get(NpcMind.Need.ADRENALINE);
            case "lonely" -> npc.mind.get(NpcMind.Need.LONELY);
            case "morale" -> npc.mind.get(NpcMind.Need.MORALE);
            case "trust" -> npc.bond.trust;
            case "affection" -> npc.bond.affection;
            case "attraction" -> npc.bond.attraction;
            case "brave" -> npc.mind.trait(NpcMind.Trait.BRAVE) * 100;
            case "kind" -> npc.mind.trait(NpcMind.Trait.KIND) * 100;
            case "talkative" -> npc.mind.trait(NpcMind.Trait.TALKATIVE) * 100;
            case "loyal" -> npc.mind.trait(NpcMind.Trait.LOYAL) * 100;
            case "optimist" -> npc.mind.trait(NpcMind.Trait.OPTIMIST) * 100;
            case "health" -> npc.getHealth() / npc.getMaxHealth() * 100;
            default -> 0;
        };
    }

    /** Every second: a moment of her own with no event word speaks when all its conditions hold; sees_corpse on a new one. */
    static void tick(NpcEntity npc) {
        Script s = of(npc.model());
        if (s == null || !npc.isAlive()) {
            return;
        }
        boolean corpse = !npc.level().getEntitiesOfClass(Corpse.class, npc.getBoundingBox().inflate(8)).isEmpty();
        if (corpse && !npc.lines.sawCorpse) {
            npc.lines.event(npc, "sees_corpse");
        }
        npc.lines.sawCorpse = corpse;
        for (Moment m : s.moments) {
            if (m.kind == null && m.events.isEmpty() && !m.lines.isEmpty()) {
                npc.lines.moment(npc, m, null, false);
            }
        }
    }

    /** Players hurt and killed, and zombies killed, near companions with scripts of their own. */
    static void register() {
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof Zombie) {
                for (NpcEntity npc : entity.level().getEntitiesOfClass(NpcEntity.class, entity.getBoundingBox().inflate(16))) {
                    if (source.getEntity() == npc) {
                        npc.lines.event(npc, "she_kills");
                    } else if (source.getEntity() instanceof Player p && p == npc.leader()) {
                        npc.lines.event(npc, "you_kill");
                    }
                }
            } else if (entity instanceof Player p) {
                for (NpcEntity npc : entity.level().getEntitiesOfClass(NpcEntity.class, entity.getBoundingBox().inflate(24))) {
                    if (p == npc.leader()) {
                        npc.lines.event(npc, "you_down");
                    }
                }
            }
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register(
                (entity, source, base, taken, blocked) -> {
                    if (entity instanceof Player p && taken > 0 && entity.isAlive()) {
                        for (NpcEntity npc : entity.level().getEntitiesOfClass(NpcEntity.class, entity.getBoundingBox().inflate(16))) {
                            if (p == npc.leader()) {
                                npc.lines.event(npc, "you_hurt");
                            }
                        }
                    }
                });
    }
}
