package com.zomboidcraft.client.reach;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Reads one converted Halo level piece (a .glb from additions\halo-reach\world) into plain arrays, already placed in
 * the Reach world: node matrices, the level's turn and its offset applied, texture tiling baked into the UVs, normals
 * worked out from the triangles (the converter writes none). Nothing here touches the game, so it can run off the
 * render thread; {@link ReachMeshes} uploads the result.
 */
public final class ReachGlb {
    /** One material: its colour picture (PNG bytes, or null for a plain colour) and how it blends. */
    public record Material(String name, byte[] png, int color, boolean cutout, boolean blend) {
    }

    /** One drawable run of triangles with one material, in world coordinates. */
    public static final class Batch {
        public final int material;
        public final float[] pos;
        public final float[] uv;
        public final float[] normal;
        public final int[] color;
        public final int[] index;
        public final float minX, minY, minZ, maxX, maxY, maxZ;

        Batch(int material, float[] pos, float[] uv, float[] normal, int[] color, int[] index) {
            this.material = material;
            this.pos = pos;
            this.uv = uv;
            this.normal = normal;
            this.color = color;
            this.index = index;
            float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, z0 = Float.MAX_VALUE;
            float x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
            for (int i = 0; i < pos.length; i += 3) {
                x0 = Math.min(x0, pos[i]);
                y0 = Math.min(y0, pos[i + 1]);
                z0 = Math.min(z0, pos[i + 2]);
                x1 = Math.max(x1, pos[i]);
                y1 = Math.max(y1, pos[i + 1]);
                z1 = Math.max(z1, pos[i + 2]);
            }
            minX = x0;
            minY = y0;
            minZ = z0;
            maxX = x1;
            maxY = y1;
            maxZ = z1;
        }

        public int vertices() {
            return pos.length / 3;
        }
    }

    public record Level(List<Material> materials, List<Batch> batches) {
        public int triangles() {
            int n = 0;
            for (Batch b : batches) {
                n += b.index.length / 3;
            }
            return n;
        }
    }

    private ReachGlb() {
    }

    /** Reads a .glb and places it: turned by yaw degrees (as tools/reach/voxelize.py turns the walls), then moved. */
    public static Level read(Path file, float yaw, float ox, float oy, float oz) throws IOException {
        return read(file, file.toAbsolutePath().getParent(), yaw, ox, oy, oz);
    }

    /** As above, with the folder its tex/ pictures are found in (a joined-up split file lives somewhere else). */
    public static Level read(Path file, Path textureDir, float yaw, float ox, float oy, float oz) throws IOException {
        ByteBuffer all = ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN);
        if (all.getInt(0) != 0x46546C67) {
            throw new IOException("not a glb: " + file);
        }
        int jsonLength = all.getInt(12);
        JsonObject json = JsonParser.parseString(new String(all.array(), 20, jsonLength, StandardCharsets.UTF_8)).getAsJsonObject();
        int binStart = 20 + jsonLength + 8;
        Glb g = new Glb(json, all, binStart, textureDir);

        List<Material> materials = new ArrayList<>();
        float[][] uvTransforms = new float[size(json, "materials") + 1][];
        JsonArray mats = arr(json, "materials");
        for (int m = 0; m < mats.size(); m++) {
            JsonObject mat = mats.get(m).getAsJsonObject();
            JsonObject pbr = mat.has("pbrMetallicRoughness") ? mat.getAsJsonObject("pbrMetallicRoughness") : new JsonObject();
            int color = 0xFFFFFFFF;
            if (pbr.has("baseColorFactor")) {
                JsonArray f = pbr.getAsJsonArray("baseColorFactor");
                color = argb(f.get(3).getAsFloat(), f.get(0).getAsFloat(), f.get(1).getAsFloat(), f.get(2).getAsFloat());
            }
            byte[] png = null;
            if (pbr.has("baseColorTexture")) {
                JsonObject ref = pbr.getAsJsonObject("baseColorTexture");
                png = g.image(ref.get("index").getAsInt());
                uvTransforms[m] = textureTransform(ref);
            }
            if (png == null && !pbr.has("baseColorFactor")) {
                // no picture and no colour (Halo's blended ground, until its textures are baked): weathered rock grey
                color = 0xFF7A766E;
            }
            String alpha = mat.has("alphaMode") ? mat.get("alphaMode").getAsString() : "OPAQUE";
            materials.add(new Material(mat.has("name") ? mat.get("name").getAsString() : "material " + m, png, color,
                    alpha.equals("MASK"), alpha.equals("BLEND")));
        }
        int fallback = materials.size();
        materials.add(new Material("plain", null, 0xFFB0B0B0, false, false));

