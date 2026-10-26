package com.zomboidcraft.client.cars;

import com.mojang.blaze3d.platform.NativeImage;
import com.zomboidcraft.ZomboidCraft;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.InflaterInputStream;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Loads a car's packed file (assets/zomboidcraft/cars/ID.zcar, written by tools/cars/pack.py)
 * the first time one is drawn: unscrambles and inflates it, keeps every state's mesh, and turns
 * a state's texture into a GPU texture only once a car in that state shows up.
 */
public final class CarAssets {
    /**
     * Triangles in model space (metres, nose toward +z), their uvs, and a flat normal each. The
     * first {@code opaque} are solid; the rest are glass, drawn see-through.
     */
    public record Mesh(int triangles, int opaque, float[] pos, float[] uv, float[] normal) {
    }

    /** A boot lid, tailgate or back door: turns about the axis through pivot by up to angle radians. */
    public record Lid(boolean yAxis, float px, float py, float pz, float angle, Mesh mesh) {
    }

    /** A road wheel: spins about the x axis through its centre, and the front ones steer. */
    public record Wheel(float cx, float cy, float cz, float radius, boolean steers, Mesh mesh) {
    }

    /**
     * A helicopter rotor: spins about the axis (ax, ay, az) through pivot. The bought models have
     * their rotors modelled in with the airframe, so they're cut out of it when it loads ({@link #rotors}).
     */
    public record Rotor(float ax, float ay, float az, float px, float py, float pz, Mesh mesh) {
    }

    public record Ready(Mesh mesh, List<Lid> lids, List<Wheel> wheels, List<Rotor> rotors, Identifier texture) {
    }

    private static final class Variant {
        Mesh mesh;
        List<Lid> lids;
        List<Wheel> wheels;
        List<Rotor> rotors = List.of();
        byte[] jpeg;
        Identifier texture;
        /** The car at each level of detail, simplified the first time it's needed. */
        final Ready[] levels = new Ready[CarLod.LEVELS];
    }

    private static final Map<String, Map<String, Variant>> MODELS = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();

    /** The full mesh and texture for a model in a state, or null if it can't be loaded. Render thread only. */
    public static Ready get(String model, String state) {
        return get(model, state, 0);
    }

