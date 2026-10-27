package com.zomboidcraft.client.zombie.body;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.combat.Dismember;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

/**
 * Draws one new-style body in a pose: the skin, the worn clothes (or the baked far-off body), the eyes, and torn
 * stumps where limbs are gone. For a walking zombie (ZBodyLayer) and for a dead one's body lying on the ground
 * (CorpseRenderer). Render thread only.
 */
public final class ZBodyDraw {
    private final Matrix4f[] world = new Matrix4f[ZMove.BONES];
    private final Matrix4f at = new Matrix4f();
    private final Gore gore = new Gore();

    public ZBodyDraw() {
        for (int i = 0; i < world.length; i++) {
            world[i] = new Matrix4f();
        }
    }

    /**
     * @param low the outfit's baked far-off body, or null
     * @param eyes the eye mask, or null for none (turned off, or dead)
     * @param cuts limbs shot off, with Dismember.HEAD_GONE
     * @param missingLimb a limb lost before it rose (ZombieLook.missingLimb)
     * @param lod how much detail (ZDetail)
     */
    public void draw(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, ZBodyMesh mesh,
                     ZBodyMesh low, Identifier skin, Identifier kit, Identifier lowSkin, Identifier eyes,
                     ZMove.Pose pose, int cuts, int missingLimb, boolean legless, int variant, int lod) {
        float[] mats = ZBodyLayer.matrices(mesh, pose, legless, world);
        int stumps = ZBodyLayer.stumps(cuts, missingLimb);
        int hidden = ZBodyLayer.hiddenBones(stumps, legless);
        if (lod >= ZDetail.LOW && low != null) {
            // the baked body: its own atlas already carries the skin's tint and the clothes painted over it
            int kitLod = lod == ZDetail.SIMPLE ? ZDetail.SIMPLE : ZDetail.FULL;
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(lowSkin),
                    (p, out) -> ZBodyLayer.emit(low, mats, p, out, true, hidden, ZDetail.FULL, legless, light, overlay, -1));
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(kit),
                    (p, out) -> ZBodyLayer.emit(low, mats, p, out, false, hidden, kitLod, legless, light, overlay, -1));
        } else {
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(skin),
                    (p, out) -> ZBodyLayer.emit(mesh, mats, p, out, true, hidden, lod, legless, light, overlay,
                            ZBodyLayer.BODY_TINT));
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(kit),
                    (p, out) -> ZBodyLayer.emit(mesh, mats, p, out, false, hidden, lod, legless, light, overlay, -1));
        }
        if (eyes != null && (cuts & Dismember.HEAD_GONE) == 0) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(eyes),
                    (p, out) -> ZBodyLayer.eyes(mats, p, out));
        }
        if (stumps != 0 && lod < 2) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(Gore.TEXTURE),
                    (p, out) -> rags(mats, stumps, variant, p, out, light, overlay));
        }
    }

    /**
     * A torn stump on the bone above each missing limb, at the joint it came off: elbow and knee stumps hang
     * where the forearm or shin was, a hip stump under the pelvis, a shoulder stump on the side of the chest; and
     * where the head burst, the neck torn open round a stub of spine.
     */
    private void rags(float[] mats, int stumps, int seed, PoseStack.Pose pose, VertexConsumer out, int light, int overlay) {
        if ((stumps & Dismember.HEAD_GONE) != 0) {
            at.set(mats, ZMove.HEAD * 16).translate(0f, 0.6f, 0f).rotateZ((float) Math.PI);
            gore.rag(out, pose, at, ZBodyLayer.NECK_W, true, seed + 8, light, overlay);
        }
        for (int side = 0; side < 2; side++) {
            boolean right = side == 0;
            float s = right ? -1f : 1f;
            if ((stumps & (right ? Dismember.SH_R : Dismember.SH_L)) != 0) {
                at.set(mats, ZMove.SPINE * 16).translate(s * (ZBodyLayer.TORSO_HALF + 0.5f), ZBodyLayer.SHOULDER_Y, 0f)
                        .rotateZ(s * (float) Math.PI / 2);
                gore.rag(out, pose, at, ZBodyLayer.UPPER_W + 0.4f, true, seed + side, light, overlay);
            } else if ((stumps & (right ? Dismember.EL_R : Dismember.EL_L)) != 0) {
                at.set(mats, (right ? ZMove.SH_R : ZMove.SH_L) * 16).translate(0f, -4.5f, 0f);
                gore.rag(out, pose, at, ZBodyLayer.FORE_W, true, seed + 2 + side, light, overlay);
            }
            if ((stumps & (right ? Dismember.HIP_R : Dismember.HIP_L)) != 0) {
                // just under the pelvis, where the top of the thigh tore away
                at.set(mats, ZMove.HIPS * 16).translate(s * 1.82f, -2.0f, 0f);
                gore.rag(out, pose, at, ZBodyLayer.THIGH_W, true, seed + 4 + side, light, overlay);
            } else if ((stumps & (right ? Dismember.KN_R : Dismember.KN_L)) != 0) {
                at.set(mats, (right ? ZMove.HIP_R : ZMove.HIP_L) * 16).translate(0f, -7.2f, 0f);
                gore.rag(out, pose, at, ZBodyLayer.SHIN_W, true, seed + 6 + side, light, overlay);
            }
        }
    }
}
