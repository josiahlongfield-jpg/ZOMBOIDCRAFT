package com.zomboidcraft.client.kit;

import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.survival.character.CharacterData;
import com.zomboidcraft.survival.character.CharacterNet;
import com.zomboidcraft.survival.character.MarineData;
import com.zomboidcraft.survival.character.MarineData.Role;
import com.zomboidcraft.survival.character.Traits;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

/**
 * Marine character creation (Josiah 2026-11-06): the specialist roles down the left as angled tabs, the role and a
 * six-way profile chart in the middle, the service record (every starting skill as a ten-step bar, the role's part in
 * amber) on the right, and the ten trait picks along the bottom. Everything eases to its new value when you change the
 * role or a trait, so you see what each choice does. Then the operator's look, then where you wake.
 */
public final class MarineScreen extends Screen {
    static final int AMBER = 0xFFE0A944, AMBER_SOFT = 0x38E0A944, AMBER_FAINT = 0x14E0A944;
    static final int INK = 0xFF0B0E0D, BONE = 0xFFF5ECD2, GREY = 0xFFA9A597, FAINT = 0xFF5F5D55;

    /** The base training first, in the order a marine reads it, then whatever the role adds. */
    private static final List<Skill> ORDER = List.of(Skill.FITNESS, Skill.STRENGTH, Skill.AIMING, Skill.RELOADING, Skill.SNEAKING,
            Skill.MAINTENANCE, Skill.FIRST_AID, Skill.BLADE);

    private static final Map<String, String> TIPS = Map.of(
            "brave", "Panic builds slower", "fast_learner", "+30% XP", "strong", "Strength +4", "athletic", "Fitness +4",
            "keen_hearing", "Hears further", "eagle_eyed", "Sees further", "light_eater", "Hungry slower",
            "needs_less_sleep", "Tired slower", "handy", "Build skills +1", "organized", "Bags hold more");

    private static final String[] AXES = {"Firearms", "Close", "Mobility", "Medical", "Build", "Fieldcraft"};

    private final CharacterNet.Open open;
    private final List<Role> roles = new ArrayList<>(MarineData.roles().values());
    private int role = 0;
    private final List<String> picked = new ArrayList<>();

    // what's drawn eases toward what's chosen
    private final Map<Skill, Float> shownBase = new EnumMap<>(Skill.class), shownFull = new EnumMap<>(Skill.class);
    private final float[] shownAxes = new float[6], shownBaseAxes = new float[6];
    private final Map<Integer, Float> lift = new HashMap<>();
    private long last = System.nanoTime();
    private float flash;   // a trait you couldn't take: the slots blink

    private final List<int[]> roleRows = new ArrayList<>(), chipRows = new ArrayList<>();
    private int[] next, random, clear;
    private String info;

