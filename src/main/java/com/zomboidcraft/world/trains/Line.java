package com.zomboidcraft.world.trains;

import java.util.ArrayList;
import java.util.List;

/**
 * A train line: its track, its stops and the trains that loop it. The same on both sides; the
 * server also runs the trains ({@link TrainRun}), the client only draws the track and the cars.
 */
public final class Line {
    public enum Kind {
        RAIL, SUBWAY;

        public static Kind of(String name) {
            return "subway".equalsIgnoreCase(name) ? SUBWAY : RAIL;
        }

        public String id() {
            return name().toLowerCase();
        }

        public double radius() {
            return this == SUBWAY ? 30 : 40;
        }
    }

    /** A stop: its name, where along the line the middle of a stopped train is, the platform's side, and that point. */
    public record Stop(String name, double s, int side, double x, double z) {
    }

    public final String id;
    public final Kind kind;
    public final String name;
    public final List<double[]> points;
    public final List<Stop> stops = new ArrayList<>();
    /** Level crossings, as distances along the line: OTTO sounds the horn coming up to each. */
    public final List<Double> crossings = new ArrayList<>();
    public final int cars;
    public final Route route;
    /** Server only: the trains running it. */
    public final List<TrainRun> trains = new ArrayList<>();
    /** Server only: the stops whose call button is pressed, waiting for a train. */
    public final List<Integer> calls = new ArrayList<>();

    public Line(String id, Kind kind, String name, List<double[]> points, int cars, double lateral) {
        this.id = id;
        this.kind = kind;
        this.name = name;
        this.points = points;
        this.cars = Math.max(1, cars);
        this.route = new Route(points, kind.radius(), lateral);
    }

    /** Adds a stop at a point on (or beside) its platform. */
    public Stop addStop(String name, double x, double z) {
        double s = route.project(x, z);
        int side = route.side(s, x, z);
        Stop stop = new Stop(name, s, side, x, z);
        stops.add(stop);
        return stop;
    }

    /** Where the front car's middle is when the train stands at this stop, its middle at the stop. */
    public double frontAt(Stop stop) {
        return route.wrapS(stop.s() + (cars - 1) * TrainCar.PITCH / 2);
    }

    /** The stop nearest (x, z) within range blocks, or -1. */
    public int nearestStop(double x, double z, double range) {
        int best = -1;
        double bestD = range * range;
        for (int i = 0; i < stops.size(); i++) {
            Stop st = stops.get(i);
            double sx = route.x(st.s()), sz = route.z(st.s());
            // anywhere along the platform counts, so measure to the platform's run, not just its middle
            double along = Math.abs(route.diff(st.s(), route.project(x, z)));
            double d = along < platformHalf() ? sq(route.distance(route.project(x, z), x, z)) : sq(sx - x) + sq(sz - z);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** Half a train's length: a platform reaches at least this far either side of its stop. */
    public double platformHalf() {
        return cars * TrainCar.PITCH / 2;
    }

    private static double sq(double v) {
        return v * v;
    }
}
