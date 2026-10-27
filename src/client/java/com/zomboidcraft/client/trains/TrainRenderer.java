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
 * turned on the curve, its doors slid open as far as they are, its lights on (now and then one
 * flickers), and OTTO in the cab looking about, or straight at you when he talks.
 */
public class TrainRenderer extends EntityRenderer<TrainEntity, TrainRenderer.State> {
    static final Identifier ATLAS = ZomboidCraft.id("textures/entity/train/atlas.png");
    private static final int FULL = 0xF000F0;

    public static class State extends EntityRenderState {
        Line.Kind kind = Line.Kind.RAIL;
        int cars;
        /** Per car: x, y, z relative to the entity's drawn position, and yaw in degrees. */
        float[] at = new float[0];
        int[] light = new int[0];
        int[] inside = new int[0];
        float doors;
        int side;
        boolean talking, eyesOff;
        float headYaw, jaw;
        long seed;
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
        if (state.at.length != line.cars * 4) {
            state.at = new float[line.cars * 4];
            state.light = new int[line.cars];
            state.inside = new int[line.cars];
        }
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
            // the cars' own lights: steady, but now and then a tube stutters
            long h = (time / 3 + car * 7919L + train.getId() * 31L) * 0x9E3779B97F4A7C15L;
            boolean flicker = ((h >>> 40) & 0xFF) < 6;
            int block = flicker ? 3 : 12;
            // little sky gets in: inside it's indoors (shader packs keep rain and wet off what's lit like this)
            int sky = Math.min((lit >> 20) & 0xF, com.zomboidcraft.world.trains.TrainPhysics.INDOOR_SKY);
            state.inside[car] = Math.max(block, (lit >> 4) & 0xF) << 4 | sky << 20;
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

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        for (int car = 0; car < state.cars; car++) {
            TrainMesh mesh = TrainMesh.get(state.kind, car == 0, car == state.cars - 1);
            int light = state.light[car], inside = state.inside[car];
            poseStack.pushPose();
            poseStack.translate(state.at[car * 4], state.at[car * 4 + 1], state.at[car * 4 + 2]);
            poseStack.rotateDegrees(Axis.YP, -state.at[car * 4 + 3]);
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(ATLAS),
                    (pose, out) -> draw(mesh, state, pose, out, light, inside, false));
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(ATLAS),
                    (pose, out) -> draw(mesh, state, pose, out, light, inside, true));
            poseStack.popPose();
        }
    }

    private static void draw(TrainMesh mesh, State state, PoseStack.Pose pose, VertexConsumer out, int light, int inside, boolean glass) {
        float slide = state.doors * 0.95f;
        float headCos = (float) Math.cos(Math.toRadians(state.headYaw)), headSin = (float) Math.sin(Math.toRadians(state.headYaw));
        float[] v = new float[3], n = new float[3];
        for (TrainMesh.Quad q : mesh.quads) {
            if (q.glass != glass) {
                continue;
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
            int lit = q.light == TrainMesh.GLOW ? FULL : q.light == TrainMesh.INSIDE ? inside : light;
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
