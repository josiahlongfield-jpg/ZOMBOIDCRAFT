package com.zomboidcraft.clothing;

import com.zomboidcraft.survival.Body;
import java.util.EnumSet;
import java.util.Set;

/**
 * What one piece of clothing is: where it's worn, the parts of the body it covers, and what it does for them.
 *
 * @param bite    the share of zombie bites it stops on a covered part, 0 to 1
 * @param scratch the share of scratches and lacerations it stops there
 * @param warmth  how warm it keeps you, roughly 0 (a tank top) to 10 (a turnout coat)
 * @param water   how much rain it keeps off, 0 to 1
 * @param armour  armour points, as Minecraft counts them, against everything else
 */
public record Garment(String id, ClothingSlot slot, Set<Body.Region> covers, float bite, float scratch, int warmth,
                      float water, int armour) {

    public boolean covers(Body.Region region) {
        return covers.contains(region);
    }

    /** Parts from a short code: H head, T torso, A both arms, L both legs. */
    static Set<Body.Region> parts(String code) {
        Set<Body.Region> out = EnumSet.noneOf(Body.Region.class);
        for (char c : code.toCharArray()) {
            switch (c) {
                case 'H' -> out.add(Body.Region.HEAD);
                case 'T' -> out.add(Body.Region.TORSO);
                case 'A' -> {
                    out.add(Body.Region.LEFT_ARM);
                    out.add(Body.Region.RIGHT_ARM);
                }
                case 'L' -> {
                    out.add(Body.Region.LEFT_LEG);
                    out.add(Body.Region.RIGHT_LEG);
                }
                default -> throw new IllegalArgumentException("Unknown body part code " + c);
            }
        }
        return Set.copyOf(out);
    }
}
