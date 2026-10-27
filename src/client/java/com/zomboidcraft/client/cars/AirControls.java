package com.zomboidcraft.client.cars;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.cars.CarProp;
import com.zomboidcraft.world.cars.Flight;
import java.util.BitSet;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * An aircraft's stick and throttle (the helicopter's cyclic and collective) moving with the pilot's
 * controls, and the pilot's own hands on them in first person. Where the bought cockpit has a stick of its
 * own (the Su-27) its triangles turn about the stick's foot; elsewhere a stick and lever are drawn here.
 */
final class AirControls {
    /**
     * Where the controls are, in model space: the stick's foot and length, the throttle lever's pivot and
     * length (a collective lies forward from a pivot behind), and the bought stick to turn (foot, top, radius) or null.
     */
    record Rig(float[] stick, float stickLength, boolean side, float[] lever, float leverLength, boolean collective, float[] mesh) {
    }

    private static final Map<String, Rig> RIGS = Map.of(
            // F-22: a short side stick on the right console, the throttle on the left
            "jet_corvid", new Rig(new float[]{-0.31f, 2.40f, 5.64f}, 0.17f, true, new float[]{0.31f, 2.40f, 5.55f}, 0.16f, false, null),
            // Su-27: its own centre stick between the knees, the throttle on the left console
            "jet_gyrfalcon", new Rig(new float[]{-0.005f, 2.99f, 5.70f}, 0.27f, false, new float[]{0.41f, 2.98f, 5.52f}, 0.17f, false,
                    new float[]{-0.005f, 2.99f, 5.70f, 0.016f, 3.30f, 5.78f, 0.075f}),
            // AH-64 front seat (flown from the front here): the cyclic between the knees, the collective at the left hand
            "heli_hornbill", new Rig(new float[]{0f, 1.62f, 4.78f}, 0.34f, false, new float[]{0.33f, 1.76f, 4.28f}, 0.36f, true, null));

    /** How far the stick leans at full deflection, and the throttle's travel either side of the middle. */
    private static final float STICK = (float) Math.toRadians(14), LEVER = (float) Math.toRadians(28);

    private static CarProp flown;
    private static float pitchIn, rollIn, yawIn;
    /** The stick's lean as drawn, eased toward the controls each frame. */
    private static float pitch, roll;
    private static long last;

    private static final Map<CarAssets.Mesh, BitSet> FOUND = Collections.synchronizedMap(new WeakHashMap<>());
    private static final BitSet NONE = new BitSet();
    private static Identifier swatch;

    private AirControls() {
    }

    /** The controls the local pilot just gave (each tick, from {@link FlightClient}). */
    static void input(CarProp car, Flight.Controls c) {
        flown = car;
        boolean heli = car.flight().kind == Flight.Kind.HELI;
        pitchIn = heli ? -c.cyclicFwd() : c.pitch();
        rollIn = heli ? c.cyclicSide() : c.roll();
        yawIn = heli ? 0 : c.yaw();
    }

    /** The local pilot's pitch, roll and yaw demands for the aircraft he's flying, or null for any other. */
    static float[] command(CarProp car) {
        var player = Minecraft.getInstance().player;
        if (car != flown || player == null || car.seatOf(player) != 0) {
            return null;
        }
        return new float[]{pitchIn, rollIn, yawIn};
    }

    static Rig rig(String model) {
        return RIGS.get(model);
    }

