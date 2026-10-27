package com.zomboidcraft.client.cars;

import com.zomboidcraft.world.cars.CarProp;
import com.zomboidcraft.world.cars.Flight;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The pages on an aircraft's displays, painted live from the flight each time they're due (see
 * {@link CockpitDisplays}), laid out after the real ones: the F-22's colour LCDs (attitude, engines,
 * the tactical picture, stores, the two upfront displays) and its head-up display; the Su-27's
 * ILS-31 style head-up display in metres and km/h; the AH-64D's flight, map, weapons and engine
 * pages and its amber upfront display. Black is no light: the screen under it shows through. Made-up
 * readouts only, no real call signs or weapon names.
 */
final class CockpitPages {
    static final Color GREEN = new Color(70, 255, 100), HUD = new Color(90, 255, 120), CYAN = new Color(70, 215, 255),
            YELLOW = new Color(255, 225, 60), AMBER = new Color(255, 165, 40), RED = new Color(255, 60, 45),
            WHITE = new Color(230, 238, 232), MAGENTA = new Color(235, 90, 235), DIM = new Color(30, 110, 60),
            SKY = new Color(30, 85, 170), EARTH = new Color(105, 62, 26), GREY = new Color(120, 130, 125);

    /** What the avionics know this frame. Heading true from north, degrees; altitude above the sea (m). */
    record Data(CarProp car, Flight.Kind kind, Quaternionf q, Vector3f vel, float speed, float altitude, float radar, float vs,
                float heading, float pitch, float bank, float load, float peakLoad, float aoa, float throttle, float power, float rpm,
                boolean afterburner, boolean engine, boolean grounded, boolean stalled, boolean brake, float fuel, float fuelKg,
                boolean battery, int condition, float hours, List<float[]> tracks, long millis) {
        /** Seconds to hitting the ground at this sink rate, or a lot. */
        float impact() {
            return vs < -1 ? radar / -vs : 999;
        }

        boolean flash() {
            return (millis / 250) % 2 == 0;
        }
    }

    /** Where a direction in the aircraft's own frame (nose +z, left +x) meets the head-up display's glass, in its pixels; null off it. */
    interface Glass {
        float[] at(Vector3f body);
    }

    interface Painter {
        void paint(Graphics2D g, int w, int h, Data d, Glass glass);
    }

    static final Map<String, Painter> PAGES = new HashMap<>();

    static {
        PAGES.put("adi", (g, w, h, d, glass) -> adi(g, w, h, d));
        PAGES.put("engines", (g, w, h, d, glass) -> engines(g, w, h, d));
        PAGES.put("tsd", (g, w, h, d, glass) -> tsd(g, w, h, d, false));
        PAGES.put("tsd_heli", (g, w, h, d, glass) -> tsd(g, w, h, d, true));
        PAGES.put("stores", (g, w, h, d, glass) -> stores(g, w, h, d));
        PAGES.put("cni", (g, w, h, d, glass) -> cni(g, w, h, d));
        PAGES.put("icaw", (g, w, h, d, glass) -> icaw(g, w, h, d));
        PAGES.put("flight", (g, w, h, d, glass) -> flight(g, w, h, d));
        PAGES.put("tapes", (g, w, h, d, glass) -> tapes(g, w, h, d));
        PAGES.put("eufd", (g, w, h, d, glass) -> eufd(g, w, h, d));
        PAGES.put("weapons", (g, w, h, d, glass) -> weapons(g, w, h, d));
        PAGES.put("hud_jet", CockpitPages::hudRaptor);
        PAGES.put("hud_flanker", CockpitPages::hudFlanker);
    }

    // ------------------------------------------------------------------ drawing

    private static final Map<Integer, Font> FONTS = new HashMap<>();

    private static Font font(float px) {
        return FONTS.computeIfAbsent(Math.max(5, Math.round(px)), s -> new Font(Font.MONOSPACED, Font.BOLD, s));
    }

    static void pen(Graphics2D g, Color c, float width) {
        g.setColor(c);
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    }

