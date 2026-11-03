package com.zomboidcraft.client.npc;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.mixin.client.BufferBuilderAccessor;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormatElement;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

/**
 * Writes a skinned body's quads straight into the draw buffer, whole vertices at a time, instead of corner by corner
 * through VertexConsumer. With a shader pack on, Iris widens the entity vertex with its own extras (where the quad's
 * texture centre is, its tangent, which entity it is) and works them out after every fourth corner by reading the quad
 * back out of the buffer: the 2026-11-02 horde profile (600 zombies) had that alone at 30% of the frame. Here they're
 * worked out from the floats already in hand, the same way Iris does (the quad's face normal over its corners, its
 * tangent from the first three corners and their texture coordinates), so the picture is the same.
 * <p>
 * Only for layouts it knows (vanilla's entity vertex, or Iris's widened one); anything else, or a consumer that isn't a
 * plain BufferBuilder (an outline, a glint), goes the old way. performance.fastBodyVertices turns it off.
 */
public final class FastVerts {
    private FastVerts() {
    }

    /** Byte offsets of each part of one vertex layout (-1: not in it). */
    record Layout(int stride, int pos, int color, int uv0, int uv1, int uv2, int normal, int entity, int midTex,
                          int tangent) {
        boolean extended() {
            return midTex >= 0;
        }
    }

    private static final Layout NONE = new Layout(0, -1, -1, -1, -1, -1, -1, -1, -1, -1);
    private static final Map<VertexFormat, Layout> LAYOUTS = new IdentityHashMap<>();
    private static boolean warned;

    private static Layout layout(VertexFormat f) {
        Layout l = LAYOUTS.get(f);
        if (l == null) {
            l = read(f);
            LAYOUTS.put(f, l);
            ZomboidCraft.LOGGER.info("Fast body vertices for layout {}: {}", f, l == NONE ? "not used" : l);
        }
        return l;
    }

    private static Layout read(VertexFormat f) {
        int pos = -1, color = -1, uv0 = -1, uv1 = -1, uv2 = -1, normal = -1, entity = -1, midTex = -1, tangent = -1;
        for (VertexFormatElement e : f.getElements()) {
            int o = e.offset();
            GpuFormat g = e.format();
            switch (e.name()) {
                case "Position" -> pos = g == GpuFormat.RGB32_FLOAT ? o : -2;
                case "Color" -> color = g == GpuFormat.RGBA8_UNORM ? o : -2;
                case "UV0" -> uv0 = g == GpuFormat.RG32_FLOAT ? o : -2;
                case "UV1" -> uv1 = g == GpuFormat.RG16_SINT ? o : -2;
                case "UV2" -> uv2 = g == GpuFormat.RG16_SINT ? o : -2;
                case "Normal" -> normal = g == GpuFormat.RGBA8_SNORM ? o : -2;
                case "iris_Entity" -> entity = g == GpuFormat.RGBA16_UINT ? o : -2;
                case "mc_midTexCoord" -> midTex = g == GpuFormat.RG32_FLOAT ? o : -2;
                case "at_tangent" -> tangent = g == GpuFormat.RGBA8_SNORM ? o : -2;
                default -> {
                    ZomboidCraft.LOGGER.info("Fast body vertices: unknown part {} ({}) in {}", e.name(), g, f);
                    return NONE;
                }
            }
        }
        // everything vanilla's entity vertex has, and Iris's three extras all or none
        if (pos < 0 || color < 0 || uv0 < 0 || uv1 < 0 || uv2 < 0 || normal < 0) {
            ZomboidCraft.LOGGER.info("Fast body vertices: layout {} lacks a part or has it in another form: {}", f, f.getElements());
            return NONE;
        }
        boolean anyIris = entity != -1 || midTex != -1 || tangent != -1;
        if (anyIris && (entity < 0 || midTex < 0 || tangent < 0)) return NONE;
        return new Layout(f.getVertexSize(), pos, color, uv0, uv1, uv2, normal, entity, midTex, tangent);
    }

    // ------------------------------------------------------------------ Iris's "which entity is this" (by name, optional)

    private static MethodHandle irisEntity, irisBlockEntity, irisItem;
    private static Object irisState;
    private static boolean irisLooked;

