package com.zomboidcraft.client.trains;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.trains.Line;
import com.zomboidcraft.world.trains.TrainCar;
import com.zomboidcraft.world.trains.TrainEntity;
import com.zomboidcraft.world.trains.TrainNet;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * OTTO speaking: a line's clips played one after another from the speaker over your head in the
 * car you're nearest (each car has one in its ceiling), following the train as it moves. One line
 * at a time; a line that comes while he's talking waits its turn, and if they pile up the oldest go.
 */
final class OttoVoice {
    private static final Deque<TrainNet.OttoSay> WAITING = new ArrayDeque<>();
    private static TrainNet.OttoSay line;
    private static int next;
    private static Clip playing;
    private static int gap;

    static void received(TrainNet.OttoSay say) {
        WAITING.addLast(say);
        while (WAITING.size() > 2) {
            WAITING.removeFirst();
        }
    }

    static void clear() {
        WAITING.clear();
        line = null;
        if (playing != null) {
            Minecraft.getInstance().getSoundManager().stop(playing);
            playing = null;
        }
    }

    /** Every client tick: keeps him talking and his mouth moving. */
    static void tick(Minecraft mc) {
        if (mc.level == null) {
            return;
        }
        if (playing != null) {
            if (mc.getSoundManager().isActive(playing)) {
                if (playing.train != null) {
                    playing.train.talking = 0;
                }
                return;
            }
            playing = null;
            gap = 3;
        }
        if (gap > 0) {
            gap--;
            return;
        }
        if (line == null || next >= line.clips().size()) {
            line = WAITING.pollFirst();
            next = 0;
            if (line == null) {
                return;
            }
        }
        TrainEntity train = line.train() >= 0 && mc.level.getEntity(line.train()) instanceof TrainEntity t ? t : null;
        String clip = line.clips().get(next++);
        playing = new Clip(SoundEvent.createVariableRangeEvent(ZomboidCraft.id(clip)), train);
        mc.getSoundManager().play(playing);
        gap = 0;
    }

    private static final class Clip extends AbstractTickableSoundInstance {
        final TrainEntity train;

        Clip(SoundEvent event, TrainEntity train) {
            super(event, SoundSource.VOICE, SoundInstance.createUnseededRandom());
            this.train = train;
            this.volume = 1f;
            this.delay = 0;
            this.relative = train == null;   // the station speaker: right with you
            this.attenuation = train == null ? SoundInstance.Attenuation.NONE : SoundInstance.Attenuation.LINEAR;
            follow();
        }

        @Override
        public void tick() {
            if (train != null && train.isRemoved()) {
                stop();
                return;
            }
            follow();
        }

        /** The ceiling speaker nearest you: over your head if you're aboard, the nearest bit of the train if not. */
        private void follow() {
            Player me = Minecraft.getInstance().player;
            Line line = train == null ? null : train.line();
            if (me == null || line == null) {
                x = y = z = 0;
                return;
            }
            Vec3 best = null;
            double bestD = Double.MAX_VALUE;
            for (int car = 0; car < line.cars; car++) {
                TrainCar.Pose p = TrainCar.pose(line.route, train.s(), car);
                Vec3 local = p.toLocal(me.position());
                Vec3 at = p.toWorld(0, TrainCar.CEILING - 0.2, Mth.clamp(local.z, -TrainCar.HALF + 1, TrainCar.HALF - 1));
                double d = at.distanceToSqr(me.getEyePosition());
                if (d < bestD) {
                    bestD = d;
                    best = at;
                }
            }
            x = best.x;
            y = best.y;
            z = best.z;
        }
    }

    private OttoVoice() {
    }
}
