package com.zomboidcraft.client.npc;

import com.mojang.blaze3d.platform.NativeImage;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.npc.Npcs;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.InflaterInputStream;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A skinned character (tools/npc/skinned/pack.py): her meshes in pieces she can put on and take off, two levels of detail,
 * her own skeleton and its clips, and the jaw and eyelids put back from her face rig. Read from config/zomboidcraft/npc/ID.zsk
 * on this PC (characters someone else owns never ship in the jar), else assets/zomboidcraft/npc/ID.zsk. The file is big,
 * so it's read on a worker thread the first time she's drawn; she isn't drawn until it's in.
 * <p>
 * Model space: metres, feet on y=0, facing +z, +x her left.
 */
public final class NpcSkin {
    /** One mesh: its piece ("body" always shows), texture, both sides or not, cut-out alpha, level (0 near, 1 far). */
    public static final class Group {
        public final String mesh, piece, texture;
        public final boolean bothSides, cutout;
        public final int level, vertices;
        public final float[] pos, normal, uv, weight;
        public final byte[] joint;
        public int[] tris;

        Group(String mesh, String piece, String texture, int flags, int level, int vertices) {
            this.mesh = mesh;
            this.piece = piece;
            this.texture = texture;
            this.bothSides = (flags & 1) != 0;
            this.cutout = (flags & 2) != 0;
            this.level = level;
            this.vertices = vertices;
            pos = new float[vertices * 3];
            normal = new float[vertices * 3];
            uv = new float[vertices * 2];
            joint = new byte[vertices * 4];
            weight = new float[vertices * 4];
        }
    }

    /** A clip: per frame, per bone (the first `bones`), its turn (x, y, z, w) and position in model space. */
    public record Clip(String name, int frames, float fps, int bones, float[] turn, float[] at) {
        public float seconds() {
            return (frames - 1) / fps;
        }
    }

    public final Group[] groups;
    public final String[] boneNames;
    public final int[] parent;
    /** Model space at rest into each bone's own. */
    public final Matrix4f[] inverse;
    /** The face bones: jaw, lid_l, lid_r (index, or -1) and the points they turn about. */
    public final int jaw, lidL, lidR, head;
    public final Vector3f jawPivot = new Vector3f(), lidLPivot = new Vector3f(), lidRPivot = new Vector3f();
    public final Map<String, Clip> clips = new HashMap<>();
    /** How far the heels lift her (metres): she's dropped this much with them off. */
    public final float heel;
    private final Map<String, byte[]> images = new HashMap<>();
    private final Map<String, Identifier> textures = new HashMap<>();

    private static final Map<String, CompletableFuture<NpcSkin>> LOADING = new HashMap<>();

    /** Whether any of her meshes belongs to this piece (older models have fewer clothes). */
    public boolean has(String piece) {
        for (Group g : groups) {
            if (g.piece.equals(piece)) return true;
        }
        return false;
    }

