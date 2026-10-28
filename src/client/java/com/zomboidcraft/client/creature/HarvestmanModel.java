package com.zomboidcraft.client.creature;

import com.zomboidcraft.ZomboidCraft;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.InflaterInputStream;

/**
 * The Harvestman's model, from assets/zomboidcraft/creatures/harvestman.zhm (packed from the approved preview by
 * tools/creatures/harvestman): its legs' sizes, and the body, its scythe arms and beard (hunting, and closed in the
 * feeding grip), thighs and lower legs at two levels of detail. Each part is
 * plain triangles in its own frame, coloured per corner (shading baked in), some of them glowing (the red seams).
 */
public final class HarvestmanModel {
    public static final int BODY = 0, ARMS_HUNT = 1, ARMS_FEED = 2, THIGH_F = 3, THIGH_R = 4, LOW_F = 5, LOW_R = 6;

    /** One leg pair's sizes (the left one; the right mirrors it). */
    public static final class Leg {
        public final float hipX, hipY, hipZ, a, lc, restA, restR;

        Leg(DataInputStream in) throws IOException {
            hipX = in.readFloat();
            hipY = in.readFloat();
            hipZ = in.readFloat();
            a = in.readFloat();
            lc = in.readFloat();
            restA = in.readFloat();
            restR = in.readFloat();
        }
    }

    /** One part: three corners a triangle, positions and normals packed three floats a corner. */
    public static final class Part {
        public final String name;
        public final float[] pos, normal;
        /** ARGB per corner. */
        public final int[] colour;
        /** Per triangle: it glows (drawn at full light). */
        public final boolean[] glow;
        /** Per corner: it's red (the seams and the core), which it can darken to hide. */
        public final boolean[] red;

        Part(DataInputStream in) throws IOException {
            byte[] nb = new byte[in.readShort()];
            in.readFully(nb);
            name = new String(nb, StandardCharsets.UTF_8);
            int tris = in.readInt(), corners = tris * 3;
            pos = new float[corners * 3];
            normal = new float[corners * 3];
            colour = new int[corners];
            glow = new boolean[tris];
            red = new boolean[corners];
            for (int c = 0; c < corners; c++) {
                pos[c * 3] = in.readFloat();
                pos[c * 3 + 1] = in.readFloat();
                pos[c * 3 + 2] = in.readFloat();
                float nx = in.readByte() / 127f, ny = in.readByte() / 127f, nz = in.readByte() / 127f;
                float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (l > 1e-4f) {
                    nx /= l;
                    ny /= l;
                    nz /= l;
                }
                normal[c * 3] = nx;
                normal[c * 3 + 1] = ny;
                normal[c * 3 + 2] = nz;
                int r = in.readUnsignedByte(), g = in.readUnsignedByte(), b = in.readUnsignedByte();
                colour[c] = 0xFF000000 | r << 16 | g << 8 | b;
                red[c] = r > 30 && r > g * 1.8 && r > b * 1.8;
                if (in.readUnsignedByte() != 0) {
                    glow[c / 3] = true;
                }
            }
        }

        public int triangles() {
            return glow.length;
        }
    }

    public final Leg[] legs;
    /** [level][part]: level 0 near, 1 far. */
    public final Part[][] levels;

    private static HarvestmanModel loaded;
    private static boolean failed;

    private HarvestmanModel(DataInputStream in) throws IOException {
        byte[] magic = new byte[4];
        in.readFully(magic);
        if (!"ZHM1".equals(new String(magic, StandardCharsets.US_ASCII))) {
            throw new IOException("not a Harvestman model");
        }
        legs = new Leg[in.readInt()];
        for (int i = 0; i < legs.length; i++) {
            legs[i] = new Leg(in);
        }
        levels = new Part[in.readInt()][];
        for (int l = 0; l < levels.length; l++) {
            levels[l] = new Part[in.readInt()];
            for (int p = 0; p < levels[l].length; p++) {
                levels[l][p] = new Part(in);
            }
        }
    }

    /** The model, read the first time it's wanted; null if it couldn't be. */
    public static HarvestmanModel get() {
        if (loaded == null && !failed) {
            try (InputStream raw = HarvestmanModel.class.getResourceAsStream("/assets/zomboidcraft/creatures/harvestman.zhm")) {
                if (raw == null) {
                    throw new IOException("missing");
                }
                loaded = new HarvestmanModel(new DataInputStream(new BufferedInputStream(new InflaterInputStream(raw), 1 << 16)));
            } catch (IOException e) {
                failed = true;
                ZomboidCraft.LOGGER.error("Couldn't read the Harvestman model", e);
            }
        }
        return loaded;
    }
}
