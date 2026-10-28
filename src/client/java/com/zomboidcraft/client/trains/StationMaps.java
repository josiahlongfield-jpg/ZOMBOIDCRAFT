package com.zomboidcraft.client.trains;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.world.trains.Line;
import com.zomboidcraft.world.trains.Route;
import com.zomboidcraft.world.trains.TrainNet;
import com.zomboidcraft.world.trains.Trains;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/**
 * A live map board on every stop's platform: a lit panel standing near the platform edge, facing
 * the people waiting, with the whole line drawn north-up, its stops, a blinking "you are here", and
 * every train on the line where it is right now (the server sends them each second; amber when one's
 * running flat out to a call).
 */
final class StationMaps {
    private static final double RANGE = 64;
    /** The board: half width and height, how high its middle stands over the platform, how far out from the track. */
    private static final double HW = 1.3, HH = 0.8, MID = 1.75, OUT = 2.6, ALONG = 6;
    /** A plain light tile of the train atlas, tinted by vertex colour. */
    private static final float U = (15 * 32 + 16) / 512f, V = (5 * 32 + 16) / 512f;
    private static final int FULL = 0xF000F0;

    private record Seen(double[] s, boolean[] called) {
    }

    private static final Map<String, Seen> POSITIONS = new HashMap<>();
    /** Each line's outline, fitted to the board: flat pairs of map u, v (north up). */
    private static final Map<String, float[]> OUTLINES = new HashMap<>();

    static void received(TrainNet.Positions p) {
        POSITIONS.put(p.line(), new Seen(p.s(), p.called()));
    }

    static void clear() {
        POSITIONS.clear();
        OUTLINES.clear();
    }

