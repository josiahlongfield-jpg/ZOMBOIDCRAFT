package com.zomboidcraft.client.combat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.LocalGuns;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A local gun's first-person poses as Josiah set them in the posing tool (2026-11-03): from the hip and on its iron
 * sights, the gun and both arms bone by bone. Read from {@code config/zomboidcraft/guns/poses/<id>.json} (written on
 * this computer from the tool's numbers, never shipped), already in the hand frame: blocks, x right, y up, z back, the eye at
 * -ARM. A gun without a file keeps our anchor gun's pose and arms.
 */
public final class GunPoses {
    /** One pose: the gun, and each arm bone's place and turn (by its name without "Bip01_"). */
    record Posed(RifleView.Pose gun, Map<String, Matrix4f> bones) {
    }

    /** Both of a gun's first-person poses, and its third-person ones when the file has them (else null). */
    record Gun(Posed hip, Posed ads, @Nullable Posed tpReady, @Nullable Posed tpAim, Att att) {
    }

    /**
     * Where Josiah placed each sight and grip on this gun in the posing tool: the gun's model units per metre (a part's
     * metres times this), each sight's mount (item pixels) and its sights pose (the gun and both arms), each grip's
     * mount and the front hand on it in every pose (L_ bones; third person by full name).
     */
    record Att(float k, Map<String, OpticPose> optics, Map<String, GripPose> grips) {
    }

    record OpticPose(float[] mount, Posed ads) {
    }

    record GripPose(float[] mount, Map<String, Map<String, Matrix4f>> hands) {
    }

    private static final Att NO_ATT = new Att(0, Map.of(), Map.of());

    /**
     * A third-person pose: the gun (item pixels / 16 to her Bip01_Spine1's frame) and her arm bones by their full
     * names (Bip01_R_Hand, and Bip01_R_Finger1_index etc. for the split fingers), each in Spine1's frame, in her
     * pack's space (metres, facing +z).
     */
    public record Third(Matrix4f gun, Map<String, Matrix4f> bones) {
    }

    /** The third-person pose Josiah set for this local gun, aimed or ready; null for a gun without one. */
    public static @Nullable Third third(@Nullable String id, boolean aim, @Nullable String grip) {
        Gun g = of(id);
        if (g != null && grip != null) g = with(g, null, grip);
        Posed p = g == null ? null : aim ? g.tpAim() : g.tpReady();
        return p == null ? null : new Third(matrix(p.gun()), p.bones());
    }

    private static final Path DIR = LocalGuns.DIR.resolve("poses");
    private static final Map<String, Gun> CACHE = new HashMap<>();
    private static final Gun NONE = new Gun(null, null, null, null, NO_ATT);
    private static int generation = -1;

    static @Nullable Gun of(@Nullable String id) {
        if (id == null) {
            return null;
        }
        if (generation != LocalGuns.generation()) {
            generation = LocalGuns.generation();
            CACHE.clear();
        }
        Gun g = CACHE.computeIfAbsent(id, GunPoses::read);
        return g == NONE ? null : g;
    }

    private static Gun read(String id) {
        Path f = DIR.resolve(id + ".json");
        if (!Files.isRegularFile(f)) {
            return NONE;
        }
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            Posed hip = posed(o.getAsJsonObject("hip")), ads = posed(o.getAsJsonObject("ads"));
            return hip == null || ads == null ? NONE
                    : new Gun(hip, ads, posed(o.getAsJsonObject("tpready")), posed(o.getAsJsonObject("tpaim")), att(o));
        } catch (RuntimeException | java.io.IOException e) {
            ZomboidCraft.LOGGER.error("Couldn't read gun poses {}", f, e);
            return NONE;
        }
    }

    private static Att att(JsonObject o) {
        if (!o.has("k")) {
            return NO_ATT;
        }
        Map<String, OpticPose> optics = new HashMap<>();
        Map<String, GripPose> grips = new HashMap<>();
        JsonObject os = o.getAsJsonObject("optics"), gs = o.getAsJsonObject("grips");
        if (os != null) {
            for (Map.Entry<String, JsonElement> e : os.entrySet()) {
                JsonObject v = e.getValue().getAsJsonObject();
                Posed ads = posed(v.getAsJsonObject("ads"));
                if (ads != null) optics.put(e.getKey(), new OpticPose(floats(v.getAsJsonArray("mount")), ads));
            }
        }
        if (gs != null) {
            for (Map.Entry<String, JsonElement> e : gs.entrySet()) {
                JsonObject v = e.getValue().getAsJsonObject();
                Map<String, Map<String, Matrix4f>> hands = new HashMap<>();
                for (String m : new String[]{"hip", "ads", "tpready", "tpaim"}) {
                    JsonObject h = v.getAsJsonObject(m);
                    if (h != null) hands.put(m, bones(h));
                }
                grips.put(e.getKey(), new GripPose(floats(v.getAsJsonArray("mount")), hands));
            }
        }
        return new Att(o.get("k").getAsFloat(), optics, grips);
    }

    private static Map<String, Matrix4f> bones(JsonObject b) {
        Map<String, Matrix4f> bones = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : b.entrySet()) {
            float[] v = floats(e.getValue().getAsJsonArray());
            bones.put(e.getKey(), new Matrix4f().translationRotate(v[0], v[1], v[2], new Quaternionf(v[3], v[4], v[5], v[6]).normalize()));
        }
        return bones;
    }

    /**
     * The gun's poses with a sight and a grip of the posing tool's on it (either may be null): the sights pose the
     * sight gives (its own line, both arms carried with the gun), and the front hand on the grip, on the sights carried
     * along with the gun from where the grip alone put it. Null when this gun has no pose for that sight.
     */
    static @Nullable Gun with(Gun g, @Nullable String optic, @Nullable String grip) {
        if (optic == null && grip == null) {
            return g;
        }
        OpticPose op = optic == null ? null : g.att().optics().get(optic);
        if (optic != null && op == null) {
            return null;
        }
        GripPose gp = grip == null ? null : g.att().grips().get(grip);
        Posed ads = op == null ? g.ads() : op.ads();
        Posed hip = g.hip();
        Posed tpr = g.tpReady(), tpa = g.tpAim();
        if (gp != null) {
            hip = hand(hip, gp.hands().get("hip"), null);
            // the grip's front hand was set on the gun's iron-sights pose: carried along to the sight's
            Matrix4f carry = matrix(ads.gun()).mul(matrix(g.ads().gun()).invert());
            ads = hand(ads, gp.hands().get("ads"), carry);
            tpr = tpr == null ? null : hand(tpr, gp.hands().get("tpready"), null);
            tpa = tpa == null ? null : hand(tpa, gp.hands().get("tpaim"), null);
        }
        return new Gun(hip, ads, tpr, tpa, g.att());
    }

    private static Posed hand(Posed p, @Nullable Map<String, Matrix4f> left, @Nullable Matrix4f carry) {
        if (left == null) {
            return p;
        }
        Map<String, Matrix4f> bones = new HashMap<>(p.bones());
        for (Map.Entry<String, Matrix4f> e : left.entrySet()) {
            bones.put(e.getKey(), carry == null ? e.getValue() : new Matrix4f(carry).mul(e.getValue()));
        }
        return new Posed(p.gun(), bones);
    }

    /** A local gun's sight and grip mounts and scale, or null for a gun without them. */
    public static @Nullable Att att(@Nullable String id) {
        Gun g = of(id);
        return g == null || g.att().k() <= 0 ? null : g.att();
    }

    private static @Nullable Posed posed(@Nullable JsonObject o) {
        if (o == null) {
            return null;
        }
        JsonObject g = o.getAsJsonObject("gun");
        float[] t = floats(g.getAsJsonArray("t")), q = floats(g.getAsJsonArray("q"));
        RifleView.Pose gun = new RifleView.Pose(new Vector3f(t[0], t[1], t[2]), new Quaternionf(q[0], q[1], q[2], q[3]).normalize(),
                g.get("scale").getAsFloat());
        Map<String, Matrix4f> bones = new HashMap<>();
        JsonObject b = o.getAsJsonObject("bones");
        if (b != null) {
            for (Map.Entry<String, JsonElement> e : b.entrySet()) {
                float[] v = floats(e.getValue().getAsJsonArray());
                bones.put(e.getKey(), new Matrix4f().translationRotate(v[0], v[1], v[2], new Quaternionf(v[3], v[4], v[5], v[6]).normalize()));
            }
        }
        return new Posed(gun, bones);
    }

    private static float[] floats(JsonArray a) {
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) {
            f[i] = a.get(i).getAsFloat();
        }
        return f;
    }

    /** A pose as a matrix: item pixels / 16 to the hand frame. */
    static Matrix4f matrix(RifleView.Pose p) {
        return new Matrix4f().translation(p.t()).rotate(p.q()).scale(p.scale()).translate(-0.5f, -0.5f, -0.5f);
    }

    /** Back from a matrix (a turn, a uniform scale and a move). */
    static RifleView.Pose pose(Matrix4f m) {
        Matrix4f x = new Matrix4f(m).translate(0.5f, 0.5f, 0.5f);
        float s = x.getScale(new Vector3f()).x;
        Quaternionf q = x.getUnnormalizedRotation(new Quaternionf()).normalize();
        return new RifleView.Pose(x.getTranslation(new Vector3f()), q, s);
    }

    /** The bones blended from the hip's to the sights' (a of the way), then carried along by move. */
    static Map<String, Matrix4f> bones(Gun g, float a, Matrix4f move) {
        Map<String, Matrix4f> out = new HashMap<>();
        Vector3f p0 = new Vector3f(), p1 = new Vector3f();
        Quaternionf q0 = new Quaternionf(), q1 = new Quaternionf();
        for (Map.Entry<String, Matrix4f> e : g.hip().bones().entrySet()) {
            Matrix4f h = e.getValue(), s = g.ads().bones().getOrDefault(e.getKey(), h);
            h.getTranslation(p0);
            s.getTranslation(p1);
            h.getUnnormalizedRotation(q0).normalize();
            s.getUnnormalizedRotation(q1).normalize();
            Matrix4f m = new Matrix4f().translationRotate(p0.lerp(p1, a, new Vector3f()), q0.slerp(q1, a, new Quaternionf()));
            out.put(e.getKey(), new Matrix4f(move).mul(m));
        }
        return out;
    }

    private GunPoses() {
    }
}
