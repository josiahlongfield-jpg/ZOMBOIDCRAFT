package com.zomboidcraft.client.zombie.body;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZcConfig;
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
 * leave off the smallest clothing details so a horde stays cheap.
 */
public class ZBodyLayer extends RenderLayer<WalkerRenderState, WalkerModel> {
    /** Preview units to model pixels: the 35.2-tall preview figure stands 32 pixels (two blocks). */
    private static final float K = 32f / 35.2f;
    /** Body tint under the clothes, as the preview draws it. */
    private static final int BODY_TINT = 0xFF9D978C;
    private static final int FULL_BRIGHT = 0xF000F0;
    /** Quad corners in drawing order (counter-clockwise from outside). */
    private static final int[] ORDER = {0, 3, 2, 1};
    /** The eye mask covers the head's front face, just proud of it: head-local x, y, z and u, v per corner. */
    private static final float[][] EYES = {{-3.2f, 7.2f, 3.35f, 0f, 0f}, {-3.2f, 0f, 3.35f, 0f, 1f},
            {3.2f, 0f, 3.35f, 1f, 1f}, {3.2f, 7.2f, 3.35f, 1f, 0f}};

    private final Matrix4f[] world = new Matrix4f[ZMove.BONES];

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
        float[] mats = matrices(mesh, state.zpose, legless);
        int hidden = hiddenBones(state.missingLimb, legless);
        double detail = Math.max(1.0, ZcConfig.get().look.zombieDetailDistance);
        double dist = state.distanceToCameraSq;
        int lod = dist < detail * detail ? 0 : dist < detail * detail * 6.25 ? 1 : 2;
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0f);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(state.zskin),
                (pose, out) -> emit(mesh, mats, pose, out, true, hidden, lod, legless, light, overlay, BODY_TINT));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(state.zkit),
                (pose, out) -> emit(mesh, mats, pose, out, false, hidden, lod, legless, light, overlay, -1));
        if (state.zeyes != null) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(state.zeyes),
                    (pose, out) -> eyes(mats, pose, out));
        }
    }

    /** 0 none, 1 right forearm, 2 left forearm, 3 right arm, 4 left arm; no legs takes the shins and feet. */
    private static int hiddenBones(int missingLimb, boolean legless) {
        int hidden = switch (missingLimb) {
            case 1 -> bit(ZMove.EL_R) | bit(ZMove.WR_R);
            case 2 -> bit(ZMove.EL_L) | bit(ZMove.WR_L);
            case 3 -> bit(ZMove.SH_R) | bit(ZMove.EL_R) | bit(ZMove.WR_R);
            case 4 -> bit(ZMove.SH_L) | bit(ZMove.EL_L) | bit(ZMove.WR_L);
            default -> 0;
        };
        if (legless) {
            hidden |= bit(ZMove.KN_R) | bit(ZMove.AN_R) | bit(ZMove.KN_L) | bit(ZMove.AN_L);
        }
        return hidden;
    }

    private static int bit(int bone) {
        return 1 << bone;
    }

    /**
     * Each bone's matrix from its own space to the walker model's (column major, 16 floats a bone),
     * built like the preview's scene graph: parent, then this bone's offset, turn and stretch.
     */
    private float[] matrices(ZBodyMesh mesh, ZMove.Pose p, boolean legless) {
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

    private static void emit(ZBodyMesh m, float[] a, PoseStack.Pose pose, VertexConsumer out, boolean body, int hidden,
            int lod, boolean legless, int light, int overlay, int colour) {
        for (int q = 0; q < m.count; q++) {
            int f = m.flags[q];
            if (((f & ZBodyMesh.BODY) != 0) != body || (hidden >> m.bone[q] & 1) != 0
                    || ((f & ZBodyMesh.STUMP) != 0 && !legless)) {
                continue;
            }
            if (!body && lod > 0 && ((f & ZBodyMesh.SMALL) != 0 || (lod > 1 && m.minEdge[q] < 1f))) {
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
                out.addVertex(pose, a[o] * x + a[o + 4] * y + a[o + 8] * z + a[o + 12],
                                a[o + 1] * x + a[o + 5] * y + a[o + 9] * z + a[o + 13],
                                a[o + 2] * x + a[o + 6] * y + a[o + 10] * z + a[o + 14])
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