        Matrix4f place = new Matrix4f().translate(ox, oy, oz).rotateY((float) Math.toRadians(-yaw));
        List<Batch> batches = new ArrayList<>();
        JsonArray scenes = arr(json, "scenes");
        int scene = json.has("scene") ? json.get("scene").getAsInt() : 0;
        if (scenes.isEmpty()) {
            return new Level(materials, batches);
        }
        for (JsonElement root : scenes.get(scene).getAsJsonObject().getAsJsonArray("nodes")) {
            walk(g, root.getAsInt(), place, uvTransforms, fallback, batches);
        }
        return new Level(materials, batches);
    }

    private static void walk(Glb g, int nodeIndex, Matrix4f parent, float[][] uvTransforms, int fallback, List<Batch> out) {
        JsonObject node = arr(g.json, "nodes").get(nodeIndex).getAsJsonObject();
        Matrix4f m = new Matrix4f(parent);
        if (node.has("matrix")) {
            JsonArray a = node.getAsJsonArray("matrix");
            float[] f = new float[16];
            for (int i = 0; i < 16; i++) {
                f[i] = a.get(i).getAsFloat();
            }
            m.mul(new Matrix4f().set(f));
        } else {
            if (node.has("translation")) {
                JsonArray t = node.getAsJsonArray("translation");
                m.translate(t.get(0).getAsFloat(), t.get(1).getAsFloat(), t.get(2).getAsFloat());
            }
            if (node.has("rotation")) {
                JsonArray r = node.getAsJsonArray("rotation");
                m.rotate(new org.joml.Quaternionf(r.get(0).getAsFloat(), r.get(1).getAsFloat(), r.get(2).getAsFloat(), r.get(3).getAsFloat()));
            }
            if (node.has("scale")) {
                JsonArray s = node.getAsJsonArray("scale");
                m.scale(s.get(0).getAsFloat(), s.get(1).getAsFloat(), s.get(2).getAsFloat());
            }
        }
        if (node.has("mesh")) {
            JsonObject mesh = arr(g.json, "meshes").get(node.get("mesh").getAsInt()).getAsJsonObject();
            for (JsonElement p : mesh.getAsJsonArray("primitives")) {
                Batch b = primitive(g, p.getAsJsonObject(), m, uvTransforms, fallback);
                if (b != null) {
                    out.add(b);
                }
            }
        }
        if (node.has("children")) {
            for (JsonElement c : node.getAsJsonArray("children")) {
                walk(g, c.getAsInt(), m, uvTransforms, fallback, out);
            }
        }
    }

    private static Batch primitive(Glb g, JsonObject p, Matrix4f m, float[][] uvTransforms, int fallback) {
        if (p.has("mode") && p.get("mode").getAsInt() != 4) {
            return null;   // only triangle lists
        }
        JsonObject at = p.getAsJsonObject("attributes");
        if (!at.has("POSITION")) {
            return null;
        }
        float[] local = g.floats(at.get("POSITION").getAsInt());
        int n = local.length / 3;
        int[] index = p.has("indices") ? g.ints(p.get("indices").getAsInt()) : sequence(n);
        int material = p.has("material") ? p.get("material").getAsInt() : fallback;

        float[] pos = new float[n * 3];
        Vector3f v = new Vector3f();
        for (int i = 0; i < n; i++) {
            m.transformPosition(local[i * 3], local[i * 3 + 1], local[i * 3 + 2], v);
            pos[i * 3] = v.x;
            pos[i * 3 + 1] = v.y;
            pos[i * 3 + 2] = v.z;
        }
        // a mirroring node matrix turns triangles inside out: swap two corners back
        if (m.determinant3x3() < 0) {
            for (int i = 0; i + 2 < index.length; i += 3) {
                int t = index[i + 1];
                index[i + 1] = index[i + 2];
                index[i + 2] = t;
            }
        }

        float[] uv = at.has("TEXCOORD_0") ? g.floats(at.get("TEXCOORD_0").getAsInt()) : new float[n * 2];
        float[] tr = material < uvTransforms.length ? uvTransforms[material] : null;
        if (tr != null) {
            // KHR_texture_transform: scale, then rotate, then offset
            float c = (float) Math.cos(tr[4]), s = (float) Math.sin(tr[4]);
            for (int i = 0; i < n; i++) {
                float u = uv[i * 2] * tr[2], w = uv[i * 2 + 1] * tr[3];
                uv[i * 2] = c * u + s * w + tr[0];
                uv[i * 2 + 1] = -s * u + c * w + tr[1];
            }
        }

        int[] color = new int[n];
        if (at.has("COLOR_0")) {
            float[] c = g.floats(at.get("COLOR_0").getAsInt());
            int k = c.length / n;
            for (int i = 0; i < n; i++) {
                color[i] = argb(k == 4 ? c[i * 4 + 3] : 1f, c[i * k], c[i * k + 1], c[i * k + 2]);
            }
        } else {
            java.util.Arrays.fill(color, 0xFFFFFFFF);
        }
        return new Batch(material, pos, uv, normals(pos, index), color, index);
    }

    /** Smooth normals: each corner gets the area-weighted sum of its triangles' faces. */
    static float[] normals(float[] pos, int[] index) {
        float[] nrm = new float[pos.length];
        for (int i = 0; i + 2 < index.length; i += 3) {
            int a = index[i] * 3, b = index[i + 1] * 3, c = index[i + 2] * 3;
            float ux = pos[b] - pos[a], uy = pos[b + 1] - pos[a + 1], uz = pos[b + 2] - pos[a + 2];
            float vx = pos[c] - pos[a], vy = pos[c + 1] - pos[a + 1], vz = pos[c + 2] - pos[a + 2];
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            for (int k : new int[]{a, b, c}) {
                nrm[k] += nx;
                nrm[k + 1] += ny;
                nrm[k + 2] += nz;
            }
        }
        for (int i = 0; i < nrm.length; i += 3) {
            float l = (float) Math.sqrt(nrm[i] * nrm[i] + nrm[i + 1] * nrm[i + 1] + nrm[i + 2] * nrm[i + 2]);
            if (l > 1e-12f) {
                nrm[i] /= l;
                nrm[i + 1] /= l;
                nrm[i + 2] /= l;
            } else {
                nrm[i + 1] = 1f;
            }
        }
        return nrm;
    }

    /** offset u, offset v, scale u, scale v, rotation; null when the texture has no transform. */
    private static float[] textureTransform(JsonObject ref) {
        if (!ref.has("extensions") || !ref.getAsJsonObject("extensions").has("KHR_texture_transform")) {
            return null;
        }
        JsonObject t = ref.getAsJsonObject("extensions").getAsJsonObject("KHR_texture_transform");
        float[] out = {0, 0, 1, 1, 0};
        if (t.has("offset")) {
            out[0] = t.getAsJsonArray("offset").get(0).getAsFloat();
            out[1] = t.getAsJsonArray("offset").get(1).getAsFloat();
        }
        if (t.has("scale")) {
            out[2] = t.getAsJsonArray("scale").get(0).getAsFloat();
            out[3] = t.getAsJsonArray("scale").get(1).getAsFloat();
        }
        if (t.has("rotation")) {
            out[4] = t.get("rotation").getAsFloat();
        }
        return out;
    }

    private static int[] sequence(int n) {
        int[] s = new int[n];
        for (int i = 0; i < n; i++) {
            s[i] = i;
        }
        return s;
    }

    static int argb(float a, float r, float g, float b) {
        return channel(a) << 24 | channel(r) << 16 | channel(g) << 8 | channel(b);
    }

    private static int channel(float f) {
        return Math.max(0, Math.min(255, Math.round(f * 255f)));
    }

    private static JsonArray arr(JsonObject json, String key) {
        return json.has(key) ? json.getAsJsonArray(key) : new JsonArray();
    }

    private static int size(JsonObject json, String key) {
        return arr(json, key).size();
    }

    /** The binary chunk and the accessor reading. */
    private static final class Glb {
        final JsonObject json;
        final ByteBuffer bin;
        final int binStart;
        final Path dir;

        Glb(JsonObject json, ByteBuffer bin, int binStart, Path dir) {
            this.json = json;
            this.bin = bin;
            this.binStart = binStart;
            this.dir = dir;
        }

        private JsonObject view(int i) {
            return arr(json, "bufferViews").get(i).getAsJsonObject();
        }

        private static int components(String type) {
            return switch (type) {
                case "SCALAR" -> 1;
                case "VEC2" -> 2;
                case "VEC3" -> 3;
                case "VEC4" -> 4;
                case "MAT4" -> 16;
                default -> throw new IllegalArgumentException(type);
            };
        }

        private static int bytes(int componentType) {
            return switch (componentType) {
                case 5120, 5121 -> 1;
                case 5122, 5123 -> 2;
                default -> 4;
            };
        }

        /** An accessor as floats (normalized integers become 0..1). */
        float[] floats(int accessor) {
            JsonObject a = arr(json, "accessors").get(accessor).getAsJsonObject();
            int count = a.get("count").getAsInt(), k = components(a.get("type").getAsString());
            int type = a.get("componentType").getAsInt();
            boolean normalized = a.has("normalized") && a.get("normalized").getAsBoolean();
            float[] out = new float[count * k];
            if (!a.has("bufferView")) {
                return out;
            }
            JsonObject v = view(a.get("bufferView").getAsInt());
            int base = binStart + (v.has("byteOffset") ? v.get("byteOffset").getAsInt() : 0)
                    + (a.has("byteOffset") ? a.get("byteOffset").getAsInt() : 0);
            int stride = v.has("byteStride") ? v.get("byteStride").getAsInt() : k * bytes(type);
            for (int i = 0; i < count; i++) {
                int at = base + i * stride;
                for (int c = 0; c < k; c++) {
                    float f = switch (type) {
                        case 5126 -> bin.getFloat(at + c * 4);
                        case 5121 -> (bin.get(at + c) & 0xFF) / (normalized ? 255f : 1f);
                        case 5123 -> (bin.getShort(at + c * 2) & 0xFFFF) / (normalized ? 65535f : 1f);
                        case 5120 -> Math.max(bin.get(at + c) / (normalized ? 127f : 1f), normalized ? -1f : -128f);
                        case 5122 -> Math.max(bin.getShort(at + c * 2) / (normalized ? 32767f : 1f), normalized ? -1f : -32768f);
                        default -> (float) (bin.getInt(at + c * 4) & 0xFFFFFFFFL);
                    };
                    out[i * k + c] = f;
                }
            }
            return out;
        }

        int[] ints(int accessor) {
            JsonObject a = arr(json, "accessors").get(accessor).getAsJsonObject();
            int count = a.get("count").getAsInt();
            int type = a.get("componentType").getAsInt();
            JsonObject v = view(a.get("bufferView").getAsInt());
            int base = binStart + (v.has("byteOffset") ? v.get("byteOffset").getAsInt() : 0)
                    + (a.has("byteOffset") ? a.get("byteOffset").getAsInt() : 0);
            int[] out = new int[count];
            for (int i = 0; i < count; i++) {
                out[i] = switch (type) {
                    case 5121 -> bin.get(base + i) & 0xFF;
                    case 5123 -> bin.getShort(base + i * 2) & 0xFFFF;
                    default -> bin.getInt(base + i * 4);
                };
            }
            return out;
        }

        /** A texture's picture bytes: from the binary chunk, or a file next to the .glb. */
        byte[] image(int texture) {
            try {
                JsonObject tex = arr(json, "textures").get(texture).getAsJsonObject();
                if (!tex.has("source")) {
                    return null;
                }
                JsonObject img = arr(json, "images").get(tex.get("source").getAsInt()).getAsJsonObject();
                if (img.has("bufferView")) {
                    JsonObject v = view(img.get("bufferView").getAsInt());
                    int off = binStart + (v.has("byteOffset") ? v.get("byteOffset").getAsInt() : 0);
                    byte[] out = new byte[v.get("byteLength").getAsInt()];
                    System.arraycopy(bin.array(), off, out, 0, out.length);
                    return out;
                }
                if (img.has("uri")) {
                    String uri = java.net.URLDecoder.decode(img.get("uri").getAsString(), StandardCharsets.UTF_8);
                    // next to the level file, or in the shared tex/ folder one up (levels share their pictures)
                    for (Path base : new Path[]{dir, dir.getParent()}) {
                        if (base == null) {
                            continue;
                        }
                        Path p = base.resolve(uri).normalize();
                        if (p.startsWith(base) && Files.isRegularFile(p)) {
                            return Files.readAllBytes(p);
                        }
                    }
                    return null;
                }
            } catch (IOException | RuntimeException e) {
                return null;
            }
            return null;
        }
    }
}
