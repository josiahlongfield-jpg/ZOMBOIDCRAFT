package com.zomboidcraft.client.trains;

import com.zomboidcraft.world.trains.Line;
import com.zomboidcraft.world.trains.TrainCar;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A train car's model, built from boxes in the car's own frame and skinned one block to a tile from
 * the train atlas (tools/trains/paint.py): the shell with its windows, doors that slide, the inside
 * (floor, benches, poles, rails, ads, light strips), the running gear, and on the first car the cab
 * with OTTO in it. Subway cars are scruffy stainless steel with a red stripe; railway cars are old
 * green and cream. Both are tagged with graffiti.
 */
final class TrainMesh {
    static final int ATLAS = 512, TILE = 32;

    /** A material: the atlas row and the columns it picks among (repeats weight a pick), and how it's drawn. */
    record Mat(int row, int[] cols, boolean glass) {
        static Mat of(int row, int... cols) {
            return new Mat(row, cols, false);
        }

        int pick(int hash) {
            return cols[Math.floorMod(hash, cols.length)];
        }
    }

    // the subway's skin
    static final Mat STEEL = Mat.of(0, 0, 1, 2, 3, 4, 5, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
    static final Mat STEEL_CLEAN = Mat.of(0, 0, 1, 2, 3, 4, 5, 12, 13);
    static final Mat STRIPE = Mat.of(2, 0, 1, 2, 3);
    // the railway's skin
    static final Mat GREEN = Mat.of(1, 0, 1, 2, 3, 4, 5, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
    static final Mat GREEN_CLEAN = Mat.of(1, 0, 1, 2, 3, 4, 5, 12, 13);
    static final Mat CREAM = Mat.of(2, 4, 5, 6, 7);
    static final Mat ROOF = Mat.of(2, 8, 9, 8, 9, 10, 11);
    static final Mat UNDER = Mat.of(2, 12, 13, 14, 15);
    static final Mat WALL_IN = Mat.of(3, 0, 1, 0, 1, 0, 1, 2, 3);
    static final Mat FLOOR = Mat.of(3, 4, 5, 4, 5, 4, 5, 6, 7);
    static final Mat CEILING = Mat.of(3, 8, 9);
    static final Mat LIGHT = Mat.of(3, 10, 10, 10, 10, 10, 11);
    static final Mat SEAT_ORANGE = Mat.of(3, 12, 12, 12, 13);
    static final Mat SEAT_BLUE = Mat.of(3, 14, 14, 14, 15);
    static final Mat DOOR_STEEL_LOW = Mat.of(4, 0), DOOR_STEEL_HIGH = Mat.of(4, 1);
    static final Mat DOOR_GREEN_LOW = Mat.of(4, 2), DOOR_GREEN_HIGH = Mat.of(4, 3);
    static final Mat GLASS = new Mat(4, new int[] {4, 4, 4, 4, 4, 5}, true);
    static final Mat HEADLIGHT = Mat.of(4, 6), SIGN = Mat.of(4, 7), NOSE = Mat.of(4, 8), RUBBER = Mat.of(4, 9);
    static final Mat CHROME = Mat.of(4, 10), BOGIE = Mat.of(4, 11), WHEEL = Mat.of(4, 12);
    static final Mat RAIL = Mat.of(4, 13), SLEEPER = Mat.of(4, 14), SLEEPER_CONCRETE = Mat.of(4, 15);
    static final Mat OTTO_SHELL = Mat.of(5, 0), OTTO_DARK = Mat.of(5, 1), OTTO_FACE = Mat.of(5, 2), OTTO_TALK = Mat.of(5, 3);
    static final Mat OTTO_CHEST = Mat.of(5, 4), BELLOWS = Mat.of(5, 6), WARNING = Mat.of(5, 7), ADS = Mat.of(5, 8, 9, 10, 11);
    static final Mat MAP = Mat.of(5, 12), EMERGENCY = Mat.of(5, 13), HAZARD = Mat.of(5, 14), LAMP = Mat.of(5, 15);

    /** How a quad is lit: by the world outside, by the car's own lights inside, or glowing itself. */
    static final byte OUTSIDE = 0, INSIDE = 1, GLOW = 2;
    /** What moves it: nothing, a door leaf (PART_DOOR + leaf number), OTTO's head, OTTO's face when he talks. */
    static final int STATIC = 0, PART_HEAD = 1, PART_TALK = 2, PART_DOOR = 100;

    static final class Quad {
        final float[] p = new float[12];
        final float[] uv = new float[8];
        float nx, ny, nz;
        byte light;
        int part;
        boolean glass;
    }

    /** A door leaf: which side of the car it's on and which way it slides open (along z). */
    record Leaf(int side, float slide) {
    }

    final List<Quad> quads = new ArrayList<>();
    final List<Leaf> leaves = new ArrayList<>();
    /** OTTO's head pivot, in the car's frame (front car only). */
    float[] headPivot;

    private int seed;
    private int part = STATIC;
    private byte light = OUTSIDE;

    private static final Map<String, TrainMesh> CACHE = new HashMap<>();

    static TrainMesh get(Line.Kind kind, boolean front, boolean back) {
        return CACHE.computeIfAbsent(kind + "/" + front + "/" + back, k -> new TrainMesh(kind, front, back));
    }

    private TrainMesh(Line.Kind kind, boolean front, boolean back) {
        boolean subway = kind == Line.Kind.SUBWAY;
        seed = (subway ? 7 : 3) + (front ? 11 : 0) + (back ? 23 : 0);
        Mat skin = subway ? STEEL : GREEN, clean = subway ? STEEL_CLEAN : GREEN_CLEAN, band = subway ? STRIPE : CREAM;
        Mat seat = subway ? SEAT_ORANGE : SEAT_BLUE;
        double w = TrainCar.HALF_WIDTH, t = TrainCar.WALL, h = TrainCar.HALF, ceil = TrainCar.CEILING;
        double winLow = 1.1, winHigh = 2.3;

        // ---- the shell
        light = OUTSIDE;
        // skirts below the floor and the underframe
        box(-w, -0.85, -h, -w + 0.1, 0, h, skin, null, null, null, null, null);
        box(w - 0.1, -0.85, -h, w, 0, h, null, skin, null, null, null, null);
        box(-w + 0.1, -0.5, -h, w - 0.1, -0.05, h, null, null, UNDER, null, UNDER, UNDER);
        // roof
        box(-w, ceil, -h, w, TrainCar.ROOF, h, skin, skin, null, ROOF, skin, skin);
        box(-w + 0.4, TrainCar.ROOF, -h + 0.5, w - 0.4, TrainCar.ROOF + 0.12, h - 0.5, ROOF, ROOF, null, ROOF, ROOF, ROOF);
        // side walls, both sides, door gaps left for the leaves
        for (int side = -1; side <= 1; side += 2) {
            double x0 = side > 0 ? w - t : -w, x1 = side > 0 ? w : -w + t;
            List<double[]> spans = new ArrayList<>();
            double z = -h;
            for (double d : TrainCar.DOORS) {
                spans.add(new double[] {z, d - TrainCar.DOOR_HALF});
                // over the doorway
                sideBox(side, x0, x1, TrainCar.DOOR_HEIGHT, ceil, d - TrainCar.DOOR_HALF, d + TrainCar.DOOR_HALF, band, WALL_IN);
                z = d + TrainCar.DOOR_HALF;
            }
            spans.add(new double[] {z, h});
            for (double[] span : spans) {
                double za = span[0], zb = span[1];
                boolean cab = front && zb > TrainCar.CAB;
                sideBox(side, x0, x1, 0, winLow, za, zb, skin, WALL_IN);
                sideBox(side, x0, x1, winHigh, ceil, za, zb, band, WALL_IN);
                // window pillars about every two blocks, glass between
                double len = zb - za;
                int panes = Math.max(1, (int) Math.round(len / 2));
                double step = len / panes;
                for (int i = 0; i <= panes; i++) {
                    double pz = za + step * i;
                    double p0 = Math.max(za, pz - 0.12), p1 = Math.min(zb, pz + 0.12);
                    sideBox(side, x0, x1, winLow, winHigh, p0, p1, clean, WALL_IN);
                }
                for (int i = 0; i < panes; i++) {
                    double g0 = za + step * i + 0.12, g1 = za + step * (i + 1) - 0.12;
                    if (g1 > g0) {
                        glassX((x0 + x1) / 2, winLow, winHigh, g0, g1);
                    }
                }
                // inside: ads over the windows, benches under them
                light = INSIDE;
                if (!cab) {
                    double ix = side > 0 ? x0 : x1;
                    panel(side, ix, winHigh + 0.05, ceil - 0.05, za + 0.3, zb - 0.3, ADS);
                }
                light = OUTSIDE;
            }
            // door leaves: two per doorway, sliding apart along the inside of the wall
            for (double d : TrainCar.DOORS) {
                for (int k = -1; k <= 1; k += 2) {
                    leaves.add(new Leaf(side, k));
                    part = PART_DOOR + leaves.size() - 1;
                    double lx0 = side > 0 ? w - t - 0.06 : -w + t, lx1 = side > 0 ? w - t : -w + t + 0.06;
                    double za = k < 0 ? d - TrainCar.DOOR_HALF : d, zb = k < 0 ? d : d + TrainCar.DOOR_HALF;
                    Mat low = subway ? DOOR_STEEL_LOW : DOOR_GREEN_LOW, high = subway ? DOOR_STEEL_HIGH : DOOR_GREEN_HIGH;
                    // outer and inner faces: the door tiles low, the windowed tile high
                    leafFaces(side, lx0, lx1, za, zb, low, high);
                    part = STATIC;
                }
            }
        }
        // ends
        endWall(h - t, h, front, true, skin, subway);
        endWall(-h, -h + t, back, false, skin, subway);
        if (!front) {
            bellows(h, h + 0.3);
        }
        if (!back) {
            bellows(-h - 0.3, -h);
        }
        // running gear: two bogies with four wheels each
        for (double bz : new double[] {-TrainCar.BOGIE, TrainCar.BOGIE}) {
            box(-1.3, -0.95, bz - 1.3, 1.3, -0.45, bz + 1.3, BOGIE, BOGIE, BOGIE, BOGIE, BOGIE, BOGIE);
            for (double wz : new double[] {bz - 0.8, bz + 0.8}) {
                for (int sx = -1; sx <= 1; sx += 2) {
                    double c = sx * 0.72;
                    box(c - 0.1, -1.0, wz - 0.45, c + 0.1, -0.1, wz + 0.45, WHEEL, WHEEL, null, null, null, null);
                }
            }
        }

        // ---- the inside
        light = INSIDE;
        box(-w + t, -0.05, -h + t, w - t, 0, h - t, null, null, null, FLOOR, null, null);
        // ceiling with the light strip down the middle
        box(-w + t, ceil - 0.02, -h + t, -0.5, ceil, h - t, null, null, CEILING, null, null, null);
        box(0.5, ceil - 0.02, -h + t, w - t, ceil, h - t, null, null, CEILING, null, null, null);
        light = GLOW;
        box(-0.5, ceil - 0.06, -h + t, 0.5, ceil, h - t, null, null, LIGHT, null, null, null);
        light = INSIDE;
        // benches along the walls, with backs
        for (int side = -1; side <= 1; side += 2) {
            for (double[] run : TrainCar.seatRuns(front)) {
                double x0 = side > 0 ? w - t - TrainCar.SEAT_DEPTH : -w + t, x1 = side > 0 ? w - t : -w + t + TrainCar.SEAT_DEPTH;
                box(x0, 0, run[0], x1, TrainCar.SEAT_HEIGHT, run[1], seat, seat, null, seat, seat, seat);
                double bx0 = side > 0 ? w - t - 0.12 : -w + t, bx1 = side > 0 ? w - t : -w + t + 0.12;
                box(bx0, TrainCar.SEAT_HEIGHT, run[0], bx1, 1.05, run[1], seat, seat, null, seat, seat, seat);
            }
        }
        // grab poles by the doors and down the middle, and the overhead rails
        for (double d : TrainCar.DOORS) {
            for (int side = -1; side <= 1; side += 2) {
                pole(side * 1.3, d - 1.25);
                pole(side * 1.3, d + 1.25);
            }
        }
        for (double z : new double[] {-3.5, 3.5, -9.5}) {
            pole(0, z);
        }
        for (int side = -1; side <= 1; side += 2) {
            box(side * 1.05 - 0.03, 2.55, -h + 0.4, side * 1.05 + 0.03, 2.61, (front ? TrainCar.CAB : h) - 0.4,
                    CHROME, CHROME, CHROME, CHROME, CHROME, CHROME);
        }
        // a line map and a warning sign on the end walls
        panel(0, 0, 2.2, 2.8, -1.8, -0.9, MAP);
        light = OUTSIDE;

        if (front) {
            cab(subway);
        }
        if (back) {
            // a rear cab window and tail lamps
            light = GLOW;
            box(-1.6, 0.35, -h - 0.02, -1.25, 0.65, -h, null, null, null, null, HEADLIGHT, null);
            box(1.25, 0.35, -h - 0.02, 1.6, 0.65, -h, null, null, null, null, HEADLIGHT, null);
            light = OUTSIDE;
        }
    }

    // ------------------------------------------------------------------ pieces

    /** A run of side wall: outer face skinned, inner face panelled, top and bottom edges plain. */
    private void sideBox(int side, double x0, double x1, double y0, double y1, double z0, double z1, Mat out, Mat in) {
        if (y1 <= y0 || z1 <= z0) {
            return;
        }
        byte was = light;
        light = OUTSIDE;
        box(x0, y0, z0, x1, y1, z1, side < 0 ? out : null, side > 0 ? out : null, out, out, out, out);
        light = INSIDE;
        box(x0, y0, z0, x1, y1, z1, side > 0 ? in : null, side < 0 ? in : null, null, null, null, null);
        light = was;
    }

    /** A flat panel on the inner face of a side wall (or, side 0, across the car on the gangway end). */
    private void panel(int side, double x, double y0, double y1, double z0, double z1, Mat mat) {
        if (side == 0) {
            box(z0, y0, -TrainCar.HALF + TrainCar.WALL, z1, y1, -TrainCar.HALF + TrainCar.WALL + 0.01, null, null, null, null, null, mat);
            return;
        }
        double off = side > 0 ? -0.01 : 0.01;
        double a = Math.min(x, x + off), b = Math.max(x, x + off);
        box(a, y0, z0, b, y1, z1, side > 0 ? mat : null, side < 0 ? mat : null, null, null, null, null);
    }

    private void leafFaces(int side, double x0, double x1, double z0, double z1, Mat low, Mat high) {
        byte was = light;
        for (int half = 0; half < 2; half++) {
            double y0 = half == 0 ? 0 : 1.0, y1 = half == 0 ? 1.0 : TrainCar.DOOR_HEIGHT;
            Mat m = half == 0 ? low : high;
            light = OUTSIDE;
            box(x0, y0, z0, x1, y1, z1, side < 0 ? m : null, side > 0 ? m : null, null, half == 1 ? RUBBER : null, RUBBER, RUBBER);
            light = INSIDE;
            box(x0, y0, z0, x1, y1, z1, side > 0 ? m : null, side < 0 ? m : null, null, null, null, null);
        }
        light = was;
    }

    private void pole(double x, double z) {
        byte was = light;
        light = INSIDE;
        box(x - 0.04, 0, z - 0.04, x + 0.04, TrainCar.CEILING, z + 0.04, CHROME, CHROME, null, null, CHROME, CHROME);
        light = was;
    }

    /** Glass across x at the middle of a wall: both faces, see-through. */
    private void glassX(double x, double y0, double y1, double z0, double z1) {
        byte was = light;
        light = OUTSIDE;
        box(x - 0.005, y0, z0, x + 0.005, y1, z1, GLASS, GLASS, null, null, null, null);
        light = was;
    }

    private void glassZ(double z, double x0, double x1, double y0, double y1) {
        byte was = light;
        light = OUTSIDE;
        box(x0, y0, z - 0.005, x1, y1, z + 0.005, null, null, null, null, GLASS, GLASS);
        light = was;
    }

    /**
     * An end wall: on the train's own ends the cab front with a big window (front) or a rear window
     * (back); between cars a gangway door.
     */
    private void endWall(double z0, double z1, boolean shut, boolean isFront, Mat skin, boolean subway) {
        double w = TrainCar.HALF_WIDTH, ceil = TrainCar.CEILING;
        Mat outFace = shut ? NOSE : skin;
        Mat inFace = WALL_IN;
        boolean plusOut = isFront;     // the outer face is +z at the front end, -z at the back
        if (!shut) {
            double g = TrainCar.GANGWAY_HALF;
            endBox(-w, z0, -g, 0, ceil, z1, plusOut, skin, inFace);
            endBox(g, z0, w, 0, ceil, z1, plusOut, skin, inFace);
            endBox(-g, z0, g, TrainCar.GANGWAY_HEIGHT, ceil, z1, plusOut, skin, inFace);
            return;
        }
        // the train's end: solid below and beside a wide window
        endBox(-w, z0, w, 0, 1.2, z1, plusOut, outFace, inFace);
        endBox(-w, z0, w, 2.6, ceil, z1, plusOut, outFace, inFace);
        endBox(-w, z0, -1.5, 1.2, 2.6, z1, plusOut, outFace, inFace);
        endBox(1.5, z0, w, 1.2, 2.6, z1, plusOut, outFace, inFace);
        glassZ((z0 + z1) / 2, -1.5, 1.5, 1.2, 2.6);
        // headlights low on the nose, the destination sign over the window
        byte was = light;
        light = GLOW;
        double face = plusOut ? z1 : z0;
        double d = plusOut ? 0.02 : -0.02;
        Mat lamp = isFront ? HEADLIGHT : null;
        if (lamp != null) {
            box(-1.75, 0.3, Math.min(face, face + d), -1.15, 0.9, Math.max(face, face + d), null, null, null, null, plusOut ? null : lamp, plusOut ? lamp : null);
            box(1.15, 0.3, Math.min(face, face + d), 1.75, 0.9, Math.max(face, face + d), null, null, null, null, plusOut ? null : lamp, plusOut ? lamp : null);
            box(-1.0, 2.68, Math.min(face, face + d), 1.0, 2.98, Math.max(face, face + d), null, null, null, null, plusOut ? null : SIGN, plusOut ? SIGN : null);
        }
        light = was;
        // a hazard-striped bumper
        box(-w, -0.85, plusOut ? z1 : z0 - 0.25, w, -0.35, plusOut ? z1 + 0.25 : z0, HAZARD, HAZARD, HAZARD, HAZARD, HAZARD, HAZARD);
    }

    private void endBox(double x0, double z0, double x1, double y0, double y1, double z1, boolean plusOut, Mat out, Mat in) {
        byte was = light;
        light = OUTSIDE;
        box(x0, y0, z0, x1, y1, z1, out, out, out, out, plusOut ? null : out, plusOut ? out : null);
        light = INSIDE;
        box(x0, y0, z0, x1, y1, z1, null, null, null, null, plusOut ? in : null, plusOut ? null : in);
        light = was;
    }

    /** The concertina between two cars, open down the middle for walking through. */
    private void bellows(double z0, double z1) {
        double g = TrainCar.GANGWAY_HALF;
        box(g, 0, z0, g + 0.15, TrainCar.GANGWAY_HEIGHT + 0.2, z1, BELLOWS, BELLOWS, null, BELLOWS, null, null);
        box(-g - 0.15, 0, z0, -g, TrainCar.GANGWAY_HEIGHT + 0.2, z1, BELLOWS, BELLOWS, null, BELLOWS, null, null);
        box(-g, TrainCar.GANGWAY_HEIGHT, z0, g, TrainCar.GANGWAY_HEIGHT + 0.2, z1, null, null, BELLOWS, BELLOWS, null, null);
        box(-g - 0.15, -0.3, z0, g + 0.15, 0, z1, null, null, null, FLOOR, null, null);
    }

    /** The cab: the partition with its window, the console, and OTTO at the controls facing the riders. */
    private void cab(boolean subway) {
        double w = TrainCar.HALF_WIDTH, c = TrainCar.CAB, t = TrainCar.WALL, ceil = TrainCar.CEILING;
        light = INSIDE;
        // the partition: panels, a window to watch him through, a hazard strip
        box(-w + t, 0, c, -0.7, ceil, c + t, null, null, null, null, WALL_IN, WALL_IN);
        box(0.7, 0, c, w - t, ceil, c + t, null, null, null, null, WALL_IN, WALL_IN);
        box(-0.7, 0, c, 0.7, 1.1, c + t, null, null, null, null, WARNING, WALL_IN);
        box(-0.7, 2.3, c, 0.7, ceil, c + t, null, null, null, null, HAZARD, WALL_IN);
        glassZ(c + t / 2, -0.7, 0.7, 1.1, 2.3);
        // the console under the windscreen, and the cab floor
        box(-w + t, 0, TrainCar.HALF - 0.9, w - t, 1.0, TrainCar.HALF - t, OTTO_DARK, OTTO_DARK, null, OTTO_DARK, OTTO_DARK, OTTO_DARK);
        light = GLOW;
        box(-1.2, 1.0, TrainCar.HALF - 0.85, 1.2, 1.02, TrainCar.HALF - 0.3, null, null, null, OTTO_CHEST, null, null);
        light = INSIDE;
        // OTTO: bolted to a pedestal, turned to face back down the train
        double ox = 0, oz = c + 1.0;
        box(ox - 0.2, 0, oz - 0.2, ox + 0.2, 0.75, oz + 0.2, OTTO_DARK, OTTO_DARK, null, null, OTTO_DARK, OTTO_DARK);
        box(ox - 0.38, 0.75, oz - 0.24, ox + 0.38, 1.6, oz + 0.24, OTTO_SHELL, OTTO_SHELL, OTTO_SHELL, OTTO_SHELL, OTTO_CHEST, OTTO_SHELL);
        // arms reaching back to the console
        box(ox - 0.55, 0.9, oz - 0.1, ox - 0.38, 1.5, oz + 0.1, OTTO_DARK, OTTO_DARK, OTTO_DARK, OTTO_DARK, OTTO_DARK, OTTO_DARK);
        box(ox + 0.38, 0.9, oz - 0.1, ox + 0.55, 1.5, oz + 0.1, OTTO_DARK, OTTO_DARK, OTTO_DARK, OTTO_DARK, OTTO_DARK, OTTO_DARK);
        box(ox - 0.55, 0.9, oz + 0.1, ox - 0.4, 1.05, oz + 0.75, OTTO_DARK, OTTO_DARK, OTTO_DARK, OTTO_DARK, OTTO_DARK, OTTO_DARK);
        box(ox + 0.4, 0.9, oz + 0.1, ox + 0.55, 1.05, oz + 0.75, OTTO_DARK, OTTO_DARK, OTTO_DARK, OTTO_DARK, OTTO_DARK, OTTO_DARK);
        box(ox - 0.08, 1.6, oz - 0.08, ox + 0.08, 1.72, oz + 0.08, OTTO_DARK, OTTO_DARK, null, null, OTTO_DARK, OTTO_DARK);
        // the head, which turns: a box with his visor face toward the riders, and an antenna
        headPivot = new float[] {(float) ox, 2.0f, (float) oz};
        part = PART_HEAD;
        box(ox - 0.3, 1.72, oz - 0.27, ox + 0.3, 2.28, oz + 0.27, OTTO_SHELL, OTTO_SHELL, OTTO_SHELL, OTTO_SHELL, null, OTTO_SHELL);
        box(ox - 0.02, 2.28, oz - 0.02, ox + 0.02, 2.6, oz + 0.02, OTTO_DARK, OTTO_DARK, null, OTTO_DARK, OTTO_DARK, OTTO_DARK);
        light = GLOW;
        box(ox - 0.26, 1.76, oz - 0.28, ox + 0.26, 2.24, oz - 0.27, null, null, null, null, OTTO_FACE, null);
        box(ox - 0.04, 2.6, oz - 0.04, ox + 0.04, 2.66, oz + 0.04, LAMP, LAMP, null, LAMP, LAMP, LAMP);
        part = PART_TALK;
        box(ox - 0.26, 1.76, oz - 0.285, ox + 0.26, 2.24, oz - 0.281, null, null, null, null, OTTO_TALK, null);
        part = STATIC;
        light = OUTSIDE;
    }

    // ------------------------------------------------------------------ boxes into quads

    /** A box, with the material for each face (-x, +x, -y, +y, -z, +z; null leaves the face out), one tile per block. */
    private void box(double x0, double y0, double z0, double x1, double y1, double z1,
                     Mat nx, Mat px, Mat ny, Mat py, Mat nz, Mat pz) {
        if (px != null) {
            face(px, x1, y1, z1, 0, 0, -1, z1 - z0, 0, -1, 0, y1 - y0, 1, 0, 0);
        }
        if (nx != null) {
            face(nx, x0, y1, z0, 0, 0, 1, z1 - z0, 0, -1, 0, y1 - y0, -1, 0, 0);
        }
        if (pz != null) {
            face(pz, x0, y1, z1, 1, 0, 0, x1 - x0, 0, -1, 0, y1 - y0, 0, 0, 1);
        }
        if (nz != null) {
            face(nz, x1, y1, z0, -1, 0, 0, x1 - x0, 0, -1, 0, y1 - y0, 0, 0, -1);
        }
        if (py != null) {
            face(py, x0, y1, z0, 1, 0, 0, x1 - x0, 0, 0, 1, z1 - z0, 0, 1, 0);
        }
        if (ny != null) {
            face(ny, x0, y0, z1, 1, 0, 0, x1 - x0, 0, 0, -1, z1 - z0, 0, -1, 0);
        }
    }

    /**
     * One face from its top-left corner (o), its right and down directions and lengths, cut into
     * block-sized tiles; corners go top-left, bottom-left, bottom-right, top-right (anticlockwise seen
     * from outside).
     */
    private void face(Mat mat, double ox, double oy, double oz, double rx, double ry, double rz, double rl,
                      double dx, double dy, double dz, double dl, float nx, float ny, float nz) {
        if (rl <= 1e-4 || dl <= 1e-4) {
            return;
        }
        for (double u = 0; u < rl - 1e-4; u += 1) {
            double du = Math.min(1, rl - u);
            for (double v = 0; v < dl - 1e-4; v += 1) {
                double dv = Math.min(1, dl - v);
                int hash = (int) (ox * 31 + oz * 17 + oy * 7 + u * 131 + v * 59 + nx * 3 + nz * 5) * 2654435 + seed * 977;
                hash ^= hash >>> 13;
                int col = mat.pick(hash);
                Quad q = new Quad();
                double[][] c = {{u, v}, {u, v + dv}, {u + du, v + dv}, {u + du, v}};
                for (int k = 0; k < 4; k++) {
                    q.p[k * 3] = (float) (ox + rx * c[k][0] + dx * c[k][1]);
                    q.p[k * 3 + 1] = (float) (oy + ry * c[k][0] + dy * c[k][1]);
                    q.p[k * 3 + 2] = (float) (oz + rz * c[k][0] + dz * c[k][1]);
                    double tu = c[k][0] - u, tv = c[k][1] - v;
                    q.uv[k * 2] = (float) ((col * TILE + tu * TILE) / ATLAS);
                    q.uv[k * 2 + 1] = (float) ((mat.row() * TILE + tv * TILE) / ATLAS);
                }
                q.nx = nx;
                q.ny = ny;
                q.nz = nz;
                q.light = light;
                q.part = part;
                q.glass = mat.glass();
                quads.add(q);
            }
        }
    }
}
