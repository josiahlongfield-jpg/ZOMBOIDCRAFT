package com.zomboidcraft.client.trains;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.trains.Line;
import com.zomboidcraft.world.trains.TrainCar;
import com.zomboidcraft.world.trains.TrainEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;

/**
 * Draws a whole train: each car where the client's own running of the train has it this frame,
 * turned on the curve, its doors slid open as far as they are, its lamps lit (three in ten of them
 * failing, and always the one over OTTO: they stutter, hold, and die for a while, darkening that end
 * of the car), and OTTO in the cab looking about with glowing red eyes, or straight at you when he talks.
 */
public class TrainRenderer extends EntityRenderer<TrainEntity, TrainRenderer.State> {
    static final Identifier ATLAS = ZomboidCraft.id("textures/entity/train/atlas.png");
    private static final int FULL = 0xF000F0;
    /** Lamps a car can have (the state keeps a slot for each, per car). */
    private static final int LAMPS = 12;
    /** Block light under a lamp that's on, beside one that's on, and with both out. */
    private static final int LIT = 12, SPILL = 7, DARK = 3;

    public static class State extends EntityRenderState {
        Line.Kind kind = Line.Kind.RAIL;
        int cars;
        /** Per car: x, y, z relative to the entity's drawn position, and yaw in degrees. */
        float[] at = new float[0];
        int[] light = new int[0];
        int[] inside = new int[0];
        boolean[] lampOn = new boolean[0];
        boolean shaders;
        float doors;
        int side;
        boolean talking, eyesOff;
        float headYaw, jaw;
        long seed;
        /** The camera is by or in the train: its insides cast shadows then (from outside they can't be seen). */
        boolean close;
    }

    public TrainRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    protected AABB getBoundingBoxForCulling(TrainEntity train, float partialTick) {
        double r = train.cars() * TrainCar.PITCH / 2 + 4;
        return new AABB(train.getX() - r, train.getY() - 2, train.getZ() - r, train.getX() + r, train.getY() + 5, train.getZ() + r);
    }

    @Override
    public void extractRenderState(TrainEntity train, State state, float partialTick) {
        super.extractRenderState(train, state, partialTick);
        Line line = train.line();
        if (line == null) {
            state.cars = 0;
            return;
        }
        state.kind = line.kind;
        state.cars = line.cars;
        net.minecraft.world.entity.Entity eye = net.minecraft.client.Minecraft.getInstance().getCameraEntity();
        double reach = line.cars * TrainCar.PITCH / 2 + 4;
        state.close = eye == null || eye.distanceToSqr(train) < reach * reach;
        if (state.at.length != line.cars * 4) {
            state.at = new float[line.cars * 4];
            state.light = new int[line.cars];
            state.inside = new int[line.cars];
            state.lampOn = new boolean[line.cars * LAMPS];
        }
        state.shaders = com.zomboidcraft.client.cars.CarLights.shaderPackOn();
        long lineSeed = line.id.hashCode();
        double s = train.sAt(partialTick);
        ClientLevel level = Minecraft.getInstance().level;
        long time = level == null ? 0 : level.getGameTime();
        for (int car = 0; car < line.cars; car++) {
            TrainCar.Pose p = TrainCar.pose(line.route, s, car);
            state.at[car * 4] = (float) (p.x() - state.x);
            state.at[car * 4 + 1] = (float) (p.y() - state.y);
            state.at[car * 4 + 2] = (float) (p.z() - state.z);
            state.at[car * 4 + 3] = p.yawDegrees();
            int lit = FULL;
            if (level != null) {
                // the world's light just over the roof (inside, the car counts as indoors)
                BlockPos pos = BlockPos.containing(p.x(), p.y() + TrainCar.ROOF + 0.6, p.z());
                lit = level.getBrightness(LightLayer.BLOCK, pos) << 4 | level.getBrightness(LightLayer.SKY, pos) << 20;
            }
            state.light[car] = lit;
            // little sky gets in: inside it's indoors (shader packs keep rain and wet off what's lit like this);
            // the car's own lamps add their light quad by quad
            int sky = Math.min((lit >> 20) & 0xF, com.zomboidcraft.world.trains.TrainPhysics.INDOOR_SKY);
            state.inside[car] = ((lit >> 4) & 0xF) << 4 | sky << 20;
            TrainMesh mesh = TrainMesh.get(line.kind, car == 0, car == line.cars - 1);
            for (int i = 0; i < LAMPS; i++) {
                state.lampOn[car * LAMPS + i] = i >= mesh.lamps.size() || lampOn(lineSeed, car, i, i == mesh.cabLamp, time);
            }
        }
        float d = train.doorsO + (train.doors - train.doorsO) * partialTick;
        state.doors = d * d * (3 - 2 * d);
        state.side = train.doorSide();
        state.talking = train.talking < 50;
        // OTTO holds a dead stare down the train, and every few seconds snaps his head aside and back;
        // talking, his jaw works and his head shakes a little. Now and then his eyes cut out.
        double tt = (time + partialTick) / 20.0;
        long beat = (long) Math.floor(tt / 2.5) + train.getId() * 7L;
        long h = beat * 0x9E3779B97F4A7C15L;
        float[] looks = {0, 0, 0, -38, 32, -14, 0, 21};
        float target = looks[(int) ((h >>> 33) & 7)];
        double into = tt / 2.5 - Math.floor(tt / 2.5);
        float snap = (float) Math.min(1, into * 2.5 / 0.12);      // a twitch: there in about a tenth of a second
        float prev = looks[(int) ((((beat - 1) * 0x9E3779B97F4A7C15L) >>> 33) & 7)];
        state.headYaw = prev + (target - prev) * snap;
        if (state.talking) {
            state.headYaw = state.headYaw * 0.3f + (float) Math.sin(tt * 23) * 3;
            state.jaw = (float) (Math.abs(Math.sin(tt * 13)) * 0.045 + Math.abs(Math.sin(tt * 31)) * 0.015);
        } else {
            state.jaw = 0;
        }
        long e = (time / 2 + train.getId() * 13L) * 0x632BE59BD9B4E019L;
        state.eyesOff = ((e >>> 41) & 0xFF) < 5;
        state.seed = time;
    }

