package com.zomboidcraft.creature.spider;

import com.zomboidcraft.ZomboidCraft;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.InflaterInputStream;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Harvestman's body (the bought "Spider 7" creature), from assets/zomboidcraft/creatures/spider.zsp, packed by
 * tools/creatures/spider/pack.py: its skinned body and skull, their textures and glow maps (kept as JPEG for the client),
 * its 49 bones and the model's own 30 animations. Model space is metres (one block each), +y up, +z toward its head, +x
 * its left, its feet on y=0 and its pelvis over the origin. Server and client both load it: the server poses it too, so
 * shots strike it where it's drawn (SpiderHits). Null if the file is missing or bad.
 *
 * <p>Its limbs that can be shot away, by number: 0-3 its walking legs (HarvestmanBody's numbering: 0 left front, 1
 * left rear, 2 right front, 3 right rear; A is the front pair, L its left), cut at the knee; 4-5 its arms (left, right),
 * cut at the elbow; 6-7 its long scythe claws and 8-9 its short ones (left, right), cut past their first joint.
 */
public final class SpiderModel {
    /** One textured part: its vertices (position, normal, uv, four bones and their weights) and triangles. */
    public static final class Group {
        public final String name;
        public final int vertices;
        public final float[] pos, normal, uv, weight;
        public final byte[] joint;
        public final int[] tris;

        Group(String name, int vertices, int tris) {
            this.name = name;
            this.vertices = vertices;
            pos = new float[vertices * 3];
            normal = new float[vertices * 3];
            uv = new float[vertices * 2];
            joint = new byte[vertices * 4];
            weight = new float[vertices * 4];
            this.tris = new int[tris * 3];
        }
    }

    /** An animation: per frame, per bone, its turn (x, y, z, w) and position in model space. */
    public record Clip(String name, int frames, float fps, float[] turn, float[] at) {
        public float seconds() {
            return (frames - 1) / fps;
        }
    }

    /** A tube round one bone at rest (model space), and what a shot there strikes: a zone and limb (or -1). */
    public record Capsule(int bone, Vector3f a, Vector3f b, float radius, int zone, int limb) {
    }

    public static final int LIMBS = 10;
    /** Hit zones (the same numbers as HarvestmanBody's, plus the smaller limbs). */
    public static final int HULL = 0, CORE = 1, LEG = 2, EYES = 3, LIMB = 4;

    public final Group[] groups;
    public final String[] boneNames;
    public final int[] parent;
    /** Each bone's inverse bind: model space at rest into the bone's own. */
    public final Matrix4f[] inverse;
    public final Map<String, Clip> clips = new HashMap<>();
    /** The textures as packed (body, skull, and their glow maps), for the client. */
    public final Map<String, byte[]> jpegs = new HashMap<>();
    /** Each bone at rest: where it is and how it's turned, in model space. */
    public final Vector3f[] bindPos;
    public final Quaternionf[] bindTurn;
    /** Bone indices under each bone (not itself), for turning a limb or the body from a joint. */
    public final int[][] below;
    /** The four walking legs: each one's hip, knee and ankle bones, and its point in the ankle bone's own frame. */
    public final int[][] legs = new int[4][3];
    public final Vector3f[] tip = new Vector3f[4];
    /** Per limb: the bone it's cut at, every bone that goes with it, and the triangles (by group) that fly off. */
    public final int[] cutAt = new int[LIMBS];
    public final int[][] cutBones = new int[LIMBS][];
    public final int[][][] cutTris = new int[LIMBS][][];
    /** What each bone is (for the hit test): its limb, or -1. */
    public final int[] limbOf;
    public final Capsule[] capsules;
    public final int pelvis, spine, neck, head, tail;
    /** The bones of its arms and claws (held round what it drags). */
    public final boolean[] arm;

    private static SpiderModel model;
    private static boolean failed;

