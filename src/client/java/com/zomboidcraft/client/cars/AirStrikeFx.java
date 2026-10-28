package com.zomboidcraft.client.cars;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.events.AirStrikes;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * What the air strikes look like here ({@link AirStrikes}): the glowing streaks of the cannon's and chain gun's
 * tracers, a flash at the muzzle and where each one bursts, and the bombs themselves falling from under the jets,
 * nose dropping as they go, until they strike the ground (the server sets them off there, the same moment).
 */
final class AirStrikeFx {
    /** How fast a tracer is drawn crossing (blocks a second: slower than a real shell, so the eye catches it). */
    private static final double SPEED = 420, LENGTH = 16;
    private static final float[] TRACER = {1f, 0.58f, 0.22f}, FLASH = {1f, 0.85f, 0.55f};
    private static final Identifier BEAM = ZomboidCraft.id("textures/effect/rail_glow.png");
    private static final double G = 9.81;

    private record Shot(Vec3 from, Vec3 to, long born) {
    }

    private static final class Falling {
        Vec3 pos, vel, posO, velO;
        boolean landed;

        Falling(Vec3 pos, Vec3 vel) {
            this.pos = this.posO = pos;
            this.vel = this.velO = vel;
        }
    }

    private static final List<Shot> SHOTS = new ArrayList<>();
    private static final List<Falling> BOMBS = new ArrayList<>();
    private static final ItemStack BOMB = bomb();
    private static ClientLevel in;

    private AirStrikeFx() {
    }

    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(AirStrikes.Tracer.TYPE, (payload, context) -> context.client().execute(() ->
                SHOTS.add(new Shot(payload.from(), payload.to(), System.nanoTime()))));
        ClientPlayNetworking.registerGlobalReceiver(AirStrikes.Bomb.TYPE, (payload, context) -> context.client().execute(() ->
                BOMBS.add(new Falling(new Vec3(payload.x(), payload.y(), payload.z()), new Vec3(payload.vx(), payload.vy(), payload.vz())))));
        ClientTickEvents.END_CLIENT_TICK.register(AirStrikeFx::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(AirStrikeFx::draw);
    }

    private static ItemStack bomb() {
        ItemStack stack = new ItemStack(Items.STICK);
        stack.set(DataComponents.ITEM_MODEL, ZomboidCraft.id("strike_bomb"));
        return stack;
    }

    private static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level != in || level == null) {
            SHOTS.clear();
            BOMBS.clear();
            in = level;
            return;
        }
        for (var it = BOMBS.iterator(); it.hasNext(); ) {
            Falling b = it.next();
            if (b.landed) {
                it.remove();
                continue;
            }
            b.posO = b.pos;
            b.velO = b.vel;
            Vec3 next = b.pos.add(b.vel.scale(0.05));
            b.vel = b.vel.add(0, -G * 0.05, 0);
            if (next.y < level.getMinY() || level.isLoaded(BlockPos.containing(next)) && level.clip(new ClipContext(b.pos, next,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContext.empty())).getType() != HitResult.Type.MISS) {
                b.landed = true;
            }
            b.pos = next;
        }
        long now = System.nanoTime();
        SHOTS.removeIf(s -> (now - s.born) / 1e9 > s.from.distanceTo(s.to) / SPEED + 0.15);
    }

    private static void draw(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        CameraRenderState cam = context.levelState().cameraRenderState;
        if (level == null || cam == null || cam.pos == null || SHOTS.isEmpty() && BOMBS.isEmpty()) {
            return;
        }
        boolean shaders = CarLights.shaderPackOn();
        CarLights.Quads glow = new CarLights.Quads(cam.pos, shaders);
        long now = System.nanoTime();
        for (Shot s : SHOTS) {
            Vec3 line = s.to.subtract(s.from);
            double length = line.length();
            if (length < 1) {
                continue;
            }
            Vec3 dir = line.scale(1 / length);
            double t = (now - s.born) / 1e9, head = Math.min(length, t * SPEED), tail = Math.max(0, head - LENGTH);
            if (t < 0.06) {
                glow.spot(s.from, 1.4, glow.shade(FLASH, 1f));
            }
            if (head >= length && t * SPEED - length < 0.15 * SPEED) {
                glow.spot(s.to.subtract(dir.scale(0.4)), 1.8, glow.shade(FLASH, (float) (1 - (t * SPEED - length) / (0.15 * SPEED))));
            }
            if (head - tail < 0.5) {
                continue;
            }
            Vec3[] p = {s.from.add(dir.scale(tail)), s.from.add(dir.scale((head + tail) / 2)), s.from.add(dir.scale(head))};
            // wider further off, so it still shows across a city
            double w = 0.09 + 0.0025 * p[2].distanceTo(cam.pos);
            int bright = glow.shade(TRACER, 1f), dim = glow.shade(TRACER, 0.35f);
            if (shaders) {
                glow.fadeStrip(p, new double[] {w * 0.5, w, w * 1.1}, new int[] {dim, bright, bright});
            } else {
                glow.strip(p, new double[] {w * 0.5, w, w * 1.1}, new int[] {dim, bright, bright});
            }
        }
        if (glow.count > 0) {
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(),
                    shaders ? RenderTypes.lightning() : CarLights.glowType(BEAM, false), glow::emit);
        }
        float partial = cam.cameraEntityPartialTicks;
        for (Falling b : BOMBS) {
            Vec3 at = b.posO.add(b.pos.subtract(b.posO).scale(partial)), v = b.velO.add(b.vel.subtract(b.velO).scale(partial));
            if (at.distanceToSqr(cam.pos) > 400 * 400) {
                continue;
            }
            ItemStackRenderState state = new ItemStackRenderState();
            mc.getItemModelResolver().updateForTopItem(state, BOMB, ItemDisplayContext.NONE, level, mc.player, 0);
            BlockPos pos = BlockPos.containing(at);
            int light = level.getBrightness(LightLayer.BLOCK, pos) << 4 | level.getBrightness(LightLayer.SKY, pos) << 20;
            PoseStack pose = context.poseStack();
            pose.pushPose();
            pose.translate(at.x - cam.pos.x, at.y - cam.pos.y, at.z - cam.pos.z);
            // the model's nose is +z: turn it to the way it's falling
            float yaw = (float) Math.atan2(v.x, v.z), pitch = (float) Math.atan2(-v.y, Math.sqrt(v.x * v.x + v.z * v.z));
            pose.mulPose(new org.joml.Quaternionf().rotationY(yaw).rotateX(pitch));
            pose.scale(2.2f, 2.2f, 2.2f);
            state.submit(pose, context.submitNodeCollector(), light, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
}
