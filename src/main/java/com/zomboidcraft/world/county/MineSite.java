package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Placer;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * The old mine in the Black Pine hills (refs: the timbered portals at Winfield and Kootenay Lake,
 * the rock-cut adits of Eylesbarrow and Ciltwllan, the drain tunnel portal): a cutting runs into the
 * hillside to a timbered portal, boards nailed across it, a DANGER / OLD WORKINGS sign, and the
 * narrow-gauge rails running out of it onto the flat-topped spoil tip, an ore tub tipped at the end.
 * Inside, the old grey sets stand every few yards to where the roof came in; past the fall, through
 * a squeeze he propped open, a prepper put in his own sets of new sawn pine and dug his bunker into
 * the rock ({@link Bunker.Kind#FOREST} dressed {@link Bunker#inMine}).
 *
 * The clue is that new timber: the yellow props he stood behind the old portal set, seen through
 * the gaps in the boards.
 *
 * The frame: the portal's ground at the footprint's middle, +z running into the hill (the place's
 * waterSide holds its quarter turn). Nothing is levelled but the cutting and the spoil.
 */
final class MineSite extends Site {
    private final County county;
    private final int c = County.MINE_REACH;

    MineSite(Place p, County county, BlockSink out) {
        super(p, out);
        this.county = county;
    }

    /** In world blocks, the floor of the cutting in front of the boards, where a visitor stands. */
    static int[] front(Place p) {
        Placer m = new Placer(null, p.centreX(), 0, p.centreZ(), p.waterSide);
        return m.world(0, -3);
    }

    /** The ground's top block at a column of the footprint, in its own frame. */
    private int top(int x, int z) {
        return (int) Math.floor(county.terrain.height(p.x0 + x, p.z0 + z)) - p.level;
    }

    private int g(Placer m, int x, int z) {
        int[] w = m.world(x, z);
        return top(w[0], w[1]);
    }

    @Override
    void build() {
        double r = chance();
        Bunker.State state = r < 0.5 ? Bunker.State.SEALED : r < 0.75 ? Bunker.State.LOOTED : Bunker.State.BREACHED;
        Placer m = new Placer(out, c, 0, c, p.waterSide);

        // the portal stands where the hill is a block over the caps all across the adit
        int zp = 18;
        for (int z = 1; z <= 18; z++) {
            int low = Integer.MAX_VALUE;
            for (int x = -2; x <= 1; x++) {
                low = Math.min(low, g(m, x, z));
            }
            if (low >= 5) {
                zp = z;
                break;
            }
        }
        int zc = zp + 9, zn = zp + 12;   // the fall, and his new workings past it
        long seed = rng.nextLong();

        // the bunker: as deep under the hill as its cover asks, his steps going down to it
        int zs = zn + 4;
        int fall = 0, zb = 0;
        Bunker bunker = null;
        for (int tries = 0; tries < 3 && bunker == null; tries++) {
            zb = zs + fall + 7;
            Placer probe = m.turned(0, 0, zb, 0);
            int[] box = new Bunker(probe, new SplittableRandom(seed), Bunker.Kind.FOREST, state).bounds();
            if (!inside(probe, box[0] - 1, -7, box[2] + 1, box[3] + 1)) {
                return;
            }
            int lowest = Integer.MAX_VALUE;
            for (int x = box[0] - 1; x <= box[2] + 1; x += 2) {
                for (int z = -7; z <= box[3] + 1; z += 2) {
                    lowest = Math.min(lowest, g(m, x, zb + z));
                }
            }
            int want = Math.max(0, 8 - lowest);
            if (want > 12) {
                return;
            }
            if (want <= fall) {
                bunker = new Bunker(m.turned(0, -fall, zb, 0), new SplittableRandom(seed), Bunker.Kind.FOREST, state).inMine();
            } else {
                fall = want;
            }
        }
        if (bunker == null) {
            return;
        }
        Placer bp = m.turned(0, -fall, zb, 0);

        cutting(m, zp);
        spoil(m);
        adit(m, zp, zc, zn, zs, fall, zb, state);
        antechamber(bp, state);
        bunker.build();
        // the way through from the steps into the antechamber's north wall
        for (int x = -1; x <= 0; x++) {
            for (int y = 1; y <= 3; y++) {
                bp.set(x, y, -6, "air");
            }
        }
        bp.set(-2, 1, -6, "bunk_concrete");
        m.mark("bunker " + state.name().toLowerCase(Locale.ROOT) + " mine", 0, 1, zp - 2);
        bp.mark("bunker door", 0, 1, -2);
    }

    private boolean inside(Placer bp, int x0, int z0, int x1, int z1) {
        for (int[] k : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            int[] at = bp.world(k[0], k[1]);
            if (at[0] < 1 || at[1] < 1 || at[0] > w - 2 || at[1] > d - 2) {
                return false;
            }
        }
        return true;
    }

    private String spoilBlock() {
        double k = chance();
        return k < 0.55 ? "minecraft:gravel" : k < 0.85 ? "minecraft:coarse_dirt" : "minecraft:tuff";
    }

    /**
     * The cutting they dug into the hillside to reach rock for the portal: level with the adit's
     * floor, six wide, its sides the slope as they left it, from the tip's head up to the face.
     */
    private void cutting(Placer m, int zp) {
        for (int z = -4; z < zp; z++) {
            for (int x = -3; x <= 2; x++) {
                int g = g(m, x, z);
                for (int y = 1; y <= Math.max(g, 0) + 1; y++) {
                    m.set(x, y, z, "air");
                }
                for (int y = Math.min(g + 1, 0); y <= 0; y++) {
                    m.set(x, y, z, y == 0 && chance() < 0.4 ? "minecraft:coarse_dirt" : "minecraft:gravel");
                }
            }
        }
        // its sides cut back in benches, so the slope stands off the trench
        for (int d = 1; d <= 3; d++) {
            for (int x : new int[] {-3 - d, 2 + d}) {
                for (int z = -4; z < zp - 1; z++) {
                    for (int y = 1 + 2 * d; y <= g(m, x, z); y++) {
                        m.set(x, y, z, "air");
                    }
                }
            }
        }
    }

    /**
     * The spoil tip: everything they brought out, tipped off the end of the rails until it made a
     * flat-topped tongue of gravel running out from the cutting, its sides and its end at the
     * angle the gravel lies at.
     */
    private void spoil(Placer m) {
        int len = 10 + rng.nextInt(6);
        for (int z = -5; z >= -5 - len - 12; z--) {
            int t = -5 - z;
            double half = 3.5 + t * 0.25;
            for (int x = -16; x <= 16; x++) {
                double off = Math.abs(x + 0.5) - half;
                int topY = (int) Math.floor(-Math.max(0, off) - Math.max(0, t - len));
                int g = g(m, x, z);
                if (topY <= g || topY < -14) {
                    continue;
                }
                for (int y = g + 1; y <= topY; y++) {
                    m.set(x, y, z, spoilBlock());
                }
                if (topY == 0 && chance() < 0.04) {
                    m.set(x, 1, z, chance() < 0.5 ? "rubble_pile" : "debris_chunk");
                }
            }
        }
        m.set(1, 1, -2 - len, "mn_cart[axis=x]");
        tipEnd = -4 - len;
    }

    /** Where the rails end on the tip. */
    private int tipEnd = -4;

    /**
     * The adit itself: the old sets every three blocks to the fall, the fall with his squeeze through
     * it, his own sets past it, and his steps down to the bunker's antechamber.
     */
    private void adit(Placer m, int zp, int zc, int zn, int zs, int fall, int zb, Bunker.State state) {
        // the old workings, four wide and four high to the caps
        for (int z = zp - 3; z < zn + 4; z++) {
            for (int x = -2; x <= 1; x++) {
                for (int y = 1; y <= 4; y++) {
                    m.set(x, y, z, "air");
                }
                if (z >= zp) {
                    m.set(x, 0, z, chance() < 0.7 ? "minecraft:gravel" : "minecraft:coarse_dirt");
                }
            }
        }
        for (int z = zp; z < zc; z += 3) {
            set(m, z, "old");
        }
        // the clue: the props he stood in behind the old portal set, yellow through the boards
        set(m, zp + 1, "new");
        // the boards across the portal, one pried loose and pulled aside low in the middle
        for (int x = -2; x <= 1; x++) {
            for (int y = 1; y <= 4; y++) {
                boolean way = x == -1 && y <= 2 || state == Bunker.State.BREACHED && x == 0 && y <= 2;
                if (!way) {
                    m.set(x, y, zp - 1, "mn_boards[facing=north]");
                }
            }
        }
        m.set(-3, 2, zp - 1, "mn_sign[facing=north]");
        // the rails, from the fall out through the gap in the boards to the tip's end, a sleeper gone here and there
        for (int z = tipEnd; z < zc - 1; z++) {
            if (chance() < 0.1 || z < -4 && g(m, -1, z) > 0) {
                continue;
            }
            m.set(-1, 1, z, "mn_rail[axis=z]");
        }
        // a cap that came down before the roof did, lying by the wall
        m.set(-2, 1, zc - 2, "mn_cap_old[axis=z]");
        m.set(-2, 1, zc - 1, "mn_cap_old[axis=z]");

        // the fall: rock to the roof, but for the squeeze he cut along the east wall and propped
        for (int z = zc; z < zn; z++) {
            for (int x = -2; x <= 1; x++) {
                for (int y = 1; y <= 4; y++) {
                    boolean squeeze = x == 1 && y <= 2;
                    if (!squeeze) {
                        m.set(x, y, z, x == 0 && y <= 2 ? "mn_post_new" : x >= 0 && y == 3 ? "mn_cap_new[axis=x]"
                                : chance() < 0.5 ? "minecraft:stone" : chance() < 0.6 ? "minecraft:gravel" : "minecraft:tuff");
                    }
                }
            }
        }
        // its toe spilling back toward the portal
        for (int x = -2; x <= 0; x++) {
            m.set(x, 1, zc - 1, x == -2 ? "minecraft:gravel" : chance() < 0.5 ? "rubble_pile" : "minecraft:gravel");
        }
        m.set(-1, 2, zc - 1, "air");
        m.set(1, 1, zc - 1, "debris_chunk");

        // his workings: new sets every two blocks, the floor swept
        for (int z = zn; z < zs; z++) {
            for (int x = -2; x <= 1; x++) {
                m.set(x, 0, z, "bunk_floor_dirty");
            }
        }
        for (int z = zn + 1; z < zs; z += 2) {
            set(m, z, "new");
        }
        m.set(-2, 2, zn, "bunk_lamp_red" + (state == Bunker.State.SEALED ? "_lit" : "") + "[facing=east]");
        m.set(1, 1, zn + 2, "bunk_jerry_cans[facing=west]");

        // the steps down, two wide in the rock, a new cap over every other one
        for (int k = 1; k <= fall + 1; k++) {
            int z = zs + k - 1, base = -(k - 1);
            for (int x = -2; x <= 1; x++) {
                for (int y = base - 1; y <= base + 5; y++) {
                    boolean way = x >= -1 && x <= 0;
                    if (!way) {
                        continue;
                    }
                    String b = y == base - 1 ? "bunk_floor_dirty"
                            : y == base ? (k <= fall ? "ext_stairs_wood[facing=north]" : "bunk_floor_dirty")
                            : y == base + 4 && k % 2 == 1 ? "mn_cap_new[axis=x]" : y <= base + 4 ? "air" : null;
                    if (b != null) {
                        m.set(x, y, z, b);
                    }
                }
            }
        }
        // the landing at the foot, then the antechamber's wall
        for (int z = zs + fall + 1; z < zb - 6; z++) {
            for (int x = -1; x <= 0; x++) {
                m.set(x, -fall, z, "bunk_floor_dirty");
                for (int y = -fall + 1; y <= -fall + 3; y++) {
                    m.set(x, y, z, "air");
                }
            }
        }
    }

    /** One set across the adit at z: a post each side and the cap over them. */
    private void set(Placer m, int z, String age) {
        for (int y = 1; y <= 3; y++) {
            m.set(-2, y, z, "mn_post_" + age);
            m.set(1, y, z, "mn_post_" + age);
        }
        for (int x = -2; x <= 1; x++) {
            m.set(x, 4, z, "mn_cap_" + age + "[axis=x]");
        }
    }

    /** In the bunker's frame: the antechamber in front of the blast door (x -1..2, z -5..-1), his stores in it. */
    private void antechamber(Placer bp, Bunker.State state) {
        for (int x = -2; x <= 3; x++) {
            for (int z = -6; z <= -1; z++) {
                for (int y = 0; y <= 4; y++) {
                    boolean room = x >= -1 && x <= 2 && z >= -5 && y >= 1 && y <= 3;
                    String wall = rng.nextDouble() < 0.3 ? "bunk_concrete_damp" : rng.nextDouble() < 0.2 ? "bunk_concrete_crack" : "bunk_concrete";
                    bp.set(x, y, z, room ? "air" : y == 0 ? (rng.nextDouble() < 0.3 ? "bunk_floor_crack" : "bunk_floor_dirty") : wall);
                }
            }
        }
        boolean sealed = state == Bunker.State.SEALED;
        bp.set(1, 3, -5, sealed ? "bunk_lamp_red_lit[facing=south]" : "bunk_lamp_red[facing=south]");
        bp.set(1, 1, -5, "bunk_crate_ration[facing=south]");
        bp.set(2, 1, -5, "bunk_jerry_cans[facing=west]");
        bp.set(2, 1, -4, "cellar_shelf_preserves[facing=west]");
        bp.set(2, 1, -3, "cellar_shelf_cans[facing=west]");
        bp.set(-1, 1, -3, "bunk_hazmat[facing=east]");
        bp.set(-1, 2, -3, "bunk_hazmat_top[facing=east]");
        bp.set(-1, 2, -2, "bunk_radmeter[facing=east]");
        bp.set(2, 2, -2, "bunk_tally[facing=west]");
        bp.set(-1, 3, -1, "bunk_cobweb[facing=east]");
        bp.set(2, 3, -5, "bunk_cobweb[facing=south]");
        if (sealed) {
            bp.set(2, 2, -1, "bunk_scrawl_a[facing=north]");
            bp.set(-1, 1, -2, "bunk_tripwire[axis=x]");
            bp.set(0, 1, -2, "bunk_tripwire[axis=x]");
            bp.set(1, 1, -2, "bunk_tripwire[axis=x]");
        } else {
            bp.set(0, 1, -3, "bunk_bootprints[facing=north]");
            bp.set(1, 1, -1, "paper");
            bp.set(-1, 1, -2, rng.nextBoolean() ? "glass_shards" : "bunk_grit");
        }
        if (state == Bunker.State.BREACHED) {
            bp.set(0, 1, -2, "home_blood_drag[facing=north]");
            bp.set(1, 1, -4, "blood_splat");
            bp.set(2, 2, -1, "bunk_scrawl_a[facing=north]");
        }
    }
}