    private static int irisId(MethodHandle h) {
        if (h == null) return 0;
        try {
            return (int) h.invoke(irisState);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void findIris() {
        irisLooked = true;
        try {
            Class<?> c = Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState");
            irisState = c.getField("INSTANCE").get(null);
            MethodHandles.Lookup l = MethodHandles.publicLookup();
            irisEntity = l.findVirtual(c, "getCurrentRenderedEntity", MethodType.methodType(int.class));
            irisBlockEntity = l.findVirtual(c, "getCurrentRenderedBlockEntity", MethodType.methodType(int.class));
            irisItem = l.findVirtual(c, "getCurrentRenderedItem", MethodType.methodType(int.class));
        } catch (Throwable t) {
            irisEntity = irisBlockEntity = irisItem = null;
        }
    }

    // ------------------------------------------------------------------ writing

    /** The builder we wrote last and how many vertices it held after: a later write right behind it needn't close one. */
    private static BufferBuilder last;
    private static int lastVertices;

    /**
     * Writes quads q0..q1 (indices into quads, four a quad) of a skinned group, posed (p, n: skinned positions and
     * normals), and again wound backwards if back. False if this consumer or layout can't be written this way (nothing
     * written: draw it the old way).
     */
    static boolean write(VertexConsumer out, PoseStack.Pose pose, int[] quads, int q0, int q1, boolean back, float[] p,
                         float[] n, float[] uv, int light, int overlay) {
        if (!(out instanceof BufferBuilder bb) || !ZcConfig.get().performance.fastBodyVertices) return false;
        BufferBuilderAccessor a = (BufferBuilderAccessor) bb;
        if (!a.zomboidcraft$building() || a.zomboidcraft$topology() != PrimitiveTopology.QUADS) return false;
        Layout l = layout(a.zomboidcraft$format());
        if (l == NONE) return false;
        seen(l);
        int nq = (q1 - q0) / 4 * (back ? 2 : 1);
        if (nq <= 0) return true;
        try {
            // a vertex someone else started is closed first (its checks and Iris's work on its quad run as usual)
            if (a.zomboidcraft$vertices() > 0 && !(bb == last && a.zomboidcraft$vertices() == lastVertices)) {
                a.zomboidcraft$endLastVertex();
            }
            int stride = l.stride, count = nq * 4, length = count * stride;
            long dst = a.zomboidcraft$buffer().reserve(length);
            int[] ids = ids(l);
            fill(dst, l, pose.pose(), pose.normal(), quads, q0, q1, back, p, n, uv, light, overlay, ids[0], ids[1], ids[2]);
            int v = a.zomboidcraft$vertices() + count;
            a.zomboidcraft$setVertices(v);
            a.zomboidcraft$setVertexPointer(dst + length - stride);
            a.zomboidcraft$setElementsToFill(0);
            last = bb;
            lastVertices = v;
            return true;
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                ZomboidCraft.LOGGER.error("Fast body vertices failed; bodies drawn the old way from now on", t);
            }
            ZcConfig.get().performance.fastBodyVertices = false;
            throw t;
        }
    }

    /** Iris's ids of what's being drawn now (render thread), all 0 for a layout without them. Remembered for jobs. */
    private static int[] ids(Layout l) {
        if (!l.extended()) return NO_IDS;
        if (!irisLooked) findIris();
        int[] ids = {irisId(irisEntity), irisId(irisBlockEntity), irisId(irisItem)};
        lastIds = ids;
        return ids;
    }

    private static final int[] NO_IDS = {0, 0, 0};
    /**
     * The layouts bodies' buffers were last seen in, with Iris's widened one and without (a shader pack's world, or no
     * pack and the menus), and Iris's ids then: what a job on another thread writes in.
     */
    private static volatile Layout lastExtended, lastPlain;
    private static volatile long extendedFrame = Long.MIN_VALUE;
    static volatile int[] lastIds = NO_IDS;

    private static void seen(Layout l) {
        if (l.extended()) {
            lastExtended = l;
            extendedFrame = com.zomboidcraft.zombie.Profiler.frameNo;
        } else {
            lastPlain = l;
        }
    }

    /** The layout a body drawn this frame most likely goes into: Iris's while a shader pack was drawing lately. */
    static Layout guess() {
        return extendedFrame >= com.zomboidcraft.zombie.Profiler.frameNo - 2 ? lastExtended : lastPlain;
    }

    /** The layout this consumer takes bodies in (render thread), or null if it can't take them whole. Remembered. */
    static Layout layoutOf(VertexConsumer out) {
        if (!(out instanceof BufferBuilder bb) || !ZcConfig.get().performance.fastBodyVertices) return null;
        BufferBuilderAccessor a = (BufferBuilderAccessor) bb;
        if (!a.zomboidcraft$building() || a.zomboidcraft$topology() != PrimitiveTopology.QUADS) return null;
        Layout l = layout(a.zomboidcraft$format());
        if (l == NONE) return null;
        seen(l);
        return l;
    }

    /** Bytes a group's quads take written in a layout (four vertices a quad, twice if both sides). */
    static int bytes(Layout l, int quadIndices, boolean back) {
        return quadIndices / 4 * (back ? 2 : 1) * 4 * l.stride;
    }

    /**
     * Copies vertices a job wrote (in layout l, Iris's ids as given) into the consumer. False (nothing copied) if it
     * doesn't take that layout now: the caller writes them afresh.
     */
    static boolean copy(VertexConsumer out, Layout l, long src, int bytes, int[] idsUsed) {
        if (l == null || layoutOf(out) != l) return false;
        BufferBuilder bb = (BufferBuilder) out;
        BufferBuilderAccessor a = (BufferBuilderAccessor) bb;
        if (bytes <= 0) return true;
        if (a.zomboidcraft$vertices() > 0 && !(bb == last && a.zomboidcraft$vertices() == lastVertices)) {
            a.zomboidcraft$endLastVertex();
        }
        int stride = l.stride, count = bytes / stride;
        long dst = a.zomboidcraft$buffer().reserve(bytes);
        MemoryUtil.memCopy(src, dst, bytes);
        if (l.extended()) {
            int[] now = ids(l);
            if (now[0] != idsUsed[0] || now[1] != idsUsed[1] || now[2] != idsUsed[2]) {
                for (long at = dst + l.entity, end = dst + bytes; at < end; at += stride) {
                    MemoryUtil.memPutShort(at, (short) now[0]);
                    MemoryUtil.memPutShort(at + 2, (short) now[1]);
                    MemoryUtil.memPutShort(at + 4, (short) now[2]);
                }
            }
        }
        int v = a.zomboidcraft$vertices() + count;
        a.zomboidcraft$setVertices(v);
        a.zomboidcraft$setVertexPointer(dst + bytes - stride);
        a.zomboidcraft$setElementsToFill(0);
        last = bb;
        lastVertices = v;
        return true;
    }

    /** Writes the quads as write does, into memory at dst (any thread: reads only what it's given). */
    static void fill(long dst, Layout l, Matrix4f m, Matrix3f nm, int[] quads, int q0, int q1, boolean back, float[] p,
                     float[] n, float[] uv, int light, int overlay, int ent, int blockEnt, int item) {
        float m00 = m.m00(), m01 = m.m01(), m02 = m.m02(), m10 = m.m10(), m11 = m.m11(), m12 = m.m12();
        float m20 = m.m20(), m21 = m.m21(), m22 = m.m22(), m30 = m.m30(), m31 = m.m31(), m32 = m.m32();
        float n00 = nm.m00(), n01 = nm.m01(), n02 = nm.m02(), n10 = nm.m10(), n11 = nm.m11(), n12 = nm.m12();
        float n20 = nm.m20(), n21 = nm.m21(), n22 = nm.m22();
        boolean ext = l.extended();
        int stride = l.stride;
        float[] x = new float[4], y = new float[4], z = new float[4], u = new float[4], w = new float[4];
        int[] vi = new int[4];
        long at = dst;
        for (int s = 0; s < (back ? 2 : 1); s++) {
            float f = s == 0 ? 1 : -1;
            for (int q = q0; q < q1; q += 4) {
                for (int k = 0; k < 4; k++) {
                    int v = quads[q + (s == 0 ? k : 3 - k)], i = v * 3;
                    float px = p[i], py = p[i + 1], pz = p[i + 2];
                    x[k] = m00 * px + m10 * py + m20 * pz + m30;
                    y[k] = m01 * px + m11 * py + m21 * pz + m31;
                    z[k] = m02 * px + m12 * py + m22 * pz + m32;
                    u[k] = uv[v * 2];
                    w[k] = uv[v * 2 + 1];
                    vi[k] = v;
                }
                int faceNormal = 0, tangent = 0;
                float midU = 0, midV = 0;
                if (ext) {
                    // as Iris does for a quad: its face normal across the diagonals, the tangent from corners 0-2
                    float dx0 = x[2] - x[0], dy0 = y[2] - y[0], dz0 = z[2] - z[0];
                    float dx1 = x[3] - x[1], dy1 = y[3] - y[1], dz1 = z[3] - z[1];
                    float fx = dy0 * dz1 - dz0 * dy1, fy = dz0 * dx1 - dx0 * dz1, fz = dx0 * dy1 - dy0 * dx1;
                    float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
                    if (fl > 0) {
                        fx /= fl;
                        fy /= fl;
                        fz /= fl;
                    }
                    faceNormal = pack(fx, fy, fz, 0);
                    tangent = tangent(fx, fy, fz, x, y, z, u, w);
                    midU = (u[0] + u[1] + u[2] + u[3]) * 0.25f;
                    midV = (w[0] + w[1] + w[2] + w[3]) * 0.25f;
                }
                for (int k = 0; k < 4; k++) {
                    MemoryUtil.memPutFloat(at + l.pos, x[k]);
                    MemoryUtil.memPutFloat(at + l.pos + 4, y[k]);
                    MemoryUtil.memPutFloat(at + l.pos + 8, z[k]);
                    MemoryUtil.memPutInt(at + l.color, 0xFFFFFFFF);
                    MemoryUtil.memPutFloat(at + l.uv0, u[k]);
                    MemoryUtil.memPutFloat(at + l.uv0 + 4, w[k]);
                    MemoryUtil.memPutShort(at + l.uv1, (short) (overlay & 0xFFFF));
                    MemoryUtil.memPutShort(at + l.uv1 + 2, (short) (overlay >> 16 & 0xFFFF));
                    MemoryUtil.memPutShort(at + l.uv2, (short) (light & 0xFFFF));
                    MemoryUtil.memPutShort(at + l.uv2 + 2, (short) (light >> 16 & 0xFFFF));
                    if (ext) {
                        MemoryUtil.memPutInt(at + l.normal, faceNormal);
                        MemoryUtil.memPutShort(at + l.entity, (short) ent);
                        MemoryUtil.memPutShort(at + l.entity + 2, (short) blockEnt);
                        MemoryUtil.memPutShort(at + l.entity + 4, (short) item);
                        MemoryUtil.memPutShort(at + l.entity + 6, (short) 0);
                        MemoryUtil.memPutFloat(at + l.midTex, midU);
                        MemoryUtil.memPutFloat(at + l.midTex + 4, midV);
                        MemoryUtil.memPutInt(at + l.tangent, tangent);
                    } else {
                        // as setNormal(pose, ...) does: turned by the pose, made unit length
                        int i = vi[k] * 3;
                        float ax = f * n[i], ay = f * n[i + 1], az = f * n[i + 2];
                        float nx = n00 * ax + n10 * ay + n20 * az, ny = n01 * ax + n11 * ay + n21 * az;
                        float nz = n02 * ax + n12 * ay + n22 * az;
                        float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                        if (nl > 0) {
                            nx /= nl;
                            ny /= nl;
                            nz /= nl;
                        }
                        MemoryUtil.memPutByte(at + l.normal, normalByte(nx));
                        MemoryUtil.memPutByte(at + l.normal + 1, normalByte(ny));
                        MemoryUtil.memPutByte(at + l.normal + 2, normalByte(nz));
                    }
                    at += stride;
                }
            }
        }
    }

    private static byte normalByte(float v) {
        return (byte) ((int) (Math.clamp(v, -1f, 1f) * 127.0f) & 255);
    }

    /** Four components in one int, x in the lowest byte (Iris's and Sodium's packed normal). */
    private static int pack(float x, float y, float z, float w) {
        return ((int) (x * 127) & 0xFF) | (((int) (y * 127) & 0xFF) << 8) | (((int) (z * 127) & 0xFF) << 16)
                | (((int) (w * 127) & 0xFF) << 24);
    }

    /** The usual tangent from a triangle's edges and texture coordinates (corners 0, 1, 2), w the bitangent's side. */
    private static int tangent(float nx, float ny, float nz, float[] x, float[] y, float[] z, float[] u, float[] v) {
        float e1x = x[1] - x[0], e1y = y[1] - y[0], e1z = z[1] - z[0];
        float e2x = x[2] - x[0], e2y = y[2] - y[0], e2z = z[2] - z[0];
        float du1 = u[1] - u[0], dv1 = v[1] - v[0], du2 = u[2] - u[0], dv2 = v[2] - v[0];
        float den = du1 * dv2 - du2 * dv1;
        float f = den == 0 ? 1 : 1 / den;
        float tx = f * (dv2 * e1x - dv1 * e2x), ty = f * (dv2 * e1y - dv1 * e2y), tz = f * (dv2 * e1z - dv1 * e2z);
        float tl = (float) (1 / Math.sqrt(tx * tx + ty * ty + tz * tz));
        tx *= tl;
        ty *= tl;
        tz *= tl;
        float bx = f * (-du2 * e1x + du1 * e2x), by = f * (-du2 * e1y + du1 * e2y), bz = f * (-du2 * e1z + du1 * e2z);
        float bl = (float) (1 / Math.sqrt(bx * bx + by * by + bz * bz));
        bx *= bl;
        by *= bl;
        bz *= bl;
        float pbx = ty * nz - tz * ny, pby = tz * nx - tx * nz, pbz = tx * ny - ty * nx;
        float dot = bx * pbx + by * pby + bz * pbz;
        return pack(tx, ty, tz, dot < 0 ? -1 : 1);
    }
}
