package com.zomboidcraft.client.operator;

import com.google.gson.Gson;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.LocalGuns;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The body's own motion in first person while moving, as Tarkov's player model has it (Josiah 2026-11-07: no sway at
 * all looked wrong; "whatever Tarkov has their player models do"). Taken from Tarkov's walk, run and sprint body clips
 * (config/zomboidcraft/guns/tarkov/motion.json, made on the cloud side from the game files, kept on his PC only): the
 * head's bob is the camera's, and how the rifle moves against the head is the gun's in the hands, the sprint carrying it
 * lowered. Each clip runs at the pace its own feet set (its speed: Tarkov's walk 2.5 m/s, run 3.36, sprint 6.2), so the
 * steps keep time with the ground going by. On the sights it calms by Tarkov's aimed intensity.
 */
public final class TarkovMotion {
    private TarkovMotion() {
    }

    public static final Path FILE = LocalGuns.DIR.resolve("tarkov").resolve("motion.json");

    static final class Data {
        float aimScale = 0.75f;
        Map<String, Clip> clips;
    }

    static final class Clip {
        float speed, fps;
        Frame[] frames;
    }

    static final class Frame {
        float[] cam, gun;
    }

    private static final Gson GSON = new Gson();
    private static @Nullable Data data;
    private static int loadedFor = -1;
    private static final String[] CLIPS = {"walk", "run", "sprint"};

    private static @Nullable Data data() {
        if (loadedFor != LocalGuns.generation()) {
            loadedFor = LocalGuns.generation();
            data = null;
            if (Files.isRegularFile(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                    Data d = GSON.fromJson(r, Data.class);
                    if (d != null && d.clips != null && d.clips.containsKey("walk")) data = d;
                } catch (RuntimeException | java.io.IOException e) {
                    ZomboidCraft.LOGGER.error("Couldn't read Tarkov motion {}", FILE, e);
                }
            }
        }
        return data;
    }

    /** Blocks a second. */
    private static float speed(Clip c) {
        return c.speed;
    }

    private static float phase;
    private static float sprint, amp, lastTime = Float.NaN;
    private static double lastX = Double.NaN, lastZ;
    private static float pace;
    private static final Vector3f CAM_T = new Vector3f();
    private static final Quaternionf CAM_Q = new Quaternionf();
    private static final Vector3f GUN_T = new Vector3f();
    private static final Quaternionf GUN_Q = new Quaternionf();
    private static final Matrix4f GUN = new Matrix4f();

    /**
     * Steps the motion on to this frame (once a frame, from the camera's setup). Off unless the operator's first person
     * is drawn (a Tarkov gun, blade or grenade in hand) and the game is in first person.
     */
    public static void update(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Data d = data();
        CAM_T.zero();
        CAM_Q.identity();
        GUN_T.zero();
        GUN_Q.identity();
        GUN.identity();
        if (d == null || player == null || !mc.options.getCameraType().isFirstPerson() || !OperatorView.steady()) {
            lastTime = Float.NaN;
            amp = 0;
            return;
        }
        float time = (player.tickCount + partialTick) / 20f;
        float dt = Float.isNaN(lastTime) ? 0 : Math.max(0, Math.min(0.1f, time - lastTime));
        lastTime = time;
        double x = player.xo + (player.getX() - player.xo) * partialTick, z = player.zo + (player.getZ() - player.zo) * partialTick;
        if (!Double.isNaN(lastX) && dt > 0) {
            float now = (float) (Math.sqrt((x - lastX) * (x - lastX) + (z - lastZ) * (z - lastZ)) / dt);
            pace += (now - pace) * Math.min(1, dt * 8);
        }
        lastX = x;
        lastZ = z;
        boolean grounded = player.onGround() && !player.isPassenger() && !player.getAbilities().flying;
        Clip walk = d.clips.get("walk"), run = d.clips.get("run"), spr = d.clips.get("sprint");
        if (run == null) run = walk;
        if (spr == null) spr = run;
        float want = grounded ? Math.min(1, pace / (speed(walk) * 0.5f)) : 0;
        amp += (want - amp) * Math.min(1, dt * 6);
        sprint += ((player.isSprinting() && grounded ? 1 : 0) - sprint) * Math.min(1, dt * 5);
        // between Tarkov's walk and run by pace, the sprint over both while sprinting
        float t = Math.max(0, Math.min(1, (pace - speed(walk)) / Math.max(0.01f, speed(run) - speed(walk))));
        float[] w = {(1 - t) * (1 - sprint), t * (1 - sprint), sprint};
        Clip[] cs = {walk, run, spr};
        // one stride's worth of ground for the blend: the phase goes on by the ground covered
        float stride = 0;
        for (int i = 0; i < 3; i++) stride += w[i] * speed(cs[i]) * (cs[i].frames.length / Math.max(1, cs[i].fps));
        if (stride > 0) phase = (phase + pace * dt / stride) % 1f;

        float aim = OperatorView.aimShown();
        float camScale = amp * (1 - aim * (1 - d.aimScale));
        // on the sights the rifle keeps much closer to the eye than the head bobs; a magnified scope's picture is
        // drawn in the middle of the screen, so the rifle holds still on it
        float gunScale = amp * (1 - aim * 0.75f);
        if (aim > 0.5f && com.zomboidcraft.client.combat.ScopeView.active()) gunScale = 0;
        Vector3f gt = new Vector3f();
        Quaternionf cq = new Quaternionf(0, 0, 0, 0), gq = new Quaternionf(0, 0, 0, 0);
        for (int i = 0; i < 3; i++) {
            if (w[i] <= 0) continue;
            Clip c = cs[i];
            float f = phase * c.frames.length;
            int a = (int) Math.floor(f) % c.frames.length, b = (a + 1) % c.frames.length;
            float k = f - (float) Math.floor(f);
            add(c.frames[a].cam, c.frames[b].cam, k, w[i], CAM_T, cq);
            add(c.frames[a].gun, c.frames[b].gun, k, w[i], gt, gq);
        }
        CAM_T.mul(camScale);
        CAM_Q.set(new Quaternionf().slerp(norm(cq), camScale));
        GUN_T.set(gt).mul(gunScale);
        GUN_Q.set(new Quaternionf().slerp(norm(gq), gunScale));
    }

    private static Quaternionf norm(Quaternionf q) {
        return q.lengthSquared() < 1e-8f ? new Quaternionf() : q.normalize();
    }

    /** Adds one clip's frame (between two, k of the way) with its weight. */
    private static void add(float[] a, float[] b, float k, float w, Vector3f t, Quaternionf q) {
        t.add((a[0] + (b[0] - a[0]) * k) * w, (a[1] + (b[1] - a[1]) * k) * w, (a[2] + (b[2] - a[2]) * k) * w);
        Quaternionf qa = new Quaternionf(a[3], a[4], a[5], a[6]), qb = new Quaternionf(b[3], b[4], b[5], b[6]);
        Quaternionf s = qa.slerp(qb, k, new Quaternionf());
        if (s.w < 0) s.set(-s.x, -s.y, -s.z, -s.w);
        q.set(q.x + s.x * w, q.y + s.y * w, q.z + s.z * w, q.w + s.w * w);
    }

    /** The camera's bob this frame: where the eye is off its rest (camera axes) and how it's turned. */
    public static Vector3f cameraShift() {
        return CAM_T;
    }

    public static Quaternionf cameraTurn() {
        return CAM_Q;
    }

    /**
     * How the rifle (arms and all) moves against the camera this frame: Tarkov's clips move the rifle's own root (its
     * displacement and its turn about itself), so the turn goes about where the rifle's root is drawn now (camera space,
     * as drawn). Kept for {@link #gun()} until the next call.
     */
    public static Matrix4f gun(Vector3f pivot) {
        return GUN.translation(pivot.x + GUN_T.x, pivot.y + GUN_T.y, pivot.z + GUN_T.z).rotate(GUN_Q).translate(-pivot.x, -pivot.y, -pivot.z);
    }

    /** The rifle's motion as last worked out by {@link #gun(Vector3f)} (for the lights and lenses drawn with it). */
    public static Matrix4f gun() {
        return GUN;
    }
}
