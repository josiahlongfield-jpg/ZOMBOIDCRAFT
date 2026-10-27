package com.zomboidcraft.client.weather;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.weather.Weather;
import com.zomboidcraft.world.weather.WeatherView;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The mist lying on the ground: thin sheets of fog stacked just over the street, draped over
 * the ground round you, sparser the higher they lie. How thick the mist is comes from soft cloud
 * noise drifting with the wind, a little differently in each sheet, so it wisps and curls along.
 * In fog the stack grows taller.
 *
 * Shader packs drop anything fainter than about a tenth, which turns a soft fade into a hard
 * edge. So the sheets are speckle, not smooth: each pixel a random strength, and the thinner the
 * mist the fewer pixels clear the bar. The speckle moves every frame and the pack's frame
 * blending smooths it into haze that thins out to nothing, with no edge anywhere.
 */
final class GroundFog {
    private static final Identifier TEXTURE = ZomboidCraft.id("textures/effect/ground_fog.png");
    private static final int CELL = 2, REACH = 36, SIDE = REACH * 2 / CELL + 1;
    /** A quad is left out where the ground under it steps more than this (a wall, a kerb up to a roof). */
    private static final float STEP = 1.1f;
    private static final float[] HEIGHT = new float[SIDE * SIDE];
    private static final int[] LIGHT = new int[SIDE * SIDE];
    private static int originX, originZ, clock;
    private static boolean ready;
    private static float thick;
    /** Each sheet's drift so far in blocks, the wind it's following, and the last frame's time. */
    private static final double[] OFF_X = new double[8], OFF_Z = new double[8];
    /** How many times the speckle tile repeats across one quad (CELL blocks): about 4 texture pixels a block-sixteenth. */
    private static final double SPECKLE = 1.5;
    private static double windX, windZ = 1, drift = 0.25, lastT = Double.NaN;

