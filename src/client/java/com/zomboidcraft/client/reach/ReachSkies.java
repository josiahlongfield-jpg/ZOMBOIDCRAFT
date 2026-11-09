package com.zomboidcraft.client.reach;

import com.zomboidcraft.ZomboidCraft;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.zip.Inflater;

/**
 * Each part of the Reach world keeps its own mission's sky (Josiah 2026-11-09, card answered 04:06), blended over about
 * 1.5 km where two regions meet, like the climate. The regions are the approved sky map (build log Preview 7): eight
 * weights per 64 m cell in assets/zomboidcraft/reach/sky_regions.zsk, made by tools/landrender/towers/skymap2.py.
 *
 * <p>The values per region are stand-ins written from the missions' look (overcast Winter Contingency, bright Sword
 * Base, burning night over New Alexandria, storm over Exodus...) until each mission's own sky files come off the PC.
 * Fog follows the approved layered plan as far as the game's fog allows: thick near the ground, thinning as you climb,
 * so from high up the far cities and ships still show.
 *
 * <p>File: 'ZSK1', then big-endian ints version 1, cells per side n, x and z of the first cell's corner, cell size in
 * metres, region count r, zlib length; then zlib data of n * n * r bytes (rows north to south, each row west to east,
 * the r weights of a cell together, 0..255).
 */
public final class ReachSkies {
    /** One region's sky. Colours 0xRRGGBB; fog distances in blocks; dark 0 day .. 1 full night. */
    public record Sky(String name, int sky, int fog, int cloud, float fogStart, float fogEnd, float dark) {
    }

    /** In the file's order. */
    public static final Sky[] REGIONS = {
            new Sky("Winter Contingency", 0x8A96A3, 0x9AA4AE, 0xC8CDD2, 300, 9000, 0.15f),
            new Sky("Sword Base", 0x6F9FD8, 0xB9CDE0, 0xF2F6FA, 900, 22000, 0f),
            new Sky("The Package", 0x8FA3BF, 0xC4CFDC, 0xDDE3EA, 500, 12000, 0.25f),
            new Sky("Tip of the Spear", 0xA99C86, 0xB8A98E, 0xC9BCA6, 400, 10000, 0.1f),
            new Sky("New Alexandria", 0x1A1A2A, 0x3A2A26, 0x4A3A36, 600, 16000, 0.75f),
            new Sky("Exodus", 0x4D5563, 0x5D6572, 0x6E7582, 250, 7000, 0.4f),
            new Sky("Pillar of Autumn", 0xC79A62, 0xD0A874, 0xE0C49A, 500, 14000, 0.2f),
            new Sky("Lone Wolf", 0xB8562A, 0x9A4A2A, 0xB06A44, 300, 8000, 0.35f),
    };

    /** The blended sky at one spot. */
    public static final class Blend {
        public float skyR, skyG, skyB, fogR, fogG, fogB, cloudR, cloudG, cloudB, fogStart, fogEnd, dark;
        /** The region with the most weight here, for messages and the debug screen. */
        public int main;

        public int skyColour() {
            return rgb(skyR, skyG, skyB);
        }

        public int fogColour() {
            return rgb(fogR, fogG, fogB);
        }

        public int cloudColour() {
            return rgb(cloudR, cloudG, cloudB);
        }

        private static int rgb(float r, float g, float b) {
            return Math.round(Math.max(0, Math.min(255, r))) << 16 | Math.round(Math.max(0, Math.min(255, g))) << 8
                    | Math.round(Math.max(0, Math.min(255, b)));
        }
    }

    private static int n, x0, z0, cell, regions;
    private static byte[] weights;
    private static boolean tried;

    private ReachSkies() {
    }

