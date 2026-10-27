package com.zomboidcraft.client.trains;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * OTTO's model, made by tools/trains/otto.py (assets/zomboidcraft/models/train/otto.bin): quads in his
 * own frame (x across, y up from the cab floor, z toward the windscreen; his head faces -z, down the
 * train). Each quad is 25 floats: four corners (x, y, z), four atlas uvs, the normal, the part (0
 * still, 1 head, 3 jaw, 4 eyes) and how it's lit (1 the car's light, 2 glowing).
 */
final class OttoModel {
    static final int STRIDE = 25;
    static final float[] HEAD_PIVOT = {0.0f, 2.12f, 0.0f};
    static final float[] JAW_PIVOT = {0.0f, 2.11f, 0.04f};
    static final float[] QUADS = load();

    private static float[] load() {
        try (InputStream in = OttoModel.class.getResourceAsStream("/assets/zomboidcraft/models/train/otto.bin")) {
            if (in == null) {
                return new float[0];
            }
            DataInputStream data = new DataInputStream(in);
            float[] q = new float[data.readInt()];
            for (int i = 0; i < q.length; i++) {
                q[i] = data.readFloat();
            }
            return q;
        } catch (IOException e) {
            return new float[0];
        }
    }

    private OttoModel() {
    }
}
