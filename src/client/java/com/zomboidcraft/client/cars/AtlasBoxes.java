package com.zomboidcraft.client.cars;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

/**
 * Toon boxes from an 8 x 8 painted atlas, in an object's own frame turned to its yaw: what the car lift and
 * the shop tools are drawn from. Every face is flat-shaded by which way it faces: tops full, sides a step
 * darker, undersides darkest.
 */
class AtlasBoxes {
    final PoseStack.Pose pose;
    final VertexConsumer out;
    final int light;
    final float cos, sin;

    AtlasBoxes(PoseStack.Pose pose, VertexConsumer out, int light, float yaw) {
        this.pose = pose;
        this.out = out;
        this.light = light;
        double t = -Math.toRadians(yaw);
        cos = (float) Math.cos(t);
        sin = (float) Math.sin(t);
    }

    /** An axis-lined box in the object's frame: centre and half sizes. */
    void box(float x, float y, float z, float hx, float hy, float hz, int tile) {
        obox(new Vector3f(x, y, z), new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1), hx, hy, hz, tile);
    }

    /** A box about centre c along three unit axes with half sizes along each. */
    void obox(Vector3f c, Vector3f a, Vector3f b, Vector3f d, float ha, float hb, float hd, int tile) {
        Vector3f[] k = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            k[i] = new Vector3f(c).add(new Vector3f(a).mul((i & 1) == 0 ? -ha : ha))
                    .add(new Vector3f(b).mul((i & 2) == 0 ? -hb : hb))
                    .add(new Vector3f(d).mul((i & 4) == 0 ? -hd : hd));
        }
        int[][] faces = {{0, 2, 6, 4}, {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 5, 7, 6}};
        float u0 = (tile % 8) / 8f + 0.002f, v0 = (tile / 8) / 8f + 0.002f, du = 1 / 8f - 0.004f;
        for (int[] f : faces) {
            Vector3f mid = new Vector3f(k[f[0]]).add(k[f[1]]).add(k[f[2]]).add(k[f[3]]).mul(0.25f);
            Vector3f n = new Vector3f(mid).sub(c).normalize();
            Vector3f e1 = new Vector3f(k[f[1]]).sub(k[f[0]]), e2 = new Vector3f(k[f[2]]).sub(k[f[0]]);
            boolean flip = new Vector3f(e1).cross(e2).dot(n) < 0;
            Vector3f wn = turn(n);
            // toon steps: tops lit, sides a shade down, undersides dark
            float shade = wn.y > 0.5f ? 1f : wn.y < -0.5f ? 0.5f : Math.abs(wn.x) > Math.abs(wn.z) ? 0.8f : 0.68f;
            int g = (int) (shade * 255);
            int colour = 0xFF000000 | g << 16 | g << 8 | g;
            float[][] uv = {{u0, v0 + du}, {u0 + du, v0 + du}, {u0 + du, v0}, {u0, v0}};
            for (int i = 0; i < 4; i++) {
                Vector3f p = turn(k[f[flip ? 3 - i : i]]);
                out.addVertex(pose, p.x, p.y, p.z).setColor(colour).setUv(uv[i][0], uv[i][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, wn.x, wn.y, wn.z);
            }
        }
    }

    /** The object's frame turned to its yaw (as Vec3.yRot does), about its foot. */
    Vector3f turn(Vector3f v) {
        return new Vector3f(v.x * cos + v.z * sin, v.y, v.z * cos - v.x * sin);
    }
}
