package com.zomboidcraft.client.zombie.body;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.client.zombie.WalkerModel;
import com.zomboidcraft.client.zombie.WalkerRenderState;
import com.zomboidcraft.combat.StuckBolts;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Crossbow bolts standing in a zombie ({@link StuckBolts}): each one on the part it struck, posed with that part's
 * bones, so a bolt in an arm swings with the arm. A 20" carbon shaft buried point first, two red hen vanes and the
 * white cock vane near its orange nock.
 */
public class BoltsLayer extends RenderLayer<WalkerRenderState, WalkerModel> {
    private static final Identifier TEXTURE = ZomboidCraft.id("textures/entity/zbody/bolt.png");
    /** The bolt's length, shaft half width and how deep it goes in at most (blocks; the figure stands two). */
    private static final double LENGTH = 0.535, HALF = 0.013, DEPTH = 0.15;
    /** The vanes from the nock: where they start and end, and how tall they stand at the back and front. */
    private static final double VANE_FROM = 0.027, VANE_TO = 0.134, VANE_BACK = 0.032, VANE_FRONT = 0.010;
    private static final double NOCK = 0.016;
    /** The texture's stripes: carbon, red, white, orange. */
    private static final int CARBON = 0, RED = 1, WHITE = 2, ORANGE = 3;
    /** Head and torso parts as the server's capsules lie on the bones: the head's length, a torso half's offset. */
    private static final double HEAD_LEN = 0.204, TORSO_OFF = 0.097;

