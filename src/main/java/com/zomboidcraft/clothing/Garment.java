package com.zomboidcraft.clothing;

import com.zomboidcraft.survival.Body;
import java.util.EnumSet;
import java.util.Set;

/**
 * What one piece of clothing is: where it's worn, the spots it covers, and what it does for them. The numbers follow
 * Project Zomboid's (Build 42) clothing scripts, scaled 0 to 1.
 *
 * @param bite       the chance it stops a zombie bite on a covered spot
 * @param scratch    the chance it stops a scratch or laceration there
 * @param bullet     the chance it stops a bullet there (what gets through a stopped one is a bruise)
 * @param insulation how warm it keeps you, 0 to 1 (a winter hat is 1)
 * @param wind       how much wind it keeps out, 0 to 1
 * @param water      how much rain it keeps off, 0 to 1
 * @param discomfort how much it chafes and weighs on you, 0 to 1; heavy armour is uncomfortable to live in
 * @param run        how much it slows your running, 0 to 1 (0.1 is a tenth slower)
 * @param swing      how much it slows your swings
 * @param armour     Minecraft armour points against everything that isn't teeth, nails or bullets
 * @param cloth      what it's made of: what patches it, what it rips into, whether it tears at all
 * @param condition  how many hits it takes before it's ruined
 */
public record Garment(String id, ClothingSlot slot, Set<Spot> covers, float bite, float scratch, float bullet,
                      float insulation, float wind, float water, float discomfort, float run, float swing, int armour,
                      Cloth cloth, int condition) {

    /** What a garment is made of. */
    public enum Cloth {
        /** Cotton, wool, nylon: rips into sheets, tears easily. */
        FABRIC,
        DENIM,
        LEATHER,
        /** Waders, gloves and raincoats: keeps water out, can't be torn into anything useful. */
        RUBBER,
        /** Helmets, plates and shells: never tears, only wears down. */
        HARD,
        /** Glasses and the like: guards nothing. */
        NONE
    }

    public boolean covers(Body.Region region) {
        for (Spot s : covers) if (s.on(region)) return true;
        return false;
    }

    /** Warmth as a whole number, 0 to 10, for the kit's totals. */
    public int warmth() {
        return Math.round(insulation * 10);
    }

    /**
     * Spots from a short code. Capitals are the big ones: H head, N neck, T torso, A full sleeves, L full trouser legs
     * (and the groin), P hands, O feet, B boots (feet and shins). Small letters are the part pieces: a short sleeves, l
     * shorts (thighs and groin), u upper arms, e elbows, f forearms, k knees, s shins, t thighs, g groin.
     */
    static Set<Spot> spots(String code) {
        Set<Spot> out = EnumSet.noneOf(Spot.class);
        for (char c : code.toCharArray()) {
            switch (c) {
                case 'H' -> out.add(Spot.HEAD);
                case 'N' -> out.add(Spot.NECK);
                case 'T' -> out.add(Spot.TORSO);
                case 'A' -> out.addAll(Set.of(Spot.UPPER_ARM, Spot.ELBOW, Spot.FOREARM));
                case 'L' -> out.addAll(Set.of(Spot.GROIN, Spot.THIGH, Spot.KNEE, Spot.SHIN));
                case 'P' -> out.add(Spot.HAND);
                case 'O' -> out.add(Spot.FOOT);
                case 'B' -> out.addAll(Set.of(Spot.FOOT, Spot.SHIN));
                case 'a', 'u' -> out.add(Spot.UPPER_ARM);
                case 'l' -> out.addAll(Set.of(Spot.GROIN, Spot.THIGH));
                case 'e' -> out.add(Spot.ELBOW);
                case 'f' -> out.add(Spot.FOREARM);
                case 'k' -> out.add(Spot.KNEE);
                case 's' -> out.add(Spot.SHIN);
                case 't' -> out.add(Spot.THIGH);
                case 'g' -> out.add(Spot.GROIN);
                default -> throw new IllegalArgumentException("Unknown spot code " + c);
            }
        }
        return java.util.Collections.unmodifiableSet(out);
    }
}