    /** Every tick: follow the player, and every half second re-read the ground under the fog. */
    static void tick(ClientLevel level, LocalPlayer player, Weather w, float night) {
        float want = 0.45f + 0.15f * night + 0.15f * w.rain() + 0.9f * w.fog();
        thick += (want - thick) * 0.02f;
        if (clock++ % 10 != 0 && ready) {
            return;
        }
        originX = Mth.floor(player.getX() / CELL) * CELL - REACH;
        originZ = Mth.floor(player.getZ() / CELL) * CELL - REACH;
        double feet = player.getY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < SIDE; i++) {
            for (int j = 0; j < SIDE; j++) {
                int x = originX + i * CELL, z = originZ + j * CELL;
                // leaves don't count: the mist lies under the trees, not on them
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                // only on real ground: never over your head, and never on a roof, an awning or a car
                // park deck (anything with open space under its top)
                HEIGHT[i * SIDE + j] = top > feet + 6 || hollow(level, pos, x, top, z) ? Float.NaN : top;
                pos.set(x, top, z);
                int block = level.getBrightness(LightLayer.BLOCK, pos), sky = level.getBrightness(LightLayer.SKY, pos);
                LIGHT[i * SIDE + j] = sky << 20 | Math.max(block, 5) << 4;
            }
        }
        ready = true;
    }

    /** Whether there's open space a little under the top at x, z: a roof or a deck, not the ground. */
    private static boolean hollow(ClientLevel level, BlockPos.MutableBlockPos pos, int x, int top, int z) {
        for (int y = top - 2; y >= top - 7; y--) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static void forget() {
        ready = false;
    }

    static void draw(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        CameraRenderState cam = context.levelState().cameraRenderState;
        ZcConfig.Weather c = ZcConfig.get().weather;
        if (!ready || level == null || cam == null || cam.pos == null || !c.mist || c.mistAmount <= 0
                || level.dimension() != Level.OVERWORLD || WeatherView.client() == null) {
            return;
        }
        Vec3 eye = cam.pos;
        Weather w = WeatherView.client();
        double t = (level.getGameTime() + cam.cameraEntityPartialTicks) / 20.0;
        double dt = Double.isNaN(lastT) ? 0 : Mth.clamp(t - lastT, 0, 0.25);
        lastT = t;
        // the wind as the mist feels it: eased toward the real one over a few seconds
        double yaw = Math.toRadians(w.windYaw());
        double ease = 1 - Math.exp(-dt / 3.0);
        windX += (-Math.sin(yaw) - windX) * ease;
        windZ += (Math.cos(yaw) - windZ) * ease;
        drift += (0.25 + 0.9 * w.wind() - drift) * ease;
        float flash = WeatherView.flash();
        // many thin sheets, sparser the higher they lie, so the mist has depth and no top surface
        int layers = Mth.clamp(4 + Math.round(thick * 3), 4, 8);
        float strength = (float) Math.min(0.6, 0.42 * c.mistAmount * (0.8 + 0.4 * Math.min(thick, 1))) * (1 - 0.4f * flash);
        // the speckle shifts to a fresh spot every frame: the shader pack's frame blending melts it into smooth haze
        double jitterU = Math.random(), jitterV = Math.random();
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityTranslucent(TEXTURE),
                (pose, out) -> {
                    for (int layer = 0; layer < layers; layer++) {
                        float lift = 0.08f + layer * 0.22f;
                        // each sheet drifts its own way through the wind, so they shear past each other
                        double turn = (layer % 2 == 0 ? 1 : -1) * 0.3 * layer;
                        double dx = windX * Math.cos(turn) - windZ * Math.sin(turn), dz = windX * Math.sin(turn) + windZ * Math.cos(turn);
                        // moved on by this frame's share of the wind, never worked out from the clock: a
                        // wind that turns or picks up then just steers the drift instead of jumping it
                        OFF_X[layer] += dx * drift * dt * (1 + 0.12 * layer);
                        OFF_Z[layer] += dz * drift * dt * (1 + 0.12 * layer);
                        // sparser the higher it lies, fading out to nothing at the top
                        float alpha = strength * (1 - layer / (float) (layers + 1));
                        sheet(pose, out, eye, lift, layer, alpha, jitterU + layer * 0.29, jitterV + layer * 0.53);
                    }
                });
    }

    private static void sheet(PoseStack.Pose pose, VertexConsumer out, Vec3 eye, float lift, int layer, float alpha,
                              double ju, double jv) {
        for (int i = 0; i < SIDE - 1; i++) {
            for (int j = 0; j < SIDE - 1; j++) {
                int a = i * SIDE + j, b = (i + 1) * SIDE + j, cc = (i + 1) * SIDE + j + 1, d = i * SIDE + j + 1;
                float ha = HEIGHT[a], hb = HEIGHT[b], hc = HEIGHT[cc], hd = HEIGHT[d];
                if (Float.isNaN(ha) || Float.isNaN(hb) || Float.isNaN(hc) || Float.isNaN(hd)) {
                    continue;
                }
                float lo = Math.min(Math.min(ha, hb), Math.min(hc, hd)), hi = Math.max(Math.max(ha, hb), Math.max(hc, hd));
                if (hi - lo > STEP) {
                    continue;
                }
                corner(pose, out, eye, i, j, 0, 0, ha, lift, layer, alpha, ju, jv, LIGHT[a]);
                corner(pose, out, eye, i, j + 1, 0, 1, hd, lift, layer, alpha, ju, jv, LIGHT[d]);
                corner(pose, out, eye, i + 1, j + 1, 1, 1, hc, lift, layer, alpha, ju, jv, LIGHT[cc]);
                corner(pose, out, eye, i + 1, j, 1, 0, hb, lift, layer, alpha, ju, jv, LIGHT[b]);
            }
        }
    }

    /**
     * One corner of a quad. How thick the mist is here comes from the drifting cloud noise and
     * fades out toward the edge of the patch, and it's carried in the corner's alpha. The texture
     * is pure speckle (each pixel a random strength): the pack drops the pixels too faint to keep,
     * so a thinner corner keeps fewer of them, and the thickness fades smoothly to nothing with
     * no edge anywhere.
     */
    private static void corner(PoseStack.Pose pose, VertexConsumer out, Vec3 eye, int i, int j, int du, int dv, float ground,
                               float lift, int layer, float alpha, double ju, double jv, int light) {
        double x = originX + i * CELL, z = originZ + j * CELL;
        double away = Math.hypot(x - eye.x, z - eye.z);
        float edge = (float) Mth.clamp((REACH - 2 - away) / 14.0, 0, 1);
        float cloud = cloud(x - OFF_X[layer] + layer * 31.7, z - OFF_Z[layer] - layer * 17.3);
        float a = alpha * edge * cloud;
        int col = Mth.clamp((int) (a * 255), 0, 255) << 24 | 0x9A9EA8;
        // the speckle tile spans one quad
        out.addVertex(pose, (float) (x - eye.x), (float) (ground + lift - eye.y), (float) (z - eye.z))
                .setColor(col)
                .setUv((float) (du * SPECKLE + ju), (float) (dv * SPECKLE + jv))
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0, 1, 0);
    }

    /** Soft drifting thickness, 0.15..1: two octaves of smooth value noise over the world. */
    private static float cloud(double x, double z) {
        double v = 0.65 * noise(x / 14, z / 14) + 0.35 * noise(x / 5.5 + 7.1, z / 5.5 - 3.3);
        return (float) Mth.clamp(0.15 + 1.1 * (v - 0.2), 0.15, 1);
    }

    private static double noise(double x, double z) {
        int x0 = Mth.floor(x), z0 = Mth.floor(z);
        double fx = x - x0, fz = z - z0;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash(x0, z0), b = hash(x0 + 1, z0), c = hash(x0, z0 + 1), d = hash(x0 + 1, z0 + 1);
        return Mth.lerp(fz, Mth.lerp(fx, a, b), Mth.lerp(fx, c, d));
    }

    private static double hash(int x, int z) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    private GroundFog() {
    }
}
