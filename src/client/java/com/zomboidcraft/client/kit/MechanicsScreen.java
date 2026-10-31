package com.zomboidcraft.client.kit;

import com.zomboidcraft.world.cars.CarModels;
import com.zomboidcraft.world.cars.CarParts;
import com.zomboidcraft.world.cars.CarProp;
import com.zomboidcraft.world.cars.Mechanics;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The mechanics screen, opened with the interact key at a car's front end: on the left an x-ray of the car
 * from above with each part drawn and coloured by its condition and what's wrong with it (from Mechanics 1), on the right
 * every part grouped by where it's worked on, with its condition and the jobs it offers. A job you can't do
 * yet is greyed with the reason (the level, the tool, the hoist), the same reason the server would give.
 */
public class MechanicsScreen extends Screen {
    private static final int W = 420, LEFT = 150, ROW = 15;
    /** As tall as fits: 270, less in a small window (rows scroll). */
    private int H = 270;
    private static final String[] AREAS = {"Engine bay", "Underneath", "Wheels", "Body"};

    private final CarProp car;
    private float scroll;
    /** The rows as drawn last frame, for clicks: slot, job, button bounds. */
    private final List<int[]> buttons = new ArrayList<>();
    private String hover;

    public MechanicsScreen(CarProp car) {
        super(Component.literal("Mechanics"));
        this.car = car;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (car.isRemoved() || minecraft.player == null || car.distanceToSqr(minecraft.player) > 8 * 8) {
            onClose();
        }
    }

    private int x() {
        return (width - W) / 2;
    }

    private int y() {
        H = Math.min(270, height - 8);
        return (height - H) / 2;
    }

    private static int colour(int c) {
        if (c == -1) return 0x66E8E4DA;
        if (c < 25) return KitDraw.BAD;
        if (c < 60) return KitDraw.WARN;
        return KitDraw.GOOD;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        g.fill(0, 0, width, height, 0x99080A0C);
        int x = x(), y = y();
        g.fill(x, y, x + W, y + H, KitDraw.PANEL);
        KitDraw.frame(g, x, y, W, H, 0x2EE8E4DA);
        String parts = car.parts();
        CarModels.Model m = CarModels.get(car.model());
        int level = Mechanics.level(minecraft.player);
        KitDraw.caps(g, font, car.model().replace('_', ' '), x + 10, y + 9, KitDraw.BRIGHT, 1.1f);
        KitDraw.text(g, font, "Overall " + CarParts.overall(parts) + "%   " + CarParts.kind(m).name().toLowerCase(Locale.ROOT)
                + " parts   Mechanics " + level + (car.hoisted() ? "   on the hoist" : ""), x + 10, y + 22, KitDraw.DIM, 0.85f);
        // the car takes the column down to the diagnosis, which keeps room for a few lines under it
        int figBottom = y + H - 70;
        outline(g, parts, m, x + 4, y + 36, LEFT - 12, figBottom - y - 36, mx, my);
        // what's wrong, worst first: only someone who knows can read it off the car
        int fy = figBottom + 4;
        KitDraw.caps(g, font, "Diagnosis", x + 10, fy, KitDraw.TEXT, 0.8f);
        fy += 12;
        if (level < 1) {
            fy = KitDraw.wrap(g, font, "You can't tell what's wrong yet (Mechanics 1).", x + 10, fy, LEFT - 20, KitDraw.DIM);
        } else {
            List<String> faults = CarParts.faults(parts);
            if (faults.isEmpty()) {
                KitDraw.text(g, font, "Nothing failing.", x + 10, fy, KitDraw.GOOD, 0.8f);
            }
            for (int i = 0; i < Math.min(Math.min(7, faults.size()), (y + H - 8 - fy) / 10); i++) {
                KitDraw.text(g, font, faults.get(i), x + 10, fy + i * 10, KitDraw.BAD, 0.75f);
            }
        }
        rows(g, parts, x + LEFT, y + 10, W - LEFT - 10, H - 34, mx, my);
        if (hover == null && figureHover != null) {
            KitDraw.text(g, font, figureHover, x + LEFT, y + H - 16, KitDraw.BRIGHT, 0.8f);
        } else if (hover != null) {
            KitDraw.text(g, font, hover, x + LEFT, y + H - 16, KitDraw.WARN, 0.8f);
        } else {
            KitDraw.text(g, font, "Click a job to start it. Moving or being hit stops it.", x + LEFT, y + H - 16, KitDraw.DIM, 0.8f);
        }
    }

