package com.zomboidcraft.client.reach;

import com.zomboidcraft.world.reach.ReachLand;
import java.util.ArrayList;
import java.util.List;

/**
 * The drawn side of the land between the Reach levels ({@link ReachLand}): the ground as a mesh, coloured by the
 * approved climate map (snow in the north fading through tundra and green to dry grass and desert in the south, rock
 * where it is steep), plus the sea surface. Built in 256 m tiles so the ones off screen are skipped. Inside the levels'
 * own surroundings their real Halo land is drawn instead; this only covers the new land, overlapping the edge by one
 * cell so there is no gap. Past the map's own edge {@link ReachHorizon} carries the land on to the horizon.
 *
 * <p>Each tile is made twice: with 8 m cells for near the camera and 32 m cells for far away (Batch.lod 1 and 2), so
 * the whole 16 km can be drawn out to the horizon without drawing millions of small triangles in the distance.
 */
final class ReachLandMesh {
    static final int STEP = 2;        // samples per near mesh cell (land samples are 4 m apart: cells of 8 m)
    static final int FAR_STEP = 8;    // samples per far mesh cell (32 m)
    static final int TILE_SAMPLES = 64; // samples per tile side (256 m), the same for both

    static final int[] CLIMATE = {0xFF5F7F45, 0xFFE4E9EE, 0xFF8C9486, 0xFF5F7F45, 0xFF8E8457, 0xFFC6AC7E};
    static final int ROCK = 0xFF7E7A74, SEA_COLOUR = 0xFF2E5468;

    private ReachLandMesh() {
    }

    /** The land as a level: material 0 the ground, material 1 the sea. Vertices in world coordinates. */
    static ReachGlb.Level build(ReachLand land) {
        List<ReachGlb.Material> mats = List.of(new ReachGlb.Material("land", null, 0xFFFFFFFF, false, false),
                new ReachGlb.Material("sea", null, 0xFFFFFFFF, false, false));
        List<ReachGlb.Batch> batches = new ArrayList<>();
        int last = land.samples() - 1;
        for (int sz = 0; sz < last; sz += TILE_SAMPLES) {
            for (int sx = 0; sx < last; sx += TILE_SAMPLES) {
                int ws = Math.min(TILE_SAMPLES, last - sx), hs = Math.min(TILE_SAMPLES, last - sz);
                for (int lod = 1; lod <= 2; lod++) {
                    int step = lod == 1 ? STEP : FAR_STEP;
                    ReachGlb.Batch ground = tile(land, sx / step, sz / step, Math.max(1, ws / step), Math.max(1, hs / step), step);
                    if (ground != null) {
                        ground.lod = lod;
                        batches.add(ground);
                    }
                }
                // the sea is flat, so its coarse quads serve near and far
                ReachGlb.Batch sea = sea(land, sx / FAR_STEP, sz / FAR_STEP, Math.max(1, ws / FAR_STEP), Math.max(1, hs / FAR_STEP), FAR_STEP);
                if (sea != null) {
                    batches.add(sea);
                }
            }
        }
        ReachHorizon.build(land, batches);
        return new ReachGlb.Level(mats, batches);
    }

    /** The last sample index the land's meshes reach at this step (the tiles round the far edge down). */
    static int end(ReachLand land, int step) {
        int last = land.samples() - 1, end = 0;
        for (int s = 0; s < last; s += TILE_SAMPLES) {
            end = s + Math.max(1, Math.min(TILE_SAMPLES, last - s) / step) * step;
        }
        return end;
    }

