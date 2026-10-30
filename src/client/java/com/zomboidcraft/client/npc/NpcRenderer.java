package com.zomboidcraft.client.npc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.zomboidcraft.combat.GunType;
import com.zomboidcraft.npc.NpcEntity;
import com.zomboidcraft.npc.Npcs;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws an NPC's mesh turned to its body's facing. A skinned model (NpcSkin, from Mixamo, with its own clips) is
 * animated by NpcSkinAnimator and drawn by NpcSkinDraw, the gun in her right hand. Otherwise the mesh is NpcAssets'. A rigged model is posed by NpcPose (walk and run from how
 * fast it's moving, its head turned where it looks, its gun up while it shoots) and skinned here each frame; one
 * without bones only breathes. Whatever's in its main hand is drawn in its right hand. Both sides of every triangle
 * are drawn, since bought clothes are often single sheets. The "raw" look is lit like any mob. The "toon" look, on its
 * painted texture, is lit in three flat bands from one fixed light (as the gun renders are) and gets an ink outline:
 * the mesh pushed out a few millimetres along its normals, then back away from the eye further than that, drawn in
 * near-black. Where the body faces you the shell ends up behind it; only round the edges, where nothing covers it,
 * does it show. (It doesn't lean on back-face culling, which shader packs may turn off: drawn inside out, it hid the
 * whole body.)
 */
public class NpcRenderer extends EntityRenderer<NpcEntity, NpcRenderer.State> {
    /** How far the outline shell stands off the body, in blocks. */
    private static final float INK = 0.0065f;
    /** How far the shell is pushed back from the eye, so its front sits behind the body. */
    private static final float BACK = INK * 2.5f;
    private static final int INK_COLOUR = 0xFF14110F;
    /** The toon light (world space, normalised below) and its three bands. */
    private static final float LX, LY, LZ;
    private static final float LIT = 1.06f, MID = 0.86f, SHADE = 0.68f;
    /** Where the right wrist is on the rest pose (the dark elf's), and which way her forearm runs from the elbow. */
    private static final Vector3f WRIST = new Vector3f(-0.50f, 1.03f, -0.09f), FOREARM = new Vector3f(-0.17f, -0.16f, -0.03f).normalize();

    static {
        float x = -0.4f, y = 0.8f, z = 0.45f, len = (float) Math.sqrt(x * x + y * y + z * z);
        LX = x / len;
        LY = y / len;
        LZ = z / len;
    }

    public static void register() {
        EntityRendererRegistry.register(Npcs.NPC, NpcRenderer::new);
        NpcClient.register();
        try {
            // made up front so it's there to drop local model files into
            java.nio.file.Files.createDirectories(Npcs.localFolder());
        } catch (java.io.IOException e) {
            // the folder is only a convenience
        }
    }

