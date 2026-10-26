package com.zomboidcraft.client.cars;

import com.zomboidcraft.world.cars.CarModels;
import java.util.BitSet;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The steering wheel picked out of a car's body mesh, so it can be turned with the steering: the
 * triangles inside the rim's disc (rim, spokes and hub) from the rim models.json gives
 * (tools/cars/cabin.py). Kept by the triangle's index, so the dials' and lamps' indexes stand.
 */
final class CarSteering {
    private static final Map<CarAssets.Mesh, BitSet> FOUND = Collections.synchronizedMap(new WeakHashMap<>());
    private static final BitSet NONE = new BitSet();

    /** The steering wheel's triangles in a body mesh, or null if the model has none. */
    static BitSet of(CarAssets.Mesh mesh, CarModels.Model m) {
        if (m == null || m.steering == null || m.steering.length < 7) {
            return null;
        }
        BitSet found = FOUND.computeIfAbsent(mesh, k -> find(k, m.steering));
        return found.isEmpty() ? null : found;
    }

    private static BitSet find(CarAssets.Mesh mesh, float[] s) {
        float r = s[6] + 0.03f;
        float[] p = mesh.pos();
        BitSet out = new BitSet();
        for (int t = 0; t < mesh.opaque(); t++) {
            boolean in = true;
            for (int c = 0; c < 3 && in; c++) {
                int i = (t * 3 + c) * 3;
                float dx = p[i] - s[0], dy = p[i + 1] - s[1], dz = p[i + 2] - s[2];
                float along = dx * s[3] + dy * s[4] + dz * s[5];
                in = along > -0.07f && along < 0.1f && dx * dx + dy * dy + dz * dz - along * along < r * r;
            }
            if (in) {
                out.set(t);
            }
        }
        return out.isEmpty() ? NONE : out;
    }

    /** Turns a point (or, with normal set, a direction) about the wheel's axis by angle radians, in place. */
    static void turn(float[] v, boolean normal, float[] s, float cos, float sin) {
        float x = v[0], y = v[1], z = v[2];
        if (!normal) {
            x -= s[0];
            y -= s[1];
            z -= s[2];
        }
        float nx = s[3], ny = s[4], nz = s[5];
        float dot = x * nx + y * ny + z * nz;
        // Rodrigues: v cos + (n x v) sin + n (n . v)(1 - cos)
        float cx = ny * z - nz * y, cy = nz * x - nx * z, cz = nx * y - ny * x;
        x = x * cos + cx * sin + nx * dot * (1 - cos);
        y = y * cos + cy * sin + ny * dot * (1 - cos);
        z = z * cos + cz * sin + nz * dot * (1 - cos);
        if (!normal) {
            x += s[0];
            y += s[1];
            z += s[2];
        }
        v[0] = x;
        v[1] = y;
        v[2] = z;
    }

    private CarSteering() {
    }
}
