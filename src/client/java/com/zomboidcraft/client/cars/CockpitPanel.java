package com.zomboidcraft.client.cars;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZomboidCraft;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * A sharp instrument panel laid over a bought cockpit's blurred one ({@link Su27Panel} for the
 * Su-27): the panel itself as one textured sheet on the panel's flat face, cut to the face's shape;
 * the attitude ball and heading card behind windows in it; the needles over it, drawn each frame so
 * they move smoothly; and at night the markings' red light and whichever warning lamps are lit.
 * Only in the aircraft you're sitting in.
 */
final class CockpitPanel {
    /** Where a model's panel face is: a corner, the face's right and up (unit, model space), and the face's extent from the corner. */
    record Face(float[] origin, float[] right, float[] up, float s0, float u1) {
    }

    /** The Su-27's main panel face (measured from the model: tools/cars face fit, 2026-10-27). */
    private static final Map<String, Face> FACES = Map.of("jet_gyrfalcon",
            new Face(new float[]{0.027f, 3.155f, 5.907f}, new float[]{-1f, 0.0004f, -0.0027f}, new float[]{0f, 0.9866f, 0.1633f}, -0.41f, 0.33f));

    /** Only the face's triangles that start this far below its top (mm) count: higher up are other parts on the same plane. */
    private static final float TOP_MM = 195;

    private record Tex(Identifier id, int w, int h) {
    }

    static final class Panel {
        final Face face;
        final float[] normal;
        final Tex day, night, lamps, ball, rose, swatch;
        /** The cockpit-light triangles on the face, which this replaces. */
        final BitSet covered;

        Panel(Face face, float[] normal, Tex day, Tex night, Tex lamps, Tex ball, Tex rose, Tex swatch, BitSet covered) {
            this.face = face;
            this.normal = normal;
            this.day = day;
            this.night = night;
            this.lamps = lamps;
            this.ball = ball;
            this.rose = rose;
            this.swatch = swatch;
            this.covered = covered;
        }
    }

    private static final Map<String, Panel> PANELS = new HashMap<>();

    private CockpitPanel() {
    }

    /** The model's panel, built the first time it's asked for (render thread), or null if it has none. */
    static Panel get(String model, CockpitGlow.Glow glow, float[] eye) {
        if (PANELS.containsKey(model)) {
            return PANELS.get(model);
        }
        Panel panel = null;
        Face face = FACES.get(model);
        CarAssets.Ready ready = face == null ? null : CarAssets.get(model, "usable", 0);
        if (face != null && ready != null) {
            try {
                panel = build(model, face, ready.mesh(), glow, eye);
            } catch (RuntimeException e) {
                ZomboidCraft.LOGGER.error("Couldn't build the instrument panel of {}", model, e);
            }
        } else if (face != null) {
            return null;    // the model isn't loaded yet: try again next frame
        }
        PANELS.put(model, panel);
        return panel;
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    /** A point's place on the face: millimetres right and down from its top left corner, and how far off the face (m). */
    private static float[] onFace(Face f, float[] n, float x, float y, float z) {
        float[] p = {x - f.origin()[0], y - f.origin()[1], z - f.origin()[2]};
        return new float[]{(dot(p, f.right()) - f.s0()) * 1000, (f.u1() - dot(p, f.up())) * 1000, dot(p, n)};
    }

    private static Panel build(String model, Face f, CarAssets.Mesh mesh, CockpitGlow.Glow glow, float[] eye) {
        float[] n = cross(f.right(), f.up());
        float len = (float) Math.sqrt(dot(n, n));
        n = new float[]{n[0] / len, n[1] / len, n[2] / len};
        float[] toEye = {eye[0] - f.origin()[0], eye[1] - f.origin()[1], eye[2] - f.origin()[2]};
        if (dot(n, toEye) < 0) {
            n = new float[]{-n[0], -n[1], -n[2]};
        }
        // the face's shape: its triangles, drawn flat at a pixel a millimetre
        BufferedImage mask = new BufferedImage(Su27Panel.W, Su27Panel.H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = mask.createGraphics();
        g.setColor(Color.WHITE);
        float[] p = mesh.pos();
        for (int t = 0; t < mesh.opaque(); t++) {
            Path2D tri = new Path2D.Float();
            boolean on = true;
            for (int k = 0; k < 3 && on; k++) {
                int i = (t * 3 + k) * 3;
                float[] q = onFace(f, n, p[i], p[i + 1], p[i + 2]);
                on = Math.abs(q[2]) < 0.004f && q[1] >= TOP_MM && q[0] > -5 && q[0] < Su27Panel.W + 5 && q[1] < Su27Panel.H + 5;
                if (k == 0) {
                    tri.moveTo(q[0], q[1]);
                } else {
                    tri.lineTo(q[0], q[1]);
                }
            }
            if (on) {
                tri.closePath();
                g.fill(tri);
            }
        }
        g.dispose();
        // the cockpit lights' copies of those triangles go: the new panel has its own
        BitSet covered = new BitSet();
        if (glow != null) {
            float[] gp = glow.pos();
            for (int t = 0; t < glow.group().length; t++) {
                if (glow.kinds()[glow.group()[t]] != CockpitGlow.KEYS) {
                    continue;
                }
                boolean on = true;
                for (int k = 0; k < 3 && on; k++) {
                    int i = (t * 3 + k) * 3;
                    float[] q = onFace(f, n, gp[i], gp[i + 1], gp[i + 2]);
                    on = Math.abs(q[2]) < 0.006f && q[1] >= TOP_MM - 2 && q[0] > -2 && q[0] < Su27Panel.W + 2 && q[1] < Su27Panel.H + 2;
                }
                if (on) {
                    covered.set(t);
                }
            }
        }
        BufferedImage swatch = new BufferedImage(4, 1, BufferedImage.TYPE_INT_ARGB);
        swatch.setRGB(Su27Panel.WHITE, 0, 0xFFF0F0E8);
        swatch.setRGB(Su27Panel.NEEDLE_ORANGE, 0, 0xFFFF8020);
        swatch.setRGB(Su27Panel.NEEDLE_YELLOW, 0, 0xFFFAD746);
        swatch.setRGB(Su27Panel.DARK, 0, 0xFF141414);
        String base = "dynamic/panel/" + model + "/";
        return new Panel(f, n,
                upload(base + "day", Su27Panel.panel(mask, false), false),
                upload(base + "night", Su27Panel.panel(mask, true), true),
                upload(base + "lamps", Su27Panel.lamps(), true),
                upload(base + "ball", Su27Panel.ball(), false),
                upload(base + "rose", Su27Panel.rose(), false),
                upload(base + "swatch", swatch, false), covered);
    }

    /** A texture from a picture; light ones premultiplied, their strength the brightest channel. */
    private static Tex upload(String name, BufferedImage img, boolean light) {
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
        Identifier id = ZomboidCraft.id(name);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "ZomboidCraft " + name, image));
        return new Tex(id, w, h);
    }

