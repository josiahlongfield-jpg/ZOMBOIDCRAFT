package com.zomboidcraft.client.creature.spider;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.creature.HarvestmanEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws one Harvestman as the Spider 7 creature: its animator's pose skinned onto the mesh each frame (four bones a
 * vertex), stood on the surface it holds (its feet on the floor, wall or ceiling under its body) and turned the way its
 * body lies. Its skull always glows. Its red seams and core are its glow map: dark when it hides (the red painted over
 * too), drawn lit over the top when it shows itself or feeds, pulsing as it feeds. One per Harvestman, client only.
 */
public final class SpiderDraw {
    private static final int FULL_BRIGHT = 0xF000F0;
    /** How far the glow pass sits off the skin, so it never fights it. */
    private static final float GLOW_OFF = 0.002f;

    public final SpiderAnimator animator;
    private final SpiderIk ik;
    private final java.util.List<Piece> pieces = new java.util.ArrayList<>();
    private int lostSeen = -1;
    /** Where the entity is drawn this frame (the flying legs are placed relative to it). */
    private Vec3 drawnNow = Vec3.ZERO;
    private final SpiderModel model;
    private final float[][] pos, normal;
    /** Where the model stands relative to where the entity is drawn, and how it's turned, this frame. */
    public final Matrix4f place = new Matrix4f();
    public float camo = 1, pulse = 1;
    public boolean hurt;

    private SpiderDraw(SpiderModel model) {
        this.model = model;
        animator = new SpiderAnimator(model);
        ik = new SpiderIk(model);
        pos = new float[model.groups.length][];
        normal = new float[model.groups.length][];
        for (int g = 0; g < model.groups.length; g++) {
            pos[g] = new float[model.groups[g].pos.length];
            normal[g] = new float[model.groups[g].normal.length];
        }
    }

    /** This Harvestman's, made the first time it's drawn; null if the model can't be loaded. */
    public static SpiderDraw of(HarvestmanEntity e) {
        if (e.body instanceof SpiderDraw d) {
            return d;
        }
        SpiderModel m = SpiderModel.get();
        if (m == null) {
            return null;
        }
        SpiderDraw d = new SpiderDraw(m);
        e.body = d;
        return d;
    }

    /**
     * Brings it up to this frame.
     *
     * @param drawnAt where the entity is drawn
     * @param turn how its body lies (model to world)
     */
    public void update(HarvestmanEntity e, Vec3 drawnAt, Quaternionf turn, float camo, float pulse, float partialTick) {
        this.camo = camo;
        this.pulse = pulse;
        hurt = e.hurtTime > 0 && e.isAlive();
        // its feet on the surface under its body: the body's centre, less how high it holds itself off that surface
        Vector3f up = turn.transform(new Vector3f(0, 1, 0));
        float lift = e.pose() == HarvestmanEntity.DEAD ? (float) HarvestmanEntity.MID : e.lift();
        Vector3f base = new Vector3f(0, (float) HarvestmanEntity.MID, 0).sub(up.mul(lift, new Vector3f()));
        place.translation(base).rotate(turn);
        drawnNow = drawnAt;
        if (animator.update(e, drawnAt, turn, camo, e.tickCount + partialTick)) {
            ik.apply(e, animator, e.level(), place, drawnAt, animator.dt);
            animator.bake();
            legsOff(e, drawnAt);
            skin();
            fly(e, drawnAt, animator.dt);
        }
    }

    /**
     * Legs shot away at the knee: below it, folded into the knee (the stump's end), and a leg that's just gone flies
     * off from where it was, tumbling, and lies where it lands a while.
     */
    private void legsOff(HarvestmanEntity e, Vec3 drawnAt) {
        int lost = e.lost();
        Matrix4f[] bones = animator.skin;
        for (int l = 0; l < 4; l++) {
            if ((lost >> l & 1) == 0) {
                continue;
            }
            int knee = model.legs[l][1], ankle = model.legs[l][2];
            if (lostSeen >= 0 && (lostSeen >> l & 1) == 0 && model.lower[l].length > 0) {
                Piece f = new Piece(l, new Matrix4f(place).mul(bones[knee]), new Matrix4f(place).mul(bones[ankle]));
                Vector3f at = place.transformPosition(new Vector3f(animator.p[knee]));
                f.start = new Vec3(drawnAt.x + at.x, drawnAt.y + at.y, drawnAt.z + at.z);
                f.pos = f.start;
                f.drawnAtThen = drawnAt;
                Vector3f outward = place.transformDirection(new Vector3f(l < 2 ? 1 : -1, 0.2f, (l & 1) == 0 ? 0.5f : -0.5f)).normalize();
                f.vel = new Vec3(outward.x, outward.y, outward.z).scale(3 + e.getRandom().nextDouble() * 2).add(0, 3.5, 0);
                f.axis.set((float) e.getRandom().nextGaussian(), (float) e.getRandom().nextGaussian(), (float) e.getRandom().nextGaussian()).normalize();
                f.spin = 6 + e.getRandom().nextFloat() * 6;
                pieces.add(f);
            }
            Vector3f k = new Vector3f(animator.p[knee]);
            bones[knee].zero().m30(k.x).m31(k.y).m32(k.z).m33(1);
            bones[ankle].set(bones[knee]);
        }
        lostSeen = lost;
    }

