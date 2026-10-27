package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Placer;

/**
 * The car park behind the fence of a country halt the plan's roads pass well clear of (Layout puts
 * one wherever a stop has no road near it, and links() runs a county road in to it): a gravel drive
 * in from the road, a row of stalls nosed up to the platform's fence with their wheel stops, a
 * cinder path along the fence to the platform's two gates, a lamp, a bin, the cars of the people who
 * took the last train out and never came back for them.
 *
 * <p>Drawn in its lot's frame: x along the platform (0 .. {@link #L} - 1, the stop's middle halfway,
 * the platform's gates at x 3..4 and L - 5..L - 4), z = 0 against the fence, growing away from the
 * line; y = 0 the ground, y = 1 standing on it (the platform is a block higher).
 */
final class HaltSite extends Site {
    private static final int L = Layout.HALT_LEN, D = Layout.HALT_DEPTH;
    /**
     * The stalls' middles along the row, their wheel stops at STOP_Z; the drive behind them at
     * LANE_Z, end gate to end gate (the gates are halfway back), the road in at one of them or the back.
     */
    private static final int[] STALLS = {9, 13, 17, 21, 25};
    private static final int STOP_Z = 1, LANE_Z = D / 2;

    private final County county;
    private Placer q;
    private Kit kit;
    private int facing;

    HaltSite(Place p, County county, BlockSink out) {
        super(p, out);
        this.county = county;
    }

    @Override
    void build() {
        facing = p.waterSide;
        q = lot(0, 0, L, D, facing);
        kit = new Kit(out, rng, w, d);
        drive();
        stalls();
        kit.woods(0.25);
        kit.overgrow(1.0);
        kit.tidyPlants();
    }

    /** The place's own column for (lx, lz) of the lot's frame (as Placer.forLot turns it). */
    private int[] at(double lx, double lz) {
        return switch (facing & 3) {
            case 0 -> new int[] {(int) Math.floor(lx), (int) Math.floor(lz)};
            case 1 -> new int[] {(int) Math.floor(D - 1 - lz), (int) Math.floor(lx)};
            case 2 -> new int[] {(int) Math.floor(L - 1 - lx), (int) Math.floor(D - 1 - lz)};
            default -> new int[] {(int) Math.floor(lz), (int) Math.floor(L - 1 - lx)};
        };
    }

    /** Lays ground (gravel, cinders, bare dirt in patches) on the lot's free columns in the box, marked as kind. */
    private void ground(int lx0, int lz0, int lx1, int lz1, byte kind, double cinders) {
        for (int x = lx0; x <= lx1; x++) {
            for (int z = lz0; z <= lz1; z++) {
                int[] c = at(x, z);
                if (!inside(c[0], c[1]) || kit.use(c[0], c[1]) != Kit.FREE) {
                    continue;
                }
                double r = (Noise.hash(p.seed, Math.floorDiv(x, 3), Math.floorDiv(z, 3)) & 0xFFFF) / 65536.0 * 0.8 + chance() * 0.2;
                out.set(c[0], 0, c[1], r < 1 - cinders ? "minecraft:gravel" : r < 0.9 ? "rw_cinder" : "minecraft:coarse_dirt");
                kit.claim(c[0], c[1], c[0], c[1], kind);
            }
        }
    }

    /** The drive through from end gate to end gate behind the stalls, in from the back gate; the path along the fence to the platform's gates. */
    private void drive() {
        int back = (facing + 2) & 3;
        double[] ea = kit.beyond((facing + 1) & 3), eb = kit.beyond((facing + 3) & 3);
        kit.lane(Kit.Style.GRAVEL, ea[0], ea[1], eb[0], eb[1]);
        if (roadAt(back)) {
            double[] g = kit.beyond(back);
            int[] mid = at(L / 2.0, LANE_Z);
            kit.lane(Kit.Style.GRAVEL, g[0], g[1], mid[0] + 0.5, mid[1] + 0.5);
        }
        kit.pave();
        // the path along the fence, cinders trodden into the dirt, out to both gates
        ground(1, 0, L - 2, 0, Kit.WALK, 0.85);
        // the stalls' ground
        ground(STALLS[0] - 3, 1, STALLS[STALLS.length - 1] + 2, LANE_Z, Kit.LOT, 0.3);
    }

    /** Wheel stops at the head of each stall, the cars left in them, the lamp and the bin by the path. */
    private void stalls() {
        int[] toLine = dir(0, -1);
        float nose = Kit.yaw(toLine[0], toLine[1]);
        for (int sx : STALLS) {
            // a wheel stop two long at the stall's head, now and then one knocked askew or gone
            for (int x = sx - 1; x <= sx; x++) {
                double r = chance();
                if (r > 0.12) {
                    q.set(x, 1, STOP_Z, r < 0.2 ? "dn_wheelstop[axis=z]" : "dn_wheelstop[axis=x]");
                }
            }
            if (chance() < 0.55) {
                // nosed up to the stop, or backed in
                int[] c = at(sx, STOP_Z + 2.5);
                boolean backed = chance() < 0.25;
                kit.car(c[0] + 0.5, c[1] + 0.5, backed ? nose + 180 : nose, 4, 5.5);
            }
        }
        // the lamp by the path, its head out over the stalls; a bin, likely knocked over
        int lx = L / 2;
        q.set(lx, 1, 1, "rw_lamp_post");
        q.set(lx, 2, 1, "rw_lamp_post");
        q.set(lx, 3, 1, "rw_lamp_post");
        q.set(lx, 4, 1, "rw_lamp_head[facing=south]");
        q.set(lx + 2, 1, 1, chance() < 0.5 ? "ext_trash_can_tipped[facing=" + Site.FACINGS[rng.nextInt(4)] + "]" : "ext_trash_can");
        if (chance() < 0.6) {
            q.set(lx + 3, 1, 1, chance() < 0.5 ? "trash_bag[facing=" + Site.FACINGS[rng.nextInt(4)] + "]" : "paper");
        }
    }

    /** True if the county's road in to the halt ends at the gate on that side of the place. */
    private boolean roadAt(int side) {
        if (county == null) {
            return false;
        }
        int[] g = p.gate(side);
        for (Road r : county.roadsNear(g[0] - 16, g[1] - 16, g[0] + 16, g[1] + 16)) {
            for (int e : new int[] {0, r.xs.length - 1}) {
                if (Math.hypot(r.xs[e] - g[0], r.zs[e] - g[1]) < 4) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A direction of the lot's frame as the place's own (dx, dz). */
    private int[] dir(int dx, int dz) {
        int[] a = at(10, 5), b = at(10 + dx, 5 + dz);
        return new int[] {b[0] - a[0], b[1] - a[1]};
    }
}
