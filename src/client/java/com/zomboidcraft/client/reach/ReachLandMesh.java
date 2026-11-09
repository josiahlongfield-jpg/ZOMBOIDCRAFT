package com.zomboidcraft.client.reach;

import com.zomboidcraft.world.reach.ReachLand;
import java.util.ArrayList;
import java.util.List;

/**
 * The drawn side of the land between the Reach levels ({@link ReachLand}): the ground as a mesh, coloured by the
 * approved climate map (snow in the north fading through tundra and green to dry grass and desert in the south, rock
 * where it is steep), plus the sea surface. Built in 256 m tiles so the ones off screen are skipped. Inside the levels'
 * own surroundings their real Halo land is drawn instead; this only covers the new land, overlapping the edge by one
 * cell so there is no gap.
 */
final class ReachLandMesh {
    static final int STEP = 2;        // samples per mesh cell (land samples are 4 m apart: cells of 8 m)
    static final int TILE = 32;       // cells per tile side

    private static final int[] CLIMATE = {0xFF5F7F45, 0xFFE4E9EE, 0xFF8C9486, 0xFF5F7F45, 0xFF8E8457, 0xFFC6AC7E};
    private static final int ROCK = 0xFF7E7A74, SEA_COLOUR = 0xFF2E5468;

    private ReachLandMesh() {
    }

    /** The land as a level: material 0 the ground, material 1 the sea. Vertices in world coordinates. */
    static ReachGlb.Level build(ReachLand land) {
        List<ReachGlb.Material> mats = List.of(new ReachGlb.Material("land", null, 0xFFFFFFFF, false, false),
                new ReachGlb.Material("sea", null, 0xFFFFFFFF, false, false));
        List<ReachGlb.Batch> batches = new ArrayList<>();
        int cells = (land.samples() - 1) / STEP;
        for (int tz = 0; tz < cells; tz += TILE) {
            for (int tx = 0; tx < cells; tx += TILE) {
                ReachGlb.Batch ground = tile(land, tx, tz, Math.min(TILE, cells - tx), Math.min(TILE, cells - tz));
                if (ground != null) {
                    batches.add(ground);
                }
                ReachGlb.Batch sea = sea(land, tx, tz, Math.min(TILE, cells - tx), Math.min(TILE, cells - tz));
                if (sea != null) {
                    batches.add(sea);
                }
            }
        }
        return new ReachGlb.Level(mats, batches);
    }

