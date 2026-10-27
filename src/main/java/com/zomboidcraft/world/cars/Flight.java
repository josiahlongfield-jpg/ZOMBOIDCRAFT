package com.zomboidcraft.world.cars;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * How an aircraft flies: one per jet or helicopter, worked on whichever side moves it (the pilot's
 * game while someone flies it, the server once nobody does). Metres and seconds throughout, with
 * blocks as metres; a tick is 1/20 s. Orientation is a quaternion in the model's frame (nose +z,
 * the pilot's left +x, up +y), so loops and rolls never lock up.
 *
 * <p>The jet: thrust from a lever that stays where you leave it and an engine that spools up behind
 * it (an afterburner on the last 15%), lift from airspeed and the angle between the nose and where
 * it's going (a stall past about 16 degrees), drag that grows with speed and with how hard it's
 * turning, and control surfaces that go soft when it's slow. On its wheels it rolls, brakes and
 * steers by the nose wheel, and only lifts its nose once it's fast enough.
 *
 * <p>The helicopter: a rotor that takes ten seconds to come up to speed, a collective that holds the
 * height you're at unless you climb or sink, a disc you tip to fly (W/S nose down and up, A/D bank),
 * a nose that follows your mouse, and a little extra lift close to the ground.
 */
public final class Flight {
    public static final float G = 9.81f, DT = 0.05f;

    public enum Kind { JET, HELI }

    /** What the controls ask for this tick, from the pilot (or nothing, for an empty aircraft). */
    public record Controls(float pitch, float roll, float yaw, float throttle, boolean brake, float cyclicFwd, float cyclicSide,
                           float collective, float turn) {
        public static final Controls NONE = new Controls(0, 0, 0, 0, false, 0, 0, 0, 0);
    }

    /** How a tick ended, for whoever moves the aircraft: the move to make, in blocks. */
    public final Kind kind;
    public final Quaternionf q = new Quaternionf(), qO = new Quaternionf();
    /** Velocity, metres a second. */
    public Vec3 vel = Vec3.ZERO;
    /** The jet's throttle lever, 0 to 1 (afterburner past 0.85). */
    public float throttle;
    /** Thrust the engine actually makes (lags the lever), 0 to 1; for the helicopter the collective's share of full lift. */
    public float power;
    /** Engine core (jet, idle 0.6) or rotor (helicopter) speed, 0 to 1. */
    public float rpm;
    /** The helicopter's attitude in degrees: heading as Minecraft's yaw, nose up and right wing down positive. */
    public float heading, pitch, roll;
    /** Felt load in g, angle of attack (radians), and whether the wing has stalled. For the HUD and sounds. */
    public float load = 1, aoa;
    public boolean stalled, grounded = true, airbrake;
    /** Top speed allowed (metres a second), from the server's settings. */
    public float top;
    /**
     * The pilot (set by their game each tick, {@link AirReadiness}): a trainee hasn't read the manual and
     * can only taxi; skill 0 to 1 is Piloting, which steadies the controls.
     */
    public boolean trainee;
    public float skill = 1;
    /**
     * The helicopter's level lock (set by the pilot's game): holds the height it had when the collective was
     * let go, and with the cyclic centred tilts against any drift until it hovers in place.
     */
    public boolean levelLock;
    /**
     * The jet's flight assist (the pilot's client sets it each tick): where the pilot is looking, which the
     * jet flies straight toward, banking itself through the turn; null flies on the full physics.
     */
    public org.joml.Vector3f assist;
    /** Ticks since the wheels last touched. */
    private int airTicks;
    /** How far it has drifted above the held height, metres, while the lock is on. */
    private float heightError;
    private int shake;

    public Flight(Kind kind) {
        this.kind = kind;
        this.top = kind == Kind.JET ? 300 / 3.6f : 250 / 3.6f;
    }

    public Vec3 forward() {
        return dir(0, 0, 1);
    }

    public Vec3 up() {
        return dir(0, 1, 0);
    }

    public Vec3 left() {
        return dir(1, 0, 0);
    }