    public MarineScreen(CharacterNet.Open open) {
        super(Component.literal("Marine"));
        this.open = open;
        for (Skill s : Skill.values()) {
            shownBase.put(s, 0f);
            shownFull.put(s, 0f);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private Role current() {
        return roles.get(role);
    }

    private Map<Skill, Integer> levels() {
        return Traits.marineLevels(current().id(), picked);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        float ease = 1f - (float) Math.exp(-dt * 10);
        flash = Math.max(0, flash - dt * 2.5f);
        info = null;

        backdrop(g);
        int pad = 8, top = 28, foot = height - 52, bottom = foot - 6;
        header(g, pad);
        int lw = Math.max(92, width * 22 / 100), rw = Math.max(124, width * 30 / 100);
        int rx = width - pad - rw;
        roles(g, pad, top, lw, bottom, mx, my, ease);
        Map<Skill, Integer> levels = levels();
        record(g, rx, top, rw, bottom, levels, ease);
        centre(g, pad + lw + 8, top, rx - 8, bottom, levels, ease);
        footer(g, pad, foot, mx, my);
    }

    private void backdrop(GuiGraphicsExtractor g) {
        g.fill(0, 0, width, height, 0xFF0B0E0D);
        // a faint glow behind the middle, the tactical grid over it, and the edges falling into the dark
        g.fillGradient(0, height / 5, width, height / 2, 0x00283A2E, 0x5C283A2E);
        g.fillGradient(0, height / 2, width, height * 4 / 5, 0x5C283A2E, 0x00283A2E);
        for (int x = 0; x < width; x += 20) g.fill(x, 0, x + 1, height, 0x0CE0A944);
        for (int y = 0; y < height; y += 20) g.fill(0, y, width, y + 1, 0x0CE0A944);
        g.fillGradient(0, 0, width, 40, 0x99000000, 0x00000000);
        g.fillGradient(0, height - 60, width, height, 0x00000000, 0xB3000000);
    }

    private void header(GuiGraphicsExtractor g, int pad) {
        for (int i = 0; i < 24; i++) {
            int a = Math.max(0, 0x1F - i * 0x1F / 24);
            g.fill(i * width / 60, 0, (i + 1) * width / 60, 22, a << 24 | 0xE0A944);
        }
        g.fill(0, 22, width, 23, 0x40E0A944);
        float tw = KitDraw.caps(g, font, "Squad roster", pad, 7, AMBER, 1.3f);
        KitDraw.caps(g, font, open.again() ? "Man down · a new marine" : "Purge force · insertion prep", pad + tw + 8, 10, GREY, 0.6f);
        String[] steps = {"01 Marine", "02 Operator", "03 Insertion"};
        int x = width - pad;
        for (int i = steps.length - 1; i >= 0; i--) {
            int w = Math.round(KitDraw.capsWidth(font, steps[i], 0.6f)) + 12;
            x -= w;
            slant(g, x, 6, w, 10, i == 0 ? AMBER : 0x14D8D4C6);
            KitDraw.caps(g, font, steps[i], x + 6, 9, i == 0 ? INK : GREY, 0.6f);
            x -= 3;
        }
    }

    /** A parallelogram leaning right, for tabs. */
    private static void slant(GuiGraphicsExtractor g, int x, int y, int w, int h, int colour) {
        for (int dy = 0; dy < h; dy++) {
            int shift = (h - dy) * 3 / h;
            g.fill(x + shift, y + dy, x + w - 3 + shift, y + dy + 1, colour);
        }
    }

    private void roles(GuiGraphicsExtractor g, int x, int top, int w, int bottom, int mx, int my, float ease) {
        KitDraw.caps(g, font, "Specialist role", x, top, GREY, 0.6f);
        roleRows.clear();
        int y = top + 9, gap = 2;
        int rh = Math.max(13, Math.min(20, (bottom - y - gap * (roles.size() - 1)) / roles.size()));
        for (int i = 0; i < roles.size(); i++) {
            Role r = roles.get(i);
            boolean sel = i == role, hover = KitDraw.in(mx, my, x, y, w, rh);
            float l = lift.getOrDefault(i, 0f);
            l += ((sel ? 4 : hover ? 3 : 0) - l) * ease;
            lift.put(i, l);
            int ox = x + Math.round(l);
            chevron(g, ox, y, w - 4, rh, sel ? AMBER : hover ? 0x1FD8D4C6 : 0x0DD8D4C6, sel ? 0xFFE0A944 : hover ? 0x99E0A944 : 0x1AD8D4C6);
            // the role's code in a small box, its name and the jobs it's built from
            int bx = ox + 4, by = y + (rh - 9) / 2;
            KitDraw.frame(g, bx, by, 18, 9, sel ? INK : 0x99E0A944);
            KitDraw.capsCentred(g, font, r.code(), bx + 9, by + 2, sel ? INK : AMBER, 0.5f);
            int tx = bx + 22;
            if (rh >= 16) {
                KitDraw.text(g, font, r.name(), tx, y + 3, sel ? INK : BONE, 0.75f);
                KitDraw.text(g, font, r.from(), tx, y + rh - 6, sel ? 0xCC0B0E0D : GREY, 0.45f);
            } else {
                KitDraw.text(g, font, r.name(), tx, y + (rh - 6) / 2f, sel ? INK : BONE, 0.7f);
            }
            if (hover) info = r.name() + ": " + r.description();
            roleRows.add(new int[]{x, y, w, rh});
            y += rh + gap;
        }
    }

    /** A tab with a pointed right end, filled and edged. */
    private static void chevron(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int edge) {
        int tip = Math.max(4, h / 2);
        for (int dy = 0; dy < h; dy++) {
            int reach = w - tip + Math.round(tip * (1 - Math.abs((dy + 0.5f) - h / 2f) / (h / 2f)));
            g.fill(x, y + dy, x + reach, y + dy + 1, fill);
            g.fill(x + reach - 1, y + dy, x + reach, y + dy + 1, edge);
        }
        g.fill(x, y, x + w - tip, y + 1, edge);
        g.fill(x, y + h - 1, x + w - tip, y + h, edge);
        g.fill(x, y, x + 1, y + h, edge);
    }

    private void record(GuiGraphicsExtractor g, int x, int top, int w, int bottom, Map<Skill, Integer> levels, float ease) {
        int h = bottom - top;
        cutPanel(g, x, top, w, h, 6);
        // a slow scan line running down the record
        long t = System.currentTimeMillis() % 4000;
        int sy = top + (int) ((h + 30) * t / 4000) - 30;
        g.enableScissor(x + 1, top + 1, x + w - 1, bottom - 1);
        g.fillGradient(x + 1, sy, x + w - 1, sy + 15, 0x00E0A944, 0x10E0A944);
        g.fillGradient(x + 1, sy + 15, x + w - 1, sy + 30, 0x10E0A944, 0x00E0A944);
        g.disableScissor();

        int cx = x + 8, cw = w - 16, y = top + 6;
        KitDraw.caps(g, font, "Service record", cx, y, GREY, 0.6f);
        float beat = (float) (0.55 + 0.45 * Math.sin(System.currentTimeMillis() / 1000.0 * Math.PI));
        int liveA = Math.round(0xFF * beat);
        KitDraw.capsRight(g, font, "● Live", cx + cw, y, liveA << 24 | 0xE0A944, 0.6f);
        y += 10;

        List<Skill> rows = new ArrayList<>(ORDER);
        for (Skill s : Skill.values()) if (!rows.contains(s) && levels.getOrDefault(s, 0) > 0) rows.add(s);
        int traitsH = 20;
        int rowH = Math.max(7, Math.min(10, (bottom - traitsH - y) / Math.max(1, rows.size())));
        Map<Skill, Integer> base = MarineData.base();
        for (Skill s : Skill.values()) {
            float b = base.getOrDefault(s, 0), f = levels.getOrDefault(s, 0);
            shownBase.put(s, shownBase.get(s) + (b - shownBase.get(s)) * ease);
            shownFull.put(s, shownFull.get(s) + (f - shownFull.get(s)) * ease);
        }
        int nameW = Math.round(cw * 0.36f), valW = 18;
        for (Skill s : rows) {
            int b = base.getOrDefault(s, 0), f = levels.getOrDefault(s, 0);
            boolean up = f > b;
            KitDraw.text(g, font, label(s), cx, y + (rowH - 5) / 2f, up ? BONE : GREY, 0.6f);
            segments(g, cx + nameW, y + rowH / 2 - 2, cw - nameW - valW - 2, 4, shownBase.get(s), shownFull.get(s));
            String v = String.valueOf(f);
            float vx = cx + cw - KitDraw.width(font, v + (up ? " +" + (f - b) : ""), 0.6f);
            KitDraw.text(g, font, v, vx, y + (rowH - 5) / 2f, up ? BONE : GREY, 0.6f);
            if (up) KitDraw.text(g, font, " +" + (f - b), vx + KitDraw.width(font, v, 0.6f), y + (rowH - 5) / 2f, AMBER, 0.6f);
            y += rowH;
        }

        int ty = bottom - traitsH;
        for (int dx = cx; dx < cx + cw; dx += 4) g.fill(dx, ty, dx + 2, ty + 1, 0x40E0A944);
        KitDraw.caps(g, font, "Role traits", cx, ty + 4, GREY, 0.5f);
        List<String> names = new ArrayList<>();
        for (String id : current().traits()) {
            var tr = CharacterData.trait(id);
            names.add(tr == null ? id : tr.name());
        }
        KitDraw.text(g, font, names.isEmpty() ? "None" : String.join(", ", names), cx, ty + 11, names.isEmpty() ? FAINT : BONE, 0.6f);
    }

    private static String label(Skill s) {
        return s == Skill.BLADE ? "Short Blade" : s.displayName();
    }

    /** Ten steps: the base training in bone, the role and traits' part in amber with a glow, the rest empty. */
    private void segments(GuiGraphicsExtractor g, int x, int y, int w, int h, float base, float full) {
        float gui = (float) minecraft.getWindow().getGuiScale();
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.scale(1 / gui, 1 / gui);
        int px = Math.round(x * gui), py = Math.round(y * gui), pw = Math.round(w * gui), ph = Math.round(h * gui);
        int gap = Math.max(1, Math.round(gui));
        float seg = (pw - gap * 9) / 10f;
        for (int i = 0; i < 10; i++) {
            int sx = px + Math.round(i * (seg + gap)), ex = px + Math.round(i * (seg + gap) + seg);
            g.fill(sx, py, ex, py + ph, 0x14D8D4C6);
            float fFill = Math.max(0, Math.min(1, full - i)), bFill = Math.max(0, Math.min(1, base - i));
            if (fFill > 0) {
                int fx = sx + Math.round((ex - sx) * fFill);
                g.fill(sx, py - gap, fx, py + ph + gap, 0x26E0A944);
                g.fill(sx, py, fx, py + ph, AMBER);
            }
            if (bFill > 0) g.fill(sx, py, sx + Math.round((ex - sx) * bFill), py + ph, 0xFFB9B4A4);
        }
        pose.popMatrix();
    }

    /** A dark panel with its top-right and bottom-left corners cut off, edged in faint amber. */
    private static void cutPanel(GuiGraphicsExtractor g, int x, int y, int w, int h, int cut) {
        for (int dy = 0; dy < h; dy++) {
            int l = dy >= h - cut ? cut - (h - 1 - dy) : 0;
            int r = dy < cut ? cut - dy : 0;
            g.fill(x + l, y + dy, x + w - r, y + dy + 1, 0xD10C100E);
            g.fill(x + l, y + dy, x + l + 1, y + dy + 1, 0x38E0A944);
            g.fill(x + w - r - 1, y + dy, x + w - r, y + dy + 1, 0x38E0A944);
        }
        g.fill(x, y, x + w - cut, y + 1, 0x38E0A944);
        g.fill(x + cut, y + h - 1, x + w, y + h, 0x38E0A944);
    }

    private void centre(GuiGraphicsExtractor g, int left, int top, int right, int bottom, Map<Skill, Integer> levels, float ease) {
        Role r = current();
        float cx = (left + right) / 2f;
        // the role's code, huge and faint, behind everything
        KitDraw.capsCentred(g, font, r.code(), cx, top + (bottom - top) / 2f - 22, 0x10E0A944, 6f);
        KitDraw.capsCentred(g, font, r.code() + " // " + r.from(), cx, top + 1, AMBER, 0.5f);
        KitDraw.capsCentred(g, font, r.name(), cx, top + 8, BONE, 1.5f);
        int y = top + 22;
        y += wrapCentred(g, r.description(), cx, y, right - left - 10, GREY, 0.65f);

        // the profile chart: training in bone, with the role and traits in amber
        float[] full = axes(levels), base = axes(MarineData.base());
        for (int i = 0; i < 6; i++) {
            shownAxes[i] += (full[i] - shownAxes[i]) * ease;
            shownBaseAxes[i] += (base[i] - shownBaseAxes[i]) * ease;
        }
        int legend = 10;
        float avail = bottom - legend - y - 4;
        float rad = Math.max(16, Math.min((right - left) / 2f - 30, avail / 2f - 9));
        float cy = y + 4 + avail / 2f;
        radar(g, cx, cy, rad);

        if (info != null) {
            wrapCentred(g, info, cx, bottom - legend - 1, right - left - 6, BONE, 0.55f);
        } else {
            float lw = KitDraw.capsWidth(font, "Marine training", 0.5f) + KitDraw.capsWidth(font, "With role + traits", 0.5f) + 30;
            float lx = cx - lw / 2;
            g.fill(Math.round(lx), bottom - 5, Math.round(lx) + 7, bottom - 4, 0x99D8D4C6);
            lx += 10 + KitDraw.caps(g, font, "Marine training", lx + 10, bottom - 7, GREY, 0.5f);
            g.fill(Math.round(lx + 10), bottom - 5, Math.round(lx + 10) + 7, bottom - 4, AMBER);
            KitDraw.caps(g, font, "With role + traits", lx + 20, bottom - 7, GREY, 0.5f);
        }
    }

    /** The six profile axes out of 10, read off the skills. */
    private static float[] axes(Map<Skill, Integer> m) {
        java.util.function.ToIntFunction<Skill> l = s -> m.getOrDefault(s, 0);
        float[] a = {
                (l.applyAsInt(Skill.AIMING) + l.applyAsInt(Skill.RELOADING)) / 2f,
                Math.max(l.applyAsInt(Skill.BLADE), l.applyAsInt(Skill.BLUNT)),
                (l.applyAsInt(Skill.FITNESS) + l.applyAsInt(Skill.SPRINTING) + l.applyAsInt(Skill.LIGHTFOOTED)
                        + l.applyAsInt(Skill.NIMBLE) + l.applyAsInt(Skill.SNEAKING)) / 3.2f,
                l.applyAsInt(Skill.FIRST_AID),
                Math.max(Math.max(Math.max(l.applyAsInt(Skill.CARPENTRY), l.applyAsInt(Skill.MASONRY)),
                        Math.max(l.applyAsInt(Skill.ELECTRICAL), l.applyAsInt(Skill.MECHANICS))),
                        Math.max(l.applyAsInt(Skill.METALWORKING), l.applyAsInt(Skill.MAINTENANCE))),
                Math.max(Math.max(l.applyAsInt(Skill.COOKING), l.applyAsInt(Skill.AGRICULTURE)),
                        Math.max(l.applyAsInt(Skill.TRACKING) * 2, Math.max(Math.round(l.applyAsInt(Skill.BUTCHERING) * 1.5f),
                                l.applyAsInt(Skill.ANIMAL_CARE))))
        };
        for (int i = 0; i < a.length; i++) a[i] = Math.min(10, a[i]);
        return a;
    }

    /** The hexagon chart, drawn at the screen's real pixels so its edges are fine. */
    private void radar(GuiGraphicsExtractor g, float cx, float cy, float rad) {
        float gui = (float) minecraft.getWindow().getGuiScale();
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.scale(1 / gui, 1 / gui);
        float pcx = cx * gui, pcy = cy * gui, pr = rad * gui;
        for (int ring = 1; ring <= 3; ring++) polygon(g, pcx, pcy, ring(pr * ring / 3f), 0, ring == 3 ? 0x29D8D4C6 : 0x14D8D4C6, gui);
        for (int i = 0; i < 6; i++) {
            float[] p = point(pcx, pcy, i, pr);
            line(g, pcx, pcy, p[0], p[1], 0x14D8D4C6, Math.max(1, gui / 3));
        }
        float[] base = new float[6], full = new float[6];
        for (int i = 0; i < 6; i++) {
            base[i] = Math.max(0.4f, shownBaseAxes[i]) / 10f * pr;
            full[i] = Math.max(0.4f, shownAxes[i]) / 10f * pr;
        }
        polygon(g, pcx, pcy, base, 0x1AD8D4C6, 0x73D8D4C6, gui);
        polygon(g, pcx, pcy, full, 0x38E0A944, AMBER, gui);
        for (int i = 0; i < 6; i++) {
            float[] p = point(pcx, pcy, i, full[i]);
            int d = Math.round(gui * 1.3f);
            g.fill(Math.round(p[0]) - d, Math.round(p[1]) - d, Math.round(p[0]) + d, Math.round(p[1]) + d, AMBER);
        }
        pose.popMatrix();
        for (int i = 0; i < 6; i++) {
            float[] p = point(cx, cy, i, rad + 9);
            KitDraw.capsCentred(g, font, AXES[i], p[0], p[1] - 5, GREY, 0.45f);
            KitDraw.textCentred(g, font, String.format(java.util.Locale.ROOT, "%.1f", shownAxes[i]), p[0], p[1] + 0.5f, BONE, 0.6f);
        }
    }

    private static float[] ring(float r) {
        return new float[]{r, r, r, r, r, r};
    }

    private static float[] point(float cx, float cy, int i, float r) {
        double a = -Math.PI / 2 + i * Math.PI / 3;
        return new float[]{(float) (cx + Math.cos(a) * r), (float) (cy + Math.sin(a) * r)};
    }

    /** A six-sided shape with a radius per corner: filled row by row, then outlined. */
    private static void polygon(GuiGraphicsExtractor g, float cx, float cy, float[] radii, int fill, int edge, float gui) {
        float[][] pts = new float[6][];
        for (int i = 0; i < 6; i++) pts[i] = point(cx, cy, i, radii[i]);
        if ((fill >>> 24) != 0) {
            float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (float[] p : pts) {
                minY = Math.min(minY, p[1]);
                maxY = Math.max(maxY, p[1]);
            }
            for (int y = Math.round(minY); y < Math.round(maxY); y++) {
                float yc = y + 0.5f, lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
                for (int i = 0; i < 6; i++) {
                    float[] a = pts[i], b = pts[(i + 1) % 6];
                    if ((a[1] <= yc && b[1] > yc) || (b[1] <= yc && a[1] > yc)) {
                        float x = a[0] + (yc - a[1]) / (b[1] - a[1]) * (b[0] - a[0]);
                        lo = Math.min(lo, x);
                        hi = Math.max(hi, x);
                    }
                }
                if (lo < hi) g.fill(Math.round(lo), y, Math.round(hi), y + 1, fill);
            }
        }
        float t = Math.max(1, gui * 0.6f);
        for (int i = 0; i < 6; i++) {
            float[] a = pts[i], b = pts[(i + 1) % 6];
            line(g, a[0], a[1], b[0], b[1], edge, t);
        }
    }

    /** A line of square dots, thick enough to read at any scale. */
    private static void line(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int colour, float thick) {
        float len = (float) Math.hypot(x1 - x0, y1 - y0);
        int steps = Math.max(1, Math.round(len));
        int t = Math.max(1, Math.round(thick));
        for (int i = 0; i <= steps; i += 1) {
            float f = i / (float) steps;
            int x = Math.round(x0 + (x1 - x0) * f), y = Math.round(y0 + (y1 - y0) * f);
            g.fill(x - t / 2, y - t / 2, x - t / 2 + t, y - t / 2 + t, colour);
        }
    }

    /** Wrapped text at a scale, each line centred. Returns the height used. */
    private int wrapCentred(GuiGraphicsExtractor g, String s, float cx, float y, int width, int colour, float scale) {
        var lines = font.split(com.zomboidcraft.client.hud.Hud.text(s), Math.round(width / scale));
        float ly = y;
        for (var line : lines) {
            float w = font.width(line) * scale;
            Matrix3x2fStack pose = g.pose();
            pose.pushMatrix();
            pose.translate(cx - w / 2, ly);
            pose.scale(scale, scale);
            g.text(font, line, 0, 0, colour, false);
            pose.popMatrix();
            ly += 9 * scale + 1;
        }
        return Math.round(ly - y) + 2;
    }

    private void footer(GuiGraphicsExtractor g, int pad, int y, int mx, int my) {
        g.fill(0, y, width, y + 1, 0x40E0A944);
        g.fillGradient(0, y + 1, width, height, 0x00E0A944, 0x12E0A944);
        int top = y + 6;
        // the slots: two bars that fill as you pick, and blink when you try a third
        int lx = pad;
        KitDraw.caps(g, font, "Traits", lx, top, GREY, 0.6f);
        int max = MarineData.picks();
        for (int i = 0; i < max; i++) {
            boolean on = i < picked.size();
            int c = on ? AMBER : (flash > 0 && Math.sin(flash * 20) > 0 ? 0x99D0675E : 0x26D8D4C6);
            slant(g, lx + i * 18, top + 9, 16, 4, c);
        }
        KitDraw.text(g, font, picked.size() + " of " + max + " picked", lx, top + 17, picked.size() == max ? AMBER : GREY, 0.6f);

        // Next, and the two small buttons above the footer hints
        int bw = 74, bh = 22, bx = width - pad - bw, by = top;
        boolean overNext = KitDraw.in(mx, my, bx, by, bw, bh);
        bevel(g, bx, by, bw, bh, overNext ? 0xFFF3C56B : AMBER);
        KitDraw.capsCentred(g, font, "Next: operator ›", bx + bw / 2f, by + 8, INK, 0.7f);
        next = new int[]{bx, by, bw, bh};

        // the ten picks
        int gx = pad + 60, gw = bx - 8 - gx, cols = 5, gap = 3;
        int chipW = (gw - gap * (cols - 1)) / cols, chipH = 15;
        chipRows.clear();
        List<String> list = MarineData.traits();
        boolean full = picked.size() >= max;
        for (int i = 0; i < list.size(); i++) {
            String id = list.get(i);
            int cx = gx + (i % cols) * (chipW + gap), cy = top + (i / cols) * (chipH + gap);
            boolean on = picked.contains(id), hover = KitDraw.in(mx, my, cx, cy, chipW, chipH);
            int fill = on ? 0x2EE0A944 : hover ? 0x14D8D4C6 : 0x0AD8D4C6;
            int edge = on ? AMBER : hover ? 0x99E0A944 : full ? 0x14D8D4C6 : 0x24D8D4C6;
            g.fill(cx, cy, cx + chipW, cy + chipH, fill);
            KitDraw.frame(g, cx, cy, chipW, chipH, edge);
            if (on) g.fill(cx, cy, cx + 2, cy + chipH, AMBER);
            var tr = CharacterData.trait(id);
            String name = tr == null ? id : tr.name();
            int nameC = on ? BONE : full ? FAINT : 0xFFC8C4B6;
            float ns = Math.min(0.6f, (chipW - 8) / Math.max(1f, KitDraw.width(font, name, 1f)));
            KitDraw.text(g, font, name, cx + 5, cy + 2.5f, nameC, ns);
            KitDraw.text(g, font, TIPS.getOrDefault(id, ""), cx + 5, cy + 9, on ? AMBER : full ? FAINT : GREY, 0.45f);
            if (hover && tr != null) info = name + ": " + tr.description();
            chipRows.add(new int[]{cx, cy, chipW, chipH});
        }

        int hy = height - 10, hx = pad;
        hx += KitDraw.hint(g, font, "LMB", "Pick", hx, hy);
        random = new int[]{hx, hy - 1, 0, 11};
        int w = KitDraw.hint(g, font, "R", "Random", hx, hy);
        random[2] = w;
        hx += w;
        clear = new int[]{hx, hy - 1, 0, 11};
        w = KitDraw.hint(g, font, "C", "Clear", hx, hy);
        clear[2] = w;
        hx += w;
        hx += KitDraw.hint(g, font, "Enter", "Next", hx, hy);
        KitDraw.hint(g, font, "Esc", "Game menu", hx, hy);
    }

    /** A button with its top-left and bottom-right corners clipped. */
    private static void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int colour) {
        int c = 4;
        for (int dy = 0; dy < h; dy++) {
            int l = dy < c ? c - dy : 0, r = dy >= h - c ? c - (h - 1 - dy) : 0;
            g.fill(x + l, y + dy, x + w - r, y + dy + 1, colour);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        for (int i = 0; i < roleRows.size(); i++) {
            int[] r = roleRows.get(i);
            if (KitDraw.in(mx, my, r[0], r[1], r[2], r[3])) {
                role = i;
                return true;
            }
        }
        List<String> list = MarineData.traits();
        for (int i = 0; i < chipRows.size(); i++) {
            int[] r = chipRows.get(i);
            if (KitDraw.in(mx, my, r[0], r[1], r[2], r[3])) {
                toggle(list.get(i));
                return true;
            }
        }
        if (next != null && KitDraw.in(mx, my, next[0], next[1], next[2], next[3])) finish();
        else if (random != null && KitDraw.in(mx, my, random[0], random[1], random[2], random[3])) randomise();
        else if (clear != null && KitDraw.in(mx, my, clear[0], clear[1], clear[2], clear[3])) picked.clear();
        return true;
    }

    private void toggle(String id) {
        if (picked.remove(id)) return;
        if (picked.size() >= MarineData.picks()) {
            flash = 1;
            return;
        }
        picked.add(id);
    }

    private void randomise() {
        Random r = new Random();
        role = r.nextInt(roles.size());
        picked.clear();
        List<String> pool = new ArrayList<>(MarineData.traits());
        java.util.Collections.shuffle(pool, r);
        picked.addAll(pool.subList(0, Math.min(MarineData.picks(), pool.size())));
    }

    private void finish() {
        ClientPlayNetworking.send(new CharacterNet.Create(MarineData.PREFIX + current().id(), List.copyOf(picked)));
        CharacterScreen.made();
        AppearanceScreen.afterTraits(minecraft);  // then the operator's look, then where you wake
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();
        if (k == InputConstants.KEY_ESCAPE) {
            minecraft.gui.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true));
        } else if (k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER) {
            finish();
        } else if (k == InputConstants.KEY_UP) {
            role = (role + roles.size() - 1) % roles.size();
        } else if (k == InputConstants.KEY_DOWN) {
            role = (role + 1) % roles.size();
        } else if (k == InputConstants.KEY_R) {
            randomise();
        } else if (k == InputConstants.KEY_C) {
            picked.clear();
        }
        return true;
    }

    // ------------------------------------------------------------------ for the UI test

    public void choose(String roleId, List<String> traits) {
        for (int i = 0; i < roles.size(); i++) if (roles.get(i).id().equals(roleId)) role = i;
        picked.clear();
        for (String t : traits) toggle(t);
    }

    /** Snaps every bar and the chart to their values, so a screenshot shows the choice rather than the easing. */
    public void settle() {
        Map<Skill, Integer> levels = levels(), base = MarineData.base();
        for (Skill s : Skill.values()) {
            shownBase.put(s, (float) base.getOrDefault(s, 0));
            shownFull.put(s, (float) levels.getOrDefault(s, 0));
        }
        float[] f = axes(levels), b = axes(base);
        System.arraycopy(f, 0, shownAxes, 0, 6);
        System.arraycopy(b, 0, shownBaseAxes, 0, 6);
    }
}
