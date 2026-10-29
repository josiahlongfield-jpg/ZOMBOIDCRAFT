package com.zomboidcraft.client.creature.spider;

import com.mojang.blaze3d.platform.NativeImage;
import com.zomboidcraft.ZomboidCraft;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.InflaterInputStream;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

/**
 * The Harvestman's body (the bought "Spider 7" creature), from assets/zomboidcraft/creatures/spider.zsp, packed by
 * tools/creatures/spider/pack.py: its skinned body and skull, their textures and red glow maps, its 49 bones and the
 * model's own 30 animations. Model space is metres (one block each), +y up, +z toward its head, +x its left, its feet
 * on y=0 and its pelvis over the origin. Loaded the first time one is drawn; null if the file is missing or bad, and the
 * old Harvestman is drawn instead.
 */
public final class SpiderModel {
    /** One textured part: its vertices (position, normal, uv, four bones and their weights), triangles, how far each vertex glows. */
    public static final class Group {
        public final String name;
        public final int vertices;
        public final float[] pos, normal, uv, weight, glow;
        public final byte[] joint;
        public final int[] tris;
        /** Triangles with some glow on them: the only ones drawn again in the glow pass. */
        public int[] glowTris;
        Identifier texture, glowTexture;

        Group(String name, int vertices, int tris) {
            this.name = name;
            this.vertices = vertices;
            pos = new float[vertices * 3];
            normal = new float[vertices * 3];
            uv = new float[vertices * 2];
            joint = new byte[vertices * 4];
            weight = new float[vertices * 4];
            glow = new float[vertices];
            this.tris = new int[tris * 3];
        }
    }

    /** An animation: per frame, per bone, its turn (x, y, z, w) and position in model space. */
    public record Clip(String name, int frames, float fps, float[] turn, float[] at) {
        public float seconds() {
            return (frames - 1) / fps;
        }
    }

    public final Group[] groups;
    public final String[] boneNames;
    public final int[] parent;
    /** Each bone's inverse bind: model space at rest into the bone's own. */
    public final Matrix4f[] inverse;
    public final Map<String, Clip> clips = new HashMap<>();
    private final Map<String, byte[]> jpegs = new HashMap<>();

    private static SpiderModel model;
    private static boolean failed;

    /** The model, loading it the first time; null if it can't be. Render thread only. */
    public static SpiderModel get() {
        if (model == null && !failed) {
            try (InputStream in = SpiderModel.class.getResourceAsStream("/assets/zomboidcraft/creatures/spider.zsp")) {
                if (in == null) {
                    throw new IOException("missing");
                }
                SpiderModel m = new SpiderModel(in.readAllBytes());
                m.textures();
                model = m;
            } catch (Exception e) {
                ZomboidCraft.LOGGER.error("Couldn't load the Harvestman's body (spider.zsp)", e);
                failed = true;
            }
        }
        return model;
    }

    public int bones() {
        return boneNames.length;
    }