    private Vec3 dir(float x, float y, float z) {
        Vector3f v = q.transform(new Vector3f(x, y, z));
        return new Vec3(v.x, v.y, v.z);
    }

    /** Minecraft's yaw for the way the nose points. */
    public float yaw() {
        Vec3 f = forward();
        if (Math.abs(f.x) + Math.abs(f.z) < 1e-4) {
            Vec3 u = up().scale(-Math.signum(f.y));
            return (float) Math.toDegrees(Math.atan2(-u.x, u.z));
        }
        return (float) Math.toDegrees(Math.atan2(-f.x, f.z));
    }

    /** Nose above the horizon, degrees. */
    public float noseUp() {
        return (float) Math.toDegrees(Math.asin(Mth.clamp(forward().y, -1, 1)));
    }

    /** Right wing down, degrees. */
    public float bank() {
        return (float) Math.toDegrees(Math.atan2(left().y, up().y));
    }

    public static Quaternionf attitude(float yaw, float noseUp, float bank) {
        return new Quaternionf().rotationY((float) Math.toRadians(-yaw)).rotateX((float) Math.toRadians(-noseUp))
                .rotateZ((float) Math.toRadians(bank));
    }

    /** Sets the attitude outright (a parked aircraft, or one just spawned). */
    public void set(float yaw, float noseUp, float bank) {
        q.set(attitude(yaw, noseUp, bank));
        qO.set(q);
        heading = yaw;
        pitch = noseUp;
        roll = bank;
    }

    public float speed() {
        return (float) vel.length();
    }

    /** Stall speed: a fraction of the top speed, kept believable either way. */
    public float stallSpeed() {
        return Mth.clamp(0.36f * top, 28f, 65f);
    }

    // ---------------------------------------------------------------- the jet

    private static final float AOA_STALL = (float) Math.toRadians(16), MAX_G = 9f;

    /** Thrust, as a share of the most (afterburner) there is, from how far the engine has spooled. */
    public float thrustShare() {
        if (rpm < 0.58f) {
            return 0;
        }
        return power <= 0.85f ? 0.72f * power / 0.85f : 0.72f + 0.28f * (power - 0.85f) / 0.15f;
    }

    public boolean afterburner() {
        return kind == Kind.JET && power > 0.86f && rpm > 0.9f;
    }

