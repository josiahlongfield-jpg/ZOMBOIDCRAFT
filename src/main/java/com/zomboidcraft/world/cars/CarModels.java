package com.zomboidcraft.world.cars;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * The car models the mod ships (built by tools/cars/pack.py): their sizes, how common each is,
 * and a coarse height map of each body used to lay invisible collision blocks under the model.
 * Read from data/zomboidcraft/zc_cars/models.json on the classpath, so the server has it too.
 */
public final class CarModels {
    /** One vehicle. Model space: metres, wheels on y = 0, nose toward +z, centred on x and z. */
    public static final class Model {
        public String id;
        public String kind;
        public int weight;
        public float[] size;
        public float cell, x0, z0;
        public int nx, nz;
        public int[] heights;
        /** Eye positions of the seats in model space, the driver's first (left, +x). */
        public float[][] seats;
        /** Front axle to back axle, metres: how tight the car turns. */
        public float wheelbase;
        /** The outside door handle each seat is got into by (null where there's none to show), in model space. */
        public float[][] handles;
        /** The steering wheel's rim: centre x, y, z, the normal toward the driver, radius; or null. */
        public float[] steering;
        /** The middle of the glovebox's lid, or null. */
        public float[] glovebox;
        /**
         * The body's underside over each cell in centimetres, or null: a jet's wings stand clear
         * of the deck, so its hull leaves the room under them open to walk through.
         */
        public int[] floors;
        /**
         * Per look (variants order), each window's pane: centre, outward normal, two half axes, kind, holes it
         * takes, then its outline; null where that look has it broken out. See {@link CarGlass}.
         */
        public float[][][] windows;
        /** Per look, per mesh (the body, then each lid), the window each of its glass triangles belongs to. */
        public int[][][] glassTris;
        transient CarGlass.Window[][] parsedWindows;

        public float length() {
            return size[2];
        }

        /** Highest point of the body above (lx, lz) in metres, 0 off the car. */
        public float heightAt(double lx, double lz) {
            int ix = (int) Math.floor((lx - x0) / cell), iz = (int) Math.floor((lz - z0) / cell);
            if (ix < 0 || iz < 0 || ix >= nx || iz >= nz) {
                return 0;
            }
            return heights[iz * nx + ix] / 100f;
        }

        /** Lowest point of the body above (lx, lz) in metres, 0 off the car or where it meets the ground. */
        public float floorAt(double lx, double lz) {
            int ix = (int) Math.floor((lx - x0) / cell), iz = (int) Math.floor((lz - z0) / cell);
            if (floors == null || ix < 0 || iz < 0 || ix >= nx || iz >= nz) {
                return 0;
            }
            return floors[iz * nx + ix] / 100f;
        }

        /** The carrier's fighters: parked props you can sit in, never on the street, never driven. */
        public boolean jet() {
            return "jet".equals(kind);
        }
    }

    private static final class Index {
        List<String> variants;
        List<Model> models;
    }

    private static Map<String, Model> models;
    private static List<String> variants;

    private static synchronized void load() {
        if (models != null) {
            return;
        }
        Map<String, Model> found = new LinkedHashMap<>();
        List<String> names = List.of("usable", "abandoned", "wrecked", "burnt");
        try (InputStream in = CarModels.class.getResourceAsStream("/data/zomboidcraft/zc_cars/models.json")) {
            if (in != null) {
                Index index = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Index.class);
                for (Model m : index.models) {
                    seatToWheel(m);
                    found.put(m.id, m);
                }
                names = index.variants;
            }
        } catch (Exception e) {
            com.zomboidcraft.ZomboidCraft.LOGGER.error("Couldn't read the car models", e);
        }
        variants = names;
        models = found;
    }

    /**
     * Where the driver's eyes sit against the steering wheel, the same in every vehicle: this far behind the
     * rim's nearest point, the wheel's middle this far below, and square behind it. The pack's seats put some
     * drivers twice as far back as others; this sits them all close enough to see out over the dash.
     */
    private static final float WHEEL_GAP = 0.18f, WHEEL_DROP = 0.28f;

    /** Seats the driver against the wheel (cars with one only); whoever sits beside them moves up or back as much. */
    private static void seatToWheel(Model m) {
        if (m.steering == null || m.seats == null || m.seats.length == 0) {
            return;
        }
        float[] w = m.steering, eye = m.seats[0];
        // the rim's nearest point to the driver: down the wheel's face from its middle
        float ny = w[4], nz = w[5], upY = 1 - ny * ny, upZ = -ny * nz, len = (float) Math.sqrt(upY * upY + upZ * upZ);
        float nearZ = len < 1e-4f ? w[2] : w[2] - upZ / len * w[6];
        float front = eye[2], z = nearZ - WHEEL_GAP;
        for (float[] seat : m.seats) {
            if (Math.abs(seat[2] - front) < 0.3f) {
                seat[2] += z - front;
            }
        }
        eye[0] = w[0];
        eye[1] = w[1] + WHEEL_DROP;
    }

    public static Map<String, Model> all() {
        load();
        return models;
    }

    public static List<String> variants() {
        load();
        return variants;
    }

    public static Model get(String id) {
        return all().get(id);
    }

    /** A weighted pick among the models that pass the filter, or null if none do. */
    public static Model pick(RandomGenerator rng, Predicate<Model> filter) {
        int total = 0;
        for (Model m : all().values()) {
            if (filter.test(m)) {
                total += m.weight;
            }
        }
        if (total == 0) {
            return null;
        }
        int r = rng.nextInt(total);
        for (Model m : all().values()) {
            if (filter.test(m) && (r -= m.weight) < 0) {
                return m;
            }
        }
        return null;
    }

    private CarModels() {
    }
}