    static void dashed(Graphics2D g, Color c, float width, float dash) {
        g.setColor(c);
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 4, new float[]{dash, dash * 0.7f}, 0));
    }

    static void line(Graphics2D g, double x0, double y0, double x1, double y1) {
        g.draw(new Line2D.Double(x0, y0, x1, y1));
    }

    static void line(Graphics2D g, float[] a, float[] b) {
        if (a != null && b != null) {
            g.draw(new Line2D.Double(a[0], a[1], b[0], b[1]));
        }
    }

    static void circle(Graphics2D g, double x, double y, double r) {
        g.draw(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
    }

    static void box(Graphics2D g, double x0, double y0, double x1, double y1) {
        g.draw(new Rectangle2D.Double(x0, y0, x1 - x0, y1 - y0));
    }

    /** Text with its top at y; align -1 left, 0 centred, 1 right. */
    static void text(Graphics2D g, String s, double x, double y, float px, int align) {
        g.setFont(font(px));
        FontMetrics m = g.getFontMetrics();
        double width = m.stringWidth(s);
        g.drawString(s, (float) (x - (align + 1) * width / 2), (float) (y + px * 0.78));
    }

    static void text(Graphics2D g, Color c, String s, double x, double y, float px, int align) {
        g.setColor(c);
        text(g, s, x, y, px, align);
    }

    /** The soft-key legends round a display's edge: short bars, the live ones bright. */
    static void keys(Graphics2D g, int w, int h, Color c, int n, String sides, int lit) {
        Color dim = new Color(c.getRed() * 45 / 100, c.getGreen() * 45 / 100, c.getBlue() * 45 / 100);
        float k = Math.max(1, w / 110f);
        for (int i = 0; i < n; i++) {
            double f = (i + 0.5) / n;
            for (char side : sides.toCharArray()) {
                pen(g, (i * 7 + side) % 5 == lit ? c : dim, k);
                double y = h * (0.15 + 0.7 * f), x = w * (0.12 + 0.76 * f);
                switch (side) {
                    case 'l' -> line(g, 2, y, 2 + w * 0.05, y);
                    case 'r' -> line(g, w - 2, y, w - 2 - w * 0.05, y);
                    case 'b' -> line(g, x - w * 0.035, h - 3, x + w * 0.035, h - 3);
                    default -> line(g, x - w * 0.035, 3, x + w * 0.035, 3);
                }
            }
        }
    }

    private static String feet(float metres) {
        return String.format("%,d", Math.round(metres * 3.28084f / 10) * 10);
    }

    private static int knots(float ms) {
        return Math.round(ms * 1.94384f);
    }

    private static String clock(float hours) {
        int m = (int) (hours * 60) % (24 * 60);
        return String.format("%02d:%02d", m / 60, m % 60);
    }

    // ------------------------------------------------------------------ the F-22's displays

    /** Attitude: the ball (sky over earth) banked and pitched, the roll scale over it, speed and height either side. */
    static void adi(Graphics2D g, int w, int h, Data d) {
        double cx = w / 2.0, cy = h * 0.5, r = Math.min(w, h) * 0.33, ppd = r / 32;
        float px = h * 0.075f;
        Shape clip = g.getClip();
        g.setClip(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        AffineTransform t = g.getTransform();
        g.rotate(Math.toRadians(-d.bank()), cx, cy);
        double hy = cy + d.pitch() * ppd;
        g.setColor(SKY);
        g.fill(new Rectangle2D.Double(cx - r * 2, hy - r * 6, r * 4, r * 6));
        g.setColor(EARTH);
        g.fill(new Rectangle2D.Double(cx - r * 2, hy, r * 4, r * 6));
        pen(g, WHITE, Math.max(1.2f, w / 120f));
        line(g, cx - r * 2, hy, cx + r * 2, hy);
        for (int p = -90; p <= 90; p += 5) {
            if (p == 0) {
                continue;
            }
            double y = hy - p * ppd;
            if (Math.abs(y - cy) > r) {
                continue;
            }
            double half = p % 10 == 0 ? r * 0.32 : r * 0.14;
            line(g, cx - half, y, cx + half, y);
            if (p % 10 == 0) {
                text(g, WHITE, Integer.toString(Math.abs(p)), cx + half + 2, y - px * 0.4, px * 0.8f, -1);
            }
        }
        g.setTransform(t);
        g.setClip(clip);
        pen(g, GREY, 1.2f);
        circle(g, cx, cy, r);
        // the roll scale and its pointer
        pen(g, WHITE, 1.3f);
        for (int a : new int[]{-60, -45, -30, -20, -10, 0, 10, 20, 30, 45, 60}) {
            double rad = Math.toRadians(a - 90);
            double r1 = r * (a % 30 == 0 ? 1.13 : 1.07);
            line(g, cx + Math.cos(rad) * r, cy + Math.sin(rad) * r, cx + Math.cos(rad) * r1, cy + Math.sin(rad) * r1);
        }
        double pr = Math.toRadians(-d.bank() - 90);
        Path2D ptr = new Path2D.Double();
        double tx = cx + Math.cos(pr) * r * 0.97, ty = cy + Math.sin(pr) * r * 0.97;
        double nx = Math.cos(pr + Math.PI / 2) * r * 0.06, ny = Math.sin(pr + Math.PI / 2) * r * 0.06;
        ptr.moveTo(tx, ty);
        ptr.lineTo(tx - Math.cos(pr) * r * 0.1 + nx, ty - Math.sin(pr) * r * 0.1 + ny);
        ptr.lineTo(tx - Math.cos(pr) * r * 0.1 - nx, ty - Math.sin(pr) * r * 0.1 - ny);
        ptr.closePath();
        g.setColor(YELLOW);
        g.fill(ptr);
        // the aircraft, fixed
        pen(g, YELLOW, Math.max(2f, w / 60f));
        line(g, cx - r * 0.55, cy, cx - r * 0.18, cy);
        line(g, cx - r * 0.18, cy, cx - r * 0.18, cy + r * 0.08);
        line(g, cx + r * 0.55, cy, cx + r * 0.18, cy);
        line(g, cx + r * 0.18, cy, cx + r * 0.18, cy + r * 0.08);
        g.fill(new Rectangle2D.Double(cx - 1.5, cy - 1.5, 3, 3));
        // speed, height, heading
        pen(g, WHITE, 1.2f);
        box(g, 3, cy - px * 0.8, w * 0.2, cy + px * 0.8);
        text(g, WHITE, Integer.toString(knots(d.speed())), w * 0.2 - 2, cy - px * 0.5, px, 1);
        text(g, CYAN, "KCAS", 3, cy + px, px * 0.75f, -1);
        box(g, w * 0.8, cy - px * 0.8, w - 3, cy + px * 0.8);
        text(g, WHITE, feet(d.altitude()), w - 5, cy - px * 0.5, px * 0.85f, 1);
        text(g, CYAN, "BARO", w - 3, cy + px, px * 0.75f, 1);
        text(g, WHITE, String.format("%03d", Math.round(d.heading()) % 360), cx, h - px * 1.4, px, 0);
        text(g, CYAN, String.format("G %.1f", d.load()), 3, 4, px * 0.8f, -1);
        text(g, CYAN, String.format("M %.2f", d.speed() / 340f), w - 3, 4, px * 0.8f, 1);
        keys(g, w, h, CYAN, 4, "b", 1);
    }

    /** Engines: both cores' speed on dials, turbine temperature and nozzles as bars, fuel and its flow. */
    static void engines(Graphics2D g, int w, int h, Data d) {
        float px = h * 0.07f;
        text(g, CYAN, "ENGINE", w / 2.0, 3, px, 0);
        float rpm = d.rpm() * 100, ftit = d.rpm() < 0.05f ? 20 : 380 + 460 * d.power() + (d.afterburner() ? 120 : 0);
        float noz = d.afterburner() ? 70 + 30 * (d.power() - 0.85f) / 0.15f : d.rpm() < 0.58f ? 0 : 12 + 20 * (1 - d.power());
        for (int side = 0; side < 2; side++) {
            double cx = w * (side == 0 ? 0.28 : 0.72), cy = h * 0.34, r = w * 0.15;
            float wobble = side == 0 ? 0 : 0.4f * (float) Math.sin(d.millis() / 900.0);
            float v = Math.max(0, rpm + wobble);
            pen(g, GREY, 1.2f);
            g.draw(new Arc2D.Double(cx - r, cy - r, r * 2, r * 2, -45, 270, Arc2D.OPEN));
            pen(g, v > 102 ? RED : GREEN, Math.max(2f, w / 70f));
            g.draw(new Arc2D.Double(cx - r, cy - r, r * 2, r * 2, 225, -270 * Math.min(v, 110) / 110, Arc2D.OPEN));
            double a = Math.toRadians(225 - 270 * Math.min(v, 110) / 110);
            pen(g, WHITE, 1.5f);
            line(g, cx, cy, cx + Math.cos(a) * r * 0.9, cy - Math.sin(a) * r * 0.9);
            text(g, WHITE, Integer.toString(Math.round(v)), cx, cy + r * 0.3, px, 0);
            text(g, CYAN, side == 0 ? "L RPM" : "R RPM", cx, cy - r - px * 1.3, px * 0.75f, 0);
            // temperature and nozzle bars
            double bx = cx - r * 0.7, by = h * 0.58, bw = r * 0.5, bh = h * 0.24;
            bar(g, bx, by, bw, bh, ftit / 1100, ftit > 1000 ? RED : GREEN);
            bar(g, bx + r * 0.9, by, bw, bh, noz / 100, CYAN);
            text(g, WHITE, Integer.toString(Math.round(ftit)), bx + bw / 2, by + bh + 2, px * 0.7f, 0);
            text(g, WHITE, Integer.toString(Math.round(noz)), bx + r * 0.9 + bw / 2, by + bh + 2, px * 0.7f, 0);
        }
        text(g, CYAN, "FTIT", w * 0.5, h * 0.62, px * 0.7f, 0);
        text(g, CYAN, "NOZ", w * 0.5, h * 0.7, px * 0.7f, 0);
        int flow = d.rpm() < 0.05f ? 0 : Math.round((1100 + 16000 * (float) Math.pow(d.power(), 1.3) + (d.afterburner() ? 26000 : 0)) / 10) * 10;
        text(g, d.fuel() < 0.15f ? AMBER : WHITE, String.format("FUEL %,d", Math.round(d.fuelKg() * 2.2046f / 10) * 10), 4, h - px * 1.2, px * 0.85f, -1);
        text(g, WHITE, String.format("FF %,d", flow), w - 4, h - px * 1.2, px * 0.85f, 1);
        if (d.afterburner()) {
            text(g, YELLOW, "AB", w / 2.0, h * 0.5, px, 0);
        }
    }

    private static void bar(Graphics2D g, double x, double y, double w, double h, float share, Color c) {
        pen(g, GREY, 1f);
        box(g, x, y, x + w, y + h);
        float s = Math.max(0, Math.min(1, share));
        g.setColor(c);
        g.fill(new Rectangle2D.Double(x + 1, y + h * (1 - s), w - 2, h * s));
    }

    /**
     * The tactical picture, heading up: ownship low in the middle (in the middle on the helicopter), range
     * rings, a compass rose turning with the heading, and every other aircraft about as a track.
     */
    static void tsd(Graphics2D g, int w, int h, Data d, boolean heli) {
        float px = h * 0.065f;
        double cx = w / 2.0, cy = heli ? h * 0.55 : h * 0.78, R = heli ? h * 0.42 : h * 0.72;
        double range = heli ? 1500 : 4000;
        Color ring = new Color(28, 86, 104);
        pen(g, ring, 1f);
        for (double k : new double[]{1 / 3.0, 2 / 3.0, 1}) {
            if (heli) {
                circle(g, cx, cy, R * k);
            } else {
                g.draw(new Arc2D.Double(cx - R * k, cy - R * k, R * k * 2, R * k * 2, 20, 140, Arc2D.OPEN));
            }
        }
        // the compass rose round the outer ring, turned so the heading is straight up
        for (int a = 0; a < 360; a += 10) {
            double rel = Math.toRadians(a - d.heading()) - Math.PI / 2;
            if (!heli && Math.sin(rel) > -0.34) {
                continue;
            }
            double r0 = R, r1 = R * (a % 30 == 0 ? 1.06 : 1.03);
            pen(g, ring, 1f);
            line(g, cx + Math.cos(rel) * r0, cy + Math.sin(rel) * r0, cx + Math.cos(rel) * r1, cy + Math.sin(rel) * r1);
            if (a % 30 == 0) {
                String s = a == 0 ? "N" : a == 90 ? "E" : a == 180 ? "S" : a == 270 ? "W" : Integer.toString(a / 10);
                text(g, a % 90 == 0 ? WHITE : CYAN, s, cx + Math.cos(rel) * R * 1.13, cy + Math.sin(rel) * R * 1.13 - px * 0.5, px * 0.8f, 0);
            }
        }
        // tracks: other aircraft, friendly green, unknowns yellow
        g.setClip(new Rectangle2D.Double(0, 0, w, h));
        for (float[] t : d.tracks()) {
            double rel = Math.toRadians(t[0] - d.heading()) - Math.PI / 2;
            double r = t[1] / range * R;
            if (r > R * 1.05) {
                continue;
            }
            double x = cx + Math.cos(rel) * r, y = cy + Math.sin(rel) * r, s = w * 0.028;
            pen(g, t[3] > 0 ? GREEN : YELLOW, 1.4f);
            if (t[3] > 0) {
                g.draw(new Arc2D.Double(x - s, y - s, s * 2, s * 2, 0, 180, Arc2D.OPEN));
            } else {
                box(g, x - s, y - s, x + s, y + s);
            }
            double head = Math.toRadians(t[4] - d.heading()) - Math.PI / 2;
            line(g, x, y, x + Math.cos(head) * s * 2.2, y + Math.sin(head) * s * 2.2);
            text(g, t[3] > 0 ? GREEN : YELLOW, Integer.toString(Math.round(t[2] / 100)), x + s * 1.3, y - px * 0.3, px * 0.7f, -1);
        }
        // ownship
        double s = w * 0.035;
        Path2D own = new Path2D.Double();
        own.moveTo(cx, cy - s * 1.4);
        own.lineTo(cx + s, cy + s * 0.6);
        own.lineTo(cx, cy + s * 0.2);
        own.lineTo(cx - s, cy + s * 0.6);
        own.closePath();
        g.setColor(WHITE);
        g.fill(own);
        text(g, CYAN, Integer.toString(Math.round((float) range / 1000)), 5, 4, px, -1);
        text(g, WHITE, heli ? "TSD" : "SA", w - 5, 4, px, 1);
        String nav = String.format("HDG %03d  GS %d", Math.round(d.heading()) % 360, knots(horizontal(d)));
        if (heli) {
            text(g, CYAN, nav, 5, h - px * 1.2, px * 0.7f, -1);
        } else {
            text(g, CYAN, nav, cx, h - px * 1.3, px * 0.8f, 0);
        }
        if (!heli) {
            keys(g, w, h, CYAN, 5, "lr", 2);
        }
    }

    private static float horizontal(Data d) {
        return (float) Math.hypot(d.vel().x, d.vel().z);
    }

    /** Stores: the airframe from above with its bays, what's loaded, master arm. */
    static void stores(Graphics2D g, int w, int h, Data d) {
        float px = h * 0.08f;
        double cx = w / 2.0, cy = h * 0.45, s = Math.min(w, h) * 0.38;
        Path2D plan = new Path2D.Double();
        double[][] pts = {{0, -1}, {0.12, -0.55}, {0.14, -0.1}, {0.9, 0.35}, {0.9, 0.5}, {0.2, 0.45}, {0.2, 0.62}, {0.55, 0.85},
                {0.55, 1}, {0.1, 0.95}, {0, 0.98}};
        plan.moveTo(cx, cy - s);
        for (double[] p : pts) {
            plan.lineTo(cx + p[0] * s, cy + p[1] * s);
        }
        for (int i = pts.length - 1; i >= 0; i--) {
            plan.lineTo(cx - pts[i][0] * s, cy + pts[i][1] * s);
        }
        plan.closePath();
        pen(g, GREEN, 1.4f);
        g.draw(plan);
        // the main bay and the two side bays
        pen(g, CYAN, 1.2f);
        box(g, cx - s * 0.1, cy - s * 0.3, cx + s * 0.1, cy + s * 0.3);
        box(g, cx - s * 0.2, cy - s * 0.42, cx - s * 0.13, cy - s * 0.15);
        box(g, cx + s * 0.13, cy - s * 0.42, cx + s * 0.2, cy - s * 0.15);
        text(g, WHITE, "6", cx, cy - px * 0.5, px, 0);
        text(g, WHITE, "1", cx - s * 0.33, cy - s * 0.35, px * 0.8f, 0);
        text(g, WHITE, "1", cx + s * 0.33, cy - s * 0.35, px * 0.8f, 0);
        text(g, GREEN, "MRM 6", 4, h - px * 2.6, px * 0.8f, -1);
        text(g, GREEN, "SRM 2", 4, h - px * 1.5, px * 0.8f, -1);
        text(g, GREEN, "GUN 480", w - 4, h - px * 2.6, px * 0.8f, 1);
        text(g, d.flash() ? YELLOW : AMBER, "SAFE", w - 4, h - px * 1.5, px * 0.8f, 1);
        keys(g, w, h, GREEN, 4, "t", 3);
    }

    /** The left upfront display: radios, navigation, transponder, the clock, in green. */
    static void cni(Graphics2D g, int w, int h, Data d) {
        String[] rows = {"COM1 251.300", "COM2 133.050", "NAV  STPT 01", "TCN  72X", "XPDR 4021 M3", "TIME " + clock(d.hours())};
        rows(g, w, h, GREEN, rows, null);
    }

    /** The right upfront display: warnings red, cautions amber, advisories green, newest first; empty when all's well. */
    static void icaw(Graphics2D g, int w, int h, Data d) {
        List<String> lines = new ArrayList<>();
        List<Color> cols = new ArrayList<>();
        cautions(d, lines, cols, false);
        if (lines.isEmpty()) {
            lines.add("ICAW");
            cols.add(DIM);
        }
        rows(g, w, h, GREEN, lines.toArray(String[]::new), cols);
    }

    private static void rows(Graphics2D g, int w, int h, Color c, String[] rows, List<Color> colours) {
        float px = Math.min(h / (rows.length + 1f), w / 8.5f);
        for (int i = 0; i < rows.length; i++) {
            text(g, colours == null ? c : colours.get(i), rows[i], 4, 3 + i * px * 1.12, px, -1);
        }
    }

    /** What's wrong, worst first. */
    private static void cautions(Data d, List<String> lines, List<Color> cols, boolean heli) {
        if (!d.grounded() && d.impact() < 6 && d.flash()) {
            add(lines, cols, "PULL UP", RED);
        }
        if (d.stalled()) {
            add(lines, cols, "STALL", RED);
        }
        if (d.load() > 9) {
            add(lines, cols, "OVER G", RED);
        }
        if (d.fuel() <= 0.001f && !d.grounded()) {
            add(lines, cols, heli ? "ENG OUT" : "FLAMEOUT", RED);
        }
        if (d.fuel() < 0.12f) {
            add(lines, cols, "FUEL LOW", AMBER);
        } else if (d.fuel() < 0.25f) {
            add(lines, cols, "BINGO", AMBER);
        }
        if (d.condition() < 75) {
            add(lines, cols, "AIRFRAME", AMBER);
        }
        if (!d.engine() && d.rpm() < 0.05f) {
            add(lines, cols, heli ? "ENG 1+2 OFF" : "ENG OFF", AMBER);
        } else if (d.engine() && d.rpm() < (heli ? 0.95f : 0.58f)) {
            add(lines, cols, heli ? "ROTOR LOW" : "ENG START", AMBER);
        }
        if (d.grounded() && d.brake()) {
            add(lines, cols, "BRAKES", GREEN);
        }
        if (d.afterburner()) {
            add(lines, cols, "AB", GREEN);
        }
    }

    private static void add(List<String> lines, List<Color> cols, String s, Color c) {
        lines.add(s);
        cols.add(c);
    }

    // ------------------------------------------------------------------ the AH-64D's displays

    /** The flight page: heading tape, horizon and pitch, airspeed, height and radar height, torque, the climb. */
    static void flight(Graphics2D g, int w, int h, Data d) {
        float px = h * 0.075f;
        double cx = w / 2.0, cy = h * 0.52;
        Color c = GREEN;
        // heading tape
        tape(g, c, d.heading(), cx, h * 0.07, w * 0.7, px * 0.8f, 3);
        // horizon and ladder
        AffineTransform t = g.getTransform();
        Shape clip = g.getClip();
        g.setClip(new Rectangle2D.Double(w * 0.2, h * 0.2, w * 0.6, h * 0.62));
        g.rotate(Math.toRadians(-d.bank()), cx, cy);
        double ppd = h / 70.0, hy = cy + d.pitch() * ppd;
        pen(g, c, 1.5f);
        line(g, cx - w * 0.4, hy, cx - w * 0.06, hy);
        line(g, cx + w * 0.06, hy, cx + w * 0.4, hy);
        for (int p = -30; p <= 30; p += 10) {
            if (p != 0) {
                double y = hy - p * ppd;
                if (p < 0) {
                    dashed(g, c, 1.2f, 4);
                } else {
                    pen(g, c, 1.2f);
                }
                line(g, cx - w * 0.12, y, cx + w * 0.12, y);
            }
        }
        g.setTransform(t);
        g.setClip(clip);
        pen(g, WHITE, 1.8f);
        line(g, cx - w * 0.08, cy, cx - w * 0.025, cy);
        line(g, cx + w * 0.025, cy, cx + w * 0.08, cy);
        circle(g, cx, cy, 2);
        text(g, c, Integer.toString(knots(horizontal(d))), w * 0.05, cy - px * 0.5, px, -1);
        text(g, c, feet(d.altitude()), w * 0.93, cy - px * 0.5, px, 1);
        text(g, c, "R" + Math.round(d.radar() * 3.28084f), w * 0.93, cy + px * 1.2, px * 0.85f, 1);
        text(g, c, Math.round(d.power() * 100) + "%", w * 0.05, h * 0.2, px, -1);
        text(g, c, String.format("%+d", Math.round(d.vs() * 196.85f / 100) * 100), w * 0.95, h * 0.2, px * 0.8f, 1);
        // radar height bar
        double bx = w * 0.955, by = h * 0.3, bh = h * 0.4;
        pen(g, c, 1f);
        box(g, bx, by, bx + w * 0.03, by + bh);
        float rh = Math.min(1, d.radar() / 60f);
        g.fill(new Rectangle2D.Double(bx, by + bh * (1 - rh), w * 0.03, bh * rh));
        text(g, CYAN, "FLT", cx, h - px * 1.3, px * 0.8f, 0);
    }

    /** A heading tape across the top: ticks every 5 degrees, numbers every 30, the heading boxed. */
    static void tape(Graphics2D g, Color c, float heading, double cx, double y, double width, float px, int every) {
        double ppd = width / 60;
        pen(g, c, 1.2f);
        for (int a = (int) Math.floor((heading - 30) / 5) * 5; a <= heading + 30; a += 5) {
            double x = cx + (a - heading) * ppd;
            if (Math.abs(x - cx) > width / 2) {
                continue;
            }
            int m = Math.floorMod(a, 360);
            line(g, x, y + px * 1.3, x, y + px * (m % 10 == 0 ? 1.9 : 1.6));
            if (m % (every * 10) == 0 && Math.abs(x - cx) > px * 1.5) {
                text(g, c, m % 90 == 0 ? "NESW".substring(m / 90, m / 90 + 1) : Integer.toString(m / 10), x, y, px * 0.8f, 0);
            }
        }
        g.setColor(Color.BLACK);
        g.fill(new Rectangle2D.Double(cx - px * 1.3, y - 1, px * 2.6, px * 1.2));
        pen(g, c, 1.2f);
        box(g, cx - px * 1.3, y - 1, cx + px * 1.3, y + px * 1.2);
        text(g, c, String.format("%03d", Math.round(heading) % 360), cx, y, px * 0.85f, 0);
    }

    /** The engine strip: gas generator, turbine temperature and torque for both engines, rotor speed. */
    static void tapes(Graphics2D g, int w, int h, Data d) {
        float px = h * 0.16f;
        String[] names = {"NG1", "NG2", "TGT1", "TGT2", "TQ1", "TQ2", "NR"};
        float ng = d.rpm() < 0.05f ? 0 : 68 + 30 * d.power() * d.rpm();
        float tgt = d.rpm() < 0.05f ? 20 : 480 + 330 * d.power();
        float[] values = {ng, ng + 0.6f, tgt, tgt - 6, d.power() * 100, d.power() * 100 - 1, d.rpm() * 101};
        float[] max = {110, 110, 900, 900, 125, 125, 110};
        double col = w / (double) names.length;
        for (int i = 0; i < names.length; i++) {
            double x = col * i + col * 0.25, bw = col * 0.5, top = px * 1.3, bh = h - top - px * 1.5;
            boolean over = values[i] > max[i] * 0.92f;
            bar(g, x, top, bw, bh, values[i] / max[i], over ? AMBER : GREEN);
            text(g, CYAN, names[i], x + bw / 2, 1, (float) Math.min(px * 0.85f, col / names[i].length() * 1.55), 0);
            text(g, WHITE, Integer.toString(Math.round(values[i])), x + bw / 2, h - px * 1.3, px, 0);
        }
    }

    /** The amber upfront display: warnings and cautions on the left, radios in the middle, fuel and the clock on the right. */
    static void eufd(Graphics2D g, int w, int h, Data d) {
        float px = h / 6.5f;
        List<String> lines = new ArrayList<>();
        List<Color> cols = new ArrayList<>();
        cautions(d, lines, cols, true);
        for (int i = 0; i < Math.min(5, lines.size()); i++) {
            text(g, cols.get(i) == GREEN ? AMBER : cols.get(i), lines.get(i), 3, 2 + i * px * 1.15, px, -1);
        }
        String[] radios = {"VHF 121.500", "UHF 243.000", "FM1  30.000", "FM2  41.250"};
        for (int i = 0; i < radios.length; i++) {
            text(g, AMBER, radios[i], w * 0.36, 2 + i * px * 1.15, px * 0.9f, -1);
        }
        text(g, AMBER, String.format("FUEL %d", Math.round(d.fuelKg() * 2.2046f / 10) * 10), w - 3, 2, px * 0.9f, 1);
        text(g, AMBER, clock(d.hours()), w - 3, 2 + px * 1.15, px * 0.9f, 1);
        text(g, AMBER, String.format("%d%%", Math.round(d.fuel() * 100)), w - 3, 2 + px * 2.3, px * 0.9f, 1);
    }

    /** The weapons page: the helicopter from above with its four pylons, what's on them, the gun, armed or safe. */
    static void weapons(Graphics2D g, int w, int h, Data d) {
        float px = h * 0.075f;
        double cx = w / 2.0, cy = h * 0.45, s = Math.min(w, h) * 0.4;
        pen(g, GREEN, 1.4f);
        // fuselage, stub wings and the rotor disc
        box(g, cx - s * 0.1, cy - s * 0.7, cx + s * 0.1, cy + s * 0.4);
        line(g, cx, cy + s * 0.4, cx, cy + s);
        line(g, cx - s * 0.65, cy - s * 0.1, cx + s * 0.65, cy - s * 0.1);
        dashed(g, DIM, 1f, 5);
        circle(g, cx, cy - s * 0.15, s * 0.95);
        pen(g, GREEN, 1.4f);
        String[][] loads = {{"MSL", "4"}, {"RKT", "19"}, {"RKT", "19"}, {"MSL", "4"}};
        for (int i = 0; i < 4; i++) {
            double x = cx + s * (-0.6 + 0.4 * i);
            box(g, x - s * 0.08, cy - s * 0.05, x + s * 0.08, cy + s * 0.22);
            text(g, i == 0 || i == 3 ? CYAN : WHITE, loads[i][0], x, cy + s * 0.3, px * 0.7f, 0);
            text(g, WHITE, loads[i][1], x, cy + s * 0.3 + px * 0.85, px * 0.7f, 0);
        }
        text(g, WHITE, "GUN 1200", cx, cy - s * 0.95, px * 0.85f, 0);
        text(g, d.flash() ? YELLOW : AMBER, "SAFE", cx, h - px * 1.4, px, 0);
        text(g, CYAN, "WPN", 4, 4, px, -1);
    }

    // ------------------------------------------------------------------ head-up displays (on the glass, true to the world)

    /** A direction in the world to the aircraft's own frame. */
    private static Vector3f body(Data d, double x, double y, double z) {
        return new Quaternionf(d.q()).conjugate().transform(new Vector3f((float) x, (float) y, (float) z));
    }

    /** The way along the ground the ladder is laid out on: where it's going, or the nose when slow. */
    private static double[] track(Data d) {
        double x = d.vel().x, z = d.vel().z;
        if (d.speed() < 5 || Math.hypot(x, z) < 1) {
            Vector3f nose = new Quaternionf(d.q()).transform(new Vector3f(0, 0, 1));
            x = nose.x;
            z = nose.z;
        }
        double l = Math.hypot(x, z);
        return l < 1e-4 ? new double[]{0, 1} : new double[]{x / l, z / l};
    }

    /** A point on the glass at pitch degrees and side degrees off the track (right positive). */
    private static float[] sky(Data d, Glass glass, double[] tr, double pitch, double side) {
        double p = Math.toRadians(pitch), s = Math.toRadians(side);
        double fx = tr[0], fz = tr[1];
        // the pilot's right of the track, in the world: forward x up
        double rx = -fz, rz = fx;
        double c = Math.cos(p);
        double x = (fx * Math.cos(s) + rx * Math.sin(s)) * c, z = (fz * Math.cos(s) + rz * Math.sin(s)) * c;
        return glass.at(body(d, x, Math.sin(p), z));
    }

    /** The F-22's head-up display: flight-path-centred ladder, boxes for speed and height, the bank scale, all in green. */
    static void hudRaptor(Graphics2D g, int w, int h, Data d, Glass glass) {
        Color c = HUD;
        float px = w / 24f, lw = Math.max(1.8f, w / 220f);
        double[] tr = track(d);
        float pathPitch = d.speed() > 5 ? (float) Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, d.vel().y / d.speed())))) : d.pitch();
        // pitch ladder every 5 degrees round the flight path: solid above, dashed below, tips toward the horizon
        for (int p = -90; p <= 90; p += 5) {
            if (Math.abs(p - pathPitch) > 22) {
                continue;
            }
            if (p == 0) {
                pen(g, c, lw);
                line(g, sky(d, glass, tr, 0, -28), sky(d, glass, tr, 0, -2.2));
                line(g, sky(d, glass, tr, 0, 2.2), sky(d, glass, tr, 0, 28));
                continue;
            }
            if (p > 0) {
                pen(g, c, lw);
            } else {
                dashed(g, c, lw, w / 70f);
            }
            double tip = p > 0 ? -1.0 : 1.0;
            for (int side = -1; side <= 1; side += 2) {
                float[] in = sky(d, glass, tr, p, side * 1.6), out = sky(d, glass, tr, p, side * 5.5);
                line(g, in, out);
                pen(g, c, lw);
                line(g, out, sky(d, glass, tr, p + tip, side * 5.5));
                if (p < 0) {
                    dashed(g, c, lw, w / 70f);
                }
                float[] label = sky(d, glass, tr, p + tip * 0.35, side * 7.3);
                if (label != null) {
                    g.setColor(c);
                    text(g, Integer.toString(Math.abs(p)), label[0], label[1] - px * 0.45, px * 0.8f, 0);
                }
            }
        }
        pen(g, c, lw);
        // the gun cross on the nose, and the flight path marker where it's actually going
        float[] nose = glass.at(new Vector3f(0, (float) Math.sin(Math.toRadians(-1.5)), 1));
        if (nose != null) {
            line(g, nose[0] - px * 0.6, nose[1], nose[0] + px * 0.6, nose[1]);
            line(g, nose[0], nose[1] - px * 0.6, nose[0], nose[1] + px * 0.6);
        }
        float[] fpm = d.speed() > 5 ? glass.at(body(d, d.vel().x, d.vel().y, d.vel().z)) : nose;
        if (fpm != null) {
            double r = px * 0.42;
            circle(g, fpm[0], fpm[1], r);
            line(g, fpm[0] - r * 2.6, fpm[1], fpm[0] - r, fpm[1]);
            line(g, fpm[0] + r, fpm[1], fpm[0] + r * 2.6, fpm[1]);
            line(g, fpm[0], fpm[1] - r, fpm[0], fpm[1] - r * 2);
        }
        // heading tape across the top, boxed heading
        tape(g, c, d.heading(), w / 2.0, h * 0.09, w * 0.55, px, 1);
        // speed and height boxes
        double by = h * 0.47;
        box(g, w * 0.04, by, w * 0.24, by + px * 1.4);
        text(g, Integer.toString(knots(d.speed())), w * 0.225, by + px * 0.2, px, 1);
        box(g, w * 0.72, by, w * 0.96, by + px * 1.4);
        text(g, feet(d.altitude()), w * 0.945, by + px * 0.2, px, 1);
        text(g, String.format("%+,d", Math.round(d.vs() * 196.85f / 100) * 100), w * 0.945, by - px * 1.3, px * 0.8f, 1);
        if (d.radar() * 3.28f < 5000) {
            text(g, "R " + feet(d.radar()), w * 0.945, by + px * 1.8, px * 0.8f, 1);
        }
        text(g, String.format("M %.2f", d.speed() / 340f), w * 0.04, by + px * 1.8, px * 0.8f, -1);
        text(g, String.format("G %.1f", d.load()), w * 0.04, by - px * 2.6, px * 0.8f, -1);
        text(g, String.format("  %.1f", d.peakLoad()), w * 0.04, by - px * 1.5, px * 0.8f, -1);
        text(g, String.format("α %.1f", d.aoa()), w * 0.04, by + px * 2.9, px * 0.8f, -1);
        // the bank scale under the middle
        double bcx = w / 2.0, bcy = h * 0.62, br = h * 0.3;
        for (int a : new int[]{-45, -30, -20, -10, 0, 10, 20, 30, 45}) {
            double rad = Math.toRadians(90 + a);
            double r1 = br * (a % 30 == 0 || a == 0 ? 1.08 : 1.04);
            line(g, bcx + Math.cos(rad) * br, bcy + Math.sin(rad) * br, bcx + Math.cos(rad) * r1, bcy + Math.sin(rad) * r1);
        }
        double pr = Math.toRadians(90 - Math.max(-50, Math.min(50, d.bank())));
        double tx = bcx + Math.cos(pr) * br * 0.95, ty = bcy + Math.sin(pr) * br * 0.95;
        line(g, tx, ty, tx - Math.cos(pr - 0.06) * px * 0.7, ty - Math.sin(pr - 0.06) * px * 0.7);
        line(g, tx, ty, tx - Math.cos(pr + 0.06) * px * 0.7, ty - Math.sin(pr + 0.06) * px * 0.7);
        // the bottom corners: mode and arming, fuel
        text(g, d.grounded() ? "TAXI" : "NAV", w * 0.04, h - px * 2.4, px * 0.8f, -1);
        text(g, "SAFE", w * 0.04, h - px * 1.3, px * 0.8f, -1);
        text(g, String.format("FUEL %.1f", d.fuelKg() * 2.2046f / 1000), w * 0.96, h - px * 1.3, px * 0.8f, 1);
        // warnings: the breakaway cross over everything, low fuel, stall
        warnings(g, w, h, d, px, c, lw, 0.78);
    }

    private static void warnings(Graphics2D g, int w, int h, Data d, float px, Color c, float lw, double y) {
        if (!d.grounded() && d.impact() < 5 && d.flash()) {
            pen(g, c, lw * 2);
            line(g, w * 0.3, h * 0.25, w * 0.7, h * 0.75);
            line(g, w * 0.7, h * 0.25, w * 0.3, h * 0.75);
        }
        String warn = d.stalled() ? "STALL" : d.load() > 9 ? "OVER G" : d.fuel() < 0.12f ? "FUEL LOW" : d.fuel() < 0.25f ? "BINGO" : null;
        if (warn != null && d.flash()) {
            g.setColor(c);
            text(g, warn, w / 2.0, h * y, px, 0);
        }
    }

    /**
     * The Su-27's head-up display (after the ILS-31): speed in km/h and height in metres up top, the
     * aircraft symbol fixed on the boresight, a long horizon line with short pitch marks, the bank scale,
     * load and angle of attack bottom left, the mode in Cyrillic.
     */
    static void hudFlanker(Graphics2D g, int w, int h, Data d, Glass glass) {
        Color c = HUD;
        float px = w / 19f, lw = Math.max(1.8f, w / 190f);
        double[] tr = track(d);
        pen(g, c, lw);
        // the horizon, long, with a break in the middle, and marks every 10 degrees
        line(g, sky(d, glass, tr, 0, -32), sky(d, glass, tr, 0, -3));
        line(g, sky(d, glass, tr, 0, 3), sky(d, glass, tr, 0, 32));
        for (int p = -80; p <= 80; p += 10) {
            if (p == 0 || Math.abs(p - d.pitch()) > 25) {
                continue;
            }
            if (p < 0) {
                dashed(g, c, lw, w / 60f);
            } else {
                pen(g, c, lw);
            }
            line(g, sky(d, glass, tr, p, -6), sky(d, glass, tr, p, -2.5));
            line(g, sky(d, glass, tr, p, 2.5), sky(d, glass, tr, p, 6));
            float[] label = sky(d, glass, tr, p, 7.6);
            if (label != null) {
                g.setColor(c);
                text(g, Integer.toString(Math.abs(p)), label[0], label[1] - px * 0.45, px * 0.75f, 0);
            }
        }
        pen(g, c, lw);
        // the aircraft symbol, fixed on the boresight
        float[] nose = glass.at(new Vector3f(0, 0, 1));
        if (nose != null) {
            double r = px * 0.45;
            circle(g, nose[0], nose[1], r);
            line(g, nose[0] - r * 3, nose[1], nose[0] - r, nose[1]);
            line(g, nose[0] + r, nose[1], nose[0] + r * 3, nose[1]);
            line(g, nose[0], nose[1] - r, nose[0], nose[1] - r * 2);
        }
        // velocity vector, a small diamond
        if (d.speed() > 5) {
            float[] v = glass.at(body(d, d.vel().x, d.vel().y, d.vel().z));
            if (v != null) {
                double r = px * 0.35;
                Path2D dia = new Path2D.Double();
                dia.moveTo(v[0], v[1] - r);
                dia.lineTo(v[0] + r, v[1]);
                dia.lineTo(v[0], v[1] + r);
                dia.lineTo(v[0] - r, v[1]);
                dia.closePath();
                g.draw(dia);
            }
        }
        // heading scale: tens of degrees, the heading's mark under the middle
        double ty = h * 0.84, ppd = w * 0.5 / 50;
        for (int a = (int) Math.floor((d.heading() - 25) / 5) * 5; a <= d.heading() + 25; a += 5) {
            double x = w / 2.0 + (a - d.heading()) * ppd;
            int m = Math.floorMod(a, 360);
            line(g, x, ty, x, ty + px * (m % 10 == 0 ? 0.6 : 0.35));
            if (m % 10 == 0) {
                g.setColor(c);
                text(g, Integer.toString(m / 10), x, ty + px * 0.7, px * 0.75f, 0);
            }
        }
        line(g, w / 2.0, ty - px * 0.5, w / 2.0, ty - px * 0.1);
        g.setColor(c);
        text(g, String.format("%03d", Math.round(d.heading()) % 360), w / 2.0, ty + px * 1.6, px * 0.8f, 0);
        // speed and height up top, Mach and climb under them
        text(g, "V " + Math.round(d.speed() * 3.6f), w * 0.06, h * 0.06, px, -1);
        text(g, String.format("M %.2f", d.speed() / 340f), w * 0.06, h * 0.06 + px * 1.3, px * 0.8f, -1);
        text(g, "H " + Math.round(d.altitude()), w * 0.94, h * 0.06, px, 1);
        text(g, String.format("Vy %+d", Math.round(d.vs())), w * 0.94, h * 0.06 + px * 1.3, px * 0.8f, 1);
        if (d.radar() < 600) {
            text(g, "Hр " + Math.round(d.radar()), w * 0.94, h * 0.06 + px * 2.5, px * 0.8f, 1);
        }
        // bank scale under the boresight
        double bcx = w / 2.0, bcy = h * 0.42, br = h * 0.3;
        pen(g, c, lw);
        for (int a : new int[]{-60, -30, -15, 0, 15, 30, 60}) {
            double rad = Math.toRadians(90 + a);
            line(g, bcx + Math.cos(rad) * br, bcy + Math.sin(rad) * br, bcx + Math.cos(rad) * br * 1.06, bcy + Math.sin(rad) * br * 1.06);
        }
        double pr = Math.toRadians(90 - Math.max(-65, Math.min(65, d.bank())));
        circle(g, bcx + Math.cos(pr) * br * 0.95, bcy + Math.sin(pr) * br * 0.95, px * 0.18);
        // load and angle of attack, the mode, fuel
        g.setColor(c);
        text(g, String.format("n %.1f", d.load()), w * 0.06, h * 0.72, px * 0.85f, -1);
        text(g, String.format("α %.0f", d.aoa()), w * 0.06, h * 0.72 + px * 1.2, px * 0.85f, -1);
        text(g, d.grounded() ? "ПОС" : "НАВ", w * 0.06, h * 0.72 + px * 2.4, px * 0.85f, -1);
        text(g, "Т " + Math.round(d.fuelKg() / 10) * 10, w * 0.94, h * 0.72, px * 0.85f, 1);
        warnings(g, w, h, d, px, c, lw, 0.62);
    }

    private CockpitPages() {
    }
}