    static void draw(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        CameraRenderState cam = context.levelState().cameraRenderState;
        if (level == null || cam == null || cam.pos == null || Trains.lines(true).isEmpty()) {
            return;
        }
        Vec3 eye = cam.pos;
        boolean blink = (level.getGameTime() / 10) % 2 == 0;
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
            float[] outline = OUTLINES.computeIfAbsent(line.id + "/" + line.route.length(), k -> outline(line.route));
            Seen seen = POSITIONS.get(line.id);
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityCutout(TrainRenderer.ATLAS),
                    (pose, out) -> {
                        for (Board b : boards) {
                            paint(b, line, outline, seen, blink, eye, pose, out);
                        }
                    });
        }
    }

    /** Where a stop's board stands: its middle, which way it faces (away from the track), and its right. */
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

    private static float[] outline(Route r) {
        double len = r.length(), step = Math.max(2, len / 400);
        double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z0 = Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        List<double[]> pts = new ArrayList<>();
        for (double s = 0; s <= len; s += step) {
            double x = r.x(s), z = r.z(s);
            pts.add(new double[] {x, z});
            x0 = Math.min(x0, x);
            x1 = Math.max(x1, x);
            z0 = Math.min(z0, z);
            z1 = Math.max(z1, z);
        }
        pts.add(new double[] {r.x(0), r.z(0)});
        float[] out = new float[pts.size() * 2 + 5];
        // the fit: scale and centre, kept at the end for placing stops and trains
        double scale = Math.max((x1 - x0) / (2 * (HW - 0.15)), (z1 - z0) / (2 * (HH - 0.12)));
        double cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        for (int i = 0; i < pts.size(); i++) {
            out[i * 2] = (float) ((pts.get(i)[0] - cx) / scale);
            out[i * 2 + 1] = (float) (-(pts.get(i)[1] - cz) / scale);
        }
        int k = pts.size() * 2;
        out[k] = (float) scale;
        out[k + 1] = (float) cx;
        out[k + 2] = (float) cz;
        out[k + 3] = pts.size();
        return out;
    }

    private static void paint(Board b, Line line, float[] outline, Seen seen, boolean blink, Vec3 eye, PoseStack.Pose pose, VertexConsumer out) {
        int n = (int) outline[outline.length - 2];
        double scale = outline[n * 2], cx = outline[n * 2 + 1], cz = outline[n * 2 + 2];
        double rx = b.nz, rz = -b.nx;   // the right of someone facing the board
        Vec3 o = b.at.subtract(eye);
        // the panel, its frame, its back, and its post
        rect(out, pose, o, b, rx, rz, 0, 0, HW + 0.05, HH + 0.05, 0.03, 0xFF2A2D30);
        rect(out, pose, o, b, rx, rz, 0, 0, HW, HH, 0.05, 0xFF14181C);
        back(out, pose, o, b, rx, rz, HW + 0.05, HH + 0.05, 0xFF2A2D30);
        post(out, pose, o, b, rx, rz);
        int lineColour = line.kind == Line.Kind.SUBWAY ? 0xFFE89A2C : 0xFF3FAE5A;
        // the line
        for (int i = 0; i + 1 < n; i++) {
            seg(out, pose, o, b, rx, rz, outline[i * 2], outline[i * 2 + 1], outline[i * 2 + 2], outline[i * 2 + 3], 0.022, 0.055, lineColour);
        }
        // its stops, and this one
        for (Line.Stop st : line.stops) {
            double u = (st.x() - cx) / scale, v = -(st.z() - cz) / scale;
            boolean here = st.name().equals(b.stop.name()) && Math.abs(st.s() - b.stop.s()) < 1;
            if (here) {
                rect(out, pose, o, b, rx, rz, u, v, 0.07, 0.07, 0.06, blink ? 0xFFFFE04A : 0xFF8A7A20);
            } else {
                rect(out, pose, o, b, rx, rz, u, v, 0.035, 0.035, 0.06, 0xFFF2F2EC);
            }
        }
        // the trains, live
        if (seen != null) {
            for (int i = 0; i < seen.s.length; i++) {
                double s = seen.s[i];
                double u = (line.route.x(s) - cx) / scale, v = -(line.route.z(s) - cz) / scale;
                rect(out, pose, o, b, rx, rz, u, v, 0.05, 0.05, 0.065, seen.called[i] ? 0xFFFFA020 : 0xFFE02828);
            }
        }
    }

    /** A rectangle on the board's face, centred at map (u, v), lifted off the panel by lift. */
    private static void rect(VertexConsumer out, PoseStack.Pose pose, Vec3 o, Board b, double rx, double rz, double u, double v,
                             double hu, double hv, double lift, int colour) {
        double[][] c = {{u - hu, v + hv}, {u - hu, v - hv}, {u + hu, v - hv}, {u + hu, v + hv}};
        for (double[] p : c) {
            vertex(out, pose, o.x + rx * p[0] + b.nx * lift, o.y + p[1], o.z + rz * p[0] + b.nz * lift, colour, b.nx, b.nz);
        }
    }

    /** A thick line on the board's face from (u0, v0) to (u1, v1). */
    private static void seg(VertexConsumer out, PoseStack.Pose pose, Vec3 o, Board b, double rx, double rz, double u0, double v0,
                            double u1, double v1, double w, double lift, int colour) {
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
            vertex(out, pose, o.x + rx * p[0] + b.nx * lift, o.y + p[1], o.z + rz * p[0] + b.nz * lift, colour, b.nx, b.nz);
        }
    }

    private static void back(VertexConsumer out, PoseStack.Pose pose, Vec3 o, Board b, double rx, double rz, double hu, double hv, int colour) {
        double[][] c = {{hu, hv}, {hu, -hv}, {-hu, -hv}, {-hu, hv}};
        for (double[] p : c) {
            vertex(out, pose, o.x + rx * p[0] - b.nx * 0.03, o.y + p[1], o.z + rz * p[0] - b.nz * 0.03, colour, -b.nx, -b.nz);
        }
    }

    /** A post under the board, down to the platform: two crossed panels. */
    private static void post(VertexConsumer out, PoseStack.Pose pose, Vec3 o, Board b, double rx, double rz) {
        double top = -HH, bottom = -MID, w = 0.06;
        for (int k = 0; k < 2; k++) {
            double ax = k == 0 ? rx : b.nx, az = k == 0 ? rz : b.nz;
            for (int face = -1; face <= 1; face += 2) {
                double[][] c = {{-w, top}, {-w, bottom}, {w, bottom}, {w, top}};
                if (face < 0) {
                    c = new double[][] {{w, top}, {w, bottom}, {-w, bottom}, {-w, top}};
                }
                double fnx = k == 0 ? b.nx * face : -rx * face, fnz = k == 0 ? b.nz * face : -rz * face;
                for (double[] p : c) {
                    vertex(out, pose, o.x + ax * p[0], o.y + p[1], o.z + az * p[0], 0xFF3A3D40, fnx, fnz);
                }
            }
        }
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, double x, double y, double z, int colour, double nx, double nz) {
        out.addVertex(pose, (float) x, (float) y, (float) z).setColor(colour).setUv(U, V).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL).setNormal(pose, (float) nx, 0, (float) nz);
    }

    private StationMaps() {
    }
}