    /**
     * One tick of a jet. engine: the engine's running (spooling up first if it's just been started).
     * Returns the move to make, in blocks.
     */
    public Vec3 tickJet(Controls c, boolean engine) {
        qO.set(q);
        float spooled = 0.6f;
        if (engine) {
            if (rpm < spooled - 0.01f && power == 0) {
                rpm = Math.min(spooled, rpm + spooled / 160f);   // eight seconds from cold to idle
            } else {
                throttle = Mth.clamp(throttle + c.throttle() * 0.5f * DT, 0, 1);
                power += Mth.clamp(throttle - power, -0.45f * DT, 0.35f * DT);
                rpm += Mth.clamp(spooled + 0.4f * power - rpm, -0.3f * DT, 0.3f * DT);
            }
        } else {
            power = Math.max(0, power - 0.6f * DT);
            rpm = Math.max(0, rpm - 0.1f * DT);
        }
        airbrake = c.brake() && !grounded;
        airTicks = grounded ? 0 : airTicks + 1;
        if (assist != null && !grounded) {
            return assisted(c);
        }

        Vec3 f = forward(), u = up(), l = left();
        double speed = vel.length();
        Vec3 along = speed > 0.01 ? vel.scale(1 / speed) : f;
        float vs = stallSpeed();
        aoa = speed > 2 ? (float) Math.atan2(-vel.dot(u), vel.dot(f)) : 0;
        float a = Math.abs(aoa);
        float cl = a <= AOA_STALL ? aoa / AOA_STALL
                : Math.signum(aoa) * Math.max(0.35f, 1 - (a - AOA_STALL) / 0.35f * 0.65f);
        stalled = !grounded && a > AOA_STALL && speed > 5;
        float lift = (float) (G * (speed / vs) * (speed / vs) * cl);
        lift = Mth.clamp(lift, -MAX_G * G * 0.4f, MAX_G * G);
        Vec3 liftDir = u.subtract(along.scale(u.dot(along)));
        liftDir = liftDir.lengthSqr() < 1e-6 ? u : liftDir.normalize();

        float thrust = 1.1f * G * thrustShare();
        if (trainee && grounded && speed > 9) {
            thrust = 0;     // without the manual you don't know how to take off: taxi speed at most
        }
        float cd0 = 1.1f * G / (top * top);
        double drag = cd0 * speed * speed + Math.abs(lift) * Math.min(a, 0.6f) * 0.6f + (airbrake ? 0.25f * G + 0.5f * G * speed * speed / (top * top) : 0);
        Vec3 acc = new Vec3(0, -G, 0).add(f.scale(thrust)).add(liftDir.scale(lift)).subtract(along.scale(drag));
        // the fin keeps it flying straight through the air rather than skidding
        float authority = (float) Mth.clamp((speed / (1.4 * vs)) * (speed / (1.4 * vs)), 0.05, 1);
        acc = acc.subtract(l.scale(vel.dot(l) * 1.2 * authority));

        // an unpractised pilot's hands: slower on the stick, and a wobble that settles as Piloting grows
        float hands = 0.65f + 0.35f * skill, wobble = grounded ? 0 : (1 - skill) * 0.18f;
        shake++;
        float pitchRate = (c.pitch() + wobble * (float) Math.sin(shake * 0.23)) * 28 * authority * hands;
        float rollRate = (c.roll() + wobble * (float) Math.sin(shake * 0.17 + 1.3)) * 190 * authority * hands;
        float yawRate = c.yaw() * 8 * authority;
        if (stalled) {
            pitchRate -= 12;    // the nose drops through
        }
        if (!grounded && speed > 5) {
            // weathercock stability: the fins swing the nose into a sideslip, so a banked jet turns into the airflow
            // rather than sliding, and with the stick let go the nose settles back onto the flight path
            float slip = (float) Math.toDegrees(Math.atan2(vel.dot(l), vel.dot(f)));
            yawRate -= Mth.clamp(slip, -20, 20) * 1.6f * authority;
            pitchRate -= (float) Math.toDegrees(aoa) * 0.35f * authority * (1 - Math.abs(c.pitch()));
        }
        if (grounded) {
            // on its wheels: rolling resistance, the brakes, no skidding, the nose wheel steers
            double ahead = vel.dot(f);
            double resist = 0.015 * G + (c.brake() ? 0.5 * G : 0);
            acc = acc.subtract(f.scale(Math.signum(ahead) * Math.min(Math.abs(ahead) / DT, resist)));
            acc = acc.subtract(l.scale(vel.dot(l) / DT));
            yawRate = Mth.clamp(c.yaw() + c.roll(), -1, 1) * 25 * (float) Mth.clamp(1 - Math.abs(ahead) / 45, 0.15, 1);
            rollRate = 0;
            if (speed < 0.8 * vs) {
                pitchRate = Math.min(pitchRate, 0);
            } else if (assist != null && speed > 1.05 * vs && noseUp() < 9) {
                pitchRate = Math.max(pitchRate, 6);     // the assist rotates for take-off at flying speed
            }
        }
        load = grounded ? 1 : lift / G;
        q.rotateX((float) Math.toRadians(-pitchRate * DT)).rotateY((float) Math.toRadians(-yawRate * DT))
                .rotateZ((float) Math.toRadians(rollRate * DT));
        if (grounded) {
            // the gear holds it level side to side and stops the nose going below the deck
            float nose = noseUp(), b = bank();
            q.set(attitude(yaw(), Mth.clamp(nose, 0, 15), b * 0.7f));
        }
        q.normalize();
        vel = vel.add(acc.scale(DT));
        double s = vel.length();
        if (s > top * 1.15) {
            vel = vel.scale(top * 1.15 / s);
        }
        return vel.scale(DT);
    }

