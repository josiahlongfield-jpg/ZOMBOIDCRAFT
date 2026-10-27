package com.zomboidcraft.client.zombie.body;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZomboidCraft;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Far-off zombies as pictures: each outfit's baked body drawn once, in software, from eight sides through the
 * frames of standing, shambling and running, into one sheet. A zombie past the nearest few dozen is then a single
 * card turned to face you with the frame for its gait and the side you see, a couple of hundred times cheaper than
 * its body. Made the first time an outfit is needed. Render thread only.
 */
public final class ZSprites {
    public static final int SIDES = 8;
    /** Frames: standing, then a shamble's stride, then a run's. */
    static final int IDLE_FRAMES = 4, WALK_FRAMES = 8, FRAMES = IDLE_FRAMES + WALK_FRAMES * 2;
    /** Pixels a block, and the card's reach in model space (x across, y down from the head's top to the feet). */
    private static final int PX = 32;
    static final float HALF_W = 0.8f, TOP = -0.75f, BOTTOM = 1.55f;
    private static final int W = Math.round(HALF_W * 2 * PX), H = Math.round((BOTTOM - TOP) * PX);
    /** A little light from above and in front, so the picture keeps its shape in the dark. */
    private static final Vector3f LIGHT = new Vector3f(0.3f, -0.8f, -0.5f).normalize();

    /** An outfit's sheet: SIDES columns by FRAMES rows. */
    public record Sheet(Identifier texture) {
        /** The texture rectangle of one picture: u0, v0, u1, v1. */
        public float[] uv(int side, int frame) {
            float du = 1f / SIDES, dv = 1f / FRAMES;
            return new float[] {side * du, frame * dv, (side + 1) * du, (frame + 1) * dv};
        }
    }

    private static final Map<String, Sheet> SHEETS = new HashMap<>();
    private static final Map<String, Boolean> FAILED = new HashMap<>();

    /** The outfit's sheet, made now if this is the first time (null if it can't be). */
    public static Sheet get(String outfit, ZBodyMesh low, Identifier skin, Identifier kit) {
        Sheet s = SHEETS.get(outfit);
        if (s != null || FAILED.containsKey(outfit) || low == null || skin == null || kit == null) {
            return s;
        }
        try {
            s = make(outfit, low, skin, kit);
            SHEETS.put(outfit, s);
        } catch (Exception | LinkageError e) {
            ZomboidCraft.LOGGER.warn("Couldn't make the far-off pictures for {}", outfit, e);
            FAILED.put(outfit, true);
        }
        return s;
    }

    /** Which frame: from how it's moving, its gait so far and its own offset. */
    public static int frame(boolean moving, boolean chasing, float walkPos, float age, int variant) {
        float phase = (variant & 0xFFFF) / 65535f * (float) Math.PI * 2f;
        if (!moving) {
            return (int) Math.floorMod((long) Math.floor((age / 20f + phase) * 1.2f), IDLE_FRAMES);
        }
        float q = walkPos * (chasing ? 0.8f : 1.2f) + phase;
        int f = (int) Math.floorMod((long) Math.floor(q / (Math.PI * 2) * WALK_FRAMES), WALK_FRAMES);
        return IDLE_FRAMES + (chasing ? WALK_FRAMES : 0) + f;
    }

    /**
     * The card for one zombie, in its model space (y down, front -z): turned to the side nearest the camera, which
     * sits at {@code cam} in that space.
     */
    public static void card(Sheet sheet, int frame, Vector3f cam, PoseStack.Pose pose, VertexConsumer out, int light, int overlay) {
        double a = Math.atan2(cam.x, cam.z);
        int side = (int) Math.floorMod(Math.round(a / (Math.PI * 2 / SIDES)), SIDES);
        float ang = (float) (side * Math.PI * 2 / SIDES);
        float dx = (float) Math.sin(ang), dz = (float) Math.cos(ang);   // toward the camera
        float rx = dz, rz = -dx;                                         // across the picture, left to right
        float[] uv = sheet.uv(side, frame);
        float[][] corners = {{-HALF_W, TOP, uv[0], uv[1]}, {-HALF_W, BOTTOM, uv[0], uv[3]},
                {HALF_W, BOTTOM, uv[2], uv[3]}, {HALF_W, TOP, uv[2], uv[1]}};
        // both faces, so it shows whichever way the render type culls
        for (int face = 0; face < 2; face++) {
            for (int i = 0; i < 4; i++) {
                float[] c = corners[face == 0 ? i : 3 - i];
                float s = face == 0 ? 1f : -1f;
                out.addVertex(pose, rx * c[0], c[1], rz * c[0])
                        .setColor(-1)
                        .setUv(c[2], c[3])
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(pose, dx * s, 0f, dz * s);
            }
        }
    }

