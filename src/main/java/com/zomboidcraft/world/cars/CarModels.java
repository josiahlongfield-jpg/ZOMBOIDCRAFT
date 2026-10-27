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