    /** The skinned model with this id, or null while it loads or if there isn't one. Render thread. */
    public static NpcSkin get(String model) {
        CompletableFuture<NpcSkin> f = LOADING.computeIfAbsent(model, m -> {
            Path local = Npcs.localFolder().resolve(m + ".zsk");
            boolean inJar = NpcSkin.class.getResource("/assets/zomboidcraft/npc/" + m + ".zsk") != null;
            if (!Files.isRegularFile(local) && !inJar) {
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.supplyAsync(() -> {
                try {
                    byte[] data;
                    if (Files.isRegularFile(local)) {
                        data = Files.readAllBytes(local);
                    } else {
                        try (InputStream in = NpcSkin.class.getResourceAsStream("/assets/zomboidcraft/npc/" + m + ".zsk")) {
                            data = in.readAllBytes();
                        }
                    }
                    return new NpcSkin(data, m);
                } catch (Exception e) {
                    ZomboidCraft.LOGGER.error("Couldn't load skinned NPC model {}", m, e);
                    return null;
                }
            });
        });
        return f.isDone() ? f.getNow(null) : null;
    }

    /** Whether there's a skinned model by this name (it may still be loading). */
    public static boolean exists(String model) {
        get(model);
        CompletableFuture<NpcSkin> f = LOADING.get(model);
        return f != null && (!f.isDone() || f.getNow(null) != null);
    }

    public int bone(String name) {
        for (int i = 0; i < boneNames.length; i++) {
            if (boneNames[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    /** The texture by its key, made into a GPU texture the first time it's asked for. Render thread. */
    public Identifier texture(String key, String model) {
        Identifier id = textures.get(key);
        if (id == null) {
            byte[] bytes = images.remove(key);
            if (bytes == null) {
                return null;
            }
            id = ZomboidCraft.id("dynamic/npc/" + model + "/" + key.toLowerCase(java.util.Locale.ROOT));
            try {
                Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "ZomboidCraft NPC " + model + " " + key, decode(bytes)));
            } catch (IOException e) {
                ZomboidCraft.LOGGER.error("Bad texture {} in NPC model {}", key, model, e);
                return null;
            }
            textures.put(key, id);
        }
        return id;
    }

    private NpcSkin(byte[] data, String model) throws IOException {
        NpcAssets.unscramble(data, model);
        try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(new InflaterInputStream(new ByteArrayInputStream(data)), 1 << 16))) {
            byte[] magic = in.readNBytes(4);
            if (magic[0] != 'Z' || magic[1] != 'S' || magic[2] != 'K' || magic[3] != 'N' || in.readInt() != 1) {
                throw new IOException("not a skinned NPC file");
            }
            groups = new Group[in.readInt()];
            for (int g = 0; g < groups.length; g++) {
                String mesh = in.readUTF(), piece = in.readUTF(), texture = in.readUTF();
                int flags = in.readByte(), level = in.readByte(), verts = in.readInt();
                Group gr = new Group(mesh, piece, texture, flags, level, verts);
                for (int v = 0; v < verts; v++) {
                    gr.pos[v * 3] = in.readFloat();
                    gr.pos[v * 3 + 1] = in.readFloat();
                    gr.pos[v * 3 + 2] = in.readFloat();
                    float nx = in.readByte(), ny = in.readByte(), nz = in.readByte();
                    float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (len < 1e-3f) {
                        ny = len = 1;
                    }
                    gr.normal[v * 3] = nx / len;
                    gr.normal[v * 3 + 1] = ny / len;
                    gr.normal[v * 3 + 2] = nz / len;
                    gr.uv[v * 2] = in.readChar() / 65535f;
                    gr.uv[v * 2 + 1] = in.readChar() / 65535f;
                    for (int k = 0; k < 4; k++) {
                        gr.joint[v * 4 + k] = in.readByte();
                    }
                    for (int k = 0; k < 4; k++) {
                        gr.weight[v * 4 + k] = (in.readByte() & 255) / 255f;
                    }
                }
                gr.tris = new int[in.readInt() * 3];
                for (int i = 0; i < gr.tris.length; i++) {
                    gr.tris[i] = in.readInt();
                }
                groups[g] = gr;
            }
            int texCount = in.readInt();
            for (int i = 0; i < texCount; i++) {
                String key = in.readUTF();
                images.put(key, in.readNBytes(in.readInt()));
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
            int jaw = -1, lidL = -1, lidR = -1;
            int faces = in.readInt();
            for (int i = 0; i < faces; i++) {
                String name = in.readUTF();
                int idx = in.readByte();
                Vector3f p = new Vector3f(in.readFloat(), in.readFloat(), in.readFloat());
                switch (name) {
                    case "jaw" -> { jaw = idx; jawPivot.set(p); }
                    case "lid_l" -> { lidL = idx; lidLPivot.set(p); }
                    case "lid_r" -> { lidR = idx; lidRPivot.set(p); }
                    default -> { }
                }
            }
            this.jaw = jaw;
            this.lidL = lidL;
            this.lidR = lidR;
            int clipCount = in.readInt();
            for (int c = 0; c < clipCount; c++) {
                String name = in.readUTF();
                int frames = in.readInt();
                float fps = in.readFloat();
                int bones = in.readInt();
                float[] turn = new float[frames * bones * 4], at = new float[frames * bones * 3];
                for (int i = 0; i < frames * bones; i++) {
                    float x = in.readShort() / 32767f, y = in.readShort() / 32767f, z = in.readShort() / 32767f, w = in.readShort() / 32767f;
                    float len = (float) Math.sqrt(x * x + y * y + z * z + w * w);
                    turn[i * 4] = x / len;
                    turn[i * 4 + 1] = y / len;
                    turn[i * 4 + 2] = z / len;
                    turn[i * 4 + 3] = w / len;
                    at[i * 3] = in.readShort() / 1000f;
                    at[i * 3 + 1] = in.readShort() / 1000f;
                    at[i * 3 + 2] = in.readShort() / 1000f;
                }
                clips.put(name, new Clip(name, frames, fps, bones, turn, at));
            }
        }
        head = bone("mixamorigHead");
        // the heels' height: how far the body's lowest point sits above the shoes' lowest
        float body = Float.MAX_VALUE, shoes = Float.MAX_VALUE;
        for (Group g : groups) {
            if (g.level != 0 || !(g.piece.equals("body") || g.piece.equals("heels"))) {
                continue;
            }
            float low = Float.MAX_VALUE;
            for (int v = 0; v < g.vertices; v++) {
                low = Math.min(low, g.pos[v * 3 + 1]);
            }
            if (g.piece.equals("body") && g.mesh.equals("body")) {
                body = low;
            } else if (g.piece.equals("heels")) {
                shoes = Math.min(shoes, low);
            }
        }
        heel = body < Float.MAX_VALUE && shoes < Float.MAX_VALUE ? Math.max(0, body - shoes) : 0;
    }

    /** PNG straight in (it keeps its alpha); JPEG through Java's reader. */
    private static NativeImage decode(byte[] bytes) throws IOException {
        if (bytes.length > 4 && (bytes[0] & 255) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return NativeImage.read(new ByteArrayInputStream(bytes));
        }
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
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
}
