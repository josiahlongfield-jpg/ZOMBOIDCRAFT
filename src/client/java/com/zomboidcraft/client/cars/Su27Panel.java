package com.zomboidcraft.client.cars;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Su-27's main instrument panel, drawn sharp over the bought model's blurred one, laid out after
 * photos of the real cockpit: the attitude indicator (the Russian kind: the horizon only moves up and
 * down, the little aircraft banks) over the horizontal situation indicator on the centreline; angle of
 * attack and load, airspeed, the altimeter and the climb on the left; the radar altimeter and Mach
 * meter right of the attitude; fuel, both engines' speed and temperature and the warning receiver on
 * the right; the warning lamps down the left. Everything that moves is drawn as its own piece over
 * the panel each frame ({@link #needles}), so the needles sweep smoothly: this class paints the still
 * textures once and says where the moving parts go.
 *
 * <p>Coordinates are millimetres on the panel face, x to the pilot's right, y down, the face's top left
 * corner (0, 0); the face is {@link #W} by {@link #H} mm.
 */
final class Su27Panel {
    static final int W = 879, H = 550;
    /** Texture pixels per millimetre. */
    static final float K = 1.2f;

    static final Color PANEL = new Color(88, 124, 128), PANEL_DARK = new Color(62, 88, 92), BEZEL = new Color(34, 38, 40),
            FACE = new Color(14, 15, 16), MARK = new Color(236, 236, 228), ORANGE = new Color(255, 120, 30), RED = new Color(220, 40, 30),
            YELLOW = new Color(250, 210, 60), GLOW = new Color(255, 80, 45), LABEL = new Color(225, 232, 228);

    enum Kind { ADI, HSI, AOA, SPEED, ALT, VVI, RADALT, MACH, RPM, EGT, CLOCK, STANDBY }

    record Dial(Kind kind, float x, float y, float r) {
    }

    static final List<Dial> DIALS = List.of(
            new Dial(Kind.ADI, 410, 262, 50), new Dial(Kind.HSI, 410, 380, 52),
            new Dial(Kind.AOA, 165, 255, 40), new Dial(Kind.SPEED, 262, 255, 42),
            new Dial(Kind.ALT, 165, 355, 42), new Dial(Kind.VVI, 262, 355, 40),
            new Dial(Kind.STANDBY, 150, 440, 27), new Dial(Kind.CLOCK, 262, 428, 25),
            new Dial(Kind.RADALT, 530, 258, 38), new Dial(Kind.MACH, 528, 362, 27),
            new Dial(Kind.RPM, 712, 335, 36), new Dial(Kind.EGT, 712, 425, 30));

    /** The fuel gauge's column: left, top, width, height (mm). */
    static final float[] FUEL = {604, 296, 26, 140};

    /** The warning lamps: x, y, width, height (mm), legend, colour. */
    record Lamp(float x, float y, float w, float h, String text, Color colour) {
    }

    static final List<Lamp> LAMPS = List.of(
            new Lamp(68, 318, 48, 20, "ПОЖАР", RED),
            new Lamp(68, 342, 48, 20, "ОПАСН ВЫСОТА", RED),
            new Lamp(68, 366, 48, 20, "ОСТАТОК 800", YELLOW),
            new Lamp(68, 390, 48, 20, "КРИТ РЕЖИМ", RED),
            new Lamp(40, 414, 48, 20, "ВЫПУСТИ ШАССИ", YELLOW),
            new Lamp(40, 438, 48, 18, "ГЕНЕР", YELLOW),
            new Lamp(40, 460, 48, 18, "ГИДРО", YELLOW));

    private Su27Panel() {
    }

    private static final Map<Integer, Font> FONTS = new HashMap<>();

    private static Font font(float px) {
        return FONTS.computeIfAbsent(Math.max(5, Math.round(px)), s -> new Font(Font.SANS_SERIF, Font.BOLD, s));
    }

    private static void text(Graphics2D g, Color c, String s, double x, double y, float px) {
        g.setColor(c);
        g.setFont(font(px));
        FontMetrics m = g.getFontMetrics();
        g.drawString(s, (float) (x - m.stringWidth(s) / 2.0), (float) (y + px * 0.36));
    }

    private static void pen(Graphics2D g, Color c, float w) {
        g.setColor(c);
        g.setStroke(new BasicStroke(w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
    }

    private static Graphics2D begin(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(K, K);
        return g;
    }

    // ------------------------------------------------------------------ scales (angle clockwise from 12 o'clock, degrees)

    static float speedAngle(float kmh) {
        return 210 + piecewise(kmh, new float[]{0, 200, 400, 600, 1000, 1500, 2500}, new float[]{0, 60, 120, 170, 230, 275, 320});
    }

    static float vviAngle(float ms) {
        return 270 + Math.signum(ms) * piecewise(Math.abs(ms), new float[]{0, 10, 30, 60, 150}, new float[]{0, 45, 85, 120, 160});
    }

    static float radaltAngle(float m) {
        return 210 + piecewise(m, new float[]{0, 10, 20, 50, 100, 200, 300, 500, 1000, 1500},
                new float[]{0, 30, 55, 95, 130, 170, 195, 230, 275, 300});
    }

    static float machAngle(float mach) {
        return 225 + Math.max(0, Math.min(1, (mach - 0.2f) / 2.3f)) * 270;
    }

    static float rpmAngle(float pct) {
        return 210 + Math.max(0, Math.min(110, pct)) / 110 * 300;
    }

    static float egtAngle(float c) {
        return 210 + Math.max(0, Math.min(1, (c - 200) / 800)) * 300;
    }

    static float aoaAngle(float deg) {
        return 210 + Math.max(-10, Math.min(30, deg)) * 3 + 30;
    }

    static float loadAngle(float g) {
        return 150 - (Math.max(-2, Math.min(9, g)) + 2) * (120f / 11);
    }

    private static float piecewise(float v, float[] at, float[] to) {
        if (v <= at[0]) {
            return to[0];
        }
        for (int i = 1; i < at.length; i++) {
            if (v <= at[i]) {
                return to[i - 1] + (to[i] - to[i - 1]) * (v - at[i - 1]) / (at[i] - at[i - 1]);
            }
        }
        return to[to.length - 1];
    }

    private static double[] polar(Dial d, double angle, double r) {
        double a = Math.toRadians(angle);
        return new double[]{d.x() + Math.sin(a) * r, d.y() - Math.cos(a) * r};
    }

    /** Graduations along a scale: ticks at the values, long ones with labels. */
    private static void ticks(Graphics2D g, Dial d, Color c, float[] values, java.util.function.DoubleUnaryOperator angle, String[] labels,
                              float labelPx) {
        for (int i = 0; i < values.length; i++) {
            boolean major = labels != null && i < labels.length && labels[i] != null;
            double a = angle.applyAsDouble(values[i]);
            double[] p0 = polar(d, a, d.r() * 0.92), p1 = polar(d, a, d.r() * (major ? 0.74 : 0.82));
            pen(g, c, major ? 1.5f : 0.9f);
            g.draw(new Line2D.Double(p0[0], p0[1], p1[0], p1[1]));
            if (major && !labels[i].isEmpty()) {
                double[] t = polar(d, a, d.r() * 0.58);
                text(g, c, labels[i], t[0], t[1], labelPx);
            }
        }
    }

    private static void arc(Graphics2D g, Dial d, Color c, double from, double to, double r, float width) {
        pen(g, c, width);
        // Arc2D: degrees counter-clockwise from 3 o'clock; ours clockwise from 12
        g.draw(new Arc2D.Double(d.x() - r, d.y() - r, r * 2, r * 2, 90 - from, -(to - from), Arc2D.OPEN));
    }

    // ------------------------------------------------------------------ the still panel

    /**
     * The panel by day (night false: paint, bezels, dial faces and markings, unlit lamps; mask the face's
     * shape, alpha 0 outside it and in the attitude and heading windows) or the glow it gives at night
     * (night true: the markings and legends in red light on black).
     */
    static BufferedImage panel(BufferedImage mask, boolean night) {
        BufferedImage img = new BufferedImage(Math.round(W * K), Math.round(H * K), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = begin(img);
        Color mark = night ? GLOW : MARK, label = night ? GLOW : LABEL;
        if (!night) {
            g.setColor(PANEL);
            g.fill(new Rectangle2D.Double(0, 0, W, H));
            // sub-panel seams and screws
            pen(g, PANEL_DARK, 1.2f);
            for (float[] r : new float[][]{{100, 210, 205, 470}, {330, 205, 490, 470}, {490, 220, 580, 420}, {585, 280, 760, 470}, {30, 305, 124, 484}}) {
                g.draw(new Rectangle2D.Double(r[0], r[1], r[2] - r[0], r[3] - r[1]));
                g.setColor(PANEL_DARK);
                for (float[] s : new float[][]{{r[0] + 4, r[1] + 4}, {r[2] - 4, r[1] + 4}, {r[0] + 4, r[3] - 4}, {r[2] - 4, r[3] - 4}}) {
                    g.fill(new Ellipse2D.Double(s[0] - 1.6, s[1] - 1.6, 3.2, 3.2));
                }
            }
        }
        for (Dial d : DIALS) {
            if (!night) {
                double br = d.r() + 6;
                g.setColor(BEZEL);
                g.fill(new RoundRectangle2D.Double(d.x() - br, d.y() - br, br * 2, br * 2, br * 0.5, br * 0.5));
                g.setColor(new Color(20, 22, 23));
                for (int k = 0; k < 4; k++) {
                    double a = Math.toRadians(45 + 90 * k);
                    g.fill(new Ellipse2D.Double(d.x() + Math.cos(a) * br * 1.08 - 2, d.y() + Math.sin(a) * br * 1.08 - 2, 4, 4));
                }
                g.setColor(FACE);
                g.fill(new Ellipse2D.Double(d.x() - d.r(), d.y() - d.r(), d.r() * 2, d.r() * 2));
            }
            face(g, d, mark, label, night);
        }
        // the fuel gauge: a frame, a scale beside it, the legend
        float[] f = FUEL;
        if (!night) {
            g.setColor(BEZEL);
            g.fill(new Rectangle2D.Double(f[0] - 5, f[1] - 14, f[2] + 26, f[3] + 26));
            g.setColor(FACE);
            g.fill(new Rectangle2D.Double(f[0], f[1], f[2], f[3]));
        }
        for (int k = 0; k <= 9; k++) {
            double y = f[1] + f[3] - f[3] * k / 9.0;
            pen(g, mark, k % 3 == 0 ? 1.4f : 0.8f);
            g.draw(new Line2D.Double(f[0] + f[2] + 1, y, f[0] + f[2] + (k % 3 == 0 ? 8 : 5), y));
            if (k % 3 == 0) {
                text(g, mark, Integer.toString(k), f[0] + f[2] + 14, y, 7);
            }
        }
        text(g, label, "ТОПЛИВО", f[0] + f[2] / 2 + 6, f[1] - 7, 7);
        // the warning receiver: the aircraft in the middle, lamps round it for each quarter
        float rx = 800, ry = 410, rs = 26;
        if (!night) {
            g.setColor(BEZEL);
            g.fill(new Rectangle2D.Double(rx - rs - 4, ry - rs - 4, rs * 2 + 8, rs * 2 + 8));
            g.setColor(new Color(24, 18, 18));
            g.fill(new Rectangle2D.Double(rx - rs, ry - rs, rs * 2, rs * 2));
        }
        Path2D jet = new Path2D.Double();
        jet.moveTo(rx, ry - 12);
        jet.lineTo(rx + 3, ry - 2);
        jet.lineTo(rx + 13, ry + 4);
        jet.lineTo(rx + 3, ry + 4);
        jet.lineTo(rx + 5, ry + 12);
        jet.lineTo(rx - 5, ry + 12);
        jet.lineTo(rx - 3, ry + 4);
        jet.lineTo(rx - 13, ry + 4);
        jet.lineTo(rx - 3, ry - 2);
        jet.closePath();
        g.setColor(night ? GLOW : new Color(200, 60, 40));
        g.fill(jet);
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(22.5 + 45 * k);
            g.setColor(night ? new Color(90, 30, 18) : new Color(70, 20, 15));
            g.fill(new Ellipse2D.Double(rx + Math.sin(a) * 20 - 2.5, ry - Math.cos(a) * 20 - 2.5, 5, 5));
        }
        // the lamps, unlit; the lit ones are drawn over them (lamps())
        for (Lamp l : LAMPS) {
            if (!night) {
                g.setColor(new Color(30, 30, 28));
                g.fill(new Rectangle2D.Double(l.x() - 1.5, l.y() - 1.5, l.w() + 3, l.h() + 3));
                g.setColor(new Color(l.colour().getRed() / 5 + 20, l.colour().getGreen() / 5 + 20, l.colour().getBlue() / 5 + 20));
                g.fill(new Rectangle2D.Double(l.x(), l.y(), l.w(), l.h()));
                legend(g, l, new Color(150, 150, 140));
            }
        }
        // switches along the bottom right, with their legends
        String[] names = {"ПИТ", "ПНК", "РЛС", "ОЛС", "СПО", "РЭБ"};
        for (int k = 0; k < names.length; k++) {
            float x = 728 + k * 22, y = 478;
            if (!night) {
                g.setColor(BEZEL);
                g.fill(new Ellipse2D.Double(x - 4, y - 4, 8, 8));
                g.setColor(new Color(190, 192, 188));
                g.fill(new RoundRectangle2D.Double(x - 1.6, y - 11, 3.2, 10, 2, 2));
            }
            text(g, label, names[k], x, y + 10, 6);
        }
        // legends over the dials
        text(g, label, "ВЫСОТА", 165, 305, 7);
        text(g, label, "ВАР", 262, 305, 7);
        text(g, label, "РВ", 530, 212, 7);
        text(g, label, "ОБОРОТЫ", 712, 291, 7);
        text(g, label, "Т ГАЗОВ", 712, 387, 7);
        g.dispose();
        if (!night) {
            cut(img, mask);
        }
        return img;
    }

    private static void legend(Graphics2D g, Lamp l, Color c) {
        String[] words = l.text().split(" ");
        if (words.length == 1) {
            text(g, c, words[0], l.x() + l.w() / 2, l.y() + l.h() / 2, 6.5f);
        } else {
            text(g, c, words[0], l.x() + l.w() / 2, l.y() + l.h() * 0.3, 6f);
            text(g, c, words[1], l.x() + l.w() / 2, l.y() + l.h() * 0.72, 6f);
        }
    }

    /** The lamps lit, in their places, on black (drawn added over the panel lamp by lamp). */
    static BufferedImage lamps() {
        BufferedImage img = new BufferedImage(Math.round(W * K), Math.round(H * K), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = begin(img);
        for (Lamp l : LAMPS) {
            Color c = l.colour();
            g.setColor(new Color(c.getRed() * 3 / 5, c.getGreen() * 3 / 5, c.getBlue() * 3 / 5));
            g.fill(new Rectangle2D.Double(l.x(), l.y(), l.w(), l.h()));
            legend(g, l, new Color(255, 250, 235));
        }
        g.dispose();
        return img;
    }

    /** Makes everything outside the face's shape and inside the moving windows see-through. */
    private static void cut(BufferedImage img, BufferedImage mask) {
        int w = img.getWidth(), h = img.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float mx = x / K, my = y / K;
                boolean in = mask == null || inMask(mask, mx, my);
                for (Dial d : DIALS) {
                    if ((d.kind() == Kind.ADI || d.kind() == Kind.HSI) && sq(mx - d.x()) + sq(my - d.y()) < sq(d.r() * (d.kind() == Kind.HSI ? 0.86f : 0.97f))) {
                        in = false;
                    }
                }
                if (!in) {
                    img.setRGB(x, y, 0);
                }
            }
        }
    }

    private static boolean inMask(BufferedImage mask, float mx, float my) {
        int x = Math.round(mx * mask.getWidth() / W), y = Math.round(my * mask.getHeight() / H);
        return x >= 0 && y >= 0 && x < mask.getWidth() && y < mask.getHeight() && (mask.getRGB(x, y) & 0xFF) > 127;
    }

    private static float sq(float v) {
        return v * v;
    }

    /** A dial's markings: scales, numbers, its legend. */
    private static void face(Graphics2D g, Dial d, Color mark, Color label, boolean night) {
        float r = d.r();
        switch (d.kind()) {
            case AOA -> {
                arc(g, d, mark, aoaAngle(-10), aoaAngle(30), r * 0.92, 1.2f);
                arc(g, d, night ? GLOW : RED, aoaAngle(20), aoaAngle(30), r * 0.86, 3f);
                ticks(g, d, mark, new float[]{-10, -5, 0, 5, 10, 15, 20, 25, 30}, v -> aoaAngle((float) v),
                        new String[]{"-10", null, "0", null, "10", null, "20", null, "30"}, 6.5f);
                arc(g, d, mark, loadAngle(9), loadAngle(-2), r * 0.92, 1.2f);
                arc(g, d, night ? GLOW : ORANGE, loadAngle(9), loadAngle(7.5f), r * 0.86, 3f);
                ticks(g, d, mark, new float[]{-2, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, v -> loadAngle((float) v),
                        new String[]{"-2", null, "0", null, "2", null, "4", null, "6", null, "8", null}, 6.5f);
                text(g, mark, "α", d.x() - r * 0.3, d.y() + r * 0.45, 8);
                text(g, mark, "nу", d.x() + r * 0.3, d.y() + r * 0.45, 8);
            }
            case SPEED -> {
                float[] v = {0, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1200, 1400, 1600, 1800, 2000, 2500};
                String[] l = new String[v.length];
                for (int i = 0; i < v.length; i++) {
                    l[i] = v[i] % 200 == 0 && v[i] <= 1000 || v[i] == 1500 || v[i] == 2000 || v[i] == 2500 ? Integer.toString(Math.round(v[i] / 100)) : null;
                }
                ticks(g, d, mark, v, x -> speedAngle((float) x), l, 7);
                text(g, mark, "КУС КМ/Ч", d.x(), d.y() + r * 0.42, 5.5f);
            }
            case ALT -> {
                float[] v = new float[50];
                String[] l = new String[50];
                for (int i = 0; i < 50; i++) {
                    v[i] = i * 20;
                    l[i] = i % 5 == 0 ? Integer.toString(i / 5) : null;
                }
                ticks(g, d, mark, v, x -> x / 1000 * 360, l, 8);
                text(g, mark, "МЕТРЫ", d.x(), d.y() + r * 0.35, 6);
            }
            case VVI -> {
                float[] v = {-150, -60, -30, -20, -10, -5, 0, 5, 10, 20, 30, 60, 150};
                ticks(g, d, mark, v, x -> vviAngle((float) x), new String[]{"150", "60", "30", null, "10", null, "0", null, "10", null, "30", "60", "150"}, 6);
                text(g, mark, "↑", d.x() - r * 0.15, d.y() - r * 0.3, 7);
                text(g, mark, "М/С", d.x() + r * 0.25, d.y() + r * 0.1, 6);
            }
            case RADALT -> {
                float[] v = {0, 10, 20, 30, 50, 100, 150, 200, 300, 500, 1000, 1500};
                ticks(g, d, mark, v, x -> radaltAngle((float) x),
                        new String[]{"0", "10", "20", null, "50", "100", null, "200", "300", "500", "1000", "1500"}, 5.5f);
                arc(g, d, night ? GLOW : ORANGE, radaltAngle(0), radaltAngle(50), r * 0.97, 2.5f);
                text(g, mark, "Н РВ", d.x(), d.y() + r * 0.42, 6);
            }
            case MACH -> {
                float[] v = {0.4f, 0.6f, 0.8f, 1.0f, 1.2f, 1.4f, 1.6f, 1.8f, 2.0f, 2.2f};
                ticks(g, d, mark, v, x -> machAngle((float) x), new String[]{".4", null, ".8", "1", null, "1.4", null, "1.8", null, "2.2"}, 5.5f);
                text(g, mark, "М", d.x(), d.y() + r * 0.42, 7);
            }
            case RPM -> {
                float[] v = {0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110};
                ticks(g, d, mark, v, x -> rpmAngle((float) x), new String[]{"0", null, "20", null, "40", null, "60", null, "80", null, "100", null}, 6);
                arc(g, d, night ? GLOW : RED, rpmAngle(103), rpmAngle(110), r * 0.86, 3f);
                text(g, mark, "%", d.x(), d.y() + r * 0.45, 7);
                text(g, mark, "1", d.x() - r * 0.35, d.y() + r * 0.15, 7);
                text(g, mark, "2", d.x() + r * 0.35, d.y() + r * 0.15, 7);
            }
            case EGT -> {
                float[] v = {200, 300, 400, 500, 600, 700, 800, 900, 1000};
                ticks(g, d, mark, v, x -> egtAngle((float) x), new String[]{"2", null, "4", null, "6", null, "8", null, "10"}, 6);
                arc(g, d, night ? GLOW : RED, egtAngle(900), egtAngle(1000), r * 0.86, 3f);
                text(g, mark, "°C×100", d.x(), d.y() + r * 0.45, 5);
            }
            case CLOCK -> {
                float[] v = new float[60];
                String[] l = new String[60];
                for (int i = 0; i < 60; i++) {
                    v[i] = i;
                    l[i] = i % 15 == 0 ? Integer.toString(i == 0 ? 12 : i / 5) : i % 5 == 0 ? "" : null;
                }
                ticks(g, d, mark, v, x -> x * 6, l, 6);
                text(g, mark, "АЧС", d.x(), d.y() + r * 0.42, 5);
            }
            case STANDBY -> {
                if (!night) {
                    g.setColor(new Color(130, 150, 165));
                    g.fill(new Arc2D.Double(d.x() - r * 0.9, d.y() - r * 0.9, r * 1.8, r * 1.8, 0, 180, Arc2D.CHORD));
                    g.setColor(new Color(60, 44, 32));
                    g.fill(new Arc2D.Double(d.x() - r * 0.9, d.y() - r * 0.9, r * 1.8, r * 1.8, 180, 180, Arc2D.CHORD));
                }
                text(g, mark, "АГР", d.x(), d.y() + r * 0.6, 5);
            }
            case ADI -> {
                // the bank scale round the window, on the panel
                for (int a : new int[]{-90, -60, -45, -30, -15, 0, 15, 30, 45, 60, 90}) {
                    double[] p0 = polar(d, a, r * 1.02), p1 = polar(d, a, r * (a % 30 == 0 ? 1.13 : 1.08));
                    pen(g, mark, 1.4f);
                    g.draw(new Line2D.Double(p0[0], p0[1], p1[0], p1[1]));
                }
                text(g, label, "ПКП", d.x() + r * 0.9, d.y() + r * 1.0, 6);
            }
            case HSI -> {
                // the fixed lubber marks round the card, on the panel
                for (int a = 0; a < 360; a += 45) {
                    double[] p0 = polar(d, a, r * 0.88), p1 = polar(d, a, r * (a == 0 ? 1.0 : 0.95));
                    pen(g, a == 0 ? (night ? GLOW : ORANGE) : mark, a == 0 ? 2.2f : 1.2f);
                    g.draw(new Line2D.Double(p0[0], p0[1], p1[0], p1[1]));
                }
                text(g, label, "ПНП", d.x() + r * 0.9, d.y() + r * 1.0, 6);
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ the moving cards

    /** Degrees of pitch the attitude ball shows from its middle to its edge. */
    static final float BALL_SPAN = 40;

    /**
     * The attitude ball, laid flat: a strip from 90 up (top) to 90 down (bottom), sky over earth, a line
     * every 5 degrees and numbers every 10, BALL_PX pixels to a degree. It slides up and down behind the
     * window with the pitch. As wide as the window shows, so it isn't stretched.
     */
    static final int BALL_PX = 6, BALL_W = Math.round(2 * BALL_SPAN * BALL_PX);

    static BufferedImage ball() {
        int h = 180 * BALL_PX + BALL_W;
        BufferedImage img = new BufferedImage(BALL_W, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double mid = h / 2.0;
        g.setColor(new Color(150, 172, 190));
        g.fill(new Rectangle2D.Double(0, 0, BALL_W, mid));
        g.setColor(new Color(58, 40, 30));
        g.fill(new Rectangle2D.Double(0, mid, BALL_W, h - mid));
        g.setColor(Color.WHITE);
        g.fill(new Rectangle2D.Double(0, mid - 2, BALL_W, 4));
        for (int p = -90; p <= 90; p += 5) {
            if (p == 0) {
                continue;
            }
            double y = mid - p * BALL_PX;
            double half = p % 10 == 0 ? BALL_W * 0.22 : BALL_W * 0.1;
            g.setColor(p > 0 ? new Color(30, 30, 34) : Color.WHITE);
            g.setStroke(new BasicStroke(2.4f));
            g.draw(new Line2D.Double(BALL_W / 2.0 - half, y, BALL_W / 2.0 + half, y));
            if (p % 10 == 0) {
                g.setFont(font(20));
                String s = Integer.toString(Math.abs(p));
                FontMetrics m = g.getFontMetrics();
                g.drawString(s, (float) (BALL_W / 2.0 + half + 6), (float) (y + 7));
                g.drawString(s, (float) (BALL_W / 2.0 - half - 6 - m.stringWidth(s)), (float) (y + 7));
            }
        }
        g.dispose();
        return img;
    }

    /** The heading card: degrees round the edge, numbers every 30 (tens of degrees), turned with the heading. */
    static BufferedImage rose() {
        int s = 256;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double c = s / 2.0, r = s / 2.0 - 1;
        g.setColor(new Color(22, 23, 24));
        g.fill(new Ellipse2D.Double(c - r, c - r, r * 2, r * 2));
        g.setColor(MARK);
        for (int a = 0; a < 360; a += 5) {
            double t = Math.toRadians(a), r1 = r * (a % 10 == 0 ? 0.84 : 0.9);
            g.setStroke(new BasicStroke(a % 10 == 0 ? 2.4f : 1.4f));
            g.draw(new Line2D.Double(c + Math.sin(t) * r * 0.98, c - Math.cos(t) * r * 0.98, c + Math.sin(t) * r1, c - Math.cos(t) * r1));
            if (a % 30 == 0) {
                String n = a == 0 ? "С" : a == 90 ? "В" : a == 180 ? "Ю" : a == 270 ? "З" : Integer.toString(a / 10);
                g.setFont(font(a % 90 == 0 ? 26 : 22));
                FontMetrics m = g.getFontMetrics();
                java.awt.geom.AffineTransform at = g.getTransform();
                g.rotate(t, c, c);
                g.drawString(n, (float) (c - m.stringWidth(n) / 2.0), (float) (c - r * 0.62));
                g.setTransform(at);
            }
        }
        g.dispose();
        return img;
    }

    // ------------------------------------------------------------------ what moves, this frame

    /** Swatches in the needle texture: white, orange, yellow, dark. */
    static final int WHITE = 0, NEEDLE_ORANGE = 1, NEEDLE_YELLOW = 2, DARK = 3;

    /** A needle: pivot (mm), angle (degrees clockwise from 12), length past the pivot and behind it, width, swatch. */
    record Needle(float x, float y, float angle, float length, float tail, float width, int swatch) {
    }

    /** Every needle and pointer on the panel for this flight. */
    static List<Needle> needles(CockpitPages.Data d) {
        List<Needle> out = new ArrayList<>();
        float kmh = d.speed() * 3.6f, alt = Math.max(0, d.altitude()), rpm = d.rpm() * 100;
        float egt = d.rpm() < 0.05f ? 20 : 400 + 420 * d.power() + (d.afterburner() ? 120 : 0);
        for (Dial dial : DIALS) {
            float r = dial.r(), x = dial.x(), y = dial.y();
            switch (dial.kind()) {
                case AOA -> {
                    out.add(new Needle(x, y, aoaAngle(d.aoa()), r * 0.85f, 0, 2.2f, WHITE));
                    out.add(new Needle(x, y, loadAngle(d.load()), r * 0.85f, 0, 2.2f, NEEDLE_YELLOW));
                }
                case SPEED -> {
                    out.add(new Needle(x, y, speedAngle(kmh * (1 + alt * 0.00004f)), r * 0.88f, r * 0.15f, 1.4f, NEEDLE_YELLOW));
                    out.add(new Needle(x, y, speedAngle(kmh), r * 0.8f, r * 0.15f, 2.6f, WHITE));
                }
                case ALT -> {
                    out.add(new Needle(x, y, alt / 10000 * 360, r * 0.55f, r * 0.1f, 3.6f, WHITE));
                    out.add(new Needle(x, y, alt % 1000 / 1000 * 360, r * 0.86f, r * 0.15f, 2f, WHITE));
                }
                case VVI -> out.add(new Needle(x, y, vviAngle(d.vs()), r * 0.85f, r * 0.12f, 2.4f, WHITE));
                case RADALT -> out.add(new Needle(x, y, radaltAngle(Math.min(d.radar(), 1500)), r * 0.85f, r * 0.12f, 2.2f, WHITE));
                case MACH -> out.add(new Needle(x, y, machAngle(d.speed() / 340f), r * 0.85f, r * 0.12f, 2f, WHITE));
                case RPM -> {
                    out.add(new Needle(x, y, rpmAngle(rpm), r * 0.85f, r * 0.12f, 2.2f, WHITE));
                    out.add(new Needle(x, y, rpmAngle(rpm + (rpm > 1 ? 0.6f : 0)), r * 0.62f, r * 0.12f, 2.2f, NEEDLE_YELLOW));
                }
                case EGT -> {
                    out.add(new Needle(x, y, egtAngle(egt), r * 0.85f, r * 0.12f, 2f, WHITE));
                    out.add(new Needle(x, y, egtAngle(egt - 8), r * 0.62f, r * 0.12f, 2f, NEEDLE_YELLOW));
                }
                case CLOCK -> {
                    float minutes = d.hours() * 60;
                    out.add(new Needle(x, y, minutes % 720 / 2, r * 0.5f, r * 0.1f, 2.4f, WHITE));
                    out.add(new Needle(x, y, minutes % 60 * 6, r * 0.8f, r * 0.1f, 1.6f, WHITE));
                    out.add(new Needle(x, y, d.millis() % 60000 / 1000f * 6, r * 0.85f, r * 0.2f, 0.8f, NEEDLE_ORANGE));
                }
                case STANDBY -> {
                    // the standby horizon: a bar moved by the pitch and turned by the bank
                    float shift = Math.max(-r * 0.7f, Math.min(r * 0.7f, d.pitch() * r / 30));
                    out.add(new Needle(x, y + shift, 90 - d.bank(), r * 0.8f, r * 0.8f, 1.6f, WHITE));
                    out.add(new Needle(x, y, 90, r * 0.35f, r * 0.35f, 2f, NEEDLE_ORANGE));
                }
                case ADI -> {
                    // the little aircraft banks: wings, a body, a fin
                    out.add(new Needle(x, y, 90 + d.bank(), r * 0.62f, r * 0.62f, 2.6f, NEEDLE_ORANGE));
                    out.add(new Needle(x, y, d.bank(), r * 0.18f, 0, 2.6f, NEEDLE_ORANGE));
                    out.add(new Needle(x, y, d.bank(), r * 1.06f, -r * 0.98f, 3f, NEEDLE_ORANGE));
                }
                case HSI -> {
                    // the fixed aircraft in the middle, the course arrow on the track over the ground
                    out.add(new Needle(x, y, 0, r * 0.25f, r * 0.2f, 2.4f, NEEDLE_ORANGE));
                    out.add(new Needle(x, y, 90, r * 0.18f, r * 0.18f, 2.4f, NEEDLE_ORANGE));
                    float track = (float) Math.toDegrees(Math.atan2(d.vel().x, -d.vel().z));
                    if (Math.hypot(d.vel().x, d.vel().z) > 3) {
                        out.add(new Needle(x, y, track - d.heading(), r * 0.7f, r * 0.7f, 1.2f, NEEDLE_YELLOW));
                    }
                }
                default -> {
                }
            }
        }
        return out;
    }

    /** Which lamps are lit. */
    static boolean[] lit(CockpitPages.Data d) {
        return new boolean[]{
                d.condition() < 30,
                !d.grounded() && d.impact() < 6,
                d.fuel() * 9400 < 800,
                d.stalled() || d.load() > 8.5f,
                !d.grounded() && d.radar() < 60 && d.speed() < 100 && d.vs() < -1,
                d.engine() && d.rpm() < 0.58f,
                d.engine() && d.rpm() < 0.3f};
    }

    /** The fuel column's height, 0 to 1 of the gauge (it reads up to 9 tonnes). */
    static float fuel(CockpitPages.Data d) {
        return Math.max(0, Math.min(1, d.fuelKg() / 9000));
    }
}
