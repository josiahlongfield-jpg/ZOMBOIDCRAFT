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
 *
 * A head that bursts goes the same way in pieces: bits of skull and scalp, brain and bone sprayed out along the shot,
 * and its hat (or hair) thrown off whole. A chest blown open throws rags of its clothes and bits of flesh and bone
 * out of its back.
 */
public final class Gibs {
    /** Preview units to blocks. */
    private static final float UNIT = ZBodyLayer.K / 16f;
    private static final float GRAVITY = 160f * UNIT;
    /** How hard a limb is flung, by the gun's kind (handgun, rifle, hunting rifle, buckshot), preview units a second. */
    private static final float[] FLING = {45f, 60f, 80f, 95f};
    private static final Random RANDOM = new Random();
    /** How many bits of skull and scalp, and of brain and bone, a burst head throws; most bits lying about at once. */
    private static final int SKULL_BITS = 8, GORE_BITS = 7, MAX_BITS = 150;
    /** How many rags of clothes (or skin), and bits of flesh and bone, a chest blown open throws. */
    private static final int RAG_BITS = 7, CHEST_GORE_BITS = 9;
    /** Seconds a bit of head lies about (at most the limbs' time). */
    private static final float BIT_SECONDS = 20f;

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
        /** Only the clothes of its bones: a hat or hair, without the head under it. No torn end either. */
        boolean kitOnly;
        /** A bit of a burst head instead of a piece of body: a cube this big, from this square of this texture. */
        Identifier tex;
        float[] uv;
        int colour = -1;
        float size;
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
        if ((newly & Dismember.HEAD_GONE) != 0) {
            head(mesh, skin, kit, mats, model, x, y, z, heading, fling, entityScale, seed);
        }
        if ((newly & Dismember.CHEST_HOLE) != 0) {
            chest(mesh, skin, kit, mats, model, x, y, z, heading, fling, entityScale);
        }
        trim();
    }

    /** Too many pieces lying about: the oldest limbs go first, and the oldest bits of head. */
    private static void trim() {
        int max = Math.max(0, ZcConfig.get().limbs.maxLimbs), limbs = 0, bits = 0;
        for (int i = GIBS.size() - 1; i >= 0; i--) {
            boolean bit = GIBS.get(i).tex != null;
            if (bit ? ++bits > MAX_BITS : ++limbs > max) {
                GIBS.remove(i);
            }
        }
    }

    /**
     * A head bursting: its hat or hair thrown off whole, and bits of skull and scalp (from its own skin), brain and
     * bone sprayed out along the shot.
     */
    private static void head(ZBodyMesh mesh, Identifier skin, Identifier kit, float[] mats, Matrix4f model,
                             double x, double y, double z, float heading, float fling, float entityScale, int seed) {
        int head = 1 << ZMove.HEAD;
        M.set(model).mul(new Matrix4f().set(mats, ZMove.HEAD * 16));
        float scale = M.getScale(V).x;
        M.transformPosition(V.set(0f, 3.6f, 0f));
        double hx = x + V.x, hy = y + V.y, hz = z + V.z;
        if (hasKit(mesh, head)) {
            Gib g = new Gib();
            g.mesh = mesh;
            g.skin = skin;
            g.kit = kit;
            g.hidden = ~head;
            g.kitOnly = true;
            g.seed = seed * 31 + 99;
            // its own space is the head bone's
            for (int k = 0; k < 16; k += 5) {
                g.mats[ZMove.HEAD * 16 + k] = 1f;
            }
            extent(g, head);
            g.x = x + M.m30();
            g.y = y + M.m31();
            g.z = z + M.m32();
            M.getNormalizedRotation(g.rot);
            g.scale = scale;
            float speed = fling * UNIT * (0.5f + RANDOM.nextFloat() * 0.3f);
            g.vx = -Math.sin(heading) * speed;
            g.vz = Math.cos(heading) * speed;
            g.vy = (45f + RANDOM.nextFloat() * 25f) * UNIT;
            g.spin.set(RANDOM.nextFloat() - 0.5f, RANDOM.nextFloat() - 0.5f, RANDOM.nextFloat() - 0.5f).mul(10f);
            GIBS.add(g);
        }
        List<float[]> faces = new ArrayList<>();
        for (int q = 0; q < mesh.count; q++) {
            if (mesh.bone[q] == ZMove.HEAD && (mesh.flags[q] & ZBodyMesh.BODY) != 0) {
                faces.add(new float[] {mesh.uv[q * 4], mesh.uv[q * 4 + 1], mesh.uv[q * 4 + 2], mesh.uv[q * 4 + 3]});
            }
        }
        for (int i = 0; i < SKULL_BITS + GORE_BITS; i++) {
            Gib g = new Gib();
            if (i < SKULL_BITS && !faces.isEmpty()) {
                // a patch of the head's own skin
                float[] f = faces.get(RANDOM.nextInt(faces.size()));
                float w = (f[2] - f[0]) * (0.3f + RANDOM.nextFloat() * 0.2f), h = (f[3] - f[1]) * (0.3f + RANDOM.nextFloat() * 0.2f);
                float u = f[0] + (f[2] - f[0] - w) * RANDOM.nextFloat(), v = f[1] + (f[3] - f[1] - h) * RANDOM.nextFloat();
                g.tex = skin;
                g.uv = new float[] {u, v, u + w, v + h};
                g.colour = ZBodyLayer.BODY_TINT;
            } else {
                g.tex = Gore.TEXTURE;
                g.uv = Gore.region(i % 3 == 0 ? Gore.BONE : i % 3 == 1 ? Gore.FLESH : Gore.SHRED);
            }
            g.size = 0.9f + RANDOM.nextFloat() * 1.4f;
            for (int c = 0; c < 8; c++) {
                g.corners[c * 3] = (c & 1) == 0 ? -g.size / 2 : g.size / 2;
                g.corners[c * 3 + 1] = (c & 2) == 0 ? -g.size / 2 : g.size / 2;
                g.corners[c * 3 + 2] = (c & 4) == 0 ? -g.size / 2 : g.size / 2;
            }
            g.x = hx + RANDOM.nextGaussian() * 0.06;
            g.y = hy + RANDOM.nextGaussian() * 0.06;
            g.z = hz + RANDOM.nextGaussian() * 0.06;
            g.scale = UNIT * entityScale;
            g.rot.rotationXYZ(RANDOM.nextFloat() * 6.28f, RANDOM.nextFloat() * 6.28f, RANDOM.nextFloat() * 6.28f);
            float speed = fling * UNIT * (0.4f + RANDOM.nextFloat() * 0.9f);
            double way = heading + RANDOM.nextGaussian() * 0.5;
            g.vx = -Math.sin(way) * speed;
            g.vz = Math.cos(way) * speed;
            g.vy = (15f + RANDOM.nextFloat() * 50f) * UNIT;
            g.spin.set(RANDOM.nextFloat() - 0.5f, RANDOM.nextFloat() - 0.5f, RANDOM.nextFloat() - 0.5f).mul(20f);
            GIBS.add(g);
        }
    }

    /** A chest blown open: rags of what it wore there (or its skin) and bits of flesh and bone, out of its back. */
    private static void chest(ZBodyMesh mesh, Identifier skin, Identifier kit, float[] mats, Matrix4f model,
                              double x, double y, double z, float heading, float fling, float entityScale) {
        M.set(model).mul(new Matrix4f().set(mats, ZMove.SPINE * 16));
        M.transformPosition(V.set(-0.1f, 6.45f, 0f));
        double cx = x + V.x, cy = y + V.y, cz = z + V.z;
        List<float[]> cloth = new ArrayList<>(), flesh = new ArrayList<>();
        for (int q = 0; q < mesh.count; q++) {
            if (mesh.bone[q] == ZMove.SPINE && Math.abs(mesh.normal[q * 3 + 2]) > 0.9f) {
                ((mesh.flags[q] & ZBodyMesh.BODY) != 0 ? flesh : cloth).add(
                        new float[] {mesh.uv[q * 4], mesh.uv[q * 4 + 1], mesh.uv[q * 4 + 2], mesh.uv[q * 4 + 3]});
            }
        }
        boolean worn = !cloth.isEmpty();
        List<float[]> rags = worn ? cloth : flesh;
        for (int i = 0; i < RAG_BITS + CHEST_GORE_BITS; i++) {
            Gib g = new Gib();
            if (i < RAG_BITS && !rags.isEmpty()) {
                float[] f = rags.get(RANDOM.nextInt(rags.size()));
                float w = (f[2] - f[0]) * (0.4f + RANDOM.nextFloat() * 0.3f), h = (f[3] - f[1]) * (0.4f + RANDOM.nextFloat() * 0.3f);
                float u = f[0] + (f[2] - f[0] - w) * RANDOM.nextFloat(), v = f[1] + (f[3] - f[1] - h) * RANDOM.nextFloat();
                g.tex = worn ? kit : skin;
                g.uv = new float[] {u, v, u + w, v + h};
                g.colour = worn ? -1 : ZBodyLayer.BODY_TINT;
            } else {
                g.tex = Gore.TEXTURE;
                g.uv = Gore.region(i % 3 == 0 ? Gore.BONE : i % 3 == 1 ? Gore.FLESH : Gore.SHRED);
            }
            g.size = 0.8f + RANDOM.nextFloat() * 1.3f;
            for (int c = 0; c < 8; c++) {
                g.corners[c * 3] = (c & 1) == 0 ? -g.size / 2 : g.size / 2;
                g.corners[c * 3 + 1] = (c & 2) == 0 ? -g.size / 2 : g.size / 2;
                g.corners[c * 3 + 2] = (c & 4) == 0 ? -g.size / 2 : g.size / 2;
            }
            g.x = cx + RANDOM.nextGaussian() * 0.08;
            g.y = cy + RANDOM.nextGaussian() * 0.08;
            g.z = cz + RANDOM.nextGaussian() * 0.08;
            g.scale = UNIT * entityScale;
            g.rot.rotationXYZ(RANDOM.nextFloat() * 6.28f, RANDOM.nextFloat() * 6.28f, RANDOM.nextFloat() * 6.28f);
            float speed = fling * UNIT * (0.5f + RANDOM.nextFloat() * 0.9f);
            double way = heading + RANDOM.nextGaussian() * 0.45;
            g.vx = -Math.sin(way) * speed;
            g.vz = Math.cos(way) * speed;
            g.vy = (10f + RANDOM.nextFloat() * 40f) * UNIT;
            g.spin.set(RANDOM.nextFloat() - 0.5f, RANDOM.nextFloat() - 0.5f, RANDOM.nextFloat() - 0.5f).mul(20f);
            GIBS.add(g);
        }
    }

    private static boolean hasKit(ZBodyMesh mesh, int bones) {
        for (int q = 0; q < mesh.count; q++) {
            if ((bones >> mesh.bone[q] & 1) != 0 && (mesh.flags[q] & ZBodyMesh.BODY) == 0) {
                return true;
            }
        }
        return false;
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
            if ((bones >> m.bone[q] & 1) == 0 || (m.flags[q] & ZBodyMesh.STUMP) != 0
                    || g.kitOnly && (m.flags[q] & ZBodyMesh.BODY) != 0) {
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
            float lasts = g.tex != null ? Math.min(life, BIT_SECONDS) : life;
            if (g.age > lasts) {
                GIBS.remove(i);
                continue;
            }
            step(g, dt);
            // in its last two seconds it sinks out of sight
            double sink = Math.max(0, g.age - (lasts - 2f)) * 0.15;
            BlockPos at = BlockPos.containing(g.x, g.y + 0.1, g.z);
            int light = level.getBrightness(LightLayer.BLOCK, at) << 4 | level.getBrightness(LightLayer.SKY, at) << 20;
            double away = c.distanceToSqr(g.x, g.y, g.z);
            int lod = away < detail * detail ? 0 : 1;
            stack.pushPose();
            stack.translate((float) (g.x - c.x), (float) (g.y - sink - c.y), (float) (g.z - c.z));
            stack.mulPose(TURN.rotation(g.rot).scale(g.scale));
            int overlay = OverlayTexture.NO_OVERLAY;
            if (g.tex != null) {
                context.submitNodeCollector().submitCustomGeometry(stack, RenderTypes.entityCutout(g.tex),
                        (pose, out) -> GORE.cube(out, pose, g.size, g.uv, g.colour, light, overlay));
                stack.popPose();
                continue;
            }
            if (!g.kitOnly) {
                context.submitNodeCollector().submitCustomGeometry(stack, RenderTypes.entityCutout(g.skin),
                        (pose, out) -> ZBodyLayer.emit(g.mesh, g.mats, pose, out, true, g.hidden, lod, false, light, overlay,
                                ZBodyLayer.BODY_TINT));
            }
            context.submitNodeCollector().submitCustomGeometry(stack, RenderTypes.entityCutout(g.kit),
                    (pose, out) -> ZBodyLayer.emit(g.mesh, g.mats, pose, out, false, g.hidden, lod, false, light, overlay, -1));
            if (!g.kitOnly) {
                context.submitNodeCollector().submitCustomGeometry(stack, RenderTypes.entityCutout(Gore.TEXTURE),
                        (pose, out) -> GORE.rag(out, pose, g.end, g.endWidth, g.endBone, g.seed, light, overlay));
            }
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
