package com.zomboidcraft.world.lifts;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Where a lift runs: the shaft under its machine and the landings its doors open onto. The machine
 * sits over the shaft's front corner cell; the doors are in the wall one block out from the shaft
 * the way the machine faces (facing f), the shaft runs w cells to the right as you look at the
 * machine's face (r = f turned clockwise) and d cells back from the doors (b, away from f).
 * Landings are the heights of the floors the car stands level with, bottom up; the lobby is the
 * one whose doors were marked as the street floor (numbered 1, the floors under it B1, B2 ...);
 * a shaft that starts higher up has its lobby below its bottom landing (a negative index).
 *
 * Both sides build it the same way: the server from the blocks, the client from the strings the
 * car is sent ({@link #geo()} and {@link #landingsString()}).
 */
public final class LiftShaft {
    /** How high the car is: floor to the top of its roof, and floor to its ceiling. */
    public static final double ROOF = 2.96, CEILING = 2.78;
    /** How high the doors are, landing and car alike. */
    public static final double DOOR = 2.0;

    public final int mx, my, mz, w, d, lobby;
    public final Direction f, r, b;
    public final int[] landings;
    /** The corner of the shaft on the doors' side, to the left of cell 0 (where u = v = 0). */
    public final double ox, oz;
    /** The footprint in world x and z. */
    public final double minX, maxX, minZ, maxZ;

    public LiftShaft(int mx, int my, int mz, Direction f, int w, int d, int[] landings, int lobby) {
        this.mx = mx;
        this.my = my;
        this.mz = mz;
        this.f = f;
        this.r = f.getClockWise();
        this.b = f.getOpposite();
        this.w = w;
        this.d = d;
        this.landings = landings;
        this.lobby = Math.min(landings.length - 1, lobby);
        this.ox = mx + 0.5 - 0.5 * r.getStepX() - 0.5 * b.getStepX();
        this.oz = mz + 0.5 - 0.5 * r.getStepZ() - 0.5 * b.getStepZ();
        double x2 = wx(w, d), z2 = wz(w, d);
        this.minX = Math.min(ox, x2);
        this.maxX = Math.max(ox, x2);
        this.minZ = Math.min(oz, z2);
        this.maxZ = Math.max(oz, z2);
    }

    /** World x and z of a point u along the doors (to the right) and v back from them. */
    public double wx(double u, double v) {
        return ox + u * r.getStepX() + v * b.getStepX();
    }

    public double wz(double u, double v) {
        return oz + u * r.getStepZ() + v * b.getStepZ();
    }

    public double centreX() {
        return wx(w / 2.0, d / 2.0);
    }

    public double centreZ() {
        return wz(w / 2.0, d / 2.0);
    }

    /** Whether a point's in the shaft's footprint, shrunk by margin. */
    public boolean over(double x, double z, double margin) {
        return x > minX + margin && x < maxX - margin && z > minZ + margin && z < maxZ - margin;
    }

    /** Whether a box's footprint overlaps the shaft's. */
    public boolean overlaps(double x0, double z0, double x1, double z1) {
        return x1 > minX + 1e-3 && x0 < maxX - 1e-3 && z1 > minZ + 1e-3 && z0 < maxZ - 1e-3;
    }

    /** The door block a landing's leaf a (0 .. w - 1) stands in, lower half. */
    public BlockPos door(int landing, int a) {
        return new BlockPos(mx + f.getStepX() + a * r.getStepX(), landings[landing], mz + f.getStepZ() + a * r.getStepZ());
    }

    /** The call button at a landing: on the wall to the right of the doors, a block over the floor. */
    public BlockPos button(int landing) {
        return new BlockPos(mx + 2 * f.getStepX() + w * r.getStepX(), landings[landing] + 1, mz + 2 * f.getStepZ() + w * r.getStepZ());
    }

    /** The landing a height is at, or -1. */
    public int landingAt(int y) {
        for (int i = 0; i < landings.length; i++) {
            if (landings[i] == y) {
                return i;
            }
        }
        return -1;
    }

    /** The landing the car is at or last passed going the way it's going (what the indicators show). */
    public int showing(double y, int dir) {
        // going up it counts on a floor before it gets there; going down, as soon as it leaves the one above
        double ahead = dir > 0 ? 1.5 : dir < 0 ? 0.05 : 0.5;
        int best = 0;
        for (int i = 0; i < landings.length; i++) {
            if (landings[i] <= y + ahead) {
                best = i;
            }
        }
        return best;
    }

    /** What a landing is called on the buttons and indicators: 1 for the lobby, B1, B2 under it. */
    public String label(int i) {
        int n = i - lobby;
        return n >= 0 ? Integer.toString(n + 1) : "B" + -n;
    }

    /** Room over a landing before the next: the doors' header is a block over them when there's 4. */
    public int pitch(int i) {
        return i + 1 < landings.length ? landings[i + 1] - landings[i] : 4;
    }

    public String geo() {
        return mx + "," + my + "," + mz + "," + f.get2DDataValue() + "," + w + "," + d;
    }

    public String landingsString() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < landings.length; i++) {
            s.append(i > 0 ? "," : "").append(landings[i]);
        }
        return s.append(';').append(lobby).toString();
    }

    /** Back from the strings the car carries, or null if they're empty or broken. */
    public static LiftShaft parse(String geo, String landings) {
        try {
            String[] g = geo.split(","), l = landings.split(";");
            if (g.length < 6 || l.length < 2) {
                return null;
            }
            String[] ys = l[0].split(",");
            int[] ls = new int[ys.length];
            for (int i = 0; i < ys.length; i++) {
                ls[i] = Integer.parseInt(ys[i]);
            }
            return new LiftShaft(Integer.parseInt(g[0]), Integer.parseInt(g[1]), Integer.parseInt(g[2]),
                    Direction.from2DDataValue(Integer.parseInt(g[3])), Integer.parseInt(g[4]), Integer.parseInt(g[5]), ls,
                    Integer.parseInt(l[1]));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