    private static boolean drawn(ReachLand land, int ci, int cj, int step) {
        // a cell is ours if any sample in or right beside it is new land
        for (int dj = -step; dj <= 2 * step; dj += Math.max(2, step / 2)) {
            for (int di = -step; di <= 2 * step; di += Math.max(2, step / 2)) {
                if (ReachLand.kind(land.flags(ci * step + di, cj * step + dj)) == ReachLand.LAND) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ReachGlb.Batch tile(ReachLand land, int tx, int tz, int w, int h, int step) {
        boolean[] use = new boolean[w * h];
        int used = 0;
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                if (drawn(land, tx + i, tz + j, step)) {
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
        float c = land.cell() * step;
        for (int j = 0; j < vh; j++) {
            for (int i = 0; i < vw; i++) {
                int si = (tx + i) * step, sj = (tz + j) * step, v = j * vw + i;
                float y = land.sample(si, sj);
                pos[v * 3] = land.firstX() + si * land.cell();
                pos[v * 3 + 1] = y;
                pos[v * 3 + 2] = land.firstZ() + sj * land.cell();
                float dx = (land.sample(si + step, sj) - land.sample(si - step, sj)) / (2 * c);
                float dz = (land.sample(si, sj + step) - land.sample(si, sj - step)) / (2 * c);
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

    private static ReachGlb.Batch sea(ReachLand land, int tx, int tz, int w, int h, int step) {
        List<int[]> quads = new ArrayList<>();
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int si = (tx + i) * step, sj = (tz + j) * step;
                boolean wet = false, below = false;
                for (int dj = 0; dj <= step; dj += Math.max(1, step / 4)) {
                    for (int di = 0; di <= step; di += Math.max(1, step / 4)) {
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
        float c = land.cell() * step;
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
    static int colour(ReachLand land, int si, int sj, float up) {
        int base = CLIMATE[Math.min(CLIMATE.length - 1, ReachLand.climate(land.flags(si, sj)))];
        float rock = Math.max(0, Math.min(1, (0.8f - up) / 0.25f));
        int c = mix(base, ROCK, rock);
        int hash = (si * 73856093) ^ (sj * 19349663);
        float vary = 0.94f + ((hash >>> 8) & 0xFF) / 255f * 0.12f;
        return scale(c, vary);
    }

    static int mix(int a, int b, float t) {
        int out = 0xFF000000;
        for (int s = 0; s < 24; s += 8) {
            float x = (a >>> s) & 0xFF, y = (b >>> s) & 0xFF;
            out |= Math.round(x + (y - x) * t) << s;
        }
        return out;
    }

    static int scale(int c, float f) {
        int out = 0xFF000000;
        for (int s = 0; s < 24; s += 8) {
            out |= Math.min(255, Math.round(((c >>> s) & 0xFF) * f)) << s;
        }
        return out;
    }

    /**
     * A level's surrounding Halo land (its _000 piece) cut to where that level's land is in this world: triangles over
     * the new land, and the sky and far-horizon pieces (any edge over 1.5 km, and Halo's painted far ranges and
 * glaciers, which moved into this world would stand in the air over the neighbouring regions), are dropped.
     */
    /**
     * Halo's far-horizon scenery, by material name. Where a piece of it stands on this world's land it stays; where it
     * would hang in the air it goes, and the world's own horizon (ReachHorizon) takes its place.
     */
    private static final java.util.regex.Pattern FAR_SCENERY = java.util.regex.Pattern.compile(
            "glacier|background_mountain|distant_mountain|crater_mountain|distant_ridge|far_ridge", java.util.regex.Pattern.CASE_INSENSITIVE);

    static ReachGlb.Level clip(ReachGlb.Level level, ReachLand land) {
        List<ReachGlb.Batch> out = new ArrayList<>();
        for (ReachGlb.Batch b : level.batches()) {
            boolean scenery = FAR_SCENERY.matcher(level.materials().get(b.material).name()).find();
            int[] keep = new int[b.index.length];
            int k = 0;
            for (int t = 0; t < b.index.length; t += 3) {
                int i0 = b.index[t] * 3, i1 = b.index[t + 1] * 3, i2 = b.index[t + 2] * 3;
                float cx = (b.pos[i0] + b.pos[i1] + b.pos[i2]) / 3, cz = (b.pos[i0 + 2] + b.pos[i1 + 2] + b.pos[i2 + 2]) / 3;
                float longest = Math.max(edge(b.pos, i0, i1), Math.max(edge(b.pos, i1, i2), edge(b.pos, i2, i0)));
                if (longest > 1500) {
                    continue;
                }
                if (!land.covers(cx, cz) || ReachLand.kind(land.flagsAt(cx, cz)) == ReachLand.LAND) {
                    continue;
                }
                keep[k++] = b.index[t];
                keep[k++] = b.index[t + 1];
                keep[k++] = b.index[t + 2];
            }
            if (scenery && !grounded(land, b.pos, keep, k)) {
                continue;
            }
            if (k == b.index.length) {
                out.add(b);
            } else if (k > 0) {
                out.add(new ReachGlb.Batch(b.material, b.pos, b.uv, b.normal, b.color, java.util.Arrays.copyOf(keep, k)));
            }
        }
        return new ReachGlb.Level(level.materials(), out);
    }

    /**
     * Whether most of these triangles sit on the ground: a painted far range or glacier that stands on this world's
     * land stays (Sword Base's glacier, the farm's crater walls), one left hanging in the air goes.
     */
    private static boolean grounded(ReachLand land, float[] p, int[] index, int count) {
        if (count == 0) {
            return false;
        }
        int on = 0;
        for (int t = 0; t < count; t += 3) {
            float low = Float.MAX_VALUE;
            for (int k = 0; k < 3; k++) {
                int v = index[t + k] * 3;
                low = Math.min(low, p[v + 1] - land.height(p[v], p[v + 2]));
            }
            if (low < 30) {
                on++;
            }
        }
        return on * 10 >= count / 3;  // a tenth touching the ground: tall walls of rock and ice count, floating cards don't
    }

    private static float edge(float[] p, int a, int b) {
        float dx = p[a] - p[b], dy = p[a + 1] - p[b + 1], dz = p[a + 2] - p[b + 2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
