package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import com.zomboidcraft.world.city.Signs;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * The county's signs (tools/city/signs.py draws them, one block per slice): fascias over shop
 * windows, boards on posts by the road, lettering bolted to the courthouse, and the neon blades
 * hung off the corner of a cafe or a hotel.
 *
 * Like {@link com.zomboidcraft.world.city.Signs}, a sign on a front (local z = 0, facing north)
 * hangs at z = -1 facing south, and slice 0, the left end as you read it, is the high-x end.
 */
final class Boards {
    /** Every fascia's width in blocks, from signs.py's FASCIA list. */
    static final Map<String, Integer> WIDTH = Map.ofEntries(
            Map.entry("medline", 5), Map.entry("stopnsave", 4), Map.entry("suds", 5), Map.entry("patriot", 4),
            Map.entry("cash", 4), Map.entry("joes", 5), Map.entry("luckypawn", 5), Map.entry("tonys", 4),
            Map.entry("thrift", 6), Map.entry("riverside", 5), Map.entry("kessler", 5), Map.entry("quarantine", 5),
            Map.entry("restricted", 7), Map.entry("armydepot", 6), Map.entry("motel", 5), Map.entry("fuel", 5),
            Map.entry("route9", 4), Map.entry("feed", 5), Map.entry("school", 7), Map.entry("church", 5),
            Map.entry("terminal", 7), Map.entry("bait", 4), Map.entry("grain", 5), Map.entry("storage", 5),
            Map.entry("shadyacres", 6), Map.entry("sheriff", 4), Map.entry("grocery", 4), Map.entry("bank", 6),
            Map.entry("postoffice", 5), Map.entry("courthouse", 7), Map.entry("library", 5),
            Map.entry("firedept", 4), Map.entry("rialto", 3), Map.entry("hotel", 5), Map.entry("tavern", 5),
            Map.entry("barber", 4), Map.entry("drugstore", 5), Map.entry("cafe", 4), Map.entry("dime", 4),
            Map.entry("furniture", 5), Map.entry("autoparts", 4), Map.entry("townhall", 4), Map.entry("mill", 6),
            Map.entry("company", 5), Map.entry("elementary", 7), Map.entry("methodist", 6), Map.entry("stmary", 5),
            Map.entry("generalstore", 5), Map.entry("antiques", 3), Map.entry("realty", 4), Map.entry("vfw", 4),
            Map.entry("hollowcreek", 6), Map.entry("dairy", 6), Map.entry("ranch", 4), Map.entry("orchard", 5),
            Map.entry("apples", 5), Map.entry("produce", 5), Map.entry("eggs", 4), Map.entry("trespass", 4),
            Map.entry("seedcorn", 5),
            Map.entry("emergency", 5), Map.entry("mercy", 7), Map.entry("police", 4), Map.entry("precinct", 6),
            Map.entry("subway", 3), Map.entry("central", 5), Map.entry("parking", 5), Map.entry("aid", 5),
            Map.entry("hardhat", 6));

    /** A fascia centred over span blocks from x0, at height y on the front's z; false when it won't fit. */
    static boolean fascia(Placer p, SplittableRandom rng, String id, int x0, int y, int z, int span) {
        Integer k = WIDTH.get(id);
        if (k == null || k > span) {
            return false;
        }
        int start = x0 + (span - k) / 2;
        int fallen = rng.nextDouble() < 0.1 ? rng.nextInt(k) : -1;
        for (int i = 0; i < k; i++) {
            if (i != fallen) {
                p.set(start + k - 1 - i, y, z, "sign_" + id + "_" + i + "[facing=south]");
            }
        }
        return true;
    }

    /** A sign standing on two posts (a motel's, a church's), its face toward the front, left end at high x. */
    static void board(Placer p, SplittableRandom rng, String id, int x0, int y, int z) {
        int k = WIDTH.getOrDefault(id, 4);
        for (int i = 0; i < k; i++) {
            p.set(x0 + k - 1 - i, y, z, "sign_" + id + "_" + i + "[facing=south]");
        }
        for (int h = 0; h < y; h++) {
            p.set(x0, h, z + 1, "pole_wood");
            p.set(x0 + k - 1, h, z + 1, "pole_wood");
        }
    }

    /** Letters of a neon blade, top to bottom from y, hung at (x, z) off a front; lit (a lamp box under it) or dead. */
    static void blade(Placer p, SplittableRandom rng, String id, int letters, int x, int top, int z, boolean lit) {
        for (int i = 0; i < letters; i++) {
            boolean on = lit && rng.nextDouble() > 0.15;
            p.set(x, top - i, z, "blade_" + id + "_" + i + (on ? "" : "_off") + "[facing=south]");
        }
        if (lit) {
            Signs.glow(p, id, x, top - letters, z);
        }
    }

    private Boards() {
    }
}
