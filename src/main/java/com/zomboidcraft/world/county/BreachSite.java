package com.zomboidcraft.world.county;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the breach is in a planned county: the cracked plug in the ring under Ridgeview Labs, as
 * Ridgeview marked it while building ("breach", and "breach glow BLOCK" on each glowing crack),
 * in world coordinates. {@code world/Breach} lights the cracks up when it surges.
 */
public final class BreachSite {
    /** One glowing crack: where it is and the block it is at rest. */
    public record Glow(int x, int y, int z, String block) {
    }

    /** The plug's centre, its glowing cracks, and the ground level of the lab's campus over it. */
    public record Found(int x, int y, int z, List<Glow> glows, int ground) {
    }

    /** The breach of this county, or null (an endless county, or a lab that isn't Ridgeview). */
    public static Found find(CountyGen gen) {
        Place p = gen.county().named("lab");
        if (p == null || !p.variant.equals("ridgeview")) {
            return null;
        }
        Found centre = null;
        List<Glow> glows = new ArrayList<>();
        for (Voxels.Mark m : gen.voxels(p).marks) {
            int x = p.x0 + m.x(), y = p.level + m.y(), z = p.z0 + m.z();
            if (m.what().equals("breach")) {
                centre = new Found(x, y, z, glows, p.level);
            } else if (m.what().startsWith("breach glow ")) {
                glows.add(new Glow(x, y, z, m.what().substring("breach glow ".length())));
            }
        }
        return centre;
    }

    private BreachSite() {
    }
}