    /** Where the figure is drawn this frame: its middle on screen and pixels per centimetre. */
    private float fcx, fcy, ppc;
    /** The figure's parts as drawn this frame, top last, for the mouse: slot, then the box in centimetres. */
    private final List<int[]> hits = new ArrayList<>();
    private String figureHover;
    private int hoverSlot = -1;

    /**
     * The car from above, nose up, x-ray style (Josiah 2026-10-31: "more detail on the image"): the model's own
     * width, length, cabin, axles, seats, steering wheel and glovebox, and every part drawn as itself in its
     * condition's colour. Parts underneath (the hoist's side) are dashed; a missing part is an empty dashed outline.
     * Drawn in centimetres under a scaled pose, u to the right (the passenger side) and v toward the back.
     */
    private void outline(GuiGraphicsExtractor g, String parts, CarModels.Model m, int x, int y, int w, int h, int mx, int my) {
        float wide = m != null && m.size != null ? m.size[0] * 100 : 180, longCm = m != null && m.size != null ? m.size[2] * 100 : 450;
        ppc = Math.min(h / (longCm + 16), w / (wide + 40));
        fcx = x + w / 2f;
        fcy = y + h / 2f;
        int W2 = Math.round(wide / 2), L2 = Math.round(longCm / 2);
        int axle = Math.round((m != null && m.wheelbase > 0 ? Math.min(m.wheelbase, longCm / 100 * 0.8f) : longCm / 100 * 0.6f) * 50);
        // the cabin: where the body stands near its full height
        int roofFront = -L2 / 3, roofBack = L2 / 2;
        if (m != null && m.heights != null && m.nx > 0) {
            int top = 0;
            for (int hgt : m.heights) top = Math.max(top, hgt);
            int first = -1, last = -1;
            for (int iz = 0; iz < m.nz; iz++) {
                int row = 0;
                for (int ix = 0; ix < m.nx; ix++) row = Math.max(row, m.heights[iz * m.nx + ix]);
                if (row >= top * 0.82f) {
                    if (first < 0) first = iz;
                    last = iz;
                }
            }
            if (first >= 0) {
                // nose toward +z, so the cabin's front edge is its highest z
                roofFront = -Math.round((m.z0 + (last + 1) * m.cell) * 100);
                roofBack = -Math.round((m.z0 + first * m.cell) * 100);
            }
        }
        int bayEnd = Math.max(-L2 + 60, roofFront - 50);
        hits.clear();
        hoverSlot = -1;
        figureHover = null;
        float mu = (mx - fcx) / ppc, mv = (my - fcy) / ppc;

        g.pose().pushMatrix();
        g.pose().translate(fcx, fcy);
        g.pose().scale(ppc, ppc);
        int px = Math.max(1, Math.round(1 / ppc));

        // the shell: body, bumpers, glass, roof, dash and steering wheel (not parts, so drawn plain)
        g.fill(-W2, -L2 + 22, W2, L2 - 22, SHELL_EDGE);
        g.fill(-W2 + 14, -L2, W2 - 14, L2, SHELL_EDGE);
        g.fill(-W2 + 6, -L2 + 10, W2 - 6, L2 - 10, SHELL_EDGE);
        g.fill(-W2 + px, -L2 + 22, W2 - px, L2 - 22, SHELL);
        g.fill(-W2 + 14, -L2 + px, W2 - 14, L2 - px, SHELL);
        g.fill(-W2 + 6, -L2 + 10, W2 - 6, L2 - 10, SHELL);
        g.fill(-W2 + 20, -L2 - 4, W2 - 20, -L2 - 4 + 2 * px, SHELL_EDGE);
        g.fill(-W2 + 20, L2 + 4 - 2 * px, W2 - 20, L2 + 4, SHELL_EDGE);
        g.fill(-W2 + 16, roofFront - 46, W2 - 16, roofFront, GLASS);
        g.fill(-W2 + 18, roofFront, W2 - 18, roofBack, ROOF);
        g.fill(-W2 + 16, roofBack, W2 - 16, Math.min(L2 - 30, roofBack + 30), GLASS);
        int dash = roofFront - 40;
        g.fill(-W2 + 12, dash, W2 - 12, dash + 2 * px, SHELL_EDGE);
        if (m != null && m.steering != null) {
            int su = -Math.round(m.steering[0] * 100), sv = -Math.round(m.steering[2] * 100);
            ring(g, su - 18, sv - 4, su + 18, sv + 4, 2 * px, SHELL_EDGE);
        }

        // underneath first, so everything above draws over it
        int under = Math.round(W2 * 0.4f);
        part(g, parts, CarParts.Part.STEERING, 0, -W2 + 36, -axle - 3, W2 - 36, -axle + 3, true, mu, mv, px);
        part(g, parts, CarParts.Part.GEARBOX, 0, -14, bayEnd - 10, 14, bayEnd + 50, true, mu, mv, px);
        g.fill(-3, bayEnd + 50, 3, axle - 10, SHELL_EDGE);   // the prop shaft back to the rear axle
        part(g, parts, CarParts.Part.FUEL_TANK, 0, -under, axle - 80, under, axle - 36, true, mu, mv, px);
        part(g, parts, CarParts.Part.FUEL_PUMP, 0, under - 18, axle - 74, under - 4, axle - 60, true, mu, mv, px);
        part(g, parts, CarParts.Part.EXHAUST, 0, under + 8, bayEnd - 20, under + 14, L2 - 70, true, mu, mv, px);
        part(g, parts, CarParts.Part.EXHAUST, 0, under - 6, L2 - 70, under + 30, L2 - 36, true, mu, mv, px);
        part(g, parts, CarParts.Part.EXHAUST, 0, under + 18, L2 - 36, under + 24, L2 + 2, true, mu, mv, px);

        // the wheels: tyre (with tread), rim, brake disc and calliper, and the spring
        for (int i = 0; i < 4; i++) {
            int side = i % 2 == 0 ? -1 : 1, v = i < 2 ? -axle : axle;
            int out = side * (W2 + 8), in = side * (W2 - 14);
            int t0 = Math.min(out, in), t1 = Math.max(out, in);
            part(g, parts, CarParts.Part.TIRE, i, t0, v - 32, t1, v + 32, false, mu, mv, px);
            if (CarParts.get(parts, CarParts.Part.TIRE, i) >= 0) {
                for (int k = v - 28; k < v + 28; k += 8) g.fill(t0 + 2, k, t1 - 2, k + px, DETAIL);
            }
            part(g, parts, CarParts.Part.RIM, i, t0 + 6, v - 16, t1 - 6, v + 16, false, mu, mv, px);
            int disc = side * (W2 - 20);
            part(g, parts, CarParts.Part.BRAKES, i, Math.min(disc, disc + side * 6), v - 18, Math.max(disc, disc + side * 6), v + 18, false, mu, mv, px);
            int cal = side * (W2 - 28);
            part(g, parts, CarParts.Part.BRAKES, i, Math.min(cal, cal + side * 8), v - 6, Math.max(cal, cal + side * 8), v + 8, false, mu, mv, px);
            // the coil spring: a zigzag of short bars inboard of the brake
            for (int k = 0; k < 6; k++) {
                int u0 = side * (W2 - 34 - k * 6);
                int dv = k % 2 == 0 ? -7 : 1;
                part(g, parts, CarParts.Part.SUSPENSION, i, Math.min(u0, u0 - side * 6), v + dv, Math.max(u0, u0 - side * 6), v + dv + 6, false, mu, mv, px);
            }
        }

        // the engine bay, laid out by fractions of the bay so vans and trucks keep the same order
        int bx0 = -W2 + 16, bx1 = W2 - 16, by0 = -L2 + 10, by1 = bayEnd;
        Bay b = new Bay(bx0, by0, bx1, by1);
        part(g, parts, CarParts.Part.RADIATOR, 0, b.u(.12f), b.v(.02f), b.u(.88f), b.v(.12f), false, mu, mv, px);
        for (float f = .16f; f < .86f; f += .06f) g.fill(b.u(f), b.v(.02f), b.u(f) + px, b.v(.12f), DETAIL);
        part(g, parts, CarParts.Part.HORN, 0, b.u(.04f), b.v(.15f), b.u(.13f), b.v(.24f), false, mu, mv, px);
        part(g, parts, CarParts.Part.FAN_BELT, 0, b.u(.40f), b.v(.15f), b.u(.60f), b.v(.23f), false, mu, mv, px);
        part(g, parts, CarParts.Part.ENGINE, 0, b.u(.28f), b.v(.26f), b.u(.72f), b.v(.86f), false, mu, mv, px);
        if (CarParts.get(parts, CarParts.Part.ENGINE, 0) >= 0) {
            g.fill(b.u(.34f), b.v(.31f), b.u(.66f), b.v(.81f), DETAIL);   // the rocker cover
            for (int k = 0; k < 6; k++) {
                float fu = k % 2 == 0 ? .40f : .54f, fv = .36f + (k / 2) * .14f;
                part(g, parts, CarParts.Part.SPARK_PLUGS, 0, b.u(fu), b.v(fv), b.u(fu + .06f), b.v(fv + .07f), false, mu, mv, px);
            }
        }
        part(g, parts, CarParts.Part.ALTERNATOR, 0, b.u(.14f), b.v(.28f), b.u(.25f), b.v(.44f), false, mu, mv, px);
        part(g, parts, CarParts.Part.STARTER, 0, b.u(.14f), b.v(.68f), b.u(.25f), b.v(.84f), false, mu, mv, px);
        part(g, parts, CarParts.Part.BATTERY, 0, b.u(.76f), b.v(.28f), b.u(.95f), b.v(.52f), false, mu, mv, px);
        if (CarParts.get(parts, CarParts.Part.BATTERY, 0) >= 0) {
            g.fill(b.u(.79f), b.v(.25f), b.u(.83f), b.v(.29f), SHELL_EDGE);
            g.fill(b.u(.88f), b.v(.25f), b.u(.92f), b.v(.29f), SHELL_EDGE);
        }
        part(g, parts, CarParts.Part.BRAKE_FLUID, 0, b.u(.77f), b.v(.60f), b.u(.88f), b.v(.74f), false, mu, mv, px);
        part(g, parts, CarParts.Part.OIL, 0, b.u(.36f), b.v(.87f), b.u(.64f), b.v(.97f), true, mu, mv, px);
        part(g, parts, CarParts.Part.HEADLIGHT, 0, -W2 + 14, -L2 + 1, -W2 + 40, -L2 + 9, false, mu, mv, px);
        part(g, parts, CarParts.Part.HEADLIGHT, 1, W2 - 40, -L2 + 1, W2 - 14, -L2 + 9, false, mu, mv, px);
        // the bonnet over it all, see-through
        lid(g, parts, CarParts.Part.BONNET, bx0 - 2, by0 - 4, bx1 + 2, by1 + 2, mu, mv, px);

        // the cabin: radio and glovebox in the dash, the seats where the model's seats are, the doors along the sides
        part(g, parts, CarParts.Part.RADIO, 0, -10, dash + 3, 10, dash + 11, false, mu, mv, px);
        int gu = m != null && m.glovebox != null ? -Math.round(m.glovebox[0] * 100) : W2 / 2;
        part(g, parts, CarParts.Part.GLOVEBOX, 0, gu - 14, dash + 3, gu + 14, dash + 11, false, mu, mv, px);
        if (m != null && m.seats != null) {
            for (int i = 0; i < Math.min(8, m.seats.length); i++) {
                int su = -Math.round(m.seats[i][0] * 100), sv = -Math.round(m.seats[i][2] * 100);
                part(g, parts, CarParts.Part.SEAT, i, su - 20, sv - 16, su + 20, sv + 18, false, mu, mv, px);
                part(g, parts, CarParts.Part.SEAT, i, su - 20, sv + 20, su + 20, sv + 30, false, mu, mv, px);
                if (CarParts.get(parts, CarParts.Part.SEAT, i) >= 0) {
                    g.fill(su - 9, sv + 26, su + 9, sv + 34, DETAIL);   // the headrest
                    g.fill(su - 15, sv - 12, su - 15 + px, sv + 14, DETAIL);
                    g.fill(su + 15 - px, sv - 12, su + 15, sv + 14, DETAIL);
                }
            }
        }
        boolean four = CarParts.get(parts, CarParts.Part.DOOR, 2) != -2;
        int doorLen = four ? (roofBack - roofFront) / 2 : Math.round((roofBack - roofFront) * 0.6f);
        for (int i = 0; i < 4; i++) {
            if (CarParts.get(parts, CarParts.Part.DOOR, i) == -2) continue;
            int side = i % 2 == 0 ? -1 : 1;
            int v0 = i < 2 ? roofFront - 30 : roofFront + doorLen, v1 = i < 2 ? roofFront + doorLen - 2 : roofFront + 2 * doorLen - 30;
            int o = side * W2, n = side * (W2 - 8);
            part(g, parts, CarParts.Part.DOOR, i, Math.min(o, n), v0, Math.max(o, n), v1, false, mu, mv, px);
            if (i < 2) {
                int mo = side * (W2 + 14);
                g.fill(Math.min(o, mo), v0 + 4, Math.max(o, mo), v0 + 14, SHELL_EDGE);   // the wing mirror
            }
        }

        // the back: boot lid over the boot, tail lights at the corners
        int bootFront = Math.min(L2 - 50, Math.max(roofBack + 30, axle + 34));
        lid(g, parts, CarParts.Part.BOOT_LID, -W2 + 16, bootFront, W2 - 16, L2 - 6, mu, mv, px);
        part(g, parts, CarParts.Part.TAIL_LIGHT, 0, -W2 + 14, L2 - 9, -W2 + 36, L2 - 1, false, mu, mv, px);
        part(g, parts, CarParts.Part.TAIL_LIGHT, 1, W2 - 36, L2 - 9, W2 - 14, L2 - 1, false, mu, mv, px);

        // the part under the mouse: the last (topmost) box it's in
        for (int k = hits.size() - 1; k >= 0; k--) {
            int[] hb = hits.get(k);
            if (mu >= hb[1] && mu < hb[3] && mv >= hb[2] && mv < hb[4]) {
                hoverSlot = hb[0];
                break;
            }
        }
        if (hoverSlot >= 0) {
            for (int[] hb : hits) {
                if (hb[0] == hoverSlot) ring(g, hb[1] - px, hb[2] - px, hb[3] + px, hb[4] + px, px, 0xFFF2EFE8);
            }
            CarParts.Slot s = CarParts.SLOTS.get(hoverSlot);
            int c = CarParts.get(parts, hoverSlot);
            figureHover = s.title() + ": " + (c < 0 ? "missing" : c + "%");
        }
        g.pose().popMatrix();
    }