    private static synchronized boolean load() {
        if (tried) {
            return weights != null;
        }
        tried = true;
        try (InputStream raw = ReachSkies.class.getResourceAsStream("/assets/zomboidcraft/reach/sky_regions.zsk")) {
            if (raw == null) {
                ZomboidCraft.LOGGER.warn("Reach world: no sky regions in the jar");
                return false;
            }
            DataInputStream in = new DataInputStream(raw);
            byte[] magic = new byte[4];
            in.readFully(magic);
            int version = in.readInt();
            if (magic[0] != 'Z' || magic[1] != 'S' || magic[2] != 'K' || magic[3] != '1' || version != 1) {
                ZomboidCraft.LOGGER.warn("Reach world: sky regions file is not ZSK1 version 1");
                return false;
            }
            n = in.readInt();
            x0 = in.readInt();
            z0 = in.readInt();
            cell = in.readInt();
            regions = in.readInt();
            byte[] packed = new byte[in.readInt()];
            in.readFully(packed);
            Inflater inf = new Inflater();
            inf.setInput(packed);
            ByteArrayOutputStream out = new ByteArrayOutputStream(n * n * regions);
            byte[] buf = new byte[1 << 16];
            while (!inf.finished()) {
                int k = inf.inflate(buf);
                if (k == 0 && (inf.needsInput() || inf.needsDictionary())) {
                    break;
                }
                out.write(buf, 0, k);
            }
            inf.end();
            byte[] w = out.toByteArray();
            if (w.length != n * n * regions || regions != REGIONS.length) {
                ZomboidCraft.LOGGER.warn("Reach world: sky regions file has {} bytes for {} regions, wants {} for {}",
                        w.length, regions, n * n * REGIONS.length, REGIONS.length);
                return false;
            }
            weights = w;
            ZomboidCraft.LOGGER.info("Reach world: {} sky regions over {} x {} cells of {} m", regions, n, n, cell);
            return true;
        } catch (Exception e) {
            ZomboidCraft.LOGGER.error("Reach world: couldn't read the sky regions", e);
            return false;
        }
    }

    private static float weight(int i, int j, int r) {
        i = Math.max(0, Math.min(n - 1, i));
        j = Math.max(0, Math.min(n - 1, j));
        return (weights[(j * n + i) * regions + r] & 0xFF) / 255f;
    }

    /**
     * The sky at world (x, z), seen from height y: the regions' values weighted smoothly between cells. Past the map's
     * edge the nearest edge cell's sky carries on. Null if the regions couldn't be read.
     */
    public static Blend at(double x, double y, double z) {
        if (!load()) {
            return null;
        }
        double fx = (x - x0) / cell - 0.5, fz = (z - z0) / cell - 0.5;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
        float tx = (float) (fx - i), tz = (float) (fz - j);
        Blend b = new Blend();
        float total = 0, best = -1;
        for (int r = 0; r < regions; r++) {
            float w = (weight(i, j, r) * (1 - tx) + weight(i + 1, j, r) * tx) * (1 - tz)
                    + (weight(i, j + 1, r) * (1 - tx) + weight(i + 1, j + 1, r) * tx) * tz;
            if (w <= 0) {
                continue;
            }
            Sky s = REGIONS[r];
            b.skyR += w * (s.sky >> 16 & 0xFF);
            b.skyG += w * (s.sky >> 8 & 0xFF);
            b.skyB += w * (s.sky & 0xFF);
            b.fogR += w * (s.fog >> 16 & 0xFF);
            b.fogG += w * (s.fog >> 8 & 0xFF);
            b.fogB += w * (s.fog & 0xFF);
            b.cloudR += w * (s.cloud >> 16 & 0xFF);
            b.cloudG += w * (s.cloud >> 8 & 0xFF);
            b.cloudB += w * (s.cloud & 0xFF);
            // distances blend in reciprocal, so a thick fog bank reads as thick right to its edge
            b.fogStart += w / s.fogStart;
            b.fogEnd += w / s.fogEnd;
            b.dark += w * s.dark;
            total += w;
            if (w > best) {
                best = w;
                b.main = r;
            }
        }
        if (total <= 0) {
            return null;
        }
        float k = 1 / total;
        b.skyR *= k;
        b.skyG *= k;
        b.skyB *= k;
        b.fogR *= k;
        b.fogG *= k;
        b.fogB *= k;
        b.cloudR *= k;
        b.cloudG *= k;
        b.cloudB *= k;
        b.dark *= k;
        b.fogStart = total / b.fogStart;
        b.fogEnd = total / b.fogEnd;
        // the layered fog: the ground fog thins as you climb, so from high ground or the air the far skylines show
        float lift = 1 + Math.max(0, (float) y - 150) / 350;
        b.fogStart *= lift;
        b.fogEnd = Math.min(b.fogEnd * lift, 33000);
        return b;
    }
}
