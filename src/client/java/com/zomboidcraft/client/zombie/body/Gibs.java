package com.zomboidcraft.client.zombie.body;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.combat.Dismember;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Limbs shot off zombies, on this computer only: each comes away whole, frozen in the pose it was in with a torn
 * end of its own, flies along the shot (harder from a shotgun than a handgun), tumbles, bounces and settles flat on
 * the ground, and after a while is gone. As the preview does it (limbs.js), in blocks.
 */
public final class Gibs {
    /** Preview units to blocks. */
    private static final float UNIT = ZBodyLayer.K / 16f;
    private static final float GRAVITY = 160f * UNIT;
    /** How hard a limb is flung, by the gun's kind (handgun, rifle, hunting rifle, buckshot), preview units a second. */
    private static final float[] FLING = {45f, 60f, 80f, 95f};
    private static final Random RANDOM = new Random();

    private static final class Gib {
        ZBodyMesh mesh;
        Identifier skin, kit;
        /** Every bone not in this piece. */
        int hidden;
        /** Each bone's matrix from its own space to the piece's root bone (16 floats a bone). */
        final float[] mats = new float[ZMove.BONES * 16];
        /** The torn end, in the root bone's space, and how it looks. */
        final Matrix4f end = new Matrix4f();
        float endWidth;
        boolean endBone;
        int seed;
        /** The piece's extent in its root bone's space: eight corners, for landing on the ground. */
        final float[] corners = new float[24];
        double x, y, z, vx, vy, vz;
        final Quaternionf rot = new Quaternionf();
        float scale;
        final Vector3f spin = new Vector3f();
        /** 0 flying, then settling flat up to 1, then lying still. */
        float rest;
        float age;
    }

