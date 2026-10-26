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
 * The mist lying on the ground: a few thin sheets of fog stacked just over the street, draped
 * over the ground round you, each sliding its own way with the wind so the mist wisps and
 * curls along. Seen from above it's thin; looking along the street the sheets pile up into a
 * low bank. In fog the stack grows taller.
 *
 * Shader packs drop anything fainter than about a tenth, so the sheets never go that faint:
 * where the fog texture thins, the sheet opens into holes instead, which reads as wisps.
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
    /** Each sheet's drift so far (texture repeats), the wind it's following, and the last frame's time. */
    private static final double[] OFF_U = new double[8], OFF_V = new double[8];
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
        // the low sheets always; more of them, higher up, as fog comes in
        int layers = Mth.clamp(1 + Math.round(thick * 3), 2, 5);
        float strength = (float) Math.min(1, 0.27 * c.mistAmount * (0.8 + 0.4 * Math.min(thick, 1))) * (1 - 0.4f * flash);
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityTranslucent(TEXTURE),
                (pose, out) -> {
                    for (int layer = 0; layer < layers; layer++) {
                        float lift = 0.12f + layer * (0.28f + 0.1f * layer);
                        // each sheet its own scale and way through the wind, so they shear past each other
                        double scale = 22 + layer * 9;
                        double turn = (layer % 2 == 0 ? 1 : -1) * 0.35 * layer;
                        double dx = windX * Math.cos(turn) - windZ * Math.sin(turn), dz = windX * Math.sin(turn) + windZ * Math.cos(turn);
                        // moved on by this frame's share of the wind, never worked out from the clock: a
                        // wind that turns or picks up then just steers the drift instead of jumping it
                        OFF_U[layer] = (OFF_U[layer] + dx * drift * dt * (1 + 0.15 * layer) / scale) % 1;
                        OFF_V[layer] = (OFF_V[layer] + dz * drift * dt * (1 + 0.15 * layer) / scale) % 1;
                        double offU = OFF_U[layer] + layer * 0.37, offV = OFF_V[layer] + layer * 0.61;
                        // thinner the higher it lies
                        float alpha = strength * (1 - 0.09f * layer);
                        sheet(pose, out, eye, lift, scale, offU, offV, alpha);
                    }
                });
    }

    private static void sheet(PoseStack.Pose pose, VertexConsumer out, Vec3 eye, float lift, double scale,
                              double offU, double offV, float alpha) {
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
                // the texture repeats; keep each quad's coordinates near 0..1 whether or not the sampler wraps
                double su = Math.floor((originX + i * CELL) / scale - offU), sv = Math.floor((originZ + j * CELL) / scale - offV);
                corner(pose, out, eye, i, j, ha, lift, scale, offU + su, offV + sv, alpha, LIGHT[a]);
                corner(pose, out, eye, i, j + 1, hd, lift, scale, offU + su, offV + sv, alpha, LIGHT[d]);
                corner(pose, out, eye, i + 1, j + 1, hc, lift, scale, offU + su, offV + sv, alpha, LIGHT[cc]);
                corner(pose, out, eye, i + 1, j, hb, lift, scale, offU + su, offV + sv, alpha, LIGHT[b]);
            }
        }
    }

    private static void corner(PoseStack.Pose pose, VertexConsumer out, Vec3 eye, int i, int j, float ground, float lift,
                               double scale, double offU, double offV, float alpha, int light) {
        double x = originX + i * CELL, z = originZ + j * CELL;
        // the edge of the patch thins out; past a tenth the pack drops it, so the rim breaks up along the wisps
        double away = Math.hypot(x - eye.x, z - eye.z);
        float a = alpha * (float) Mth.clamp((REACH - 2 - away) / 12.0, 0, 1);
        int col = Mth.clamp((int) (a * 255), 0, 255) << 24 | 0x9A9EA8;
        out.addVertex(pose, (float) (x - eye.x), (float) (ground + lift - eye.y), (float) (z - eye.z))
                .setColor(col)
                .setUv((float) (x / scale - offU), (float) (z / scale - offV))
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0, 1, 0);
    }

    private GroundFog() {
    }
}
