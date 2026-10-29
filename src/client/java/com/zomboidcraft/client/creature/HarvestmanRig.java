package com.zomboidcraft.client.creature;

import com.zomboidcraft.creature.HarvestmanBody;
import com.zomboidcraft.creature.HarvestmanEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One Harvestman as this client draws it: the body where the entity is, eased and riding on its pose (reared up, jolted
 * by a hit, bowed to feed, sprawled dead), and each leg bent from its hip to the foot the server planted (the feet come
 * in each tick and are drawn as far behind as the body is). A leg shot off at the knee leaves its thigh hanging and
 * twitching, and its lower half flies off and lies where it lands. Render thread only.
 */
public final class HarvestmanRig {
    /** How many ticks behind the server the body is drawn (its position eases in over a few ticks). */
    private static final int DELAY = 2;

    /** Part matrices from the model's frames to the entity's drawn position: body, then thigh and lower leg per leg. */
    public final Matrix4f body = new Matrix4f();
    public final Matrix4f[] thigh = new Matrix4f[4], shin = new Matrix4f[4];
    public int lost;
    public float camo = 1, glowPulse;
    /** How hard its eyes catch the light (a beam swung across them flares them, then they settle), 0..1. */
    public float eyeShine;
    public boolean feedArms;

    /** A lower leg shot off: where it is, how it's moving, and how long it has lain. */
    public static final class Flying {
        public final Matrix4f at = new Matrix4f();
        Vec3 pos, vel;
        final Quaternionf turn = new Quaternionf();
        final Vector3f spin = new Vector3f();
        double age;
        boolean down;
        public final int leg;
        public float fade = 1;

        Flying(int leg) {
            this.leg = leg;
        }
    }

    public final List<Flying> flying = new ArrayList<>();

    private double t, jolt, rear, pitch = 0.1, deadRoll;
    private int flinchesSeen = -1, lostSeen = -1, stumblesSeen = -1, recoilsSeen = -1;
    /** Alive at rest: its weight shifting from leg to leg, and small sharp twitches (toward a noise, or at nothing). */
    private double shiftRoll, shiftPitch, shiftWantRoll, shiftWantPitch, nextShift, twitchYaw, twitchPitch,
            twitchWantYaw, twitchWantPitch, twitchHold, nextTwitch = 2, lookYaw, lookPitch;
    /** A foot slipping (which leg), and a shot in the eyes throwing it back: each 1 when it happens, easing to 0. */
    private double stumble, recoil;
    private int stumbleLeg;
    /** Its legs gone, as drawn: the lean toward the missing side. */
    private double limpRoll, limpPitch;
    private double flare;
    private boolean litSeen;
    private long lastNanos;

    public HarvestmanRig() {
        for (int i = 0; i < 4; i++) {
            thigh[i] = new Matrix4f();
            shin[i] = new Matrix4f();
        }
    }

    private static Vec3 norm(Vec3 v) {
        double l = v.length();
        return l < 1e-9 ? v : v.scale(1 / l);
    }

