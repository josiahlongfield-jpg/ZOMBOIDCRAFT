package com.zomboidcraft.client.creature.spider;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.creature.HarvestmanEntity;
import com.zomboidcraft.creature.spider.SpiderModel;
import com.zomboidcraft.creature.spider.SpiderPose;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws one Harvestman as the Spider 7 creature: its pose this frame (SpiderPose: its clips, fitted to the world) skinned
 * onto the mesh (four bones a vertex). Its skull always glows, blue while it means no one harm and red while it hunts,
 * strikes or feeds (it eases from one to the other), and the whole skull flares in that colour through its rage. Its
 * seams and core are lit the same way when it shows itself, dark when it hides. A limb shot away flies off tumbling and
 * lies where it lands a while. One per Harvestman, client only.
 */
public final class SpiderDraw {
    private static final int FULL_BRIGHT = 0xF000F0;
    /** How far the glow passes sit off the skin, so they never fight it. */
    private static final float GLOW_OFF = 0.002f, RAGE_OFF = 0.004f;
    /** What its rage lights its skull with: red and blue. */
    private static final float[] RAGE_RED = {1f, 0.22f, 0.12f}, RAGE_BLUE = {0.3f, 0.65f, 1f};

    public final SpiderPose pose;
    private final SpiderModel model;
    private final SpiderLooks looks;
    private final int skull;
    private final float[][] pos, normal;
    private final java.util.List<Piece> pieces = new java.util.ArrayList<>();
    /** Where the entity is drawn this frame (the flying limbs are placed relative to it). */
    private Vec3 drawnNow = Vec3.ZERO;
    private long lastNanos;
    public float camo = 1, pulse = 1;
    /** How red its glow is (0 blue, 1 red), eased. */
    private float red = -1;
    private boolean hurt;

    private SpiderDraw(SpiderModel model, SpiderLooks looks) {
        this.model = model;
        this.looks = looks;
        pose = new SpiderPose(model);
        int s = -1;
        pos = new float[model.groups.length][];
        normal = new float[model.groups.length][];
        for (int g = 0; g < model.groups.length; g++) {
            pos[g] = new float[model.groups[g].pos.length];
            normal[g] = new float[model.groups[g].normal.length];
            if (model.groups[g].name.equals("skull")) {
                s = g;
            }
        }
        skull = s;
    }

    /** This Harvestman's, made the first time it's drawn; null if the model can't be loaded. */
    public static SpiderDraw of(HarvestmanEntity e) {
        if (e.body instanceof SpiderDraw d) {
            return d;
        }
        SpiderModel m = SpiderModel.get();
        SpiderLooks l = m == null ? null : SpiderLooks.get(m);
        if (l == null) {
            return null;
        }
        SpiderDraw d = new SpiderDraw(m, l);
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
        drawnNow = drawnAt;
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.05f : Math.min(0.1f, (now - lastNanos) / 1e9f);
        if (pose.update(e, drawnAt, turn, e.tickCount + partialTick, dt)) {
            lastNanos = now;
            dt = Math.max(dt, 0);
            float want = e.menace() ? 1 : 0;
            red = red < 0 ? want : Mth.lerp(1 - (float) Math.exp(-dt * 2.5f), red, want);
            for (int c = 0; c < SpiderModel.LIMBS; c++) {
                if ((pose.justGone >> c & 1) != 0) {
                    pieces.add(new Piece(e, c, drawnAt));
                }
            }
            skin();
            fly(e, dt);
        }
    }

    /**
     * Just over its skull as last drawn, from where the entity's drawn: off its back the way its body stands (so
     * under it when it hangs from a ceiling). Null before it's first drawn.
     */
    public Vec3 overHead() {
        if (lastNanos == 0 || model.head < 0) {
            return null;
        }
        Vector3f at = pose.place.transformPosition(new Vector3f(pose.animator.p[model.head]));
        Vector3f up = pose.place.transformDirection(new Vector3f(0, 1, 0)).normalize();
        return new Vec3(at.x + up.x * 0.75f, at.y + up.y * 0.75f, at.z + up.z * 0.75f);
    }

