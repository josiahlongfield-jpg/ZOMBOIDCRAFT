package com.zomboidcraft.world.trains;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.phys.Vec3;

/**
 * One car's size and shape, in its own frame: +z forward, +x to one side, y up from the floor's top.
 * A car is 22 long and 4 wide with 3 blocks of headroom; its floor is a block above the track bed,
 * level with the platforms. Three doors each side, a gangway at each coupled end, the robot
 * conductor's cab at the front of the first car.
 */
public final class TrainCar {
    public static final double LENGTH = 22, HALF = LENGTH / 2, HALF_WIDTH = 2, PITCH = 22.6;
    /** Where the bogies sit either side of the middle; they decide where a car points on a curve. */
    public static final double BOGIE = 7.5;
    public static final double FLOOR = 1.0, CEILING = 3.0, ROOF = 3.5, WALL = 0.15;
    public static final double[] DOORS = {-7, 0, 7};
    public static final double DOOR_HALF = 1.0, DOOR_HEIGHT = 2.5;
    public static final double GANGWAY_HALF = 0.7, GANGWAY_HEIGHT = 2.3;
    /** The cab partition at the front of the first car; the cab's behind it. */
    public static final double CAB = 8.9;
    /** The stop-request buttons, on the grab pole beside each door, both sides (car frame: x, y, z of the button's face). */
    public static final double[][] STOP_BUTTONS = stopButtons();
    /** The close-doors buttons, just above each stop button on the same pole. */
    public static final double[][] CLOSE_BUTTONS = closeButtons();
    /** How near a look has to pass a button's middle to press it. */
    public static final double BUTTON_RADIUS = 0.22;

    private static double[][] closeButtons() {
        double[][] out = new double[STOP_BUTTONS.length][];
        for (int i = 0; i < out.length; i++) {
            out[i] = new double[] {STOP_BUTTONS[i][0], STOP_BUTTONS[i][1] + 0.34, STOP_BUTTONS[i][2]};
        }
        return out;
    }

    private static double[][] stopButtons() {
        double[][] out = new double[DOORS.length * 2][];
        int i = 0;
        for (double d : DOORS) {
            for (int side = -1; side <= 1; side += 2) {
                out[i++] = new double[] {side * 1.24, 1.35, d + 1.25};
            }
        }
        return out;
    }
    public static final double SEAT_DEPTH = 0.5, SEAT_HEIGHT = 0.45;

    /** Where a car is: its floor's middle (top of the floor), and which way it faces. */
    public record Pose(double x, double y, double z, double yaw) {
        public double cos() {
            return Math.cos(yaw);
        }

        public double sin() {
            return Math.sin(yaw);
        }

        /** Minecraft's yaw in degrees (0 faces south). */
        public float yawDegrees() {
            return (float) Math.toDegrees(yaw);
        }

        public Vec3 toWorld(double lx, double ly, double lz) {
            double c = Math.cos(yaw), s = Math.sin(yaw);
            return new Vec3(x + c * lx - s * lz, y + ly, z + s * lx + c * lz);
        }

        public Vec3 toLocal(Vec3 at) {
            double c = Math.cos(yaw), s = Math.sin(yaw), dx = at.x - x, dz = at.z - z;
            return new Vec3(c * dx + s * dz, at.y - y, -s * dx + c * dz);
        }

        /** A world direction turned into the car's frame. */
        public Vec3 dirToLocal(Vec3 d) {
            double c = Math.cos(yaw), s = Math.sin(yaw);
            return new Vec3(c * d.x + s * d.z, d.y, -s * d.x + c * d.z);
        }

        /** A direction in the car's frame turned out into the world. */
        public Vec3 dirToWorld(Vec3 d) {
            double c = Math.cos(yaw), s = Math.sin(yaw);
            return new Vec3(c * d.x - s * d.z, d.y, s * d.x + c * d.z);
        }
    }

    /** Where car (0 at the front) of a train whose front car's middle is at sFront stands. */
    public static Pose pose(Route route, double sFront, int car) {
        double s = sFront - car * PITCH;
        double fx = route.x(s + BOGIE), fz = route.z(s + BOGIE), rx = route.x(s - BOGIE), rz = route.z(s - BOGIE);
        double fy = route.y(s + BOGIE), ry = route.y(s - BOGIE);
        double dx = fx - rx, dz = fz - rz;
        double yaw = Math.atan2(-dx, dz);
        return new Pose((fx + rx) / 2, (fy + ry) / 2 + FLOOR, (fz + rz) / 2, yaw);
    }

    /** Distance along the line of the middle of a train of so many cars, when its front car is at sFront. */
    public static double middle(double sFront, int cars) {
        return sFront - (cars - 1) * PITCH / 2;
    }

    private static final Map<Integer, List<double[]>> SHAPES = new HashMap<>();

    /**
     * The solid boxes of a car, {x0, y0, z0, x1, y1, z1} in its frame: floor, walls with door gaps
     * (left open on a side whose doors are open), ends with gangways, roof, cab, seats.
     */
    public static List<double[]> boxes(int car, int cars, boolean openPlus, boolean openMinus) {
        boolean front = car == 0, back = car == cars - 1;
        int key = (front ? 1 : 0) | (back ? 2 : 0) | (openPlus ? 4 : 0) | (openMinus ? 8 : 0);
        return SHAPES.computeIfAbsent(key, k -> build(front, back, openPlus, openMinus));
    }