    /** Where the car is drawn: its middle on screen and pixels per centimetre (the UI test points the mouse with it). */
    public float[] figure() {
        return new float[]{fcx, fcy, ppc};
    }

    private static final int SHELL = 0xFF1C1F23, SHELL_EDGE = 0x73E8E4DA, ROOF = 0xFF16181B, GLASS = 0x26E8E4DA, DETAIL = 0x8C0B0C0E;

    /** The engine bay's box, for laying parts out by fractions of it. */
    private record Bay(int u0, int v0, int u1, int v1) {
        int u(float f) {
            return Math.round(u0 + (u1 - u0) * f);
        }

        int v(float f) {
            return Math.round(v0 + (v1 - v0) * f);
        }
    }

    /**
     * One piece of a part, filled by its condition: solid on top, half and dashed underneath, an empty dashed
     * outline when it's missing, nothing for a slot this model doesn't have. A part can be several pieces.
     */
    private void part(GuiGraphicsExtractor g, String parts, CarParts.Part part, int index, int u0, int v0, int u1, int v1,
                      boolean under, float mu, float mv, int px) {
        int c = CarParts.get(parts, part, index);
        if (c == -2) return;
        int slot = CarParts.slot(part, index);
        hits.add(new int[]{slot, u0, v0, u1, v1});
        if (c == -1) {
            dashed(g, u0, v0, u1, v1, px, 0x80E8E4DA);
            return;
        }
        int col = colour(c);
        if (under) {
            g.fill(u0, v0, u1, v1, (col & 0x00FFFFFF) | 0x70000000);
            dashed(g, u0, v0, u1, v1, px, col);
        } else {
            g.fill(u0, v0, u1, v1, (col & 0x00FFFFFF) | 0xE0000000);
            ring(g, u0, v0, u1, v1, px, 0xA00B0C0E);
        }
    }