    private static boolean drawn(ReachLand land, int ci, int cj) {
        // a cell is ours if any sample in or right beside it is new land
        for (int dj = -STEP; dj <= 2 * STEP; dj += STEP) {
            for (int di = -STEP; di <= 2 * STEP; di += STEP) {
                if (ReachLand.kind(land.flags(ci * STEP + di, cj * STEP + dj)) == ReachLand.LAND) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ReachGlb.Batch tile(ReachLand land, int tx, int tz, int w, int h) {
        boolean[] use = new boolean[w * h];
        int used = 0;
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                if (drawn(land, tx + i, tz + j)) {
                    use[j * w + i] = true;
                    used++;
                }
            }
        }
        if (used == 0) {
            return null;
        }
        int vw = w + 1, vh = h + 1;
        float[] pos = new float[vw * vh * 3], uv = new float[vw * vh * 2], normal = new float[vw * vh * 3];
        int[] color = new int[vw * vh];
        float c = land.cell() * STEP;
        for (int j = 0; j < vh; j++) {
            for (int i = 0; i < vw; i++) {
                int si = (tx + i) * STEP, sj = (tz + j) * STEP, v = j * vw + i;
                float y = land.sample(si, sj);
                pos[v * 3] = land.firstX() + si * land.cell();
                pos[v * 3 + 1] = y;
                pos[v * 3 + 2] = land.firstZ() + sj * land.cell();
                float dx = (land.sample(si + STEP, sj) - land.sample(si - STEP, sj)) / (2 * c);
                float dz = (land.sample(si, sj + STEP) - land.sample(si, sj - STEP)) / (2 * c);
                float len = (float) Math.sqrt(dx * dx + 1 + dz * dz);
                normal[v * 3] = -dx / len;
                normal[v * 3 + 1] = 1 / len;
                normal[v * 3 + 2] = -dz / len;
                color[v] = colour(land, si, sj, 1 / len);
            }
        }
        int[] index = new int[used * 6];
        int k = 0;
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                if (!use[j * w + i]) {
                    continue;
                }
                int a = j * vw + i, b = a + 1, d = a + vw, e = d + 1;
                index[k++] = a;
                index[k++] = d;
                index[k++] = b;
                index[k++] = b;
                index[k++] = d;
                index[k++] = e;
            }
        }
        return new ReachGlb.Batch(0, pos, uv, normal, color, index);
    }

    private static ReachGlb.Batch sea(ReachLand land, int tx, int tz, int w, int h) {
        List<int[]> quads = new ArrayList<>();
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int si = (tx + i) * STEP, sj = (tz + j) * STEP;
                boolean wet = false, below = false;
                for (int dj = 0; dj <= STEP; dj += STEP) {
                    for (int di = 0; di <= STEP; di += STEP) {
                        wet |= ReachLand.wet(land.flags(si + di, sj + dj));
                        below |= land.sample(si + di, sj + dj) < ReachLand.SEA;
                    }
                }
                if (wet && below) {
                    quads.add(new int[] {si, sj});
                }
            }
        }
        if (quads.isEmpty()) {
            return null;
        }
        float c = land.cell() * STEP;
        float[] pos = new float[quads.size() * 12], uv = new float[quads.size() * 8], normal = new float[quads.size() * 12];
        int[] color = new int[quads.size() * 4], index = new int[quads.size() * 6];
        for (int q = 0; q < quads.size(); q++) {
            float x = land.firstX() + quads.get(q)[0] * land.cell(), z = land.firstZ() + quads.get(q)[1] * land.cell();
            float[][] corners = {{x, z}, {x + c, z}, {x, z + c}, {x + c, z + c}};
            for (int v = 0; v < 4; v++) {
                pos[(q * 4 + v) * 3] = corners[v][0];
                pos[(q * 4 + v) * 3 + 1] = ReachLand.SEA;
                pos[(q * 4 + v) * 3 + 2] = corners[v][1];
                normal[(q * 4 + v) * 3 + 1] = 1;
                color[q * 4 + v] = SEA_COLOUR;
            }
            int b = q * 4;
            index[q * 6] = b;
            index[q * 6 + 1] = b + 2;
            index[q * 6 + 2] = b + 1;
            index[q * 6 + 3] = b + 1;
            index[q * 6 + 4] = b + 2;
            index[q * 6 + 5] = b + 3;
        }
        return new ReachGlb.Batch(1, pos, uv, normal, color, index);
    }

    /** Climate colour, rock on steep ground, with a little variation so it doesn't read as flat paint. */
    private static int colour(ReachLand land, int si, int sj, float up) {
        int base = CLIMATE[Math.min(CLIMATE.length - 1, ReachLand.climate(land.flags(si, sj)))];
        float rock = Math.max(0, Math.min(1, (0.8f - up) / 0.25f));
        int c = mix(base, ROCK, rock);
        int hash = (si * 73856093) ^ (sj * 19349663);
        float vary = 0.94f + ((hash >>> 8) & 0xFF) / 255f * 0.12f;
        return scale(c, vary);
    }

    private static int mix(int a, int b, float t) {
        int out = 0xFF000000;
        for (int s = 0; s < 24; s += 8) {
            float x = (a >>> s) & 0xFF, y = (b >>> s) & 0xFF;
            out |= Math.round(x + (y - x) * t) << s;
        }
        return out;
    }

    private static int scale(int c, float f) {
        int out = 0xFF000000;
        for (int s = 0; s < 24; s += 8) {
            out |= Math.min(255, Math.round(((c >>> s) & 0xFF) * f)) << s;
        }
        return out;
    }

    /**
     * A level's surrounding Halo land (its _000 piece) cut to where that level's land is in this world: triangles over
     * the new land, and the sky and far-horizon pieces (any edge over 1.5 km), are dropped.
     */
    static ReachGlb.Level clip(ReachGlb.Level level, ReachLand land) {
        List<ReachGlb.Batch> out = new ArrayList<>();
        for (ReachGlb.Batch b : level.batches()) {
            int[] keep = new int[b.index.length];
            int k = 0;
            for (int t = 0; t < b.index.length; t += 3) {
                int i0 = b.index[t] * 3, i1 = b.index[t + 1] * 3, i2 = b.index[t + 2] * 3;
                float cx = (b.pos[i0] + b.pos[i1] + b.pos[i2]) / 3, cz = (b.pos[i0 + 2] + b.pos[i1 + 2] + b.pos[i2 + 2]) / 3;
                if (edge(b.pos, i0, i1) > 1500 || edge(b.pos, i1, i2) > 1500 || edge(b.pos, i2, i0) > 1500) {
                    continue;
                }
                if (!land.covers(cx, cz) || ReachLand.kind(land.flagsAt(cx, cz)) == ReachLand.LAND) {
                    continue;
                }
                keep[k++] = b.index[t];
                keep[k++] = b.index[t + 1];
                keep[k++] = b.index[t + 2];
            }
            if (k == b.index.length) {
                out.add(b);
            } else if (k > 0) {
                out.add(new ReachGlb.Batch(b.material, b.pos, b.uv, b.normal, b.color, java.util.Arrays.copyOf(keep, k)));
            }
        }
        return new ReachGlb.Level(level.materials(), out);
    }

    private static float edge(float[] p, int a, int b) {
        float dx = p[a] - p[b], dy = p[a + 1] - p[b + 1], dz = p[a + 2] - p[b + 2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
