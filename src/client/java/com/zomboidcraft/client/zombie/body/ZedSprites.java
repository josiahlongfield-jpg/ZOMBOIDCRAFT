package com.zomboidcraft.client.zombie.body;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.client.npc.NpcSkin;
import com.zomboidcraft.client.survivor.ZedBodies;
import com.zomboidcraft.client.zombie.WalkerRenderState;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Far-off Zomboid zombies as pictures: the thinned Zomboid body (ZedBodies.far, level 1) drawn once in software from
 * eight sides through standing, shambling and running, into one sheet laid out like ZSprites' (so its card and frame
 * code draw it). One sheet per look family (sex and Zomboid outfit), not per zombie. Past ZDetail's sprite distance a
 * whole, upright Zomboid zombie is then one card instead of a thousand skinned triangles. Josiah's rule holds: with the
 * Zomboid zombies in, the picture is of a Zomboid zombie, never the old body. Render thread only.
 */
public final class ZedSprites {
    /** The same sheet geometry as ZSprites: pixels a block, card reach in model space (y down, feet at 1.501). */
    private static final int PX = 32;
    private static final int W = Math.round(ZSprites.HALF_W * 2 * PX), H = Math.round((ZSprites.BOTTOM - ZSprites.TOP) * PX);
    private static final Vector3f LIGHT = new Vector3f(0.3f, -0.8f, -0.5f).normalize();
    /** Clips for the three rows of frames, first one the pack has. */
    private static final String[] IDLE = {"idle", "lunge_idle"}, SHAMBLE = {"walk1", "walk2", "walk3"},
            CHASE = {"walk_fast", "sprint1", "walk1"};

    private static final Map<String, ZSprites.Sheet> SHEETS = new HashMap<>();
    private static final Map<String, Boolean> FAILED = new HashMap<>();
    /** Each zombie (variant and outfit) to its look family. */
    private static final Map<String, String> FAMILY = new HashMap<>();
    private static final long MAKE_GAP_NANOS = 150_000_000L;
    private static long lastMade;

    /** The family's sheet for this zombie, made now if it's the first of its family and its turn (null if not yet). */
    public static ZSprites.Sheet get(int variant, String outfit) {
        // a zombie's family never changes: once its sheet is made, skip working out its look again
        String key = variant + "|" + outfit;
        String known = FAMILY.get(key);
        if (known != null && SHEETS.containsKey(known)) {
            return SHEETS.get(known);
        }
        ZedBodies.Far far = ZedBodies.far(variant, outfit);
        if (far == null) {
            return null;
        }
        FAMILY.put(key, far.family());
        ZSprites.Sheet s = SHEETS.get(far.family());
        if (s != null || FAILED.containsKey(far.family())) {
            return s;
        }
        long now = System.nanoTime();
        if (now - lastMade < MAKE_GAP_NANOS) {
            return null;
        }
        lastMade = now;
        try {
            s = make(far);
            if (s != null) {
                SHEETS.put(far.family(), s);
            }
        } catch (Exception | LinkageError e) {
            ZomboidCraft.LOGGER.warn("Couldn't make the far-off Zomboid pictures for {}", far.family(), e);
            FAILED.put(far.family(), true);
        }
        return s;
    }

