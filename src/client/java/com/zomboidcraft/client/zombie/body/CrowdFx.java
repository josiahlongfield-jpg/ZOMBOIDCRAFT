package com.zomboidcraft.client.zombie.body;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.zombie.population.CrowdPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * The dead out in the open past the real zombies, as the server's records have them (Population.crowd): no
 * entities, only Zomboid's thinnest bodies (no pictures), each standing where it is, facing its own way and swaying through
 * the standing frames. One batch per outfit. Replaced about once a second; gone if the server stops sending.
 */
public final class CrowdFx {
    /** Past this with no word from the server, the crowd is old news. */
    private static final long STALE_NANOS = 5_000_000_000L;

    /** One outfit's figures: feet positions, facings, phases and light. */
    private record Group(String outfit, float[] x, float[] y, float[] z, float[] yaw, int[] variant, int[] light) {}

    private static List<Group> groups = List.of();
    private static ClientLevel level;
    private static long heard;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CrowdPayload.TYPE,
                (payload, context) -> context.client().execute(() -> store(payload)));
        LevelRenderEvents.COLLECT_SUBMITS.register(CrowdFx::draw);
    }

    private static void store(CrowdPayload p) {
        Minecraft mc = Minecraft.getInstance();
        level = mc.level;
        heard = System.nanoTime();
        if (level == null) {
            groups = List.of();
            return;
        }
        Map<Integer, List<Integer>> by = new HashMap<>();
        for (int i = 0; i < p.dx().length; i++) {
            int o = p.outfit()[i] & 0xFF;
            if (o > 0 && o <= p.outfits().size()) {
                by.computeIfAbsent(o, k -> new ArrayList<>()).add(i);
            }
        }
        List<Group> made = new ArrayList<>();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (Map.Entry<Integer, List<Integer>> e : by.entrySet()) {
            List<Integer> ids = e.getValue();
            int n = ids.size();
            float[] x = new float[n], y = new float[n], z = new float[n], yaw = new float[n];
            int[] variant = new int[n], light = new int[n];
            for (int k = 0; k < n; k++) {
                int i = ids.get(k);
                int bx = p.bx() + p.dx()[i], by_ = p.y()[i], bz = p.bz() + p.dz()[i];
                // a little off the block's middle, by where it stands, so a crowd isn't a grid
                int h = bx * 73856093 ^ by_ * 19349663 ^ bz * 83492791;
                h ^= h >>> 13;
                h *= 0x5bd1e995;
                h ^= h >>> 15;
                x[k] = bx + 0.5f + ((h & 255) / 255f - 0.5f) * 0.6f;
                z[k] = bz + 0.5f + ((h >>> 8 & 255) / 255f - 0.5f) * 0.6f;
                y[k] = by_;
                yaw[k] = (h >>> 16 & 1023) * (360f / 1024f);
                variant[k] = h;
                at.set(bx, by_, bz);
                light[k] = level.getBrightness(LightLayer.BLOCK, at) << 4 | level.getBrightness(LightLayer.SKY, at) << 20;
            }
            made.add(new Group(p.outfits().get(e.getKey() - 1), x, y, z, yaw, variant, light));
        }
        groups = made;
    }

    private static void draw(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (groups.isEmpty()) {
            ZDetail.crowd(0);
            return;
        }
        if (mc.level != level || System.nanoTime() - heard > STALE_NANOS) {
            groups = List.of();
            return;
        }
        ZcConfig c = ZcConfig.get();
        boolean zomboid = com.zomboidcraft.client.survivor.ZedBodies.available();
        if (!c.population.crowd || !zomboid && (!c.performance.spriteZombies || !c.look.newZombies)) {
            return;
        }
        CameraRenderState cam = context.levelState().cameraRenderState;
        if (cam == null || cam.pos == null) {
            return;
        }
        Vec3 eye = cam.pos;
        // in ticks; the standing frames only turn over about once a second, so the wall clock will do
        float age = (System.nanoTime() / 1_000_000L % 3_600_000L) / 50f;
        int drawn = 0;
        if (zomboid) {
            // with the Zomboid packs here the crowd is Zomboid's thinnest bodies, never pictures or the old body
            // (Josiah: a scope would show a picture up)
            long frame = ++frames;
            var ps = context.poseStack();
            // only the figures in front of you: the crowd was drawn all round, behind you included, and with a few
            // thousand of them it was a sixth of the frame (2026-11-08 profile). A cone a little wider than the
            // screen's corners, so nothing pops at the edges; a scope narrows the view, so the cone still covers it.
            Vec3 look = mc.player != null ? mc.player.getViewVector(1f) : null;
            double aspect = mc.getWindow().getHeight() > 0 ? (double) mc.getWindow().getWidth() / mc.getWindow().getHeight() : 1.78;
            double halfV = Math.toRadians(mc.options.fov().get() * 1.15 / 2.0);
            double cone = Math.cos(Math.min(Math.PI * 0.95, Math.atan(Math.tan(halfV) * Math.sqrt(1 + aspect * aspect)) + Math.toRadians(10)));
            for (Group g : groups) {
                for (int i = 0; i < g.x.length; i++) {
                    if (look != null) {
                        double dx = g.x[i] - eye.x, dy = g.y[i] + 1 - eye.y, dz = g.z[i] - eye.z;
                        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (d > 4 && (dx * look.x + dy * look.y + dz * look.z) < cone * d) {
                            continue;
                        }
                    }
                    drawn++;
                    ps.pushPose();
                    ps.translate((float) (g.x[i] - eye.x), (float) (g.y[i] - eye.y), (float) (g.z[i] - eye.z));
                    ps.rotateDegrees(com.mojang.math.Axis.YP, -g.yaw[i]);
                    com.zomboidcraft.client.survivor.ZedBodies.submitCrowd(g.variant[i], g.outfit, age, frame, ps,
                            context.submitNodeCollector(), g.light[i]);
                    ps.popPose();
                }
            }
            ZDetail.crowd(drawn);
            return;
        }
        // without the Zomboid packs the crowd isn't drawn: no picture zombies, ever (Josiah: a scope shows them up)
        ZDetail.crowd(drawn);
    }

    private static long frames;

    private CrowdFx() {}
}
