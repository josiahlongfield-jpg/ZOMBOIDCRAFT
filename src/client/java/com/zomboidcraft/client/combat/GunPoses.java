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
    record Gun(Posed hip, Posed ads, @Nullable Posed tpReady, @Nullable Posed tpAim) {
    }

    /**
     * A third-person pose: the gun (item pixels / 16 to her Bip01_Spine1's frame) and her arm bones by their full
     * names (Bip01_R_Hand, and Bip01_R_Finger1_index etc. for the split fingers), each in Spine1's frame, in her
     * pack's space (metres, facing +z).
     */
    public record Third(Matrix4f gun, Map<String, Matrix4f> bones) {
    }

    /** The third-person pose Josiah set for this local gun, aimed or ready; null for a gun without one. */
    public static @Nullable Third third(@Nullable String id, boolean aim) {
        Gun g = of(id);
        Posed p = g == null ? null : aim ? g.tpAim() : g.tpReady();
        return p == null ? null : new Third(matrix(p.gun()), p.bones());
    }

    private static final Path DIR = LocalGuns.DIR.resolve("poses");
    private static final Map<String, Gun> CACHE = new HashMap<>();
    private static final Gun NONE = new Gun(null, null, null, null);
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
                    : new Gun(hip, ads, posed(o.getAsJsonObject("tpready")), posed(o.getAsJsonObject("tpaim")));
        } catch (RuntimeException | java.io.IOException e) {
            ZomboidCraft.LOGGER.error("Couldn't read gun poses {}", f, e);
            return NONE;
        }
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
