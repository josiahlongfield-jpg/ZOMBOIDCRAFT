package com.zomboidcraft.client.cars;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zomboidcraft.ZomboidCraft;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

/**
 * Where each car's lamps are (assets/zomboidcraft/cars/lamps.json, written by tools/cars/lamps.py):
 * runs of a mesh's solid triangles that are headlight lenses, tail lights, reversing lights or the
 * dash's dials and radio display, per model and state, and a spot on the dash for models without
 * dials. From the full-detail mesh it works out where each lamp is, for its glow and beam.
 */
public final class CarLamps {
    public static final int HEAD = 1, TAIL = 2, REVERSE = 3, DASH = 4;
    /** The cabin kit's backlit faces (dials, radio, CB, taxi meter): drawn at full brightness, in their own colours. */
    public static final int PANEL = 5;

    /** count triangles from start in a mesh (0 the body, then each lid) are a lamp of this kind. */
    public record Run(int mesh, int kind, int start, int count) {
    }

    /** One lamp on one side of the car (or the whole dash): its middle, the way it faces, and its rough size. */
    public record Spot(int kind, float x, float y, float z, float nx, float ny, float nz, float size) {
    }

    /** panel: which of the body's solid triangles are backlit. */
    public record Lamps(List<Run> runs, List<Spot> spots, float[] dash, java.util.BitSet panel) {
        public Spot first(int kind) {
            for (Spot s : spots) {
                if (s.kind() == kind) {
                    return s;
                }
            }
            return null;
        }
    }

    private static Map<String, JsonObject> file;
    private static final Map<String, Lamps> CACHE = new HashMap<>();

    /** A model's lamps in a state, worked out from its full-detail mesh; null if it has none. Render thread only. */
    public static Lamps get(String model, String state, CarAssets.Ready full) {
        String key = model + "/" + state;
        Lamps lamps = CACHE.get(key);
        if (lamps == null && !CACHE.containsKey(key)) {
            lamps = build(model, state, full);
            CACHE.put(key, lamps);
        }
        return lamps;
    }

    private static Lamps build(String model, String state, CarAssets.Ready full) {
        JsonObject m = load().get(model);
        if (m == null || full == null) {
            return null;
        }
        JsonArray dash = m.getAsJsonArray("dash");
        float[] d = new float[6];
        for (int i = 0; i < 6 && dash != null && i < dash.size(); i++) {
            d[i] = dash.get(i).getAsFloat();
        }
        JsonArray list = m.has(state) ? m.getAsJsonArray(state) : m.getAsJsonArray("usable");
        List<Run> runs = new ArrayList<>();
        for (JsonElement e : list == null ? new JsonArray() : list) {
            JsonArray r = e.getAsJsonArray();
            CarAssets.Mesh mesh = mesh(full, r.get(0).getAsInt());
            int start = r.get(2).getAsInt(), count = r.get(3).getAsInt();
            if (mesh != null && start >= 0 && start + count <= mesh.opaque()) {
                runs.add(new Run(r.get(0).getAsInt(), r.get(1).getAsInt(), start, count));
            }
        }
        java.util.BitSet panel = new java.util.BitSet();
        for (Run run : runs) {
            if (run.kind() == PANEL && run.mesh() == 0) {
                panel.set(run.start(), run.start() + run.count());
            }
        }
        List<Run> lamps = runs.stream().filter(r -> r.kind() != PANEL).toList();
        return new Lamps(lamps, spots(lamps, full, d), d, panel);
    }

    /** Mesh 0 is the body, then the lids in order. */
    public static CarAssets.Mesh mesh(CarAssets.Ready car, int index) {
        if (index == 0) {
            return car.mesh();
        }
        return index - 1 < car.lids().size() ? car.lids().get(index - 1).mesh() : null;
    }

    /** Each kind of lamp split by side (the dash kept whole), averaged by area. */
    private static List<Spot> spots(List<Run> runs, CarAssets.Ready full, float[] dash) {
        // kind, side: area, sum of centre*area, sum of normal*area, min and max x/y
        Map<Integer, double[]> sums = new HashMap<>();
        for (Run run : runs) {
            CarAssets.Mesh mesh = mesh(full, run.mesh());
            float[] p = mesh.pos(), n = mesh.normal();
            for (int t = run.start(); t < run.start() + run.count(); t++) {
                int a = t * 9;
                float cx = (p[a] + p[a + 3] + p[a + 6]) / 3, cy = (p[a + 1] + p[a + 4] + p[a + 7]) / 3, cz = (p[a + 2] + p[a + 5] + p[a + 8]) / 3;
                float ux = p[a + 3] - p[a], uy = p[a + 4] - p[a + 1], uz = p[a + 5] - p[a + 2];
                float vx = p[a + 6] - p[a], vy = p[a + 7] - p[a + 1], vz = p[a + 8] - p[a + 2];
                double area = 0.5 * Math.sqrt(Math.pow(uy * vz - uz * vy, 2) + Math.pow(uz * vx - ux * vz, 2) + Math.pow(ux * vy - uy * vx, 2)) + 1e-6;
                int key = run.kind() * 2 + (run.kind() == DASH || cx >= 0 ? 0 : 1);
                double[] s = sums.computeIfAbsent(key, k -> new double[]{0, 0, 0, 0, 0, 0, 0, 1e9, -1e9, 1e9, -1e9});
                s[0] += area;
                s[1] += cx * area; s[2] += cy * area; s[3] += cz * area;
                s[4] += n[t * 3] * area; s[5] += n[t * 3 + 1] * area; s[6] += n[t * 3 + 2] * area;
                s[7] = Math.min(s[7], cx); s[8] = Math.max(s[8], cx);
                s[9] = Math.min(s[9], cy); s[10] = Math.max(s[10], cy);
            }
        }
        List<Spot> out = new ArrayList<>();
        sums.forEach((key, s) -> {
            double len = Math.sqrt(s[4] * s[4] + s[5] * s[5] + s[6] * s[6]) + 1e-9;
            float size = (float) Math.max(0.12, Math.max(s[8] - s[7], s[10] - s[9]));
            out.add(new Spot(key / 2, (float) (s[1] / s[0]), (float) (s[2] / s[0]), (float) (s[3] / s[0]),
                    (float) (s[4] / len), (float) (s[5] / len), (float) (s[6] / len), size));
        });
        if (out.stream().noneMatch(s -> s.kind() == DASH)) {
            out.add(new Spot(DASH, dash[0], dash[1], dash[2], dash[3], dash[4], dash[5], 0.3f));
        }
        return out;
    }

    private static Map<String, JsonObject> load() {
        if (file == null) {
            file = new HashMap<>();
            try (InputStream in = Minecraft.getInstance().getResourceManager().open(ZomboidCraft.id("cars/lamps.json"))) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                    file.put(e.getKey(), e.getValue().getAsJsonObject());
                }
            } catch (Exception e) {
                ZomboidCraft.LOGGER.error("Couldn't load car lamps", e);
            }
        }
        return file;
    }

    private CarLamps() {
    }
}
