package com.zomboidcraft.world.cars;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A car's windows, each its own pane of glass (from tools/cars/glass.py): a bullet goes through one and leaves a
 * hole where it struck, and after enough holes (a laminated windscreen holds on to many, a side window gives after
 * one or two) it shatters. Side windows wind down out of the way, so you can shoot out without blowing them; a
 * zombie gets at you through the window nearest it once that's open or gone.
 * <p>
 * What's become of the windows travels with the car as text ({@link Panes#encode}): per window a state (up, down,
 * shattered), then its marks, each a bullet hole or a crack from a blow, at exactly where on the glass it is.
 */
public final class CarGlass {
    public static final int WINDSCREEN = 0, SIDE = 1, REAR = 2, OTHER = 3;
    public static final byte UP = 0, DOWN = 1, SHATTERED = 2;

    /**
     * One window, in model space: its middle, its outward normal, its two half axes (the rectangle it fits in, u
     * and v running from -1 to 1 along them), what kind it is, how many holes it takes, and its outline in u, v.
     */
    public record Window(int index, Vec3 centre, Vec3 normal, Vec3 a, Vec3 b, int kind, int holes, float[] ring) {
        /** Where on the window a (u, v) is, in model space. */
        public Vec3 at(double u, double v) {
            return centre.add(a.scale(u)).add(b.scale(v));
        }

        /** Whether (u, v) is on the glass, not just in its rectangle. */
        public boolean on(double u, double v) {
            int n = ring.length / 2;
            if (n < 3) {
                return Math.abs(u) <= 1 && Math.abs(v) <= 1;
            }
            for (int i = 0; i < n; i++) {
                double x0 = ring[i * 2], y0 = ring[i * 2 + 1], x1 = ring[(i + 1) % n * 2], y1 = ring[(i + 1) % n * 2 + 1];
                if ((x1 - x0) * (v - y0) - (y1 - y0) * (u - x0) < -1e-4) {
                    return false;
                }
            }
            return true;
        }

        /** Whether it winds down: the side windows do. */
        public boolean winds() {
            return kind == SIDE;
        }

        /**
         * Down the glass, in its own plane (unit, model space): the way it winds down into the door. Lowered, every
         * point of it goes this way by {@link #drop()}, as far as the sill ({@link #sill()}) and no further.
         */
        public Vec3 down() {
            Vec3 d = new Vec3(0, -1, 0);
            d = d.subtract(normal.scale(d.dot(normal)));
            return d.lengthSqr() < 1e-6 ? b.normalize() : d.normalize();
        }

        /** How far down the glass its sill is from its middle, along {@link #down()} (metres). */
        public double sill() {
            Vec3 d = down();
            double low = -Double.MAX_VALUE;
            for (int i = 0; i + 1 < ring.length; i += 2) {
                low = Math.max(low, at(ring[i], ring[i + 1]).subtract(centre).dot(d));
            }
            return low == -Double.MAX_VALUE ? a.dot(d) + b.dot(d) : low;
        }

        /** How far it goes down when wound right down: its whole height. */
        public double drop() {
            Vec3 d = down();
            double high = Double.MAX_VALUE;
            for (int i = 0; i + 1 < ring.length; i += 2) {
                high = Math.min(high, at(ring[i], ring[i + 1]).subtract(centre).dot(d));
            }
            return high == Double.MAX_VALUE ? 0 : sill() - high;
        }

        /**
         * Where a ray (model space) meets this window: { distance along dir, u, v }, or null if it misses it or
         * meets it further than far.
         */
        public double[] meet(Vec3 from, Vec3 dir, double far) {
            double facing = dir.dot(normal);
            if (Math.abs(facing) < 1e-5) {
                return null;
            }
            double t = centre.subtract(from).dot(normal) / facing;
            if (t < 0 || t > far) {
                return null;
            }
            Vec3 p = from.add(dir.scale(t)).subtract(centre);
            double u = p.dot(a) / a.lengthSqr(), v = p.dot(b) / b.lengthSqr();
            return on(u, v) ? new double[]{t, u, v} : null;
        }
    }

