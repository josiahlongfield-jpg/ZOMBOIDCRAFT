package com.zomboidcraft.client.cars;

import com.zomboidcraft.client.gear.NightVisionClient;
import com.zomboidcraft.world.events.StrikeCalls;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The marks a radio operator puts on a target for an air strike (StrikeCalls), as everyone sees them: the laser's
 * spot where the beam lands, a small hot dot anyone can see (Josiah's call A8), and the beam itself only through
 * night vision, as an infrared laser shows; and a coloured smoke signal's column, pouring from the can.
 */
final class StrikeMarksFx {
    private static final float[] DOT = {1f, 0.16f, 0.1f}, BEAM = {0.75f, 1f, 0.8f};
    private static final double REACH = 800;

    private static final class Cloud {
        final Vec3 at;
        final float r, g, b;
        final int ticks;
        int age;

        Cloud(Vec3 at, int rgb, int ticks) {
            this.at = at;
            this.r = (rgb >> 16 & 255) / 255f;
            this.g = (rgb >> 8 & 255) / 255f;
            this.b = (rgb & 255) / 255f;
            this.ticks = ticks;
        }
    }

    private static final List<Cloud> CLOUDS = new ArrayList<>();
    private static ClientLevel in;

    private StrikeMarksFx() {
    }

    static void register() {
        // the colour travels in the velocity slots
        ParticleProviderRegistry.getInstance().register(StrikeCalls.SIGNAL_SMOKE, sprites -> (type, level, x, y, z, dx, dy, dz, random) ->
                new SignalSmokeParticle(level, x, y, z, (float) dx, (float) dy, (float) dz, sprites.get(random)));
        ClientPlayNetworking.registerGlobalReceiver(StrikeCalls.SmokeCloud.TYPE, (payload, context) -> context.client().execute(() ->
                CLOUDS.add(new Cloud(new Vec3(payload.x(), payload.y(), payload.z()), payload.colour(), payload.ticks()))));
        ClientTickEvents.END_CLIENT_TICK.register(StrikeMarksFx::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(StrikeMarksFx::draw);
    }

    /** Each signal pours out its smoke: a thick jet low at the can, rising into a column, thinning at the end. */
    private static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level != in || level == null) {
            CLOUDS.clear();
            in = level;
            return;
        }
        RandomSource r = level.getRandom();
        for (var it = CLOUDS.iterator(); it.hasNext(); ) {
            Cloud c = it.next();
            if (++c.age > c.ticks) {
                it.remove();
                continue;
            }
            double fade = Math.min(1, (c.ticks - c.age) / 100.0), grow = Math.min(1, c.age / 30.0);
            int puffs = (int) Math.ceil(3 * fade * grow + r.nextDouble());
            for (int i = 0; i < puffs; i++) {
                level.addParticle(StrikeCalls.SIGNAL_SMOKE, true, true, c.at.x + (r.nextDouble() - 0.5) * 0.6, c.at.y + 0.2 + r.nextDouble() * 0.4,
                        c.at.z + (r.nextDouble() - 0.5) * 0.6, c.r, c.g, c.b);
            }
        }
    }

    private static void draw(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        CameraRenderState cam = context.levelState().cameraRenderState;
        if (level == null || cam == null || cam.pos == null) {
            return;
        }
        float partial = cam.cameraEntityPartialTicks;
        boolean shaders = CarLights.shaderPackOn();
        CarLights.Quads glow = null;
        boolean nv = NightVisionClient.viewing();
        for (Player p : level.players()) {
            if (!StrikeCalls.lasing(p)) {
                continue;
            }
            Vec3 eye = p.getEyePosition(partial), dir = p.getViewVector(partial);
            BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(dir.scale(REACH)), ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, p));
            if (hit.getType() == HitResult.Type.MISS) {
                continue;
            }
            if (glow == null) {
                glow = new CarLights.Quads(cam.pos, shaders);
            }
            Vec3 at = hit.getLocation();
            Vec3 normal = new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ());
            double far = at.distanceTo(cam.pos);
            // a small spot, a little bigger far off so it still shows from a rooftop down the street
            double size = Math.min(0.5, 0.07 + far * 0.0016);
            glow.flat(at.add(normal.scale(0.02)), normal, size, glow.shade(DOT, 1f));
            glow.spot(at.add(normal.scale(0.06)), size * 2.2, glow.shade(DOT, 0.45f));
            if (nv) {
                // through the tube the beam shows, from the laser's muzzle in front of the operator's eyes
                Vec3 from = eye.add(dir.scale(0.6)).add(0, -0.12, 0);
                Vec3[] line = {from, from.add(at.subtract(from).scale(0.5)), at};
                double w = 0.012;
                int bright = glow.shade(BEAM, 0.55f), faint = glow.shade(BEAM, 0.25f);
                glow.strip(line, new double[] {w, w * 2.5, w * 4}, new int[] {bright, faint, bright});
            }
        }
        if (glow != null && glow.count > 0) {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(),
                    shaders ? RenderTypes.lightning() : CarLights.glowType(false), glow::emit);
        }
    }
}
