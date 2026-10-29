package com.zomboidcraft.client.lifts;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.lifts.LiftCar;
import com.zomboidcraft.world.lifts.LiftShaft;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;

/**
 * Draws a lift: the car where the client's running of it has it this frame (an 80s car: brushed
 * steel sides, a wood-grain back wall over a scuffed kick plate, rubber floor, a lit ceiling panel,
 * handrails and the operating panel by the doors, its doors sliding; see refs/city/lifts), the crosshead and ropes over
 * it, the landing doors sliding at the floor it's opening on, and the amber floor indicator over
 * every landing's doors (and inside the car) counting the floors as it goes.
 *
 * Everything is worked out in the shaft's own frame: u along the doors to the right (looking at
 * them from inside), v back from them, y up from the car's floor, then put in the world.
 */
public class LiftRenderer extends EntityRenderer<LiftCar, LiftRenderer.State> {
    static final Identifier ATLAS = ZomboidCraft.id("textures/entity/lift/atlas.png");
    private static final int FULL = 0xF000F0;
    /** Tiles in the atlas (8 x 8 of 64 px; see tools/lifts/paint.py). */
    static final int WALL = 0, WOOD = 1, FLOOR = 2, CEIL = 3, LIGHT = 4, DOOR = 5, ROOF = 6, RAIL = 7, PANEL = 8,
            BTN_OFF = 9, BTN_ON = 10, SEG_ON = 11, SEG_OFF = 12, BEZEL = 13, CABLE = 14, BEAM = 15, KICK = 16, SILL = 17,
            FRAME = 18, LABEL = 19, NUMERAL = 20, BTN_OPEN = 21, BTN_CLOSE = 22, BTN_ALARM = 23;
    /** Tiles laid about one to a block on big faces (walls, floor, rails), so they stay sharp. */
    private static final java.util.Set<Integer> TILED = java.util.Set.of(WALL, WOOD, FLOOR, CEIL, ROOF, KICK, CABLE, BEAM, RAIL, SILL, FRAME);
    /** Segments a to g lit for each character. */
    private static final int[] DIGITS = {0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F};

    public static class State extends EntityRenderState {
        LiftShaft shaft;
        /** The car's floor, relative to where the entity's drawn. */
        double car;
        float doors;
        int at, dir, showing;
        boolean[] called = new boolean[0];
        int inside, outside;
        boolean shaders;
        /** The landings near enough to draw their indicators. */
        int lo, hi;
    }

    public LiftRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    protected AABB getBoundingBoxForCulling(LiftCar car, float partialTick) {
        LiftShaft s = car.shaft();
        if (s == null) {
            return car.getBoundingBox();
        }
        return new AABB(s.minX - 2, s.landings[0] - 1, s.minZ - 2, s.maxX + 2, s.my + 1, s.maxZ + 2);
    }

