package com.zomboidcraft.client.trains;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.world.trains.Line;
import com.zomboidcraft.world.trains.Route;
import com.zomboidcraft.world.trains.TrainCar;
import com.zomboidcraft.world.trains.TrainNet;
import com.zomboidcraft.world.trains.Trains;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/**
 * A live map board on every stop's platform: a lit panel standing near the platform edge, facing the
 * people waiting. The whole line drawn north-up with every stop named, this one marked YOU ARE HERE,
 * and every train on the line at its true place, moving smoothly between the server's updates (four a
 * second), an arrow for which way it's going, amber when it's running flat out to a call.
 */
final class StationMaps {
    private static final double RANGE = 72;
    /** The board: half width and height, how high its middle stands over the platform, how far out from the track. */
    private static final double HW = 2.4, HH = 1.4, MID = 1.85, OUT = 2.6, ALONG = 6;
    /** The map's part of the board (under the title bar, over the key), and the room left round it for names. */
    private static final double MAP_TOP = HH - 0.42, MAP_BOTTOM = -HH + 0.34, MAP_HW = HW - 0.95;
    /** Text: one pixel of the 5 x 7 lettering, for names and for the title. */
    private static final double PX = 0.013, PX_TITLE = 0.02;
    /** A plain light tile of the train atlas, tinted by vertex colour. */
    private static final float U = (15 * 32 + 16) / 512f, V = (5 * 32 + 16) / 512f;
    private static final int FULL = 0xF000F0;
    private static final int PANEL = 0xFF14181C, FRAME = 0xFF2A2D30, BAR = 0xFF22272C, TEXT = 0xFFE8E8E0, DIM = 0xFF9AA0A4,
            HERE = 0xFFFFD83A, TRAIN = 0xFFE02828, CALLED = 0xFFFFA020, STOP = 0xFFF2F2EC, INK = 0xFF101214;

    private record Seen(double[] s, float[] v, boolean[] called, long at) {
    }

    private static final Map<String, Seen> POSITIONS = new HashMap<>();
    /** Each line's fitted map: outline, scale and centre, and where each stop's name goes. */
    private static final Map<String, Fit> FITS = new HashMap<>();

    static void received(TrainNet.Positions p) {
        ClientLevel level = Minecraft.getInstance().level;
        POSITIONS.put(p.line(), new Seen(p.s(), p.v(), p.called(), level == null ? 0 : level.getGameTime()));
    }

    static void clear() {
        POSITIONS.clear();
        FITS.clear();
    }

