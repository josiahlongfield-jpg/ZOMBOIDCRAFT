package com.zomboidcraft.world.county;

import java.util.ArrayList;
import java.util.List;

/**
 * The county's ferry crossings, for the bells that call them: each route's two ends, a bell on the
 * quay and the spot the ferry puts you ashore, in the world: the prison ferry, from the Waterfront's
 * slip ({@link Waterfront#BELL}) to Ravenrock's landing ({@link Ravenrock#bell}), and the Hollis
 * ferry from Seabright pier ({@link Boardwalk#BELL}) to the island's quay ({@link Hollis#BELL}).
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
        // the Hollis ferry: Seabright pier's T to the island's quay
        Place bw = l.named.get("boardwalk"), ho = l.named.get("hollis");
        if (bw != null && ho != null) {
            int bx = bw.x0 - Boardwalk.FX0, bz = bw.z0 - Boardwalk.FZ0, hx = ho.x0 - Hollis.FX0, hz = ho.z0 - Hollis.FZ0;
            int[] sb = Boardwalk.BELL, sl = Boardwalk.LANDING, hb = Hollis.BELL, hl = Hollis.LANDING;
            End pier = new End("Seabright pier", bx + sb[0], bw.level + sb[1], bz + sb[2], bx + sl[0] + 0.5, bw.level + sl[1], bz + sl[2] + 0.5, -90f);
            End quay = new End("Hollis Island", hx + hb[0], ho.level + hb[1], hz + hb[2], hx + hl[0] + 0.5, ho.level + hl[1], hz + hl[2] + 0.5, -90f);
            out.add(new End[] {pier, quay});
        }
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
