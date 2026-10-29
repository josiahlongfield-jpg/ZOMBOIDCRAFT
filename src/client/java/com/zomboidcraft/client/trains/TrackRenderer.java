package com.zomboidcraft.client.trains;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.world.trains.Line;
import com.zomboidcraft.world.trains.Route;
import com.zomboidcraft.world.trains.Trains;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * The rails and sleepers, drawn along each line's route near the camera rather than built from
 * blocks, so they curve smoothly and always sit at the trains' gauge. The world lays the bed under
 * them.
 */
final class TrackRenderer {
    private static final double RANGE = 176, GAUGE = 0.7175;
    /** Inside this, the small parts too (plates, clips, joints, weeds); and where the subway's third rail runs across. */
    private static final double FINE = 64, THIRD = 1.3;
    private static final float SLEEPER_H = 0.12f, RAIL_H = 0.16f;

    static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(TrackRenderer::draw);
        LevelRenderEvents.COLLECT_SUBMITS.register(StationMaps::draw);
    }

    private static void draw(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        CameraRenderState cam = context.levelState().cameraRenderState;
        if (level == null || cam == null || cam.pos == null || Trains.lines(true).isEmpty()) {
            return;
        }
        Vec3 eye = cam.pos;
        for (Line line : Trains.lines(true).values()) {
            Route r = line.route;
            // the stretch of line near the camera: walk the whole loop coarsely, draw finely where it's close
            double len = r.length();
            boolean any = false;
            for (double s = 0; s < len && !any; s += 16) {
                any = near(r, s, eye, RANGE + 16);
            }
            if (!any) {
                continue;
            }
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityCutout(TrainRenderer.ATLAS),
                    (pose, out) -> {
                        for (double s0 = 0; s0 < len; s0 += 16) {
                            if (!near(r, s0, eye, RANGE + 16)) {
                                continue;
                            }
                            for (double s = s0; s < Math.min(len, s0 + 16); s += 1) {
                                piece(level, r, s, Math.min(1, len - s), eye, pose, out, line.kind == Line.Kind.SUBWAY);
                            }
                        }
                    });
        }
    }

    private static boolean near(Route r, double s, Vec3 eye, double range) {
        double dx = r.x(s) - eye.x, dz = r.z(s) - eye.z;
        return dx * dx + dz * dz < range * range;
    }

    /** One block of track from s: ballast, sleepers with their plates and clips, both rails (and in the subway the third rail). */
    private static void piece(ClientLevel level, Route r, double s, double ds, Vec3 eye, PoseStack.Pose pose, VertexConsumer out,
                              boolean subway) {
        double x0 = r.x(s), z0 = r.z(s), y0 = r.y(s), x1 = r.x(s + ds), z1 = r.z(s + ds), y1 = r.y(s + ds);
        double dx = x1 - x0, dz = z1 - z0, l = Math.hypot(dx, dz);
        if (l < 1e-6) {
            return;
        }
        double ux = dx / l, uz = dz / l, nx = -uz, nz = ux;
        // across directions at both ends, so the rails bend smoothly
        double ax0 = -(r.z(s + 0.05) - r.z(s - 0.05)), az0 = r.x(s + 0.05) - r.x(s - 0.05);
        double a0 = Math.hypot(ax0, az0);
        ax0 /= a0;
        az0 /= a0;
        double ax1 = -(r.z(s + ds + 0.05) - r.z(s + ds - 0.05)), az1 = r.x(s + ds + 0.05) - r.x(s + ds - 0.05);
        double a1 = Math.hypot(ax1, az1);
        ax1 /= a1;
        az1 /= a1;
        BlockPos at = BlockPos.containing(x0, y0 + 0.5, z0);
        int light = level.getBrightness(LightLayer.BLOCK, at) << 4 | level.getBrightness(LightLayer.SKY, at) << 20;
        double ex = eye.x, ey = eye.y, ez = eye.z;
        double ddx = x0 - ex, ddz = z0 - ez;
        boolean close = ddx * ddx + ddz * ddz < FINE * FINE;
        int block = (int) Math.floor(s);
        // street-running track (the county lays it in a street bed, flush with the road): rails set into the
        // asphalt with a dark flangeway groove inside each, no sleepers
        boolean street = BuiltInRegistries.BLOCK.getKey(level.getBlockState(BlockPos.containing(x0, y0 - 0.5, z0)).getBlock())
                .getPath().equals("rw_street_bed");
        float lift = street ? 0.02f : SLEEPER_H;
        if (street) {
            for (int side = -1; side <= 1; side += 2) {
                double g = GAUGE - 0.1;
                strip(out, pose, light, TrainMesh.CLIP, x0 + ax0 * g * side - ex, y0 + 0.012 - ey, z0 + az0 * g * side - ez,
                        x1 + ax1 * g * side - ex, y1 + 0.012 - ey, z1 + az1 * g * side - ez, ax0, az0, ax1, az1, 0.05);
            }
        } else {
            // the ballast: a crown packed up round the sleepers, then the shoulders falling away to the bed's edge
            for (int side = -1; side <= 1; side += 2) {
                slope(out, pose, light, x0 - ex, y0 - ey, z0 - ez, x1 - ex, y1 - ey, z1 - ez, ax0, az0, ax1, az1, 0, 1.55 * side, 0.07, 0.07);
                slope(out, pose, light, x0 - ex, y0 - ey, z0 - ez, x1 - ex, y1 - ey, z1 - ez, ax0, az0, ax1, az1, 1.55 * side, 2.35 * side,
                        0.07, 0.012);
            }
        }
        // sleepers, every half block: timber mostly (a few replaced in concrete), each a little off true, a few rotten
        for (double k = 0.25; k < ds && !street; k += 0.5) {
            int id = (int) Math.floor((s + k) * 2);
            int hash = id * 7919 ^ (id >>> 3) * 31337;
            boolean concrete = (hash & 15) < 2;
            double skew = ((hash >> 4 & 15) - 7.5) / 7.5 * (concrete ? 0.01 : 0.05), shift = ((hash >> 8 & 15) - 7.5) / 7.5 * 0.04;
            double sux = ux + nx * skew, suz = uz + nz * skew, sl = Math.hypot(sux, suz);
            sux /= sl;
            suz /= sl;
            double snx = -suz, snz = sux;
            double cx = x0 + ux * (k + shift), cz = z0 + uz * (k + shift), cy = y0 + (y1 - y0) * k / ds;
            boolean rotten = !concrete && (hash >> 12 & 63) == 0;
            TrainMesh.Mat m = concrete ? TrainMesh.SLEEPER_CONCRETE : TrainMesh.SLEEPER;
            double sink = rotten ? 0.07 : ((hash >> 18 & 3) * 0.008);
            orientedBox(out, pose, light, m, cx - ex, cy - ey, cz - ez, sux, suz, snx, snz, 0.13, rotten ? 1.1 : 1.25, 0, SLEEPER_H - sink);
            if (!close) {
                continue;
            }
            // under each rail: its plate, and the clips (or spikes) holding the foot down
            for (int side = -1; side <= 1; side += 2) {
                double px = cx + nx * GAUGE * side, pz = cz + nz * GAUGE * side;
                double top = SLEEPER_H - sink;
                orientedBox(out, pose, light, concrete ? TrainMesh.CLIP : TrainMesh.TIE_PLATE, px - ex, cy - ey, pz - ez, ux, uz, nx, nz,
                        0.1, 0.19, top, top + 0.022);
                for (int c = -1; c <= 1; c += 2) {
                    double qx = px + nx * 0.105 * c, qz = pz + nz * 0.105 * c;
                    orientedBox(out, pose, light, TrainMesh.CLIP, qx - ex, cy - ey, qz - ez, ux, uz, nx, nz,
                            concrete ? 0.05 : 0.025, concrete ? 0.035 : 0.025, top + 0.022, top + (concrete ? 0.055 : 0.07));
                }
            }
        }
        // weeds come up through the ballast here and there (and between street rails less)
        if (close && !subway) {
            int w = block * 92821 ^ block >>> 2;
            if ((w & 7) < (street ? 1 : 3)) {
                double k = (w >> 3 & 15) / 16.0 * ds, side = ((w >> 7 & 1) * 2 - 1) * (street ? 1.0 : (w >> 8 & 3) * 0.55);
                double cx = x0 + ux * k + nx * side, cz = z0 + uz * k + nz * side, cy = y0 + (street ? 0 : 0.06);
                double h = 0.3 + (w >> 10 & 7) * 0.06;
                weed(out, pose, light, cx - ex, cy - ey, cz - ez, h, ux, uz, nx, nz);
            }
        }
        // the two rails: foot, web and head, swept along the curve
        for (int side = -1; side <= 1; side += 2) {
            double px0 = x0 + ax0 * GAUGE * side, pz0 = z0 + az0 * GAUGE * side, px1 = x1 + ax1 * GAUGE * side, pz1 = z1 + az1 * GAUGE * side;
            double base = lift + (street ? 0 : 0.022);
            double rh = street ? RAIL_H - 0.12 : RAIL_H;
            if (!street) {
                rail(out, pose, light, TrainMesh.CLIP, px0 - ex, y0 + base - ey, pz0 - ez, px1 - ex, y1 + base - ey, pz1 - ez,
                        ax0, az0, ax1, az1, 0.15, 0, 0.022, false);
                rail(out, pose, light, TrainMesh.RAIL, px0 - ex, y0 + base - ey, pz0 - ez, px1 - ex, y1 + base - ey, pz1 - ez,
                        ax0, az0, ax1, az1, 0.04, 0.022, rh - 0.045, false);
            }
            rail(out, pose, light, TrainMesh.RAIL, px0 - ex, y0 + base - ey, pz0 - ez, px1 - ex, y1 + base - ey, pz1 - ez,
                    ax0, az0, ax1, az1, 0.08, street ? 0 : rh - 0.045, rh, true);
            // a joint every 13 blocks: fishplates bolted through the web on both sides
            if (close && !street && Math.floorMod(block, 13) == 0) {
                double jx = x0 + nx * GAUGE * side, jz = z0 + nz * GAUGE * side;
                for (int c = -1; c <= 1; c += 2) {
                    orientedBox(out, pose, light, TrainMesh.CLIP, jx + nx * 0.035 * c - ex, y0 + base - ey, jz + nz * 0.035 * c - ez,
                            ux, uz, nx, nz, 0.3, 0.012, 0.03, 0.1);
                }
            }
        }
        // the subway's third rail: on insulators every few blocks, under a cover board on brackets
        if (subway && !street) {
            double off = THIRD;
            double px0 = x0 + ax0 * off, pz0 = z0 + az0 * off, px1 = x1 + ax1 * off, pz1 = z1 + az1 * off;
            rail(out, pose, light, TrainMesh.RAIL, px0 - ex, y0 + 0.22 - ey, pz0 - ez, px1 - ex, y1 + 0.22 - ey, pz1 - ez,
                    ax0, az0, ax1, az1, 0.08, 0, 0.08, true);
            double cw = 0.17;
            rail(out, pose, light, TrainMesh.RAIL_COVER, px0 + ax0 * 0.02 - ex, y0 + 0.36 - ey, pz0 + az0 * 0.02 - ez,
                    px1 + ax1 * 0.02 - ex, y1 + 0.36 - ey, pz1 + az1 * 0.02 - ez, ax0, az0, ax1, az1, cw * 2, 0, 0.03, true);
            if (Math.floorMod(block, 3) == 0) {
                double ix = x0 + nx * off, iz = z0 + nz * off;
                orientedBox(out, pose, light, TrainMesh.INSULATOR, ix - ex, y0 - ey, iz - ez, ux, uz, nx, nz, 0.07, 0.07, 0.07, 0.22);
                orientedBox(out, pose, light, TrainMesh.CLIP, ix - ex, y0 - ey, iz - ez, ux, uz, nx, nz, 0.1, 0.12, SLEEPER_H, SLEEPER_H + 0.02);
                // the bracket: a post outside the rail up to the board
                orientedBox(out, pose, light, TrainMesh.CLIP, ix + nx * 0.21 - ex, y0 - ey, iz + nz * 0.21 - ez, ux, uz, nx, nz,
                        0.025, 0.025, SLEEPER_H, 0.36);
            }
        }
    }

    /** A flat strip (width w) lying along the track from one end to the other. */
    private static void strip(VertexConsumer out, PoseStack.Pose pose, int light, TrainMesh.Mat m, double x0, double y0, double z0,
                              double x1, double y1, double z1, double ax0, double az0, double ax1, double az1, double w) {
        float[] uv = uv(m);
        double h = w / 2;
        quad(out, pose, light, x0 - ax0 * h, y0, z0 - az0 * h, x0 + ax0 * h, y0, z0 + az0 * h, x1 + ax1 * h, y1, z1 + az1 * h,
                x1 - ax1 * h, y1, z1 - az1 * h, uv[0], uv[1], uv[2], uv[3], 0, 1, 0);
    }

    /** Ballast between two offsets across the track, from height h0 at the first to h1 at the second. */
    private static void slope(VertexConsumer out, PoseStack.Pose pose, int light, double x0, double y0, double z0, double x1, double y1,
                              double z1, double ax0, double az0, double ax1, double az1, double o0, double o1, double h0, double h1) {
        float[] uv = uv(TrainMesh.BALLAST);
        double[] a = {x0 + ax0 * o0, y0 + h0, z0 + az0 * o0}, b = {x0 + ax0 * o1, y0 + h1, z0 + az0 * o1};
        double[] c = {x1 + ax1 * o1, y1 + h1, z1 + az1 * o1}, d = {x1 + ax1 * o0, y1 + h0, z1 + az1 * o0};
        // wind it to face up whichever side it's on
        if (o1 < o0) {
            double[] t = a;
            a = d;
            d = t;
            t = b;
            b = c;
            c = t;
        }
        quad(out, pose, light, a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2], uv[0], uv[1], uv[2], uv[3], 0, 1, 0);
    }

    /** A tuft of weeds: two crossed cut-out cards. */
    private static void weed(VertexConsumer out, PoseStack.Pose pose, int light, double cx, double cy, double cz, double h,
                             double ux, double uz, double nx, double nz) {
        float[] uv = uv(TrainMesh.WEED);
        double w = h * 0.6;
        for (int k = 0; k < 2; k++) {
            double ax = k == 0 ? ux : nx, az = k == 0 ? uz : nz;
            double fx = k == 0 ? nx : -ux, fz = k == 0 ? nz : -uz;
            for (int f = -1; f <= 1; f += 2) {
                double sx = ax * w * f, sz = az * w * f;
                quad(out, pose, light, cx - sx, cy + h, cz - sz, cx - sx, cy, cz - sz, cx + sx, cy, cz + sz, cx + sx, cy + h, cz + sz,
                        f > 0 ? uv[0] : uv[2], uv[1], f > 0 ? uv[2] : uv[0], uv[3], (float) (fx * f), 0, (float) (fz * f));
            }
        }
    }

    private static float[] uv(TrainMesh.Mat m) {
        float u0 = (float) (m.cols()[0] * TrainMesh.TILE) / TrainMesh.ATLAS, v0 = (float) (m.row() * TrainMesh.TILE) / TrainMesh.ATLAS;
        return new float[] {u0, v0, u0 + (float) TrainMesh.TILE / TrainMesh.ATLAS, v0 + (float) TrainMesh.TILE / TrainMesh.ATLAS};
    }

    /** A bar from one end to the other, w wide, from h0 to h1 over the line: top (shiny if it's a running surface) and two sides. */
    private static void rail(VertexConsumer out, PoseStack.Pose pose, int light, TrainMesh.Mat m, double x0, double y0, double z0,
                             double x1, double y1, double z1, double ax0, double az0, double ax1, double az1, double width, double h0,
                             double h1, boolean shiny) {
        double w = width / 2;
        float u0 = (float) (m.cols()[0] * TrainMesh.TILE) / TrainMesh.ATLAS, u1 = u0 + (float) TrainMesh.TILE / TrainMesh.ATLAS;
        float v0 = (float) (m.row() * TrainMesh.TILE) / TrainMesh.ATLAS, vTop = v0 + 8f / TrainMesh.ATLAS, v1 = v0 + (float) TrainMesh.TILE / TrainMesh.ATLAS;
        float tv0 = shiny ? v0 : vTop, tv1 = shiny ? vTop : v1;
        double[] l0 = {x0 - ax0 * w, z0 - az0 * w}, r0 = {x0 + ax0 * w, z0 + az0 * w};
        double[] l1 = {x1 - ax1 * w, z1 - az1 * w}, r1 = {x1 + ax1 * w, z1 + az1 * w};
        quad(out, pose, light, l0[0], y0 + h1, l0[1], r0[0], y0 + h1, r0[1], r1[0], y1 + h1, r1[1], l1[0], y1 + h1, l1[1],
                u0, tv0, u1, tv1, 0, 1, 0);
        quad(out, pose, light, r0[0], y0 + h1, r0[1], r0[0], y0 + h0, r0[1], r1[0], y1 + h0, r1[1], r1[0], y1 + h1, r1[1],
                u0, vTop, u1, v1, (float) ax0, 0, (float) az0);
        quad(out, pose, light, l1[0], y1 + h1, l1[1], l1[0], y1 + h0, l1[1], l0[0], y0 + h0, l0[1], l0[0], y0 + h1, l0[1],
                u0, vTop, u1, v1, (float) -ax0, 0, (float) -az0);
    }

    /** A box centred at c (bottom at its y), long along (ax, az) by half-sizes, a flat sleeper. */
    private static void orientedBox(VertexConsumer out, PoseStack.Pose pose, int light, TrainMesh.Mat m, double cx, double cy, double cz,
                                    double ux, double uz, double nx, double nz, double halfAlong, double halfAcross, double y0, double y1) {
        float u0 = (float) (m.cols()[0] * TrainMesh.TILE) / TrainMesh.ATLAS, u1 = u0 + (float) TrainMesh.TILE / TrainMesh.ATLAS;
        float v0 = (float) (m.row() * TrainMesh.TILE) / TrainMesh.ATLAS, v1 = v0 + (float) TrainMesh.TILE / TrainMesh.ATLAS;
        double[][] c = new double[4][];
        double[][] corners = {{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};
        for (int i = 0; i < 4; i++) {
            double a = corners[i][0] * halfAlong, b = corners[i][1] * halfAcross;
            c[i] = new double[] {cx + ux * a + nx * b, cz + uz * a + nz * b};
        }
        double top = cy + y1;
        quad(out, pose, light, c[0][0], top, c[0][1], c[1][0], top, c[1][1], c[2][0], top, c[2][1], c[3][0], top, c[3][1],
                u0, v0, u1, v1, 0, 1, 0);
        for (int i = 0; i < 4; i++) {
            double[] p = c[i], q = c[(i + 1) % 4];
            double mx = (p[0] + q[0]) / 2 - cx, mz = (p[1] + q[1]) / 2 - cz, ml = Math.hypot(mx, mz);
            quad(out, pose, light, q[0], top, q[1], p[0], top, p[1], p[0], cy + y0, p[1], q[0], cy + y0, q[1],
                    u0, v0, u1, v0 + (v1 - v0) * 0.25f, (float) (mx / ml), 0, (float) (mz / ml));
        }
    }

    private static void quad(VertexConsumer out, PoseStack.Pose pose, int light, double ax, double ay, double az, double bx, double by,
                             double bz, double cx, double cy, double cz, double dx, double dy, double dz, float u0, float v0, float u1,
                             float v1, float nx, float ny, float nz) {
        vertex(out, pose, light, ax, ay, az, u0, v0, nx, ny, nz);
        vertex(out, pose, light, bx, by, bz, u0, v1, nx, ny, nz);
        vertex(out, pose, light, cx, cy, cz, u1, v1, nx, ny, nz);
        vertex(out, pose, light, dx, dy, dz, u1, v0, nx, ny, nz);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, int light, double x, double y, double z, float u, float v,
                               float nx, float ny, float nz) {
        out.addVertex(pose, (float) x, (float) y, (float) z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, nx, ny, nz);
    }

    private TrackRenderer() {
    }
}
