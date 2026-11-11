package com.zomboidcraft.client.zombie.body;

import java.io.PrintWriter;
import java.util.Locale;

/** Dumps a body's bones and main-shape corners, and some deaths worked out by ZDeath, for the JS port's test. */
public final class MeshDump {
    public static void main(String[] args) throws Exception {
        ZBodyMesh mesh = ZBodyMesh.get(args.length > 1 ? args[1] : "civilian");
        StringBuilder s = new StringBuilder("{\"parent\":[");
        for (int b = 0; b < mesh.bones; b++) s.append(b > 0 ? "," : "").append(mesh.parent[b]);
        s.append("],\"rest\":").append(arr(mesh.rest)).append(",\"pts\":{");
        for (int leg = 0; leg < 2; leg++) {
            s.append(leg > 0 ? "," : "").append(leg == 0 ? "\"legs\":[" : "\"legless\":[");
            boolean first = true;
            for (int pass = 0; pass < 2; pass++) {
                for (int q : mesh.quads(pass == 0, ZDetail.SIMPLE, leg == 1)) {
                    for (int c = 0; c < 4; c++) {
                        int v = q * 12 + c * 3;
                        s.append(first ? "" : ",").append(String.format(Locale.ROOT, "[%d,%.4f,%.4f,%.4f]", mesh.bone[q], mesh.pos[v], mesh.pos[v + 1], mesh.pos[v + 2]));
                        first = false;
                    }
                }
            }
            s.append("]");
        }
        s.append("},\"cases\":[");
        Object[][] cases = DeathCases.CASES;
        ZDeath death = new ZDeath();
        for (int i = 0; i < cases.length; i++) {
            Object[] c = cases[i];
            int mode = (int) c[0], kind = (int) c[1], force = (int) c[3], part = (int) c[4], seed = (int) c[5], cuts = (int) c[6];
            float heading = (float) c[2], gait = (float) c[8];
            boolean legless = (boolean) c[7];
            ZMove.Pose from = DeathCases.from(mode, cuts, legless, gait);
            ZDeath.Fall f = new ZDeath.Fall().set(kind, heading, force, part, seed, (cuts & 80) != 0, legless, cuts);
            s.append(i > 0 ? "," : "").append(String.format(Locale.ROOT, "{\"kind\":%d,\"heading\":%.6f,\"force\":%d,\"part\":%d,\"seed\":%d,\"goneRight\":%b,\"legless\":%b,\"hidden\":%d,\"from\":%s",
                    kind, heading, force, part, seed, (cuts & 80) != 0, legless, ZBodyLayer.hiddenBones(cuts, legless), pose(from)));
            ZMove.Pose out = new ZMove.Pose();
            death.rest(out, f, mesh);
            s.append(",\"rest\":").append(pose(out)).append(",\"taus\":{");
            float[] taus = {0.15f, 0.4f, 0.7f, 1.0f, 1.45f};
            for (int k = 0; k < taus.length; k++) {
                death.pose(out, from, f, mesh, taus[k]);
                s.append(k > 0 ? "," : "").append(String.format(Locale.ROOT, "\"%.2f\":", taus[k])).append(pose(out));
            }
            s.append("}}");
        }
        s.append("]}");
        try (PrintWriter w = new PrintWriter(args[0])) { w.print(s); }
    }

    static String arr(float[] a) {
        StringBuilder s = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) s.append(i > 0 ? "," : "").append(String.format(Locale.ROOT, "%.5f", a[i]));
        return s.append("]").toString();
    }

    static String pose(ZMove.Pose p) {
        return String.format(Locale.ROOT, "{\"rot\":%s,\"pos\":[%.5f,%.5f,%.5f],\"lying\":%b,\"fallDir\":%.5f,\"tilt\":%.5f}",
                arr(p.rot), p.hx, p.hy, p.hz, p.lying, p.fallDir, p.tilt);
    }
}