    private static Sheet make(String outfit, ZBodyMesh low, Identifier skinId, Identifier kitId) throws Exception {
        long start = System.nanoTime();
        int[][] skin = read(skinId), kit = read(kitId);
        int sw = SIDES * W, sh = FRAMES * H;
        int[] sheet = new int[sw * sh];
        ZMove move = new ZMove();
        ZMove.Pose pose = new ZMove.Pose();
        ZMove.Input in = new ZMove.Input();
        float[] weights = new float[ZMove.MODES];
        Matrix4f[] world = new Matrix4f[ZMove.BONES];
        for (int i = 0; i < world.length; i++) {
            world[i] = new Matrix4f();
        }
        int[] bodyQuads = low.quads(true, ZDetail.FULL, false), kitQuads = low.quads(false, ZDetail.FULL, false);
        float[] depth = new float[W * H];
        for (int f = 0; f < FRAMES; f++) {
            java.util.Arrays.fill(weights, 0f);
            int mode = f < IDLE_FRAMES ? ZMove.IDLE : f < IDLE_FRAMES + WALK_FRAMES ? ZMove.SHAMBLE : ZMove.CHASE;
            weights[mode] = 1f;
            int k = f < IDLE_FRAMES ? f : (f - IDLE_FRAMES) % WALK_FRAMES;
            in.t = f < IDLE_FRAMES ? k / 1.2f : k * 0.1f;
            in.gait = (k + 0.5f) / WALK_FRAMES * (float) Math.PI * 2f;
            in.phase = 0f;
            in.look = 0f;
            in.attack = 0f;
            in.legless = false;
            move.pose(pose, weights, in);
            float[] mats = ZBodyLayer.matrices(low, pose, false, world);
            for (int side = 0; side < SIDES; side++) {
                java.util.Arrays.fill(depth, -Float.MAX_VALUE);
                double a = side * Math.PI * 2 / SIDES;
                float dx = (float) Math.sin(a), dz = (float) Math.cos(a);
                int ox = side * W, oy = f * H;
                draw(low, mats, bodyQuads, skin, dx, dz, sheet, sw, ox, oy, depth);
                draw(low, mats, kitQuads, kit, dx, dz, sheet, sw, ox, oy, depth);
            }
        }
        NativeImage image = new NativeImage(sw, sh, false);
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                image.setPixel(x, y, sheet[y * sw + x]);
            }
        }
        Identifier id = ZomboidCraft.id("dynamic/zsprite/" + outfit);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "ZomboidCraft far zombie " + outfit, image));
        ZomboidCraft.LOGGER.info("Far-off zombie pictures for {} made in {} ms", outfit, (System.nanoTime() - start) / 1_000_000);
        return new Sheet(id);
    }

    /** One set of quads into one picture: flat-shaded a little, nearest wins, see-through texels left out. */
    private static void draw(ZBodyMesh m, float[] a, int[] quads, int[][] tex, float dx, float dz, int[] sheet, int sw,
            int ox, int oy, float[] depth) {
        int tw = tex[1][0], th = tex[1][1];
        int[] px = tex[0];
        float rx = dz, rz = -dx;
        float[] sx = new float[4], sy = new float[4], sd = new float[4], su = new float[4], sv = new float[4];
        for (int q : quads) {
            int o = m.bone[q] * 16;
            float nx0 = m.normal[q * 3], ny0 = m.normal[q * 3 + 1], nz0 = m.normal[q * 3 + 2];
            float nx = a[o] * nx0 + a[o + 4] * ny0 + a[o + 8] * nz0;
            float ny = a[o + 1] * nx0 + a[o + 5] * ny0 + a[o + 9] * nz0;
            float nz = a[o + 2] * nx0 + a[o + 6] * ny0 + a[o + 10] * nz0;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            float lit = len > 0 ? Math.max(0f, (nx * LIGHT.x + ny * LIGHT.y + nz * LIGHT.z) / len) : 0.5f;
            float shade = 0.6f + 0.4f * lit;
            float u0 = m.uv[q * 4], v0 = m.uv[q * 4 + 1], u1 = m.uv[q * 4 + 2], v1 = m.uv[q * 4 + 3];
            for (int c = 0; c < 4; c++) {
                int i = q * 12 + c * 3;
                float x = m.pos[i], y = m.pos[i + 1], z = m.pos[i + 2];
                float wx = a[o] * x + a[o + 4] * y + a[o + 8] * z + a[o + 12];
                float wy = a[o + 1] * x + a[o + 5] * y + a[o + 9] * z + a[o + 13];
                float wz = a[o + 2] * x + a[o + 6] * y + a[o + 10] * z + a[o + 14];
                sx[c] = (wx * rx + wz * rz + HALF_W) * PX;
                sy[c] = (wy - TOP) * PX;
                sd[c] = wx * dx + wz * dz;
                su[c] = c == 1 || c == 2 ? u1 : u0;
                sv[c] = c >= 2 ? v1 : v0;
            }
            tri(0, 1, 2, sx, sy, sd, su, sv, px, tw, th, shade, sheet, sw, ox, oy, depth);
            tri(0, 2, 3, sx, sy, sd, su, sv, px, tw, th, shade, sheet, sw, ox, oy, depth);
        }
    }

    private static void tri(int i0, int i1, int i2, float[] sx, float[] sy, float[] sd, float[] su, float[] sv, int[] px,
            int tw, int th, float shade, int[] sheet, int sw, int ox, int oy, float[] depth) {
        float x0 = sx[i0], y0 = sy[i0], x1 = sx[i1], y1 = sy[i1], x2 = sx[i2], y2 = sy[i2];
        float area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
        if (Math.abs(area) < 1e-6f) {
            return;
        }
        int minX = Math.max(0, (int) Math.floor(Math.min(x0, Math.min(x1, x2))));
        int maxX = Math.min(W - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2))));
        int minY = Math.max(0, (int) Math.floor(Math.min(y0, Math.min(y1, y2))));
        int maxY = Math.min(H - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2))));
        for (int y = minY; y <= maxY; y++) {
            float cy = y + 0.5f;
            for (int x = minX; x <= maxX; x++) {
                float cx = x + 0.5f;
                float w0 = ((x1 - cx) * (y2 - cy) - (x2 - cx) * (y1 - cy)) / area;
                float w1 = ((x2 - cx) * (y0 - cy) - (x0 - cx) * (y2 - cy)) / area;
                float w2 = 1f - w0 - w1;
                if (w0 < -1e-4f || w1 < -1e-4f || w2 < -1e-4f) {
                    continue;
                }
                float d = w0 * sd[i0] + w1 * sd[i1] + w2 * sd[i2];
                int at = y * W + x;
                if (d <= depth[at]) {
                    continue;
                }
                float u = w0 * su[i0] + w1 * su[i1] + w2 * su[i2], v = w0 * sv[i0] + w1 * sv[i1] + w2 * sv[i2];
                int c = px[Math.clamp((int) (v * th), 0, th - 1) * tw + Math.clamp((int) (u * tw), 0, tw - 1)];
                if ((c >>> 24) < 128) {
                    continue;
                }
                depth[at] = d;
                int r = Math.round((c >> 16 & 255) * shade), g = Math.round((c >> 8 & 255) * shade), b = Math.round((c & 255) * shade);
                sheet[(oy + y) * sw + ox + x] = 0xFF000000 | r << 16 | g << 8 | b;
            }
        }
    }

    private static int[][] read(Identifier id) throws Exception {
        try (InputStream s = Minecraft.getInstance().getResourceManager().getResource(id).orElseThrow().open()) {
            BufferedImage img = ImageIO.read(s);
            int w = img.getWidth(), h = img.getHeight();
            int[] px = img.getRGB(0, 0, w, h, null, 0, w);
            return new int[][] {px, {w, h}};
        }
    }

    private ZSprites() {
    }
}