    /** The model, loading it the first time; null if it can't be. */
    public static synchronized SpiderModel get() {
        if (model == null && !failed) {
            try (InputStream in = SpiderModel.class.getResourceAsStream("/assets/zomboidcraft/creatures/spider.zsp")) {
                if (in == null) {
                    throw new IOException("missing");
                }
                model = new SpiderModel(in.readAllBytes());
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
                Group gr = new Group(name, verts, 0);
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
                int tris = in.readInt();
                Group full = new Group(name, verts, tris);
                System.arraycopy(gr.pos, 0, full.pos, 0, gr.pos.length);
                System.arraycopy(gr.normal, 0, full.normal, 0, gr.normal.length);
                System.arraycopy(gr.uv, 0, full.uv, 0, gr.uv.length);
                System.arraycopy(gr.joint, 0, full.joint, 0, gr.joint.length);
                System.arraycopy(gr.weight, 0, full.weight, 0, gr.weight.length);
                for (int i = 0; i < full.tris.length; i++) {
                    full.tris[i] = in.readInt();
                }
                groups[g] = full;
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
        int n = boneNames.length;
        bindPos = new Vector3f[n];
        bindTurn = new Quaternionf[n];
        for (int i = 0; i < n; i++) {
            Matrix4f rest = new Matrix4f(inverse[i]).invert();
            bindPos[i] = rest.getTranslation(new Vector3f());
            bindTurn[i] = rest.getNormalizedRotation(new Quaternionf());
        }
        below = new int[n][];
        for (int i = 0; i < n; i++) {
            List<Integer> list = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                for (int a = parent[j]; a >= 0; a = parent[a]) {
                    if (a == i) {
                        list.add(j);
                        break;
                    }
                }
            }
            below[i] = list.stream().mapToInt(Integer::intValue).toArray();
        }
        pelvis = bone("def_pelvis");
        spine = bone("def_spine_1");
        neck = bone("def_neck");
        head = bone("def_head");
        tail = bone("def_tail_1");
        arm = new boolean[n];
        for (int i = 0; i < n; i++) {
            String b = boneNames[i].toLowerCase(Locale.ROOT);
            arm[i] = b.contains("sholder") || b.contains("arm") || b.contains("hand") || b.contains("claw");
        }

        // the walking legs, and each one's point: the vertex furthest out of those its ankle carries
        String[] legNames = {"A_%dL", "B_%dL", "A_%dR", "B_%dR"};
        Group body = groups[0];
        for (int l = 0; l < 4; l++) {
            for (int k = 0; k < 3; k++) {
                legs[l][k] = bone("def_leg_" + String.format(legNames[l], k + 1));
                if (legs[l][k] < 0) {
                    throw new IOException("no leg bone " + legNames[l]);
                }
            }
            int ankle = legs[l][2];
            Vector3f best = new Vector3f(bindPos[ankle]);
            float far = -1;
            for (int v = 0; v < body.vertices; v++) {
                if (main(body, v) == ankle) {
                    float d = bindPos[ankle].distanceSquared(body.pos[v * 3], body.pos[v * 3 + 1], body.pos[v * 3 + 2]);
                    if (d > far) {
                        far = d;
                        best.set(body.pos[v * 3], body.pos[v * 3 + 1], body.pos[v * 3 + 2]);
                    }
                }
            }
            tip[l] = inverse[ankle].transformPosition(best, new Vector3f());
        }

        // the limbs that can be shot away: where each is cut, and its bones
        limbOf = new int[n];
        Arrays.fill(limbOf, -1);
        String[][] small = {{"def_sholderL", "def_forarmL"}, {"def_sholderR", "def_forarmR"}, {"def_clAw_A_1L", "def_clAw_A_2L"},
                {"def_clAw_A_1R", "def_clAw_A_2R"}, {"def_claw_B_1L", "def_claw_B_2L"}, {"def_claw_B_1R", "def_claw_B_2R"}};
        for (int c = 0; c < LIMBS; c++) {
            int root, cut;
            if (c < 4) {
                root = legs[c][0];
                cut = legs[c][1];
            } else {
                root = bone(small[c - 4][0]);
                cut = bone(small[c - 4][1]);
                if (root < 0 || cut < 0) {
                    throw new IOException("no limb bone " + small[c - 4][1]);
                }
            }
            limbOf[root] = c;
            for (int j : below[root]) {
                limbOf[j] = c;
            }
            cutAt[c] = cut;
            int[] gone = new int[below[cut].length + 1];
            gone[0] = cut;
            System.arraycopy(below[cut], 0, gone, 1, below[cut].length);
            cutBones[c] = gone;
            cutTris[c] = new int[groups.length][];
            for (int g = 0; g < groups.length; g++) {
                Group gr = groups[g];
                List<Integer> piece = new ArrayList<>();
                for (int t = 0; t < gr.tris.length; t += 3) {
                    boolean all = true;
                    for (int k = 0; k < 3 && all; k++) {
                        all = contains(gone, main(gr, gr.tris[t + k]));
                    }
                    if (all) {
                        piece.add(gr.tris[t]);
                        piece.add(gr.tris[t + 1]);
                        piece.add(gr.tris[t + 2]);
                    }
                }
                cutTris[c][g] = piece.stream().mapToInt(Integer::intValue).toArray();
            }
        }
        capsules = capsules();
    }

    /**
     * A tube round each bone, fitted to the mesh it carries: from the bone's joint to its furthest vertex, as thick as
     * most of those vertices sit off that line.
     */
    private Capsule[] capsules() {
        int n = boneNames.length;
        List<float[]>[] verts = new List[n];
        for (int i = 0; i < n; i++) {
            verts[i] = new ArrayList<>();
        }
        for (Group g : groups) {
            for (int v = 0; v < g.vertices; v++) {
                verts[main(g, v)].add(new float[] {g.pos[v * 3], g.pos[v * 3 + 1], g.pos[v * 3 + 2]});
            }
        }
        List<Capsule> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            List<float[]> vs = verts[i];
            if (vs.size() < 6) {
                continue;
            }
            Vector3f a = new Vector3f(bindPos[i]), b = new Vector3f(a), p = new Vector3f();
            float far = -1;
            for (float[] v : vs) {
                float d = a.distanceSquared(v[0], v[1], v[2]);
                if (d > far) {
                    far = d;
                    b.set(v[0], v[1], v[2]);
                }
            }
            float[] off = new float[vs.size()];
            Vector3f ab = new Vector3f(b).sub(a);
            float abab = Math.max(1e-6f, ab.lengthSquared());
            for (int k = 0; k < off.length; k++) {
                float[] v = vs.get(k);
                p.set(v[0], v[1], v[2]).sub(a);
                float s = Math.max(0, Math.min(1, p.dot(ab) / abab));
                off[k] = p.distance(ab.x * s, ab.y * s, ab.z * s);
            }
            Arrays.sort(off);
            // a shade over most of it: a shot that grazes the surface still counts
            float r = Math.max(0.05f, Math.min(0.5f, off[(int) (off.length * 0.8f)] + 0.03f));
            String name = boneNames[i].toLowerCase(Locale.ROOT);
            int limb = limbOf[i], zone;
            if (name.contains("head") || name.contains("jaw")) {
                zone = EYES;
            } else if (name.contains("tail")) {
                zone = CORE;
            } else if (limb >= 0) {
                zone = limb < 4 ? LEG : LIMB;
            } else {
                zone = HULL;
            }
            out.add(new Capsule(i, a, b, r, zone, limb));
        }
        return out.toArray(new Capsule[0]);
    }

    private static boolean contains(int[] a, int x) {
        for (int v : a) {
            if (v == x) {
                return true;
            }
        }
        return false;
    }

    /** The bone a vertex follows most. */
    public static int main(Group g, int v) {
        int best = 0;
        for (int k = 1; k < 4; k++) {
            if (g.weight[v * 4 + k] > g.weight[v * 4 + best]) {
                best = k;
            }
        }
        return g.joint[v * 4 + best] & 255;
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
