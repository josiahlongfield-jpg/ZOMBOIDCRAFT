package com.zomboidcraft.client.zombie.body;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.combat.Dismember;
import com.zomboidcraft.client.zombie.WalkerModel;
import com.zomboidcraft.client.zombie.WalkerRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

/**
 * Draws the new zombie: body A in its per-type skin, the worn 3D clothes from the outfit's atlas,
 * and the red eyes, every quad hung off its bone and posed by {@link ZMove}. Zombies further off
 * leave off the smallest clothing details so a horde stays cheap ({@link ZDetail}). Limbs that have been shot off (or were lost
 * before it rose) are left out, with a torn stump where they were.
 */
public class ZBodyLayer extends RenderLayer<WalkerRenderState, WalkerModel> {
    /** Preview units to model pixels: the 35.2-tall preview figure stands 32 pixels (two blocks). */
    static final float K = 32f / 35.2f;
    /** Body tint under the clothes, as the preview draws it. */
    static final int BODY_TINT = 0xFF9D978C;
    // body A sizes where a stump goes: forearm, shin and thigh thickness, upper arm, and the torso's half width and
    // the height of its shoulders
    static final float FORE_W = 2.5f, SHIN_W = 3.0f, THIGH_W = 3.4f, UPPER_W = 2.7f, TORSO_HALF = 3.8f, SHOULDER_Y = 7.6f;
    private static final int FULL_BRIGHT = 0xF000F0;
    /** Quad corners in drawing order (counter-clockwise from outside). */
    private static final int[] ORDER = {0, 3, 2, 1};
    /** The eye mask covers the head's front face, just proud of it: head-local x, y, z and u, v per corner. */
    private static final float[][] EYES = {{-3.2f, 7.2f, 3.35f, 0f, 0f}, {-3.2f, 0f, 3.35f, 0f, 1f},
            {3.2f, 0f, 3.35f, 1f, 1f}, {3.2f, 7.2f, 3.35f, 1f, 0f}};

    private final Matrix4f[] world = new Matrix4f[ZMove.BONES];
    private final Matrix4f at = new Matrix4f();
    private final Gore gore = new Gore();