    /** The legs flying off: falling, tumbling, stopping on what they land on, and gone after a while. */
    private void fly(HarvestmanEntity e, Vec3 drawnAt, float dt) {
        for (java.util.Iterator<Piece> it = pieces.iterator(); it.hasNext(); ) {
            Piece f = it.next();
            f.age += dt;
            if (f.age > 40) {
                it.remove();
                continue;
            }
            if (!f.landed) {
                f.vel = f.vel.add(0, -22 * dt, 0).scale(Math.pow(0.6, dt));
                Vec3 next = f.pos.add(f.vel.scale(dt));
                net.minecraft.world.phys.BlockHitResult hit = e.level().clip(new net.minecraft.world.level.ClipContext(f.pos, next,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, e));
                if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                    f.pos = hit.getLocation().add(0, 0.15, 0);
                    f.landed = true;
                } else {
                    f.pos = next;
                    f.angle += f.spin * dt;
                }
            }
        }
    }

    /** A leg below its knee, shot away: as it was posed when it went, and where it's got to since. */
    private static final class Piece {
        final int leg;
        final Matrix4f knee, ankle;
        final Vector3f axis = new Vector3f(0, 1, 0);
        Vec3 start, pos, vel;
        float spin, angle, age;
        boolean landed;

        Piece(int leg, Matrix4f knee, Matrix4f ankle) {
            this.leg = leg;
            this.knee = knee;
            this.ankle = ankle;
        }

        /** From the model at rest to where the piece is now, relative to where the entity's drawn. */
        Matrix4f now(Vec3 drawnAt, Matrix4f bone) {
            return new Matrix4f().translation((float) (pos.x - drawnAt.x), (float) (pos.y - drawnAt.y), (float) (pos.z - drawnAt.z))
                    .rotate(angle, axis)
                    .translate((float) (drawnAtThen.x - start.x), (float) (drawnAtThen.y - start.y), (float) (drawnAtThen.z - start.z))
                    .mul(bone);
        }

        Vec3 drawnAtThen;
    }

    private void skin() {
        Matrix4f[] bones = animator.skin;
        Vector3f a = new Vector3f(), n = new Vector3f(), t = new Vector3f();
        for (int g = 0; g < model.groups.length; g++) {
            SpiderModel.Group gr = model.groups[g];
            float[] p = gr.pos, nm = gr.normal, w = gr.weight, op = pos[g], on = normal[g];
            byte[] j = gr.joint;
            for (int v = 0, i = 0; v < gr.vertices; v++, i += 3) {
                a.zero();
                n.zero();
                for (int k = 0; k < 4; k++) {
                    float wk = w[v * 4 + k];
                    if (wk <= 0) {
                        continue;
                    }
                    Matrix4f m = bones[j[v * 4 + k] & 255];
                    m.transformPosition(p[i], p[i + 1], p[i + 2], t);
                    a.fma(wk, t);
                    m.transformDirection(nm[i], nm[i + 1], nm[i + 2], t);
                    n.fma(wk, t);
                }
                float len = n.length();
                if (len > 1e-6f) {
                    n.div(len);
                }
                op[i] = a.x;
                op[i + 1] = a.y;
                op[i + 2] = a.z;
                on[i] = n.x;
                on[i + 1] = n.y;
                on[i + 2] = n.z;
            }
        }
    }

    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, boolean shaders) {
        poseStack.pushPose();
        poseStack.mulPose(place);
        int overlay = OverlayTexture.pack(0, hurt ? 3 : 10);
        float show = (1 - camo) * pulse;
        for (int g = 0; g < model.groups.length; g++) {
            SpiderModel.Group gr = model.groups[g];
            int gi = g;
            // its skull always glows, lit however dark it is round it; the rest of its red goes dark as it hides
            boolean skull = gr.name.equals("skull");
            float hide = skull ? 0 : camo, lit = skull ? 1 : show;
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(gr.texture),
                    (pose, out) -> body(gr, pos[gi], normal[gi], pose, out, skull ? FULL_BRIGHT : light, overlay, 1, hide));
            if (lit > 0.02f && gr.glowTexture != null && gr.glowTris != null && gr.glowTris.length > 0) {
                collector.submitCustomGeometry(poseStack, shaders ? RenderTypes.beaconBeam(gr.glowTexture, true) : RenderTypes.eyes(gr.glowTexture),
                        (pose, out) -> glow(gr, pos[gi], normal[gi], pose, out, lit, shaders));
            }
        }
        poseStack.popPose();
        if (!pieces.isEmpty()) {
            SpiderModel.Group body = model.groups[0];
            for (Piece f : pieces) {
                Matrix4f kneeAt = f.now(drawnNow, f.knee), ankleAt = f.now(drawnNow, f.ankle);
                int knee = model.legs[f.leg][1];
                int[] tris = model.lower[f.leg];
                collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(body.texture),
                        (pose, out) -> piece(body, tris, knee, kneeAt, ankleAt, pose, out, light));
            }
        }
    }

    /** A leg that's been shot away: each vertex by the bone it follows most, as posed when it went. */
    private static void piece(SpiderModel.Group g, int[] tris, int knee, Matrix4f kneeAt, Matrix4f ankleAt, PoseStack.Pose pose,
                              VertexConsumer out, int light) {
        Vector3f p = new Vector3f(), n = new Vector3f();
        for (int t = 0; t < tris.length; t += 3) {
            for (int k = 0; k < 4; k++) {
                int v = tris[t + Math.min(k, 2)], i = v * 3;
                Matrix4f m = SpiderModel.main(g, v) == knee ? kneeAt : ankleAt;
                m.transformPosition(g.pos[i], g.pos[i + 1], g.pos[i + 2], p);
                m.transformDirection(g.normal[i], g.normal[i + 1], g.normal[i + 2], n).normalize();
                out.addVertex(pose, p.x, p.y, p.z)
                        .setColor(0xFFFFFFFF)
                        .setUv(g.uv[v * 2], g.uv[v * 2 + 1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, n.x, n.y, n.z);
            }
        }
    }

    /** Every triangle (as a quad with its last corner repeated), its glowing parts darkened as far as it hides. */
    private static void body(SpiderModel.Group g, float[] p, float[] n, PoseStack.Pose pose, VertexConsumer out, int light, int overlay,
                             float dull, float camo) {
        float[] uv = g.uv, glow = g.glow;
        int[] tris = g.tris;
        for (int t = 0; t < tris.length; t += 3) {
            for (int k = 0; k < 4; k++) {
                int v = tris[t + Math.min(k, 2)], i = v * 3;
                float c = dull * (1 - 0.85f * camo * glow[v]);
                int grey = Math.max(0, Math.min(255, (int) (c * 255)));
                out.addVertex(pose, p[i], p[i + 1], p[i + 2])
                        .setColor(0xFF000000 | grey << 16 | grey << 8 | grey)
                        .setUv(uv[v * 2], uv[v * 2 + 1])
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(pose, n[i], n[i + 1], n[i + 2]);
            }
        }
    }

    /** The glowing triangles again, a hair off the skin, full bright and as strong as it's showing itself. */
    private static void glow(SpiderModel.Group g, float[] p, float[] n, PoseStack.Pose pose, VertexConsumer out, float show, boolean shaders) {
        float[] uv = g.uv, glow = g.glow;
        int[] tris = g.glowTris;
        int s = Math.max(0, Math.min(255, (int) (show * 255)));
        for (int t = 0; t < tris.length; t += 3) {
            for (int k = 0; k < 4; k++) {
                int v = tris[t + Math.min(k, 2)], i = v * 3;
                // with a shader pack it's blended by its alpha, so the dark between the seams stays clear
                int alpha = shaders ? Math.min(255, (int) (s * Math.min(1, glow[v] * 2.5f))) : 255;
                out.addVertex(pose, p[i] + n[i] * GLOW_OFF, p[i + 1] + n[i + 1] * GLOW_OFF, p[i + 2] + n[i + 2] * GLOW_OFF)
                        .setColor(alpha << 24 | s << 16 | s << 8 | s)
                        .setUv(uv[v * 2], uv[v * 2 + 1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(FULL_BRIGHT)
                        .setNormal(pose, n[i], n[i + 1], n[i + 2]);
            }
        }
    }
}