    private static List<double[]> build(boolean front, boolean back, boolean openPlus, boolean openMinus) {
        List<double[]> b = new ArrayList<>();
        double w = HALF_WIDTH, t = WALL;
        // the floor: a whole block deep, so nothing falls through at speed
        b.add(new double[] {-w, -1, -HALF, w, 0, HALF});
        // gangway floors bridge the gap to the next car
        if (!front) {
            b.add(new double[] {-GANGWAY_HALF - 0.15, -0.3, HALF, GANGWAY_HALF + 0.15, 0, PITCH / 2 + 0.02});
            b.add(new double[] {GANGWAY_HALF, 0, HALF, GANGWAY_HALF + 0.15, GANGWAY_HEIGHT, PITCH / 2});
            b.add(new double[] {-GANGWAY_HALF - 0.15, 0, HALF, -GANGWAY_HALF, GANGWAY_HEIGHT, PITCH / 2});
            b.add(new double[] {-GANGWAY_HALF - 0.15, GANGWAY_HEIGHT, HALF, GANGWAY_HALF + 0.15, GANGWAY_HEIGHT + 0.2, PITCH / 2});
        }
        if (!back) {
            b.add(new double[] {-GANGWAY_HALF - 0.15, -0.3, -PITCH / 2 - 0.02, GANGWAY_HALF + 0.15, 0, -HALF});
            b.add(new double[] {GANGWAY_HALF, 0, -PITCH / 2, GANGWAY_HALF + 0.15, GANGWAY_HEIGHT, -HALF});
            b.add(new double[] {-GANGWAY_HALF - 0.15, 0, -PITCH / 2, -GANGWAY_HALF, GANGWAY_HEIGHT, -HALF});
            b.add(new double[] {-GANGWAY_HALF - 0.15, GANGWAY_HEIGHT, -PITCH / 2, GANGWAY_HALF + 0.15, GANGWAY_HEIGHT + 0.2, -HALF});
        }
        // roof and ceiling
        b.add(new double[] {-w, CEILING, -HALF, w, ROOF, HALF});
        // side walls, with the door gaps where those doors are open
        for (int side = -1; side <= 1; side += 2) {
            boolean open = side > 0 ? openPlus : openMinus;
            double x0 = side > 0 ? w - t : -w, x1 = side > 0 ? w : -w + t;
            double z = -HALF;
            for (double d : DOORS) {
                b.add(new double[] {x0, 0, z, x1, CEILING, d - DOOR_HALF});
                if (!open) {
                    b.add(new double[] {x0, 0, d - DOOR_HALF, x1, CEILING, d + DOOR_HALF});
                } else {
                    b.add(new double[] {x0, DOOR_HEIGHT, d - DOOR_HALF, x1, CEILING, d + DOOR_HALF});
                }
                z = d + DOOR_HALF;
            }
            b.add(new double[] {x0, 0, z, x1, CEILING, HALF});
        }
        // ends: shut at the train's ends, a gangway door in the middle between cars
        endWall(b, HALF - t, HALF, front);
        endWall(b, -HALF, -HALF + t, back);
        if (front) {
            // the cab partition: the conductor's side is out of bounds
            b.add(new double[] {-w, 0, CAB, w, CEILING, CAB + t});
        }
        // bench seats along the walls between the doors
        for (int side = -1; side <= 1; side += 2) {
            double x0 = side > 0 ? w - t - SEAT_DEPTH : -w + t, x1 = side > 0 ? w - t : -w + t + SEAT_DEPTH;
            for (double[] run : seatRuns(front)) {
                b.add(new double[] {x0, 0, run[0], x1, SEAT_HEIGHT, run[1]});
            }
        }
        return b;
    }

    /** The z spans of the benches: between the doors, and between the end doors and the ends. */
    public static List<double[]> seatRuns(boolean front) {
        List<double[]> runs = new ArrayList<>();
        double edge = 0.25;
        runs.add(new double[] {-HALF + 0.6, DOORS[0] - DOOR_HALF - edge});
        runs.add(new double[] {DOORS[0] + DOOR_HALF + edge, DOORS[1] - DOOR_HALF - edge});
        runs.add(new double[] {DOORS[1] + DOOR_HALF + edge, DOORS[2] - DOOR_HALF - edge});
        runs.add(new double[] {DOORS[2] + DOOR_HALF + edge, (front ? CAB : HALF) - 0.6});
        return runs;
    }

    private static void endWall(List<double[]> b, double z0, double z1, boolean shut) {
        double w = HALF_WIDTH;
        if (shut) {
            b.add(new double[] {-w, 0, z0, w, CEILING, z1});
            return;
        }
        b.add(new double[] {-w, 0, z0, -GANGWAY_HALF, CEILING, z1});
        b.add(new double[] {GANGWAY_HALF, 0, z0, w, CEILING, z1});
        b.add(new double[] {-GANGWAY_HALF, GANGWAY_HEIGHT, z0, GANGWAY_HALF, CEILING, z1});
    }

    /** Whether a point (feet, in the car's frame) is inside the car or standing on its roof. */
    public static boolean aboard(Vec3 local, boolean front, boolean back) {
        double lz = local.z;
        double zMax = front ? HALF : PITCH / 2, zMin = back ? -HALF : -PITCH / 2;
        if (lz > zMax || lz < zMin) {
            return false;
        }
        if (Math.abs(local.x) < HALF_WIDTH - 0.05 && local.y > -0.25 && local.y < CEILING - 0.5) {
            return true;
        }
        return Math.abs(local.x) < HALF_WIDTH + 0.1 && Math.abs(lz) < HALF && local.y > ROOF - 0.25 && local.y < ROOF + 2.5;
    }

    private TrainCar() {
    }
}