    static void draw(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        CameraRenderState cam = context.levelState().cameraRenderState;
        if (level == null || cam == null || cam.pos == null || Trains.lines(true).isEmpty()) {
            return;
        }
        Vec3 eye = cam.pos;
        long now = level.getGameTime();
        boolean blink = (now / 10) % 2 == 0;
        for (Line line : Trains.lines(true).values()) {
            List<Board> boards = new ArrayList<>();
            for (Line.Stop stop : line.stops) {
                Board b = board(line, stop);
                if (b.at.distanceToSqr(eye) < RANGE * RANGE) {
                    boards.add(b);
                }
            }
            if (boards.isEmpty()) {
                continue;
            }
            Fit fit = FITS.computeIfAbsent(line.id + "/" + line.route.length() + "/" + line.stops.size(), k -> fit(line));
            Seen seen = POSITIONS.get(line.id);
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityCutout(TrainRenderer.ATLAS),
                    (pose, out) -> {
                        for (Board b : boards) {
                            paint(b, line, fit, seen, now, blink, eye, pose, out);
                        }
                    });
        }
    }

    /** Where a stop's board stands: its middle and which way it faces (away from the track). */
    private record Board(Line.Stop stop, Vec3 at, double nx, double nz) {
    }

    private static Board board(Line line, Line.Stop stop) {
        Route r = line.route;
        double s = stop.s() + ALONG;
        double dx = r.x(s + 1) - r.x(s - 1), dz = r.z(s + 1) - r.z(s - 1), l = Math.hypot(dx, dz);
        double ux = dx / l, uz = dz / l;
        // the platform's side (Route.side): (uz, -ux) times the side
        double nx = uz * stop.side(), nz = -ux * stop.side();
        Vec3 at = new Vec3(r.x(s) + nx * OUT, r.y(s) + 1 + MID, r.z(s) + nz * OUT);
        return new Board(stop, at, nx, nz);
    }

    // ------------------------------------------------------------------ fitting the map

    /** The line fitted to the board: outline points (u, v pairs), the fit, and each stop's name and its box. */
    private record Fit(float[] outline, double scale, double cx, double cz, double mv, String[] names, double[][] labels) {
        double u(double x) {
            return (x - cx) / scale;
        }

        double v(double z) {
            return mv - (z - cz) / scale;
        }
    }

    private static Fit fit(Line line) {
        Route r = line.route;
        double len = r.length(), step = Math.max(2, len / 600);
        double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z0 = Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        List<double[]> pts = new ArrayList<>();
        for (double s = 0; s <= len; s += step) {
            pts.add(new double[] {r.x(s), r.z(s)});
        }
        pts.add(new double[] {r.x(0), r.z(0)});
        for (double[] p : pts) {
            x0 = Math.min(x0, p[0]);
            x1 = Math.max(x1, p[0]);
            z0 = Math.min(z0, p[1]);
            z1 = Math.max(z1, p[1]);
        }
        double mhh = (MAP_TOP - MAP_BOTTOM) / 2 - 0.1, mv = (MAP_TOP + MAP_BOTTOM) / 2;
        double scale = Math.max(1e-3, Math.max((x1 - x0) / (2 * MAP_HW), (z1 - z0) / (2 * mhh)));
        double cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        float[] outline = new float[pts.size() * 2];
        for (int i = 0; i < pts.size(); i++) {
            outline[i * 2] = (float) ((pts.get(i)[0] - cx) / scale);
            outline[i * 2 + 1] = (float) (mv - (pts.get(i)[1] - cz) / scale);
        }
        Fit draft = new Fit(outline, scale, cx, cz, mv, null, null);
        // each stop's name: beside its dot, on whichever side clears the line, the other names and the board's edge best
        int n = line.stops.size();
        String[] names = new String[n];
        double[][] labels = new double[n][];
        List<double[]> taken = new ArrayList<>();
        for (Line.Stop st : line.stops) {
            double u = draft.u(st.x()), v = draft.v(st.z());
            taken.add(new double[] {u - 0.05, v - 0.05, u + 0.05, v + 0.05});
        }
        double mu = 0, mvv = 0;
        for (Line.Stop st : line.stops) {
            mu += draft.u(st.x()) / n;
            mvv += draft.v(st.z()) / n;
        }
        for (int i = 0; i < n; i++) {
            Line.Stop st = line.stops.get(i);
            names[i] = shortName(st.name());
            double w = width(names[i], PX) + 0.06, h = 7 * PX + 0.05;
            double u = draft.u(st.x()), v = draft.v(st.z());
            // outward from the middle of the stops first
            double ou = u - mu, ov = v - mvv;
            double[][] tries = {
                    {u + 0.08, v - h / 2}, {u - 0.08 - w, v - h / 2}, {u - w / 2, v + 0.08}, {u - w / 2, v - 0.08 - h},
                    {u + 0.08, v + 0.04}, {u + 0.08, v - 0.04 - h}, {u - 0.08 - w, v + 0.04}, {u - 0.08 - w, v - 0.04 - h}};
            double[] best = null;
            double bestCost = Double.MAX_VALUE;
            for (double[] t : tries) {
                double bu0 = Math.max(-HW + 0.06, Math.min(HW - 0.06 - w, t[0])), bv0 = Math.max(-HH + 0.3, Math.min(HH - 0.4 - h, t[1]));
                double[] box = {bu0, bv0, bu0 + w, bv0 + h};
                double cost = 0;
                for (double[] o : taken) {
                    cost += overlap(box, o) * 40;
                }
                cost += crossings(outline, box) * 0.02;
                double bcu = (box[0] + box[2]) / 2 - u, bcv = (box[1] + box[3]) / 2 - v;
                cost -= (bcu * ou + bcv * ov) * 0.05;   // a little sooner on the outside of the loop
                cost += Math.abs(bu0 - t[0]) + Math.abs(bv0 - t[1]);   // pushed in off the edge
                if (cost < bestCost) {
                    bestCost = cost;
                    best = box;
                }
            }
            labels[i] = best;
            taken.add(best);
        }
        return new Fit(outline, scale, cx, cz, mv, names, labels);
    }

    private static double overlap(double[] a, double[] b) {
        double w = Math.min(a[2], b[2]) - Math.max(a[0], b[0]), h = Math.min(a[3], b[3]) - Math.max(a[1], b[1]);
        return w > 0 && h > 0 ? w * h : 0;
    }

    /** How many outline points fall inside a box: a name over the line is hard to read. */
    private static int crossings(float[] outline, double[] box) {
        int k = 0;
        for (int i = 0; i < outline.length; i += 2) {
            if (outline[i] > box[0] && outline[i] < box[2] && outline[i + 1] > box[1] && outline[i + 1] < box[3]) {
                k++;
            }
        }
        return k;
    }

    /** "Fort Caldwell siding" -> "FORT CALDWELL": the place, without what kind of stop it is. */
    static String shortName(String name) {
        String s = name.trim();
        for (String tail : new String[] {" station", " halt", " siding", " stop"}) {
            if (s.toLowerCase(Locale.ROOT).endsWith(tail) && s.length() > tail.length() + 2) {
                s = s.substring(0, s.length() - tail.length());
                break;
            }
        }
        return s.toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ painting

    private static void paint(Board b, Line line, Fit fit, Seen seen, long now, boolean blink, Vec3 eye, PoseStack.Pose pose, VertexConsumer out) {
        Face f = new Face(b, b.at.subtract(eye), pose, out);
        // the panel, its frame, its back and its posts
        f.box(-HW - 0.06, -HH - 0.06, HW + 0.06, HH + 0.06, 0.03, FRAME);
        f.box(-HW, -HH, HW, HH, 0.045, PANEL);
        f.back(HW + 0.06, HH + 0.06, FRAME);
        f.post(-HW + 0.4);
        f.post(HW - 0.4);
        // the hood over it against the rain, a lamp strip under the hood, the frame's depth round the sides, bolts
        f.flat(-HW - 0.14, HW + 0.14, HH + 0.1, -0.05, 0.34, FRAME);
        f.flat(-HW - 0.14, HW + 0.14, HH + 0.07, -0.05, 0.34, FRAME);
        f.box(-HW - 0.14, HH + 0.03, HW + 0.14, HH + 0.1, 0.34, FRAME);
        f.flat(-HW + 0.1, HW - 0.1, HH + 0.065, 0.12, 0.2, 0xFFF4F0DC);
        f.side(-HW - 0.06, -HH - 0.06, HH + 0.06, -0.03, 0.06, FRAME);
        f.side(HW + 0.06, -HH - 0.06, HH + 0.06, -0.03, 0.06, FRAME);
        for (double bu : new double[] {-HW - 0.03, HW + 0.03}) {
            for (double bv : new double[] {-HH - 0.03, HH + 0.03}) {
                f.box(bu - 0.018, bv - 0.018, bu + 0.018, bv + 0.018, 0.034, 0xFF6A6E72);
            }
        }
        int lineColour = line.kind == Line.Kind.SUBWAY ? 0xFFE89A2C : 0xFF3FAE5A;
        // title bar: the line's colour, its name, and LIVE
        f.box(-HW, HH - 0.3, HW, HH, 0.05, BAR);
        f.box(-HW, HH - 0.3, -HW + 0.08, HH, 0.052, lineColour);
        f.text(line.name.toUpperCase(Locale.ROOT), -HW + 0.16, HH - 0.15 - 3.5 * PX_TITLE, PX_TITLE, 0.056, TEXT);
        double lw = width("LIVE", PX);
        f.text("LIVE", HW - 0.12 - lw, HH - 0.15 - 3.5 * PX, PX, 0.056, TEXT);
        if (blink) {
            f.box(HW - 0.2 - lw - 0.07, HH - 0.185, HW - 0.2 - lw, HH - 0.115, 0.056, TRAIN);
        }
        // the line
        float[] o = fit.outline;
        for (int i = 0; i + 3 < o.length; i += 2) {
            f.seg(o[i], o[i + 1], o[i + 2], o[i + 3], 0.03, 0.05, lineColour);
        }
        // stops and their names
        for (int i = 0; i < line.stops.size(); i++) {
            Line.Stop st = line.stops.get(i);
            double u = fit.u(st.x()), v = fit.v(st.z());
            boolean here = st.name().equals(b.stop.name()) && Math.abs(st.s() - b.stop.s()) < 1;
            double[] box = fit.labels[i];
            if (here) {
                f.box(u - 0.075, v - 0.075, u + 0.075, v + 0.075, 0.058, blink ? HERE : 0xFF8A7A20);
                f.box(box[0], box[1], box[2], box[3], 0.054, HERE);
                f.text(fit.names[i], box[0] + 0.03, box[1] + 0.025, PX, 0.058, INK);
            } else {
                f.box(u - 0.045, v - 0.045, u + 0.045, v + 0.045, 0.056, INK);
                f.box(u - 0.032, v - 0.032, u + 0.032, v + 0.032, 0.058, STOP);
                f.text(fit.names[i], box[0] + 0.03, box[1] + 0.025, PX, 0.056, TEXT);
            }
        }
        // the trains, live: where the server last put them, carried on at their speed until the next reading
        if (seen != null) {
            double ticks = Math.min(10, Math.max(0, now - seen.at));
            Route r = line.route;
            double tail = (line.cars - 1) * TrainCar.PITCH + TrainCar.HALF;
            for (int i = 0; i < seen.s.length; i++) {
                double s = seen.s[i] + seen.v[i] * ticks;
                int colour = seen.called[i] ? CALLED : TRAIN;
                // its length along the line, then a bold arrow at the front pointing the way it's going
                double step = Math.max(1, tail / 6);
                for (double d = 0; d < tail + TrainCar.HALF; d += step) {
                    double a = s + TrainCar.HALF - d, c = Math.max(s - tail, a - step);
                    f.seg(fit.u(r.x(a)), fit.v(r.z(a)), fit.u(r.x(c)), fit.v(r.z(c)), 0.07, 0.062, colour);
                }
                double hu = r.x(s + 4) - r.x(s - 4), hv = -(r.z(s + 4) - r.z(s - 4)), hl = Math.hypot(hu, hv);
                if (hl > 1e-6) {
                    hu /= hl;
                    hv /= hl;
                    double u = fit.u(r.x(s)), v = fit.v(r.z(s));
                    f.box(u - 0.06, v - 0.06, u + 0.06, v + 0.06, 0.062, INK);
                    f.box(u - 0.045, v - 0.045, u + 0.045, v + 0.045, 0.064, colour);
                    f.tri(u + hu * 0.12, v + hv * 0.12, u + hv * 0.06, v - hu * 0.06, u - hv * 0.06, v + hu * 0.06, 0.064, colour);
                }
            }
        }
        // the key
        double ky = -HH + 0.12, kx = -HW + 0.15;
        f.box(kx, ky - 0.045, kx + 0.09, ky + 0.045, 0.056, HERE);
        kx = f.text("YOU ARE HERE", kx + 0.14, ky - 3.5 * PX, PX, 0.056, DIM) + 0.22;
        f.box(kx, ky - 0.045, kx + 0.09, ky + 0.045, 0.056, TRAIN);
        kx = f.text("TRAIN", kx + 0.14, ky - 3.5 * PX, PX, 0.056, DIM) + 0.22;
        f.box(kx, ky - 0.045, kx + 0.09, ky + 0.045, 0.056, CALLED);
        f.text("ON ITS WAY TO A CALL", kx + 0.14, ky - 3.5 * PX, PX, 0.056, DIM);
    }

    static double width(String s, double px) {
        return Math.max(0, s.length() * 6 - 1) * px;
    }

    /** Drawing on one board's face: map (u right, v up), lifted off the panel by lift. */
    private record Face(Board b, Vec3 o, PoseStack.Pose pose, VertexConsumer out) {
        double rx() {
            return b.nz;   // the right of someone facing the board
        }

        double rz() {
            return -b.nx;
        }

        void corner(double u, double v, double lift, int colour) {
            vertex(out, pose, o.x + rx() * u + b.nx * lift, o.y + v, o.z + rz() * u + b.nz * lift, colour, b.nx, b.nz);
        }

        void corner(double u, double v, double lift, int colour, double nx, double ny, double nz) {
            out.addVertex(pose, (float) (o.x + rx() * u + b.nx * lift), (float) (o.y + v), (float) (o.z + rz() * u + b.nz * lift))
                    .setColor(colour).setUv(U, V).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL)
                    .setNormal(pose, (float) nx, (float) ny, (float) nz);
        }

        /** A level panel at height v, from u0 to u1 and from lift l0 out to l1: both faces. */
        void flat(double u0, double u1, double v, double l0, double l1, int colour) {
            corner(u0, v, l0, colour, 0, 1, 0);
            corner(u0, v, l1, colour, 0, 1, 0);
            corner(u1, v, l1, colour, 0, 1, 0);
            corner(u1, v, l0, colour, 0, 1, 0);
            corner(u1, v, l0, colour, 0, -1, 0);
            corner(u1, v, l1, colour, 0, -1, 0);
            corner(u0, v, l1, colour, 0, -1, 0);
            corner(u0, v, l0, colour, 0, -1, 0);
        }

        /** An upright panel edge-on to the board at u, from v0 to v1 and lift l0 to l1: both faces. */
        void side(double u, double v0, double v1, double l0, double l1, int colour) {
            corner(u, v1, l0, colour, rx(), 0, rz());
            corner(u, v0, l0, colour, rx(), 0, rz());
            corner(u, v0, l1, colour, rx(), 0, rz());
            corner(u, v1, l1, colour, rx(), 0, rz());
            corner(u, v1, l1, colour, -rx(), 0, -rz());
            corner(u, v0, l1, colour, -rx(), 0, -rz());
            corner(u, v0, l0, colour, -rx(), 0, -rz());
            corner(u, v1, l0, colour, -rx(), 0, -rz());
        }

        void box(double u0, double v0, double u1, double v1, double lift, int colour) {
            corner(u0, v1, lift, colour);
            corner(u0, v0, lift, colour);
            corner(u1, v0, lift, colour);
            corner(u1, v1, lift, colour);
        }

        /** A triangle, as a quad with its last corner doubled; wound to face out. */
        void tri(double u0, double v0, double u1, double v1, double u2, double v2, double lift, int colour) {
            if ((u1 - u0) * (v2 - v0) - (u2 - u0) * (v1 - v0) < 0) {
                double tu = u1, tv = v1;
                u1 = u2;
                v1 = v2;
                u2 = tu;
                v2 = tv;
            }
            corner(u0, v0, lift, colour);
            corner(u1, v1, lift, colour);
            corner(u2, v2, lift, colour);
            corner(u2, v2, lift, colour);
        }

        /** A thick line from (u0, v0) to (u1, v1). */
        void seg(double u0, double v0, double u1, double v1, double w, double lift, int colour) {
            double du = u1 - u0, dv = v1 - v0, l = Math.hypot(du, dv);
            if (l < 1e-6) {
                return;
            }
            double pu = -dv / l * w / 2, pv = du / l * w / 2;
            double eu = du / l * w / 2, ev = dv / l * w / 2;   // a little past each end, so corners meet
            double[][] c = {{u0 - eu + pu, v0 - ev + pv}, {u0 - eu - pu, v0 - ev - pv}, {u1 + eu - pu, v1 + ev - pv}, {u1 + eu + pu, v1 + ev + pv}};
            // keep the winding facing out
            if ((c[1][0] - c[0][0]) * (c[2][1] - c[0][1]) - (c[2][0] - c[0][0]) * (c[1][1] - c[0][1]) < 0) {
                double[] t = c[1];
                c[1] = c[3];
                c[3] = t;
            }
            for (double[] p : c) {
                corner(p[0], p[1], lift, colour);
            }
        }

        /** Lettering from (u, v) at its bottom left; returns where it ends. Runs of lit pixels make one quad each. */
        double text(String s, double u, double v, double px, double lift, int colour) {
            double x = u;
            for (int i = 0; i < s.length(); i++) {
                int g = GLYPH_KEYS.indexOf(s.charAt(i));
                if (g >= 0) {
                    String rows = GLYPHS[g];
                    for (int row = 0; row < 7; row++) {
                        int col = 0;
                        while (col < 5) {
                            if (rows.charAt(row * 5 + col) != '#') {
                                col++;
                                continue;
                            }
                            int end = col;
                            while (end < 5 && rows.charAt(row * 5 + end) == '#') {
                                end++;
                            }
                            double top = v + (7 - row) * px;
                            box(x + col * px, top - px, x + end * px, top, lift, colour);
                            col = end;
                        }
                    }
                }
                x += 6 * px;
            }
            return x - px;
        }

        void back(double hu, double hv, int colour) {
            double[][] c = {{hu, hv}, {hu, -hv}, {-hu, -hv}, {-hu, hv}};
            for (double[] p : c) {
                vertex(out, pose, o.x + rx() * p[0] - b.nx * 0.03, o.y + p[1], o.z + rz() * p[0] - b.nz * 0.03, colour, -b.nx, -b.nz);
            }
        }

        /** A post under the board at u, down to the platform: two crossed panels. */
        void post(double at) {
            double top = -HH, bottom = -MID, w = 0.06;
            for (int k = 0; k < 2; k++) {
                double ax = k == 0 ? rx() : b.nx, az = k == 0 ? rz() : b.nz;
                double ox = o.x + rx() * at, oz = o.z + rz() * at;
                for (int face = -1; face <= 1; face += 2) {
                    double[][] c = {{-w, top}, {-w, bottom}, {w, bottom}, {w, top}};
                    if (face < 0) {
                        c = new double[][] {{w, top}, {w, bottom}, {-w, bottom}, {-w, top}};
                    }
                    double fnx = k == 0 ? b.nx * face : -rx() * face, fnz = k == 0 ? b.nz * face : -rz() * face;
                    for (double[] p : c) {
                        vertex(out, pose, ox + ax * p[0], o.y + p[1], oz + az * p[0], 0xFF3A3D40, fnx, fnz);
                    }
                }
            }
        }
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, double x, double y, double z, int colour, double nx, double nz) {
        out.addVertex(pose, (float) x, (float) y, (float) z).setColor(colour).setUv(U, V).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL).setNormal(pose, (float) nx, 0, (float) nz);
    }

    private static final String GLYPH_KEYS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-.\':&/";
    /** 5 x 7 glyphs, rows top down, # lit. */
    private static final String[] GLYPHS = {
            " ### #   ##   #######   ##   ##   #",
            "#### #   ##   ##### #   ##   ##### ",
            " ### #   ##    #    #    #   # ### ",
            "#### #   ##   ##   ##   ##   ##### ",
            "######    #    #### #    #    #####",
            "######    #    #### #    #    #    ",
            " ### #   ##    # ####   ##   # ####",
            "#   ##   ##   #######   ##   ##   #",
            " ###   #    #    #    #    #   ### ",
            "  ###   #    #    #    # #  #  ##  ",
            "#   ##  # # #  ##   # #  #  # #   #",
            "#    #    #    #    #    #    #####",
            "#   ### ### # ## # ##   ##   ##   #",
            "#   ###  ## # ##  ###   ##   ##   #",
            " ### #   ##   ##   ##   ##   # ### ",
            "#### #   ##   ##### #    #    #    ",
            " ### #   ##   ##   ## # ##  #  ## #",
            "#### #   ##   ##### # #  #  # #   #",
            " #####    #     ###     #    ##### ",
            "#####  #    #    #    #    #    #  ",
            "#   ##   ##   ##   ##   ##   # ### ",
            "#   ##   ##   ##   ##   # # #   #  ",
            "#   ##   ##   ## # ## # ## # # # # ",
            "#   ##   # # #   #   # # #   ##   #",
            "#   ##   # # #   #    #    #    #  ",
            "#####    #   #   #   #   #    #####",
            " ### #   ##  ### # ###  ##   # ### ",
            "  #   ##    #    #    #    #   ### ",
            " ### #   #    #   #   #   #   #####",
            "#####   #   #     #     ##   # ### ",
            "   #   ##  # # #  # #####   #    # ",
            "######    ####     #    ##   # ### ",
            "  ##  #   #    #### #   ##   # ### ",
            "#####    #   #   #   #    #    #   ",
            " ### #   ##   # ### #   ##   # ### ",
            " ### #   ##   # ####    #   #  ##  ",
            "                ###                ",
            "                                #  ",
            "  #    #                           ",
            "            #              #       ",
            " ##  #  # # #   #   # # ##  #  ## #",
            "    #    #   #   #   #   #    #    ",
    };

    private StationMaps() {
    }
}