    /** A model's windows in one of its looks (variant index), null where that look has the window broken out. */
    public static Window[] windows(CarModels.Model m, int variant) {
        if (m == null || m.windows == null || variant < 0 || variant >= m.windows.length || m.windows[variant] == null) {
            return new Window[0];
        }
        if (m.parsedWindows == null) {
            Window[][] all = new Window[m.windows.length][];
            for (int v = 0; v < all.length; v++) {
                float[][] raw = m.windows[v] == null ? new float[0][] : m.windows[v];
                all[v] = new Window[raw.length];
                for (int i = 0; i < raw.length; i++) {
                    float[] w = raw[i];
                    if (w == null || w.length < 14) {
                        continue;
                    }
                    all[v][i] = new Window(i, new Vec3(w[0], w[1], w[2]), new Vec3(w[3], w[4], w[5]), new Vec3(w[6], w[7], w[8]),
                            new Vec3(w[9], w[10], w[11]), (int) w[12], Math.max(1, (int) w[13]),
                            java.util.Arrays.copyOfRange(w, 14, w.length));
                }
            }
            m.parsedWindows = all;
        }
        return m.parsedWindows[variant];
    }

    /** One mark on a window: where (u, v), and whether it's a crack from a blow rather than a bullet hole. */
    public record Mark(float u, float v, boolean crack) {
    }

    /** The windows of one car as they are now. */
    public static final class Panes {
        public final byte[] state;
        public final List<List<Mark>> marks;

        Panes(int n) {
            state = new byte[n];
            marks = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                marks.add(new ArrayList<>());
            }
        }

        public int size() {
            return state.length;
        }

        /** Whether this window stands in the way: there, wound up and not shattered. */
        public boolean closed(int w) {
            return w >= 0 && w < state.length && state[w] == UP;
        }

        public int bulletHoles(int w) {
            int n = 0;
            for (Mark m : marks.get(w)) {
                n += m.crack ? 0 : 1;
            }
            return n;
        }

        /** Every window gone (or wound down): nothing between the inside and out. */
        public boolean allOpen() {
            for (byte s : state) {
                if (s == UP) {
                    return false;
                }
            }
            return true;
        }

        public boolean allShattered() {
            for (byte s : state) {
                if (s != SHATTERED) {
                    return false;
                }
            }
            return true;
        }

