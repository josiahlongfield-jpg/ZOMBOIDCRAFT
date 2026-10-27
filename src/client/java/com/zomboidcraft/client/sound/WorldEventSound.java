package com.zomboidcraft.client.sound;

import com.zomboidcraft.world.cars.AirSounds;
import com.zomboidcraft.world.cars.CarProp;
import com.zomboidcraft.world.events.WorldEvents;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * This player's side of the shared world events ({@link WorldEvents}): every game is told the same siren,
 * explosion or flyby, and each plays it from where its own player stands. A siren or explosion starts only once
 * its sound has had time to travel here, so a friend nearer to it hears it first. A flyby's far-off roar is placed
 * where the aircraft was when the sound now arriving left it, and bent by the Doppler shift; once the aircraft
 * itself is near enough to be in this world, its own engines take over ({@code AirSound}) and this fades out.
 */
public final class WorldEventSound {
    private static final List<Pending> PENDING = new ArrayList<>();
    /** How far off a flyby can still be heard, in blocks. */
    private static final double JET_RANGE = 800, HELI_RANGE = 520;
    /** Where the aircraft is overhead in every flyby recording (tools/audio/build_flyby_sounds.py). */
    private static final double PEAK = 10;

    private record Pending(WorldEvents.Event event, boolean[] played) {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(WorldEvents.Event.TYPE, (payload, context) ->
                context.client().execute(() -> receive(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(WorldEventSound::tick);
        CarProp.quiet = WorldEventSound::quiet;
    }

    private static void receive(WorldEvents.Event e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.flyby() && e.sound() == 0) {
            mc.getSoundManager().play(new Flyby(e));
        } else {
            PENDING.add(new Pending(e, new boolean[1]));
        }
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.level.dimension() != Level.OVERWORLD) {
            PENDING.clear();
            return;
        }
        long now = mc.level.getGameTime();
        for (var it = PENDING.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            WorldEvents.Event e = p.event();
            double t = (now - e.start()) / 20.0;
            if (e.flyby()) {
                if (t > e.ticks() / 20.0) {
                    it.remove();
                } else if (!p.played()[0]) {
                    recording(mc, p, t);
                }
                continue;
            }
            Vec3 at = e.at(0);
            double d = mc.player.getEyePosition().distanceTo(at);
            if (t > e.ticks() / 20.0 + d / WorldEvents.SOUND_SPEED) {
                it.remove();
                continue;
            }
            if (e.kind() == WorldEvents.Kind.SIREN) {
                // Josiah: the siren always sounds far off, wherever you are on the map, and everyone hears it
                if (!p.played()[0] && t >= Math.min(d, 600) / WorldEvents.SOUND_SPEED) {
                    p.played()[0] = true;
                    mc.getSoundManager().play(new Distant(WorldEvents.SIREN, at));
                }
                continue;
            }
            if (!p.played()[0] && t >= d / WorldEvents.SOUND_SPEED) {
                p.played()[0] = true;
                SoundEvent sound = d > 150 ? WorldEvents.EXPLOSION_FAR : WorldEvents.EXPLOSION;
                if (d < sound.getRange(1)) {
                    mc.getSoundManager().play(new Fixed(sound, at, 1.6f));
                }
            }
        }
    }

    /**
     * Starts a flyby's recording when its loudest moment, PEAK seconds in, will arrive here just as the sound of
     * the aircraft at its nearest to us does. Worked out again each tick until then, as the player moves.
     */
    private static void recording(Minecraft mc, Pending p, double t) {
        WorldEvents.Event e = p.event();
        Vec3 ear = mc.player.getEyePosition(), v = e.velocity();
        double end = e.ticks() / 20.0;
        double tc = Mth.clamp(ear.subtract(e.at(0)).dot(v) / v.lengthSqr(), 0, end);
        double dc = e.at(tc).distanceTo(ear);
        double range = e.kind() == WorldEvents.Kind.JET ? JET_RANGE : HELI_RANGE;
        double begin = tc + dc / WorldEvents.SOUND_SPEED - PEAK;
        if (dc > range || t > begin + 3) {
            p.played()[0] = t > begin;      // out of earshot, or we came too late to hear it come in
            return;
        }
        if (t >= begin) {
            p.played()[0] = true;
            SoundEvent[] takes = e.kind() == WorldEvents.Kind.JET ? WorldEvents.FLYBY_JET : WorldEvents.FLYBY_HELI;
            SoundEvent take = takes[Math.min(e.sound(), takes.length) - 1];
            mc.getSoundManager().play(new Recording(take, e, (float) Math.pow(1 - dc / range, 1.4)));
        }
    }