    /**
     * Brings it up to now and works out every part's matrix.
     *
     * @param centre the body's centre as drawn this frame
     * @param drawnAt the entity's drawn position (what the matrices are relative to)
     * @param q how it lies as drawn this frame (model to world)
     */
    public void update(HarvestmanEntity e, Level level, Vec3 centre, Vec3 drawnAt, Quaternionf q, float partialTick) {
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        t += dt;
        Vector3f xv = q.transform(new Vector3f(1, 0, 0)), yv = q.transform(new Vector3f(0, 1, 0)), zv = q.transform(new Vector3f(0, 0, 1));
        Vec3 fx = norm(new Vec3(xv.x, xv.y, xv.z)), up = norm(new Vec3(yv.x, yv.y, yv.z)), fz = norm(new Vec3(zv.x, zv.y, zv.z));
        int pose = e.pose();
        boolean dead = pose == HarvestmanEntity.DEAD, feeding = pose == HarvestmanEntity.FEED, holding = pose == HarvestmanEntity.HOLD;
        feedArms = feeding || holding;
        // a hit jolts it
        if (flinchesSeen >= 0 && e.flinches() != flinchesSeen) {
            jolt = 1;
        }
        flinchesSeen = e.flinches();
        jolt = Math.max(0, jolt - dt * 2.4);
        double j = Math.sin(Math.min(1, jolt) * Math.PI);
        // a foot skidding: it lurches down toward that leg; a shot in the eyes: it throws its front up and back
        if (stumblesSeen >= 0 && e.stumbles() != stumblesSeen) {
            stumble = 1;
            stumbleLeg = e.stumbles() & 3;
        }
        stumblesSeen = e.stumbles();
        if (recoilsSeen >= 0 && e.recoils() != recoilsSeen) {
            recoil = 1;
        }
        recoilsSeen = e.recoils();
        stumble = Math.max(0, stumble - dt * 3);
        recoil = Math.max(0, recoil - dt * 1.6);
        double sk = Math.sin(stumble * Math.PI), rk = Math.sin(Math.min(1, recoil * 1.4) * Math.PI) * (recoil > 0 ? 1 : 0);
        rear = Mth.lerp(1 - Math.exp(-dt * 10), rear, e.rear());
        // legs gone: it leans toward the side that's missing one, nose down without a front leg
        double lr = 0, lp = 0;
        for (int li = 0; li < 4; li++) {
            if ((e.lost() >> li & 1) != 0) {
                lr -= HarvestmanBody.side(li) * 0.1;
                lp += (li & 1) == 0 ? 0.08 : -0.05;
            }
        }
        limpRoll = Mth.lerp(1 - Math.exp(-dt * 3), limpRoll, dead ? 0 : lr);
        limpPitch = Mth.lerp(1 - Math.exp(-dt * 3), limpPitch, dead ? 0 : lp);
        double pitchWant = dead ? 0.05 : feedArms ? 0.0 : 0.1 - 0.42 * rear - 0.14 * j + limpPitch - 0.5 * rk;
        pitch = Mth.lerp(1 - Math.exp(-dt * 8), pitch, pitchWant);
        deadRoll = Mth.lerp(1 - Math.exp(-dt * 1.5), deadRoll, dead ? 0.22 : 0);
        camo = (float) Mth.lerp(1 - Math.exp(-dt * 6), camo, e.camo());
        glowPulse = feeding ? (float) (0.6 + 0.4 * Math.sin(t * 7.5)) : 1f;

        // the feet as the server planted them, as far behind as the body is drawn
        Vec3[] feet = new Vec3[4];
        for (int i = 0; i < 4; i++) {
            Vec3 a = e.feetSeen[i][(e.feetSeenAt - DELAY - 1) & 3], b = e.feetSeen[i][(e.feetSeenAt - DELAY) & 3];
            feet[i] = a != null && b != null ? a.lerp(b, partialTick) : e.foot(i);
        }
        // the body: risen as it rears, dipped by a jolt, bobbing as it walks or works at what it holds
        boolean walking = !dead && e.position().distanceToSqr(new Vec3(e.xo, e.yo, e.zo)) > 0.0004;
        boolean limping = walking && e.lost() != 0;
        double hitch = limping ? Math.pow(Math.abs(Math.sin(t * 2.6)), 4) : 0;
        double bob = dead ? 0 : feeding ? 0.02 * Math.sin(t * 7.5) : limping ? 0.03 * Math.sin(t * 2.6) - 0.09 * hitch
                : walking ? 0.05 * Math.sin(t * 3.4) : 0.035 * Math.sin(t * 1.15);
        double sway = dead ? 0 : 0.02 * Math.sin(t * 0.8);
        idle(e, dt, !dead && !walking && !feedArms, fx, up, fz, centre);
        double sideways = stumbleLeg < 2 ? -1 : 1;
        Vec3 o = centre.add(up.scale(bob + 0.28 * rear - 0.28 * j - 0.15 * sk - 0.1 * rk)).add(fz.scale(-0.35 * rk))
                .add(fx.scale(sideways * 0.08 * sk - 0.05 * shiftRoll));
        double yaw = sway * 0.5 + twitchYaw + lookYaw + 0.12 * rk * Math.sin(t * 38);
        double roll = sway + deadRoll + shiftRoll + limpRoll - sideways * 0.2 * sk + (limping ? limpRoll * 0.6 * hitch : 0);
        Vec3[] ax = axes(fx, up, fz, pitch + shiftPitch + twitchPitch + lookPitch + (walking ? 0.025 * Math.sin(t * 1.7) : 0), yaw, roll);
        HarvestmanBody.Frame frame = new HarvestmanBody.Frame(o, ax[0], ax[1], ax[2]);
        set(body, o, ax[0], ax[1], ax[2], drawnAt);

        int nowLost = e.lost();
        for (int li = 0; li < 4; li++) {
            HarvestmanBody.Bent b;
            if ((nowLost >> li & 1) != 0) {
                b = HarvestmanEntity.dangle(frame, up, li);
                // the stump twitches
                Vec3 tw = up.scale(0.12 * Math.sin(t * 9 + li * 2) * (dead ? 0 : 1));
                b = new HarvestmanBody.Bent(b.hip(), b.knee().add(tw), b.knee().add(tw), b.n());
            } else {
                b = HarvestmanBody.bend(frame, up, li, feet[li], up);
            }
            Vec3 tx = norm(b.knee().subtract(b.hip())), sx = norm(b.tip().subtract(b.knee()));
            if (tx.lengthSqr() < 0.5) {
                tx = fx;
            }
            if (sx.lengthSqr() < 0.5) {
                sx = tx;
            }
            set(thigh[li], b.hip(), tx, b.n().cross(tx), b.n(), drawnAt);
            // just shot off: the lower leg flies from where it was
            if (lostSeen >= 0 && (nowLost >> li & 1) != 0 && (lostSeen >> li & 1) == 0) {
                Flying f = new Flying(li);
                f.pos = b.knee();
                Matrix4f m = new Matrix4f(shin[li]);
                f.turn.setFromNormalized(m);
                Vec3 out = norm(b.knee().subtract(centre));
                f.vel = out.scale(3 + e.getRandom().nextDouble() * 2).add(0, 3.5, 0);
                f.spin.set((float) e.getRandom().nextGaussian() * 6, (float) e.getRandom().nextGaussian() * 6, (float) e.getRandom().nextGaussian() * 6);
                flying.add(f);
            }
            if ((nowLost >> li & 1) == 0) {
                set(shin[li], b.knee(), sx, b.n().cross(sx), b.n(), drawnAt);
            }
        }
        lostSeen = nowLost;
        lost = nowLost;
        fly(level, e, dt, drawnAt);
        shine(e, level, frame, dt, partialTick, dead);
    }