    public static class State extends EntityRenderState {
        String model = "", look = "raw";
        /** A skinned model (NpcSkin) and this frame's bones, the clothes she has off, and her animator's buffers. */
        NpcSkin skin;
        Matrix4f[] skinned;
        java.util.Set<String> hidden = java.util.Set.of();
        Object[] buffers;
        float bodyYaw;
        boolean hurt;
        /** A gun with a stock (not a handgun): held further forward so the stock sits against her shoulder. */
        boolean longGun;
        final NpcPose.Input pose = new NpcPose.Input();
        final ItemStackRenderState held = new ItemStackRenderState();
    }

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.35f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(NpcEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.model = entity.model();
        state.look = entity.look();
        state.bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        state.hurt = entity.hurtTime > 0 && entity.isAlive();
        NpcPose.Input in = state.pose;
        float speed = entity.walkAnimation.speed(partialTick);
        in.move = Mth.clamp(speed * 2.2f, 0f, 1f);
        in.run = Mth.clamp((speed - 0.55f) / 0.35f, 0f, 1f);
        in.phase = entity.walkAnimation.position(partialTick) * 0.6662f;
        in.t = entity.tickCount + partialTick;
        in.aim = Mth.lerp(partialTick, entity.aimAnimO, entity.aimAnim);
        in.kick = Mth.lerp(partialTick, entity.kickO, entity.kick);
        in.headYaw = Mth.clamp(Mth.wrapDegrees(Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot) - state.bodyYaw), -70f, 70f);
        in.headPitch = entity.getXRot(partialTick);
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(state.held, entity.getMainHandItem(),
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity.level(), entity, entity.getId());
        GunType gun = GunType.of(entity.getMainHandItem());
        state.longGun = gun != null && !gun.handgun();
        state.skin = NpcSkin.get(state.model);
        state.skinned = null;
        if (state.skin != null) {
            NpcSkin skin = state.skin;
            if (!(entity.clientAnim instanceof NpcSkinAnimator)) {
                entity.clientAnim = new NpcSkinAnimator();
            }
            NpcSkinAnimator anim = (NpcSkinAnimator) entity.clientAnim;
            NpcSkinAnimator.Input a = new NpcSkinAnimator.Input();
            a.now = in.t / 20f;
            double vx = entity.getX() - entity.xo, vz = entity.getZ() - entity.zo;
            a.speed = (float) Math.sqrt(vx * vx + vz * vz) * 20f;
            a.aim = in.aim;
            a.gun = !entity.getMainHandItem().isEmpty();
            a.shots = entity.shots();
            a.headYaw = in.headYaw;
            a.headPitch = in.headPitch;
            a.dead = entity.deathTime > 0 ? (entity.deathTime + partialTick) / 20f : 0;
            a.talking = entity.talking() > 0;
            a.id = entity.getId();
            a.action = entity.action();
            a.mood = entity.moodBits();
            a.bond = entity.bondBits();
            state.skinned = new Matrix4f[skin.boneNames.length];
            for (int i = 0; i < state.skinned.length; i++) {
                state.skinned[i] = new Matrix4f();
            }
            anim.pose(skin, a, state.skinned);
            state.hidden = entity.hidden();
            if (anim.buffers.length != skin.groups.length * 2) {
                anim.buffers = new Object[skin.groups.length * 2];
            }
            state.buffers = anim.buffers;
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.skin != null && state.skinned != null) {
            poseStack.pushPose();
            poseStack.rotateDegrees(Axis.YP, -state.bodyYaw);
            NpcSkinDraw.submit(state.model, state.skin, state.skinned, state.hidden, state.buffers, state.distanceToCameraSq,
                    state.lightCoords, OverlayTexture.pack(0, state.hurt ? 3 : 10), poseStack, collector);
            if (!state.held.isEmpty()) {
                skinHeld(state.skin, state.skinned, state, poseStack, collector, state.lightCoords);
            }
            poseStack.popPose();
            super.submit(state, poseStack, collector, camera);
            return;
        }
        if (NpcSkin.exists(state.model)) {
            return;  // still loading
        }
        NpcAssets.Mesh mesh = NpcAssets.mesh(state.model);
        Identifier texture = NpcAssets.texture(state.model, state.look);
        if (mesh == null || texture == null) {
            return;
        }
        boolean toon = state.look.equals("toon");
        int light = state.lightCoords, overlay = OverlayTexture.pack(0, state.hurt ? 3 : 10);
        float yaw = state.bodyYaw;
        // the eye in the model's own frame (undoing the turn below), for the outline
        double dx = camera.pos.x - state.x, dy = camera.pos.y - state.y, dz = camera.pos.z - state.z, r = Math.toRadians(yaw);
        float ex = (float) (dx * Math.cos(r) + dz * Math.sin(r)), ey = (float) dy, ez = (float) (-dx * Math.sin(r) + dz * Math.cos(r));
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, -yaw);
        Skinned posed;
        Matrix4f[] bones = null;
        if (mesh.bones() != null) {
            bones = new Matrix4f[mesh.bones().length];
            for (int i = 0; i < bones.length; i++) {
                bones[i] = new Matrix4f();
            }
            NpcPose.pose(mesh.bones(), state.pose, bones);
            posed = skin(mesh, bones);
        } else {
            // not rigged: a slow breath, the chest rising a few millimetres and the body swaying a touch
            float breath = Mth.sin(state.pose.t * 0.09f);
            poseStack.scale(1f, 1f + breath * 0.004f, 1f);
            poseStack.rotateDegrees(Axis.ZP, Mth.sin(state.pose.t * 0.031f) * 0.6f);
            posed = new Skinned(mesh.pos(), mesh.normal());
        }
        int[] shade = toon ? bands(mesh, posed.normal, yaw) : null;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture), (pose, out) -> body(mesh, posed, shade, pose, out, light, overlay));
        if (toon) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture), (pose, out) -> ink(mesh, posed, ex, ey, ez, pose, out, light));
        }
        if (bones != null && !state.held.isEmpty()) {
            held(mesh, bones, state, poseStack, collector, light);
        }
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    /**
     * The held item in the right hand, placed the way the game places one in a player's hand: as if the forearm hung
     * straight down, then turned to where the forearm really points.
     */
    private static void held(NpcAssets.Mesh mesh, Matrix4f[] bones, State state, PoseStack poseStack, SubmitNodeCollector collector, int light) {
        int fore = NpcPose.find(mesh.bones(), "r_forearm");
        if (fore < 0) {
            return;
        }
        Matrix4f m = bones[fore];
        Vector3f wrist = m.transformPosition(new Vector3f(WRIST));
        // the forearm's turn: from hanging (-y) to its rest direction, then as posed
        Quaternionf rest = new Quaternionf().rotationTo(new Vector3f(0, -1, 0), FOREARM);
        Quaternionf posed = m.getNormalizedRotation(new Quaternionf()).mul(rest);
        poseStack.pushPose();
        poseStack.translate(wrist.x, wrist.y, wrist.z);
        poseStack.mulPose(new Matrix4f().rotation(posed));
        // into the player model's own frame (y down, facing -z), then the game's hand-to-item turn
        poseStack.mulPose(new Matrix4f().rotationX((float) Math.PI));
        poseStack.translate(-1f / 16f, -0.02f, -2f / 16f);
        poseStack.mulPose(new Matrix4f().rotationX((float) (-Math.PI / 2)));
        poseStack.mulPose(new Matrix4f().rotationY((float) Math.PI));
        state.held.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    /** How far (blocks) a long gun slides forward along her hand from the palm, so its stock meets her shoulder, not passes through it. */
    private static final float LONG_GUN_FORWARD = 0.13f;

    /**
     * The held item in a skinned model's right hand: at the palm (between the wrist and the middle finger's root), turned
     * as the hand's turned from its rest, which points along her arm (the T-pose), as if from a hand hanging straight down.
     */
    private static void skinHeld(NpcSkin skin, Matrix4f[] skinned, State state, PoseStack poseStack, SubmitNodeCollector collector, int light) {
        int hand = skin.bone("mixamorigRightHand"), finger = skin.bone("mixamorigRightHandMiddle1");
        if (hand < 0 || finger < 0) {
            return;
        }
        Vector3f wristRest = new Matrix4f(skin.inverse[hand]).invert().getTranslation(new Vector3f());
        Vector3f fingerRest = new Matrix4f(skin.inverse[finger]).invert().getTranslation(new Vector3f());
        Vector3f restDir = new Vector3f(fingerRest).sub(wristRest).normalize();
        Vector3f palm = skinned[hand].transformPosition(new Vector3f(wristRest).lerp(fingerRest, 0.45f));
        Quaternionf rest = new Quaternionf().rotationTo(new Vector3f(0, -1, 0), restDir);
        Quaternionf posed = skinned[hand].getNormalizedRotation(new Quaternionf()).mul(rest);
        if (state.longGun) {
            Vector3f along = skinned[hand].transformDirection(new Vector3f(restDir)).normalize();
            palm.add(along.mul(LONG_GUN_FORWARD));
        }
        poseStack.pushPose();
        poseStack.translate(palm.x, palm.y, palm.z);
        poseStack.mulPose(new Matrix4f().rotation(posed));
        // into the player model's own frame (y down, facing -z), then the game's hand-to-item turn
        poseStack.mulPose(new Matrix4f().rotationX((float) Math.PI));
        poseStack.translate(-1f / 16f, -0.06f, -2f / 16f);
        poseStack.mulPose(new Matrix4f().rotationX((float) (-Math.PI / 2)));
        poseStack.mulPose(new Matrix4f().rotationY((float) Math.PI));
        state.held.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    /** A posed copy of the mesh's positions and normals. */
    private record Skinned(float[] pos, float[] normal) {
    }

    /** Each vertex moved by its two bones, blended. */
    private static Skinned skin(NpcAssets.Mesh mesh, Matrix4f[] bones) {
        float[] p = mesh.pos(), n = mesh.normal(), w = mesh.weight();
        byte[] b0 = mesh.bone0(), b1 = mesh.bone1();
        float[] op = new float[p.length], on = new float[n.length];
        Vector3f a = new Vector3f(), b = new Vector3f(), na = new Vector3f(), nb = new Vector3f();
        Matrix3f[] rot = new Matrix3f[bones.length];
        for (int i = 0; i < bones.length; i++) {
            rot[i] = bones[i].get3x3(new Matrix3f());
        }
        for (int v = 0, i = 0; v < mesh.vertices(); v++, i += 3) {
            int i0 = b0[v], i1 = b1[v];
            float k = w[v];
            bones[i0].transformPosition(p[i], p[i + 1], p[i + 2], a);
            rot[i0].transform(n[i], n[i + 1], n[i + 2], na);
            if (k < 0.999f) {
                bones[i1].transformPosition(p[i], p[i + 1], p[i + 2], b);
                rot[i1].transform(n[i], n[i + 1], n[i + 2], nb);
                a.mul(k).add(b.mul(1 - k));
                na.mul(k).add(nb.mul(1 - k));
                na.normalize();
            }
            op[i] = a.x;
            op[i + 1] = a.y;
            op[i + 2] = a.z;
            on[i] = na.x;
            on[i + 1] = na.y;
            on[i + 2] = na.z;
        }
        return new Skinned(op, on);
    }

    /** Each vertex's toon colour: which band its normal falls in, turned to the body's facing, under the fixed light. */
    private static int[] bands(NpcAssets.Mesh mesh, float[] n, float yawDegrees) {
        float a = (float) Math.toRadians(-yawDegrees), c = Mth.cos(a), s = Mth.sin(a);
        int[] out = new int[mesh.vertices()];
        for (int v = 0; v < out.length; v++) {
            float nx = n[v * 3], ny = n[v * 3 + 1], nz = n[v * 3 + 2];
            float wx = nx * c + nz * s, wz = -nx * s + nz * c;
            float d = wx * LX + ny * LY + wz * LZ;
            float k = d > 0.45f ? LIT : d > 0f ? MID : SHADE;
            k *= ny > 0.6f ? 1.06f : ny < -0.5f ? 0.85f : 1f;
            int g = Math.min(255, (int) (k * 235));
            out[v] = 0xFF000000 | g << 16 | g << 8 | g;
        }
        return out;
    }

    /** Every triangle twice, front and back (each as a quad with its last corner repeated). */
    private static void body(NpcAssets.Mesh mesh, Skinned posed, int[] shade, PoseStack.Pose pose, VertexConsumer out, int light, int overlay) {
        float[] p = posed.pos(), uv = mesh.uv(), n = posed.normal();
        int[] tris = mesh.tris();
        for (int t = 0; t < tris.length; t += 3) {
            for (int side = 0; side < 2; side++) {
                float flip = side == 0 ? 1f : -1f;
                for (int k = 0; k < 4; k++) {
                    int corner = Math.min(k, 2);
                    int v = tris[t + (side == 0 ? corner : 2 - corner)];
                    int i = v * 3;
                    out.addVertex(pose, p[i], p[i + 1], p[i + 2])
                            .setColor(shade == null ? 0xFFFFFFFF : shade[v])
                            .setUv(uv[v * 2], uv[v * 2 + 1])
                            .setOverlay(overlay)
                            .setLight(light);
                    if (shade == null) {
                        out.setNormal(pose, n[i] * flip, n[i + 1] * flip, n[i + 2] * flip);
                    } else {
                        // toon shading is in the colour; an upward normal keeps the game's own lighting even
                        out.setNormal(pose, 0f, 1f, 0f);
                    }
                }
            }
        }
    }

    /** The outline: the mesh pushed out along its normals and back from the eye (ex, ey, ez, model space), so only its rim shows. */
    private static void ink(NpcAssets.Mesh mesh, Skinned posed, float ex, float ey, float ez, PoseStack.Pose pose, VertexConsumer out, int light) {
        float[] p = posed.pos(), uv = mesh.uv(), n = posed.normal();
        int[] tris = mesh.tris();
        float[] moved = new float[p.length];
        for (int v = 0, i = 0; v < mesh.vertices(); v++, i += 3) {
            float vx = ex - p[i], vy = ey - p[i + 1], vz = ez - p[i + 2];
            float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
            float back = len > 1e-4f ? BACK / len : 0f;
            moved[i] = p[i] + n[i] * INK - vx * back;
            moved[i + 1] = p[i + 1] + n[i + 1] * INK - vy * back;
            moved[i + 2] = p[i + 2] + n[i + 2] * INK - vz * back;
        }
        for (int t = 0; t < tris.length; t += 3) {
            for (int k = 0; k < 4; k++) {
                int v = tris[t + Math.min(k, 2)];
                int i = v * 3;
                out.addVertex(pose, moved[i], moved[i + 1], moved[i + 2])
                        .setColor(INK_COLOUR)
                        .setUv(uv[v * 2], uv[v * 2 + 1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, 0f, 1f, 0f);
            }
        }
    }
}