    /**
     * A tick of the assisted jet in the air: the nose turns toward the pilot's aim as fast as the jet can
     * turn at this speed, the wings bank into the turn by themselves and come level when it straightens,
     * and the jet goes where it points. Thrust, drag and climbing or diving still set the speed; too slow
     * and the nose drops until it has flying speed again. A or D still roll it by hand.
     */
    private Vec3 assisted(Controls c) {
        Vec3 f = forward();
        double speed = vel.length();
        float vs = stallSpeed();
        float thrust = 1.1f * G * thrustShare();
        float cd0 = 1.1f * G / (top * top);
        double drag = cd0 * speed * speed + (airbrake ? 0.25f * G + 0.5f * G * speed * speed / (top * top) : 0);
        double newSpeed = Math.max(0, speed + (thrust - drag - G * f.y) * DT);
        float authority = (float) Mth.clamp((speed / (1.2 * vs)) * (speed / (1.2 * vs)), 0.05, 1);
        float hands = 0.7f + 0.3f * skill;
        org.joml.Vector3f nose = new org.joml.Vector3f((float) f.x, (float) f.y, (float) f.z);
        org.joml.Vector3f want = new org.joml.Vector3f(assist).normalize();
        if (airTicks < 80 && want.y < 0.14f && want.x * want.x + want.z * want.z > 1e-4f) {
            // just off the ground: keep climbing away whatever the aim, so it doesn't settle back onto the deck
            want.set(want.x, 0, want.z).normalize().mul(0.99f).add(0, 0.14f, 0).normalize();
        }
        stalled = speed < 0.75f * vs;
        if (stalled) {
            // no flying speed: the nose falls through toward the ground until it has some
            want.set(nose.x, nose.y - 1.5f, nose.z).normalize();
        }
        float before = yaw();
        float angle = nose.angle(want), most = (float) Math.toRadians(40 * (stalled ? 1 : authority) * hands) * DT;
        if (angle > 1e-4f) {
            org.joml.Vector3f axis = nose.cross(want, new org.joml.Vector3f());
            if (axis.lengthSquared() > 1e-10f) {
                axis.normalize();
                q.set(new Quaternionf().rotationAxis(Math.min(angle, most), axis).mul(q));
            }
        }
        // the wings: into the turn by themselves (right turn, right wing down), level again when it straightens
        float turnRate = Mth.wrapDegrees(yaw() - before) / DT;
        if (c.roll() != 0) {
            q.rotateZ((float) Math.toRadians(c.roll() * 150 * DT));
        } else {
            float wantBank = Mth.clamp(turnRate * 2.4f, -70, 70);
            q.rotateZ((float) Math.toRadians(Mth.clamp(wantBank - bank(), -120 * DT, 120 * DT)));
        }
        q.normalize();
        // it goes where it points, a beat behind the nose
        Vec3 along = speed > 0.01 ? vel.scale(1 / speed) : forward();
        Vec3 dir = along.add(forward().subtract(along).scale(0.3)).normalize();
        vel = dir.scale(newSpeed);
        if (newSpeed < vs) {
            vel = vel.add(0, -G * (1 - (newSpeed / vs) * (newSpeed / vs)) * DT, 0);
        }
        aoa = (float) Math.atan2(-vel.dot(up()), Math.max(0.1, vel.dot(forward())));
        load = 1 + (float) (Math.abs(Math.toRadians(turnRate)) * newSpeed / G);
        double s = vel.length();
        if (s > top * 1.15) {
            vel = vel.scale(top * 1.15 / s);
        }
        return vel.scale(DT);
    }

    // ---------------------------------------------------------------- the helicopter