    public ZBodyLayer(RenderLayerParent<WalkerRenderState, WalkerModel> parent) {
        super(parent);
        for (int i = 0; i < world.length; i++) {
            world[i] = new Matrix4f();
        }
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, WalkerRenderState state,
            float yRot, float xRot) {
        ZBodyMesh mesh = state.zbody;
        if (mesh == null || state.isInvisible) {
            return;
        }
        boolean legless = state.legless;
        float[] mats = matrices(mesh, state.zpose, legless, world);
        int stumps = stumps(state.cuts, state.missingLimb);
        int hidden = hiddenBones(stumps, legless);
        int lod = ZDetail.level(state.distanceToCameraSq);
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0f);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(state.zskin),
                (pose, out) -> emit(mesh, mats, pose, out, true, hidden, lod, legless, light, overlay, BODY_TINT));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(state.zkit),
                (pose, out) -> emit(mesh, mats, pose, out, false, hidden, lod, legless, light, overlay, -1));
        if (state.zeyes != null) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(state.zeyes),
                    (pose, out) -> eyes(mats, pose, out));
        }
        if (stumps != 0 && lod < 2) {
            int seed = state.variant;
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(Gore.TEXTURE),
                    (pose, out) -> stumps(mats, stumps, seed, pose, out, light, overlay));
        }
    }

    /**
     * The limbs it hasn't got, as cut bits: shot off, or lost before it rose (0 none, 1 right forearm, 2 left
     * forearm, 3 right arm, 4 left arm). A whole arm or leg gone counts only at the upper joint.
     */
    static int stumps(int cuts, int missingLimb) {
        cuts |= missingCuts(missingLimb);
        if ((cuts & Dismember.SH_R) != 0) {
            cuts &= ~Dismember.EL_R;
        }
        if ((cuts & Dismember.SH_L) != 0) {
            cuts &= ~Dismember.EL_L;
        }
        if ((cuts & Dismember.HIP_R) != 0) {
            cuts &= ~Dismember.KN_R;
        }
        if ((cuts & Dismember.HIP_L) != 0) {
            cuts &= ~Dismember.KN_L;
        }
        return cuts;
    }

    /** A limb lost before it rose, as a cut bit (0 none, 1 right forearm, 2 left forearm, 3 right arm, 4 left arm). */
    public static int missingCuts(int missingLimb) {
        return switch (missingLimb) {
            case 1 -> Dismember.EL_R;
            case 2 -> Dismember.EL_L;
            case 3 -> Dismember.SH_R;
            case 4 -> Dismember.SH_L;
            default -> 0;
        };
    }

    /** Bone matrices (walker model space) for a pose, freshly made. */
    public static float[] pose(ZBodyMesh mesh, ZMove.Pose p, boolean legless) {
        Matrix4f[] world = new Matrix4f[ZMove.BONES];
        for (int i = 0; i < world.length; i++) {
            world[i] = new Matrix4f();
        }
        return matrices(mesh, p, legless, world);
    }

    /** The bones a cut takes with it. */
    static int bonesOf(int cut) {
        return switch (cut) {
            case Dismember.EL_R -> bit(ZMove.EL_R) | bit(ZMove.WR_R);
            case Dismember.EL_L -> bit(ZMove.EL_L) | bit(ZMove.WR_L);
            case Dismember.SH_R -> bit(ZMove.SH_R) | bit(ZMove.EL_R) | bit(ZMove.WR_R);
            case Dismember.SH_L -> bit(ZMove.SH_L) | bit(ZMove.EL_L) | bit(ZMove.WR_L);
            case Dismember.KN_R -> bit(ZMove.KN_R) | bit(ZMove.AN_R);
            case Dismember.KN_L -> bit(ZMove.KN_L) | bit(ZMove.AN_L);
            case Dismember.HIP_R -> bit(ZMove.HIP_R) | bit(ZMove.KN_R) | bit(ZMove.AN_R);
            case Dismember.HIP_L -> bit(ZMove.HIP_L) | bit(ZMove.KN_L) | bit(ZMove.AN_L);
            default -> 0;
        };
    }

    /** Everything left out: the missing limbs, and with no legs the shins and feet. */
    static int hiddenBones(int stumps, boolean legless) {
        int hidden = 0;
        for (int cut = 1; cut <= Dismember.CUTS; cut <<= 1) {
            if ((stumps & cut) != 0) {
                hidden |= bonesOf(cut);
            }
        }
        if (legless) {
            hidden |= bit(ZMove.KN_R) | bit(ZMove.AN_R) | bit(ZMove.KN_L) | bit(ZMove.AN_L);
        }
        return hidden;
    }

    static int bit(int bone) {
        return 1 << bone;
    }

    /**
     * A torn stump on the bone above each missing limb, at the joint it came off: elbow and knee stumps hang
     * where the forearm or shin was, a hip stump under the pelvis, a shoulder stump on the side of the chest.
     */
    private void stumps(float[] mats, int stumps, int seed, PoseStack.Pose pose, VertexConsumer out, int light, int overlay) {
        for (int side = 0; side < 2; side++) {
            boolean right = side == 0;
            float s = right ? -1f : 1f;
            if ((stumps & (right ? Dismember.SH_R : Dismember.SH_L)) != 0) {
                at.set(mats, ZMove.SPINE * 16).translate(s * (TORSO_HALF + 0.5f), SHOULDER_Y, 0f)
                        .rotateZ(s * (float) Math.PI / 2);
                gore.rag(out, pose, at, UPPER_W + 0.4f, true, seed + side, light, overlay);
            } else if ((stumps & (right ? Dismember.EL_R : Dismember.EL_L)) != 0) {
                at.set(mats, (right ? ZMove.SH_R : ZMove.SH_L) * 16).translate(0f, -4.5f, 0f);
                gore.rag(out, pose, at, FORE_W, true, seed + 2 + side, light, overlay);
            }
            if ((stumps & (right ? Dismember.HIP_R : Dismember.HIP_L)) != 0) {
                // just under the pelvis, where the top of the thigh tore away
                at.set(mats, ZMove.HIPS * 16).translate(s * 1.82f, -2.0f, 0f);
                gore.rag(out, pose, at, THIGH_W, true, seed + 4 + side, light, overlay);
            } else if ((stumps & (right ? Dismember.KN_R : Dismember.KN_L)) != 0) {
                at.set(mats, (right ? ZMove.HIP_R : ZMove.HIP_L) * 16).translate(0f, -7.2f, 0f);
                gore.rag(out, pose, at, SHIN_W, true, seed + 6 + side, light, overlay);
            }
        }
    }

    /**
     * Each bone's matrix from its own space to the walker model's (column major, 16 floats a bone),
     * built like the preview's scene graph: parent, then this bone's offset, turn and stretch.
     */
    static float[] matrices(ZBodyMesh mesh, ZMove.Pose p, boolean legless, Matrix4f[] world) {
        float[] mats = new float[ZMove.BONES * 16];
        for (int b = 0; b < ZMove.BONES; b++) {
            Matrix4f m = world[b];
            float rx = p.rot[b * 3], ry = p.rot[b * 3 + 1], rz = p.rot[b * 3 + 2];
            if (b == ZMove.HIPS) {
                // preview y up, front +z; the walker model has y down, front -z, feet at 24 pixels
                m.translation(0f, (24f + 0.4f * K) / 16f, 0f).scale(K / 16f, -K / 16f, -K / 16f);
                m.translate(p.hx, p.hy, p.hz);
                if (p.lying) {
                    m.rotateXYZ(rx, ry, rz);
                } else {
                    m.rotateYXZ(ry, rx, rz);
                }
            } else {
                m.set(world[mesh.parent[b]]);
                m.translate(mesh.rest[b * 3], mesh.rest[b * 3 + 1], mesh.rest[b * 3 + 2]);
                m.rotateXYZ(rx, ry, rz);
                if (legless && (b == ZMove.HIP_R || b == ZMove.HIP_L)) {
                    m.scale(1f, 0.34f, 1f);
                }
            }
            m.get(mats, b * 16);
        }
        return mats;
    }

    static void emit(ZBodyMesh m, float[] a, PoseStack.Pose pose, VertexConsumer out, boolean body, int hidden,
            int lod, boolean legless, int light, int overlay, int colour) {
        // each bone's matrix with the pose stack's folded in, so a corner costs one transform instead of two
        float[] w = new float[a.length];
        Matrix4f bone = new Matrix4f(), stack = pose.pose();
        for (int b = 0; b * 16 < a.length; b++) {
            stack.mul(bone.set(a, b * 16), bone).get(w, b * 16);
        }
        for (int q = 0; q < m.count; q++) {
            int f = m.flags[q];
            if (((f & ZBodyMesh.BODY) != 0) != body || (hidden >> m.bone[q] & 1) != 0
                    || ((f & ZBodyMesh.STUMP) != 0 && !legless)) {
                continue;
            }
            if (!body && lod > ZDetail.FULL && ((f & ZBodyMesh.SMALL) != 0
                    || (lod >= ZDetail.LOW && m.minEdge[q] < (lod == ZDetail.SIMPLE ? 2f : 1f)))) {
                continue;
            }
            int o = m.bone[q] * 16;
            float nx0 = m.normal[q * 3], ny0 = m.normal[q * 3 + 1], nz0 = m.normal[q * 3 + 2];
            float nx = a[o] * nx0 + a[o + 4] * ny0 + a[o + 8] * nz0;
            float ny = a[o + 1] * nx0 + a[o + 5] * ny0 + a[o + 9] * nz0;
            float nz = a[o + 2] * nx0 + a[o + 6] * ny0 + a[o + 10] * nz0;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 0f) {
                nx /= len;
                ny /= len;
                nz /= len;
            }
            float u0 = m.uv[q * 4], v0 = m.uv[q * 4 + 1], u1 = m.uv[q * 4 + 2], v1 = m.uv[q * 4 + 3];
            for (int c : ORDER) {
                int i = q * 12 + c * 3;
                float x = m.pos[i], y = m.pos[i + 1], z = m.pos[i + 2];
                out.addVertex(w[o] * x + w[o + 4] * y + w[o + 8] * z + w[o + 12],
                                w[o + 1] * x + w[o + 5] * y + w[o + 9] * z + w[o + 13],
                                w[o + 2] * x + w[o + 6] * y + w[o + 10] * z + w[o + 14])
                        .setColor(colour)
                        .setUv(c == 1 || c == 2 ? u1 : u0, c >= 2 ? v1 : v0)
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(pose, nx, ny, nz);
            }
        }
    }

    private static void eyes(float[] a, PoseStack.Pose pose, VertexConsumer out) {
        int o = ZMove.HEAD * 16;
        float nx = a[o + 8], ny = a[o + 9], nz = a[o + 10];
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        for (float[] c : EYES) {
            out.addVertex(pose, a[o] * c[0] + a[o + 4] * c[1] + a[o + 8] * c[2] + a[o + 12],
                            a[o + 1] * c[0] + a[o + 5] * c[1] + a[o + 9] * c[2] + a[o + 13],
                            a[o + 2] * c[0] + a[o + 6] * c[1] + a[o + 10] * c[2] + a[o + 14])
                    .setColor(-1)
                    .setUv(c[3], c[4])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(FULL_BRIGHT)
                    .setNormal(pose, nx / len, ny / len, nz / len);
        }
    }
}
