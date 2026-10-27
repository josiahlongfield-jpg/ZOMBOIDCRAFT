package com.zomboidcraft.world.cars;

import java.util.List;

/**
 * How an aircraft comes apart in a crash: a few boxes in the model's own frame (nose +z, left +x, up
 * y from the ground), as shares of its size. Each triangle goes to the first box its middle is in;
 * what's left over is the wreck that stays put. The server throws a {@link AirDebris} for each piece
 * and the client cuts the same pieces out of the mesh, so both sides read this one table.
 */
public final class AirBreakup {
    /** A piece: everything with x, y and z inside these bounds (shares of half width, height, half length). */
    public record Piece(String name, float x0, float x1, float y0, float y1, float z0, float z1) {
        public boolean holds(float x, float y, float z, float[] size) {
            float hw = size[0] / 2, h = size[1], hl = size[2] / 2;
            return x >= x0 * hw && x <= x1 * hw && y >= y0 * h && y <= y1 * h && z >= z0 * hl && z <= z1 * hl;
        }

        /** The point the piece turns about, in model metres: the middle of its box within the airframe. */
        public float[] anchor(float[] size) {
            float hw = size[0] / 2, h = size[1], hl = size[2] / 2;
            return new float[]{mid(x0, x1) * hw, mid(y0, y1) * h, mid(z0, z1) * hl};
        }

        private static float mid(float a, float b) {
            return (Math.max(a, -1) + Math.min(b, 1)) / 2;
        }
    }

    private static final float INF = 100;

    /** Jets: nose cone, tail (fins, tailplanes, nozzles) and each wing. */
    private static final List<Piece> JET = List.of(
            new Piece("nose", -INF, INF, -INF, INF, 0.7f, INF),
            new Piece("tail", -INF, INF, -INF, INF, -INF, -0.45f),
            new Piece("left wing", 0.28f, INF, -INF, INF, -INF, INF),
            new Piece("right wing", -INF, -0.28f, -INF, INF, -INF, INF));

    /** The helicopter: rotor head and blades, tail boom, and each stub wing with its pylons. */
    private static final List<Piece> HELI = List.of(
            new Piece("rotor", -INF, INF, 0.7f, INF, -0.68f, INF),
            new Piece("tail", -INF, INF, -INF, INF, -INF, -0.35f),
            new Piece("left wing", 0.2f, INF, -INF, 0.7f, -INF, INF),
            new Piece("right wing", -INF, -0.2f, -INF, 0.7f, -INF, INF));

    public static List<Piece> pieces(String model) {
        return model.startsWith("heli") ? HELI : JET;
    }

    /** Which piece a triangle with its middle at (x, y, z) belongs to, or -1 for the wreck that stays. */
    public static int pieceOf(String model, float[] size, float x, float y, float z) {
        List<Piece> pieces = pieces(model);
        for (int i = 0; i < pieces.size(); i++) {
            if (pieces.get(i).holds(x, y, z, size)) {
                return i;
            }
        }
        return -1;
    }

    private AirBreakup() {
    }
}