    /** The stick's turn about its foot: pulled back toward the pilot, leant toward the roll. */
    private static Quaternionf lean(CarProp car) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        boolean mine = car == flown && Minecraft.getInstance().player != null && car.seatOf(Minecraft.getInstance().player) == 0;
        float k = 1 - (float) Math.exp(-dt * 14);
        pitch += ((mine ? pitchIn : 0) - pitch) * k;
        roll += ((mine ? rollIn : 0) - roll) * k;
        return new Quaternionf().rotationX(-pitch * STICK).rotateZ(roll * STICK);
    }

    /** The bought stick's triangles in a body mesh, or null where the model has none. */
    static BitSet meshStick(CarAssets.Mesh mesh, String model) {
        Rig rig = RIGS.get(model);
        if (rig == null || rig.mesh == null) {
            return null;
        }
        BitSet found = FOUND.computeIfAbsent(mesh, k -> find(k, rig.mesh));
        return found.isEmpty() ? null : found;
    }

    private static BitSet find(CarAssets.Mesh mesh, float[] s) {
        float ax = s[3] - s[0], ay = s[4] - s[1], az = s[5] - s[2];
        float len2 = ax * ax + ay * ay + az * az;
        float[] p = mesh.pos();
        BitSet out = new BitSet();
        for (int t = 0; t < mesh.opaque(); t++) {
            boolean in = true;
            for (int c = 0; c < 3 && in; c++) {
                int i = (t * 3 + c) * 3;
                float dx = p[i] - s[0], dy = p[i + 1] - s[1], dz = p[i + 2] - s[2];
                float along = (dx * ax + dy * ay + dz * az) / len2;
                float ox = dx - ax * along, oy = dy - ay * along, oz = dz - az * along;
                in = along > 0 && along < 1.1f && ox * ox + oy * oy + oz * oz < s[6] * s[6];
            }
            if (in) {
                out.set(t);
            }
        }
        return out.isEmpty() ? NONE : out;
    }

    /** The bought stick's turn as a mesh turn, or null while it stands upright. */
    static CarRenderer.Turn meshTurn(CarProp car, String model) {
        Rig rig = RIGS.get(model);
        if (rig == null || rig.mesh == null) {
            return null;
        }
        Quaternionf q = lean(car);
        if (Math.abs(pitch) + Math.abs(roll) < 1e-3f) {
            return null;
        }
        float[] foot = rig.mesh;
        Vector3f v = new Vector3f();
        return (p, normal) -> {
            v.set(p[0], p[1], p[2]);
            if (!normal) {
                v.sub(foot[0], foot[1], foot[2]);
            }
            q.transform(v);
            if (!normal) {
                v.add(foot[0], foot[1], foot[2]);
            }
            p[0] = v.x;
            p[1] = v.y;
            p[2] = v.z;
        };
    }

    /**
     * Draws the stick (unless the cockpit has its own) and the throttle or collective, and, for the local
     * pilot in first person, their hands on them. pose is the aircraft's model space.
     */
    static void draw(CarProp car, String model, float[] eye, boolean hands, PoseStack pose, SubmitNodeCollector out, int light) {
        Rig rig = RIGS.get(model);
        if (rig == null) {
            return;
        }
        Quaternionf q = rig.mesh != null ? lastLean() : lean(car);
        Flight f = car.flight();
        Vector3f foot = new Vector3f(rig.stick[0], rig.stick[1], rig.stick[2]);
        Vector3f stickTop = rig.mesh != null
                ? new Vector3f(rig.mesh[3] - rig.mesh[0], rig.mesh[4] - rig.mesh[1], rig.mesh[5] - rig.mesh[2])
                : new Vector3f(0, rig.stickLength, 0);
        Vector3f stickDir = q.transform(new Vector3f(stickTop)).normalize();
        Vector3f grip = new Vector3f(stickDir).mul(rig.stickLength * 0.82f).add(foot);
        // the throttle: forward with power; a collective rises with it
        float power = rig.collective ? f.power : f.throttle;
        Vector3f pivot = new Vector3f(rig.lever[0], rig.lever[1], rig.lever[2]);
        float a = rig.collective ? (float) Math.toRadians(4 + 22 * power) : -LEVER + 2 * LEVER * power;
        Vector3f leverDir = rig.collective
                ? new Vector3f(0, (float) Math.sin(a), (float) Math.cos(a))
                : new Vector3f(0, (float) Math.cos(a), (float) Math.sin(a));
        Vector3f handle = new Vector3f(leverDir).mul(rig.leverLength).add(pivot);
        Identifier tex = swatch();
        out.submitCustomGeometry(pose, RenderTypes.entityCutout(tex), (p, v) -> {
            Vector3f side = new Vector3f(1, 0, 0);
            if (rig.mesh == null) {
                // a rubber boot, the shaft, the grip with its trigger and a red button on top
                box(v, p, foot, new Vector3f(0, 1, 0), side, 0.05f, 0.05f, 0.035f, DARK, light);
                box(v, p, foot, stickDir, side, 0.011f, 0.011f, rig.stickLength * 0.62f, METAL, light);
                Vector3f g0 = new Vector3f(stickDir).mul(rig.stickLength * 0.6f).add(foot);
                Vector3f fwd = q.transform(new Vector3f(0, 0, 1));
                box(v, p, g0, stickDir, side, 0.02f, 0.024f, rig.stickLength * 0.4f, BLACK, light);
                box(v, p, new Vector3f(stickDir).mul(rig.stickLength * 0.12f).add(g0).add(new Vector3f(fwd).mul(0.024f)),
                        stickDir, side, 0.006f, 0.008f, 0.02f, METAL, light);
                box(v, p, new Vector3f(stickDir).mul(rig.stickLength).add(foot), stickDir, side, 0.008f, 0.008f, 0.008f, RED, light);
            }
            // the throttle quadrant's slot, the lever and its grip
            Vector3f slot = new Vector3f(pivot).add(0, 0.004f, 0);
            box(v, p, slot, new Vector3f(0, 1, 0), side, 0.02f, rig.collective ? 0.02f : 0.07f, 0.006f, BLACK, light);
            box(v, p, pivot, leverDir, side, 0.009f, 0.009f, rig.leverLength * 0.75f, METAL, light);
            Vector3f h0 = new Vector3f(leverDir).mul(rig.leverLength * 0.7f).add(pivot);
            box(v, p, h0, leverDir, side, 0.026f, 0.022f, rig.leverLength * 0.36f, BLACK, light);
            if (power > 0.85f && !rig.collective) {
                box(v, p, new Vector3f(leverDir).mul(rig.leverLength * 0.9f).add(pivot).add(-0.027f, 0, 0), leverDir, side,
                        0.003f, 0.01f, 0.02f, RED, light);
            }
        });
        if (hands) {
            Vector3f right = new Vector3f(grip).add(-0.012f, 0, 0);
            Vector3f rightPalm = new Vector3f(right).add(1, 0, 0);
            Vector3f left = new Vector3f(handle).add(0.012f, 0.01f, 0);
            Vector3f leftPalm = new Vector3f(left).add(rig.collective ? new Vector3f(0, -1, 0) : new Vector3f(-1, 0, 0));
            CarArms.grips(pose, out, light, eye, left, leftPalm, right, rightPalm);
        }
    }

    private static Quaternionf lastLean() {
        return new Quaternionf().rotationX(-pitch * STICK).rotateZ(roll * STICK);
    }

    private static final int DARK = 0, BLACK = 1, METAL = 2, RED = 3;

    private static Identifier swatch() {
        if (swatch == null) {
            NativeImage image = new NativeImage(4, 1, false);
            image.setPixel(DARK, 0, 0xFF262624);
            image.setPixel(BLACK, 0, 0xFF121212);
            image.setPixel(METAL, 0, 0xFF55575A);
            image.setPixel(RED, 0, 0xFFB01A16);
            swatch = ZomboidCraft.id("dynamic/air_controls");
            Minecraft.getInstance().getTextureManager().register(swatch, new DynamicTexture(() -> "ZomboidCraft aircraft controls", image));
        }
        return swatch;
    }

    /**
     * A box from base along axis for length, half widths across (toward side) and deep, in one colour of
     * the swatch, each face wound to face out.
     */
    private static void box(VertexConsumer out, PoseStack.Pose pose, Vector3f base, Vector3f axis, Vector3f side, float across, float deep,
                            float length, int colour, int light) {
        Vector3f a = new Vector3f(axis).normalize();
        Vector3f b = new Vector3f(side).sub(new Vector3f(a).mul(side.dot(a)));
        if (b.lengthSquared() < 1e-6f) {
            b.set(0, 0, 1).sub(new Vector3f(a).mul(a.z));
        }
        b.normalize();
        Vector3f c = new Vector3f(a).cross(b);
        Vector3f[] k = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            k[i] = new Vector3f(base)
                    .add(new Vector3f(a).mul((i & 1) == 0 ? 0 : length))
                    .add(new Vector3f(b).mul((i & 2) == 0 ? -across : across))
                    .add(new Vector3f(c).mul((i & 4) == 0 ? -deep : deep));
        }
        int[][] faces = {{0, 2, 6, 4}, {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 5, 7, 6}};
        Vector3f centre = new Vector3f(base).add(new Vector3f(a).mul(length / 2));
        float u = (colour + 0.5f) / 4;
        for (int[] face : faces) {
            Vector3f mid = new Vector3f(k[face[0]]).add(k[face[1]]).add(k[face[2]]).add(k[face[3]]).mul(0.25f);
            Vector3f n = new Vector3f(mid).sub(centre).normalize();
            Vector3f e1 = new Vector3f(k[face[1]]).sub(k[face[0]]), e2 = new Vector3f(k[face[2]]).sub(k[face[0]]);
            boolean flip = new Vector3f(e1).cross(e2).dot(n) < 0;
            for (int i = 0; i < 4; i++) {
                Vector3f pt = k[face[flip ? 3 - i : i]];
                out.addVertex(pose, pt.x, pt.y, pt.z)
                        .setColor(-1)
                        .setUv(u, 0.5f)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, n.x, n.y, n.z);
            }
        }
    }
}
