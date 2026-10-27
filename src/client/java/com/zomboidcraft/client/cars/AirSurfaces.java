package com.zomboidcraft.client.cars;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.cars.CarProp;
import com.zomboidcraft.world.cars.Flight;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A jet's control surfaces moving as the real jet's flight control system moves them
 * (assets/zomboidcraft/cars/ID.surfaces.json, from tools/cars/surfaces.py: each surface's triangles
 * and hinge). Angles in degrees, positive trailing edge down (a rudder's to the left, a leading-edge
 * flap's leading edge down):
 * <ul>
 * <li>F-22: the all-moving stabilators pitch together and split for roll; flaperons and ailerons roll
 * the wings, the flaperons drooping when slow for takeoff and landing; the twin rudders yaw and, as
 * the F-22 has no speed brake, toe out with the ailerons up and flaperons down to make one; the
 * leading-edge flaps droop with the angle of attack; the nozzle flaps vector the thrust up or down with
 * the stick and open wide in afterburner.</li>
 * <li>Su-27: stabilators pitch and split for roll; flaperons roll and droop when slow; rudders yaw.</li>
 * </ul>
 * With the engines off there's no hydraulic pressure and everything rests. The pilot's own jet follows
 * the stick he's holding; any other follows how it's actually turning.
 */
final class AirSurfaces {
    enum Kind { STAB, FLAPERON, AILERON, RUDDER, LEF, NOZZLE_UPPER, NOZZLE_LOWER }

    /** One surface: its kind, side (+1 left, -1 right) and hinge (pivot, unit axis) in model space. */
    record Part(Kind kind, int side, float[] pivot, float[] axis) {
    }

    /** A model's surfaces, part[t] the surface solid triangle t belongs to (-1 none), end[t] where its run ends. */
    record Set(Part[] parts, int triangles, byte[] part, int[] end) {
    }

    private static final Map<String, Set> SETS = new HashMap<>();
    /** Each jet's surface angles as drawn, and when they were last moved. */
    private static final Map<CarProp, float[]> ANGLES = new WeakHashMap<>();
    private static final Map<CarProp, Long> MOVED = new WeakHashMap<>();
    /** How fast the actuators drive a surface, degrees a second. */
    private static final float RATE = 75;

    private AirSurfaces() {
    }

    /** The model's surfaces if mesh is the full airframe they were found on, else null. Render thread only. */
    static Set of(String model, CarAssets.Mesh mesh) {
        Set set = SETS.computeIfAbsent(model, AirSurfaces::load);
        return set == null || set.triangles() != mesh.opaque() ? null : set;
    }

    private static Set load(String model) {
        var resource = Minecraft.getInstance().getResourceManager().getResource(ZomboidCraft.id("cars/" + model + ".surfaces.json"));
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStream in = resource.get().open()) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            int triangles = root.get("triangles").getAsInt();
            JsonArray list = root.getAsJsonArray("parts");
            Part[] parts = new Part[list.size()];
            byte[] part = new byte[triangles];
            Arrays.fill(part, (byte) -1);
            for (int i = 0; i < parts.length; i++) {
                JsonObject p = list.get(i).getAsJsonObject();
                parts[i] = new Part(Kind.valueOf(p.get("kind").getAsString().toUpperCase(java.util.Locale.ROOT)), p.get("side").getAsInt(),
                        floats(p.getAsJsonArray("pivot")), floats(p.getAsJsonArray("axis")));
                JsonArray runs = p.getAsJsonArray("runs");
                for (int r = 0; r + 1 < runs.size(); r += 2) {
                    int start = runs.get(r).getAsInt(), count = runs.get(r + 1).getAsInt();
                    Arrays.fill(part, Math.max(0, start), Math.min(triangles, start + count), (byte) i);
                }
            }
            int[] end = new int[triangles];
            for (int t = triangles - 1; t >= 0; t--) {
                end[t] = t == triangles - 1 || part[t + 1] != part[t] ? t + 1 : end[t + 1];
            }
            return new Set(parts, triangles, part, end);
        } catch (Exception e) {
            ZomboidCraft.LOGGER.error("Couldn't load the control surfaces of {}", model, e);
            return null;
        }
    }

    private static float[] floats(JsonArray a) {
        float[] out = new float[a.size()];
        int i = 0;
        for (JsonElement e : a) {
            out[i++] = e.getAsFloat();
        }
        return out;
    }

    /** Each surface's turn this frame (null where it sits at rest), eased toward where the controls put it. */
    static CarRenderer.Turn[] turns(CarProp car, String model, Set set) {
        float[] want = targets(car, model, set);
        float[] now = ANGLES.computeIfAbsent(car, c -> new float[set.parts().length]);
        long t = System.nanoTime();
        Long last = MOVED.put(car, t);
        float step = last == null ? 0 : Math.min(0.1f, (t - last) / 1e9f) * RATE;
        CarRenderer.Turn[] out = new CarRenderer.Turn[set.parts().length];
        for (int i = 0; i < out.length && i < now.length; i++) {
            now[i] += Mth.clamp(want[i] - now[i], -step, step);
            out[i] = Math.abs(now[i]) < 0.05f ? null : hinge(set.parts()[i], (float) Math.toRadians(now[i]));
        }
        return out;
    }

    /** Where each surface should be now: the stick, the rudder, speed, angle of attack and thrust. */
    private static float[] targets(CarProp car, String model, Set set) {
        Flight f = car.flight();
        float[] out = new float[set.parts().length];
        boolean pressure = f.rpm > 0.35f && !car.broken();
        if (!pressure) {
            return out;
        }
        float[] cmd = commands(car);
        float p = cmd[0], r = cmd[1], y = cmd[2];
        float speed = (float) f.vel.length(), aoa = (float) Math.toDegrees(f.aoa);
        boolean raptor = "jet_corvid".equals(model);
        // takeoff and landing: the trailing edge drooped for lift while slow
        float slow = Mth.clamp(((raptor ? 95 : 90) - speed) / 30, 0, 1);
        boolean brake = f.airbrake && !f.grounded;
        for (int i = 0; i < out.length; i++) {
            Part part = set.parts()[i];
            float s = part.side();
            out[i] = switch (part.kind()) {
                case STAB -> raptor ? Mth.clamp(-25 * p + 8 * s * r, -25, 25) : Mth.clamp(-20 * p + 10 * s * r, -20, 15);
                case FLAPERON -> raptor ? Mth.clamp(20 * slow + 20 * s * r + (brake ? 30 : 0), -25, 35)
                        : Mth.clamp(30 * slow + 20 * s * r, -20, 35);
                case AILERON -> Mth.clamp(25 * s * r - (brake ? 25 : 0), -30, 30);
                case RUDDER -> Mth.clamp(-30 * y + (brake ? 25 * s : 0), -30, 30);
                case LEF -> f.grounded ? 0 : Mth.clamp(1.1f * aoa + 10 * slow, -3, 32);
                // thrust vectoring: exhaust up to push the tail down and the nose up; the throat opens in afterburner
                case NOZZLE_UPPER -> f.grounded ? 0 : Mth.clamp(-20 * p, -20, 20) - (f.afterburner() ? 9 : 0);
                case NOZZLE_LOWER -> f.grounded ? 0 : Mth.clamp(-20 * p, -20, 20) + (f.afterburner() ? 9 : 0);
            };
        }
        return out;
    }

    /**
     * Pitch (nose up +), roll (right +) and yaw (nose right +) demands, -1 to 1: the stick the local pilot is
     * holding, or for anyone else's jet how it's turning this tick.
     */
    private static float[] commands(CarProp car) {
        float[] own = AirControls.command(car);
        if (own != null) {
            return own;
        }
        Flight f = car.flight();
        Quaternionf turn = new Quaternionf(f.qO).conjugate().mul(f.q);
        float angle = 2 * (float) Math.acos(Mth.clamp(turn.w, -1, 1));
        Vector3f axis = new Vector3f(turn.x, turn.y, turn.z);
        if (axis.lengthSquared() < 1e-12f) {
            return new float[3];
        }
        axis.normalize().mul((float) Math.toDegrees(angle) / Flight.DT);   // degrees a second about each body axis
        return new float[]{Mth.clamp(-axis.x / 28, -1, 1), Mth.clamp(axis.z / 190, -1, 1), Mth.clamp(-axis.y / 8, -1, 1)};
    }

    /** A surface turned by angle radians about its hinge (Rodrigues' rotation). */
    private static CarRenderer.Turn hinge(Part part, float angle) {
        float c = (float) Math.cos(angle), sn = (float) Math.sin(angle);
        float kx = part.axis()[0], ky = part.axis()[1], kz = part.axis()[2];
        float px = part.pivot()[0], py = part.pivot()[1], pz = part.pivot()[2];
        return (v, normal) -> {
            if (!normal) {
                v[0] -= px;
                v[1] -= py;
                v[2] -= pz;
            }
            float dot = kx * v[0] + ky * v[1] + kz * v[2];
            float cx = ky * v[2] - kz * v[1], cy = kz * v[0] - kx * v[2], cz = kx * v[1] - ky * v[0];
            float x = v[0] * c + cx * sn + kx * dot * (1 - c);
            float y = v[1] * c + cy * sn + ky * dot * (1 - c);
            float z = v[2] * c + cz * sn + kz * dot * (1 - c);
            v[0] = x;
            v[1] = y;
            v[2] = z;
            if (!normal) {
                v[0] += px;
                v[1] += py;
                v[2] += pz;
            }
        };
    }
}
