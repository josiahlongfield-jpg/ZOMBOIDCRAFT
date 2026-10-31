package com.zomboidcraft.zombie;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZcConfig.ZombieLore;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Project Zomboid's Zombie Lore, zombie by zombie: what each one rolled for speed, strength, toughness, sight,
 * hearing, memory and wits under the settings in {@link ZcConfig#zombieLore}. Every roll comes from the zombie's UUID
 * (each trait from its own scrambled bits), so it never changes, the server needs nothing saved, and the same
 * zombie is the same on every player's screen. Rolls are read live, so a changed setting reaches every zombie.
 */
public final class Lore {
    public enum Pace { SHAMBLER, FAST_SHAMBLER, SPRINTER }

    /** Zomboid's tiers as multipliers: strength on bites and holds, toughness on health and limbs. */
    private static final double SUPERHUMAN = 1.5, WEAK = 0.5, TOUGH = 1.5, FRAGILE = 0.6;
    /** Eagle and pinpoint go half again as far, poor ones 60% (Zomboid's Poor). */
    private static final double KEEN = 1.5, POOR = 0.6;

    /** A number from 0 up to 1 for this zombie and this trait, the same every time. */
    static double roll(Entity zombie, long salt) {
        UUID id = zombie.getUUID();
        long h = id.getMostSignificantBits() * 0x9E3779B97F4A7C15L ^ id.getLeastSignificantBits() ^ salt * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    private static ZombieLore lore() {
        return ZcConfig.get().zombieLore;
    }

    public static Pace pace(Entity zombie) {
        return switch (lore().speed) {
            case SPRINTERS -> Pace.SPRINTER;
            case FAST_SHAMBLERS -> Pace.FAST_SHAMBLER;
            case SHAMBLERS -> Pace.SHAMBLER;
            case RANDOM -> {
                // the sprinter share first, kept on the same bits the old sprinterChance used so nobody's runner
                // turns walker on the update; the rest shamble, half of them faster
                if (Math.floorMod(zombie.getUUID().getLeastSignificantBits(), 10000) < lore().sprinterPercent * 100) {
                    yield Pace.SPRINTER;
                }
                yield roll(zombie, 1) < 0.5 ? Pace.SHAMBLER : Pace.FAST_SHAMBLER;
            }
        };
    }

    /** Bites, scratches and holds: superhuman 1.5, normal 1, weak 0.5. */
    public static double strength(Entity zombie) {
        return switch (lore().strength) {
            case SUPERHUMAN -> SUPERHUMAN;
            case NORMAL -> 1;
            case WEAK -> WEAK;
            case RANDOM -> pick(roll(zombie, 2), SUPERHUMAN, 1, WEAK);
        };
    }

    /** Health, limb toughness and blows: tough 1.5, normal 1, fragile 0.6. A bullet in the head kills whatever it rolled. */
    public static double toughness(Entity zombie) {
        return switch (lore().toughness) {
            case TOUGH -> TOUGH;
            case NORMAL -> 1;
            case FRAGILE -> FRAGILE;
            case RANDOM -> pick(roll(zombie, 3), TOUGH, 1, FRAGILE);
        };
    }

    /** How far this one sees, as a share of the normal range. */
    public static double sight(Entity zombie) {
        return switch (lore().sight) {
            case EAGLE -> KEEN;
            case NORMAL -> 1;
            case POOR -> POOR;
            case RANDOM -> pick(roll(zombie, 4), KEEN, 1, POOR);
            case RANDOM_NORMAL_POOR -> roll(zombie, 4) < 0.5 ? 1 : POOR;
        } * dulled(zombie);
    }

    /** How far this one hears, as a share of each noise's reach. */
    public static double hearing(Entity zombie) {
        return switch (lore().hearing) {
            case PINPOINT -> KEEN;
            case NORMAL -> 1;
            case POOR -> POOR;
            case RANDOM -> pick(roll(zombie, 5), KEEN, 1, POOR);
            case RANDOM_NORMAL_POOR -> roll(zombie, 5) < 0.5 ? 1 : POOR;
        } * dulled(zombie);
    }

    /** How far off the spot it walks to when it hears something: pinpoint none, normal 2 blocks, poor 4. */
    public static double hearingSpread(Entity zombie) {
        double h = hearing(zombie) / dulled(zombie);
        return h >= KEEN ? 0 : h >= 1 ? 2 : 4;
    }

    public enum Recall { LONG, NORMAL, SHORT, NONE }

    public static Recall memory(Entity zombie) {
        return switch (lore().memory) {
            case LONG -> Recall.LONG;
            case NORMAL -> Recall.NORMAL;
            case SHORT -> Recall.SHORT;
            case NONE -> Recall.NONE;
            case RANDOM -> Recall.values()[(int) (roll(zombie, 6) * 4)];
            case RANDOM_NORMAL_NONE -> roll(zombie, 6) < 0.5 ? Recall.NORMAL : Recall.NONE;
        };
    }

    /** Seconds it keeps after you once you're out of sight: long 30, normal the senses setting (15), short 5, none 0. */
    public static int chaseSeconds(Entity zombie) {
        int normal = ZcConfig.get().senses.memorySeconds;
        return switch (memory(zombie)) {
            case LONG -> normal * 2;
            case NORMAL -> normal;
            case SHORT -> Math.max(1, normal / 3);
            case NONE -> 0;
        };
    }

    /** Seconds it searches where it lost you or heard you: long 90, normal 30, short 10, none 0. */
    public static int searchSeconds(Entity zombie) {
        int normal = ZcConfig.get().senses.searchSeconds;
        return switch (memory(zombie)) {
            case LONG -> normal * 3;
            case NORMAL -> normal;
            case SHORT -> Math.max(1, normal / 3);
            case NONE -> 0;
        };
    }

    /** How far out from the spot its search spirals: long 16 blocks, normal 8, short none. */
    public static double searchReach(Entity zombie) {
        return switch (memory(zombie)) {
            case LONG -> 16;
            case NORMAL -> 8;
            default -> 0;
        };
    }

    /** It opens unlocked doors instead of beating them in. */
    public static boolean opensDoors(Entity zombie) {
        return switch (lore().cognition) {
            case NAVIGATE_AND_USE_DOORS -> true;
            case RANDOM -> roll(zombie, 7) * 100 < lore().doorOpeningPercent;
            default -> false;
        };
    }

    /**
     * How far its path search reaches, as a share of vanilla's: basic navigation gives up on long ways round sooner
     * (so it goes for the door or window in front of it), navigating ones plan as far as vanilla does.
     */
    public static float pathReach(Entity zombie) {
        return switch (lore().cognition) {
            case NAVIGATE_AND_USE_DOORS, NAVIGATE -> 1f;
            case BASIC_NAVIGATION -> 0.6f;
            case RANDOM -> opensDoors(zombie) || roll(zombie, 8) < 0.5 ? 1f : 0.6f;
        };
    }

    /** The clock hours Zomboid counts as night: 19:00 to 06:00 (Minecraft time 13000 to 24000). */
    public static boolean night(Level level) {
        long t = Math.floorMod(level.getDayTime(), 24000L);
        return t >= 13000;
    }

    /** Outside its active hours (Day/Night Zombie Speed Effect) a zombie is slower and duller. */
    public static boolean dormant(Level level) {
        return switch (lore().activeOnly) {
            case BOTH -> false;
            case NIGHT -> !night(level);
            case DAY -> night(level);
        };
    }

    /** Senses while dormant: half. */
    private static double dulled(Entity zombie) {
        return dormant(zombie.level()) ? 0.5 : 1;
    }

    /** One of three tiers, a third each. */
    private static double pick(double r, double hi, double mid, double lo) {
        return r < 1 / 3.0 ? hi : r < 2 / 3.0 ? mid : lo;
    }

    /** The rolls as words, for /zc lore and the preview. */
    public static String describe(Entity zombie) {
        return pace(zombie).name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')
                + ", strength x" + strength(zombie) + ", toughness x" + toughness(zombie)
                + ", sight x" + Math.round(sight(zombie) * 100) / 100.0 + ", hearing x" + Math.round(hearing(zombie) * 100) / 100.0
                + ", memory " + memory(zombie).name().toLowerCase(java.util.Locale.ROOT)
                + (opensDoors(zombie) ? ", opens doors" : "")
                + (AttachedWeapons.of(zombie) != null ? ", " + AttachedWeapons.of(zombie) + " stuck in it" : "")
                + (dormant(zombie.level()) ? " (dormant now)" : "");
    }

    private Lore() {
    }
}
