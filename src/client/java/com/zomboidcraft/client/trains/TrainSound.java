package com.zomboidcraft.client.trains;

import com.zomboidcraft.world.trains.Line;
import com.zomboidcraft.world.trains.TrainCar;
import com.zomboidcraft.world.trains.TrainEntity;
import com.zomboidcraft.world.trains.TrainRider;
import com.zomboidcraft.world.trains.TrainSounds;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * A moving train's rumble, from the car nearest you (or all around you when you're aboard),
 * louder and higher the faster it goes, with the clack of the rail joints passing under the wheels.
 */
final class TrainSound extends AbstractTickableSoundInstance {
    private static final Map<TrainEntity, TrainSound> PLAYING = new WeakHashMap<>();

    private final TrainEntity train;
    private double clacked;

    private TrainSound(TrainEntity train) {
        super(TrainSounds.ROLLING, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.train = train;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01f;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        clacked = train.s();
        follow();
    }

    static void keep(TrainEntity train) {
        TrainSound sound = PLAYING.get(train);
        if (train.speed() > 0.01 && (sound == null || sound.isStopped())) {
            sound = new TrainSound(train);
            PLAYING.put(train, sound);
            Minecraft.getInstance().getSoundManager().play(sound);
        }
    }

    private void follow() {
        Minecraft mc = Minecraft.getInstance();
        Player me = mc.player;
        Line line = train.line();
        if (me == null || line == null) {
            return;
        }
        if (((TrainRider) me).zomboidcraft$train() == train) {
            x = me.getX();
            y = me.getY();
            z = me.getZ();
            return;
        }
        Vec3 best = null;
        double bestD = Double.MAX_VALUE;
        for (int car = 0; car < line.cars; car++) {
            TrainCar.Pose p = TrainCar.pose(line.route, train.s(), car);
            Vec3 local = p.toLocal(me.position());
            Vec3 at = p.toWorld(0, -0.5, Mth.clamp(local.z, -TrainCar.HALF, TrainCar.HALF));
            double d = at.distanceToSqr(me.position());
            if (d < bestD) {
                bestD = d;
                best = at;
            }
        }
        if (best != null) {
            x = best.x;
            y = best.y;
            z = best.z;
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (train.isRemoved()) {
            stop();
            return;
        }
        follow();
        double speed = train.speed() * 20;
        float fast = (float) Mth.clamp(speed / 14, 0, 1);
        volume = speed < 0.3 ? 0 : 0.25f + 0.85f * fast;
        pitch = 0.5f + 0.5f * fast;
        Line line = train.line();
        Minecraft mc = Minecraft.getInstance();
        if (line != null && mc.player != null && speed > 1) {
            // a pair of clacks each rail length (18 blocks), as each bogie crosses a joint
            double s = train.s();
            if (Math.abs(line.route.diff(clacked, s)) > 18) {
                clacked = s;
                float v = Math.min(1f, volume) * 0.5f;
                mc.level.playLocalSound(x, y, z, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.NEUTRAL, v, 0.55f, false);
            }
        }
    }
}
