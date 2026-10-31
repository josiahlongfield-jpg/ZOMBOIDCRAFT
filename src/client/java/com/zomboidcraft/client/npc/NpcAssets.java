package com.zomboidcraft.client.npc;

import com.mojang.blaze3d.platform.NativeImage;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.npc.Npcs;
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
 * Loads an NPC's packed file the first time one is drawn: config/zomboidcraft/npc/ID.znpc on this PC if it's there (models
 * that must never ship with the mod), otherwise assets/zomboidcraft/npc/ID.znpc in the jar (both written by tools/npc/pack.py);
 * it unscrambles and inflates it, and turns each look's texture into a GPU texture once something wears it.
 */
public final class NpcAssets {
    /**
     * Vertices in model space (blocks, feet at y=0, facing +z), their uvs and smooth normals, and triangles over them. A
     * rigged model also has its bones (null if not) and each vertex's two bones and how much of the first it follows.
     */
    public record Mesh(int vertices, float[] pos, float[] uv, float[] normal, int[] tris, Bone[] bones, byte[] bone0, byte[] bone1,
                       float[] weight) {
    }

    /** A bone: its parent's index (-1 for the root) and the point it turns about. */
    public record Bone(String name, int parent, float px, float py, float pz) {
    }

    private static final class Model {
        Mesh mesh;
        final Map<String, byte[]> jpegs = new HashMap<>();
        final Map<String, Identifier> textures = new HashMap<>();
    }

    private static final Map<String, Model> MODELS = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();

    public static Mesh mesh(String model) {
        Model m = load(model);
        return m == null ? null : m.mesh;
    }

    /** The texture for a look ("raw" if the model hasn't that one), or null if it can't be loaded. Render thread only. */
    public static Identifier texture(String model, String look) {
        Model m = load(model);
        if (m == null) {
            return null;
        }
        if (!m.jpegs.containsKey(look) && !m.textures.containsKey(look)) {
            look = "raw";
        }
        Identifier id = m.textures.get(look);
        if (id == null) {
            byte[] jpeg = m.jpegs.remove(look);
            if (jpeg == null) {
                return null;
            }
            id = ZomboidCraft.id("dynamic/npc/" + model + "/" + look);
            try {
                String name = model;
                Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "ZomboidCraft NPC " + name, decode(jpeg)));
            } catch (IOException e) {
                ZomboidCraft.LOGGER.error("Bad texture in NPC model {}", model, e);
                MISSING.add(model);
                return null;
            }
            m.textures.put(look, id);
        }
        return id;
    }

    private static Model load(String model) {
        if (MISSING.contains(model)) {
            return null;
        }
        Model m = MODELS.get(model);
        if (m == null) {
            try {
                m = read(model);
            } catch (Exception e) {
                ZomboidCraft.LOGGER.error("Couldn't load NPC model {}", model, e);
                MISSING.add(model);
                return null;
            }
            MODELS.put(model, m);
        }
        return m;
    }

    private static Model read(String model) throws IOException {
        byte[] data;
        java.nio.file.Path local = Npcs.localFolder().resolve(model + ".znpc");
        if (java.nio.file.Files.isRegularFile(local)) {
            data = java.nio.file.Files.readAllBytes(local);
        } else {
            try (InputStream in = Minecraft.getInstance().getResourceManager().open(ZomboidCraft.id("npc/" + model + ".znpc"))) {
                data = in.readAllBytes();
            }
        }
        unscramble(data, model);
        Model out = new Model();
        try (DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data)))) {
            byte[] magic = in.readNBytes(4);
            int version;
            if (magic[0] != 'Z' || magic[1] != 'N' || magic[2] != 'P' || magic[3] != 'C' || (version = in.readInt()) < 1 || version > 2) {
                throw new IOException("not an NPC file");
            }
            int verts = in.readInt();
            float[] pos = new float[verts * 3], uv = new float[verts * 2], normal = new float[verts * 3];
            for (int v = 0; v < verts; v++) {
                pos[v * 3] = in.readShort() / 1000f;
                pos[v * 3 + 1] = in.readShort() / 1000f;
                pos[v * 3 + 2] = in.readShort() / 1000f;
                uv[v * 2] = in.readChar() / 65535f;
                uv[v * 2 + 1] = in.readChar() / 65535f;
                float nx = in.readByte(), ny = in.readByte(), nz = in.readByte();
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len < 1e-3f) {
                    ny = len = 1;
                }
                normal[v * 3] = nx / len;
                normal[v * 3 + 1] = ny / len;
                normal[v * 3 + 2] = nz / len;
            }
            int tris = in.readInt();
            int[] idx = new int[tris * 3];
            for (int i = 0; i < idx.length; i++) {
                idx[i] = in.readChar();
            }
            int looks = in.readInt();
            for (int i = 0; i < looks; i++) {
                String name = in.readUTF();
                out.jpegs.put(name, in.readNBytes(in.readInt()));
            }
            Bone[] bones = null;
            byte[] b0 = null, b1 = null;
            float[] w = null;
            int count = version >= 2 ? in.readInt() : 0;
            if (count > 0) {
                bones = new Bone[count];
                for (int i = 0; i < count; i++) {
                    bones[i] = new Bone(in.readUTF(), in.readByte(), in.readFloat(), in.readFloat(), in.readFloat());
                }
                b0 = new byte[verts];
                b1 = new byte[verts];
                w = new float[verts];
                for (int v = 0; v < verts; v++) {
                    b0[v] = in.readByte();
                    b1[v] = in.readByte();
                    w[v] = (in.readByte() & 255) / 255f;
                }
            }
            out.mesh = new Mesh(verts, pos, uv, normal, idx, bones, b0, b1, w);
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
    static void unscramble(byte[] data, String model) {
        int x = 0x3C6EF372 ^ model.hashCode();
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

    private NpcAssets() {
    }
}
