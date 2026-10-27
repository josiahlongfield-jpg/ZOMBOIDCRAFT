package com.zomboidcraft.client.cars;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZomboidCraft;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * The F-22's display bezels and keypad, drawn sharp over the bought cockpit's blurred ones: a dark frame
 * round each of the six colour displays with its row of push buttons on every side (the real jet's
 * option select buttons), and the integrated control panel's keypad under the head-up display. The
 * keys' legends light in the cockpit's NVIS green with the rest of the panel lighting.
 */
final class CockpitBezels {
    /** Which models get bezels: the frame's margins (m) at the sides and top and bottom. */
    private static final Map<String, float[]> MODELS = Map.of("jet_corvid", new float[]{0.017f, 0.021f});
    /** The F-22's keypad face under the head-up display (top left, top right, bottom right, bottom left; model space). */
    private static final float[][] ICP = {{0.104f, 2.954f, 5.770f}, {-0.104f, 2.954f, 5.770f}, {-0.104f, 2.871f, 5.750f}, {0.104f, 2.871f, 5.750f}};

    private static Identifier frame, frameLit, keypad, keypadLit;

    private CockpitBezels() {
    }

    static boolean has(String model) {
        return MODELS.containsKey(model);
    }

    /** The bezels round the live screens and the keypad; legends lit in keys' colour (0 for dark). */
    static void submit(String model, CockpitDisplays.Set live, int keys, PoseStack poseStack, SubmitNodeCollector collector, int light,
                       boolean shaders) {
        float[] margin = MODELS.get(model);
        if (margin == null) {
            return;
        }
        if (frame == null) {
            frame = upload("frame", bezel(false), false);
            frameLit = upload("frame_lit", bezel(true), true);
            keypad = upload("keypad", keypad(false), false);
            keypadLit = upload("keypad_lit", keypad(true), true);
        }
        float[] icpNormal = normal(ICP);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(frame), (pose, out) -> {
            for (CockpitDisplays.Screen s : live.screens()) {
                if (!s.hud) {
                    quad(out, pose, grown(s.corner, margin), s.normal, 0.002f, -1, light);
                }
            }
        });
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(keypad), (pose, out) -> quad(out, pose, ICP, icpNormal, 0.003f, -1, light));
        if (keys != 0) {
            RenderType lit = shaders ? RenderTypes.beaconBeam(frameLit, true) : RenderTypes.eyes(frameLit);
            collector.submitCustomGeometry(poseStack, lit, (pose, out) -> {
                for (CockpitDisplays.Screen s : live.screens()) {
                    if (!s.hud) {
                        quad(out, pose, grown(s.corner, margin), s.normal, 0.0028f, keys, CarLights.FULL_BRIGHT);
                    }
                }
            });
            RenderType pad = shaders ? RenderTypes.beaconBeam(keypadLit, true) : RenderTypes.eyes(keypadLit);
            collector.submitCustomGeometry(poseStack, pad, (pose, out) -> quad(out, pose, ICP, icpNormal, 0.0038f, keys, CarLights.FULL_BRIGHT));
        }
    }

    private static final Map<String, java.util.BitSet> COVERED = new java.util.HashMap<>();

    /** The cockpit lights' key triangles the bezels and keypad lie over (they light their own legends instead). */
    static java.util.BitSet covered(String model, CockpitGlow.Glow glow, CockpitDisplays.Set live) {
        float[] margin = MODELS.get(model);
        if (margin == null || glow == null || live == null) {
            return null;
        }
        return COVERED.computeIfAbsent(model, m -> {
            java.util.List<float[][]> faces = new java.util.ArrayList<>();
            for (CockpitDisplays.Screen s : live.screens()) {
                if (!s.hud) {
                    faces.add(grown(s.corner, margin));
                }
            }
            faces.add(ICP);
            java.util.BitSet out = new java.util.BitSet();
            float[] p = glow.pos();
            for (int t = 0; t < glow.group().length; t++) {
                if (glow.kinds()[glow.group()[t]] != CockpitGlow.KEYS) {
                    continue;
                }
                float[] c = {(p[t * 9] + p[t * 9 + 3] + p[t * 9 + 6]) / 3, (p[t * 9 + 1] + p[t * 9 + 4] + p[t * 9 + 7]) / 3,
                        (p[t * 9 + 2] + p[t * 9 + 5] + p[t * 9 + 8]) / 3};
                for (float[][] f : faces) {
                    if (inside(f, c)) {
                        out.set(t);
                        break;
                    }
                }
            }
            return out;
        });
    }

    /** True if point c lies on the face (within 1.5 cm of its plane, inside its edges). */
    private static boolean inside(float[][] f, float[] c) {
        float[] u = sub(f[1], f[0]), v = sub(f[3], f[0]), d = sub(c, f[0]);
        float uu = dot(u, u), vv = dot(v, v);
        float a = dot(d, u) / uu, b = dot(d, v) / vv;
        float[] n = unit(new float[]{u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]});
        return a > -0.02f && a < 1.02f && b > -0.02f && b < 1.02f && Math.abs(dot(d, n)) < 0.015f;
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /** A screen's corners pushed out by the frame's margins, in its own plane. */
    private static float[][] grown(float[][] c, float[] margin) {
        float[] u = unit(sub(c[1], c[0])), v = unit(sub(c[3], c[0]));
        float sx = margin[0], sy = margin[1];
        return new float[][]{
                add(c[0], u, -sx, v, -sy), add(c[1], u, sx, v, -sy), add(c[2], u, sx, v, sy), add(c[3], u, -sx, v, sy)};
    }

    private static float[] add(float[] p, float[] u, float a, float[] v, float b) {
        return new float[]{p[0] + u[0] * a + v[0] * b, p[1] + u[1] * a + v[1] * b, p[2] + u[2] * a + v[2] * b};
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float[] unit(float[] a) {
        float l = (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return new float[]{a[0] / l, a[1] / l, a[2] / l};
    }

    /** The keypad's face normal, toward the pilot (its corners run clockwise as he sees them). */
    private static float[] normal(float[][] c) {
        float[] a = sub(c[1], c[0]), b = sub(c[3], c[0]);
        float[] n = unit(new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]});
        return n[2] > 0 ? new float[]{-n[0], -n[1], -n[2]} : n;
    }

    /** A textured quad over corners (top left, top right, bottom right, bottom left), lifted toward the pilot, wound to face him. */
    private static void quad(VertexConsumer out, PoseStack.Pose pose, float[][] c, float[] n, float lift, int colour, int light) {
        float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        int[] order = {0, 1, 2, 3};
        float[] a = sub(c[1], c[0]), b = sub(c[2], c[0]);
        float face = (a[1] * b[2] - a[2] * b[1]) * n[0] + (a[2] * b[0] - a[0] * b[2]) * n[1] + (a[0] * b[1] - a[1] * b[0]) * n[2];
        if (face < 0) {
            order = new int[]{0, 3, 2, 1};
        }
        for (int k : order) {
            out.addVertex(pose, c[k][0] + n[0] * lift, c[k][1] + n[1] * lift, c[k][2] + n[2] * lift)
                    .setColor(colour)
                    .setUv(uv[k][0], uv[k][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, n[0], n[1], n[2]);
        }
    }

    // ------------------------------------------------------------------ the pictures

    private static final Color BODY = new Color(0x2A2D2E), EDGE = new Color(0x161818), KEY = new Color(0x3C4042),
            LEGEND = new Color(0xC8D0C8), NVIS = new Color(0x7CFF9A);

    /** A display bezel: a frame with five push buttons along each side, the middle left open for the screen. */
    private static BufferedImage bezel(boolean lit) {
        int w = 256, h = 256, mx = 22, my = 26;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = start(img);
        if (!lit) {
            g.setColor(BODY);
            g.fillRect(0, 0, w, h);
            g.setComposite(java.awt.AlphaComposite.Clear);
            g.fillRect(mx, my, w - 2 * mx, h - 2 * my);
            g.setComposite(java.awt.AlphaComposite.SrcOver);
            g.setColor(EDGE);
            g.setStroke(new BasicStroke(3));
            g.drawRect(mx - 2, my - 2, w - 2 * mx + 3, h - 2 * my + 3);
            g.drawRect(1, 1, w - 3, h - 3);
        }
        for (int i = 0; i < 5; i++) {
            int along = 40 + i * 44;
            button(g, along - 13, 5, 26, my - 10, lit, true);              // top row
            button(g, along - 13, h - my + 5, 26, my - 10, lit, true);     // bottom row
            button(g, 4, along - 13, mx - 8, 26, lit, false);              // left column
            button(g, w - mx + 4, along - 13, mx - 8, 26, lit, false);     // right column
        }
        g.dispose();
        return img;
    }

    private static void button(Graphics2D g, int x, int y, int w, int h, boolean lit, boolean bar) {
        if (!lit) {
            g.setColor(EDGE);
            g.fillRoundRect(x - 1, y - 1, w + 2, h + 2, 5, 5);
            g.setColor(KEY);
            g.fillRoundRect(x, y, w, h, 4, 4);
        }
        // the legend: a short bar across the key's face
        g.setColor(lit ? NVIS : LEGEND);
        if (bar) {
            g.fillRect(x + w / 4, y + h / 2 - 1, w / 2, 3);
        } else {
            g.fillRect(x + w / 2 - 1, y + h / 4, 3, h / 2);
        }
    }

    /** The integrated control panel: a keypad of twelve keys either side, function keys over them, a small readout. */
    private static BufferedImage keypad(boolean lit) {
        int w = 384, h = 176;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = start(img);
        if (!lit) {
            g.setColor(BODY);
            g.fillRect(0, 0, w, h);
            g.setColor(EDGE);
            g.setStroke(new BasicStroke(4));
            g.drawRect(2, 2, w - 5, h - 5);
            g.setColor(new Color(0x101412));
            g.fillRect(132, 16, 120, 40);
        } else {
            g.setColor(NVIS);
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));
            g.drawString("STPT 01", 146, 44);
        }
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "CLR", "0", "ENT"};
        String[] fn = {"COM1", "COM2", "NAV", "IFF", "A/P", "MRK"};
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        for (int side = 0; side < 2; side++) {
            int x0 = side == 0 ? 14 : 262;
            for (int i = 0; i < 12; i++) {
                int x = x0 + i % 3 * 37, y = 22 + i / 3 * 37;
                key(g, x, y, 32, 31, keys[i], lit);
            }
        }
        for (int i = 0; i < 6; i++) {
            key(g, 132 + i % 3 * 41, 72 + i / 3 * 46, 37, 38, fn[i], lit);
        }
        g.dispose();
        return img;
    }

    private static void key(Graphics2D g, int x, int y, int w, int h, String legend, boolean lit) {
        if (!lit) {
            g.setColor(EDGE);
            g.fillRoundRect(x - 2, y - 2, w + 4, h + 4, 7, 7);
            g.setColor(KEY);
            g.fillRoundRect(x, y, w, h, 6, 6);
        }
        g.setColor(lit ? NVIS : LEGEND);
        var fm = g.getFontMetrics();
        float size = legend.length() > 3 ? 11 : legend.length() > 1 ? 13 : 17;
        g.setFont(g.getFont().deriveFont(size));
        fm = g.getFontMetrics();
        g.drawString(legend, x + (w - fm.stringWidth(legend)) / 2f, y + h / 2f + fm.getAscent() / 2.6f);
    }

    private static Graphics2D start(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    /** A texture from a picture; light ones premultiplied, their strength the brightest channel. */
    private static Identifier upload(String name, BufferedImage img, boolean light) {
        int w = img.getWidth(), h = img.getHeight();
        NativeImage image = new NativeImage(w, h, false);
        int[] argb = img.getRGB(0, 0, w, h, null, 0, w);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = argb[y * w + x];
                if (light) {
                    int a = c >>> 24;
                    int r = (c >> 16 & 255) * a / 255, gr = (c >> 8 & 255) * a / 255, b = (c & 255) * a / 255;
                    c = Math.max(r, Math.max(gr, b)) << 24 | r << 16 | gr << 8 | b;
                }
                image.setPixel(x, y, c);
            }
        }
        Identifier id = ZomboidCraft.id("dynamic/bezel/" + name);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "ZomboidCraft " + name, image));
        return id;
    }
}