        /** As text: per window (comma between) its state 0/1/2, then each mark as two letters, a crack's after a *. */
        public String encode() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < state.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append((char) ('0' + state[i]));
                for (Mark m : marks.get(i)) {
                    if (m.crack) {
                        sb.append('*');
                    }
                    sb.append(letter(m.u)).append(letter(m.v));
                }
            }
            return sb.toString();
        }

        public static Panes decode(String text, int n) {
            Panes p = new Panes(n);
            if (text == null || text.isEmpty()) {
                return p;
            }
            String[] parts = text.split(",", -1);
            for (int i = 0; i < Math.min(n, parts.length); i++) {
                String s = parts[i];
                if (s.isEmpty()) {
                    continue;
                }
                p.state[i] = (byte) Math.max(0, Math.min(2, s.charAt(0) - '0'));
                boolean crack = false;
                for (int k = 1; k < s.length(); k++) {
                    if (s.charAt(k) == '*') {
                        crack = true;
                        continue;
                    }
                    if (k + 1 >= s.length()) {
                        break;
                    }
                    p.marks.get(i).add(new Mark(number(s.charAt(k)), number(s.charAt(k + 1)), crack));
                    crack = false;
                    k++;
                }
            }
            return p;
        }
    }

    /** Positions on the glass go in one letter each: 64 steps from -1 to 1 (a centimetre or two on a window). */
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    private static char letter(float x) {
        int q = Math.round((Math.max(-1, Math.min(1, x)) + 1) / 2 * 63);
        return LETTERS.charAt(q);
    }

    private static float number(char c) {
        int q = LETTERS.indexOf(c);
        return q < 0 ? 0 : q / 63f * 2 - 1;
    }

    /** A bullet's ray crossing one car's window: which car and window, how far along, and where on it. */
    public record Crossing(CarProp car, int window, double distance, float u, float v, Vec3 at) {
    }

    /**
     * Every car window a ray crosses between from and to that's still in its way (up and whole), nearest first.
     * Aircraft keep their old all-in-one canopies.
     */
    public static List<Crossing> along(Level level, Vec3 from, Vec3 to) {
        List<Crossing> out = new ArrayList<>();
        Vec3 dir = to.subtract(from);
        double far = dir.length();
        if (far < 1e-4) {
            return out;
        }
        dir = dir.scale(1 / far);
        for (CarProp car : level.getEntitiesOfClass(CarProp.class, new AABB(from, to).inflate(1), c -> !c.aircraft())) {
            Window[] ws = windows(CarModels.get(car.model()), car.variantIndex());
            if (ws.length == 0 || !car.getBoundingBox().inflate(0.5).clip(from, to).isPresent()
                    && !car.getBoundingBox().inflate(0.5).contains(from)) {
                continue;
            }
            Panes panes = car.panes();
            Vec3 o = car.toLocal(from), d = car.toLocal(from.add(dir)).subtract(o);
            for (Window w : ws) {
                if (w == null || !panes.closed(w.index())) {
                    continue;
                }
                double[] hit = w.meet(o, d, far);
                if (hit != null) {
                    out.add(new Crossing(car, w.index(), hit[0], (float) hit[1], (float) hit[2], from.add(dir.scale(hit[0]))));
                }
            }
        }
        out.sort(java.util.Comparator.comparingDouble(Crossing::distance));
        return out;
    }

    /**
     * A shot from from (along dir) that struck a parked car's hull (its invisible collision blocks) at hit: if it
     * struck there through one of the car's window openings (glass or not), where it leaves the car on the far
     * side, to look on from; null when it struck the bodywork.
     */
    public static Vec3 throughHull(Level level, BlockHitResult hit, Vec3 from, Vec3 dir) {
        BlockPos pos = hit.getBlockPos();
        double struck = hit.getLocation().distanceTo(from);
        for (CarProp car : level.getEntitiesOfClass(CarProp.class, new AABB(pos).inflate(4), c -> !c.aircraft())) {
            CarModels.Model m = CarModels.get(car.model());
            Window[] ws = windows(m, 0);
            if (ws.length == 0) {
                continue;
            }
            Vec3 cell = car.toLocal(Vec3.atCenterOf(pos));
            if (Math.abs(cell.x) > m.size[0] / 2 + 0.7 || Math.abs(cell.z) > m.size[2] / 2 + 0.7) {
                continue;   // not this car's hull
            }
            Vec3 o = car.toLocal(from), d = car.toLocal(from.add(dir)).subtract(o);
            double[] box = slab(o, d, m);
            if (box == null) {
                continue;
            }
            // the hull's blocks are coarser than the body: a window met near where it struck is the way it went in
            // (a look's broken-out windows go by where the whole car has them)
            for (Window w : ws) {
                double[] at = w == null ? null : w.meet(o, d, box[1]);
                if (at != null && Math.abs(at[0] - struck) < 0.9) {
                    return from.add(dir.scale(box[1] + 0.02));
                }
            }
            return null;
        }
        return null;
    }

    /** Where a ray (model space) enters and leaves a model's box: { in, out } along it, or null if it misses. */
    private static double[] slab(Vec3 o, Vec3 d, CarModels.Model m) {
        double[] lo = {-m.size[0] / 2, 0, -m.size[2] / 2}, hi = {m.size[0] / 2, m.size[1], m.size[2] / 2};
        double[] oo = {o.x, o.y, o.z}, dd = {d.x, d.y, d.z};
        double in = 0, out = Double.MAX_VALUE;
        for (int k = 0; k < 3; k++) {
            if (Math.abs(dd[k]) < 1e-9) {
                if (oo[k] < lo[k] || oo[k] > hi[k]) {
                    return null;
                }
                continue;
            }
            double t0 = (lo[k] - oo[k]) / dd[k], t1 = (hi[k] - oo[k]) / dd[k];
            in = Math.max(in, Math.min(t0, t1));
            out = Math.min(out, Math.max(t0, t1));
        }
        return in > out ? null : new double[]{in, out};
    }

    /**
     * The window a seat looks out of to the side, or -1 (the one that winds down for whoever sits there): on their
     * side of the car, nearest their eyes.
     */
    public static int seatWindow(CarModels.Model m, int variant, int seat) {
        if (m == null || m.seats == null || seat < 0 || seat >= m.seats.length) {
            return -1;
        }
        float[] eye = m.seats[seat];
        Vec3 e = new Vec3(eye[0], eye[1], eye[2]);
        int best = -1;
        double bestD = 2.2;
        for (Window w : windows(m, 0)) {
            if (w == null || !w.winds() || Math.signum(w.centre().x) != Math.signum(eye[0]) && Math.abs(eye[0]) > 0.15) {
                continue;
            }
            double d = Math.hypot(w.centre().z - e.z, (w.centre().y - e.y) * 0.5);
            if (d < bestD) {
                bestD = d;
                best = w.index();
            }
        }
        return best;
    }

    /** The window nearest something outside the car (model space), facing it: the one a zombie there bashes at. */
    public static Window facing(Window[] ws, Vec3 local) {
        Window best = null;
        double bestD = Double.MAX_VALUE;
        for (Window w : ws) {
            if (w == null) {
                continue;
            }
            Vec3 to = local.subtract(w.centre());
            double d = to.length() - (to.dot(w.normal()) > 0 ? 0.5 : 0);
            if (d < bestD) {
                bestD = d;
                best = w;
            }
        }
        return best;
    }

    private CarGlass() {
    }
}