    // ------------------------------------------------------------------ drawing

    /** A point on the face, x right and y down in millimetres, lift metres toward the pilot, into model space. */
    private static float[] at(Panel panel, float x, float y, float lift) {
        Face f = panel.face;
        float s = f.s0() + x / 1000, u = f.u1() - y / 1000;
        return new float[]{f.origin()[0] + f.right()[0] * s + f.up()[0] * u + panel.normal[0] * lift,
                f.origin()[1] + f.right()[1] * s + f.up()[1] * u + panel.normal[1] * lift,
                f.origin()[2] + f.right()[2] * s + f.up()[2] * u + panel.normal[2] * lift};
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Panel panel, float[] p, float u, float v, int colour, int light) {
        out.addVertex(pose, p[0], p[1], p[2])
                .setColor(colour)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, panel.normal[0], panel.normal[1], panel.normal[2]);
    }

    /** A quad on the face: corners (mm) top left, top right, bottom right, bottom left, with their uvs. */
    private static void quad(VertexConsumer out, PoseStack.Pose pose, Panel panel, float[][] mm, float[][] uv, float lift, int colour, int light) {
        // wound to face the pilot: their right is the face's x, up its -y
        for (int k : new int[]{0, 3, 2, 1}) {
            vertex(out, pose, panel, at(panel, mm[k][0], mm[k][1], lift), uv[k][0], uv[k][1], colour, light);
        }
    }

    private static final float[][] WHOLE = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

    /**
     * Draws the panel. light is the light where the aircraft is; dark how dark it is (0 to 1) and powered
     * whether its avionics are on, for the panel lighting and lamps; shaders whether a shader pack is on.
     */
    static void submit(Panel panel, CockpitPages.Data d, PoseStack poseStack, SubmitNodeCollector collector, int light, float dark,
                       boolean powered, boolean shaders) {
        int white = 0xFFFFFFFF;
        // the ball behind the attitude window: it slides with the pitch
        Su27Panel.Dial adi = dial(Su27Panel.Kind.ADI), hsi = dial(Su27Panel.Kind.HSI);
        float mid = panel.ball.h() / 2f - Math.max(-90, Math.min(90, d.pitch())) * Su27Panel.BALL_PX;
        float half = Su27Panel.BALL_SPAN * Su27Panel.BALL_PX;
        float v0 = (mid - half) / panel.ball.h(), v1 = (mid + half) / panel.ball.h();
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(panel.ball.id()), (pose, out) ->
                quad(out, pose, panel, square(adi.x(), adi.y(), adi.r(), 0), new float[][]{{0, v0}, {1, v0}, {1, v1}, {0, v1}}, 0.0006f, white, light));
        // the heading card, turned
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(panel.rose.id()), (pose, out) ->
                quad(out, pose, panel, square(hsi.x(), hsi.y(), hsi.r(), -d.heading()), WHOLE, 0.0006f, white, light));
        // the panel
        float[][] sheet = {{0, 0}, {Su27Panel.W, 0}, {Su27Panel.W, Su27Panel.H}, {0, Su27Panel.H}};
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(panel.day.id()), (pose, out) ->
                quad(out, pose, panel, sheet, WHOLE, 0.0012f, white, light));
        // needles, and the fuel column
        List<Su27Panel.Needle> needles = Su27Panel.needles(d);
        float fuel = Su27Panel.fuel(d);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(panel.swatch.id()), (pose, out) -> {
            for (Su27Panel.Needle n : needles) {
                quad(out, pose, panel, needle(n), swatch(n.swatch()), n.x() == adi.x() && n.y() == adi.y() ? 0.0024f : 0.0018f, white, light);
            }
            float[] f = Su27Panel.FUEL;
            float top = f[1] + f[3] * (1 - fuel);
            quad(out, pose, panel, new float[][]{{f[0] + 5, top}, {f[0] + f[2] - 5, top}, {f[0] + f[2] - 5, f[1] + f[3]}, {f[0] + 5, f[1] + f[3]}},
                    swatch(Su27Panel.WHITE), 0.0018f, white, light);
        });
        if (!powered) {
            return;
        }
        // the red panel lighting at night, the needles' luminous paint, the lamps that are lit
        if (dark > 0.05f) {
            int glow = CarLights.shade(new float[]{1, 1, 1}, dark * 0.9f, shaders);
            collector.submitCustomGeometry(poseStack, type(panel.night.id(), shaders), (pose, out) ->
                    quad(out, pose, panel, sheet, WHOLE, 0.0021f, glow, CarLights.FULL_BRIGHT));
            int paint = CarLights.shade(new float[]{1, 0.55f, 0.4f}, dark * 0.6f, shaders);
            collector.submitCustomGeometry(poseStack, type(panel.swatch.id(), shaders), (pose, out) -> {
                for (Su27Panel.Needle n : needles) {
                    quad(out, pose, panel, needle(n), swatch(n.swatch()), 0.0026f, paint, CarLights.FULL_BRIGHT);
                }
            });
        }
        boolean[] lit = Su27Panel.lit(d);
        int lamp = CarLights.shade(new float[]{1, 1, 1}, 0.75f + 0.25f * dark, shaders);
        boolean flash = d.millis() / 300 % 2 == 0;
        collector.submitCustomGeometry(poseStack, type(panel.lamps.id(), shaders), (pose, out) -> {
            for (int i = 0; i < lit.length && i < Su27Panel.LAMPS.size(); i++) {
                Su27Panel.Lamp l = Su27Panel.LAMPS.get(i);
                // the red ones flash
                if (!lit[i] || l.colour() == Su27Panel.RED && !flash) {
                    continue;
                }
                float u0 = l.x() / Su27Panel.W, u1 = (l.x() + l.w()) / Su27Panel.W, v0l = l.y() / Su27Panel.H, v1l = (l.y() + l.h()) / Su27Panel.H;
                quad(out, pose, panel, new float[][]{{l.x(), l.y()}, {l.x() + l.w(), l.y()}, {l.x() + l.w(), l.y() + l.h()}, {l.x(), l.y() + l.h()}},
                        new float[][]{{u0, v0l}, {u1, v0l}, {u1, v1l}, {u0, v1l}}, 0.0022f, lamp, CarLights.FULL_BRIGHT);
            }
        });
    }

    private static RenderType type(Identifier texture, boolean shaders) {
        return shaders ? RenderTypes.beaconBeam(texture, true) : RenderTypes.eyes(texture);
    }

    private static Su27Panel.Dial dial(Su27Panel.Kind kind) {
        for (Su27Panel.Dial d : Su27Panel.DIALS) {
            if (d.kind() == kind) {
                return d;
            }
        }
        throw new IllegalStateException("no " + kind);
    }

    /** A square r either side of (x, y), turned by degrees (clockwise as the pilot sees it). */
    private static float[][] square(float x, float y, float r, float degrees) {
        double a = Math.toRadians(degrees);
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float[][] out = new float[4][];
        float[][] corner = {{-r, -r}, {r, -r}, {r, r}, {-r, r}};
        for (int k = 0; k < 4; k++) {
            float cx = corner[k][0], cy = corner[k][1];
            out[k] = new float[]{x + cx * c - cy * s, y + cx * s + cy * c};
        }
        return out;
    }

    private static float[][] needle(Su27Panel.Needle n) {
        double t = Math.toRadians(n.angle());
        float dx = (float) Math.sin(t), dy = (float) -Math.cos(t), px = -dy * n.width() / 2, py = dx * n.width() / 2;
        float ax = n.x() + dx * n.length(), ay = n.y() + dy * n.length(), bx = n.x() - dx * n.tail(), by = n.y() - dy * n.tail();
        return new float[][]{{bx - px, by - py}, {ax - px, ay - py}, {ax + px, ay + py}, {bx + px, by + py}};
    }

    private static float[][] swatch(int i) {
        float u = (i + 0.5f) / 4;
        return new float[][]{{u, 0.5f}, {u, 0.5f}, {u, 0.5f}, {u, 0.5f}};
    }
}