    /**
     * Draws a far, whole, upright Zomboid zombie as its card; false leaves it to the body (not far enough, hurt in a
     * way the picture doesn't show, lying or falling, or no sheet yet).
     */
    public static boolean submit(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, WalkerRenderState state) {
        if (!ZDetail.sprite(state.distanceToCameraSq) || state.cuts != 0 || state.missingLimb != 0 || state.legless
                || state.crawler || state.deathTime > 0f || state.zpose == null || state.zpose.lying || state.zpose.tilt != 0f
                || state.outfit == null) {
            return false;
        }
        ZSprites.Sheet sheet = get(state.variant, state.outfit);
        if (sheet == null) {
            return false;
        }
        boolean moving = state.walkAnimationSpeed > 0.08f;
        int frame = ZSprites.frame(moving, moving && state.isAggressive, state.walkAnimationPos, state.ageInTicks, state.variant);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(sheet.texture()), (pose, out) -> {
            Vector3f cam = new Matrix4f(pose.pose()).invert().transformPosition(new Vector3f());
            ZSprites.card(sheet, frame, cam, pose, out, light, overlay);
        });
        ZDetail.drew(3, 1);
        return true;
    }

    private static String clip(NpcSkin skin, String[] names) {
        for (String n : names) {
            if (skin.clips.containsKey(n)) {
                return n;
            }
        }
        return null;
    }

    private static ZSprites.Sheet make(ZedBodies.Far far) {
        long start = System.nanoTime();
        NpcSkin skin = far.pack().skin;
        // every mesh it wears and its texture's texels; give up for now if any texture isn't readable yet
        java.util.List<NpcSkin.Group> groups = new java.util.ArrayList<>();
        java.util.List<int[][]> texels = new java.util.ArrayList<>();
        Map<Identifier, int[][]> read = new HashMap<>();
        for (NpcSkin.Group g : skin.groups) {
            if (!far.show().test(g)) {
                continue;
            }
            Identifier id = far.textures().apply(g);
            if (id == null) {
                continue;
            }
            int[][] t = read.computeIfAbsent(id, ZedSprites::texels);
            if (t == null) {
                return null;
            }
            groups.add(g);
            texels.add(t);
        }
        if (groups.isEmpty()) {
            return null;
        }
        String idle = clip(skin, IDLE), shamble = clip(skin, SHAMBLE), chase = clip(skin, CHASE);
        int sw = ZSprites.SIDES * W, sh = ZSprites.FRAMES * H;
        int[] sheet = new int[sw * sh];
        float[] depth = new float[W * H];
        float[] m = new float[skin.boneNames.length * 12];
        Map<NpcSkin.Group, float[][]> posed = new HashMap<>();
        for (int f = 0; f < ZSprites.FRAMES; f++) {
            String c;
            float at;
            if (f < ZSprites.IDLE_FRAMES) {
                c = idle;
                at = f / (float) ZSprites.IDLE_FRAMES;
            } else {
                int k = (f - ZSprites.IDLE_FRAMES) % ZSprites.WALK_FRAMES;
                c = f < ZSprites.IDLE_FRAMES + ZSprites.WALK_FRAMES ? shamble : chase;
                at = (k + 0.5f) / ZSprites.WALK_FRAMES;
            }
            float seconds = c == null ? 0 : at * skin.clips.get(c).seconds();
            Matrix4f[] bones = ZedBodies.farPose(far.pack(), c == null ? "idle" : c, seconds);
            rows(bones, m);
            for (NpcSkin.Group g : groups) {
                skinned(g, m, posed.computeIfAbsent(g, k -> new float[][] {new float[k.vertices * 3], new float[k.vertices * 3]}));
            }
            for (int side = 0; side < ZSprites.SIDES; side++) {
                Arrays.fill(depth, -Float.MAX_VALUE);
                double a = side * Math.PI * 2 / ZSprites.SIDES;
                float dx = (float) Math.sin(a), dz = (float) Math.cos(a);
                for (int i = 0; i < groups.size(); i++) {
                    float[][] p = posed.get(groups.get(i));
                    draw(groups.get(i), p[0], p[1], texels.get(i), dx, dz, sheet, sw, side * W, f * H, depth);
                }
            }
        }
        NativeImage image = new NativeImage(sw, sh, false);
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                image.setPixel(x, y, sheet[y * sw + x]);
            }
        }
        String name = far.family().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        Identifier id = ZomboidCraft.id("dynamic/zedsprite/" + name);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "ZomboidCraft far Zomboid zombie " + name, image));
        ZomboidCraft.LOGGER.info("Far-off Zomboid pictures for {} made in {} ms", far.family(), (System.nanoTime() - start) / 1_000_000);
        return new ZSprites.Sheet(id);
    }

    /** A composed (dynamic) texture's texels as ARGB, or null if it isn't there yet. */
    private static int[][] texels(Identifier id) {
        AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(id);
        if (!(t instanceof DynamicTexture dyn) || dyn.getPixels() == null) {
            return null;
        }
        NativeImage img = dyn.getPixels();
        int w = img.getWidth(), h = img.getHeight();
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                px[y * w + x] = img.getPixel(x, y);
            }
        }
        return new int[][] {px, {w, h}};
    }

    /** Bones as 3x4 rows (x', y', z'), as NpcSkinDraw lays them out. */
    private static void rows(Matrix4f[] bones, float[] m) {
        for (int b = 0; b < bones.length && b * 12 + 11 < m.length; b++) {
            Matrix4f x = bones[b];
            int o = b * 12;
            m[o] = x.m00(); m[o + 1] = x.m10(); m[o + 2] = x.m20(); m[o + 3] = x.m30();
            m[o + 4] = x.m01(); m[o + 5] = x.m11(); m[o + 6] = x.m21(); m[o + 7] = x.m31();
            m[o + 8] = x.m02(); m[o + 9] = x.m12(); m[o + 10] = x.m22(); m[o + 11] = x.m32();
        }
    }

    /** NpcSkinDraw's skinning, into the body's own metres (y up, +z ahead, feet at 0). */
    private static void skinned(NpcSkin.Group g, float[] m, float[][] out) {
        float[] p = g.pos, n = g.normal, w = g.weight, pos = out[0], normal = out[1];
        byte[] j = g.joint;
        for (int v = 0; v < g.vertices; v++) {
            float px = p[v * 3], py = p[v * 3 + 1], pz = p[v * 3 + 2];
            float nx = n[v * 3], ny = n[v * 3 + 1], nz = n[v * 3 + 2];
            float ox = 0, oy = 0, oz = 0, qx = 0, qy = 0, qz = 0;
            for (int k = 0; k < 4; k++) {
                float k4 = w[v * 4 + k];
                if (k4 <= 0) {
                    continue;
                }
                int o = (j[v * 4 + k] & 255) * 12;
                ox += k4 * (m[o] * px + m[o + 1] * py + m[o + 2] * pz + m[o + 3]);
                oy += k4 * (m[o + 4] * px + m[o + 5] * py + m[o + 6] * pz + m[o + 7]);
                oz += k4 * (m[o + 8] * px + m[o + 9] * py + m[o + 10] * pz + m[o + 11]);
                qx += k4 * (m[o] * nx + m[o + 1] * ny + m[o + 2] * nz);
                qy += k4 * (m[o + 4] * nx + m[o + 5] * ny + m[o + 6] * nz);
                qz += k4 * (m[o + 8] * nx + m[o + 9] * ny + m[o + 10] * nz);
            }
            pos[v * 3] = ox;
            pos[v * 3 + 1] = oy;
            pos[v * 3 + 2] = oz;
            normal[v * 3] = qx;
            normal[v * 3 + 1] = qy;
            normal[v * 3 + 2] = qz;
        }
    }

    /**
     * One mesh into one picture. Body metres go to the walker's model space as ZedBodies.submit places them
     * (x, 1.501 - y, -z), then onto the card as ZSprites does: nearest wins, clear texels left out, lit a little.
     */
    private static void draw(NpcSkin.Group g, float[] pos, float[] normal, int[][] tex, float dx, float dz, int[] sheet,
            int sw, int ox, int oy, float[] depth) {
        int tw = tex[1][0], th = tex[1][1];
        int[] px = tex[0];
        float rx = dz, rz = -dx;
        float[] uv = g.uv;
        int[] tris = g.tris;
        float[] sx = new float[3], sy = new float[3], sd = new float[3], su = new float[3], sv = new float[3];
        for (int t = 0; t + 2 < tris.length; t += 3) {
            float lx = 0, ly = 0, lz = 0;
            for (int c = 0; c < 3; c++) {
                int v = tris[t + c], i = v * 3;
                float mx = pos[i], my = 1.501f - pos[i + 1], mz = -pos[i + 2];
                sx[c] = (mx * rx + mz * rz + ZSprites.HALF_W) * PX;
                sy[c] = (my - ZSprites.TOP) * PX;
                sd[c] = mx * dx + mz * dz;
                su[c] = uv[v * 2];
                sv[c] = uv[v * 2 + 1];
                lx += normal[i];
                ly -= normal[i + 1];
                lz -= normal[i + 2];
            }
            float len = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
            float lit = len > 0 ? Math.max(0f, (lx * LIGHT.x + ly * LIGHT.y + lz * LIGHT.z) / len) : 0.5f;
            tri(sx, sy, sd, su, sv, px, tw, th, 0.6f + 0.4f * lit, sheet, sw, ox, oy, depth);
        }
    }

    private static void tri(float[] sx, float[] sy, float[] sd, float[] su, float[] sv, int[] px, int tw, int th,
            float shade, int[] sheet, int sw, int ox, int oy, float[] depth) {
        float x0 = sx[0], y0 = sy[0], x1 = sx[1], y1 = sy[1], x2 = sx[2], y2 = sy[2];
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
                float d = w0 * sd[0] + w1 * sd[1] + w2 * sd[2];
                int at = y * W + x;
                if (d <= depth[at]) {
                    continue;
                }
                float u = w0 * su[0] + w1 * su[1] + w2 * su[2], v = w0 * sv[0] + w1 * sv[1] + w2 * sv[2];
                int c = px[Math.clamp((int) (v * th), 0, th - 1) * tw + Math.clamp((int) (u * tw), 0, tw - 1)];
                if ((c >>> 24) < 128) {
                    continue;
                }
                depth[at] = d;
                int r = Math.round((c >> 16 & 255) * shade), gg = Math.round((c >> 8 & 255) * shade), b = Math.round((c & 255) * shade);
                sheet[(oy + y) * sw + ox + x] = 0xFF000000 | r << 16 | gg << 8 | b;
            }
        }
    }

    private ZedSprites() {
    }
}