    /**
     * At rest it's never quite still: its weight shifts from leg to leg every few seconds, and now and then it gives a
     * small sharp twitch, held a moment, then eases back. Something it's looking at (a noise it heard) it snaps toward,
     * as far as its body will turn without stepping.
     */
    private void idle(HarvestmanEntity e, double dt, boolean still, Vec3 fx, Vec3 up, Vec3 fz, Vec3 centre) {
        var rnd = e.getRandom();
        nextShift -= dt;
        if (nextShift <= 0) {
            nextShift = 2.5 + rnd.nextDouble() * 3;
            shiftWantRoll = still ? (rnd.nextDouble() - 0.5) * 0.12 : 0;
            shiftWantPitch = still ? (rnd.nextDouble() - 0.5) * 0.07 : 0;
        }
        if (!still) {
            shiftWantRoll = shiftWantPitch = 0;
        }
        shiftRoll = Mth.lerp(1 - Math.exp(-dt * 1.4), shiftRoll, shiftWantRoll);
        shiftPitch = Mth.lerp(1 - Math.exp(-dt * 1.4), shiftPitch, shiftWantPitch);
        // twitches: a snap, held, and back
        nextTwitch -= dt;
        if (still && nextTwitch <= 0) {
            nextTwitch = 2 + rnd.nextDouble() * 5;
            twitchWantYaw = (rnd.nextBoolean() ? 1 : -1) * (0.05 + rnd.nextDouble() * 0.1);
            twitchWantPitch = (rnd.nextDouble() - 0.5) * 0.1;
            twitchHold = 0.3 + rnd.nextDouble() * 0.9;
        }
        if (twitchHold > 0) {
            twitchHold -= dt;
        } else {
            twitchWantYaw = twitchWantPitch = 0;
        }
        double snap = twitchHold > 0 ? 28 : 5;
        twitchYaw = Mth.lerp(1 - Math.exp(-dt * snap), twitchYaw, still ? twitchWantYaw : 0);
        twitchPitch = Mth.lerp(1 - Math.exp(-dt * snap), twitchPitch, still ? twitchWantPitch : 0);
        // toward what it's looking at
        Vec3 look = e.lookingAt();
        double wy = 0, wp = 0;
        if (still && look != null) {
            Vec3 to = look.subtract(centre);
            double ahead = to.dot(fz), across = to.dot(fx), high = to.dot(up);
            wy = Mth.clamp(Math.atan2(-across, Math.max(0.1, ahead)), -0.35, 0.35);
            wp = Mth.clamp(-Math.atan2(high, Math.max(0.5, Math.hypot(ahead, across))) * 0.5, -0.2, 0.15);
        }
        lookYaw = Mth.lerp(1 - Math.exp(-dt * 14), lookYaw, wy);
        lookPitch = Mth.lerp(1 - Math.exp(-dt * 8), lookPitch, wp);
    }