    /**
     * One tick of a helicopter. height: how far the ground is below it (for the extra lift close to
     * it), up to 8. Returns the move to make, in blocks.
     */
    public Vec3 tickHeli(Controls c, boolean engine, float height) {
        qO.set(q);
        rpm = engine ? Math.min(1, rpm + 1 / 200f) : Math.max(0, rpm - 1 / 300f);
        Vec3 u = up();
        float targetVy = c.collective() > 0 ? 7 : c.collective() < 0 ? -6 : grounded ? -1 : 0;
        boolean holding = levelLock && !grounded && c.collective() == 0;
        if (holding) {
            // back to the held height, gently
            heightError += (float) vel.y * DT;
            targetVy = Mth.clamp(-heightError * 0.9f, -4, 4);
        } else {
            heightError = 0;
        }
        float most = 1.7f * G * rpm * rpm;
        if (trainee && grounded) {
            most = Math.min(most, 0.96f * G);   // without the manual it never comes off the ground
        }
        float want = (float) ((targetVy - vel.y) * 1.2 + G) / (float) Math.max(u.y, 0.35);
        float thrust = Mth.clamp(want, 0, most);
        power = most > 0 ? thrust / most : 0;
        if (height < 7) {
            thrust *= 1 + 0.12f * (1 - height / 7);  // ground effect
        }
        Vec3 acc = u.scale(thrust).add(0, -G, 0);
        Vec3 flat = new Vec3(vel.x, 0, vel.z);
        double hs = flat.length();
        acc = acc.subtract(flat.scale(0.012 + 0.00055 * hs)).subtract(0, vel.y * 0.25, 0);
        // side-on it pushes a lot more air than nose-first
        double r = Math.toRadians(heading);
        Vec3 side = new Vec3(Math.cos(r), 0, Math.sin(r));
        acc = acc.subtract(side.scale(vel.dot(side) * 0.25));

        float wantPitch = c.cyclicFwd() > 0 ? -18 : c.cyclicFwd() < 0 ? 12 : 0;
        float wantRoll = c.cyclicSide() * 25;
        if (levelLock && !grounded) {
            // the lock flies the hover: with the cyclic centred it leans against the drift, ahead and sideways
            double ahead = vel.x * -Math.sin(r) + vel.z * Math.cos(r), leftward = vel.dot(side);
            if (c.cyclicFwd() == 0) {
                wantPitch = (float) Mth.clamp(ahead * 1.6, -14, 14);
            }
            if (c.cyclicSide() == 0) {
                wantRoll = (float) Mth.clamp(leftward * 2.2, -16, 16);
            }
            if (c.cyclicFwd() == 0 && c.cyclicSide() == 0 && hs < 3) {
                acc = acc.subtract(flat.scale(0.6));   // and settles the last of it
            }
        }
        shake++;
        if (!grounded) {
            float wobble = (1 - skill) * 6;
            wantPitch += wobble * (float) Math.sin(shake * 0.19);
            wantRoll += wobble * (float) Math.sin(shake * 0.13 + 2.1);
        }
        if (grounded && thrust < G) {
            wantPitch = 0;
            wantRoll = 0;
        }
        float hands = 0.65f + 0.35f * skill;
        pitch += Mth.clamp(wantPitch - pitch, -25 * DT * hands, 25 * DT * hands);
        roll += Mth.clamp(wantRoll - roll, -50 * DT * hands, 50 * DT * hands);
        float turn = Mth.clamp(c.turn(), -3, 3);
        if (!grounded && hs > 8) {
            turn += (float) Math.toDegrees(G * Math.tan(Math.toRadians(roll)) / hs) * DT * 0.8f;
        }
        if (grounded && rpm < 0.5f) {
            turn = 0;
        }
        heading = Mth.wrapDegrees(heading + turn);
        q.set(attitude(heading, pitch, roll));
        load = (float) (thrust * u.y / G);
        stalled = false;
        aoa = 0;
        vel = vel.add(acc.scale(DT));
        double s = vel.length();
        if (s > top) {
            vel = vel.scale(top / s);
        }
        return vel.scale(DT);
    }

    /** The rotor's speed in turns a second (main rotor), for drawing and sounds. */
    public float rotorTurns() {
        return rpm * 4.8f;
    }
}
