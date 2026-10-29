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
    static final Mat CLOSE_DOORS = Mat.of(6, 0);
    static final Mat TIE_PLATE = Mat.of(6, 1), BALLAST = Mat.of(6, 2), WEED = Mat.of(6, 3), INSULATOR = Mat.of(6, 4),
            RAIL_COVER = Mat.of(6, 5), CLIP = Mat.of(6, 6);
    static final Mat MAP = Mat.of(5, 12), EMERGENCY = Mat.of(5, 13), HAZARD = Mat.of(5, 14), LAMP = Mat.of(5, 15);

    /** How a quad is lit: by the world outside, by the car's own lights inside, or glowing itself. */
    static final byte OUTSIDE = 0, INSIDE = 1, GLOW = 2;
    /** What moves it: nothing, a door leaf (PART_DOOR + leaf number), OTTO's head, his jaw, his eyes. */
    static final int STATIC = 0, PART_HEAD = 1, PART_JAW = 3, PART_EYES = 4, PART_DOOR = 100;

    static final class Quad {
        final float[] p = new float[12];
        final float[] uv = new float[8];
        float nx, ny, nz;
        byte light;
        int part;
        boolean glass;
        /** The lamp this is the tube of (-1: none), and the two lamps nearest it that light it (inside quads). */
        int lamp = -1, near = -1, near2 = -1;
    }

    /** A door leaf: which side of the car it's on and which way it slides open (along z). */
    record Leaf(int side, float slide) {
    }

    final List<Quad> quads = new ArrayList<>();
    final List<Leaf> leaves = new ArrayList<>();
    /** OTTO's head pivot and jaw hinge, in the car's frame (front car only). */
    float[] headPivot, jawPivot;
    /** The ceiling lamps, by where along the car they hang, and which one lights the cab (-1: no cab). */
    final List<Float> lamps = new ArrayList<>();
    int cabLamp = -1;

    private int seed;
    private int part = STATIC;
    private int lamp = -1;
    /** Whether a face shows its whole tile however small it is (labels, buttons), not a block-sized piece of it. */
    private boolean stretch;
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
        // big windows: from just above the bench backs nearly to the ceiling
        double winLow = 1.0, winHigh = 2.72;

        // ---- the shell
        light = OUTSIDE;
        // skirts below the floor and the underframe
        box(-w, -0.32, -h, -w + 0.1, 0, h, skin, null, UNDER, null, null, null);
        box(w - 0.1, -0.32, -h, w, 0, h, null, skin, UNDER, null, null, null);
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
                // slim window pillars about every three blocks, wide panes between
                double len = zb - za, pillar = 0.08;
                int panes = Math.max(1, (int) Math.round(len / 3));
                double step = len / panes;
                for (int i = 0; i <= panes; i++) {
                    double pz = za + step * i;
                    double p0 = Math.max(za, pz - pillar), p1 = Math.min(zb, pz + pillar);
                    sideBox(side, x0, x1, winLow, winHigh, p0, p1, clean, WALL_IN);
                }
                for (int i = 0; i < panes; i++) {
                    double g0 = za + step * i + pillar, g1 = za + step * (i + 1) - pillar;
                    if (g1 > g0) {
                        glassX((x0 + x1) / 2, winLow, winHigh, g0, g1);
                    }
                }
                // inside: ads over the windows, benches under them
                light = INSIDE;
                if (!cab && ceil - winHigh > 0.4) {
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
        // running gear, the gear slung under the floor, and the roof, door and end fittings
        for (double bz : new double[] {-TrainCar.BOGIE, TrainCar.BOGIE}) {
            bogie(bz);
        }
        underfloor(subway);
        roofGear(subway, front);
        fittings(subway, front, back);

        // ---- the inside
        light = INSIDE;
        box(-w + t, -0.05, -h + t, w - t, 0, h - t, null, null, null, FLOOR, null, null);
        // the ceiling, with a row of tube lamps down the middle (some of them failing: TrainRenderer flickers them)
        box(-w + t, ceil - 0.02, -h + t, w - t, ceil, h - t, null, null, CEILING, null, null, null);
        double lampsEnd = front ? TrainCar.CAB : h - t;
        int lampCount = (int) Math.floor((lampsEnd + h - t) / 3.0);
        double lampPitch = (lampsEnd + h - t) / lampCount;
        for (int i = 0; i < lampCount; i++) {
            double zc = -h + t + lampPitch * (i + 0.5);
            lamp(zc - 1.15, zc + 1.15);
        }
        // benches along the walls: a seat each, cushion and back on a steel frame, an armrest every two,
        // the heater under them
        for (int side = -1; side <= 1; side += 2) {
            for (double[] run : TrainCar.seatRuns(front)) {
                double x0 = side > 0 ? w - t - TrainCar.SEAT_DEPTH : -w + t, x1 = side > 0 ? w - t : -w + t + TrainCar.SEAT_DEPTH;
                double sh = TrainCar.SEAT_HEIGHT;
                double front0 = side > 0 ? x0 : x1;
                // the frame: a plinth set back with the heater grille, a steel lip
                double p0 = side > 0 ? x0 + 0.08 : x0, p1 = side > 0 ? x1 : x1 - 0.08;
                box(p0, 0, run[0], p1, sh - 0.1, run[1], WALL_IN, WALL_IN, null, null, WALL_IN, WALL_IN);
                box(side > 0 ? p0 - 0.01 : p1, 0.06, run[0] + 0.1, side > 0 ? p0 : p1 + 0.01, sh - 0.18, run[1] - 0.1,
                        side > 0 ? CLIP : null, side > 0 ? null : CLIP, null, null, null, null);
                box(x0, sh - 0.12, run[0], x1, sh - 0.1, run[1], CHROME, CHROME, CHROME, CHROME, CHROME, CHROME);
                int seats = Math.max(1, (int) Math.round((run[1] - run[0]) / 0.55));
                double pitch = (run[1] - run[0]) / seats;
                for (int i = 0; i < seats; i++) {
                    double a = run[0] + pitch * i + 0.02, b = run[0] + pitch * (i + 1) - 0.02;
                    // cushion, dipped a touch at the front, and the back leaning out from the wall
                    box(x0 + (side > 0 ? 0.02 : 0), sh - 0.1, a, x1 - (side > 0 ? 0 : 0.02), sh, b, seat, seat, null, seat, seat, seat);
                    double bx0 = side > 0 ? w - t - 0.14 : -w + t, bx1 = side > 0 ? w - t : -w + t + 0.14;
                    box(bx0, sh + 0.04, a, bx1, 1.05, b, seat, seat, null, seat, seat, seat);
                    if (i % 2 == 0 && i > 0) {
                        double ax = front0 + (side > 0 ? 0.02 : -0.02);
                        box(Math.min(ax, side > 0 ? x1 : x0), sh, a - 0.04, Math.max(ax, side > 0 ? x1 : x0), sh + 0.22, a,
                                CHROME, CHROME, null, CHROME, CHROME, CHROME);
                    }
                }
            }
        }
        interiorDetail(subway, front, w, t, h, ceil, winLow);
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
        // a stop button on the pole beside each door, facing in: press it (interact) to be let off
        // and over it a close-doors button, to send the train on its way
        for (int k = 0; k < TrainCar.STOP_BUTTONS.length * 2; k++) {
            double[] b = k % 2 == 0 ? TrainCar.STOP_BUTTONS[k / 2] : TrainCar.CLOSE_BUTTONS[k / 2];
            Mat face = k % 2 == 0 ? EMERGENCY : CLOSE_DOORS;
            double in = b[0] > 0 ? -1 : 1;     // the side of the pole toward the aisle
            double x0 = Math.min(b[0], b[0] - in * 0.05), x1 = Math.max(b[0], b[0] - in * 0.05);
            light = GLOW;
            stretch = true;
            box(x0, b[1] - 0.14, b[2] - 0.09, x1, b[1] + 0.14, b[2] + 0.09,
                    in > 0 ? null : face, in > 0 ? face : null, null, null, null, null);
            stretch = false;
            light = INSIDE;
            box(x0 + 0.005, b[1] - 0.14, b[2] - 0.09, x1 - 0.005, b[1] + 0.14, b[2] + 0.09, null, null, OTTO_DARK, OTTO_DARK, OTTO_DARK, OTTO_DARK);
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
        nearLamps(front);
        if (back) {
            // a rear cab window and tail lamps
            light = GLOW;
            box(-1.6, 0.35, -h - 0.02, -1.25, 0.65, -h, null, null, null, null, HEADLIGHT, null);
            box(1.25, 0.35, -h - 0.02, 1.6, 0.65, -h, null, null, null, null, HEADLIGHT, null);
            light = OUTSIDE;
        }
    }

    // ------------------------------------------------------------------ pieces

    /** A tube lamp on the ceiling's centreline: a housing with a glowing diffuser underneath. */
    private void lamp(double z0, double z1) {
        double ceil = TrainCar.CEILING;
        byte was = light;
        light = INSIDE;
        box(-0.45, ceil - 0.08, z0, 0.45, ceil - 0.02, z1, CEILING, CEILING, null, null, CEILING, CEILING);
        light = GLOW;
        lamp = lamps.size();
        box(-0.38, ceil - 0.085, z0 + 0.06, 0.38, ceil - 0.08, z1 - 0.06, null, null, LIGHT, null, null, null);
        lamp = -1;
        light = was;
        lamps.add((float) ((z0 + z1) / 2));
    }

    /** Which lamps light each inside quad: the cab's for anything in the cab, else the two nearest along the car. */
    private void nearLamps(boolean front) {
        for (Quad q : quads) {
            if (q.light != INSIDE || lamps.isEmpty()) {
                continue;
            }
            float z = (q.p[2] + q.p[5] + q.p[8] + q.p[11]) / 4;
            if (front && cabLamp >= 0 && z > TrainCar.CAB) {
                q.near = q.near2 = cabLamp;
                continue;
            }
            float best = Float.MAX_VALUE, second = Float.MAX_VALUE;
            for (int i = 0; i < lamps.size(); i++) {
                if (i == cabLamp) {
                    continue;
                }
                float d = Math.abs(lamps.get(i) - z);
                if (d < best) {
                    second = best;
                    q.near2 = q.near;
                    best = d;
                    q.near = i;
                } else if (d < second) {
                    second = d;
                    q.near2 = i;
                }
            }
            if (q.near2 < 0) {
                q.near2 = q.near;
            }
        }
    }

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
        box(-0.7, 2.75, c, 0.7, ceil, c + t, null, null, null, null, HAZARD, WALL_IN);
        glassZ(c + t / 2, -0.7, 0.7, 1.1, 2.75);
        // the console under the windscreen, and the cab floor
        box(-w + t, 0, TrainCar.HALF - 0.9, w - t, 1.0, TrainCar.HALF - t, OTTO_DARK, OTTO_DARK, null, OTTO_DARK, OTTO_DARK, OTTO_DARK);
        light = GLOW;
        box(-1.2, 1.0, TrainCar.HALF - 0.85, 1.2, 1.02, TrainCar.HALF - 0.3, null, null, null, OTTO_CHEST, null, null);
        light = INSIDE;
        // OTTO (tools/trains/otto.py): bolted to the floor at the controls, head wrenched round to the riders
        double ox = 0, oz = c + 1.0;
        float[] q = OttoModel.QUADS;
        for (int i = 0; i + OttoModel.STRIDE <= q.length; i += OttoModel.STRIDE) {
            Quad quad = new Quad();
            for (int k = 0; k < 4; k++) {
                quad.p[k * 3] = (float) (q[i + k * 3] + ox);
                quad.p[k * 3 + 1] = q[i + k * 3 + 1];
                quad.p[k * 3 + 2] = (float) (q[i + k * 3 + 2] + oz);
                quad.uv[k * 2] = q[i + 12 + k * 2];
                quad.uv[k * 2 + 1] = q[i + 12 + k * 2 + 1];
            }
            quad.nx = q[i + 20];
            quad.ny = q[i + 21];
            quad.nz = q[i + 22];
            quad.part = (int) q[i + 23];
            quad.light = (byte) q[i + 24];
            quads.add(quad);
        }
        // his own lamp, right over his head
        cabLamp = lamps.size();
        lamp(oz - 0.6, oz + 0.6);
        headPivot = new float[] {(float) ox + OttoModel.HEAD_PIVOT[0], OttoModel.HEAD_PIVOT[1], (float) oz + OttoModel.HEAD_PIVOT[2]};
        jawPivot = new float[] {(float) ox + OttoModel.JAW_PIVOT[0], OttoModel.JAW_PIVOT[1], (float) oz + OttoModel.JAW_PIVOT[2]};
        part = STATIC;
        light = OUTSIDE;
    }

    /** Inside: hand straps off the overhead rails, vents and speakers in the ceiling, sills, door thresholds, and litter. */
    private void interiorDetail(boolean subway, boolean front, double w, double t, double h, double ceil, double winLow) {
        byte was = light;
        light = INSIDE;
        double end = (front ? TrainCar.CAB : h) - 0.5;
        // straps: a loop on a strap every 0.7 along both overhead rails
        for (int side = -1; side <= 1; side += 2) {
            for (double z = -h + 0.7; z < end; z += 0.7) {
                double x = side * 1.05;
                box(x - 0.012, 2.25, z - 0.025, x + 0.012, 2.55, z + 0.025, RUBBER, RUBBER, null, null, RUBBER, RUBBER);
                box(x - 0.015, 2.12, z - 0.07, x + 0.015, 2.25, z + 0.07, OTTO_SHELL, OTTO_SHELL, OTTO_SHELL, OTTO_SHELL, OTTO_SHELL, OTTO_SHELL);
            }
            // air vents along the ceiling either side of the lamps, and the window sills
            box(side * 0.55 - 0.12, ceil - 0.04, -h + 0.5, side * 0.55 + 0.12, ceil - 0.02, end, null, null, CLIP, null, null, null);
            double sx0 = side > 0 ? w - t - 0.08 : -w + t, sx1 = side > 0 ? w - t : -w + t + 0.08;
            box(sx0, winLow - 0.03, -h + 0.4, sx1, winLow, end, CHROME, CHROME, null, CHROME, CHROME, CHROME);
        }
        for (double z = -h + 2; z < end; z += 4.5) {
            box(-0.18, ceil - 0.04, z - 0.18, 0.18, ceil - 0.02, z + 0.18, null, null, CLIP, null, null, null);   // speakers
        }
        // a yellow threshold strip at each doorway
        for (double d : TrainCar.DOORS) {
            for (int side = -1; side <= 1; side += 2) {
                double x0 = side > 0 ? w - t - 0.25 : -w + t, x1 = side > 0 ? w - t : -w + t + 0.25;
                box(x0, 0, d - TrainCar.DOOR_HALF, x1, 0.012, d + TrainCar.DOOR_HALF, null, null, null, HAZARD, null, null);
            }
        }
        // what was left behind: newspapers, a dropped bag
        java.util.Random r = new java.util.Random(seed * 977L);
        for (int i = 0; i < 4; i++) {
            double x = (r.nextDouble() - 0.5) * 1.6, z = -h + 1 + r.nextDouble() * (end + h - 2);
            double a = 0.15 + r.nextDouble() * 0.12, b = 0.2 + r.nextDouble() * 0.12;
            box(x - a, 0, z - b, x + a, 0.008 + i * 0.002, z + b, null, null, null, CREAM, null, null);
        }
        if (r.nextBoolean()) {
            double z = -h + 2 + r.nextDouble() * (end + h - 4), x = (r.nextBoolean() ? 1 : -1) * 1.1;
            box(x - 0.18, 0, z - 0.25, x + 0.18, 0.28, z + 0.25, RUBBER, RUBBER, null, RUBBER, RUBBER, RUBBER);
            box(x - 0.03, 0.28, z - 0.15, x + 0.03, 0.36, z + 0.15, RUBBER, RUBBER, null, RUBBER, RUBBER, RUBBER);
        }
        light = was;
    }

    // ------------------------------------------------------------------ the fine parts outside

    private void solid(double x0, double y0, double z0, double x1, double y1, double z1, Mat m) {
        box(x0, y0, z0, x1, y1, z1, m, m, m, m, m, m);
    }

    /** A wheel in the y-z plane at (x, y, z): an octagon of three boxes, the flange inside, the hub out. */
    private void wheel(double x0, double x1, double y, double z, double r, int inSide) {
        double k = r * 0.42, d = r * 0.8;
        solid(x0, y - k, z - r, x1, y + k, z + r, WHEEL);
        solid(x0, y - r, z - k, x1, y + r, z + k, WHEEL);
        solid(x0, y - d, z - d, x1, y + d, z + d, WHEEL);
        double fx = inSide > 0 ? x1 : x0 - 0.03, fx1 = inSide > 0 ? x1 + 0.03 : x0;
        solid(fx, y - (r + 0.04) * 0.42, z - r - 0.04, fx1, y + (r + 0.04) * 0.42, z + r + 0.04, WHEEL);
        solid(fx, y - r - 0.04, z - (r + 0.04) * 0.42, fx1, y + r + 0.04, z + (r + 0.04) * 0.42, WHEEL);
        double hx = inSide > 0 ? x0 - 0.05 : x1, hx1 = inSide > 0 ? x0 : x1 + 0.05;
        solid(hx, y - 0.11, z - 0.11, hx1, y + 0.11, z + 0.11, CLIP);
    }

    /** A bogie: side frames with axle boxes on springs, a bolster across, brake gear at each wheel, two axles. */
    private void bogie(double bz) {
        double wy = -0.55, r = 0.43;
        for (double wz : new double[] {bz - 0.9, bz + 0.9}) {
            // the axle and its wheels (the gauge: rails at +-0.72)
            solid(-0.72, wy - 0.06, wz - 0.06, 0.72, wy + 0.06, wz + 0.06, CLIP);
            for (int sx = -1; sx <= 1; sx += 2) {
                double c = sx * 0.72;
                wheel(c - 0.06, c + 0.06, wy, wz, r, -sx);
                // axle box on its springs, outside the wheel
                double ax0 = sx > 0 ? 0.82 : -1.02, ax1 = sx > 0 ? 1.02 : -0.82;
                solid(ax0, wy - 0.16, wz - 0.18, ax1, wy + 0.14, wz + 0.18, BOGIE);
                solid(ax0 + 0.03, wy - 0.2, wz - 0.13, ax1 - 0.03, wy - 0.16, wz + 0.13, CLIP);
                for (int coil = 0; coil < 4; coil++) {
                    double cw = coil % 2 == 0 ? 0.09 : 0.07, cy = wy + 0.14 + coil * 0.055;
                    solid((ax0 + ax1) / 2 - cw, cy, wz - cw, (ax0 + ax1) / 2 + cw, cy + 0.04, wz + cw, CLIP);
                }
                // brake shoe on the tread, its hanger and the cylinder working it
                double bzs = wz + (wz < bz ? -1 : 1) * (r + 0.03);
                solid(c - 0.07, wy - 0.17, bzs - 0.04, c + 0.07, wy + 0.17, bzs + 0.04, CLIP);
                solid(c - 0.03, wy + 0.17, bzs - 0.03, c + 0.03, wy + 0.36, bzs + 0.03, CLIP);
            }
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            // the side frame: a deep beam between the axle boxes, sagging in the middle under the bolster
            double f0 = sx > 0 ? 0.85 : -0.99, f1 = sx > 0 ? 0.99 : -0.85;
            solid(f0, wy + 0.18, bz - 1.25, f1, wy + 0.33, bz + 1.25, BOGIE);
            solid(f0, wy - 0.12, bz - 0.55, f1, wy + 0.18, bz + 0.55, BOGIE);
            // brake cylinder on the frame
            solid(sx > 0 ? 0.99 : -1.15, wy - 0.02, bz - 0.25, sx > 0 ? 1.15 : -0.99, wy + 0.16, bz + 0.05, CLIP);
            solid(sx > 0 ? 1.0 : -1.06, wy + 0.04, bz + 0.05, sx > 0 ? 1.06 : -1.0, wy + 0.1, bz + 0.3, CHROME);
        }
        // the bolster carrying the car, its centre bearing and the air springs on it
        solid(-1.0, wy + 0.2, bz - 0.28, 1.0, wy + 0.42, bz + 0.28, BOGIE);
        for (int sx = -1; sx <= 1; sx += 2) {
            solid(sx * 0.7 - 0.16, wy + 0.42, bz - 0.16, sx * 0.7 + 0.16, -0.32, bz + 0.16, RUBBER);
        }
        solid(-0.2, wy + 0.42, bz - 0.2, 0.2, -0.32, bz + 0.2, CLIP);
    }

    /** The gear slung under the floor between the bogies: boxes, tanks and pipes, a different run on each side. */
    private void underfloor(boolean subway) {
        double z0 = -TrainCar.BOGIE + 1.7, z1 = TrainCar.BOGIE - 1.7;
        java.util.Random r = new java.util.Random(seed * 131L);
        for (int sx = -1; sx <= 1; sx += 2) {
            double z = z0;
            while (z < z1 - 0.6) {
                double len = 0.8 + r.nextDouble() * 1.8;
                double zb = Math.min(z1, z + len);
                int kind = r.nextInt(4);
                double out = 1.72, in = 0.9 + r.nextDouble() * 0.4, low = -0.82 + r.nextDouble() * 0.2;
                double x0 = sx > 0 ? in : -out, x1 = sx > 0 ? out : -in;
                if (kind == 0) {
                    // an air reservoir: a long drum (octagon) on two straps
                    double cx = sx * 1.3, cy = -0.55, rr = 0.22;
                    solid(cx - rr, cy - rr * 0.42, z, cx + rr, cy + rr * 0.42, zb, UNDER);
                    solid(cx - rr * 0.42, cy - rr, z, cx + rr * 0.42, cy + rr, zb, UNDER);
                    solid(cx - rr * 0.8, cy - rr * 0.8, z, cx + rr * 0.8, cy + rr * 0.8, zb, UNDER);
                    for (double sz : new double[] {z + 0.15, zb - 0.2}) {
                        solid(cx - rr - 0.02, cy - rr - 0.02, sz, cx + rr + 0.02, -0.32, sz + 0.05, CLIP);
                    }
                } else {
                    // an equipment case: battery, inverter, compressor; lids, hinges and a vent grille
                    solid(x0, low, z, x1, -0.32, zb, UNDER);
                    double face = sx > 0 ? x1 : x0;
                    solid(face - (sx > 0 ? 0 : 0.02), low + 0.05, z + 0.06, face + (sx > 0 ? 0.02 : 0), -0.38, zb - 0.06, CLIP);
                    if (kind == 2) {
                        for (double g = z + 0.2; g < zb - 0.2; g += 0.12) {
                            solid(face - (sx > 0 ? 0 : 0.035), low + 0.12, g, face + (sx > 0 ? 0.035 : 0), -0.45, g + 0.04, UNDER);
                        }
                    }
                }
                z = zb + 0.15 + r.nextDouble() * 0.5;
            }
            // the brake pipe and the main reservoir pipe, the length of the car
            solid(sx * 0.6 - 0.03, -0.42, -TrainCar.HALF + 0.2, sx * 0.6 + 0.03, -0.36, TrainCar.HALF - 0.2, CLIP);
        }
    }

    /** On the roof: the air-conditioning units with their fans, vents down the middle, gutters along the eaves, a horn and aerial on the front car. */
    private void roofGear(boolean subway, boolean front) {
        double y = TrainCar.ROOF, w = TrainCar.HALF_WIDTH;
        for (double z : new double[] {-5.2, 5.2}) {
            solid(-0.95, y + 0.12, z - 1.5, 0.95, y + 0.48, z + 1.5, ROOF);
            solid(-0.98, y + 0.15, z - 1.3, 0.98, y + 0.4, z + 1.3, CLIP);    // the louvred sides
            solid(-0.95, y + 0.4, z - 1.5, 0.95, y + 0.48, z + 1.5, ROOF);
            for (double fz : new double[] {z - 0.7, z + 0.7}) {
                solid(-0.4, y + 0.48, fz - 0.4, 0.4, y + 0.54, fz + 0.4, CLIP);   // fan guards
                solid(-0.08, y + 0.54, fz - 0.08, 0.08, y + 0.57, fz + 0.08, CHROME);
            }
        }
        for (double z = -9; z <= 9; z += 3) {
            if (Math.abs(Math.abs(z) - 5.2) > 1.8) {
                solid(-0.25, y + 0.12, z - 0.2, 0.25, y + 0.24, z + 0.2, ROOF);
                solid(-0.27, y + 0.24, z - 0.22, 0.27, y + 0.27, z + 0.22, CLIP);
            }
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            solid(sx > 0 ? w : -w - 0.05, TrainCar.CEILING + 0.02, -TrainCar.HALF, sx > 0 ? w + 0.05 : -w, TrainCar.CEILING + 0.1, TrainCar.HALF, CLIP);
        }
        if (front) {
            solid(-0.15, y + 0.12, TrainCar.HALF - 1.2, 0.15, y + 0.3, TrainCar.HALF - 0.6, CHROME);    // the horns
            solid(0.6, y + 0.12, TrainCar.HALF - 2.4, 0.66, y + 0.75, TrainCar.HALF - 2.34, CLIP);      // radio aerial
        }
    }

    /** Door frames and grab rails, car numbers, couplers between the cars. */
    private void fittings(boolean subway, boolean front, boolean back) {
        double w = TrainCar.HALF_WIDTH, h = TrainCar.HALF;
        for (int sx = -1; sx <= 1; sx += 2) {
            double o0 = sx > 0 ? w : -w - 0.03, o1 = sx > 0 ? w + 0.03 : -w;
            for (double d : TrainCar.DOORS) {
                double a = d - TrainCar.DOOR_HALF, b = d + TrainCar.DOOR_HALF;
                // the frame round the doorway, and a rubber edge
                solid(o0, 0, a - 0.08, o1, TrainCar.DOOR_HEIGHT + 0.08, a, CLIP);
                solid(o0, 0, b, o1, TrainCar.DOOR_HEIGHT + 0.08, b + 0.08, CLIP);
                solid(o0, TrainCar.DOOR_HEIGHT, a, o1, TrainCar.DOOR_HEIGHT + 0.08, b, CLIP);
                // the door warning light over it
                solid(o0 - (sx > 0 ? 0 : 0.03), TrainCar.DOOR_HEIGHT + 0.12, d - 0.08, o1 + (sx > 0 ? 0.03 : 0), TrainCar.DOOR_HEIGHT + 0.22, d + 0.08, HEADLIGHT);
                // grab rails each side of the doorway, on stand-offs
                double g0 = sx > 0 ? w + 0.06 : -w - 0.1, g1 = sx > 0 ? w + 0.1 : -w - 0.06;
                for (double gz : new double[] {a - 0.25, b + 0.25}) {
                    solid(g0, 0.7, gz - 0.02, g1, 1.9, gz + 0.02, CHROME);
                    for (double gy : new double[] {0.75, 1.85}) {
                        solid(Math.min(o1, g0), gy - 0.02, gz - 0.02, Math.max(o0, g1), gy + 0.02, gz + 0.02, CHROME);
                    }
                }
            }
            // the car's number, low at each end
            for (int e = -1; e <= 1; e += 2) {
                double nz = e * (h - 1.2);
                solid(o0 - (sx > 0 ? 0 : 0.01), 0.35, nz - 0.35, o1 + (sx > 0 ? 0.01 : 0), 0.6, nz + 0.35, SIGN);
            }
        }
        // couplers and the hoses beside them at both ends, reaching half the gap to the next car (or out in front)
        for (int e = -1; e <= 1; e += 2) {
            double z0 = e > 0 ? h : -h - 0.32, z1 = e > 0 ? h + 0.32 : -h;
            solid(-0.12, -0.58, z0, 0.12, -0.34, z1, BOGIE);
            solid(-0.2, -0.64, e > 0 ? z1 - 0.12 : z0, 0.2, -0.28, e > 0 ? z1 : z0 + 0.12, CLIP);
            for (int sx = -1; sx <= 1; sx += 2) {
                solid(sx * 0.4 - 0.04, -0.7, z0, sx * 0.4 + 0.04, -0.4, z1, RUBBER);
            }
        }
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
                    double tu = stretch ? c[k][0] / rl : c[k][0] - u, tv = stretch ? c[k][1] / dl : c[k][1] - v;
                    q.uv[k * 2] = (float) ((col * TILE + tu * TILE) / ATLAS);
                    q.uv[k * 2 + 1] = (float) ((mat.row() * TILE + tv * TILE) / ATLAS);
                }
                q.nx = nx;
                q.ny = ny;
                q.nz = nz;
                q.light = light;
                q.part = part;
                q.lamp = lamp;
                q.glass = mat.glass();
                quads.add(q);
            }
        }
    }
}