    /** A limb shot away: as it was posed when it went, and where it's got to since (falling, tumbling, then lying). */
    private final class Piece {
        final int limb;
        final Matrix4f[] kept;
        final Vector3f axis;
        final Vec3 drawnAtThen, start;
        Vec3 pos, vel;
        float spin, angle, age;
        boolean landed;

        Piece(HarvestmanEntity e, int limb, Vec3 drawnAt) {
            this.limb = limb;
            kept = pose.lastSeen[limb];
            drawnAtThen = drawnAt;
            Vector3f joint = new Vector3f(pose.animator.p[model.cutAt[limb]]);
            Vector3f at = pose.place.transformPosition(new Vector3f(joint));
            start = drawnAt.add(at.x, at.y, at.z);
            pos = start;
            // thrown out from its body, the way the limb points, and up
            Vector3f out = new Vector3f(joint).sub(pose.animator.p[model.pelvis]);
            out.y = Math.max(out.y, 0) + 0.2f;
            pose.place.transformDirection(out).normalize();
            double speed = (limb < 4 ? 3 : 2) + e.getRandom().nextDouble() * 2;
            vel = new Vec3(out.x, out.y, out.z).scale(speed).add(0, limb < 4 ? 3.5 : 2.5, 0);
            axis = new Vector3f((float) e.getRandom().nextGaussian(), (float) e.getRandom().nextGaussian(), (float) e.getRandom().nextGaussian()).normalize();
            spin = (limb < 4 ? 6 : 9) + e.getRandom().nextFloat() * 6;
        }

        /** From one of its bones as kept to where the piece is now, relative to where the entity's drawn. */
        Matrix4f now(Matrix4f bone) {
            return new Matrix4f().translation((float) (pos.x - drawnNow.x), (float) (pos.y - drawnNow.y), (float) (pos.z - drawnNow.z))
                    .rotate(angle, axis)
                    .translate((float) (drawnAtThen.x - start.x), (float) (drawnAtThen.y - start.y), (float) (drawnAtThen.z - start.z))
                    .mul(bone);
        }
    }

    /** The limbs flying off: falling, tumbling, stopping on what they land on, and gone after a while. */
    private void fly(HarvestmanEntity e, float dt) {
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
                BlockHitResult hit = e.level().clip(new ClipContext(f.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
                if (hit.getType() != HitResult.Type.MISS) {
                    f.pos = hit.getLocation().add(0, 0.12, 0);
                    f.landed = true;
                } else {
                    f.pos = next;
                    f.angle += f.spin * dt;
                }
            }
        }
    }