    /** Its eyes catching the light: a beam swung across them (or a lit room) as you look at it flares them, briefly. */
    private void shine(HarvestmanEntity e, Level level, HarvestmanBody.Frame frame, double dt, float partialTick, boolean dead) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        boolean lit = false;
        if (!dead && mc.player != null) {
            Vec3 eyes = frame.at(0, EYES[0][1], EYES[0][2]), cam = mc.player.getEyePosition(partialTick);
            Vec3 to = eyes.subtract(cam);
            double d = to.length();
            if (d > 1 && d < 32 && mc.player.getViewVector(partialTick).dot(to.scale(1 / d)) > Math.cos(Math.toRadians(11))
                    && level.getMaxLocalRawBrightness(net.minecraft.core.BlockPos.containing(eyes)) >= 8) {
                lit = level.clip(new ClipContext(cam, eyes, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player))
                        .getType() == HitResult.Type.MISS;
            }
        }
        if (lit && !litSeen) {
            flare = 1;
        }
        litSeen = lit;
        flare = Math.max(0, flare - dt * 1.4);
        float want = lit ? (float) Math.max(0.3, flare) : 0f;
        eyeShine = (float) Mth.lerp(1 - Math.exp(-dt * (want > eyeShine ? 20 : 4)), eyeShine, want);
    }

    /** The flying lower legs fall, tumble, land, lie a minute and fade. */
    private void fly(Level level, HarvestmanEntity e, double dt, Vec3 drawnAt) {
        for (int k = flying.size() - 1; k >= 0; k--) {
            Flying f = flying.get(k);
            f.age += dt;
            if (!f.down) {
                Vec3 next = f.pos.add(f.vel.scale(dt));
                BlockHitResult hit = level.clip(new ClipContext(f.pos, next.add(0, -0.15, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
                if (hit.getType() != HitResult.Type.MISS) {
                    // it lands and lies along the floor
                    f.pos = hit.getLocation().add(0, 0.12, 0);
                    f.down = true;
                    Vector3f along = f.turn.transform(new Vector3f(1, 0, 0));
                    Vec3 flat = norm(new Vec3(along.x, 0, along.z));
                    if (flat.lengthSqr() < 0.5) {
                        flat = new Vec3(1, 0, 0);
                    }
                    Vec3 side = new Vec3(0, 1, 0).cross(flat);
                    // lying on its side, the bow along the floor
                    f.turn.setFromNormalized(new org.joml.Matrix3f(
                            new Vector3f((float) flat.x, (float) flat.y, (float) flat.z),
                            new Vector3f((float) side.x, (float) side.y, (float) side.z),
                            new Vector3f(0, 1, 0)));
                } else {
                    f.pos = next;
                    f.vel = f.vel.add(0, -12 * dt, 0);
                    f.turn.rotateXYZ(f.spin.x * (float) dt, f.spin.y * (float) dt, f.spin.z * (float) dt);
                }
            }
            if (f.age > 60) {
                f.fade = (float) Math.max(0, 1 - (f.age - 60) / 3);
            }
            if (f.age > 63 || f.pos.distanceToSqr(e.position()) > 64 * 64) {
                flying.remove(k);
                continue;
            }
            Vector3f x = f.turn.transform(new Vector3f(1, 0, 0)), y = f.turn.transform(new Vector3f(0, 1, 0)), z = f.turn.transform(new Vector3f(0, 0, 1));
            set(f.at, f.pos, new Vec3(x.x, x.y, x.z), new Vec3(y.x, y.y, y.z), new Vec3(z.x, z.y, z.z), drawnAt);
        }
    }

    /** The body's axes: the flat frame turned by a yaw, a pitch (nose down) and a roll, as the preview's rot(). */
    private static Vec3[] axes(Vec3 fx, Vec3 fy, Vec3 fz, double pitch, double yaw, double roll) {
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch), cr = Math.cos(roll), sr = Math.sin(roll);
        double[][] m = {
                {cy * cr + sy * sp * sr, -cy * sr + sy * sp * cr, sy * cp},
                {cp * sr, cp * cr, -sp},
                {-sy * cr + cy * sp * sr, sy * sr + cy * sp * cr, cy * cp}};
        Vec3[] out = new Vec3[3];
        for (int c = 0; c < 3; c++) {
            out[c] = fx.scale(m[0][c]).add(fy.scale(m[1][c])).add(fz.scale(m[2][c]));
        }
        return out;
    }

    private static void set(Matrix4f m, Vec3 o, Vec3 x, Vec3 y, Vec3 z, Vec3 drawnAt) {
        m.set((float) x.x, (float) x.y, (float) x.z, 0f,
                (float) y.x, (float) y.y, (float) y.z, 0f,
                (float) z.x, (float) z.y, (float) z.z, 0f,
                (float) (o.x - drawnAt.x), (float) (o.y - drawnAt.y), (float) (o.z - drawnAt.z), 1f);
    }

    // ------------------------------------------------------------------------------------------------ its eyes

    /** Three small red eyes down each side of its front, in the body's frame: centre and size (HarvestmanBody). */
    public static final float[][] EYES = HarvestmanBody.EYES;
}
