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
    private static final float SLEEPER_H = 0.12f, RAIL_H = 0.16f, RAIL_W = 0.1f;

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
                                piece(level, r, s, Math.min(1, len - s), eye, pose, out);
                            }
                        }
                    });
        }
    }

    private static boolean near(Route r, double s, Vec3 eye, double range) {
        double dx = r.x(s) - eye.x, dz = r.z(s) - eye.z;
        return dx * dx + dz * dz < range * range;
    }

    /** One block of track from s: two sleepers and both rails, the rails as boxes swept along the curve. */
    private static void piece(ClientLevel level, Route r, double s, double ds, Vec3 eye, PoseStack.Pose pose, VertexConsumer out) {
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
        float ex = (float) eye.x, ey = (float) eye.y, ez = (float) eye.z;
        // street-running track (the county lays it in a street bed, flush with the road): rails set into the
        // asphalt, no sleepers
        boolean street = BuiltInRegistries.BLOCK.getKey(level.getBlockState(BlockPos.containing(x0, y0 - 0.5, z0)).getBlock())
                .getPath().equals("rw_street_bed");
        float lift = street ? 0.02f : SLEEPER_H;
        // sleepers, every half block
        for (double k = 0.25; k < ds && !street; k += 0.5) {
            double cx = x0 + ux * k, cz = z0 + uz * k, cy = y0 + (y1 - y0) * k / ds;
            boolean concrete = ((int) Math.floor((s + k) * 2) * 7919 & 15) < 2;
            TrainMesh.Mat m = concrete ? TrainMesh.SLEEPER_CONCRETE : TrainMesh.SLEEPER;
            orientedBox(out, pose, light, m, cx - ex, cy - ey, cz - ez, ux, uz, nx, nz, 0.13, 1.25, 0, SLEEPER_H);
        }
        // the two rails
        for (int side = -1; side <= 1; side += 2) {
            double px0 = x0 + ax0 * GAUGE * side, pz0 = z0 + az0 * GAUGE * side, px1 = x1 + ax1 * GAUGE * side, pz1 = z1 + az1 * GAUGE * side;
            rail(out, pose, light, px0 - ex, y0 + lift - ey, pz0 - ez, px1 - ex, y1 + lift - ey, pz1 - ez, ax0, az0, ax1, az1);
        }
    }

    /** A rail from one end to the other: top and two sides, each end's width laid across that end's direction. */
    private static void rail(VertexConsumer out, PoseStack.Pose pose, int light, double x0, double y0, double z0, double x1, double y1,
                             double z1, double ax0, double az0, double ax1, double az1) {
        float w = RAIL_W / 2, h = RAIL_H;
        TrainMesh.Mat m = TrainMesh.RAIL;
        float u0 = (float) (m.cols()[0] * TrainMesh.TILE) / TrainMesh.ATLAS, u1 = u0 + (float) TrainMesh.TILE / TrainMesh.ATLAS;
        float v0 = (float) (m.row() * TrainMesh.TILE) / TrainMesh.ATLAS, vTop = v0 + 8f / TrainMesh.ATLAS, v1 = v0 + (float) TrainMesh.TILE / TrainMesh.ATLAS;
        // corners: left/right at each end, bottom and top
        double[] l0 = {x0 - ax0 * w, z0 - az0 * w}, r0 = {x0 + ax0 * w, z0 + az0 * w};
        double[] l1 = {x1 - ax1 * w, z1 - az1 * w}, r1 = {x1 + ax1 * w, z1 + az1 * w};
        // top: shiny
        quad(out, pose, light, l0[0], y0 + h, l0[1], r0[0], y0 + h, r0[1], r1[0], y1 + h, r1[1], l1[0], y1 + h, l1[1],
                u0, v0, u1, vTop, 0, 1, 0);
        // the outer (+across) side and the inner side
        quad(out, pose, light, r0[0], y0 + h, r0[1], r0[0], y0, r0[1], r1[0], y1, r1[1], r1[0], y1 + h, r1[1],
                u0, vTop, u1, v1, (float) ax0, 0, (float) az0);
        quad(out, pose, light, l1[0], y1 + h, l1[1], l1[0], y1, l1[1], l0[0], y0, l0[1], l0[0], y0 + h, l0[1],
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