    @Override
    public void extractRenderState(LiftCar car, State state, float partialTick) {
        super.extractRenderState(car, state, partialTick);
        LiftShaft s = car.shaft();
        state.shaft = s;
        if (s == null) {
            return;
        }
        double y = car.yAt(partialTick);
        state.car = y - state.y;
        float d = car.doorsO + (car.doors - car.doorsO) * partialTick;
        state.doors = d * d * (3 - 2 * d);
        state.at = car.at();
        state.dir = car.dir();
        state.showing = s.showing(y, Math.abs(car.speed()) > 1e-4 ? state.dir : 0);
        if (state.called.length != s.landings.length) {
            state.called = new boolean[s.landings.length];
        }
        for (int i = 0; i < s.landings.length; i++) {
            state.called[i] = car.called(i);
        }
        ClientLevel level = Minecraft.getInstance().level;
        int out = FULL;
        if (level != null) {
            BlockPos pos = BlockPos.containing(s.centreX(), y + 3.5, s.centreZ());
            out = level.getBrightness(LightLayer.BLOCK, pos) << 4 | level.getBrightness(LightLayer.SKY, pos) << 20;
        }
        state.outside = out;
        // its own ceiling light lights it inside, like a room indoors
        state.inside = Math.max(12, (out >> 4) & 0xF) << 4;
        state.shaders = com.zomboidcraft.client.cars.CarLights.shaderPackOn();
        double eye = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getY() : y;
        state.lo = s.landings.length;
        state.hi = -1;
        for (int i = 0; i < s.landings.length; i++) {
            if (Math.abs(s.landings[i] - eye) < 48) {
                state.lo = Math.min(state.lo, i);
                state.hi = i;
            }
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.shaft == null) {
            return;
        }
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(ATLAS), (pose, out) -> draw(state, pose, out, false));
        if (state.shaders) {
            // with a shader pack the lit parts glow the way a beacon's beam does
            collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(ATLAS, false), (pose, out) -> draw(state, pose, out, true));
        }
    }

    // ------------------------------------------------------------------ the drawing

    /** One pass: everything (glow pass false), or just what glows, for shader packs. */
    private static void draw(State st, PoseStack.Pose pose, VertexConsumer out, boolean glowPass) {
        Painter p = new Painter(st, pose, out, glowPass);
        LiftShaft s = st.shaft;
        int w = s.w, d = s.d;
        double c = st.car;
        // ---- the car
        p.light = st.inside;
        p.zone(0.04, 0.0, c, w - 0.04, 0.15, c, SILL, 'y');
        p.zone(0.04, 0.15, c, w - 0.04, d - 0.04, c, FLOOR, 'y');
        // side walls, inner faces: kick plate, then steel
        for (int side = 0; side < 2; side++) {
            double u = side == 0 ? 0.1 : w - 0.1;
            p.wallU(u, 0.15, d - 0.1, c, c + 0.3, KICK);
            p.wallU(u, 0.15, d - 0.1, c + 0.3, c + LiftShaft.CEILING, WALL);
        }
        // back wall: wood-grain laminate over a kick plate, with the handrail
        p.wallV(d - 0.1, 0.1, w - 0.1, c, c + 0.3, KICK);
        p.wallV(d - 0.1, 0.1, w - 0.1, c + 0.3, c + LiftShaft.CEILING, WOOD);
        p.zone(0.3, d - 0.2, c + 0.95, w - 0.3, d - 0.1, c + 0.95, RAIL, 'y');
        p.wallV(d - 0.2, 0.3, w - 0.3, c + 0.88, c + 0.95, RAIL);
        // the transom over the doors, the ceiling and its light
        p.wallV(0.15, 0.04, w - 0.04, c + LiftShaft.DOOR, c + LiftShaft.CEILING, WALL);
        p.zone(0.04, 0.04, c + LiftShaft.CEILING, w - 0.04, d - 0.04, c + LiftShaft.CEILING, CEIL, 'y');
        p.glow = true;
        p.zone(w / 2.0 - 0.45, d / 2.0 - 0.55, c + LiftShaft.CEILING - 0.01, w / 2.0 + 0.45, d / 2.0 + 0.55,
                c + LiftShaft.CEILING - 0.01, LIGHT, 'y');
        p.glow = false;
        // handrails along both sides as well, on their brackets
        for (int side = 0; side < 2; side++) {
            double u = side == 0 ? 0.2 : w - 0.2, wall = side == 0 ? 0.1 : w - 0.1;
            double v0 = side == 1 ? 0.8 : 0.4;
            p.zone(Math.min(u, wall), v0, c + 0.95, Math.max(u, wall), d - 0.3, c + 0.95, RAIL, 'y');
            p.wallU(u, v0, d - 0.3, c + 0.88, c + 0.95, RAIL);
        }
        panel(p, st, w - 0.11, c);
        // the car's indicator over the doors, inside
        indicator(p, st, w / 2.0 - 0.24, w / 2.0 + 0.24, 0.155, c + 2.24, c + 2.54, true);
        // ---- outside the car: its doors, roof, crosshead and ropes
        double open = st.doors * (w / 2.0 - 0.05);
        leaves(p, 0.1, 0.15, c, w, open, true);
        p.light = st.outside;
        p.zone(0, 0, c - 0.15, w, d, c - 0.15, ROOF, 'y');
        p.zone(0.02, 0.02, c + LiftShaft.ROOF, w - 0.02, d - 0.02, c + LiftShaft.ROOF, ROOF, 'y');
        p.box(0.08, d / 2.0 - 0.12, c + LiftShaft.ROOF, w - 0.08, d / 2.0 + 0.12, c + LiftShaft.ROOF + 0.24, BEAM);
        double ropeTop = s.my - st.y, top = c + LiftShaft.ROOF + 0.24;
        if (ropeTop > top) {
            for (int k = -1; k <= 1; k += 2) {
                double u = w / 2.0 + k * 0.1;
                p.wallU(u, d / 2.0 - 0.02, d / 2.0 + 0.02, top, top + (ropeTop - top), CABLE);
            }
        }
        // ---- the landings: doors sliding at the floor it's opening on, and every indicator in sight
        if (st.at >= 0 && st.doors > 0.001 && st.doors < 0.999) {
            double ly = s.landings[st.at] - st.y;
            leaves(p, -1 + 0.0625, -1 + 0.1875, ly, w, open, false);
        }
        for (int i = st.lo; i <= st.hi; i++) {
            double ly = s.landings[i] - st.y;
            p.light = st.outside;
            // the doorway's stainless frame, standing just proud of the wall
            double fv = -1.02;
            p.box(-0.1, fv, ly, 0.0, -1.0, ly + LiftShaft.DOOR + 0.1, FRAME);
            p.box(w, fv, ly, w + 0.1, -1.0, ly + LiftShaft.DOOR + 0.1, FRAME);
            p.box(0.0, fv, ly + LiftShaft.DOOR, w, -1.0, ly + LiftShaft.DOOR + 0.1, FRAME);
            if (s.pitch(i) >= 4 || i == s.landings.length - 1) {
                indicator(p, st, w / 2.0 - 0.28, w / 2.0 + 0.28, -1.025, ly + 2.3, ly + 2.62, false);
            } else {
                indicator(p, st, w + 0.2, w + 0.7, -1.025, ly + 1.68, ly + 1.96, false);
            }
        }
    }

    /** The two door leaves (car or landing), opened by open on each side. */
    private static void leaves(Painter p, double v0, double v1, double y, int w, double open, boolean car) {
        double half = w / 2.0;
        double x0 = car ? 0.1 : 0, x1 = car ? w - 0.1 : w;
        p.box(x0 - open, v0, y, half - open, v1, y + LiftShaft.DOOR, DOOR);
        p.box(half + open, v0, y, x1 + open, v1, y + LiftShaft.DOOR, DOOR);
    }

    /** A flat face to draw on: a rectangle from a0 to a1 along it (from the viewer's left) and y0 to y1 up it. */
    private interface Face {
        void rect(double a0, double a1, double y0, double y1, int tile);
    }

    /** The plane v, seen from inside the car (+u on the viewer's right) or from a landing (+u on the left). */
    private static Face faceV(Painter p, double v, double u0, double u1, boolean intoCar) {
        return (a0, a1, y0, y1, tile) -> {
            if (intoCar) {
                p.wallV(v, u0 + a0, u0 + a1, y0, y1, tile);
            } else {
                p.wallV(v, u1 - a1, u1 - a0, y0, y1, tile);
            }
        };
    }

    /**
     * An amber seven-segment display: the floor the car's at (or passing) and an arrow the way it's
     * going, on a black glass face across u0 .. u1, y0 .. y1, in the plane v (facing out of the
     * shaft at a landing, or into the car).
     */
    private static void indicator(Painter p, State st, double u0, double u1, double v, double y0, double y1, boolean intoCar) {
        p.wallV(v, u0 - 0.03, u1 + 0.03, y0 - 0.03, y1 + 0.03, FRAME);
        p.wallV(v + (intoCar ? 0.004 : -0.004), u0, u1, y0, y1, BEZEL);
        Face f = faceV(p, v + (intoCar ? 0.008 : -0.008), u0, u1, intoCar);
        display(p, f, st, u1 - u0, y0, y1);
    }

    /** The arrow and two digits centred on a face width wide, between y0 and y1. */
    private static void display(Painter p, Face f, State st, double width, double y0, double y1) {
        double h = (y1 - y0) * 0.72, cy = y0 + (y1 - y0) * 0.14, cw = h * 0.55, gap = cw * 0.3;
        double arrowW = cw * 0.9;
        double total = arrowW + gap + 2 * cw + gap;
        double start = (width - total) / 2;
        arrow(p, f, st.dir, start, cy, h, arrowW);
        digits(p, f, st.shaft.label(st.showing), start + arrowW + gap, cy, h, cw, SEG_ON, SEG_OFF);
    }

    private static void arrow(Painter p, Face f, int dir, double a0, double y0, double h, double w) {
        if (dir == 0) {
            return;
        }
        double mid = a0 + w / 2;
        p.glow = true;
        for (int k = 0; k < 4; k++) {
            // a stack of bars, widest at the arrow's foot
            double bw = w * (k + 1) / 4.0, by = dir > 0 ? y0 + h * (0.75 - k * 0.18) : y0 + h * (0.1 + k * 0.18);
            f.rect(mid - bw / 2, mid + bw / 2, by, by + h * 0.14, SEG_ON);
        }
        p.glow = false;
    }

    /** Two characters (the last two of label) from a0, each cw wide; off segments drawn with off (or not, if off < 0). */
    private static void digits(Painter p, Face f, String label, double a0, double y0, double h, double cw, int on, int off) {
        String two = label.length() >= 2 ? label.substring(label.length() - 2) : " " + label;
        double gap = cw * 0.15;
        for (int k = 0; k < 2; k++) {
            digit(p, f, two.charAt(k), a0 + k * (cw + gap), y0, h, cw, on, off);
        }
    }

    private static void digit(Painter p, Face f, char ch, double x0, double y0, double h, double wd, int onTile, int offTile) {
        int mask = ch >= '0' && ch <= '9' ? DIGITS[ch - '0'] : ch == 'B' ? 0x7C : ch == '-' ? 0x40 : 0;
        double t = wd * 0.2, ym = y0 + h / 2, y1 = y0 + h;
        // segment boxes from the viewer's left
        double[][] seg = {
                {t, wd - t, y1 - t, y1},            // a
                {wd - t, wd, ym, y1 - t},           // b
                {wd - t, wd, y0 + t, ym},           // c
                {t, wd - t, y0, y0 + t},            // d
                {0, t, y0 + t, ym},                 // e
                {0, t, ym, y1 - t},                 // f
                {t, wd - t, ym - t / 2, ym + t / 2} // g
        };
        for (int i = 0; i < 7; i++) {
            boolean on = (mask >> i & 1) != 0;
            if (!on && offTile < 0) {
                continue;
            }
            p.glow = on && onTile == SEG_ON;
            f.rect(x0 + seg[i][0], x0 + seg[i][1], seg[i][2], seg[i][3], on ? onTile : offTile);
        }
        p.glow = false;
    }

    /**
     * The car operating panel on the right-hand wall by the doors, as in the photos: a tall
     * stainless strip with its own floor display at the top, the floor buttons in two columns each
     * on a black number label, then door open, door close and the yellow alarm button.
     */
    private static void panel(Painter p, State st, double pu, double c) {
        LiftShaft s = st.shaft;
        int n = s.landings.length, rows = (n + 1) / 2;
        double v0 = 0.28, v1 = 0.62, y0 = c + 0.8, y1 = c + 1.95;
        p.wallU(pu, v0, v1, y0, y1, PANEL);
        // looking at the right-hand wall from inside, the doors (low v) are on the left
        Face f = (a0, a1, b0, b1, tile) -> p.wallU(pu - 0.006, v0 + a0, v0 + a1, b0, b1, tile);
        Face top = (a0, a1, b0, b1, tile) -> p.wallU(pu - 0.009, v0 + a0, v0 + a1, b0, b1, tile);
        double width = v1 - v0;
        // the display at the top
        f.rect(0.06, width - 0.06, y1 - 0.15, y1 - 0.04, BEZEL);
        display(p, top, st, width, y1 - 0.14, y1 - 0.05);
        // the floor buttons
        double fy1 = y1 - 0.2, fy0 = y0 + 0.2;
        double cell = Math.min((fy1 - fy0) / rows, 0.09), bs = cell * 0.62;
        for (int i = 0; i < n; i++) {
            int col = i % 2, row = i / 2;
            double a = 0.03 + col * (width / 2), b = fy0 + row * cell + (cell - bs) / 2;
            double lw = width / 2 - 0.06 - bs;
            f.rect(a, a + lw, b + bs * 0.1, b + bs * 0.9, LABEL);
            digits(p, top, s.label(i), a + lw * 0.1, b + bs * 0.22, bs * 0.56, lw * 0.32, NUMERAL, -1);
            p.glow = st.called[i];
            f.rect(a + lw + 0.01, a + lw + 0.01 + bs, b, b + bs, st.called[i] ? BTN_ON : BTN_OFF);
            p.glow = false;
        }
        // door open, door close, alarm
        double bb = 0.055, by = y0 + 0.07;
        f.rect(0.05, 0.05 + bb, by, by + bb, BTN_OPEN);
        f.rect(width / 2 - bb / 2, width / 2 + bb / 2, by, by + bb, BTN_CLOSE);
        f.rect(width - 0.05 - bb, width - 0.05, by, by + bb, BTN_ALARM);
    }

    // ------------------------------------------------------------------ quads

    /** Puts quads in the shaft's frame into the world, each both ways round so it shows from either side. */
    private static final class Painter {
        final LiftShaft s;
        final PoseStack.Pose pose;
        final VertexConsumer out;
        final boolean glowPass, shaders;
        final double ex, ey, ez;
        int light;
        boolean glow;

        Painter(State st, PoseStack.Pose pose, VertexConsumer out, boolean glowPass) {
            this.s = st.shaft;
            this.pose = pose;
            this.out = out;
            this.glowPass = glowPass;
            this.shaders = st.shaders;
            this.ex = st.x;
            this.ey = st.y;
            this.ez = st.z;
        }

        private boolean skip() {
            // with a shader pack the glowing bits are drawn in the glow pass only; without one, lit at full in the one pass
            return glowPass ? !glow : glow && shaders;
        }

        /** A horizontal patch at height y (relative), u0 .. u1 by v0 .. v1. */
        void zone(double u0, double v0, double y, double u1, double v1, double y1, int tile, char axis) {
            if (skip()) {
                return;
            }
            quad(new double[][] {{u0, y, v0}, {u1, y, v0}, {u1, y, v1}, {u0, y, v1}}, tile, 0, 1, 0);
        }

        /** A face in the plane u, across v0 .. v1 and y0 .. y1. */
        void wallU(double u, double v0, double v1, double y0, double y1, int tile) {
            if (skip()) {
                return;
            }
            quad(new double[][] {{u, y0, v0}, {u, y0, v1}, {u, y1, v1}, {u, y1, v0}}, tile, 1, 0, 0);
        }

        /** A face in the plane v, across u0 .. u1 and y0 .. y1. */
        void wallV(double v, double u0, double u1, double y0, double y1, int tile) {
            if (skip()) {
                return;
            }
            quad(new double[][] {{u0, y0, v}, {u1, y0, v}, {u1, y1, v}, {u0, y1, v}}, tile, 0, 0, 1);
        }

        /** A box's six faces. */
        void box(double u0, double v0, double y0, double u1, double v1, double y1, int tile) {
            zone(u0, v0, y0, u1, v1, y0, tile, 'y');
            zone(u0, v0, y1, u1, v1, y1, tile, 'y');
            wallU(u0, v0, v1, y0, y1, tile);
            wallU(u1, v0, v1, y0, y1, tile);
            wallV(v0, u0, u1, y0, y1, tile);
            wallV(v1, u0, u1, y0, y1, tile);
        }

        /** Four corners {u, y, v} in order round the face, its normal in the frame (nu, ny, nv); a tiled face is laid a tile to about a block. */
        private void quad(double[][] c, int tile, double nu, double ny, double nv) {
            int na = TILED.contains(tile) ? (int) Math.max(1, Math.round(dist(c[0], c[1]))) : 1;
            int nb = TILED.contains(tile) ? (int) Math.max(1, Math.round(dist(c[0], c[3]))) : 1;
            if (na == 1 && nb == 1) {
                piece(c, tile, nu, ny, nv);
                return;
            }
            for (int i = 0; i < na; i++) {
                for (int j = 0; j < nb; j++) {
                    piece(new double[][] {lerp(c, (double) i / na, (double) j / nb), lerp(c, (double) (i + 1) / na, (double) j / nb),
                            lerp(c, (double) (i + 1) / na, (double) (j + 1) / nb), lerp(c, (double) i / na, (double) (j + 1) / nb)},
                            tile, nu, ny, nv);
                }
            }
        }

        private static double dist(double[] a, double[] b) {
            return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
        }

        /** A point on the face: s along corner 0 to 1, t along corner 0 to 3 (it's a parallelogram). */
        private static double[] lerp(double[][] c, double s, double t) {
            double[] r = new double[3];
            for (int k = 0; k < 3; k++) {
                r[k] = c[0][k] + (c[1][k] - c[0][k]) * s + (c[3][k] - c[0][k]) * t;
            }
            return r;
        }

        private void piece(double[][] c, int tile, double nu, double ny, double nv) {
            float tu = (tile % 8) / 8f, tv = (tile / 8) / 8f, ts = 1 / 8f, e = 0.5f / 512f;
            float[][] uv = {{tu + e, tv + ts - e}, {tu + ts - e, tv + ts - e}, {tu + ts - e, tv + e}, {tu + e, tv + e}};
            int lit = glow ? FULL : light;
            // the frame's normal in the world
            float nx = (float) (nu * s.r.getStepX() + nv * s.b.getStepX()), nz = (float) (nu * s.r.getStepZ() + nv * s.b.getStepZ());
            float nyf = (float) ny;
            for (int side = 0; side < 2; side++) {
                for (int k = 0; k < 4; k++) {
                    int i = side == 0 ? k : 3 - k;
                    double u = c[i][0], y = c[i][1], v = c[i][2];
                    float x = (float) (s.wx(u, v) - ex), z = (float) (s.wz(u, v) - ez);
                    out.addVertex(pose, x, (float) (y), z)
                            .setColor(-1)
                            .setUv(uv[i][0], uv[i][1])
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(lit)
                            .setNormal(pose, side == 0 ? nx : -nx, side == 0 ? nyf : -nyf, side == 0 ? nz : -nz);
                }
            }
        }
    }
}