    private static final List<Gib> GIBS = new ArrayList<>();
    private static final Gore GORE = new Gore();
    private static final Matrix4f M = new Matrix4f(), INV = new Matrix4f(), TURN = new Matrix4f();
    private static final Vector3f V = new Vector3f();
    private static final Quaternionf Q = new Quaternionf();
    private static ClientLevel level;
    private static long lastFrame;

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(Gibs::draw);
    }

    /**
     * Throws the limbs that just came off a zombie.
     *
     * @param mats the zombie's bone matrices (walker model space) in the pose it was in when they came off
     * @param newly the limbs that just came off (cut bits)
     * @param gone what it had lost before, shot off or long ago (cut bits): not part of any piece
     * @param limbs the zombie's saved limbs value, with which way and how hard the shot sent them
     */
    public static void spawn(ZBodyMesh mesh, Identifier skin, Identifier kit, float[] mats, int newly, int gone,
                             int limbs, double x, double y, double z, float bodyRot, float entityScale, int seed) {
        float heading = (limbs >> 8 & 0xFF) / 256f * (float) Math.PI * 2f;
        float fling = FLING[limbs >> 16 & 3];
        int goneBones = ZBodyLayer.hiddenBones(ZBodyLayer.stumps(gone, 0), false);
        // the vanilla body turn: the walker model's space to the world's, about the zombie's feet
        Matrix4f model = new Matrix4f().rotationY((float) Math.toRadians(180f - bodyRot))
                .scale(-entityScale, -entityScale, entityScale).translate(0f, -1.501f, 0f);
        for (int cut : new int[] {Dismember.SH_R, Dismember.SH_L, Dismember.HIP_R, Dismember.HIP_L,
                Dismember.EL_R, Dismember.EL_L, Dismember.KN_R, Dismember.KN_L}) {
            if ((newly & cut) == 0 || (newly & above(cut)) != 0) {
                continue;   // a whole limb that came off takes its lower half with it, in one piece
            }
            int bones = ZBodyLayer.bonesOf(cut) & ~goneBones;
            int root = rootOf(cut);
            if ((bones >> root & 1) == 0) {
                continue;
            }
            Gib g = new Gib();
            g.mesh = mesh;
            g.skin = skin;
            g.kit = kit;
            g.hidden = ~bones;
            g.seed = seed * 31 + cut;
            INV.set(mats, root * 16).invert();
            for (int b = 0; b < ZMove.BONES; b++) {
                if ((bones >> b & 1) != 0) {
                    M.set(INV).mul(new Matrix4f().set(mats, b * 16)).get(g.mats, b * 16);
                }
            }
            boolean shoulder = cut == Dismember.SH_R || cut == Dismember.SH_L, hip = cut == Dismember.HIP_R || cut == Dismember.HIP_L;
            g.endWidth = shoulder ? ZBodyLayer.UPPER_W : hip ? ZBodyLayer.THIGH_W
                    : cut == Dismember.EL_R || cut == Dismember.EL_L ? ZBodyLayer.FORE_W : ZBodyLayer.SHIN_W;
            g.endBone = shoulder || hip;
            g.end.translation(0f, shoulder ? 0.6f : hip ? 0.3f : 0.1f, 0f).rotateX((float) Math.PI);
            extent(g, bones);
            // where the root bone is in the world, and how it's turned
            M.set(model).mul(new Matrix4f().set(mats, root * 16));
            g.x = x + M.m30();
            g.y = y + M.m31();
            g.z = z + M.m32();
            M.getNormalizedRotation(g.rot);
            g.scale = M.getScale(V).x;
            float speed = fling * UNIT * (0.8f + RANDOM.nextFloat() * 0.4f);
            g.vx = -Math.sin(heading) * speed;
            g.vz = Math.cos(heading) * speed;
            g.vy = (25f + RANDOM.nextFloat() * 25f) * UNIT;
            g.spin.set(RANDOM.nextFloat() - 0.5f, RANDOM.nextFloat() - 0.5f, RANDOM.nextFloat() - 0.5f).mul(14f);
            GIBS.add(g);
        }
        int max = Math.max(0, ZcConfig.get().limbs.maxLimbs);
        while (GIBS.size() > max) {
            GIBS.remove(0);
        }
    }

    private static int above(int cut) {
        return switch (cut) {
            case Dismember.EL_R -> Dismember.SH_R;
            case Dismember.EL_L -> Dismember.SH_L;
            case Dismember.KN_R -> Dismember.HIP_R;
            case Dismember.KN_L -> Dismember.HIP_L;
            default -> 0;
        };
    }

    private static int rootOf(int cut) {
        return switch (cut) {
            case Dismember.EL_R -> ZMove.EL_R;
            case Dismember.EL_L -> ZMove.EL_L;
            case Dismember.SH_R -> ZMove.SH_R;
            case Dismember.SH_L -> ZMove.SH_L;
            case Dismember.KN_R -> ZMove.KN_R;
            case Dismember.KN_L -> ZMove.KN_L;
            case Dismember.HIP_R -> ZMove.HIP_R;
            default -> ZMove.HIP_L;
        };
    }

    /** The box round every corner of the piece's body and clothes, in its root bone's space. */
    private static void extent(Gib g, int bones) {
        float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        ZBodyMesh m = g.mesh;
        for (int q = 0; q < m.count; q++) {
            if ((bones >> m.bone[q] & 1) == 0 || (m.flags[q] & ZBodyMesh.STUMP) != 0) {
                continue;
            }
            int o = m.bone[q] * 16;
            float[] a = g.mats;
            for (int c = 0; c < 4; c++) {
                int i = q * 12 + c * 3;
                float px = m.pos[i], py = m.pos[i + 1], pz = m.pos[i + 2];
                float[] w = {a[o] * px + a[o + 4] * py + a[o + 8] * pz + a[o + 12],
                        a[o + 1] * px + a[o + 5] * py + a[o + 9] * pz + a[o + 13],
                        a[o + 2] * px + a[o + 6] * py + a[o + 10] * pz + a[o + 14]};
                for (int k = 0; k < 3; k++) {
                    lo[k] = Math.min(lo[k], w[k]);
                    hi[k] = Math.max(hi[k], w[k]);
                }
            }
        }
        if (lo[0] > hi[0]) {
            lo = new float[] {-1, -6, -1};
            hi = new float[] {1, 1, 1};
        }
        for (int c = 0; c < 8; c++) {
            g.corners[c * 3] = (c & 1) == 0 ? lo[0] : hi[0];
            g.corners[c * 3 + 1] = (c & 2) == 0 ? lo[1] : hi[1];
            g.corners[c * 3 + 2] = (c & 4) == 0 ? lo[2] : hi[2];
        }
    }

    // ------------------------------------------------------------------ every frame

    private static void draw(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            GIBS.clear();
            level = mc.level;
        }
        CameraRenderState cam = context.levelState().cameraRenderState;
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        if (GIBS.isEmpty() || level == null || cam == null || cam.pos == null) {
            return;
        }
        if (mc.isPaused()) {
            dt = 0f;
        }
        float life = (float) Math.max(1, ZcConfig.get().limbs.limbSeconds);
        double detail = Math.max(1.0, ZcConfig.get().look.zombieDetailDistance);
        PoseStack stack = context.poseStack();
        Vec3 c = cam.pos;
        for (int i = GIBS.size() - 1; i >= 0; i--) {
            Gib g = GIBS.get(i);
            g.age += dt;
            if (g.age > life) {
                GIBS.remove(i);
                continue;
            }
            step(g, dt);
            // in its last two seconds it sinks out of sight
            double sink = Math.max(0, g.age - (life - 2f)) * 0.15;
            BlockPos at = BlockPos.containing(g.x, g.y + 0.1, g.z);
            int light = level.getBrightness(LightLayer.BLOCK, at) << 4 | level.getBrightness(LightLayer.SKY, at) << 20;
            double away = c.distanceToSqr(g.x, g.y, g.z);
            int lod = away < detail * detail ? 0 : 1;
            stack.pushPose();
            stack.translate((float) (g.x - c.x), (float) (g.y - sink - c.y), (float) (g.z - c.z));
            stack.mulPose(TURN.rotation(g.rot).scale(g.scale));
            int overlay = OverlayTexture.NO_OVERLAY;
            context.submitNodeCollector().submitCustomGeometry(stack, RenderTypes.entityCutout(g.skin),
                    (pose, out) -> ZBodyLayer.emit(g.mesh, g.mats, pose, out, true, g.hidden, lod, false, light, overlay,
                            ZBodyLayer.BODY_TINT));
            context.submitNodeCollector().submitCustomGeometry(stack, RenderTypes.entityCutout(g.kit),
                    (pose, out) -> ZBodyLayer.emit(g.mesh, g.mats, pose, out, false, g.hidden, lod, false, light, overlay, -1));
            context.submitNodeCollector().submitCustomGeometry(stack, RenderTypes.entityCutout(Gore.TEXTURE),
                    (pose, out) -> GORE.rag(out, pose, g.end, g.endWidth, g.endBone, g.seed, light, overlay));
            stack.popPose();
        }
    }

    /** Flies, tumbles and bounces, then eases flat along the ground and lies still. */
    private static void step(Gib g, float dt) {
        if (dt <= 0f || g.rest > 1f) {
            return;
        }
        if (g.rest > 0f) {
            // settling: turn the limb's length level with the ground
            Vector3f up = g.rot.transform(new Vector3f(0f, 1f, 0f));
            Vector3f flat = new Vector3f(up.x, 0f, up.z);
            if (flat.lengthSquared() > 1e-4f) {
                Q.identity().rotationTo(up, flat.normalize());
                new Quaternionf().slerp(Q, Math.min(1f, dt * 6f), Q);
                g.rot.premul(Q);
            }
            g.rest += dt * 1.5f;
        } else {
            double ox = g.x, oz = g.z;
            g.vy -= GRAVITY * dt;
            g.x += g.vx * dt;
            g.y += g.vy * dt;
            g.z += g.vz * dt;
            if (solid(g.x, g.y, g.z)) {
                // into a wall: it drops down the face of it
                g.x = ox;
                g.z = oz;
                g.vx = 0;
                g.vz = 0;
            }
            float a = g.spin.length();
            if (a > 0f) {
                g.rot.premul(Q.identity().rotateAxis(a * dt, g.spin.x / a, g.spin.y / a, g.spin.z / a));
            }
        }
        // the lowest corner rests on the ground
        double low = Double.MAX_VALUE, lx = g.x, lz = g.z;
        for (int c = 0; c < 8; c++) {
            g.rot.transform(V.set(g.corners[c * 3], g.corners[c * 3 + 1], g.corners[c * 3 + 2])).mul(g.scale);
            if (g.y + V.y < low) {
                low = g.y + V.y;
                lx = g.x + V.x;
                lz = g.z + V.z;
            }
        }
        double ground = ground(lx, low, lz);
        if (low < ground) {
            g.y += ground - low;
            if (g.rest == 0f) {
                g.vy = Math.abs(g.vy) * 0.22;
                g.vx *= 0.55;
                g.vz *= 0.55;
                g.spin.mul(0.5f);
                if (Math.sqrt(g.vx * g.vx + g.vy * g.vy + g.vz * g.vz) < 9f * UNIT) {
                    g.rest = 0.01f;
                }
            }
        }
    }

    /** The top of whatever is underfoot at a point: the block it's in, or the one below. */
    private static double ground(double x, double y, double z) {
        BlockPos p = BlockPos.containing(x, y, z);
        VoxelShape in = level.getBlockState(p).getCollisionShape(level, p);
        if (!in.isEmpty()) {
            double top = p.getY() + in.max(Direction.Axis.Y);
            if (top > y) {
                return Math.min(top, y + 0.6);
            }
        }
        BlockPos below = p.below();
        VoxelShape under = level.getBlockState(below).getCollisionShape(level, below);
        return under.isEmpty() ? -Double.MAX_VALUE : below.getY() + under.max(Direction.Axis.Y);
    }

    private static boolean solid(double x, double y, double z) {
        BlockPos p = BlockPos.containing(x, y, z);
        VoxelShape s = level.getBlockState(p).getCollisionShape(level, p);
        return !s.isEmpty() && s.max(Direction.Axis.Y) + p.getY() > y + 0.3 && s.min(Direction.Axis.Y) + p.getY() < y;
    }

    private Gibs() {
    }
}
