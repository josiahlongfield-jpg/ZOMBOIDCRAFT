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

    private record Pending(WorldEvents.Event event, boolean[] played) {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(WorldEvents.Event.TYPE, (payload, context) ->
                context.client().execute(() -> receive(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(WorldEventSound::tick);
    }

    private static void receive(WorldEvents.Event e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.flyby()) {
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
            Vec3 at = e.at(0);
            double d = mc.player.getEyePosition().distanceTo(at);
            if (t > e.ticks() / 20.0 + d / WorldEvents.SOUND_SPEED) {
                it.remove();
                continue;
            }
            if (!p.played()[0] && t >= d / WorldEvents.SOUND_SPEED) {
                p.played()[0] = true;
                SoundEvent sound = e.kind() == WorldEvents.Kind.SIREN ? WorldEvents.SIREN
                        : d > 150 ? WorldEvents.EXPLOSION_FAR : WorldEvents.EXPLOSION;
                if (d < sound.getRange(1)) {
                    mc.getSoundManager().play(new Fixed(sound, at, e.kind() == WorldEvents.Kind.SIREN ? 1.0f : 1.6f));
                }
            }
        }
    }

    /** A siren or explosion, where it is; its loudness falls off with distance as the player moves. */
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