    /** A lid (bonnet, boot lid): a see-through tint and a dashed edge, so what's under it still shows. */
    private void lid(GuiGraphicsExtractor g, String parts, CarParts.Part part, int u0, int v0, int u1, int v1, float mu, float mv, int px) {
        int c = CarParts.get(parts, part, 0);
        if (c == -2) return;
        int slot = CarParts.slot(part, 0);
        // only its edge takes the mouse, so the parts under it can still be pointed at
        int e = 6;
        hits.add(0, new int[]{slot, u0, v0, u1, v0 + e});
        hits.add(0, new int[]{slot, u0, v1 - e, u1, v1});
        hits.add(0, new int[]{slot, u0, v0, u0 + e, v1});
        hits.add(0, new int[]{slot, u1 - e, v0, u1, v1});
        if (c == -1) {
            dashed(g, u0, v0, u1, v1, px, 0x80E8E4DA);
            return;
        }
        int col = colour(c);
        g.fill(u0, v0, u1, v1, (col & 0x00FFFFFF) | 0x1C000000);
        dashed(g, u0, v0, u1, v1, 2 * px, col);
    }

    private static void ring(GuiGraphicsExtractor g, int u0, int v0, int u1, int v1, int t, int colour) {
        g.fill(u0, v0, u1, v0 + t, colour);
        g.fill(u0, v1 - t, u1, v1, colour);
        g.fill(u0, v0 + t, u0 + t, v1 - t, colour);
        g.fill(u1 - t, v0 + t, u1, v1 - t, colour);
    }

