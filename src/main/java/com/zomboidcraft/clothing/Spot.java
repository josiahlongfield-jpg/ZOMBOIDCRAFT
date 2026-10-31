package com.zomboidcraft.clothing;

import com.zomboidcraft.survival.Body.Region;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;

/**
 * The finer places on a body part that clothing covers, Zomboid style: a glove guards the hand but not the forearm, a
 * knee pad only the knee. A zombie's hit lands on a part (Body.zombieTarget) and then on one of its spots, weighted by
 * how often that spot is the one they reach; teeth find a forearm or a hand far more often than an elbow.
 */
public enum Spot {
    HEAD(Kind.HEAD, 0.7f, "head"), NECK(Kind.HEAD, 0.3f, "neck"),
    TORSO(Kind.TORSO, 0.85f, "torso"), GROIN(Kind.TORSO, 0.15f, "groin"),
    UPPER_ARM(Kind.ARM, 0.25f, "upper arms"), ELBOW(Kind.ARM, 0.1f, "elbows"), FOREARM(Kind.ARM, 0.35f, "forearms"),
    HAND(Kind.ARM, 0.3f, "hands"),
    THIGH(Kind.LEG, 0.35f, "thighs"), KNEE(Kind.LEG, 0.1f, "knees"), SHIN(Kind.LEG, 0.35f, "shins"), FOOT(Kind.LEG, 0.2f, "feet");

    enum Kind { HEAD, TORSO, ARM, LEG }

    public final Kind kind;
    /** The share of hits on its part that land here; the spots of a part add up to one. */
    public final float weight;
    public final String label;

    Spot(Kind kind, float weight, String label) {
        this.kind = kind;
        this.weight = weight;
        this.label = label;
    }

    /** One bit per spot on each side: twenty places in all, so holes and patches fit in an int and a long. */
    public static final int PLACES = 20;

    static Kind kind(Region region) {
        return switch (region) {
            case HEAD -> Kind.HEAD;
            case TORSO -> Kind.TORSO;
            case LEFT_ARM, RIGHT_ARM -> Kind.ARM;
            case LEFT_LEG, RIGHT_LEG -> Kind.LEG;
        };
    }

    public boolean on(Region region) {
        return kind == kind(region);
    }

    /** The spots of a part, head to toe. */
    public static List<Spot> of(Region region) {
        List<Spot> out = new ArrayList<>(4);
        for (Spot s : values()) if (s.on(region)) out.add(s);
        return out;
    }

    /** Where on the part a hit lands. */
    public static Spot roll(Region region, RandomSource random) {
        float r = random.nextFloat();
        Spot last = null;
        for (Spot s : values()) {
            if (!s.on(region)) continue;
            last = s;
            r -= s.weight;
            if (r < 0) return s;
        }
        return last;
    }

    /** This spot on that part, as a bit index 0 to 19 (head and torso spots have no side). */
    public int place(Region region) {
        return switch (kind) {
            case HEAD -> ordinal();
            case TORSO -> ordinal();
            case ARM -> 4 + (region == Region.RIGHT_ARM ? 4 : 0) + (ordinal() - UPPER_ARM.ordinal());
            case LEG -> 12 + (region == Region.RIGHT_LEG ? 4 : 0) + (ordinal() - THIGH.ordinal());
        };
    }

    /** The parts a spot is on: one for the head and torso, both sides for a limb. */
    public Region[] regions() {
        return switch (kind) {
            case HEAD -> new Region[]{Region.HEAD};
            case TORSO -> new Region[]{Region.TORSO};
            case ARM -> new Region[]{Region.LEFT_ARM, Region.RIGHT_ARM};
            case LEG -> new Region[]{Region.LEFT_LEG, Region.RIGHT_LEG};
        };
    }
}