    public BoltsLayer(RenderLayerParent<WalkerRenderState, WalkerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, WalkerRenderState state,
            float yRot, float xRot) {
        List<StuckBolts.Bolt> bolts = state.bolts;
        if (bolts.isEmpty() || state.zbody == null || state.isInvisible) {
            return;
        }
        float[] mats = ZBodyLayer.pose(state.zbody, state.zpose, state.legless);
        int hidden = ZBodyLayer.hiddenBones(ZBodyLayer.stumps(state.cuts, state.missingLimb), state.legless);
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0f);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (pose, out) -> {
            for (StuckBolts.Bolt bolt : bolts) {
                if (bolt.part() >= 0 && bolt.part() < 12 && (hidden >> bone(bolt.part()) & 1) == 0) {
                    draw(mats, bolt, pose, out, light, overlay);
                }
            }
        });
    }

    /** The bone a part hangs from (a bolt in it goes when that bone's hidden). */
    private static int bone(int part) {
        return switch (part) {
            case 0 -> ZMove.HEAD;
            case 1, 2 -> ZMove.SPINE;
            case 3 -> ZMove.HIPS;
            case 4 -> ZMove.SH_R;
            case 5 -> ZMove.EL_R;
            case 6 -> ZMove.SH_L;
            case 7 -> ZMove.EL_L;
            case 8 -> ZMove.HIP_R;
            case 9 -> ZMove.KN_R;
            case 10 -> ZMove.HIP_L;
            default -> ZMove.KN_L;
        };
    }

    private static void draw(float[] m, StuckBolts.Bolt bolt, PoseStack.Pose pose, VertexConsumer out, int light, int overlay) {
        // the part's line on the bones as posed now (walker model space: blocks, y down, front -z), from its upper end
        Vec3 a, b;
        switch (bolt.part()) {
            case 0 -> {
                Vec3 up = axis(m, ZMove.HEAD, 4);
                a = origin(m, ZMove.HEAD).add(up.scale(0.02));
                b = a.add(up.scale(HEAD_LEN));
            }
            case 1, 2 -> {
                Vec3 side = axis(m, ZMove.SPINE, 0).scale(bolt.part() == 1 ? TORSO_OFF : -TORSO_OFF);
                a = origin(m, ZMove.SPINE).add(side);
                b = origin(m, ZMove.NECK).add(side);
            }
            case 3 -> {
                a = origin(m, ZMove.HIP_R);
                b = origin(m, ZMove.HIP_L);
            }
            default -> {
                int[] ends = {ZMove.SH_R, ZMove.EL_R, ZMove.WR_R, ZMove.SH_L, ZMove.EL_L, ZMove.WR_L,
                        ZMove.HIP_R, ZMove.KN_R, ZMove.AN_R, ZMove.HIP_L, ZMove.KN_L, ZMove.AN_L};
                int k = bolt.part() - 4, limb = k / 2, lower = k % 2;     // upper arm or thigh 0, forearm or shin 1
                a = origin(m, ends[limb * 3 + lower]);
                b = origin(m, ends[limb * 3 + lower + 1]);
            }
        }
        // worked in the zombie's frame (x left, y up, z ahead), as the server measured it, then back to the model's
        Vec3 u = flip(b.subtract(a));
        double len = u.length();
        if (len < 1e-6) {
            return;
        }
        Vec3 axis = u.scale(1 / len), f = StuckBolts.front(bolt.part(), axis), r = axis.cross(f);
        float radius = StuckBolts.RADIUS[bolt.part()];
        Vec3 radial = f.scale(Math.cos(bolt.round())).add(r.scale(Math.sin(bolt.round())));
        Vec3 at = a.add(flip(u.scale(bolt.along()).add(radial.scale(radius))));
        Vec3 d = flip(axis.scale(bolt.du()).add(f.scale(bolt.df())).add(r.scale(bolt.dr())));
        if (d.lengthSqr() < 1e-6) {
            return;
        }
        d = d.normalize();
        double depth = Math.min(DEPTH, 1.5 * radius);
        Vec3 tip = at.add(d.scale(depth)), nock = at.subtract(d.scale(LENGTH - depth));
        Vec3 p1 = d.cross(Math.abs(d.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize(), p2 = d.cross(p1);
        box(out, pose, nock.add(d.scale(NOCK)), tip, p1, p2, HALF, CARBON, light, overlay);
        box(out, pose, nock, nock.add(d.scale(NOCK)), p1, p2, HALF * 1.3, ORANGE, light, overlay);
        for (int i = 0; i < 3; i++) {
            double turn = Math.PI / 2 + i * 2 * Math.PI / 3;
            Vec3 e = p1.scale(Math.cos(turn)).add(p2.scale(Math.sin(turn)));
            Vec3 front = nock.add(d.scale(VANE_TO)), back = nock.add(d.scale(VANE_FROM));
            Vec3 c0 = front.add(e.scale(HALF)), c1 = back.add(e.scale(HALF)), c2 = back.add(e.scale(HALF + VANE_BACK)),
                    c3 = front.add(e.scale(HALF + VANE_FRONT));
            int colour = i == 0 ? WHITE : RED;
            Vec3 n = d.cross(e);
            quad(out, pose, c0, c1, c2, c3, n, colour, light, overlay);
            quad(out, pose, c3, c2, c1, c0, n.scale(-1), colour, light, overlay);
        }
    }

    /** A square rod from one end to the other, w half wide, its four sides facing out. */
    private static void box(VertexConsumer out, PoseStack.Pose pose, Vec3 from, Vec3 to, Vec3 p1, Vec3 p2, double w,
            int colour, int light, int overlay) {
        Vec3[] side = {p1, p2, p1.scale(-1), p2.scale(-1)};
        for (int i = 0; i < 4; i++) {
            Vec3 n = side[i], t = side[(i + 1) % 4];
            Vec3 c = n.scale(w);
            quad(out, pose, from.add(c).subtract(t.scale(w)), from.add(c).add(t.scale(w)), to.add(c).add(t.scale(w)),
                    to.add(c).subtract(t.scale(w)), n, colour, light, overlay);
        }
    }

    /** A flat-coloured quad facing n: wound counter-clockwise as seen from that side. */
    private static void quad(VertexConsumer out, PoseStack.Pose pose, Vec3 c0, Vec3 c1, Vec3 c2, Vec3 c3, Vec3 n,
            int colour, int light, int overlay) {
        Vec3[] c = {c0, c1, c2, c3};
        if (c1.subtract(c0).cross(c2.subtract(c0)).dot(n) < 0) {
            c = new Vec3[] {c3, c2, c1, c0};
        }
        float u = (colour * 4 + 2) / 16f;
        for (Vec3 v : c) {
            out.addVertex(pose, (float) v.x, (float) v.y, (float) v.z).setColor(-1).setUv(u, 0.5f).setOverlay(overlay)
                    .setLight(light).setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
        }
    }

    private static Vec3 origin(float[] m, int bone) {
        int o = bone * 16;
        return new Vec3(m[o + 12], m[o + 13], m[o + 14]);
    }

    /** One of a bone's axes in the model (column 0 its x, 4 its y), made unit. */
    private static Vec3 axis(float[] m, int bone, int column) {
        int o = bone * 16 + column;
        return new Vec3(m[o], m[o + 1], m[o + 2]).normalize();
    }

    /** Between the walker model's frame (y down, front -z) and the zombie's own (y up, front +z): a half turn about x. */
    private static Vec3 flip(Vec3 v) {
        return new Vec3(v.x, -v.y, -v.z);
    }
}