    /** The mesh at a level of detail ({@link CarLod}) and texture, or null if it can't be loaded. Render thread only. */
    public static Ready get(String model, String state, int level) {
        if (MISSING.contains(model)) {
            return null;
        }
        Map<String, Variant> variants = MODELS.get(model);
        if (variants == null) {
            try {
                variants = read(model);
            } catch (Exception e) {
                ZomboidCraft.LOGGER.error("Couldn't load car model {}", model, e);
                MISSING.add(model);
                return null;
            }
            MODELS.put(model, variants);
        }
        Variant v = variants.get(state);
        if (v == null) {
            v = variants.get("usable");
            if (v == null) {
                return null;
            }
        }
        if (v.texture == null) {
            Identifier id = ZomboidCraft.id("dynamic/car/" + model + "/" + state);
            try {
                NativeImage image = decode(v.jpeg);
                Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "ZomboidCraft car " + model, image));
            } catch (IOException e) {
                ZomboidCraft.LOGGER.error("Bad texture in car model {}", model, e);
                MISSING.add(model);
                return null;
            }
            v.texture = id;
            v.jpeg = null;
        }
        Ready ready = v.levels[level];
        if (ready == null) {
            List<Lid> lids = new ArrayList<>(v.lids.size());
            for (Lid lid : v.lids) {
                lids.add(new Lid(lid.yAxis(), lid.px(), lid.py(), lid.pz(), lid.angle(), CarLod.simplify(lid.mesh(), level)));
            }
            List<Wheel> wheels = new ArrayList<>(v.wheels.size());
            for (Wheel w : v.wheels) {
                wheels.add(new Wheel(w.cx(), w.cy(), w.cz(), w.radius(), w.steers(), CarLod.simplify(w.mesh(), level)));
            }
            List<Rotor> rotors = new ArrayList<>(v.rotors.size());
            for (Rotor r : v.rotors) {
                rotors.add(new Rotor(r.ax(), r.ay(), r.az(), r.px(), r.py(), r.pz(), CarLod.simplify(r.mesh(), level)));
            }
            ready = new Ready(CarLod.simplify(v.mesh, level), lids, wheels, rotors, v.texture);
            v.levels[level] = ready;
        }
        return ready;
    }

    private static Map<String, Variant> read(String model) throws IOException {
        byte[] data;
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(ZomboidCraft.id("cars/" + model + ".zcar"))) {
            data = in.readAllBytes();
        }
        unscramble(data, model);
        Map<String, Variant> out = new HashMap<>();
        try (DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data)))) {
            byte[] magic = in.readNBytes(4);
            if (magic[0] != 'Z' || magic[1] != 'C' || magic[2] != 'A' || magic[3] != 'R' || in.readInt() != 3) {
                throw new IOException("not a car file");
            }
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                String name = in.readUTF();
                Variant v = new Variant();
                v.jpeg = in.readNBytes(in.readInt());
                v.mesh = readMesh(in);
                int lids = in.readInt();
                v.lids = new ArrayList<>(lids);
                for (int k = 0; k < lids; k++) {
                    boolean yAxis = in.readByte() == 1;
                    float px = in.readFloat(), py = in.readFloat(), pz = in.readFloat(), angle = in.readFloat();
                    v.lids.add(new Lid(yAxis, px, py, pz, angle, readMesh(in)));
                }
                int wheels = in.readInt();
                v.wheels = new ArrayList<>(wheels);
                for (int k = 0; k < wheels; k++) {
                    float cx = in.readFloat(), cy = in.readFloat(), cz = in.readFloat(), r = in.readFloat();
                    boolean steers = in.readByte() == 1;
                    v.wheels.add(new Wheel(cx, cy, cz, r, steers, readMesh(in)));
                }
                if ("usable".equals(name) || "abandoned".equals(name)) {
                    rotors(model, v);
                }
                out.put(name, v);
            }
        }
        return out;
    }

    /**
     * Cuts a helicopter's rotors out of its airframe so they can spin: the main rotor is whatever lies
     * within half a metre of its (forward-tilted) disc and inside its blades' reach, the tail rotor
     * whatever is out on its side of the fin. Measured on the bought AH-64D (tools/cars/zcar.py).
     */
    private static void rotors(String model, Variant v) {
        if (!"heli_hornbill".equals(model)) {
            return;
        }
        float nx = 0, ny = 1, nz = 0.0887f, len = (float) Math.sqrt(ny * ny + nz * nz);
        ny /= len;
        nz /= len;
        float hx = 0, hy = 3.97f, hz = 1.77f;
        Mesh m = v.mesh;
        java.util.BitSet main = new java.util.BitSet(), tail = new java.util.BitSet();
        for (int t = 0; t < m.opaque(); t++) {
            float cx = 0, cy = 0, cz = 0;
            for (int c = 0; c < 3; c++) {
                cx += m.pos()[t * 9 + c * 3] / 3;
                cy += m.pos()[t * 9 + c * 3 + 1] / 3;
                cz += m.pos()[t * 9 + c * 3 + 2] / 3;
            }
            float dx = cx - hx, dy = cy - hy, dz = cz - hz;
            float d = dx * nx + dy * ny + dz * nz;
            float rx = dx - d * nx, ry = dy - d * ny, rz = dz - d * nz;
            if (d > -0.42f && d < 0.45f && rx * rx + ry * ry + rz * rz < 7.4f * 7.4f && cz > -6) {
                main.set(t);
            } else if (cz < -6 && cx > 0.6f && cy > 2.8f) {
                tail.set(t);
            }
        }
        java.util.BitSet moving = (java.util.BitSet) main.clone();
        moving.or(tail);
        v.rotors = List.of(new Rotor(nx, ny, nz, hx, hy, hz, subset(m, main, false)),
                new Rotor(1, 0, 0, 0.646f, 3.835f, -7.429f, subset(m, tail, false)));
        v.mesh = subset(m, moving, true);
    }

    /** The triangles of a mesh that are (or, with without set, aren't) in take, solid ones still first. */
    private static Mesh subset(Mesh m, java.util.BitSet take, boolean without) {
        int n = 0, opaque = 0;
        for (int t = 0; t < m.triangles(); t++) {
            if (take.get(t) != without) {
                n++;
                if (t < m.opaque()) {
                    opaque++;
                }
            }
        }
        float[] pos = new float[n * 9], uv = new float[n * 6], normal = new float[n * 3];
        int k = 0;
        for (int t = 0; t < m.triangles(); t++) {
            if (take.get(t) == without) {
                continue;
            }
            System.arraycopy(m.pos(), t * 9, pos, k * 9, 9);
            System.arraycopy(m.uv(), t * 6, uv, k * 6, 6);
            System.arraycopy(m.normal(), t * 3, normal, k * 3, 3);
            k++;
        }
        return new Mesh(n, opaque, pos, uv, normal);
    }

    private static Mesh readMesh(DataInputStream in) throws IOException {
        int opaque = in.readInt(), tris = opaque + in.readInt();
        float[] pos = new float[tris * 9], uv = new float[tris * 6], normal = new float[tris * 3];
        for (int t = 0; t < tris * 3; t++) {
            pos[t * 3] = in.readShort() / 1000f;
            pos[t * 3 + 1] = in.readShort() / 1000f;
            pos[t * 3 + 2] = in.readShort() / 1000f;
            uv[t * 2] = in.readUnsignedShort() / 65535f;
            uv[t * 2 + 1] = in.readUnsignedShort() / 65535f;
        }
        for (int t = 0; t < tris; t++) {
            int a = t * 9;
            float ux = pos[a + 3] - pos[a], uy = pos[a + 4] - pos[a + 1], uz = pos[a + 5] - pos[a + 2];
            float vx = pos[a + 6] - pos[a], vy = pos[a + 7] - pos[a + 1], vz = pos[a + 8] - pos[a + 2];
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-9f) {
                len = 1;
                ny = 1;
            }
            normal[t * 3] = nx / len;
            normal[t * 3 + 1] = ny / len;
            normal[t * 3 + 2] = nz / len;
        }
        return new Mesh(tris, opaque, pos, uv, normal);
    }

    /** Textures ship as JPEG to keep the mod small; Minecraft only reads PNG, so Java decodes them. */
    private static NativeImage decode(byte[] jpeg) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpeg));
        if (img == null) {
            throw new IOException("unreadable texture");
        }
        NativeImage out = new NativeImage(img.getWidth(), img.getHeight(), false);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                out.setPixel(x, y, 0xFF000000 | img.getRGB(x, y));
            }
        }
        return out;
    }

    /** Undoes pack.py's xorshift32 scramble (seeded from the model id). */
    static void unscramble(byte[] data, String model) {
        int x = 0x5A0C4A55 ^ model.hashCode();
        if (x == 0) {
            x = 1;
        }
        for (int i = 0; i < data.length; i += 4) {
            x ^= x << 13;
            x ^= x >>> 17;
            x ^= x << 5;
            for (int j = 0; j < 4 && i + j < data.length; j++) {
                data[i + j] ^= (byte) (x >>> (24 - 8 * j));
            }
        }
    }

    private CarAssets() {
    }
}