    private static void dashed(GuiGraphicsExtractor g, int u0, int v0, int u1, int v1, int t, int colour) {
        int on = 8, off = 5;
        for (int u = u0; u < u1; u += on + off) {
            g.fill(u, v0, Math.min(u + on, u1), v0 + t, colour);
            g.fill(u, v1 - t, Math.min(u + on, u1), v1, colour);
        }
        for (int v = v0; v < v1; v += on + off) {
            g.fill(u0, v, u0 + t, Math.min(v + on, v1), colour);
            g.fill(u1 - t, v, u1, Math.min(v + on, v1), colour);
        }
    }

    private void rows(GuiGraphicsExtractor g, String parts, int x, int y, int w, int h, int mx, int my) {
        buttons.clear();
        hover = null;
        int total = 0;
        g.enableScissor(x, y, x + w, y + h);
        int ry = y - Math.round(scroll);
        for (int area = 0; area < AREAS.length; area++) {
            KitDraw.caps(g, font, AREAS[area], x, ry + 3, KitDraw.TEXT, 0.8f);
            ry += ROW;
            total += ROW;
            for (int slot = 0; slot < CarParts.SLOTS.size(); slot++) {
                CarParts.Slot s = CarParts.SLOTS.get(slot);
                int c = CarParts.get(parts, slot);
                if (s.part().area.ordinal() != area || c == -2) {
                    continue;
                }
                KitDraw.text(g, font, s.title(), x + 4, ry + 3, KitDraw.TEXT, 0.78f);
                KitDraw.bar(g, x + 104, ry + 6, 40, 3, c < 0 ? 0 : c / 100f, Float.NaN);
                KitDraw.text(g, font, c < 0 ? "missing" : c + "%", x + 148, ry + 3, colour(c), 0.75f);
                int bx = x + w;
                Mechanics.Job[] jobs = Mechanics.jobs(s.part());
                for (int j = jobs.length - 1; j >= 0; j--) {
                    String label = Mechanics.label(s.part(), jobs[j]);
                    int bw = Math.round(KitDraw.width(font, label, 0.72f)) + 8;
                    bx -= bw + 3;
                    String why = Mechanics.why(minecraft.player, car, slot, jobs[j]);
                    boolean over = KitDraw.in(mx, my, bx, ry + 1, bw, 12) && my >= y && my < y + h;
                    g.fill(bx, ry + 1, bx + bw, ry + 13, why != null ? 0x08E8E4DA : over ? 0x40E8E4DA : 0x1AE8E4DA);
                    KitDraw.frame(g, bx, ry + 1, bw, 12, why != null ? 0x1FE8E4DA : 0x66E8E4DA);
                    KitDraw.text(g, font, label, bx + 4, ry + 4, why != null ? KitDraw.FAINT : KitDraw.TEXT, 0.72f);
                    if (over) {
                        hover = why != null ? why + "." : label + " the " + s.title().toLowerCase(Locale.ROOT) + ": "
                                + Math.round(Mechanics.ticks(minecraft.player, car, slot, jobs[j]) / 20f) + " s";
                    }
                    if (why == null) {
                        buttons.add(new int[]{slot, jobs[j].ordinal(), bx, ry + 1, bw, 12});
                    }
                }
                ry += ROW;
                total += ROW;
            }
            ry += 4;
            total += 4;
        }
        g.disableScissor();
        maxScroll = Math.max(0, total - h);
    }

    private int maxScroll;

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.clamp(scroll - (float) sy * ROW * 2, 0, maxScroll);
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return true;
        for (int[] b : buttons) {
            if (event.x() >= b[2] && event.x() < b[2] + b[4] && event.y() >= b[3] && event.y() < b[3] + b[5]) {
                ClientPlayNetworking.send(new Mechanics.Work(car.getId(), b[0], b[1]));
                com.zomboidcraft.client.sound.UiSounds.play(com.zomboidcraft.client.sound.UiSounds.CLICK);
                if (!minecraft.player.isCreative()) {
                    onClose();      // stand back and work: the bar shows over the belt
                }
                return true;
            }
        }
        return true;
    }
}