    /** Whether this aircraft is one of the flybys playing as a whole recording, so its own engines keep quiet. */
    private static boolean quiet(CarProp car) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || car.isVehicle()) {
            return false;
        }
        long now = mc.level.getGameTime();
        for (Pending p : PENDING) {
            WorldEvents.Event e = p.event();
            if (e.flyby() && e.at((now - e.start()) / 20.0).distanceToSqr(car.position()) < 40 * 40) {
                return true;
            }
        }
        return false;
    }

    /** Where the sound reaching ear at t seconds set off from: solves (t - ts) * c = |P(ts) - ear|. */
    private static double heard(WorldEvents.Event e, Vec3 ear, double t) {
        double ts = t;
        for (int i = 0; i < 5; i++) {
            ts = t - e.at(ts).distanceTo(ear) / WorldEvents.SOUND_SPEED;
        }
        return ts;
    }

    /**
     * A whole flyby recording, its Doppler shift already in it: carried along the aircraft's line (where the
     * sound now arriving left it) so it sweeps across from the side it comes from, as loud as how near it passes.
     */
    private static final class Recording extends AbstractTickableSoundInstance {
        private final WorldEvents.Event event;

        Recording(SoundEvent take, WorldEvents.Event e, float volume) {
            super(take, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.event = e;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = Mth.clamp(volume, 0.02f, 1f);
            place();
        }

        private void place() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                return;
            }
            double t = (mc.level.getGameTime() - event.start()) / 20.0;
            Vec3 at = event.at(Mth.clamp(heard(event, mc.player.getEyePosition(), t), 0, event.ticks() / 20.0));
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null || mc.level.dimension() != Level.OVERWORLD) {
                stop();
                return;
            }
            place();
        }
    }

    /** A siren or explosion, where it is; its loudness falls off with distance as the player moves. */
    private static final class Distant extends AbstractTickableSoundInstance {
        private final Vec3 source;

        Distant(SoundEvent event, Vec3 source) {
            super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.source = source;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.6f;
            place();
        }

        /** Always the same faint distance away, in the direction of where it really is. */
        private void place() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return;
            }
            Vec3 ear = mc.player.getEyePosition();
            Vec3 dir = source.subtract(ear).multiply(1, 0, 1);
            dir = dir.lengthSqr() < 1 ? new Vec3(1, 0, 0) : dir.normalize();
            this.x = ear.x + dir.x * 64;
            this.y = ear.y + 12;
            this.z = ear.z + dir.z * 64;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null || mc.level.dimension() != Level.OVERWORLD) {
                stop();
                return;
            }
            place();
        }
    }

    /** An explosion, where it is; its loudness falls off with distance as the player moves. */
    private static final class Fixed extends AbstractSoundInstance {
        Fixed(SoundEvent event, Vec3 at, float volume) {
            super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
            this.volume = volume;
            this.pitch = 0.95f + random.nextFloat() * 0.1f;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
        }
    }

    /**
     * A flyby's far-off loop: placed where the sound now arriving left the aircraft, as loud as the distance it
     * crossed allows, pitched by how fast the aircraft was closing or pulling away.
     */
    private static final class Flyby extends AbstractTickableSoundInstance {
        private final WorldEvents.Event event;
        private final double range;
        private float near = 0;

        Flyby(WorldEvents.Event e) {
            super(e.kind() == WorldEvents.Kind.JET ? AirSounds.JET_DISTANT : AirSounds.HELI_DISTANT, SoundSource.NEUTRAL,
                    SoundInstance.createUnseededRandom());
            this.event = e;
            this.range = e.kind() == WorldEvents.Kind.JET ? JET_RANGE : HELI_RANGE;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.001f;
            Vec3 at = e.at(0);
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null || mc.level.dimension() != Level.OVERWORLD) {
                stop();
                return;
            }
            Vec3 ear = mc.player.getEyePosition();
            double t = (mc.level.getGameTime() - event.start()) / 20.0;
            // the moment the sound reaching us now set off: solve (t - ts) * c = |P(ts) - ear|
            double ts = t;
            for (int i = 0; i < 5; i++) {
                ts = t - event.at(ts).distanceTo(ear) / WorldEvents.SOUND_SPEED;
            }
            double end = event.ticks() / 20.0;
            if (ts > end) {
                stop();
                return;
            }
            Vec3 from = event.at(ts);
            double d = from.distanceTo(ear);
            this.x = from.x;
            this.y = from.y;
            this.z = from.z;
            Vec3 toEar = ear.subtract(from).normalize();
            double closing = event.velocity().dot(toEar);
            this.pitch = (float) Mth.clamp(1 / (1 - closing / WorldEvents.SOUND_SPEED), 0.6, 1.8);
            // the aircraft itself is here: its own engines carry the sound now
            Vec3 now = event.at(t);
            boolean here = !mc.level.getEntitiesOfClass(CarProp.class, new AABB(now, now).inflate(48), CarProp::aircraft).isEmpty()
                    && d < 160;
            near = Mth.clamp(near + (here ? 0.05f : -0.05f), 0, 1);
            double edge = Math.min(Mth.clamp(ts / 2, 0, 1), Mth.clamp((end - ts) / 2, 0, 1));   // fade in and out at the ends
            double loud = d >= range ? 0 : Math.pow(1 - d / range, 1.6) * edge * (1 - near);
            this.volume = (float) Mth.clamp(loud * 1.1, 0.001, 1);
        }
    }

    private WorldEventSound() {
    }
}
