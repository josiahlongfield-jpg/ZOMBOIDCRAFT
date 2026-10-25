package com.zomboidcraft.client.cars;

import com.mojang.blaze3d.platform.NativeImage;
import com.zomboidcraft.ZomboidCraft;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
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
    /** Triangles in model space (metres, nose toward +z), their uvs, and a flat normal each. */
    public record Mesh(int triangles, float[] pos, float[] uv, float[] normal) {
    }

    public record Ready(Mesh mesh, Identifier texture) {
    }

    private static final class Variant {
        Mesh mesh;
        byte[] jpeg;
        Identifier texture;
    }

    private static final Map<String, Map<String, Variant>> MODELS = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();

    /** The mesh and texture for a model in a state, or null if it can't be loaded. Render thread only. */
    public static Ready get(String model, String state) {
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
        return new Ready(v.mesh, v.texture);
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
            if (magic[0] != 'Z' || magic[1] != 'C' || magic[2] != 'A' || magic[3] != 'R' || in.readInt() != 1) {
                throw new IOException("not a car file");
            }
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                String name = in.readUTF();
                Variant v = new Variant();
                v.jpeg = in.readNBytes(in.readInt());
                int tris = in.readInt();
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
                v.mesh = new Mesh(tris, pos, uv, normal);
                out.put(name, v);
            }
        }
        return out;
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
    private static void unscramble(byte[] data, String model) {
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
