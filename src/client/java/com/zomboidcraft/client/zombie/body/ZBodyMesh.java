package com.zomboidcraft.client.zombie.body;

import com.zomboidcraft.ZomboidCraft;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

/**
 * One outfit's zombie: the body boxes (textured from the per-type skin) and the worn 3D kit
 * (textured from the outfit's atlas), as quads in the space of the bone they hang from.
 * Written by tools/zombies/pack.py from the character preview, so the game draws exactly
 * what the preview showed.
 *
 * File layout (little endian): "ZBD1", bone count, per bone (parent byte, rest x y z), quad count,
 * per quad (bone byte, flag byte, 4 corners x y z, normal x y z, u0 v0 u1 v1). Corners run
 * top-left, top-right, bottom-right, bottom-left of the quad's texture rectangle.
 */
public final class ZBodyMesh {
    public static final int SMALL = 1, STUMP = 2, CUT = 4, BODY = 8;

    public final int bones;
    public final int[] parent;
    public final float[] rest;
    public final int count;
    public final byte[] bone;
    public final byte[] flags;
    public final float[] pos;
    public final float[] normal;
    public final float[] uv;
    /** Each quad's shorter side, so far-off zombies can leave off the thin edges of their clothes. */
    public final float[] minEdge;

    private static final Map<String, ZBodyMesh> CACHE = new HashMap<>();

    /** Which quads each pass draws, worked out once: body or clothes, by detail level, with or without legs. */
    private final int[][] lists = new int[16][];

    private ZBodyMesh(ByteBuffer b) {
        if (b.get() != 'Z' || b.get() != 'B' || b.get() != 'D' || b.get() != '1') {
            throw new IllegalArgumentException("not a zombie body mesh");
        }
        bones = b.getInt();
        parent = new int[bones];
        rest = new float[bones * 3];
        for (int i = 0; i < bones; i++) {
            parent[i] = b.get();
            rest[i * 3] = b.getFloat();
            rest[i * 3 + 1] = b.getFloat();
            rest[i * 3 + 2] = b.getFloat();
        }
        count = b.getInt();
        bone = new byte[count];
        flags = new byte[count];
        pos = new float[count * 12];
        normal = new float[count * 3];
        uv = new float[count * 4];
        minEdge = new float[count];
        for (int q = 0; q < count; q++) {
            bone[q] = b.get();
            flags[q] = b.get();
            for (int i = 0; i < 12; i++) {
                pos[q * 12 + i] = b.getFloat();
            }
            for (int i = 0; i < 3; i++) {
                normal[q * 3 + i] = b.getFloat();
            }
            for (int i = 0; i < 4; i++) {
                uv[q * 4 + i] = b.getFloat();
            }
            minEdge[q] = Math.min(edge(q, 0, 1), edge(q, 1, 2));
        }
    }

    private float edge(int q, int a, int c) {
        float dx = pos[q * 12 + c * 3] - pos[q * 12 + a * 3];
        float dy = pos[q * 12 + c * 3 + 1] - pos[q * 12 + a * 3 + 1];
        float dz = pos[q * 12 + c * 3 + 2] - pos[q * 12 + a * 3 + 2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * The quads drawn for the body or the clothes at a detail level ({@link ZDetail}): past level 0 the smallest
     * pieces go, from level 2 the clothes' thin edges (under a pixel, at 3 under two). Stump caps only on the legless.
     */
    public int[] quads(boolean body, int lod, boolean legless) {
        int key = (body ? 8 : 0) | (legless ? 4 : 0) | Math.clamp(lod, 0, 3);
        int[] list = lists[key];
        if (list == null) {
            int[] all = new int[count];
            int n = 0;
            for (int q = 0; q < count; q++) {
                int f = flags[q];
                if (((f & BODY) != 0) != body || ((f & STUMP) != 0 && !legless)) {
                    continue;
                }
                if (!body && lod > ZDetail.FULL && ((f & SMALL) != 0
                        || (lod >= ZDetail.LOW && minEdge[q] < (lod == ZDetail.SIMPLE ? 2f : 1f)))) {
                    continue;
                }
                all[n++] = q;
            }
            list = lists[key] = java.util.Arrays.copyOf(all, n);
        }
        return list;
    }

    /** The outfit's mesh, loaded once; null if the outfit has none (the old model draws it then). */
    public static synchronized ZBodyMesh get(String outfit) {
        if (CACHE.containsKey(outfit)) {
            return CACHE.get(outfit);
        }
        ZBodyMesh mesh = null;
        try (InputStream in = ZBodyMesh.class.getResourceAsStream("/assets/zomboidcraft/zbody/" + outfit + ".bin")) {
            if (in != null) {
                byte[] data = new DataInputStream(in).readAllBytes();
                mesh = new ZBodyMesh(ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN));
            }
        } catch (IOException | RuntimeException e) {
            ZomboidCraft.LOGGER.warn("Zombie body mesh for {} failed to load", outfit, e);
        }
        CACHE.put(outfit, mesh);
        return mesh;
    }
}