    public int bone(String name) {
        for (int i = 0; i < boneNames.length; i++) {
            if (boneNames[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private SpiderModel(byte[] data) throws IOException {
        unscramble(data, "spider");
        try (DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data)))) {
            byte[] magic = in.readNBytes(4);
            if (magic[0] != 'Z' || magic[1] != 'S' || magic[2] != 'P' || magic[3] != 'D' || in.readInt() != 1) {
                throw new IOException("not a spider file");
            }
            groups = new Group[in.readInt()];
            for (int g = 0; g < groups.length; g++) {
                String name = in.readUTF();
                int verts = in.readInt();
                float[] pos = new float[verts * 3], normal = new float[verts * 3], uv = new float[verts * 2], weight = new float[verts * 4];
                byte[] joint = new byte[verts * 4];
                for (int v = 0; v < verts; v++) {
                    pos[v * 3] = in.readFloat();
                    pos[v * 3 + 1] = in.readFloat();
                    pos[v * 3 + 2] = in.readFloat();
                    float nx = in.readByte(), ny = in.readByte(), nz = in.readByte();
                    float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (len < 1e-3f) {
                        ny = len = 1;
                    }
                    normal[v * 3] = nx / len;
                    normal[v * 3 + 1] = ny / len;
                    normal[v * 3 + 2] = nz / len;
                    uv[v * 2] = in.readChar() / 65535f;
                    uv[v * 2 + 1] = in.readChar() / 65535f;
                    for (int k = 0; k < 4; k++) {
                        joint[v * 4 + k] = in.readByte();
                    }
                    for (int k = 0; k < 4; k++) {
                        weight[v * 4 + k] = (in.readByte() & 255) / 255f;
                    }
                }
                int tris = in.readInt();
                Group gr = new Group(name, verts, tris);
                System.arraycopy(pos, 0, gr.pos, 0, pos.length);
                System.arraycopy(normal, 0, gr.normal, 0, normal.length);
                System.arraycopy(uv, 0, gr.uv, 0, uv.length);
                System.arraycopy(joint, 0, gr.joint, 0, joint.length);
                System.arraycopy(weight, 0, gr.weight, 0, weight.length);
                for (int i = 0; i < gr.tris.length; i++) {
                    gr.tris[i] = in.readInt();
                }
                groups[g] = gr;
            }
            int looks = in.readInt();
            for (int i = 0; i < looks; i++) {
                String name = in.readUTF();
                jpegs.put(name, in.readNBytes(in.readInt()));
            }
            int count = in.readInt();
            boneNames = new String[count];
            parent = new int[count];
            inverse = new Matrix4f[count];
            for (int i = 0; i < count; i++) {
                boneNames[i] = in.readUTF();
                parent[i] = in.readByte();
                float[] m = new float[12];
                for (int k = 0; k < 12; k++) {
                    m[k] = in.readFloat();
                }
                // rows of a 3x4 into JOML's column-major constructor
                inverse[i] = new Matrix4f(m[0], m[4], m[8], 0, m[1], m[5], m[9], 0, m[2], m[6], m[10], 0, m[3], m[7], m[11], 1);
            }
            int clipCount = in.readInt();
            for (int c = 0; c < clipCount; c++) {
                String name = in.readUTF();
                int frames = in.readInt();
                float fps = in.readFloat();
                float[] turn = new float[frames * count * 4], at = new float[frames * count * 3];
                for (int f = 0; f < frames; f++) {
                    for (int b = 0; b < count; b++) {
                        int q = (f * count + b) * 4, p = (f * count + b) * 3;
                        float x = in.readShort() / 32767f, y = in.readShort() / 32767f, z = in.readShort() / 32767f, w = in.readShort() / 32767f;
                        float len = (float) Math.sqrt(x * x + y * y + z * z + w * w);
                        turn[q] = x / len;
                        turn[q + 1] = y / len;
                        turn[q + 2] = z / len;
                        turn[q + 3] = w / len;
                        at[p] = in.readShort() / 1000f;
                        at[p + 1] = in.readShort() / 1000f;
                        at[p + 2] = in.readShort() / 1000f;
                    }
                }
                clips.put(name, new Clip(name, frames, fps, turn, at));
            }
        }
    }

    /** Uploads each part's texture and glow map, and reads how far each vertex glows (so a hidden one can darken it). */
    private void textures() throws IOException {
        for (Group g : groups) {
            g.texture = upload(g.name, jpegs.get(g.name));
            byte[] glowJpeg = jpegs.get(g.name + "_glow");
            if (glowJpeg == null) {
                continue;
            }
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(glowJpeg));
            int w = img.getWidth(), h = img.getHeight();
            for (int v = 0; v < g.vertices; v++) {
                int x = Math.min(w - 1, (int) (g.uv[v * 2] * w)), y = Math.min(h - 1, (int) (g.uv[v * 2 + 1] * h));
                int rgb = img.getRGB(x, y);
                g.glow[v] = Math.max((rgb >> 16) & 255, Math.max((rgb >> 8) & 255, rgb & 255)) / 255f;
            }
            int n = 0;
            int[] lit = new int[g.tris.length];
            for (int t = 0; t < g.tris.length; t += 3) {
                if (g.glow[g.tris[t]] + g.glow[g.tris[t + 1]] + g.glow[g.tris[t + 2]] > 0.12f) {
                    lit[n++] = g.tris[t];
                    lit[n++] = g.tris[t + 1];
                    lit[n++] = g.tris[t + 2];
                }
            }
            g.glowTris = java.util.Arrays.copyOf(lit, n);
            g.glowTexture = upload(g.name + "_glow", glowJpeg);
        }
        jpegs.clear();
    }

    private static Identifier upload(String name, byte[] jpeg) throws IOException {
        if (jpeg == null) {
            throw new IOException("no texture " + name);
        }
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpeg));
        if (img == null) {
            throw new IOException("unreadable texture " + name);
        }
        NativeImage out = new NativeImage(img.getWidth(), img.getHeight(), false);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                out.setPixel(x, y, 0xFF000000 | img.getRGB(x, y));
            }
        }
        Identifier id = ZomboidCraft.id("dynamic/spider/" + name);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "ZomboidCraft spider " + name, out));
        return id;
    }

    /** Undoes pack.py's xorshift32 scramble (seeded from the model id). */
    private static void unscramble(byte[] data, String model) {
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
}