    private void skin() {
        Matrix4f[] bones = pose.animator.skin;
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
        poseStack.mulPose(pose.place);
        int overlay = OverlayTexture.pack(0, hurt ? 3 : 10);
        float show = (1 - camo) * pulse, r = Math.max(0, red);
        for (int g = 0; g < model.groups.length; g++) {
            SpiderModel.Group gr = model.groups[g];
            int gi = g;
            // its skull always glows, lit however dark it is round it; the rest of its glow goes dark as it hides
            boolean isSkull = g == skull;
            float hide = isSkull ? 0 : camo, lit = isSkull ? 1 : show;
            float[] vg = looks.glow[g];
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(looks.texture[g]),
                    (p, out) -> body(gr, gr.tris, vg, pos[gi], normal[gi], p, out, isSkull ? FULL_BRIGHT : light, overlay, hide));
            // red and blue glow, as far as it's turned to each
            glowPass(poseStack, collector, gr, g, looks.red[g], looks.redTris[g], lit * r, shaders);
            glowPass(poseStack, collector, gr, g, looks.blue[g], looks.blueTris[g], lit * (1 - r), shaders);
        }
        // its rage: the whole skull flares in its colour
        float rage = pose.animator.rage();
        if (rage > 0.02f && skull >= 0) {
            SpiderModel.Group gr = model.groups[skull];
            Identifier tex = looks.texture[skull];
            float[] tint = new float[3];
            for (int k = 0; k < 3; k++) {
                tint[k] = Mth.lerp(r, RAGE_BLUE[k], RAGE_RED[k]) * rage;
            }
            collector.submitCustomGeometry(poseStack, shaders ? RenderTypes.beaconBeam(tex, true) : RenderTypes.eyes(tex),
                    (p, out) -> flare(gr, pos[skull], normal[skull], p, out, tint, rage, shaders));
        }
        poseStack.popPose();
        for (Piece f : pieces) {
            int[] bones = model.cutBones[f.limb];
            Matrix4f[] at = new Matrix4f[f.kept.length];
            for (int k = 0; k < at.length; k++) {
                at[k] = f.now(f.kept[k]);
            }
            for (int g = 0; g < model.groups.length; g++) {
                int[] tris = model.cutTris[f.limb][g];
                if (tris == null || tris.length == 0) {
                    continue;
                }
                SpiderModel.Group gr = model.groups[g];
                collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(looks.texture[g]),
                        (p, out) -> piece(gr, tris, bones, at, p, out, light));
            }
        }
    }

    private void glowPass(PoseStack poseStack, SubmitNodeCollector collector, SpiderModel.Group gr, int g, Identifier tex, int[] tris,
                          float show, boolean shaders) {
        if (show <= 0.02f || tex == null || tris == null || tris.length == 0) {
            return;
        }
        float[] vg = looks.glow[g], p = pos[g], n = normal[g];
        collector.submitCustomGeometry(poseStack, shaders ? RenderTypes.beaconBeam(tex, true) : RenderTypes.eyes(tex),
                (pp, out) -> glow(gr, tris, vg, p, n, pp, out, show, shaders));
    }

    /** A limb that's been shot away: each vertex by the bone it follows most, as posed when it went. */
    private static void piece(SpiderModel.Group g, int[] tris, int[] bones, Matrix4f[] at, PoseStack.Pose pose, VertexConsumer out, int light) {
        Vector3f p = new Vector3f(), n = new Vector3f();
        for (int t = 0; t < tris.length; t += 3) {
            for (int k = 0; k < 4; k++) {
                int v = tris[t + Math.min(k, 2)], i = v * 3;
                int main = SpiderModel.main(g, v), b = 0;
                for (int q = 0; q < bones.length; q++) {
                    if (bones[q] == main) {
                        b = q;
                        break;
                    }
                }
                Matrix4f m = at[b];
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
    private static void body(SpiderModel.Group g, int[] tris, float[] glow, float[] p, float[] n, PoseStack.Pose pose, VertexConsumer out,
                             int light, int overlay, float camo) {
        float[] uv = g.uv;
        for (int t = 0; t < tris.length; t += 3) {
            for (int k = 0; k < 4; k++) {
                int v = tris[t + Math.min(k, 2)], i = v * 3;
                float c = 1 - 0.85f * camo * glow[v];
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
    private static void glow(SpiderModel.Group g, int[] tris, float[] glow, float[] p, float[] n, PoseStack.Pose pose, VertexConsumer out,
                             float show, boolean shaders) {
        float[] uv = g.uv;
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

    /** The whole skull again over itself, its own texture tinted the colour it's glowing, as strong as its rage. */
    private static void flare(SpiderModel.Group g, float[] p, float[] n, PoseStack.Pose pose, VertexConsumer out, float[] tint, float rage,
                              boolean shaders) {
        float[] uv = g.uv;
        int[] tris = g.tris;
        // its bone-white lit up well past white, so the colour reads over the texture's own
        int cr = Math.min(255, (int) (tint[0] * 400)), cg = Math.min(255, (int) (tint[1] * 400)), cb = Math.min(255, (int) (tint[2] * 400));
        int alpha = shaders ? Math.min(255, (int) (rage * 200)) : 255;
        int colour = alpha << 24 | cr << 16 | cg << 8 | cb;
        for (int t = 0; t < tris.length; t += 3) {
            for (int k = 0; k < 4; k++) {
                int v = tris[t + Math.min(k, 2)], i = v * 3;
                out.addVertex(pose, p[i] + n[i] * RAGE_OFF, p[i + 1] + n[i + 1] * RAGE_OFF, p[i + 2] + n[i + 2] * RAGE_OFF)
                        .setColor(colour)
                        .setUv(uv[v * 2], uv[v * 2 + 1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(FULL_BRIGHT)
                        .setNormal(pose, n[i], n[i + 1], n[i + 2]);
            }
        }
    }
}
