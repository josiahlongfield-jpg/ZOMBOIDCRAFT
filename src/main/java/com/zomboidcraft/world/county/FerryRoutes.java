package com.zomboidcraft.world.county;

import java.util.ArrayList;
import java.util.List;

/**
 * The county's ferry crossings, for the bells that call them: each route's two ends, a bell on the
 * quay and the spot the ferry puts you ashore, in the world. The only one is the prison ferry, from
 * the Waterfront's slip ({@link Waterfront#BELL}) to Ravenrock's landing ({@link Ravenrock#bell}).
 */
public final class FerryRoutes {
    /** One end of a crossing: its bell, where you step ashore (x, y, z) and the way you face there (yaw). */
    public record End(String name, int bx, int by, int bz, double x, int y, double z, float yaw) {
    }

    private FerryRoutes() {
    }

    /** The routes as pairs of ends (empty when the county isn't planned, or a place in them is missing). */
    public static List<End[]> routes(County county) {
        List<End[]> out = new ArrayList<>();
        if (!county.planned()) {
            return out;
        }
        Layout l = county.layout();
        Place wf = l.named.get("waterfront"), rr = l.named.get("ravenrock");
        if (wf == null || rr == null) {
            return out;
        }
        int[] wb = Waterfront.BELL, wl = Waterfront.LANDING;
        End mainland = new End("the Waterfront", wf.x0 + wb[0], wf.level + wb[1], wf.z0 + wb[2],
                wf.x0 + wl[0] + 0.5, wf.level + wl[1], wf.z0 + wl[2] + 0.5, 90f);
        String[] rxz = rr.variant.split("@")[1].split(",");
        int rx = Integer.parseInt(rxz[0]), rz = Integer.parseInt(rxz[1]);
        int[] rb = Ravenrock.bell(rr.level), rl = Ravenrock.landing(rr.level);
        End island = new End("Ravenrock", rx + rb[0], rr.level + rb[1], rz + rb[2],
                rx + rl[0] + 0.5, rr.level + rl[1], rz + rl[2] + 0.5, -90f);
        out.add(new End[] {mainland, island});
        return out;
    }

    /** The far end of the crossing whose bell is at (x, y, z), or null if no route's bell is there. */
    public static End across(County county, int x, int y, int z) {
        for (End[] r : routes(county)) {
            for (int i = 0; i < 2; i++) {
                End e = r[i];
                if (e.bx() == x && e.by() == y && e.bz() == z) {
                    return r[1 - i];
                }
            }
        }
        return null;
    }
}