    /**
     * Whether a lamp is lit this tick. Seven in ten are fine; the rest (and always OTTO's) are failing
     * tubes that go in spells of a second and a half: holding steady, stuttering on and off, or dead
     * with now and then a blink.
     */
    static boolean lampOn(long seed, int car, int lamp, boolean cab, long tick) {
        long id = mix(seed * 1_000_003L + car * 131L + lamp);
        if (!cab && Long.remainderUnsigned(id, 100) >= 30) {
            return true;
        }
        long spell = (tick + (id & 63)) / 30;
        int mood = (int) Long.remainderUnsigned(mix(id ^ spell * 0x9E3779B97F4A7C15L), 10);
        long fizz = mix(id ^ tick * 0xC2B2AE3D27D4EB4FL);
        if (mood < 4) {
            return true;                    // holding
        } else if (mood < 8) {
            return (fizz & 3) != 0 && ((fizz >>> 7) & 7) > 2;    // stuttering
        }
        return (fizz & 31) == 0;            // dead, with a blink now and then
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        for (int car = 0; car < state.cars; car++) {
            TrainMesh mesh = TrainMesh.get(state.kind, car == 0, car == state.cars - 1);
            int light = state.light[car], inside = state.inside[car], c = car;
            poseStack.pushPose();
            poseStack.translate(state.at[car * 4], state.at[car * 4 + 1], state.at[car * 4 + 2]);
            poseStack.rotateDegrees(Axis.YP, -state.at[car * 4 + 3]);
            // the shadow map gets the shell only (the profile showed trains drawn whole a second time for it): no
            // glass or glow, and no insides unless you're by or in the train
            boolean shadow = com.zomboidcraft.client.zombie.body.ZDetail.shadowPass();
            int solid = shadow && !state.close ? SHELL : SOLID;
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(ATLAS),
                    (pose, out) -> draw(mesh, state, c, pose, out, light, inside, solid));
            if (shadow) {
                poseStack.popPose();
                continue;
            }
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(ATLAS),
                    (pose, out) -> draw(mesh, state, c, pose, out, light, inside, GLASS));
            // what glows, drawn so it glows: with a shader pack the way a beacon's beam is, which the packs light up
            // (lamps, eyes, his core, the head lamps); without, his eyes glare over the top
            collector.submitCustomGeometry(poseStack, state.shaders ? RenderTypes.beaconBeam(ATLAS, false) : RenderTypes.eyes(ATLAS),
                    (pose, out) -> draw(mesh, state, c, pose, out, light, inside, GLOWING));
            poseStack.popPose();
        }
    }

    /** SHELL: the solid pass without the insides, for the shadow map. */
    private static final int SOLID = 0, GLASS = 1, GLOWING = 2, SHELL = 3;

    /** Whether a quad is drawn in the glow pass. */
    private static boolean glowing(TrainMesh.Quad q, State state, int car) {
        if (q.light != TrainMesh.GLOW || q.glass || (q.lamp >= 0 && !state.lampOn[car * LAMPS + q.lamp])) {
            return false;
        }
        return state.shaders || q.part == TrainMesh.PART_EYES;
    }

    private static void draw(TrainMesh mesh, State state, int car, PoseStack.Pose pose, VertexConsumer out, int light, int inside, int pass) {
        float slide = state.doors * 0.95f;
        float headCos = (float) Math.cos(Math.toRadians(state.headYaw)), headSin = (float) Math.sin(Math.toRadians(state.headYaw));
        float[] v = new float[3], n = new float[3];
        int insideBlock = (inside >> 4) & 0xF, insideSky = inside & 0xF00000;
        for (TrainMesh.Quad q : mesh.quads) {
            if (pass == SHELL ? q.glass || q.light != TrainMesh.OUTSIDE
                    : pass == GLOWING ? !glowing(q, state, car) : q.glass != (pass == GLASS)) {
                continue;
            }
            if (pass == SOLID && state.shaders && glowing(q, state, car)) {
                continue;   // the glow pass draws it
            }
            if (q.part == TrainMesh.PART_EYES && state.eyesOff) {
                continue;
            }
            float ox = 0, oz = 0, oy = q.part == TrainMesh.PART_JAW ? -state.jaw : 0;
            boolean head = q.part == TrainMesh.PART_HEAD || q.part == TrainMesh.PART_JAW || q.part == TrainMesh.PART_EYES;
            if (q.part >= TrainMesh.PART_DOOR) {
                TrainMesh.Leaf leaf = mesh.leaves.get(q.part - TrainMesh.PART_DOOR);
                if (state.side == 0 || state.side == leaf.side()) {
                    oz = leaf.slide() * slide;
                }
            }
            int lit;
            if (q.light == TrainMesh.GLOW) {
                // a dead tube is just grey glass in the dark
                lit = q.lamp >= 0 && !state.lampOn[car * LAMPS + q.lamp] ? Math.max(DARK - 1, insideBlock) << 4 | insideSky : FULL;
            } else if (q.light == TrainMesh.INSIDE) {
                int block = q.near < 0 ? LIT : state.lampOn[car * LAMPS + q.near] ? LIT : state.lampOn[car * LAMPS + q.near2] ? SPILL : DARK;
                lit = Math.max(block, insideBlock) << 4 | insideSky;
            } else {
                lit = light;
            }
            n[0] = q.nx;
            n[1] = q.ny;
            n[2] = q.nz;
            if (head) {
                turn(n, headCos, headSin, 0, 0);
            }
            for (int k = 0; k < 4; k++) {
                v[0] = q.p[k * 3] + ox;
                v[1] = q.p[k * 3 + 1] + oy;
                v[2] = q.p[k * 3 + 2] + oz;
                if (head && mesh.headPivot != null) {
                    turn(v, headCos, headSin, mesh.headPivot[0], mesh.headPivot[2]);
                }
                out.addVertex(pose, v[0], v[1], v[2])
                        .setColor(-1)
                        .setUv(q.uv[k * 2], q.uv[k * 2 + 1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(lit)
                        .setNormal(pose, n[0], n[1], n[2]);
            }
        }
    }

    /** Turns a point about a vertical axis through (px, pz). */
    private static void turn(float[] v, float cos, float sin, float px, float pz) {
        float x = v[0] - px, z = v[2] - pz;
        v[0] = px + x * cos + z * sin;
        v[2] = pz - x * sin + z * cos;
    }
}
