package com.zomboidcraft.client.zombie.body;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZomboidCraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A torn end, as the preview builds it (limbs.js rag): a dark wet cap, a stub of bone and a few ragged shreds
 * hanging off it, its open face along -y. Left on the body where a limb came off, and on the limb itself.
 */
public final class Gore {
    public static final Identifier TEXTURE = ZomboidCraft.id("textures/entity/zbody/gore.png");
    /** Texture squares: wet flesh, bone, shreds of muscle. */
    private static final float[][] REGION = {{0f, 0f, 0.5f, 0.5f}, {0.5f, 0f, 1f, 0.5f}, {0f, 0.5f, 0.5f, 1f}};
    public static final int FLESH = 0, BONE = 1, SHRED = 2;
    /** Each face's corners as signs of the half sizes, and its normal. */
    private static final float[][] FACES = {
            {1, -1, -1, 1, 1, -1, 1, 1, 1, 1, -1, 1, 1, 0, 0},
            {-1, -1, 1, -1, 1, 1, -1, 1, -1, -1, -1, -1, -1, 0, 0},
            {-1, 1, -1, -1, 1, 1, 1, 1, 1, 1, 1, -1, 0, 1, 0},
            {-1, -1, 1, -1, -1, -1, 1, -1, -1, 1, -1, 1, 0, -1, 0},
            {1, -1, 1, 1, 1, 1, -1, 1, 1, -1, -1, 1, 0, 0, 1},
            {-1, -1, -1, -1, 1, -1, 1, 1, -1, 1, -1, -1, 0, 0, -1}};
    private static final float[] U = {0, 0, 1, 1}, V = {1, 0, 0, 1};

    private final Matrix4f box = new Matrix4f();
    private final Vector3f p = new Vector3f(), n = new Vector3f();

    /**
     * Draws a torn end.
     *
     * @param at from the end's own space to the space being drawn in
     * @param width how thick the limb is there (preview units)
     * @param bone whether a stub of bone sticks out of it
     * @param seed picks how the shreds hang
     */
    public void rag(VertexConsumer out, PoseStack.Pose pose, Matrix4f at, float width, boolean bone, int seed,
                    int light, int overlay) {
        box(out, pose, at, 0f, 0.2f, 0f, 0f, width + 0.5f, 1.4f, width + 0.5f, FLESH, light, overlay);
        if (bone) {
            box(out, pose, at, 0.3f, -0.9f, 0.15f, 0f, 0.9f, 1.9f, 0.9f, BONE, light, overlay);
        }
        int r = seed * 0x9E3779B9;
        for (int i = 0; i < 4; i++) {
            r = r * 1103515245 + 12345;
            float s = 0.4f + (r >>> 8 & 0xFF) / 255f * 0.6f;
            float h = 1f + (r >>> 16 & 0xFF) / 255f * 1.6f;
            r = r * 1103515245 + 12345;
            float a = (r >>> 8 & 0xFFFF) / 65535f * (float) Math.PI * 2f;
            box(out, pose, at, (float) Math.cos(a) * width * 0.45f, -0.4f, (float) Math.sin(a) * width * 0.45f, a,
                    s, h, 0.3f, SHRED, light, overlay);
        }
    }

    /** A square of the gore texture: wet flesh, bone or shreds of muscle (u0, v0, u1, v1). */
    public static float[] region(int region) {
        return REGION[region];
    }

    /** A little cube about the space's middle, every face from one square of a texture (u0, v0, u1, v1), tinted. */
    public void cube(VertexConsumer out, PoseStack.Pose pose, float size, float[] uv, int colour, int light, int overlay) {
        box.identity();
        faces(out, pose, size, size, size, uv, colour, light, overlay);
    }

    /** A box of one square of the texture: its middle and its turn about y in the end's space, then its sizes. */
    void box(VertexConsumer out, PoseStack.Pose pose, Matrix4f at, float cx, float cy, float cz, float turn,
             float sx, float sy, float sz, int region, int light, int overlay) {
        box.set(at).translate(cx, cy, cz).rotateY(turn);
        faces(out, pose, sx, sy, sz, REGION[region], -1, light, overlay);
    }

    private void faces(VertexConsumer out, PoseStack.Pose pose, float sx, float sy, float sz, float[] uv, int colour,
                       int light, int overlay) {
        for (float[] f : FACES) {
            box.transformDirection(n.set(f[12], f[13], f[14])).normalize();
            for (int c = 0; c < 4; c++) {
                box.transformPosition(p.set(f[c * 3] * sx / 2, f[c * 3 + 1] * sy / 2, f[c * 3 + 2] * sz / 2));
                out.addVertex(pose, p.x, p.y, p.z)
                        .setColor(colour)
                        .setUv(uv[0] + (uv[2] - uv[0]) * U[c], uv[1] + (uv[3] - uv[1]) * V[c])
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(pose, n.x, n.y, n.z);
            }
        }
    }
}
